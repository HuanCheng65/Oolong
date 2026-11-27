package oolong.core.network

import okio.ByteString.Companion.encodeUtf8
import oolong.core.network.identity.DeviceIdentity
import oolong.core.network.identity.InstallIdentity
import oolong.core.network.identity.Session
import oolong.core.network.identity.SessionProvider
import oolong.core.network.protos.AppPosInfo
import oolong.core.network.protos.CommonRequest
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Builds the common parameters and headers every request carries, from the injected identities.
 */
class CommonParams(
    private val device: DeviceIdentity,
    private val install: InstallIdentity,
    private val sessionProvider: SessionProvider,
    private val clock: () -> Long = System::currentTimeMillis,
    random: Random = Random.Default,
) {
    private val processStartTime = clock()

    /** The last `client_logid` handed out. */
    private val lastLogId = AtomicLong(0)

    /** The day the process started, not the day of each request. */
    private val eventDay = eventDay(processStartTime)

    /** Used until `c/s/sync` has issued a client id. */
    private val fallbackClientId = "wappc_${processStartTime}_${random.nextInt(1000)}"

    fun session(): Session? = sessionProvider.currentSession()

    fun userAgent(profile: TiebaProfile): String =
        "${device.webViewUserAgent} tieba/${profile.version}"

    /**
     * Headers that differ on every request of the app's own stacks: a random trace id, and a log
     * id that counts the process's requests on from the time of its first. The in-app web pages
     * send neither.
     */
    fun requestHeaders(profile: TiebaProfile): List<Pair<String, String>> {
        if (profile.stack == TiebaStack.WEB_VIEW) return emptyList()
        return listOf(
            "client_logid" to nextLogId().toString(),
            "X-Bd-Traceid" to traceId(),
        )
    }

    /** One counter for the process: the time of the first request, then one more for each. */
    private fun nextLogId(): Long = lastLogId.updateAndGet { last -> if (last == 0L) clock() else last + 1 }

    /** A random UUID in lower case without its dashes. */
    private fun traceId(): String = UUID.randomUUID().toString().lowercase().replace("-", "")

    fun headers(profile: TiebaProfile): List<Pair<String, String>> = when (profile.stack) {
        TiebaStack.MESSAGE_TASK -> messageTaskHeaders(profile)
        TiebaStack.CLASSIC -> formHeaders(profile)
        TiebaStack.WEB_VIEW -> webViewHeaders(profile)
    }

    /**
     * Headers the in-app web view adds to page requests: the user agent and a cookie with the
     * session and device identifiers, without any of the app API headers.
     */
    private fun webViewHeaders(profile: TiebaProfile): List<Pair<String, String>> {
        val session = sessionProvider.currentSession()
        val cookie = listOfNotNull(
            session?.let { "BDUSS=${it.bduss}" },
            session?.stoken?.let { "STOKEN=$it" },
            install.baiduId?.let { "BAIDUID=$it" },
            "CUID=${device.cuid}",
            session?.zid?.let { "BAIDUZID=$it" },
            "BAIDUCUID=${device.cuid.encodeUtf8().base64()}",
            "TBBRAND=${device.model}",
            "DNARBBT=",
            "ka=open",
            "need_cookie_decrypt=0",
        )
        return listOf(
            "User-Agent" to userAgent(profile),
            "cookie" to cookie.joinToString(";"),
        )
    }

    /**
     * Headers of the message task stack. `client_type` is a form parameter there, not a header,
     * and the cookie carries the full set of device identifiers, joined with ";" and no spaces.
     */
    private fun messageTaskHeaders(profile: TiebaProfile): List<Pair<String, String>> = buildList {
        val session = sessionProvider.currentSession()
        add("Charset" to "UTF-8")
        add("User-Agent" to userAgent(profile))
        session?.let { add("client_user_token" to it.uid) }
        add("cuid" to device.cuid)
        add("cuid_galaxy2" to device.cuid)
        add("c3_aid" to device.c3Aid)
        add("cuid_gid" to "")
        val cookie = listOfNotNull(
            "ka=open",
            "need_cookie_decrypt=0",
            "TBBRAND=${device.model}",
            "DNARBBT=",
            "CUID=${device.cuid}",
            install.baiduId?.let { "BAIDUID=$it" },
            session?.zid?.let { "BAIDUZID=$it" },
            "BAIDUCUID=${device.cuid.encodeUtf8().base64()}",
        )
        add("cookie" to cookie.joinToString(";"))
        if (profile.protocol == TiebaProtocol.PROTOBUF) add("x_bd_data_type" to "protobuf")
    }

    /** Headers of the classic form stack, which sends neither `client_type` nor `Charset`. */
    private fun formHeaders(profile: TiebaProfile): List<Pair<String, String>> = buildList {
        add("User-Agent" to userAgent(profile))
        add("cookie" to "ka=open;" + install.baiduId?.let { "BAIDUID=$it" }.orEmpty())
        sessionProvider.currentSession()?.let { add("client_user_token" to it.uid) }
        add("cuid" to device.cuid)
        add("cuid_galaxy2" to device.cuid)
        add("c3_aid" to device.c3Aid)
        add("cuid_gid" to "")
    }

    /**
     * Common parameters of a form request. Unlike the protobuf common request, the session goes
     * here rather than in a cookie, and there is no OS version, screen size or user agent.
     *
     * The message task stack adds a few version and device fields to the classic set.
     *
     * @param includeTbs whether to send the account's `tbs`, which write operations need
     */
    fun form(profile: TiebaProfile, includeTbs: Boolean = false): List<Pair<String, String>> {
        val session = sessionProvider.currentSession()
        return buildList {
            if (profile.stack == TiebaStack.MESSAGE_TASK) {
                add("sdk_ver" to profile.sdkVersion)
                add("framework_ver" to profile.frameworkVersion)
                add("naws_game_ver" to profile.swanGameVersion)
                add("extra" to "")
                add("device_score" to device.deviceScore.toString())
                add("personalized_rec_switch" to "1")
            }
            add("_client_type" to "2")
            add("_client_version" to profile.version)
            add("_client_id" to (install.clientId ?: fallbackClientId))
            add("from" to CHANNEL)
            add("net_type" to device.networkType.toString())
            add("cuid" to device.cuid)
            add("cuid_galaxy2" to device.cuid)
            add("c3_aid" to device.c3Aid)
            add("cuid_gid" to "")
            add("active_timestamp" to install.activeTimestamp.toString())
            add("first_install_time" to install.firstInstallTime.toString())
            add("last_update_time" to install.lastUpdateTime.toString())
            add("event_day" to eventDay)
            add("cmode" to "1")
            add("is_teenager" to "0")
            add("start_type" to "1")
            add("start_scheme" to "")
            add("_phone_imei" to device.imei)
            add("oaid" to device.oaid)
            add("model" to device.model)
            add("brand" to device.brand)
            add("android_id" to device.androidId)
            add("mac" to MAC)
            add("need_decrypt" to "0")
            add("timestamp" to clock().toString())
            install.baiduId?.let { add("baiduid" to it) }
            install.sampleId?.let { add("sample_id" to it) }
            if (session != null) {
                add("BDUSS" to session.bduss)
                session.stoken?.let { add("stoken" to it) }
                session.zid?.let { add("z_id" to it) }
                if (includeTbs) session.tbs?.let { add("tbs" to it) }
            }
        }
    }

    /**
     * The `common` field of a protobuf request.
     *
     * @param includeTbs whether to send the account's `tbs`, which write operations need
     */
    fun protobuf(profile: TiebaProfile, includeTbs: Boolean = false): CommonRequest {
        val session = sessionProvider.currentSession()
        val now = clock()
        return CommonRequest(
            _client_type = 2,
            _client_version = profile.version,
            _client_id = install.clientId ?: fallbackClientId,
            _phone_imei = device.imei,
            from = CHANNEL,
            cuid = device.cuid,
            _timestamp = now,
            model = device.model,
            BDUSS = session?.bduss,
            tbs = session?.tbs?.takeIf { includeTbs },
            net_type = device.networkType,
            pversion = "1.0.3",
            _os_version = device.osVersion,
            brand = device.brand,
            lego_lib_version = "3.0.0",
            applist = "",
            stoken = session?.stoken,
            z_id = session?.zid,
            cuid_galaxy2 = device.cuid,
            cuid_gid = "",
            oaid = device.oaid,
            c3_aid = device.c3Aid,
            sample_id = install.sampleId,
            scr_w = device.screenWidth,
            scr_h = device.screenHeight,
            scr_dip = device.screenDensity.toDouble(),
            q_type = 0,
            is_teenager = 0,
            sdk_ver = profile.sdkVersion,
            framework_ver = profile.frameworkVersion,
            swan_game_ver = profile.swanGameVersion,
            active_timestamp = install.activeTimestamp,
            first_install_time = install.firstInstallTime,
            last_update_time = install.lastUpdateTime,
            event_day = eventDay,
            android_id = device.androidId,
            cmode = 1,
            start_scheme = "",
            start_type = 1,
            extra = "",
            user_agent = userAgent(profile),
            personalized_rec_switch = 1,
            device_score = device.deviceScore.toString(),
            mac = MAC,
        )
    }

    /** The `app_pos` field of page requests: no location, connected to an access point. */
    fun appPos(): AppPosInfo = AppPosInfo(
        addr_timestamp = 0L,
        ap_connected = true,
        ap_mac = MAC,
        asp_shown_info = "",
        coordinate_type = "BD09LL",
    )

    /** The `stoken` form field protobuf requests carry next to the message, when logged in. */
    fun stokenField(): List<Pair<String, String>> =
        sessionProvider.currentSession()?.stoken?.let { listOf("stoken" to it) }.orEmpty()

    /** Year, month and day in local time, concatenated without zero padding (`2026111` for 2026-11-01). */
    private fun eventDay(time: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = time }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "$year$month$day"
    }

    private companion object {
        const val CHANNEL = "1020031h"

        /** What Android has returned for the MAC address since Android 6. */
        const val MAC = "02:00:00:00:00:00"
    }
}
