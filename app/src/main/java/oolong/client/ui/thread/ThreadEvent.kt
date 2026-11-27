package oolong.client.ui.thread

import oolong.client.model.Post
import oolong.client.model.PostContent
import oolong.client.model.SubPost

/** Everything the user can do on the thread page. */
sealed interface ThreadEvent {
    data object Back : ThreadEvent
    data object Refresh : ThreadEvent
    data object Retry : ThreadEvent
    data object LoadMore : ThreadEvent
    data object LoadLatest : ThreadEvent
    data object LoadPrevious : ThreadEvent
    data class SetSeeLz(val seeLz: Boolean) : ThreadEvent
    data object ToggleSort : ThreadEvent
    data object OpenReader : ThreadEvent
    data class JumpToPage(val page: Int) : ThreadEvent
    data object OpenMenu : ThreadEvent
    data object AgreeThread : ThreadEvent
    data class AgreePost(val postId: Long) : ThreadEvent
    data class Vote(val optionIds: Set<Int>) : ThreadEvent
    data object OpenForum : ThreadEvent
    data class OpenUser(val userId: Long) : ThreadEvent

    /** Reply to the thread when [post] is null, to a post, or to a reply inside it. */
    data class Reply(val post: Post?, val subPost: SubPost? = null) : ThreadEvent

    /** Open the replies inside [post], at [subPostId] or from the start when 0. */
    data class OpenSubPosts(val post: Post, val subPostId: Long) : ThreadEvent

    /** Shows more of the replies inside [post] in place. */
    data class ShowMoreSubPosts(val post: Post) : ThreadEvent

    /** Back to the few replies inside post [postId] the thread's page brings. */
    data class ShowFewerSubPosts(val postId: Long) : ThreadEvent
    data class OpenOriginThread(val threadId: Long, val forumId: Long) : ThreadEvent

    /** Copy [content] or a part of it. */
    data class Copy(val content: List<PostContent>) : ThreadEvent
    data class Report(val postId: Long) : ThreadEvent
    data object ReportThread : ThreadEvent
    data object Share : ThreadEvent
    data object CopyLink : ThreadEvent

    /** Open this thread in the official client, to reply there. */
    data object OpenInOfficialClient : ThreadEvent
    data class StoreAt(val post: Post) : ThreadEvent
    data object StoreAtLastRead : ThreadEvent
    data object RemoveStore : ThreadEvent
    data class DeletePost(val post: Post) : ThreadEvent
    data object DeleteThread : ThreadEvent
    data class ReadUpTo(val postId: Long) : ThreadEvent
}

/** User settings the thread page follows. */
data class ThreadPreferences(
    val hideReply: Boolean = false,
    val hideBlocked: Boolean = false,
)

/** Whether [this] changes something on the server, which a thread read from its saved copy can't. */
val ThreadEvent.needsServer: Boolean
    get() = when (this) {
        ThreadEvent.AgreeThread, is ThreadEvent.AgreePost, is ThreadEvent.Vote, is ThreadEvent.Reply,
        is ThreadEvent.StoreAt, ThreadEvent.StoreAtLastRead, ThreadEvent.RemoveStore,
        is ThreadEvent.DeletePost, ThreadEvent.DeleteThread, is ThreadEvent.Report, ThreadEvent.ReportThread,
        -> true

        else -> false
    }
