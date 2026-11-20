package com.feedcap.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feedcap.app.BuildConfig
import com.feedcap.app.budget.Budget
import com.feedcap.app.budget.BudgetState
import com.feedcap.app.budget.Limits
import com.feedcap.app.data.AppSettings
import com.feedcap.app.data.Store
import com.feedcap.app.detect.HomeFeedMode
import com.feedcap.app.detect.Rules
import com.feedcap.app.service.BlockerService
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.contentDescription
import com.feedcap.app.data.Accent
import com.feedcap.app.data.ThemeMode
import com.feedcap.app.ui.design.TabBar
import com.feedcap.app.ui.design.TabIcon
import com.feedcap.app.ui.design.TabItem
import com.feedcap.app.ui.design.accentColors
import androidx.compose.runtime.CompositionLocalProvider
import com.feedcap.app.detect.AppRule
import com.feedcap.app.insights.DayStats
import com.feedcap.app.insights.Insights
import com.feedcap.app.ui.design.SectionHeader
import com.feedcap.app.ui.design.SegmentRing
import com.feedcap.app.ui.design.WeekBars
import com.feedcap.app.ui.design.ActionRow
import com.feedcap.app.ui.design.BackChevron
import com.feedcap.app.ui.design.CheckRow
import com.feedcap.app.ui.design.FcType
import com.feedcap.app.ui.design.Hairline
import com.feedcap.app.ui.design.NavRow
import com.feedcap.app.ui.design.PageGutter
import com.feedcap.app.ui.design.PlainButton
import com.feedcap.app.ui.design.PrimaryButton
import com.feedcap.app.ui.design.Ring
import com.feedcap.app.ui.design.Section
import com.feedcap.app.ui.design.Sheet
import com.feedcap.app.ui.design.Stepper
import com.feedcap.app.ui.design.TimeWheels
import com.feedcap.app.ui.design.ToggleRow
import com.feedcap.app.ui.design.fc
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

private const val POST_MODE_MS = 10 * 60_000L

private enum class Tab(val item: TabItem) {
    TODAY(TabItem("Insights", TabIcon.RING)),
    LIMITS(TabItem("Limits", TabIcon.TIMER)),
    SETTINGS(TabItem("Settings", TabIcon.GEAR)),
}

/** Pages pushed inside the Settings tab. */
private enum class Page { HOME, PARENTAL, TEST, APPEARANCE }

private enum class Edit { DAILY, IN_A_ROW, BREAK, SLEEP, BEDTIME, WAKE, HOME_FEED, SHARED, STRICT_EXIT, CREATE_PIN, PERMISSION }

/** Everything the pages need, so they stay plain functions of state. */
private class Ui(
    val store: Store,
    val context: Context,
    val settings: AppSettings,
    val now: Long,
    val serviceOn: Boolean,
    val change: ((AppSettings) -> AppSettings) -> Unit,
    val guarded: (String, () -> Unit) -> Unit,
    /** Opens a sheet. [Ui.editFor] targets one app's limits (null = shared limits). */
    val editFor: (Edit, String?) -> Unit,
    val open: (Page) -> Unit,
    val showLimits: () -> Unit,
) {
    fun edit(e: Edit) = editFor(e, null)
}

@Composable
fun HomeScreen(store: Store) {
    val context = LocalContext.current
    val settings by store.settings.collectAsState()
    val parental by store.parental.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var serviceOn by remember { mutableStateOf(isServiceEnabled(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            serviceOn = isServiceEnabled(context)
            delay(1_000)
        }
    }

    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var editing by remember { mutableStateOf<Edit?>(null) }
    var editPkg by remember { mutableStateOf<String?>(null) }
    val tourDone by store.tourDone.collectAsState()
    val tour = remember { TourState() }
    var pinAction by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    fun guarded(reason: String, action: () -> Unit) {
        if (store.parental.value.unlocked(System.currentTimeMillis())) action() else pinAction = reason to action
    }

    fun change(update: (AppSettings) -> AppSettings) {
        if (store.parental.value.enabled) {
            // A parent's change applies at once; Strict mode's wait is for self-control.
            guarded("Enter the parent PIN to change settings.") { store.changeSettingsAsParent(update) }
            return
        }
        if (!store.changeSettings(System.currentTimeMillis(), update)) {
            toast(context, "Strict Mode: this change applies in 24 hours")
        }
    }

    val ui = Ui(
        store, context, settings, now, serviceOn,
        change = ::change,
        guarded = ::guarded,
        editFor = { e, pkg ->
            editPkg = pkg
            editing = e
        },
        open = { p ->
            // Pages live in the Settings tab.
            if (p != Page.HOME) tab = Tab.SETTINGS
            page = p
        },
        showLimits = { tab = Tab.LIMITS },
    )

    BackHandler(enabled = page != Page.HOME || tab != Tab.TODAY) {
        if (page != Page.HOME) page = Page.HOME else tab = Tab.TODAY
    }

    CompositionLocalProvider(LocalTour provides tour) {
    Box(Modifier.fillMaxSize().background(fc.background)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(160)).togetherWith(fadeOut(tween(120))) },
            label = "tab",
        ) { t ->
            when (t) {
                Tab.TODAY -> InsightsPage(ui)
                Tab.LIMITS -> LimitsPage(ui)
                Tab.SETTINGS -> AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val push = targetState != Page.HOME
                        val dir = if (push) 1 else -1
                        (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it * dir } + fadeIn(tween(200)))
                            .togetherWith(slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 3 * dir } + fadeOut(tween(300)))
                    },
                    label = "page",
                ) { p ->
                    when (p) {
                        Page.HOME -> SettingsPage(ui)
                        Page.PARENTAL -> ParentalPage(ui, parental)
                        Page.TEST -> TestPage(ui)
                        Page.APPEARANCE -> AppearancePage(ui)
                    }
                }
            }
        }
        TabBar(
            items = Tab.entries.map { it.item },
            selected = tab.ordinal,
            onSelect = { i ->
                val t = Tab.entries[i]
                // Tapping the current tab again pops back to its root, like iOS.
                if (t == tab || t != Tab.SETTINGS) page = Page.HOME
                tab = t
            },
            modifier = Modifier.align(Alignment.BottomCenter),
            itemModifier = { i -> Modifier.tourTarget("tab_${Tab.entries[i].name}") },
        )

        // First visit after onboarding: a short guided tour of this screen.
        if (!tourDone && tab == Tab.TODAY && page == Page.HOME) {
            TourOverlay(tour, TOUR_STEPS) { store.setTourDone(true) }
        }
    }
    }

    editing?.let { EditSheet(ui, it, editPkg) { editing = null } }

    pinAction?.let { (reason, action) ->
        ParentPinDialog(
            store = store,
            reason = reason,
            onDismiss = { pinAction = null },
            onUnlocked = {
                pinAction = null
                action()
            },
        )
    }
}

