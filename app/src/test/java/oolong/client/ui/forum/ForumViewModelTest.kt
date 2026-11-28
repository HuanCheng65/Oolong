package oolong.client.ui.forum

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import oolong.client.R
import oolong.client.arch.MessageController
import oolong.client.arch.query.QueryClient
import oolong.client.data.block.BlockRuleSource
import oolong.client.data.block.BlockRules
import oolong.client.data.block.blockRulesMatching
import oolong.client.data.forum.ForumSortStore
import oolong.client.data.forum.forumRepository
import oolong.client.model.ForumDetail
import oolong.client.model.ForumSort
import oolong.client.model.ForumTab
import oolong.client.navigation.ForumKey
import oolong.client.testing.MainDispatcherRule
import oolong.client.testing.queryClient
import oolong.client.testing.threadActions
import oolong.core.network.agree.AgreeRemoteDataSource
import oolong.core.network.forum.FirstGet
import oolong.core.network.forum.ForumRemoteDataSource
import oolong.core.network.forum.FrsTab
import oolong.core.network.forum.LikeForumResponse
import oolong.core.network.forum.LikeRemoteDataSource
import oolong.core.network.identity.Session
import oolong.core.network.protos.Agree
import oolong.core.network.protos.Anti
import oolong.core.network.protos.FrsTabInfo
import oolong.core.network.protos.Page
import oolong.core.network.protos.ThreadInfo
import oolong.core.network.protos.frsPage.ForumInfo
import oolong.core.network.protos.frsPage.FrsPageResponseData
import oolong.core.network.protos.frsPage.NavTabInfo
import oolong.core.network.protos.generalTabList.GeneralTabListResponseData
import oolong.core.network.sign.SignRemoteDataSource
import oolong.core.network.sign.SignResponse
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForumViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val remote = mockk<ForumRemoteDataSource>()
    private val likeRemote = mockk<LikeRemoteDataSource>()
    private val signRemote = mockk<SignRemoteDataSource>()
    private val agreeRemote = mockk<AgreeRemoteDataSource>(relaxed = true)
    private val blockRules = MutableStateFlow(BlockRules { null })
    private val saved = mutableListOf<ForumDetail>()
    private val shown = mutableListOf<Int>()
    private val sorts = mutableMapOf<String, Int>()

    private val custom = FrsTabInfo(tabId = 99, tabType = 15, tabName = "吧友互助", isGeneralTab = 1)

    private fun thread(id: Long, title: String = "帖子 $id", agreed: Boolean = false) =
        ThreadInfo(id = id, title = title, forumId = FORUM_ID, firstPostId = id * 10, agree = Agree(hasAgree = if (agreed) 1 else 0))

    private fun frsPage(threads: List<ThreadInfo> = listOf(thread(1)), liked: Boolean = true, signedIn: Boolean = false) =
        FrsPageResponseData(
            forum = ForumInfo(
                id = FORUM_ID,
                name = "吧名",
                is_like = if (liked) 1 else 0,
                sign_in_info = oolong.core.network.protos.frsPage.SignInfo(
                    user_info = oolong.core.network.protos.frsPage.SignUser(is_sign_in = if (signedIn) 1 else 0),
                ),
            ),
            page = Page(has_more = 0),
            thread_list = threads,
            anti = Anti(tbs = "page-tbs"),
            nav_tab_info = NavTabInfo(tab = listOf(custom)),
        )

    /** Answers frs/page with [pages] in turn; the quick half of a first page gets nothing of its own. */
    private fun respond(vararg pages: FrsPageResponseData) {
        coEvery { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returnsMany pages.toList()
        coEvery { remote.page(any(), any(), any(), any(), any(), any(), any(), FirstGet.Less, any(), any()) } returns FrsPageResponseData()
    }

    private fun TestScope.createViewModel(
        storedSort: Int = ForumSort.REPLY_TIME,
        client: QueryClient = queryClient(),
    ) = ForumViewModel(
        key = ForumKey("吧名"),
        repository = forumRepository(remote, likeRemote, signRemote),
        client = client,
        threads = threadActions(client, agreeRemote),
        blockRuleSource = object : BlockRuleSource {
            override val rules = blockRules
        },
        history = { saved += it },
        sortStore = object : ForumSortStore {
            override suspend fun sortType(forumName: String) = sorts[forumName] ?: storedSort

            override suspend fun setSortType(forumName: String, sortType: Int) {
                sorts[forumName] = sortType
            }
        },
        messages = object : MessageController {
            override fun show(message: Int, vararg args: Any) {
                shown += message
            }
        },
        sessionProvider = { Session(uid = "99", name = "me", bduss = "b", stoken = null, tbs = null, zid = null) },
    )

    @Test
    fun `loads the forum with its latest threads in the stored order`() = runTest {
        respond(frsPage())

        val viewModel = createViewModel(storedSort = ForumSort.POST_TIME)
        runCurrent()

        val state = viewModel.state.value
        assertEquals("吧名", state.forum?.name)
        assertEquals(ForumSort.POST_TIME, state.sortType)
        assertEquals(listOf(1L), state.list(ForumTab.Latest).items.map { it.thread.id })
        assertEquals(
            listOf(ForumTab.Good, ForumTab.Latest, ForumTab.Hot, ForumTab.Custom(99, "吧友互助", 15)),
            state.tabs,
        )
        assertTrue(state.loggedIn)
        assertEquals(listOf("吧名"), saved.map { it.name })
        coVerify { remote.page("吧名", any(), FrsTab(503, 14, "最新"), 1, 1, 1, 0, any(), any(), any()) }
    }

    @Test
    fun `a tab loads the first time it is shown`() = runTest {
        respond(frsPage(), frsPage(threads = listOf(thread(2))))
        coEvery { remote.generalTabList(FORUM_ID, "吧名", 99, "吧友互助", 15, 1, any(), any()) } returns
            GeneralTabListResponseData(general_list = listOf(thread(3)))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.selectTab(ForumTab.Hot)
        runCurrent()
        viewModel.selectTab(ForumTab.Custom(99, "吧友互助", 15))
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf(2L), state.list(ForumTab.Hot).items.map { it.thread.id })
        assertEquals(listOf(3L), state.list(ForumTab.Custom(99, "吧友互助", 15)).items.map { it.thread.id })
        coVerify { remote.page("吧名", any(), FrsTab(1, 13, "热门"), 1, 1, -1, 0, any(), any(), any()) }
        assertEquals(listOf("吧名"), saved.map { it.name })
    }

    @Test
    fun `changing the order reloads the latest threads and remembers it`() = runTest {
        respond(frsPage(), frsPage(threads = listOf(thread(5))))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.setSort(ForumSort.POST_TIME)
        runCurrent()

        assertEquals(listOf(5L), viewModel.state.value.list(ForumTab.Latest).items.map { it.thread.id })
        assertEquals(ForumSort.POST_TIME, sorts["吧名"])
        coVerify { remote.page("吧名", any(), FrsTab(503, 14, "最新"), 1, 1, 1, 0, any(), any(), any()) }
    }

    @Test
    fun `choosing a class reloads the good threads of it`() = runTest {
        respond(frsPage())
        val viewModel = createViewModel()
        runCurrent()
        viewModel.selectTab(ForumTab.Good)
        runCurrent()

        viewModel.setGoodClass(3)
        runCurrent()

        assertEquals(3, viewModel.state.value.goodClassId)
        coVerify { remote.page("吧名", any(), FrsTab(301, FrsTab.GoodType, "精华"), 1, 1, -1, 0, any(), any(), any()) }
        coVerify { remote.page("吧名", any(), FrsTab(301, FrsTab.GoodType, "精华"), 1, 1, -1, 3, any(), any(), any()) }
    }

    @Test
    fun `the button follows the forum, then signs in to it`() = runTest {
        respond(frsPage(liked = false))
        coEvery { likeRemote.like(FORUM_ID, "吧名", false, "page-tbs") } returns
            LikeForumResponse(info = LikeForumResponse.Info(memberSum = "11"))
        coEvery { signRemote.sign(FORUM_ID, "吧名", "page-tbs") } returns
            SignResponse(userInfo = SignResponse.UserInfo(isSignIn = "1", contSignNum = "1", signBonusPoint = "8", userSignRank = "3"))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.followOrSignIn()
        runCurrent()
        assertTrue(viewModel.state.value.forum!!.liked)

        viewModel.followOrSignIn()
        runCurrent()
        assertTrue(viewModel.state.value.forum!!.signedIn)
        assertEquals(listOf(R.string.toast_like_success, R.string.toast_sign_success), shown)
    }

    @Test
    fun `agreeing shows at once and is rolled back when it fails`() = runTest {
        respond(frsPage())
        val response = CompletableDeferred<Unit>()
        coEvery { agreeRemote.opAgree(any(), any(), any(), any()) } coAnswers {
            response.await()
            throw IOException("network")
        }
        val viewModel = createViewModel()
        runCurrent()

        viewModel.agree(ForumTab.Latest, 1)
        runCurrent()
        assertEquals(true, viewModel.state.value.list(ForumTab.Latest).items.single().thread.agree.agreed)

        response.complete(Unit)
        runCurrent()
        assertEquals(false, viewModel.state.value.list(ForumTab.Latest).items.single().thread.agree.agreed)
        assertEquals(listOf(R.string.toast_agree_failed), shown)
    }

    @Test
    fun `blocked threads follow the block rules`() = runTest {
        respond(frsPage(threads = listOf(thread(1, title = "广告"), thread(2))))
        val viewModel = createViewModel()
        runCurrent()

        blockRules.value = blockRulesMatching { "广告" in it.title }
        runCurrent()

        assertEquals(listOf(true, false), viewModel.state.value.list(ForumTab.Latest).items.map { it.blocked })
    }

    private companion object {
        const val FORUM_ID = 52L
    }
}
