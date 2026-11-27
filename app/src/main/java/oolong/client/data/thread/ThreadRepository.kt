package oolong.client.data.thread

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import oolong.client.arch.IoDispatcher
import oolong.client.arch.Page
import oolong.client.arch.query.QueryClient
import oolong.client.data.offline.OfflineThreads
import oolong.client.model.Agree
import oolong.client.model.BawuType
import oolong.client.model.ForumSummary
import oolong.client.model.Post
import oolong.client.model.SubPost
import oolong.client.model.ThreadDetail
import oolong.client.model.User
import oolong.client.model.Viewer
import oolong.core.network.TiebaException
import oolong.core.network.TiebaUnknownException
import oolong.core.network.post.PostRemoteDataSource
import oolong.core.network.protos.PbContent
import oolong.core.network.protos.VideoInfo
import oolong.core.network.protos.pbFloor.PbFloorResponseData
import oolong.core.network.protos.pbPage.PbPageResponseData
import oolong.core.network.thread.ThreadRemoteDataSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import oolong.core.network.protos.Agree as ProtoAgree
import oolong.core.network.protos.Post as ProtoPost
import oolong.core.network.protos.SubPostList as ProtoSubPost
import oolong.core.network.protos.User as ProtoUser

enum class ThreadSort(val value: Int) {
    OldestFirst(0),
    NewestFirst(1),
    Hottest(2),
}

/** What the thread page shows: which thread, in which order, and whose posts. */
@Immutable
data class ThreadQuery(
    val threadId: Long,
    val sort: ThreadSort = ThreadSort.OldestFirst,
    val seeLz: Boolean = false,
    val forumId: Long? = null,
    /** Opened from the thread store, which the server is told about. */
    val fromStore: Boolean = false,
    /** Read from the copy saved to read offline, not from the server. */
    val offline: Boolean = false,
)

/** Where a page of a thread starts. */
@Immutable
sealed interface ThreadCursor {
    /**
     * A page by number. Page 0 leaves it to the server, which returns the first page in the
     * thread's order: the oldest posts, or the newest when newest first. (The last-page flag
     * only means something together with the total page count, so it isn't sent.)
     */
    data class Number(val page: Int) : ThreadCursor

    /**
     * The posts from [postId] on: the page containing it when oldest first, the posts older than
     * it when newest first.
     */
    data class Anchor(val postId: Long) : ThreadCursor

    /**
     * The posts before [postId], the first one loaded: the previous page when oldest first, the
     * newer posts when newest first. [page] is the page number to send along, 0 for none.
     */
    data class Before(val postId: Long, val page: Int) : ThreadCursor

    /** Posts newer than [postId], the last one loaded, once the end has been reached. */
    data class After(val postId: Long) : ThreadCursor

    companion object {
        /**
         * Posts newer than [newestPostId], the newest loaded when newest first, with the page
         * after [maxPageSeen], the highest page loaded so far, while it isn't [lastPage].
         */
        fun newerThan(newestPostId: Long, maxPageSeen: Int, lastPage: Int): ThreadCursor =
            Before(newestPostId, page = if (maxPageSeen < lastPage) maxPageSeen + 1 else 0)

        /** Where a thread opened at [postId], or from its start when 0, begins. */
        fun first(postId: Long = 0): ThreadCursor = if (postId != 0L) Anchor(postId) else Number(0)
    }
}

@Immutable
data class ThreadPage(
    val thread: ThreadDetail,
    /** Shown above the replies, and never part of [posts]. */
    val firstPost: Post?,
    val posts: Page<ThreadCursor, Post>,
    val currentPage: Int,
    /** The number of pages as shown to the user. */
    val totalPage: Int,
    /** The last page number as the server counts pages, for loading newer posts newest first. */
    val lastPage: Int,
    val viewer: Viewer,
    /** The tbs to send with actions on this thread, or null to send the account's. */
    val tbs: String?,
)

/** Where a page of the replies inside a post starts. */
@Immutable
data class FloorCursor(
    val page: Int,
    /** A reply to load the page around, which the server then picks; 0 for none. */
    val subPostId: Long = 0,
)

/** A post with a page of the replies inside it. */
@Immutable
data class FloorPage(
    val post: Post,
    val threadAuthorId: Long,
    val forum: ForumSummary,
    val subPosts: Page<FloorCursor, SubPost>,
    /** How many replies the post has in all. */
    val totalCount: Int,
    /** The tbs to send with actions on these replies, or null to send the account's. */
    val tbs: String?,
)

/** The order of the replies inside a post, both the few shown under it and all of them. */
enum class SubPostSort(val value: String) {
    /** The hottest first, then the rest by time. */
    Hot("hot"),

