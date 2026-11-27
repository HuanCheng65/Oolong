package oolong.core.network.user

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okio.Buffer
import okio.ByteString
import oolong.core.network.FakeDevice
import oolong.core.network.TiebaProfile
import oolong.core.network.protos.profile.ProfileRequest
import oolong.core.network.protos.profile.ProfileResponse
import oolong.core.network.protos.profile.ProfileResponseData
import oolong.core.network.protos.userPost.UserPostRequest
import oolong.core.network.protos.userPost.UserPostResponse
import oolong.core.network.protos.userPost.UserPostResponseData
import oolong.core.network.testing.FormServer
import oolong.core.network.testing.assertProtobufParts
import oolong.core.network.testing.assertSigned
import oolong.core.network.testing.form
import oolong.core.network.testing.multipartParts
import oolong.core.network.testing.value
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserRemoteDataSourceTest {
    private val server = FormServer()
    private val commonParams = server.commonParams
    private val dataSource = UserRemoteDataSource(server.network, commonParams, FakeDevice)

    @After
    fun tearDown() = server.close()

    private fun enqueue(bytes: ByteArray) {
        server.server.enqueue(MockResponse.Builder().body(Buffer().write(bytes)).build())
    }

    private fun sentData(target: String): ByteString {
        val request = server.takeRequest()
        assertEquals(target, request.target)
        commonParams.headers(TiebaProfile.PROTO).forEach { (name, value) -> assertEquals(value, request.headers[name], name) }
        val parts = multipartParts(request.headers["Content-Type"]!!, request.body!!)
        return assertProtobufParts(parts)
    }

    @Test
    fun `asks for another user's profile as a guest`() = runTest {
        enqueue(ProfileResponse(data_ = ProfileResponseData()).encode())

        dataSource.profile(uid = 777)

        val sent = ProfileRequest.ADAPTER.decode(sentData("/c/u/user/profile?cmd=303012&format=protobuf")).data_!!
        assertEquals(777L, sent.friend_uid)
        assertEquals(1, sent.is_guest)
        assertEquals(12345L, sent.uid)
        assertEquals(commonParams.protobuf(TiebaProfile.PROTO), sent.common)
    }

    @Test
    fun `asks for the user's own profile as themselves`() = runTest {
        enqueue(ProfileResponse(data_ = ProfileResponseData()).encode())

        dataSource.profile(uid = 12345)

        val sent = ProfileRequest.ADAPTER.decode(sentData("/c/u/user/profile?cmd=303012&format=protobuf")).data_!!
        assertNull(sent.friend_uid)
        assertEquals(0, sent.is_guest)
    }

    @Test
    fun `asks for a page of replies`() = runTest {
        enqueue(UserPostResponse(data_ = UserPostResponseData()).encode())

        dataSource.posts(uid = 777, page = 2, threads = false)

        val sent = UserPostRequest.ADAPTER.decode(sentData("/c/u/feed/userpost?cmd=303002&format=protobuf")).data_!!
        assertEquals(777L, sent.uid)
        assertEquals(2, sent.pn)
        assertEquals(0, sent.is_thread)
        assertEquals(0, sent.is_view_card)
        assertEquals(0, sent.subtype)
    }

    @Test
    fun `lists another user's forums as a guest`() = runTest {
        server.enqueue("""{"error_code":"0","has_more":"1","forum_list":{"non-gconforum":[{"id":"52","name":"吧名","level_id":"7","avatar":"a"}]}}""")

        val response = dataSource.likedForums(uid = 777, page = 2)

        val form = server.takeRequest().form()
        assertSigned(form, withSig = false)
        assertEquals("2", form.value("page_no"))
        assertEquals("50", form.value("page_size"))
        assertEquals("12345", form.value("uid"))
        assertEquals("777", form.value("friend_uid"))
        assertEquals("1", form.value("is_guest"))
        assertEquals("吧名", response.forumList?.forums?.single()?.name)
    }

    @Test
    fun `follows with sig and unfollows without`() = runTest {
        server.enqueue("""{"error_code":"0"}""")
        server.enqueue("""{"error_code":"0"}""")

        dataSource.follow("tb.1.abc")
        dataSource.unfollow("tb.1.abc")

        val follow = server.takeRequest()
        assertEquals("/c/c/user/follow", follow.target)
        assertSigned(follow.form(), withSig = true)
        assertEquals("tb.1.abc", follow.form().value("portrait"))
        assertEquals("tbs-value", follow.form().value("tbs"))
        val unfollow = server.takeRequest()
        assertEquals("/c/c/user/unfollow", unfollow.target)
        assertSigned(unfollow.form(), withSig = false)
        assertEquals("2", unfollow.form().value("from_type"))
    }

    @Test
    fun `changes the user's own profile`() = runTest {
        server.enqueue("""{"error_code":"0"}""")

        dataSource.modifyProfile(nickName = "昵称", sex = 2, intro = "简介", birthdayShown = true, birthdayTime = 946684800)

        val request = server.takeRequest()
        assertEquals("/c/c/profile/modify", request.target)
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("昵称", form.value("nick_name"))
        assertEquals("2", form.value("sex"))
        assertEquals("简介", form.value("intro"))
        assertEquals("1", form.value("birthday_show_status"))
        assertEquals("946684800", form.value("birthday_time"))
        assertEquals("1", form.value("need_cam_decrypt"))
        assertEquals("0", form.value("need_keep_nickname_flag"))
    }

    @Test
    fun `reads and sets what another user is barred from`() = runTest {
        server.enqueue("""{"error_code":"0","perm_list":{"follow":1,"interact":0,"chat":"1"},"is_black_white":0}""")
        server.enqueue("""{"error_code":"0"}""")

        val permissions = dataSource.blackInfo(42)
        dataSource.setBlack(42, follow = true, interact = false, chat = true)

        assertEquals(BlackPermissions(follow = "1", interact = "0", chat = "1"), permissions)
        val read = server.takeRequest()
        assertEquals("/c/u/user/getUserBlackInfo", read.target)
        assertEquals("42", read.form().value("black_uid"))
        val set = server.takeRequest()
        assertEquals("/c/c/user/setUserBlack", set.target)
        val form = set.form()
        assertSigned(form, withSig = false)
        assertEquals("42", form.value("black_uid"))
        assertEquals("""{"follow":1,"interact":0,"chat":1}""", form.value("perm_list"))
        assertEquals("tbs-value", form.value("tbs"))
    }

    @Test
    fun `lists a page of the replies the user liked, each under its thread`() = runTest {
        server.enqueue(
            """{"error_code":0,"data":{"has_more":1,"thread_list":[{"id":11,"tid":11,"title":"标题","fid":3,"fname":"吧",
            "agree":{"has_agree":0,"agree_num":9},"top_agree_post":{"id":22,"time":1700000000,
            "content":[{"type":0,"text":"回复 "},{"type":4,"text":"某人","uid":5},{"type":2,"text":"image_emoticon22","src":"u"}],
            "author":{"id":7,"name":"n","name_show":"昵称","portrait":"tb.1.p?t=1"},"agree":{"agree_num":17},"quote_id":"21"}}]}}"""
        )

        val page = dataSource.likes(replies = true, page = 2)

        val request = server.takeRequest()
        assertEquals("/c/u/feed/userAgree", request.target)
        assertEquals("hybrid", request.headers["Subapp-Type"])
        commonParams.headers(TiebaProfile.WEB).forEach { (name, value) -> assertEquals(value, request.headers[name], name) }
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("2", form.value("tab_id"))
        assertEquals("2", form.value("pn"))
        assertEquals("20", form.value("rn"))
        assertEquals("hybrid", form.value("subapp_type"))
        assertEquals("bduss-value", form.value("BDUSS"))
        assertEquals(1, page.hasMore)
        val reply = page.threads.single().likedReply!!
        assertEquals(22L, reply.id)
        assertEquals(21L, reply.floorId)
        assertEquals(17L, reply.agree?.count)
        assertEquals(5L, reply.content[1].uid)
        assertEquals("昵称", reply.author?.nameShow)
    }

    @Test
    fun `asks for liked threads by their own tab`() = runTest {
        server.enqueue("""{"error_code":0,"data":{"has_more":0,"thread_list":[{"tid":11,"video_info":{"video_url":"v","video_duration":19}}]}}""")

        val page = dataSource.likes(replies = false, page = 1)

        assertEquals("1", server.takeRequest().form().value("tab_id"))
        assertEquals(0, page.hasMore)
        assertEquals(19, page.threads.single().video?.duration)
    }

    @Test
    fun `lists a page of whom a user follows`() = runTest {
        server.enqueue(
            """{"error_code":"0","has_more":1,"total_follow_num":"2","follow_list":[{"id":7,"name":"n","name_show":"昵称",
            "portrait":"tb.1.p","intro":"自我介绍","has_concerned":1}]}"""
        )

        val response = dataSource.follows(uid = 42, page = 2)

        val request = server.takeRequest()
        assertEquals("/c/u/follow/followList", request.target)
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("42", form.value("uid"))
        assertEquals("2", form.value("pn"))
        assertEquals("1", response.hasMore)
        assertEquals(ListedUser("7", "n", "昵称", "tb.1.p", "自我介绍", "1"), response.users.single())
    }

    @Test
    fun `lists a page of who follows a user`() = runTest {
        server.enqueue(
            """{"error_code":"0","page":{"current_page":"1","total_page":"3","has_more":"1"},
            "user_list":[{"id":"8","name":"m","name_show":"","portrait":"tb.1.q","follow_time":"1700000000","has_concerned":"0"}]}"""
        )

        val response = dataSource.fans(uid = 42, page = 1)

        val request = server.takeRequest()
        assertEquals("/c/u/fans/page", request.target)
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("42", form.value("uid"))
        assertEquals("1", form.value("pn"))
        assertEquals("1", response.page?.hasMore)
        assertEquals(ListedUser("8", "m", "", "tb.1.q", null, "0"), response.users.single())
    }
}
