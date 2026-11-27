package oolong.core.network.explore

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import mockwebserver3.MockResponse
import okio.Buffer
import okio.ByteString
import oolong.core.network.FakeDevice
import oolong.core.network.TiebaProfile
import oolong.core.network.protos.hotThreadList.HotThreadListRequest
import oolong.core.network.protos.hotThreadList.HotThreadListResponse
import oolong.core.network.protos.hotThreadList.HotThreadListResponseData
import oolong.core.network.protos.personalized.PersonalizedRequest
import oolong.core.network.protos.personalized.PersonalizedResponse
import oolong.core.network.protos.personalized.PersonalizedResponseData
import oolong.core.network.protos.topicList.TopicListRequest
import oolong.core.network.protos.topicList.TopicListResponse
import oolong.core.network.protos.topicList.TopicListResponseData
import oolong.core.network.protos.userLike.UserLikeRequest
import oolong.core.network.protos.userLike.UserLikeResponse
import oolong.core.network.protos.userLike.UserLikeResponseData
import oolong.core.network.testing.FormServer
import oolong.core.network.testing.assertProtobufParts
import oolong.core.network.testing.assertSigned
import oolong.core.network.testing.form
import oolong.core.network.testing.multipartParts
import oolong.core.network.testing.value
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals

class ExploreRemoteDataSourceTest {
    private val server = FormServer()
    private val commonParams = server.commonParams
    private val dataSource = ExploreRemoteDataSource(server.network, commonParams, FakeDevice)

    @After
    fun tearDown() = server.close()

    private fun enqueue(bytes: ByteArray) {
        server.server.enqueue(MockResponse.Builder().body(Buffer().write(bytes)).build())
    }

    private fun sentData(target: String, profile: TiebaProfile = TiebaProfile.PROTO_12_52): ByteString {
        val request = server.takeRequest()
        assertEquals(target, request.target)
        commonParams.headers(profile).forEach { (name, value) -> assertEquals(value, request.headers[name], name) }
        val parts = multipartParts(request.headers["Content-Type"]!!, request.body!!)
        return assertProtobufParts(parts)
    }

    @Test
    fun `asks for new recommendations on refresh and the next page otherwise`() = runTest {
        enqueue(PersonalizedResponse(data_ = PersonalizedResponseData()).encode())
        enqueue(PersonalizedResponse(data_ = PersonalizedResponseData()).encode())

        dataSource.personalized(refresh = true, page = 1)
        dataSource.personalized(refresh = false, page = 3)

        val target = "/c/f/excellent/personalized?cmd=309264&format=protobuf"
        val refresh = PersonalizedRequest.ADAPTER.decode(sentData(target, TiebaProfile.PROTO_22_10)).data_!!
        assertEquals(1, refresh.load_type)
        assertEquals(1, refresh.pn)
        assertEquals(0, refresh.page_thread_count)
        assertEquals(0, refresh.pre_ad_thread_count)
        assertEquals(1, refresh.request_times)
        assertEquals(1, refresh.is_newfeed)
        assertEquals(2, refresh.q_type)
        assertEquals(1, refresh.cache_column_style)
        assertEquals(commonParams.protobuf(TiebaProfile.PROTO_22_10), refresh.common)
        assertEquals(commonParams.appPos(), refresh.app_pos)
        val more = PersonalizedRequest.ADAPTER.decode(sentData(target, TiebaProfile.PROTO_22_10)).data_!!
        assertEquals(2, more.load_type)
        assertEquals(3, more.pn)
        assertEquals(12, more.page_thread_count)
        assertEquals(11, more.pre_ad_thread_count)
        assertEquals(2, more.request_times)
    }

    @Test
    fun `loads the followed users' threads from the top without a page tag`() = runTest {
        enqueue(UserLikeResponse(data_ = UserLikeResponseData()).encode())
        enqueue(UserLikeResponse(data_ = UserLikeResponseData()).encode())

        dataSource.userLike(pageTag = "", lastRequestUnix = 100)
        dataSource.userLike(pageTag = "tag", lastRequestUnix = 100)

        val first = UserLikeRequest.ADAPTER.decode(sentData("/c/f/concern/userlike?cmd=309474&format=protobuf")).data_!!
        assertEquals(1, first.loadType)
        assertEquals("", first.pageTag)
        assertEquals(100L, first.lastRequestUnix)
        assertEquals(1, first.followType)
        val next = UserLikeRequest.ADAPTER.decode(sentData("/c/f/concern/userlike?cmd=309474&format=protobuf")).data_!!
        assertEquals(2, next.loadType)
        assertEquals("tag", next.pageTag)
    }

    @Test
    fun `asks for the hot threads of a category`() = runTest {
        enqueue(HotThreadListResponse(data_ = HotThreadListResponseData()).encode())

        dataSource.hotThreads("game")

        val sent = HotThreadListRequest.ADAPTER.decode(sentData("/c/f/forum/hotThreadList?cmd=309661&format=protobuf")).data_!!
        assertEquals("game", sent.tabCode)
        assertEquals("1", sent.tabId)
    }

