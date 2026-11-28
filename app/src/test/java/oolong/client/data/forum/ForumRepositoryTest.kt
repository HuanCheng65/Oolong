package oolong.client.data.forum

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import oolong.client.model.ForumDetail
import oolong.client.model.ForumIntro
import oolong.client.model.ForumRuleAuthor
import oolong.client.model.ForumRuleDetail
import oolong.client.model.ForumRuleItem
import oolong.client.model.ForumSort
import oolong.client.model.ForumTab
import oolong.client.model.GoodClass
import oolong.client.model.PostContent
import oolong.client.model.TextSpan
import oolong.core.network.forum.ForumRemoteDataSource
import oolong.core.network.forum.FrsTab
import oolong.core.network.forum.LikeForumResponse
import oolong.core.network.forum.LikeRemoteDataSource
import oolong.core.network.protos.AlaLiveInfo
import oolong.core.network.protos.Anti
import oolong.core.network.protos.BawuRoleInfoPub
import oolong.core.network.protos.ForumRule
import oolong.core.network.protos.ForumRuleStatus
import oolong.core.network.protos.FrsTabInfo
import oolong.core.network.protos.Page
import oolong.core.network.protos.PbContent
import oolong.core.network.protos.RecommendForumInfo
import oolong.core.network.protos.ThreadInfo
import oolong.core.network.protos.User
import oolong.core.network.protos.VideoInfo
import oolong.core.network.protos.forumRuleDetail.ForumRuleDetailResponseData
import oolong.core.network.protos.frsPage.Classify
import oolong.core.network.protos.frsPage.ForumInfo
import oolong.core.network.protos.frsPage.FrsPageResponseData
import oolong.core.network.protos.frsPage.NavTabInfo
import oolong.core.network.protos.frsPage.RankInfo
import oolong.core.network.protos.frsPage.SignForum
import oolong.core.network.protos.frsPage.SignInfo
import oolong.core.network.protos.frsPage.SignUser
import oolong.core.network.protos.generalTabList.GeneralTabListResponseData
import oolong.core.network.protos.threadList.ThreadListResponseData
import oolong.core.network.sign.SignRemoteDataSource
import oolong.core.network.sign.SignResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ForumRepositoryTest {
    private val remote = mockk<ForumRemoteDataSource>()
    private val likeRemote = mockk<LikeRemoteDataSource>()
    private val signRemote = mockk<SignRemoteDataSource>()
    private var hidesVideos = false
    private val repository = forumRepository(remote, likeRemote, signRemote, videoFilter = { hidesVideos })

    private fun thread(id: Long, authorId: Long = 0, live: Boolean = false, video: Boolean = false) = ThreadInfo(
        id = id,
        authorId = authorId,
        author = User(id = authorId).takeIf { authorId != 0L },
        ala_info = if (live) AlaLiveInfo() else null,
        videoInfo = if (video) VideoInfo() else null,
    )

    private fun frsPage(
        threads: List<ThreadInfo> = listOf(thread(1)),
        ids: List<Long> = emptyList(),
        hasMore: Boolean = true,
        users: List<User> = emptyList(),
        forum: ForumInfo = ForumInfo(id = FORUM_ID, name = "吧名", avatar = "a"),
    ) = FrsPageResponseData(
        forum = forum,
        page = Page(has_more = if (hasMore) 1 else 0),
        thread_list = threads,
        thread_id_list = ids,
        user_list = users,
        anti = Anti(tbs = "page-tbs"),
    )

    @Test
    fun `maps a forum's intro with its introduction as plain text`() = runTest {
        coEvery { remote.forumDetail(52) } returns RecommendForumInfo(
            forum_name = "测试", avatar = "a", member_count = 10, thread_count = 20, slogan = "标语",
            content = listOf(PbContent(type = 0, text = "介绍")),
        )

        assertEquals(ForumIntro("测试", "a", 10, 20, "标语", "介绍"), repository.forumIntro(52))
    }

    @Test
    fun `the first page of a forum comes with the forum and its tabs`() = runTest {
        coEvery { remote.page("吧名", 0, any(), 1, 1, 0, 0, any()) } returns frsPage(
            forum = ForumInfo(
                id = FORUM_ID, name = "吧名", avatar = "a", member_num = 10, is_like = 1, user_level = 7,
                level_name = "七级", cur_score = 30, levelup_score = 100, has_frs_star = 1,
                sign_in_info = SignInfo(
                    user_info = SignUser(is_sign_in = 1, cont_sign_num = 5),
                    forum_info = SignForum(current_rank_info = RankInfo(sign_count = 1_200)),
                ),
                good_classify = listOf(Classify(class_id = 0, class_name = "全部"), Classify(class_id = 3, class_name = "攻略")),
            ),
        ).copy(
            nav_tab_info = NavTabInfo(
                tab = listOf(
                    FrsTabInfo(tabId = 1, tabType = 13, tabName = "热门"),
                    FrsTabInfo(tabId = 99, tabType = 15, tabName = "吧友互助", isGeneralTab = 1),
                    FrsTabInfo(tabId = 507, tabType = 102, tabName = "合辑", isGeneralTab = 1),
                ),
            ),
            forum_rule = ForumRuleStatus(has_forum_rule = 1, title = "吧规"),
        )

        val page = repository.threads("吧名", 0, ForumTab.Latest, ForumCursor.FrsPage(1))

        assertEquals(
            ForumDetail(
                id = FORUM_ID, name = "吧名", avatar = "a", memberCount = 10, liked = true, level = 7,
                levelName = "七级", score = 30, levelUpScore = 100, signedIn = true, signedDays = 5,
                todaySignCount = 1_200, hasHead = true, ruleTitle = "吧规",
                goodClasses = persistentListOf(GoodClass(0, "全部"), GoodClass(3, "攻略")),
                tabs = persistentListOf(ForumTab.Good, ForumTab.Latest, ForumTab.Hot, ForumTab.Custom(99, "吧友互助", 15)),
            ),
            page.forum,
        )
        assertEquals("page-tbs", page.tbs)
    }

    @Test
    fun `the threads listed by id are loaded in batches before the next page`() = runTest {
        val ids = (100L until 140L).toList()
        coEvery { remote.page("吧名", FORUM_ID, any(), 1, 1, 1, 0, any()) } returns frsPage(ids = ids)
        coEvery { remote.threadList(FORUM_ID, "吧名", 1, 1, any(), false) } answers {
            ThreadListResponseData(thread_list = arg<List<Long>>(4).map { thread(it) })
        }
        coEvery { remote.page("吧名", FORUM_ID, any(), 2, 2, 1, 0, any()) } returns frsPage(threads = listOf(thread(200)), hasMore = false)

        var cursor: ForumCursor? = ForumCursor.FrsPage(1)
        val loaded = mutableListOf<Long>()
        while (cursor != null) {
            val page = repository.threads("吧名", FORUM_ID, ForumTab.Latest, cursor, sortType = ForumSort.POST_TIME).threads
            loaded += page.items.map { it.id }
            cursor = page.next
        }

        assertEquals(listOf(1L) + ids + 200L, loaded)
        coVerify { remote.threadList(FORUM_ID, "吧名", 1, 1, ids.take(30), false) }
        coVerify { remote.threadList(FORUM_ID, "吧名", 1, 1, ids.drop(30), false) }
    }

    @Test
    fun `hot and good threads are asked for by their own tabs, in the server's order`() = runTest {
        coEvery { remote.page(any(), any(), any(), any(), any(), any(), any(), any()) } returns frsPage(hasMore = false)

        repository.threads("吧名", 0, ForumTab.Hot, ForumCursor.FrsPage(1), sortType = ForumSort.POST_TIME)
        repository.threads("吧名", 0, ForumTab.Good, ForumCursor.FrsPage(1), goodClassId = 3)

        coVerify { remote.page("吧名", 0, FrsTab(1, 13, "热门"), 1, 1, -1, 0, any()) }
        coVerify { remote.page("吧名", 0, FrsTab(301, FrsTab.GoodType, "精华"), 1, 1, -1, 3, any()) }
    }

    @Test
    fun `a moderators' tab pages after its last thread`() = runTest {
        val tab = ForumTab.Custom(99, "吧友互助", 15)
        coEvery { remote.generalTabList(FORUM_ID, "吧名", 99, "吧友互助", 15, 1, 0) } returns
            GeneralTabListResponseData(general_list = listOf(thread(5), thread(6)), has_more = 1)
        coEvery { remote.generalTabList(FORUM_ID, "吧名", 99, "吧友互助", 15, 2, 6) } returns
            GeneralTabListResponseData(general_list = listOf(thread(7)), has_more = 0)

        val first = repository.threads("吧名", FORUM_ID, tab, ForumCursor.first(tab)).threads
        val second = repository.threads("吧名", FORUM_ID, tab, first.next!!).threads

        assertEquals(listOf(5L, 6L, 7L), (first.items + second.items).map { it.id })
        assertNull(second.next)
    }

    @Test
    fun `live streams and hidden videos are dropped and authors filled in`() = runTest {
        hidesVideos = true
        coEvery { remote.page(any(), any(), any(), any(), any(), any(), any(), any()) } returns frsPage(
            threads = listOf(thread(1, authorId = 20), thread(2, live = true), thread(3, video = true)),
            users = listOf(User(id = 20, name = "作者")),
            hasMore = false,
        )

        val threads = repository.threads("吧名", 0, ForumTab.Latest, ForumCursor.FrsPage(1)).threads.items

        assertEquals(listOf(1L), threads.map { it.id })
        assertEquals("作者", threads.single().author.name)
    }

    @Test
    fun `signing in updates the level, score and days`() = runTest {
        val forum = ForumDetail(id = FORUM_ID, name = "吧名", avatar = "", liked = true, level = 7, score = 30, levelUpScore = 100)
        coEvery { signRemote.sign(FORUM_ID, "吧名", "tbs") } returns SignResponse(
            userInfo = SignResponse.UserInfo(
                isSignIn = "1", contSignNum = "6", userSignRank = "12", signBonusPoint = "8",
                levelName = "八级", levelUpScore = "200",
                allLevelInfo = listOf(SignResponse.LevelInfo("7", "七级", "50"), SignResponse.LevelInfo("8", "八级", "100"), SignResponse.LevelInfo("9", "九级", "200")),
            ),
        )

        val result = repository.signIn(forum, "tbs")

        assertEquals(forum.copy(signedIn = true, signedDays = 6, score = 38, levelUpScore = 200, level = 8, levelName = "八级"), result.forum)
        assertEquals(8, result.bonusPoints)
        assertEquals(12, result.rank)
    }

    @Test
    fun `following takes the level and member count the server sends`() = runTest {
        val forum = ForumDetail(id = FORUM_ID, name = "吧名", avatar = "", hasHead = true)
        coEvery { likeRemote.like(FORUM_ID, "吧名", true, "tbs") } returns LikeForumResponse(
            info = LikeForumResponse.Info(levelId = "1", levelName = "一级", memberSum = "11", curScore = "0", levelUpScore = "5"),
        )

        assertEquals(
            forum.copy(liked = true, level = 1, levelName = "一级", memberCount = 11, score = 0, levelUpScore = 5),
            repository.like(forum, "tbs"),
        )
    }

    @Test
    fun `maps the forum rule response`() = runTest {
        coEvery { remote.forumRuleDetail(FORUM_ID) } returns forumRuleData()

        assertEquals(forumRuleDetail(), repository.forumRuleDetail(FORUM_ID))
    }

    @Test
    fun `keeps consecutive text pieces of a rule in one block`() = runTest {
        val rule = ForumRule(
            title = "规则",
            content = listOf(PbContent(type = 0, text = "第一句"), PbContent(type = 0, text = "第二句")),
        )
        coEvery { remote.forumRuleDetail(FORUM_ID) } returns forumRuleData(rules = listOf(rule))

        val detail = repository.forumRuleDetail(FORUM_ID)
        assertEquals(
            persistentListOf(PostContent.Text(persistentListOf(TextSpan.Plain("第一句"), TextSpan.Plain("第二句")))),
            detail.rules.single().content,
        )
    }

    @Test
    fun `fails when the rule has no author`() = runTest {
        coEvery { remote.forumRuleDetail(FORUM_ID) } returns ForumRuleDetailResponseData(title = "本吧吧规")

        assertFailsWith<IllegalStateException> { repository.forumRuleDetail(FORUM_ID) }
    }
}

