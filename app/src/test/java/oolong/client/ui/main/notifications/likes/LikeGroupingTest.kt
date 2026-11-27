package oolong.client.ui.main.notifications.likes

import oolong.client.model.Like
import oolong.client.model.User
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class LikeGroupingTest {
    private val zone = TimeZone.getTimeZone("GMT+08:00")

    // 2026-11-26 12:00 at UTC+8
    private val noon = 1_795_665_600L

    private fun like(id: Long, postId: Long, liker: Long, time: Long) = Like(
        id = id, threadId = 1, postId = postId, inPost = false, threadTitle = "", forumName = "",
        threadPicture = null, content = "", deleted = false, liker = User(liker, "u$liker", "", ""), time = time,
    )

    @Test
    fun `likes on one post on one day make one group where the newest is`() {
        val likes = listOf(
            like(6, postId = 10, liker = 1, time = noon + 300),
            like(5, postId = 20, liker = 2, time = noon + 200),
            like(4, postId = 10, liker = 3, time = noon + 100),
            like(3, postId = 10, liker = 1, time = noon),
        )

        val groups = likes.groupedByPostAndDay(zone)

        assertEquals(listOf(6L, 5L), groups.map { it.latest.id })
        assertEquals(listOf(1L, 3L), groups[0].likers.map { it.id })
    }

    @Test
    fun `the same post on another day is another group`() {
        val likes = listOf(
            like(2, postId = 10, liker = 1, time = noon),
            like(1, postId = 10, liker = 2, time = noon - 24 * 3600),
        )

        assertEquals(2, likes.groupedByPostAndDay(zone).size)
    }
}
