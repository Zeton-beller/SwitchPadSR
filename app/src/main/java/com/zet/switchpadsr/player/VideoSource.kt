package com.zet.switchpadsr.player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.roundToInt

data class VideoInfo(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val buffering: Boolean = false,
    val ended: Boolean = false,
    val error: String? = null
)

/** Local video playback through Media3 ExoPlayer, rendered into the SurfaceTexture owned by [SrRenderer]. */
class VideoSource(
    context: Context,
    private val onSize: (Int, Int) -> Unit,
    private val onInfo: (VideoInfo) -> Unit
) {
    companion object { private const val TAG = "SwitchPadSR-Video" }

    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var surface: Surface? = null
    private var info = VideoInfo()
    private var loop = true

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(appContext).build()
        p.setAudioAttributes(
            AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(), true
        )
        p.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val w = (videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt()
                    onSize(w, videoSize.height)
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) = push { it.copy(isPlaying = isPlaying) }
            override fun onPlaybackStateChanged(state: Int) = push {
                it.copy(
                    buffering = state == Player.STATE_BUFFERING,
                    ended = state == Player.STATE_ENDED,
                    durationMs = p.duration.takeIf { d -> d != C.TIME_UNSET } ?: it.durationMs
                )
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "playback error", error)
                push { it.copy(error = "无法播放: ${error.errorCodeName}", isPlaying = false) }
            }
        })
        surface?.let { p.setVideoSurface(it) }
        player = p
        return p
    }

    private fun push(f: (VideoInfo) -> VideoInfo) {
        info = f(info)
        onInfo(info)
    }

    fun open(uri: Uri, startPositionMs: Long = 0L, loopVideo: Boolean = true) {
        val p = ensurePlayer()
        loop = loopVideo
        p.repeatMode = if (loopVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        info = VideoInfo(speed = info.speed)
        onInfo(info)
        p.setMediaItem(MediaItem.fromUri(uri), startPositionMs)
        p.setPlaybackSpeed(info.speed)
        p.prepare()
        p.playWhenReady = true
    }

    fun setSurface(s: Surface?) {
        surface = s
        player?.setVideoSurface(s)
    }

    fun togglePlay() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) { p.seekTo(0); p.play() }
        else if (p.isPlaying) p.pause() else p.play()
    }

    fun pause() { player?.pause() }
    fun seekTo(ms: Long) { player?.seekTo(ms.coerceAtLeast(0)) }
    fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val dur = if (p.duration == C.TIME_UNSET) Long.MAX_VALUE else p.duration
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0, dur))
    }
    fun setSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        push { it.copy(speed = speed) }
    }
    fun setLoop(l: Boolean) {
        loop = l
        player?.repeatMode = if (l) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun positionMs(): Long = player?.currentPosition ?: 0L
    fun durationMs(): Long = player?.duration?.takeIf { it != C.TIME_UNSET } ?: 0L
    fun hasMedia(): Boolean = player?.currentMediaItem != null

    /** Polled by the UI to refresh the progress bar. */
    fun poll() {
        val p = player ?: return
        push { it.copy(positionMs = p.currentPosition, durationMs = p.duration.takeIf { d -> d != C.TIME_UNSET } ?: it.durationMs) }
    }

    fun stop() {
        player?.let { it.pause(); it.stop(); it.clearMediaItems() }
        info = VideoInfo(speed = info.speed)
        onInfo(info)
    }

    fun release() {
        player?.release()
        player = null
    }
}
