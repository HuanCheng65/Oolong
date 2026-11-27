package oolong.core.network.form

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import oolong.core.network.CommonParams
import oolong.core.network.FakeDevice
import oolong.core.network.FakeInstall
import oolong.core.network.TiebaProfile
import oolong.core.network.fakeSession
import oolong.core.network.testing.assertSigned
import oolong.core.network.testing.value
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormParamsInterceptorTest {
    private val commonParams = CommonParams(
        device = FakeDevice,
        install = FakeInstall(),
        sessionProvider = { fakeSession },
        clock = { 1_700_000_000_000L },
        random = Random(0),
    )

    private fun send(builder: Request.Builder.() -> Unit): Request {
        lateinit var sent: Request
        OkHttpClient.Builder()
            .addInterceptor(FormParamsInterceptor(TiebaProfile.FORM, commonParams))
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder().request(sent).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{}".toResponseBody()).build()
            }
            .build()
            .newCall(Request.Builder().url("https://tiebac.baidu.com/c/s/sync").apply(builder).build())
            .execute().close()
        return sent
    }

    private fun Request.form(): List<Pair<String, String>> {
        val form = body as FormBody
        return (0 until form.size).map { form.name(it) to form.value(it) }
    }

    @Test
    fun `adds the common parameters and signs the form`() {
        val form = send { post(FormBody.Builder().add("kw", "吧名").build()) }.form()

        assertSigned(form, withSig = false)
        assertEquals("吧名", form.value("kw"))
        assertEquals("22.10.1.0", form.value("_client_version"))
        assertEquals("bduss-value", form.value("BDUSS"))
    }

    @Test
    fun `lets business parameters override common ones`() {
        val form = send { post(FormBody.Builder().add("tbs", "page-tbs").add("brand", "x").build()) }.form()

        assertEquals("page-tbs", form.value("tbs"))
        assertEquals("x", form.value("brand"))
        assertEquals(1, form.count { it.first == "brand" })
    }

    @Test
    fun `leaves out the listed common parameters`() {
        val request = send {
            header(FormParamsInterceptor.OMIT_COMMON_PARAMS, "BDUSS,oaid")
            post(FormBody.Builder().add("bdusstoken", "token").build())
        }

        val form = request.form()
        assertSigned(form, withSig = false)
        assertNull(form.value("BDUSS"))
        assertNull(form.value("oaid"))
        assertNull(request.header(FormParamsInterceptor.OMIT_COMMON_PARAMS))
    }

    @Test
    fun `adds sig when the request asks for it`() {
        val request = send {
            header(FormParamsInterceptor.WITH_SIG, "1")
            post(FormBody.Builder().add("kw", "吧名").build())
        }

        assertSigned(request.form(), withSig = true)
        assertNull(request.header(FormParamsInterceptor.WITH_SIG))
    }

    @Test
    fun `replaces a signature the request already carries`() {
        val form = send { post(FormBody.Builder().add("kw", "吧名").add("sign", "stale").build()) }.form()

        assertSigned(form, withSig = false)
    }
}
