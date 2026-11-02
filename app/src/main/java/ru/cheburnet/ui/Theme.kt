package ru.cheburnet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AccentCyan = Color(0xFF5BD6FF)
val SignalGreen = Color(0xFF3DDC84)
val AccentSoft = Color(0xFF9BE3FF)
val IceWhite = Color(0xFFEAF8FF)
val DimText = Color(0xFF7E9AAC)
val DeepRed = Color(0xFFB4433F)
val AlertAmber = Color(0xFFFFC857)

private val Colors = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color(0xFF04131C),
    secondary = AccentSoft,
    background = Color(0xFF02070E),
    onBackground = IceWhite,
    surface = Color(0xFF06131F),
    onSurface = IceWhite,
    surfaceVariant = Color(0xFF0B1D2B),
    onSurfaceVariant = DimText,
    outline = Color(0xFF1E3F56),
    error = Color(0xFFFF6B6B),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp),
)

@Composable
fun CheburnetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = AppTypography,
        content = content,
    )
}
