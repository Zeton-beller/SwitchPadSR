package com.zet.switchpadsr.ui

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
        while (true) { vm.refreshUsbHint(); delay(1500) }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night, Ink)))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(Cyan.copy(alpha = 0.22f), Color.Transparent), Offset(size.width * 0.15f, size.height * 0.1f), size.minDimension * 0.7f),
                radius = size.minDimension * 0.7f, center = Offset(size.width * 0.15f, size.height * 0.1f)
            )
            drawCircle(
                Brush.radialGradient(listOf(Violet.copy(alpha = 0.25f), Color.Transparent), Offset(size.width * 0.9f, size.height * 0.95f), size.minDimension * 0.8f),
                radius = size.minDimension * 0.8f, center = Offset(size.width * 0.9f, size.height * 0.95f)
            )
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 56.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(AccentBrushDiag),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.AutoAwesome, null, tint = Ink, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("SwitchPad SR", color = TextHi, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("Switch 采集卡 · 本地视频 · GPU 实时超分", color = TextMid, fontSize = 14.sp)
                }
                Spacer(Modifier.weight(1f))
                Pill("当前算法  ${vm.settings.algorithm.displayName}", color = Cyan, bg = Cyan.copy(alpha = 0.14f))
            }
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth()) {
                SourceCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.SettingsInputHdmi,
                    title = "采集卡模式",
                    desc = "接收 Switch 底座 1080p60 画面，低延迟实时超分，同步转发 USB 音频",
                    status = if (vm.usbPresent) "● 已检测到 USB 视频设备" else "○ 未检测到采集卡",
                    statusColor = if (vm.usbPresent) Mint else TextLow,
                    brush = Brush.linearGradient(listOf(Color(0xFF1B3A5E), Color(0xFF2B2A6B))),
                    cta = "开始接收",
                    onClick = onCapture
                )
                SourceCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.VideoLibrary,
                    title = "本地视频",
                    desc = "MP4 / MKV / WebM 等硬件解码，带进度、倍速，与采集卡共用全部超分算法",
                    status = if (vm.recents.isEmpty()) "还没有播放记录" else "最近 ${vm.recents.size} 个视频",
                    statusColor = if (vm.recents.isEmpty()) TextLow else Cyan,
                    brush = Brush.linearGradient(listOf(Color(0xFF3A1F5E), Color(0xFF5E2060))),
                    cta = "选择视频",
                    onClick = onPickVideo
                )
            }
            if (vm.recents.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text("继续观看", color = TextMid, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(vm.recents, key = { it.uri }) { r ->
                        Row(
                            Modifier.width(300.dp).glass(16).clickable { onOpenRecent(r) }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(0x22FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Cyan) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, color = TextHi, fontSize = 14.sp, maxLines = 1)
                                Text(
                                    if (r.lastPositionMs > 3000) "上次看到 ${formatTime(r.lastPositionMs)}" else "从头播放",
                                    color = TextLow, fontSize = 11.sp
                                )
                            }
                            Icon(
                                Icons.Rounded.Close, "移除", tint = TextLow,
                                modifier = Modifier.size(20.dp).clickable { vm.removeRecent(r) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    modifier: Modifier, icon: ImageVector, title: String, desc: String,
    status: String, statusColor: Color, brush: Brush, cta: String, onClick: () -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(28.dp)).background(brush)
            .border(1.dp, Outline, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick).padding(28.dp)
    ) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x26FFFFFF)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = TextHi, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(title, color = TextHi, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(desc, color = TextMid, fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(18.dp))
        Text(status, color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(AccentBrush).padding(horizontal = 22.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(cta, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.ChevronRight, null, tint = Ink, modifier = Modifier.size(20.dp))
        }
    }
}
