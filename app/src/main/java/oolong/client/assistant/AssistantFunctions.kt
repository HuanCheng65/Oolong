package oolong.client.assistant

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionLimitExceededException
import androidx.appfunctions.AppFunctionPermissionRequiredException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import oolong.client.MainActivity
import oolong.client.data.explore.ExploreRepository
import oolong.client.data.forum.FollowedForums
import oolong.client.data.forum.ForumCursor
import oolong.client.data.forum.ForumRepository
import oolong.client.data.history.HistoryOwner
import oolong.client.data.search.SearchRepository
import oolong.client.data.thread.ThreadCursor
import oolong.client.data.thread.ThreadQuery
import oolong.client.data.thread.ThreadRepository
import oolong.client.model.ForumTab
import oolong.client.model.HotTopic
import oolong.client.model.PostContent
import oolong.client.model.RankReason
import oolong.client.model.SearchOrder
import oolong.client.model.ThreadMeasure
import oolong.client.model.ThreadSummary
import oolong.client.model.User
import javax.inject.Inject

/**
 * What the app lets the system's assistant do for the user: read Tieba (百度贴吧) — the hot
 * topics and threads, a search, a thread's posts, a forum's hot threads, the forums the user
 * follows — and open a page in the app. Nothing here writes: no replying, following, liking or
 * signing in, so an assistant misreading a request can't act on the user's account.
 *
 * Reads are limited to a modest rate, as an assistant may call them in a burst and the server
 * watches for that.
 */
@RequiresApi(36)
@AndroidEntryPoint
@AppFunctionServiceEntryPoint(
    serviceName = "TiebaAppFunctionService",
    appFunctionXmlFileName = "tieba_app_functions",
)
abstract class AssistantFunctions : AppFunctionService() {
    @Inject internal lateinit var explore: ExploreRepository

    @Inject internal lateinit var threads: ThreadRepository

    @Inject internal lateinit var search: SearchRepository

    @Inject internal lateinit var forums: ForumRepository

    @Inject internal lateinit var followedForums: FollowedForums

    @Inject internal lateinit var account: HistoryOwner

