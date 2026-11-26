package oolong.client.diagnostics

import android.content.Context
import com.squareup.wire.FieldEncoding
import com.squareup.wire.ProtoReader
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MultipartReader
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.ByteString
import okio.GzipSource
import okio.buffer
import oolong.client.data.identity.legacySessionProvider
import retrofit2.Invocation
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-only network interceptor that appends every request, as sent on the wire, to a text file
 * in `files/netlog/`. Used as a baseline when changing how requests are built.
 *
 * Credentials (BDUSS, STOKEN, tbs) are replaced with placeholders before anything is written.
 */
object RequestRecorder : Interceptor {
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Where requests are written; nothing is written before [attach]. */
    @Volatile
    private var dir: File? = null

    /** Writes from now on into the app's files, as the app starts in a debug build. */
    fun attach(context: Context) {
        dir = File(context.filesDir, "netlog")
    }

    /** This run's file, made on the first request written. */
    private var logFile: File? = null

    @Synchronized
    private fun logFile(): File? = logFile ?: dir?.let { dir ->
        val name = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        File(dir.apply { mkdirs() }, "requests-$name.txt").also { logFile = it }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val entry = runCatching { describe(request) }.getOrElse { "!! failed to describe: $it\n" }
        val response = chain.proceed(request)
        val body = runCatching { describeResponse(request, response) }.getOrNull().orEmpty()
        write("$entry<- HTTP ${response.code}\n$body\n")
        return response
    }

    private val errorCode = Regex("\"error_code\"\\s*:\\s*\"?(-?\\d+)")

    /**
     * The body of JSON responses, which migrations get checked against, and of any other text
     * response with a non-zero `error_code`, so that verification responses can be studied once
     * one occurs.
     */
    private fun describeResponse(request: Request, response: Response): String? {
        if (response.body.contentType()?.subtype?.contains("protobuf") == true) return null
        val peeked = response.peekBody(MAX_PEEKED_BODY).source()
        val source = if (response.header("Content-Encoding").equals("gzip", ignoreCase = true)) {
            GzipSource(peeked).buffer()
        } else peeked
        val body = source.use { it.readUtf8() }
        val isJson = body.trimStart().startsWith("{")
        if (request.body !is FormBody && !isJson) {
            val code = errorCode.find(body)?.groupValues?.get(1) ?: return null
            if (code == "0") return null
        }
        return "response:\n  ${body.take(MAX_RECORDED_CHARS)}\n"
    }

    private const val MAX_PEEKED_BODY = 1024 * 1024L
    private const val MAX_RECORDED_CHARS = 16 * 1024

    @Synchronized
    private fun write(text: String) {
        logFile()?.appendText(redact(text))
    }

    private val secretCookie = Regex("\\b(BDUSS|BDUSS_BFESS|STOKEN|PTOKEN)=([^;\\s]+)", RegexOption.IGNORE_CASE)

    private fun redact(text: String): String {
        var result = text.replace(secretCookie) { "${it.groupValues[1]}=<${it.groupValues[1].uppercase()}>" }
        val session = legacySessionProvider.currentSession() ?: return result
        listOf(
            "BDUSS" to session.bduss,
            "STOKEN" to session.stoken,
            "TBS" to session.tbs,
        ).forEach { (name, value) ->
            if (value.isNullOrEmpty()) return@forEach
            result = result
                .replace(value, "<$name>")
                .replace(URLEncoder.encode(value, "UTF-8"), "<$name>")
        }
        return result
    }

