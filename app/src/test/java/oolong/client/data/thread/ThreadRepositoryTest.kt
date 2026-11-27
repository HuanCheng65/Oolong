package oolong.client.data.thread

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import oolong.client.arch.query.QueryClient
import oolong.client.data.offline.OfflineThreads
import oolong.client.model.BawuType
import oolong.client.model.PostContent
import oolong.core.network.TiebaUnknownException
import oolong.core.network.post.PostRemoteDataSource
import oolong.core.network.protos.Anti
import oolong.core.network.protos.PbContent
import oolong.core.network.protos.PollInfo
import oolong.core.network.protos.PollOption
import oolong.core.network.protos.SimpleForum
import oolong.core.network.protos.SubPost
import oolong.core.network.protos.SubPostList
import oolong.core.network.protos.ThreadInfo
import oolong.core.network.protos.VideoInfo
import oolong.core.network.protos.pbFloor.PbFloorResponseData
import oolong.core.network.protos.pbPage.PbPageResponseData
import oolong.core.network.thread.ThreadRemoteDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import oolong.core.network.protos.Agree as ProtoAgree
import oolong.core.network.protos.Page as ProtoPage
import oolong.core.network.protos.Post as ProtoPost
import oolong.core.network.protos.User as ProtoUser

class ThreadRepositoryTest {
    private val remote = mockk<ThreadRemoteDataSource>()
    private val postRemote = mockk<PostRemoteDataSource>(relaxed = true)
    private val repository = ThreadRepository(remote, postRemote, { _, _ -> }, Dispatchers.Unconfined, QueryClient(CoroutineScope(Dispatchers.Unconfined)))

    private val lz = ProtoUser(id = 10, name = "lz", nameShow = "楼主", portrait = "p10", level_id = 12)
    private val replier = ProtoUser(id = 20, name = "replier", nameShow = "", portrait = "p20", level_id = 3)

    private fun post(id: Long, floor: Int, author: ProtoUser? = replier, authorId: Long = author?.id ?: 0) =
        ProtoPost(
            id = id,
            floor = floor,
            time = 1_700_000_000,
            author = author,
            author_id = authorId,
            content = listOf(PbContent(type = 0, text = "第 $floor 楼")),
        )

    private fun data(
        posts: List<ProtoPost>,
        page: ProtoPage = ProtoPage(current_page = 1, new_total_page = 5, has_more = 1),
        thread: ThreadInfo = ThreadInfo(id = 9_000, title = "标题", author = lz, replyNum = 70, firstPostId = 1),
        firstFloor: ProtoPost? = null,
        users: List<ProtoUser> = emptyList(),
    ) = PbPageResponseData(
        thread = thread,
        forum = SimpleForum(id = 52, name = "吧名", avatar = "avatar"),
        page = page,
        anti = Anti(tbs = "page-tbs"),
        user = ProtoUser(id = 99, name = "me", is_login = 1),
        post_list = posts,
        first_floor_post = firstFloor,
        user_list = users,
    )

