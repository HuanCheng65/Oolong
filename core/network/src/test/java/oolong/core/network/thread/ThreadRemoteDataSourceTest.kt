package oolong.core.network.thread

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okio.Buffer
import oolong.core.network.FakeDevice
import oolong.core.network.TiebaProfile
import oolong.core.network.TiebaServerException
import oolong.core.network.protos.Error
import oolong.core.network.protos.Page
import oolong.core.network.protos.pbFloor.PbFloorRequest
import oolong.core.network.protos.pbFloor.PbFloorRequestData
import oolong.core.network.protos.pbFloor.PbFloorResponse
import oolong.core.network.protos.pbFloor.PbFloorResponseData
import oolong.core.network.protos.pbPage.AdParam
import oolong.core.network.protos.pbPage.PbPageRequest
import oolong.core.network.protos.pbPage.PbPageRequestData
import oolong.core.network.protos.pbPage.PbPageResponse
import oolong.core.network.protos.pbPage.PbPageResponseData
import oolong.core.network.testing.FormServer
import oolong.core.network.testing.assertProtobufParts
import oolong.core.network.testing.assertSigned
import oolong.core.network.testing.form
import oolong.core.network.testing.multipartParts
import oolong.core.network.testing.value
import oolong.core.network.testing.withoutStatistics
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ThreadRemoteDataSourceTest {
    private val server = FormServer()
    private val commonParams = server.commonParams
    private val dataSource = ThreadRemoteDataSource(server.network, commonParams, FakeDevice)

    @After
    fun tearDown() = server.close()

    private fun enqueue(response: PbPageResponse) {
        server.server.enqueue(MockResponse.Builder().body(Buffer().write(response.encode())).build())
    }

    private fun sentPageRequest(): PbPageRequestData {
        val request = server.takeRequest()
        assertEquals("/c/f/pb/page?cmd=302001&format=protobuf", request.target)
        commonParams.headers(TiebaProfile.PROTO).forEach { (name, value) ->
            assertEquals(value, request.headers[name], name)
        }
        val parts = multipartParts(request.headers["Content-Type"]!!, request.body!!)
        return PbPageRequest.ADAPTER.decode(assertProtobufParts(parts)).data_!!
    }

    @Test
    fun `requests a page as the 22_10 client`() = runTest {
        enqueue(PbPageResponse(data_ = PbPageResponseData()))

        dataSource.page(threadId = 9_000, page = 2, forumId = 52)

        val expected = PbPageRequestData(
            last = 0,
            common = commonParams.protobuf(TiebaProfile.PROTO),
            kz = 9_000,
            pid = 0,
            pn = 2,
            r = 0,
            lz = 0,
            forum_id = 52,
            ad_param = AdParam(load_count = 0, refresh_count = 1, is_req_ad = 1),
            mark = 0,
            last_pid = 0,
            app_pos = commonParams.appPos(),
            back = 0,
            banner = 0,
            broadcast_id = 0,
            floor_rn = 4,
            floor_sort_type = 1,
            from_push = 0,
            from_smart_frs = 0,
            immersion_video_comment_source = 0,
            is_comm_reverse = 0,
            is_fold_comment_req = 0,
            is_jumpfloor = 0,
            jumpfloor_num = 0,
            need_repost_recommend_forum = 0,
            obj_locate = "",
            obj_param1 = "10",
            obj_source = "",
            ori_ugc_type = 0,
            pb_rn = 0,
            q_type = 2,
            request_times = 0,
            rn = 15,
            s_model = 0,
            scr_dip = 2.625,
            scr_h = 2400,
            scr_w = 1080,
            similar_from = 0,
            source_type = 2,
            st_type = "",
            thread_type = 0,
            weipost = 0,
            with_floor = 1,
        )
        assertEquals(expected, sentPageRequest())
    }

    @Test
    fun `sends the cursor, order and filters of a page`() = runTest {
        enqueue(PbPageResponse(data_ = PbPageResponseData()))

        dataSource.page(
            threadId = 9_000,
            postId = 77,
            sortType = 1,
            seeLz = true,
            back = true,
            last = true,
            lastPostId = 66,
            stType = "store_thread",
            mark = 1,
        )

        val sent = sentPageRequest()
        assertEquals(77, sent.pid)
        assertEquals(0, sent.pn)
        assertEquals(1, sent.r)
        assertEquals(1, sent.lz)
        assertEquals(1, sent.back)
        assertEquals(1, sent.last)
        assertEquals(66, sent.last_pid)
        assertEquals("store_thread", sent.st_type)
        assertEquals(1, sent.mark)
    }

    @Test
    fun `returns the page data and throws server errors`() = runTest {
        val data = PbPageResponseData(page = Page(current_page = 2))
        enqueue(PbPageResponse(data_ = data))
        assertEquals(data, dataSource.page(threadId = 9_000))

        enqueue(PbPageResponse(error = Error(error_code = 4, error_msg = "贴子已被删除")))
        val error = assertFailsWith<TiebaServerException> { dataSource.page(threadId = 9_000) }
        assertEquals(4, error.code)
    }

    @Test
    fun `requests the replies inside a post as the 22_10 client without stoken`() = runTest {
        val data = PbFloorResponseData(page = Page(current_page = 1, total_page = 2))
        server.server.enqueue(MockResponse.Builder().body(Buffer().write(PbFloorResponse(data_ = data).encode())).build())

        val returned = dataSource.floor(threadId = 9_000, postId = 77, forumId = 52, page = 2, subPostId = 88)

        val request = server.takeRequest()
        assertEquals("/c/f/pb/floor?cmd=302002&format=protobuf", request.target)
        commonParams.headers(TiebaProfile.PROTO).forEach { (name, value) ->
            assertEquals(value, request.headers[name], name)
        }
        val parts = multipartParts(request.headers["Content-Type"]!!, request.body!!)
        val message = assertProtobufParts(parts, loggedIn = false)
        val expected = PbFloorRequestData(
            preview_spids = "",
            common = commonParams.protobuf(TiebaProfile.PROTO),
            forum_id = 52,
            kz = 9_000,
            pid = 77,
            pn = 2,
            spid = 88,
            scr_dip = 2.625,
            scr_h = 2400,
            scr_w = 1080,
            is_comm_reverse = 0,
            ori_ugc_type = 0,
            sort = 2,
        )
        assertEquals(expected, PbFloorRequest.ADAPTER.decode(message).data_)
        assertEquals(data, returned)
    }

    @Test
    fun `adds a thread to the store marked at a post`() = runTest {
        server.enqueue("""{"error_code":"0","error_msg":""}""")

        dataSource.addStore(threadId = 9_000, postId = 77)

        val request = server.takeRequest()
        assertEquals("/c/c/post/addstore", request.target)
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("""[{"pid":"77","status":1,"tid":"9000"}]""", form.value("data"))
        assertEquals("stoken-value", form.value("stoken"))
        assertNull(form.value("tbs"))
        val expected = commonParams.form(TiebaProfile.FORM) + ("data" to form.value("data")!!)
        assertEquals(expected.sortedBy { it.first }, form.filter { it.first != "sign" }.withoutStatistics())
    }

    @Test
    fun `removes a thread from the store`() = runTest {
        server.enqueue("""{"error_code":"0","error_msg":""}""")

        dataSource.removeStore(threadId = 9_000, forumId = 52)

        val request = server.takeRequest()
        assertEquals("/c/c/post/rmstore", request.target)
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("9000", form.value("tid"))
        assertEquals("52", form.value("fid"))
        assertEquals("12345", form.value("user_id"))
        assertEquals("tbs-value", form.value("tbs"))
        assertEquals("stoken-value", form.value("stoken"))
    }

    @Test
    fun `removes a thread from the store page without its forum`() = runTest {
        server.enqueue("""{"error_code":"0","error_msg":""}""")

        dataSource.removeStore(threadId = 9_000, forumId = null)

        assertEquals("null", server.takeRequest().form().value("fid"))
    }

    @Test
    fun `asks for the pictures before one as a signed form`() = runTest {
        server.enqueue(
            """{"error_code":"0","pic_amount":"30","pic_list":[{"overall_index":"5","show_original_btn":"1","img":{"original":{"id":"p5","big_cdn_src":"b","original_src":"o"}},"post_id":"77"}]}"""
        )

        val response = dataSource.picPage(
            forumId = 52, forumName = "测试", threadId = 9_000, picId = "p6", picIndex = 6,
            objType = "", previous = true, seeLz = false, postId = 0,
        )

        val request = server.takeRequest()
        assertEquals("/c/f/pb/picpage", request.target)
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("other", form.value("obj_type"))
        assertEquals("10", form.value("prev"))
        assertEquals("0", form.value("next"))
        assertEquals("1", form.value("not_see_lz"))
        assertEquals(null, form.value("post_id"))
        assertEquals("12345", form.value("user_id"))
        assertEquals("p5", response.picList.single().img.original.id)
        assertEquals("30", response.picAmount)
    }

    @Test
    fun `asks where to report a post`() = runTest {
        server.enqueue("""{"error_code":"0","errno":0,"errmsg":"","data":{"url":"https://example.com/report"}}""")

        val url = dataSource.reportUrl(77)

        val form = server.takeRequest().form()
        assertEquals("1", form.value("category"))
        assertEquals("77", form.value("pid"))
        assertEquals("https://example.com/report", url)
    }

    @Test
    fun `lists a page of the thread store`() = runTest {
        server.enqueue(
            """{"error_code":"0","error":{"errno":"0","errmsg":""},"store_thread":[{"thread_id":"9","title":"t","count":"2","post_no":"30","author":{"lz_uid":"7","name_show":"n"}}]}"""
        )

        val response = dataSource.threadStore(page = 2)

        val request = server.takeRequest()
        assertEquals("/c/f/post/threadstore", request.target)
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("20", form.value("rn"))
        assertEquals("40", form.value("offset"))
        assertEquals("12345", form.value("user_id"))
        assertEquals("7", response.storeThread.single().author.lzUid)
    }

    @Test
    fun `a thread store error is thrown`() = runTest {
        server.enqueue("""{"error_code":"0","error":{"errno":"220034","errmsg":"操作太频繁"}}""")

        val error = assertFailsWith<TiebaServerException> { dataSource.threadStore(page = 0) }

        assertEquals(220034, error.code)
    }

    @Test
    fun `sends the tbs of the page instead of the account's when given`() = runTest {
        server.enqueue("""{"error_code":"0","error_msg":""}""")

        dataSource.removeStore(threadId = 9_000, forumId = 52, tbs = "page-tbs")

        val form = server.takeRequest().form()
        assertEquals(listOf("page-tbs"), form.filter { it.first == "tbs" }.map { it.second })
    }

    @Test
    fun `deletes the user's own post`() = runTest {
        server.enqueue("""{"error_code":"0","error_msg":""}""")

        dataSource.deletePost(52, "吧名", threadId = 9_000, postId = 77, ownPost = true)

        val request = server.takeRequest()
        assertEquals("/c/c/bawu/delpost", request.target)
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("52", form.value("fid"))
        assertEquals("吧名", form.value("word"))
        assertEquals("9000", form.value("z"))
        assertEquals("77", form.value("pid"))
        assertEquals("0", form.value("isfloor"))
        assertEquals("1", form.value("src"))
        assertEquals("0", form.value("is_vipdel"))
        assertEquals("1", form.value("delete_my_post"))
        assertEquals("tbs-value", form.value("tbs"))
    }

    @Test
    fun `deletes a reply inside a post as a moderator`() = runTest {
        server.enqueue("""{"error_code":"0","error_msg":""}""")

        dataSource.deletePost(52, "吧名", 9_000, postId = 88, ownPost = false, isSubPost = true)

        val form = server.takeRequest().form()
        assertEquals("1", form.value("isfloor"))
        assertEquals("3", form.value("src"))
        assertEquals("1", form.value("is_vipdel"))
        assertNull(form.value("delete_my_post"))
        assertNull(form.value("reason"))
    }

    @Test
    fun `deletes a thread`() = runTest {
        server.enqueue("""{"error_code":"0","error_msg":""}""")

        dataSource.deleteThread(52, "吧名", threadId = 9_000, ownThread = true)

        val request = server.takeRequest()
        assertEquals("/c/c/bawu/delthread", request.target)
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("52", form.value("fid"))
        assertEquals("吧名", form.value("word"))
        assertEquals("9000", form.value("z"))
        assertEquals("0", form.value("is_vipdel"))
        assertEquals("1", form.value("delete_my_thread"))
        assertNull(form.value("src"))
        assertNull(form.value("is_frs_mask"))
        assertEquals("tbs-value", form.value("tbs"))
    }

    @Test
    fun `deletes another user's thread as a moderator without the own-thread flag`() = runTest {
        server.enqueue("""{"error_code":"0","error_msg":""}""")

        dataSource.deleteThread(52, "吧名", threadId = 9_000, ownThread = false)

        assertNull(server.takeRequest().form().value("delete_my_thread"))
    }

    @Test
    fun `throws when the server refuses a deletion`() = runTest {
        server.enqueue("""{"error_code":"220034","error_msg":"您的操作太频繁了"}""")

        val error = assertFailsWith<TiebaServerException> {
            dataSource.deleteThread(52, "吧名", threadId = 9_000, ownThread = true)
        }
        assertEquals(220034, error.code)
    }
}
