package oolong.core.network

import oolong.core.network.protos.CommonRequest
import java.util.Calendar
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
            BDUSS = "bduss-value",
            _client_type = 2,
            _client_version = "22.10.1.0",
            _timestamp = now,
            active_timestamp = 1_700_000_100_000L,
            ad_context = "",
            applist = "",
            c3_aid = FakeDevice.c3Aid,
            cam = "",
            cmode = 1,
            cuid = FakeDevice.cuid,
            cuid_galaxy2 = FakeDevice.cuid,
            cuid_gid = "",
            device_score = "0.3",
            di_diordna = "gavu8gaiv8jcOEzOI4E8oqqSB",
            diao = "oaid-value",
            dnarb = "Npm-oNCwA",
            event_day = "2026111",
            extra = "",
            first_install_time = 1_600_000_000_000L,
            framework_ver = "4220001",
            from = "1020031h",
            iemi = "_a2q8_aq28_qa28qALNqA",
            is_teenager = 0,
            last_update_time = 1_650_000_000_000L,
            ledom = "pJGgzNjOoqqqB",
            lego_lib_version = "3.0.0",
            naws_game_ver = "2035000",
            need_cam_decrypt = 1,
            need_decrypt = 1,
            net_type = 5,
            noisrev_so = "mmqqC",
            package_version = "hybrid-main-pb_1.0.386.1",
            personalized_rec_switch = 1,
            pversion = "1.0.3",
            q_type = 0,
            sample_id = "100_1-200_2",
            scr_dip = 2.625,
            scr_h = 2400,
            scr_w = 1080,
            sdk_ver = "3.36.0",
            start_scheme = "",
            start_type = 1,
            stoken = "stoken-value",
            support_image = "jepgwebp",
            user_agent = "Mozilla/5.0 (Linux; Android 16; Pixel 9; wv) tieba/22.10.1.0",
            z_id = "zid-value",
        )
        assertEquals(expected, commonParams().protobuf(TiebaProfile.PROTO))
    }

    @Test
    fun `uses the version constants of the profile`() {
        val common = commonParams().protobuf(TiebaProfile.PROTO)

        assertEquals("22.10.1.0", common._client_version)
        assertEquals("4220001", common.framework_ver)
        assertEquals("3.36.0", common.sdk_ver)
        assertEquals("2035000", common.naws_game_ver)
        assertEquals("Mozilla/5.0 (Linux; Android 16; Pixel 9; wv) tieba/22.10.1.0", common.user_agent)
    }

    @Test
    fun `sends tbs only when asked to`() {
        assertNull(commonParams().protobuf(TiebaProfile.PROTO).tbs)
        assertEquals("tbs-value", commonParams().protobuf(TiebaProfile.PROTO, includeTbs = true).tbs)
    }

    @Test
    fun `leaves out the session when logged out`() {
        val common = commonParams(loggedIn = false).protobuf(TiebaProfile.PROTO, includeTbs = true)

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
        assertEquals(expected, params.form(TiebaProfile.FORM).single { it.first == "_client_id" }.second)
        assertEquals(expected, params.form(TiebaProfile.FORM).single { it.first == "_client_id" }.second)
    }

    @Test
    fun `writes the common request's fields in name order, empty ones too`() {
        val bytes = commonParams().protobuf(TiebaProfile.PROTO, includeTbs = true).encode()
        val tags = mutableListOf<Int>()
        val reader = com.squareup.wire.ProtoReader(okio.Buffer().write(bytes))
        val token = reader.beginMessage()
        while (true) {
            val tag = reader.nextTag()
            if (tag == -1) break
            tags += tag
            reader.skip()
        }
        reader.endMessageAndGetUnknownFields(token)
        val names = tags.map { FieldNames.getValue(it) }
        assertEquals(names.sorted(), names)
        // Empty strings and zeros are written as set
        listOf("applist", "cam", "extra", "q_type", "is_teenager").forEach { assertTrue(it in names, it) }
    }

    @Test
    fun `builds the headers of the message task stack in its order`() {
        val expected = listOf(
            "Cookie" to "BAIDUZID=zid-value;DNARBBT=pJGgzNjOoqqqB;CUID=${FakeDevice.cuid};ka=open;" +
                "BAIDUCUID=gavu8gaiv8jcOHaO0iH8ugaQv8g3av82jOHoa0ifHu8bkHaC0iH8uY8mA;need_cookie_decrypt=1;" +
                "BAIDUID=0123456789ABCDEF0123456789ABCDEF:FG=1;",
            "cuid" to FakeDevice.cuid,
            "c3_aid" to FakeDevice.c3Aid,
            "User-Agent" to "Mozilla/5.0 (Linux; Android 16; Pixel 9; wv) tieba/22.10.1.0",
            "Connection" to "Keep-Alive",
            "cuid_gid" to "",
            "Charset" to "UTF-8",
            "cuid_galaxy2" to FakeDevice.cuid,
            "Accept-Encoding" to "gzip",
            "client_user_token" to "12345",
            "x_bd_data_type" to "protobuf",
        )
        assertEquals(expected, commonParams().headers(TiebaProfile.PROTO))
    }

    @Test
    fun `leaves the session out of the message task cookie when logged out`() {
        val cookie = commonParams(loggedIn = false).headers(TiebaProfile.PROTO).single { it.first == "Cookie" }.second

        assertFalse("BAIDUZID" in cookie)
    }

    @Test
    fun `builds the web view headers`() {
        val cuidBase64 = java.util.Base64.getEncoder().encodeToString(FakeDevice.cuid.toByteArray())
        val expected = listOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 16; Pixel 9; wv) tieba/22.10.1.0",
            "cookie" to "BDUSS=bduss-value;STOKEN=stoken-value;BAIDUID=0123456789ABCDEF0123456789ABCDEF:FG=1;" +
                "CUID=${FakeDevice.cuid};BAIDUZID=zid-value;BAIDUCUID=$cuidBase64;TBBRAND=Pixel 9;DNARBBT=;" +
                "ka=open;need_cookie_decrypt=0",
        )
        assertEquals(expected, commonParams().headers(TiebaProfile.WEB))
    }

    @Test
    fun `leaves the session out of the web view cookie when logged out`() {
        val cookie = commonParams(loggedIn = false).headers(TiebaProfile.WEB).single { it.first == "cookie" }.second

        assertEquals(
            "BAIDUID=0123456789ABCDEF0123456789ABCDEF:FG=1;CUID=${FakeDevice.cuid};BAIDUCUID=" +
                java.util.Base64.getEncoder().encodeToString(FakeDevice.cuid.toByteArray()) +
                ";TBBRAND=Pixel 9;DNARBBT=;ka=open;need_cookie_decrypt=0",
            cookie,
        )
    }

    @Test
    fun `adds the message task fields to form parameters`() {
        val classic = commonParams().form(TiebaProfile.FORM)
        val messageTask = commonParams().form(TiebaProfile.MESSAGE_FORM)

        assertEquals(
            listOf(
                "sdk_ver" to "3.36.0",
                "framework_ver" to "4220001",
                "naws_game_ver" to "2035000",
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
        val headers = commonParams(loggedIn = false).headers(TiebaProfile.PROTO)

        assertNull(headers.find { it.first == "client_user_token" })
    }

    @Test
    fun `builds the common parameters of a form profile`() {
        val expected = listOf(
            "_client_type" to "2",
            "_client_version" to "22.10.1.0",
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
            "os_version" to "16",
            "iemi" to "_a2q8_aq28_qa28qALNqA",
            "cam" to "",
            "di_diordna" to "gavu8gaiv8jcOEzOI4E8oqqSB",
            "ledom" to "pJGgzNjOoqqqB",
            "diao" to "oaid-value",
            "dnarb" to "Npm-oNCwA",
            "need_decrypt" to "1",
            "need_cam_decrypt" to "1",
            "ad_context" to "",
            "timestamp" to "$now",
            "baiduid" to "0123456789ABCDEF0123456789ABCDEF:FG=1",
            "sample_id" to "100_1-200_2",
            "BDUSS" to "bduss-value",
            "stoken" to "stoken-value",
            "z_id" to "zid-value",
            "tbs" to "tbs-value",
        )
        assertEquals(expected, commonParams().form(TiebaProfile.FORM, includeTbs = true))
    }

    @Test
    fun `leaves the session out of form parameters when logged out`() {
        val names = commonParams(loggedIn = false).form(TiebaProfile.FORM, includeTbs = true).map { it.first }

        listOf("BDUSS", "stoken", "z_id", "tbs").forEach { assertFalse(it in names, it) }
    }

    @Test
    fun `builds the headers of the form stack`() {
        val expected = listOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 16; Pixel 9; wv) tieba/22.10.1.0",
            "cookie" to "ka=open;BAIDUID=0123456789ABCDEF0123456789ABCDEF:FG=1",
            "client_user_token" to "12345",
            "cuid" to FakeDevice.cuid,
            "cuid_galaxy2" to FakeDevice.cuid,
            "c3_aid" to FakeDevice.c3Aid,
            "cuid_gid" to "",
        )
        assertEquals(expected, commonParams().headers(TiebaProfile.FORM))
    }

    @Test
    fun `keeps the event day of the process start`() {
        val params = commonParams()

        now = localTime(2026, 11, 10)
        assertEquals("2026111", params.protobuf(TiebaProfile.PROTO).event_day)
    }

    @Test
    fun `does not pad months or days`() {
        now = localTime(2026, 1, 1)
        assertEquals("202611", commonParams().protobuf(TiebaProfile.PROTO).event_day)

        now = localTime(2026, 11, 10)
        assertEquals("20261110", commonParams().protobuf(TiebaProfile.PROTO).event_day)
    }

    private fun localTime(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply { set(year, month - 1, day, 12, 0, 0) }.timeInMillis
}