    private fun describe(request: Request): String = buildString {
        val method = request.tag(Invocation::class.java)?.method()
        append("### ").append(timeFormat.format(Date())).append(' ')
        append(request.method).append(' ').append(request.url.scheme).append("://")
        append(request.url.host).append(request.url.encodedPath).append('\n')
        if (method != null) {
            append("endpoint: ").append(method.declaringClass.simpleName)
                .append('.').append(method.name).append('\n')
        }
        append("headers:\n")
        request.headers.forEach { (name, value) -> append("  ").append(name).append(": ").append(value).append('\n') }
        if (request.url.querySize > 0) {
            append("query:\n")
            for (i in 0 until request.url.querySize) {
                append("  ").append(request.url.queryParameterName(i)).append(" = ")
                    .append(request.url.queryParameterValue(i)).append('\n')
            }
        }
        val body = request.body ?: return@buildString
        when {
            body is FormBody -> {
                append("form:\n")
                for (i in 0 until body.size) {
                    append("  ").append(body.name(i)).append(" = ").append(body.value(i)).append('\n')
                }
            }

            body.contentType()?.type == "multipart" -> {
                val boundary = body.contentType()?.parameter("boundary")
                val buffer = Buffer().also { body.writeTo(it) }
                append("multipart:\n")
                MultipartReader(buffer, checkNotNull(boundary)).use { reader ->
                    while (true) {
                        val part = reader.nextPart() ?: break
                        val disposition = part.headers["Content-Disposition"].orEmpty()
                        val name = Regex("name=\"([^\"]*)\"").find(disposition)?.groupValues?.get(1)
                        val content = part.body.readByteString()
                        if (name == "data" && disposition.contains("filename=")) {
                            append("  ").append(name).append(" (protobuf, ")
                                .append(content.size).append(" bytes):\n")
                            appendProto(content, indent = 4)
                        } else if (disposition.contains("filename=")) {
                            append("  ").append(name).append(" (file, ")
                                .append(content.size).append(" bytes)\n")
                        } else {
                            append("  ").append(name).append(" = ").append(content.utf8()).append('\n')
                        }
                    }
                }
            }

            else -> {
                val buffer = Buffer().also { body.writeTo(it) }
                append("body (").append(body.contentType()).append("):\n  ")
                append(buffer.readUtf8()).append('\n')
            }
        }
    }

    /**
     * Prints a protobuf message by field number, like `protoc --decode_raw`. Length-delimited
     * fields are shown as text when they look like text, otherwise as a nested message if they
     * parse as one, otherwise as hex.
     */
    private fun StringBuilder.appendProto(bytes: ByteString, indent: Int) {
        val pad = " ".repeat(indent)
        val fields = parseProto(bytes) ?: run {
            append(pad).append("<not a message> ").append(bytes.hex()).append('\n')
            return
        }
        fields.forEach { (tag, value) ->
            append(pad).append(tag)
            when (value) {
                is ByteString -> when {
                    value.size == 0 -> append(": \"\"\n")
                    value.isText() -> append(": \"").append(value.utf8()).append("\"\n")
                    parseProto(value) != null -> {
                        append(" {\n")
                        appendProto(value, indent + 2)
                        append(pad).append("}\n")
                    }

                    else -> append(": 0x").append(value.hex()).append('\n')
                }

                else -> append(": ").append(value).append('\n')
            }
        }
    }

    private fun parseProto(bytes: ByteString): List<Pair<Int, Any>>? = runCatching {
        val reader = ProtoReader(Buffer().write(bytes))
        val fields = mutableListOf<Pair<Int, Any>>()
        val token = reader.beginMessage()
        while (true) {
            val tag = reader.nextTag()
            if (tag == -1) break
            val value: Any = when (reader.peekFieldEncoding()) {
                FieldEncoding.VARINT -> reader.readVarint64()
                FieldEncoding.FIXED32 -> reader.readFixed32()
                FieldEncoding.FIXED64 -> reader.readFixed64()
                FieldEncoding.LENGTH_DELIMITED -> reader.readBytes()
                null -> error("unknown wire type")
            }
            fields += tag to value
        }
        reader.endMessageAndGetUnknownFields(token)
        fields
    }.getOrNull()

    private fun ByteString.isText(): Boolean {
        val text = runCatching { utf8() }.getOrNull() ?: return false
        if (text.contains('�')) return false
        return text.all { !it.isISOControl() || it == '\n' || it == '\r' || it == '\t' }
    }
}
