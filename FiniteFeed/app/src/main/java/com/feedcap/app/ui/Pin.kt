package com.feedcap.app.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.feedcap.app.ui.design.PageGutter
import com.feedcap.app.ui.design.PlainButton
import com.feedcap.app.ui.design.PrimaryButton
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
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

private enum class PinMode { PIN, CODE, NEW_PIN, NEW_CODE, RESET }

/**
 * Asks for the parent PIN in a sheet; [onUnlocked] runs after the right one.
 * "Forgot PIN?" offers the recovery code, and without one a 72-hour reset.
 */
@Composable
fun ParentPinDialog(store: Store, reason: String, onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    val parental by store.parental.collectAsState()
    var mode by remember { mutableStateOf(PinMode.PIN) }
    var error by remember { mutableStateOf<String?>(null) }
    var lockedUntil by remember { mutableLongStateOf(store.parental.value.lockedUntil) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var newCode by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val locked = lockedUntil > now
    val lockText = "Too many tries. Try again in ${((lockedUntil - now) / 1000) + 1} seconds"

    // Once a new PIN is set there's nothing to cancel; just save the code.
    Sheet(title = "", onDismiss = onDismiss, doneLabel = if (mode == PinMode.NEW_CODE) null else "Cancel") {
        Spacer(Modifier.height(8.dp))
        when (mode) {
            PinMode.PIN -> {
                PinPad(
                    title = "Enter Parent PIN",
                    subtitle = reason,
                    enabled = !locked,
                    error = if (locked) lockText else error,
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
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PlainButton("Forgot PIN?") {
                        error = null
                        mode = if (parental.hasRecoveryCode) PinMode.CODE else PinMode.RESET
                    }
                }
            }

            PinMode.CODE -> {
                var typed by remember { mutableStateOf("") }
                SheetTitle("Enter Recovery Code", "The code you saved when the PIN was set, like K7QM-4XRP-9TWD.")
                CodeField(typed) { typed = it }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
                    val msg = if (locked) lockText else error
                    if (msg != null) Text(msg, style = FcType.footnote, color = fc.red)
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    "Use Code",
                    enabled = !locked && ParentPin.normalizeCode(typed).length >= 12,
                    modifier = Modifier.padding(horizontal = PageGutter),
                ) {
                    when (val r = store.checkRecoveryCode(typed, System.currentTimeMillis())) {
                        PinCheck.Ok -> {
                            error = null
                            mode = PinMode.NEW_PIN
                        }
                        is PinCheck.Wrong -> {
                            lockedUntil = r.lockedUntil
                            error = "That code didn't match"
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PlainButton("I Don't Have My Code", color = fc.label2) { mode = PinMode.RESET }
                }
            }

            PinMode.NEW_PIN -> CreatePin { pin ->
                store.setParentPin(pin, System.currentTimeMillis())
                // The old code was used up; give them a fresh one to save.
                newCode = store.newRecoveryCode()
                mode = PinMode.NEW_CODE
            }

            PinMode.NEW_CODE -> RecoveryCodeView(newCode, title = "New Recovery Code") { onUnlocked() }

            PinMode.RESET -> {
                if (parental.resetPending) {
                    val left = ParentPin.resetRemainingMs(parental.resetStartedAt, now)
                    SheetTitle(
                        "PIN Reset in ${durationText(left)}",
                        "Limits stay on until then. After that, parental controls turn off and you can set a new PIN. " +
                            "Entering the PIN or recovery code cancels the reset.",
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("OK", modifier = Modifier.padding(horizontal = PageGutter), onClick = onDismiss)
                } else {
                    SheetTitle(
                        "Reset the PIN in 72 Hours",
                        "Without the recovery code, the PIN can be reset after a 72-hour wait. Limits stay on meanwhile, " +
                            "and a notice stays in the app, so nobody can do this quietly.",
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Start 72-Hour Reset", modifier = Modifier.padding(horizontal = PageGutter)) {
                        store.startPinReset(System.currentTimeMillis())
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PlainButton("Back", color = fc.label2) { mode = PinMode.PIN }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = FcType.title2, color = fc.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = FcType.subhead, color = fc.label2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .padding(horizontal = PageGutter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(fc.cell)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        if (value.isEmpty()) Text("XXXX-XXXX-XXXX", style = FcType.title3.copy(fontFamily = FontFamily.Monospace), color = fc.label3)
        BasicTextField(
            value = value,
            // Keep what was typed; changing it mid-input makes keyboards drop letters. Show it in capitals instead.
            onValueChange = { onChange(it.take(20)) },
            singleLine = true,
            textStyle = FcType.title3.copy(fontFamily = FontFamily.Monospace, color = fc.label),
            cursorBrush = SolidColor(fc.accent),
            visualTransformation = UppercaseTransformation,
            // Password type: no suggestions, and the keyboard doesn't learn the code.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Shows text in capitals without changing it (same length, so the cursor maps one to one). */
private object UppercaseTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString) = TransformedText(AnnotatedString(text.text.uppercase()), OffsetMapping.Identity)
}

/** Shows a recovery code once, with Copy, and waits for "I've Saved It". */
@Composable
fun RecoveryCodeView(code: String, title: String = "Save Your Recovery Code", onDone: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    SheetTitle(
        title,
        "If the PIN is ever forgotten, this code resets it straight away. Write it down or save it somewhere only you can see. " +
            "It won't be shown again.",
    )
    Box(
        Modifier
            .padding(horizontal = PageGutter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(fc.cell)
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            code,
            style = FcType.title2.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp),
            color = fc.label,
            modifier = Modifier.semantics { contentDescription = "Recovery code " + code.toList().joinToString(" ") },
        )
    }
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        PlainButton(if (copied) "Copied" else "Copy Code") {
            copySensitive(context, code)
            copied = true
        }
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton("I've Saved It", modifier = Modifier.padding(horizontal = PageGutter), onClick = onDone)
}

/** Copies text, marked sensitive so Android hides it in the clipboard preview. */
private fun copySensitive(context: Context, text: String) {
    val clip = ClipData.newPlainText("Recovery code", text)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

/** "2 days 14 h", "5 h 20 min", "12 min". */
fun durationText(ms: Long): String {
    val min = ms / 60_000
    val h = min / 60
    val d = h / 24
    return when {
        d >= 1 -> "$d ${if (d == 1L) "day" else "days"} ${h % 24} h"
        h >= 1 -> "$h h ${min % 60} min"
        else -> "${min.coerceAtLeast(1)} min"
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
            "You'll need it to change limits or turn Finite Feed off."
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

/**
 * What a locked app shows: nothing but this. No stats, no settings, no tabs.
 * The right PIN (or recovery code) opens the full app for a few minutes.
 */
@Composable
fun ParentLockScreen(store: Store, now: Long, onEnterPin: () -> Unit) {
    val parental by store.parental.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .background(fc.background)
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Padlock(fc.label)
        Spacer(Modifier.height(28.dp))
        Text("Parental Controls Are On", style = FcType.title1, color = fc.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Finite Feed is managed by a parent. Enter the parent PIN to see or change anything.",
            style = FcType.body,
            color = fc.label2,
            textAlign = TextAlign.Center,
        )
        if (parental.resetPending) {
            Spacer(Modifier.height(20.dp))
            Text(
                "PIN reset in ${durationText(ParentPin.resetRemainingMs(parental.resetStartedAt, now))}",
                style = FcType.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = fc.orange,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Enter PIN", onClick = onEnterPin)
        Spacer(Modifier.height(28.dp))
    }
}

/** A simple outline padlock, drawn to match the line icons. */
@Composable
private fun Padlock(color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.Canvas(Modifier.size(width = 64.dp, height = 80.dp)) {
        val w = 5.dp.toPx()
        val bodyTop = size.height * 0.42f
        drawRoundRect(
            color,
            topLeft = androidx.compose.ui.geometry.Offset(w / 2, bodyTop),
            size = androidx.compose.ui.geometry.Size(size.width - w, size.height - bodyTop - w / 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w),
        )
        val r = size.width * 0.3f
        drawArc(
            color, 180f, 180f, false,
            topLeft = androidx.compose.ui.geometry.Offset(size.width / 2 - r, w / 2),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width / 2 - r, w / 2 + r), androidx.compose.ui.geometry.Offset(size.width / 2 - r, bodyTop), w)
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width / 2 + r, w / 2 + r), androidx.compose.ui.geometry.Offset(size.width / 2 + r, bodyTop), w)
        drawCircle(color, 5.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width / 2, bodyTop + (size.height - bodyTop) * 0.45f))
    }
}