    @Test
    fun `asks for the whole hot topics ranking`() = runTest {
        enqueue(TopicListResponse(data_ = TopicListResponseData()).encode())

        dataSource.topics()

        val sent = TopicListRequest.ADAPTER.decode(sentData("/c/f/recommend/topicList?cmd=309289&format=protobuf")).data_!!
        assertEquals("newbang", sent.call_from)
        assertEquals("all", sent.list_type)
    }

    @Test
    fun `sends the reasons for disliking a thread as one JSON entry`() = runTest {
        server.enqueue("""{"error_code":"0"}""")

        dataSource.dislike(threadId = 9, forumId = 52, reasonIds = listOf(1, 7), extras = listOf("a", "b"), clickTime = 1700000000000)

        val request = server.takeRequest()
        assertEquals("/c/c/excellent/submitDislike", request.target)
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("homepage", form.value("dislike_from"))
        val entry = Json.parseToJsonElement(form.value("dislike")!!).jsonArray.single()
        assertEquals(
            JsonObject(
                mapOf(
                    "tid" to JsonPrimitive("9"),
                    "dislike_ids" to JsonPrimitive("1,7"),
                    "fid" to JsonPrimitive("52"),
                    "click_time" to JsonPrimitive(1700000000000),
                    "extra" to JsonPrimitive("a,b"),
                )
            ),
            entry,
        )
    }

    @Test
    fun `asks for a thread ranking signed, as the client's own pages, and reads why each thread is on it`() = runTest {
        server.enqueue(
            """{"error_code":0,"data":{"tab_list":[{"name":"浏览最多贴","tab_code":"thread_uv_num","sort_rule":"近一天吧友浏览最多的贴",""" +
                """"label_list":["近一天2.6W吧友看过"],"thread_list":[{"id":9,"tid":9,"title":"t","fname":"f","create_time":"100",""" +
                """"media":[{"type":3,"big_pic":"b","src_pic":"s","width":10,"height":20}],"first_post_content":[{"type":18,"text":"#话题#","link":"l"}],""" +
                """"thread_recommend_infos":[{"recommend_reason":"话题","topic_id":7}]}]}]}}"""
        )

        val tab = dataSource.threadRanking("thread_uv_num")!!

        val url = server.takeRequest().url
        assertEquals("/c/f/material/threadbang", url.encodedPath)
        assertEquals("thread_uv_num", url.queryParameter("tab_code"))
        assertEquals("0", url.queryParameter("is_need_tab"))
        assertEquals("tieba", url.queryParameter("subapp_type"))
        assertSigned(url.queryParameterNames.map { it to url.queryParameter(it)!! }, withSig = false)
        assertEquals(listOf("近一天2.6W吧友看过"), tab.labels)
        val thread = tab.threads.single()
        assertEquals(100, thread.createTime)
        assertEquals(listOf(WebThread.Reason("话题", 7)), thread.reasons)
        assertEquals("b", thread.media.single().toMedia().bigPic)
        assertEquals("#话题#", thread.firstPost.single().toPbContent().text)
    }

    @Test
    fun `asks for a forum ranking and reads each forum's score`() = runTest {
        server.enqueue(
            """{"error_code":0,"data":{"tab_list":[{"name":"涨粉最多","tab_code":"forum_like_num",""" +
                """"forum_list":[{"forum_id":5,"forum_name":"f","member_count":12,"is_like":1,"score_desc":"吧关注+2992"}]}]}}"""
        )

        val tab = dataSource.forumRanking("forum_like_num")!!

        val url = server.takeRequest().url
        assertEquals("/c/f/material/forumbang", url.encodedPath)
        assertEquals("forum_like_num", url.queryParameter("tab_code"))
        assertEquals(listOf(ForumRankingResponse.Forum(5, "f", "", 12, 1, "吧关注+2992")), tab.forums)
    }

    @Test
    fun `asks for a topic by name and page, and reads its cards`() = runTest {
        server.enqueue(
            """{"error_code":"0","has_more":1,"topic_info":{"topic_id":7,"topic_name":"n","idx_num":3},""" +
                """"theme_color":{"day":{"common_color":"9B8F37"}},"area_card_list":[{"card_data":[""" +
                """{"card_type":"relate_forum","data":{"forum_list":[{"id":5,"name":"f","member_num":12}]}},""" +
                """{"card_type":"relate_thread","data":{"thread_info":{"id":9,"title":"t"}}},{"card_type":"game_card","data":{}}]}]}"""
        )

        val topic = dataSource.topic(topicId = 0, name = "n", page = 2, lastThreadId = 99)

        val url = server.takeRequest().url
        assertEquals("/c/f/hottopic/topicDetail", url.encodedPath)
        assertEquals("0", url.queryParameter("topic_id"))
        assertEquals("n", url.queryParameter("topic_name"))
        assertEquals("2", url.queryParameter("pn"))
        assertEquals("10", url.queryParameter("offset"))
        assertEquals("99", url.queryParameter("last_id"))
        assertEquals(7, topic.topic!!.id)
        assertEquals("9B8F37", topic.themeColor?.day?.common)
        assertEquals(listOf("relate_forum", "relate_thread", "game_card"), topic.areas.single().cards.map { it.type })
        assertEquals("t", topic.areas.single().cards[1].data?.thread?.title)
    }
}
