package oolong.core.network

import oolong.core.network.protos.CommonRequest
import java.util.Calendar
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CommonParamsTest {
    private val startTime = localTime(2026, 11, 1)
    private var now = startTime

    private fun commonParams(
        install: FakeInstall = FakeInstall(),
        loggedIn: Boolean = true,
    ) = CommonParams(
        device = FakeDevice,
        install = install,
        sessionProvider = { fakeSession.takeIf { loggedIn } },
        clock = { now },
        random = Random(0),
    )

    @Test
    fun `builds the protobuf common request of a profile`() {
        val expected = CommonRequest(
            _client_type = 2,
            _client_version = "12.52.1.0",
            _client_id = "wappc_1700000000000_123",
            _phone_imei = "000000000000000",
            from = "1020031h",
            cuid = FakeDevice.cuid,
            _timestamp = now,
            model = "Pixel 9",
            BDUSS = "bduss-value",
            net_type = 5,
            pversion = "1.0.3",
            _os_version = "16",
            brand = "google",
            lego_lib_version = "3.0.0",
            applist = "",
            stoken = "stoken-value",
            z_id = "zid-value",
            cuid_galaxy2 = FakeDevice.cuid,
            cuid_gid = "",
            oaid = "oaid-value",
            c3_aid = FakeDevice.c3Aid,
            sample_id = "100_1-200_2",
            scr_w = 1080,
            scr_h = 2400,
            scr_dip = 2.625,
            q_type = 0,
            is_teenager = 0,
            sdk_ver = "2.34.0",
            framework_ver = "3340042",
            swan_game_ver = "1038000",
            active_timestamp = 1_700_000_100_000L,
            first_install_time = 1_600_000_000_000L,
            last_update_time = 1_650_000_000_000L,
            event_day = "2026111",
            android_id = "0123456789abcdef",
            cmode = 1,
            start_scheme = "",
            start_type = 1,
            extra = "",
            user_agent = "Mozilla/5.0 (Linux; Android 16; Pixel 9; wv) tieba/12.52.1.0",
            personalized_rec_switch = 1,
            device_score = "0.3",
            mac = "02:00:00:00:00:00",
        )
        assertEquals(expected, commonParams().protobuf(TiebaProfile.PROTO_12_52))
    }

    @Test
    fun `uses the version constants of the profile`() {
        val common = commonParams().protobuf(TiebaProfile.PROTO_22_10)

        assertEquals("22.10.1.0", common._client_version)
        assertEquals("4220001", common.framework_ver)
        assertEquals("3.36.0", common.sdk_ver)
        assertEquals("2035000", common.swan_game_ver)
        assertEquals("Mozilla/5.0 (Linux; Android 16; Pixel 9; wv) tieba/22.10.1.0", common.user_agent)
    }

    @Test
    fun `sends tbs only when asked to`() {
        assertNull(commonParams().protobuf(TiebaProfile.PROTO_12_52).tbs)
        assertEquals("tbs-value", commonParams().protobuf(TiebaProfile.PROTO_12_52, includeTbs = true).tbs)
    }

    @Test
    fun `leaves out the session when logged out`() {
        val common = commonParams(loggedIn = false).protobuf(TiebaProfile.PROTO_12_52, includeTbs = true)

        assertNull(common.BDUSS)
        assertNull(common.stoken)
        assertNull(common.tbs)
        assertNull(common.z_id)
    }

    @Test
    fun `uses a stable fallback client id until sync issues one`() {
        val params = commonParams(install = FakeInstall(clientId = null))
        val expected = "wappc_${startTime}_${Random(0).nextInt(1000)}"

        now += 60_000
        assertEquals(expected, params.protobuf(TiebaProfile.PROTO_12_52)._client_id)
        assertEquals(expected, params.protobuf(TiebaProfile.PROTO_12_52)._client_id)
    }

    @Test
    fun `builds the headers of the message task stack`() {
        val cuidBase64 = java.util.Base64.getEncoder().encodeToString(FakeDevice.cuid.toByteArray())
        val expected = listOf(
            "Charset" to "UTF-8",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 16; Pixel 9; wv) tieba/12.52.1.0",
            "client_user_token" to "12345",
            "cuid" to FakeDevice.cuid,
            "cuid_galaxy2" to FakeDevice.cuid,
            "c3_aid" to FakeDevice.c3Aid,
            "cuid_gid" to "",
            "cookie" to "ka=open;need_cookie_decrypt=0;TBBRAND=Pixel 9;DNARBBT=;CUID=${FakeDevice.cuid};" +
                "BAIDUID=0123456789ABCDEF0123456789ABCDEF:FG=1;BAIDUZID=zid-value;BAIDUCUID=$cuidBase64",
            "x_bd_data_type" to "protobuf",
        )
        assertEquals(expected, commonParams().headers(TiebaProfile.PROTO_12_52))
    }

    @Test
    fun `leaves the session out of the message task cookie when logged out`() {
        val cookie = commonParams(loggedIn = false).headers(TiebaProfile.PROTO_12_52).single { it.first == "cookie" }.second

        assertFalse("BAIDUZID" in cookie)
    }

    @Test
    fun `builds the web view headers`() {
        val cuidBase64 = java.util.Base64.getEncoder().encodeToString(FakeDevice.cuid.toByteArray())
        val expected = listOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 16; Pixel 9; wv) tieba/12.52.1.0",
            "cookie" to "BDUSS=bduss-value;STOKEN=stoken-value;BAIDUID=0123456789ABCDEF0123456789ABCDEF:FG=1;" +
                "CUID=${FakeDevice.cuid};BAIDUZID=zid-value;BAIDUCUID=$cuidBase64;TBBRAND=Pixel 9;DNARBBT=;" +
                "ka=open;need_cookie_decrypt=0",
        )
        assertEquals(expected, commonParams().headers(TiebaProfile.WEB_12_52))
    }

    @Test
    fun `leaves the session out of the web view cookie when logged out`() {
        val cookie = commonParams(loggedIn = false).headers(TiebaProfile.WEB_12_52).single { it.first == "cookie" }.second

        assertEquals(
            "BAIDUID=0123456789ABCDEF0123456789ABCDEF:FG=1;CUID=${FakeDevice.cuid};BAIDUCUID=" +
                java.util.Base64.getEncoder().encodeToString(FakeDevice.cuid.toByteArray()) +
                ";TBBRAND=Pixel 9;DNARBBT=;ka=open;need_cookie_decrypt=0",
            cookie,
        )
    }

    @Test
    fun `adds the message task fields to form parameters`() {
        val classic = commonParams().form(TiebaProfile.FORM_12_52)
        val messageTask = commonParams().form(TiebaProfile.MESSAGE_FORM_12_52)

        assertEquals(
            listOf(
                "sdk_ver" to "2.34.0",
                "framework_ver" to "3340042",
                "naws_game_ver" to "1038000",
                "extra" to "",
                "device_score" to "0.3",
                "personalized_rec_switch" to "1",
            ),
            messageTask - classic.toSet(),
        )
        assertEquals(emptyList(), classic - messageTask.toSet() - setOf("timestamp" to "$now"))
    }

    @Test
    fun `leaves out the user token header when logged out`() {
        val headers = commonParams(loggedIn = false).headers(TiebaProfile.PROTO_12_52)

        assertNull(headers.find { it.first == "client_user_token" })
    }

    @Test
    fun `builds the common parameters of a form profile`() {
        val expected = listOf(
            "_client_type" to "2",
            "_client_version" to "12.52.1.0",
            "_client_id" to "wappc_1700000000000_123",
            "from" to "1020031h",
            "net_type" to "5",
            "cuid" to FakeDevice.cuid,
            "cuid_galaxy2" to FakeDevice.cuid,
            "c3_aid" to FakeDevice.c3Aid,
            "cuid_gid" to "",
            "active_timestamp" to "1700000100000",
            "first_install_time" to "1600000000000",
            "last_update_time" to "1650000000000",
            "event_day" to "2026111",
            "cmode" to "1",
            "is_teenager" to "0",
            "start_type" to "1",
            "start_scheme" to "",
            "_phone_imei" to "000000000000000",
            "oaid" to "oaid-value",
            "model" to "Pixel 9",
            "brand" to "google",
            "android_id" to "0123456789abcdef",
            "mac" to "02:00:00:00:00:00",
            "need_decrypt" to "0",
            "timestamp" to "$now",
            "baiduid" to "0123456789ABCDEF0123456789ABCDEF:FG=1",
            "sample_id" to "100_1-200_2",
            "BDUSS" to "bduss-value",
            "stoken" to "stoken-value",
            "z_id" to "zid-value",
            "tbs" to "tbs-value",
        )
        assertEquals(expected, commonParams().form(TiebaProfile.FORM_12_52, includeTbs = true))
    }

    @Test
    fun `leaves the session out of form parameters when logged out`() {
        val names = commonParams(loggedIn = false).form(TiebaProfile.FORM_12_52, includeTbs = true).map { it.first }

        listOf("BDUSS", "stoken", "z_id", "tbs").forEach { assertFalse(it in names, it) }
    }

    @Test
    fun `builds the headers of the form stack`() {
        val expected = listOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 16; Pixel 9; wv) tieba/12.52.1.0",
            "cookie" to "ka=open;BAIDUID=0123456789ABCDEF0123456789ABCDEF:FG=1",
            "client_user_token" to "12345",
            "cuid" to FakeDevice.cuid,
            "cuid_galaxy2" to FakeDevice.cuid,
            "c3_aid" to FakeDevice.c3Aid,
            "cuid_gid" to "",
        )
        assertEquals(expected, commonParams().headers(TiebaProfile.FORM_12_52))
    }

    @Test
    fun `keeps the event day of the process start`() {
        val params = commonParams()

        now = localTime(2026, 11, 10)
        assertEquals("2026111", params.protobuf(TiebaProfile.PROTO_12_52).event_day)
    }

    @Test
    fun `does not pad months or days`() {
        now = localTime(2026, 1, 1)
        assertEquals("202611", commonParams().protobuf(TiebaProfile.PROTO_12_52).event_day)

        now = localTime(2026, 11, 10)
        assertEquals("20261110", commonParams().protobuf(TiebaProfile.PROTO_12_52).event_day)
    }

    private fun localTime(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply { set(year, month - 1, day, 12, 0, 0) }.timeInMillis
}
