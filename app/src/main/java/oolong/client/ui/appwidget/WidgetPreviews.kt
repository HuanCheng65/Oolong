package oolong.client.ui.appwidget

import android.content.Context
import android.os.Build
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import oolong.client.BuildConfig

/**
 * The widgets as the launcher's widget list shows them before they are added: drawn by the
 * widgets themselves from real content, which the list supports from Android 15. The system
 * limits how often previews may be set, so they are set once for each version of the app.
 */
internal object WidgetPreviews {
    /** The size a preview is drawn at, a widget's default four cells by two. */
    val Size = SizeMode.Responsive(setOf(DpSize(360.dp, 220.dp)))

    private const val PREFS = "appwidget"
    private const val VERSION_KEY = "previews_version"

    fun publishOnce(context: Context, scope: CoroutineScope) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(VERSION_KEY, 0) == BuildConfig.VERSION_CODE) return
        scope.launch(Dispatchers.Default) {
            val manager = GlanceAppWidgetManager(context)
            val receivers = listOf(
                HotTopicsWidgetReceiver::class,
                ThreadRankingWidgetReceiver::class,
                ForumHotWidgetReceiver::class,
                ForumsWidgetReceiver::class,
            )
            val results = receivers.map { runCatching { manager.setWidgetPreviews(it) }.getOrNull() }
            // Rate limited or failed ones are tried again at the next start
            if (results.all { it == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }) {
                prefs.edit { putInt(VERSION_KEY, BuildConfig.VERSION_CODE) }
            }
        }
    }
}
