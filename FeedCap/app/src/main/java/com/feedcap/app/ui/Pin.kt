package com.feedcap.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feedcap.app.data.ParentPin
import com.feedcap.app.data.PinCheck
import com.feedcap.app.data.Store
import com.feedcap.app.ui.design.FcType
import com.feedcap.app.ui.design.Sheet
import com.feedcap.app.ui.design.fc
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Four dots and a number pad, after the iOS passcode screen. Calls [onComplete] once four
 * digits are in; return false to shake and clear (a wrong PIN).
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String,
    error: String?,
    enabled: Boolean = true,
    onComplete: (String) -> Boolean,
) {
    var pin by remember { mutableStateOf("") }
    val shake = remember { Animatable(0f) }
    var shakeKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(shakeKey) {
        if (shakeKey == 0) return@LaunchedEffect
        for (x in listOf(-22f, 18f, -12f, 8f, -3f, 0f)) shake.animateTo(x, spring(stiffness = Spring.StiffnessHigh))
    }

    fun press(d: Char) {
        if (!enabled || pin.length >= ParentPin.LENGTH) return
        pin += d
        if (pin.length == ParentPin.LENGTH) {
            if (!onComplete(pin)) shakeKey++
            pin = ""
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = FcType.title2, color = fc.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = FcType.subhead,
            color = fc.label2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(32.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier
                .offset { IntOffset(shake.value.roundToInt(), 0) }
                .semantics { contentDescription = "${pin.length} of ${ParentPin.LENGTH} digits entered" },
        ) {
            repeat(ParentPin.LENGTH) { i ->
                val filled = i < pin.length
                Box(
                    Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(if (filled) fc.label else fc.background)
                        .border(1.4.dp, fc.label, CircleShape),
                )
            }
        }
        Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
            if (error != null) Text(error, style = FcType.footnote, color = fc.red, textAlign = TextAlign.Center)
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("123", "456", "789", " 0<").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { key ->
                        when (key) {
                            ' ' -> Spacer(Modifier.size(76.dp))
                            '<' -> Key(null, "Delete", enabled) { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
                            else -> Key(key.toString(), key.toString(), enabled) { press(key) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(label: String?, description: String, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(
                when {
                    label == null -> fc.background
                    pressed -> fc.label3
                    else -> fc.fill
                },
            )
            .semantics { contentDescription = description }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(label, fontSize = 32.sp, fontWeight = FontWeight.Normal, color = fc.label)
        } else {
            Text("Delete", style = FcType.callout, color = if (enabled) fc.label else fc.label3)
        }
    }
}

/** Asks for the parent PIN in a sheet; [onUnlocked] runs after the right one. */
@Composable
fun ParentPinDialog(store: Store, reason: String, onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var lockedUntil by remember { mutableLongStateOf(store.parental.value.lockedUntil) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val locked = lockedUntil > now
    Sheet(title = "", onDismiss = onDismiss, doneLabel = "Cancel") {
        Spacer(Modifier.height(8.dp))
        PinPad(
            title = "Enter Parent PIN",
            subtitle = reason,
            enabled = !locked,
            error = if (locked) "Try again in ${((lockedUntil - now) / 1000) + 1} seconds" else error,
        ) { pin ->
            when (val r = store.checkParentPin(pin, System.currentTimeMillis())) {
                PinCheck.Ok -> {
                    onUnlocked()
                    true
                }
                is PinCheck.Wrong -> {
                    lockedUntil = r.lockedUntil
                    error = "Wrong PIN"
                    false
                }
            }
        }
    }
}

/** Create a PIN: enter it, then again. */
@Composable
fun CreatePin(onCreated: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    PinPad(
        title = if (first == null) "Create a Parent PIN" else "Confirm PIN",
        subtitle = if (first == null) {
            "You'll need it to change limits or turn FeedCap off."
        } else {
            "Enter the same four digits again."
        },
        error = error,
    ) { pin ->
        val f = first
        when {
            f == null -> {
                first = pin
                error = null
                true
            }
            f == pin -> {
                onCreated(pin)
                true
            }
            else -> {
                first = null
                error = "PINs didn't match. Try again."
                false
            }
        }
    }
}
