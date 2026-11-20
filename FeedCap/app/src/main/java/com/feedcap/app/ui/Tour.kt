package com.feedcap.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feedcap.app.ui.design.FcType
import com.feedcap.app.ui.design.PlainButton
import com.feedcap.app.ui.design.fc
import kotlinx.coroutines.delay

/*
 * A one-time guided tour: the screen dims, a spotlight cuts out around one part of the UI, and an
 * arrow points to it from a short note. Parts opt in with Modifier.tourTarget("key").
 */

@OptIn(ExperimentalFoundationApi::class)
class TourState {
    internal val bounds = mutableStateMapOf<String, Rect>()
    internal val requesters = mutableMapOf<String, BringIntoViewRequester>()
}

val LocalTour = staticCompositionLocalOf<TourState?> { null }

data class TourStep(val key: String, val title: String, val body: String)

/** Marks this element as a tour stop. The tour scrolls it into view when it's that stop's turn. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tourTarget(key: String): Modifier = composed {
    val tour = LocalTour.current ?: return@composed this
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(key) {
        tour.requesters[key] = requester
        onDispose {
            tour.requesters.remove(key)
            tour.bounds.remove(key)
        }
    }
    this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { tour.bounds[key] = it.boundsInRoot() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TourOverlay(tour: TourState, steps: List<TourStep>, onDone: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index]
    val last = index == steps.lastIndex

    LaunchedEffect(index) {
        tour.requesters[step.key]?.bringIntoView()
        delay(50)
    }
    val target = tour.bounds[step.key]
    val density = LocalDensity.current
    val pad = with(density) { 8.dp.toPx() }

    // Glide the spotlight between stops.
    val l by animateFloatAsState((target?.left ?: 0f) - pad, tween(380), label = "l")
    val t by animateFloatAsState((target?.top ?: 0f) - pad, tween(380), label = "t")
    val r by animateFloatAsState((target?.right ?: 0f) + pad, tween(380), label = "r")
    val b by animateFloatAsState((target?.bottom ?: 0f) + pad, tween(380), label = "b")
    val appear by animateFloatAsState(if (target != null) 1f else 0f, tween(300), label = "appear")
    val accent = fc.accent

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Swallow touches so the app underneath can't be used mid-tour.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val screenH = with(density) { maxHeight.toPx() }
        val below = target == null || (t + b) / 2 < screenH * 0.55f
        val gap = with(density) { 54.dp.toPx() }

        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            drawRect(Color.Black.copy(alpha = 0.78f * appear.coerceAtLeast(0.6f)))
            if (target != null) {
                val corner = CornerRadius(18.dp.toPx())
                drawRoundRect(Color.Transparent, Offset(l, t), Size(r - l, b - t), corner, blendMode = BlendMode.Clear)
                drawRoundRect(accent.copy(alpha = appear), Offset(l, t), Size(r - l, b - t), corner, style = Stroke(2.dp.toPx()))

                // A hand-drawn-feeling arrow from the note to the spotlight.
                val cx = ((l + r) / 2).coerceIn(size.width * 0.18f, size.width * 0.82f)
                val startY = if (below) b + gap - 6.dp.toPx() else t - gap + 6.dp.toPx()
                val endY = if (below) b + 8.dp.toPx() else t - 8.dp.toPx()
                val startX = cx + 26.dp.toPx()
                val path = Path().apply {
                    moveTo(startX, startY)
                    quadraticTo(startX + 14.dp.toPx(), (startY + endY) / 2, cx, endY)
                }
                val stroke = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(path, Color.White.copy(alpha = appear), style = stroke)
                val head = 9.dp.toPx()
                val dir = if (below) -1f else 1f
                drawLine(Color.White.copy(alpha = appear), Offset(cx, endY), Offset(cx - head * 0.8f, endY - dir * head), 2.4.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White.copy(alpha = appear), Offset(cx, endY), Offset(cx + head * 0.9f, endY - dir * head * 0.7f), 2.4.dp.toPx(), StrokeCap.Round)
            }
        }

        val noteModifier = if (target == null) {
            Modifier.align(Alignment.Center)
        } else if (below) {
            Modifier.align(Alignment.TopCenter).padding(top = with(density) { (b + gap).toDp() })
        } else {
            Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { (screenH - t + gap).toDp() })
        }

        Column(
            noteModifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(fc.elevated)
                .padding(20.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(step.title, style = FcType.title3, color = fc.label)
            Spacer(Modifier.height(6.dp))
            Text(step.body, style = FcType.subhead, color = fc.label2)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    steps.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(if (i == index) 18.dp else 6.dp, 6.dp)
                                .clip(CircleShape)
                                .background(if (i == index) fc.accent else fc.fill),
                        )
                    }
                }
                if (!last) PlainButton("Skip", color = fc.label2, onClick = onDone)
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(fc.accent)
                        .clickable { if (last) onDone() else index++ }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(if (last) "Got It" else "Next", style = FcType.headline.copy(fontWeight = FontWeight.SemiBold), color = fc.onAccent)
                }
            }
        }
    }
}