    /**
     * Lists what Tieba (百度贴吧) is talking about right now: the hot topics (热议话题), most
     * discussed first.
     *
     * @param limit How many topics to return, 1 to 30; 10 when not given.
     * @return The hot topics in ranking order.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getHotTopics(limit: Int? = null): List<AssistantTopic> {
        RateLimit.take()
        val count = limit.orDefault(10, 1..30)
        return explore.topics().take(count).mapIndexed { index, topic ->
            AssistantTopic(
                rank = index + 1,
                name = topic.name,
                description = topic.description,
                discussCount = topic.discussCount,
                isNew = topic.tag == HotTopic.Tag.New,
            )
        }
    }

    /**
     * Lists the threads of the last day across Tieba (帖子榜) that the most people read, or with
     * [byLikes], that got the most likes.
     *
     * @param byLikes Rank by likes rather than by readers; false when not given.
     * @param limit How many threads to return, 1 to 20; 10 when not given.
     * @return The ranked threads, top first.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getTopThreads(byLikes: Boolean? = null, limit: Int? = null): List<AssistantThread> {
        RateLimit.take()
        val measure = if (byLikes == true) ThreadMeasure.MostLiked else ThreadMeasure.MostRead
        return explore.threadRanking(measure).take(limit.orDefault(10, 1..20)).map { ranked ->
            ranked.thread.toAssistantThread(readers = (ranked.reason as? RankReason.Readers)?.count)
        }
    }

    /**
     * Searches Tieba's threads and replies for [keyword], as the search page of the app does.
     * Useful to find people's experience and opinions on a product, game, place or event; to look
     * in one forum, put the forum's name in the keyword.
     *
     * @param keyword The words to search for.
     * @param newestFirst Order by time, newest first, rather than by relevance; false when not given.
     * @param page Which page of results, from 1; 1 when not given.
     * @return The matching posts, each with the thread it is in.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun searchThreads(keyword: String, newestFirst: Boolean? = null, page: Int? = null): List<AssistantSearchResult> {
        if (keyword.isBlank()) throw AppFunctionInvalidArgumentException("The keyword is empty.")
        RateLimit.take()
        val order = if (newestFirst == true) SearchOrder.Newest else SearchOrder.Relevant
        return search.threads(keyword.trim(), order, page.orDefault(1, 1..50)).items.map { post ->
            AssistantSearchResult(
                threadId = post.threadId,
                title = post.title,
                text = post.content.take(MAX_TEXT),
                forumName = post.forumName,
                author = post.author.name,
                replyCount = post.replyCount,
                postedAt = post.time,
                url = threadUrl(post.threadId),
            )
        }
    }

    /**
     * Reads a thread: its first post and a page of its replies (楼层), each as text. Pictures,
     * videos and voice are named in brackets rather than included.
     *
     * @param threadId The thread's id, as other functions return it or as in tieba.baidu.com/p/<id>.
     * @param page Which page of replies, from 1; 1 when not given.
     * @param onlyAuthor Only the posts of the thread's author (只看楼主); false when not given.
     * @return The thread with that page of its posts.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun readThread(threadId: Long, page: Int? = null, onlyAuthor: Boolean? = null): AssistantThreadContent {
        if (threadId <= 0) throw AppFunctionInvalidArgumentException("No thread has the id $threadId.")
        RateLimit.take()
        val pageNumber = page.orDefault(1, 1..10_000)
        val loaded = runCatching {
            threads.page(ThreadQuery(threadId, seeLz = onlyAuthor == true), ThreadCursor.Number(pageNumber))
        }.getOrElse { throw AppFunctionElementNotFoundException("The thread $threadId couldn't be read: ${it.message}") }
        val thread = loaded.thread
        return AssistantThreadContent(
            thread = thread.summary.toAssistantThread(readers = null),
            firstPost = loaded.firstPost?.takeIf { pageNumber == 1 }?.let { AssistantPost(1, it.author.displayName, it.content.asText(), it.time) },
            posts = loaded.posts.items.map { AssistantPost(it.floor, it.author.displayName, it.content.asText(), it.time) },
            page = loaded.currentPage,
            totalPages = loaded.totalPage,
        )
    }

    /**
     * Lists a forum's (吧) hot threads, as its 热门 tab shows them.
     *
     * @param forumName The forum's name, with or without the 吧 at its end.
     * @param limit How many threads to return, 1 to 30; 10 when not given.
     * @return The forum's hot threads, pinned threads left out.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getForumHotThreads(forumName: String, limit: Int? = null): List<AssistantThread> {
        val name = forumName.trim().removeSuffix("吧")
        if (name.isEmpty()) throw AppFunctionInvalidArgumentException("The forum name is empty.")
        RateLimit.take()
        val page = runCatching {
            forums.threads(forumName = name, forumId = 0, tab = ForumTab.Hot, cursor = ForumCursor.FrsPage(1))
        }.getOrElse { throw AppFunctionElementNotFoundException("No forum named $name could be read: ${it.message}") }
        return page.threads.items.filter { it.isTop != true }.take(limit.orDefault(10, 1..30)).map { it.toAssistantThread(readers = null) }
    }

    /**
     * Lists the forums (吧) the user follows, with their level in each and whether they signed
     * in (签到) to it today. Needs the user to be logged in to the app.
     *
     * @return The followed forums.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getFollowedForums(): List<AssistantForum> {
        val uid = account.uid.first()
        if (uid.isEmpty()) throw AppFunctionPermissionRequiredException("The user isn't logged in to the app.")
        RateLimit.take()
        return followedForums.query.reload(uid).map { AssistantForum(it.name, it.level, it.signedIn) }
    }

    /**
     * Opens a page in the app for the user to see: a thread when [threadId] is given, else a
     * forum when [forumName] is, else a search for [keyword]. Give exactly one of them.
     *
     * @param threadId A thread to open.
     * @param forumName A forum (吧) to open.
     * @param keyword Words to search Tieba for.
     * @return What opens the page.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun openInApp(threadId: Long? = null, forumName: String? = null, keyword: String? = null): PendingIntent {
        val link = when {
            threadId != null && threadId > 0 -> "oolong://thread/$threadId"
            !forumName.isNullOrBlank() -> "oolong://forum/${Uri.encode(forumName.trim().removeSuffix("吧"))}"
            !keyword.isNullOrBlank() -> "oolong://search?keyword=${Uri.encode(keyword.trim())}"
            else -> throw AppFunctionInvalidArgumentException("Give a thread id, a forum name or a keyword.")
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link), this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(this, link.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun ThreadSummary.toAssistantThread(readers: Long?) = AssistantThread(
        threadId = id,
        title = title.ifBlank { abstract },
        summary = abstract.take(MAX_TEXT),
        forumName = forum.name,
        author = author.displayName,
        replyCount = replyCount,
        likeCount = agree.count,
        readers = readers,
        url = threadUrl(id),
    )

    private companion object {
        /** The most characters of text one post or summary carries. */
        const val MAX_TEXT = 2_000

        fun threadUrl(id: Long) = "https://tieba.baidu.com/p/$id"

        fun Int?.orDefault(default: Int, range: IntRange): Int = (this ?: default).coerceIn(range)

        val User.displayName: String get() = nameShow.ifEmpty { name }

        fun List<PostContent>.asText(): String = joinToString("") { content ->
            when (content) {
                is PostContent.Text -> content.spans.joinToString("") { it.text }
                is PostContent.Image -> "[图片]"
                is PostContent.Video -> "[视频]"
                is PostContent.Voice -> "[语音]"
            }
        }.trim().take(MAX_TEXT)
    }
}

/**
 * Turns away calls beyond a modest rate: an assistant may read a dozen threads to answer one
 * question, but a flood of requests from one account is what the server's checks look for.
 */
private object RateLimit {
    private const val CALLS = 30
    private const val WINDOW_MILLIS = 60_000L
    private val calls = ArrayDeque<Long>()

    @Synchronized
    fun take() {
        val now = System.currentTimeMillis()
        while (calls.isNotEmpty() && now - calls.first() > WINDOW_MILLIS) calls.removeFirst()
        if (calls.size >= CALLS) throw AppFunctionLimitExceededException("Too many requests; try again in a minute.")
        calls.addLast(now)
    }
}
