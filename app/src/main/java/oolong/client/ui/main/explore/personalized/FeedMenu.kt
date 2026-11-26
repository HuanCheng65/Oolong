package oolong.client.ui.main.explore.personalized

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import oolong.client.R
import oolong.client.model.DislikeOption
import oolong.client.model.ThreadSummary
import oolong.client.model.threadId
import oolong.client.navigation.Navigator
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.IconButton
import oolong.client.ui.designsystem.LocalContextMenus
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.main.explore.rememberThreadMenu
import oolong.client.util.TiebaUtil
import oolong.client.util.launchUrl

/**
 * The ⋯ of a recommended thread, which opens the thread menu, the same as under its peek: the
 * server's options for not wanting it among them, each done in one tap.
 */
@Composable
fun FeedMenu(
    thread: ThreadSummary,
    options: ImmutableList<DislikeOption>,
    onDislike: (clickTime: Long, reasons: ImmutableList<DislikeOption>) -> Unit,
) {
    val menus = LocalContextMenus.current
    // The same as under the thread's peek
    val threadMenu = rememberThreadMenu()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var menuId by remember { mutableStateOf<Long?>(null) }
    // The menu holds the icon while it shows, and gives it back where it took it
    val open = menus?.isShowing(menuId) == true

    IconButton(
        onClick = {
            if (menus == null || threadMenu == null) return@IconButton
            menuId = menus.show(threadMenu.items(thread, options, onDislike), anchor = bounds, icon = Symbols.MoreVert)
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
 * such as reporting it, open it instead. Keeping out its forum is left to the block menu, which
 * adds a rule of the app's own and sends this option along ([blockingForum]).
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
            removesAnchor = schema == null,
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

/**
 * Sending the server's option for keeping the thread's forum out of the recommendations, for the
 * block menu to do with its own rule; null when the server doesn't offer it.
 */
fun List<DislikeOption>.blockingForum(
    onDislike: (clickTime: Long, reasons: ImmutableList<DislikeOption>) -> Unit,
): (() -> Unit)? {
    val option = firstOrNull { it.id == BlockForumOption } ?: return null
    val clickTime = System.currentTimeMillis()
    return { onDislike(clickTime, persistentListOf(option)) }
}

/** The option that keeps the thread's forum out of the recommendations. */
private const val BlockForumOption = 7
