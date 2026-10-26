package com.zet.switchpadsr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zet.switchpadsr.AppViewModel
import com.zet.switchpadsr.capture.CaptureStatus
import com.zet.switchpadsr.model.InputSourceMode
import com.zet.switchpadsr.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    vm: AppViewModel,
    onPickVideo: () -> Unit,
    onSelectCapture: () -> Unit,
    onSelectVideo: () -> Unit
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var panelOpen by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    val isCapture = vm.mode == InputSourceMode.CAPTURE_CARD
    val streaming = isCapture && vm.captureInfo.status == CaptureStatus.STREAMING
    val hasVideo = !isCapture && vm.currentVideoUri != null
    val busy = if (isCapture) streaming else vm.videoInfo.isPlaying
    val s = vm.settings

    BackHandler(enabled = true) { if (panelOpen) panelOpen = false else vm.goHome() }

    LaunchedEffect(tick, controlsVisible, panelOpen, busy) {
        if (controlsVisible && !panelOpen && busy) { delay(4000); controlsVisible = false }
    }
    LaunchedEffect(vm.mode, controlsVisible) {
        while (!isCapture && controlsVisible) { vm.video.poll(); delay(250) }
    }
    val showChrome = controlsVisible || (isCapture && !streaming) || (!isCapture && !hasVideo)

    Box(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            controlsVisible = !controlsVisible; tick++
        }
    ) {
        // ---- empty / status cards ----
        if (isCapture && !streaming) CaptureStatusCard(vm, Modifier.align(Alignment.Center))
        if (!isCapture && !hasVideo) EmptyVideoCard(onPickVideo, Modifier.align(Alignment.Center))

        // ---- top bar ----
        AnimatedVisibility(
            visible = showChrome, modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GhostIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回") { vm.goHome() }
                Spacer(Modifier.width(14.dp))
                ModeSwitch(vm.mode, onSelectCapture = { tick++; onSelectCapture() }, onSelectVideo = { tick++; onSelectVideo() })
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isCapture) "Switch 采集卡" else vm.videoName.ifEmpty { "本地视频" },
                        color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                    Text(subtitle(vm), color = TextMid, fontSize = 12.sp, maxLines = 1)
                }
                if (s.showHud && (streaming || (!isCapture && hasVideo))) {
                    val st = vm.stats
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("%.0f FPS".format(st.fps), color = Mint, bg = Mint.copy(alpha = 0.15f))
                        if (st.gpuMs > 0f) Pill("GPU %.1f ms".format(st.gpuMs))
                        if (st.inW > 0) Pill("${st.inW}×${st.inH} → ${st.outW}×${st.outH}")
                        Pill(s.algorithm.displayName, color = Cyan, bg = Cyan.copy(alpha = 0.15f))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                GhostIconButton(Icons.Rounded.ZoomIn, "局部放大", tint = if (s.zoomFactor > 1f) Cyan else TextHi) {
                    val nextZoom = when (s.zoomFactor) {
                        1.0f -> 2.0f
                        2.0f -> 4.0f
                        else -> 1.0f
                    }
                    vm.update { it.copy(zoomFactor = nextZoom) }; tick++
                }
                Spacer(Modifier.width(10.dp))
                GhostIconButton(Icons.Rounded.Compare, "A/B 对比", tint = if (s.splitScreenEnabled) Cyan else TextHi) {
                    vm.update { it.copy(splitScreenEnabled = !it.splitScreenEnabled) }; tick++
                }
                Spacer(Modifier.width(10.dp))
                GradientIconButton(Icons.Rounded.Tune, "画质设置") { panelOpen = !panelOpen; tick++ }
            }
        }

        // ---- A/B split-screen & zoom floating badges ----
        if ((streaming || (!isCapture && hasVideo)) && (s.splitScreenEnabled || s.zoomFactor > 1f)) {
            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 74.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (s.splitScreenEnabled) {
                    Pill("◀ 左：${s.algorithm.displayName} (${vm.stats.outW}×${vm.stats.outH})", color = Cyan, bg = Color(0xBF0B1020))
                    Pill("右：原始画面 (${vm.stats.inW}×${vm.stats.inH}) ▶", color = Amber, bg = Color(0xBF0B1020))
                }
                if (s.zoomFactor > 1f) {
                    Pill("🔍 ${s.zoomFactor.toInt()}x 像素放大检视中（点击放大镜复原）", color = Mint, bg = Color(0xBF0B1020))
                }
            }
        }

        // ---- bottom bar ----
        AnimatedVisibility(
            visible = controlsVisible && hasVideo, modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }
        ) { VideoControls(vm, onPickVideo, onTouch = { tick++ }) }

        AnimatedVisibility(
            visible = controlsVisible && streaming, modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }
        ) {
            Row(
                Modifier.navigationBarsPadding().padding(bottom = 20.dp).glass(26).padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val ci = vm.captureInfo
                Pill("● 正在接收", color = Mint, bg = Mint.copy(alpha = 0.15f))
                Pill("${ci.width}×${ci.height} @${ci.fps}")
                GhostIconButton(Icons.Rounded.Refresh, "重新连接", size = 38) { vm.capture.reconnect(); tick++ }
            }
        }

        // ---- settings panel ----
        AnimatedVisibility(
            visible = panelOpen, modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()
        ) { SettingsPanel(vm, onClose = { panelOpen = false }) }

        // ---- toast ----
        val msg = vm.message
        LaunchedEffect(msg) { if (msg != null) { delay(4500); if (vm.message == msg) vm.message = null } }
        AnimatedVisibility(
            visible = msg != null, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Text(msg ?: "", color = TextHi, fontSize = 14.sp,
                modifier = Modifier.glass(18).padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
}

private fun subtitle(vm: AppViewModel): String =
    if (vm.mode == InputSourceMode.CAPTURE_CARD) {
        val c = vm.captureInfo
        when (c.status) {
            CaptureStatus.STREAMING -> "${c.deviceName}  ·  ${c.width}×${c.height}@${c.fps}"
            CaptureStatus.IDLE -> "准备中"
            CaptureStatus.NO_DEVICE -> "等待采集卡"
            CaptureStatus.REQUESTING_PERMISSION -> "等待 USB 授权"
            CaptureStatus.OPENING -> "正在打开"
            CaptureStatus.ERROR -> "连接出错"
        }
    } else {
        val v = vm.videoInfo
        if (v.error != null) v.error
        else if (v.buffering) "缓冲中…"
        else if (vm.currentVideoUri == null) "未选择视频"
        else if (v.isPlaying) "播放中" else "已暂停"
    }

@Composable
private fun ModeSwitch(mode: InputSourceMode, onSelectCapture: () -> Unit, onSelectVideo: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0x40FFFFFF)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        @Composable
        fun seg(sel: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (sel) AccentBrush else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = if (sel) Ink else TextHi, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(text, color = if (sel) Ink else TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        seg(mode == InputSourceMode.CAPTURE_CARD, Icons.Rounded.SettingsInputHdmi, "采集卡", onSelectCapture)
        seg(mode == InputSourceMode.LOCAL_VIDEO, Icons.Rounded.VideoLibrary, "本地视频", onSelectVideo)
    }
}

@Composable
private fun CaptureStatusCard(vm: AppViewModel, modifier: Modifier) {
    val c = vm.captureInfo
    val (title, color) = when (c.status) {
        CaptureStatus.ERROR -> "连接出错" to Danger
        CaptureStatus.REQUESTING_PERMISSION -> "请在弹窗中允许 USB 访问" to Amber
        CaptureStatus.OPENING -> "正在打开采集卡…" to Cyan
        CaptureStatus.NO_DEVICE -> "未检测到采集卡" to Amber
        else -> "正在启动采集…" to Cyan
    }
    Column(
        modifier.width(480.dp).glass(28).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.SettingsInputHdmi, null, tint = color, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (c.message.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(c.message, color = color, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "① 用 USB 线把采集卡接到平板\n② Switch 底座 HDMI OUT 接入采集卡 IN\n③ 打开 Switch，系统输出设为 1080p",
            color = TextMid, fontSize = 13.sp, lineHeight = 21.sp
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(AccentBrush)
                .clickable { vm.capture.reconnect() }.padding(horizontal = 22.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Refresh, null, tint = Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("重新连接", color = Ink, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyVideoCard(onPick: () -> Unit, modifier: Modifier) {
    Column(modifier.width(420.dp).glass(28).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.VideoLibrary, null, tint = Violet, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text("选择一个视频开始播放", color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(AccentBrush).clickable(onClick = onPick)
                .padding(horizontal = 22.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.FolderOpen, null, tint = Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("浏览文件", color = Ink, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VideoControls(vm: AppViewModel, onPickVideo: () -> Unit, onTouch: () -> Unit) {
    val v = vm.videoInfo
    var dragging by remember { mutableStateOf<Float?>(null) }
    var speedMenu by remember { mutableStateOf(false) }
    val dur = v.durationMs.coerceAtLeast(1L)
    val frac = dragging ?: (v.positionMs.toFloat() / dur).coerceIn(0f, 1f)
    Column(
        Modifier.navigationBarsPadding().padding(horizontal = 40.dp).padding(bottom = 20.dp)
            .fillMaxWidth().glass(28).padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(if (dragging != null) (dragging!! * dur).toLong() else v.positionMs), color = TextHi, fontSize = 12.sp)
            Slider(
                value = frac,
                onValueChange = { dragging = it; onTouch() },
                onValueChangeFinished = { dragging?.let { vm.video.seekTo((it * dur).toLong()) }; dragging = null },
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan, inactiveTrackColor = Color(0x33FFFFFF))
            )
            Text(formatTime(v.durationMs), color = TextMid, fontSize = 12.sp)
        }
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
        ) {
            GhostIconButton(Icons.Rounded.FolderOpen, "打开文件", size = 40) { onPickVideo() }
            GhostIconButton(Icons.Rounded.Replay10, "后退10秒", size = 44) { vm.video.seekBy(-10_000); onTouch() }
            GradientIconButton(if (v.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "播放/暂停", size = 56) {
                vm.video.togglePlay(); onTouch()
            }
            GhostIconButton(Icons.Rounded.Forward10, "前进10秒", size = 44) { vm.video.seekBy(10_000); onTouch() }
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).clickable { speedMenu = true; onTouch() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Speed, null, tint = TextHi, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${v.speed}x", color = TextHi, fontSize = 13.sp)
                }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                        DropdownMenuItem(text = { Text("${sp}x") }, onClick = { vm.video.setSpeed(sp); speedMenu = false })
                    }
                }
            }
            GhostIconButton(
                Icons.Rounded.Repeat, "循环", size = 40,
                tint = if (vm.settings.loopVideo) Cyan else TextHi
            ) { vm.update { it.copy(loopVideo = !it.loopVideo) }; onTouch() }
        }
    }
}
