package oolong.core.network.upload

import kotlinx.coroutines.test.runTest
import mockwebserver3.RecordedRequest
import okhttp3.MultipartReader
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import oolong.core.network.TiebaProfile
import oolong.core.network.TiebaServerException
import oolong.core.network.form.FormSignature
import oolong.core.network.testing.FormServer
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UploadRemoteDataSourceTest {
    private val server = FormServer()
    private val dataSource = UploadRemoteDataSource(server.network, server.commonParams)

    @After
    fun tearDown() = server.close()

    private fun chunk(watermark: PictureWatermark, forumName: String = "吧名") = PictureChunk(
        resourceId = "md5512000",
        chunkNo = 1,
        isFinish = true,
        size = 1024,
        width = 800,
        height = 600,
        saveOrigin = false,
        watermark = watermark,
        forumName = forumName,
        bytes = "image".encodeUtf8(),
    )

    private fun RecordedRequest.parts(): List<Pair<String, ByteString>> {
        val boundary = headers["Content-Type"]!!.substringAfter("boundary=")
        val parts = mutableListOf<Pair<String, ByteString>>()
        MultipartReader(Buffer().write(body!!), boundary).use { reader ->
            while (true) {
                val part = reader.nextPart() ?: break
                assertNull(part.headers["Content-Length"])
                val name = Regex("name=\"([^\"]*)\"").find(part.headers["Content-Disposition"]!!)!!.groupValues[1]
                parts += name to part.body.readByteString()
            }
        }
        return parts
    }

    private fun List<Pair<String, ByteString>>.fields() = dropLast(1).map { it.first to it.second.utf8() }

    private fun assertSignedWithoutSig(fields: List<Pair<String, String>>) {
        assertEquals(fields.map { it.first }.sorted(), fields.map { it.first })
        val params = fields.filter { it.first != "sign" }
        assertEquals(FormSignature.sign(params), fields.single { it.first == "sign" }.second)
        assertNull(fields.singleOrNull { it.first == "sig" })
    }

    @Test
    fun `uploads a picture chunk as a signed classic multipart form`() = runTest {
        server.enqueue("""{"error_code":"0","error_msg":"","resourceId":"md5512000","chunkNo":"1","picId":"123",
            "picInfo":{"originPic":{"width":"800","height":"600","type":"jpg","picUrl":"https://example.com/p.jpg"}}}""")

        val response = dataSource.uploadPictureChunk(chunk(PictureWatermark.FORUM_NAME))

        val request = server.takeRequest()
        assertEquals("/c/s/uploadPicture", request.target)
        assertNull(request.headers["Charset"])
        val parts = request.parts()
        assertEquals("chunk" to "image".encodeUtf8(), parts.last())
        val fields = parts.fields()
        assertSignedWithoutSig(fields)
        val map = fields.toMap()
        assertEquals("json", map["alt"])
        assertEquals("md5512000", map["resourceId"])
        assertEquals("1", map["isFinish"])
        assertEquals("2", map["pic_water_type"])
        assertEquals("吧名", map["forum_name"])
        assertEquals("吧名", map["small_flow_fname"])
        assertNull(map["user_name"])
        assertEquals("bduss-value", map["BDUSS"])
        assertEquals("22.10.1.0", map["_client_version"])
        assertEquals("123", response.picId)
        assertEquals("https://example.com/p.jpg", response.picInfo?.originPic?.picUrl)
    }

    @Test
    fun `sends the user name only for a user name watermark`() = runTest {
        server.enqueue("""{"error_code":"0"}""")

        dataSource.uploadPictureChunk(chunk(PictureWatermark.USER_NAME))

        val map = server.takeRequest().parts().fields().toMap()
        assertEquals("1", map["pic_water_type"])
        assertEquals("user-name", map["user_name"])
        assertNull(map["forum_name"])
    }

    @Test
    fun `uploads a portrait`() = runTest {
        server.enqueue("""{"error_code":"0","error_msg":""}""")

        dataSource.uploadPortrait("portrait".encodeUtf8())

        val request = server.takeRequest()
        assertEquals("/c/c/img/portrait", request.target)
        val parts = request.parts()
        assertEquals("pic" to "portrait".encodeUtf8(), parts.last())
        val fields = parts.fields()
        assertSignedWithoutSig(fields)
        assertEquals(
            server.commonParams.form(TiebaProfile.FORM).map { it.first }.sorted(),
            fields.map { it.first }.filter { it != "sign" },
        )
    }

    @Test
    fun `throws when an upload fails`() = runTest {
        server.enqueue("""{"error_code":"1990055","error_msg":"未实名"}""")

        assertFailsWith<TiebaServerException> { dataSource.uploadPortrait("portrait".encodeUtf8()) }
    }
}
