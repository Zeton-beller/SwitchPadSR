package com.zet.switchpadsr.engine

import android.view.Surface
import java.nio.ByteBuffer

object NativeEngine {
    init {
        System.loadLibrary("switchpadsr_engine")
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
