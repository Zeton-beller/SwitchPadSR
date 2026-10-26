package com.zet.switchpadsr.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocalVideoPlayer(
    private val context: Context,
    private val onPositionUpdate: (currentMs: Long, durationMs: Long) -> Unit,
    private val onVideoSizeChanged: (width: Int, height: Int) -> Unit,
    private val onCompletion: () -> Unit
) {
    companion object {
        private const val TAG = "SwitchPadSR-VideoPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    var isPlaying: Boolean = false
        private set

    var currentUri: Uri? = null
        private set

    fun openVideo(uri: Uri, surface: Surface) {
        release()
        currentUri = uri
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setSurface(surface)
                isLooping = true
                setOnPreparedListener { mp ->
                    Log.i(TAG, "Video prepared. Size: ${mp.videoWidth}x${mp.videoHeight}, Duration: ${mp.duration}ms")
                    onVideoSizeChanged(mp.videoWidth, mp.videoHeight)
                    mp.start()
                    isPlaying = true
                    startProgressTracker()
                }
                setOnCompletionListener {
                    isPlaying = false
                    onCompletion()
                }
                setOnVideoSizeChangedListener { _, w, h ->
                    if (w > 0 && h > 0) {
                        onVideoSizeChanged(w, h)
                    }
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open video uri: $uri", e)
        }
    }

    fun play() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                isPlaying = true
                startProgressTracker()
            }
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
                progressJob?.cancel()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
    }

    fun setSpeed(speed: Float) {
        try {
            mediaPlayer?.let {
                val params = it.playbackParams
                params.speed = speed
                it.playbackParams = params
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set playback speed", e)
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && isPlaying) {
                mediaPlayer?.let { mp ->
                    try {
                        val current = mp.currentPosition.toLong()
                        val duration = mp.duration.toLong()
                        onPositionUpdate(current, duration)
                    } catch (e: Exception) {
                        // ignore when transitioning
                    }
                }
                delay(200)
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer", e)
        }
        mediaPlayer = null
        isPlaying = false
    }
}
