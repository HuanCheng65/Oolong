package oolong.core.network

import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.GzipSource
import okio.buffer
import oolong.core.network.form.MultipartFormBody
import oolong.core.network.form.signedParams
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * How the app's recent requests went, handed out a request at a time as the `st*` parameters: the
 * time, size and attempts of an earlier request, how it was sent, and how many requests failed
 * since the last report.
 */
class RequestStatistics(private val clock: () -> Long = System::currentTimeMillis) {
    private class Entry(val time: Long, val size: Long, val attempts: Int, val mode: Int, val method: Int)

    private val entries = ArrayList<Entry>()
    private val errors = AtomicInteger()

    /** Keeps the numbers of a finished request, up to a small backlog. */
    @Synchronized
    fun record(timeMillis: Long, size: Long, networkType: Int, post: Boolean) {
        if (entries.size > MAX_ENTRIES) return
        entries += Entry(
            time = timeMillis,
            size = size,
            attempts = 1,
            mode = if (networkType in 1..3) networkType else 1,
            method = if (post) 1 else 2,
        )
    }

    fun failed() {
        errors.incrementAndGet()
    }

    /** The parameters of the next request, taking the latest numbers kept and the failure count. */
    @Synchronized
    fun next(): List<Pair<String, String>> {
        val entry = entries.removeLastOrNull()
        val errorCount = errors.getAndSet(0)
        return buildList {
            if (entry != null) {
                add("stTime" to entry.time.toString())
                add("stSize" to entry.size.toString())
                add("stTimesNum" to entry.attempts.toString())
                add("stMode" to entry.mode.toString())
                add("stMethod" to entry.method.toString())
            }
            add("stErrorNums" to (if (errorCount != 0) errorCount else entry?.attempts ?: 0).toString())
        }
    }

    /** Measures a request from sending it to reading the last byte of its answer. */
    internal fun measure(chain: Interceptor.Chain, request: Request, networkType: () -> Int): Response {
        val started = clock()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            failed()
            throw e
        }
        if (!response.isSuccessful) {
            failed()
            return response
        }
        val body = response.body
        var read = 0L
        var done = false
        // The size is what came over the network: a gzip answer is counted before it is inflated
        val gzip = response.header("Content-Encoding").equals("gzip", ignoreCase = true) &&
            request.header("Accept-Encoding") != null
        val counting = object : ForwardingSource(body.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val count = super.read(sink, byteCount)
                if (count > 0) read += count
                if (count == -1L) finish()
                return count
            }

            override fun close() {
                finish()
                super.close()
            }

            private fun finish() {
                if (done) return
                done = true
                record(clock() - started, read, networkType(), request.method == "POST")
            }
        }
        if (gzip) {
            return response.newBuilder()
                .removeHeader("Content-Encoding")
                .removeHeader("Content-Length")
                .body(GzipSource(counting).buffer().asResponseBody(body.contentType(), -1))
                .build()
        }
        return response.newBuilder()
            .body(counting.buffer().asResponseBody(body.contentType(), body.contentLength()))
            .build()
    }

    private companion object {
        const val MAX_ENTRIES = 20
    }
}

/**
 * Adds the request statistics to signed requests of the app's own stacks, and to protobuf
 * requests a signature over all their form fields and, beside the stoken, the BDUSS.
 */
class RequestExtrasInterceptor(
    private val profile: TiebaProfile,
    private val commonParams: CommonParams,
    private val statistics: RequestStatistics,
    private val networkType: () -> Int,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (profile.stack == TiebaStack.WEB_VIEW) return chain.proceed(chain.request())
        val request = chain.request()
        val body = request.body
        val rewritten = when {
            body is FormBody && (0 until body.size).any { body.name(it) == "sign" } -> {
                val params = (0 until body.size).map { body.name(it) to body.value(it) }
                val withSig = params.any { it.first == "sig" }
                val unsigned = params.filter { it.first != "sign" && it.first != "sig" } + statistics.next()
                FormBody.Builder().apply {
                    signedParams(unsigned, withSig).forEach { (name, value) -> add(name, value) }
                }.build()
            }

            body is MultipartFormBody && body.fileField == PROTOBUF_FIELD -> {
                val fields = body.fields.filter { it.first != "sign" }.toMutableList()
                // Requests that carry the session's stoken carry its BDUSS beside it
                val session = commonParams.session()
                if (session != null && fields.any { it.first == "stoken" } && fields.none { it.first == "BDUSS" }) {
                    fields += "BDUSS" to session.bduss
                }
                fields += statistics.next()
                MultipartFormBody(signedParams(fields, withSig = false), body.fileField, body.file)
            }

            else -> null
        }
        val sent = if (rewritten == null) request else request.newBuilder().method(request.method, rewritten).build()
        return statistics.measure(chain, sent, networkType)
    }

    private companion object {
        /** The part protobuf requests carry their message in. */
        const val PROTOBUF_FIELD = "data"
    }
}
