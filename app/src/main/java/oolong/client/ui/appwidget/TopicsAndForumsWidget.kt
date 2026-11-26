package oolong.client.ui.appwidget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import oolong.client.R

/**
 * The hot topics above a row of the user's forums: what Tieba talks about and where the user
 * goes, in one widget. The forums are the followed ones, or the visited ones when logged out.
 */
class TopicsAndForumsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = WidgetPreviews.TallSize

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val loaded = load(context, fresh = true)
        provideContent { Content(Shown(loaded, loading = false)) }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = load(context, fresh = false)
        provideContent {
            val shown = rememberShown(initial, currentState(RefreshKey)) { load(context, it) }
            Content(shown)
        }
    }

    private class Loaded(
        val topics: TopicsContent,
        val thumbs: List<Bitmap?>,
        val forums: List<ForumsContent.Item>,
        val avatars: List<Bitmap?>,
    )

    private suspend fun load(context: Context, fresh: Boolean): Loaded? = coroutineScope {
        val source = if (context.widgetEntryPoint.home().account.first().isNotEmpty()) ForumsSource.Followed else ForumsSource.Visited
        val forumsLoad = async { WidgetData.forums(context, source, fresh) }
        val topics = WidgetData.topics(context, fresh)?.takeIf { it.topics.isNotEmpty() } ?: return@coroutineScope null
        val thumb = context.px(TopicThumbSize)
        val thumbs = WidgetPictures.loadAll(context, topics.topics.map { it.image }, thumb, thumb, context.px(8.dp).toFloat())
        val forums = forumsLoad.await()?.forums.orEmpty().take(FORUMS)
        val avatarPx = context.px(ForumCellAvatarSize)
        val avatars = WidgetPictures.loadAll(context, forums.map { it.avatar }, avatarPx, avatarPx, avatarPx * 0.3f)
        Loaded(topics, thumbs, forums, avatars)
    }

    @Composable
    private fun Content(shown: Shown<Loaded>) {
        val context = LocalContext.current
        val size = LocalSize.current
        WidgetSurface {
            val loaded = shown.value
            if (loaded == null) {
                if (!shown.loading) WidgetMessage(context.getString(R.string.appwidget_load_failed), refreshWidget)
                return@WidgetSurface
            }
            WidgetHeader(context.getString(R.string.hot_section_topics), context.clock(loaded.topics.loadedAt), openLink(context, "oolong://topics"))
            // The forums keep their row; the topics take what room is left above it
            val forumsHeight = if (loaded.forums.isEmpty()) 0.dp else ForumsRowHeight + 17.dp
            val room = size.height - 28.dp - 30.dp - forumsHeight
            val rows = (room / TopicRowHeight).toInt().coerceIn(1, loaded.topics.topics.size)
            // Rows share out what room is left over, up to a point, rather than leave a gap at the bottom
            val rowHeight = minOf(room / rows, MaxRowHeight)
            loaded.topics.topics.take(rows).forEachIndexed { index, topic -> TopicRow(index + 1, topic, loaded.thumbs.getOrNull(index), rowHeight) }
            if (loaded.forums.isNotEmpty()) {
                Spacer(GlanceModifier.height(6.dp))
                Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(WidgetColors.divider)) {}
                Spacer(GlanceModifier.height(10.dp))
                Row(modifier = GlanceModifier.fillMaxWidth().height(ForumsRowHeight)) {
                    loaded.forums.forEachIndexed { index, forum -> ForumCell(forum, loaded.avatars.getOrNull(index)) }
                    repeat(FORUMS - loaded.forums.size) { Box(modifier = GlanceModifier.defaultWeight()) {} }
                }
            }
        }
    }

    private companion object {
        const val FORUMS = 4
        val ForumsRowHeight = 76.dp
        val MaxRowHeight = 48.dp
    }
}

class TopicsAndForumsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TopicsAndForumsWidget()
}
