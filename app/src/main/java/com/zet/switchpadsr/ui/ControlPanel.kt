package com.zet.switchpadsr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zet.switchpadsr.model.DisplayAspectMode
import com.zet.switchpadsr.model.InputSourceMode
import com.zet.switchpadsr.model.RenderSettings
import com.zet.switchpadsr.model.SRAlgorithmType
import com.zet.switchpadsr.model.TargetResolutionPreset
import com.zet.switchpadsr.ui.theme.AccentGreen
import com.zet.switchpadsr.ui.theme.DarkBackground
import com.zet.switchpadsr.ui.theme.ElectricBlue
import com.zet.switchpadsr.ui.theme.GlassBackground
import com.zet.switchpadsr.ui.theme.GlassBorder
import com.zet.switchpadsr.ui.theme.NeonCyan
import com.zet.switchpadsr.ui.theme.SurfaceVariantDark
import com.zet.switchpadsr.ui.theme.TextPrimary
import com.zet.switchpadsr.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlPanel(
    settings: RenderSettings,
    onSettingsChanged: (RenderSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .clip(RoundedCornerShape(20.dp))
            .background(GlassBackground)
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = NeonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "超分辨率与渲染核心控制面板",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = TextSecondary)
                }
            }

            // Mode Tabs: [Capture Card] vs [Video Player]
            TabRow(
                selectedTabIndex = settings.inputMode.ordinal,
                containerColor = SurfaceVariantDark,
                contentColor = NeonCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[settings.inputMode.ordinal]),
                        color = NeonCyan
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = settings.inputMode == InputSourceMode.CAPTURE_CARD,
                    onClick = { onSettingsChanged(settings.copy(inputMode = InputSourceMode.CAPTURE_CARD)) },
                    text = { Text("🎮 采集卡模式 (Switch 1080p60)", fontSize = 13.sp) }
                )
                Tab(
                    selected = settings.inputMode == InputSourceMode.LOCAL_VIDEO,
                    onClick = { onSettingsChanged(settings.copy(inputMode = InputSourceMode.LOCAL_VIDEO)) },
                    text = { Text("🎬 普通视频重制播放", fontSize = 13.sp) }
                )
            }

            Divider(color = GlassBorder)

            // Section 1: Algorithm Selector
            Text(
                text = "1. 选择超分辨率算法引擎",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = NeonCyan
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SRAlgorithmType.values().forEach { algo ->
                    FilterChip(
                        selected = settings.algorithm == algo,
                        onClick = { onSettingsChanged(settings.copy(algorithm = algo)) },
                        label = { Text(algo.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue,
                            selectedLabelColor = TextPrimary,
                            containerColor = SurfaceVariantDark,
                            labelColor = TextSecondary
                        )
                    )
                }
            }
            Text(
                text = settings.algorithm.desc,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            Divider(color = GlassBorder)

            // Section 2: Algorithm-Specific Fine Details Tuning
            Text(
                text = "2. ${settings.algorithm.displayName} 内部参数精细微调",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = NeonCyan
            )

            when (settings.algorithm) {
                SRAlgorithmType.SGSR_1_0 -> {
                    // SGSR Parameters
                    SliderRow(
                        label = "边缘检测阈值 (EdgeThreshold)",
                        value = settings.sgsrThreshold,
                        range = 1.0f..16.0f,
                        displayValue = String.format("%.1f", settings.sgsrThreshold),
                        onValueChange = { onSettingsChanged(settings.copy(sgsrThreshold = it)) }
                    )
                    SliderRow(
                        label = "边缘锐化强度 (EdgeSharpness)",
                        value = settings.sgsrSharpness,
                        range = 1.0f..2.0f,
                        displayValue = String.format("%.2f", settings.sgsrSharpness),
                        onValueChange = { onSettingsChanged(settings.copy(sgsrSharpness = it)) }
                    )
                    SwitchRow(
                        label = "边缘走向定向滤波 (UseEdgeDirection)",
                        desc = "防止跨边缘模糊，保留极细致线稿",
                        checked = settings.sgsrUseDirection,
                        onCheckedChange = { onSettingsChanged(settings.copy(sgsrUseDirection = it)) }
                    )
                }
                SRAlgorithmType.AMD_FSR -> {
                    // FSR Parameters
                    SliderRow(
                        label = "RCAS 对比度自适应锐化衰减 (Sharpness)",
                        value = settings.fsrSharpness,
                        range = 0.0f..1.0f,
                        displayValue = String.format("%.2f", settings.fsrSharpness),
                        onValueChange = { onSettingsChanged(settings.copy(fsrSharpness = it)) }
                    )
                    SwitchRow(
                        label = "去振铃伪影抑制 (Deringing)",
                        desc = "消除边缘高频过度振铃白边",
                        checked = settings.fsrDering,
                        onCheckedChange = { onSettingsChanged(settings.copy(fsrDering = it)) }
                    )
                }
                SRAlgorithmType.ANIME4K -> {
                    // Anime4K Parameters
                    SliderRow(
                        label = "卷积增强增益强度 (Strength)",
                        value = settings.anime4kStrength,
                        range = 0.5f..2.0f,
                        displayValue = String.format("%.2f", settings.anime4kStrength),
                        onValueChange = { onSettingsChanged(settings.copy(anime4kStrength = it)) }
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("重构模式:", fontSize = 13.sp, color = TextPrimary)
                        listOf("Mode A (激进线稿)", "Mode B (柔和去阶)", "Mode C (高保真)").forEachIndexed { idx, title ->
                            FilterChip(
                                selected = settings.anime4kMode == idx,
                                onClick = { onSettingsChanged(settings.copy(anime4kMode = idx)) },
                                label = { Text(title, fontSize = 11.sp) }
                            )
                        }
                    }
                }
                SRAlgorithmType.RETRO_CRT -> {
                    // CRT Parameters
                    SliderRow(
                        label = "扫描线浓度 (Scanlines)",
                        value = settings.crtScanline,
                        range = 0.0f..1.0f,
                        displayValue = String.format("%.2f", settings.crtScanline),
                        onValueChange = { onSettingsChanged(settings.copy(crtScanline = it)) }
                    )
                    SliderRow(
                        label = "屏幕玻璃桶形曲率 (Curvature)",
                        value = settings.crtCurvature,
                        range = 0.0f..0.2f,
                        displayValue = String.format("%.3f", settings.crtCurvature),
                        onValueChange = { onSettingsChanged(settings.copy(crtCurvature = it)) }
                    )
                    SliderRow(
                        label = "RGB 荧光粉栅格强度 (Phosphor Mask)",
                        value = settings.crtMask,
                        range = 0.0f..1.0f,
                        displayValue = String.format("%.2f", settings.crtMask),
                        onValueChange = { onSettingsChanged(settings.copy(crtMask = it)) }
                    )
                }
                SRAlgorithmType.BICUBIC -> {
                    Text("双三次滤波为无锐化基准模式，参数固定。", fontSize = 12.sp, color = TextSecondary)
                }
            }

            Divider(color = GlassBorder)

            // Section 3: Target Resolution Selection
            Text(
                text = "3. 目标输出分辨率",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = NeonCyan
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TargetResolutionPreset.values().forEach { preset ->
                    FilterChip(
                        selected = settings.targetResolution == preset,
                        onClick = { onSettingsChanged(settings.copy(targetResolution = preset)) },
                        label = { Text(preset.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue,
                            selectedLabelColor = TextPrimary,
                            containerColor = SurfaceVariantDark,
                            labelColor = TextSecondary
                        )
                    )
                }
            }
            if (settings.targetResolution == TargetResolutionPreset.CUSTOM) {
                SliderRow(
                    label = "自定义缩放倍率 (Scale Factor)",
                    value = settings.customScaleFactor,
                    range = 1.0f..2.5f,
                    displayValue = String.format("%.2fx", settings.customScaleFactor),
                    onValueChange = { onSettingsChanged(settings.copy(customScaleFactor = it)) }
                )
            }

            Divider(color = GlassBorder)

            // Section 4: Display Aspect Ratio Mode (3:2 vs 16:9)
            Text(
                text = "4. 3:2 物理屏幕画幅排版与氛围光",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = NeonCyan
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DisplayAspectMode.values().forEach { mode ->
                    FilterChip(
                        selected = settings.displayMode == mode,
                        onClick = { onSettingsChanged(settings.copy(displayMode = mode)) },
                        label = { Text(mode.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue,
                            selectedLabelColor = TextPrimary,
                            containerColor = SurfaceVariantDark,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            Divider(color = GlassBorder)

            // Section 5: Split-Screen Comparison
            Text(
                text = "5. 画质 A/B 实时分屏对比",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = NeonCyan
            )
            SwitchRow(
                label = "开启分屏对比",
                desc = "在屏幕上显示分割线，左右滑动对比超分前后画质",
                checked = settings.splitScreenEnabled,
                onCheckedChange = { onSettingsChanged(settings.copy(splitScreenEnabled = it)) }
            )
            if (settings.splitScreenEnabled) {
                SliderRow(
                    label = "分割线位置",
                    value = settings.splitPosition,
                    range = 0.05f..0.95f,
                    displayValue = "${(settings.splitPosition * 100).toInt()}%",
                    onValueChange = { onSettingsChanged(settings.copy(splitPosition = it)) }
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, fontSize = 13.sp, color = TextPrimary)
            Text(displayValue, fontSize = 13.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = NeonCyan,
                activeTrackColor = NeonCyan,
                inactiveTrackColor = SurfaceVariantDark
            )
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = TextPrimary)
            Text(desc, fontSize = 11.sp, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = ElectricBlue
            )
        )
    }
}
