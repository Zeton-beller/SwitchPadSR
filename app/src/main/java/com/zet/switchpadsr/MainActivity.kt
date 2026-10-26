package com.zet.switchpadsr

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.zet.switchpadsr.ui.AppRoot
import com.zet.switchpadsr.ui.theme.SwitchPadSRTheme

class MainActivity : ComponentActivity() {
    companion object { private const val TAG = "SwitchPadSR-MainAct" }

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } catch (e: Throwable) { Log.w(TAG, "keep screen on", e) }
        hideSystemBars()
        requestMaxRefreshRate()
        setContent {
            SwitchPadSRTheme { AppRoot(vm) }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { c ->
                    c.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN)
            }
        } catch (e: Throwable) { Log.w(TAG, "hide bars", e) }
    }

    /** Ask for the highest refresh-rate display mode (144 Hz on the Xiaomi Pad). */
    private fun requestMaxRefreshRate() {
        try {
            val d = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else null
            } catch (_: Throwable) { null }
            @Suppress("DEPRECATION")
            val disp = d ?: windowManager.defaultDisplay
            val best = disp?.supportedModes?.maxByOrNull { it.refreshRate } ?: return
            val p = window.attributes
            p.preferredDisplayModeId = best.modeId
            window.attributes = p
            Log.i(TAG, "preferred display mode ${best.modeId} @ ${best.refreshRate}Hz")
        } catch (e: Throwable) { Log.w(TAG, "refresh rate", e) }
    }
}
