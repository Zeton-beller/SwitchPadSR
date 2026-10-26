package com.zet.switchpadsr.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Ink = Color(0xFF070A14)
val Night = Color(0xFF0D1224)
val Panel = Color(0xFF151B33)
val PanelHi = Color(0xFF1D2547)
val Glass = Color(0xE60C1126)
val Outline = Color(0x33FFFFFF)
val Cyan = Color(0xFF4FD8FF)
val Violet = Color(0xFF8B7CFF)
val Pink = Color(0xFFFF6BD6)
val Mint = Color(0xFF55E6A5)
val Amber = Color(0xFFFFC857)
val Danger = Color(0xFFFF6B7A)
val TextHi = Color(0xFFF2F5FF)
val TextMid = Color(0xFFA9B3D6)
val TextLow = Color(0xFF6C7699)

val AccentBrush = Brush.horizontalGradient(listOf(Cyan, Violet))
val AccentBrushDiag = Brush.linearGradient(listOf(Cyan, Violet, Pink))

private val Scheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Ink,
    secondary = Violet,
    tertiary = Pink,
    background = Ink,
    onBackground = TextHi,
    surface = Panel,
    onSurface = TextHi,
    surfaceVariant = PanelHi,
    onSurfaceVariant = TextMid,
    outline = Outline,
    error = Danger
)

@Composable
fun SwitchPadSRTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
