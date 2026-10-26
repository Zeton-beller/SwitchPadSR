package com.zet.switchpadsr

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.zet.switchpadsr.audio.CaptureAudio
import com.zet.switchpadsr.capture.CaptureInfo
import com.zet.switchpadsr.capture.CaptureStatus
import com.zet.switchpadsr.capture.UvcCapture
import com.zet.switchpadsr.data.RecentVideo
import com.zet.switchpadsr.data.SettingsStore
import com.zet.switchpadsr.gl.RenderStats
import com.zet.switchpadsr.gl.SrRenderer
import com.zet.switchpadsr.model.InputSourceMode
import com.zet.switchpadsr.model.RenderSettings
import com.zet.switchpadsr.player.VideoInfo
import com.zet.switchpadsr.player.VideoSource

enum class Screen { HOME, PLAYER }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app
    private val main = Handler(Looper.getMainLooper())
    val store = SettingsStore(ctx)

    var settings by mutableStateOf(store.load()); private set
    var screen by mutableStateOf(Screen.HOME); private set
    var mode by mutableStateOf(store.lastMode()); private set
    var captureInfo by mutableStateOf(CaptureInfo()); private set
    var videoInfo by mutableStateOf(VideoInfo()); private set
    var stats by mutableStateOf(RenderStats()); private set
    var videoName by mutableStateOf(""); private set
    var recents by mutableStateOf<List<RecentVideo>>(store.recents()); private set
    var usbPresent by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var askedPermissions = false

    var currentVideoUri: Uri? = null; private set
    private var inputSurface: Surface? = null
    private var audioRunning = false

    val renderer = SrRenderer(
        ctx,
        onInputSurface = { s -> main.post { onInputSurface(s) } },
        onStats = { st -> main.post { stats = st } },
        onError = { msg -> main.post { message = msg } }
    )

    val video = VideoSource(
        ctx,
        onSize = { w, h -> renderer.setSourceSize(w, h) },
        onInfo = { info ->
            videoInfo = info
            if (info.error != null) message = info.error
        }
    )

    val capture = UvcCapture(
        ctx,
        onSize = { w, h -> renderer.setSourceSize(w, h) },
        onInfo = { info -> main.post { onCaptureInfo(info) } }
    )

    private val audio = CaptureAudio(ctx)

    init {
        renderer.updateSettings(settings)
        audio.setVolume(settings.audioVolume)
    }

    // ------------------------------------------------------------ surface plumbing
    private fun onInputSurface(s: Surface?) {
        inputSurface = s
        video.setSurface(s)
        capture.attachSurface(s)
    }

    private fun onCaptureInfo(info: CaptureInfo) {
        captureInfo = info
        if (mode == InputSourceMode.CAPTURE_CARD && screen == Screen.PLAYER) {
            if (info.status == CaptureStatus.STREAMING) startAudioIfWanted() else stopAudio()
        }
    }

    private fun startAudioIfWanted() {
        if (!settings.captureAudio || audioRunning) return
        audioRunning = audio.start()
    }

    private fun stopAudio() {
        if (audioRunning) { audio.stop(); audioRunning = false }
    }

    // ------------------------------------------------------------ navigation / modes
    fun goCapture() {
        saveVideoPosition()
        video.stop()
        mode = InputSourceMode.CAPTURE_CARD
        store.saveLastMode(mode)
        screen = Screen.PLAYER
        renderer.resetFrame()
        capture.start()
        if (captureInfo.status == CaptureStatus.STREAMING) startAudioIfWanted()
    }

    fun openVideo(uri: Uri, name: String, resume: Boolean = true) {
        stopCapture()
        saveVideoPosition()
        mode = InputSourceMode.LOCAL_VIDEO
        store.saveLastMode(mode)
        currentVideoUri = uri
        videoName = name
        val pos = if (resume) store.recents().firstOrNull { it.uri == uri.toString() }?.lastPositionMs ?: 0L else 0L
        store.addRecent(uri, name, pos)
        recents = store.recents()
        screen = Screen.PLAYER
        renderer.resetFrame()
        video.setSurface(inputSurface)
        video.open(uri, if (pos > 3000) pos - 1500 else 0L, settings.loopVideo)
    }

    fun resumeVideoOrNull(): Boolean {
        val uri = currentVideoUri ?: return false
        openVideo(uri, videoName, resume = true)
        return true
    }

    fun goHome() {
        saveVideoPosition()
        stopCapture()
        video.stop()
        screen = Screen.HOME
        renderer.resetFrame()
        recents = store.recents()
    }

    private fun stopCapture() {
        stopAudio()
        capture.stop()
    }

    fun removeRecent(r: RecentVideo) {
        store.removeRecent(r.uri)
        recents = store.recents()
    }

    private fun saveVideoPosition() {
        val u = currentVideoUri ?: return
        if (mode == InputSourceMode.LOCAL_VIDEO && video.hasMedia()) {
            val pos = video.positionMs()
            val dur = video.durationMs()
            store.updatePosition(u.toString(), if (dur > 0 && pos > dur - 5000) 0L else pos)
        }
    }

    fun onAppBackground() {
        if (screen != Screen.PLAYER) return
        if (mode == InputSourceMode.LOCAL_VIDEO) { video.pause(); saveVideoPosition() }
        else { stopAudio(); capture.stop() }
    }

    fun onAppForeground() {
        if (screen == Screen.PLAYER && mode == InputSourceMode.CAPTURE_CARD) capture.start()
    }

    fun refreshUsbHint() {
        try {
            val um = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            usbPresent = um.deviceList.values.any { capture.isVideoDevice(it) }
        } catch (_: Throwable) { usbPresent = false }
    }

    // ------------------------------------------------------------ settings
    fun update(f: (RenderSettings) -> RenderSettings) {
        val old = settings
        val new = f(old)
        settings = new
        store.save(new)
        renderer.updateSettings(new)
        if (new.loopVideo != old.loopVideo) video.setLoop(new.loopVideo)
        if (new.audioVolume != old.audioVolume) audio.setVolume(new.audioVolume)
        if (new.captureAudio != old.captureAudio) {
            if (new.captureAudio) { if (captureInfo.status == CaptureStatus.STREAMING) startAudioIfWanted() } else stopAudio()
        }
    }

    fun resetAlgorithmParams() {
        val d = RenderSettings()
        update {
            it.copy(
                sgsrThreshold = d.sgsrThreshold, sgsrSharpness = d.sgsrSharpness, sgsrUseDirection = d.sgsrUseDirection,
                fsrSharpness = d.fsrSharpness, fsrDering = d.fsrDering,
                anime4kStrength = d.anime4kStrength, anime4kMode = d.anime4kMode,
                crtScanline = d.crtScanline, crtCurvature = d.crtCurvature, crtMask = d.crtMask
            )
        }
    }

    override fun onCleared() {
        saveVideoPosition()
        stopAudio()
        capture.stop()
        video.release()
        super.onCleared()
    }
}
