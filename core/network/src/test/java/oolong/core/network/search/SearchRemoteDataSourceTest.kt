package oolong.core.network.search

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okio.Buffer
import oolong.core.network.FakeDevice
import oolong.core.network.TiebaProfile
import oolong.core.network.TiebaServerException
import oolong.core.network.UnreadableResponseException
import oolong.core.network.protos.searchSug.SearchSugRequest
import oolong.core.network.protos.searchSug.SearchSugResponse
import oolong.core.network.protos.searchSug.SearchSugResponseData
import oolong.core.network.testing.FormServer
import oolong.core.network.testing.multipartParts
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SearchRemoteDataSourceTest {
    private val server = FormServer()
    private val commonParams = server.commonParams
    private val dataSource = SearchRemoteDataSource(server.network, commonParams, FakeDevice, clock = { 1_700_000_000_000L })

    @After
    fun tearDown() = server.close()

    @Test
    fun `asks for suggestions as a protobuf request`() = runTest {
        server.server.enqueue(
            MockResponse.Builder().body(Buffer().write(SearchSugResponse(data_ = SearchSugResponseData(list = listOf("a", "ab"))).encode())).build()
        )

        val suggestions = dataSource.suggestions("a")

        val request = server.takeRequest()
        assertEquals("/c/s/searchSug?cmd=309438&format=protobuf", request.target)
        val parts = multipartParts(request.headers["Content-Type"]!!, request.body!!)
        val sent = SearchSugRequest.ADAPTER.decode(parts.single { it.first == "data" }.second).data_!!
        assertEquals("a", sent.word)
        assertEquals("0", sent.isforum)
        assertEquals(listOf("a", "ab"), suggestions)
    }

    @Test
    fun `a web page where data was expected names the endpoint and what came`() = runTest {
        server.server.enqueue(
            MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body("<html>安全验证</html>").build()
        )

        val error = assertFailsWith<UnreadableResponseException> { dataSource.threads("k", 1, SearchSort.NEWEST) }

        assertEquals("mo/q/search/thread", error.endpoint)
        assertEquals("text/html", error.contentType)
    }

    @Test
    fun `reads fuzzy forum matches whether they come as a list or an object`() = runTest {
        server.enqueue(
            """{"no":0,"error":"success","data":{"exactMatch":{"forum_id":"52","forum_name":"测试"},
              |"fuzzyMatch":{"0":{"forum_id":1,"forum_name":"a"},"1":{"forum_id":2,"forum_name":"b"}}}}""".trimMargin()
        )
        server.enqueue("""{"no":0,"error":"success","data":{"exactMatch":[],"fuzzyMatch":[{"forum_id":3,"forum_name":"c"}]}}""")

        val first = dataSource.forums("测试")
        val second = dataSource.forums("c")

        val request = server.takeRequest()
        assertEquals("/mo/q/search/forum?word=%E6%B5%8B%E8%AF%95", request.target)
        assertEquals(
            "https://tieba.baidu.com/mo/q/hybrid/search?keyword=%E6%B5%8B%E8%AF%95&_webview_time=1700000000000",
            request.headers["Referer"],
        )
        assertEquals("com.baidu.tieba", request.headers["X-Requested-With"])
        commonParams.headers(TiebaProfile.WEB).forEach { (name, value) -> assertEquals(value, request.headers[name], name) }
        assertEquals(52L, first.exactMatch?.forumId)
        assertEquals(listOf("a", "b"), first.fuzzyForums.map { it.forumName })
        assertNull(second.exactMatch)
        assertEquals(listOf("c"), second.fuzzyForums.map { it.forumName })
    }

    @Test
    fun `searches threads across Tieba`() = runTest {
        server.enqueue("""{"no":0,"error":"success","data":{"has_more":1,"current_page":2,"post_list":[{"tid":"9","title":"t"}]}}""")

        val data = dataSource.threads("k", page = 2, sort = SearchSort.NEWEST)

        val url = server.takeRequest().url
        assertEquals("k", url.queryParameter("word"))
        assertEquals("2", url.queryParameter("pn"))
        assertEquals("5", url.queryParameter("st"))
        assertEquals("1", url.queryParameter("tt"))
        assertEquals("1", url.queryParameter("ct"))
        assertEquals("1", url.queryParameter("is_use_zonghe"))
        assertEquals("99.9.101", url.queryParameter("cv"))
        assertNull(url.queryParameter("fname"))
        assertEquals(listOf("9"), data.postList.map { it.tid })
    }

    @Test
    fun `searches threads in a forum`() = runTest {
        server.enqueue("""{"no":0,"error":"success","data":{"has_more":0,"post_list":[]}}""")

        dataSource.threadsInForum("k", forumName = "测试", forumId = 52, page = 1, sort = SearchSort.IN_FORUM_RELEVANT, onlyThreads = false)

        val request = server.takeRequest()
        val url = request.url
        assertEquals("测试", url.queryParameter("fname"))
        assertEquals("2", url.queryParameter("tt"))
        assertEquals("2", url.queryParameter("st"))
        assertEquals("2", url.queryParameter("ct"))
        assertEquals("20", url.queryParameter("rn"))
        assertNull(url.queryParameter("is_use_zonghe"))
        assertEquals("22.10.1.0", url.queryParameter("cv"))
        assertEquals(true, request.headers["Referer"]?.contains("forumName=%E6%B5%8B%E8%AF%95&forumId=52"))
    }

    @Test
    fun `a nonzero no is an error`() = runTest {
        server.enqueue("""{"no":110003,"error":"请先登录","data":[]}""")

        val error = assertFailsWith<TiebaServerException> { dataSource.users("k") }

        assertEquals(110003, error.code)
        assertEquals("请先登录", error.message)
    }
}
