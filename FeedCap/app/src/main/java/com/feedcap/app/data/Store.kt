package com.feedcap.app.data

import android.content.Context
import com.feedcap.app.budget.BudgetState
import com.feedcap.app.budget.Limits
import com.feedcap.app.budget.SleepTime
import com.feedcap.app.detect.HomeFeedMode
import com.feedcap.app.detect.Rules
import com.feedcap.app.insights.DayStats
import com.feedcap.app.insights.Insights
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.LocalDate

data class AppSettings(
    val enabled: Boolean = true,
    val limits: Limits = Limits(),
    val strict: Boolean = false,
    val apps: Set<String> = Rules.all.map { it.pkg }.toSet(),
    val sleep: SleepTime = SleepTime(),
    /**
     * Never allow swiping on from a reel opened from a DM or link, even with budget left.
     * Off by default: swiping on counts toward limits and is blocked only at a limit or in sleep time.
     */
    val blockSharedSwipe: Boolean = false,
    /** Instagram main home feed: send to Following (default), stop at "caught up", or no limit. */
    val homeFeed: HomeFeedMode = HomeFeedMode.FOLLOWING,
    /** Each app gets its own budget ([appLimits]) instead of sharing [limits]. */
    val separateLimits: Boolean = false,
    /** Per-app limits, used when [separateLimits] is on. Missing apps fall back to [limits]. */
    val appLimits: Map<String, Limits> = emptyMap(),
) {
    /** The limits that apply to [pkg]. */
    fun limitsFor(pkg: String): Limits = if (separateLimits) appLimits[pkg] ?: limits else limits

    /** The budget [pkg] counts against: its own, or the one shared by every app. */
    fun bucketFor(pkg: String): String = if (separateLimits) pkg else SHARED_BUCKET

    /** Changes the shared limits ([pkg] null) or one app's own limits. */
    fun withLimits(pkg: String?, change: (Limits) -> Limits): AppSettings =
        if (pkg == null) copy(limits = change(limits)) else copy(appLimits = appLimits + (pkg to change(limitsFor(pkg))))

    /** Turns separate limits on or off; on starts each app from the shared limits. */
    fun withSeparateLimits(on: Boolean): AppSettings =
        if (!on) copy(separateLimits = false)
        else copy(separateLimits = true, appLimits = Rules.all.associate { it.pkg to (appLimits[it.pkg] ?: limits) })

    /**
     * True when going from [cur] to this makes it easier to watch more.
     * Turning Strict mode itself off is not delayed (for now, while testing).
     */
    fun loosens(cur: AppSettings): Boolean =
        (cur.enabled && !enabled) ||
            // Two budgets allow more in total than one shared budget.
            (separateLimits && !cur.separateLimits) ||
            Rules.all.any { looser(limitsFor(it.pkg), cur.limitsFor(it.pkg)) } ||
            !apps.containsAll(cur.apps) ||
            !sleep.covers(cur.sleep) ||
            (cur.blockSharedSwipe && !blockSharedSwipe) ||
            homeFeed < cur.homeFeed

    private fun looser(new: Limits, old: Limits) =
        new.dailyMinutes > old.dailyMinutes ||
            new.videosPerSession > old.videosPerSession ||
            new.resetMinutes < old.resetMinutes

    companion object {
        const val SHARED_BUCKET = "shared"
    }
}

/** A loosening change made in Strict mode, applied after a delay. */
data class Pending(val settings: AppSettings, val applyAt: Long)

data class DebugInfo(
    val pkg: String = "",
    val onSurface: Boolean = false,
    val onDm: Boolean = false,
    val onHomeFeed: Boolean = false,
    val freeVideo: Boolean = false,
    val ids: List<String> = emptyList(),
    val events: List<String> = emptyList(),
)

/** How FeedCap looks. Dark (pure black) is the default. */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** The one accent colour. */
enum class Accent(val label: String) { LIME("Lime"), EMBER("Ember"), SKY("Sky"), ROSE("Rose"), MONO("Mono") }

