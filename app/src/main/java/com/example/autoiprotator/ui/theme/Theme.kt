package com.example.autoiprotator.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autoiprotator.R

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium)
)

object VpnPalette {
    // Deep Terminal / Cyber Void surfaces
    val ObsidianBg = Color(0xFF030806)
    val SlateSurface = Color(0xFF07130E)
    val SlateCard = Color(0xFF0B1C15)
    val SlateElevated = Color(0xFF10281E)

    // Hacker Neon Accents
    val CyanPrimary = Color(0xFF00F0FF)
    val CyanDim = Color(0xFF063B44)
    val EmeraldVerified = Color(0xFF00FF88)
    val EmeraldDim = Color(0xFF073B22)
    val MatrixLime = Color(0xFF76FF03)
    val NeonViolet = Color(0xFF9D6BFF)
    val NeonMagenta = Color(0xFFFF2A85)
    val AmberWarn = Color(0xFFFFB800)
    val AmberDim = Color(0xFF3A2906)
    val CoralError = Color(0xFFFF385C)
    val CoralDim = Color(0xFF3E101C)

    // Terminal Text & Borders
    val TextPrimary = Color(0xFFE6FFF3)
    val TextSecondary = Color(0xFF83B89E)
    val BorderSubtle = Color(0xFF1A4633)
}

private val UltraVpnColorScheme = darkColorScheme(
    primary = VpnPalette.EmeraldVerified,
    onPrimary = Color(0xFF002412),
    primaryContainer = VpnPalette.EmeraldDim,
    onPrimaryContainer = Color(0xFF9BFFCA),
    secondary = VpnPalette.CyanPrimary,
    onSecondary = Color(0xFF00262B),
    secondaryContainer = VpnPalette.CyanDim,
    onSecondaryContainer = Color(0xFFA2FAFF),
    tertiary = VpnPalette.AmberWarn,
    onTertiary = Color(0xFF2B1C00),
    tertiaryContainer = VpnPalette.AmberDim,
    onTertiaryContainer = Color(0xFFFFDF99),
    background = VpnPalette.ObsidianBg,
    onBackground = VpnPalette.TextPrimary,
    surface = VpnPalette.SlateSurface,
    onSurface = VpnPalette.TextPrimary,
    surfaceVariant = VpnPalette.SlateCard,
    onSurfaceVariant = VpnPalette.TextSecondary,
    surfaceContainerHigh = VpnPalette.SlateElevated,
    outline = VpnPalette.BorderSubtle,
    error = VpnPalette.CoralError,
    onError = Color(0xFF3A000D),
    errorContainer = VpnPalette.CoralDim,
    onErrorContainer = Color(0xFFFFB4C2)
)

private val UltraVpnTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 1.2.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.6.sp
    ),
    titleLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.8.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.3.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.7.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.9.sp
    )
)

private val UltraVpnShapes = Shapes(
    small = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp, topEnd = 2.dp, bottomStart = 2.dp),
    medium = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp, topEnd = 4.dp, bottomStart = 4.dp),
    large = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp, topEnd = 4.dp, bottomStart = 4.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun UltraVpnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UltraVpnColorScheme,
        typography = UltraVpnTypography,
        shapes = UltraVpnShapes,
        content = content
    )
}
