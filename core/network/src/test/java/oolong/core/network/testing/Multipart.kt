package oolong.core.network.testing

import okhttp3.MultipartReader
import okio.Buffer
import okio.ByteString
import kotlin.test.assertEquals

/** The parts of a multipart body by name, asserting none declares its length. */
fun multipartParts(contentType: String, body: ByteString): List<Pair<String, ByteString>> {
    val boundary = contentType.substringAfter("boundary=")
    val parts = mutableListOf<Pair<String, ByteString>>()
    MultipartReader(Buffer().write(body), boundary).use { reader ->
        while (true) {
            val part = reader.nextPart() ?: break
            assertEquals(null, part.headers["Content-Length"])
            val name = Regex("name=\"([^\"]*)\"")
                .find(part.headers["Content-Disposition"]!!)!!.groupValues[1]
            parts += name to part.body.readByteString()
        }
    }
    return parts
}

/**
 * Asserts the form fields of a protobuf request: the session, the request statistics and any
 * [extra] fields, sorted and signed, then the message last. Returns the message.
 */
fun assertProtobufParts(
    parts: List<Pair<String, ByteString>>,
    extra: List<String> = emptyList(),
    loggedIn: Boolean = true,
): ByteString {
    val fields = parts.dropLast(1).map { it.first to it.second.utf8() }
    assertEquals("data", parts.last().first)
    val expected = (extra + listOf("sign", "stErrorNums") + (if (loggedIn) listOf("BDUSS", "stoken") else emptyList())).sorted()
    assertEquals(expected, fields.map { it.first }.filter { it !in setOf("stTime", "stSize", "stTimesNum", "stMode", "stMethod") })
    assertEquals(fields.map { it.first }.sorted(), fields.map { it.first })
    val unsigned = fields.filter { it.first != "sign" }
    assertEquals(oolong.core.network.form.FormSignature.sign(unsigned), fields.single { it.first == "sign" }.second)
    return parts.last().second
}