// ---- Page chrome ----

/**
 * A page with an iOS large title. Scrolling tucks it into a small centred title in a navigation
 * bar, which also holds the back button.
 */
@Composable
private fun PageScaffold(
    title: String,
    overline: String? = null,
    back: Pair<String, () -> Unit>? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val threshold = with(LocalDensity.current) { 52.dp.toPx() }
    val collapsed = scroll.value > threshold
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(180), label = "bar")

    Box(Modifier.fillMaxSize().background(fc.background)) {
        Column(
            Modifier
                .fillMaxSize()
                // The scrolling area ends at the tab bar, so nothing (or no tour stop) hides behind it.
                .navigationBarsPadding()
                .padding(bottom = 56.dp)
                .verticalScroll(scroll)
                .statusBarsPadding()
                .padding(top = 44.dp)
                .padding(bottom = 32.dp),
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                if (overline != null) {
                    Text(overline.uppercase(), style = FcType.footnote.copy(fontWeight = FontWeight.SemiBold), color = fc.label2)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = FcType.largeTitle,
                        color = fc.label,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    trailing?.invoke()
                }
            }
            content()
        }
        // Navigation bar
        Column(Modifier.fillMaxWidth().background(fc.background.copy(alpha = barAlpha))) {
            Box(Modifier.statusBarsPadding().fillMaxWidth().height(44.dp)) {
                if (back != null) {
                    Row(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = back.second)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BackChevron(fc.accent)
                        Spacer(Modifier.width(6.dp))
                        Text(back.first, style = FcType.body, color = fc.accent)
                    }
                }
                Text(
                    title,
                    style = FcType.headline,
                    color = fc.label.copy(alpha = barAlpha),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (barAlpha > 0f) Box(Modifier.fillMaxWidth().height(0.5.dp).background(fc.separator.copy(alpha = fc.separator.alpha * barAlpha)))
        }
    }
}

// ---- Insights tab ----

private val TOUR_STEPS = listOf(
    TourStep("status", "FeedCap is on", "Green means FeedCap is watching Instagram and YouTube. If it ever says Off, tap it to turn it back on."),
    TourStep("rings", "Your day at a glance", "How much of today's Reels and Shorts time is left. Instagram and YouTube each get their own colour."),
    TourStep("quick", "Change things right here", "Tap any of these to adjust a limit, sleep time or the home feed without leaving this screen."),
    TourStep("week", "Your week", "Minutes a day for each app. The dashed line is your daily limit."),
    TourStep("tab_LIMITS", "All your limits", "Separate limits for each app, sleep time and Instagram's home feed live here."),
    TourStep("tab_SETTINGS", "Settings", "Strict Mode, parental controls and the look of FeedCap."),
)

/** Per-app picture for today. */
private class AppDay(
    val rule: AppRule,
    val color: Color,
    val stats: DayStats,
    val limits: Limits,
    val budget: BudgetState,
    val enabled: Boolean,
)

@Composable
private fun seriesColor(i: Int): Color = when (i) {
    0 -> fc.accent
    // A calm second colour that works with any accent: the label colour, softened.
    else -> if (fc.accent == fc.label) fc.label2 else fc.label.copy(alpha = 0.82f)
}

