package oolong.client.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A thread as lists show it, the same in every list: feeds, a forum's threads, a user's threads.
 * It carries the whole first floor, which lists get with the thread, so a peek or the thread page
 * can show it before anything else loads.
 */
@Immutable
data class ThreadSummary(
    val id: Long,
    /** The thread's forum; its name is empty where the list leaves it out. */
    val forum: ForumSummary,
    /** Empty when the thread has no title. */
    val title: String,
    val author: User,
    /** When the thread was posted, in seconds since the epoch. */
    val createTime: Long,
    /** When the thread last had a reply, in seconds since the epoch; 0 where the list leaves it out. */
    val lastReplyTime: Long = 0,
    /** What the list shows of the thread, cut short by the server. */
    val abstract: String,
    val firstPostId: Long,
    /** The whole first floor, or what the list showed of it when [firstPostShort]. */
    val firstPost: ImmutableList<PostContent> = persistentListOf(),
    /** Whether the list gave only the abstract and the pictures of the first floor, not all of it. */
    val firstPostShort: Boolean = false,
    val images: ImmutableList<ThreadImage> = persistentListOf(),
    val video: ThreadVideo? = null,
    /** The thread this one shares, if it shares one. */
    val shared: SharedThread? = null,
    val replyCount: Int = 0,
    val shareCount: Long = 0,
    val agree: Agree = Agree(0, agreed = false),
    /** Marked good by the forum's moderators. */
    val isGood: Boolean = false,
    /** Pinned to the top of its forum; null where that isn't told, as on the thread page. */
    val isTop: Boolean? = null,
    /** The forum's section the thread is filed under, shown before the title; empty for none. */
    val tabName: String = "",
    /** How hot the thread is on the hot threads ranking; 0 elsewhere. */
    val hotNum: Long = 0,
    /**
     * The reply a feed recommends in place of a thread. [id] is then the feed's own id for the
     * entry, [firstPost] the reply in full, and the rest the thread the reply was made in, whose
     * author the feed leaves out.
     */
    val reply: FeedReply? = null,
)

/** A reply a feed recommends, shown on the thread it was made in. */
@Immutable
data class FeedReply(
    val threadId: Long,
    val postId: Long,
    val author: User,
    /** When the reply was made, in seconds since the epoch. */
    val time: Long,
    val text: String,
    val images: ImmutableList<ThreadImage> = persistentListOf(),
)

/** The thread's own id, which a recommended reply's entry doesn't go by. */
val ThreadSummary.threadId: Long
    get() = reply?.threadId ?: id

/** The thread liked or not, its like count following. */
fun ThreadSummary.withAgreed(agreed: Boolean) = copy(agree = agree.withAgreed(agreed))

/**
 * The first floor as a list knows it, to show until the thread page loads it: all of its content,
 * but no likes or replies inside it. Its id is 0, as it isn't the loaded post.
 */
fun ThreadSummary.knownFirstFloor() = Post(
    id = 0,
    floor = 1,
    title = title,
    time = createTime,
    author = author,
    content = firstPost,
    agree = Agree(0, agreed = false),
    subPosts = persistentListOf(),
    subPostCount = 0,
)

/** A picture of a thread as lists show it, at every size the server offers. */
@Immutable
data class ThreadImage(
    val small: String,
    val large: String,
    val original: String,
    /** The animated version, empty when the picture isn't animated. */
    val animated: String,
    val width: Int,
    val height: Int,
    val isLong: Boolean,
    /** The post the picture is in. */
    val postId: Long,
    /** The original's size in bytes, which the viewer shows before loading it. */
    val originalSize: Int,
    /** Whether the viewer offers loading the original. */
    val showOriginalButton: Boolean,
)

@Immutable
data class ThreadVideo(
    val url: String,
    val cover: String,
    val coverWidth: Int,
    val coverHeight: Int,
    /** In seconds, or 0 when not known. */
    val duration: Int = 0,
)

/** The thread a sharing thread shares, as much of it as the card shows. */
@Immutable
data class SharedThread(
    val id: Long,
    val forumId: Long,
    val forumName: String,
    val content: ImmutableList<PostContent>,
    val images: ImmutableList<ThreadImage> = persistentListOf(),
    val video: ThreadVideo? = null,
)
