package oolong.client.ui.appwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first
import oolong.client.R

/** One forum's hot threads under the forum's name, washed in its avatar's colour as its page is. */
class ForumHotWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = WidgetPreviews.Size

    /** Shown with the forum the user visited last, or asking for one when there is none. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val name = context.widgetEntryPoint.home().visitedForums.first().firstOrNull()?.name
        val loaded = name?.let { load(context, it, fresh = true) }
        provideContent { Content(name, AppWidgetManager.INVALID_APPWIDGET_ID, Shown(loaded, loading = false)) }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val firstName = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)[ForumNameKey]
        val initial = firstName?.let { load(context, it, fresh = false) }
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val name = currentState(ForumNameKey)
            val shown = rememberShown(initial, name, currentState(RefreshKey)) { fresh -> name?.let { load(context, it, fresh) } }
            Content(name, appWidgetId, shown)
        }
    }

    private class Loaded(val content: ForumContent, val thumbs: List<Bitmap?>, val avatar: Bitmap?, val wash: Bitmap?)

    private suspend fun load(context: Context, name: String, fresh: Boolean): Loaded? {
        val content = WidgetData.forum(context, name, fresh) ?: return null
        val thumb = context.px(ThumbSize)
        val thumbs = WidgetPictures.loadAll(context, content.threads.map { it.image }, thumb, thumb, context.px(9.dp).toFloat())
        val avatarPx = context.px(AvatarSize)
        val avatar = WidgetPictures.load(context, content.avatar, avatarPx, avatarPx, avatarPx * 0.3f)
        return Loaded(content, thumbs, avatar, WidgetPictures.wash(avatar, context.px(WashHeight)))
    }

    @Composable
    private fun Content(name: String?, appWidgetId: Int, shown: Shown<Loaded>) {
        val context = LocalContext.current
        val size = LocalSize.current
        val loaded = shown.value
        when {
            name == null -> WidgetSurface {
                WidgetMessage(context.getString(R.string.appwidget_pick_forum), actionStartActivity(setupIntent(context, appWidgetId)))
            }

            loaded == null -> WidgetSurface {
                if (!shown.loading) WidgetMessage(context.getString(R.string.appwidget_load_failed), refreshWidget)
            }

            else -> Box(modifier = GlanceModifier.fillMaxSize()) {
                val (content, thumbs) = loaded.content to loaded.thumbs
                val (avatar, wash) = loaded.avatar to loaded.wash
                WidgetSurface(padded = false) {}
                if (wash != null) {
                    Image(
                        ImageProvider(wash),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = GlanceModifier.fillMaxWidth().height(WashHeight).widgetCorners(),
                    )
                }
                Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = WidgetPadding, vertical = 12.dp)) {
                    Header(content, avatar)
                    val narrow = size.width < 200.dp
                    val rowHeight = if (narrow) NarrowRowHeight else RowHeight
                    val rows = ((size.height - 24.dp - HeaderHeight) / rowHeight).toInt().coerceAtLeast(1)
                    content.threads.take(rows).forEachIndexed { index, thread ->
                        if (narrow) NarrowRow(thread) else ThreadRow(thread, thumbs.getOrNull(index))
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(content: ForumContent, avatar: Bitmap?) {
        val context = LocalContext.current
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth().height(HeaderHeight).clickable(openLink(context, WidgetLinks.forum(content.name))),
        ) {
            ForumAvatar(avatar, AvatarSize, content.signedIn, mark = 14.dp)
            Spacer(GlanceModifier.width(8.dp))
            Column {
                Text(
                    text = context.getString(R.string.appwidget_forum_title, content.name),
                    style = TextStyle(color = WidgetColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(text = context.getString(R.string.tab_forum_hot), style = TextStyle(color = WidgetColors.textSecondary, fontSize = 11.5.sp), maxLines = 1)
            }
        }
    }

    @Composable
    private fun ThreadRow(thread: ForumContent.Item, thumb: Bitmap?) {
        val context = LocalContext.current
        WidgetRow(RowHeight, openLink(context, WidgetLinks.thread(thread.id))) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = thread.title, style = TextStyle(color = WidgetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Replies(thread.replies)
            }
            if (thread.image.isNotEmpty()) {
                Spacer(GlanceModifier.width(12.dp))
                WidgetPicture(thumb, ThumbSize, 9.dp)
            }
        }
    }

    @Composable
    private fun NarrowRow(thread: ForumContent.Item) {
        val context = LocalContext.current
        Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp).clickable(openLink(context, WidgetLinks.thread(thread.id)))) {
            Text(text = thread.title, style = TextStyle(color = WidgetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 2)
            Replies(thread.replies)
        }
    }

    @Composable
    private fun Replies(count: Int) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                ImageProvider(R.drawable.appwidget_replies),
                contentDescription = null,
                colorFilter = ColorFilter.tint(WidgetColors.textTertiary),
                modifier = GlanceModifier.size(13.dp),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(text = count.toString(), style = TextStyle(color = WidgetColors.textSecondary, fontSize = 12.sp), maxLines = 1)
        }
    }

    internal companion object {
        val ForumNameKey = stringPreferencesKey("forum_name")
        private val ThumbSize = 42.dp
        private val AvatarSize = 34.dp
        private val HeaderHeight = 44.dp
        private val RowHeight = 50.dp
        private val NarrowRowHeight = 68.dp
        private val WashHeight = 110.dp

        fun setupIntent(context: Context, appWidgetId: Int): Intent =
            Intent(context, WidgetSetupActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

class ForumHotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ForumHotWidget()
}
