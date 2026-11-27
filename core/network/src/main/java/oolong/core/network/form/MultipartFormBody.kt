package oolong.core.network.form

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ByteString

/**
 * A multipart form of text [fields] followed by one file part, with no `Content-Length` on the
 * parts, so this doesn't use OkHttp's `MultipartBody`. The body's own length is sent.
 */
class MultipartFormBody(
    internal val fields: List<Pair<String, String>>,
    internal val fileField: String,
    internal val file: ByteString,
) : RequestBody() {
    override fun contentType(): MediaType = CONTENT_TYPE

    private val bytes: ByteString by lazy { Buffer().also(::write).readByteString() }

    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        sink.write(bytes)
    }

    private fun write(sink: BufferedSink) {
        fields.forEach { (name, value) ->
            sink.writePartHeader("form-data; name=\"$name\"")
            sink.writeUtf8(value).writeUtf8(CRLF)
        }
        sink.writePartHeader("form-data; name=\"$fileField\"; filename=\"file\"")
        sink.write(file).writeUtf8(CRLF)
        sink.writeUtf8("--").writeUtf8(BOUNDARY).writeUtf8("--").writeUtf8(CRLF)
    }

    private fun BufferedSink.writePartHeader(disposition: String) {
        writeUtf8("--").writeUtf8(BOUNDARY).writeUtf8(CRLF)
        writeUtf8("Content-Disposition: ").writeUtf8(disposition).writeUtf8(CRLF)
        writeUtf8(CRLF)
    }

    companion object {
        const val BOUNDARY = "--------7da3d81520810*"
        private const val CRLF = "\r\n"
        private val CONTENT_TYPE = "multipart/form-data; boundary=$BOUNDARY".toMediaType()
    }
}
