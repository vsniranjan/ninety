package com.niranjan.ninety.ui.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pairs used by enabled fields, buttons, chips, dialogs, feedback and page text. */
class ThemeContrastTest {
    @Test fun lightComponentContrast() = verify(LightColors, LightCaution)
    @Test fun darkComponentContrast() = verify(DarkColors, DarkCaution)

    private fun verify(colors: ColorScheme, caution: CautionColors) {
        with(colors) {
            listOf(
                "caution on page" to (caution.foreground to background),
                "caution on surface" to (caution.foreground to surface),
                "pending leave chip" to (caution.foreground to caution.container),
                "holiday date" to (onSurfaceVariant to tertiaryContainer),
                "page text" to (onBackground to background),
                "page secondary text" to (onSurfaceVariant to background),
                "field/dialog text" to (onSurface to surface),
                "field/dialog secondary text" to (onSurfaceVariant to surface),
                "primary button" to (onPrimary to primary),
                "text/outlined button" to (primary to background),
                "date picker action" to (primary to surfaceContainerHigh),
                "date picker secondary text" to (onSurfaceVariant to surfaceContainerHigh),
                "selected chip" to (onSecondaryContainer to secondaryContainer),
                "selected date" to (onPrimaryContainer to primaryContainer),
                "error text" to (error to background),
                "snackbar" to (inverseOnSurface to inverseSurface),
            ).forEach { (component, pair) -> assertContrast(component, pair.first, pair.second, 4.5f) }
            assertContrast("field boundary", outline, surface, 3f)
            assertContrast("outlined button boundary", outline, background, 3f)
            assertContrast("focus boundary", primary, surface, 3f)
        }
    }

    private fun assertContrast(component: String, foreground: Color, background: Color, minimum: Float) {
        val a = foreground.luminance()
        val b = background.luminance()
        val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
        assertTrue("$component contrast $ratio must be >= $minimum", ratio >= minimum)
    }
}
