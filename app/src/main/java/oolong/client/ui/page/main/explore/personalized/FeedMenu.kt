package oolong.client.ui.page.main.explore.personalized

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import oolong.client.R
import oolong.client.model.DislikeOption
import oolong.client.model.ThreadSummary
import oolong.client.model.threadId
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.settings.block.LocalBlockShortcuts
import oolong.client.ui.page.settings.block.blockMenuItems
import oolong.client.ui.page.settings.block.blockMenuLabels
import oolong.client.ui.widgets.compose.ContextMenuItem
import oolong.client.ui.widgets.compose.IconButton
import oolong.client.ui.widgets.compose.LocalContextMenus
import oolong.client.utils.TiebaUtil
import oolong.client.utils.launchUrl

/**
 * The ⋯ of a recommended thread, which opens into what the server offers for not wanting it, each
 * done in one tap, then sharing it or copying its link.
 */
@Composable
fun FeedMenu(
    thread: ThreadSummary,
    options: ImmutableList<DislikeOption>,
    onDislike: (clickTime: Long, reasons: ImmutableList<DislikeOption>) -> Unit,
) {
    val context = LocalContext.current
    val shareLabels = shareMenuLabels()
    val blockLabels = blockMenuLabels()
    val navigator = LocalNavigator.current
    val menus = LocalContextMenus.current
    val shortcuts = LocalBlockShortcuts.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var menuId by remember { mutableStateOf<Long?>(null) }
    // The menu holds the icon while it shows, and gives it back where it took it
    val open = menus?.isShowing(menuId) == true

    IconButton(
        onClick = {
            menus ?: return@IconButton
            val dislikes = dislikeMenuItems(context, navigator, options, onDislike)
            val items = dislikes + shareMenuItems(shareLabels, context, thread.threadId, thread.title).mapIndexed { i, item ->
                if (i == 0) item.copy(startsGroup = dislikes.isNotEmpty()) else item
            } + shortcuts?.let { blockMenuItems(blockLabels, it, thread) }.orEmpty()
            menuId = menus.show(items, anchor = bounds, icon = Symbols.MoreVert)
        },
        modifier = Modifier
            .size(24.dp)
            .onGloballyPositioned { bounds = it.boundsInWindow() },
    ) {
        Icon(
            imageVector = Symbols.MoreVert,
            contentDescription = stringResource(id = R.string.btn_more),
            tint = ExtendedTheme.colors.textSecondary,
            modifier = Modifier.graphicsLayer { alpha = if (open) 0f else 1f },
        )
    }
}

/**
 * The server's options for not wanting a thread, each done in one tap: those that lead somewhere,
 * such as reporting it, open it instead. Keeping out its forum is left to the block menu, whose
 * rule holds signed out too and shows with the other rules.
 */
fun dislikeMenuItems(
    context: Context,
    navigator: Navigator,
    options: List<DislikeOption>,
    onDislike: (clickTime: Long, reasons: ImmutableList<DislikeOption>) -> Unit,
): List<ContextMenuItem> {
    val clickTime = System.currentTimeMillis()
    return options.filter { it.id != BlockForumOption }.map { option ->
        val schema = option.schema
        ContextMenuItem(
            icon = when {
                schema != null -> Symbols.Flag
                else -> Symbols.VisibilityOff
            },
            label = option.text.trim(),
        ) {
            if (schema != null) {
                launchUrl(context, navigator, schema)
            } else {
                onDislike(clickTime, persistentListOf(option))
            }
        }
    }
}

/** The labels of [shareMenuItems], read where the menu is offered. */
@Immutable
class ShareMenuLabels(val share: String, val copyLink: String)

@Composable
fun shareMenuLabels() = ShareMenuLabels(
    share = stringResource(id = R.string.title_share),
    copyLink = stringResource(id = R.string.title_copy_link),
)

/** Sharing thread [threadId], titled [title], or copying its link. */
fun shareMenuItems(labels: ShareMenuLabels, context: Context, threadId: Long, title: String): List<ContextMenuItem> {
    val url = "https://tieba.baidu.com/p/$threadId"
    return listOf(
        ContextMenuItem(Symbols.Share, labels.share) {
            TiebaUtil.shareText(context, url, title.takeIf(String::isNotBlank))
        },
        ContextMenuItem(Symbols.Link, labels.copyLink) { TiebaUtil.copyText(context, url) },
    )
}

/** The option that keeps the thread's forum out of the recommendations. */
private const val BlockForumOption = 7
