package oolong.client.ui.appwidget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import oolong.client.MainActivity

/**
 * The widgets' colours. They follow the system's light or dark, not the app's theme: a widget
 * sits on the launcher, whose look the system sets. The surface is opaque, as nothing behind a
 * widget can be blurred.
 */
internal object WidgetColors {
    val surface = pair(0xFFFFFFFF, 0xFF1F2227)
    val text = pair(0xDE000000, 0xFFECEEF2)
    val textSecondary = pair(0x8C000000, 0xFF9AA0AB)
    val textTertiary = pair(0x5C000000, 0xFF6B717C)
    val divider = pair(0x12000000, 0x12FFFFFF)
    val accent = pair(0xFF3E6FC0, 0xFF5588D7)
    val accentContainer = pair(0x1F3E6FC0, 0x2E5588D7)
    val hot = pair(0xFFD94545, 0xFFEF6B6B)
    val hotContainer = pair(0x1AD94545, 0x29EF6B6B)
    val placeholder = pair(0x0F000000, 0x14FFFFFF)
    val pictureDim = pair(0x00000000, 0x4D000000)

    private fun pair(day: Long, night: Long): ColorProvider = ColorProvider(day = Color(day), night = Color(night))
}

/** The inset of a widget's content from its edges, at its sides and at its top and bottom. */
internal val WidgetPadding = 16.dp
internal val WidgetPaddingVertical = 10.dp

/** A heading's height, with the gap under it. */
internal val WidgetHeaderHeight = 20.dp
internal val WidgetHeaderGap = 4.dp

/** What a padded widget with a heading has left for its rows. */
internal fun listRoom(height: androidx.compose.ui.unit.Dp) = height - WidgetPaddingVertical * 2 - WidgetHeaderHeight - WidgetHeaderGap

/** The widget's surface, with the system's widget corners where there are any to follow. */
@Composable
internal fun WidgetSurface(
    modifier: GlanceModifier = GlanceModifier,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetColors.surface)
            .widgetCorners()
            .then(if (padded) GlanceModifier.padding(horizontal = WidgetPadding, vertical = WidgetPaddingVertical) else GlanceModifier),
        content = content,
    )
}

/** Android 12 rounds widgets itself; before it the launcher draws them square, and so do we. */
internal fun GlanceModifier.widgetCorners(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cornerRadius(android.R.dimen.system_app_widget_background_radius) else this

/** A widget's heading: its name, and at the end a quiet note such as when it was loaded. */
@Composable
internal fun WidgetHeader(title: String, note: String? = null, onClick: Action? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = WidgetHeaderGap).height(WidgetHeaderHeight + WidgetHeaderGap).then(onClick?.let { GlanceModifier.clickable(it) } ?: GlanceModifier),
    ) {
        Text(text = title, style = TextStyle(color = WidgetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        if (note != null) {
            Box(modifier = GlanceModifier.defaultWeight()) {}
            Text(text = note, style = TextStyle(color = WidgetColors.textTertiary, fontSize = 11.5.sp), maxLines = 1)
        }
    }
}

/** What a widget says when it has nothing to show: [message], and a tap that does [onClick]. */
@Composable
internal fun WidgetMessage(message: String, onClick: Action) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier.fillMaxSize().clickable(onClick),
    ) {
        Text(
            text = message,
            style = TextStyle(color = WidgetColors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center),
        )
    }
}

/** A row of a widget's list, [height] tall, opening [onClick]. */
@Composable
internal fun WidgetRow(height: Dp, onClick: Action, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().height(height).clickable(onClick),
        content = content,
    )
}

/** The rank before a ranked row: the top three as dark as the text, the rest faint. */
@Composable
internal fun RankText(rank: Int) {
    Text(
        text = rank.toString(),
        style = TextStyle(
            color = if (rank <= 3) WidgetColors.text else WidgetColors.textTertiary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        modifier = GlanceModifier.width(18.dp),
        maxLines = 1,
    )
}

/** Opens [uri], one of the app's own oolong:// links, in the app. */
internal fun openLink(context: Context, uri: String): Action =
    actionStartActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )

/** Opens the app as the launcher does. */
internal fun openApp(context: Context): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
    )

internal object WidgetLinks {
    fun thread(id: Long) = "oolong://thread/$id"

    fun forum(name: String) = "oolong://forum/${Uri.encode(name)}"

    fun topic(id: Long, name: String) = "oolong://topic/$id?name=${Uri.encode(name)}"
}
