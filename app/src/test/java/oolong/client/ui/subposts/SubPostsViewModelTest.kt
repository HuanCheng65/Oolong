package oolong.client.ui.subposts

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import oolong.client.arch.MessageController
import oolong.client.arch.query.QueryClient
import oolong.client.data.block.BlockRuleSource
import oolong.client.data.block.BlockRules
import oolong.client.data.block.blockRulesMatching
import oolong.client.data.post.SentReplies
import oolong.client.data.post.SentReply
import oolong.client.data.thread.ThreadRepository
import oolong.client.navigation.SubPostsKey
import oolong.client.testing.MainDispatcherRule
import oolong.client.testing.queryClient
import oolong.client.testing.threadActions
import oolong.core.network.TiebaServerException
import oolong.core.network.agree.AgreeRemoteDataSource
import oolong.core.network.identity.Session
import oolong.core.network.protos.Anti
import oolong.core.network.protos.PbContent
import oolong.core.network.protos.SimpleForum
import oolong.core.network.protos.SubPostList
import oolong.core.network.protos.ThreadInfo
import oolong.core.network.protos.pbFloor.PbFloorResponseData
import oolong.core.network.thread.ThreadRemoteDataSource
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import oolong.core.network.protos.Agree as ProtoAgree
import oolong.core.network.protos.Page as ProtoPage
import oolong.core.network.protos.Post as ProtoPost
import oolong.core.network.protos.User as ProtoUser

class SubPostsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val remote = mockk<ThreadRemoteDataSource>(relaxed = true)
    private val agreeRemote = mockk<AgreeRemoteDataSource>(relaxed = true)
    private val sentReplies = SentReplies()
    private val blockRules = MutableStateFlow(BlockRules { null })
    private val shown = mutableListOf<Int>()

    private val lz = ProtoUser(id = 10, name = "lz")
    private val me = ProtoUser(id = 99, name = "me")

    private fun reply(id: Long, author: ProtoUser = ProtoUser(id = 20, name = "replier"), text: String = "回复 $id") =
        SubPostList(id = id, author = author, content = listOf(PbContent(type = 0, text = text)), agree = ProtoAgree(diffAgreeNum = 1))

    private fun floor(
        replies: List<SubPostList>,
        page: ProtoPage = ProtoPage(current_page = 1, total_page = 2, total_count = 12),
        post: ProtoPost = ProtoPost(id = 77, floor = 5, author = me),
    ) = PbFloorResponseData(
        post = post,
        subpost_list = replies,
        page = page,
        thread = ThreadInfo(id = 9_000, author = lz),
        forum = SimpleForum(id = 52, name = "吧名"),
        anti = Anti(tbs = "page-tbs"),
    )

    private fun respond(vararg pages: PbFloorResponseData) {
        coEvery { remote.floor(any(), any(), any(), any(), any(), any(), any()) } returnsMany pages.toList()
    }

    private fun TestScope.createViewModel(
        postId: Long = 77,
        subPostId: Long = 0,
        loadFromSubPost: Boolean = false,
        client: QueryClient = queryClient(),
    ) =
        SubPostsViewModel(
            key = SubPostsKey(threadId = 9_000L, postId = postId, subPostId = subPostId, loadFromSubPost = loadFromSubPost),
            repository = ThreadRepository(remote, mockk(), { _, _ -> }, mainDispatcherRule.dispatcher, client),
            client = client,
            threads = threadActions(client, agreeRemote),
            blockRuleSource = object : BlockRuleSource {
                override val rules = blockRules
            },
            messages = object : MessageController {
                override fun show(message: Int, vararg args: Any) {
                    shown += message
                }
            },
            sessionProvider = { Session(uid = "99", name = "me", bduss = "b", stoken = null, tbs = null, zid = null) },
            sentReplies = sentReplies,
        )

    private fun CoroutineScope.collectEffects(viewModel: SubPostsViewModel) = mutableListOf<SubPostsEffect>().also { into ->
        launch(start = CoroutineStart.UNDISPATCHED) { viewModel.effect.collect { into += it } }
    }

    @Test
    fun `loads the post and its first page of replies`() = runTest {
        respond(floor(listOf(reply(201), reply(202))))

        val viewModel = createViewModel()
        runCurrent()

        val state = viewModel.state.value
        assertEquals(77L, state.post?.id)
        assertEquals(10L, state.threadAuthorId)
        assertEquals(listOf(201L, 202L), state.subPosts.items.map { it.subPost.id })
        assertEquals(12, state.totalCount)
        assertEquals(99L, state.viewerId)
        coVerify { remote.floor(9_000, 77, 0, 1, 0) }
    }

    @Test
    fun `opened from a reply it loads around it and then uses the ids the server returned`() = runTest {
        respond(
            floor(listOf(reply(301), reply(302)), ProtoPage(current_page = 3, total_page = 4)),
            floor(listOf(reply(303))),
        )
        val viewModel = createViewModel(postId = 302, subPostId = 302, loadFromSubPost = true)
        val effects = backgroundScope.collectEffects(viewModel)
        runCurrent()

        assertEquals(listOf<SubPostsEffect>(SubPostsEffect.ScrollToSubPost(302)), effects)
        viewModel.loadMore()
        runCurrent()

        coVerify { remote.floor(9_000, 302, 0, 1, 302, true) }
        coVerify { remote.floor(9_000, 77, 52, 4, 0) }
        assertTrue(viewModel.state.value.subPosts.hasPrevious)
    }

    @Test
    fun `opened from a reply that is a floor itself it loads that floor`() = runTest {
        coEvery { remote.floor(9_000, 0, 0, 1, 77, true) } throws TiebaServerException(4, "主题不存在", null)
        coEvery { remote.floor(9_000, 77, 0, 1, 0, false) } returns floor(listOf(reply(201)))
        val viewModel = createViewModel(postId = 0, subPostId = 77, loadFromSubPost = true)
        runCurrent()

        assertEquals(77L, viewModel.state.value.post?.id)
        assertEquals(listOf(201L), viewModel.state.value.subPosts.items.map { it.subPost.id })
    }

    @Test
    fun `liking a reply shows at once and is rolled back when it fails`() = runTest {
        respond(floor(listOf(reply(201))))
        val response = CompletableDeferred<Unit>()
        coEvery { agreeRemote.opAgree(any(), any(), any(), any()) } coAnswers {
            response.await()
            throw IOException("network")
        }
        val viewModel = createViewModel()
        runCurrent()

        viewModel.agreeSubPost(201)
        runCurrent()
        assertEquals(true, viewModel.state.value.subPosts.items.single().subPost.agree.agreed)

        response.complete(Unit)
        runCurrent()
        assertEquals(false, viewModel.state.value.subPosts.items.single().subPost.agree.agreed)
        assertEquals(1, shown.size)
    }

    @Test
    fun `deleting a reply removes it, deleting the post closes the page`() = runTest {
        respond(floor(listOf(reply(201, me), reply(202))))
        val viewModel = createViewModel()
        val effects = backgroundScope.collectEffects(viewModel)
        runCurrent()

        viewModel.delete(viewModel.state.value.subPosts.items[0].subPost)
        runCurrent()
        assertEquals(listOf(202L), viewModel.state.value.subPosts.items.map { it.subPost.id })
        coVerify { remote.deletePost(52, "吧名", 9_000, 201, ownPost = true, isSubPost = true, tbs = "page-tbs") }

        viewModel.delete(null)
        runCurrent()
        coVerify { remote.deletePost(52, "吧名", 9_000, 77, ownPost = true, isSubPost = false, tbs = "page-tbs") }
        assertEquals(listOf<SubPostsEffect>(SubPostsEffect.NavigateUp), effects)
    }

    @Test
    fun `a reply sent inside the post is loaded and scrolled to, one in another post is not`() = runTest {
        respond(floor(listOf(reply(201))), floor(listOf(reply(201), reply(250))))
        val viewModel = createViewModel()
        val effects = backgroundScope.collectEffects(viewModel)
        runCurrent()

        sentReplies.add(SentReply(threadId = 9_000L, inPost = 7, postId = 1))
        sentReplies.add(SentReply(threadId = 9_000L, inPost = 77, postId = 250))
        runCurrent()

        coVerify { remote.floor(9_000, 77, 52, 1, 250, true) }
        assertEquals(listOf(201L, 250L), viewModel.state.value.subPosts.items.map { it.subPost.id })
        assertEquals(listOf<SubPostsEffect>(SubPostsEffect.ScrollToSubPost(250)), effects)
    }

    @Test
    fun `blocked replies follow the block rules`() = runTest {
        respond(floor(listOf(reply(201, text = "广告"), reply(202))))
        val viewModel = createViewModel()
        runCurrent()

        blockRules.value = blockRulesMatching { "广告" in it.content }
        runCurrent()
        assertEquals(listOf(true, false), viewModel.state.value.subPosts.items.map { it.blocked })
    }
}
