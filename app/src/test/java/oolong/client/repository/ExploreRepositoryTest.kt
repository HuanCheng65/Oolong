package oolong.client.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import oolong.client.api.models.protos.AlaLiveInfo
import oolong.client.api.models.protos.SimpleForum
import oolong.client.api.models.protos.ThreadInfo
import oolong.client.api.models.protos.VideoInfo
import oolong.client.api.models.protos.personalized.DislikeReason
import oolong.client.api.models.protos.personalized.PersonalizedResponseData
import oolong.client.api.models.protos.personalized.ThreadPersonalized
import oolong.client.api.models.protos.topicList.NewTopicList
import oolong.client.api.models.protos.topicList.TopicListResponseData
import oolong.client.api.models.protos.userLike.ConcernData
import oolong.client.api.models.protos.userLike.UserLikeResponseData
import oolong.client.model.DislikeOption
import oolong.client.model.ForumSummary
import oolong.client.model.HotTopic
import oolong.client.model.RankReason
import oolong.client.model.RankedTopic
import oolong.client.model.ThreadMeasure
import oolong.client.model.TopicForum
import oolong.client.model.TopicRef
import oolong.core.network.explore.ExploreRemoteDataSource
import oolong.core.network.explore.ThreadRankingResponse
import oolong.core.network.explore.TopicResponse
import oolong.core.network.explore.WebContent
import oolong.core.network.explore.WebThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExploreRepositoryTest {
    private val remote = mockk<ExploreRemoteDataSource>(relaxed = true)
    private val store = object : ConcernFeedStore {
        override var lastRequestUnix = 100L
    }
    private var hideVideos = false
    private val snapshots = object : FeedSnapshots {
        val pages = mutableMapOf<String, MutableList<ByteArray>>()
        var next: String? = null
        val hidden = mutableSetOf<Long>()
        override fun read(feed: String) = pages[feed]?.let { FeedSnapshot(it, next, hidden) }
        override fun write(feed: String, page: ByteArray, place: FeedSnapshots.Place, next: String?) {
            val list = pages.getOrPut(feed) { mutableListOf() }
            when (place) {
                FeedSnapshots.Place.Replace -> list.apply { clear() }.add(page)
                FeedSnapshots.Place.Front -> list.add(0, page)
                FeedSnapshots.Place.Back -> list.add(page)
            }
            this.next = next
        }
        override fun hide(feed: String, threadId: Long) {
            hidden += threadId
        }
    }
    private var autoRefresh = false
    private val repository = ExploreRepository(
        remote, store, { hideVideos }, Dispatchers.Unconfined, { _, _ -> }, snapshots, { autoRefresh },
    )

    @Test
    fun `recommended threads carry their dislike reasons and leave out live streams, hidden videos and adverts`() = runTest {
        hideVideos = true
        val forum = SimpleForum(id = 52)
        coEvery { remote.personalized(true, 1) } returns PersonalizedResponseData(
            thread_list = listOf(
                ThreadInfo(id = 1, threadId = 1, forumInfo = forum),
                ThreadInfo(id = 2, threadId = 2, forumInfo = forum, ala_info = AlaLiveInfo()),
                ThreadInfo(id = 3, threadId = 3, forumInfo = forum, videoInfo = VideoInfo()),
                ThreadInfo(id = 4, threadId = 4),
                ThreadInfo(id = 5, threadId = 5, forumInfo = forum, thread_type = 80),
                ThreadInfo(id = 6, threadId = 6, forumInfo = forum, thread_type = 79),
            ),
            thread_personalized = listOf(
                ThreadPersonalized(tid = 1, dislikeResource = listOf(DislikeReason(dislikeReason = "看过了", dislikeId = 2, extra = "x"))),
            ),
        )

        val threads = repository.recommended(refresh = true, page = 1)

        assertEquals(listOf(1L), threads.map { it.thread.id })
        assertEquals(listOf(DislikeOption(2, "看过了", "x")), threads.single().dislikeOptions)
    }

    @Test
    fun `the recommended pages are kept in the list's order and read back through the same filters`() = runTest {
        val forum = SimpleForum(id = 52)
        coEvery { remote.personalized(true, 1) } returnsMany listOf(
            PersonalizedResponseData(thread_list = listOf(ThreadInfo(id = 1, threadId = 1, forumInfo = forum))),
            PersonalizedResponseData(thread_list = listOf(ThreadInfo(id = 3, threadId = 3, forumInfo = forum), ThreadInfo(id = 9, threadId = 9, forumInfo = forum, thread_type = 80))),
        )
        coEvery { remote.personalized(false, 2) } returns PersonalizedResponseData(thread_list = listOf(ThreadInfo(id = 2, threadId = 2, forumInfo = forum)))

        repository.recommended(refresh = true, page = 1, newList = true)
        repository.recommended(refresh = false, page = 2)
        repository.recommended(refresh = true, page = 1)
        val kept = repository.keptRecommended()!!

        assertEquals(listOf(3L, 1L, 2L), kept.items.map { it.thread.id })
        assertEquals(2, kept.next)
    }

    @Test
    fun `nothing kept means nothing to show`() = runTest {
        assertNull(repository.keptRecommended())
        assertNull(repository.keptFollowedUsersThreads())
    }

    @Test
    fun `loading anew as the app opens shows nothing kept, and disliked threads stay hidden`() = runTest {
        coEvery { remote.personalized(true, 1) } returns PersonalizedResponseData(thread_list = listOf(ThreadInfo(id = 1, threadId = 1, forumInfo = SimpleForum(id = 52))))
        repository.recommended(refresh = true, page = 1, newList = true)
        repository.dislike(ThreadInfo(id = 1, threadId = 1, forumInfo = SimpleForum(id = 52)).toThreadSummary { _, _ -> }, emptyList(), 0)

        assertEquals(setOf(1L), repository.keptRecommended()!!.hidden)
        autoRefresh = true
        assertNull(repository.keptRecommended())
    }

    @Test
    fun `the followed users' feed remembers when it was loaded from the top`() = runTest {
        coEvery { remote.userLike("", 100) } returns UserLikeResponseData(
            threadInfo = listOf(
                ConcernData(threadList = ThreadInfo(id = 1, threadId = 1), recommendType = 1),
                ConcernData(recommendType = 2),
            ),
            pageTag = "next",
            hasMore = 1,
            requestUnix = 200,
        )
        coEvery { remote.userLike("next", 200) } returns UserLikeResponseData(pageTag = "", hasMore = 0, requestUnix = 300)

        val first = repository.followedUsersThreads("")
        val second = repository.followedUsersThreads("next")

        assertEquals(listOf(1L), first.items.map { it.id })
        assertEquals("next", first.next)
        assertNull(second.next)
        assertEquals(200L, store.lastRequestUnix)
    }

    @Test
    fun `maps the full topics ranking`() = runTest {
        coEvery { remote.topics() } returns TopicListResponseData(
            topic_list = listOf(NewTopicList(topic_id = 1, topic_name = "话题", topic_desc = "描述", discuss_num = 5, topic_image = "i", topic_tag = 2)),
        )

        assertEquals(listOf(RankedTopic(1, "话题", "描述", "i", 5, HotTopic.Tag.Hot)), repository.topics())
    }

    @Test
    fun `dislikes a thread with the reasons picked`() = runTest {
        val thread = ThreadInfo(id = 9, threadId = 9, forumInfo = SimpleForum(id = 52))

        repository.dislike(thread.toThreadSummary { _, _ -> }, listOf(DislikeOption(1, "a", "e1"), DislikeOption(7, "b", "e2")), clickTime = 5)

        coVerify { remote.dislike(9, 52, listOf(1, 7), listOf("e1", "e2"), 5) }
    }

    @Test
    fun `ranks threads with why each is there and the topic it is under`() = runTest {
        coEvery { remote.threadRanking("thread_uv_num") } returns ThreadRankingResponse.Tab(
            labels = listOf("近一天2.6W吧友看过", "说不清"),
            threads = listOf(
                WebThread(id = 1, tid = 1, title = "a", reasons = listOf(WebThread.Reason("话题", 7))),
                WebThread(id = 2, tid = 2, title = "b", reasons = listOf(WebThread.Reason("最新上榜", 0))),
            ),
        )

        val threads = repository.threadRanking(ThreadMeasure.MostRead)

        assertEquals(listOf(1, 2), threads.map { it.rank })
        assertEquals(listOf(RankReason.Readers(26_000), RankReason.Text("说不清")), threads.map { it.reason })
        assertEquals(listOf(TopicRef(7, "话题"), null), threads.map { it.topic })
    }

    @Test
    fun `reads the server's reasons in its several wordings`() {
        assertEquals(RankReason.Likes(78_000), rankReason("近一天获赞7.8W次"))
        assertEquals(RankReason.Likes(9866), rankReason("近一天获赞9866次"))
        assertEquals(RankReason.NewFollowers(2992), rankReason("吧关注+2992"))
        assertEquals(RankReason.NewThreads(10_000), rankReason("发贴量 +1W"))
        assertEquals(RankReason.AnswerMinutes(6.4f), rankReason("平均6.4min被解答"))
        assertEquals(RankReason.Reads(12_000_000), rankReason("贴子阅读量1.2KW"))
        assertEquals(RankReason.Reads(2_649_000), rankReason("贴子阅读量264.9W"))
    }

    @Test
    fun `a topic page puts the lead thread first and its forums apart`() = runTest {
        coEvery { remote.topic(0, "n", 1, 0) } returns TopicResponse(
            topic = TopicResponse.Topic(id = 7, name = "n", rank = 3, tag = 2),
            themeColor = TopicResponse.ThemeColor(day = TopicResponse.Color("9B8F37")),
            areas = listOf(
                TopicResponse.Area(
                    listOf(
                        TopicResponse.Card("common_thread", TopicResponse.CardData(thread = WebThread(tid = 1))),
                        TopicResponse.Card("relate_forum", TopicResponse.CardData(forums = listOf(TopicResponse.Forum(5, "f", "", 12)))),
                        TopicResponse.Card("relate_thread", TopicResponse.CardData(thread = WebThread(tid = 2))),
                        TopicResponse.Card("game_card", TopicResponse.CardData()),
                    )
                )
            ),
            hasMore = 1,
        )

        val page = repository.topic(0, "n", 1, 0)

        assertEquals(0xFF9B8F37.toInt(), page.topic?.color)
        assertEquals(HotTopic.Tag.Hot, page.topic?.tag)
        assertEquals(listOf(1L to true, 2L to false), page.threads.map { it.first.id to it.second })
        assertEquals(listOf(TopicForum(ForumSummary(5, "f", ""), 12)), page.forums)
        assertEquals(true, page.hasMore)
    }

    @Test
    fun `a thread without its first floor shows its abstract there, topic text kept`() {
        val thread = WebThread(
            tid = 3,
            richAbstract = listOf(WebContent(type = 18, text = "#话题#"), WebContent(type = 0, text = "正文")),
        ).toThreadSummary { _, _ -> }

        assertEquals("#话题#正文", thread.abstract)
        assertEquals(false, thread.firstPost.isEmpty())
    }
}
