package oolong.client.data.forum

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import oolong.client.arch.IoDispatcher
import oolong.client.arch.Page
import oolong.client.data.thread.EmoticonRegistry
import oolong.client.data.thread.toPostContent
import oolong.client.data.thread.toThreadSummaries
import oolong.client.data.thread.toThreadSummary
import oolong.client.diagnostics.RecentResponses
import oolong.client.model.ForumDetail
import oolong.client.model.ForumIntro
import oolong.client.model.ForumRuleAuthor
import oolong.client.model.ForumRuleDetail
import oolong.client.model.ForumRuleItem
import oolong.client.model.ForumSort
import oolong.client.model.ForumTab
import oolong.client.model.GoodClass
import oolong.client.model.ThreadSummary
import oolong.client.model.plainText
import oolong.core.network.forum.FirstGet
import oolong.core.network.forum.ForumRemoteDataSource
import oolong.core.network.forum.FrsTab
import oolong.core.network.forum.LikeRemoteDataSource
import oolong.core.network.protos.ThreadInfo
import oolong.core.network.protos.User
import oolong.core.network.protos.feed.LayoutFactory
import oolong.core.network.protos.forumRuleDetail.ForumRuleDetailResponseData
import oolong.core.network.protos.frsPage.FrsPageResponseData
import oolong.core.network.sign.SignRemoteDataSource
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the user hides threads with videos from thread lists. */
fun interface VideoThreadFilter {
    fun hidesVideos(): Boolean
}

/** Whether the user hides the events Tieba itself runs from thread lists. */
fun interface ActivityThreadFilter {
    fun hidesActivities(): Boolean
}

/**
 * Where the next page of a forum's threads comes from. [shown] counts the threads the list
 * shows before it, which the requests tell.
 */
sealed interface ForumCursor {
    val shown: Int

    /** A page of frs/page, which lists its first threads and the ids of the rest. */
    data class FrsPage(val page: Int, override val shown: Int = 0) : ForumCursor

    /**
     * Threads of [ids] that frs/page [page] listed, the first batch of them or, after it, all the
     * rest; after them, the next page if [pageHasMore].
     */
    data class FrsThreads(
        val page: Int,
        val ids: List<Long>,
        val pageHasMore: Boolean,
        override val shown: Int = 0,
        val firstBatch: Boolean = true,
    ) : ForumCursor

    /** A page of a moderators' tab, or of the videos. */
    data class Custom(val page: Int, override val shown: Int = 0) : ForumCursor

    companion object {
        fun first(tab: ForumTab): ForumCursor = if (tab is ForumTab.Custom) Custom(1) else FrsPage(1)
    }
}

/** A page of a forum's threads, with the forum as frs/page returned it when that was loaded. */
data class ForumThreadsPage(
    val threads: Page<ForumCursor, ThreadSummary>,
    val forum: ForumDetail? = null,
    val tbs: String? = null,
)

/** The result of signing in to a forum, and the forum updated with it. */
data class ForumSignIn(val forum: ForumDetail, val bonusPoints: Int, val rank: Int)