    /** By time, earliest first. */
    Earliest("earliest"),
    ;

    companion object {
        fun of(value: String?): SubPostSort = entries.firstOrNull { it.value == value } ?: Hot
    }
}

/** Where the [SubPostSort] the user chose is kept. */
interface SubPostSortStore {
    var sort: SubPostSort

    /** The sort as it is now and then each time it changes, wherever it is changed. */
    val changes: Flow<SubPostSort>
}

/**
 * A page of a thread as its list loads it: the [page], or null when asking for the posts newer
 * than the last one found none, which the server answers with nothing at all, not even the thread.
 */
@Immutable
data class ThreadLoad(val page: ThreadPage?, val posts: Page<ThreadCursor, Post>)

@Singleton
class ThreadRepository @Inject constructor(
    private val remote: ThreadRemoteDataSource,
    private val postRemote: PostRemoteDataSource,
    private val emoticons: EmoticonRegistry,
    @IoDispatcher private val io: CoroutineDispatcher,
    client: QueryClient,
    private val subPostSort: SubPostSortStore? = null,
    private val offline: OfflineThreads? = null,
) {
    /**
     * The posts of threads as the thread page shows them, kept a little: a thread peeked at and
     * then opened, or opened again soon, shows what was loaded of it at once.
     */
    val posts = client.infiniteQuery<ThreadQuery, ThreadCursor, ThreadLoad, Post>(
        name = "thread",
        itemKey = Post::id,
        page = { it.posts },
        staleTime = 1.minutes,
        normalize = { post(it) },
    ) { query, cursor -> load(query, cursor) }

    /** Starts loading the start of [query] ahead of opening it. */
    fun prefetch(query: ThreadQuery) = posts.prefetch(query, ThreadCursor.first())

    /** Marks what is kept of thread [threadId], which changed, to be loaded again. */
    fun changed(threadId: Long) = posts.invalidate { it.threadId == threadId }

    suspend fun page(query: ThreadQuery, cursor: ThreadCursor): ThreadPage =
        fetch(query, cursor).toThreadPage(query.sort, emoticons)

    /** A page for the list of [query]; see [ThreadLoad] for when it has no [ThreadLoad.page]. */
    suspend fun load(query: ThreadQuery, cursor: ThreadCursor): ThreadLoad {
        val data = fetch(query, cursor)
        if (cursor is ThreadCursor.After && data.thread == null && data.post_list.isEmpty()) {
            return ThreadLoad(page = null, posts = Page(emptyList()))
        }
        val page = data.toThreadPage(query.sort, emoticons)
        return ThreadLoad(page, page.posts)
    }

    /**
     * The page from the saved copy when [ThreadQuery.offline]; otherwise from the server, or from
     * the saved copy when the server can't be reached and the thread was saved.
     */
    private suspend fun fetch(query: ThreadQuery, cursor: ThreadCursor): PbPageResponseData {
        if (query.offline) return offline?.page(query, cursor) ?: throw TiebaUnknownException
        return try {
            fetchRemote(query, cursor)
        } catch (e: IOException) {
            if (e is TiebaException) throw e
            offline?.page(query, cursor) ?: throw e
        }
    }

    private suspend fun fetchRemote(query: ThreadQuery, cursor: ThreadCursor): PbPageResponseData = withContext(io) {
        remote.page(
            threadId = query.threadId,
            page = when (cursor) {
                is ThreadCursor.Number -> cursor.page
                is ThreadCursor.Before -> cursor.page
                is ThreadCursor.Anchor, is ThreadCursor.After -> 0
            },
            postId = when (cursor) {
                is ThreadCursor.Number -> 0
                is ThreadCursor.Anchor -> cursor.postId
                is ThreadCursor.Before -> cursor.postId
                is ThreadCursor.After -> cursor.postId
            },
            sortType = query.sort.value,
            seeLz = query.seeLz,
            back = cursor is ThreadCursor.Before,
            lastPostId = (cursor as? ThreadCursor.After)?.postId ?: 0,
            forumId = query.forumId ?: 0,
            stType = if (query.fromStore) "store_thread" else "",
            mark = if (query.fromStore) 1 else 0,
            subPostsByTime = subPostSort?.sort == SubPostSort.Earliest,
        )
    }

    /** A page of the replies inside post [postId] of thread [threadId]. */
    suspend fun floor(threadId: Long, postId: Long, forumId: Long, cursor: FloorCursor, previewIds: List<Long> = emptyList()): FloorPage =
        withContext(io) {
            // Loading around a reply goes by time, so the pages before and after it are the replies
            // written before and after it
            val byTime = cursor.subPostId != 0L || subPostSort?.sort == SubPostSort.Earliest
            remote.floor(threadId, postId, forumId, cursor.page, cursor.subPostId, byTime, previewIds).toFloorPage(emoticons)
        }

    /** Deletes a post, or with [isSubPost] a reply inside one, from the sub-post page. */
    suspend fun deletePost(
        forum: ForumSummary,
        threadId: Long,
        postId: Long,
        ownPost: Boolean,
        isSubPost: Boolean,
        tbs: String?,
    ) = withContext(io) {
        remote.deletePost(forum.id, forum.name, threadId, postId, ownPost, isSubPost = isSubPost, tbs = tbs)
    }.also { changed(threadId) }

    suspend fun vote(thread: ThreadDetail, optionIds: Collection<Int>) = withContext(io) {
        postRemote.addPollPost(thread.id, thread.forum.id, optionIds.toList())
    }

    suspend fun addStore(threadId: Long, markPostId: Long) = withContext(io) {
        remote.addStore(threadId, markPostId)
    }

    suspend fun removeStore(threadId: Long, forumId: Long, tbs: String?) = withContext(io) {
        remote.removeStore(threadId, forumId, tbs)
    }

    suspend fun deletePost(
        thread: ThreadDetail,
        postId: Long,
        ownPost: Boolean,
        tbs: String?,
    ) = withContext(io) {
        remote.deletePost(thread.forum.id, thread.forum.name, thread.id, postId, ownPost, tbs = tbs)
    }.also { changed(thread.id) }

    suspend fun deleteThread(thread: ThreadDetail, ownThread: Boolean, tbs: String?) =
        withContext(io) {
            remote.deleteThread(thread.forum.id, thread.forum.name, thread.id, ownThread, tbs = tbs)
        }.also { changed(thread.id) }
}

