package oolong.client.diagnostics

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.data.update.UpdateHost
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Sends kept crash records to the developer, when the person agrees: each time they are asked, or
 * always if they chose so. A record is sent as it was kept, which leaves out sign-in tokens, with a
 * random id made once per install so that the reports of one install count once. A send that fails
 * is tried again at the next start, for a week.
 */
object CrashReports {
    enum class Mode(val value: String) { Ask("ask"), Always("always"), Never("never") }

    /** The setting under which records are sent; its values are [Mode.value]. */
    const val ModeKey = "crash_report_mode"

    suspend fun mode(context: Context): Mode {
        val value = Settings.of(context)[SettingKeys.CrashReportMode]
        return Mode.entries.firstOrNull { it.value == value } ?: Mode.Ask
    }

    /** Whether [entry] can be sent: only records of release builds, whose traces the developer can read. */
    fun canSend(context: Context, entry: CrashLogs.Entry): Boolean =
        CrashLogs.read(context, entry.id)?.let { parse(it, entry.kind) } != null

    fun isSent(context: Context, entry: CrashLogs.Entry): Boolean =
        entry.id in preferences(context).getStringSet(Sent, emptySet()).orEmpty()

    /** Sends [entry] now, or keeps it to be tried again at the next start; true once it is sent. Blocking. */
    fun send(context: Context, entry: CrashLogs.Entry): Boolean {
        if (isSent(context, entry)) return true
        val report = CrashLogs.read(context, entry.id)?.let { parse(it, entry.kind) } ?: return false
        val sent = post(report.put("install", installId(context)))
        preferences(context).edit {
            val waiting = preferences(context).getStringSet(Waiting, emptySet()).orEmpty()
            if (sent) {
                putStringSet(Waiting, waiting - entry.id)
                putStringSet(Sent, preferences(context).getStringSet(Sent, emptySet()).orEmpty() + entry.id)
            } else {
                putStringSet(Waiting, waiting + entry.id)
            }
        }
        return sent
    }

    /** Tries again the records whose sending failed, dropping those older than a week. Blocking. */
    fun sendWaiting(context: Context) {
        val waiting = preferences(context).getStringSet(Waiting, emptySet()).orEmpty()
        if (waiting.isEmpty()) return
        val since = System.currentTimeMillis() - Week
        val entries = CrashLogs.list(context).filter { it.id in waiting }
        preferences(context).edit { putStringSet(Waiting, entries.filter { it.time >= since }.map { it.id }.toSet()) }
        entries.filter { it.time >= since }.forEach { send(context, it) }
    }

    /**
     * What the Worker takes from a kept [record] of [kind], or null when it isn't from a release
     * build or holds no trace. The record is CrashLogs' header, a blank line and the trace.
     */
    internal fun parse(record: String, kind: CrashLogs.Kind): JSONObject? {
        val header = record.substringBefore("\n\n").lines()
        val body = record.substringAfter("\n\n", "")
        // "Oolong <versionName> (<versionCode>) <build type>"
        val build = Regex("""^Oolong (\S+) \(\d+\) (\S+)$""").find(header.firstOrNull().orEmpty()) ?: return null
        if (build.groupValues[2] != "release") return null
        val fields = header.drop(1)
        fun field(name: String) = fields.firstOrNull { it.startsWith("$name: ") }?.removePrefix("$name: ")?.takeIf { it != "-" }
        val known = setOf("Android ", "System: ", "Time: ", "Page: ", "Thread: ")
        val detail = fields.firstOrNull { line -> known.none { line.startsWith(it) } }
        val trace = when (kind) {
            CrashLogs.Kind.Crash -> body
            CrashLogs.Kind.Freeze -> mainThread(body)
            // The system tells only the signal and where, in a line
            CrashLogs.Kind.Native -> detail.orEmpty()
        }.take(MaxTrace)
        if (trace.isBlank()) return null
        return JSONObject()
            .put("version", build.groupValues[1])
            .put(
                "kind",
                when (kind) {
                    CrashLogs.Kind.Crash -> "crash"
                    CrashLogs.Kind.Freeze -> "anr"
                    CrashLogs.Kind.Native -> "native"
                },
            )
            .put("trace", trace)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".take(100))
            .put("rom", Build.DISPLAY.take(100))
            .apply {
                field("Page")?.let { put("page", it.take(100)) }
                field("Thread")?.let { put("thread", it.take(100)) }
            }
    }

    /** A freeze's main thread from the dump of every thread: from its `"main"` line to the next blank one. */
    private fun mainThread(dump: String): String {
        val lines = dump.lines()
        val start = lines.indexOfFirst { it.startsWith("\"main\"") }
        if (start < 0) return dump.take(MaxTrace)
        val end = lines.drop(start).indexOfFirst { it.isBlank() }.let { if (it < 0) lines.size else start + it }
        return lines.subList(start, end).joinToString("\n")
    }

    internal fun post(report: JSONObject): Boolean = runCatching {
        val connection = URL("$UpdateHost/crash").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = Timeout
            connection.readTimeout = Timeout
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(report.toString().toByteArray()) }
            // Turned away for good: too many from this install today, or not taken; trying again won't help
            connection.responseCode in 200..299 || connection.responseCode in 400..499
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    internal fun installId(context: Context): String {
        val preferences = preferences(context)
        preferences.getString(Install, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        preferences.edit { putString(Install, id) }
        return id
    }

    private fun preferences(context: Context) = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)

    private const val Preferences = "crash_reports"
    private const val Install = "install"
    private const val Sent = "sent"
    private const val Waiting = "waiting"
    private const val Week = 7 * 24 * 60 * 60 * 1000L
    private const val Timeout = 15_000

    /** The Worker takes reports of up to 64 KB; escaped as JSON, a trace may grow by a part. */
    private const val MaxTrace = 48 * 1024
}
