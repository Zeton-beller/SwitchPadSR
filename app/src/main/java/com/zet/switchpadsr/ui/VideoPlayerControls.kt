package com.zet.switchpadsr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zet.switchpadsr.ui.theme.ElectricBlue
import com.zet.switchpadsr.ui.theme.GlassBackground
import com.zet.switchpadsr.ui.theme.GlassBorder
import com.zet.switchpadsr.ui.theme.NeonCyan
import com.zet.switchpadsr.ui.theme.TextPrimary
import com.zet.switchpadsr.ui.theme.TextSecondary

@Composable
fun VideoPlayerControls(
    isPlaying: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onOpenFilePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(16.dp))
            .background(GlassBackground)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Seekbar and Time labels
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formatTime(currentPosMs),
                    fontSize = 12.sp,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )

                Slider(
                    value = if (durationMs > 0) currentPosMs.toFloat() / durationMs.toFloat() else 0.0f,
                    onValueChange = { fraction ->
                        onSeek((fraction * durationMs).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = TextSecondary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )

                Text(
                    text = formatTime(durationMs),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Buttons row
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Left: Pick file button
                OutlinedButton(
                    onClick = onOpenFilePicker,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(GlassBorder))
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "打开本地视频")
                    Spacer(Modifier.width(6.dp))
                    Text("打开视频", fontSize = 13.sp)
                }

                // Center: Playback control buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onSeek(maxOf(0, currentPosMs - 10000)) }) {
                        Icon(Icons.Default.Replay10, contentDescription = "快退10秒", tint = TextPrimary)
                    }

                    IconButton(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(ElectricBlue)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = TextPrimary
                        )
                    }

                    IconButton(onClick = { onSeek(minOf(durationMs, currentPosMs + 10000)) }) {
                        Icon(Icons.Default.Forward10, contentDescription = "快进10秒", tint = TextPrimary)
                    }
                }

                // Right: Speed toggle
                OutlinedButton(
                    onClick = {
                        val nextSpeed = when (playbackSpeed) {
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2.0f
                            2.0f -> 0.75f
                            else -> 1.0f
                        }
                        onSpeedChange(nextSpeed)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(GlassBorder))
                ) {
                    Text("${playbackSpeed}x 倍速", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}
