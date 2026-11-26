package oolong.client.data.post

import android.content.Context
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import oolong.core.network.post.PostRemoteDataSource
import oolong.core.network.protos.ReplyExp
import oolong.core.network.protos.addPost.AddPostRequestData
import oolong.core.network.protos.addPost.AddPostResponseData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PostRepositoryTest {
    private val remote = mockk<PostRemoteDataSource>()
    private val sentReplies = SentReplies()
    private val repository = PostRepository(remote, mockk(), { "昵称" }, sentReplies, mockk<Context>(), Dispatchers.Unconfined)
    private val sent = slot<AddPostRequestData>()

    init {
        coEvery { remote.addPost(capture(sent), any()) } returns AddPostResponseData(tid = "9", pid = "100", exp = ReplyExp(inc = "2"))
    }

    @Test
    fun `replies to a thread`() = runTest {
        val reply = repository.reply(ReplyTarget(52, "测试", 9), "内容", tbs = "t")

        assertEquals(PostedReply(9, 100, "2"), reply)
        with(sent.captured) {
            assertEquals("内容", content)
            assertEquals("52", fid)
            assertEquals("测试", kw)
            assertEquals("9", tid)
            assertEquals("昵称", name_show)
            assertEquals("13", post_from)
            assertEquals("0", barrage_time)
            assertNull(quote_id)
            assertNull(reply_uid)
        }
    }

    @Test
    fun `tells the thread's pages of the reply`() = runTest {
        val heard = mutableListOf<SentReply>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sentReplies.to(9).toList(heard) }

        repository.reply(ReplyTarget(52, "测试", 9, postId = 3), "内容", tbs = "t")

        assertEquals(listOf(SentReply(threadId = 9, inPost = 3, postId = 100)), heard)
    }

    @Test
    fun `replies inside a floor to someone`() = runTest {
        repository.reply(ReplyTarget(52, "测试", 9, postId = 50, subPostId = 60, replyUserId = 7), "内容", tbs = null)

        with(sent.captured) {
            assertEquals("50", quote_id)
            assertEquals("50", repostid)
            assertEquals("60", sub_post_id)
            assertEquals("7", reply_uid)
            assertNull(post_from)
            assertNull(barrage_time)
            assertNull(v_fid)
        }
    }

    @Test
    fun `replies to a floor`() = runTest {
        repository.reply(ReplyTarget(52, "测试", 9, postId = 50), "内容", tbs = null)

        assertEquals("0", sent.captured.post_from)
    }
}
