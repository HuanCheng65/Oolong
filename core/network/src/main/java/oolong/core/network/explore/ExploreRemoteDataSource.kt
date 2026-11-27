package oolong.core.network.explore

import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import oolong.core.network.CommonParams
import oolong.core.network.TiebaNetwork
import oolong.core.network.TiebaProfile
import oolong.core.network.form.signedFormBody
import oolong.core.network.form.signedWebQuery
import oolong.core.network.identity.DeviceIdentity
import oolong.core.network.protobuf.ProtobufBody
import oolong.core.network.protos.hotThreadList.HotThreadListRequest
import oolong.core.network.protos.hotThreadList.HotThreadListRequestData
import oolong.core.network.protos.hotThreadList.HotThreadListResponseData
import oolong.core.network.protos.personalized.PersonalizedRequest
import oolong.core.network.protos.personalized.PersonalizedRequestData
import oolong.core.network.protos.personalized.PersonalizedResponseData
import oolong.core.network.protos.topicList.TopicListRequest
import oolong.core.network.protos.topicList.TopicListRequestData
import oolong.core.network.protos.topicList.TopicListResponseData
import oolong.core.network.protos.userLike.UserLikeRequest
import oolong.core.network.protos.userLike.UserLikeRequestData
import oolong.core.network.protos.userLike.UserLikeResponseData
import java.util.concurrent.atomic.AtomicInteger