@Singleton
class ForumRepository @Inject constructor(
    private val remote: ForumRemoteDataSource,
    private val likeRemote: LikeRemoteDataSource,
    private val signRemote: SignRemoteDataSource,
    private val followed: FollowedForums,
    private val videoFilter: VideoThreadFilter,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val emoticons: EmoticonRegistry,
    private val recent: RecentResponses = RecentResponses.None,
    private val activityFilter: ActivityThreadFilter = ActivityThreadFilter { false },
) {
    suspend fun forumRuleDetail(forumId: Long): ForumRuleDetail = withContext(io) {
        remote.forumRuleDetail(forumId).toForumRuleDetail(emoticons)
    }

    suspend fun forumIntro(forumId: Long): ForumIntro = withContext(io) {
        val info = remote.forumDetail(forumId)
        ForumIntro(
            name = info.forum_name,
            avatar = info.avatar,
            memberCount = info.member_count,
            threadCount = info.thread_count,
            slogan = info.slogan,
            intro = info.content.toPostContent(emoticons).plainText,
        )
    }

    /** What each forum's last page gave to send back with the requests after it. */
    private val commonInfo = ConcurrentHashMap<String, String>()

    /**
     * A page of [tab]'s threads from [cursor], as the forum page asks for them. [forumId] may be 0
     * until frs/page has returned it.
     *
     * @param opening whether the forum page is opening: its first page then comes as the official
     *   client's does as it opens, twice at once and for the tab the forum picks, which is then
     *   asked for again only if it isn't [tab]
     * @param goodClassId the class of good threads to list, 0 for all of them
     */
    suspend fun threads(
        forumName: String,
        forumId: Long,
        tab: ForumTab,
        cursor: ForumCursor,
        sortType: Int = ForumSort.REPLY_TIME,
        goodClassId: Int = 0,
        opening: Boolean = false,
    ): ForumThreadsPage = withContext(io) {
        val frsTab = tab.frsTab()
        val sort = if (tab == ForumTab.Latest) sortType else -1
        when (cursor) {
            is ForumCursor.FrsPage -> {
                if (cursor.page == 1 && opening) {
                    val opened = open(forumName)
                    // Its own tab in its own order, as this one asks
                    if (opened.tabId == frsTab?.id && (tab != ForumTab.Latest || opened.sortType == sortType)) {
                        return@withContext opened.page
                    }
                    // The forum opened on another tab: this one is asked for as switching to it would
                    val forum = opened.page.forum
                    val rest = threads(forumName, forum?.id ?: forumId, tab, cursor, sortType, goodClassId)
                    return@withContext rest.copy(forum = forum ?: rest.forum, tbs = opened.page.tbs ?: rest.tbs)
                }
                val first = cursor.page == 1
                val data = remote.page(
                    forumName, forumId, frsTab, cursor.page, if (first) 1 else 2, sort, goodClassId,
                    commonInfo = commonInfo[forumName].orEmpty(), shownThreads = cursor.shown,
                )
                recent.keep(RecentResponses.ForumPage, data.encode())
                frsPage(forumName, tab, cursor, data, emptyList())
            }

            is ForumCursor.FrsThreads -> {
                // The first batch, then all the rest at once
                val batch = if (cursor.firstBatch) cursor.ids.take(THREAD_BATCH) else cursor.ids
                val rest = cursor.ids.drop(batch.size)
                val data = remote.threadList(
                    forumId, forumName, cursor.page, sortType = sort, threadIds = batch, good = tab == ForumTab.Good,
                    commonInfo = commonInfo[forumName].orEmpty(), shownThreads = cursor.shown,
                )
                recent.keep(RecentResponses.ForumThreads, data.encode())
                val threads = data.page_data?.feed_list.orEmpty().toThreadSummaries(emoticons)
                    .ifEmpty { data.thread_list.toSummaries(data.user_list) }
                    .let(::filter)
                val shown = cursor.shown + threads.size
                val next = when {
                    threads.isEmpty() && data.thread_list.isEmpty() -> null
                    rest.isNotEmpty() -> cursor.copy(ids = rest, shown = shown, firstBatch = false)
                    cursor.pageHasMore -> ForumCursor.FrsPage(cursor.page + 1, shown)
                    else -> null
                }
                ForumThreadsPage(Page(threads, next))
            }

            is ForumCursor.Custom -> {
                val custom = tab as ForumTab.Custom
                val data = remote.generalTabList(
                    forumId, forumName, custom.id, custom.name, custom.type, cursor.page,
                    commonInfo = commonInfo[forumName].orEmpty(), shownThreads = cursor.shown,
                )
                recent.keep(RecentResponses.ForumTab, data.encode())
                val threads = data.page_data?.feed_list.orEmpty().toThreadSummaries(emoticons)
                    .ifEmpty { data.general_list.toSummaries(data.user_list) }
                    .let(::filter)
                val next = if (data.has_more == 1 && threads.isNotEmpty()) ForumCursor.Custom(cursor.page + 1, cursor.shown + threads.size) else null
                ForumThreadsPage(Page(threads, next))
            }
        }
    }

    /** The forum's page as it opens, on the tab and in the order the forum picks. */
    private suspend fun open(forumName: String): Opened = coroutineScope {
        // As the official client opens a forum: quickly with the pinned threads, and in full,
        // which leaves them out; neither names a tab, so the forum opens on its own
        val quick = async { runCatching { remote.page(forumName, 0, null, 1, 1, -1, firstGet = FirstGet.Less) }.getOrNull() }
        val full = async { remote.page(forumName, 0, null, 1, 1, -1, firstGet = FirstGet.Normal) }
        val data = full.await()
        val less = quick.await()
        less?.let { recent.keep(RecentResponses.ForumPage, it.encode()) }
        recent.keep(RecentResponses.ForumPage, data.encode())
        val pinned = less?.page_data?.feed_list.orEmpty().filter { it.layout == TopThreadsLayout }
        val tab = data.page_data_tab_id.takeIf { it != 0 } ?: data.frs_tab_default
        Opened(tab, data.sort_type, frsPage(forumName, null, ForumCursor.FrsPage(1), data, pinned))
    }

    private class Opened(val tabId: Int, val sortType: Int, val page: ForumThreadsPage)

    /** A page of frs/page: its threads, pinned ones first, and where the next ones come from. */
    private fun frsPage(
        forumName: String,
        tab: ForumTab?,
        cursor: ForumCursor.FrsPage,
        data: FrsPageResponseData,
        pinned: List<LayoutFactory>,
    ): ForumThreadsPage {
        data.frs_common_info.takeIf { it.isNotEmpty() }?.let { commonInfo[forumName] = it }
        val threads = (pinned + data.page_data?.feed_list.orEmpty()).toThreadSummaries(emoticons)
            .ifEmpty { data.thread_list.toSummaries(data.user_list) }
            .let(::filter)
            .distinctBy { it.id }
        val shownIds = threads.mapTo(HashSet()) { it.id }
        val ids = data.thread_id_list.filter { it !in shownIds }
        val pageHasMore = data.page?.has_more == 1
        val shown = cursor.shown + threads.size
        val next = when {
            // Hot threads go by page alone
            tab != ForumTab.Hot && ids.isNotEmpty() -> ForumCursor.FrsThreads(cursor.page, ids, pageHasMore, shown)
            pageHasMore -> ForumCursor.FrsPage(cursor.page + 1, shown)
            else -> null
        }
        return ForumThreadsPage(
            threads = Page(threads, next),
            forum = if (cursor.page == 1) data.toForumDetail() else null,
            tbs = data.anti?.tbs?.takeIf { it.isNotEmpty() },
        )
    }

    suspend fun like(forum: ForumDetail, tbs: String?): ForumDetail = withContext(io) {
        val info = likeRemote.like(forum.id, forum.name, forum.hasHead, tbs).info
        followed.changed()
        forum.copy(
            liked = true,
            score = info?.curScore?.toIntOrNull() ?: forum.score,
            levelUpScore = info?.levelUpScore?.toIntOrNull() ?: forum.levelUpScore,
            level = info?.levelId?.toIntOrNull() ?: forum.level,
            levelName = info?.levelName ?: forum.levelName,
            memberCount = info?.memberSum?.toIntOrNull() ?: forum.memberCount,
        )
    }

    /** Follows the forum [name], known by [id], as from a list that has no more of it. */
    suspend fun like(id: Long, name: String) = withContext(io) {
        likeRemote.like(id, name, hasHead = false)
        followed.changed()
    }

    suspend fun unlike(forum: ForumDetail, tbs: String?): ForumDetail = withContext(io) {
        likeRemote.unfavolike(forum.id, forum.name, tbs)
        followed.changed()
        forum.copy(liked = false)
    }

    suspend fun signIn(forum: ForumDetail, tbs: String?): ForumSignIn = withContext(io) {
        val info = checkNotNull(signRemote.sign(forum.id, forum.name, tbs).userInfo)
        followed.changed()
        val bonus = info.signBonusPoint?.toIntOrNull() ?: 0
        val levelUpScore = info.levelUpScore?.toIntOrNull() ?: forum.levelUpScore
        val level = info.allLevelInfo.lastOrNull { (it.score.toIntOrNull() ?: 0) < levelUpScore }?.id?.toIntOrNull()
        ForumSignIn(
            forum = forum.copy(
                signedIn = info.isSignIn == "1",
                signedDays = info.contSignNum?.toIntOrNull() ?: forum.signedDays,
                score = forum.score + bonus,
                levelUpScore = levelUpScore,
                level = level ?: forum.level,
                levelName = info.levelName ?: forum.levelName,
            ),
            bonusPoints = bonus,
            rank = info.userSignRank?.toIntOrNull() ?: 0,
        )
    }

    /**
     * Threads as lists of the old kind give them: their authors taken from [users], since a
     * thread's own author holds little more than the id, and live streams left out.
     */
    private fun List<ThreadInfo>.toSummaries(users: List<User>): List<ThreadSummary> = filter { it.ala_info == null }
        .map { thread -> thread.copy(author = users.find { it.id == thread.authorId } ?: thread.author).toThreadSummary(emoticons) }

    /** Leaves out threads with videos and Tieba's own events when the user hides them. */
    private fun filter(threads: List<ThreadSummary>): List<ThreadSummary> {
        val hideVideos = videoFilter.hidesVideos()
        val hideActivities = activityFilter.hidesActivities()
        return threads.filter { !(hideVideos && it.video != null) && !(hideActivities && it.isActivity) }
    }

    private companion object {
        /** How many of the ids frs/page lists one frs/threadlist request loads. */
        const val THREAD_BATCH = 30
    }
}

