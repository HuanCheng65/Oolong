package oolong.client.ui.appwidget

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import java.util.Date
import kotlin.math.roundToInt

/** [dp] in the screen's pixels, for sizing bitmaps. */
internal fun Context.px(dp: Dp): Int = (dp.value * resources.displayMetrics.density).roundToInt()

/** When something was loaded, as the system's clock shows the time. */
internal fun Context.clock(time: Long): String = DateFormat.getTimeFormat(this).format(Date(time))

/** [bitmap] at [size], or a quiet square in its place when there is none. */
@Composable
internal fun WidgetPicture(bitmap: Bitmap?, size: Dp, radius: Dp, modifier: GlanceModifier = GlanceModifier) {
    if (bitmap != null) {
        Box(modifier = modifier.size(size)) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.size(size),
            )
            // Dimmed at night, as pictures in the app are: a white screenshot would glare off the
            // dark surface. Only where the veil can take the picture's rounded corners
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Settings.of(LocalContext.current)[SettingKeys.ImageDarkenWhenNightMode]) {
                Box(modifier = GlanceModifier.size(size).background(WidgetColors.pictureDim).cornerRadius(radius)) {}
            }
        }
    } else {
        val rounded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceModifier.cornerRadius(radius) else GlanceModifier
        Box(modifier = modifier.size(size).background(WidgetColors.placeholder).then(rounded)) {}
    }
}

/** A small label such as 新 or 热 after a topic's name. */
@Composable
internal fun WidgetTag(text: String, color: ColorProvider, container: ColorProvider) {
    val rounded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceModifier.cornerRadius(5.dp) else GlanceModifier
    Text(
        text = text,
        style = TextStyle(color = color, fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
        modifier = GlanceModifier.background(container).then(rounded).padding(horizontal = 4.dp, vertical = 0.dp),
    )
}

/** A forum's avatar, with the signed-in mark at its corner when [signedIn]. */
@Composable
internal fun ForumAvatar(bitmap: Bitmap?, size: Dp, signedIn: Boolean, mark: Dp) {
    // The mark hangs off the avatar's corner, so the box is wider than the avatar by a little
    Box(contentAlignment = Alignment.BottomEnd, modifier = GlanceModifier.size(size + mark / 4)) {
        Box(contentAlignment = Alignment.TopStart, modifier = GlanceModifier.size(size + mark / 4)) {
            WidgetPicture(bitmap, size, radius = size * 0.3f)
        }
        if (signedIn) {
            Image(provider = ImageProvider(oolong.client.R.drawable.appwidget_signed), contentDescription = null, modifier = GlanceModifier.size(mark))
        }
    }
}

/** Loads a widget again, as a tap on its failed-to-load message asks. */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) {
            it.toMutablePreferences().apply { this[RefreshKey] = System.currentTimeMillis() }
        }
        val manager = GlanceAppWidgetManager(context)
        val widgets: List<GlanceAppWidget> = listOf(HotTopicsWidget(), ThreadRankingWidget(), ForumHotWidget(), ForumsWidget(), TopicsAndForumsWidget())
        widgets.firstOrNull { glanceId in manager.getGlanceIds(it.javaClass) }?.update(context, glanceId)
    }
}

internal val refreshWidget get() = actionRunCallback<RefreshWidgetAction>()

/** What a widget shows: [value] once there is one, else null while [loading] or after the load failed. */
internal data class Shown<T>(val value: T?, val loading: Boolean)

/** Set to the time by a tap asking a widget to load again; widgets load again whenever it changes. */
internal val RefreshKey = longPreferencesKey("refresh")

/**
 * What [load] gives, again whenever [keys] change: first what was kept from the last load, at once,
 * then a fresh load. [initial] is what was kept for the first keys, read before the widget showed.
 *
 * Loading here rather than before the content is provided is what makes a widget that is already
 * showing load again: an update to a running widget only recomposes it.
 */
@Composable
internal fun <T : Any> rememberShown(initial: T?, vararg keys: Any?, load: suspend (fresh: Boolean) -> T?): Shown<T> {
    // The first keys' kept load is [initial]; later keys show their own while the fresh one runs
    val firstRun = remember { booleanArrayOf(true) }
    val shown by produceState(Shown(initial, loading = true), *keys) {
        if (!firstRun[0]) value = Shown(load(false), loading = true)
        firstRun[0] = false
        value = Shown(load(true), loading = false)
    }
    return shown
}
