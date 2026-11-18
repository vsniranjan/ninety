package com.niranjan.ninety.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.niranjan.ninety.R

// Explicit palettes: wallpaper-derived dynamic colors must never override this direction.
// outline is for interactive boundaries (>=3:1); outlineVariant is the quiet divider.
internal val LightColors = lightColorScheme(
    primary = Color(0xFF2457D6), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EEFC), onPrimaryContainer = Color(0xFF173C91),
    secondary = Color(0xFF626975), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EEFC), onSecondaryContainer = Color(0xFF173C91),
    tertiary = Color(0xFF626975), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEFF1F4), onTertiaryContainer = Color(0xFF17191D),
    background = Color(0xFFF7F8FA), onBackground = Color(0xFF17191D),
    surface = Color.White, onSurface = Color(0xFF17191D),
    surfaceVariant = Color(0xFFEFF1F4), onSurfaceVariant = Color(0xFF626975),
    surfaceDim = Color(0xFFE3E6EB), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFEFF1F4),
    surfaceContainerHighest = Color(0xFFE3E6EB), surfaceTint = Color.Transparent,
    outline = Color(0xFF626975), outlineVariant = Color(0xFFE3E6EB),
    inverseSurface = Color(0xFF191B1F), inverseOnSurface = Color(0xFFF3F4F6),
    inversePrimary = Color(0xFF8AACFF),
    primaryFixed = Color(0xFFE8EEFC), primaryFixedDim = Color(0xFF8AACFF),
    onPrimaryFixed = Color(0xFF102654), onPrimaryFixedVariant = Color(0xFF173C91),
    secondaryFixed = Color(0xFFEFF1F4), secondaryFixedDim = Color(0xFFE3E6EB),
    onSecondaryFixed = Color(0xFF17191D), onSecondaryFixedVariant = Color(0xFF626975),
    tertiaryFixed = Color(0xFFEFF1F4), tertiaryFixedDim = Color(0xFFE3E6EB),
    onTertiaryFixed = Color(0xFF17191D), onTertiaryFixedVariant = Color(0xFF626975),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFFCEDEA), onErrorContainer = Color(0xFF8C1D18),
)
internal val DarkColors = darkColorScheme(
    primary = Color(0xFF8AACFF), onPrimary = Color(0xFF102654),
    primaryContainer = Color(0xFF243653), onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFA5ACB8), onSecondary = Color(0xFF191B1F),
    secondaryContainer = Color(0xFF243653), onSecondaryContainer = Color(0xFFDCE6FF),
    tertiary = Color(0xFFA5ACB8), onTertiary = Color(0xFF191B1F),
    tertiaryContainer = Color(0xFF25282E), onTertiaryContainer = Color(0xFFF3F4F6),
    background = Color(0xFF101113), onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF191B1F), onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF25282E), onSurfaceVariant = Color(0xFFA5ACB8),
    surfaceDim = Color(0xFF101113), surfaceBright = Color(0xFF30343B),
    surfaceContainerLowest = Color(0xFF101113), surfaceContainerLow = Color(0xFF191B1F),
    surfaceContainer = Color(0xFF191B1F), surfaceContainerHigh = Color(0xFF25282E),
    surfaceContainerHighest = Color(0xFF30343B), surfaceTint = Color.Transparent,
    outline = Color(0xFFA5ACB8), outlineVariant = Color(0xFF30343B),
    inverseSurface = Color(0xFFF3F4F6), inverseOnSurface = Color(0xFF17191D),
    inversePrimary = Color(0xFF2457D6),
    primaryFixed = Color(0xFFE8EEFC), primaryFixedDim = Color(0xFF8AACFF),
    onPrimaryFixed = Color(0xFF102654), onPrimaryFixedVariant = Color(0xFF173C91),
    secondaryFixed = Color(0xFFEFF1F4), secondaryFixedDim = Color(0xFFE3E6EB),
    onSecondaryFixed = Color(0xFF17191D), onSecondaryFixedVariant = Color(0xFF626975),
    tertiaryFixed = Color(0xFFEFF1F4), tertiaryFixedDim = Color(0xFFE3E6EB),
    onTertiaryFixed = Color(0xFF17191D), onTertiaryFixedVariant = Color(0xFF626975),
    error = Color(0xFFFFB4AB), onError = Color(0xFF601410),
    errorContainer = Color(0xFF44211F), onErrorContainer = Color(0xFFFFDAD6),
)

private val NinetyFont = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)
private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0f) = TextStyle(fontFamily = NinetyFont, fontSize = size.sp,
    lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp,
    fontFeatureSettings = "tnum")
private val NinetyTypography = Typography(
    displayLarge = type(44, 52, FontWeight.Bold, -1f),
    displayMedium = type(38, 46, FontWeight.Bold, -0.8f),
    displaySmall = type(32, 40, FontWeight.Bold, -0.6f),
    headlineLarge = type(32, 40, FontWeight.Bold, -0.6f),
    headlineMedium = type(28, 36, FontWeight.Bold, -0.5f),
    headlineSmall = type(23, 30, FontWeight.SemiBold, -0.3f),
    titleLarge = type(20, 28, FontWeight.SemiBold, -0.2f),
    titleMedium = type(16, 24, FontWeight.SemiBold),
    titleSmall = type(14, 20, FontWeight.SemiBold),
    bodyLarge = type(16, 24), bodyMedium = type(14, 22), bodySmall = type(12, 18),
    labelLarge = type(14, 20, FontWeight.SemiBold),
    labelMedium = type(12, 18, FontWeight.Medium),
    labelSmall = type(11, 16, FontWeight.Medium),
)

internal data class CautionColors(val foreground: Color, val container: Color)

internal val LightCaution = CautionColors(Color(0xFF8A5300), Color(0xFFFFF4DF))
internal val DarkCaution = CautionColors(Color(0xFFFFCF86), Color(0xFF3A2A0E))
private val LocalCautionColors = staticCompositionLocalOf { LightCaution }

internal val MaterialTheme.caution: CautionColors
    @Composable get() = LocalCautionColors.current

@Composable
fun NinetyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = NinetyTypography,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(24.dp)),
        content = {
            CompositionLocalProvider(LocalCautionColors provides if (dark) DarkCaution else LightCaution, content = content)
        },
    )
}
