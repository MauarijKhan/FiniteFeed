package com.feedcap.app.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feedcap.app.budget.Limits
import com.feedcap.app.budget.SleepTime
import com.feedcap.app.data.AppSettings
import com.feedcap.app.data.Store
import com.feedcap.app.detect.HomeFeedMode
import com.feedcap.app.detect.Rules
import com.feedcap.app.ui.CreatePin
import com.feedcap.app.ui.RecoveryCodeView
import com.feedcap.app.ui.design.BackChevron
import com.feedcap.app.ui.design.CheckRow
import com.feedcap.app.ui.design.Checkmark
import com.feedcap.app.ui.design.FcType
import com.feedcap.app.ui.design.IosSwitch
import com.feedcap.app.ui.design.NavRow
import com.feedcap.app.ui.design.PlainButton
import com.feedcap.app.ui.design.PrimaryButton
import com.feedcap.app.ui.design.Section
import com.feedcap.app.ui.design.Sheet
import com.feedcap.app.ui.design.TimeWheels
import com.feedcap.app.ui.design.ToggleRow
import com.feedcap.app.ui.design.fc
import com.feedcap.app.ui.isServiceEnabled
import com.feedcap.app.ui.minuteText
import com.feedcap.app.ui.openAppInfo
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ---- Choices ----

private enum class Step { WELCOME, WHO, USAGE, REVEAL, APPS, PLAN, SLEEP, PIN, PERMISSION, BATTERY, DONE }

private enum class Who { ME, CHILD }

private enum class Usage(val label: String, val hours: Float) {
    UNDER_30("Under 30 minutes", 0.4f),
    UP_TO_1("30 minutes to an hour", 0.75f),
    ONE_TWO("1 to 2 hours", 1.5f),
    TWO_THREE("2 to 3 hours", 2.5f),
    THREE_PLUS("More than 3 hours", 3.5f),
}

private enum class Plan(val title: String, val detail: String, val limits: Limits) {
    GENTLE("Gentle", "30 minutes a day, 20 in a row", Limits(30, 20, 30)),
    BALANCED("Balanced", "20 minutes a day, 15 in a row", Limits(20, 15, 30)),
    FOCUSED("Focused", "10 minutes a day, 8 in a row", Limits(10, 8, 30)),
    OFF("None", "No Reels or Shorts. Ones friends send still open.", Limits(0, 1, 30)),
}

private fun daysPerYear(hoursPerDay: Float) = hoursPerDay * 365f / 24f

private val AGGRESSIVE_OEMS = setOf(
    "xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "vivo", "realme",
    "oneplus", "samsung", "meizu", "asus", "tecno", "infinix",
)

private fun needsBatteryStep(context: Context): Boolean {
    if (Build.MANUFACTURER.lowercase() !in AGGRESSIVE_OEMS) return false
    val pm = context.getSystemService(PowerManager::class.java)
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}

/** A value that's saved to the onboarding draft whenever it changes. */
@Composable
private fun <T> rememberDraft(store: Store, key: String, initial: T, decode: (String) -> T?, encode: (T) -> String): MutableState<T> {
    val state = remember { mutableStateOf(store.draft(key)?.let(decode) ?: initial) }
    LaunchedEffect(state.value) { store.setDraft(key, encode(state.value)) }
    return state
}

private inline fun <reified E : Enum<E>> enumOrNull(s: String): E? = enumValues<E>().firstOrNull { it.name == s }

// ---- Flow ----

