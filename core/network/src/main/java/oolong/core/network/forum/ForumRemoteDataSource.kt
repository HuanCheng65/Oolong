package oolong.core.network.forum

import oolong.core.network.CommonParams
import oolong.core.network.TiebaNetwork
import oolong.core.network.TiebaProfile
import oolong.core.network.identity.DeviceIdentity
import oolong.core.network.protobuf.ProtobufBody
import oolong.core.network.protos.RecommendForumInfo
import oolong.core.network.protos.forumRuleDetail.ForumRuleDetailRequest
import oolong.core.network.protos.forumRuleDetail.ForumRuleDetailRequestData
import oolong.core.network.protos.forumRuleDetail.ForumRuleDetailResponseData
import oolong.core.network.protos.frsPage.AdParam
import oolong.core.network.protos.frsPage.FrsPageRequest
import oolong.core.network.protos.frsPage.FrsPageRequestData
import oolong.core.network.protos.frsPage.FrsPageResponseData
import oolong.core.network.protos.generalTabList.GeneralTabListRequest
import oolong.core.network.protos.generalTabList.GeneralTabListRequestData
import oolong.core.network.protos.generalTabList.GeneralTabListResponseData
import oolong.core.network.protos.getForumDetail.GetForumDetailRequest
import oolong.core.network.protos.getForumDetail.GetForumDetailRequestData
import oolong.core.network.protos.threadList.ThreadListRequest
import oolong.core.network.protos.threadList.ThreadListRequestData
import oolong.core.network.protos.threadList.ThreadListResponseData
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import oolong.core.network.protos.threadList.AdParam as ThreadListAdParam

