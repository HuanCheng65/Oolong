package oolong.client.repository

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import oolong.client.api.models.protos.ThreadInfo
import oolong.client.api.models.protos.personalized.PersonalizedResponseData
import oolong.client.api.models.protos.personalized.ThreadPersonalized
import oolong.client.api.models.protos.userLike.UserLikeResponseData
import oolong.client.arch.IoDispatcher
import oolong.client.arch.Page
import oolong.client.model.DislikeOption
import oolong.client.model.ForumMeasure
import oolong.client.model.ForumRanking
import oolong.client.model.ForumSummary
import oolong.client.model.HotTopic
import oolong.client.model.RankedForum
import oolong.client.model.RankedThread
import oolong.client.model.RankedTopic
import oolong.client.model.RecommendedThread
import oolong.client.model.ThreadMeasure
import oolong.client.model.ThreadSummary
import oolong.client.model.Topic
import oolong.client.model.TopicForum
import oolong.client.model.TopicPage
import oolong.client.model.TopicRef
import oolong.core.network.explore.ExploreRemoteDataSource
import javax.inject.Inject

/** Whether the feeds load anew when the app opens, rather than showing what was last loaded. */
fun interface FeedAutoRefresh {
    fun enabled(): Boolean
}

/** Remembers when the followed users' feed was last loaded from the top, which the server wants back. */
interface ConcernFeedStore {
    var lastRequestUnix: Long
}

/** The explore tab's feeds: recommended threads, the followed users' threads and the hot threads. */
class ExploreRepository @Inject constructor(
    private val remote: ExploreRemoteDataSource,
    private val concernStore: ConcernFeedStore,
    private val videoFilter: VideoThreadFilter,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val emoticons: EmoticonRegistry,
    private val snapshots: FeedSnapshots,
    private val autoRefresh: FeedAutoRefresh,
    private val recent: RecentResponses = RecentResponses.None,
) {
    /**
     * A page of recommended threads, without adverts, live streams and, if the user hides them, videos.
     * It is kept to show again when the app opens next, see [keptRecommended].
     *
     * @param refresh whether the user pulled to refresh, which asks for new threads rather than the next page
     * @param newList whether the page starts the list over, rather than going in front of it on a refresh
     */
    suspend fun recommended(refresh: Boolean, page: Int, newList: Boolean = false): List<RecommendedThread> = withContext(io) {
        val response = remote.personalized(refresh, page)
        val place = when {
            page != 1 -> FeedSnapshots.Place.Back
            newList -> FeedSnapshots.Place.Replace
            else -> FeedSnapshots.Place.Front
        }
        val bytes = response.encode()
        snapshots.write(RecommendedFeed, bytes, place, next = "${page + 1}")
        recent.keep(RecentResponses.Recommended, bytes)
        response.recommendedThreads()
    }

    /**
     * The recommended threads as they were last loaded, with the page to load after them; null
     * when the feeds load anew as the app opens, or nothing was kept.
     */
    suspend fun keptRecommended(): KeptFeed<Int, RecommendedThread>? = withContext(io) {
        if (autoRefresh.enabled()) return@withContext null
        val kept = snapshots.read(RecommendedFeed) ?: return@withContext null
        runCatching {
            val threads = kept.pages.flatMap { PersonalizedResponseData.ADAPTER.decode(it).recommendedThreads() }.distinctBy { it.thread.id }
            KeptFeed(threads, next = kept.next?.toIntOrNull(), kept.hidden)
        }.getOrNull()?.takeIf { it.items.isNotEmpty() }
    }

    private fun PersonalizedResponseData.recommendedThreads(): List<RecommendedThread> {
        val personalized = thread_personalized.associateBy { it.tid }
        return filter(thread_list).map { thread ->
            RecommendedThread(thread.toThreadSummary(emoticons), personalized[thread.id]?.dislikeOptions().orEmpty().toImmutableList())
        }
    }

    /**
     * A page of the threads the users the user follows posted; the key is the server's page tag,
     * empty for the first. It is kept to show again when the app opens next, see [keptFollowedUsersThreads].
     */
    suspend fun followedUsersThreads(pageTag: String): Page<String, ThreadSummary> = withContext(io) {
        val first = pageTag.isEmpty()
        val response = remote.userLike(pageTag, concernStore.lastRequestUnix)
        if (first) concernStore.lastRequestUnix = response.requestUnix
        val page = response.followedThreads()
        snapshots.write(FollowedFeed, response.encode(), if (first) FeedSnapshots.Place.Replace else FeedSnapshots.Place.Back, page.next)
        page
    }

    /**
     * The followed users' threads as they were last loaded, with the tag of the page after them;
     * null when the feeds load anew as the app opens, or nothing was kept.
     */
    suspend fun keptFollowedUsersThreads(): KeptFeed<String, ThreadSummary>? = withContext(io) {
        if (autoRefresh.enabled()) return@withContext null
        val kept = snapshots.read(FollowedFeed) ?: return@withContext null
        runCatching {
            val threads = kept.pages.flatMap { UserLikeResponseData.ADAPTER.decode(it).followedThreads().items }.distinctBy { it.id }
            KeptFeed(threads, next = kept.next, kept.hidden)
        }.getOrNull()?.takeIf { it.items.isNotEmpty() }
    }

    private fun UserLikeResponseData.followedThreads(): Page<String, ThreadSummary> {
        val threads = threadInfo.filter { it.recommendType == 1 }.mapNotNull { it.threadList?.toThreadSummary(emoticons) }
        return Page(threads, next = pageTag.takeIf { hasMore == 1 && it.isNotEmpty() })
    }

    /** The threads of the last day ranked by [measure]. */
    suspend fun threadRanking(measure: ThreadMeasure): List<RankedThread> = withContext(io) {
        val tab = remote.threadRanking(measure.code) ?: return@withContext emptyList()
        tab.threads.mapIndexed { index, thread ->
            RankedThread(
                rank = index + 1,
                thread = thread.toThreadSummary(emoticons),
                reason = rankReason(tab.labels.getOrNull(index).orEmpty()),
                topic = thread.reasons.firstOrNull { it.topicId != 0L && it.text.isNotBlank() }?.let { TopicRef(it.topicId, it.text) },
            )
        }
    }

    /** The forums of the last day ranked by [measure]. */
    suspend fun forumRanking(measure: ForumMeasure): ForumRanking = withContext(io) {
        val tab = remote.forumRanking(measure.code) ?: return@withContext ForumRanking("")
        ForumRanking(
            rule = tab.rule.replace("24h内", "近一天"),
            forums = tab.forums.mapIndexed { index, forum ->
                RankedForum(
                    rank = index + 1,
                    forum = ForumSummary(forum.id, forum.name, forum.avatar),
                    members = forum.members,
                    liked = forum.liked == 1,
                    reason = rankReason(forum.score),
                )
            }.toImmutableList(),
        )
    }

    /**
     * Page [page] (from 1) of a topic, found by [name] when [id] is 0.
     *
     * @param lastThreadId the id of the last thread of the page before, 0 for the first
     */
    suspend fun topic(id: Long, name: String, page: Int, lastThreadId: Long): TopicPage = withContext(io) {
        val response = remote.topic(id, name, page, lastThreadId)
        val cards = response.areas.flatMap { it.cards }
        TopicPage(
            topic = response.topic?.let {
                Topic(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    picture = it.picture,
                    discussCount = it.discussCount,
                    rank = it.rank,
                    tag = topicTag(it.tag),
                    color = response.themeColor?.day?.common?.let(::argb),
                )
            },
            forums = cards.filter { it.type == RelatedForumsCard }.flatMap { it.data?.forums.orEmpty() }.map {
                TopicForum(ForumSummary(it.id, it.name, it.avatar), it.members)
            },
            threads = cards.mapNotNull { card ->
                val thread = card.data?.thread?.takeIf { card.type in ThreadCards } ?: return@mapNotNull null
                thread.toThreadSummary(emoticons) to (card.type == LeadThreadCard)
            },
            hasMore = response.hasMore == 1,
        )
    }

    /** The full hot topics ranking. */
    suspend fun topics(): List<RankedTopic> = withContext(io) {
        remote.topics().topic_list.map {
            RankedTopic(
                id = it.topic_id,
                name = it.topic_name,
                description = it.topic_desc,
                image = it.topic_image,
                discussCount = it.discuss_num,
                tag = topicTag(it.topic_tag),
            )
        }
    }

    /**
     * Tells the server the user doesn't want threads like [thread] for the [reasons] they picked.
     *
     * @param clickTime when the user opened the menu, in Unix milliseconds
     */
    suspend fun dislike(thread: ThreadSummary, reasons: List<DislikeOption>, clickTime: Long) = withContext(io) {
        // Hidden from the list kept too, whether or not the server hears of it
        snapshots.hide(RecommendedFeed, thread.id)
        remote.dislike(
            threadId = thread.id,
            forumId = thread.forum.id,
            reasonIds = reasons.map { it.id },
            extras = reasons.map { it.extra },
            clickTime = clickTime,
        )
    }

    /**
     * Drops live streams, videos if hidden, and adverts: those from outside any forum, which carry
     * no forum, and promoted threads and goods, which the server marks with thread types of their own.
     */
    private fun filter(threads: List<ThreadInfo>): List<ThreadInfo> {
        val hideVideos = videoFilter.hidesVideos()
        return threads.filter {
            it.ala_info == null && it.forumInfo != null && it.thread_type !in PromotedThreadTypes && !(hideVideos && it.videoInfo != null)
        }
    }
}