internal fun PbPageResponseData.toThreadPage(sort: ThreadSort, emoticons: EmoticonRegistry): ThreadPage {
    val thread = thread ?: throw TiebaUnknownException
    val threadAuthor = thread.author ?: throw TiebaUnknownException
    val forum = forum ?: throw TiebaUnknownException
    val page = page ?: throw TiebaUnknownException
    val anti = anti ?: throw TiebaUnknownException
    val mapper = PostMapper(user_list, emoticons)
    val firstPost = post_list.firstOrNull { it.floor == 1 }?.let(mapper::post)
        ?: first_floor_post?.let { post ->
            mapper.post(
                post.copy(author = post.author ?: threadAuthor, author_id = threadAuthor.id),
                content = post.content.withPlayableVideo(thread.videoInfo),
            )
        }
    // Floor 0 holds adverts dressed as replies
    val posts = post_list.filter { it.floor > 1 }.map(mapper::post)
    // A page of only adverts is not the end of the thread
    val hasReplies = post_list.any { it.floor != 1 }
    val hasMore = page.has_more != 0 && hasReplies
    val hasPrevious = page.has_prev != 0 && hasReplies

    return ThreadPage(
        thread = ThreadDetail(
            summary = thread.toThreadSummary(emoticons).let { summary ->
                summary.copy(
                    // The page's own thread may leave out what lists fill in
                    id = thread.id,
                    title = thread.title,
                    author = threadAuthor.toUser(),
                    forum = ForumSummary(id = forum.id, name = forum.name, avatar = forum.avatar),
                    agree = thread.agree.toAgree(),
                    firstPostId = thread.firstPostId.takeIf { it != 0L } ?: firstPost?.id ?: 0L,
                    firstPost = firstPost?.content ?: summary.firstPost,
                    // The page doesn't say whether the thread is pinned, only its forum's list does
                    isTop = null,
                )
            },
            storeMarkPostId = if (thread.collectStatus != 0) thread.collectMarkPid.toLongOrNull() ?: 0L else null,
            poll = thread.poll_info?.toPoll(),
        ),
        firstPost = firstPost,
        posts = Page(
            items = posts,
            next = when {
                !hasMore -> null
                sort == ThreadSort.NewestFirst -> ThreadCursor.Anchor(posts.last().id)
                else -> ThreadCursor.Number(page.current_page + 1)
            },
            prev = when {
                posts.isEmpty() -> null
                // New replies can arrive at any time, so there is always something to look for
                // Posts newer than the newest loaded, with the page after this one while there is one
                sort == ThreadSort.NewestFirst ->
                    ThreadCursor.newerThan(posts.first().id, page.current_page, page.total_page)
                !hasPrevious -> null
                else -> ThreadCursor.Before(posts.first().id, page.current_page - 1)
            },
        ),
        currentPage = page.current_page,
        totalPage = page.new_total_page,
        lastPage = page.total_page,
        viewer = Viewer(user?.takeIf { it.is_login == 1 }?.toUser()),
        tbs = anti.tbs.ifEmpty { null },
    )
}

