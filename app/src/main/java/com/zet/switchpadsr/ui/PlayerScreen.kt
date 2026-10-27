package com.zet.switchpadsr.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zet.switchpadsr.AppViewModel
import com.zet.switchpadsr.capture.CaptureStatus
import com.zet.switchpadsr.model.InputSourceMode
import com.zet.switchpadsr.model.RenderSettings
import com.zet.switchpadsr.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    vm: AppViewModel,
    onPickVideo: () -> Unit,
    onSelectCapture: () -> Unit,
    onSelectVideo: () -> Unit,
    onRequestAudioPermission: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var controlsVisible by remember { mutableStateOf(true) }
    var panelOpen by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    val isCapture = vm.mode == InputSourceMode.CAPTURE_CARD
    val streaming = isCapture && vm.captureInfo.status == CaptureStatus.STREAMING
    val hasVideo = !isCapture && vm.currentVideoUri != null
    val busy = if (isCapture) streaming else vm.videoInfo.isPlaying
    val s = vm.settings

    // Gesture indicator states
    var gestureOverlay by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var overlayIcon by remember { mutableStateOf(Icons.Rounded.BrightnessMedium) }

    // Auto-hide controls
    LaunchedEffect(tick, controlsVisible, panelOpen, busy) {
        if (controlsVisible && !panelOpen && busy && s.zoomFactor <= 1f) {
            delay(3800)
            controlsVisible = false
        }
    }

    // Clear gesture overlay after 1.4s
    LaunchedEffect(gestureOverlay) {
        if (gestureOverlay != null) {
            delay(1400)
            gestureOverlay = null
        }
    }

    // Video poll
    LaunchedEffect(vm.mode, controlsVisible) {
        while (!isCapture && controlsVisible) {
            vm.video.poll()
            delay(250)
        }
    }

    BackHandler(enabled = true) {
        if (panelOpen) panelOpen = false else vm.goHome()
    }

    Box(
        Modifier.fillMaxSize()
            // Tap & double-tap gestures
            .pointerInput(isCapture, busy) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        tick++
                    },
                    onDoubleTap = { offset ->
                        if (isCapture) {
                            // Double tap toggles A/B split-screen in capture card mode!
                            vm.update { it.copy(splitScreenEnabled = !it.splitScreenEnabled) }
                            tick++
                        } else {
                            val w = size.width
                            if (offset.x < w * 0.35f) {
                                vm.video.seekBy(-10_000); tick++
                            } else if (offset.x > w * 0.65f) {
                                vm.video.seekBy(10_000); tick++
                            } else {
                                vm.video.togglePlay(); tick++
                            }
                        }
                    }
                )
            }
            // Multi-finger pinch to zoom & pan
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f || s.zoomFactor > 1f) {
                        val nextZ = (s.zoomFactor * zoom).coerceIn(1.0f, 8.0f)
                        val dx = -pan.x / (size.width * nextZ * 0.75f)
                        val dy = pan.y / (size.height * nextZ * 0.75f)
                        val nextX = (s.zoomCenterX + dx).coerceIn(0f, 1f)
                        val nextY = (s.zoomCenterY + dy).coerceIn(0f, 1f)
                        vm.update { it.copy(zoomFactor = nextZ, zoomCenterX = nextX, zoomCenterY = nextY) }
                        tick++
                    }
                }
            }
            // Single-finger drag: pan when zoomed in, or brightness/volume when 1x
            .pointerInput(s.zoomFactor) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (s.zoomFactor > 1.0f) {
                            // Pan the magnified view area
                            val z = s.zoomFactor
                            val dx = -dragAmount.x / (size.width * z * 0.75f)
                            val dy = dragAmount.y / (size.height * z * 0.75f)
                            val nextX = (s.zoomCenterX + dx).coerceIn(0f, 1f)
                            val nextY = (s.zoomCenterY + dy).coerceIn(0f, 1f)
                            vm.update { it.copy(zoomCenterX = nextX, zoomCenterY = nextY) }
                            tick++
                        } else {
                            // Standard 1x: Left half = Brightness, Right half = Volume
                            val isLeft = change.position.x < (size.width / 2f)
                            val delta = -dragAmount.y / (size.height * 0.65f)
                            if (isLeft && activity != null) {
                                val lp = activity.window.attributes
                                val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                val next = (current + delta).coerceIn(0.05f, 1.0f)
                                lp.screenBrightness = next
                                activity.window.attributes = lp
                                overlayIcon = Icons.Rounded.BrightnessMedium
                                gestureOverlay = "亮度" to next
                            } else {
                                val next = (vm.settings.audioVolume + delta).coerceIn(0f, 1f)
                                vm.update { it.copy(audioVolume = next) }
                                overlayIcon = if (next == 0f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp
                                gestureOverlay = "音量" to next
                            }
                        }
                    }
                )
            }
    ) {
        // Status cards when idle / waiting
        if (isCapture && !streaming) {
            CaptureWaitingCard(vm, onRequestAudioPermission, Modifier.align(Alignment.Center))
        }
        if (!isCapture && !hasVideo) {
            EmptyVideoCard(onPickVideo, Modifier.align(Alignment.Center))
        }

        // Gesture feedback overlay (Center)
        AnimatedVisibility(
            visible = gestureOverlay != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f)
        ) {
            gestureOverlay?.let { (label, progress) ->
                GestureOverlayPill(overlayIcon, label, progress)
            }
        }

        // A/B Split Floating Badges & Zoom Indicator
        if ((streaming || (!isCapture && hasVideo)) && (s.splitScreenEnabled || s.zoomFactor > 1f)) {
            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 74.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (s.splitScreenEnabled) {
                    Pill("◀ 左：${s.algorithm.displayName} (${vm.stats.outW}×${vm.stats.outH})", color = Cyan, bg = Color(0xDD090E1D))
                    Pill("右：原始画面 (${vm.stats.inW}×${vm.stats.inH}) ▶", color = Amber, bg = Color(0xDD090E1D))
                }
                if (s.zoomFactor > 1f) {
                    Pill("🔍 %.1fx 放大中（可单指拖动平移）".format(s.zoomFactor), color = Mint, bg = Color(0xDD090E1D))
                }
            }
        }

        // Top Navigation Bar
        val showTop = controlsVisible || (isCapture && !streaming) || (!isCapture && !hasVideo)
        AnimatedVisibility(
            visible = showTop,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xD9060913), Color.Transparent)))
                    .statusBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GhostIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回") { vm.goHome() }
                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (isCapture) "Switch 采集卡" else vm.videoName.ifEmpty { "本地视频" },
                            color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1
                        )
                        Pill(
                            if (isCapture) "${vm.captureInfo.width}×${vm.captureInfo.height}@${vm.captureInfo.fps}"
                            else "${vm.stats.inW}×${vm.stats.inH}",
                            color = Cyan, bg = Cyan.copy(alpha = 0.15f)
                        )
                    }
                    if (s.showHud && (streaming || (!isCapture && hasVideo))) {
                        val st = vm.stats
                        Text(
                            "%.0f FPS · GPU %.1f ms · 输出 ${st.outW}×${st.outH} · ${s.algorithm.displayName}".format(st.fps, st.gpuMs),
                            color = TextMid, fontSize = 11.sp, maxLines = 1
                        )
                    }
                }

                // Action Controls Cluster
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Zoom loupe toggle
                    GhostIconButton(
                        Icons.Rounded.ZoomIn, "细节放大",
                        tint = if (s.zoomFactor > 1f) Cyan else TextHi,
                        bg = if (s.zoomFactor > 1f) Cyan.copy(alpha = 0.22f) else Color(0x26FFFFFF)
                    ) {
                        val next = if (s.zoomFactor <= 1f) 2.0f else 1.0f
                        vm.update { it.copy(zoomFactor = next) }; tick++
                    }

                    // A/B Split Screen toggle
                    GhostIconButton(
                        Icons.Rounded.Compare, "分屏对比",
                        tint = if (s.splitScreenEnabled) Cyan else TextHi,
                        bg = if (s.splitScreenEnabled) Cyan.copy(alpha = 0.22f) else Color(0x26FFFFFF)
                    ) {
                        vm.update { it.copy(splitScreenEnabled = !it.splitScreenEnabled) }; tick++
                    }

                    // Audio passthrough indicator / toggle
                    if (isCapture) {
                        GhostIconButton(
                            if (vm.audioStatus.isRunning) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, "音频直通",
                            tint = if (vm.audioStatus.isRunning) Mint else TextMid,
                            bg = if (vm.audioStatus.isRunning) Mint.copy(alpha = 0.16f) else Color(0x26FFFFFF)
                        ) {
                            if (!vm.hasAudioPermission()) onRequestAudioPermission()
                            else vm.update { it.copy(captureAudio = !it.captureAudio) }
                            tick++
                        }
                    }

                    // Settings drawer toggle
                    GradientIconButton(Icons.Rounded.Tune, "画质设置") {
                        panelOpen = !panelOpen; tick++
                    }
                }
            }
        }

        // Floating Magnifier Controller Dock (when zoomed in > 1x)
        AnimatedVisibility(
            visible = s.zoomFactor > 1f,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (controlsVisible && hasVideo) 100.dp else 24.dp),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            MagnifierFloatingDock(vm, s, onTouch = { tick++ })
        }

        // Bottom Bar (Video playback controls)
        AnimatedVisibility(
            visible = controlsVisible && hasVideo && s.zoomFactor <= 1f,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            VideoControlsDock(vm, onPickVideo, onTouch = { tick++ })
        }

        // Bottom Bar (Capture streaming minimal pill)
        AnimatedVisibility(
            visible = controlsVisible && streaming && s.zoomFactor <= 1f,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            Row(
                Modifier.navigationBarsPadding().padding(bottom = 20.dp)
                    .clip(RoundedCornerShape(30.dp)).background(Color(0xDD090E1D))
                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(30.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StatusBadge(true, "低延迟投屏中", "未激活")
                Text("${vm.captureInfo.width}×${vm.captureInfo.height}@${vm.captureInfo.fps}", color = TextMid, fontSize = 12.sp)
                if (vm.audioStatus.isRunning) {
                    Pill("扬声器直通", color = Mint, bg = Mint.copy(alpha = 0.15f))
                }
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(0x22FFFFFF))
                        .clickable { vm.capture.reconnect(); tick++ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Refresh, "刷新", tint = TextHi, modifier = Modifier.size(17.dp))
                }
            }
        }

        // Right Settings Drawer Panel
        AnimatedVisibility(
            visible = panelOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            SettingsPanel(
                vm = vm,
                onRequestAudioPermission = onRequestAudioPermission,
                onClose = { panelOpen = false }
            )
        }

        // Toast Messages
        val msg = vm.message
        LaunchedEffect(msg) {
            if (msg != null) {
                delay(4000)
                if (vm.message == msg) vm.message = null
            }
        }
        AnimatedVisibility(
            visible = msg != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Text(
                msg ?: "", color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xEE12182B))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