/** Parental controls. The PIN hash lives in prefs only; [unlockedUntil] is in memory only. */
data class Parental(
    val enabled: Boolean = false,
    /** Stop FeedCap being turned off or uninstalled without the PIN. */
    val guard: Boolean = true,
    val failures: Int = 0,
    val lockedUntil: Long = 0,
    val unlockedUntil: Long = 0,
) {
    fun unlocked(now: Long) = !enabled || unlockedUntil > now
}

sealed interface PinCheck {
    data object Ok : PinCheck
    /** Wrong PIN; [lockedUntil] > 0 when the pad is now locked. */
    data class Wrong(val lockedUntil: Long) : PinCheck
}

/** App state shared by the UI and the accessibility service (same process), kept in SharedPreferences. */
class Store private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("feedcap", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings("", AppSettings()))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _pending = MutableStateFlow(readPending())
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    /** Budget state per bucket: [AppSettings.SHARED_BUCKET], or a package when limits are separate. */
    private val _budgets = MutableStateFlow(readBudgets())
    val budgets: StateFlow<Map<String, BudgetState>> = _budgets.asStateFlow()

    /** Per-day, per-app numbers for Insights. */
    private val _history = MutableStateFlow(readHistory())
    val history: StateFlow<Map<String, Map<String, DayStats>>> = _history.asStateFlow()

    private val _postModeUntil = MutableStateFlow(prefs.getLong("postModeUntil", 0))
    val postModeUntil: StateFlow<Long> = _postModeUntil.asStateFlow()

    private val _inspector = MutableStateFlow(false)
    val inspector: StateFlow<Boolean> = _inspector.asStateFlow()

    private val _debug = MutableStateFlow(DebugInfo())
    val debug: StateFlow<DebugInfo> = _debug.asStateFlow()

    private val _theme = MutableStateFlow(
        prefs.getString("theme", null)?.let { n -> ThemeMode.entries.firstOrNull { it.name == n } } ?: ThemeMode.DARK,
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _accent = MutableStateFlow(
        prefs.getString("accent", null)?.let { n -> Accent.entries.firstOrNull { it.name == n } } ?: Accent.LIME,
    )
    val accent: StateFlow<Accent> = _accent.asStateFlow()

    fun setTheme(t: ThemeMode) {
        _theme.value = t
        prefs.edit().putString("theme", t.name).apply()
    }

    fun setAccent(a: Accent) {
        _accent.value = a
        prefs.edit().putString("accent", a.name).apply()
    }

    private val _onboarded = MutableStateFlow(prefs.getBoolean("onboarded", false))
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    private val _parental = MutableStateFlow(
        Parental(
            enabled = prefs.getBoolean("parent_on", false),
            guard = prefs.getBoolean("parent_guard", true),
            failures = prefs.getInt("parent_failures", 0),
            lockedUntil = prefs.getLong("parent_locked_until", 0),
        ),
    )
    val parental: StateFlow<Parental> = _parental.asStateFlow()

    // ---- Onboarding ----

    /** Set while the user is in Android's Accessibility settings during onboarding. */
    var awaitingPermission: Boolean
        get() = prefs.getBoolean("awaiting_permission", false)
        set(v) = prefs.edit().putBoolean("awaiting_permission", v).apply()

    /** The guided tour of the main screen, shown once after onboarding. */
    private val _tourDone = MutableStateFlow(prefs.getBoolean("tour_done", false))
    val tourDone: StateFlow<Boolean> = _tourDone.asStateFlow()

    fun setTourDone(done: Boolean) {
        _tourDone.value = done
        prefs.edit().putBoolean("tour_done", done).apply()
    }

    fun finishOnboarding() {
        _onboarded.value = true
        clearDraft(prefs.edit().putBoolean("onboarded", true).putBoolean("awaiting_permission", false)).apply()
    }

    fun replayOnboarding() {
        _onboarded.value = false
        _tourDone.value = false
        clearDraft(prefs.edit().putBoolean("onboarded", false).putBoolean("tour_done", false)).apply()
    }

    /**
     * Onboarding answers and the current step, saved as they change, so leaving for Android's
     * settings (or the app being closed) picks up where it left off.
     */
    fun draft(key: String): String? = prefs.getString("ob_$key", null)

    fun setDraft(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove("ob_$key") else putString("ob_$key", value) }.apply()
    }

    private fun clearDraft(e: android.content.SharedPreferences.Editor) = e.apply {
        prefs.all.keys.filter { it.startsWith("ob_") }.forEach(::remove)
    }

    // ---- Parental controls ----

    fun setParentPin(pin: String, now: Long) {
        require(ParentPin.isValid(pin))
        val salt = ParentPin.newSalt()
        prefs.edit()
            .putString("parent_salt", salt)
            .putString("parent_hash", ParentPin.hash(pin, salt))
            .putBoolean("parent_on", true)
            .putInt("parent_failures", 0)
            .putLong("parent_locked_until", 0)
            .apply()
        _parental.value = _parental.value.copy(
            enabled = true,
            failures = 0,
            lockedUntil = 0,
            unlockedUntil = now + PARENT_UNLOCK_MS,
        )
    }

    fun checkParentPin(pin: String, now: Long): PinCheck {
        val p = _parental.value
        if (p.lockedUntil > now) return PinCheck.Wrong(p.lockedUntil)
        val salt = prefs.getString("parent_salt", null)
        val hash = prefs.getString("parent_hash", null)
        if (salt != null && hash != null && ParentPin.matches(pin, salt, hash)) {
            setParental(p.copy(failures = 0, lockedUntil = 0, unlockedUntil = now + PARENT_UNLOCK_MS))
            return PinCheck.Ok
        }
        val failures = p.failures + 1
        val lockout = ParentPin.lockoutMs(failures)
        val until = if (lockout > 0) now + lockout else 0
        setParental(p.copy(failures = failures, lockedUntil = until))
        return PinCheck.Wrong(until)
    }

    fun lockParent() = setParental(_parental.value.copy(unlockedUntil = 0))

    fun setParentGuard(on: Boolean) = setParental(_parental.value.copy(guard = on))

    /** Turns parental controls off. The UI only offers this while unlocked. */
    fun disableParental() {
        prefs.edit().remove("parent_salt").remove("parent_hash").apply()
        setParental(Parental())
    }

    /** A parent's change: applies at once, skipping Strict mode's wait, and drops any queued change. */
    fun changeSettingsAsParent(change: (AppSettings) -> AppSettings) {
        setSettings(change(_settings.value))
        setPending(null)
    }

    private fun setParental(p: Parental) {
        _parental.value = p
        prefs.edit()
            .putBoolean("parent_on", p.enabled)
            .putBoolean("parent_guard", p.guard)
            .putInt("parent_failures", p.failures)
            .putLong("parent_locked_until", p.lockedUntil)
            .apply()
    }

    /**
     * Applies a settings change. In Strict mode a loosening change is queued for
     * [STRICT_DELAY_MS] instead; returns false in that case.
     */
    fun changeSettings(now: Long, change: (AppSettings) -> AppSettings): Boolean {
        val cur = _settings.value
        val next = change(cur)
        if (next == cur) return true
        return if (cur.strict && next.loosens(cur)) {
            setPending(Pending(next, now + STRICT_DELAY_MS))
            false
        } else {
            setSettings(next)
            setPending(null)
            true
        }
    }

    fun applyPendingIfDue(now: Long) {
        val p = _pending.value ?: return
        if (now >= p.applyAt) {
            setSettings(p.settings)
            setPending(null)
        }
    }

    fun cancelPending() = setPending(null)

    /**
     * Leaving Strict mode needs the phrase while a loosening change is queued, or was queued in the
     * last [STRICT_DELAY_MS] (so cancelling the change first doesn't skip the phrase).
     */
    fun leavingStrictNeedsPhrase(now: Long): Boolean =
        _pending.value != null || now - prefs.getLong("lastQueuedAt", 0) < STRICT_DELAY_MS

    /** Turns Strict mode off and applies any queued change right away. */
    fun leaveStrict() {
        val base = _pending.value?.settings ?: _settings.value
        setSettings(base.copy(strict = false))
        setPending(null)
    }

    fun budget(bucket: String): BudgetState = _budgets.value[bucket] ?: BudgetState()

    fun updateBudget(bucket: String, change: (BudgetState) -> BudgetState) {
        val cur = budget(bucket)
        val next = change(cur)
        if (next == cur) return
        _budgets.value = _budgets.value + (bucket to next)
        prefs.edit().putString("budgets", encodeBudgets(_budgets.value)).apply()
    }

    /** Adds to one app's numbers for today (Insights). */
    fun record(pkg: String, change: (DayStats) -> DayStats) {
        _history.value = Insights.record(_history.value, LocalDate.now(), pkg, change)
        prefs.edit().putString("history", encodeHistory(_history.value)).apply()
    }

    /** Test tool: clears every budget and today's numbers. */
    fun resetBudget() {
        _budgets.value = emptyMap()
        _history.value = _history.value - LocalDate.now().toString()
        prefs.edit()
            .putString("budgets", encodeBudgets(_budgets.value))
            .putString("history", encodeHistory(_history.value))
            .apply()
    }

    private fun readBudgets(): Map<String, BudgetState> {
        prefs.getString("budgets", null)?.let { json ->
            return runCatching {
                val o = JSONObject(json)
                o.keys().asSequence().associateWith { k ->
                    val b = o.getJSONObject(k)
                    BudgetState(
                        day = b.optString("day"),
                        usedMs = b.optLong("used"),
                        sessionVideos = b.optInt("session"),
                        lastCountedAt = b.optLong("last"),
                        sessionLockedUntil = b.optLong("locked"),
                        videosToday = b.optInt("videos"),
                        blocksToday = b.optInt("blocks"),
                    )
                }
            }.getOrDefault(emptyMap())
        }
        // Older builds kept one shared budget in b_* keys.
        if (!prefs.contains("b_day")) return emptyMap()
        return mapOf(
            AppSettings.SHARED_BUCKET to BudgetState(
                day = prefs.getString("b_day", "") ?: "",
                usedMs = prefs.getLong("b_used", 0),
                sessionVideos = prefs.getInt("b_session", 0),
                lastCountedAt = prefs.getLong("b_last", 0),
                sessionLockedUntil = prefs.getLong("b_locked", 0),
                videosToday = prefs.getInt("b_videos", 0),
                blocksToday = prefs.getInt("b_blocks", 0),
            ),
        )
    }

    private fun encodeBudgets(m: Map<String, BudgetState>): String = JSONObject().apply {
        m.forEach { (k, b) ->
            put(
                k,
                JSONObject()
                    .put("day", b.day).put("used", b.usedMs).put("session", b.sessionVideos)
                    .put("last", b.lastCountedAt).put("locked", b.sessionLockedUntil)
                    .put("videos", b.videosToday).put("blocks", b.blocksToday),
            )
        }
    }.toString()

    private fun readHistory(): Map<String, Map<String, DayStats>> {
        val json = prefs.getString("history", null) ?: return emptyMap()
        return runCatching {
            val days = JSONObject(json)
            days.keys().asSequence().associateWith { d ->
                val apps = days.getJSONObject(d)
                apps.keys().asSequence().associateWith { p ->
                    val s = apps.getJSONObject(p)
                    DayStats(s.optLong("used"), s.optInt("videos"), s.optInt("blocks"), s.optInt("shared"))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeHistory(h: Map<String, Map<String, DayStats>>): String = JSONObject().apply {
        h.forEach { (d, apps) ->
            put(
                d,
                JSONObject().apply {
                    apps.forEach { (p, s) ->
                        put(p, JSONObject().put("used", s.usedMs).put("videos", s.videos).put("blocks", s.blocks).put("shared", s.shared))
                    }
                },
            )
        }
    }.toString()

    fun setPostModeUntil(until: Long) {
        _postModeUntil.value = until
        prefs.edit().putLong("postModeUntil", until).apply()
    }

    fun setInspector(on: Boolean) {
        _inspector.value = on
        if (!on) _debug.value = DebugInfo()
    }

    fun updateDebug(change: (DebugInfo) -> DebugInfo) {
        _debug.value = change(_debug.value)
    }

    private fun setSettings(s: AppSettings) {
        _settings.value = s
        writeSettings("", s)
    }

    private fun setPending(p: Pending?) {
        _pending.value = p
        if (p == null) {
            prefs.edit().putLong("p_at", 0).apply()
        } else {
            writeSettings("p_", p.settings)
            prefs.edit()
                .putLong("p_at", p.applyAt)
                .putLong("lastQueuedAt", p.applyAt - STRICT_DELAY_MS)
                .apply()
        }
    }

    private fun readPending(): Pending? {
        val at = prefs.getLong("p_at", 0)
        return if (at == 0L) null else Pending(readSettings("p_", AppSettings()), at)
    }

    private fun readSettings(prefix: String, d: AppSettings) = AppSettings(
        enabled = prefs.getBoolean(prefix + "enabled", d.enabled),
        limits = Limits(
            dailyMinutes = prefs.getInt(prefix + "dailyMinutes", d.limits.dailyMinutes),
            videosPerSession = prefs.getInt(prefix + "videosPerSession", d.limits.videosPerSession),
            resetMinutes = prefs.getInt(prefix + "resetMinutes", d.limits.resetMinutes),
        ),
        strict = prefs.getBoolean(prefix + "strict", d.strict),
        apps = prefs.getStringSet(prefix + "apps", null)?.toSet() ?: d.apps,
        sleep = SleepTime(
            enabled = prefs.getBoolean(prefix + "sleepOn", d.sleep.enabled),
            startMinute = prefs.getInt(prefix + "sleepStart", d.sleep.startMinute),
            endMinute = prefs.getInt(prefix + "sleepEnd", d.sleep.endMinute),
        ),
        blockSharedSwipe = prefs.getBoolean(prefix + "blockSharedSwipe", d.blockSharedSwipe),
        homeFeed = prefs.getString(prefix + "homeFeed", null)
            ?.let { name -> HomeFeedMode.entries.firstOrNull { it.name == name } }
            ?: d.homeFeed,
        separateLimits = prefs.getBoolean(prefix + "separate", d.separateLimits),
        appLimits = Rules.all.mapNotNull { r ->
            val k = prefix + "lim_" + r.pkg
            if (!prefs.contains(k + "_daily")) return@mapNotNull null
            r.pkg to Limits(prefs.getInt(k + "_daily", 20), prefs.getInt(k + "_row", 15), prefs.getInt(k + "_break", 30))
        }.toMap(),
    )

    private fun writeSettings(prefix: String, s: AppSettings) {
        prefs.edit()
            .putBoolean(prefix + "enabled", s.enabled)
            .putInt(prefix + "dailyMinutes", s.limits.dailyMinutes)
            .putInt(prefix + "videosPerSession", s.limits.videosPerSession)
            .putInt(prefix + "resetMinutes", s.limits.resetMinutes)
            .putBoolean(prefix + "strict", s.strict)
            .putStringSet(prefix + "apps", s.apps)
            .putBoolean(prefix + "sleepOn", s.sleep.enabled)
            .putInt(prefix + "sleepStart", s.sleep.startMinute)
            .putInt(prefix + "sleepEnd", s.sleep.endMinute)
            .putBoolean(prefix + "blockSharedSwipe", s.blockSharedSwipe)
            .putString(prefix + "homeFeed", s.homeFeed.name)
            .putBoolean(prefix + "separate", s.separateLimits)
            .apply {
                s.appLimits.forEach { (pkg, l) ->
                    val k = prefix + "lim_" + pkg
                    putInt(k + "_daily", l.dailyMinutes).putInt(k + "_row", l.videosPerSession).putInt(k + "_break", l.resetMinutes)
                }
            }
            .remove(prefix + "caughtUpStop")
            .remove(prefix + "swipeShared")
            .apply()
    }

    companion object {
        const val STRICT_DELAY_MS = 24 * 60 * 60 * 1000L
        const val STRICT_EXIT_PHRASE = "I am a loser"
        const val PARENT_UNLOCK_MS = 5 * 60_000L

        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }
    }
}
