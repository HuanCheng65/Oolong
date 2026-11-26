package oolong.client.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import oolong.client.BuildConfig
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.data.update.UpdateChannel
import oolong.client.data.update.UpdateHost
import oolong.client.util.Roms
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.random.Random

/**
 * Usage statistics, sent only when the person agreed: at most one heartbeat a UTC day, with the
 * app's version and the kind of device, and nothing that ties one heartbeat to another. Whether it
 * is the first of the ISO week or of the month is worked out here, so that the server can count
 * active devices without telling them apart. Model, full ROM version and locale go only in the
 * month's first heartbeat. Debug builds send nothing.
 */
object UsageStats {
    /** What a heartbeat would hold now; the device part of it is what 诊断信息 shows. */
    data class Device(
        val version: String,
        val channel: String,
        val sdk: Int,
        val brand: String,
        val rom: String,
        val form: String,
        val model: String,
        val romVersion: String,
        val locale: String,
    )

    fun device(context: Context, channel: String): Device = Device(
        version = BuildConfig.VERSION_NAME,
        channel = channel,
        sdk = Build.VERSION.SDK_INT,
        brand = Build.MANUFACTURER.orEmpty().take(32),
        rom = Roms.family(),
        form = form(context),
        model = Build.MODEL.orEmpty().take(64),
        romVersion = Roms.version().take(100),
        locale = Locale.getDefault().let { listOf(it.language, it.country).filter(String::isNotEmpty).joinToString("-") }.take(20),
    )

    /** Sends today's heartbeat in the background, for right after the person agreed. */
    fun sendSoon(context: Context) {
        val app = context.applicationContext
        scope.launch { sendIfDue(app) }
    }

    /** Sends today's heartbeat if one is due and the person agreed. Blocking. */
    suspend fun sendIfDue(context: Context) = sending.withLock { send(context) }

    private suspend fun send(context: Context) {
        if (BuildConfig.BUILD_TYPE != "release" || !Settings.of(context)[SettingKeys.UsageStats]) return
        val preferences = preferences(context)
        val now = System.currentTimeMillis()
        val today = utcDay(now)
        if (preferences.getString(LastDay, null) == date(today)) return
        val month = date(today).take(7)
        // Drawn once a month, so that a device either counts all month or not at all
        if (preferences.getString(SampleMonth, null) != month) {
            val rate = preferences.getFloat(Rate, 1f)
            preferences.edit {
                putString(SampleMonth, month)
                putBoolean(Sampled, rate >= 1f || Random.nextFloat() < rate)
            }
        }
        if (!preferences.getBoolean(Sampled, true)) {
            preferences.edit { putString(LastDay, date(today)) }
            return
        }
        val channel = Settings.of(context)[SettingKeys.UpdateChannel]
        val beat = heartbeat(
            device = device(context, channel),
            today = today,
            lastWeek = preferences.getString(LastWeek, null),
            lastMonth = preferences.getString(LastMonth, null),
            crashed = CrashLogs.list(context).any { it.time > preferences.getLong(LastSent, installTime(context)) },
            installed = installTime(context),
        )
        val rate = post(beat) ?: return
        preferences.edit {
            putString(LastDay, date(today))
            putString(LastWeek, date(isoWeek(today)))
            putString(LastMonth, month)
            putLong(LastSent, now)
            putFloat(Rate, rate)
            putString(LastPayload, beat.toString(2))
            putLong(LastPayloadTime, now)
        }
    }

    /** The last heartbeat sent and when, for 诊断信息; null before the first. */
    fun lastSent(context: Context): Pair<Long, String>? {
        val preferences = preferences(context)
        val payload = preferences.getString(LastPayload, null) ?: return null
        return preferences.getLong(LastPayloadTime, 0L) to payload
    }

    /**
     * A heartbeat for [device] sent on [today] (a UTC epoch day), after the last ones of [lastWeek]
     * (its Monday) and [lastMonth] (YYYY-MM). The install week goes along only in the first 8 weeks.
     */
    internal fun heartbeat(device: Device, today: Long, lastWeek: String?, lastMonth: String?, crashed: Boolean, installed: Long): JSONObject {
        val week = date(isoWeek(today)) != lastWeek
        val month = date(today).take(7) != lastMonth
        val weeks = (isoWeek(today) - isoWeek(utcDay(installed))) / 7
        return JSONObject()
            .put("version", device.version)
            .put("channel", device.channel)
            .put("sdk", device.sdk)
            .put("brand", device.brand)
            .put("rom", device.rom)
            .put("form", device.form)
            .put("crashed", crashed)
            .put("week", week)
            .put("month", month)
            .apply {
                if (weeks in 0L..7L) put("installWeek", weeks.toInt())
                if (month) {
                    put("model", device.model)
                    put("romVersion", device.romVersion)
                    put("locale", device.locale)
                }
            }
    }

    // Days are counted from 1970-01-01 in UTC; java.time needs API 26

    /** The UTC epoch day [time] falls on. */
    internal fun utcDay(time: Long): Long = Math.floorDiv(time, DayMillis)

    /** The Monday of [day]'s ISO week; 1970-01-01 was a Thursday. */
    private fun isoWeek(day: Long): Long = day - Math.floorMod(day + 3, 7L)

    /** [day] as YYYY-MM-DD, by the civil-from-days algorithm. */
    internal fun date(day: Long): String {
        val z = day + 719_468
        val era = Math.floorDiv(z, 146_097L)
        val dayOfEra = z - era * 146_097
        val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val mp = (5 * dayOfYear + 2) / 153
        val dayOfMonth = dayOfYear - (153 * mp + 2) / 5 + 1
        val month = if (mp < 10) mp + 3 else mp - 9
        val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
        return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, dayOfMonth)
    }

    private fun form(context: Context): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE) -> "foldable"
        context.resources.configuration.smallestScreenWidthDp >= 600 -> "tablet"
        else -> "phone"
    }

    private fun installTime(context: Context): Long =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime }.getOrDefault(0L)

    /** Sends [beat]; the share of devices that should send, or null when it didn't go. */
    private fun post(beat: JSONObject): Float? = runCatching {
        val connection = URL("$UpdateHost/stats").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = Timeout
            connection.readTimeout = Timeout
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(beat.toString().toByteArray()) }
            if (connection.responseCode !in 200..299) return@runCatching null
            val reply = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(reply).optDouble("rate", 1.0).toFloat().coerceIn(0.01f, 1f)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // So that a send at launch and one right after agreeing don't both go out
    private val sending = Mutex()

    private fun preferences(context: Context) = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)

    private const val Preferences = "usage_stats"
    private const val LastDay = "last_day"
    private const val LastWeek = "last_week"
    private const val LastMonth = "last_month"
    private const val LastSent = "last_sent"
    private const val Rate = "rate"
    private const val SampleMonth = "sample_month"
    private const val Sampled = "sampled"
    private const val LastPayload = "last_payload"
    private const val LastPayloadTime = "last_payload_time"
    private const val Timeout = 15_000
    private const val DayMillis = 86_400_000L
}
