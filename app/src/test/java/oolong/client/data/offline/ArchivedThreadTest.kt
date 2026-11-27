package oolong.client.data.offline

import oolong.client.data.thread.ThreadCursor
import oolong.client.data.thread.ThreadSort
import oolong.core.network.protos.Page
import oolong.core.network.protos.PbContent
import oolong.core.network.protos.Post
import oolong.core.network.protos.SubPost
import oolong.core.network.protos.SubPostList
import oolong.core.network.protos.ThreadInfo
import oolong.core.network.protos.User
import oolong.core.network.protos.pbPage.PbPageResponseData
import kotlin.test.Test
import kotlin.test.assertEquals

class ArchivedThreadTest {
    private val author = User(id = 1, portrait = "tb.1.author")

    private fun post(floor: Int, authorId: Long = 2) = Post(id = 1000L + floor, floor = floor, author_id = authorId)

    /** Floors 2..[last] over pages of 15 as saved, the author writing every third. */
    private fun saved(last: Int): List<PbPageResponseData> {
        val floors = listOf(post(1, authorId = 1)) + (2..last).map { post(it, authorId = if (it % 3 == 0) 1 else 2) }
        return floors.chunked(15).mapIndexed { index, posts ->
            PbPageResponseData(
                thread = ThreadInfo(id = 9, author = author),
                page = Page(current_page = index + 1),
                post_list = posts,
                user_list = listOf(author, User(id = 2L + index)),
            )
        }
    }

    private fun floors(page: PbPageResponseData) = page.post_list.filter { it.floor > 1 }.map { it.floor }

    @Test
    fun `pages are cut again from every saved floor`() {
        val archive = ArchivedThread(saved(40))

        val first = archive.page(ThreadSort.OldestFirst, seeLz = false, ThreadCursor.Number(0))
        val third = archive.page(ThreadSort.OldestFirst, seeLz = false, ThreadCursor.Number(3))

        assertEquals((2..16).toList(), floors(first))
        assertEquals(1, first.post_list.first().floor)
        assertEquals(listOf(32, 33, 34, 35, 36, 37, 38, 39, 40), floors(third))
        assertEquals(3, first.page?.total_page)
        assertEquals(1, first.page?.has_more)
        assertEquals(0, first.page?.has_prev)
        assertEquals(0, third.page?.has_more)
        assertEquals(4, first.user_list.size)
    }

    @Test
    fun `only the author's floors when asked`() {
        val page = ArchivedThread(saved(40)).page(ThreadSort.OldestFirst, seeLz = true, ThreadCursor.Number(1))

        assertEquals((2..40).filter { it % 3 == 0 }, floors(page))
        assertEquals(1, page.page?.total_page)
    }

    @Test
    fun `an anchor opens its page, and before it the page ahead`() {
        val archive = ArchivedThread(saved(40))

        assertEquals(2, archive.page(ThreadSort.OldestFirst, false, ThreadCursor.Anchor(1020)).page?.current_page)
        val before = archive.page(ThreadSort.OldestFirst, false, ThreadCursor.Before(postId = 1017, page = 0))
        assertEquals((2..16).toList(), floors(before))
    }

    @Test
    fun `newest first runs backwards and finds nothing newer`() {
        val archive = ArchivedThread(saved(40))

        val first = archive.page(ThreadSort.NewestFirst, false, ThreadCursor.Number(0))
        assertEquals((40 downTo 26).toList(), floors(first))
        // The next page starts after the last floor shown
        val next = archive.page(ThreadSort.NewestFirst, false, ThreadCursor.Anchor(1026))
        assertEquals((25 downTo 11).toList(), floors(next))
        assertEquals(emptyList(), floors(archive.page(ThreadSort.NewestFirst, false, ThreadCursor.Before(1040, 0))))
        assertEquals(emptyList(), floors(archive.page(ThreadSort.OldestFirst, false, ThreadCursor.After(1040))))
    }

    @Test
    fun `pictures of floors and replies, and avatars, each once`() {
        val page = PbPageResponseData(
            post_list = listOf(
                Post(
                    floor = 2,
                    content = listOf(
                        PbContent(type = 3, bigCdnSrc = "big", src = "small"),
                        PbContent(type = 0, text = "words"),
                        PbContent(type = 5, src = "cover"),
                    ),
                    sub_post_list = SubPost(sub_post_list = listOf(SubPostList(content = listOf(PbContent(type = 3, bigSrc = "reply"))))),
                ),
            ),
            user_list = listOf(User(portrait = "p1"), User(portrait = "")),
            thread = ThreadInfo(author = User(portrait = "p1")),
        )

        assertEquals(listOf("big", "cover", "reply", "avatar/p1"), page.pictureUrls { "avatar/$it" })
    }

    @Test
    fun `lists the emoticons posts and their replies show, each once`() {
        val page = PbPageResponseData(
            post_list = listOf(
                Post(
                    id = 1,
                    content = listOf(
                        PbContent(type = 2, text = "image_emoticon25", c = "滑稽"),
                        PbContent(type = 0, text = "words"),
                        PbContent(type = 2, text = "image_emoticon25", c = "滑稽"),
                    ),
                    sub_post_list = SubPost(sub_post_list = listOf(SubPostList(content = listOf(PbContent(type = 2, text = "image_emoticon130", c = "喝彩"))))),
                ),
            ),
        )

        assertEquals(setOf("image_emoticon25", "image_emoticon130"), page.emoticonIds())
    }
}
