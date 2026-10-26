package com.zet.switchpadsr.model

enum class InputSourceMode { CAPTURE_CARD, LOCAL_VIDEO }

enum class SRAlgorithmType(val id: Int, val displayName: String, val tagline: String) {
    SGSR(0, "SGSR 边缘定向", "骁龙 GSR 思路：沿边缘方向的 Lanczos2 重建，3D 写实游戏首选"),
    FSR(1, "FSR 1.0", "EASU 边缘自适应放大 + RCAS 对比度自适应锐化，通用性强"),
    ANIME4K(2, "Anime4K 风格", "双三次放大后做线条还原/细线加深/平坦区降噪，卡通画风"),
    CRT(3, "复古 CRT", "扫描线 + 荧光粉栅格 + 微弧曲面，怀旧像素游戏"),
    BICUBIC(4, "双三次", "Catmull-Rom 平滑插值，无锐化基准"),
    OFF(5, "原画直通", "不做超分，仅双线性缩放到屏幕");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: SGSR
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
    val algorithm: SRAlgorithmType = SRAlgorithmType.SGSR,
    val targetResolution: TargetResolutionPreset = TargetResolutionPreset.AUTO,
    val customScaleFactor: Float = 1.5f,          // x source height, 1.0 .. 4.0
    val displayMode: DisplayAspectMode = DisplayAspectMode.AMBILIGHT,

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

    // A/B compare
    val splitScreenEnabled: Boolean = false,
    val splitPosition: Float = 0.5f,

    // misc
    val showHud: Boolean = true,
    val captureAudio: Boolean = true,
    val audioVolume: Float = 1.0f,
    val loopVideo: Boolean = true
)
