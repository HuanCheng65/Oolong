package oolong.client.ui.page.photoview

import oolong.client.model.ThreadPicture
import kotlin.test.Test
import kotlin.test.assertEquals

class PageKeysTest {
    private fun picture(id: String, post: Long) = ThreadPicture(picId = id, originUrl = "", url = null, overallIndex = 0, postId = post)

    @Test
    fun `a picture shown twice in one post gets a page of its own each time`() {
        val keys = pageKeys(listOf(picture("a", 1), picture("b", 1), picture("a", 1), picture("a", 2), picture("a", 1)))
        assertEquals(listOf("a_1", "b_1", "a_1_2", "a_2", "a_1_3"), keys)
    }
}
