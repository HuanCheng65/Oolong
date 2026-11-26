package oolong.client.ui.appwidget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import oolong.client.R
import oolong.client.model.HotTopic
import oolong.client.util.StringUtil.getShortNumString

/**
 * The hot topics: a list with small covers; the first topic's cover filling a small widget; and
 * on a tall one the first topic's cover heading the list.
 */
class HotTopicsWidget : GlanceAppWidget() {
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

    private class Loaded(val content: TopicsContent, val thumbs: List<Bitmap?>, val cover: Bitmap?, val hero: Bitmap?)

    private suspend fun load(context: Context, fresh: Boolean): Loaded? {
        val content = WidgetData.topics(context, fresh)?.takeIf { it.topics.isNotEmpty() } ?: return null
        val topics = content.topics
        val thumb = context.px(TopicThumbSize)
        val thumbs = WidgetPictures.loadAll(context, topics.map { it.image }, thumb, thumb, context.px(8.dp).toFloat())
        val cover = WidgetPictures.load(context, topics.first().image, context.px(200.dp), context.px(200.dp), 0f)
        val hero = WidgetPictures.load(context, topics.first().image, context.px(360.dp), context.px(HeroHeight), 0f)
        return Loaded(content, thumbs, cover, hero)
    }

    @Composable
    private fun Content(shown: Shown<Loaded>) {
        val context = LocalContext.current
        val size = LocalSize.current
        val loaded = shown.value
        if (loaded == null) {
            WidgetSurface { if (!shown.loading) WidgetMessage(context.getString(R.string.appwidget_load_failed), refreshWidget) }
            return
        }
        val (content, thumbs) = loaded.content to loaded.thumbs
        val (cover, hero) = loaded.cover to loaded.hero
        val topics = content.topics
        when {
            size.width < 200.dp -> WidgetSurface(padded = false) { Cover(topics.first(), cover) }

            size.height >= 300.dp -> WidgetSurface(padded = false) {
                Hero(topics.first(), hero, context.clock(content.loadedAt))
                val rows = ((size.height - HeroHeight - 12.dp) / HeroRowHeight).toInt().coerceAtLeast(0)
                Column(modifier = GlanceModifier.padding(horizontal = WidgetPadding, vertical = 6.dp)) {
                    topics.drop(1).take(rows).forEachIndexed { index, topic -> TopicRow(index + 2, topic, thumbs.getOrNull(index + 1), HeroRowHeight) }
                }
            }

            else -> WidgetSurface {
                WidgetHeader(context.getString(R.string.hot_section_topics), context.clock(content.loadedAt), openLink(context, "oolong://topics"))
                val rows = ((size.height - 28.dp - 30.dp) / TopicRowHeight).toInt().coerceAtLeast(1)
                topics.take(rows).forEachIndexed { index, topic -> TopicRow(index + 1, topic, thumbs.getOrNull(index), TopicRowHeight) }
            }
        }
    }

    /** The first topic over its own cover, filling the widget. */
    @Composable
    private fun Cover(topic: TopicsContent.Item, cover: Bitmap?) {
        val context = LocalContext.current
        Box(
            contentAlignment = Alignment.BottomStart,
            modifier = GlanceModifier.fillMaxSize().clickable(openLink(context, WidgetLinks.topic(topic.id, topic.name))),
        ) {
            Picture(cover)
            Column(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = WidgetPadding, vertical = 14.dp)) {
                Text(text = context.getString(R.string.hot_section_topics), style = TextStyle(color = OnPictureFaint, fontSize = 11.5.sp), maxLines = 1)
                Text(text = topic.name, style = TextStyle(color = OnPicture, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 2)
            }
        }
    }

    /** The first topic over its cover at the head of a tall widget. */
    @Composable
    private fun Hero(topic: TopicsContent.Item, hero: Bitmap?, time: String) {
        val context = LocalContext.current
        Box(modifier = GlanceModifier.fillMaxWidth().height(HeroHeight).clickable(openLink(context, WidgetLinks.topic(topic.id, topic.name)))) {
            Picture(hero)
            Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = WidgetPadding, vertical = 12.dp)) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = context.getString(R.string.hot_section_topics),
                        style = TextStyle(color = OnPicture, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(text = time, style = TextStyle(color = OnPictureFaint, fontSize = 11.5.sp), maxLines = 1)
                }
                Spacer(GlanceModifier.defaultWeight())
                Text(text = "1 · ${topic.discussCount.getShortNumString()}", style = TextStyle(color = OnPictureFaint, fontSize = 11.5.sp), maxLines = 1)
                Text(text = topic.name, style = TextStyle(color = OnPicture, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }

    @Composable
    private fun Picture(bitmap: Bitmap?) {
        if (bitmap != null) {
            Image(ImageProvider(bitmap), contentDescription = null, contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxSize())
        }
        Image(ImageProvider(R.drawable.appwidget_scrim), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize())
    }

    private companion object {
        val HeroRowHeight = 44.dp
        val HeroHeight = 180.dp
        val OnPicture = ColorProvider(Color.White)
        val OnPictureFaint = ColorProvider(Color.White.copy(alpha = 0.8f))
    }
}

class HotTopicsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HotTopicsWidget()
}

/** A topic thumbnail's size, and the height of a topic's row in a list. */
internal val TopicThumbSize = 30.dp
internal val TopicRowHeight = 39.dp

@Composable
internal fun TopicRow(rank: Int, topic: TopicsContent.Item, thumb: Bitmap?, height: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    WidgetRow(height, openLink(context, WidgetLinks.topic(topic.id, topic.name))) {
        RankText(rank)
        Spacer(GlanceModifier.width(8.dp))
        WidgetPicture(thumb, TopicThumbSize, 8.dp)
        Spacer(GlanceModifier.width(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.defaultWeight()) {
            Text(text = topic.name, style = TextStyle(color = WidgetColors.text, fontSize = 14.sp), maxLines = 1)
            TopicTag(topic.tag)
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = topic.discussCount.getShortNumString(),
            style = TextStyle(color = WidgetColors.textTertiary, fontSize = 12.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun TopicTag(tag: HotTopic.Tag) {
    val context = LocalContext.current
    when (tag) {
        HotTopic.Tag.New -> {
            Spacer(GlanceModifier.width(6.dp))
            WidgetTag(context.getString(R.string.topic_tag_new), WidgetColors.accent, WidgetColors.accentContainer)
        }

        HotTopic.Tag.Hot -> {
            Spacer(GlanceModifier.width(6.dp))
            WidgetTag(context.getString(R.string.topic_tag_hot), WidgetColors.hot, WidgetColors.hotContainer)
        }

        HotTopic.Tag.None -> {}
    }
}
