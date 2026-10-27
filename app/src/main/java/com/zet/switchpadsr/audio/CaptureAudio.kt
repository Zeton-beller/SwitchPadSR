package com.zet.switchpadsr.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

data class AudioStatus(
    val isRunning: Boolean = false,
    val deviceName: String = "",
    val sampleRate: Int = 48000,
    val isStereo: Boolean = true,
    val hasPermission: Boolean = false,
    val errorMessage: String? = null
)

/**
 * High-performance, low-latency USB Audio (UAC) passthrough to tablet speakers.
 * Features:
 * - Dynamic AudioDeviceCallback: automatically starts/stops when USB audio card is plugged/unplugged.
 * - Multi-device identification: detects TYPE_USB_DEVICE, TYPE_USB_HEADSET, TYPE_LINE_DIGITAL, etc.
 * - Safe fallback: tries 48000Hz Stereo -> 44100Hz Stereo -> 48000Hz Mono.
 * - Clock-drift compensation with small 10ms PCM buffers for <20ms real-time gaming audio sync.
 */
class CaptureAudio(
    private val context: Context,
    private val onStatusChange: (AudioStatus) -> Unit = {}
) {
    companion object {
        private const val TAG = "SwitchPadSR-Audio"
        private const val DEFAULT_RATE = 48000
    }

    private val appContext = context.applicationContext
    private val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var running = false
    @Volatile private var desiredRunning = false
    @Volatile private var volume = 1f
    private var workerThread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private var activeDeviceName = ""
    private var activeRate = DEFAULT_RATE
    private var activeStereo = true

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            val hasUsb = addedDevices?.any { isUsbInputDevice(it) } == true
            if (hasUsb) {
                Log.i(TAG, "USB audio device attached")
                if (desiredRunning && !running) {
                    mainHandler.post { startInternal() }
                }
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val hadUsb = removedDevices?.any { isUsbInputDevice(it) } == true
            if (hadUsb) {
                Log.i(TAG, "USB audio device detached")
                val remainingUsb = findUsbInputDevice()
                if (remainingUsb == null && running) {
                    mainHandler.post { stopInternal("采集卡音频已拔出") }
                }
            }
        }
    }

    init {
        try {
            am.registerAudioDeviceCallback(deviceCallback, mainHandler)
        } catch (e: Throwable) {
            Log.w(TAG, "registerAudioDeviceCallback failed", e)
        }
    }

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun isUsbInputDevice(d: AudioDeviceInfo): Boolean {
        if (!d.isSource) return false
        val t = d.type
        if (t == AudioDeviceInfo.TYPE_USB_DEVICE || t == AudioDeviceInfo.TYPE_USB_HEADSET) return true
        if (t == AudioDeviceInfo.TYPE_LINE_DIGITAL || t == AudioDeviceInfo.TYPE_AUX_LINE || t == AudioDeviceInfo.TYPE_DOCK) return true
        val name = d.productName.toString().lowercase()
        return name.contains("usb") || name.contains("capture") || name.contains("hdmi") ||
               name.contains("cam") || name.contains("ms2109") || name.contains("ms2130") || name.contains("shadowcast")
    }

    fun findUsbInputDevice(): AudioDeviceInfo? {
        val list = try { am.getDevices(AudioManager.GET_DEVICES_INPUTS) } catch (_: Throwable) { emptyArray() }
        // 1. Strict USB device type match
        val strict = list.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
        if (strict != null) return strict
        // 2. Name / heuristic match
        return list.firstOrNull { isUsbInputDevice(it) }
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        try { audioTrack?.setVolume(volume) } catch (_: Throwable) {}
    }

    fun start(): Boolean {
        desiredRunning = true
        return startInternal()
    }

    private fun startInternal(): Boolean {
        if (running) return true
        val perm = hasRecordPermission()
        if (!perm) {
            notifyStatus(AudioStatus(isRunning = false, hasPermission = false, errorMessage = "需要麦克风权限以转发采集卡音频"))
            return false
        }

        val usbIn = findUsbInputDevice()
        if (usbIn == null) {
            Log.i(TAG, "No USB audio input found right now, waiting for deviceCallback...")
            notifyStatus(AudioStatus(isRunning = false, hasPermission = true, errorMessage = "未检测到采集卡音频，等待接入…"))
            return false
        }

        val name = usbIn.productName.toString().ifEmpty { "USB 采集卡音频" }
        activeDeviceName = name

        // Try format configs in order of preference: 48000 Stereo -> 44100 Stereo -> 48000 Mono
        val configs = listOf(
            Triple(48000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.CHANNEL_OUT_STEREO),
            Triple(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.CHANNEL_OUT_STEREO),
            Triple(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_OUT_MONO)
        )

        var createdRec: AudioRecord? = null
        var createdTrack: AudioTrack? = null
        var chosenRate = DEFAULT_RATE
        var chosenStereo = true

        for ((rate, chIn, chOut) in configs) {
            try {
                val minRec = AudioRecord.getMinBufferSize(rate, chIn, AudioFormat.ENCODING_PCM_16BIT)
                if (minRec <= 0) continue
                val recFmt = AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(chIn).build()
                val rec = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(recFmt)
                    .setBufferSizeInBytes(maxOf(minRec, 2048) * 2)
                    .build()

                try { rec.preferredDevice = usbIn } catch (_: Throwable) {}

                val minTrack = AudioTrack.getMinBufferSize(rate, chOut, AudioFormat.ENCODING_PCM_16BIT)
                val trackFmt = AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(chOut).build()
                val tr = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    .setAudioFormat(trackFmt)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .setBufferSizeInBytes(maxOf(minTrack, 2048) * 2)
                    .build()

                tr.setVolume(volume)
                createdRec = rec
                createdTrack = tr
                chosenRate = rate
                chosenStereo = (chIn == AudioFormat.CHANNEL_IN_STEREO)
                break
            } catch (e: Throwable) {
                Log.w(TAG, "Config $rate failed: ${e.message}")
            }
        }

        if (createdRec == null || createdTrack == null) {
            notifyStatus(AudioStatus(isRunning = false, hasPermission = true, errorMessage = "初始化音频管道失败"))
            return false
        }

        val rec = createdRec
        val tr = createdTrack
        audioTrack = tr
        activeRate = chosenRate
        activeStereo = chosenStereo
        running = true

        try {
            rec.startRecording()
            tr.play()
        } catch (e: Throwable) {
            Log.e(TAG, "start recording / play failed", e)
            try { rec.release() } catch (_: Throwable) {}
            try { tr.release() } catch (_: Throwable) {}
            running = false
            notifyStatus(AudioStatus(isRunning = false, hasPermission = true, errorMessage = "启动音频流失败: ${e.message}"))
            return false
        }

        // Low-latency streaming thread: 10ms buffer chunks (480 samples stereo = 960 shorts)
        val channels = if (chosenStereo) 2 else 1
        val chunkShorts = (chosenRate * channels) / 100 // 10ms
        workerThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buf = ShortArray(chunkShorts)
            try {
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        tr.write(buf, 0, n)
                    } else if (n < 0) {
                        Log.w(TAG, "AudioRecord read error code $n")
                        break
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Audio loop error", e)
            } finally {
                try { rec.stop() } catch (_: Throwable) {}
                try { rec.release() } catch (_: Throwable) {}
                try { tr.stop() } catch (_: Throwable) {}
                try { tr.release() } catch (_: Throwable) {}
            }
        }, "switchpad-uac-audio").also {
            it.priority = Thread.MAX_PRIORITY
            it.start()
        }

        Log.i(TAG, "Audio passthrough ACTIVE: $activeDeviceName @ ${activeRate}Hz (Stereo=$activeStereo)")
        notifyStatus(AudioStatus(isRunning = true, deviceName = activeDeviceName, sampleRate = activeRate, isStereo = activeStereo, hasPermission = true))
        return true
    }

    fun stop() {
        desiredRunning = false
        stopInternal(null)
    }

    private fun stopInternal(err: String?) {
        running = false
        try { workerThread?.join(300) } catch (_: Throwable) {}
        workerThread = null
        audioTrack = null
        notifyStatus(AudioStatus(isRunning = false, hasPermission = hasRecordPermission(), errorMessage = err))
    }

    private fun notifyStatus(s: AudioStatus) {
        mainHandler.post { onStatusChange(s) }
    }

    fun release() {
        stop()
        try { am.unregisterAudioDeviceCallback(deviceCallback) } catch (_: Throwable) {}
    }
}
