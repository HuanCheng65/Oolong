package oolong.client.ui.thread

import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import oolong.client.arch.LoadStatus
import oolong.client.arch.MessageController
import oolong.client.arch.Page
import oolong.client.arch.query.QueryClient
import oolong.client.arch.query.pager
import oolong.client.data.block.BlockRuleSource
import oolong.client.data.block.BlockRules
import oolong.client.data.block.blockRulesMatching
import oolong.client.data.history.ThreadHistory
import oolong.client.data.post.SentReplies
import oolong.client.data.post.SentReply
import oolong.client.data.thread.ThreadRepository
import oolong.client.data.thread.ThreadSort
import oolong.client.data.thread.Threads
import oolong.client.data.thread.toThreadSummary
import oolong.client.model.Post
import oolong.client.model.ThreadDetail
import oolong.client.model.ThreadSummary
import oolong.client.navigation.ThreadKey
import oolong.client.testing.MainDispatcherRule
import oolong.client.testing.queryClient
import oolong.client.testing.threadActions
import oolong.client.ui.reader.ReaderPositions
import oolong.core.network.agree.AgreeRemoteDataSource
import oolong.core.network.post.PostRemoteDataSource
import oolong.core.network.protos.Anti
import oolong.core.network.protos.PbContent
import oolong.core.network.protos.PollInfo
import oolong.core.network.protos.PollOption
import oolong.core.network.protos.SimpleForum
import oolong.core.network.protos.ThreadInfo
import oolong.core.network.protos.pbFloor.PbFloorResponseData
import oolong.core.network.protos.pbPage.PbPageResponseData
import oolong.core.network.thread.ThreadRemoteDataSource
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import oolong.core.network.protos.Agree as ProtoAgree
import oolong.core.network.protos.Page as ProtoPage
import oolong.core.network.protos.Post as ProtoPost
import oolong.core.network.protos.SubPost as ProtoSubPosts
import oolong.core.network.protos.SubPostList as ProtoSubPost
import oolong.core.network.protos.User as ProtoUser

class ThreadViewModelTest {
    private val readerPositions = ReaderPositions()
    private val sentReplies = SentReplies()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val remote = mockk<ThreadRemoteDataSource>(relaxed = true)
    private val agreeRemote = mockk<AgreeRemoteDataSource>(relaxed = true)
    private val postRemote = mockk<PostRemoteDataSource>(relaxed = true)
    private val blockRules = MutableStateFlow(BlockRules { null })
    private val saved = mutableListOf<Pair<ThreadDetail, Post?>>()
    private val shown = mutableListOf<Int>()

    private val lz = ProtoUser(id = 10, name = "lz", nameShow = "楼主")
    private val me = ProtoUser(id = 99, name = "me", is_login = 1)

    private fun post(id: Long, author: ProtoUser = ProtoUser(id = 20, name = "replier"), text: String = "回复 $id") =
        ProtoPost(
            id = id,
            floor = id.toInt(),
            author = author,
            author_id = author.id,
            content = listOf(PbContent(type = 0, text = text)),
            agree = ProtoAgree(diffAgreeNum = 5, hasAgree = 0),
        )

    private fun data(posts: List<ProtoPost>, page: ProtoPage = ProtoPage(current_page = 1, total_page = 3, new_total_page = 3, has_more = 1)) =
        PbPageResponseData(
            thread = ThreadInfo(id = 9_000, title = "标题", author = lz, firstPostId = 1),
            forum = SimpleForum(id = 52, name = "吧名"),
            page = page,
            anti = Anti(tbs = "page-tbs"),
            user = me,
            post_list = listOf(post(1, lz)) + posts,
        )

