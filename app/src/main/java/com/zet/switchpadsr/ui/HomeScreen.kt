package com.zet.switchpadsr.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zet.switchpadsr.AppViewModel
import com.zet.switchpadsr.data.RecentVideo
import com.zet.switchpadsr.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onCapture: () -> Unit,
    onPickVideo: () -> Unit,
    onOpenRecent: (RecentVideo) -> Unit
) {
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshUsbHint()
            delay(1200)
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink, Night, Ink)))) {
        // Minimalist ambient lights
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(
                    listOf(Cyan.copy(alpha = 0.16f), Color.Transparent),
                    Offset(size.width * 0.18f, size.height * 0.15f),
                    size.minDimension * 0.65f
                ),
                radius = size.minDimension * 0.65f,
                center = Offset(size.width * 0.18f, size.height * 0.15f)
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Violet.copy(alpha = 0.18f), Color.Transparent),
                    Offset(size.width * 0.85f, size.height * 0.85f),
                    size.minDimension * 0.70f
                ),
                radius = size.minDimension * 0.70f,
                center = Offset(size.width * 0.85f, size.height * 0.85f)
            )
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 48.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(AccentBrushDiag),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Ink, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("SwitchPad SR", color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Text("Switch 采集卡 · 4K 实时超分辨率 · 音频直通", color = TextMid, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                StatusBadge(
                    active = vm.usbPresent,
                    activeText = "已检测到采集卡 · 即插即玩",
                    idleText = "未连接采集卡"
                )
                Spacer(Modifier.width(12.dp))
                Pill(
                    vm.settings.algorithm.displayName,
                    color = Cyan,
                    bg = Cyan.copy(alpha = 0.14f),
                    icon = Icons.Rounded.Tune
                )
            }

            // Main Dual Mode Cards
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Card 1: Capture Card Mode
                ModernSourceCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.SettingsInputHdmi,
                    tag = "SWITCH 专属",
                    title = "采集卡模式",
                    desc = "接收 Switch 底座 1080p60 画面，通过骁龙/AMD/Anime4K 官方算法实时超分至 3.2K/4K，并转发 USB 音频至扬声器",
                    badge = if (vm.usbPresent) "● 采集卡就绪" else "○ 等待 USB 连接",
                    badgeColor = if (vm.usbPresent) Mint else TextLow,
                    accentColor = Cyan,
                    cta = if (vm.usbPresent) "立即进入" else "开始接收",
                    isRecommended = vm.usbPresent,
                    onClick = onCapture
                )

                // Card 2: Local Video Mode
                ModernSourceCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.VideoLibrary,
                    tag = "本地播放",
                    title = "本地视频",
                    desc = "硬件解码 MP4 / MKV / WebM 等视频，支持实时超分辨率算法处理、A/B 分屏对比与进度记忆",
                    badge = if (vm.recents.isEmpty()) "暂无播放记录" else "已记录 ${vm.recents.size} 个视频",
                    badgeColor = if (vm.recents.isEmpty()) TextLow else Violet,
                    accentColor = Violet,
                    cta = "选择视频",
                    isRecommended = !vm.usbPresent && vm.recents.isNotEmpty(),
                    onClick = onPickVideo
                )
            }

            // Bottom Section: Recent Videos shelf or Quick Tips
            Column(Modifier.fillMaxWidth()) {
                if (vm.recents.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("最近播放", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("点击快速恢复上次播放进度", color = TextLow, fontSize = 12.sp)
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(vm.recents, key = { it.uri }) { r ->
                            Row(
                                Modifier.width(320.dp).clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xCC0E1428)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                                    .clickable { onOpenRecent(r) }.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape).background(Violet.copy(alpha = 0.20f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, null, tint = Violet, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(
                                        if (r.lastPositionMs > 3000) "看到 ${formatTime(r.lastPositionMs)}" else "从头播放",
                                        color = TextMid,
                                        fontSize = 11.sp
                                    )
                                }
                                Box(
                                    Modifier.size(28.dp).clip(CircleShape).clickable { vm.removeRecent(r) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Close, "移除", tint = TextLow, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(Color(0x800E1428)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Info, null, tint = Cyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "提示：将 Switch 底座 HDMI 接入采集卡并插入平板 USB-C 口，系统将自动识别并开启高画质超分监视",
                            color = TextMid,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernSourceCard(
    modifier: Modifier,
    icon: ImageVector,
    tag: String,
    title: String,
    desc: String,
    badge: String,
    badgeColor: Color,
    accentColor: Color,
    cta: String,
    isRecommended: Boolean,
    onClick: () -> Unit
) {
    val borderBrush = if (isRecommended) {
        Brush.linearGradient(listOf(accentColor.copy(alpha = 0.8f), accentColor.copy(alpha = 0.2f)))
    } else {
        Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF)))
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xD90D1426))
            .border(if (isRecommended) 1.5.dp else 1.dp, borderBrush, RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(26.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(16.dp))
                    .background(accentColor.copy(alpha = 0.16f))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.weight(1f))
            Pill(tag, color = accentColor, bg = accentColor.copy(alpha = 0.12f))
        }

        Spacer(Modifier.height(18.dp))
        Text(title, color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(desc, color = TextMid, fontSize = 13.sp, lineHeight = 20.sp, maxLines = 3)
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(badge, color = badgeColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (isRecommended) AccentBrush else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF))))
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(cta, color = if (isRecommended) Ink else TextHi, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Icon(Icons.Rounded.ChevronRight, null, tint = if (isRecommended) Ink else TextHi, modifier = Modifier.size(16.dp))
            }
        }
    }
}
