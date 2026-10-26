package com.zet.switchpadsr

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.zet.switchpadsr.ui.MainScreen
import com.zet.switchpadsr.ui.theme.SwitchPadSRTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SwitchPadSR-MainAct"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Lock screen on for gaming sessions
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to set FLAG_KEEP_SCREEN_ON", e)
        }

        // Enable immersive full-screen sticky mode
        hideSystemBars()

        // Request 144Hz maximum panel refresh rate
        requestMaxRefreshRate()

        setContent {
            SwitchPadSRTheme {
                MainScreen()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error hiding system bars", e)
        }
    }

    private fun requestMaxRefreshRate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val display = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        display
                    } else {
                        @Suppress("DEPRECATION")
                        windowManager.defaultDisplay
                    }
                } catch (e: Throwable) {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay
                }

                display?.supportedModes?.let { modes ->
                    var maxRate = 60.0f
                    var bestModeId = 0
                    for (mode in modes) {
                        if (mode.refreshRate > maxRate) {
                            maxRate = mode.refreshRate
                            bestModeId = mode.modeId
                        }
                    }
                    if (bestModeId != 0) {
                        val params = window.attributes
                        params.preferredDisplayModeId = bestModeId
                        window.attributes = params
                        Log.i(TAG, "Requested max display refresh rate: ${maxRate}Hz (mode $bestModeId)")
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error requesting max display refresh rate", e)
        }
    }
}
