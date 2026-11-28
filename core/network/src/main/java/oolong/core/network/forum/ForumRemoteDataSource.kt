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
import oolong.core.network.protos.threadList.AdParam as ThreadListAdParam

/** A forum's page: its details, tabs and threads, and its rules. */
class ForumRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
    private val device: DeviceIdentity,
) {
    private val profile = TiebaProfile.PROTO
    private val service = network.service<ForumService>(profile)

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
     * @param forumId the forum's id, 0 until a page has told it
     * @param tab the tab to load, or null for the one the forum opens on
     * @param page page number, from 1
     * @param loadType 1 for the first page or a refresh, 2 for the next page
     * @param sortType the tab's order, as its sort menu offers them; -1 for the tab's own
     * @param goodClassId the class of good threads to load, 0 for all of them
     * @param firstGet for a first page loaded twice at once, as the forum page opens: which of the
     *   two this is, the quick one with the pinned threads or the full one
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
    ): FrsPageResponseData {
        val encodedName = URLEncoder.encode(forumName, "UTF-8")
        val request = FrsPageRequest(
            FrsPageRequestData(
                kw = encodedName,
                rn = 90,
                rn_need = 30,
                is_good = if (tab?.type == FrsTab.GoodType) 1 else 0,
                cid = goodClassId,
                with_group = 1,
                scr_w = device.screenWidth,
                scr_h = device.screenHeight,
                scr_dip = device.screenDensity.toDouble(),
                q_type = 2,
                pn = page,
                st_type = "",
                ctime = 0,
                data_size = 0,
                net_error = 0,
                st_param = 0,
                common = commonParams.protobuf(profile),
                lastids = "",
                category_id = 0,
                yuelaou_locate = "",
                sort_type = sortType,
                last_click_tid = 0,
                load_type = loadType,
                app_pos = commonParams.appPos(),
                ad_param = AdParam(load_count = if (loadType == 1) 0 else page - 1, refresh_count = if (loadType == 1) 1 else 0, yoga_lib_version = ""),
                obj_locate = "2",
                obj_source = "-2",
                is_selection = 0,
                call_from = 206,
                hot_thread_id = 0,
                is_default_navtab = if (tab == null) 1 else 0,
                ad_context_list = "",
                up_schema = "",
                default_sort_type = 0,
                request_times = 0,
                is_newfrs = 1,
                first_get_less_data = if (firstGet == FirstGet.Less) 1 else 0,
                first_get_normal_data = if (firstGet == FirstGet.Normal) 1 else 0,
                forum_id = forumId,
                tab_id = tab?.id ?: 0,
                tab_type = tab?.type ?: 0,
                tab_name = tab?.name.orEmpty(),
                is_general_tab = if (tab?.general == true) 1 else 0,
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
    ): ThreadListResponseData {
        val request = ThreadListRequest(
            ThreadListRequestData(
                ad_param = ThreadListAdParam(load_count = 3, refresh_count = 0),
                app_pos = commonParams.appPos(),
                common = commonParams.protobuf(profile),
                scr_dip = device.screenDensity.toDouble(),
                scr_h = device.screenHeight,
                scr_w = device.screenWidth,
                forum_id = forumId,
                forum_name = forumName,
                pn = page,
                q_type = 2,
                user_id = commonParams.session()?.uid?.toLongOrNull(),
                thread_ids = threadIds.joinToString(","),
                sort_type = sortType,
                need_abstract = 0,
                st_type = 0,
                last_click_tid = 0,
                request_times = 0,
                is_newfrs = 1,
                is_goodtab = if (good) 1 else 0,
            )
        )
        return checkNotNull(service.threadList(ProtobufBody(request, commonParams.stokenField())).data_)
    }

    /**
     * One page of a tab the forum's moderators set up.
     *
     * @param page page number, from 1
     * @param lastThreadId the last thread of the previous page, 0 for the first
     */
    suspend fun generalTabList(
        forumId: Long,
        forumName: String,
        tabId: Int,
        tabName: String,
        tabType: Int,
        page: Int,
        lastThreadId: Long,
    ): GeneralTabListResponseData {
        val request = GeneralTabListRequest(
            GeneralTabListRequestData(
                common = commonParams.protobuf(profile),
                tab_id = tabId,
                forum_id = forumId,
                pn = page,
                rn = 30,
                scr_w = device.screenWidth,
                scr_h = device.screenHeight,
                scr_dip = device.screenDensity.toInt(),
                last_thread_id = lastThreadId,
                is_default_navtab = 0,
                tab_name = tabName,
                is_general_tab = 1,
                sort_type = -1,
                tab_type = tabType,
                ad_ext_params = "",
                ad_bear_context = "",
                has_ad_bear = 0,
                ad_bear_sid = "",
                ad_bear_sid_price = 0.0,
                request_times = 0,
                frs_common_info = "",
                is_newfrs = 1,
                is_video_doublerow = 0,
                forum_name = forumName,
                partition_type = 0,
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
    }
}

/** Which half of a first page loaded twice at once a frs/page request is. */
enum class FirstGet { None, Less, Normal }
