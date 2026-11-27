package oolong.client.data.offline

import oolong.client.data.thread.ThreadCursor
import oolong.client.data.thread.ThreadSort
import oolong.core.network.protos.Page
import oolong.core.network.protos.PbContent
import oolong.core.network.protos.Post
import oolong.core.network.protos.pbPage.PbPageResponseData

/**
 * A saved thread, read back as the server would page it: the saved pages' floors gathered, picked
 * and ordered as asked, then cut into pages again. Pages saved with every floor can so be read with
 * only the author's, or newest first, without anything else saved.
 */
internal class ArchivedThread(pages: List<PbPageResponseData>) {
    private val base: PbPageResponseData = pages.first()
    private val users = pages.flatMap { it.user_list }.distinctBy { it.id }
    private val firstFloor: Post? = pages.firstNotNullOfOrNull { page -> page.post_list.firstOrNull { it.floor == 1 } }
    private val floors: List<Post> = pages.flatMap { it.post_list }.filter { it.floor > 1 }.distinctBy { it.id }.sortedBy { it.floor }

    /** How many floors were saved, the first not counted. */
    val floorCount: Int get() = floors.size

    /** The page [cursor] asks for, in [sort] and with only the author's floors when [seeLz]. */
    fun page(sort: ThreadSort, seeLz: Boolean, cursor: ThreadCursor): PbPageResponseData {
        val authorId = base.thread?.author?.id ?: firstFloor?.author_id
        val picked = floors.filter { !seeLz || it.author_id == authorId }
        val ordered = if (sort == ThreadSort.NewestFirst) picked.asReversed() else picked
        val chunks = ordered.chunked(PageSize)
        val total = chunks.size.coerceAtLeast(1)
        val number = numberOf(cursor, sort, ordered, total)
        val posts = if (number == null) emptyList() else chunks.getOrElse(number - 1) { emptyList() }
        val current = number ?: 1
        return base.copy(
            post_list = listOfNotNull(firstFloor) + posts,
            user_list = users,
            page = (base.page ?: Page()).copy(
                current_page = current,
                total_page = total,
                new_total_page = total,
                has_more = if (number != null && current < total) 1 else 0,
                has_prev = if (number != null && current > 1) 1 else 0,
            ),
        )
    }

    /** The page [cursor] lands on, from 1; null for nothing, as asking for newer floors finds none. */
    private fun numberOf(cursor: ThreadCursor, sort: ThreadSort, ordered: List<Post>, total: Int): Int? = when (cursor) {
        is ThreadCursor.Number -> cursor.page.coerceIn(1, total)
        is ThreadCursor.Anchor -> {
            val index = ordered.indexOfFirst { it.id == cursor.postId }
            when {
                index < 0 -> 1
                // Newest first, the floors after a page's last one start the next page
                sort == ThreadSort.NewestFirst && index % PageSize == PageSize - 1 -> (index / PageSize + 2).takeIf { it <= total }
                else -> index / PageSize + 1
            }
        }

        is ThreadCursor.Before -> when {
            sort == ThreadSort.NewestFirst -> null
            cursor.page > 0 -> cursor.page.coerceAtMost(total)
            else -> ordered.indexOfFirst { it.id == cursor.postId }.takeIf { it >= 0 }?.let { it / PageSize }?.takeIf { it >= 1 }
        }

        is ThreadCursor.After -> null
    }

    companion object {
        /** Floors to a page, as the thread page asks for them. */
        const val PageSize = 15
    }
}

/** The pictures on [this] page worth keeping: in the floors and their replies, and the posters' avatars. */
internal fun PbPageResponseData.pictureUrls(avatarUrl: (String) -> String): List<String> {
    val contents = (post_list + listOfNotNull(first_floor_post)).flatMap { post ->
        post.content + post.sub_post_list?.sub_post_list.orEmpty().flatMap { it.content }
    }
    val pictures = contents.mapNotNull(PbContent::pictureUrl)
    val avatars = (user_list.map { it.portrait } + listOfNotNull(thread?.author?.portrait)).filter { it.isNotEmpty() }.map(avatarUrl)
    return (pictures + avatars).distinct()
}

/** The ids of the emoticons [this] page's posts and their replies show. */
internal fun PbPageResponseData.emoticonIds(): Set<String> =
    (post_list + listOfNotNull(first_floor_post)).flatMap { post ->
        post.content + post.sub_post_list?.sub_post_list.orEmpty().flatMap { it.content }
    }.filter { it.type == EmoticonType && it.text.isNotBlank() }.mapTo(LinkedHashSet()) { it.text }

private const val EmoticonType = 2

/** The copy of a picture the thread shows; a video's cover. */
private fun PbContent.pictureUrl(): String? = when (type) {
    3 -> listOf(bigCdnSrc, bigSrc, cdnSrc, src).firstOrNull { it.isNotBlank() }
    5, 20 -> src.takeIf { it.isNotBlank() }
    else -> null
}
