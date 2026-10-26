package com.zet.switchpadsr.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zet.switchpadsr.capture.UsbCaptureManager
import com.zet.switchpadsr.engine.NativeEngine
import com.zet.switchpadsr.model.InputSourceMode
import com.zet.switchpadsr.model.RenderSettings
import com.zet.switchpadsr.model.TargetResolutionPreset
import com.zet.switchpadsr.player.LocalVideoPlayer
import com.zet.switchpadsr.ui.theme.DarkBackground
import com.zet.switchpadsr.ui.theme.NeonCyan
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    initialSettings: RenderSettings = RenderSettings()
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(initialSettings) }
    var showControlPanel by remember { mutableStateOf(false) }
    var showOverlayControls by remember { mutableStateOf(true) }

    // Metrics state
    var fps by remember { mutableFloatStateOf(60.0f) }
    var gpuTimeMs by remember { mutableFloatStateOf(0.22f) }
    var latencyMs by remember { mutableFloatStateOf(28.5f) }

    // Video player state
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isVideoPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    var videoPlayerSurface by remember { mutableStateOf<android.view.Surface?>(null) }

    val videoPlayer = remember {
        LocalVideoPlayer(
            context = context,
            onPositionUpdate = { current, duration ->
                currentPosMs = current
                durationMs = duration
            },
            onVideoSizeChanged = { w, h ->
                NativeEngine.nativeSetTargetResolution(w, h)
            },
            onCompletion = {
                isVideoPlaying = false
            }
        )
    }

    val usbManager = remember {
        UsbCaptureManager(
            context = context,
            onDeviceConnected = { dev ->
                // Device connected
            },
            onDeviceDisconnected = { dev ->
                // Device disconnected
            },
            onError = { msg ->
                // Log or toast error
            }
        )
    }

    // Video File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            videoPlayerSurface?.let { surface ->
                videoPlayer.openVideo(it, surface)
                isVideoPlaying = true
            }
        }
    }

    // Lifecycle registration
    DisposableEffect(Unit) {
        usbManager.register()
        if (settings.inputMode == InputSourceMode.CAPTURE_CARD) {
            usbManager.scanAndConnect()
        }
        onDispose {
            usbManager.unregister()
            usbManager.disconnect()
            videoPlayer.release()
            NativeEngine.nativeDestroy()
        }
    }

    // Metrics Polling Loop (every 500ms)
    LaunchedEffect(Unit) {
        val metricsArray = FloatArray(5)
        while (true) {
            NativeEngine.nativeGetMetrics(metricsArray)
            fps = metricsArray[0]
            gpuTimeMs = metricsArray[1]
            latencyMs = metricsArray[2]
            delay(500)
        }
    }

    // Sync settings changes to native engine
    LaunchedEffect(settings) {
        NativeEngine.nativeSetAlgorithm(settings.algorithm.id)
        NativeEngine.nativeSetDisplayMode(settings.displayMode.id)

        val targetW = when (settings.targetResolution) {
            TargetResolutionPreset.NATIVE_3_2K -> 3200
            TargetResolutionPreset.QHD_2K -> 2560
            TargetResolutionPreset.FHD_1080P -> 1920
            TargetResolutionPreset.CUSTOM -> (1920 * settings.customScaleFactor).toInt()
        }
        val targetH = when (settings.targetResolution) {
            TargetResolutionPreset.NATIVE_3_2K -> 1800
            TargetResolutionPreset.QHD_2K -> 1440
            TargetResolutionPreset.FHD_1080P -> 1080
            TargetResolutionPreset.CUSTOM -> (1080 * settings.customScaleFactor).toInt()
        }
        NativeEngine.nativeSetTargetResolution(targetW, targetH)

        NativeEngine.nativeSetSGSRParams(
            settings.sgsrThreshold,
            settings.sgsrSharpness,
            settings.sgsrUseDirection
        )
        NativeEngine.nativeSetFSRParams(
            settings.fsrSharpness,
            settings.fsrDering
        )
        NativeEngine.nativeSetAnime4KParams(
            settings.anime4kStrength,
            settings.anime4kMode
        )
        NativeEngine.nativeSetCRTParams(
            settings.crtScanline,
            settings.crtCurvature,
            settings.crtMask
        )
        NativeEngine.nativeSetSplitScreen(
            settings.splitScreenEnabled,
            settings.splitPosition
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showOverlayControls = !showOverlayControls
            }
    ) {
        // Native SurfaceView rendering Vulkan output
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            videoPlayerSurface = holder.surface
                            NativeEngine.nativeInit(holder.surface, width, height)
                            if (settings.inputMode == InputSourceMode.CAPTURE_CARD) {
                                usbManager.scanAndConnect()
                            }
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
                            NativeEngine.nativeSetTargetResolution(w, h)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            videoPlayerSurface = null
                            NativeEngine.nativeDestroy()
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Gaming HUD
        AnimatedVisibility(
            visible = showOverlayControls && settings.showHud,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            GamingHud(
                fps = fps,
                gpuTimeMs = gpuTimeMs,
                latencyMs = latencyMs,
                settings = settings
            )
        }

        // Floating Settings Icon
        AnimatedVisibility(
            visible = showOverlayControls && !showControlPanel,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 20.dp)
        ) {
            FloatingActionButton(
                onClick = { showControlPanel = true },
                containerColor = DarkBackground.copy(alpha = 0.85f),
                contentColor = NeonCyan
            ) {
                Icon(Icons.Default.Settings, contentDescription = "打开设置面板")
            }
        }

        // Bottom Controls for Video Player
        if (settings.inputMode == InputSourceMode.LOCAL_VIDEO) {
            AnimatedVisibility(
                visible = showOverlayControls && !showControlPanel,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                VideoPlayerControls(
                    isPlaying = isVideoPlaying,
                    currentPosMs = currentPosMs,
                    durationMs = durationMs,
                    playbackSpeed = playbackSpeed,
                    onPlayPauseToggle = {
                        if (isVideoPlaying) {
                            videoPlayer.pause()
                            isVideoPlaying = false
                        } else {
                            videoPlayer.play()
                            isVideoPlaying = true
                        }
                    },
                    onSeek = { targetMs ->
                        videoPlayer.seekTo(targetMs)
                    },
                    onSpeedChange = { newSpeed ->
                        playbackSpeed = newSpeed
                        videoPlayer.setSpeed(newSpeed)
                    },
                    onOpenFilePicker = {
                        filePickerLauncher.launch("video/*")
                    }
                )
            }
        }

        // Full Control Panel Dialog / Drawer
        if (showControlPanel) {
            ControlPanel(
                settings = settings,
                onSettingsChanged = { updated ->
                    settings = updated
                    if (updated.inputMode != settings.inputMode) {
                        if (updated.inputMode == InputSourceMode.CAPTURE_CARD) {
                            videoPlayer.pause()
                            usbManager.scanAndConnect()
                        } else {
                            usbManager.disconnect()
                        }
                    }
                },
                onClose = { showControlPanel = false },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