/** First-run setup. Progress is saved as it goes; choices apply before the permission step. */
@Composable
fun OnboardingFlow(store: Store) {
    val context = LocalContext.current
    val current = store.settings.value
    val parentAlreadySet = remember { store.parental.value.enabled }

    var step by rememberDraft(store, "step", Step.WELCOME, { enumOrNull<Step>(it) }, { it.name })
    var who by rememberDraft<Who?>(store, "who", null, { enumOrNull<Who>(it) }, { it?.name ?: "" })
    var usage by rememberDraft<Usage?>(store, "usage", null, { enumOrNull<Usage>(it) }, { it?.name ?: "" })
    var apps by rememberDraft(
        store, "apps", current.apps.ifEmpty { Rules.all.map { it.pkg }.toSet() },
        { s -> s.split(',').filter { it.isNotBlank() }.toSet() }, { it.joinToString(",") },
    )
    var plan by rememberDraft(store, "plan", Plan.BALANCED, { enumOrNull<Plan>(it) }, { it.name })
    var strict by rememberDraft(store, "strict", current.strict, { it.toBooleanStrictOrNull() }, { it.toString() })
    var sleep by rememberDraft(
        store, "sleep", SleepTime(enabled = true),
        { s -> s.split(',').takeIf { it.size == 3 }?.let { SleepTime(it[0].toBoolean(), it[1].toInt(), it[2].toInt()) } },
        { "${it.enabled},${it.startMinute},${it.endMinute}" },
    )
    var forward by remember { mutableStateOf(true) }
    var codeSaved by rememberDraft(store, "code_saved", false, { it.toBooleanStrictOrNull() }, { it.toString() })
    val parental by store.parental.collectAsState()
    val pinDone = parental.enabled
    val needBattery = remember { needsBatteryStep(context) }

    val steps = buildList {
        addAll(listOf(Step.WELCOME, Step.WHO, Step.USAGE, Step.REVEAL, Step.APPS, Step.PLAN, Step.SLEEP))
        if (who == Who.CHILD && !parentAlreadySet) add(Step.PIN)
        add(Step.PERMISSION)
        if (needBattery) add(Step.BATTERY)
        add(Step.DONE)
    }
    val index = steps.indexOf(step).coerceAtLeast(0)

    fun go(to: Step) {
        forward = steps.indexOf(to) > index
        step = to
    }
    fun next() {
        steps.getOrNull(index + 1)?.let(::go)
    }
    fun back() {
        steps.getOrNull(index - 1)?.let(::go)
    }

    fun applyChoices() {
        val update = { s: AppSettings ->
            s.copy(
                enabled = true,
                limits = plan.limits,
                apps = apps,
                sleep = sleep,
                strict = who == Who.ME && strict,
                homeFeed = HomeFeedMode.FOLLOWING,
            )
        }
        val p = store.parental.value
        if (p.enabled && p.unlocked(System.currentTimeMillis())) {
            store.changeSettingsAsParent(update)
        } else {
            store.changeSettings(System.currentTimeMillis(), update)
        }
    }

    BackHandler(enabled = index > 0 && step != Step.DONE) { back() }

    Column(
        Modifier
            .fillMaxSize()
            .background(fc.background)
            .systemBarsPadding(),
    ) {
        TopBar(showBack = index > 0 && step != Step.DONE, progress = index / (steps.size - 1f), onBack = ::back)
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 6 * dir } + fadeIn(tween(280)))
                    .togetherWith(slideOutHorizontally(tween(260)) { -it / 6 * dir } + fadeOut(tween(180)))
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "step",
        ) { s ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                when (s) {
                    Step.WELCOME -> Welcome()
                    Step.WHO -> WhoStep(who) { who = it }
                    Step.USAGE -> UsageStep(who, usage) { usage = it }
                    Step.REVEAL -> Reveal(who, usage ?: Usage.ONE_TWO)
                    Step.APPS -> AppsStep(apps) { apps = it }
                    Step.PLAN -> PlanStep(who, plan, { plan = it }, strict, { strict = it })
                    Step.SLEEP -> SleepStep(who, sleep) { sleep = it }
                    Step.PIN -> PinStep(store, pinDone, codeSaved) { codeSaved = true }
                    Step.PERMISSION -> PermissionStep(store, onGranted = ::next)
                    Step.BATTERY -> BatteryStep()
                    Step.DONE -> Done(who, plan, sleep, apps, pinDone)
                }
            }
        }

        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (step) {
                Step.WELCOME -> PrimaryButton("Get Started") { next() }
                Step.WHO -> PrimaryButton("Continue", enabled = who != null) {
                    // Children get an earlier bedtime by default.
                    if (who == Who.CHILD && sleep == SleepTime(enabled = true)) sleep = sleep.copy(startMinute = 21 * 60 + 30)
                    next()
                }
                Step.USAGE -> PrimaryButton("Continue", enabled = usage != null) { next() }
                Step.REVEAL -> PrimaryButton("Let's Change That") { next() }
                Step.APPS -> PrimaryButton("Continue", enabled = apps.isNotEmpty()) { next() }
                Step.PLAN -> PrimaryButton("Continue") { next() }
                Step.SLEEP -> PrimaryButton("Continue") {
                    applyChoices()
                    next()
                }
                Step.PIN -> if (codeSaved || !pinDone) PrimaryButton("Continue", enabled = pinDone && codeSaved) { next() }
                Step.PERMISSION -> {
                    val on = isServiceEnabled(context)
                    PrimaryButton(if (on) "Continue" else "Agree and Open Settings") {
                        if (on) {
                            next()
                        } else {
                            store.awaitingPermission = true
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                    if (!on) PlainButton("Not Now", color = fc.label2, onClick = ::next)
                }
                Step.BATTERY -> {
                    PrimaryButton("Open Battery Settings") {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                    PlainButton("Done", onClick = ::next)
                }
                Step.DONE -> PrimaryButton("Start Using Finite Feed") { store.finishOnboarding() }
            }
        }
    }
}