/** The common request's field names by number, to check the order they are written in. */
private val FieldNames = mapOf(
    10 to "BDUSS", 1 to "_client_type", 2 to "_client_version", 8 to "_timestamp", 49 to "active_timestamp",
    90 to "ad_context", 29 to "applist", 35 to "c3_aid", 66 to "cam", 55 to "cmode", 7 to "cuid",
    32 to "cuid_galaxy2", 33 to "cuid_gid", 70 to "device_score", 68 to "di_diordna", 74 to "diao", 76 to "dnarb",
    53 to "event_day", 61 to "extra", 50 to "first_install_time", 43 to "framework_ver", 6 to "from", 64 to "iemi",
    41 to "is_teenager", 51 to "last_update_time", 73 to "ledom", 28 to "lego_lib_version", 44 to "naws_game_ver",
    82 to "need_cam_decrypt", 81 to "need_decrypt", 12 to "net_type", 75 to "noisrev_so", 88 to "package_version",
    63 to "personalized_rec_switch", 24 to "pversion", 40 to "q_type", 36 to "sample_id", 39 to "scr_dip",
    38 to "scr_h", 37 to "scr_w", 42 to "sdk_ver", 56 to "start_scheme", 57 to "start_type", 30 to "stoken",
    87 to "support_image", 11 to "tbs", 62 to "user_agent", 31 to "z_id",
)
