package oolong.core.network.testing

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import oolong.core.network.CommonParams
import oolong.core.network.FakeDevice
import oolong.core.network.FakeInstall
import oolong.core.network.TiebaNetwork
import oolong.core.network.fakeSession
import oolong.core.network.form.FormSignature
import java.net.URLDecoder
import kotlin.random.Random
import kotlin.test.assertEquals

/** A MockWebServer with a [TiebaNetwork] pointed at it, for form endpoint tests. */
class FormServer {
    val server = MockWebServer().apply { start() }
    val commonParams = CommonParams(
        device = FakeDevice,
        install = FakeInstall(),
        sessionProvider = { fakeSession },
        clock = { 1_700_000_000_000L },
        random = Random(0),
    )
    val network = TiebaNetwork(commonParams, { fakeSession }, baseUrlOverride = server.url("/"))

    fun enqueue(json: String) {
        server.enqueue(MockResponse.Builder().body(json).build())
    }

    fun takeRequest(): RecordedRequest = server.takeRequest()

    fun close() = server.close()
}

fun RecordedRequest.form(): List<Pair<String, String>> = body!!.utf8().split('&').map {
    val (name, value) = it.split('=', limit = 2)
    URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8")
}

fun List<Pair<String, String>>.value(name: String): String? = singleOrNull { it.first == name }?.second

/** Asserts the form is sorted and carries a valid sign, and sig when [withSig]. */
fun assertSigned(form: List<Pair<String, String>>, withSig: Boolean) {
    assertEquals(form.map { it.first }.sorted(), form.map { it.first })
    val params = form.filter { it.first != "sign" && it.first != "sig" }
    assertEquals(FormSignature.sign(params), form.value("sign"))
    assertEquals(if (withSig) FormSignature.sig(params) else null, form.value("sig"))
}

/** The fields without the request statistics every signed request carries. */
fun List<Pair<String, String>>.withoutStatistics(): List<Pair<String, String>> =
    filter { (name) -> name !in StatisticsFields }

private val StatisticsFields = setOf("stTime", "stSize", "stTimesNum", "stMode", "stMethod", "stErrorNums")
