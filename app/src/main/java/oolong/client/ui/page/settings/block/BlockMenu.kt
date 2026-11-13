package oolong.client.ui.page.settings.block

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import oolong.client.R
import oolong.client.model.ThreadSummary
import oolong.client.model.User
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.widgets.compose.ContextMenuItem

/** The words of the block menu items, read where composition can. */
@Immutable
class BlockMenuLabels(
    val block: String,
    /** With the user's name for `%1$s`. */
    val user: String,
    /** With the forum's name without 吧 for `%1$s`. */
    val forum: String,
    val keywords: String,
)

@Composable
fun blockMenuLabels() = BlockMenuLabels(
    block = stringResource(id = R.string.menu_block),
    user = stringResource(id = R.string.menu_block_user_item),
    forum = stringResource(id = R.string.menu_block_forum_item),
    keywords = stringResource(id = R.string.menu_block_keywords_item),
)

/**
 * Blocking [thread]'s author, its forum, or keywords picked from its title and what lists show of
 * it, as one context menu item in a group of its own that opens the choices in place.
 */
fun blockMenuItems(labels: BlockMenuLabels, shortcuts: BlockShortcuts, thread: ThreadSummary): List<ContextMenuItem> =
    blockMenuItems(
        labels, shortcuts, thread.reply?.author ?: thread.author, thread.forum.name,
        listOfNotNull(thread.title, thread.abstract, thread.reply?.text).joinToString("\n"),
    )

/**
 * Blocking [author], the [forum] if given, or keywords picked from [text], as one context menu
 * item in a group of its own that opens the choices in place.
 */
fun blockMenuItems(labels: BlockMenuLabels, shortcuts: BlockShortcuts, author: User?, forum: String?, text: String): List<ContextMenuItem> {
    val choices = buildList {
        if (author != null && (author.id != 0L || author.name.isNotBlank())) {
            add(ContextMenuItem(Symbols.PersonOff, labels.user.format(author.displayName)) {
                shortcuts.blockUser(author.id, author.name, author.displayName)
            })
        }
        if (!forum.isNullOrBlank()) {
            add(ContextMenuItem(Symbols.Forum, labels.forum.format(forum.removeSuffix("吧"))) {
                shortcuts.blockForum(forum)
            })
        }
        add(ContextMenuItem(Symbols.TextFields, labels.keywords) {
            shortcuts.askKeywords(text)
        })
    }
    return listOf(ContextMenuItem(Symbols.Block, labels.block, startsGroup = true, children = choices))
}
