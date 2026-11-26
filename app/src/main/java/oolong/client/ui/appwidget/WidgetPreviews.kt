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

    /** The size a three-row widget's preview is drawn at. */
    val TallSize = SizeMode.Responsive(setOf(DpSize(360.dp, 330.dp)))

    private const val PREFS = "appwidget"
    private const val DONE_KEY = "previews_set"

    private val receivers = listOf(
        HotTopicsWidgetReceiver::class,
        ThreadRankingWidgetReceiver::class,
        ForumHotWidgetReceiver::class,
        ForumsWidgetReceiver::class,
        TopicsAndForumsWidgetReceiver::class,
    )

    /** Sets the previews not yet set by this version; those turned away are tried at the next start. */
    fun publishOnce(context: Context, scope: CoroutineScope) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val done = prefs.getStringSet(DONE_KEY, emptySet()).orEmpty()
        val missing = receivers.filter { mark(it.java.name) !in done }
        if (missing.isEmpty()) return
        scope.launch(Dispatchers.Default) {
            val manager = GlanceAppWidgetManager(context)
            val set = missing.filter {
                runCatching { manager.setWidgetPreviews(it) }.getOrNull() == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
            }
            // Marks of an older version are dropped, so the set holds only this version's
            val kept = done.filter { it.startsWith("${BuildConfig.VERSION_CODE}:") }
            prefs.edit { putStringSet(DONE_KEY, (kept + set.map { mark(it.java.name) }).toSet()) }
        }
    }

    private fun mark(receiver: String) = "${BuildConfig.VERSION_CODE}:$receiver"
}
