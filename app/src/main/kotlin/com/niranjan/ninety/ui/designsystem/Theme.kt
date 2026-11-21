package com.niranjan.ninety.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niranjan.ninety.R

private val CoolWhite = Color(0xFFF0F0FA)
private val SoftBlack = Color(0xFF0A0A0A)

internal object NinetyDimensions {
    val hairline = 1.dp
    val tight = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val content = 16.dp
    val gutter = 24.dp
    val touchTarget = 48.dp
    val pill = RoundedCornerShape(50)
    val field = RoundedCornerShape(4.dp)
    val panel = RoundedCornerShape(8.dp)
    val listCard = RoundedCornerShape(14.dp)
    val sheet = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
}

private data class Palette(
    val canvas: Color,
    val panel: Color,
    val control: Color,
    val selection: Color,
    val ink: Color,
    val mutedInk: Color,
    val hairline: Color,
    val controlOutline: Color,
    val error: Color,
    val onError: Color,
    val errorPanel: Color,
    val errorInk: Color,
) {
    // Supply every role so nested Material controls share the same monochrome palette.
    fun scheme(): ColorScheme = lightColorScheme(
        primary = ink, onPrimary = canvas,
        primaryContainer = selection, onPrimaryContainer = ink,
        secondary = mutedInk, onSecondary = canvas,
        secondaryContainer = selection, onSecondaryContainer = ink,
        tertiary = mutedInk, onTertiary = canvas,
        tertiaryContainer = control, onTertiaryContainer = ink,
        background = canvas, onBackground = ink,
        surface = panel, onSurface = ink,
        surfaceVariant = control, onSurfaceVariant = mutedInk,
        surfaceDim = canvas, surfaceBright = panel,
        surfaceContainerLowest = canvas, surfaceContainerLow = panel,
        surfaceContainer = panel, surfaceContainerHigh = panel,
        surfaceContainerHighest = control, surfaceTint = Color.Transparent,
        outline = controlOutline, outlineVariant = hairline,
        inverseSurface = ink, inverseOnSurface = canvas, inversePrimary = canvas,
        primaryFixed = CoolWhite, primaryFixedDim = Color(0xFFE0E0E8),
        onPrimaryFixed = Color.Black, onPrimaryFixedVariant = Color(0xFF5A5A5F),
        secondaryFixed = CoolWhite, secondaryFixedDim = Color(0xFFE0E0E8),
        onSecondaryFixed = Color.Black, onSecondaryFixedVariant = Color(0xFF5A5A5F),
        tertiaryFixed = CoolWhite, tertiaryFixedDim = Color(0xFFE0E0E8),
        onTertiaryFixed = Color.Black, onTertiaryFixedVariant = Color(0xFF5A5A5F),
        scrim = Color.Black,
        error = error, onError = onError,
        errorContainer = errorPanel, onErrorContainer = errorInk,
    )
}

internal val LightColors = Palette(
    canvas = Color.White, panel = Color.White, control = CoolWhite,
    selection = Color(0xFFE6E6F0), ink = Color.Black, mutedInk = Color(0xFF5A5A5F),
    hairline = Color(0xFFE0E0E8), controlOutline = Color(0xFF77777E),
    error = Color(0xFF9D2E2B), onError = Color.White,
    errorPanel = Color(0xFFFAECEA), errorInk = Color(0xFF872724),
).scheme()

internal val DarkColors = Palette(
    canvas = Color.Black, panel = SoftBlack, control = Color(0xFF18181C),
    selection = Color(0xFF29292F), ink = Color.White, mutedInk = CoolWhite,
    hairline = Color(0xFF3A3A3F), controlOutline = Color(0xFF83838D),
    error = Color(0xFFF5AAA4), onError = Color(0xFF400F0C),
    errorPanel = Color(0xFF301C1B), errorInk = Color(0xFFFFC9C3),
).scheme()

private val NinetyFont = FontFamily(
    Font(R.font.d_din_regular, FontWeight.Normal),
    Font(R.font.d_din_bold, FontWeight.Medium),
    Font(R.font.d_din_bold, FontWeight.SemiBold),
    Font(R.font.d_din_bold, FontWeight.Bold),
)

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0.25f) = TextStyle(fontFamily = NinetyFont, fontSize = size.sp,
    lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp,
    fontFeatureSettings = "tnum")

internal val ActionTypography = type(13, 18, FontWeight.Bold, 1.05f)

private val NinetyTypography = Typography(
    displayLarge = type(40, 44, FontWeight.Bold, 1.6f),
    displayMedium = type(34, 38, FontWeight.Bold, 1.3f),
    displaySmall = type(30, 34, FontWeight.Bold, 1.2f),
    headlineLarge = type(32, 36, FontWeight.Bold, 1.2f),
    headlineMedium = type(29, 33, FontWeight.Bold, 1.1f),
    headlineSmall = type(23, 27, FontWeight.Bold, 0.9f),
    titleLarge = type(20, 25, FontWeight.Bold, 0.85f),
    titleMedium = type(17, 22, FontWeight.Bold, 0.35f),
    titleSmall = type(13, 18, FontWeight.Bold, 0.85f),
    bodyLarge = type(16, 24, tracking = 0.32f),
    bodyMedium = type(15, 22, tracking = 0.3f),
    bodySmall = type(13, 19, tracking = 0f),
    labelLarge = type(14, 20, FontWeight.Bold, 0.35f),
    labelMedium = type(12, 17, tracking = 0.55f),
    labelSmall = type(11, 15, tracking = 0.45f),
)

internal data class CautionColors(val foreground: Color, val container: Color)

internal val LightCaution = CautionColors(Color(0xFF78511B), Color(0xFFF8F1E6))
internal val DarkCaution = CautionColors(Color(0xFFE9C487), Color(0xFF2B2217))
private val LocalCautionColors = staticCompositionLocalOf { LightCaution }

internal val MaterialTheme.caution: CautionColors
    @Composable get() = LocalCautionColors.current

@Composable
fun NinetyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = NinetyTypography,
        shapes = Shapes(extraSmall = NinetyDimensions.field, small = NinetyDimensions.panel,
            medium = NinetyDimensions.panel, large = NinetyDimensions.panel,
            extraLarge = RoundedCornerShape(16.dp)),
        content = {
            CompositionLocalProvider(LocalCautionColors provides if (dark) DarkCaution else LightCaution, content = content)
        },
    )
}
