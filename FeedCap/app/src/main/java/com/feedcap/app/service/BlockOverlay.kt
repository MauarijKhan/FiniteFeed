package com.feedcap.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.feedcap.app.data.Store
import com.feedcap.app.ui.design.accentColors

/**
 * Full-screen "limit reached" screen drawn over the target app. Uses an accessibility overlay,
 * so no "Display over other apps" permission is needed. Not focusable, so the app underneath
 * stays the active window and detection keeps working.
 */
class BlockOverlay(private val service: AccessibilityService) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { hide() }
    private var view: View? = null
    private var curtain = false

    val isShowing: Boolean get() = view != null

    /**
     * A plain black screen with a line of text and no button, shown while FeedCap moves the
     * app somewhere else (e.g. from the main feed to Following), so the feed can't be seen.
     */
    fun showCurtain(message: String, maxMs: Long = CURTAIN_MAX_MS) {
        if (curtain && view != null) return
        hide()
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF000000.toInt())
        }
        root.addView(TextView(service).apply {
            text = message
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        })
        add(root, maxMs)
        curtain = view != null
    }

    /** Hides the curtain, but not a "limit reached" screen that replaced it. */
    fun hideCurtain() {
        if (curtain) hide()
    }

    fun show(title: String, message: String) {
        hide()
        val dp = service.resources.displayMetrics.density
        val pad = (32 * dp).toInt()

        val (accent, onAccent) = accentColors(Store.get(service).accent.value, dark = true)
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF000000.toInt())
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(service).apply {
            text = title
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        })
        root.addView(TextView(service).apply {
            text = message
            textSize = 17f
            setLineSpacing(0f, 1.2f)
            setTextColor(0x99EBEBF5.toInt())
            gravity = Gravity.CENTER
            setPadding(0, (14 * dp).toInt(), 0, (36 * dp).toInt())
        })
        root.addView(TextView(service).apply {
            text = "OK"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(onAccent.toArgb())
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 26 * dp
                setColor(accent.toArgb())
            }
            setPadding((56 * dp).toInt(), (14 * dp).toInt(), (56 * dp).toInt(), (14 * dp).toInt())
            isClickable = true
            setOnClickListener { hide() }
        })
        add(root, AUTO_HIDE_MS)
    }

    private fun add(root: View, autoHideMs: Long) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        runCatching {
            wm.addView(root, params)
            view = root
            handler.postDelayed(autoHide, autoHideMs)
        }
    }

    fun hide() {
        handler.removeCallbacks(autoHide)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        curtain = false
    }

    private companion object {
        const val AUTO_HIDE_MS = 6_000L
        const val CURTAIN_MAX_MS = 4_000L
    }
}
