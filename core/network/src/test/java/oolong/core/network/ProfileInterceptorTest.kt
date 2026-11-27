package oolong.core.network

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileInterceptorTest {
    private val commonParams = CommonParams(
        device = FakeDevice,
        install = FakeInstall(),
        sessionProvider = { fakeSession },
        clock = { 1_700_000_000_000L },
        random = Random(0),
    )

    private fun send(request: Request): Request {
        lateinit var sent: Request
        val client = OkHttpClient.Builder()
            .addInterceptor(ProfileInterceptor(TiebaProfile.PROTO, commonParams))
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder()
                    .request(sent)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        client.newCall(request).execute().close()
        return sent
    }

    @Test
    fun `adds the common headers of the profile`() {
        val sent = send(Request.Builder().url("https://tiebac.baidu.com/c/f/forum/forumRuleDetail").build())

        assertEquals(
            commonParams.headers(TiebaProfile.PROTO),
            sent.headers.toList().filter { it.first != "client_logid" && it.first != "X-Bd-Traceid" },
        )
    }

    @Test
    fun `gives every request its own trace id and a later log id`() {
        val request = Request.Builder().url("https://tiebac.baidu.com/c/f/forum/forumRuleDetail").build()
        val first = send(request)
        val second = send(request)

        val traceIds = listOf(first, second).map { it.header("X-Bd-Traceid")!! }
        // A version 4 UUID without its dashes
        traceIds.forEach { assertTrue(Regex("[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}").matches(it), it) }
        assertNotEquals(traceIds[0], traceIds[1])
        // The first request takes the time, and each later one counts on from it
        assertEquals(1_700_000_000_000L, first.header("client_logid")!!.toLong())
        assertEquals(1_700_000_000_001L, second.header("client_logid")!!.toLong())
    }

    @Test
    fun `sends no per-request headers from the in-app web pages`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(ProfileInterceptor(TiebaProfile.WEB, commonParams))
            .addInterceptor { chain ->
                assertNull(chain.request().header("X-Bd-Traceid"))
                assertNull(chain.request().header("client_logid"))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("".toResponseBody()).build()
            }
            .build()
        client.newCall(Request.Builder().url("https://tieba.baidu.com/mo/q/search/thread").build()).execute().close()
    }

    @Test
    fun `keeps headers the request sets itself`() {
        val sent = send(
            Request.Builder()
                .url("https://tiebac.baidu.com/c/f/forum/forumRuleDetail")
                .header("User-Agent", "custom")
                .build()
        )

        assertEquals(listOf("custom"), sent.headers("User-Agent"))
    }
}