/** A forum's page: its details, tabs and threads, and its rules. */
class ForumRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
    private val device: DeviceIdentity,
) {
    private val profile = TiebaProfile.PROTO
    private val service = network.service<ForumService>(profile)

    // How many of each the app has asked for since it started, as the forum page counts them
    private val pageRequests = AtomicInteger()
    private val threadListRequests = AtomicInteger()
    private val generalTabRequests = AtomicInteger()

    suspend fun forumRuleDetail(forumId: Long): ForumRuleDetailResponseData {
        val request = ForumRuleDetailRequest(
            ForumRuleDetailRequestData(
                common = commonParams.protobuf(profile),
                forum_id = forumId,
            )
        )
        val response = service.forumRuleDetail(ProtobufBody(request, commonParams.stokenField()))
        return checkNotNull(response.data_)
    }

    /** What the forum's info page shows: its avatar, counts, slogan and introduction. */
    suspend fun forumDetail(forumId: Long): RecommendForumInfo {
        val request = GetForumDetailRequest(
            GetForumDetailRequestData(
                common = commonParams.protobuf(profile),
                forum_id = forumId,
            )
        )
        val response = service.forumDetail(ProtobufBody(request, commonParams.stokenField()))
        return checkNotNull(response.data_?.forum_info) { "No forum info" }
    }

    /**
     * One page of a tab of the forum's threads, with the forum's details and tabs, as the forum
     * page asks for it: the threads come laid out in `page_data`, and the ids of more of them in
     * `thread_id_list`, which [threadList] loads.
     *
     * The forum name goes percent-encoded, both in the request and in a `forum_name` header.
     *
     * @param forumId the forum's id; 0, with no [tab], for the forum page opening on the tab the
     *   forum picks
     * @param tab the tab to load, or null for the one the forum opens on
     * @param page page number, from 1
     * @param loadType 1 for the first page or a refresh, 2 for the next page
     * @param sortType the tab's order, as its sort menu offers them; -1 for the tab's own
     * @param goodClassId the class of good threads to load, 0 for all of them
     * @param firstGet for a first page loaded twice at once, as the forum page opens: which of the
     *   two this is, the quick one with the pinned threads or the full one
     * @param commonInfo what the forum's last page gave to send back, `frs_common_info`
     * @param shownThreads how many threads the list shows above this page, for loading more
     */
    suspend fun page(
        forumName: String,
        forumId: Long,
        tab: FrsTab?,
        page: Int,
        loadType: Int,
        sortType: Int,
        goodClassId: Int = 0,
        firstGet: FirstGet = FirstGet.None,
        commonInfo: String = "",
        shownThreads: Int = 0,
    ): FrsPageResponseData {
        val encodedName = URLEncoder.encode(forumName, "UTF-8")
        val more = loadType != 1
        val request = FrsPageRequest(
            FrsPageRequestData(
                ad_bear_context = "",
                ad_bear_sid = FrsAds.bearSid(device.networkType),
                ad_bear_sid_price = 0.0,
                ad_context_list = "",
                ad_ext_params = FrsAds.extParams(preThreads = if (more) shownThreads else 0, requestType = if (more) 1 else 0),
                ad_external_info = "",
                // The quick half of a first page asks for no adverts; every other page as if none were seen
                ad_param = if (firstGet == FirstGet.Less) AdParam(load_count = 0, refresh_count = 0, yoga_lib_version = "")
                else AdParam(load_count = 0, refresh_count = 1, yoga_lib_version = ""),
                app_pos = commonParams.appPos(),
                app_transmit_data = FrsAds.transmitData(device),
                call_from = FrsEntry.CallFrom,
                category_id = 0,
                chatroom_new_msg = "",
                cid = goodClassId,
                common = commonParams.protobuf(profile),
                ctime = 0,
                data_size = 0,
                default_sort_type = 0,
                first_get_less_data = if (firstGet == FirstGet.Less) 1 else 0,
                first_get_normal_data = if (firstGet == FirstGet.Normal) 1 else 0,
                forum_id = if (tab == null) 0 else forumId,
                frs_common_info = if (tab == null) "" else commonInfo,
                frs_tab_type_default = "",
                has_ad_bear = 0,
                hot_thread_id = 0,
                is_default_navtab = 1,
                is_general_tab = if (tab?.general == true) 1 else 0,
                is_good = if (tab?.type == FrsTab.GoodType) 1 else 0,
                is_newfrs = 1,
                is_selection = 0,
                is_video_doublerow = if (tab?.type == FrsTab.VideoType) 1 else 0,
                kw = encodedName,
                last_click_tid = 0,
                lastids = "",
                load_type = loadType,
                log_param = FrsEntry.LogParam,
                net_error = 0,
                obj_locate = "2",
                obj_source = "-2",
                partition_type = 0,
                pn = page,
                push_tid = 0,
                q_type = 2,
                request_times = pageRequests.incrementAndGet(),
                rn = 90,
                rn_need = 30,
                scr_dip = device.screenDensity.toDouble(),
                scr_h = device.screenHeight,
                scr_w = device.screenWidth,
                sort_type = sortType,
                st_param = 0,
                st_type = FrsEntry.StType,
                tab_id = tab?.id ?: 0,
                tab_name = tab?.name.orEmpty(),
                tab_type = tab?.type ?: 0,
                top_card_id = "",
                up_schema = "",
                with_group = 1,
                yuelaou_locate = "",
            )
        )
        return checkNotNull(service.page(ProtobufBody(request, commonParams.stokenField()), encodedName).data_)
    }

    /** The threads of [threadIds], ids a [page] listed after its first threads. */
    suspend fun threadList(
        forumId: Long,
        forumName: String,
        page: Int,
        sortType: Int?,
        threadIds: List<Long>,
        good: Boolean = false,
        commonInfo: String = "",
        shownThreads: Int = 0,
    ): ThreadListResponseData {
        val request = ThreadListRequest(
            ThreadListRequestData(
                ad_bear_context = "",
                ad_bear_sid = FrsAds.bearSid(device.networkType),
                ad_bear_sid_price = 0.0,
                ad_context_list = "",
                ad_ext_params = FrsAds.threadListExtParams(shownThreads),
                ad_external_info = "",
                ad_param = ThreadListAdParam(load_count = 0, refresh_count = 0),
                app_pos = commonParams.appPos(),
                app_transmit_data = FrsAds.transmitData(device),
                common = commonParams.protobuf(profile),
                forum_id = forumId,
                forum_name = forumName,
                frs_common_info = commonInfo,
                has_ad_bear = 0,
                is_goodtab = if (good) 1 else 0,
                is_newfrs = 1,
                last_click_tid = 0,
                log_param = FrsEntry.LogParam,
                need_abstract = 0,
                pn = page,
                q_type = 2,
                request_times = threadListRequests.incrementAndGet(),
                scr_dip = device.screenDensity.toDouble(),
                scr_h = device.screenHeight,
                scr_w = device.screenWidth,
                sort_type = sortType,
                st_type = 0,
                thread_ids = threadIds.joinToString(","),
                user_id = commonParams.session()?.uid?.toLongOrNull() ?: 0,
            )
        )
        return checkNotNull(service.threadList(ProtobufBody(request, commonParams.stokenField())).data_)
    }

    /**
     * One page of a tab the forum's moderators set up, or of its videos.
     *
     * @param page page number, from 1
     * @param commonInfo what the forum's last page gave to send back, `frs_common_info`
     * @param shownThreads how many threads the list shows above this page
     */
    suspend fun generalTabList(
        forumId: Long,
        forumName: String,
        tabId: Int,
        tabName: String,
        tabType: Int,
        page: Int,
        commonInfo: String = "",
        shownThreads: Int = 0,
    ): GeneralTabListResponseData {
        val request = GeneralTabListRequest(
            GeneralTabListRequestData(
                ad_bear_context = "",
                ad_bear_sid = FrsAds.bearSid(device.networkType),
                ad_bear_sid_price = 0.0,
                ad_ext_params = FrsAds.generalTabExtParams(forumName, preThreads = shownThreads, requestType = if (page > 1) 1 else 0),
                ad_external_info = "",
                common = commonParams.protobuf(profile),
                forum_id = forumId,
                forum_name = forumName,
                frs_common_info = commonInfo,
                frs_tab_type_default = "",
                has_ad_bear = 0,
                is_default_navtab = 0,
                is_general_tab = 1,
                is_newfrs = 1,
                is_video_doublerow = if (tabType == FrsTab.VideoType) 1 else 0,
                // The forum page never sets it: pages go by number alone
                last_thread_id = 0,
                log_param = FrsEntry.LogParam,
                partition_type = 0,
                pn = page,
                request_times = generalTabRequests.incrementAndGet(),
                rn = 30,
                scr_dip = device.screenDensity.toInt(),
                scr_h = device.screenHeight,
                scr_w = device.screenWidth,
                sort_type = -1,
                tab_id = tabId,
                tab_name = tabName,
                tab_type = tabType,
                top_card_id = "",
            )
        )
        return checkNotNull(service.generalTabList(ProtobufBody(request, commonParams.stokenField())).data_)
    }
}

/**
 * A tab of the forum page, as frs/page names it: its id and type, its name, and whether it is
 * one of the forum's own sections.
 */
data class FrsTab(val id: Int, val type: Int, val name: String, val general: Boolean = false) {
    companion object {
        /** The good threads' tab's type. */
        const val GoodType = 12

        /** The videos' tab's type, which lists them two to a row. */
        const val VideoType = 100
    }
}

/** Which half of a first page loaded twice at once a frs/page request is. */
enum class FirstGet { None, Less, Normal }
