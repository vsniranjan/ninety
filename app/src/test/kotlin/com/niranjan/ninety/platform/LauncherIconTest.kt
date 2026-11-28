package com.niranjan.ninety.platform

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.niranjan.ninety.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun launcherUsesAdaptiveLayersAndSupportsThemedIcons() {
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertNotNull(icon.background)
        assertNotNull(icon.foreground)
        if (Build.VERSION.SDK_INT >= 33) assertNotNull(icon.monochrome)
    }

    @Test fun launcherMarkFitsTheSafeZoneAndNotificationHasTransparentEdges() {
        val mark = render(R.drawable.ic_launcher_foreground, 108)
        var paintedPixels = 0
        for (y in 0 until mark.height) {
            for (x in 0 until mark.width) {
                if (Color.alpha(mark.getPixel(x, y)) > 0) {
                    paintedPixels++
                    val dx = x + 0.5 - 54
                    val dy = y + 0.5 - 54
                    assertTrue("The mark must fit inside the adaptive safe circle", dx * dx + dy * dy <= 33 * 33)
                }
            }
        }
        assertTrue(paintedPixels > 0)

        val notification = render(R.drawable.ic_ninety, 24)
        for (edge in 0 until 24) {
            assertEquals(0, Color.alpha(notification.getPixel(edge, 0)))
            assertEquals(0, Color.alpha(notification.getPixel(edge, 23)))
            assertEquals(0, Color.alpha(notification.getPixel(0, edge)))
            assertEquals(0, Color.alpha(notification.getPixel(23, edge)))
        }
    }

    private fun render(resource: Int, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val drawable = requireNotNull(context.getDrawable(resource))
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }
}
