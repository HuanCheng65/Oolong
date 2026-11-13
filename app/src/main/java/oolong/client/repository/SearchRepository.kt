package oolong.client.repository

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import oolong.client.Incognito
import oolong.client.arch.IoDispatcher
import oolong.client.arch.Page
import oolong.client.database.SearchHistoryDao
import oolong.client.model.ForumSearchOrder
import oolong.client.model.QuotedPost
import oolong.client.model.SearchAuthor
import oolong.client.model.SearchHistoryItem
import oolong.client.model.SearchMatches
import oolong.client.model.SearchOrder
import oolong.client.model.SearchedForum
import oolong.client.model.SearchedPost
import oolong.client.model.SearchedUser
import oolong.core.network.search.SearchForumsResponse
import oolong.core.network.search.SearchRemoteDataSource
import oolong.core.network.search.SearchSort
import oolong.core.network.search.SearchThreadsResponse
import oolong.core.network.search.SearchUsersResponse
import javax.inject.Inject

/** Searching Tieba and inside forums, and the keywords searched for before. */
class SearchRepository @Inject constructor(
    private val remote: SearchRemoteDataSource,
    private val historyDao: SearchHistoryDao,
    private val owner: HistoryOwner,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** The keywords the current account searched for. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val history: Flow<List<SearchHistoryItem>> = owner.uid.flatMapLatest { uid ->
        historyDao.allFlow(uid).map { entries -> entries.map { SearchHistoryItem(it.id, it.content) } }
    }

    /** The keywords the current account searched for in [forumName]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun forumHistory(forumName: String): Flow<List<SearchHistoryItem>> = owner.uid.flatMapLatest { uid ->
        historyDao.inForumFlow(uid, forumName).map { entries -> entries.map { SearchHistoryItem(it.id, it.content, it.forumName) } }
    }

    suspend fun suggestions(keyword: String): List<String> = withContext(io) { remote.suggestions(keyword) }

    /** Remembers [keyword], or moves it to the top if it was searched for before; browsing incognito or with history off, nothing. */
    suspend fun remember(keyword: String, forumName: String? = null) = withContext(io) {
        if (!Incognito.recording) return@withContext
        val now = System.currentTimeMillis()
        val uid = owner.uid.value
        if (forumName == null) historyDao.add(uid, keyword, now) else historyDao.addInForum(uid, keyword, forumName, now)
    }

    suspend fun forget(item: SearchHistoryItem) = withContext(io) {
        if (item.forumName == null) historyDao.delete(item.id) else historyDao.deleteInForum(item.id)
    }

    suspend fun clearHistory() = withContext(io) { historyDao.clear(owner.uid.value) }

    suspend fun clearForumHistory(forumName: String) = withContext(io) { historyDao.clearInForum(owner.uid.value, forumName) }

    suspend fun forums(keyword: String): SearchMatches<SearchedForum> = withContext(io) {
        val data = remote.forums(keyword)
        SearchMatches(data.exactMatch?.toForum(), data.fuzzyForums.mapNotNull { it.toForum() }.toImmutableList())
    }

    suspend fun users(keyword: String): SearchMatches<SearchedUser> = withContext(io) {
        val data = remote.users(keyword)
        SearchMatches(data.exactMatch?.toUser(), data.fuzzyUsers.mapNotNull { it.toUser() }.toImmutableList())
    }

    /** A page (from 1) of threads and replies across Tieba. */
    suspend fun threads(keyword: String, order: SearchOrder, page: Int): Page<Int, SearchedPost> = withContext(io) {
        val sort = when (order) {
            SearchOrder.Newest -> SearchSort.NEWEST
            SearchOrder.Oldest -> SearchSort.OLDEST
            SearchOrder.Relevant -> SearchSort.RELEVANT
        }
        remote.threads(keyword, page, sort).toPage(page)
    }

    /** A page (from 1) of threads, and with [onlyThreads] false also replies, in one forum. */
    suspend fun threadsInForum(
        keyword: String,
        forumName: String,
        forumId: Long,
        order: ForumSearchOrder,
        onlyThreads: Boolean,
        page: Int,
    ): Page<Int, SearchedPost> = withContext(io) {
        val sort = when (order) {
            ForumSearchOrder.Newest -> SearchSort.IN_FORUM_NEWEST
            ForumSearchOrder.Relevant -> SearchSort.IN_FORUM_RELEVANT
        }
        remote.threadsInForum(keyword, forumName, forumId, page, sort, onlyThreads).toPage(page)
    }
}

private fun SearchThreadsResponse.Data.toPage(page: Int) =
    Page(postList.map { it.toPost() }, next = (page + 1).takeIf { hasMore == 1 })

private fun SearchForumsResponse.Forum.toForum(): SearchedForum? {
    val name = forumName?.takeIf { it.isNotEmpty() } ?: return null
    return SearchedForum(
        id = forumId ?: 0,
        name = name,
        nameShow = forumNameShow?.takeIf { it.isNotEmpty() } ?: name,
        avatar = avatar.orEmpty(),
        slogan = slogan.orEmpty(),
    )
}

private fun SearchUsersResponse.User.toUser(): SearchedUser? {
    val id = id?.toLongOrNull() ?: return null
    return SearchedUser(
        id = id,
        name = name.orEmpty(),
        nameShow = showNickname.orEmpty(),
        portrait = portrait.orEmpty(),
        intro = intro.orEmpty(),
    )
}

private fun SearchThreadsResponse.User.toAuthor() = SearchAuthor(
    id = userId.toLongOrNull() ?: 0,
    name = userName.orEmpty(),
    nameShow = showNickname.orEmpty(),
    portrait = portrait.orEmpty(),
)

private fun SearchThreadsResponse.Post.toPost(): SearchedPost {
    val pictures = media.filter { it.type == "pic" }.mapNotNull { it.bigPic ?: it.smallPic ?: it.waterPic }
    return SearchedPost(
        threadId = tid.toLongOrNull() ?: 0,
        postId = pid.toLongOrNull() ?: 0,
        subPostId = cid.toLongOrNull() ?: 0,
        title = title,
        content = content,
        time = time.toLongOrNull() ?: 0,
        author = user.toAuthor(),
        forumName = forumName,
        forumAvatar = forumInfo?.avatar.orEmpty(),
        replyCount = postNum.parseCount().toInt(),
        agreeCount = likeNum.parseCount().toInt(),
        shareCount = shareNum.parseCount(),
        pictures = pictures.toImmutableList(),
        mediaCount = media.size,
        thread = mainPost?.let { QuotedPost(it.tid, 0, it.title, it.content, it.user.toAuthor()) },
        quoted = postInfo?.let { QuotedPost(it.tid, it.pid, it.title, it.content, it.user.toAuthor()) },
    )
}

/** Search results may abbreviate counts, e.g. "1W" or "1.2万" for 10,000 and 12,000. */
internal fun String.parseCount(): Long {
    val value = trim()
    val multiplier = when (value.lastOrNull()) {
        'W', 'w', '万' -> 10_000L
        'K', 'k' -> 1_000L
        else -> 1L
    }
    val number = if (multiplier == 1L) value else value.dropLast(1)
    return number.toBigDecimalOrNull()?.multiply(multiplier.toBigDecimal())?.toLong() ?: 0L
}
