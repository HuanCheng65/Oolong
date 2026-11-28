package oolong.core.network.forum

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import oolong.core.network.CommonParams
import oolong.core.network.FakeDevice
import oolong.core.network.FakeInstall
import oolong.core.network.TiebaNetwork
import oolong.core.network.TiebaProfile
import oolong.core.network.TiebaServerException
import oolong.core.network.fakeSession
import oolong.core.network.protos.AppTransmitData
import oolong.core.network.protos.Error
import oolong.core.network.protos.RecommendForumInfo
import oolong.core.network.protos.forumRuleDetail.ForumRuleDetailRequest
import oolong.core.network.protos.forumRuleDetail.ForumRuleDetailResponse
import oolong.core.network.protos.forumRuleDetail.ForumRuleDetailResponseData
import oolong.core.network.protos.frsPage.FrsPageRequest
import oolong.core.network.protos.frsPage.FrsPageRequestData
import oolong.core.network.protos.frsPage.FrsPageResponse
import oolong.core.network.protos.frsPage.FrsPageResponseData
import oolong.core.network.protos.generalTabList.GeneralTabListRequest
import oolong.core.network.protos.generalTabList.GeneralTabListRequestData
import oolong.core.network.protos.generalTabList.GeneralTabListResponse
import oolong.core.network.protos.generalTabList.GeneralTabListResponseData
import oolong.core.network.protos.getForumDetail.GetForumDetailRequest
import oolong.core.network.protos.getForumDetail.GetForumDetailResponse
import oolong.core.network.protos.getForumDetail.GetForumDetailResponseData
import oolong.core.network.protos.threadList.ThreadListRequest
import oolong.core.network.protos.threadList.ThreadListResponse
import oolong.core.network.protos.threadList.ThreadListResponseData
import oolong.core.network.testing.assertProtobufParts
import oolong.core.network.testing.multipartParts
import org.junit.After
import org.junit.Before
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import oolong.core.network.protos.frsPage.AdParam as FrsAdParam
import oolong.core.network.protos.threadList.AdParam as ThreadListAdParam

class ForumRemoteDataSourceTest {
    private val server = MockWebServer()
    private val commonParams = CommonParams(
        device = FakeDevice,
        install = FakeInstall(),
        sessionProvider = { fakeSession },
        clock = { 1_700_000_000_000L },
        random = Random(0),
    )
    private lateinit var dataSource: ForumRemoteDataSource