@Composable
private fun InsightsPage(ui: Ui) {
    val s = ui.settings
    val store = ui.store
    val budgets by store.budgets.collectAsState()
    val history by store.history.collectAsState()
    val postModeUntil by store.postModeUntil.collectAsState()
    val parental by store.parental.collectAsState()
    val today = LocalDate.now()
    val sleepNow = s.sleep.block(ui.now, ZoneId.systemDefault())
    val paused = postModeUntil > ui.now

    val apps = Rules.all.mapIndexed { i, rule ->
        val limits = s.limitsFor(rule.pkg)
        AppDay(
            rule = rule,
            color = seriesColor(i),
            stats = Insights.day(history, today, rule.pkg),
            limits = limits,
            budget = Budget.normalize(budgets[s.bucketFor(rule.pkg)] ?: BudgetState(), ui.now, today.toString(), limits),
            enabled = rule.pkg in s.apps,
        )
    }

    PageScaffold(
        title = "Insights",
        overline = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
        trailing = {
            Box(Modifier.tourTarget("status")) { StatusPill(ui.serviceOn && s.enabled) { ui.edit(Edit.PERMISSION) } }
        },
    ) {
        Spacer(Modifier.height(16.dp))
        TodayCard(ui, apps, sleepNow?.until, postModeUntil)

        if (!ui.serviceOn) {
            Section(footer = "FeedCap needs this to notice Reels, Shorts and the home feed.") {
                row { ActionRow("Turn On FeedCap") { ui.edit(Edit.PERMISSION) } }
            }
        }
        if (parental.enabled) {
            val open = parental.unlocked(ui.now)
            Section(footer = if (open) null else "Changes need the parent PIN.") {
                row {
                    NavRow(
                        if (open) "Unlocked by a parent" else "Managed by a parent",
                        value = if (open) "Locks in ${clock((parental.unlockedUntil - ui.now) / 1000)}" else null,
                    ) { ui.open(Page.PARENTAL) }
                }
            }
        }

        // Quick settings: each opens its sheet right here.
        Box(Modifier.tourTarget("quick")) {
            Column {
                Section(header = "Quick Settings") {
                    if (s.separateLimits) {
                        apps.filter { it.enabled }.forEach { a ->
                            row { NavRow("${a.rule.label} Daily", minutesLabel(a.limits.dailyMinutes)) { ui.editFor(Edit.DAILY, a.rule.pkg) } }
                        }
                    } else {
                        row { NavRow("Daily Time", minutesLabel(s.limits.dailyMinutes)) { ui.edit(Edit.DAILY) } }
                        row { NavRow("In a Row", "${s.limits.videosPerSession}") { ui.edit(Edit.IN_A_ROW) } }
                    }
                    row {
                        NavRow(
                            "Sleep Time",
                            if (s.sleep.enabled) "${minuteText(s.sleep.startMinute)} – ${minuteText(s.sleep.endMinute)}" else "Off",
                        ) { ui.edit(Edit.SLEEP) }
                    }
                    row { NavRow("Home Feed", homeFeedLabel(s.homeFeed)) { ui.edit(Edit.HOME_FEED) } }
                }
            }
        }

        WeekCard(ui, history, apps)

        apps.filter { it.enabled }.forEach { a -> AppSection(ui, a, history, sleepNow?.until, paused) }

        Section(
            header = "Post Mode",
            footer = when {
                paused -> null
                s.strict -> "Not available in Strict Mode."
                sleepNow != null -> "Not available during sleep time."
                else -> "Pauses limits for 10 minutes so you can post or reply to a Reel."
            },
        ) {
            if (paused) {
                row { NavRow("Limits Paused", clock((postModeUntil - ui.now) / 1000), enabled = false) {} }
                row { ActionRow("Resume Limits Now") { store.setPostModeUntil(0) } }
            } else {
                row {
                    ActionRow("Pause for 10 Minutes", enabled = !s.strict && sleepNow == null) {
                        ui.guarded("Enter the parent PIN to pause limits.") {
                            store.setPostModeUntil(System.currentTimeMillis() + POST_MODE_MS)
                        }
                    }
                }
            }
        }
    }
}

private fun minutesLabel(m: Int) = if (m == 0) "None" else "$m min"

/** Today's rings: one shared ring split by app, or one ring per app (outer Instagram, inner YouTube). */
@Composable
private fun TodayCard(ui: Ui, apps: List<AppDay>, sleepUntil: Long?, postModeUntil: Long) {
    val s = ui.settings
    val shown = apps.filter { it.enabled }.ifEmpty { apps }
    val usedMin = shown.sumOf { it.stats.usedMs } / 60_000
    val paused = postModeUntil > ui.now
    val muted = sleepUntil != null || paused

    val (headline, detail) = when {
        paused -> "Paused" to "Limits are back in ${clock((postModeUntil - ui.now) / 1000)}"
        sleepUntil != null -> "Sleep time" to "Back at ${time(ui.context, sleepUntil)}"
        s.separateLimits -> {
            val done = shown.filter { appBlocked(it, ui.now) }
            when {
                done.size == shown.size -> "Done for today" to "Back tomorrow"
                done.isNotEmpty() -> "${done.joinToString { it.rule.surfaceName }} paused" to "Back at ${time(ui.context, nextOpen(done.first(), ui.now))}"
                else -> "On track" to "${usedMin} min used today"
            }
        }
        else -> {
            val b = shown.first().budget
            val l = s.limits
            when {
                l.dailyMinutes == 0 -> "No Reels or Shorts" to "Ones friends send still open"
                b.usedMs >= l.dailyMs -> "Done for today" to "Back tomorrow"
                b.sessionLockedUntil > ui.now -> "Taking a break" to "Back at ${time(ui.context, b.sessionLockedUntil)}"
                else -> "On track" to "${b.sessionVideos} of ${l.videosPerSession} in a row"
            }
        }
    }

    Column(
        Modifier
            .padding(horizontal = PageGutter)
            .fillMaxWidth()
            .tourTarget("rings")
            .clip(RoundedCornerShape(20.dp))
            .background(fc.cell)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                if (s.separateLimits) {
                    shown.forEachIndexed { i, a ->
                        val p by animateFloatAsState(fraction(a.stats.usedMs, a.limits), tween(900), label = "ring$i")
                        Ring(p, if (muted) fc.label3 else ringColor(a.color, p), fc.fill, size = (124 - i * 34).dp, stroke = 13.dp)
                    }
                } else {
                    val total = s.limits.dailyMs.toFloat().coerceAtLeast(1f)
                    val parts = shown.map { a ->
                        val f by animateFloatAsState((a.stats.usedMs / total).coerceIn(0f, 1f), tween(900), label = a.rule.pkg)
                        f to (if (muted) fc.label3 else a.color)
                    }
                    SegmentRing(parts, fc.fill, 124.dp, 13.dp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val big = if (s.separateLimits) "$usedMin" else "${minutesLeft(shown.first().budget, s.limits)}"
                    Text(big, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = fc.label)
                    Text(if (s.separateLimits) "min used" else "min left", style = FcType.caption, color = fc.label2)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, style = FcType.title3, color = fc.label)
                Spacer(Modifier.height(2.dp))
                Text(detail, style = FcType.subhead, color = fc.label2)
                Spacer(Modifier.height(14.dp))
                shown.forEach { a ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(a.color))
                        Spacer(Modifier.width(8.dp))
                        Text(a.rule.label, style = FcType.footnote, color = fc.label2, modifier = Modifier.weight(1f))
                        Text(
                            if (s.separateLimits) "${a.stats.minutes} / ${a.limits.dailyMinutes} min" else "${a.stats.minutes} min",
                            style = FcType.footnote.copy(fontWeight = FontWeight.SemiBold),
                            color = fc.label,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))
        Row {
            Stat("${shown.sumOf { it.stats.videos }}", "watched", Modifier.weight(1f))
            Stat("${shown.sumOf { it.stats.blocks }}", "stopped", Modifier.weight(1f))
            Stat("${shown.sumOf { it.stats.shared }}", "from friends", Modifier.weight(1f))
        }
    }
}

