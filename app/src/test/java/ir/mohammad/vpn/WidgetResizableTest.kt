package ir.mohammad.vpn

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.surfaces.SurfaceState
import ir.mohammad.vpn.widget.MohammadWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * AC-W8: the widget must work at 2x1 and at 4x1.
 *
 * Two halves: the metadata that lets a launcher offer the resize, and an actual measure/layout at
 * both sizes so a layout that only fits 4x1 fails here instead of on a home screen.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WidgetResizableTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val root: File = listOf(File(".."), File("."))
        .map { it.absoluteFile }
        .first { File(it, "app/src/main").isDirectory }

    @Test
    fun `the metadata declares a 2x1 minimum and both resize axes`() {
        val meta = File(root, "app/src/main/res/xml/widget_mohammad_info.xml").readText()

        assertTrue("target cell must be 2x1", meta.contains("android:targetCellWidth=\"2\""))
        assertTrue(meta.contains("android:targetCellHeight=\"1\""))
        assertTrue(
            "resizeMode must allow horizontal and vertical resizing",
            meta.contains("android:resizeMode=\"horizontal|vertical\""),
        )
        assertTrue("a minimum width is required for 2x1", meta.contains("android:minWidth="))
        assertTrue(meta.contains("android:minHeight="))
        assertTrue(meta.contains("android:minResizeWidth="))
        assertTrue(meta.contains("android:initialLayout=\"@layout/widget_mohammad\""))
    }

    @Test
    fun `the same layout measures correctly at 2x1 and 4x1`() {
        // 2x1 and 4x1 in dp at xhdpi -> 2x and 4x.
        listOf(2 to 1, 4 to 1).forEach { (cells, rows) ->
            val widthPx = cells * 70 * 2
            val heightPx = rows * 70 * 2

            val view = MohammadWidgetProvider.buildViews(context, SurfaceState.CONNECTED)
                .apply(context, null)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)

            assertEquals("${cells}x${rows} width", widthPx, view.measuredWidth)
            val action = view.findViewById<View>(R.id.widget_action)
            assertTrue("${cells}x${rows}: action has no size", action.width > 0 && action.height > 0)
            assertTrue(
                "${cells}x${rows}: action overflows the widget",
                action.left + action.width <= view.measuredWidth,
            )
        }
    }
}
