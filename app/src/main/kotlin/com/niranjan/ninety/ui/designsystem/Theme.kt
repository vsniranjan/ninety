package com.niranjan.ninety.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private val BaseTypography = Typography()
private val NinetyTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(fontSize = 48.sp, lineHeight = 56.sp),
    headlineSmall = BaseTypography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = BaseTypography.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
    bodySmall = BaseTypography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
)

@Composable
fun NinetyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = NinetyTypography,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(20.dp)),
        content = content,
    )
}
