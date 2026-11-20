package com.obdfacil.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.obdfacil.app.analysis.Level

object Palette {
    val Good = Color(0xFF2EE6A6)
    val Warn = Color(0xFFFFB938)
    val Bad = Color(0xFFFF5A5F)
    val Info = Color(0xFF5AA9FF)
    val Bg = Color(0xFF0E1116)
    val Surface = Color(0xFF171B22)
    val Surface2 = Color(0xFF1F2530)
}

fun Level.color(): Color = when (this) {
    Level.OK, Level.INFO -> Palette.Good
    Level.WARNING -> Palette.Warn
    Level.CRITICAL -> Palette.Bad
}

private val Dark = darkColorScheme(
    primary = Palette.Good, onPrimary = Color(0xFF00281A),
    secondary = Palette.Info,
    background = Palette.Bg, onBackground = Color(0xFFE8ECF2),
    surface = Palette.Surface, onSurface = Color(0xFFE8ECF2),
    surfaceVariant = Palette.Surface2, onSurfaceVariant = Color(0xFF9AA4B2),
    surfaceContainer = Palette.Surface,
    error = Palette.Bad,
)

private val Light = lightColorScheme(
    primary = Color(0xFF00916A), secondary = Color(0xFF1F6FD1),
    background = Color(0xFFF4F6F9), surface = Color.White,
    surfaceVariant = Color(0xFFE9EDF2), onSurfaceVariant = Color(0xFF5B6573),
    surfaceContainer = Color.White,
    error = Color(0xFFD32F2F),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun ObdTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
