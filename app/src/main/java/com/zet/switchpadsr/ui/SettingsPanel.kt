package com.zet.switchpadsr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import kotlin.math.roundToInt

private enum class Tab(val label: String) { ALGO("算法"), VIEW("画面"), TUNE("调参") }

@Composable
fun SettingsPanel(vm: AppViewModel, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.ALGO) }
    val s = vm.settings
    Column(
        Modifier.fillMaxHeight().width(400.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .background(Glass).border(1.dp, Outline)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("画质设置", color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "关闭", tint = TextMid)
            }
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)).background(Color(0x1AFFFFFF)).padding(4.dp)
        ) {
            Tab.entries.forEach { t ->
                val sel = t == tab
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(if (sel) Cyan.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable { tab = t }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) { Text(t.label, color = if (sel) Cyan else TextMid, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            when (tab) {
                Tab.ALGO -> AlgoTab(vm)
                Tab.VIEW -> ViewTab(vm)
                Tab.TUNE -> TuneTab(vm)
            }
        }
    }
}

@Composable
private fun AlgoTab(vm: AppViewModel) {
    val s = vm.settings
    SectionTitle("一键预设")
    ChipRow(
        listOf("realistic" to "写实 3D", "cartoon" to "卡通动漫", "general" to "通用", "retro" to "复古像素"),
        selected = "", onSelect = { p ->
            vm.update {
                when (p) {
                    "realistic" -> it.copy(algorithm = SRAlgorithmType.SGSR, sgsrThreshold = 4f, sgsrSharpness = 1.5f, sgsrUseDirection = true)
                    "cartoon" -> it.copy(algorithm = SRAlgorithmType.ANIME4K, anime4kStrength = 1.0f, anime4kMode = 0)
                    "general" -> it.copy(algorithm = SRAlgorithmType.FSR, fsrSharpness = 0.8f, fsrDering = true)
                    else -> it.copy(algorithm = SRAlgorithmType.CRT)
                }
            }
        }
    )
    SectionTitle("超分算法")
    SRAlgorithmType.entries.forEach { a ->
        val sel = a == s.algorithm
        Column(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp))
                .background(if (sel) Cyan.copy(alpha = 0.14f) else Color(0x14FFFFFF))
                .border(1.dp, if (sel) Cyan else Color.Transparent, RoundedCornerShape(16.dp))
                .clickable { vm.update { it.copy(algorithm = a) } }.padding(14.dp)
        ) {
            Text(a.displayName, color = if (sel) Cyan else TextHi, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(a.tagline, color = TextMid, fontSize = 12.sp, lineHeight = 17.sp)
            if (sel && a != SRAlgorithmType.BICUBIC && a != SRAlgorithmType.OFF) {
                Spacer(Modifier.height(10.dp))
                AlgoParamsControls(vm, s)
            }
        }
    }
}