private fun fraction(usedMs: Long, l: Limits) = if (l.dailyMs == 0L) 1f else (usedMs.toFloat() / l.dailyMs).coerceIn(0f, 1f)

private fun minutesLeft(b: BudgetState, l: Limits) = (((l.dailyMs - b.usedMs).coerceAtLeast(0) + 59_999) / 60_000).toInt()

@Composable
private fun ringColor(base: Color, progress: Float) = when {
    progress >= 1f -> fc.red
    progress >= 0.85f -> fc.orange
    else -> base
}

private fun appBlocked(a: AppDay, now: Long) =
    a.limits.dailyMs == 0L || a.budget.usedMs >= a.limits.dailyMs || a.budget.sessionLockedUntil > now

private fun nextOpen(a: AppDay, now: Long) =
    if (a.budget.sessionLockedUntil > now) a.budget.sessionLockedUntil
    else LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = FcType.headline, color = fc.label)
        Text(label, style = FcType.caption, color = fc.label2)
    }
}

/** This week's minutes, stacked by app, with the daily limit as a dashed line. */
@Composable
private fun WeekCard(ui: Ui, history: Map<String, Map<String, DayStats>>, apps: List<AppDay>) {
    val s = ui.settings
    val days = Insights.week(LocalDate.now())
    val shown = apps.filter { it.enabled }.ifEmpty { apps }
    val pkgs = shown.map { it.rule.pkg }
    val avg = Insights.dailyAverageMinutes(history, days, pkgs)
    val total = Insights.total(history, days, pkgs)
    // One shared limit, or the sum of the per-app limits.
    val limit = if (s.separateLimits) shown.sumOf { it.limits.dailyMinutes } else s.limits.dailyMinutes

    SectionHeader("This Week")
    Column(
        Modifier
            .padding(horizontal = PageGutter)
            .fillMaxWidth()
            .tourTarget("week")
            .clip(RoundedCornerShape(12.dp))
            .background(fc.cell)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Daily average", style = FcType.footnote, color = fc.label2)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$avg", style = FcType.title1, color = fc.label)
                    Spacer(Modifier.width(4.dp))
                    Text("min", style = FcType.subhead, color = fc.label2, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("This week", style = FcType.footnote, color = fc.label2)
                Text(hoursMinutes(total.usedMs), style = FcType.headline, color = fc.label)
            }
        }
        Spacer(Modifier.height(16.dp))
        WeekBars(
            values = days.map { d -> shown.map { a -> Insights.day(history, d, a.rule.pkg).usedMs / 60_000f } },
            colors = shown.map { it.color },
            labels = days.map { it.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()) },
            highlight = days.lastIndex,
            limit = limit.toFloat().takeIf { it > 0 },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            shown.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(a.color))
                    Spacer(Modifier.width(6.dp))
                    Text(a.rule.label, style = FcType.caption, color = fc.label2)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(14.dp).height(1.dp).background(fc.label3))
                Spacer(Modifier.width(6.dp))
                Text("Limit", style = FcType.caption, color = fc.label2)
            }
        }
    }
}

