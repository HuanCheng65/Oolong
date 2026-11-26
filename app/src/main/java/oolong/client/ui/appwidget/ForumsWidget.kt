package oolong.client.ui.appwidget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first
import oolong.client.R

/**
 * The forums the user follows, pinned ones first, or those they visited lately: avatars to tap
 * into, in one tidy block rather than icons scattered over the launcher. A mark shows the ones
 * signed in to today.
 */
class ForumsWidget : GlanceAppWidget() {
    override val sizeMode = WidgetSizes.Grid

    override val previewSizeMode = WidgetPreviews.Size

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val loaded = load(context, ForumsSource.Followed, fresh = true)
        provideContent { Content(ForumsSource.Followed, Shown(loaded, loading = false)) }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val firstSource = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)[SourceKey].toSource()
        val initial = load(context, firstSource, fresh = false)
        provideContent {
            val source = currentState(SourceKey).toSource()
            val shown = rememberShown(initial, source, currentState(RefreshKey)) { load(context, source, it) }
            Content(source, shown)
        }
    }

    internal fun String?.toSource() = this?.let { runCatching { ForumsSource.valueOf(it) }.getOrNull() } ?: ForumsSource.Followed

    internal class Loaded(val loggedIn: Boolean, val content: ForumsContent?, val avatars: List<Bitmap?>)

    internal suspend fun load(context: Context, source: ForumsSource, fresh: Boolean): Loaded {
        val loggedIn = context.widgetEntryPoint.home().account.first().isNotEmpty()
        val content = WidgetData.forums(context, source, fresh)
        val avatarPx = context.px(ForumCellAvatarSize)
        val avatars = WidgetPictures.loadAll(context, content?.forums.orEmpty().map { it.avatar }, avatarPx, avatarPx, avatarPx * 0.3f)
        return Loaded(loggedIn, content, avatars)
    }

    @Composable
    internal fun Content(source: ForumsSource, shown: Shown<Loaded>) {
        val context = LocalContext.current
        val size = LocalSize.current
        val loaded = shown.value
        val content = loaded?.content
        val forums = content?.forums.orEmpty()
        val avatars = loaded?.avatars.orEmpty()
        WidgetSurface(padded = false) {
            when {
                loaded == null || (content == null && shown.loading) -> {}

                source == ForumsSource.Followed && !loaded.loggedIn ->
                    WidgetMessage(context.getString(R.string.appwidget_login), openApp(context))

                content == null -> WidgetMessage(context.getString(R.string.appwidget_load_failed), refreshWidget)

                forums.isEmpty() -> WidgetMessage(context.getString(R.string.appwidget_no_forums), openApp(context))

                else -> {
                    val columns = (size.width / CellWidth).toInt().coerceIn(2, 5)
                    // Rows share the height, as many as fit, their avatars shrinking with them
                    val lines = (forums.size + columns - 1) / columns
                    val (rows, rowHeight) = fitRows(size.height - 16.dp, CellHeight, CellLeast, lines)
                    Column(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        forums.take(columns * rows).chunked(columns).forEach { line ->
                            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                line.forEach { forum -> ForumCell(forum, avatars.getOrNull(forums.indexOf(forum)), rowHeight) }
                                repeat(columns - line.size) { Box(modifier = GlanceModifier.defaultWeight()) {} }
                            }
                        }
                    }
                }
            }
        }
    }

    internal companion object {
        val SourceKey = stringPreferencesKey("source")
        private val CellWidth = 76.dp
        private val CellHeight = 80.dp
        private val CellLeast = 62.dp
    }
}

class ForumsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ForumsWidget()
}

/** The size of a forum's avatar in a grid of forums. */
internal val ForumCellAvatarSize = 48.dp

/** A forum cell's name with the gap above it, and room around the cell. */
private val ForumCellNameHeight = 26.dp

@Composable
internal fun androidx.glance.layout.RowScope.ForumCell(forum: ForumsContent.Item, avatar: Bitmap?, height: Dp = 80.dp) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(openLink(context, WidgetLinks.forum(forum.name))),
    ) {
        // What the name under it leaves, up to the usual size
        val avatarSize = minOf(ForumCellAvatarSize, height - ForumCellNameHeight)
        ForumAvatar(avatar, avatarSize, forum.signedIn, mark = minOf(18.dp, avatarSize * 0.38f))
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = forum.name,
            style = TextStyle(color = WidgetColors.text, fontSize = 12.sp, textAlign = TextAlign.Center),
            maxLines = 1,
            modifier = GlanceModifier.padding(horizontal = 4.dp),
        )
    }
}
