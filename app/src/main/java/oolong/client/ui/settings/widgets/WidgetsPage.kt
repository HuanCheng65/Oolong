package oolong.client.ui.settings.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.LifecycleResumeEffect
import oolong.client.R
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.ui.appwidget.ForumHotWidget
import oolong.client.ui.appwidget.ForumHotWidget.Companion.toShows
import oolong.client.ui.appwidget.ForumHotWidgetReceiver
import oolong.client.ui.appwidget.ForumShows
import oolong.client.ui.appwidget.ForumsSource
import oolong.client.ui.appwidget.ForumsWidget
import oolong.client.ui.appwidget.ForumsWidgetReceiver
import oolong.client.ui.appwidget.HotTopicsWidgetReceiver
import oolong.client.ui.appwidget.ThreadRankingWidgetReceiver
import oolong.client.ui.appwidget.TopicsAndForumsWidgetReceiver
import oolong.client.ui.appwidget.WidgetPins
import oolong.client.ui.appwidget.WidgetSetupActivity
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarTitle
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.settings.components.prefs.GroupHeader
import oolong.client.ui.settings.components.prefs.PrefsScreen
import oolong.client.ui.settings.components.prefs.widgets.TextPref
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.TextStyles

/**
 * The home screen widgets: those placed that have something to set, to set it again, and every
 * kind, to place from here where the launcher lets apps ask, for those who can't find its list.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun WidgetsPage(navigator: Navigator = LocalNavigator.current) {
    val context = LocalContext.current
    // Read again each time the page shows, as a widget may have been set up or placed meanwhile
    var shows by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        shows++
        onPauseOrDispose {}
    }
    val placed by produceState(emptyList<Placed>(), shows) { value = placedWidgets(context) }
    val canPin = remember { WidgetPins.supported(context) }
    GlassScaffold(
        largeTitle = true,
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(R.string.title_widgets)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { padding ->
        PrefsScreen(dividerThickness = 0.dp, contentPadding = padding) {
            if (placed.isNotEmpty()) {
                prefsGroup(header = { GroupHeader(stringResource(R.string.widgets_placed)) }) {
                    placed.forEach { widget ->
                        prefsItem {
                            TextPref(
                                title = stringResource(widget.kind.label),
                                summary = widget.summary(),
                                leadingIcon = { Preview(widget.kind.preview) },
                                onClick = { context.startActivity(setupIntent(context).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget.appWidgetId)) },
                            )
                        }
                    }
                }
            }
            prefsGroup(header = { GroupHeader(stringResource(R.string.widgets_add)) }) {
                if (canPin) {
                    Kind.entries.forEach { kind ->
                        prefsItem {
                            TextPref(
                                title = stringResource(kind.label),
                                summary = stringResource(kind.description),
                                leadingIcon = { Preview(kind.preview) },
                                onClick = { add(context, kind) },
                                trailingContent = { AddPill() },
                            )
                        }
                    }
                } else {
                    prefsItem { TextPref(title = stringResource(R.string.widgets_pin_unsupported)) }
                }
            }
        }
    }
}

/** Every kind of widget, with its name, what it shows and its picture in the launcher's list. */
private enum class Kind(val receiver: Class<*>, @StringRes val label: Int, @StringRes val description: Int, @DrawableRes val preview: Int) {
    Topics(HotTopicsWidgetReceiver::class.java, R.string.hot_section_topics, R.string.appwidget_topics_description, R.drawable.appwidget_preview_hot_topics),
    Ranking(ThreadRankingWidgetReceiver::class.java, R.string.hot_section_threads, R.string.appwidget_ranking_description, R.drawable.appwidget_preview_thread_ranking),
    Forum(ForumHotWidgetReceiver::class.java, R.string.appwidget_forum_name, R.string.appwidget_forum_description, R.drawable.appwidget_preview_forum_hot),
    Forums(ForumsWidgetReceiver::class.java, R.string.appwidget_forums_name, R.string.appwidget_forums_description, R.drawable.appwidget_preview_forums),
    Mix(TopicsAndForumsWidgetReceiver::class.java, R.string.appwidget_mix_name, R.string.appwidget_mix_description, R.drawable.appwidget_preview_topics_and_forums),
}

/** A placed widget with something to set: a forum widget's forum and what of it, or the forums widget's source. */
private class Placed(val kind: Kind, val appWidgetId: Int, val state: Preferences) {
    @Composable
    fun summary(): String = when (kind) {
        Kind.Forum -> state[ForumHotWidget.ForumNameKey]?.let { name ->
            val shows = when (state[ForumHotWidget.ShowsKey].toShows()) {
                ForumShows.Latest -> R.string.tab_forum_latest
                ForumShows.Hot -> R.string.tab_forum_hot
                ForumShows.Good -> R.string.tab_forum_good
            }
            stringResource(R.string.appwidget_forum_title, name) + " · " + stringResource(shows)
        } ?: stringResource(R.string.appwidget_pick_forum)

        else -> stringResource(
            if (state[ForumsWidget.SourceKey] == ForumsSource.Visited.name) R.string.appwidget_source_visited else R.string.appwidget_source_followed,
        )
    }
}

private suspend fun placedWidgets(context: Context): List<Placed> {
    val manager = GlanceAppWidgetManager(context)
    fun ids(kind: Kind) = kind to when (kind) {
        Kind.Forum -> ForumHotWidget::class.java
        else -> ForumsWidget::class.java
    }
    return listOf(ids(Kind.Forum), ids(Kind.Forums)).flatMap { (kind, widget) ->
        runCatching { manager.getGlanceIds(widget) }.getOrDefault(emptyList()).map { id ->
            Placed(kind, manager.getAppWidgetId(id), getAppWidgetState(context, PreferencesGlanceStateDefinition, id))
        }
    }
}

private fun setupIntent(context: Context) = Intent(context, WidgetSetupActivity::class.java)

/** A forum widget is set up before it is placed; the rest are placed as they are. */
private fun add(context: Context, kind: Kind) {
    if (kind == Kind.Forum) {
        context.startActivity(setupIntent(context).putExtra(WidgetSetupActivity.EXTRA_PIN, true))
    } else {
        WidgetPins.pin(context, kind.receiver)
    }
}

@Composable
private fun Preview(@DrawableRes preview: Int) {
    Box(modifier = Modifier.size(width = 56.dp, height = 40.dp), contentAlignment = Alignment.Center) {
        Image(painter = painterResource(preview), contentDescription = null, contentScale = ContentScale.Fit)
    }
}

@Composable
private fun AddPill() {
    val colors = ExtendedTheme.colors
    Text(
        text = stringResource(R.string.appwidget_setup_add),
        style = TextStyles.caption,
        fontWeight = FontWeight.Bold,
        color = colors.accent,
        modifier = Modifier
            .background(colors.accent.copy(alpha = 0.12f), Corners.full)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
