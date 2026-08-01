package com.rishikesh.lifelink.util

import android.app.Activity
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rishikesh.lifelink.R

/**
 * Enables edge-to-edge display and configures system bar appearance.
 *
 * @param isLightBackground If true, status bar icons will be dark (for light backgrounds).
 *                           If false, status bar icons will be light (for dark backgrounds).
 */
fun ComponentActivity.applyDynamicStatusBar(isLightBackground: Boolean = true) {
    applyWindowDynamicStatusBar(window, isLightBackground)
}

/**
 * Low-level utility to apply dynamic status bar to any window (Activity or Dialog).
 */

fun installPersistentLightStatusBar(window: Window) {
    val decorView = window.decorView
    val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
        androidx.core.view.WindowCompat.getInsetsController(window, decorView)
            .isAppearanceLightStatusBars = true
    }
    decorView.viewTreeObserver.addOnGlobalLayoutListener(listener)
    decorView.setTag(R.id.tag_status_bar_listener, listener)
}

fun removePersistentLightStatusBar(window: android.view.Window) {
    val decorView = window.decorView
    val listener = decorView.getTag(R.id.tag_status_bar_listener) as? android.view.ViewTreeObserver.OnGlobalLayoutListener
    if (listener != null) decorView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
}

fun applyWindowDynamicStatusBar(window: android.view.Window, isLightBackground: Boolean, source: String = "UNKNOWN") {
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

    val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
    controller.isAppearanceLightStatusBars = isLightBackground

    android.util.Log.d("STATUSBAR_DEBUG", "[$source] SDK=${android.os.Build.VERSION.SDK_INT} setLight=$isLightBackground readBack=${controller.isAppearanceLightStatusBars} windowHash=${window.hashCode()}")

    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.statusBarColor = android.graphics.Color.argb(1, 0, 0, 0)

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
    }

    window.decorView.post {
        val recheck = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        android.util.Log.d("STATUSBAR_DEBUG", "[$source] one-frame-later readBack=${recheck.isAppearanceLightStatusBars} windowHash=${window.hashCode()}")
    }
}

/**
 * Applies system bar insets as padding to the root content view.
 * Prevents UI overlap with status and navigation bars while allowing backgrounds to flow behind.
 */
fun Activity.applySystemBarInsets() {
    val rootView = findViewById<android.view.View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        insets
    }
}
