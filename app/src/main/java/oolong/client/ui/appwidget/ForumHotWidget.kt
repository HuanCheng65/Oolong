package oolong.client.ui.appwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import oolong.client.R
import oolong.client.data.error.getErrorMessage
import oolong.client.util.StringUtil.getShortNumString

/**
 * One forum's threads under its name, washed in its avatar's colour as its page is: by default
 * the newest, answering "what is going on there", or its hot or featured ones. Its header signs
 * the user in to the forum, a thing done daily.
 */
class ForumHotWidget : GlanceAppWidget() {
    override val sizeMode = WidgetSizes.Forum

    override val previewSizeMode = WidgetPreviews.Size

    /** Shown with the forum the user visited last, or asking for one when there is none. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val name = WidgetData.standIn()?.forumName ?: context.widgetEntryPoint.home().visitedForums.first().firstOrNull()?.name
        val loaded = name?.let { load(context, it, ForumShows.Latest, fresh = true) }
        provideContent { Content(name, AppWidgetManager.INVALID_APPWIDGET_ID, Shown(loaded, loading = false), signing = false) }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)
        val initial = state[ForumNameKey]?.let { load(context, it, state[ShowsKey].toShows(), fresh = false) }
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val name = currentState(ForumNameKey)
            val shows = currentState(ShowsKey).toShows()
            val shown = rememberShown(initial, name, shows, refresh = currentState(RefreshKey)) { fresh -> name?.let { load(context, it, shows, fresh) } }
            Content(name, appWidgetId, shown, signing = currentState(SigningKey) == true, signedHere = currentState(SignedKey))
        }
    }

    internal class Loaded(val content: ForumContent, val shows: ForumShows, val thumbs: List<Bitmap?>, val avatar: Bitmap?, val wash: Bitmap?)

    /**
     * Draws the widget for [name] showing [shows], as the setup page previews it; [appWidgetId]
     * is the widget's, or [AppWidgetManager.INVALID_APPWIDGET_ID] for none.
     */
    @Composable
    internal fun Content(name: String?, appWidgetId: Int, shown: Shown<Loaded>, signing: Boolean, signedHere: String? = null) {
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
                val narrow = size.width < 200.dp
                WidgetSurface(padded = false) {}
                if (loaded.wash != null) {
                    Image(
                        ImageProvider(loaded.wash),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = GlanceModifier.fillMaxWidth().height(WashHeight).widgetCorners(),
                    )
                }
                Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = if (narrow) 14.dp else WidgetPadding, vertical = 12.dp)) {
                    // Signed in from here today, it shows so at once, before the forum loads again
                    val signed = content.signedIn || signedHere == signedMark(content.id)
                    Header(content.copy(signedIn = signed), loaded.avatar, signing, narrow)
                    val (rows, rowHeight) = fitRows(
                        size.height - 24.dp - HeaderHeight,
                        if (narrow) NarrowRowHeight else RowHeight,
                        if (narrow) NarrowRowLeast else RowLeast,
                        content.threads.size,
                    )
                    val latest = loaded.shows == ForumShows.Latest
                    Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        content.threads.take(rows).forEachIndexed { index, thread ->
                            if (narrow) NarrowRow(thread, latest) else ThreadRow(thread, latest, thumbs.getOrNull(index), rowHeight)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(content: ForumContent, avatar: Bitmap?, signing: Boolean, narrow: Boolean) {
        val context = LocalContext.current
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth().height(HeaderHeight).clickable(openLink(context, WidgetLinks.forum(content.name))),
        ) {
            WidgetPicture(avatar, if (narrow) NarrowAvatarSize else AvatarSize, radius = (if (narrow) NarrowAvatarSize else AvatarSize) * 0.3f)
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = context.getString(R.string.appwidget_forum_title, content.name),
                style = TextStyle(color = WidgetColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            // Only a followed forum can be signed in to; the level shows once it is
            if (content.liked && content.id != 0L) {
                Spacer(GlanceModifier.width(6.dp))
                when {
                    content.signedIn -> {
                        if (!narrow && content.level > 0) {
                            Text(
                                text = context.getString(R.string.appwidget_level, content.level),
                                style = TextStyle(color = WidgetColors.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.width(6.dp))
                        }
                        Pill(if (narrow) "✓" else context.getString(R.string.tip_signed) + " ✓", filled = false)
                    }

                    signing -> Pill(context.getString(R.string.appwidget_signing), filled = false)

                    else -> Pill(
                        context.getString(R.string.appwidget_sign),
                        filled = true,
                        onClick = actionRunCallback<SignForumAction>(actionParametersOf(ForumIdParam to content.id, ForumNameParam to content.name)),
                    )
                }
            }
        }
    }

    @Composable
    private fun Pill(text: String, filled: Boolean, onClick: androidx.glance.action.Action? = null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .height(26.dp)
                .background(ImageProvider(if (filled) R.drawable.appwidget_chip_filled else R.drawable.appwidget_chip_tonal))
                .padding(horizontal = 11.dp)
                .then(onClick?.let { GlanceModifier.clickable(it) } ?: GlanceModifier),
        ) {
            Text(
                text = text,
                style = TextStyle(color = if (filled) OnAccent else WidgetColors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun ColumnScope.ThreadRow(thread: ForumContent.Item, latest: Boolean, thumb: Bitmap?, height: Dp) {
        val context = LocalContext.current
        WidgetRow(openLink(context, WidgetLinks.thread(thread.id))) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = thread.title, style = TextStyle(color = WidgetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Row {
                    // Among the newest, when comes first, in the accent while it is under an hour old
                    if (latest && thread.time > 0) {
                        Text(text = context.threadTime(thread.time), style = TextStyle(color = timeColor(thread.time), fontSize = 12.sp), maxLines = 1)
                        Text(text = " · ", style = TextStyle(color = WidgetColors.textSecondary, fontSize = 12.sp), maxLines = 1)
                    }
                    val replies = context.getString(R.string.appwidget_replies, thread.replies.getShortNumString())
                    val rest = if (thread.author.isEmpty()) replies else "${thread.author} · $replies"
                    Text(text = rest, style = TextStyle(color = WidgetColors.textSecondary, fontSize = 12.sp), maxLines = 1)
                }
            }
            if (thread.image.isNotEmpty()) {
                Spacer(GlanceModifier.width(12.dp))
                WidgetPicture(thumb, minOf(ThumbSize, height - 8.dp), 8.dp)
            }
        }
    }

    @Composable
    private fun ColumnScope.NarrowRow(thread: ForumContent.Item, latest: Boolean) {
        val context = LocalContext.current
        Column(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(openLink(context, WidgetLinks.thread(thread.id))),
        ) {
            Text(text = thread.title, style = TextStyle(color = WidgetColors.text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium), maxLines = 2)
            if (latest && thread.time > 0) {
                Text(text = context.threadTime(thread.time), style = TextStyle(color = timeColor(thread.time), fontSize = 12.sp), maxLines = 1)
            } else {
                Text(
                    text = context.getString(R.string.appwidget_replies, thread.replies.getShortNumString()),
                    style = TextStyle(color = WidgetColors.textSecondary, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
    }

    internal companion object {
        val ForumNameKey = stringPreferencesKey("forum_name")
        val ShowsKey = stringPreferencesKey("forum_shows")

        /** Set while the header's sign-in runs, so it says so. */
        internal val SigningKey = booleanPreferencesKey("forum_signing")

        /** The forum signed in to from the header, and the day: see [signedMark]. */
        internal val SignedKey = stringPreferencesKey("forum_signed")

        /** [forumId] on today's date, as the header's sign-in leaves it. */
        internal fun signedMark(forumId: Long): String = "$forumId@${DateFormat.format("yyyyMMdd", System.currentTimeMillis())}"

        internal val ForumIdParam = ActionParameters.Key<Long>("forum_id")
        internal val ForumNameParam = ActionParameters.Key<String>("forum_name")

        private val ThumbSize = 40.dp
        private val AvatarSize = 30.dp
        private val NarrowAvatarSize = 26.dp
        private val HeaderHeight = 36.dp
        private val RowHeight = 48.dp
        private val RowLeast = 42.dp
        private val NarrowRowHeight = 62.dp
        private val NarrowRowLeast = 50.dp
        private val WashHeight = 110.dp
        private val OnAccent = ColorProvider(day = androidx.compose.ui.graphics.Color.White, night = androidx.compose.ui.graphics.Color.White)

        /** How fresh a thread's time counts as new, shown in the accent. */
        private const val NewWithinMillis = DateUtils.HOUR_IN_MILLIS

        fun String?.toShows(): ForumShows = ForumShows.entries.firstOrNull { it.name == this } ?: ForumShows.Latest

        fun setupIntent(context: Context, appWidgetId: Int): Intent =
            Intent(context, WidgetSetupActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** [content] with its pictures and its avatar's wash, ready to draw. */
        suspend fun load(context: Context, name: String, shows: ForumShows, fresh: Boolean): Loaded? {
            val content = WidgetData.forum(context, name, shows, fresh) ?: return null
            val thumb = context.px(ThumbSize)
            val thumbs = WidgetPictures.loadAll(context, content.threads.map { it.image }, thumb, thumb, context.px(8.dp).toFloat())
            val avatarPx = context.px(AvatarSize)
            val avatar = WidgetPictures.load(context, content.avatar, avatarPx, avatarPx, avatarPx * 0.3f)
            // Softer on a dark surface, where the full wash turns muddy
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val wash = WidgetPictures.wash(avatar, context.px(WashHeight), if (night) 0.18f else 0.30f)
            return Loaded(content, shows, thumbs, avatar, wash)
        }

        /**
         * When a thread was last active: the time of day today, as a widget is not redrawn each
         * minute and "3 minutes ago" would go stale; the date before.
         */
        private fun Context.threadTime(seconds: Long): String {
            val millis = seconds * 1000
            return if (DateUtils.isToday(millis)) DateFormat.getTimeFormat(this).format(millis)
            else DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
        }

        private fun timeColor(seconds: Long) =
            if (System.currentTimeMillis() - seconds * 1000 < NewWithinMillis) WidgetColors.accent else WidgetColors.textSecondary
    }
}

/** Signs in to the forum a forum widget shows, from its header, and draws the widget again. */
class SignForumAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ForumHotWidget.ForumIdParam] ?: return
        val name = parameters[ForumHotWidget.ForumNameParam] ?: return
        val widget = ForumHotWidget()
        setState(context, glanceId, signing = true, signed = null)
        widget.update(context, glanceId)
        val entry = context.widgetEntryPoint
        val result = runCatching {
            val tbs = entry.accounts().current.value?.tbs ?: throw IllegalStateException(context.getString(R.string.text_login_first))
            entry.sign().sign(id, name, tbs)
        }
        // Signed, it shows so at once; the level comes with the load the change below brings,
        // rather than loading again here and drawing the widget once more in between
        setState(context, glanceId, signing = false, signed = result.getOrNull()?.let { ForumHotWidget.signedMark(id) })
        widget.update(context, glanceId)
        result
            .onSuccess { entry.followedForums().changed() }
            .onFailure { e ->
                val message = if (e is IllegalStateException) e.message.orEmpty() else e.getErrorMessage(context)
                withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
            }
    }

    private suspend fun setState(context: Context, glanceId: GlanceId, signing: Boolean, signed: String?) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) {
            it.toMutablePreferences().apply {
                if (signing) this[ForumHotWidget.SigningKey] = true else remove(ForumHotWidget.SigningKey)
                if (signed != null) this[ForumHotWidget.SignedKey] = signed
            }
        }
    }
}

class ForumHotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ForumHotWidget()
}
