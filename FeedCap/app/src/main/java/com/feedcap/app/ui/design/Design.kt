package com.feedcap.app.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feedcap.app.data.Accent
import com.feedcap.app.data.ThemeMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * FeedCap's design system, after Apple's Human Interface Guidelines: system grouped backgrounds,
 * inset grouped lists, large titles, one quiet accent, generous space. Light and dark.
 */

// ---- Colour ----

@Immutable
data class FcColors(
    val background: Color,
    val cell: Color,
    val cellPressed: Color,
    val elevated: Color,
    val separator: Color,
    val label: Color,
    val label2: Color,
    val label3: Color,
    val fill: Color,
    val accent: Color,
    val onAccent: Color,
    val green: Color,
    val orange: Color,
    val red: Color,
    val isDark: Boolean,
)

private val Light = FcColors(
    background = Color(0xFFF2F2F7),
    cell = Color(0xFFFFFFFF),
    cellPressed = Color(0xFFE5E5EA),
    elevated = Color(0xFFFFFFFF),
    separator = Color(0x4A3C3C43),
    label = Color(0xFF000000),
    label2 = Color(0x993C3C43),
    label3 = Color(0x4D3C3C43),
    fill = Color(0x33787880),
    accent = Color(0xFF5856D6),
    onAccent = Color(0xFFFFFFFF),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    red = Color(0xFFFF3B30),
    isDark = false,
)

private val Dark = FcColors(
    background = Color(0xFF000000),
    cell = Color(0xFF1C1C1E),
    cellPressed = Color(0xFF2C2C2E),
    elevated = Color(0xFF1C1C1E),
    separator = Color(0x99545458),
    label = Color(0xFFFFFFFF),
    label2 = Color(0x99EBEBF5),
    label3 = Color(0x4DEBEBF5),
    fill = Color(0x5C787880),
    accent = Color(0xFF5E5CE6),
    onAccent = Color(0xFFFFFFFF),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    red = Color(0xFFFF453A),
    isDark = true,
)

val LocalFc = staticCompositionLocalOf { Light }

/** Shorthand for the current colours. */
val fc: FcColors @Composable get() = LocalFc.current

// ---- Type (iOS text styles) ----

object FcType {
    val largeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    val title1 = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    val title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    val title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp)
    val callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.3).sp)
    val subhead = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.1).sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
}

/** Accent and the text colour that sits on it: bright on black, deeper on light backgrounds. */
fun accentColors(accent: Accent, dark: Boolean): Pair<Color, Color> = when (accent) {
    Accent.LIME -> if (dark) Color(0xFFD0FD3E) to Color.Black else Color(0xFF4F8A00) to Color.White
    Accent.EMBER -> if (dark) Color(0xFFFF9F0A) to Color.Black else Color(0xFFD86A00) to Color.White
    Accent.SKY -> if (dark) Color(0xFF64D2FF) to Color.Black else Color(0xFF0071E3) to Color.White
    Accent.ROSE -> if (dark) Color(0xFFFF6482) to Color.Black else Color(0xFFE0284F) to Color.White
    Accent.MONO -> if (dark) Color(0xFFFFFFFF) to Color.Black else Color(0xFF000000) to Color.White
}

@Composable
fun FcTheme(mode: ThemeMode = ThemeMode.DARK, accent: Accent = Accent.LIME, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val (tint, onTint) = accentColors(accent, dark)
    val c = (if (dark) Dark else Light).copy(accent = tint, onAccent = onTint)
    val scheme = if (c.isDark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.background, surface = c.cell,
            onSurface = c.label, onSurfaceVariant = c.label2, surfaceContainerHigh = c.fill, error = c.red,
            surfaceContainerLow = c.elevated, outline = c.separator,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.background, surface = c.cell,
            onSurface = c.label, onSurfaceVariant = c.label2, surfaceContainerHigh = c.fill, error = c.red,
            surfaceContainerLow = c.elevated, outline = c.separator,
        )
    }
    CompositionLocalProvider(LocalFc provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

// ---- Layout ----

val PageGutter = 16.dp
private val CellRadius = 12.dp

/** A section header in the iOS style: small, uppercase, grey, inset to the cell text. */
@Composable
fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = FcType.footnote,
        color = fc.label2,
        modifier = Modifier.padding(start = PageGutter + 16.dp, end = PageGutter + 16.dp, top = 22.dp, bottom = 7.dp),
    )
}

