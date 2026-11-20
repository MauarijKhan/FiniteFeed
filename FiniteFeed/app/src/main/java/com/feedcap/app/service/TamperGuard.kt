package com.feedcap.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Parental controls: while a parent PIN is set (and not unlocked), screens that could switch
 * FeedCap off are closed: its Accessibility page, its App info page (Force stop, Uninstall) and
 * the uninstall dialog. Recognised by FeedCap's name showing in Settings or the package installer.
 *
 * This is the standard parental-control approach. It's for a parent managing a child's phone,
 * which Google Play allows for parental-control apps; a self-control app may not block uninstall.
 */
class TamperGuard(private val service: AccessibilityService, private val appLabel: String) {

    private var lastCheckAt = 0L

    /** True if [root] (in [pkg]) is a screen that could turn FeedCap off. */
    fun isTamperScreen(pkg: String, root: AccessibilityNodeInfo?, now: Long): Boolean {
        if (pkg !in GUARDED_PACKAGES || root == null) return false
        if (now - lastCheckAt < CHECK_MS) return false
        lastCheckAt = now
        if (root.packageName?.toString() != pkg) return false
        return root.findAccessibilityNodeInfosByText(appLabel).any { it.isVisibleToUser }
    }

    companion object {
        private const val CHECK_MS = 300L
        val GUARDED_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )
    }
}
