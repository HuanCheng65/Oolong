package oolong.client.ui.appwidget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import oolong.client.R
import oolong.client.util.StringUtil.getShortNumString

/** The day's most read threads, each with its forum, its readers and its first picture. */
class ThreadRankingWidget : GlanceAppWidget() {
    override val sizeMode = WidgetSizes.List

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
            val room = listRoom(size.height)
            val full = fitRows(room, RowHeight, RowLeast, threads.size)
            // Too short for a few two-line rows, as four cells by two are on many phones, the
            // rows go to one line each, as the hot topics' do
            val compact = full.first < CompactBelow && threads.size > full.first
            val (rows, rowHeight) = if (compact) fitRows(room, TopicRowHeight, TopicRowLeast, threads.size) else full
            Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                threads.take(rows).forEachIndexed { index, thread ->
                    if (compact) {
                        CompactRow(index + 1, thread, thumbs.getOrNull(index), rowHeight)
                        return@forEachIndexed
                    }
                    WidgetRow(openLink(context, WidgetLinks.thread(thread.id))) {
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
    }

    /** A thread on one line: its rank, picture and title, and how many read it. */
    @Composable
    private fun ColumnScope.CompactRow(rank: Int, thread: RankingContent.Item, thumb: Bitmap?, height: Dp) {
        val context = LocalContext.current
        WidgetRow(openLink(context, WidgetLinks.thread(thread.id))) {
            RankText(rank)
            Spacer(GlanceModifier.width(8.dp))
            if (thread.image.isNotEmpty()) {
                WidgetPicture(thumb, minOf(TopicThumbSize, height - 6.dp), 8.dp)
                Spacer(GlanceModifier.width(10.dp))
            }
            Text(text = thread.title, style = TextStyle(color = WidgetColors.text, fontSize = 14.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            Text(text = thread.readers.getShortNumString(), style = TextStyle(color = WidgetColors.textTertiary, fontSize = 12.sp), maxLines = 1)
        }
    }

    private companion object {
        /** Fewer two-line rows than this fitting, the rows go to one line. */
        const val CompactBelow = 3
        val ThumbSize = 42.dp
        val AvatarSize = 14.dp
        val RowHeight = 50.dp
        val RowLeast = 42.dp
    }
}

class ThreadRankingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ThreadRankingWidget()
}
