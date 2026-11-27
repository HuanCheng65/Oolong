package oolong.client.data.notification

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import oolong.client.arch.IoDispatcher
import oolong.client.arch.Page
import oolong.client.data.thread.EmoticonRegistry
import oolong.client.data.thread.abstractText
import oolong.client.data.thread.toUser
import oolong.client.model.Like
import oolong.client.model.Notification
import oolong.client.model.NotificationType
import oolong.client.model.User
import oolong.core.network.message.FeedItem
import oolong.core.network.message.MessageRemoteDataSource
import oolong.core.network.protos.agreeMe.AgreeItem
import javax.inject.Inject

/** The replies to the user, the posts that mention them and the likes on theirs, a page at a time. */
class NotificationRepository @Inject constructor(
    private val remote: MessageRemoteDataSource,
    private val emoticons: EmoticonRegistry,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * A page of the likes on the user's posts, newest first; [after] is the last like's id from the
     * page before, 0 for the first page.
     */
    suspend fun likes(after: Long): Page<Long, Like> = withContext(io) {
        val response = remote.agreeMe(after)
        val likes = response.agree_list.mapNotNull { it.toLike(emoticons) }
        Page(likes, next = likes.lastOrNull()?.id?.takeIf { response.has_more == 1 })
    }

    /** Page [page] (from 1) of [type]; the next page's number comes with it while there are more. */
    suspend fun page(type: NotificationType, page: Int): Page<Int, Notification> = withContext(io) {
        val response = when (type) {
            NotificationType.ReplyMe -> remote.replyMe(page)
            NotificationType.AtMe -> remote.atMe(page)
        }
        val items = when (type) {
            NotificationType.ReplyMe -> response.replyList
            NotificationType.AtMe -> response.atList
        }
        Page(items.mapNotNull { it.toNotification() }, next = (page + 1).takeIf { response.page?.hasMore == "1" })
    }
}

private fun FeedItem.toNotification(): Notification? {
    val threadId = threadId?.toLongOrNull() ?: return null
    val postId = postId?.toLongOrNull() ?: return null
    return Notification(
        threadId = threadId,
        postId = postId,
        inPost = isFloor == "1",
        threadTitle = title.orEmpty(),
        content = content.orEmpty(),
        quoteContent = quoteContent,
        quotePostId = quotePid?.toLongOrNull()?.takeIf { it != 0L },
        author = replyer?.let {
            User(
                id = it.id?.toLongOrNull() ?: 0,
                name = it.name.orEmpty(),
                nameShow = it.nameShow.orEmpty().takeIf { name -> name != it.name }.orEmpty(),
                portrait = it.portrait.orEmpty(),
            )
        },
        time = time?.toLongOrNull() ?: 0,
    )
}

private fun AgreeItem.toLike(emoticons: EmoticonRegistry): Like? {
    val thread = thread_info ?: return null
    val post = post_info ?: return null
    val liker = agreeer ?: return null
    if (thread.id == 0L || post.id == 0L) return null
    return Like(
        id = id,
        threadId = thread.id,
        postId = post.id,
        inPost = type == 2,
        threadTitle = thread.title,
        forumName = thread.forumName,
        threadPicture = thread.media.firstOrNull()?.let { it.smallPic.ifEmpty { it.bigPic } }?.takeIf { it.isNotEmpty() },
        content = post.content.abstractText(emoticons).trim(),
        deleted = is_del == 1,
        liker = liker.toUser(),
        time = time.toLong(),
    )
}
