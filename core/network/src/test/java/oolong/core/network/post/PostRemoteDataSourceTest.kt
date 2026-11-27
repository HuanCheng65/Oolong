package oolong.core.network.post

import com.squareup.wire.Message
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MultipartReader
import okio.Buffer
import okio.ByteString
import oolong.core.network.CommonParams
import oolong.core.network.FakeDevice
import oolong.core.network.FakeInstall
import oolong.core.network.TiebaNetwork
import oolong.core.network.TiebaProfile
import oolong.core.network.TiebaServerException
import oolong.core.network.VerificationChallenge
import oolong.core.network.VerificationRequiredException
import oolong.core.network.fakeSession
import oolong.core.network.form.FormSignature
import oolong.core.network.identity.Session
import oolong.core.network.identity.SessionProvider
import oolong.core.network.protos.Anti
import oolong.core.network.protos.Error
import oolong.core.network.protos.PostAntiInfo
import oolong.core.network.protos.addPollPost.AddPollPostRequest
import oolong.core.network.protos.addPollPost.AddPollPostResponse
import oolong.core.network.protos.addPollPost.AddPollPostResponseData
import oolong.core.network.protos.addPost.AddPostRequest
import oolong.core.network.protos.addPost.AddPostRequestData
import oolong.core.network.protos.addPost.AddPostResponse
import oolong.core.network.protos.addPost.AddPostResponseData
import oolong.core.network.testing.withoutStatistics
import org.junit.After
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PostRemoteDataSourceTest {
    private val server = MockWebServer().apply { start() }
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
    private val dataSource = PostRemoteDataSource(
        TiebaNetwork(commonParams, sessionProvider, baseUrlOverride = server.url("/")),
        commonParams,
        sessionProvider,
    )
    private val reply = AddPostRequestData(fid = "52", kw = "吧名", tid = "100", content = "回复内容", anonymous = "1")

    @After
    fun tearDown() = server.close()

    private fun enqueue(response: Message<*, *>) {
        server.enqueue(MockResponse.Builder().body(Buffer().write(response.encode())).build())
    }

    private fun parts(target: String = "/c/c/post/add?cmd=309731&format=protobuf"): List<Pair<String, ByteString>> {
        val request = server.takeRequest()
        assertEquals(target, request.target)
        assertEquals("protobuf", request.headers["x_bd_data_type"])
        assertEquals(null, request.headers["client_type"])
        val boundary = request.headers["Content-Type"]!!.substringAfter("boundary=")
        val parts = mutableListOf<Pair<String, ByteString>>()
        MultipartReader(Buffer().write(request.body!!), boundary).use { reader ->
            while (true) {
                val part = reader.nextPart() ?: break
                val name = Regex("name=\"([^\"]*)\"").find(part.headers["Content-Disposition"]!!)!!.groupValues[1]
                parts += name to part.body.readByteString()
            }
        }
        return parts
    }

    @Test
    fun `posts a reply with the message task form fields`() = runTest {
        enqueue(AddPostResponse(data_ = AddPostResponseData(tid = "100", pid = "200")))

        dataSource.addPost(reply)

        val parts = parts()
        assertEquals("data", parts.last().first)
        val fields = parts.dropLast(1).map { it.first to it.second.utf8() }
        assertEquals(fields.map { it.first }.sorted(), fields.map { it.first })
        val params = fields.filter { it.first != "sign" }
        assertEquals(FormSignature.sign(params), fields.single { it.first == "sign" }.second)
        assertEquals(null, fields.singleOrNull { it.first == "sig" })
        assertEquals(commonParams.form(TiebaProfile.PROTO).toSet(), params.withoutStatistics().toSet())

        val sent = AddPostRequest.ADAPTER.decode(parts.last().second).data_!!
        assertEquals(commonParams.protobuf(TiebaProfile.PROTO, includeTbs = true), sent.common)
        assertEquals("tbs-value", sent.common?.tbs)
        assertEquals("", sent.tbs)
        assertEquals("", sent.bot_conf)
        assertEquals("回复内容", sent.content)
    }

    @Test
    fun `puts a given tbs in the common request`() = runTest {
        enqueue(AddPostResponse(data_ = AddPostResponseData()))

        dataSource.addPost(reply, tbs = "page-tbs")

        assertEquals("page-tbs", AddPostRequest.ADAPTER.decode(parts().last().second).data_?.common?.tbs)
    }

    @Test
    fun `returns the post and stores a refreshed tbs`() = runTest {
        enqueue(AddPostResponse(data_ = AddPostResponseData(pid = "200", anti_stat = Anti(tbs = "new-tbs"))))

        assertEquals("200", dataSource.addPost(reply).pid)
        assertEquals("new-tbs", storedTbs)
    }

    @Test
    fun `asks for a captcha`() = runTest {
        enqueue(
            AddPostResponse(
                error = Error(error_code = 5, error_msg = "需要验证码"),
                data_ = AddPostResponseData(
                    info = PostAntiInfo(need_vcode = "1", vcode_md5 = "md5-value", vcode_type = "4"),
                    anti_stat = Anti(tbs = "new-tbs"),
                ),
            )
        )

        val error = assertFailsWith<VerificationRequiredException> { dataSource.addPost(reply) }
        val captcha = assertIs<VerificationChallenge.Captcha>(error.challenge)
        assertEquals("md5-value", captcha.vcodeMd5)
        assertEquals("4", captcha.vcodeType)
        assertEquals("new-tbs", storedTbs)
    }

    @Test
    fun `throws other server errors`() = runTest {
        enqueue(AddPostResponse(error = Error(error_code = 340001, error_msg = "吧内封禁")))

        val error = assertFailsWith<TiebaServerException> { dataSource.addPost(reply) }
        assertEquals(340001, error.code)
    }

    @Test
    fun `votes with the chosen options joined and the form fields beside them`() = runTest {
        enqueue(AddPollPostResponse(data_ = AddPollPostResponseData()))

        dataSource.addPollPost(threadId = 100, forumId = 52, optionIds = listOf(2, 5))

        val parts = parts("/c/c/post/addPollPost?cmd=309006&format=protobuf")
        val fields = parts.dropLast(1).map { it.first to it.second.utf8() }
        assertEquals("tbs-value", fields.single { it.first == "tbs" }.second)
        val sent = AddPollPostRequest.ADAPTER.decode(parts.last().second).data_!!
        assertEquals(Triple(100L, "2,5", 52L), Triple(sent.thread_id, sent.options, sent.forum_id))
    }

    @Test
    fun `throws a refused vote from either error field`() = runTest {
        enqueue(AddPollPostResponse(data_ = AddPollPostResponseData(error_code = 2, error_msg = "已投票")))
        assertEquals(2, assertFailsWith<TiebaServerException> { dataSource.addPollPost(100, 52, listOf(1)) }.code)

        enqueue(AddPollPostResponse(error = Error(error_code = 3, error_msg = "投票已结束")))
        assertEquals(3, assertFailsWith<TiebaServerException> { dataSource.addPollPost(100, 52, listOf(1)) }.code)
    }
}
