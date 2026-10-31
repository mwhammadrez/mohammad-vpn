package ir.mohammad.vpn.surfaces

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ir.mohammad.vpn.R

/** Colour role, resolved to a real colour by the surface that draws it. */
enum class SurfaceColor { EMERALD, AMBER, ROSE, MUTED }

/**
 * The only state type the app widget and the quick-settings tile know about.
 *
 * SPEC ST-* / AC-W1, AC-W3. When M4 replaces the five-state machine with ten states, only
 * [SurfaceStateMapper] changes: both surfaces consume this enum and keep working. That is the
 * direction of the dependency the milestone requires — surfaces depend on the projection, not on
 * the machine.
 */
enum class SurfaceState(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @StringRes val actionRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    val colorRole: SurfaceColor,
    val tileActive: Boolean,
    val tileAvailable: Boolean,
    val canToggle: Boolean,
) {
    /** No profile loaded: the bundle is missing or empty. */
    NEEDS_APP(
        R.string.widget_title_needs_app, R.string.widget_subtitle_needs_app, R.string.widget_action_open,
        R.string.surface_desc_needs_app, R.drawable.ic_surface_needs_app,
        SurfaceColor.MUTED, tileActive = false, tileAvailable = false, canToggle = false,
    ),

    /** VPN consent has never been granted. */
    PERMISSION_NEEDED(
        R.string.widget_title_permission, R.string.widget_subtitle_permission, R.string.widget_action_open,
        R.string.surface_desc_permission, R.drawable.ic_surface_permission,
        SurfaceColor.AMBER, tileActive = false, tileAvailable = false, canToggle = false,
    ),

    IDLE(
        R.string.widget_title_idle, R.string.widget_subtitle_idle, R.string.widget_action_connect,
        R.string.surface_desc_idle, R.drawable.ic_surface_idle,
        SurfaceColor.MUTED, tileActive = false, tileAvailable = true, canToggle = true,
    ),

    CONNECTING(
        R.string.widget_title_connecting, R.string.widget_subtitle_connecting, R.string.widget_action_wait,
        R.string.surface_desc_connecting, R.drawable.ic_surface_connecting,
        SurfaceColor.AMBER, tileActive = false, tileAvailable = true, canToggle = false,
    ),

    CONNECTED(
        R.string.widget_title_connected, R.string.widget_subtitle_connected, R.string.widget_action_disconnect,
        R.string.surface_desc_connected, R.drawable.ic_surface_connected,
        SurfaceColor.EMERALD, tileActive = true, tileAvailable = true, canToggle = true,
    ),

    DISCONNECTING(
        R.string.widget_title_disconnecting, R.string.widget_subtitle_disconnecting, R.string.widget_action_wait,
        R.string.surface_desc_disconnecting, R.drawable.ic_surface_disconnecting,
        SurfaceColor.AMBER, tileActive = false, tileAvailable = true, canToggle = false,
    ),

    FAILED(
        R.string.widget_title_failed, R.string.widget_subtitle_failed, R.string.widget_action_connect,
        R.string.surface_desc_failed, R.drawable.ic_surface_failed,
        SurfaceColor.ROSE, tileActive = false, tileAvailable = true, canToggle = true,
    ),
    ;

    fun title(context: Context): String = context.getString(titleRes)
    fun subtitle(context: Context): String = context.getString(subtitleRes)
    fun action(context: Context): String = context.getString(actionRes)
    fun description(context: Context): String = context.getString(descriptionRes)
}
