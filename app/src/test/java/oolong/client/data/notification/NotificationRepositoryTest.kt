package oolong.client.data.notification

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import oolong.client.model.Like
import oolong.client.model.Notification
import oolong.client.model.NotificationType
import oolong.client.model.User
import oolong.core.network.message.FeedItem
import oolong.core.network.message.FeedResponse
import oolong.core.network.message.MessageRemoteDataSource
import oolong.core.network.protos.Media
import oolong.core.network.protos.PbContent
import oolong.core.network.protos.Post
import oolong.core.network.protos.ThreadInfo
import oolong.core.network.protos.agreeMe.AgreeItem
import oolong.core.network.protos.agreeMe.AgreeMeResponseData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import oolong.core.network.protos.User as ProtoUser

class NotificationRepositoryTest {
    private val remote = mockk<MessageRemoteDataSource>()
    private val repository = NotificationRepository(remote, emoticons = { _, _ -> }, io = Dispatchers.Unconfined)

    @Test
    fun `maps a page of replies and says whether more follow`() = runTest {
        coEvery { remote.replyMe(2) } returns FeedResponse(
            replyList = listOf(
                FeedItem(
                    isFloor = "1", title = "标题", content = "回复", quoteContent = "原文",
                    replyer = FeedItem.User("7", "name", "昵称", "p"), threadId = "9000", postId = "201",
                    quotePid = "200", time = "1700000000",
                ),
                FeedItem(threadId = null, postId = "1"),
            ),
            page = FeedResponse.Page(hasMore = "1"),
        )

        val page = repository.page(NotificationType.ReplyMe, 2)

        assertEquals(
            listOf(
                Notification(
                    threadId = 9000, postId = 201, inPost = true, threadTitle = "标题", content = "回复",
                    quoteContent = "原文", quotePostId = 200, author = User(7, "name", "昵称", "p"), time = 1_700_000_000,
                ),
            ),
            page.items,
        )
        assertEquals(3, page.next)
    }

    @Test
    fun `mentions come from their own list and end when there are no more`() = runTest {
        coEvery { remote.atMe(1) } returns FeedResponse(
            atList = listOf(FeedItem(threadId = "9", postId = "10", replyer = FeedItem.User("7", "same", "same", ""))),
            page = FeedResponse.Page(hasMore = "0"),
        )

        val page = repository.page(NotificationType.AtMe, 1)

        assertEquals(listOf(10L), page.items.map { it.postId })
        assertEquals("", page.items.single().author?.nameShow)
        assertNull(page.next)
    }

    @Test
    fun `maps a page of likes and continues after the last one`() = runTest {
        coEvery { remote.agreeMe(0) } returns AgreeMeResponseData(
            has_more = 1,
            agree_list = listOf(
                AgreeItem(
                    id = 41, type = 2, is_del = 1, time = 1_700_000_000,
                    thread_info = ThreadInfo(
                        id = 9000, title = "标题", forumName = "吧",
                        media = listOf(Media(type = 3, smallPic = "small", bigPic = "big")),
                    ),
                    post_info = Post(id = 201, content = listOf(PbContent(type = 0, text = " 赞的回复[图片] "))),
                    agreeer = ProtoUser(id = 7, name = "name", nameShow = "昵称", portrait = "p"),
                ),
                AgreeItem(id = 40, thread_info = ThreadInfo(id = 9000), post_info = Post(id = 202)),
            ),
        )

        val page = repository.likes(after = 0)

        assertEquals(
            listOf(
                Like(
                    id = 41, threadId = 9000, postId = 201, inPost = true, threadTitle = "标题", forumName = "吧",
                    threadPicture = "small", content = "赞的回复[图片]", deleted = true,
                    liker = User(7, "name", "昵称", "p"), time = 1_700_000_000,
                ),
            ),
            page.items,
        )
        assertEquals(41L, page.next)
    }

    @Test
    fun `likes end when the server says there are no more`() = runTest {
        coEvery { remote.agreeMe(41) } returns AgreeMeResponseData(has_more = 0)

        assertNull(repository.likes(after = 41).next)
    }
}
