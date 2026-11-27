package oolong.client.ui.offline

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import oolong.client.R
import oolong.client.data.offline.OfflineThreadInfo
import oolong.client.database.OfflineThreadEntity
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.ThreadKey
import oolong.client.ui.components.relativeTime
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarTitle
import oolong.client.ui.designsystem.MyLazyColumn
import oolong.client.ui.designsystem.longPressMenu
import oolong.client.ui.designsystem.motionItem
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.core.designsystem.theme.Spacing
import oolong.core.designsystem.theme.TextStyles

/** The threads saved to read offline, the last saved first; a long press updates or deletes one. */
@Composable
fun OfflineThreadsPage(navigator: Navigator = LocalNavigator.current) {
    val context = LocalContext.current
    val flow = remember { context.offlineThreads().all }
    val threads by flow.collectAsState(initial = null)
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_offline_threads)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { padding ->
        val list = threads ?: return@GlassScaffold
        if (list.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(text = stringResource(id = R.string.offline_empty), style = TextStyles.label, color = ExtendedTheme.colors.textSecondary)
            }
            return@GlassScaffold
        }
        MyLazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            items(list, key = { it.threadId }) { thread ->
                OfflineThreadRow(
                    thread = thread,
                    onOpen = { navigator.navigate(ThreadKey(threadId = thread.threadId, forumId = thread.forumId, offline = true)) },
                    modifier = Modifier.motionItem(this),
                )
            }
        }
    }
}

@Composable
private fun OfflineThreadRow(thread: OfflineThreadEntity, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = ExtendedTheme.colors
    // Nothing to read until the first page is in
    val readable = thread.pages > 0
    Column(
        modifier = modifier
            .fillMaxWidth()
            .longPressMenu(
                items = {
                    val info = OfflineThreadInfo(thread.threadId, thread.title, thread.forumId, thread.forumName, thread.authorName)
                    offlineMenuItems(context, info, thread, authorFirst = thread.onlyAuthor)
                },
                onClick = onOpen.takeIf { readable },
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            text = thread.title,
            style = TextStyles.nameCompact,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = details(context, thread),
            style = TextStyles.caption,
            color = if (thread.state == OfflineThreadEntity.Saved && thread.missingPictures == 0) colors.textSecondary else colors.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The forum, which floors, how many and how big; or how saving it stands, when that needs saying. */
@Composable
private fun details(context: Context, thread: OfflineThreadEntity): String {
    val parts = buildList {
        add(context.getString(R.string.title_forum, thread.forumName))
        when (thread.state) {
            OfflineThreadEntity.Saving -> add(
                if (thread.totalPages > 0) context.getString(R.string.offline_state_saving_pages, thread.pages, thread.totalPages)
                else context.getString(R.string.offline_saving)
            )

            OfflineThreadEntity.Failed -> add(context.getString(R.string.offline_state_failed))
            OfflineThreadEntity.Gone -> add(context.getString(R.string.offline_state_gone))
            else -> {
                if (thread.onlyAuthor) add(context.getString(R.string.title_see_lz))
                add(context.resources.getQuantityString(R.plurals.offline_floors, thread.posts, thread.posts))
                add(Formatter.formatShortFileSize(context, thread.bytes))
                if (thread.missingPictures > 0) add(context.getString(R.string.offline_state_missing, thread.missingPictures))
                else add(relativeTime(thread.updatedAt / 1000))
            }
        }
    }
    return parts.joinToString(" · ")
}
