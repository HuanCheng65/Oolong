package oolong.client.ui.appwidget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Loads the widgets about the user's own forums again when what they show changed in the app:
 * a forum followed, unfollowed or signed in to, or another account logged in. Otherwise they
 * would wait for the system's next update, up to half an hour, to catch up.
 */
internal object WidgetUpdates {
    /** Signing in to every forum changes them one after another; one load after the last will do. */
    private const val SETTLE_MILLIS = 2_000L

    @OptIn(FlowPreview::class)
    fun follow(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        val entryPoint = app.widgetEntryPoint
        scope.launch {
            merge(
                entryPoint.followedForums().changes,
                entryPoint.home().account.drop(1).map { },
            ).debounce(SETTLE_MILLIS).collect {
                refreshAll(app, ForumsWidget())
                refreshAll(app, ForumHotWidget())
                refreshAll(app, TopicsAndForumsWidget())
            }
        }
    }

    /** Has every placed [widget] of its kind load afresh, whether it is showing now or not. */
    suspend fun refreshAll(context: Context, widget: GlanceAppWidget) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(widget.javaClass).forEach { id ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) {
                it.toMutablePreferences().apply { this[RefreshKey] = System.currentTimeMillis() }
            }
            widget.update(context, id)
        }
    }
}