@Composable
private fun ViewTab(vm: AppViewModel) {
    val s = vm.settings
    SectionTitle("画面比例")
    ChipRow(DisplayAspectMode.entries.map { it to it.displayName }, s.displayMode) { m -> vm.update { it.copy(displayMode = m) } }

    SectionTitle("目标分辨率")
    ChipRow(TargetResolutionPreset.entries.map { it to it.label }, s.targetResolution) { r -> vm.update { it.copy(targetResolution = r) } }
    if (s.targetResolution == TargetResolutionPreset.CUSTOM) {
        LabeledSlider("放大倍率 (相对源高度)", s.customScaleFactor, 1f..4f, "%.2fx") { v -> vm.update { it.copy(customScaleFactor = v) } }
    }
    val st = vm.stats
    if (st.outW > 0) Text("当前输出 ${st.outW}×${st.outH}", color = TextLow, fontSize = 11.sp)

    SectionTitle("A/B 对比与细节放大")
    ToggleRow("分屏对比", "左：超分结果　右：原画直通", s.splitScreenEnabled) { v -> vm.update { it.copy(splitScreenEnabled = v) } }
    if (s.splitScreenEnabled) LabeledSlider("分割线位置", s.splitPosition, 0.05f..0.95f, "%.2f") { v -> vm.update { it.copy(splitPosition = v) } }
    Text("细节放大检视", color = TextHi, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
    ChipRow(listOf(1.0f to "1x 正常", 2.0f to "2x 放大", 4.0f to "4x 像素级"), s.zoomFactor) { z -> vm.update { it.copy(zoomFactor = z) } }

    SectionTitle("音频与显示")
    ToggleRow("采集卡音频直通", "转发采集卡 USB 音频到扬声器（需录音权限）", s.captureAudio) { v -> vm.update { it.copy(captureAudio = v) } }
    LabeledSlider("音量", s.audioVolume, 0f..1f, "%.2f") { v -> vm.update { it.copy(audioVolume = v) } }
    ToggleRow("性能 HUD", null, s.showHud) { v -> vm.update { it.copy(showHud = v) } }
    ToggleRow("循环播放本地视频", null, s.loopVideo) { v -> vm.update { it.copy(loopVideo = v) } }
}

@Composable
private fun TuneTab(vm: AppViewModel) {
    val s = vm.settings
    SectionTitle("${s.algorithm.displayName} 参数")
    AlgoParamsControls(vm, s)
    Spacer(Modifier.height(18.dp))
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x1AFFFFFF)).clickable { vm.resetAlgorithmParams() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) { Text("恢复默认参数", color = TextMid, fontSize = 13.sp) }
}

@Composable
private fun AlgoParamsControls(vm: AppViewModel, s: RenderSettings) {
    when (s.algorithm) {
        SRAlgorithmType.SGSR -> {
            LabeledSlider("边缘阈值", s.sgsrThreshold, 1f..16f, "%.1f", "越小越敏感，越大越保留平坦区") { v -> vm.update { it.copy(sgsrThreshold = v) } }
            LabeledSlider("锐化强度", s.sgsrSharpness, 1f..2f, "%.2f") { v -> vm.update { it.copy(sgsrSharpness = v) } }
            ToggleRow("边缘方向自适应", "沿边缘拉长、垂直边缘收紧核", s.sgsrUseDirection) { v -> vm.update { it.copy(sgsrUseDirection = v) } }
        }
        SRAlgorithmType.FSR -> {
            LabeledSlider("RCAS 锐化", s.fsrSharpness, 0f..1f, "%.2f", "0 = 关闭锐化，1 = 最锐利") { v -> vm.update { it.copy(fsrSharpness = v) } }
            ToggleRow("去振铃 (Dering)", "开启抑制光晕，关闭增强微对比度", s.fsrDering) { v -> vm.update { it.copy(fsrDering = v) } }
        }
        SRAlgorithmType.ANIME4K -> {
            LabeledSlider("线条强度", s.anime4kStrength, 0.5f..2f, "%.2f", "控制边缘收拢与线条锐化力度") { v -> vm.update { it.copy(anime4kStrength = v) } }
            Text("模式", color = TextHi, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            ChipRow(listOf(0 to "A 还原", 1 to "B 还原+降噪", 2 to "C 线条加深"), s.anime4kMode) { m -> vm.update { it.copy(anime4kMode = m) } }
        }
        SRAlgorithmType.CRT -> {
            LabeledSlider("扫描线", s.crtScanline, 0f..1f) { v -> vm.update { it.copy(crtScanline = v) } }
            LabeledSlider("屏幕弧度", s.crtCurvature, 0f..0.3f) { v -> vm.update { it.copy(crtCurvature = v) } }
            LabeledSlider("荧光粉栅格", s.crtMask, 0f..1f) { v -> vm.update { it.copy(crtMask = v) } }
        }
        else -> Text("该模式没有可调参数。", color = TextMid, fontSize = 13.sp)
    }
}
