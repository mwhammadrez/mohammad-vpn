package ir.mohammad.vpn.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import ir.mohammad.vpn.MainActivity
import ir.mohammad.vpn.R
import ir.mohammad.vpn.surfaces.SurfaceColor
import ir.mohammad.vpn.surfaces.SurfaceState
import ir.mohammad.vpn.surfaces.SurfaceStore
import ir.mohammad.vpn.surfaces.TunnelController

/**
 * Home-screen widget. AC-W1, AC-W2.
 *
 * It draws nothing but a [SurfaceState]: no endpoint, no profile name, no key. A tap sends the same
 * toggle the in-app dial sends, and falls back to opening the app when there is nothing the user can
 * toggle yet (no profile, no VPN consent).
 */
class MohammadWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val state = SurfaceStore.currentState(context)
        ids.forEach { manager.updateAppWidget(it, buildViews(context, state)) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // Re-render on resize so the 2x1 and 4x1 forms both lay out correctly.
        manager.updateAppWidget(appWidgetId, buildViews(context, SurfaceStore.currentState(context)))
    }

    override fun onEnabled(context: Context) = updateAll(context)

    override fun onDisabled(context: Context) = Unit

    companion object {

        /** Derived from the context, never a string literal: the package name is not data. */
        fun component(context: Context): ComponentName =
            ComponentName(context, MohammadWidgetProvider::class.java)

        /** Called by [ir.mohammad.vpn.surfaces.SurfaceUpdater] on every state change. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(component(context))
            if (ids.isEmpty()) return
            val views = buildViews(context, SurfaceStore.currentState(context))
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        fun buildViews(context: Context, state: SurfaceState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_mohammad)
            val color = colorOf(context, state.colorRole)

            views.setImageViewResource(R.id.widget_icon, state.iconRes)
            views.setInt(R.id.widget_icon, "setColorFilter", color)
            views.setTextViewText(R.id.widget_title, state.title(context))
            views.setTextColor(R.id.widget_title, color)
            views.setTextViewText(R.id.widget_subtitle, state.subtitle(context))
            views.setTextViewText(R.id.widget_action, state.action(context))
            views.setContentDescription(R.id.widget_root, state.description(context))
            views.setOnClickPendingIntent(R.id.widget_root, togglePendingIntent(context, state))
            return views
        }

        /**
         * The same toggle the dial sends. When the user cannot toggle yet, the tap opens the app
         * instead of starting a service that would immediately fail.
         */
        fun togglePendingIntent(context: Context, state: SurfaceState): PendingIntent {
            val actionable = isActionable(
                state = state,
                hasProfile = SurfaceStore.hasProfile(context),
                permissionGranted = SurfaceStore.permissionGranted(context),
            )
            return intentFor(context, actionable)
        }

        /**
         * The decision, separated from the Intent so it is testable without faking VpnService.
         * A surface may only send a tunnel action when there is a profile and consent.
         */
        fun isActionable(state: SurfaceState, hasProfile: Boolean, permissionGranted: Boolean): Boolean =
            state.canToggle && hasProfile && permissionGranted

        fun intentFor(context: Context, actionable: Boolean): PendingIntent {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return if (actionable) {
                PendingIntent.getForegroundService(context, 0, TunnelController.toggleIntent(context), flags)
            } else {
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    flags,
                )
            }
        }

        fun colorOf(context: Context, role: SurfaceColor): Int = ContextCompat.getColor(
            context,
            when (role) {
                SurfaceColor.EMERALD -> R.color.surface_mint
                SurfaceColor.AMBER -> R.color.surface_amber
                SurfaceColor.ROSE -> R.color.surface_rose
                SurfaceColor.MUTED -> R.color.surface_text_muted
            },
        )
    }
}
