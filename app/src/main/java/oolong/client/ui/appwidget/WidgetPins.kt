package oolong.client.ui.appwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.launch
import oolong.client.util.pendingIntentFlagMutable

/**
 * Putting widgets on the home screen from inside the app, where the launcher lets apps ask: for
 * those who can't find the launcher's own list of widgets.
 */
internal object WidgetPins {
    fun supported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /** Asks the launcher to place a [receiver] widget; false where it can't be asked. */
    fun pin(context: Context, receiver: Class<*>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !supported(context)) return false
        return AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, receiver), null, null)
    }

    /**
     * Asks the launcher to place a forum widget already set up for [forum] showing [shows]; the
     * setup is kept for it once the user places it.
     */
    fun pinForum(context: Context, forum: String, shows: ForumShows): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !supported(context)) return false
        val placed = Intent(context, WidgetPinnedReceiver::class.java)
            .putExtra(EXTRA_FORUM, forum)
            .putExtra(EXTRA_SHOWS, shows.name)
        // Mutable, as the launcher adds the new widget's id to it
        val callback = PendingIntent.getBroadcast(context, 0, placed, PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlagMutable())
        return AppWidgetManager.getInstance(context)
            .requestPinAppWidget(ComponentName(context, ForumHotWidgetReceiver::class.java), null, callback)
    }

    const val EXTRA_FORUM = "oolong.client.extra.WIDGET_FORUM"
    const val EXTRA_SHOWS = "oolong.client.extra.WIDGET_SHOWS"
}

/** Keeps the setup of a forum widget placed from inside the app, once the launcher says where it went. */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val forum = intent.getStringExtra(WidgetPins.EXTRA_FORUM)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || forum == null) return
        val shows = intent.getStringExtra(WidgetPins.EXTRA_SHOWS) ?: ForumShows.Latest.name
        val result = goAsync()
        context.widgetEntryPoint.applicationScope().launch {
            try {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) {
                    it.toMutablePreferences().apply {
                        this[ForumHotWidget.ForumNameKey] = forum
                        this[ForumHotWidget.ShowsKey] = shows
                    }
                }
                ForumHotWidget().update(context, glanceId)
            } finally {
                result.finish()
            }
        }
    }
}
