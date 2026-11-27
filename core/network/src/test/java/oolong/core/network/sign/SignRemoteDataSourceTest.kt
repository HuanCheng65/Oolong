package oolong.core.network.sign

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import oolong.core.network.CommonParams
import oolong.core.network.FakeDevice
import oolong.core.network.FakeInstall
import oolong.core.network.TiebaNetwork
import oolong.core.network.TiebaProfile
import oolong.core.network.TiebaServerException
import oolong.core.network.fakeSession
import oolong.core.network.form.FormSignature
import oolong.core.network.identity.Session
import oolong.core.network.identity.SessionProvider
import oolong.core.network.testing.assertSigned
import oolong.core.network.testing.form
import oolong.core.network.testing.value
import org.junit.After
import org.junit.Before
import java.net.URLDecoder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SignRemoteDataSourceTest {
    private val server = MockWebServer()
    private var storedTbs: String? = null
    private val sessionProvider = object : SessionProvider {
        override fun currentSession(): Session = fakeSession
        override fun updateTbs(tbs: String) {
            storedTbs = tbs
        }
    }
    private val commonParams = CommonParams(
        device = FakeDevice,
        install = FakeInstall(),
        sessionProvider = sessionProvider,
        clock = { 1_700_000_000_000L },
        random = Random(0),
    )
    private lateinit var dataSource: SignRemoteDataSource

    @Before
    fun setUp() {
        server.start()
        val network = TiebaNetwork(commonParams, sessionProvider, baseUrlOverride = server.url("/"))
        dataSource = SignRemoteDataSource(network, commonParams)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(json: String) {
        server.enqueue(MockResponse.Builder().body(json).build())
    }

    private fun sentForm(): List<Pair<String, String>> {
        val request = server.takeRequest()
        return request.body!!.utf8().split('&').map {
            val (name, value) = it.split('=', limit = 2)
            URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }
    }

    @Test
    fun `asks for the one-click sign-in limits`() = runTest {
        enqueue("""{"error_code":"0","level":"7","msign_step_num":"200"}""")

        val response = dataSource.forumList()

        val request = server.takeRequest()
        assertEquals("/c/f/forum/getforumlist", request.target)
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("12345", form.value("user_id"))
        assertEquals("7", response.level)
        assertEquals("200", response.msignStepNum)
    }

    @Test
    fun `sends the sign-in form as the 12_52 client`() = runTest {
        enqueue("""{"error_code":"0"}""")

        dataSource.sign(forumId = 52, forumName = "吧名")

        val request = server.takeRequest()
        assertEquals("/c/c/forum/sign", request.target)
        commonParams.headers(TiebaProfile.FORM).forEach { (name, value) ->
            assertEquals(value, request.headers[name], name)
        }
        assertEquals(null, request.headers["client_type"])
        assertEquals(null, request.headers["Charset"])
    }

    @Test
    fun `signs the sorted parameters with sign and sig`() = runTest {
        enqueue("""{"error_code":"0"}""")

        dataSource.sign(forumId = 52, forumName = "吧名")

        val form = sentForm()
        assertEquals(form.map { it.first }.sorted(), form.map { it.first })
        val params = form.filter { it.first != "sign" && it.first != "sig" }
        assertEquals(FormSignature.sign(params), form.single { it.first == "sign" }.second)
        assertEquals(FormSignature.sig(params), form.single { it.first == "sig" }.second)
        assertEquals("52", form.single { it.first == "fid" }.second)
        assertEquals("吧名", form.single { it.first == "kw" }.second)
        assertEquals("tbs-value", form.single { it.first == "tbs" }.second)
        assertEquals("bduss-value", form.single { it.first == "BDUSS" }.second)
    }

    @Test
    fun `prefers the tbs it is given`() = runTest {
        enqueue("""{"error_code":"0"}""")

        dataSource.sign(forumId = 52, forumName = "吧名", tbs = "page-tbs")

        assertEquals(listOf("page-tbs"), sentForm().filter { it.first == "tbs" }.map { it.second })
    }

    @Test
    fun `returns the sign-in result`() = runTest {
        enqueue(
            """
            {"error_code":"0","user_info":{"user_id":"12345","is_sign_in":1,"cont_sign_num":"3",
            "sign_bonus_point":"8","level_name":"初级粉丝","levelup_score":"25",
            "all_level_info":[{"id":"1","name":"初级粉丝","score":"0"}]},"time":1700000000}
            """
        )

        val result = dataSource.sign(forumId = 52, forumName = "吧名")

        assertEquals("1", result.userInfo?.isSignIn)
        assertEquals("8", result.userInfo?.signBonusPoint)
        assertEquals(1, result.userInfo?.allLevelInfo?.size)
    }

    @Test
    fun `throws the server error`() = runTest {
        enqueue("""{"error_code":"160002","error_msg":"亲，你之前已经签过了"}""")

        val error = assertFailsWith<TiebaServerException> { dataSource.sign(forumId = 52, forumName = "吧名") }
        assertEquals(160002, error.code)
    }
}