internal const val FORUM_ID = 52L

internal fun forumRepository(
    remote: ForumRemoteDataSource,
    likeRemote: LikeRemoteDataSource = mockk(),
    signRemote: SignRemoteDataSource = mockk(),
    videoFilter: VideoThreadFilter = VideoThreadFilter { false },
) = ForumRepository(remote, likeRemote, signRemote, mockk(relaxed = true), videoFilter, Dispatchers.Unconfined, { _, _ -> })

internal fun forumRuleData(
    rules: List<ForumRule> = listOf(
        ForumRule(title = "一、发帖规范", content = listOf(PbContent(type = 0, text = "标题需清楚描述帖子内容。"))),
    ),
) = ForumRuleDetailResponseData(
    title = "本吧吧规",
    preface = "请遵守以下规定。",
    publish_time = "2026-10-31",
    rules = rules,
    bazhu = BawuRoleInfoPub(user_name = "bazhu", name_show = "吧主", portrait = "tb.1.abc"),
)

internal fun forumRuleDetail() = ForumRuleDetail(
    title = "本吧吧规",
    publishTime = "2026-10-31",
    preface = "请遵守以下规定。",
    author = ForumRuleAuthor(name = "bazhu", nameShow = "吧主", portrait = "tb.1.abc"),
    rules = persistentListOf(
        ForumRuleItem("一、发帖规范", persistentListOf(PostContent.Text(persistentListOf(TextSpan.Plain("标题需清楚描述帖子内容。"))))),
    ),
)
