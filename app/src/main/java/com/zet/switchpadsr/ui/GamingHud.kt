package com.zet.switchpadsr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zet.switchpadsr.model.RenderSettings
import com.zet.switchpadsr.ui.theme.AccentGreen
import com.zet.switchpadsr.ui.theme.GlassBackground
import com.zet.switchpadsr.ui.theme.GlassBorder
import com.zet.switchpadsr.ui.theme.NeonCyan
import com.zet.switchpadsr.ui.theme.TextPrimary
import com.zet.switchpadsr.ui.theme.TextSecondary

@Composable
fun GamingHud(
    fps: Float,
    gpuTimeMs: Float,
    latencyMs: Float,
    settings: RenderSettings,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GlassBackground)
            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // FPS Counter
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "FPS: ",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = String.format("%.1f", fps),
                    fontSize = 13.sp,
                    color = AccentGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // GPU Compute Time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "GPU: ",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = String.format("%.2f ms", gpuTimeMs),
                    fontSize = 13.sp,
                    color = NeonCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // Estimated Glass-to-Glass Latency
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "延迟: ",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = String.format("%.1f ms", latencyMs),
                    fontSize = 13.sp,
                    color = if (latencyMs < 35.0f) AccentGreen else Color(0xFFFFB300),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // Algorithm Tag
            Text(
                text = settings.algorithm.displayName,
                fontSize = 12.sp,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )

            // Screen & Display Mode Tag
            Text(
                text = "${settings.targetResolution.label} | 144Hz",
                fontSize = 11.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
