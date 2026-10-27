package com.zet.switchpadsr.model

enum class InputSourceMode { CAPTURE_CARD, LOCAL_VIDEO }

enum class SRAlgorithmType(val id: Int, val displayName: String, val tagline: String) {
    FSRCNNX(0, "FSRCNNX 深度神经", "AI 卷积神经网络 (FSRCNNX GLSL) 2x 结构重构 + RCAS 自适应锐化"),
    SGSR(1, "SGSR 边缘定向", "高通官方 Snapdragon GSR v1：12-tap 快速 Lanczos2 方向自适应超分"),
    FSR(2, "FSR 1.0", "AMD 官方 FidelityFX FSR 1.0：12-tap EASU 空间放大 + RCAS 自适应锐化"),
    ANIME4K(3, "Anime4K 风格", "bloc97 官方 Anime4K：Thin_HQ 双向细线收拢 + Deblur_DoG 去模糊 + Darken 墨线加深"),
    CRT(4, "复古 CRT", "扫描线 + 荧光粉栅格 + 微弧曲面，怀旧像素游戏"),
    BICUBIC(5, "双三次", "Catmull-Rom 4-tap 平滑插值，无锐化基准"),
    OFF(6, "原画直通", "不做超分，仅双线性缩放到屏幕");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: FSRCNNX
    }
}

enum class DisplayAspectMode(val id: Int, val displayName: String) {
    LETTERBOX(0, "等比黑边"),
    AMBILIGHT(1, "氛围光"),
    CENTER_CROP(2, "填满裁剪"),
    STRETCH(3, "拉伸");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: LETTERBOX
    }
}

/** height == 0 : follow the on-screen size of the video (default, no extra resampling). */
enum class TargetResolutionPreset(val label: String, val height: Int) {
    AUTO("跟随屏幕", 0),
    P1080("1080p", 1080),
    P1440("1440p", 1440),
    P1800("1800p", 1800),
    P2160("4K", 2160),
    CUSTOM("自定义", -1);

    companion object {
        fun fromName(n: String?) = entries.firstOrNull { it.name == n } ?: AUTO
    }
}

data class RenderSettings(
    val algorithm: SRAlgorithmType = SRAlgorithmType.FSRCNNX,
    val targetResolution: TargetResolutionPreset = TargetResolutionPreset.AUTO,
    val customScaleFactor: Float = 1.5f,          // x source height, 1.0 .. 4.0
    val displayMode: DisplayAspectMode = DisplayAspectMode.AMBILIGHT,

    // FSRCNNX (AI Neural)
    val fsrcnnxSharpness: Float = 0.5f,           // 0 .. 1

    // SGSR
    val sgsrThreshold: Float = 4.0f,              // 1 .. 16
    val sgsrSharpness: Float = 1.5f,              // 1 .. 2
    val sgsrUseDirection: Boolean = true,

    // FSR
    val fsrSharpness: Float = 0.8f,               // 0 .. 1
    val fsrDering: Boolean = true,

    // Anime4K style
    val anime4kStrength: Float = 1.0f,            // 0.5 .. 2
    val anime4kMode: Int = 0,                     // 0 A, 1 B, 2 C

    // CRT
    val crtScanline: Float = 0.35f,
    val crtCurvature: Float = 0.04f,
    val crtMask: Float = 0.25f,

    // A/B compare & continuous zoom with draggable pan center
    val splitScreenEnabled: Boolean = false,
    val splitPosition: Float = 0.5f,
    val zoomFactor: Float = 1.0f,                 // 1.0 .. 8.0
    val zoomCenterX: Float = 0.5f,                // 0.0 .. 1.0
    val zoomCenterY: Float = 0.5f,                // 0.0 .. 1.0

    // misc
    val showHud: Boolean = true,
    val captureAudio: Boolean = true,
    val audioVolume: Float = 1.0f,
    val loopVideo: Boolean = true
)