@Composable
fun SectionFooter(text: String) {
    Text(
        text,
        style = FcType.footnote,
        color = fc.label2,
        modifier = Modifier.padding(start = PageGutter + 16.dp, end = PageGutter + 16.dp, top = 7.dp),
    )
}

/** Collects rows for a [Section]; separators go between them automatically. */
class SectionScope {
    internal val rows = mutableListOf<@Composable () -> Unit>()
    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/** An inset grouped list section: header, rounded cell group with hairline separators, footer. */
@Composable
fun Section(header: String? = null, footer: String? = null, build: SectionScope.() -> Unit) {
    val scope = SectionScope().apply(build)
    if (header != null) SectionHeader(header) else Spacer(Modifier.height(22.dp))
    Column(
        Modifier
            .padding(horizontal = PageGutter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(CellRadius))
            .background(fc.cell),
    ) {
        scope.rows.forEachIndexed { i, row ->
            if (i > 0) Hairline(start = 16.dp)
            row()
        }
    }
    if (footer != null) SectionFooter(footer)
}

@Composable
fun Hairline(start: Dp = 0.dp) {
    Box(
        Modifier
            .padding(start = start)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(fc.separator),
    )
}

/** Base row: 52dp tall minimum, 16dp padding, pressed highlight. */
@Composable
fun Row44(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val base = if (onClick != null) {
        modifier.clickable(interactionSource = source, indication = null, onClick = onClick)
    } else {
        modifier
    }
    Row(
        base
            .fillMaxWidth()
            .background(if (pressed) fc.cellPressed else Color.Transparent)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowTitles(title: String, subtitle: String?, titleColor: Color, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = FcType.body, color = titleColor)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = FcType.footnote, color = fc.label2)
        }
    }
}

/** Title on the left, grey value and a chevron on the right. Opens something. */
@Composable
fun NavRow(title: String, value: String? = null, subtitle: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row44(onClick = if (enabled) onClick else null) {
        RowTitles(title, subtitle, if (enabled) fc.label else fc.label3, Modifier.weight(1f))
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = FcType.body, color = fc.label2)
        }
        Spacer(Modifier.width(10.dp))
        Chevron()
    }
}

/** Title and an iOS switch. The whole row toggles. */
@Composable
fun ToggleRow(title: String, checked: Boolean, subtitle: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowTitles(title, subtitle, if (enabled) fc.label else fc.label3, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        IosSwitch(checked, enabled)
    }
}

/** A tappable line of tinted text: an action, like "Reset" or "Turn off". */
@Composable
fun ActionRow(title: String, color: Color = fc.accent, enabled: Boolean = true, onClick: () -> Unit) {
    Row44(onClick = if (enabled) onClick else null) {
        Text(title, style = FcType.body, color = if (enabled) color else fc.label3)
    }
}

/** A row of an iOS selection list: title (and detail), with a checkmark when chosen. */
@Composable
fun CheckRow(title: String, selected: Boolean, detail: String? = null, tag: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = FcType.body, color = fc.label)
                if (tag != null) {
                    Spacer(Modifier.width(8.dp))
                    Capsule(tag, fc.accent.copy(alpha = 0.14f), fc.accent)
                }
            }
            if (detail != null) {
                Spacer(Modifier.height(2.dp))
                Text(detail, style = FcType.footnote, color = fc.label2)
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (selected) Checkmark(fc.accent)
        }
    }
}