private fun hoursMinutes(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

/** One app's numbers: today and this week. */
@Composable
private fun AppSection(ui: Ui, a: AppDay, history: Map<String, Map<String, DayStats>>, sleepUntil: Long?, paused: Boolean) {
    val week = Insights.total(history, Insights.week(LocalDate.now()), listOf(a.rule.pkg))
    val status = when {
        paused -> "Paused"
        sleepUntil != null -> "Sleep time"
        a.limits.dailyMinutes == 0 -> "Off"
        a.budget.usedMs >= a.limits.dailyMs -> "Done for today"
        a.budget.sessionLockedUntil > ui.now -> "Break until ${time(ui.context, a.budget.sessionLockedUntil)}"
        else -> "${a.budget.sessionVideos} of ${a.limits.videosPerSession} in a row"
    }
    Section(header = "${a.rule.label} ${a.rule.surfaceName}") {
        row { StatRow("Status", status, a.color) }
        row {
            StatRow(
                "Time Today",
                if (ui.settings.separateLimits) "${a.stats.minutes} of ${a.limits.dailyMinutes} min" else "${a.stats.minutes} min",
            )
        }
        row { StatRow("${a.rule.surfaceName} Watched", "${a.stats.videos}") }
        row { StatRow("Times Stopped", "${a.stats.blocks}") }
        row { StatRow("Sent by Friends", "${a.stats.shared}") }
        row { StatRow("This Week", hoursMinutes(week.usedMs)) }
    }
}

@Composable
private fun StatRow(label: String, value: String, dot: Color? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = FcType.body, color = fc.label, modifier = Modifier.weight(1f))
        Text(value, style = FcType.body, color = fc.label2)
    }
}

// ---- Limits tab ----

@Composable
private fun LimitsPage(ui: Ui) {
    val s = ui.settings
    PageScaffold(title = "Limits") {
        Section(
            header = "Reels and Shorts",
            footer = if (s.separateLimits) {
                "Instagram and YouTube each have their own time, cap and break."
            } else {
                "One budget for both. Stories, messages and reels friends send never count."
            },
        ) {
            row { ToggleRow("Separate Limits for Each App", s.separateLimits) { on -> ui.change { it.withSeparateLimits(on) } } }
            if (!s.separateLimits) {
                row { NavRow("Daily Time", minutesLabel(s.limits.dailyMinutes)) { ui.edit(Edit.DAILY) } }
                row { NavRow("In a Row", "${s.limits.videosPerSession}") { ui.edit(Edit.IN_A_ROW) } }
                row { NavRow("Then a Break Of", "${s.limits.resetMinutes} min") { ui.edit(Edit.BREAK) } }
            }
        }
        if (s.separateLimits) {
            Rules.all.filter { it.pkg in s.apps }.forEach { rule ->
                val l = s.limitsFor(rule.pkg)
                Section(header = "${rule.label} ${rule.surfaceName}") {
                    row { NavRow("Daily Time", minutesLabel(l.dailyMinutes)) { ui.editFor(Edit.DAILY, rule.pkg) } }
                    row { NavRow("In a Row", "${l.videosPerSession}") { ui.editFor(Edit.IN_A_ROW, rule.pkg) } }
                    row { NavRow("Then a Break Of", "${l.resetMinutes} min") { ui.editFor(Edit.BREAK, rule.pkg) } }
                }
            }
        }

        Section(
            header = "Sleep",
            footer = "No Reels, Shorts or feed scrolling overnight. Scrolling Instagram's feed takes you to your messages instead.",
        ) {
            row { ToggleRow("Sleep Time", s.sleep.enabled) { on -> ui.change { it.copy(sleep = it.sleep.copy(enabled = on)) } } }
            if (s.sleep.enabled) {
                row { NavRow("Bedtime", minuteText(s.sleep.startMinute)) { ui.edit(Edit.BEDTIME) } }
                row { NavRow("Wake Up", minuteText(s.sleep.endMinute)) { ui.edit(Edit.WAKE) } }
            }
        }

        Section(header = "Instagram") {
            row { NavRow("Home Feed", homeFeedLabel(s.homeFeed)) { ui.edit(Edit.HOME_FEED) } }
            row { NavRow("Reels Friends Send", if (s.blockSharedSwipe) "Just That One" else "Can Swipe On") { ui.edit(Edit.SHARED) } }
        }

        Section(header = "Apps", footer = "Only the endless parts are capped. Everything else works as normal.") {
            Rules.all.forEach { rule ->
                row {
                    ToggleRow(rule.label, rule.pkg in s.apps, subtitle = rule.surfaceName) { on ->
                        ui.change { it.copy(apps = if (on) it.apps + rule.pkg else it.apps - rule.pkg) }
                    }
                }
            }
        }
    }
}

// ---- Settings tab ----