// ---- Pieces ----

@Composable
private fun TopBar(showBack: Boolean, progress: Float, onBack: () -> Unit) {
    val animated by animateFloatAsState(progress, tween(450, easing = FastOutSlowInEasing), label = "progress")
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .then(if (showBack) Modifier.clickable(onClickLabel = "Back", onClick = onBack) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (showBack) BackChevron(fc.accent)
        }
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(fc.fill),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated.coerceIn(0.03f, 1f))
                    .clip(CircleShape)
                    .background(fc.accent),
            )
        }
        Spacer(Modifier.width(44.dp))
    }
}

@Composable
private fun Title(text: String, sub: String? = null) {
    Column(Modifier.padding(horizontal = 24.dp).padding(top = 20.dp)) {
        Text(text, style = FcType.largeTitle, color = fc.label, modifier = Modifier.semantics { heading() })
        if (sub != null) {
            Spacer(Modifier.height(10.dp))
            Text(sub, style = FcType.body, color = fc.label2)
        }
    }
    Spacer(Modifier.height(8.dp))
}

// ---- Steps ----

@Composable
private fun Welcome() {
    Spacer(Modifier.height(24.dp))
    Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) { FeedIllustration() }
    Spacer(Modifier.height(16.dp))
    Title(
        "Keep the app.\nLose the scroll.",
        "Use Instagram and YouTube for friends, messages and search. Finite Feed puts a quiet cap on endless Reels and Shorts.",
    )
}