/** The feeds of the explore tab: recommended threads, the followed users' threads and the hot lists. */
class ExploreRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
    private val device: DeviceIdentity,
) {
    private val protoProfile = TiebaProfile.PROTO
    private val formProfile = TiebaProfile.FORM
    private val service = network.service<ExploreService>(protoProfile)
    private val formService = network.service<ExploreFormService>(formProfile)
    private val webProfile = TiebaProfile.WEB
    private val dislikedForumService = network.service<DislikedForumService>(webProfile)
    private val hotListService = network.service<HotListService>(webProfile)

    // Recommendations come as the newer clients get them, with their newer ways of disliking a thread
    private val personalizedProfile = TiebaProfile.PROTO
    private val personalizedService = network.service<ExploreService>(personalizedProfile)

    /** How many times recommendations were asked for, which the server counts as the feed goes on. */
    private val personalizedRequests = AtomicInteger()

    /**
     * A page of recommended threads.
     *
     * @param refresh whether the user pulled to refresh, rather than scrolled to the end
     * @param page page number, from 1
     */
    suspend fun personalized(refresh: Boolean, page: Int): PersonalizedResponseData {
        val request = PersonalizedRequest(
            PersonalizedRequestData(
                app_pos = commonParams.appPos(),
                common = commonParams.protobuf(personalizedProfile),
                load_type = if (refresh) 1 else 2,
                pn = page,
                need_tags = 0,
                // A refresh leaves the count to the server; later pages ask for a dozen
                page_thread_count = if (refresh) 0 else 12,
                pre_ad_thread_count = if (refresh) 0 else 11,
                sug_count = 0,
                tag_code = 0,
                q_type = 2,
                need_forumlist = 0,
                new_net_type = 1,
                new_install = 0,
                request_times = personalizedRequests.incrementAndGet(),
                invoke_source = "",
                scr_dip = device.screenDensity.toDouble(),
                scr_h = device.screenHeight,
                scr_w = device.screenWidth,
                is_newfeed = 1,
                is_new_user = 0,
                cache_column_style = 1,
            )
        )
        return checkNotNull(personalizedService.personalized(ProtobufBody(request, commonParams.stokenField())).data_)
    }

    /**
     * A page of what the users the user follows posted.
     *
     * @param pageTag the tag the previous page returned, empty for the first page
     * @param lastRequestUnix the `requestUnix` of the last first page loaded, 0 if none
     */
    suspend fun userLike(pageTag: String, lastRequestUnix: Long): UserLikeResponseData {
        val request = UserLikeRequest(
            UserLikeRequestData(
                common = commonParams.protobuf(protoProfile),
                pageTag = pageTag,
                lastRequestUnix = lastRequestUnix,
                followType = 1,
                loadType = if (pageTag.isEmpty()) 1 else 2,
            )
        )
        return checkNotNull(service.userLike(ProtobufBody(request, commonParams.stokenField())).data_)
    }

    /** The hot threads in the category [tabCode] ("all" for every category), with the categories and hot topics. */
    suspend fun hotThreads(tabCode: String): HotThreadListResponseData {
        val request = HotThreadListRequest(
            HotThreadListRequestData(
                common = commonParams.protobuf(protoProfile),
                tabCode = tabCode,
                tabId = "1",
            )
        )
        return checkNotNull(service.hotThreadList(ProtobufBody(request, commonParams.stokenField())).data_)
    }

    /** The hot topics ranking. */
    suspend fun topics(): TopicListResponseData {
        val request = TopicListRequest(
            TopicListRequestData(
                common = commonParams.protobuf(protoProfile),
                call_from = "newbang",
                list_type = "all",
                need_tab_list = "0",
                fid = 0L,
            )
        )
        return checkNotNull(service.topicList(ProtobufBody(request, commonParams.stokenField())).data_)
    }

    /** The threads that did best over the last day by the measure [tabCode], such as `thread_uv_num` for most read. */
    suspend fun threadRanking(tabCode: String): ThreadRankingResponse.Tab? {
        val response = hotListService.threadRanking(rankingParams("tab_code" to tabCode, "is_need_tab" to "0"))
        return response.data?.tabs?.firstOrNull { it.threads.isNotEmpty() }
    }

    /** The forums that did best over the last day by the measure [tabCode], such as `forum_like_num` for most followed. */
    suspend fun forumRanking(tabCode: String): ForumRankingResponse.Tab? {
        val response = hotListService.forumRanking(rankingParams("tab_code" to tabCode, "is_need_tab" to "0"))
        return response.data?.tabs?.firstOrNull { it.forums.isNotEmpty() }
    }

    /**
     * Page [page] (from 1) of the hot topic [topicId], or of the one named [name] when the id isn't
     * known, as in links in posts that carry only the name.
     *
     * @param lastThreadId the id of the last thread of the page before, 0 for the first
     */
    suspend fun topic(topicId: Long, name: String, page: Int, lastThreadId: Long): TopicResponse =
        hotListService.topic(
            mapOf(
                "topic_id" to topicId.toString(),
                "topic_name" to name,
                "is_new" to "1",
                "is_share" to "1",
                "pn" to page.toString(),
                "rn" to TopicPageSize.toString(),
                "offset" to ((page - 1) * TopicPageSize).toString(),
                "last_id" to lastThreadId.toString(),
                "_client_type" to "2",
                "_client_version" to webProfile.version,
            )
        )

    /**
     * Tells the server the user doesn't want to see threads like [threadId].
     *
     * @param reasonIds the ids of the reasons the user picked
     * @param extras the `extra` of each reason picked
     * @param clickTime when the user opened the menu, in Unix milliseconds
     */
    suspend fun dislike(threadId: Long, forumId: Long?, reasonIds: List<Int>, extras: List<String>, clickTime: Long) {
        val dislike = buildJsonArray {
            addJsonObject {
                put("tid", threadId.toString())
                put("dislike_ids", reasonIds.joinToString(","))
                forumId?.let { put("fid", it.toString()) }
                put("click_time", clickTime)
                put("extra", extras.joinToString(","))
            }
        }
        val params = commonParams.form(formProfile) + listOf(
            "dislike" to dislike.toString(),
            "dislike_from" to "homepage",
        )
        formService.submitDislike(signedFormBody(params, withSig = false))
    }

    /** Page [page] (from 1) of the forums user [userId] keeps out of their recommendations. */
    suspend fun dislikedForums(userId: Long, page: Int): DislikedForumsResponse =
        dislikedForumService.list(webParams("user_id" to userId.toString(), "pn" to page.toString(), "rn" to "20"))

    /** Lets forum [forumId] back into the recommendations of user [userId]. */
    suspend fun cancelDislikedForum(userId: Long, forumId: Long) {
        dislikedForumService.cancel(webParams("forum_id" to forumId.toString(), "user_id" to userId.toString()))
    }

    private fun webParams(vararg params: Pair<String, String>): Map<String, String> =
        commonParams.signedWebQuery(webProfile, params.toList() + ("client_type" to "8"))

    private fun rankingParams(vararg params: Pair<String, String>): Map<String, String> =
        commonParams.signedWebQuery(webProfile, params.toList(), subapp = "tieba")
}

private const val TopicPageSize = 10
