package com.zet.switchpadsr.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zet.switchpadsr.AppViewModel
import com.zet.switchpadsr.Screen
import com.zet.switchpadsr.model.InputSourceMode

private fun displayName(ctx: Context, uri: Uri): String {
    try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "视频"
        }
    } catch (_: Throwable) {}
    return uri.lastPathSegment ?: "视频"
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var glView by remember { mutableStateOf<GLSurfaceView?>(null) }

    DisposableEffect(lifecycleOwner, glView) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> { vm.onAppBackground(); glView?.onPause() }
                Lifecycle.Event.ON_RESUME -> { glView?.onResume(); vm.onAppForeground() }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Throwable) {}
            vm.openVideo(uri, displayName(ctx, uri), resume = false)
        }
    }
    val pickVideo: () -> Unit = { videoPicker.launch(arrayOf("video/*")) }

    val audioPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            vm.message = "麦克风权限已获取，采集卡音频直通已就绪"
            if (vm.settings.captureAudio) vm.update { it.copy(captureAudio = true) }
        } else {
            vm.message = "未授予麦克风权限，采集卡画面正常但扬声器无声音"
        }
    }
    val requestAudioPermission: () -> Unit = {
        audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.goCapture()
    }
    val startCapture: () -> Unit = {
        val need = listOf(Manifest.permission.RECORD_AUDIO).filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isEmpty()) {
            vm.goCapture()
        } else if (!vm.askedPermissions) {
            vm.askedPermissions = true
            permLauncher.launch(need.toTypedArray())
        } else {
            vm.goCapture()
        }
    }
    val openVideoTab: () -> Unit = {
        if (vm.currentVideoUri != null) vm.resumeVideoOrNull()
        else {
            val r = vm.recents.firstOrNull()
            if (r != null) vm.openVideo(Uri.parse(r.uri), r.name) else pickVideo()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                GLSurfaceView(c).apply {
                    setEGLContextClientVersion(3)
                    setEGLConfigChooser(8, 8, 8, 0, 0, 0)
                    preserveEGLContextOnPause = true
                    vm.renderer.glView = this
                    setRenderer(vm.renderer)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    glView = this
                }
            }
        )
        when (vm.screen) {
            Screen.HOME -> HomeScreen(
                vm,
                onCapture = startCapture,
                onPickVideo = pickVideo,
                onOpenRecent = { r -> vm.openVideo(Uri.parse(r.uri), r.name) }
            )
            Screen.PLAYER -> PlayerScreen(
                vm = vm,
                onPickVideo = pickVideo,
                onSelectCapture = startCapture,
                onSelectVideo = openVideoTab,
                onRequestAudioPermission = requestAudioPermission
            )
        }
    }
}