/** A phone whose feed scrolls up and fades out under a cap line. */
@Composable
private fun FeedIllustration() {
    val t = rememberInfiniteTransition(label = "feed")
    val offset by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "scroll")
    val body = fc.cell
    val card = fc.fill
    val line = fc.separator
    val accent = fc.accent
    val bg = fc.background
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width = 168.dp, height = 300.dp)) {
            val r = CornerRadius(34.dp.toPx())
            drawRoundRect(body, cornerRadius = r)
            drawRoundRect(line, cornerRadius = r, style = Stroke(1.dp.toPx()))
            val inset = 14.dp.toPx()
            val top = 34.dp.toPx()
            val bottom = size.height - 26.dp.toPx()
            val cap = top + (bottom - top) * 0.6f
            val h = 74.dp.toPx()
            val gap = 10.dp.toPx()
            clipRect(inset, top, size.width - inset, bottom) {
                for (i in -1..5) {
                    val y = top + i * (h + gap) - offset * (h + gap)
                    drawRoundRect(card, Offset(inset, y), Size(size.width - inset * 2, h), CornerRadius(14.dp.toPx()))
                }
                drawRect(
                    Brush.verticalGradient(listOf(body.copy(alpha = 0f), body), startY = cap, endY = cap + 46.dp.toPx()),
                    topLeft = Offset(0f, cap),
                    size = Size(size.width, bottom - cap),
                )
                drawRect(body, Offset(0f, cap + 46.dp.toPx()), Size(size.width, bottom))
            }
            drawLine(accent, Offset(inset, cap), Offset(size.width - inset, cap), 3.dp.toPx(), StrokeCap.Round)
            // Speaker slot
            drawRoundRect(bg, Offset(size.width / 2 - 22.dp.toPx(), 14.dp.toPx()), Size(44.dp.toPx(), 8.dp.toPx()), CornerRadius(4.dp.toPx()))
        }
        Text(
            "20 min, then stop",
            style = FcType.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = fc.onAccent,
            modifier = Modifier
                .padding(top = 118.dp)
                .clip(CircleShape)
                .background(fc.accent)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun WhoStep(who: Who?, onPick: (Who) -> Unit) {
    Title("Who is this phone for?")
    Section {
        row { CheckRow("Me", who == Who.ME, detail = "I want to spend less time scrolling.") { onPick(Who.ME) } }
        row {
            CheckRow(
                "My child",
                who == Who.CHILD,
                detail = "Set healthy limits and lock them with a parent PIN.",
            ) { onPick(Who.CHILD) }
        }
    }
}

@Composable
private fun UsageStep(who: Who?, usage: Usage?, onPick: (Usage) -> Unit) {
    Title(
        if (who == Who.CHILD) "How long do they spend on Reels and Shorts?" else "How long do you spend on Reels and Shorts?",
        "Per day. A rough guess is fine.",
    )
    Section(footer = "Digital Wellbeing in Android's Settings has the exact number.") {
        Usage.entries.forEach { u -> row { CheckRow(u.label, usage == u) { onPick(u) } } }
    }
}

@Composable
private fun Reveal(who: Who?, usage: Usage) {
    val now = daysPerYear(usage.hours)
    val capped = daysPerYear(20 / 60f)
    val back = (now - capped).coerceAtLeast(0f)
    val count = remember { Animatable(0f) }
    LaunchedEffect(usage) { count.animateTo(now, tween(1500, easing = FastOutSlowInEasing)) }

    Column(Modifier.padding(horizontal = 24.dp).padding(top = 36.dp)) {
        Text(if (who == Who.CHILD) "That adds up to" else "That's", style = FcType.title3, color = fc.label2)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                count.value.roundToInt().toString(),
                fontSize = 108.sp,
                lineHeight = 112.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-3).sp,
                color = fc.label,
            )
            Spacer(Modifier.width(10.dp))
            Text("days", style = FcType.title1, color = fc.label, modifier = Modifier.padding(bottom = 18.dp))
        }
        Text("a year, every year.", style = FcType.title3, color = fc.label2)
    }
    Spacer(Modifier.height(28.dp))
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fc.cell)
            .padding(20.dp),
    ) {
        CompareBar("Now", now, now, fc.orange)
        Spacer(Modifier.height(16.dp))
        CompareBar("With Finite Feed", capped, now, fc.accent)
        Spacer(Modifier.height(18.dp))
        Text(
            if (back >= 1f) "About ${back.roundToInt()} days back each year. For sleep, friends and everything else."
            else "Already pretty good. Finite Feed helps keep it that way.",
            style = FcType.subhead,
            color = fc.label,
        )
    }
}

@Composable
private fun CompareBar(label: String, value: Float, max: Float, color: androidx.compose.ui.graphics.Color) {
    val fraction = remember { Animatable(0f) }
    LaunchedEffect(value) { fraction.animateTo((value / max).coerceIn(0.03f, 1f), tween(1200, delayMillis = 400, easing = FastOutSlowInEasing)) }
    Row {
        Text(label, style = FcType.subhead, color = fc.label2, modifier = Modifier.weight(1f))
        Text("${value.roundToInt()} days", style = FcType.subhead.copy(fontWeight = FontWeight.SemiBold), color = fc.label)
    }
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(fc.fill)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.value).clip(CircleShape).background(color))
    }
}

@Composable
private fun AppsStep(apps: Set<String>, onChange: (Set<String>) -> Unit) {
    Title("Choose apps", "Only the endless parts are capped.")
    Section(footer = "Stories, messages, search, posting and reels friends send keep working.") {
        row {
            ToggleRow("Instagram", Rules.instagram.pkg in apps, subtitle = "Reels and the endless home feed") { on ->
                onChange(if (on) apps + Rules.instagram.pkg else apps - Rules.instagram.pkg)
            }
        }
        row {
            ToggleRow("YouTube", Rules.youtube.pkg in apps, subtitle = "Shorts") { on ->
                onChange(if (on) apps + Rules.youtube.pkg else apps - Rules.youtube.pkg)
            }
        }
    }
}

