package com.zet.switchpadsr.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zet.switchpadsr.ui.theme.*
import java.util.Locale

fun Modifier.glass(radius: Int = 20): Modifier =
    this.clip(RoundedCornerShape(radius.dp))
        .background(Glass)
        .border(BorderStroke(1.dp, Outline), RoundedCornerShape(radius.dp))

@Composable
fun GradientIconButton(icon: ImageVector, desc: String, size: Int = 44, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Cyan, Violet)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Ink, modifier = Modifier.size((size * 0.55f).dp)) }
}

@Composable
fun GhostIconButton(icon: ImageVector, desc: String, size: Int = 44, tint: Color = TextHi, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size((size * 0.52f).dp)) }
}

@Composable
fun Pill(text: String, color: Color = TextHi, bg: Color = Color(0x33FFFFFF)) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = TextMid, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRowCompat {
        options.forEach { (v, label) ->
            val sel = v == selected
            val bg by animateColorAsState(if (sel) Cyan.copy(alpha = 0.22f) else Color(0x1AFFFFFF), label = "chip")
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(bg)
                    .border(1.dp, if (sel) Cyan else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onSelect(v) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) { Text(label, color = if (sel) Cyan else TextMid, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowCompat(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
fun LabeledSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    format: String = "%.2f", hint: String? = null, onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextHi, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(String.format(Locale.US, format, value), color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Cyan, activeTrackColor = Cyan, inactiveTrackColor = Color(0x33FFFFFF)
            )
        )
        if (hint != null) Text(hint, color = TextLow, fontSize = 11.sp)
    }
}

@Composable
fun ToggleRow(label: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TextHi, fontSize = 14.sp)
            if (sub != null) Text(sub, color = TextLow, fontSize = 11.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink, checkedTrackColor = Cyan,
                uncheckedThumbColor = TextMid, uncheckedTrackColor = Color(0x33FFFFFF), uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%02d:%02d", m, sec)
}