/** The forum page's own tab for [this], by the ids and types it lists its tabs under. */
private fun ForumTab.frsTab(): FrsTab? = when (this) {
    ForumTab.Good -> FrsTab(GoodTabId, FrsTab.GoodType, "精华")
    ForumTab.Latest -> FrsTab(LatestTabId, LatestTabType, "最新")
    ForumTab.Hot -> FrsTab(HotTabId, HotTabType, "热门")
    is ForumTab.Custom -> FrsTab(id, type, name, general = true)
}

private const val GoodTabId = 301
private const val LatestTabId = 503
private const val LatestTabType = 14
private const val HotTabId = 1
private const val HotTabType = 13
private const val TopThreadsLayout = "frs_top_thread_card"

private fun FrsPageResponseData.toForumDetail(): ForumDetail? {
    val forum = forum ?: return null
    val signUser = forum.sign_in_info?.user_info
    val custom = nav_tab_info?.tab.orEmpty()
        .filter { it.isGeneralTab == 1 && it.tabType == CUSTOM_TAB_TYPE }
        .map { ForumTab.Custom(it.tabId, it.tabName, it.tabType) }
    return ForumDetail(
        id = forum.id,
        name = forum.name,
        avatar = forum.avatar,
        slogan = forum.slogan,
        memberCount = forum.member_num,
        threadCount = forum.thread_num,
        postCount = forum.post_num,
        liked = forum.is_like == 1,
        level = forum.user_level,
        levelName = forum.level_name,
        score = forum.cur_score,
        levelUpScore = forum.levelup_score,
        signedIn = signUser?.is_sign_in == 1,
        signedDays = signUser?.cont_sign_num ?: 0,
        todaySignCount = forum.sign_in_info?.forum_info?.current_rank_info?.sign_count ?: 0,
        hasHead = forum.has_frs_star == 1 || activity_head?.head_imgs?.isNotEmpty() == true,
        ruleTitle = forum_rule?.takeIf { it.has_forum_rule == 1 }?.title?.takeIf { it.isNotEmpty() },
        goodClasses = forum.good_classify.map { GoodClass(it.class_id, it.class_name) }.toImmutableList(),
        tabs = (ForumTab.Fixed + custom).toImmutableList(),
    )
}

/** The type of the tabs a forum's moderators set up, as nav tabs list them. */
private const val CUSTOM_TAB_TYPE = 15

private fun ForumRuleDetailResponseData.toForumRuleDetail(emoticons: EmoticonRegistry): ForumRuleDetail {
    val bazhu = checkNotNull(bazhu)
    return ForumRuleDetail(
        title = title,
        publishTime = publish_time,
        preface = preface,
        author = ForumRuleAuthor(
            name = bazhu.user_name,
            nameShow = bazhu.name_show,
            portrait = bazhu.portrait,
        ),
        rules = rules.map { ForumRuleItem(it.title, it.content.toPostContent(emoticons)) }.toImmutableList(),
    )
}