@Composable
private fun PlanStep(who: Who?, plan: Plan, onPick: (Plan) -> Unit, strict: Boolean, onStrict: (Boolean) -> Unit) {
    Title("Set a daily limit", "For Reels and Shorts together. You can change it any time.")
    val plans = if (who == Who.CHILD) Plan.entries else Plan.entries - Plan.OFF
    Section {
        plans.forEach { p ->
            row { CheckRow(p.title, plan == p, detail = p.detail, tag = if (p == Plan.BALANCED) "Recommended" else null) { onPick(p) } }
        }
    }
    if (who == Who.ME) {
        Section(footer = "Raising a limit then takes 24 hours, so a weak moment can't undo it.") {
            row { ToggleRow("Strict Mode", strict, onChange = onStrict) }
        }
    }
}

@Composable
private fun SleepStep(who: Who?, sleep: SleepTime, onChange: (SleepTime) -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<String?>(null) }
    Title(
        "Wind down at night",
        if (who == Who.CHILD) "No Reels or Shorts from bedtime until morning." else "No Reels or Shorts from bedtime until morning, so one more doesn't become an hour.",
    )
    Section(footer = if (sleep.enabled) "Messages and Stories still work." else null) {
        row { ToggleRow("Sleep Time", sleep.enabled) { onChange(sleep.copy(enabled = it)) } }
        if (sleep.enabled) {
            row { NavRow("Bedtime", minuteText(sleep.startMinute)) { editing = "start" } }
            row { NavRow("Wake up", minuteText(sleep.endMinute)) { editing = "end" } }
        }
    }
    editing?.let { which ->
        var value by remember(which) { mutableIntStateOf(if (which == "start") sleep.startMinute else sleep.endMinute) }
        Sheet(
            title = if (which == "start") "Bedtime" else "Wake Up",
            onDismiss = { editing = null },
            onDone = {
                onChange(if (which == "start") sleep.copy(startMinute = value) else sleep.copy(endMinute = value))
                editing = null
            },
        ) {
            Spacer(Modifier.height(12.dp))
            TimeWheels(value, DateFormat.is24HourFormat(context)) { value = it }
        }
    }
}

/** Create the PIN, then save the recovery code, then a short confirmation. */
@Composable
private fun PinStep(store: Store, pinDone: Boolean, codeSaved: Boolean, onCodeSaved: () -> Unit) {
    var code by remember { mutableStateOf<String?>(null) }
    when {
        !pinDone -> {
            Spacer(Modifier.height(32.dp))
            CreatePin { pin ->
                store.setParentPin(pin, System.currentTimeMillis())
                code = store.newRecoveryCode()
            }
        }
        !codeSaved -> {
            // If the app was closed before the code was saved, show a fresh one: the old one was never seen.
            val shown = code ?: remember { store.newRecoveryCode() }
            Spacer(Modifier.height(24.dp))
            RecoveryCodeView(shown, onDone = onCodeSaved)
        }
        else -> {
            Spacer(Modifier.height(64.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { DoneBadge() }
            Spacer(Modifier.height(24.dp))
            Text("PIN Set", style = FcType.title1, color = fc.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                "Finite Feed's settings are locked. It can't be switched off or uninstalled without the PIN. " +
                    "Forgot it? The recovery code resets it.",
                style = FcType.body,
                color = fc.label2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            )
        }
    }
}

@Composable
private fun PermissionStep(store: Store, onGranted: () -> Unit) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(isServiceEnabled(context)) }
    var help by remember { mutableStateOf(false) }
    val wasOn = remember { on }
    LaunchedEffect(Unit) {
        while (!on) {
            delay(600)
            on = isServiceEnabled(context)
        }
        store.awaitingPermission = false
        if (!wasOn) {
            delay(1000)
            onGranted()
        }
    }

    Title("Turn on Finite Feed", "It uses Android's Accessibility Service to notice Reels, Shorts and the home feed, and to step in at your limit.")
    Section {
        row { InfoRow("Sees", "Which screen is open in Instagram and YouTube") }
        row { InfoRow("Never", "Reads messages, records the screen or sends anything off this phone") }
        row { InfoRow("Keeps", "Only minutes and video counts, on this phone") }
    }
    Section(
        header = if (on) null else "In Settings",
        footer = if (on) null else "You can turn it off at any time in Settings.",
    ) {
        row { MockSettingsRow(on) }
        if (!on) {
            row { StepRow(1, "Open Downloaded apps (or Installed apps)") }
            row { StepRow(2, "Tap Finite Feed") }
            row { StepRow(3, "Turn on Use Finite Feed") }
        }
    }
    if (!on) {
        Spacer(Modifier.height(8.dp))
        PlainButton("Switch greyed out?", modifier = Modifier.padding(horizontal = 20.dp)) { help = !help }
        AnimatedVisibility(help, enter = expandVertically() + fadeIn()) {
            Column(Modifier.padding(horizontal = 32.dp)) {
                Text(
                    "Android blocks this for apps installed from a file. Open App Info, tap ⋮, choose Allow restricted settings, then try again.",
                    style = FcType.footnote,
                    color = fc.label2,
                )
                PlainButton("Open App Info") { openAppInfo(context) }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = FcType.body.copy(fontWeight = FontWeight.SemiBold), color = fc.label, modifier = Modifier.width(72.dp))
        Text(text, style = FcType.body, color = fc.label2, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StepRow(n: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(fc.fill), contentAlignment = Alignment.Center) {
            Text("$n", style = FcType.footnote.copy(fontWeight = FontWeight.SemiBold), color = fc.label)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = FcType.body, color = fc.label)
    }
}

/** What the user is looking for in Settings, with the switch gently flipping until it's really on. */
@Composable
private fun MockSettingsRow(granted: Boolean) {
    val t = rememberInfiniteTransition(label = "hint")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Restart), label = "phase")
    val checked = granted || phase > 0.5f
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Use Finite Feed", style = FcType.body, color = fc.label)
            Text(
                if (granted) "On" else "Waiting for you…",
                style = FcType.footnote,
                color = if (granted) fc.green else fc.label2,
            )
        }
        IosSwitch(checked)
    }
}