@Composable
fun Capsule(text: String, background: Color, color: Color) {
    Text(
        text,
        style = FcType.caption.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// ---- Glyphs ----

@Composable
fun Chevron(color: Color = fc.label3, size: Dp = 13.dp) {
    Canvas(Modifier.size(width = size * 0.6f, height = size)) {
        val p = Path().apply {
            moveTo(0f, 0f)
            lineTo(this@Canvas.size.width, this@Canvas.size.height / 2)
            lineTo(0f, this@Canvas.size.height)
        }
        drawPath(p, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun BackChevron(color: Color) {
    Canvas(Modifier.size(width = 11.dp, height = 19.dp)) {
        val p = Path().apply {
            moveTo(size.width, 0f)
            lineTo(0f, size.height / 2)
            lineTo(size.width, size.height)
        }
        drawPath(p, color, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun Checkmark(color: Color, size: Dp = 16.dp, stroke: Dp = 2.4.dp) {
    Canvas(Modifier.size(size)) {
        val p = Path().apply {
            moveTo(this@Canvas.size.width * 0.12f, this@Canvas.size.height * 0.55f)
            lineTo(this@Canvas.size.width * 0.4f, this@Canvas.size.height * 0.82f)
            lineTo(this@Canvas.size.width * 0.9f, this@Canvas.size.height * 0.2f)
        }
        drawPath(p, color, style = Stroke(stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ---- Controls ----

/** The iOS switch: 51×31, accent-coloured when on. */
@Composable
fun IosSwitch(checked: Boolean, enabled: Boolean = true) {
    val track by animateColorAsState(if (checked) fc.accent else fc.fill, label = "track")
    // A bright accent (lime, white) gets a dark knob so it stays visible.
    val knob = if (checked && fc.onAccent == Color.Black) Color.Black else Color.White
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.75f, stiffness = 700f), label = "knob")
    Box(
        Modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(if (enabled) track else track.copy(alpha = track.alpha * 0.5f)),
    ) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(27.dp)
                .clip(CircleShape)
                .background(knob),
        )
    }
}

// ---- Tab bar ----

enum class TabIcon { RING, TIMER, GEAR }

/** A thin-line tab icon, drawn so it matches the type weight. */
@Composable
fun TabGlyph(icon: TabIcon, color: Color, selected: Boolean) {
    Canvas(Modifier.size(26.dp)) {
        val w = (if (selected) 2.4.dp else 1.8.dp).toPx()
        val c = Offset(size.width / 2, size.height / 2)
        when (icon) {
            TabIcon.RING -> {
                val r = size.minDimension / 2 - w
                drawCircle(color.copy(alpha = 0.35f), r, c, style = Stroke(w))
                drawArc(
                    color, -90f, 250f, false,
                    Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2),
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
            TabIcon.TIMER -> {
                val r = size.minDimension / 2 - w * 1.6f
                val center = Offset(c.x, c.y + w * 0.8f)
                drawCircle(color, r, center, style = Stroke(w))
                drawLine(color, Offset(c.x - r * 0.35f, center.y - r - w * 1.4f), Offset(c.x + r * 0.35f, center.y - r - w * 1.4f), w, StrokeCap.Round)
                drawLine(color, center, Offset(center.x, center.y - r * 0.55f), w, StrokeCap.Round)
                drawLine(color, center, Offset(center.x + r * 0.4f, center.y + r * 0.2f), w, StrokeCap.Round)
            }
            TabIcon.GEAR -> {
                val outer = size.minDimension / 2 - w * 0.6f
                val inner = outer * 0.62f
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    drawLine(
                        color,
                        Offset(c.x + (inner * cos(a)).toFloat(), c.y + (inner * sin(a)).toFloat()),
                        Offset(c.x + (outer * cos(a)).toFloat(), c.y + (outer * sin(a)).toFloat()),
                        w * 1.5f,
                        StrokeCap.Round,
                    )
                }
                drawCircle(color, inner, c, style = Stroke(w))
                drawCircle(color, inner * 0.38f, c, style = Stroke(w))
            }
        }
    }
}

data class TabItem(val label: String, val icon: TabIcon)

/** The iOS tab bar: hairline on top, icon over label, accent when selected. */
@Composable
fun TabBar(
    items: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemModifier: (Int) -> Modifier = { Modifier },
) {
    Column(modifier.fillMaxWidth().background(fc.background)) {
        Hairline()
        Row(Modifier.navigationBarsPadding().fillMaxWidth().height(56.dp)) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                val color by animateColorAsState(if (on) fc.accent else fc.label3, label = "tab")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(itemModifier(i))
                        .selectable(
                            selected = on,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    TabGlyph(item.icon, color, on)
                    Spacer(Modifier.height(3.dp))
                    Text(item.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color)
                }
            }
        }
    }
}

/** Filled, rounded, full-width: the one main action on a screen. */
@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    !enabled -> fc.fill
                    pressed -> fc.accent.copy(alpha = 0.8f)
                    else -> fc.accent
                },
            )
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = FcType.headline, color = if (enabled) fc.onAccent else fc.label3)
    }
}

/** Plain tinted text button, for the secondary action. */
@Composable
fun PlainButton(text: String, color: Color = fc.accent, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = FcType.body, color = if (pressed) color.copy(alpha = 0.5f) else color)
    }
}

