package ir.mohammad.vpn

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import ir.mohammad.vpn.surfaces.SurfaceState
import ir.mohammad.vpn.widget.MohammadWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AC-W1 plus the visual snapshot the milestone asks for. The widget is inflated the way the launcher
 * inflates it — through RemoteViews — so a broken layout, a wrong id or a missing string fails here
 * rather than on a home screen.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h120dp-xhdpi")
class WidgetRenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun inflate(state: SurfaceState): View {
        val view = MohammadWidgetProvider.buildViews(context, state).apply(context, null)
        val width = View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY)
        view.measure(width, height)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }

    private fun textOf(view: View, id: Int): String =
        (view.findViewById<TextView>(id)).text.toString()

    @Test
    fun `every state renders its own title, subtitle and action`() {
        SurfaceState.entries.forEach { state ->
            val view = inflate(state)
            assertEquals(state.name, state.title(context), textOf(view, R.id.widget_title))
            assertEquals(state.name, state.subtitle(context), textOf(view, R.id.widget_subtitle))
            assertEquals(state.name, state.action(context), textOf(view, R.id.widget_action))
        }
    }

    @Test
    fun `the icon is set for every state`() {
        SurfaceState.entries.forEach { state ->
            val view = inflate(state)
            assertNotNull("${state.name} has no icon", view.findViewById<View>(R.id.widget_icon))
        }
    }

    @Test
    fun `the root carries the accessibility description`() {
        SurfaceState.entries.forEach { state ->
            val view = inflate(state)
            assertEquals(state.description(context), view.contentDescription)
        }
    }

    @Test
    fun `a 2x1 and a 4x1 render without clipping the action`() {
        listOf(320 to 120, 640 to 120).forEach { (w, h) ->
            val view = MohammadWidgetProvider.buildViews(context, SurfaceState.CONNECTED)
                .apply(context, null)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val action = view.findViewById<TextView>(R.id.widget_action)
            assertTrue("action is invisible at ${w}dp wide", action.width > 0 && action.height > 0)
            assertTrue("layout overflowed at ${w}dp", view.measuredWidth == w)
        }
    }

    @Test
    fun `visual snapshot for the four required states`() {
        // Roborazzi captures a View only when it belongs to a real Activity context, which is also
        // how a launcher inflates a RemoteViews tree.
        val activity = org.robolectric.Robolectric
            .buildActivity(android.app.Activity::class.java)
            .setup()
            .get()

        listOf(
            SurfaceState.IDLE,
            SurfaceState.CONNECTING,
            SurfaceState.CONNECTED,
            SurfaceState.FAILED,
        ).forEach { state ->
            val view = MohammadWidgetProvider.buildViews(activity, state).apply(activity, null)
            activity.setContentView(view)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 640, 240)
            view.captureRoboImage("build/reports/surfaces/widget_${state.name.lowercase()}.png")
        }
    }

    @Test
    fun `no surface text leaks an endpoint`() {
        val forbidden = listOf(
            Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b"""),
            Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"""),
            Regex("""^[A-Za-z0-9_-]{43}$"""),
            Regex("""\b[a-z0-9-]+\.(com|net|org|ir|io|xyz|top|site|online)\b"""),
        )
        SurfaceState.entries.forEach { state ->
            val view = inflate(state)
            collectText(view).forEach { text ->
                forbidden.forEach { pattern ->
                    assertTrue(
                        "${state.name} text '$text' matches ${pattern.pattern}",
                        !pattern.containsMatchIn(text),
                    )
                }
            }
        }
    }

    private fun collectText(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { collectText(view.getChildAt(it)) }
        else -> emptyList()
    }
}
