package com.zet.switchpadsr.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Pass-through of the capture card's USB audio (UAC) input to the speaker.
 * Most Switch capture cards enumerate a USB audio device next to the UVC video device.
 */
class CaptureAudio(private val context: Context) {
    companion object { private const val TAG = "SwitchPadSR-Audio"; private const val RATE = 48000 }

    @Volatile private var running = false
    private var thread: Thread? = null
    @Volatile private var volume = 1f
    private var track: AudioTrack? = null

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        track?.setVolume(volume)
    }

    /** @return true if a USB audio source was found and streaming started. */
    fun start(): Boolean {
        if (running) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO not granted"); return false
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val usbIn = am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (usbIn == null) { Log.w(TAG, "no USB audio input device"); return false }
        try {
            val chMask = AudioFormat.CHANNEL_IN_STEREO
            val fmt = AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(chMask).build()
            val minRec = AudioRecord.getMinBufferSize(RATE, chMask, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(maxOf(minRec, 4096) * 2)
                .build()
            rec.preferredDevice = usbIn
            val outFmt = AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
            val minTrack = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            val tr = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                .setAudioFormat(outFmt)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(maxOf(minTrack, 4096))
                .build()
            tr.setVolume(volume)
            track = tr
            running = true
            rec.startRecording()
            tr.play()
            thread = Thread({
                val buf = ShortArray(1920)
                try {
                    while (running) {
                        val n = rec.read(buf, 0, buf.size)
                        if (n > 0) tr.write(buf, 0, n)
                        else if (n < 0) break
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "audio loop", e)
                } finally {
                    try { rec.stop() } catch (_: Throwable) {}
                    rec.release()
                    try { tr.stop() } catch (_: Throwable) {}
                    tr.release()
                }
            }, "capture-audio").also { it.priority = Thread.MAX_PRIORITY; it.start() }
            Log.i(TAG, "audio passthrough started from ${usbIn.productName}")
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "audio start failed", e)
            running = false
            return false
        }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        track = null
    }
}
