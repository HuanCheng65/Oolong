package oolong.core.network.thread

import oolong.core.network.CommonParams
import oolong.core.network.TiebaNetwork
import oolong.core.network.TiebaProfile
import oolong.core.network.TiebaServerException
import oolong.core.network.form.signedFormBody
import oolong.core.network.identity.DeviceIdentity
import oolong.core.network.protobuf.ProtobufBody
import oolong.core.network.protos.pbFloor.PbFloorRequest
import oolong.core.network.protos.pbFloor.PbFloorRequestData
import oolong.core.network.protos.pbFloor.PbFloorResponseData
import oolong.core.network.protos.pbPage.AdParam
import oolong.core.network.protos.pbPage.PbPageRequest
import oolong.core.network.protos.pbPage.PbPageRequestData
import oolong.core.network.protos.pbPage.PbPageResponseData

/** A thread's posts, and what can be done to the thread and its posts from the thread page. */
class ThreadRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
    private val device: DeviceIdentity,
) {
    /** Replies inside posts are only sent with their pictures to a newer client. */
    private val pageProfile = TiebaProfile.PROTO
    private val formProfile = TiebaProfile.FORM
    private val pageService = network.service<ThreadService>(pageProfile)

    private val floorProfile = TiebaProfile.PROTO
    private val floorService = network.service<ThreadService>(floorProfile)
    private val formService = network.service<ThreadService>(formProfile)

    /**
     * One page of posts.
     *
     * @param page page number, 0 to let [postId] or [lastPostId] decide
     * @param postId post to load the page around, 0 for none
     * @param sortType 0 oldest first, 1 newest first, 2 hottest first
     * @param back whether to load the page before [postId] rather than the one containing it
     * @param last whether to start from the last page
     * @param lastPostId newest post already loaded, to load the posts after it; 0 for none
     * @param stType where the thread was opened from, for openings that report one
     * @param mark 1 when opened from the thread store
     * @param subPostsByTime whether the few replies shown under each post are the earliest ones
     *   rather than the hottest
     */
    suspend fun page(
        threadId: Long,
        page: Int = 0,
        postId: Long = 0,
        sortType: Int = 0,
        seeLz: Boolean = false,
        back: Boolean = false,
        last: Boolean = false,
        lastPostId: Long = 0,
        forumId: Long = 0,
        stType: String = "",
        mark: Int = 0,
        subPostsByTime: Boolean = false,
    ): PbPageResponseData {
        val request = PbPageRequest(
            PbPageRequestData(
                common = commonParams.protobuf(pageProfile),
                kz = threadId,
                pid = postId,
                pn = page,
                r = sortType,
                lz = if (seeLz) 1 else 0,
                last = if (last) 1 else 0,
                forum_id = forumId,
                ad_param = AdParam(load_count = 0, refresh_count = 1, is_req_ad = 1),
                mark = mark,
                last_pid = lastPostId,
                app_pos = commonParams.appPos(),
                back = if (back) 1 else 0,
                banner = 0,
                broadcast_id = 0,
                floor_rn = 4,
                floor_sort_type = if (subPostsByTime) 0 else 1,
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
                scr_dip = device.screenDensity.toDouble(),
                scr_h = device.screenHeight,
                scr_w = device.screenWidth,
                similar_from = 0,
                source_type = 2,
                st_type = stType,
                thread_type = 0,
                weipost = 0,
                with_floor = 1,
            )
        )
        val response = pageService.page(ProtobufBody(request, commonParams.stokenField()))
        return checkNotNull(response.data_)
    }

    /**
     * One page of the replies inside post [postId].
     *
     * @param subPostId a reply to load the page around, 0 for none
     * @param byTime whether the replies come earliest first, rather than the hottest first and the
     *   rest after them by time
     * @param previewIds the replies shown under the post on the thread page, which the hottest
     *   first keep in the order shown there
     */
    suspend fun floor(
        threadId: Long,
        postId: Long,
        forumId: Long,
        page: Int,
        subPostId: Long = 0,
        byTime: Boolean = false,
        previewIds: List<Long> = emptyList(),
    ): PbFloorResponseData {
        val request = PbFloorRequest(
            PbFloorRequestData(
                common = commonParams.protobuf(floorProfile),
                forum_id = forumId,
                kz = threadId,
                pid = postId,
                pn = page,
                spid = subPostId,
                scr_dip = device.screenDensity.toDouble(),
                scr_h = device.screenHeight,
                scr_w = device.screenWidth,
                is_comm_reverse = 0,
                ori_ugc_type = 0,
                sort = if (byTime) 0 else 2,
                preview_spids = if (byTime) "" else previewIds.joinToString(","),
            )
        )
        return checkNotNull(floorService.floor(ProtobufBody(request)).data_)
    }

    /** Adds the thread to the thread store, marked as read up to [postId]. */
    suspend fun addStore(threadId: Long, postId: Long) {
        val data = """[{"pid":"$postId","status":1,"tid":"$threadId"}]"""
        val params = commonParams.form(formProfile) + listOf("data" to data)
        formService.addStore(signedFormBody(params, withSig = false))
    }

    /** Removes the thread from the store; without [forumId], as the store page does, `fid` is sent as "null". */
    suspend fun removeStore(threadId: Long, forumId: Long?, tbs: String? = null) {
        val session = checkNotNull(commonParams.session()) { "Not logged in" }
        val params = commonParams.form(formProfile, includeTbs = tbs == null) + listOfNotNull(
            "tid" to threadId.toString(),
            "fid" to forumId.toString(),
            "user_id" to session.uid,
            tbs?.let { "tbs" to it },
        )
        formService.removeStore(signedFormBody(params, withSig = false))
    }

    /**
     * Ten of the thread's pictures after [picId], or with [previous] the ten before it.
     *
     * @param picIndex the position of [picId] among all the thread's pictures, from 1
     * @param objType where the pictures were opened from, "other" when unknown
     * @param postId the post the picture is in, 0 for none
     */
    suspend fun picPage(
        forumId: Long,
        forumName: String,
        threadId: Long,
        picId: String,
        picIndex: Int,
        objType: String,
        previous: Boolean,
        seeLz: Boolean,
        postId: Long,
    ): PicPageResponse {
        val params = commonParams.form(formProfile) + listOfNotNull(
            "forum_id" to forumId.toString(),
            "kw" to forumName,
            "tid" to threadId.toString(),
            "pic_id" to picId,
            "pic_index" to picIndex.toString(),
            "obj_type" to objType.ifEmpty { "other" },
            "prev" to if (previous) "10" else "0",
            "next" to if (previous) "0" else "10",
            "not_see_lz" to if (seeLz) "0" else "1",
            ("post_id" to postId.toString()).takeIf { postId != 0L },
            "page_name" to "PB",
            commonParams.session()?.let { "user_id" to it.uid },
            "scr_h" to device.screenHeight.toString(),
            "scr_w" to device.screenWidth.toString(),
            "q_type" to "2",
            "_os_version" to device.osVersion,
            "applist" to "",
        )
        return formService.picPage(signedFormBody(params, withSig = false))
    }

    /** The address of the page for reporting the post [postId]. */
    suspend fun reportUrl(postId: Long): String {
        val params = commonParams.form(formProfile) + listOf("category" to "1", "pid" to postId.toString())
        val response = formService.checkReport(signedFormBody(params, withSig = false))
        if (response.errno != 0) throw TiebaServerException(response.errno, response.errmsg, response)
        return response.data.url
    }

    /** A page (from 0) of the threads the user stored, newest first. */
    suspend fun threadStore(page: Int): ThreadStoreResponse {
        val session = checkNotNull(commonParams.session()) { "Not logged in" }
        val params = commonParams.form(formProfile) + listOf(
            "rn" to STORE_PAGE_SIZE.toString(),
            "offset" to (STORE_PAGE_SIZE * page).toString(),
            "user_id" to session.uid,
        )
        val response = formService.threadStore(signedFormBody(params, withSig = false))
        val error = response.error
        val code = error?.errno?.toIntOrNull() ?: 0
        if (code != 0) throw TiebaServerException(code, error?.errmsg.orEmpty(), response)
        return response
    }

    /**
     * Deletes a post, or with [isSubPost] a reply inside one. No reason is sent: deleting one's
     * own post has none, and moderating with a reason isn't offered.
     *
     * @param ownPost whether the post is the user's own; otherwise it is deleted as a moderator
     */
    suspend fun deletePost(
        forumId: Long,
        forumName: String,
        threadId: Long,
        postId: Long,
        ownPost: Boolean,
        isSubPost: Boolean = false,
        tbs: String? = null,
    ) {
        val params = commonParams.form(formProfile, includeTbs = tbs == null) + listOfNotNull(
            "fid" to forumId.toString(),
            "word" to forumName,
            "z" to threadId.toString(),
            "pid" to postId.toString(),
            "isfloor" to if (isSubPost) "1" else "0",
            "src" to if (isSubPost) "3" else "1",
            "is_vipdel" to if (ownPost) "0" else "1",
            ("delete_my_post" to "1").takeIf { ownPost },
            tbs?.let { "tbs" to it },
        )
        formService.deletePost(signedFormBody(params, withSig = false))
    }

    /** @param ownThread whether the thread is the user's own; otherwise it is deleted as a moderator */
    suspend fun deleteThread(
        forumId: Long,
        forumName: String,
        threadId: Long,
        ownThread: Boolean,
        tbs: String? = null,
    ) {
        val params = commonParams.form(formProfile, includeTbs = tbs == null) + listOfNotNull(
            "fid" to forumId.toString(),
            "word" to forumName,
            "z" to threadId.toString(),
            "is_vipdel" to "0",
            ("delete_my_thread" to "1").takeIf { ownThread },
            tbs?.let { "tbs" to it },
        )
        formService.deleteThread(signedFormBody(params, withSig = false))
    }

    private companion object {
        const val STORE_PAGE_SIZE = 20
    }
}
