package oolong.client.data.post

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import javax.inject.Inject
import javax.inject.Singleton

/** A reply this app sent: post [postId], a new floor of [threadId] or a reply inside floor [inPost]. */
data class SentReply(
    val threadId: Long,
    val inPost: Long?,
    val postId: Long,
)

/** The replies this app sends, for the pages that show them to hear of. */
@Singleton
class SentReplies @Inject constructor() {
    private val replies = MutableSharedFlow<SentReply>(extraBufferCapacity = 16)

    /** The replies sent to thread [threadId] from now on. */
    fun to(threadId: Long): Flow<SentReply> = replies.filter { it.threadId == threadId }

    fun add(reply: SentReply) {
        replies.tryEmit(reply)
    }
}