internal fun PbFloorResponseData.toFloorPage(emoticons: EmoticonRegistry): FloorPage {
    val post = post ?: throw TiebaUnknownException
    val page = page ?: throw TiebaUnknownException
    val forum = forum ?: throw TiebaUnknownException
    val mapper = PostMapper(emptyList(), emoticons)
    val subPosts = subpost_list.map(mapper::subPost)
    return FloorPage(
        post = mapper.post(post),
        threadAuthorId = thread?.author?.id ?: 0L,
        forum = ForumSummary(id = forum.id, name = forum.name, avatar = forum.avatar),
        subPosts = Page(
            items = subPosts,
            next = FloorCursor(page.current_page + 1)
                .takeIf { page.current_page < page.total_page && subPosts.isNotEmpty() },
            prev = FloorCursor(page.current_page - 1).takeIf { page.current_page > 1 && subPosts.isNotEmpty() },
        ),
        totalCount = page.total_count,
        tbs = anti?.tbs?.ifEmpty { null },
    )
}

/** Maps posts and their replies, taking authors missing from them from [users]. */
internal class PostMapper(users: List<ProtoUser>, private val emoticons: EmoticonRegistry) {
    private val usersById = users.associateBy { it.id }

    private fun author(author: ProtoUser?, authorId: Long): User {
        val id = authorId.takeIf { it != 0L } ?: author?.id ?: 0L
        return (author ?: usersById[id])?.toUser() ?: User(id = id, name = "", nameShow = "", portrait = "")
    }

    fun subPost(subPost: ProtoSubPost) = SubPost(
        id = subPost.id,
        author = author(subPost.author, subPost.author_id),
        content = subPost.content.withoutLeadingColon().toPostContent(emoticons),
        time = subPost.time.toLong(),
        agree = subPost.agree.toAgree(),
    )

    fun post(post: ProtoPost) = post(post, post.content)

    fun post(post: ProtoPost, content: List<PbContent>) = Post(
        id = post.id,
        floor = post.floor,
        title = if (post.floor <= 1 && post.is_ntitle != 1) post.title else "",
        time = post.time.toLong(),
        author = author(post.author, post.author_id),
        content = content.toPostContent(emoticons),
        agree = post.agree.toAgree(),
        subPosts = post.sub_post_list?.sub_post_list.orEmpty().map(::subPost).toImmutableList(),
        subPostCount = post.sub_post_number,
    )
}

/**
 * A reply's content without the " : " some versions of the page start it with, after where the
 * author's name went, nor the spaces before 「回复」.
 */
private fun List<PbContent>.withoutLeadingColon(): List<PbContent> {
    val rest = if (firstOrNull()?.let { it.type == 0 && it.text.trim() == ":" } == true) drop(1) else this
    val first = rest.firstOrNull()?.takeIf { it.type == 0 } ?: return rest
    return listOf(first.copy(text = first.text.trimStart())) + rest.drop(1)
}

internal fun ProtoUser.toUser() = User(
    id = id,
    name = name,
    nameShow = nameShow,
    portrait = portrait,
    level = level_id,
    bawuType = when {
        is_bawu != 1 -> null
        bawu_type == "manager" -> BawuType.Manager
        else -> BawuType.Assist
    },
    ipLocation = ip_address,
)

private fun ProtoAgree?.toAgree() = Agree(count = this?.diffAgreeNum ?: 0L, agreed = this?.hasAgree == 1)

/**
 * When the page is requested from a specific post, first_floor_post links the raw upload of the
 * thread video, which can't be played; only thread.video_info carries the transcoded, signed URL.
 */
internal fun List<PbContent>.withPlayableVideo(videoInfo: VideoInfo?): List<PbContent> {
    if (videoInfo == null || videoInfo.videoUrl.isBlank() || videoInfo.videoMD5.isBlank()) return this
    return map {
        if (it.type == 5 && it.link != videoInfo.videoUrl && it.link.contains(videoInfo.videoMD5)) {
            it.copy(
                link = videoInfo.videoUrl,
                src = videoInfo.thumbnailUrl.ifBlank { it.src },
                duringTime = it.duringTime.takeIf { d -> d > 0 } ?: videoInfo.videoDuration,
            )
        } else {
            it
        }
    }
}
