package oolong.client.ui.subposts

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import oolong.client.R
import oolong.client.data.thread.SubPostSort
import oolong.client.model.Post
import oolong.client.model.PostContent
import oolong.client.model.SubPost
import oolong.client.model.wordText
import oolong.client.ui.components.BlockableContent
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.settings.block.BlockMenuLabels
import oolong.client.ui.settings.block.BlockShortcuts
import oolong.client.ui.settings.block.LocalBlockShortcuts
import oolong.client.ui.settings.block.blockMenuItems
import oolong.client.ui.settings.block.blockMenuLabels
import oolong.client.ui.thread.SubPostBranch
import oolong.client.ui.thread.SubPostLine
import oolong.client.ui.thread.SubPostStyle
import oolong.client.ui.thread.floorIndent
import oolong.client.ui.thread.floorRuleColor
import oolong.client.ui.thread.rememberSubPostStyle
import oolong.client.ui.thread.replyPhotoViewData

/** Everything the user can do on the sub-post page. */
sealed interface SubPostsEvent {
    data object Back : SubPostsEvent
    data object Retry : SubPostsEvent
    data object LoadMore : SubPostsEvent
    data object LoadPrevious : SubPostsEvent
    data object OpenThread : SubPostsEvent
    data object AgreePost : SubPostsEvent
    data class AgreeSubPost(val subPostId: Long) : SubPostsEvent
    data class OpenUser(val userId: Long) : SubPostsEvent
    data class SetSort(val sort: SubPostSort) : SubPostsEvent

    /** Reply in the post, to its author when [subPost] is null or to the author of [subPost]. */
    data class Reply(val post: Post, val subPost: SubPost? = null) : SubPostsEvent

    /** Reply in the post without replying to anyone in particular. */
    data object ReplyToPost : SubPostsEvent
    data class Copy(val content: List<PostContent>) : SubPostsEvent
    data class Report(val postId: Long) : SubPostsEvent

    /** Delete [subPost], or the post itself when null. */
    data class Delete(val subPost: SubPost?) : SubPostsEvent
}

/**
 * A reply inside the post, as a floor shows its replies but in full: on a branch off the rule that
 * runs down from the post, the last one ending it, or in the classic style with no rule at all.
 */
@Composable
internal fun SubPostReply(
    post: Post,
    item: SubPostItem,
    threadAuthorId: Long,
    canDelete: Boolean,
    hideReply: Boolean,
    last: Boolean,
    onEvent: (SubPostsEvent) -> Unit,
) {
    val subPost = item.subPost
    val context = LocalContext.current
    val shortcuts = LocalBlockShortcuts.current
    val blockLabels = blockMenuLabels()
    BlockableContent(
        block = item.block,
        modifier = Modifier.fillMaxWidth().floorIndent(),
    ) {
        // BlockableContent only lays out its tip with the modifier, so the reply takes its own
        val rule = floorRuleColor().takeIf { rememberSubPostStyle() == SubPostStyle.Timeline }
        SubPostBranch(rule = rule, modifier = Modifier.fillMaxWidth().floorIndent(), rail = true, last = last) {
            SubPostLine(
                subPost = subPost,
                isLz = subPost.author.id == threadAuthorId,
                onClick = { onEvent(SubPostsEvent.Reply(post, subPost)) }.takeUnless { hideReply },
                menuItems = { replyMenuItems(context, blockLabels, post, subPost, hideReply, canDelete, shortcuts, onEvent) },
                onAgree = { onEvent(SubPostsEvent.AgreeSubPost(subPost.id)) },
                photoViewData = { image -> replyPhotoViewData(context, post, image) },
            )
        }
    }
}

/** What a long press on a reply offers here, with blocking its author after when [shortcuts] are there. */
private fun replyMenuItems(
    context: Context,
    blockLabels: BlockMenuLabels,
    post: Post,
    subPost: SubPost,
    hideReply: Boolean,
    canDelete: Boolean,
    shortcuts: BlockShortcuts?,
    onEvent: (SubPostsEvent) -> Unit,
): List<ContextMenuItem> = buildList {
    if (!hideReply) {
        add(ContextMenuItem(Symbols.Reply, context.getString(R.string.btn_reply)) { onEvent(SubPostsEvent.Reply(post, subPost)) })
    }
    add(ContextMenuItem(Symbols.ContentCopy, context.getString(R.string.menu_copy)) { onEvent(SubPostsEvent.Copy(subPost.content)) })
    add(ContextMenuItem(Symbols.Flag, context.getString(R.string.title_report), startsGroup = true) { onEvent(SubPostsEvent.Report(subPost.id)) })
    if (canDelete) add(ContextMenuItem(Symbols.Delete, context.getString(R.string.title_delete)) { onEvent(SubPostsEvent.Delete(subPost)) })
    if (shortcuts != null) addAll(blockMenuItems(blockLabels, shortcuts, subPost.author, forum = null, text = subPost.content.wordText))
}
