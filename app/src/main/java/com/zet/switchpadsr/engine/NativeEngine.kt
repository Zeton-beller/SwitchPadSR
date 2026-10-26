package com.zet.switchpadsr.engine

import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

object NativeEngine {
    private const val TAG = "SwitchPadSR-Native"

    var isLoaded: Boolean = false
        private set

    var loadError: String? = null
        private set

    init {
        try {
            System.loadLibrary("switchpadsr_engine")
            isLoaded = true
            Log.i(TAG, "Native library 'switchpadsr_engine' loaded successfully")
        } catch (e: Throwable) {
            loadError = e.message ?: e.toString()
            Log.e(TAG, "FATAL: Failed to load 'switchpadsr_engine' native library", e)
            isLoaded = false
        }
    }

    fun init(surface: Surface, width: Int, height: Int): Boolean {
        if (!isLoaded) {
            Log.e(TAG, "Cannot init NativeEngine: native library not loaded")
            return false
        }
        return try {
            nativeInit(surface, width, height)
        } catch (e: Throwable) {
            Log.e(TAG, "nativeInit threw exception", e)
            false
        }
    }

    external fun nativeInit(surface: Surface, width: Int, height: Int): Boolean
    external fun nativeDestroy()

    // Mode & Algorithm selection
    external fun nativeSetAlgorithm(algo: Int) // 0: SGSR, 1: FSR, 2: Anime4K, 3: CRT, 4: Bicubic
    external fun nativeSetDisplayMode(mode: Int) // 0: Letterbox, 1: Ambilight, 2: Crop, 3: Stretch
    external fun nativeSetTargetResolution(width: Int, height: Int)

    // Algorithm fine tuning parameters
    external fun nativeSetSGSRParams(threshold: Float, sharpness: Float, useDir: Boolean)
    external fun nativeSetFSRParams(sharpness: Float, dering: Boolean)
    external fun nativeSetAnime4KParams(strength: Float, mode: Int)
    external fun nativeSetCRTParams(scanline: Float, curvature: Float, mask: Float)
    external fun nativeSetSplitScreen(enabled: Boolean, position: Float)

    // UVC & Video Ingestion
    external fun nativeStartUvcCapture(fd: Int, width: Int, height: Int, fps: Int): Boolean
    external fun nativeStopUvcCapture()
    external fun nativeRenderFrame()
    external fun nativeFeedVideoFrame(directBuffer: ByteBuffer, width: Int, height: Int, stride: Int)

    // Performance metrics: [0]=fps, [1]=gpu_ms, [2]=latency_ms, [3]=total_frames, [4]=active_algo
    external fun nativeGetMetrics(outArray: FloatArray)
}
