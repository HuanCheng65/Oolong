package oolong.core.network.account

import oolong.core.network.CommonParams
import oolong.core.network.TiebaNetwork
import oolong.core.network.TiebaProfile
import oolong.core.network.form.signedFormBody
import oolong.core.network.identity.DeviceIdentity
import oolong.core.network.protobuf.ProtobufBody
import oolong.core.network.protos.User
import oolong.core.network.protos.getUserInfo.GetUserInfoRequest
import oolong.core.network.protos.getUserInfo.GetUserInfoRequestData

/** What the client tells the server about itself when it syncs, beyond the common parameters. */
data class SyncInfo(
    val clientId: String?,
    /** Whether notifications are on now ("1" or "0"), and were before. */
    val pushSwitch: String,
    val lastPushSwitch: String,
    val lastPushSwitchTime: Long,
    /** "64" or "32". */
    val runningAbi: String,
    val supportAbi: String,
    val installTime: Long,
    val board: String,
    val incremental: String,
)

/**
 * Logging in and keeping the session and installation current. Logging in takes the BDUSS and
 * STOKEN to use, which need not be the current account's.
 */
class AccountRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
    private val device: DeviceIdentity,
) {
    private val formProfile = TiebaProfile.FORM
    private val protoProfile = TiebaProfile.PROTO
    private val formService = network.service<AccountFormService>(formProfile)
    private val service = network.service<AccountService>(protoProfile)

    /** Which account [bduss] belongs to. */
    suspend fun login(bduss: String, stoken: String, firstLogin: Boolean): LoginResponse {
        val business = listOf(
            "bdusstoken" to "$bduss|",
            "stoken" to stoken,
            "first_login" to if (firstLogin) "1" else "0",
            "channel_id" to "",
            "channel_uid" to "",
            "send_tb" to "0",
        )
        return formService.login(signedFormBody(form(business, omit = setOf("BDUSS")), withSig = false))
    }

    suspend fun initNickname(bduss: String, stoken: String) {
        val business = listOf("BDUSS" to bduss, "stoken" to stoken)
        formService.initNickname(signedFormBody(form(business), withSig = false))
    }

    /** The profile of [uid], asked for as the account of [bduss]. */
    suspend fun userInfo(uid: Long, bduss: String, stoken: String): User {
        val request = GetUserInfoRequest(
            GetUserInfoRequestData(
                common = commonParams.protobuf(protoProfile).copy(BDUSS = bduss, stoken = stoken),
                uid = uid,
                scr_w = device.screenWidth,
            )
        )
        return checkNotNull(service.userInfo(ProtobufBody(request, listOf("stoken" to stoken))).data_?.user) { "No user" }
    }

    /** Registers the installation, with the device fields the common parameters carry encoded. */
    suspend fun sync(info: SyncInfo): SyncResponse {
        val business = listOfNotNull(
            info.clientId?.let { "_client_id" to it },
            "push_system_switch" to info.pushSwitch,
            "last_push_system_switch" to info.lastPushSwitch,
            "last_push_system_switch_time" to info.lastPushSwitchTime.toString(),
            "running_abi" to info.runningAbi,
            "support_abi" to info.supportAbi,
            "install_app_time" to info.installTime.toString(),
            "_os_version" to device.osVersion,
            "brand" to device.brand,
            "model" to device.model,
            "board" to info.board,
            "incremental" to info.incremental,
            "_phone_screen" to "${device.screenWidth},${device.screenHeight}",
            "scr_w" to device.screenWidth.toString(),
            "scr_h" to device.screenHeight.toString(),
            "scr_dip" to device.screenDensity.toString(),
            "_msg_status" to "1",
            "_active" to "0",
            "_pic_quality" to "0",
            "package" to "com.baidu.tieba",
            "versioncode" to "369754368",
            "signmd5" to "225172691",
            "md5" to "F86F4C238491AB3BEBFA33AC42C1582B",
        )
        return formService.sync(signedFormBody(form(business), withSig = false))
    }

    /** The common parameters with [business] in place of any of the same name, and without [omit]. */
    private fun form(business: List<Pair<String, String>>, omit: Set<String> = emptySet()): List<Pair<String, String>> {
        val names = business.mapTo(HashSet()) { it.first } + omit
        return commonParams.form(formProfile).filter { it.first !in names } + business
    }
}
