package com.zet.switchpadsr.model

enum class InputSourceMode { CAPTURE_CARD, LOCAL_VIDEO }

enum class AiBackbone(val id: Int, val displayName: String, val tagline: String) {
    CUNNY(0, "CuNNy 4x16 DS", "官方 CuNNy 游戏/二次元CG专属特调：锐利文字与无白边线稿重构 (推荐)"),
    ANIME4K_CNN(2, "Anime4K v4.0.1 CNN", "官方 Anime4K 卷积神经网络：经典动漫强力线稿收拢与去模糊"),
    FSRCNNX(1, "FSRCNNX 8-0-4-1", "官方 13 层全权重网络：2.8k 参数，原生高保真超分");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: CUNNY
    }
}

enum class AiPostProcessor(val id: Int, val displayName: String, val tagline: String) {
    SGSR(0, "高通 SGSR 边缘定向", "12-tap Lanczos2 方向自适应对比度强化，刀刻般锐利 (推荐)"),
    RCAS(1, "AMD FSR RCAS 锐化", "对比度自适应微观细节锐化与反振铃控制"),
    ANIME4K(2, "Anime4K 墨线细化", "Thin_HQ 双向细线收拢 + Darken 轮廓墨线加深"),
    OFF(3, "仅纯 AI 原生输出", "不加任何后级边缘滤镜，保留纯净神经重构");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: SGSR
    }
}

enum class SRAlgorithmType(val id: Int, val displayName: String, val tagline: String) {
    SGSR(1, "经典高通 SGSR", "单阶段：12-tap 快速 Lanczos2 方向自适应超分 (极速 120 FPS 推荐)"),
    AI_HYBRID(0, "AI 神经复合超分", "双阶段：AI 深度神经网络 (CuNNy / Anime4K / FSRCNNX) + 自选后处理锐化增强"),
    FSR(2, "经典 AMD FSR 1.0", "单阶段：12-tap EASU 空间放大 + RCAS 自适应锐化"),
    ANIME4K(3, "经典 Anime4K 风格", "单阶段：Thin_HQ 双向细线收拢 + Deblur_DoG 去模糊 + Darken 墨线加深"),
    CRT(4, "复古 CRT", "扫描线 + 荧光粉栅格 + 微弧曲面，怀旧像素游戏"),
    BICUBIC(5, "双三次", "Catmull-Rom 4-tap 平滑插值，无锐化基准"),
    OFF(6, "原画直通", "不做超分，仅双线性缩放到屏幕");

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

    // AI Pipeline
    val aiBackbone: AiBackbone = AiBackbone.CUNNY,
    val aiPostProcessor: AiPostProcessor = AiPostProcessor.SGSR,
    val aiSharpness: Float = 0.5f,                // 0 .. 1 (neural recon detail)
    val fsrcnnxSharpness: Float = 0.5f,           // compatibility

    // Post-processor & standalone parameters:
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