@Composable
private fun SettingsPage(ui: Ui) {
    val s = ui.settings
    val store = ui.store
    val pending by store.pending.collectAsState()
    val parental by store.parental.collectAsState()
    val theme by store.theme.collectAsState()
    val accent by store.accent.collectAsState()

    PageScaffold(title = "Settings") {
        Section(
            header = "Control",
            footer = if (s.strict) "Loosening a limit waits 24 hours. Tightening applies straight away." else null,
        ) {
            row {
                ToggleRow("Strict Mode", s.strict, enabled = BuildConfig.PRO_UNLOCKED) { on ->
                    when {
                        on -> ui.change { it.copy(strict = true) }
                        parental.enabled -> ui.guarded("Enter the parent PIN to turn off Strict Mode.") { store.leaveStrict() }
                        store.leavingStrictNeedsPhrase(System.currentTimeMillis()) -> ui.edit(Edit.STRICT_EXIT)
                        else -> store.leaveStrict()
                    }
                }
            }
            pending?.let { p ->
                row { NavRow("Waiting Change", "${time(ui.context, p.applyAt)}, ${dayName(p.applyAt)}", enabled = false) {} }
                row {
                    ActionRow("Cancel Waiting Change", color = fc.red) {
                        ui.guarded("Enter the parent PIN to cancel this change.") { store.cancelPending() }
                    }
                }
            }
            row { NavRow("Parental Controls", if (parental.enabled) "On" else "Off") { ui.open(Page.PARENTAL) } }
        }

        Section(header = "Appearance") {
            row { NavRow("Theme", themeLabel(theme)) { ui.open(Page.APPEARANCE) } }
            row { AccentSwatches(accent) { store.setAccent(it) } }
        }

        Section(header = "FeedCap") {
            row { NavRow("Accessibility", if (ui.serviceOn) "On" else "Off") { ui.edit(Edit.PERMISSION) } }
            row { ToggleRow("Limits Active", s.enabled) { on -> ui.change { it.copy(enabled = on) } } }
            row {
                NavRow("Battery") {
                    ui.context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            if (BuildConfig.DEBUG) row { NavRow("Test Tools") { ui.open(Page.TEST) } }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "FeedCap ${BuildConfig.VERSION_NAME}",
            style = FcType.footnote,
            color = fc.label3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A row of colour dots; the chosen one gets a ring. */
@Composable
private fun AccentSwatches(selected: Accent, onPick: (Accent) -> Unit) {
    val dark = fc.isDark
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Accent", style = FcType.body, color = fc.label, modifier = Modifier.weight(1f))
        Accent.entries.forEach { a ->
            val color = accentColors(a, dark).first
            val chosen = a == selected
            Box(
                Modifier
                    .padding(start = 10.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (chosen) color.copy(alpha = 0.25f) else Color.Transparent)
                    .selectable(selected = chosen, role = androidx.compose.ui.semantics.Role.RadioButton) { onPick(a) }
                    .semantics { contentDescription = a.label },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(if (chosen) 20.dp else 24.dp).clip(CircleShape).background(color))
            }
        }
    }
}

private fun themeLabel(t: ThemeMode) = when (t) {
    ThemeMode.DARK -> "Dark"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.SYSTEM -> "Match Phone"
}

@Composable
private fun AppearancePage(ui: Ui) {
    val store = ui.store
    val theme by store.theme.collectAsState()
    PageScaffold(title = "Theme", back = "Settings" to { ui.open(Page.HOME) }) {
        Section(footer = "Dark is pure black: easiest on the eyes at night, and on OLED screens it saves battery.") {
            ThemeMode.entries.forEach { t ->
                row { CheckRow(themeLabel(t), theme == t) { store.setTheme(t) } }
            }
        }
    }
}

@Composable
private fun StatusPill(on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(fc.cell)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) fc.green else fc.red))
        Spacer(Modifier.width(7.dp))
        Text(if (on) "On" else "Off", style = FcType.subhead.copy(fontWeight = FontWeight.SemiBold), color = fc.label)
    }
}

private fun homeFeedLabel(m: HomeFeedMode) = when (m) {
    HomeFeedMode.FOLLOWING -> "Following"
    HomeFeedMode.CAUGHT_UP -> "Until Caught Up"
    HomeFeedMode.ALLOW -> "No Limit"
}

// ---- Parental ----