@Composable
private fun BatteryStep() {
    Title("Keep Finite Feed running", "Some phones close apps in the background to save battery. Let Finite Feed run so limits always apply.")
    Section {
        row { StepRow(1, "Tap Open Battery Settings") }
        row { StepRow(2, "Find Finite Feed") }
        row { StepRow(3, "Choose Unrestricted or Don't optimise") }
    }
}

@Composable
private fun Done(who: Who?, plan: Plan, sleep: SleepTime, apps: Set<String>, parentLocked: Boolean) {
    Spacer(Modifier.height(48.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { DoneBadge() }
    Spacer(Modifier.height(24.dp))
    Text(
        "You're All Set",
        style = FcType.largeTitle,
        color = fc.label,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading() },
    )
    Spacer(Modifier.height(6.dp))
    Text(
        if (who == Who.CHILD) "Here's what's set on this phone." else "Here's your plan.",
        style = FcType.body,
        color = fc.label2,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    val l = plan.limits
    Section {
        row { SummaryRow(if (l.dailyMinutes == 0) "No Reels or Shorts" else "${l.dailyMinutes} minutes a day") }
        if (l.dailyMinutes > 0) row { SummaryRow("${l.videosPerSession} in a row, then a ${l.resetMinutes}-minute break") }
        if (sleep.enabled) row { SummaryRow("Sleep time ${minuteText(sleep.startMinute)} to ${minuteText(sleep.endMinute)}") }
        if (Rules.instagram.pkg in apps) row { SummaryRow("Instagram's home feed opens Following") }
        row { SummaryRow("Stories, messages and shared reels always work") }
        if (parentLocked) row { SummaryRow("Locked with a parent PIN") }
    }
}

@Composable
private fun SummaryRow(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkmark(fc.accent, size = 16.dp)
        Spacer(Modifier.width(14.dp))
        Text(text, style = FcType.body, color = fc.label)
    }
}

@Composable
private fun DoneBadge() {
    val scale = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
    Box(
        Modifier
            .size(88.dp)
            .scale(scale.value)
            .clip(CircleShape)
            .background(fc.accent),
        contentAlignment = Alignment.Center,
    ) {
        Checkmark(fc.onAccent, size = 40.dp, stroke = 4.dp)
    }
}
