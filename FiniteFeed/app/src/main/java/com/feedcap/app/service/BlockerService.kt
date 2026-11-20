package com.feedcap.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.format.DateFormat
import android.util.Log
import com.feedcap.app.BuildConfig
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.feedcap.app.budget.Block
import com.feedcap.app.budget.BlockReason
import com.feedcap.app.budget.Budget
import com.feedcap.app.budget.BudgetState
import com.feedcap.app.data.Store
import com.feedcap.app.detect.AppRule
import com.feedcap.app.detect.FeedWall
import com.feedcap.app.detect.HomeFeedMode
import com.feedcap.app.detect.Rules
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Watches Instagram and YouTube, counts Reels/Shorts against the budget and sends the user back
 * when a limit or sleep time is reached. Everything happens in memory on the device; only counters are saved.
 *
 * Reels opened from a DM or a link always play and are never counted. Swiping on from one counts
 * like any other Reel (blocked only at a limit or in sleep time), unless the user blocks it outright.
 */
class BlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: Store
    private lateinit var overlay: BlockOverlay
    private lateinit var power: PowerManager
    private lateinit var tamperGuard: TamperGuard

    /** Last real (non-transient) app in front, and since when. */
    private var foregroundPkg: String? = null
    private var foregroundSince = 0L
    /** The app in front before [foregroundPkg]. */
    private var previousPkg: String? = null
    /** A limited surface was entered since [foregroundPkg] came to the front. */
    private var surfaceSinceForeground = false
    /** Home-screen apps: opening Reels from one isn't "from a link". */
    private var launchers: Set<String> = emptySet()

    /** Rule of the limited surface on screen, or null when not on one. */
    private var surfaceRule: AppRule? = null
    /** This visit to the surface started from a DM or a link. */
    private var sharedVisit = false
    /** Still on the shared video itself (no swipe yet): plays freely and isn't counted. */
    private var freeVideo = false
    private var enteredAt = 0L

    /** A DM thread is the screen in front (it stays true while it sits still, with no events). */
    private var dmShowing = false
    private var dmLeftAt = 0L

    private var lastScanAt = 0L
    private var pendingScanRule: AppRule? = null
    private var lastPagerIndex = -1
    private var lastScrollAt = 0L
    private var lastTickAt = 0L
    private var lastEnforceAt = 0L
    private var lastSharedToastAt = 0L

    /** The home feed is the screen in front (set by scans). */
    private var onHomeFeed = false
    /** Instagram's Following feed is in front (set by scans). */
    private var onFollowingFeed = false
    private val feedWall = FeedWall()
    private var lastFeedCheckAt = 0L
    private var lastFeedPushAt = 0L
    private var lastFeedToastAt = 0L

    private var lastLeftPkg: String? = null
    private var lastLeftAt = 0L
    private var lastLeftFree = false
    private var lastLeftShared = false

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, TICK_MS)
        }
    }
    private val trailingScan = Runnable { pendingScanRule?.let { scan(it) } }

    override fun onServiceConnected() {
        store = Store.get(this)
        overlay = BlockOverlay(this)
        power = getSystemService(PowerManager::class.java)
        // "Finite Feed" also matches "Finite Feed Pro Test".
        tamperGuard = TamperGuard(this, "Finite Feed")
        // Onboarding sent the user to Accessibility settings: bring them back now it's on.
        if (store.awaitingPermission) {
            store.awaitingPermission = false
            runCatching {
                startActivity(
                    // SINGLE_TOP + CLEAR_TOP: reuse the open FeedCap screen instead of starting a new one,
                    // so onboarding carries on from the permission step.
                    Intent(this, com.feedcap.app.MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
                )
            }
        }
        launchers = packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
            .toSet()
        handler.post(tick)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.hide()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::store.isInitialized) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val now = System.currentTimeMillis()

        if (pkg in TamperGuard.GUARDED_PACKAGES) guardSettings(pkg, now)
        val isStateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        if (isStateChange && !isTransient(pkg) && pkg != foregroundPkg) {
            previousPkg = foregroundPkg
            surfaceSinceForeground = false
            foregroundPkg = pkg
            foregroundSince = now
            setDmShowing(false, now)
            onHomeFeed = false
            feedWall.reset()
        }

        val rule = activeRule(pkg)
        if (rule == null) {
            if (isStateChange && !isTransient(pkg) && surfaceRule != null) leaveSurface(now)
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> scan(rule)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> scanThrottled(rule, now)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScroll(event, rule, now)
        }
    }

    // ---- Parental controls ----

    private var guardBacks = 0
    private var lastGuardAt = 0L

    /** Closes screens that could turn FeedCap off, unless a parent has unlocked with the PIN. */
    private fun guardSettings(pkg: String, now: Long) {
        val p = store.parental.value
        if (!p.enabled || !p.guard || p.unlocked(now)) return
        if (!tamperGuard.isTamperScreen(pkg, rootInActiveWindow, now)) return

        if (now - lastGuardAt > 5_000) guardBacks = 0
        lastGuardAt = now
        // Back closes the page or dialog; if it's still there, go to the home screen.
        performGlobalAction(if (guardBacks++ < 2) GLOBAL_ACTION_BACK else GLOBAL_ACTION_HOME)
        if (!overlay.isShowing) {
            overlay.show(
                "Ask a parent",
                "Finite Feed is managed by a parent. It can only be turned off or removed with the parent PIN.",
            )
        }
        note("parental: closed a settings screen ($pkg)")
    }

    // ---- Detection ----

    private fun activeRule(pkg: String): AppRule? {
        val s = store.settings.value
        if (!s.enabled || pkg !in s.apps) return null
        return Rules.forPackage(pkg)
    }

    private fun scanThrottled(rule: AppRule, now: Long) {
        if (now - lastScanAt >= SCAN_MS) {
            scan(rule)
        } else {
            pendingScanRule = rule
            handler.removeCallbacks(trailingScan)
            handler.postDelayed(trailingScan, SCAN_MS)
        }
    }

    /**
     * The app's own window. Not simply rootInActiveWindow: once the block screen or curtain is
     * touched, Android makes that overlay the active window, and reading it instead of the app
     * looked like "left Reels", which stopped enforcement while Reels was still on screen.
     * Prefers the active window when it is the app; otherwise the app's largest window on screen.
     */
    private fun appRoot(pkg: String): AccessibilityNodeInfo? {
        rootInActiveWindow?.takeIf { it.packageName?.toString() == pkg }?.let { return it }
        val r = Rect()
        return windows.asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root }
            .filter { it.packageName?.toString() == pkg }
            .maxByOrNull { it.getBoundsInScreen(r); r.width().toLong() * r.height() }
    }

    /** A visible view with this short ID in any of the app's windows. */
    private fun findInApp(rule: AppRule, id: String): AccessibilityNodeInfo? =
        appRoot(rule.pkg)?.findAccessibilityNodeInfosByViewId(rule.fullId(id))?.firstOrNull { it.isVisibleToUser }

    private fun scan(rule: AppRule) {
        val now = System.currentTimeMillis()
        lastScanAt = now
        val root = appRoot(rule.pkg) ?: return

        val onSurface = isOnSurface(root, rule)
        // Only screens outside Reels/Shorts can mark a DM (the Reels UI has its own share/send bits).
        if (!onSurface) setDmShowing(hasDmMarker(root, rule), now)
        val wasOnHomeFeed = onHomeFeed
        onHomeFeed = !onSurface && isOnHomeFeed(root, rule)
        if (onHomeFeed && !wasOnHomeFeed) lastMainFeedFrom = -1
        onFollowingFeed = !onSurface && !onHomeFeed && isOnFollowingFeed(root, rule)
        if (store.inspector.value) publishInspector(root, rule, onSurface)
        debugLogScreen(root, rule, onSurface, now)

        when {
            onSurface && surfaceRule == null -> enterSurface(rule, now)
            !onSurface && surfaceRule != null -> leaveSurface(now)
        }
    }

    private fun setDmShowing(showing: Boolean, now: Long) {
        if (dmShowing && !showing) dmLeftAt = now
        dmShowing = showing
    }

    /** The last screen before this one was a DM (allowing for a short loading screen in between). */
    private fun cameFromDm(now: Long) = dmShowing || now - dmLeftAt < FROM_DM_WINDOW_MS

    private fun isOnSurface(root: AccessibilityNodeInfo, rule: AppRule) =
        rule.surfaceIds.any { isVisible(root, rule.fullId(it)) } || hasFullScreenHint(root, rule)

    /**
     * Fallback detection: a visible view filling most of the screen whose ID contains one of the
     * rule's hints. Only descends into big views, so it stays cheap.
     */
    private fun hasFullScreenHint(root: AccessibilityNodeInfo, rule: AppRule): Boolean {
        if (rule.surfaceIdHints.isEmpty()) return false
        var found = false
        walkBig(root) { n, id ->
            if (id != null && n.isVisibleToUser && rule.surfaceIdHints.any { id.contains(it) }) found = true
            found
        }
        return found
    }

    /** Visits views at least [BIG_HEIGHT] of the screen tall and [BIG_WIDTH] wide (with their short ID). */
    private fun walkBig(root: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo, String?) -> Boolean) {
        val dm = resources.displayMetrics
        val minH = dm.heightPixels * BIG_HEIGHT
        val minW = dm.widthPixels * BIG_WIDTH
        val r = Rect()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen < 250) {
            val n = queue.removeFirst()
            seen++
            n.getBoundsInScreen(r)
            if (r.height() < minH || r.width() < minW) continue
            if (visit(n, n.viewIdResourceName?.substringAfter(":id/"))) return
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
        }
    }

    private var lastDebugLogAt = 0L

    /** Debug builds: log the big views on screen, to find a surface's IDs with `adb logcat -s FeedCap`. */
    private fun debugLogScreen(root: AccessibilityNodeInfo, rule: AppRule, onSurface: Boolean, now: Long) {
        if (!BuildConfig.DEBUG || now - lastDebugLogAt < 1_500) return
        lastDebugLogAt = now
        val ids = mutableListOf<String>()
        walkBig(root) { n, id ->
            if (id != null && n.isVisibleToUser) ids += id
            false
        }
        Log.d(TAG, "${rule.label} surface=$onSurface dm=$dmShowing homeFeed=$onHomeFeed big=$ids")
    }

    /** Instagram's Following feed: a screen titled "Following". */
    private fun isOnFollowingFeed(root: AccessibilityNodeInfo, rule: AppRule): Boolean {
        val id = rule.followingTitleId ?: return false
        val label = rule.followingLabel ?: return false
        return root.findAccessibilityNodeInfosByViewId(rule.fullId(id))
            .any { it.isVisibleToUser && it.text?.toString() == label }
    }

    private fun isOnHomeFeed(root: AccessibilityNodeInfo, rule: AppRule): Boolean {
        if (rule.feedTopBarIds.any { isVisible(root, rule.fullId(it)) }) return true
        if (rule.feedScreenIds.none { isVisible(root, rule.fullId(it)) }) return false
        val tab = rule.feedTabId ?: return true
        return root.findAccessibilityNodeInfosByViewId(rule.fullId(tab)).any { it.isVisibleToUser && isTabSelected(it) }
    }

    /**
     * A bottom tab is selected. Instagram has marked this on the tab itself and, more recently
     * (seen Nov 21 2026, Instagram 457), only on the tab's icon inside it, so check both.
     */
    private fun isTabSelected(tab: AccessibilityNodeInfo): Boolean {
        if (tab.isSelected) return true
        for (i in 0 until tab.childCount) {
            val c = tab.getChild(i) ?: continue
            if (c.isSelected) return true
            for (j in 0 until c.childCount) if (c.getChild(j)?.isSelected == true) return true
        }
        return false
    }

    private fun isVisible(root: AccessibilityNodeInfo, fullId: String) =
        root.findAccessibilityNodeInfosByViewId(fullId).any { it.isVisibleToUser }

    private fun hasDmMarker(root: AccessibilityNodeInfo, rule: AppRule): Boolean {
        if (rule.dmIds.any { isVisible(root, rule.fullId(it)) }) return true
        val hint = rule.dmHint ?: return false
        var found = false
        walk(root, 300) { n ->
            if (n.isVisibleToUser && n.className?.toString() == "android.widget.EditText") {
                val h = n.hintText?.toString() ?: n.text?.toString()
                if (h != null && h.startsWith(hint, ignoreCase = true)) found = true
            }
            found
        }
        return found
    }

    private fun enterSurface(rule: AppRule, now: Long) {
        surfaceRule = rule
        enteredAt = now
        lastPagerIndex = -1
        lastScrollAt = 0
        lastTickAt = now

        // A reel opened from a DM or a link always plays, whatever the limits say.
        // "From a link" = the first Reels/Shorts screen shortly after another app (not the home
        // screen) handed over to this one, e.g. tapping a link in WhatsApp or Chrome.
        val fromDm = cameFromDm(now)
        val fromOutside = !surfaceSinceForeground &&
            now - foregroundSince < FROM_OUTSIDE_WINDOW_MS &&
            previousPkg != null && previousPkg !in launchers && !isTransient(previousPkg!!)
        val reentry = rule.pkg == lastLeftPkg && now - lastLeftAt < REENTRY_GRACE_MS
        surfaceSinceForeground = true
        setDmShowing(false, now)
        dmLeftAt = 0

        when {
            fromDm || fromOutside -> {
                sharedVisit = true
                freeVideo = true
                store.record(rule.pkg) { it.copy(shared = it.shared + 1) }
                note("entered ${rule.surfaceName} from ${if (fromDm) "a DM" else "a link"} (free)")
            }
            reentry -> {
                // Coming straight back (notification shade, a quick app switch) is the same video.
                sharedVisit = lastLeftShared
                freeVideo = lastLeftFree
                note("back on ${rule.surfaceName}")
            }
            else -> {
                sharedVisit = false
                freeVideo = false
                note("entered ${rule.surfaceName}")
                if (!postModeActive(now)) countVideo(rule, now)
            }
        }
    }

    private fun leaveSurface(now: Long) {
        val rule = surfaceRule ?: return
        addWatchTime(rule, now)
        lastLeftPkg = rule.pkg
        lastLeftAt = now
        lastLeftFree = freeVideo
        lastLeftShared = sharedVisit
        surfaceRule = null
        freeVideo = false
        sharedVisit = false
        note("left ${rule.surfaceName}")
    }

    private fun onScroll(event: AccessibilityEvent, rule: AppRule, now: Long) {
        if (surfaceRule == null) {
            // On a cold start the first scrolls can arrive before any screen scan has run, so
            // check for the home feed right here when the feed list itself scrolls.
            if (!onHomeFeed && isFeedListEvent(event, rule) && now - lastHomeCheckAt >= HOME_CHECK_MS) {
                lastHomeCheckAt = now
                val root = appRoot(rule.pkg)
                if (root != null && isOnHomeFeed(root, rule)) {
                    onHomeFeed = true
                    lastMainFeedFrom = -1
                    note("home feed: found on scroll")
                }
            }
            // Sleep time: no feed scrolling at all, main or Following. Send them to messages.
            if ((onHomeFeed || onFollowingFeed) && sleepBlock(now) != null && !postModeActive(now)) {
                onFeedScrollAtNight(event, rule, now)
                scanThrottled(rule, now)
                return
            }
            if (onHomeFeed && !postModeActive(now)) {
                when (store.settings.value.homeFeed) {
                    HomeFeedMode.FOLLOWING -> onMainFeedScroll(event, rule, now)
                    HomeFeedMode.CAUGHT_UP -> onFeedScroll(event, rule, now)
                    HomeFeedMode.ALLOW -> Unit
                }
            }
            scanThrottled(rule, now)
            return
        }
        val pager = isPagerSource(event.source, rule)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "scroll src=${event.source?.viewIdResourceName} pager=$pager from=${event.fromIndex} to=${event.toIndex} last=$lastPagerIndex")
        }
        if (!pager) return

        val idx = event.fromIndex
        // The pager settles and preloads right after opening; that isn't a swipe.
        if (now - enteredAt < SETTLE_MS) {
            if (idx >= 0) lastPagerIndex = idx
            lastScrollAt = now
            return
        }
        if (idx >= 0) {
            when {
                lastPagerIndex < 0 -> lastPagerIndex = idx
                idx > lastPagerIndex -> {
                    lastPagerIndex = idx
                    nextVideo(rule, now, event.source)
                }
                idx < lastPagerIndex -> lastPagerIndex = idx
            }
        } else {
            // No page index on this event: count one video per burst of scroll events.
            if (now - lastScrollAt > SCROLL_BURST_GAP_MS) nextVideo(rule, now, event.source)
            lastScrollAt = now
        }
    }

    // ---- Instagram main feed → Following ----

    private var lastRedirectAt = 0L
    /** First visible item of the main feed on its last scroll event; -1 = just arrived. */
    private var lastMainFeedFrom = -1

    private var lastHomeCheckAt = 0L

    /** A scroll of the home feed's list. The source can be missing on a cold start; then go by class. */
    private fun isFeedListEvent(event: AccessibilityEvent, rule: AppRule): Boolean {
        if (rule.feedListIds.isEmpty()) return false
        val id = event.source?.viewIdResourceName
        return if (id != null) id in rule.feedListIds else event.className?.contains("RecyclerView") == true
    }

    /** The feed list node: the event's source, or found on screen when the event has none. */
    private fun feedList(event: AccessibilityEvent, rule: AppRule): AccessibilityNodeInfo? {
        event.source?.let { return if (it.viewIdResourceName in rule.feedListIds) it else null }
        val root = appRoot(rule.pkg) ?: return null
        return rule.feedListIds.asSequence()
            .flatMap { root.findAccessibilityNodeInfosByViewId(it).asSequence() }
            .firstOrNull { it.isVisibleToUser }
    }

    /** Scrolling down the main (algorithmic) feed opens the Following feed instead. */
    private fun onMainFeedScroll(event: AccessibilityEvent, rule: AppRule, now: Long) {
        if (!isFeedListEvent(event, rule)) return
        val list = feedList(event, rule) ?: return
        val dy = if (Build.VERSION.SDK_INT >= 28) event.scrollDeltaY else 0
        val from = event.fromIndex
        val prevFrom = lastMainFeedFrom
        lastMainFeedFrom = from
        if (BuildConfig.DEBUG) Log.d(TAG, "main feed scroll from=$from prev=$prevFrom dy=$dy")
        if (now - lastRedirectAt < REDIRECT_COOLDOWN_MS) return

        val scrolledDown = dy > 0 || (prevFrom >= 0 && from > prevFrom)
        when {
            scrolledDown -> {
                lastRedirectAt = now
                // Black the screen at once so the feed can't be watched while we switch.
                overlay.showCurtain("The main feed is off.\nOpening Following…", maxMs = REDIRECT_CURTAIN_MS)
                openFeedMenu(rule, list, attempt = 0, round = 0)
            }
            prevFrom < 0 && from > 0 && dy == 0 -> {
                // Instagram put the feed back where it was (coming back from Following, say).
                // Jump to the top, where Stories are, instead of bouncing to Following again.
                lastRedirectAt = now
                feedToTop(rule, list)
            }
        }
    }

    /**
     * Sleep time: scrolling a feed blacks the screen with a short note and opens messages,
     * so the only thing left to do on Instagram is text someone (or put the phone down).
     */
    private fun onFeedScrollAtNight(event: AccessibilityEvent, rule: AppRule, now: Long) {
        if (!isFeedListEvent(event, rule)) return
        val dy = if (Build.VERSION.SDK_INT >= 28) event.scrollDeltaY else 0
        val from = event.fromIndex
        val prevFrom = lastMainFeedFrom
        lastMainFeedFrom = from
        val scrolledDown = dy > 0 || (prevFrom >= 0 && from > prevFrom)
        if (!scrolledDown || now - lastRedirectAt < REDIRECT_COOLDOWN_MS) return
        lastRedirectAt = now
        recordBlock(rule.pkg)

        overlay.showCurtain("It's sleep time.\n\nText someone if it's important,\nor get some sleep.", maxMs = SLEEP_CURTAIN_MS)
        val tab = rule.dmTabId?.let { findInApp(rule, it) }
        when {
            tab != null && clickNodeOrParent(tab) -> note("sleep: feed scroll, opened messages")
            // The Following feed has no tab bar: Back returns to the main feed, which has one.
            onFollowingFeed -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                handler.postDelayed({
                    rule.dmTabId?.let { findInApp(rule, it) }?.let(::clickNodeOrParent)
                }, MENU_WAIT_MS * 2)
                note("sleep: Following scroll, back then messages")
            }
            else -> {
                feedList(event, rule)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                overlay.show("It's sleep time", "Text someone if it's important, or get some sleep.")
                note("sleep: messages tab not found")
            }
        }
    }

    /** Re-tapping the Home tab scrolls Instagram's feed to the top. */
    private fun feedToTop(rule: AppRule, list: AccessibilityNodeInfo) {
        val tab = rule.feedTabId?.let { findInApp(rule, it) }
        if (tab == null || !clickNodeOrParent(tab)) list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        note("home feed: back to the top")
    }

    /** Instagram is still on screen (the curtain may be the active window); if the person left, stop tapping. */
    private fun stillIn(rule: AppRule): Boolean = appRoot(rule.pkg) != null

    /**
     * Taps the Instagram logo, which opens the feed menu. The logo hides while scrolling down,
     * so bring it back step by step: scroll up a little, then jump the feed to the top, then
     * give the header time to slide back in.
     */
    private fun openFeedMenu(rule: AppRule, list: AccessibilityNodeInfo, attempt: Int, round: Int) {
        if (!stillIn(rule)) return redirectFailed(rule, list, "left Instagram")
        val title = rule.feedTitleId?.let { findInApp(rule, it) }
        if (title != null && clickNodeOrParent(title)) {
            // The logo opens a small menu (Following, Favorites); pick Following once it's there.
            handler.postDelayed({ pickFollowing(rule, list, attempt = 0, round = round) }, MENU_WAIT_MS)
            return
        }
        when (attempt) {
            0 -> {
                list.refresh()
                list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            }
            // Re-tapping Home jumps the feed to the top, where the header always shows.
            1 -> rule.feedTabId?.let { findInApp(rule, it) }?.let(::clickNodeOrParent)
            in 2 until LOGO_TRIES -> Unit  // the header is still sliding in
            else -> return redirectFailed(rule, list, "logo not found")
        }
        handler.postDelayed({ openFeedMenu(rule, list, attempt + 1, round) }, MENU_WAIT_MS)
    }

    private fun pickFollowing(rule: AppRule, list: AccessibilityNodeInfo, attempt: Int, round: Int) {
        val label = rule.followingLabel ?: return
        if (!stillIn(rule)) return redirectFailed(rule, list, "left Instagram")
        // The menu is a popup in its own window, so search every Instagram window.
        val roots = (windows.mapNotNull { it.root } + listOfNotNull(rootInActiveWindow))
            .filter { it.packageName?.toString() == rule.pkg }
        val candidates = roots.flatMap { it.findAccessibilityNodeInfosByText(label) }.filter { it.isVisibleToUser }
        // The menu row first; failing that, a row labelled exactly "Following" in a popup window.
        // Never the main window: there "Following" can be a follow button, and tapping it unfollows.
        val mainWindow = appRoot(rule.pkg)?.windowId
        val item = candidates.firstOrNull { n ->
            n.viewIdResourceName?.endsWith("context_menu_item_label") == true ||
                (n.isClickable && n.contentDescription?.toString() == label)
        } ?: candidates.firstOrNull { n ->
            n.windowId != mainWindow &&
                (n.text?.toString()?.trim() == label || n.contentDescription?.toString()?.trim() == label)
        }
        if (item != null && clickNodeOrParent(item)) {
            // Lift the black screen once Following has had a moment to open.
            handler.postDelayed({ overlay.hideCurtain() }, CURTAIN_AFTER_MS)
            recordBlock(rule.pkg)
            toast("Here's Following: people you follow, newest first.")
            note("home feed: opened Following (round $round, try $attempt)")
            return
        }
        when {
            attempt < MENU_RETRIES ->
                handler.postDelayed({ pickFollowing(rule, list, attempt + 1, round) }, MENU_WAIT_MS)
            // The tap may have landed while the header was moving. Go round once more.
            round == 0 -> openFeedMenu(rule, list, attempt = 0, round = 1)
            else -> redirectFailed(rule, list, "Following menu not found")
        }
    }

    /**
     * Couldn't open Following this time: put the feed back at the top, behind the curtain, and
     * lift it. No instructions screen; the next scroll down simply tries again.
     */
    private fun redirectFailed(rule: AppRule, list: AccessibilityNodeInfo, why: String) {
        note("home feed: redirect failed ($why)")
        if (stillIn(rule)) {
            list.refresh()
            feedToTop(rule, list)
        }
        handler.postDelayed({ overlay.hideCurtain() }, CURTAIN_AFTER_MS)
        lastMainFeedFrom = -1
        lastRedirectAt = System.currentTimeMillis()
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        repeat(4) {
            val cur = n ?: return false
            if (cur.isClickable) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = cur.parent
        }
        return false
    }

    // ---- Home feed wall ----

    /** Home feed scrolled: past "You're all caught up" means suggested posts, so push back up. */
    private fun onFeedScroll(event: AccessibilityEvent, rule: AppRule, now: Long) {
        if (rule.feedEndTexts.isEmpty()) return
        val list = event.source ?: return
        if (list.viewIdResourceName !in rule.feedListIds) return

        val dy = if (Build.VERSION.SDK_INT >= 28) event.scrollDeltaY else 0
        // Looking for the row costs a cross-process call, so not on every event.
        val endVisible = if (now - lastFeedCheckAt >= FEED_CHECK_MS) {
            lastFeedCheckAt = now
            rule.feedEndTexts.any { t -> list.findAccessibilityNodeInfosByText(t).any { it.isVisibleToUser } }
        } else {
            null
        }
        val hadWall = feedWall.wallIndex
        val past = feedWall.onScroll(event.fromIndex, event.toIndex, dy, endVisible)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "feed from=${event.fromIndex} to=${event.toIndex} dy=$dy end=$endVisible wall=${feedWall.wallIndex} past=$past")
        }
        if (hadWall < 0 && feedWall.wallIndex >= 0) note("home feed: caught up at item ${feedWall.wallIndex}")
        if (past && !postModeActive(now)) pushBackFeed(list, now)
    }

    private fun pushBackFeed(list: AccessibilityNodeInfo, now: Long) {
        if (now - lastFeedPushAt < FEED_PUSH_GAP_MS) return
        lastFeedPushAt = now
        if (!list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        if (now - lastFeedToastAt > SHARED_TOAST_GAP_MS) {
            lastFeedToastAt = now
            recordBlock(Rules.instagram.pkg)
            toast("You're all caught up. Suggested posts are off.")
        }
        note("home feed: pushed back from suggested posts")
    }

    /** The full-screen vertical pager, not a comments sheet or caption scrolling. */
    private fun isPagerSource(src: AccessibilityNodeInfo?, rule: AppRule): Boolean {
        src ?: return false
        val id = src.viewIdResourceName
        if (id != null && rule.pagerIds.any { rule.fullId(it) == id }) return true
        val r = Rect()
        src.getBoundsInScreen(r)
        val dm = resources.displayMetrics
        return r.height() >= dm.heightPixels * 0.8 && r.width() >= dm.widthPixels * 0.9
    }

    // ---- Counting ----

    private fun nextVideo(rule: AppRule, now: Long, pager: AccessibilityNodeInfo?) {
        if (postModeActive(now)) return
        if (freeVideo) {
            // Swiping past the shared video. At a limit (or in sleep time, or with swiping off),
            // put them back on the video they were sent and explain; otherwise it's a normal Reel.
            val block = if (store.settings.value.blockSharedSwipe) {
                Block(BlockReason.SHARED, 0)
            } else {
                sleepBlock(now) ?: Budget.currentBlock(normalized(rule.pkg, now), now, limitsFor(rule.pkg), nextMidnight())
                    ?: sessionFullBlock(rule.pkg, now)
            }
            if (block != null) {
                returnToShared(rule, block, pager, now)
                return
            }
            freeVideo = false
            lastTickAt = now
        }
        countVideo(rule, now)
    }

    /** The session cap is reached but not locked yet: the next counted video would be blocked. */
    private fun sessionFullBlock(pkg: String, now: Long): Block? {
        val limits = limitsFor(pkg)
        val s = normalized(pkg, now)
        return if (s.sessionVideos >= limits.videosPerSession) {
            Block(BlockReason.SESSION, now + limits.resetMs)
        } else {
            null
        }
    }

    /** Scrolls back to the shared video and shows why they can't go further. */
    private fun returnToShared(rule: AppRule, block: Block, pager: AccessibilityNodeInfo?, now: Long) {
        val back = pager?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
        if (!back) {
            // Couldn't scroll back: leave like any other block.
            freeVideo = false
            enforce(rule, block, now)
            return
        }
        lastPagerIndex = -1
        enteredAt = now // the scroll back settles like an opening
        if (!overlay.isShowing) {
            recordBlock(rule.pkg)
            val (title, message) = blockText(rule, block)
            overlay.show(title, "$message\nYou can keep watching the one you were sent.")
        }
        note("swipe past shared ${rule.surfaceName} blocked: ${block.reason}")
    }

    private fun countVideo(rule: AppRule, now: Long) {
        sleepBlock(now)?.let {
            enforce(rule, it, now)
            return
        }
        val limits = limitsFor(rule.pkg)
        val step = Budget.onVideo(normalized(rule.pkg, now), now, limits, nextMidnight())
        updateBudget(rule.pkg) { step.state }
        val block = step.block
        if (block == null) store.record(rule.pkg) { it.copy(videos = it.videos + 1) }
        if (block != null) {
            enforce(rule, block, now)
        } else {
            val left = limits.videosPerSession - step.state.sessionVideos
            note("video ${step.state.sessionVideos}/${limits.videosPerSession}")
            if (left == WARN_VIDEOS_LEFT) toast("$left ${rule.surfaceName} left in this session")
        }
    }

    /** Adds time since the last tick. Returns a block if the daily limit is now reached. */
    private fun addWatchTime(rule: AppRule, now: Long): Block? {
        val elapsed = (now - lastTickAt).coerceIn(0, MAX_TICK_GAP_MS)
        lastTickAt = now
        if (freeVideo || postModeActive(now) || !power.isInteractive || elapsed == 0L) return null

        val limits = limitsFor(rule.pkg)
        val before = normalized(rule.pkg, now)
        val step = Budget.onWatchTime(before, now, elapsed, limits, nextMidnight())
        updateBudget(rule.pkg) { step.state }
        store.record(rule.pkg) { it.copy(usedMs = it.usedMs + elapsed) }

        val leftBefore = limits.dailyMs - before.usedMs
        val leftAfter = limits.dailyMs - step.state.usedMs
        if (leftBefore > WARN_TIME_LEFT_MS && leftAfter in 1..WARN_TIME_LEFT_MS) {
            toast("2 minutes of short videos left today")
        }
        return step.block
    }

    /** What should stop the user right now, if anything. A shared video on screen is never blocked. */
    private fun blockNow(rule: AppRule, now: Long): Block? {
        if (freeVideo || postModeActive(now)) return null
        if (sharedVisit && store.settings.value.blockSharedSwipe) return Block(BlockReason.SHARED, 0)
        return sleepBlock(now)
            ?: Budget.currentBlock(normalized(rule.pkg, now), now, limitsFor(rule.pkg), nextMidnight())
    }

    private fun onTick() {
        val now = System.currentTimeMillis()
        store.applyPendingIfDue(now)
        store.applyPinResetIfDue(now)
        val rule = surfaceRule ?: return
        if (!store.settings.value.enabled || rule.pkg !in store.settings.value.apps) {
            leaveSurface(now)
            return
        }

        // Videos play without accessibility events, so re-check the screen every tick.
        // Read the app's own window: the block screen may be the active window right now.
        val root = appRoot(rule.pkg)
        if (root == null && rootInActiveWindow == null && windows.isEmpty()) return  // can't tell this tick
        if (root == null || !isOnSurface(root, rule)) {
            leaveSurface(now)
            return
        }
        if (postModeActive(now)) {
            lastTickAt = now
            return
        }

        val block = addWatchTime(rule, now) ?: blockNow(rule, now)
        block?.let { enforce(rule, it, now) }
    }

    // ---- Blocking ----

    private fun enforce(rule: AppRule, block: Block, now: Long) {
        if (now - lastEnforceAt < ENFORCE_GAP_MS) return
        lastEnforceAt = now
        // One checked exit at a time; ticks during it only keep the block screen up.
        if (!exiting) exitSurface(rule, step = 0)

        if (block.reason == BlockReason.SHARED) {
            // Light touch: they only wanted the reel someone sent them.
            if (now - lastSharedToastAt > SHARED_TOAST_GAP_MS) {
                lastSharedToastAt = now
                recordBlock(rule.pkg)
                toast("Only the ${rule.surfaceName.removeSuffix("s")} you were sent. Swiping on is off.")
            }
            note("blocked: swipe from shared")
            return
        }

        if (!overlay.isShowing) {
            recordBlock(rule.pkg)
            val (title, message) = blockText(rule, block)
            overlay.show(title, "$message\nStories, messages and reels people send you still work.")
            note("blocked: ${block.reason}")
        }
    }

    private var exiting = false

    /**
     * Leaves Reels/Shorts and checks that it worked, because one Back isn't always enough
     * (on Instagram's Reels tab, say, or a reel opened from another reel). Each step waits a
     * moment, then looks again: Back, then the app's Home tab, then Back, then the phone's
     * home screen. Back first, so a shared reel returns to its chat.
     */
    private fun exitSurface(rule: AppRule, step: Int) {
        exiting = true
        when (step) {
            0, 2 -> performGlobalAction(GLOBAL_ACTION_BACK)
            1 -> {
                val home = homeTab(rule)
                if (home == null || !clickNodeOrParent(home)) performGlobalAction(GLOBAL_ACTION_BACK)
            }
            else -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
                note("exit: went to the home screen")
                exiting = false
                return
            }
        }
        handler.postDelayed({
            val root = appRoot(rule.pkg)
            if (root != null && isOnSurface(root, rule)) {
                note("exit: still on ${rule.surfaceName} after step $step")
                exitSurface(rule, step + 1)
            } else {
                exiting = false
            }
        }, EXIT_CHECK_MS)
    }

    /** The app's Home tab: Instagram's by ID, otherwise a visible button labelled "Home". */
    private fun homeTab(rule: AppRule): AccessibilityNodeInfo? {
        rule.feedTabId?.let { id -> findInApp(rule, id)?.let { return it } }
        return appRoot(rule.pkg)?.findAccessibilityNodeInfosByText("Home")?.firstOrNull { n ->
            n.isVisibleToUser && (n.contentDescription?.toString() == "Home" || n.text?.toString() == "Home")
        }
    }

    private fun blockText(rule: AppRule, block: Block): Pair<String, String> {
        val limits = limitsFor(rule.pkg)
        // With separate limits the message names the app; shared limits cover both.
        val what = if (store.settings.value.separateLimits) rule.surfaceName else "Reels and Shorts"
        return when (block.reason) {
            BlockReason.SESSION ->
                "That's ${limits.videosPerSession} ${rule.surfaceName} in a row" to
                    "${rule.surfaceName} open again at ${time(block.until)}."
            BlockReason.DAILY ->
                "Today's ${limits.dailyMinutes} minutes are used up" to
                    "$what open again tomorrow."
            BlockReason.SLEEP ->
                "It's sleep time" to
                    "Reels and Shorts open again at ${time(block.until)}."
            BlockReason.SHARED ->
                "Only the ${rule.surfaceName.removeSuffix("s")} you were sent" to
                    "Swiping past shared ${rule.surfaceName} is turned off."
        }
    }

    // ---- Helpers ----

    private fun sleepBlock(now: Long): Block? = store.settings.value.sleep.block(now, ZoneId.systemDefault())

    private fun limitsFor(pkg: String) = store.settings.value.limitsFor(pkg)

    /** This app's budget (its own, or the shared one), rolled over to today. */
    private fun normalized(pkg: String, now: Long): BudgetState =
        Budget.normalize(store.budget(store.settings.value.bucketFor(pkg)), now, today(), limitsFor(pkg))

    private fun updateBudget(pkg: String, change: (BudgetState) -> BudgetState) =
        store.updateBudget(store.settings.value.bucketFor(pkg), change)

    /** Insights: FeedCap stepped in on this app. */
    private fun recordBlock(pkg: String) = store.record(pkg) { it.copy(blocks = it.blocks + 1) }

    private fun postModeActive(now: Long) = store.postModeUntil.value > now

    private fun isTransient(pkg: String) =
        pkg == "com.android.systemui" || pkg.contains("inputmethod") || pkg.contains("keyboard")

    private fun today() = LocalDate.now().toString()

    private fun nextMidnight() =
        LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun time(millis: Long): String = DateFormat.getTimeFormat(this).format(Date(millis))

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun walk(root: AccessibilityNodeInfo, limit: Int, visit: (AccessibilityNodeInfo) -> Boolean) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen < limit) {
            val node = queue.removeFirst()
            seen++
            if (visit(node)) return
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
    }

    private fun publishInspector(root: AccessibilityNodeInfo, rule: AppRule, onSurface: Boolean) {
        val ids = linkedSetOf<String>()
        walk(root, 400) { n ->
            if (n.isVisibleToUser) n.viewIdResourceName?.substringAfter(":id/")?.let(ids::add)
            false
        }
        store.updateDebug {
            it.copy(
                pkg = rule.pkg,
                onSurface = onSurface,
                onDm = dmShowing,
                onHomeFeed = onHomeFeed,
                freeVideo = freeVideo,
                ids = ids.take(80),
            )
        }
    }

    private fun note(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
        if (!store.inspector.value) return
        val line = "${time(System.currentTimeMillis())}  $message"
        store.updateDebug { it.copy(events = (listOf(line) + it.events).take(15)) }
    }

    private companion object {
        const val TAG = "FeedCap"
        const val TICK_MS = 1_000L
        const val SCAN_MS = 250L
        const val MAX_TICK_GAP_MS = 2_000L
        const val FROM_DM_WINDOW_MS = 4_000L
        const val FROM_OUTSIDE_WINDOW_MS = 10_000L
        const val REDIRECT_COOLDOWN_MS = 2_500L
        const val HOME_CHECK_MS = 300L
        const val CURTAIN_AFTER_MS = 700L
        /** Long enough to read the sleep-time note. */
        const val SLEEP_CURTAIN_MS = 2_200L
        const val MENU_WAIT_MS = 350L
        const val MENU_RETRIES = 3
        /** Steps to bring back the hidden logo: scroll up, jump to top, then two waits. */
        const val LOGO_TRIES = 4
        /** Two full rounds of logo + menu take up to about 6.3 s; the curtain outlasts them. */
        const val REDIRECT_CURTAIN_MS = 7_000L
        const val REENTRY_GRACE_MS = 10_000L
        const val SETTLE_MS = 1_200L
        const val SCROLL_BURST_GAP_MS = 900L
        const val ENFORCE_GAP_MS = 700L
        const val SHARED_TOAST_GAP_MS = 4_000L
        const val FEED_CHECK_MS = 120L
        const val BIG_HEIGHT = 0.6
        const val BIG_WIDTH = 0.9
        const val FEED_PUSH_GAP_MS = 350L
        /** Wait after each exit step before checking whether Reels/Shorts is gone. */
        const val EXIT_CHECK_MS = 700L
        const val WARN_VIDEOS_LEFT = 5
        const val WARN_TIME_LEFT_MS = 120_000L
    }
}