/** A feed as it was last loaded: its threads, the key of the page after them, and the threads hidden from it. */
class KeptFeed<K, T>(val items: List<T>, val next: K?, val hidden: Set<Long>)

private const val RecommendedFeed = "recommended"
private const val FollowedFeed = "followed"

/** Promoted threads, such as a game to book (80), and goods offered as a reply in a thread (79). */
private val PromotedThreadTypes = setOf(79, 80)
private const val LeadThreadCard = "common_thread"
private const val RelatedForumsCard = "relate_forum"
private val ThreadCards = setOf(LeadThreadCard, "relate_thread")

/** `RRGGBB`, or `AARRGGBB`, without `#`, as an opaque ARGB colour; null when it isn't one. */
private fun argb(hex: String): Int? {
    val value = hex.removePrefix("#").toLongOrNull(16) ?: return null
    return when (hex.removePrefix("#").length) {
        6 -> (0xFF000000 or value).toInt()
        8 -> (value or 0xFF000000).toInt()
        else -> null
    }
}

private fun topicTag(tag: Int) = when (tag) {
    1 -> HotTopic.Tag.New
    2 -> HotTopic.Tag.Hot
    else -> HotTopic.Tag.None
}

private fun ThreadPersonalized.dislikeOptions() = dislikeResource.map {
    DislikeOption(
        id = it.dislikeId,
        text = it.dislikeReason,
        extra = it.extra,
        schema = it.schema.takeIf(String::isNotBlank),
        toast = it.toast?.content?.joinToString("") { part -> part.text }?.takeIf(String::isNotBlank),
    )
}
