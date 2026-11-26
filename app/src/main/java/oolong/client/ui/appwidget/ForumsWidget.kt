package oolong.client.ui.appwidget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.SizeMode
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
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val firstSource = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)[SourceKey].toSource()
        val initial = load(context, firstSource, fresh = false)
        provideContent {
            val source = currentState(SourceKey).toSource()
            val shown = rememberShown(initial, source, currentState(RefreshKey)) { load(context, source, it) }
            Content(source, shown)
        }
    }

    private fun String?.toSource() = this?.let { runCatching { ForumsSource.valueOf(it) }.getOrNull() } ?: ForumsSource.Followed

    private class Loaded(val loggedIn: Boolean, val content: ForumsContent?, val avatars: List<Bitmap?>)

    private suspend fun load(context: Context, source: ForumsSource, fresh: Boolean): Loaded {
        val loggedIn = context.widgetEntryPoint.home().account.first().isNotEmpty()
        val content = WidgetData.forums(context, source, fresh)
        val avatarPx = context.px(AvatarSize)
        val avatars = WidgetPictures.loadAll(context, content?.forums.orEmpty().map { it.avatar }, avatarPx, avatarPx, avatarPx * 0.3f)
        return Loaded(loggedIn, content, avatars)
    }

    @Composable
    private fun Content(source: ForumsSource, shown: Shown<Loaded>) {
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
                    val rows = ((size.height - 16.dp) / CellHeight).toInt().coerceAtLeast(1)
                    Column(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        forums.take(columns * rows).chunked(columns).forEach { line ->
                            Row(modifier = GlanceModifier.fillMaxWidth().height(CellHeight)) {
                                line.forEach { forum -> Cell(forum, avatars.getOrNull(forums.indexOf(forum))) }
                                repeat(columns - line.size) { Box(modifier = GlanceModifier.defaultWeight()) {} }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.Cell(forum: ForumsContent.Item, avatar: Bitmap?) {
        val context = LocalContext.current
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(openLink(context, WidgetLinks.forum(forum.name))),
        ) {
            ForumAvatar(avatar, AvatarSize, forum.signedIn, mark = 18.dp)
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = forum.name,
                style = TextStyle(color = WidgetColors.text, fontSize = 12.sp, textAlign = TextAlign.Center),
                maxLines = 1,
                modifier = GlanceModifier.padding(horizontal = 4.dp),
            )
        }
    }

    internal companion object {
        val SourceKey = stringPreferencesKey("source")
        private val AvatarSize = 48.dp
        private val CellWidth = 84.dp
        private val CellHeight = 90.dp
    }
}

class ForumsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ForumsWidget()
}
