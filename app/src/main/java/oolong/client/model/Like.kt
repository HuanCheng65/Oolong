package oolong.client.model

import androidx.compose.runtime.Immutable

/** One user's like on one of the user's posts or replies inside a post. */
@Immutable
data class Like(
    /** The like's own id; the next page starts after the last one. */
    val id: Long,
    val threadId: Long,
    /** The liked post, or the liked reply inside a post. */
    val postId: Long,
    /** Whether a reply inside a post was liked rather than a post in the thread. */
    val inPost: Boolean,
    /** The thread's title, or the server's note in its place when the thread is gone. */
    val threadTitle: String,
    val forumName: String,
    /** The thread's first picture, small, if it has one. */
    val threadPicture: String?,
    /** The liked text, with pictures as `[图片]`. */
    val content: String,
    /** Whether the liked post or its thread was deleted or hidden. */
    val deleted: Boolean,
    val liker: User,
    /** Unix time in seconds. */
    val time: Long,
)
