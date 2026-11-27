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
     * One page of the forum's threads, with the forum's details and tabs. The page holds the
     * first threads and the ids of the rest, which [threadList] loads.
     *
     * The forum name goes percent-encoded, both in the request and in a `forum_name` header.
     *
     * @param page page number, from 1
     * @param loadType 1 for the first page or a refresh, 2 for the next page
     * @param sortType 0 by reply time, 1 by post time, 2 by liking users, 3 smart, 4 by likes, or
     *   null for the order the server picks
     * @param good whether to load the good threads, of [goodClassId] or of every class for 0
     */
    suspend fun page(
        forumName: String,
        page: Int,
        loadType: Int,
        sortType: Int?,
        good: Boolean = false,
        goodClassId: Int = 0,
    ): FrsPageResponseData {
        val encodedName = URLEncoder.encode(forumName, "UTF-8")
        val request = FrsPageRequest(
            FrsPageRequestData(
                ad_param = AdParam(load_count = 0, refresh_count = 4, yoga_lib_version = "1.0"),
                app_pos = commonParams.appPos(),
                call_from = 0,
                category_id = 0,
                cid = goodClassId,
                common = commonParams.protobuf(profile),
                ctime = 0,
                data_size = 0,
                hot_thread_id = 0,
                is_default_navtab = 0,
                is_good = if (good) 1 else 0,
                is_selection = 0,
                kw = encodedName,
                last_click_tid = 0,
                load_type = loadType,
                net_error = 0,
                pn = page,
                q_type = 2,
                rn = 90,
                rn_need = 30,
                scr_dip = device.screenDensity.toDouble(),
                scr_h = device.screenHeight,
                scr_w = device.screenWidth,
                sort_type = sortType,
                st_param = 0,
                st_type = "recom_flist",
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
            )
        )
        return checkNotNull(service.generalTabList(ProtobufBody(request, commonParams.stokenField())).data_)
    }
}