    private fun respond(data: PbPageResponseData) {
        coEvery {
            remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns data
    }

    @Test
    fun `maps the thread, its first floor and the replies`() = runTest {
        respond(data(listOf(post(1, 1, lz), post(2, 2), post(3, 3))))

        val page = repository.page(ThreadQuery(9_000), ThreadCursor.Number(0))

        assertEquals("标题", page.thread.title)
        assertEquals("楼主", page.thread.author.displayName)
        assertEquals(52, page.thread.forum.id)
        assertEquals(70, page.thread.replyCount)
        assertNull(page.thread.storeMarkPostId)
        assertEquals(1, page.firstPost?.floor)
        assertEquals(listOf(2L, 3L), page.posts.items.map { it.id })
        assertEquals("replier", page.posts.items[0].author.displayName)
        assertEquals(1 to 5, page.currentPage to page.totalPage)
        assertEquals(99, page.viewer.user?.id)
        assertEquals("page-tbs", page.tbs)
    }

    @Test
    fun `oldest first pages forward by number and back from the first post`() = runTest {
        respond(data(listOf(post(31, 31), post(32, 32)), ProtoPage(current_page = 3, has_more = 1, has_prev = 1)))

        val posts = repository.page(ThreadQuery(9_000), ThreadCursor.Anchor(31)).posts

        assertEquals(ThreadCursor.Number(4), posts.next)
        assertEquals(ThreadCursor.Before(31, page = 2), posts.prev)
    }

    @Test
    fun `newest first pages back from the oldest post and forward from the newest`() = runTest {
        respond(data(listOf(post(70, 70), post(69, 69)), ProtoPage(current_page = 4, total_page = 5, has_more = 1)))

        val query = ThreadQuery(9_000, sort = ThreadSort.NewestFirst)
        val posts = repository.page(query, ThreadCursor.first()).posts

        assertEquals(ThreadCursor.Anchor(69), posts.next)
        assertEquals(ThreadCursor.Before(70, page = 5), posts.prev)
    }

    @Test
    fun `newest first sends no page number for newer posts once on the last page`() = runTest {
        respond(data(listOf(post(70, 70)), ProtoPage(current_page = 5, total_page = 5)))

        val query = ThreadQuery(9_000, sort = ThreadSort.NewestFirst)
        val posts = repository.page(query, ThreadCursor.first()).posts

        assertNull(posts.next)
        assertEquals(ThreadCursor.Before(70, page = 0), posts.prev)
    }

    @Test
    fun `an empty page ends the thread instead of failing`() = runTest {
        respond(data(emptyList(), ProtoPage(current_page = 0, has_more = 1, has_prev = 1)))

        val posts = repository.page(ThreadQuery(9_000), ThreadCursor.After(70)).posts

        assertTrue(posts.items.isEmpty())
        assertNull(posts.next)
        assertNull(posts.prev)
    }

    @Test
    fun `no posts newer than the last one is an empty load, not a failure`() = runTest {
        // What the server answers then: nothing, not even the thread
        respond(PbPageResponseData())

        val load = repository.load(ThreadQuery(9_000), ThreadCursor.After(70))

        assertNull(load.page)
        assertTrue(load.posts.items.isEmpty())
        assertNull(load.posts.next)
    }

    @Test
    fun `sends each cursor as the page request expects`() = runTest {
        respond(data(listOf(post(2, 2))))
        val query = ThreadQuery(9_000, seeLz = true, forumId = 52, fromStore = true)

        repository.page(query, ThreadCursor.Number(0))
        repository.page(query.copy(sort = ThreadSort.NewestFirst), ThreadCursor.Number(0))
        repository.page(query, ThreadCursor.Anchor(31))
        repository.page(query, ThreadCursor.Before(31, page = 2))
        repository.page(query, ThreadCursor.After(70))

        coVerify {
            remote.page(9_000, 0, 0, 0, true, false, false, 0, 52, "store_thread", 1)
            remote.page(9_000, 0, 0, 1, true, false, false, 0, 52, "store_thread", 1)
            remote.page(9_000, 0, 31, 0, true, false, false, 0, 52, "store_thread", 1)
            remote.page(9_000, 2, 31, 0, true, true, false, 0, 52, "store_thread", 1)
            remote.page(9_000, 0, 70, 0, true, false, false, 70, 52, "store_thread", 1)
        }
    }

    @Test
    fun `first cursor of a thread`() {
        assertEquals(ThreadCursor.Number(0), ThreadCursor.first())
        assertEquals(ThreadCursor.Anchor(31), ThreadCursor.first(postId = 31))
    }

    @Test
    fun `authors missing from posts and replies are taken from the user list`() = runTest {
        val subPost = SubPostList(id = 201, author_id = 20, content = listOf(PbContent(type = 0, text = "回复")))
        val withoutAuthor = post(2, 2, author = null, authorId = 20)
            .copy(sub_post_list = SubPost(sub_post_list = listOf(subPost)), sub_post_number = 1)
        respond(data(listOf(withoutAuthor), users = listOf(replier)))

        val post = repository.page(ThreadQuery(9_000), ThreadCursor.Number(0)).posts.items.single()

        assertEquals(20, post.author.id)
        assertEquals("replier", post.author.name)
        assertEquals(20, post.subPosts.single().author.id)
    }

    @Test
    fun `a reply loses the colon and spaces the page may start it with`() = runTest {
        val replies = listOf(
            SubPostList(id = 201, author = replier, content = listOf(PbContent(type = 0, text = " : "), PbContent(type = 0, text = "说得对"))),
            SubPostList(
                id = 202,
                author = replier,
                content = listOf(PbContent(type = 0, text = " 回复 "), PbContent(type = 4, text = "楼主", uid = 10), PbContent(type = 0, text = " :嗯")),
            ),
        )
        val withReplies = post(2, 2, replier).copy(sub_post_list = SubPost(sub_post_list = replies), sub_post_number = 2)
        respond(data(listOf(withReplies)))

        val subPosts = repository.page(ThreadQuery(9_000), ThreadCursor.Number(0)).posts.items.single().subPosts

        assertEquals("说得对", (subPosts[0].content.single() as PostContent.Text).spans.joinToString("") { it.text })
        assertEquals("回复 楼主 :嗯", (subPosts[1].content.single() as PostContent.Text).spans.joinToString("") { it.text })
    }

    @Test
    fun `a thread's poll comes with the page and votes go to the thread`() = runTest {
        val poll = PollInfo(title = "选哪个", options = listOf(PollOption(id = 1, text = "甲"), PollOption(id = 2, text = "乙")))
        respond(data(listOf(post(1, 1, lz)), thread = ThreadInfo(id = 9_000, author = lz, poll_info = poll)))

        val thread = repository.page(ThreadQuery(9_000), ThreadCursor.Number(0)).thread
        assertEquals("选哪个", thread.poll?.title)

        repository.vote(thread, listOf(1, 2))
        coVerify { postRemote.addPollPost(9_000, 52, listOf(1, 2)) }
    }

    @Test
    fun `adverts on floor 0 are dropped without ending the thread`() = runTest {
        respond(data(listOf(post(1, 1, lz), post(500, 0), post(2, 2))))
        assertEquals(listOf(2L), repository.page(ThreadQuery(9_000), ThreadCursor.Number(0)).posts.items.map { it.id })

        respond(data(listOf(post(501, 0))))
        assertEquals(ThreadCursor.Number(2), repository.page(ThreadQuery(9_000), ThreadCursor.Number(0)).posts.next)
    }

    @Test
    fun `an author id of 0 falls back to the author's own id`() = runTest {
        respond(data(listOf(post(2, 2, author = replier, authorId = 0))))

        val post = repository.page(ThreadQuery(9_000), ThreadCursor.Number(0)).posts.items.single()
        assertEquals(20, post.author.id)
    }

    @Test
    fun `the first floor comes from first_floor_post when the page doesn't include it`() = runTest {
        val video = VideoInfo(videoMD5 = "md5", videoUrl = "https://signed/md5.mp4", thumbnailUrl = "thumb")
        val firstFloor = ProtoPost(
            id = 1,
            floor = 1,
            title = "标题",
            content = listOf(PbContent(type = 5, link = "https://raw/md5", src = "raw-cover", bsize = "1,1")),
        )
        val thread = ThreadInfo(id = 9_000, title = "标题", author = lz, videoInfo = video)
        respond(data(listOf(post(31, 31)), thread = thread, firstFloor = firstFloor))

        val page = repository.page(ThreadQuery(9_000), ThreadCursor.Anchor(31))

        val first = page.firstPost!!
        assertEquals(10, first.author.id)
        assertEquals("标题", first.title)
        val playable = first.content.single() as PostContent.Video
        assertEquals("https://signed/md5.mp4", playable.url)
        assertEquals("thumb", playable.coverUrl)
        assertEquals(1, page.thread.firstPostId)
    }

    @Test
    fun `titles of later floors and of floors marked without one are dropped`() = runTest {
        respond(
            data(
                listOf(
                    post(1, 1, lz).copy(title = "标题", is_ntitle = 1),
                    post(2, 2).copy(title = "回复：标题"),
                )
            )
        )

        val page = repository.page(ThreadQuery(9_000), ThreadCursor.Number(0))
        assertEquals("", page.firstPost?.title)
        assertEquals("", page.posts.items.single().title)
    }

    @Test
    fun `maps agree, store, moderator and guest state`() = runTest {
        val thread = ThreadInfo(
            id = 9_000,
            author = lz.copy(is_bawu = 1, bawu_type = "manager"),
            agree = ProtoAgree(diffAgreeNum = 12, hasAgree = 1),
            collectStatus = 1,
            collectMarkPid = "31",
        )
        val agreed = post(2, 2).copy(agree = ProtoAgree(diffAgreeNum = 3, hasAgree = 1))
        respond(data(listOf(agreed), thread = thread).copy(user = ProtoUser(is_login = 0), anti = Anti(tbs = "")))

        val page = repository.page(ThreadQuery(9_000), ThreadCursor.Number(0))

        assertEquals(12L to true, page.thread.agree.count to page.thread.agree.agreed)
        assertEquals(31L, page.thread.storeMarkPostId)
        assertEquals(BawuType.Manager, page.thread.author.bawuType)
        assertEquals(3L to true, page.posts.items.single().agree.let { it.count to it.agreed })
        assertNull(page.viewer.user)
        assertNull(page.tbs)
    }

    @Test
    fun `maps a page of the replies inside a post`() = runTest {
        val replies = listOf(
            SubPostList(id = 201, author = lz, content = listOf(PbContent(type = 0, text = "楼主回复")), agree = ProtoAgree(diffAgreeNum = 2, hasAgree = 1)),
            SubPostList(id = 202, author = replier, content = listOf(PbContent(type = 0, text = "又一条"))),
        )
        coEvery { remote.floor(9_000, 77, 52, 2, 201, true) } returns PbFloorResponseData(
            post = post(77, 5),
            subpost_list = replies,
            page = ProtoPage(current_page = 2, total_page = 3, total_count = 25),
            thread = ThreadInfo(id = 9_000, author = lz),
            forum = SimpleForum(id = 52, name = "吧名"),
            anti = Anti(tbs = "page-tbs"),
        )

        val floor = repository.floor(9_000, 77, 52, FloorCursor(2, subPostId = 201))

        assertEquals(77, floor.post.id)
        assertEquals(10, floor.threadAuthorId)
        assertEquals(listOf(201L, 202L), floor.subPosts.items.map { it.id })
        assertEquals(2L to true, floor.subPosts.items[0].agree.let { it.count to it.agreed })
        assertEquals(FloorCursor(3), floor.subPosts.next)
        assertEquals(FloorCursor(1), floor.subPosts.prev)
        assertEquals(25, floor.totalCount)
        assertEquals("page-tbs", floor.tbs)
    }

    @Test
    fun `the last page of replies has no next page`() = runTest {
        coEvery { remote.floor(any(), any(), any(), any(), any(), any(), any()) } returns PbFloorResponseData(
            post = post(77, 5),
            subpost_list = listOf(SubPostList(id = 201, author = lz)),
            page = ProtoPage(current_page = 1, total_page = 1),
            forum = SimpleForum(id = 52),
        )

        val floor = repository.floor(9_000, 77, 52, FloorCursor(1))
        assertNull(floor.subPosts.next)
        assertNull(floor.subPosts.prev)
    }

    @Test
    fun `fails when the thread is missing`() = runTest {
        respond(data(listOf(post(2, 2))).copy(thread = null))

        assertFailsWith<TiebaUnknownException> { repository.page(ThreadQuery(9_000), ThreadCursor.Number(0)) }
    }

    @Test
    fun `a thread opened offline reads its saved copy and not the server`() = runTest {
        val offline = mockk<OfflineThreads>()
        val saved = data(listOf(post(1, 1, lz), post(2, 2)))
        coEvery { offline.page(any(), any()) } returns saved
        val repository = ThreadRepository(remote, postRemote, { _, _ -> }, Dispatchers.Unconfined, QueryClient(CoroutineScope(Dispatchers.Unconfined)), offline = offline)

        val page = repository.page(ThreadQuery(threadId = 9_000, offline = true), ThreadCursor.Number(0))

        assertEquals(listOf(2L), page.posts.items.map { it.id })
        coVerify(exactly = 0) { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the saved copy stands in when the server can't be reached, not when it refuses`() = runTest {
        val offline = mockk<OfflineThreads>()
        coEvery { offline.page(any(), any()) } returns data(listOf(post(1, 1, lz), post(2, 2)))
        val repository = ThreadRepository(remote, postRemote, { _, _ -> }, Dispatchers.Unconfined, QueryClient(CoroutineScope(Dispatchers.Unconfined)), offline = offline)
        coEvery { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws java.net.UnknownHostException()

        assertEquals(listOf(2L), repository.page(ThreadQuery(threadId = 9_000), ThreadCursor.Number(0)).posts.items.map { it.id })

        coEvery { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws TiebaUnknownException
        assertFailsWith<TiebaUnknownException> { repository.page(ThreadQuery(threadId = 9_000), ThreadCursor.Number(0)) }
    }
}
