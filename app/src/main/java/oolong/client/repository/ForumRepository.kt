package oolong.client.repository

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import oolong.client.api.models.protos.ThreadInfo
import oolong.client.api.models.protos.User
import oolong.client.api.models.protos.forumRuleDetail.ForumRuleDetailResponseData
import oolong.client.api.models.protos.frsPage.FrsPageResponseData
import oolong.client.arch.IoDispatcher
import oolong.client.arch.Page
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
import oolong.core.network.forum.ForumRemoteDataSource
import oolong.core.network.forum.LikeRemoteDataSource
import oolong.core.network.sign.SignRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the user hides threads with videos from thread lists. */
fun interface VideoThreadFilter {
    fun hidesVideos(): Boolean
}

/** Where the next page of a forum's threads comes from. */
sealed interface ForumCursor {
    /** A page of frs/page, which lists its first threads and the ids of the rest. */
    data class FrsPage(val page: Int) : ForumCursor

    /** Threads of [ids] that frs/page [page] listed; after them, the next page if [pageHasMore]. */
    data class FrsThreads(val page: Int, val ids: List<Long>, val pageHasMore: Boolean) : ForumCursor

    /** A page of a moderators' tab, after the thread [lastThreadId]. */
    data class Custom(val page: Int, val lastThreadId: Long) : ForumCursor

    companion object {
        fun first(tab: ForumTab): ForumCursor = if (tab is ForumTab.Custom) Custom(1, 0) else FrsPage(1)
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

    /**
     * A page of [tab]'s threads from [cursor]. [forumId] may be 0 until frs/page has returned it,
     * which only a [ForumCursor.FrsPage] load needs.
     *
     * @param goodClassId the class of good threads to list, 0 for all of them
     */
    suspend fun threads(
        forumName: String,
        forumId: Long,
        tab: ForumTab,
        cursor: ForumCursor,
        sortType: Int = ForumSort.REPLY_TIME,
        goodClassId: Int = 0,
    ): ForumThreadsPage = withContext(io) {
        val sortType = tab.sortType(sortType)
        when (cursor) {
            is ForumCursor.FrsPage -> {
                val data = remote.page(
                    forumName = forumName,
                    page = cursor.page,
                    loadType = if (cursor.page == 1) 1 else 2,
                    sortType = sortType,
                    good = tab == ForumTab.Good,
                    goodClassId = goodClassId,
                )
                recent.keep(RecentResponses.ForumPage, data.encode())
                val pageHasMore = data.page?.has_more == 1
                val next = when {
                    data.thread_id_list.isNotEmpty() -> ForumCursor.FrsThreads(cursor.page, data.thread_id_list, pageHasMore)
                    pageHasMore -> ForumCursor.FrsPage(cursor.page + 1)
                    else -> null
                }
                ForumThreadsPage(
                    threads = Page(filter(data.thread_list, data.user_list), next),
                    forum = data.toForumDetail(),
                    tbs = data.anti?.tbs?.takeIf { it.isNotEmpty() },
                )
            }

            is ForumCursor.FrsThreads -> {
                val batch = cursor.ids.take(THREAD_BATCH)
                val rest = cursor.ids.drop(THREAD_BATCH)
                val data = remote.threadList(forumId, forumName, cursor.page, sortType, batch)
                recent.keep(RecentResponses.ForumThreads, data.encode())
                val next = when {
                    data.thread_list.isEmpty() -> null
                    rest.isNotEmpty() -> cursor.copy(ids = rest)
                    cursor.pageHasMore -> ForumCursor.FrsPage(cursor.page + 1)
                    else -> null
                }
                ForumThreadsPage(Page(filter(data.thread_list, data.user_list), next))
            }

            is ForumCursor.Custom -> {
                val custom = tab as ForumTab.Custom
                val data = remote.generalTabList(forumId, custom.id, custom.name, custom.type, cursor.page, cursor.lastThreadId)
                recent.keep(RecentResponses.ForumTab, data.encode())
                val threads = data.general_list
                val next = if (data.has_more == 1 && threads.isNotEmpty()) {
                    ForumCursor.Custom(cursor.page + 1, threads.last().id)
                } else null
                ForumThreadsPage(Page(filter(threads, data.user_list), next))
            }
        }
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
     * Takes the authors from [users], since a thread's own author holds little more than the id,
     * and drops live streams and, if the user hides them, videos.
     */
    private fun filter(threads: List<ThreadInfo>, users: List<User>): List<ThreadSummary> {
        val hideVideos = videoFilter.hidesVideos()
        return threads
            .filter { it.ala_info == null && !(hideVideos && it.videoInfo != null) }
            .map { thread -> thread.copy(author = users.find { it.id == thread.authorId } ?: thread.author).toThreadSummary(emoticons) }
    }

    private companion object {
        /** How many of the ids frs/page lists one frs/threadlist request loads. */
        const val THREAD_BATCH = 30
    }
}

/** Hot threads come in the order the server picks; only the latest threads take the user's order. */
private fun ForumTab.sortType(latestSortType: Int): Int? = when (this) {
    ForumTab.Good -> ForumSort.REPLY_TIME
    ForumTab.Hot -> null
    else -> latestSortType
}

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
