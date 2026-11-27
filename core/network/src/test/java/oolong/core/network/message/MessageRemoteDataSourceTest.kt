package oolong.core.network.message

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okio.Buffer
import oolong.core.network.FakeDevice
import oolong.core.network.TiebaProfile
import oolong.core.network.protos.agreeMe.AgreeItem
import oolong.core.network.protos.agreeMe.AgreeMeRequest
import oolong.core.network.protos.agreeMe.AgreeMeResponse
import oolong.core.network.protos.agreeMe.AgreeMeResponseData
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
import kotlin.test.assertNull

class MessageRemoteDataSourceTest {
    private val server = FormServer()
    private val dataSource = MessageRemoteDataSource(server.network, server.commonParams, FakeDevice)

    @After
    fun tearDown() = server.close()

    @Test
    fun `asks for unread counts as the message task stack`() = runTest {
        server.enqueue("""{"error_code":"0","message":{"replyme":"2","atme":"0","fans":"1","agree":"3"}}""")

        val response = dataSource.reminder()

        val request = server.takeRequest()
        assertEquals("/c/s/msg", request.target)
        assertEquals("UTF-8", request.headers["Charset"])
        assertEquals(null, request.headers["x_bd_data_type"])
        val form = request.form()
        assertSigned(form, withSig = false)
        assertNull(form.value("bookmark"))
        assertNull(form.value("tbs"))
        assertEquals("1", form.value("personalized_rec_switch"))
        assertEquals(server.commonParams.form(TiebaProfile.MESSAGE_FORM_12_52).map { it.first }.sorted() + "sign",
            form.withoutStatistics().map { it.first }.filter { it != "sign" }.sorted() + "sign")
        assertEquals("2", response.message?.replyMe)
        assertEquals("1", response.message?.fans)
    }

    @Test
    fun `lists a page of replies to the user`() = runTest {
        server.enqueue(
            """{"error_code":"0","reply_list":[{"is_floor":"1","title":"标题","content":"回复","quote_content":"原文",
            "replyer":{"id":"7","name":"n","name_show":"昵称","portrait":"p"},"thread_id":"9000","post_id":"201",
            "quote_pid":"200","time":"1700000000"}],"page":{"current_page":"2","has_more":"1"}}"""
        )

        val response = dataSource.replyMe(page = 2)

        val request = server.takeRequest()
        assertEquals("/c/u/feed/replyme", request.target)
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("2", form.value("pn"))
        assertEquals("12345", form.value("uid"))
        assertEquals("0", form.value("time"))
        assertEquals("0", form.value("tid"))
        assertEquals("0", form.value("is_first"))
        assertEquals("", form.value("call_from"))
        assertEquals(FakeDevice.screenDensity.toString(), form.value("scr_dip"))
        assertEquals(
            FeedItem(
                isFloor = "1", title = "标题", content = "回复", quoteContent = "原文",
                replyer = FeedItem.User("7", "n", "昵称", "p"), threadId = "9000", postId = "201",
                quotePid = "200", time = "1700000000",
            ),
            response.replyList.single(),
        )
        assertEquals("1", response.page?.hasMore)
    }

    @Test
    fun `an empty list of mentions may come as a string`() = runTest {
        server.enqueue("""{"error_code":"0","at_list":"","page":{"has_more":"0"}}""")

        val response = dataSource.atMe(page = 1)

        assertEquals("/c/u/feed/atme", server.takeRequest().target)
        assertEquals(emptyList(), response.atList)
    }

    @Test
    fun `asks for a page of likes after the last one as the protobuf stack`() = runTest {
        val item = AgreeItem(id = 41, type = 2, time = 1_700_000_000)
        server.server.enqueue(
            MockResponse.Builder()
                .body(Buffer().write(AgreeMeResponse(data_ = AgreeMeResponseData(has_more = 1, agree_list = listOf(item))).encode()))
                .build()
        )

        val response = dataSource.agreeMe(after = 94359475)

        val request = server.takeRequest()
        assertEquals("/c/u/feed/agreeme?cmd=309593&format=protobuf", request.target)
        server.commonParams.headers(TiebaProfile.PROTO_12_52).forEach { (name, value) -> assertEquals(value, request.headers[name], name) }
        val parts = multipartParts(request.headers["Content-Type"]!!, request.body!!)
        val sent = AgreeMeRequest.ADAPTER.decode(assertProtobufParts(parts)).data_!!
        assertEquals(server.commonParams.protobuf(TiebaProfile.PROTO_12_52), sent.common)
        assertEquals(94359475L, sent.id)
        assertEquals(20, sent.rn)
        assertEquals(FakeDevice.screenWidth, sent.scr_w)
        assertEquals(listOf(item), response.agree_list)
        assertEquals(1, response.has_more)
    }
}