/** Floating interactive controller for the continuous magnifier with draggable pan center */
@Composable
private fun MagnifierFloatingDock(
    vm: AppViewModel,
    s: RenderSettings,
    onTouch: () -> Unit
) {
    Column(
        Modifier.navigationBarsPadding().padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(24.dp)).background(Color(0xEE090E1D))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.ZoomIn, null, tint = Cyan, modifier = Modifier.size(20.dp))
            Text("放大倍率 %.1fx".format(s.zoomFactor), color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Bold)

            Slider(
                value = s.zoomFactor,
                onValueChange = { z ->
                    vm.update { it.copy(zoomFactor = z) }
                    onTouch()
                },
                valueRange = 1.0f..8.0f,
                modifier = Modifier.width(180.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Cyan, activeTrackColor = Cyan, inactiveTrackColor = Color(0x33FFFFFF)
                )
            )

            // Preset quick chips
            listOf(1.0f to "1x", 2.0f to "2x", 4.0f to "4x", 8.0f to "8x").forEach { (z, label) ->
                val sel = (s.zoomFactor - z).let { it > -0.15f && it < 0.15f }
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (sel) Cyan.copy(alpha = 0.25f) else Color(0x22FFFFFF))
                        .clickable {
                            vm.update { it.copy(zoomFactor = z) }
                            onTouch()
                        }.padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(label, color = if (sel) Cyan else TextMid, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Center reset button
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF))
                    .clickable {
                        vm.update { it.copy(zoomCenterX = 0.5f, zoomCenterY = 0.5f) }
                        onTouch()
                    }.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("居中", color = TextHi, fontSize = 11.sp)
            }

            // Close zoom button
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x33FF6B7A))
                    .clickable {
                        vm.update { it.copy(zoomFactor = 1.0f, zoomCenterX = 0.5f, zoomCenterY = 0.5f) }
                        onTouch()
                    }.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("退出", color = Danger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "提示：在画面任意位置单指拖拽即可平移放大区域，双指捏合可连续缩放",
            color = TextLow, fontSize = 10.sp
        )
    }
}