    @Before
    fun setUp() {
        server.start()
        val network = TiebaNetwork(commonParams, { fakeSession }, baseUrlOverride = server.url("/"))
        dataSource = ForumRemoteDataSource(network, commonParams, FakeDevice)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(response: ForumRuleDetailResponse) {
        server.enqueue(MockResponse.Builder().body(Buffer().write(response.encode())).build())
    }

    private fun enqueue(bytes: ByteArray) {
        server.enqueue(MockResponse.Builder().body(Buffer().write(bytes)).build())
    }

    /** The protobuf data part of the request, after checking it was sent as the 12.52 client. */
    private fun sentData(target: String): okio.ByteString {
        val request = server.takeRequest()
        assertEquals(target, request.target)
        commonParams.headers(TiebaProfile.PROTO).forEach { (name, value) ->
            assertEquals(value, request.headers[name], name)
        }
        val parts = multipartParts(request.headers["Content-Type"]!!, request.body!!)
        lastHeaders = request.headers
        return assertProtobufParts(parts)
    }

    private var lastHeaders: okhttp3.Headers? = null

    @Test
    fun `requests a forum page's tab as the new forum page does, the name encoded`() = runTest {
        enqueue(FrsPageResponse(data_ = FrsPageResponseData()).encode())

        dataSource.page(
            "吧名", forumId = 52, tab = FrsTab(301, FrsTab.GoodType, "精华"), page = 2, loadType = 2, sortType = -1,
            goodClassId = 7, commonInfo = "{}", shownThreads = 30,
        )

        val sent = FrsPageRequest.ADAPTER.decode(sentData("/c/f/frs/page?cmd=301001&format=protobuf")).data_!!
        assertEquals("%E5%90%A7%E5%90%8D", lastHeaders!!["forum_name"])
        assertEquals(
            FrsPageRequestData(
                ad_bear_context = "",
                ad_bear_sid = "6051009917-328151198",
                ad_bear_sid_price = 0.0,
                ad_context_list = "",
                ad_ext_params = """{"pre_ad_thread_count":30,"iadex":"","req_type":1,"floor_info":"","nad_core_version":"6.54.5"}""",
                ad_external_info = "",
                ad_param = FrsAdParam(load_count = 0, refresh_count = 1, yoga_lib_version = ""),
                app_pos = commonParams.appPos(),
                app_transmit_data = AppTransmitData(carrier = 0, ismi = "", local_tz_name = FakeDevice.timeZone, yy_version = ""),
                call_from = 3,
                category_id = 0,
                chatroom_new_msg = "",
                cid = 7,
                common = commonParams.protobuf(TiebaProfile.PROTO),
                ctime = 0,
                data_size = 0,
                default_sort_type = 0,
                first_get_less_data = 0,
                first_get_normal_data = 0,
                forum_id = 52,
                frs_common_info = "{}",
                frs_tab_type_default = "",
                has_ad_bear = 0,
                hot_thread_id = 0,
                is_default_navtab = 1,
                is_general_tab = 0,
                is_good = 1,
                is_newfrs = 1,
                is_selection = 0,
                is_video_doublerow = 0,
                kw = "%E5%90%A7%E5%90%8D",
                last_click_tid = 0,
                lastids = "",
                load_type = 2,
                log_param = "%7B%7D",
                net_error = 0,
                obj_locate = "2",
                obj_source = "-2",
                partition_type = 0,
                pn = 2,
                push_tid = 0,
                q_type = 2,
                request_times = 1,
                rn = 90,
                rn_need = 30,
                scr_dip = FakeDevice.screenDensity.toDouble(),
                scr_h = FakeDevice.screenHeight,
                scr_w = FakeDevice.screenWidth,
                sort_type = -1,
                st_param = 0,
                st_type = "recom_flist",
                tab_id = 301,
                tab_name = "精华",
                tab_type = 12,
                top_card_id = "",
                up_schema = "",
                with_group = 1,
                yuelaou_locate = "",
            ),
            sent,
        )
    }

    @Test
    fun `a forum opening names no tab, and its quick half asks for no adverts`() = runTest {
        enqueue(FrsPageResponse(data_ = FrsPageResponseData()).encode())
        enqueue(FrsPageResponse(data_ = FrsPageResponseData()).encode())

        dataSource.page("吧名", forumId = 52, tab = null, page = 1, loadType = 1, sortType = -1, firstGet = FirstGet.Less, commonInfo = "{}")
        val quick = FrsPageRequest.ADAPTER.decode(sentData("/c/f/frs/page?cmd=301001&format=protobuf")).data_!!
        dataSource.page("吧名", forumId = 52, tab = null, page = 1, loadType = 1, sortType = -1, firstGet = FirstGet.Normal)
        val full = FrsPageRequest.ADAPTER.decode(sentData("/c/f/frs/page?cmd=301001&format=protobuf")).data_!!

        assertEquals(listOf(0L, 0, 0, ""), listOf(quick.forum_id, quick.tab_id, quick.tab_type, quick.tab_name))
        assertEquals("", quick.frs_common_info)
        assertEquals(1 to 0, quick.first_get_less_data to quick.first_get_normal_data)
        assertEquals(0 to 1, full.first_get_less_data to full.first_get_normal_data)
        assertEquals(FrsAdParam(load_count = 0, refresh_count = 0, yoga_lib_version = ""), quick.ad_param)
        assertEquals(FrsAdParam(load_count = 0, refresh_count = 1, yoga_lib_version = ""), full.ad_param)
        assertEquals(1 to 2, quick.request_times to full.request_times)
        assertEquals("""{"pre_ad_thread_count":0,"iadex":"","req_type":0,"floor_info":"","nad_core_version":"6.54.5"}""", full.ad_ext_params)
    }

    @Test
    fun `requests the threads of listed ids`() = runTest {
        enqueue(ThreadListResponse(data_ = ThreadListResponseData()).encode())

        dataSource.threadList(forumId = 52, forumName = "吧名", page = 1, sortType = 3, threadIds = listOf(1, 2, 3), commonInfo = "{}")

        val sent = ThreadListRequest.ADAPTER.decode(sentData("/c/f/frs/threadlist?cmd=301002&format=protobuf")).data_!!
        assertEquals("1,2,3", sent.thread_ids)
        assertEquals(52L, sent.forum_id)
        assertEquals("吧名", sent.forum_name)
        assertEquals(3, sent.sort_type)
        assertEquals(fakeSession.uid.toLong(), sent.user_id)
        assertEquals(commonParams.protobuf(TiebaProfile.PROTO), sent.common)
        assertEquals(1, sent.is_newfrs)
        assertEquals(1, sent.request_times)
        assertEquals("{}", sent.frs_common_info)
        assertEquals(ThreadListAdParam(load_count = 0, refresh_count = 0), sent.ad_param)
        assertEquals("""{"iadex":"","floor_info":"","nad_core_version":"6.54.5"}""", sent.ad_ext_params)
    }

    @Test
    fun `requests a page of a moderators' tab by number`() = runTest {
        enqueue(GeneralTabListResponse(data_ = GeneralTabListResponseData(has_more = 1)).encode())

        val data = dataSource.generalTabList(
            forumId = 52, forumName = "吧\"名", tabId = 5237271, tabName = "吧友互助", tabType = 15, page = 2, commonInfo = "{}", shownThreads = 30,
        )

        assertEquals(1, data.has_more)
        val sent = GeneralTabListRequest.ADAPTER.decode(sentData("/c/f/frs/generalTabList?cmd=309622&format=protobuf")).data_!!
        assertEquals(
            GeneralTabListRequestData(
                ad_bear_context = "",
                ad_bear_sid = "6051009917-328151198",
                ad_bear_sid_price = 0.0,
                ad_ext_params = """{"forum_name":"吧\"名","pre_ad_thread_count":30,"iadex":"","req_type":1,"floor_info":"","nad_core_version":"6.54.5","ad_context_list":""}""",
                ad_external_info = "",
                common = commonParams.protobuf(TiebaProfile.PROTO),
                forum_id = 52,
                forum_name = "吧\"名",
                frs_common_info = "{}",
                frs_tab_type_default = "",
                has_ad_bear = 0,
                is_default_navtab = 0,
                is_general_tab = 1,
                is_newfrs = 1,
                is_video_doublerow = 0,
                last_thread_id = 0,
                log_param = "%7B%7D",
                partition_type = 0,
                pn = 2,
                request_times = 1,
                rn = 30,
                scr_dip = FakeDevice.screenDensity.toInt(),
                scr_h = FakeDevice.screenHeight,
                scr_w = FakeDevice.screenWidth,
                sort_type = -1,
                tab_id = 5237271,
                tab_name = "吧友互助",
                tab_type = 15,
                top_card_id = "",
            ),
            sent,
        )
    }

    @Test
    fun `sends the forum rule request as the 12_52 client`() = runTest {
        enqueue(ForumRuleDetailResponse(data_ = ForumRuleDetailResponseData(title = "本吧吧规")))

        dataSource.forumRuleDetail(52)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/c/f/forum/forumRuleDetail?cmd=309690&format=protobuf", request.target)
        commonParams.headers(TiebaProfile.PROTO).forEach { (name, value) ->
            assertEquals(value, request.headers[name], name)
        }
        assertEquals(request.body!!.size.toString(), request.headers["Content-Length"])

        val parts = multipartParts(request.headers["Content-Type"]!!, request.body!!)
        val sent = ForumRuleDetailRequest.ADAPTER.decode(assertProtobufParts(parts))
        assertEquals(52, sent.data_?.forum_id)
        assertEquals(commonParams.protobuf(TiebaProfile.PROTO), sent.data_?.common)
    }

    @Test
    fun `asks for a forum's details`() = runTest {
        enqueue(GetForumDetailResponse(data_ = GetForumDetailResponseData(forum_info = RecommendForumInfo(forum_name = "测试"))).encode())

        val info = dataSource.forumDetail(52)

        val request = server.takeRequest()
        assertEquals("/c/f/forum/getforumdetail?cmd=303021&format=protobuf", request.target)
        val parts = multipartParts(request.headers["Content-Type"]!!, request.body!!)
        val sent = GetForumDetailRequest.ADAPTER.decode(assertProtobufParts(parts))
        assertEquals(52, sent.data_?.forum_id)
        assertEquals(commonParams.protobuf(TiebaProfile.PROTO), sent.data_?.common)
        assertEquals("测试", info.forum_name)
    }

    @Test
    fun `returns the response data`() = runTest {
        val data = ForumRuleDetailResponseData(title = "本吧吧规", preface = "请遵守")
        enqueue(ForumRuleDetailResponse(data_ = data))

        assertEquals(data, dataSource.forumRuleDetail(52))
    }

    @Test
    fun `throws the server error and keeps the response`() = runTest {
        val response = ForumRuleDetailResponse(
            error = Error(error_code = 340001, error_msg = "吧内封禁"),
            data_ = ForumRuleDetailResponseData(title = "本吧吧规"),
        )
        enqueue(response)

        val error = assertFailsWith<TiebaServerException> { dataSource.forumRuleDetail(52) }
        assertEquals(340001, error.code)
        assertEquals("吧内封禁", error.message)
        assertEquals(response, error.response)
    }
}