    private fun respond(vararg pages: PbPageResponseData) {
        coEvery { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returnsMany pages.toList()
    }

    private fun TestScope.createViewModel(
        postId: Long = 0,
        sortType: Int = 0,
        threadInfo: ThreadInfo? = null,
        appScope: CoroutineScope = backgroundScope,
        client: QueryClient = queryClient(),
        repository: ThreadRepository = threadRepository(client),
    ): ThreadViewModel {
        threadInfo?.let { listing(client, it.toThreadSummary { _, _ -> }) }
        return ThreadViewModel(
            args = ThreadKey(threadId = 9_000L, postId = postId, sortType = sortType),
            repository = repository,
            client = client,
            threads = threadActions(client, agreeRemote),
            blockRuleSource = object : BlockRuleSource {
                override val rules = blockRules
            },
            history = ThreadHistory { thread, _, post, _ -> saved += thread to post },
            messages = object : MessageController {
                override fun show(message: Int, vararg args: Any) {
                    shown += message
                }
            },
            applicationScope = appScope,
            readerPositions = readerPositions,
            sentReplies = sentReplies,
        )
    }

    @Test
    fun `loads the thread from the navigation arguments`() = runTest {
        respond(data(listOf(post(2), post(3))))

        val viewModel = createViewModel()
        runCurrent()

        val state = viewModel.state.value
        assertEquals("标题", state.thread?.title)
        assertEquals(1L, state.firstPost?.id)
        assertEquals(listOf(2L, 3L), state.posts.items.map { it.post.id })
        assertEquals(99L, state.viewer.user?.id)
        assertEquals(1 to 3, state.currentPage to state.totalPage)
        coVerify { remote.page(9_000, 0, 0, 0, false, false, false, 0, 0, "", 0) }
    }

    @Test
    fun `an error can be retried from the same place`() = runTest {
        coEvery { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws
            IOException("network") andThen data(listOf(post(31)))

        val viewModel = createViewModel(postId = 31)
        runCurrent()
        assertIs<LoadStatus.Error>(viewModel.state.value.posts.refresh)

        viewModel.retry()
        runCurrent()
        assertEquals(listOf(31L), viewModel.state.value.posts.items.map { it.post.id })
        coVerify(exactly = 2) { remote.page(9_000, 0, 31, any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `blocked posts follow the block rules as they change`() = runTest {
        respond(data(listOf(post(2, text = "广告"), post(3))))
        val viewModel = createViewModel()
        runCurrent()
        assertFalse(viewModel.state.value.posts.items[0].blocked)

        blockRules.value = blockRulesMatching { "广告" in it.content }
        runCurrent()
        assertEquals(listOf(true, false), viewModel.state.value.posts.items.map { it.blocked })
    }

    private fun reply(id: Long, text: String = "楼中楼 $id") =
        ProtoSubPost(id = id, author = ProtoUser(id = 30, name = "r$id"), content = listOf(PbContent(type = 0, text = text)))

    private fun floorPage(replies: List<ProtoSubPost>, page: Int, pages: Int) = PbFloorResponseData(
        post = post(2),
        subpost_list = replies,
        page = ProtoPage(current_page = page, total_page = pages, total_count = 15),
        thread = ThreadInfo(id = 9_000, author = lz),
        forum = SimpleForum(id = 52, name = "吧名"),
    )

    /** Post 2 shows replies 1 and 2 of the 15 inside it; their pages hold 1–10 and 11–15. */
    private fun TestScope.floorWithReplies(): ThreadViewModel {
        val withReplies = post(2).copy(sub_post_list = ProtoSubPosts(sub_post_list = listOf(reply(1), reply(2))), sub_post_number = 15)
        respond(data(listOf(withReplies)))
        coEvery { remote.floor(9_000, 2, 52, 1, 0, false, any()) } returns floorPage((1L..10L).map { reply(it) }, page = 1, pages = 2)
        coEvery { remote.floor(9_000, 2, 52, 2, 0, false, any()) } returns floorPage((11L..15L).map { reply(it, if (it == 13L) "广告" else "楼中楼 $it") }, page = 2, pages = 2)
        return createViewModel()
    }

    @Test
    fun `a floor opens ten more of its replies at a time, loading their pages as needed`() = runTest {
        val viewModel = floorWithReplies()
        runCurrent()
        val post = viewModel.state.value.posts.items.single().post

        viewModel.openSubPosts(post)
        runCurrent()
        var replies = viewModel.state.value.floorReplies.getValue(2)
        assertEquals((1L..12L).toList(), replies.subPosts.map { it.id })
        assertFalse(replies.complete)

        viewModel.openSubPosts(post)
        runCurrent()
        replies = viewModel.state.value.floorReplies.getValue(2)
        assertEquals((1L..15L).toList(), replies.subPosts.map { it.id })
        assertTrue(replies.complete)
        coVerify(exactly = 1) { remote.floor(9_000, 2, 52, 1, 0, false, any()) }
        coVerify(exactly = 1) { remote.floor(9_000, 2, 52, 2, 0, false, any()) }

        viewModel.closeSubPosts(2)
        runCurrent()
        assertNull(viewModel.state.value.floorReplies[2])
    }

    @Test
    fun `replies opened in a floor follow the block rules`() = runTest {
        blockRules.value = blockRulesMatching { "广告" in it.content }
        val viewModel = floorWithReplies()
        runCurrent()
        val post = viewModel.state.value.posts.items.single().post
        viewModel.openSubPosts(post)
        runCurrent()
        viewModel.openSubPosts(post)
        runCurrent()
        assertEquals(setOf(13L), viewModel.state.value.floorReplies.getValue(2).blocks.keys)
    }

    @Test
    fun `a floor that fails to open more can try again`() = runTest {
        val viewModel = floorWithReplies()
        coEvery { remote.floor(9_000, 2, 52, 1, 0, false, any()) } throws IOException("network") andThen floorPage((1L..10L).map { reply(it) }, page = 1, pages = 2)
        runCurrent()
        val post = viewModel.state.value.posts.items.single().post

        viewModel.openSubPosts(post)
        runCurrent()
        assertTrue(viewModel.state.value.floorReplies.getValue(2).failed)
        assertEquals(listOf(1L, 2L), viewModel.state.value.floorReplies.getValue(2).subPosts.map { it.id })

        viewModel.openSubPosts(post)
        runCurrent()
        assertEquals((1L..12L).toList(), viewModel.state.value.floorReplies.getValue(2).subPosts.map { it.id })
    }

    private fun threadRepository(client: QueryClient) =
        ThreadRepository(remote, postRemote, { _, _ -> }, mainDispatcherRule.dispatcher, client)

    @Test
    fun `a thread peeked at opens without loading it again, and refreshing loads it again`() = runTest {
        respond(data(listOf(post(2))), data(listOf(post(2), post(3))))
        val client = queryClient()
        val repository = threadRepository(client)
        repository.prefetch(ThreadKey(threadId = 9_000L).query())
        runCurrent()

        val viewModel = createViewModel(client = client, repository = repository)
        runCurrent()
        assertEquals(listOf(2L), viewModel.state.value.posts.items.map { it.post.id })
        coVerify(exactly = 1) { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }

        viewModel.refresh()
        runCurrent()
        assertEquals(listOf(2L, 3L), viewModel.state.value.posts.items.map { it.post.id })
    }

    @Test
    fun `a thread opened again soon shows what was loaded of it`() = runTest {
        respond(data(listOf(post(2))), data(listOf(post(3)), ProtoPage(current_page = 2, total_page = 3, new_total_page = 3)))
        val client = queryClient()
        val repository = threadRepository(client)
        val first = createViewModel(client = client, repository = repository)
        runCurrent()
        first.loadMore()
        runCurrent()

        val again = createViewModel(client = client, repository = repository)
        runCurrent()
        assertEquals(listOf(2L, 3L), again.state.value.posts.items.map { it.post.id })
        assertEquals("标题", again.state.value.thread?.title)
        coVerify(exactly = 2) { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `likes given to a post or a reply inside it on another page show here`() = runTest {
        val reply = ProtoSubPost(id = 201, author_id = 20, content = listOf(PbContent(type = 0, text = "楼中楼")))
        respond(data(listOf(post(2).copy(sub_post_list = ProtoSubPosts(sub_post_list = listOf(reply))))))
        val client = queryClient()
        val viewModel = createViewModel(client = client)
        runCurrent()

        // As the page of the replies inside post 2 would
        threadActions(client).agreePost(9_000, 52, 2, true)
        threadActions(client).agreeSubPost(9_000, 52, 201, true)
        runCurrent()

        val post = viewModel.state.value.posts.items.single().post
        assertEquals(6L to true, post.agree.count to post.agree.agreed)
        assertEquals(true, post.subPosts.single().agree.agreed)
    }

    /** A list showing [threads], as the one a thread is opened from. */
    private fun TestScope.listing(client: QueryClient, vararg threads: ThreadSummary) = client.pager(
        scope = backgroundScope,
        initialKey = 1,
        itemKey = ThreadSummary::id,
        normalize = { Threads(it) },
    ) { Page(threads.toList()) }.also { runCurrent() }

    @Test
    fun `liking the thread shows in the list it was opened from`() = runTest {
        respond(data(listOf(post(2))))
        val client = queryClient()
        val list = listing(client, ThreadInfo(threadId = 9_000, title = "标题").toThreadSummary { _, _ -> })
        val viewModel = createViewModel(client = client)
        runCurrent()

        viewModel.agreeThread()
        runCurrent()

        assertEquals(true, viewModel.state.value.thread?.agree?.agreed)
        assertEquals(true, list.state.value.items.single().agree.agreed)
    }

    @Test
    fun `a pinned thread stays pinned in its forum's list once its page has loaded`() = runTest {
        respond(data(listOf(post(2))))
        val client = queryClient()
        val list = listing(client, ThreadInfo(threadId = 9_000, title = "标题", isTop = 1).toThreadSummary { _, _ -> })
        createViewModel(client = client)
        runCurrent()

        assertEquals(true, list.state.value.items.single().isTop)
    }

    @Test
    fun `agreeing shows at once and is rolled back when it fails`() = runTest {
        respond(data(listOf(post(2))))
        val response = CompletableDeferred<Unit>()
        coEvery { agreeRemote.opAgree(any(), any(), any(), any()) } coAnswers {
            response.await()
            throw IOException("network")
        }
        val viewModel = createViewModel()
        runCurrent()

        viewModel.agreePost(2)
        runCurrent()
        val optimistic = viewModel.state.value.posts.items.single().post.agree
        assertEquals(6L to true, optimistic.count to optimistic.agreed)

        response.complete(Unit)
        runCurrent()
        val rolledBack = viewModel.state.value.posts.items.single().post.agree
        assertEquals(5L to false, rolledBack.count to rolledBack.agreed)
        assertEquals(1, shown.size)
    }

    @Test
    fun `a vote shows at once and is rolled back when it fails`() = runTest {
        val poll = PollInfo(options = listOf(PollOption(id = 1, num = 3, text = "甲"), PollOption(id = 2, num = 5, text = "乙")), total_num = 8)
        respond(data(listOf(post(2))).let { it.copy(thread = it.thread!!.copy(poll_info = poll)) })
        val response = CompletableDeferred<Unit>()
        coEvery { postRemote.addPollPost(any(), any(), any()) } coAnswers {
            response.await()
            throw IOException("network")
        }
        val viewModel = createViewModel()
        runCurrent()

        viewModel.vote(setOf(2))
        runCurrent()
        val voted = viewModel.state.value.thread?.poll!!
        assertEquals(listOf(3L, 6L), voted.options.map { it.votes })
        assertEquals(9L to setOf(2), voted.voters to voted.chosen)

        response.complete(Unit)
        runCurrent()
        val rolledBack = viewModel.state.value.thread?.poll!!
        assertEquals(listOf(3L, 5L), rolledBack.options.map { it.votes })
        assertEquals(false, rolledBack.hasVoted)
        assertEquals(1, shown.size)
    }

    @Test
    fun `seeing only the author's posts reloads from the start`() = runTest {
        respond(data(listOf(post(2))), data(listOf(post(5, lz))))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.setSeeLz(true)
        runCurrent()

        assertTrue(viewModel.state.value.query.seeLz)
        assertEquals(listOf(5L), viewModel.state.value.posts.items.map { it.post.id })
        coVerify { remote.page(9_000, 0, 0, 0, true, false, false, 0, 0, "", 0) }
    }

    @Test
    fun `newest first loads newer posts when pulled down`() = runTest {
        respond(
            data(listOf(post(70), post(69)), ProtoPage(current_page = 2, total_page = 3, new_total_page = 3, has_more = 1)),
            data(listOf(post(71))),
        )
        val viewModel = createViewModel(sortType = ThreadSort.NewestFirst.value)
        runCurrent()
        coVerify { remote.page(9_000, 0, 0, 1, false, false, false, 0, 0, "", 0) }

        viewModel.loadPrevious()
        runCurrent()

        coVerify { remote.page(9_000, 3, 70, 1, false, true, false, 0, 0, "", 0) }
        assertEquals(listOf(71L, 70L, 69L), viewModel.state.value.posts.items.map { it.post.id })
    }

    @Test
    fun `newer posts are asked for after the highest page seen`() = runTest {
        respond(
            data(listOf(post(1977), post(1976)), ProtoPage(current_page = 1, total_page = 123, has_more = 1)),
            data(listOf(post(1963), post(1962)), ProtoPage(current_page = 121, total_page = 123, has_more = 1)),
            data(emptyList()),
        )
        val viewModel = createViewModel(sortType = ThreadSort.NewestFirst.value)
        runCurrent()
        viewModel.loadMore()
        runCurrent()

        viewModel.loadPrevious()
        runCurrent()

        coVerify { remote.page(9_000, 122, 1977, 1, false, true, false, 0, 0, "", 0) }
    }

    @Test
    fun `opening at a post scrolls to it once loaded`() = runTest {
        respond(data(listOf(post(31))))
        val viewModel = createViewModel(postId = 31)
        val effects = mutableListOf<ThreadEffect>()
        backgroundScope.launchCollect(viewModel, effects)

        runCurrent()
        assertEquals(listOf<ThreadEffect>(ThreadEffect.ScrollToPost(31)), effects)
    }

    @Test
    fun `a reply at the end of the thread is appended and scrolled to`() = runTest {
        respond(
            data(listOf(post(2), post(3)), ProtoPage(current_page = 1, total_page = 1, has_more = 0)),
            data(listOf(post(3), post(4))),
        )
        val viewModel = createViewModel()
        val effects = mutableListOf<ThreadEffect>()
        backgroundScope.launchCollect(viewModel, effects)
        runCurrent()

        sentReplies.add(SentReply(threadId = 9_000L, inPost = null, postId = 4))
        runCurrent()

        assertEquals(listOf(2L, 3L, 4L), viewModel.state.value.posts.items.map { it.post.id })
        assertEquals(listOf<ThreadEffect>(ThreadEffect.ScrollToPost(4)), effects)
        coVerify { remote.page(9_000, 0, 4, 0, false, false, false, 0, 0, "", 0) }
    }

    @Test
    fun `a reply far from the loaded pages jumps to it`() = runTest {
        respond(data(listOf(post(2), post(3))), data(listOf(post(400))))
        val viewModel = createViewModel()
        val effects = mutableListOf<ThreadEffect>()
        backgroundScope.launchCollect(viewModel, effects)
        runCurrent()

        sentReplies.add(SentReply(threadId = 9_000L, inPost = null, postId = 400))
        runCurrent()

        assertEquals(listOf(400L), viewModel.state.value.posts.items.map { it.post.id })
        assertEquals(listOf<ThreadEffect>(ThreadEffect.ScrollToPost(400)), effects)
    }

    @Test
    fun `a reply inside a post updates that post where it is`() = runTest {
        val replied = post(3).copy(sub_post_number = 1)
        respond(data(listOf(post(2), post(3))), data(listOf(replied)))
        val viewModel = createViewModel()
        runCurrent()

        sentReplies.add(SentReply(threadId = 9_000L, inPost = 3, postId = 301))
        runCurrent()

        assertEquals(listOf(2L, 3L), viewModel.state.value.posts.items.map { it.post.id })
        assertEquals(1, viewModel.state.value.posts.items[1].post.subPostCount)
    }

    @Test
    fun `a reply sent while a jump loads only scrolls to the reply`() = runTest {
        val jumpPage = CompletableDeferred<Unit>()
        var calls = 0
        coEvery { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            when (++calls) {
                1 -> data(listOf(post(2), post(3)))
                2 -> {
                    jumpPage.await()
                    data(listOf(post(31)))
                }
                else -> data(listOf(post(400)))
            }
        }
        val viewModel = createViewModel()
        val effects = mutableListOf<ThreadEffect>()
        backgroundScope.launchCollect(viewModel, effects)
        runCurrent()

        viewModel.jumpToPage(3)
        runCurrent()
        sentReplies.add(SentReply(threadId = 9_000L, inPost = null, postId = 400))
        runCurrent()

        assertEquals(listOf<ThreadEffect>(ThreadEffect.ScrollToPost(400)), effects)
    }

    @Test
    fun `back from the reader the page scrolls to where the reading stopped`() = runTest {
        respond(data(listOf(post(2), post(3), post(4))))
        val viewModel = createViewModel()
        val effects = mutableListOf<ThreadEffect>()
        backgroundScope.launchCollect(viewModel, effects)
        runCurrent()

        readerPositions.leave(threadId = 9_000L, postId = 4)
        runCurrent()

        assertEquals(listOf<ThreadEffect>(ThreadEffect.ScrollToPost(4)), effects)
    }

    @Test
    fun `jumping to a page is only possible oldest first`() = runTest {
        respond(data(listOf(post(70))), data(listOf(post(31))))
        val viewModel = createViewModel(sortType = ThreadSort.NewestFirst.value)
        runCurrent()

        viewModel.jumpToPage(3)
        runCurrent()
        coVerify(exactly = 1) { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the thread passed along is shown until the thread loads`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            gate.await()
            data(listOf(post(2)))
        }
        val passed = ThreadInfo(
            threadId = 9_000,
            title = "列表里的标题",
            author = lz,
            firstPostContent = listOf(PbContent(type = 0, text = "摘要")),
        )
        val viewModel = createViewModel(threadInfo = passed)
        runCurrent()

        assertEquals("列表里的标题", viewModel.state.value.preview?.title)
        assertEquals(10L, viewModel.state.value.preview?.author?.id)

        gate.complete(Unit)
        runCurrent()
        assertNull(viewModel.state.value.preview)
        assertEquals("标题", viewModel.state.value.thread?.title)
    }

    @Test
    fun `history remembers the last post read once scrolling settles`() = runTest {
        respond(data(listOf(post(2), post(3))))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onReadUpTo(2)
        viewModel.onReadUpTo(3)
        advanceTimeBy(500)
        runCurrent()
        assertTrue(saved.isEmpty())

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3L, saved.single().second?.id)
    }

    @Test
    fun `deleting a post removes it from the list`() = runTest {
        respond(data(listOf(post(2, me), post(3))))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.deletePost(viewModel.state.value.posts.items[0].post)
        runCurrent()

        assertEquals(listOf(3L), viewModel.state.value.posts.items.map { it.post.id })
        coVerify { remote.deletePost(52, "吧名", 9_000, 2, ownPost = true, isSubPost = false, tbs = "page-tbs") }
    }

    @Test
    fun `the store mark is moved on leaving even though the page closes`() = runTest {
        val thread = data(listOf(post(2), post(3))).let {
            it.copy(thread = it.thread!!.copy(collectStatus = 1, collectMarkPid = "2"))
        }
        respond(thread)
        val viewModel = createViewModel()
        val effects = mutableListOf<ThreadEffect>()
        backgroundScope.launchCollect(viewModel, effects)
        runCurrent()

        viewModel.onReadUpTo(3)
        val update = viewModel.storeMarkUpdate()
        assertEquals(3L, update?.id)
        viewModel.leaveUpdatingStoreMark(update!!)
        viewModel.clear()
        runCurrent()

        assertEquals(listOf<ThreadEffect>(ThreadEffect.NavigateUp), effects)
        coVerify { remote.addStore(9_000, 3) }
    }

    @Test
    fun `no store mark update is offered when the mark is already there`() = runTest {
        val thread = data(listOf(post(2))).let {
            it.copy(thread = it.thread!!.copy(collectStatus = 1, collectMarkPid = "2"))
        }
        respond(thread)
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onReadUpTo(2)
        assertNull(viewModel.storeMarkUpdate())
    }
}

private fun CoroutineScope.launchCollect(viewModel: ThreadViewModel, into: MutableList<ThreadEffect>) {
    launch(start = CoroutineStart.UNDISPATCHED) {
        viewModel.effect.collect { into += it }
    }
}

/** Clears the view model the way leaving the page does. */
private fun ThreadViewModel.clear() {
    ViewModelStore().apply { put("thread", this@clear) }.clear()
}
