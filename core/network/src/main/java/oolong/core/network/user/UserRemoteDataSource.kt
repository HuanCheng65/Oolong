package oolong.core.network.user

import oolong.core.network.CommonParams
import oolong.core.network.TiebaNetwork
import oolong.core.network.TiebaProfile
import oolong.core.network.form.signedFormBody
import oolong.core.network.form.signedWebQuery
import oolong.core.network.identity.DeviceIdentity
import oolong.core.network.protobuf.ProtobufBody
import oolong.core.network.protos.profile.ProfileRequest
import oolong.core.network.protos.profile.ProfileRequestData
import oolong.core.network.protos.profile.ProfileResponseData
import oolong.core.network.protos.userPost.UserPostRequest
import oolong.core.network.protos.userPost.UserPostRequestData
import oolong.core.network.protos.userPost.UserPostResponseData

/** A user's profile and what they posted and follow, and following them. */
class UserRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
    private val device: DeviceIdentity,
) {
    private val protoProfile = TiebaProfile.PROTO
    private val formProfile = TiebaProfile.FORM
    private val service = network.service<UserService>(protoProfile)
    private val formService = network.service<UserFormService>(formProfile)
    private val webProfile = TiebaProfile.WEB
    private val blackListService = network.service<BlackListService>(webProfile)
    private val likesService = network.service<LikesService>(webProfile)

    private val viewerUid: Long? get() = commonParams.session()?.uid?.toLongOrNull()

    /** The profile of [uid], as the user themselves or as a guest of theirs. */
    suspend fun profile(uid: Long): ProfileResponseData {
        val viewer = viewerUid
        val self = viewer == uid
        val request = ProfileRequest(
            ProfileRequestData(
                common = commonParams.protobuf(protoProfile),
                friend_uid = uid.takeIf { !self },
                friend_uid_portrait = "",
                has_plist = 1,
                is_from_usercenter = 1,
                is_guest = if (self) 0 else 1,
                need_post_count = 1,
                page = 1,
                pn = 1,
                q_type = 0,
                rn = 20,
                scr_dip = device.screenDensity.toDouble(),
                scr_h = device.screenHeight,
                scr_w = device.screenWidth,
                uid = viewer,
            )
        )
        return checkNotNull(service.profile(ProtobufBody(request, commonParams.stokenField())).data_)
    }

    /**
     * A page of the threads [uid] started, or with [threads] false of their replies.
     *
     * @param page page number, from 1
     */
    suspend fun posts(uid: Long, page: Int, threads: Boolean): UserPostResponseData {
        val request = UserPostRequest(
            UserPostRequestData(
                uid = uid,
                rn = 20,
                is_thread = if (threads) 1 else 0,
                need_content = 1,
                pn = page,
                common = commonParams.protobuf(protoProfile),
                scr_w = device.screenWidth,
                scr_h = device.screenHeight,
                scr_dip = device.screenDensity.toDouble(),
                q_type = 1,
                is_view_card = if (threads) 1 else 0,
                subtype = 0.takeUnless { threads },
            )
        )
        return checkNotNull(service.userPost(ProtobufBody(request, commonParams.stokenField())).data_)
    }

    /** A page (from 1) of the forums [uid] follows. */
    suspend fun likedForums(uid: Long, page: Int): LikedForumsResponse {
        val viewer = viewerUid
        val guest = viewer != uid
        val params = commonParams.form(formProfile) + listOfNotNull(
            "page_no" to page.toString(),
            "page_size" to "50",
            viewer?.let { "uid" to it.toString() },
            ("friend_uid" to uid.toString()).takeIf { guest },
            ("is_guest" to "1").takeIf { guest },
        )
        return formService.likedForums(signedFormBody(params, withSig = false))
    }

    /** A page (from 1) of the users [uid] follows, as far as they let others see. */
    suspend fun follows(uid: Long, page: Int): FollowListResponse {
        val params = commonParams.form(formProfile) + listOf("uid" to uid.toString(), "pn" to page.toString())
        return formService.followList(signedFormBody(params, withSig = false))
    }

    /** A page (from 1) of the users who follow [uid], as far as they let others see. */
    suspend fun fans(uid: Long, page: Int): FansResponse {
        val params = commonParams.form(formProfile) + listOf("uid" to uid.toString(), "pn" to page.toString())
        return formService.fans(signedFormBody(params, withSig = false))
    }

    /** Follows the user of [portrait]; unlike unfollowing, this is sent with `sig`. */
    suspend fun follow(portrait: String, tbs: String? = null) {
        val params = commonParams.form(formProfile, includeTbs = tbs == null) + listOfNotNull(
            "portrait" to portrait,
            "authsid" to "null",
            "from_type" to "2",
            "in_live" to "0",
            tbs?.let { "tbs" to it },
        )
        formService.follow(signedFormBody(params, withSig = true))
    }

    suspend fun unfollow(portrait: String, tbs: String? = null) {
        val params = commonParams.form(formProfile, includeTbs = tbs == null) + listOfNotNull(
            "portrait" to portrait,
            "authsid" to "null",
            "from_type" to "2",
            "in_live" to "0",
            tbs?.let { "tbs" to it },
        )
        formService.unfollow(signedFormBody(params, withSig = false))
    }

    /** What the user has barred [uid] from: following them, interacting with their posts, messaging them. */
    suspend fun blackInfo(uid: Long): BlackPermissions {
        val params = commonParams.form(formProfile) + listOf("black_uid" to uid.toString())
        return formService.blackInfo(signedFormBody(params, withSig = false)).permissions ?: BlackPermissions()
    }

    /** Page [page] (from 1) of the users the user blacklisted, with what each is barred from. */
    suspend fun blackList(page: Int): BlackListResponse =
        blackListService.page(commonParams.signedWebQuery(webProfile, listOf("pn" to page.toString(), "rn" to "30")))

    /**
     * Page [page] (from 1) of what the user liked, the latest first: the threads, or with
     * [replies] the replies, each under its thread.
     */
    suspend fun likes(replies: Boolean, page: Int): LikesResponse.Data {
        val params = listOf("tab_id" to if (replies) "2" else "1", "pn" to page.toString(), "rn" to "20")
        return likesService.page(commonParams.signedWebQuery(webProfile, params, subapp = "hybrid")).data ?: LikesResponse.Data()
    }

    /** Bars [uid] from following the user, interacting with their posts and messaging them, as each is true. */
    suspend fun setBlack(uid: Long, follow: Boolean, interact: Boolean, chat: Boolean) {
        fun flag(barred: Boolean) = if (barred) 1 else 0
        val params = commonParams.form(formProfile, includeTbs = true) + listOf(
            "black_uid" to uid.toString(),
            "perm_list" to "{\"follow\":${flag(follow)},\"interact\":${flag(interact)},\"chat\":${flag(chat)}}",
        )
        formService.setBlack(signedFormBody(params, withSig = false))
    }

    /**
     * Changes the user's own profile.
     *
     * @param birthdayTime the birthday in Unix seconds, sent back as the profile had it
     * @param sex 1 male, 2 female
     */
    suspend fun modifyProfile(nickName: String, sex: Int, intro: String, birthdayShown: Boolean, birthdayTime: Long) {
        val params = commonParams.form(formProfile) + listOf(
            "birthday_show_status" to if (birthdayShown) "1" else "0",
            "birthday_time" to birthdayTime.toString(),
            "intro" to intro,
            "sex" to sex.toString(),
            "nick_name" to nickName,
            "need_keep_nickname_flag" to "0",
        )
        formService.modifyProfile(signedFormBody(params, withSig = false))
    }
}
