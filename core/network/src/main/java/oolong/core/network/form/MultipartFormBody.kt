package oolong.core.network.form

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.ByteString

/**
 * A multipart form of text [fields] followed by one file part, with no `Content-Length` on the
 * parts and the body sent chunked, so this doesn't use OkHttp's `MultipartBody`.
 */
class MultipartFormBody(
    internal val fields: List<Pair<String, String>>,
    internal val fileField: String,
    internal val file: ByteString,
) : RequestBody() {
    override fun contentType(): MediaType = CONTENT_TYPE

    override fun contentLength(): Long = -1

    override fun writeTo(sink: BufferedSink) {
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