@Composable
private fun CaptureWaitingCard(vm: AppViewModel, onRequestAudioPermission: () -> Unit, modifier: Modifier) {
    val c = vm.captureInfo
    val (title, color) = when (c.status) {
        CaptureStatus.ERROR -> "连接遇到问题" to Danger
        CaptureStatus.REQUESTING_PERMISSION -> "请在系统弹窗中允许 USB 访问" to Amber
        CaptureStatus.OPENING -> "正在初始化采集卡…" to Cyan
        CaptureStatus.NO_DEVICE -> "未检测到采集卡" to Amber
        else -> "正在启动采集…" to Cyan
    }

    Column(
        modifier.width(460.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xE60D1426))
            .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(26.dp)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.SettingsInputHdmi, null, tint = color, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = TextHi, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (c.message.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(c.message, color = color, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "① 使用 USB-C 数据线将采集卡连接至平板\n② Switch 底座 HDMI 输出口接入采集卡 HDMI IN\n③ 打开 Switch，设置 -> 电视输出设为 1080p",
            color = TextMid, fontSize = 12.sp, lineHeight = 20.sp
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(AccentBrush)
                    .clickable { vm.capture.reconnect() }.padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Refresh, null, tint = Ink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("重新检测", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            if (!vm.hasAudioPermission()) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0x26FFFFFF))
                        .clickable { onRequestAudioPermission() }.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Mic, null, tint = Cyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("授权音频", color = Cyan, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptyVideoCard(onPick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.width(400.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xE60D1426))
            .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(26.dp)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(Violet.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.VideoLibrary, null, tint = Violet, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("选择视频开始超分播放", color = TextHi, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(AccentBrush).clickable(onClick = onPick)
                .padding(horizontal = 24.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.FolderOpen, null, tint = Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("浏览文件", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun VideoControlsDock(vm: AppViewModel, onPickVideo: () -> Unit, onTouch: () -> Unit) {
    val v = vm.videoInfo
    var dragging by remember { mutableStateOf<Float?>(null) }
    var speedMenu by remember { mutableStateOf(false) }
    val dur = v.durationMs.coerceAtLeast(1L)
    val frac = dragging ?: (v.positionMs.toFloat() / dur).coerceIn(0f, 1f)

    Column(
        Modifier.navigationBarsPadding().padding(horizontal = 48.dp).padding(bottom = 18.dp)
            .fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color(0xEE0A0E1C))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(26.dp))
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        // Progress Slider
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatTime(if (dragging != null) (dragging!! * dur).toLong() else v.positionMs),
                color = TextHi, fontSize = 12.sp, fontWeight = FontWeight.Medium
            )
            Slider(
                value = frac,
                onValueChange = { dragging = it; onTouch() },
                onValueChangeFinished = {
                    dragging?.let { vm.video.seekTo((it * dur).toLong()) }
                    dragging = null
                },
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Cyan, activeTrackColor = Cyan, inactiveTrackColor = Color(0x33FFFFFF)
                )
            )
            Text(formatTime(v.durationMs), color = TextMid, fontSize = 12.sp)
        }

        // Action Buttons Row
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            GhostIconButton(Icons.Rounded.FolderOpen, "打开文件", size = 38) { onPickVideo() }
            GhostIconButton(Icons.Rounded.Replay10, "后退10秒", size = 42) { vm.video.seekBy(-10_000); onTouch() }
            GradientIconButton(if (v.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "播放/暂停", size = 52) {
                vm.video.togglePlay(); onTouch()
            }
            GhostIconButton(Icons.Rounded.Forward10, "前进10秒", size = 42) { vm.video.seekBy(10_000); onTouch() }

            // Speed dropdown
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0x26FFFFFF)).clickable { speedMenu = true; onTouch() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Speed, null, tint = TextHi, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${v.speed}x", color = TextHi, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                        DropdownMenuItem(
                            text = { Text("${sp}x") },
                            onClick = { vm.video.setSpeed(sp); speedMenu = false }
                        )
                    }
                }
            }

            GhostIconButton(
                Icons.Rounded.Repeat, "循环", size = 38,
                tint = if (vm.settings.loopVideo) Cyan else TextHi
            ) {
                vm.update { it.copy(loopVideo = !it.loopVideo) }
                onTouch()
            }
        }
    }
}