@Composable
private fun ParentalPage(ui: Ui, parental: com.feedcap.app.data.Parental) {
    val store = ui.store
    PageScaffold(title = "Parental Controls", back = "Settings" to { ui.open(Page.HOME) }) {
        if (!parental.enabled) {
            Text(
                "For a child's phone. A parent PIN locks FeedCap's settings, and stops FeedCap being switched off or uninstalled.",
                style = FcType.body,
                color = fc.label2,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Section {
                row { ActionRow("Set Up Parent PIN") { ui.edit(Edit.CREATE_PIN) } }
            }
        } else {
            val open = parental.unlocked(ui.now)
            Section(footer = if (open) "Settings are open until then. Lock now when you're done." else "Enter the PIN to change anything.") {
                row {
                    NavRow(
                        if (open) "Unlocked" else "Locked",
                        value = if (open) clock((parental.unlockedUntil - ui.now) / 1000) else null,
                        enabled = !open,
                    ) { ui.guarded("Enter the parent PIN to unlock.") {} }
                }
                if (open) row { ActionRow("Lock Now") { store.lockParent() } }
            }
            Section(footer = "Closes FeedCap's pages in Android's Settings, so it can't be switched off, force-stopped or uninstalled.") {
                row {
                    ToggleRow("Prevent Removal", parental.guard) { on ->
                        ui.guarded("Enter the parent PIN to change this.") { store.setParentGuard(on) }
                    }
                }
            }
            Section(footer = "Parents can also set Daily Time to None: no Reels or Shorts at all.") {
                row { NavRow("Change PIN") { ui.guarded("Enter your current PIN first.") { ui.edit(Edit.CREATE_PIN) } } }
            }
            Section {
                row {
                    ActionRow("Turn Off Parental Controls", color = fc.red) {
                        ui.guarded("Enter the parent PIN to turn off parental controls.") {
                            store.disableParental()
                            toast(ui.context, "Parental controls are off")
                        }
                    }
                }
                if (BuildConfig.DEBUG) row { ActionRow("Forgot PIN (Test Build)", color = fc.label2) { store.disableParental() } }
            }
        }
    }
}

// ---- Test tools ----

@Composable
private fun TestPage(ui: Ui) {
    val store = ui.store
    val inspector by store.inspector.collectAsState()
    val debug by store.debug.collectAsState()
    PageScaffold(title = "Test Tools", back = "Settings" to { ui.open(Page.HOME) }) {
        Section {
            row { ActionRow("Reset Today's Counters") { store.resetBudget() } }
            row { ActionRow("Replay Onboarding") { store.replayOnboarding() } }
        }
        Section(footer = "Open Instagram or YouTube, then come back to see what FeedCap detected.") {
            row { ToggleRow("Screen Inspector", inspector) { store.setInspector(it) } }
            if (inspector && debug.pkg.isNotEmpty()) {
                row { NavRow("App", debug.pkg.substringAfterLast('.'), enabled = false) {} }
                row { NavRow("Reels or Shorts", yesNo(debug.onSurface) + if (debug.freeVideo) ", shared" else "", enabled = false) {} }
                row { NavRow("DM Chat", yesNo(debug.onDm), enabled = false) {} }
                row { NavRow("Home Feed", yesNo(debug.onHomeFeed), enabled = false) {} }
            }
        }
        if (inspector && debug.events.isNotEmpty()) {
            Section(header = "Recent Events") {
                debug.events.forEach { e -> row { Text(e, style = FcType.footnote, color = fc.label, modifier = Modifier.padding(16.dp, 10.dp)) } }
            }
        }
        if (inspector && debug.ids.isNotEmpty()) {
            Section(header = "Visible View IDs") {
                row { Text(debug.ids.joinToString(", "), style = FcType.footnote, color = fc.label2, modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}

private fun yesNo(b: Boolean) = if (b) "Yes" else "No"

// ---- Sheets ----

@Composable
private fun EditSheet(ui: Ui, edit: Edit, pkg: String?, close: () -> Unit) {
    val s = ui.settings
    val parentMode = ui.store.parental.value.enabled
    // Limit sheets edit one app's limits (pkg) or the shared ones (null).
    val target = pkg?.takeIf { s.separateLimits }
    val l = if (target == null) s.limits else s.limitsFor(target)
    val appName = target?.let { p -> Rules.all.firstOrNull { it.pkg == p } }
    val prefix = appName?.let { "${it.label} " } ?: ""
    val what = appName?.surfaceName ?: "Reels and Shorts"
    when (edit) {
        Edit.DAILY -> StepperSheet(
            title = "${prefix}Daily Time",
            value = l.dailyMinutes,
            range = if (parentMode) 0..120 else 5..120,
            step = 5,
            presets = listOf(10, 20, 30, 45),
            format = { if (it == 0) "None" to "no $what" else "$it" to "minutes a day" },
            footer = if (target == null) "For Reels and Shorts together." else "For $what only.",
            onDismiss = close,
        ) { v -> ui.change { it.withLimits(target) { x -> x.copy(dailyMinutes = v) } } }
        Edit.IN_A_ROW -> StepperSheet(
            title = "${prefix}In a Row",
            value = l.videosPerSession,
            range = 1..50,
            step = 1,
            presets = listOf(5, 10, 15, 25),
            format = { "$it" to "videos, then a break" },
            footer = "Swiping to a new one counts as one.",
            onDismiss = close,
        ) { v -> ui.change { it.withLimits(target) { x -> x.copy(videosPerSession = v) } } }
        Edit.BREAK -> StepperSheet(
            title = "${prefix}Break",
            value = l.resetMinutes,
            range = 10..120,
            step = 5,
            presets = listOf(15, 30, 60),
            format = { "$it" to "minutes" },
            footer = "After the cap, $what reopen once this much time has passed.",
            onDismiss = close,
        ) { v -> ui.change { it.withLimits(target) { x -> x.copy(resetMinutes = v) } } }
        Edit.SLEEP -> Sheet(title = "Sleep Time", onDismiss = close) {
            Section(footer = "No Reels, Shorts or feed scrolling overnight. Messages still work.") {
                row { ToggleRow("Sleep Time", s.sleep.enabled) { on -> ui.change { it.copy(sleep = it.sleep.copy(enabled = on)) } } }
                if (s.sleep.enabled) {
                    row { NavRow("Bedtime", minuteText(s.sleep.startMinute)) { ui.edit(Edit.BEDTIME) } }
                    row { NavRow("Wake Up", minuteText(s.sleep.endMinute)) { ui.edit(Edit.WAKE) } }
                }
            }
        }
        Edit.BEDTIME, Edit.WAKE -> {
            val start = edit == Edit.BEDTIME
            var value by remember { mutableIntStateOf(if (start) s.sleep.startMinute else s.sleep.endMinute) }
            Sheet(
                title = if (start) "Bedtime" else "Wake Up",
                onDismiss = close,
                onDone = {
                    ui.change { it.copy(sleep = if (start) it.sleep.copy(startMinute = value) else it.sleep.copy(endMinute = value)) }
                    close()
                },
            ) {
                Spacer(Modifier.height(12.dp))
                TimeWheels(value, DateFormat.is24HourFormat(ui.context)) { value = it }
            }
        }
        Edit.HOME_FEED -> Sheet(title = "Home Feed", onDismiss = close) {
            Section(footer = "Stories, messages and the other tabs always work.") {
                listOf(
                    Triple(HomeFeedMode.FOLLOWING, "Following", "Scrolling the main feed opens Following: only people you follow, newest first."),
                    Triple(HomeFeedMode.CAUGHT_UP, "Until Caught Up", "Scroll the main feed, but not into the endless suggested posts."),
                    Triple(HomeFeedMode.ALLOW, "No Limit", "The main feed works as normal."),
                ).forEach { (mode, title, detail) ->
                    row {
                        CheckRow(title, s.homeFeed == mode, detail = detail) {
                            ui.change { it.copy(homeFeed = mode) }
                            close()
                        }
                    }
                }
            }
        }
        Edit.SHARED -> Sheet(title = "Reels Friends Send", onDismiss = close) {
            Section(footer = "Reels and Shorts from DMs and links always open and never count, even at a limit or at night.") {
                row {
                    CheckRow("Can Swipe On", !s.blockSharedSwipe, detail = "Swiping past it counts toward your limits.") {
                        ui.change { it.copy(blockSharedSwipe = false) }
                        close()
                    }
                }
                row {
                    CheckRow("Just That One", s.blockSharedSwipe, detail = "Swiping past it takes you back to the chat.") {
                        ui.change { it.copy(blockSharedSwipe = true) }
                        close()
                    }
                }
            }
        }
        Edit.STRICT_EXIT -> StrictExitSheet(ui, close)
        Edit.CREATE_PIN -> Sheet(title = "", onDismiss = close, doneLabel = "Cancel") {
            Spacer(Modifier.height(8.dp))
            CreatePin { pin ->
                ui.store.setParentPin(pin, System.currentTimeMillis())
                toast(ui.context, "Parent PIN set")
                close()
            }
        }
        Edit.PERMISSION -> PermissionSheet(ui, close)
    }
}

@Composable
private fun StepperSheet(
    title: String,
    value: Int,
    range: IntRange,
    step: Int,
    presets: List<Int>,
    format: (Int) -> Pair<String, String>,
    footer: String,
    onDismiss: () -> Unit,
    onDone: (Int) -> Unit,
) {
    var v by remember { mutableIntStateOf(value.coerceIn(range)) }
    Sheet(
        title = title,
        onDismiss = onDismiss,
        onDone = {
            if (v != value) onDone(v)
            onDismiss()
        },
    ) {
        val (big, unit) = format(v)
        Spacer(Modifier.height(28.dp))
        Text(
            big,
            fontSize = 64.sp,
            lineHeight = 68.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.5).sp,
            color = fc.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(unit, style = FcType.callout, color = fc.label2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Stepper(
                onMinus = { v = (v - step).coerceIn(range) },
                onPlus = { v = (v + step).coerceIn(range) },
                minusEnabled = v > range.first,
                plusEnabled = v < range.last,
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            presets.filter { it in range }.forEach { p ->
                val chosen = p == v
                Text(
                    "$p",
                    style = FcType.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = if (chosen) fc.onAccent else fc.label,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(64.dp)
                        .clip(CircleShape)
                        .background(if (chosen) fc.accent else fc.cell)
                        .clickable { v = p }
                        .padding(vertical = 9.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            footer,
            style = FcType.footnote,
            color = fc.label2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        )
    }
}

@Composable
private fun StrictExitSheet(ui: Ui, close: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim().equals(Store.STRICT_EXIT_PHRASE, ignoreCase = true)
    val pending = ui.store.pending.value
    Sheet(title = "Turn Off Strict Mode", onDismiss = close, doneLabel = "Cancel") {
        Text(
            if (pending != null) "A change is waiting. Turning off Strict Mode applies it now."
            else "You queued a change in the last 24 hours.",
            style = FcType.body,
            color = fc.label2,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        Text(
            "Type “${Store.STRICT_EXIT_PHRASE}” to continue.",
            style = FcType.body,
            color = fc.label,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .padding(horizontal = PageGutter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(fc.cell)
                .padding(horizontal = 16.dp, vertical = 15.dp),
        ) {
            if (typed.isEmpty()) Text(Store.STRICT_EXIT_PHRASE, style = FcType.body, color = fc.label3)
            BasicTextField(
                value = typed,
                onValueChange = { typed = it },
                singleLine = true,
                textStyle = FcType.body.copy(color = fc.label),
                cursorBrush = SolidColor(fc.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Turn Off Strict Mode", enabled = matches, modifier = Modifier.padding(horizontal = PageGutter)) {
            ui.store.leaveStrict()
            toast(ui.context, "Strict Mode is off")
            close()
        }
    }
}

@Composable
private fun PermissionSheet(ui: Ui, close: () -> Unit) {
    Sheet(title = "Accessibility", onDismiss = close) {
        Text(
            if (ui.serviceOn) "FeedCap is on and watching for Reels, Shorts and the home feed."
            else "FeedCap uses Android's Accessibility Service to notice Reels, Shorts and the home feed, and to step in at your limit.",
            style = FcType.body,
            color = fc.label2,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        Section {
            row { DisclosureRow("Sees", "Which screen is open in Instagram and YouTube") }
            row { DisclosureRow("Never", "Reads messages, records the screen or sends anything off this phone") }
            row { DisclosureRow("Keeps", "Only minutes and video counts, on this phone") }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            if (ui.serviceOn) "Open Accessibility Settings" else "Agree and Open Settings",
            modifier = Modifier.padding(horizontal = PageGutter),
        ) {
            ui.context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            close()
        }
        if (!ui.serviceOn) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PlainButton("Switch Greyed Out? Open App Info") { openAppInfo(ui.context) }
            }
        }
    }
}

@Composable
private fun DisclosureRow(label: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = FcType.body.copy(fontWeight = FontWeight.SemiBold), color = fc.label, modifier = Modifier.width(72.dp))
        Text(text, style = FcType.body, color = fc.label2, modifier = Modifier.weight(1f))
    }
}

// ---- Helpers ----

private fun clock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return "${s / 60}:${"%02d".format(s % 60)}"
}

internal fun isServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val me = ComponentName(context, BlockerService::class.java).flattenToString()
    return enabled.split(':').any { it.equals(me, ignoreCase = true) }
}

internal fun openAppInfo(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}

private fun time(context: Context, millis: Long): String = DateFormat.getTimeFormat(context).format(Date(millis))

private fun dayName(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("EEE"))

internal fun minuteText(minute: Int): String =
    LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
