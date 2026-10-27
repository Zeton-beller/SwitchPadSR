package com.zet.switchpadsr.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zet.switchpadsr.AppViewModel
import com.zet.switchpadsr.model.*
import com.zet.switchpadsr.ui.theme.*

private enum class Tab(val label: String) {
    ALGO("超分算法"),
    VIEW("画面显示"),
    AUDIO("音频设备")
}

@Composable
fun SettingsPanel(
    vm: AppViewModel,
    onRequestAudioPermission: () -> Unit,
    onClose: () -> Unit
) {
    var tab by remember { mutableStateOf(Tab.ALGO) }
    val s = vm.settings

    Column(
        Modifier.fillMaxHeight().width(410.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .background(Color(0xF0080D1A)).border(1.dp, Color(0x33FFFFFF))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        // Drawer Header
        Row(
            Modifier.padding(start = 22.dp, end = 14.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("画质与设置", color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x1AFFFFFF))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, "关闭", tint = TextMid, modifier = Modifier.size(20.dp))
            }
        }

        // Tab Selector
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)).background(Color(0x1AFFFFFF)).padding(4.dp)
        ) {
            Tab.entries.forEach { t ->
                val sel = t == tab
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(if (sel) Cyan.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable { tab = t }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        t.label,
                        color = if (sel) Cyan else TextMid,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Tab Content
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)
        ) {
            when (tab) {
                Tab.ALGO -> AlgoTab(vm)
                Tab.VIEW -> ViewTab(vm)
                Tab.AUDIO -> AudioTab(vm, onRequestAudioPermission)
            }
        }
    }
}