/** − value + , in the iOS stepper style. */
@Composable
fun Stepper(onMinus: () -> Unit, onPlus: () -> Unit, minusEnabled: Boolean, plusEnabled: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(fc.fill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperHalf("−", "Decrease", minusEnabled, onMinus)
        Box(Modifier.width(0.5.dp).height(20.dp).background(fc.separator))
        StepperHalf("+", "Increase", plusEnabled, onPlus)
    }
}

@Composable
private fun StepperHalf(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 56.dp, height = 36.dp)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 24.sp, color = if (enabled) fc.label else fc.label3)
    }
}

// ---- Sheets ----

/** A bottom sheet with a grabber, a centred title and a Done button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(
    title: String,
    onDismiss: () -> Unit,
    doneLabel: String? = "Done",
    onDone: () -> Unit = onDismiss,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = fc.background,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(fc.label3),
            )
        },
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(title, style = FcType.headline, color = fc.label, modifier = Modifier.align(Alignment.Center))
            if (doneLabel != null) {
                PlainButton(doneLabel, modifier = Modifier.align(Alignment.CenterEnd), onClick = onDone)
            }
        }
        Column(Modifier.fillMaxWidth().padding(bottom = 28.dp), content = content)
    }
}

// ---- Wheel picker ----

/** An iOS-style scroll wheel. Calls [onSelect] when it settles on an item. */
@Composable
fun Wheel(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 40.dp,
    visible: Int = 5,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(items.indices))
    val fling = rememberSnapFlingBehavior(lazyListState = state, snapPosition = SnapPosition.Center)
    var centered by remember { mutableIntStateOf(selected) }

    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: centered
        }.distinctUntilChanged().collect { centered = it }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().collect { moving ->
            if (!moving) onSelect(centered)
        }
    }

    Box(modifier.height(itemHeight * visible), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(fc.fill.copy(alpha = fc.fill.alpha * 0.6f)),
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight * (visible / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(items.size) { i ->
                val d = abs(i - centered)
                Box(Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        items[i],
                        style = if (d == 0) FcType.title3.copy(fontWeight = FontWeight.Normal) else FcType.body,
                        color = when (d) {
                            0 -> fc.label
                            1 -> fc.label2
                            else -> fc.label3
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Hours and minutes (5-minute steps) wheels. Returns minutes after midnight. */
@Composable
fun TimeWheels(minuteOfDay: Int, is24h: Boolean, onChange: (Int) -> Unit) {
    val hour = minuteOfDay / 60
    val minute = (minuteOfDay % 60) / 5
    val hours = (0..23).map { h ->
        if (is24h) "%02d".format(h) else "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}"
    }
    val minutes = (0..11).map { "%02d".format(it * 5) }
    var h by remember { mutableIntStateOf(hour) }
    var m by remember { mutableIntStateOf(minute) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wheel(hours, h, { h = it; onChange(h * 60 + m * 5) }, Modifier.weight(1f))
        Text(":", style = FcType.title3, color = fc.label)
        Wheel(minutes, m, { m = it; onChange(h * 60 + m * 5) }, Modifier.weight(1f))
    }
}

// ---- Charts ----

/** One ring made of coloured segments laid end to end (e.g. Instagram then YouTube of a shared budget). */
@Composable
fun SegmentRing(segments: List<Pair<Float, Color>>, track: Color, size: Dp, stroke: Dp) {
    Canvas(Modifier.size(size)) {
        val s = stroke.toPx()
        val inset = s / 2
        val arc = androidx.compose.ui.geometry.Size(this.size.width - s, this.size.height - s)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(s))
        var start = -90f
        // Draw back to front so each segment's round cap sits on top of the one before.
        val sweeps = segments.map { (f, c) -> (360f * f.coerceIn(0f, 1f)) to c }
        sweeps.forEach { (sweep, c) ->
            if (sweep > 0.5f) {
                drawArc(c, start, sweep, false, Offset(inset, inset), arc, style = Stroke(s, cap = StrokeCap.Round))
                start += sweep
            }
        }
    }
}

/**
 * Seven stacked bars (oldest first), with an optional dashed line for the daily limit.
 * [values] are per day, per series, in minutes.
 */
@Composable
fun WeekBars(
    values: List<List<Float>>,
    colors: List<Color>,
    labels: List<String>,
    highlight: Int,
    limit: Float?,
    modifier: Modifier = Modifier,
) {
    val track = fc.fill
    val line = fc.label3
    val totals = values.map { it.sum() }
    val max = maxOf(totals.maxOrNull() ?: 0f, limit ?: 0f, 1f) * 1.15f
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(132.dp)) {
            val n = values.size
            val slot = size.width / n
            val barW = minOf(slot * 0.42f, 22.dp.toPx())
            val r = androidx.compose.ui.geometry.CornerRadius(barW / 2.6f)
            values.forEachIndexed { i, series ->
                val x = slot * i + (slot - barW) / 2
                if (series.sum() <= 0f) {
                    drawRoundRect(track, Offset(x, size.height - 4.dp.toPx()), androidx.compose.ui.geometry.Size(barW, 4.dp.toPx()), r)
                    return@forEachIndexed
                }
                // Stack from the bottom; each series is drawn as a rounded bar up to its running total.
                var acc = series.sum()
                for (k in series.indices.reversed()) {
                    if (acc <= 0f) continue
                    val h = size.height * (acc / max)
                    drawRoundRect(colors[k], Offset(x, size.height - h), androidx.compose.ui.geometry.Size(barW, h), r)
                    acc -= series[k]
                }
            }
            if (limit != null && limit > 0f) {
                val y = size.height - size.height * (limit / max)
                drawLine(
                    line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(
                    l,
                    style = FcType.caption.copy(fontWeight = if (i == highlight) FontWeight.Bold else FontWeight.Normal),
                    color = if (i == highlight) fc.label else fc.label2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ---- Ring ----

/** A single activity-style ring. [progress] 0..1. */
@Composable
fun Ring(progress: Float, color: Color, track: Color, size: Dp, stroke: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = stroke.toPx()
        val inset = s / 2
        val arc = androidx.compose.ui.geometry.Size(this.size.width - s, this.size.height - s)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(s))
        if (progress > 0f) {
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(s, cap = StrokeCap.Round))
        }
    }
}
