package oolong.client.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** A forum found by a search. */
@Immutable
data class SearchedForum(
    val id: Long,
    val name: String,
    val nameShow: String,
    val avatar: String,
    val slogan: String,
    /** How many follow the forum; 0 where the search leaves it out. */
    val memberCount: Long = 0,
)

/** A user found by a search. */
@Immutable
data class SearchedUser(
    val id: Long,
    val name: String,
    val nameShow: String,
    val portrait: String,
    val intro: String,
)

/** What a search for forums or users found: the one named exactly as searched, if any, and the rest. */
@Immutable
data class SearchMatches<T>(
    val exact: T? = null,
    val fuzzy: ImmutableList<T> = persistentListOf(),
)

/** The author of a thread or reply found by a search; [id] is 0 when the results leave it out. */
@Immutable
data class SearchAuthor(val id: Long, val name: String, val nameShow: String, val portrait: String)

/** A thread, or a reply in one, that a search found. */
@Immutable
data class SearchedPost(
    val threadId: Long,
    val postId: Long,
    /** For a reply inside a floor, its own id; 0 otherwise. */
    val subPostId: Long = 0,
    val title: String,
    val content: String,
    /** When it was posted, in Unix seconds. */
    val time: Long,
    val author: SearchAuthor,
    val forumName: String,
    val forumAvatar: String,
    val replyCount: Int,
    val agreeCount: Int,
    val shareCount: Long,
    /** The pictures, as many as the result names. */
    val pictures: ImmutableList<String> = persistentListOf(),
    /** How many media the post has, pictures and others. */
    val mediaCount: Int = pictures.size,
    /** For a matching reply, the thread it is in. */
    val thread: QuotedPost? = null,
    /** For a matching reply, the post it quotes. */
    val quoted: QuotedPost? = null,
    /** For a thread found by a reply in it rather than by itself, that reply. */
    val matchedReply: QuotedPost? = null,
) {
    /** Tells results apart: a thread, a floor or a reply inside one. */
    val key: String get() = "${threadId}_${postId}_$subPostId"
}

/** A post that a search result refers to. */
@Immutable
data class QuotedPost(val threadId: Long, val postId: Long, val title: String, val content: String, val author: SearchAuthor)

/** How thread search results are ordered across Tieba. */
enum class SearchOrder { Newest, Oldest, Relevant }

/** How thread search results are ordered inside a forum. */
enum class ForumSearchOrder { Newest, Relevant }

/** A keyword the user searched for, across Tieba or in [forumName]. */
@Immutable
data class SearchHistoryItem(val id: Long, val keyword: String, val forumName: String? = null)