@Composable
private fun AlgoTab(vm: AppViewModel) {
    val s = vm.settings

    SectionTitle("场景一键预设")
    ChipRow(
        listOf(
            "realistic" to "🎮 3D 游戏",
            "cartoon" to "🌸 卡通动漫",
            "general" to "⚡ 通用画质",
            "retro" to "📺 复古像素",
            "off" to "🍃 原画直通"
        ),
        selected = "",
        onSelect = { p ->
            vm.update {
                when (p) {
                    "realistic" -> it.copy(algorithm = SRAlgorithmType.SGSR, sgsrThreshold = 8f, sgsrSharpness = 1.5f, sgsrUseDirection = true)
                    "cartoon" -> it.copy(algorithm = SRAlgorithmType.ANIME4K, anime4kStrength = 1.0f, anime4kMode = 0)
                    "general" -> it.copy(algorithm = SRAlgorithmType.FSR, fsrSharpness = 0.8f, fsrDering = true)
                    "retro" -> it.copy(algorithm = SRAlgorithmType.CRT)
                    else -> it.copy(algorithm = SRAlgorithmType.OFF)
                }
            }
        }
    )

    SectionTitle("选择算法与微调")
    SRAlgorithmType.entries.forEach { a ->
        val sel = a == s.algorithm
        Column(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp))
                .background(if (sel) Cyan.copy(alpha = 0.12f) else Color(0x12FFFFFF))
                .border(1.dp, if (sel) Cyan.copy(alpha = 0.8f) else Color(0x18FFFFFF), RoundedCornerShape(16.dp))
                .clickable { vm.update { it.copy(algorithm = a) } }
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.displayName, color = if (sel) Cyan else TextHi, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                if (sel) {
                    Pill("生效中", color = Cyan, bg = Cyan.copy(alpha = 0.20f))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(a.tagline, color = TextMid, fontSize = 12.sp, lineHeight = 17.sp)

            // Inline expandable tuning controls right inside the card
            AnimatedVisibility(
                visible = sel && a != SRAlgorithmType.BICUBIC && a != SRAlgorithmType.OFF,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(Modifier.padding(top = 12.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
                    Spacer(Modifier.height(10.dp))
                    AlgoInlineParams(vm, s)
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x1AFFFFFF))
            .clickable { vm.resetAlgorithmParams() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text("恢复算法默认参数", color = TextMid, fontSize = 13.sp)
    }
}

@Composable
private fun AlgoInlineParams(vm: AppViewModel, s: RenderSettings) {
    when (s.algorithm) {
        SRAlgorithmType.SGSR -> {
            LabeledSlider("边缘锐化倍率", s.sgsrSharpness, 1f..2f, "%.2fx", "高通原版 EdgeSharpness：强化 3D 轮廓清晰度") { v -> vm.update { it.copy(sgsrSharpness = v) } }
            LabeledSlider("边缘识别阈值", s.sgsrThreshold, 1f..16f, "%.1f", "越小越敏感，越大越保留平坦区域") { v -> vm.update { it.copy(sgsrThreshold = v) } }
            ToggleRow("边缘方向自适应", "根据边缘走向旋转并拉长核函数", s.sgsrUseDirection) { v -> vm.update { it.copy(sgsrUseDirection = v) } }
        }
        SRAlgorithmType.FSR -> {
            LabeledSlider("RCAS 锐化强度", s.fsrSharpness, 0f..1f, "%.2f", "0 = 关闭锐化，1 = 最锐利") { v -> vm.update { it.copy(fsrSharpness = v) } }
            ToggleRow("去振铃 (Dering)", "抑制高对比度文字和光晕周边的白边", s.fsrDering) { v -> vm.update { it.copy(fsrDering = v) } }
        }
        SRAlgorithmType.ANIME4K -> {
            LabeledSlider("线条细化强度", s.anime4kStrength, 0.5f..2f, "%.2f", "控制 Thin_HQ 双向细线收拢与去模糊力度") { v -> vm.update { it.copy(anime4kStrength = v) } }
            Text("处理模式", color = TextHi, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
            ChipRow(
                listOf(0 to "A 还原去模糊", 1 to "B 还原+平坦降噪", 2 to "C 强力线条加深"),
                s.anime4kMode
            ) { m -> vm.update { it.copy(anime4kMode = m) } }
        }
        SRAlgorithmType.CRT -> {
            LabeledSlider("扫描线", s.crtScanline, 0f..1f) { v -> vm.update { it.copy(crtScanline = v) } }
            LabeledSlider("屏幕微弧度", s.crtCurvature, 0f..0.3f) { v -> vm.update { it.copy(crtCurvature = v) } }
            LabeledSlider("荧光粉栅格", s.crtMask, 0f..1f) { v -> vm.update { it.copy(crtMask = v) } }
        }
        else -> {}
    }
}

@Composable
private fun ViewTab(vm: AppViewModel) {
    val s = vm.settings

    SectionTitle("目标渲染分辨率")
    ChipRow(
        TargetResolutionPreset.entries.map { it to it.label },
        s.targetResolution
    ) { r -> vm.update { it.copy(targetResolution = r) } }

    if (s.targetResolution == TargetResolutionPreset.CUSTOM) {
        LabeledSlider("自定义放大倍率 (相对源画面)", s.customScaleFactor, 1f..4f, "%.2fx") { v -> vm.update { it.copy(customScaleFactor = v) } }
    }
    val st = vm.stats
    if (st.outW > 0) {
        Text("当前 GPU 输出 FBO: ${st.outW}×${st.outH}", color = Cyan, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }

    SectionTitle("画面比例")
    ChipRow(
        DisplayAspectMode.entries.map { it to it.displayName },
        s.displayMode
    ) { m -> vm.update { it.copy(displayMode = m) } }

    SectionTitle("A/B 对比与细节放大")
    ToggleRow("分屏对比", "左侧：超分结果　右侧：原画直通", s.splitScreenEnabled) { v -> vm.update { it.copy(splitScreenEnabled = v) } }
    if (s.splitScreenEnabled) {
        LabeledSlider("分割线位置", s.splitPosition, 0.05f..0.95f, "%.2f") { v -> vm.update { it.copy(splitPosition = v) } }
    }

    SectionTitle("细节放大检视与平移")
    LabeledSlider("放大倍率", s.zoomFactor, 1f..8f, "%.1fx", "支持在画面上单指拖拽平移放大区域，双指捏合缩放") { z ->
        vm.update { it.copy(zoomFactor = z) }
    }
    ChipRow(
        listOf(1.0f to "1x 原图", 2.0f to "2x 细节", 4.0f to "4x 像素", 8.0f to "8x 极致"),
        s.zoomFactor
    ) { z -> vm.update { it.copy(zoomFactor = z) } }

    if (s.zoomFactor > 1f) {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x1EFFFFFF))
                .clickable { vm.update { it.copy(zoomCenterX = 0.5f, zoomCenterY = 0.5f) } }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Refresh, null, tint = Cyan, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("重置放大中心到画面中央", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AudioTab(vm: AppViewModel, onRequestAudioPermission: () -> Unit) {
    val s = vm.settings
    val audioStatus = vm.audioStatus
    val hasPerm = vm.hasAudioPermission()

    SectionTitle("采集卡音频直通")
    ToggleRow("启用扬声器直通", "将采集卡 USB 音频实时转发到平板扬声器", s.captureAudio) { v ->
        if (v && !hasPerm) {
            onRequestAudioPermission()
        }
        vm.update { it.copy(captureAudio = v) }
    }

    LabeledSlider("直通音量", s.audioVolume, 0f..1f, "%.2f") { v -> vm.update { it.copy(audioVolume = v) } }

    SectionTitle("音频设备状态")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Color(0x14FFFFFF)).border(1.dp, Color(0x1EFFFFFF), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("USB 音频通道", color = TextMid, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            if (audioStatus.isRunning) {
                Pill("● 正在输出", color = Mint, bg = Mint.copy(alpha = 0.18f))
            } else {
                Pill("○ 未激活", color = TextLow, bg = Color(0x1AFFFFFF))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (audioStatus.deviceName.isNotEmpty()) "${audioStatus.deviceName} (${audioStatus.sampleRate}Hz ${if (audioStatus.isStereo) "立体声" else "单声道"})"
            else if (!hasPerm) "未获得录音权限"
            else "等待 USB 采集卡音频输入",
            color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
        )

        if (!hasPerm) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(AccentBrush)
                    .clickable { onRequestAudioPermission() }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("申请麦克风权限", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    SectionTitle("视频源管理")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x1AFFFFFF))
            .clickable { vm.capture.reconnect() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Refresh, null, tint = Cyan, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("重新检测与连接采集卡", color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }

    SectionTitle("性能与播放")
    ToggleRow("性能监视 HUD", "在屏幕顶部显示实时 FPS 与 GPU 耗时", s.showHud) { v -> vm.update { it.copy(showHud = v) } }
    ToggleRow("循环播放本地视频", null, s.loopVideo) { v -> vm.update { it.copy(loopVideo = v) } }
}
