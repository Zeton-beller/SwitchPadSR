package com.zet.switchpadsr.model

enum class InputSourceMode {
    CAPTURE_CARD,
    LOCAL_VIDEO
}

enum class SRAlgorithmType(val id: Int, val displayName: String, val desc: String) {
    SGSR_1_0(0, "高通 SGSR 1.0", "Adreno专有12-Tap单Pass边缘定向超分，极速0.2ms，适合3D写实游戏"),
    ANIME4K(2, "Anime4K v4.0", "二次元线稿卷积重构与去噪，绝配塞尔达/马力欧等卡通画风"),
    AMD_FSR(1, "AMD FSR 1.0", "EASU空间缩放 + RCAS对比自适应锐化，通用性极佳"),
    RETRO_CRT(3, "复古 CRT 显像管", "经典光栅扫描线、荧光粉掩模与微弧曲率，怀旧像素首选"),
    BICUBIC(4, "双三次滤波", "经典高质量平滑插值，作为无锐化基准参照")
}

enum class DisplayAspectMode(val id: Int, val displayName: String) {
    LETTERBOX(0, "等比黑边 (推荐 1:1)"),
    AMBILIGHT(1, "动态氛围光 (Ambilight)"),
    CENTER_CROP(2, "居中裁剪 (填满3:2)"),
    STRETCH(3, "强制拉伸")
}

enum class TargetResolutionPreset(val width: Int, val height: Int, val label: String) {
    NATIVE_3_2K(3200, 1800, "原生 3.2K (3200×1800)"),
    QHD_2K(2560, 1440, "2K 均衡 (2560×1440)"),
    FHD_1080P(1920, 1080, "1080p 直通原画 (1:1)"),
    CUSTOM(0, 0, "自定义倍率")
}

data class RenderSettings(
    val inputMode: InputSourceMode = InputSourceMode.CAPTURE_CARD,
    val algorithm: SRAlgorithmType = SRAlgorithmType.SGSR_1_0,
    val targetResolution: TargetResolutionPreset = TargetResolutionPreset.NATIVE_3_2K,
    val customScaleFactor: Float = 1.667f,
    val displayMode: DisplayAspectMode = DisplayAspectMode.LETTERBOX,

    // SGSR 1.0 Parameters
    val sgsrThreshold: Float = 4.0f,      // 1.0 to 16.0
    val sgsrSharpness: Float = 2.0f,      // 1.0 to 2.0
    val sgsrUseDirection: Boolean = true,

    // AMD FSR 1.0 Parameters
    val fsrSharpness: Float = 0.8f,       // 0.0 to 1.0
    val fsrDering: Boolean = true,

    // Anime4K Parameters
    val anime4kStrength: Float = 1.0f,    // 0.5 to 2.0
    val anime4kMode: Int = 0,             // 0: Mode A, 1: Mode B, 2: Mode C

    // CRT Parameters
    val crtScanline: Float = 0.35f,       // 0.0 to 1.0
    val crtCurvature: Float = 0.04f,      // 0.0 to 0.3
    val crtMask: Float = 0.25f,           // 0.0 to 1.0

    // Split-screen A/B comparison
    val splitScreenEnabled: Boolean = false,
    val splitPosition: Float = 0.5f,

    // HUD Display
    val showHud: Boolean = true
)
