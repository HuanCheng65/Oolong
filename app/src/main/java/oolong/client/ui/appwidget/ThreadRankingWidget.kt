package oolong.client.ui.appwidget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import oolong.client.R
import oolong.client.util.StringUtil.getShortNumString

/** The day's most read threads, each with its forum, its readers and its first picture. */
class ThreadRankingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = WidgetPreviews.Size

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

    private class Loaded(val content: RankingContent, val thumbs: List<Bitmap?>, val avatars: List<Bitmap?>)

    private suspend fun load(context: Context, fresh: Boolean): Loaded? {
        val content = WidgetData.ranking(context, fresh)?.takeIf { it.threads.isNotEmpty() } ?: return null
        val threads = content.threads
        val thumb = context.px(ThumbSize)
        val thumbs = WidgetPictures.loadAll(context, threads.map { it.image }, thumb, thumb, context.px(9.dp).toFloat())
        val avatar = context.px(AvatarSize)
        val avatars = WidgetPictures.loadAll(context, threads.map { it.forumAvatar }, avatar, avatar, context.px(4.dp).toFloat())
        return Loaded(content, thumbs, avatars)
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
            val threads = loaded.content.threads
            val (thumbs, avatars) = loaded.thumbs to loaded.avatars
            WidgetHeader(context.getString(R.string.hot_section_threads), context.getString(R.string.appwidget_ranking_note))
            val (rows, rowHeight) = fitRows(listRoom(size.height), RowHeight, RowLeast, threads.size)
            threads.take(rows).forEachIndexed { index, thread ->
                WidgetRow(rowHeight, openLink(context, WidgetLinks.thread(thread.id))) {
                    RankText(index + 1)
                    Spacer(GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = thread.title,
                            style = TextStyle(color = WidgetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            maxLines = 1,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WidgetPicture(avatars.getOrNull(index), AvatarSize, 4.dp)
                            Spacer(GlanceModifier.width(4.dp))
                            val forum = context.getString(R.string.appwidget_forum_title, thread.forum)
                            val readers = context.getString(R.string.rank_reason_readers, thread.readers.getShortNumString())
                            Text(text = "$forum · $readers", style = TextStyle(color = WidgetColors.textSecondary, fontSize = 12.sp), maxLines = 1)
                        }
                    }
                    if (thread.image.isNotEmpty()) {
                        Spacer(GlanceModifier.width(10.dp))
                        WidgetPicture(thumbs.getOrNull(index), minOf(ThumbSize, rowHeight - 6.dp), 9.dp)
                    }
                }
            }
        }
    }

    private companion object {
        val ThumbSize = 42.dp
        val AvatarSize = 14.dp
        val RowHeight = 50.dp
        val RowLeast = 42.dp
    }
}

class ThreadRankingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ThreadRankingWidget()
}
