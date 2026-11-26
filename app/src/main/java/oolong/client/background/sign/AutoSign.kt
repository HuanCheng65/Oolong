package oolong.client.background.sign

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import oolong.client.R
import oolong.client.background.sign.AutoSignAlarm
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Signing in every day at the time the person chose, as reliably as the system allows.
 *
 * An exact alarm starts the signing at the time, which also lets it start in the foreground from
 * the background. Whatever stops that alarm, the day is caught up later: when the app opens,
 * when the phone starts, or when a periodic check finds the time passed with no signing yet. Each
 * signing that finishes is recorded, so the settings can say how the last one went.
 */
object AutoSign {
    /** How the last signing went, written by the signing service, which runs in its own process. */
    data class LastRun(val time: Long, val signed: Int, val total: Int, val success: Boolean)

    /** Sets the next alarm, or cancels it when signing in automatically is off. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val alarm = alarmIntent(context)
        if (!Settings.of(context)[SettingKeys.AutoSign]) {
            alarms.cancel(alarm)
            context.getSystemService(JobScheduler::class.java)?.cancel(CheckJobId)
            return
        }
        setAlarm(context, alarms, nextTime(context), alarm)
        scheduleCheck(context)
    }

    /** Signs in now if today's time has passed and today was not signed yet. */
    fun catchUp(context: Context, foreground: Boolean) {
        if (!isDue(context)) return
        if (foreground) {
            start(context)
        } else {
            // Starting in the foreground from the background takes an alarm's permission, so the
            // signing is handed to an alarm a moment from now
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            setAlarm(context, alarms, System.currentTimeMillis() + CatchUpDelayMillis, alarmIntent(context, catchUp = true))
        }
    }

    /** Signs in once, [delayMillis] from now, the way the daily alarm does: to see that it gets through. */
    fun test(context: Context, delayMillis: Long): Long {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return 0L
        val time = System.currentTimeMillis() + delayMillis
        setAlarm(context, alarms, time, alarmIntent(context, catchUp = true))
        return time
    }

    /** Starts the signing service in the foreground, as one may from the app or an alarm. */
    fun start(context: Context) {
        // One run at a time: asked again while one runs, that run is the answer
        if (!SignProgress.begin()) return
        markAttempt(context)
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OKSignService::class.java).setAction(OKSignService.ACTION_START_SIGN),
            )
        } catch (e: RuntimeException) {
            SignProgress.abandon()
            throw e
        }
    }

    /**
     * Starts the signing from an alarm. An alarm that isn't exact may not start anything in the
     * foreground, so then the person is told instead, and opening the app signs.
     */
    fun startFromAlarm(context: Context) {
        try {
            start(context)
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException, from Android 12
            notifyNotStarted(context)
        }
    }

    private fun notifyNotStarted(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        val notifications = NotificationManagerCompat.from(context)
        notifications.createNotificationChannel(
            NotificationChannelCompat.Builder(OKSignService.NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.title_oksign))
                .build(),
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        notifications.notify(
            OKSignService.NOTIFICATION_ID,
            NotificationCompat.Builder(context, OKSignService.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_oksign)
                .setContentTitle(context.getString(R.string.title_auto_sign_not_started))
                .setContentText(context.getString(R.string.text_auto_sign_not_started))
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE))
                .build(),
        )
    }

    /** Whether alarms go off on time; without the permission they may come late. */
    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    fun record(context: Context, signed: Int, total: Int, success: Boolean) {
        val file = lastRunFile(context)
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText("${System.currentTimeMillis()} $signed $total $success")
        temp.renameTo(file)
    }

    fun lastRun(context: Context): LastRun? = runCatching {
        val (time, signed, total, success) = lastRunFile(context).readText().trim().split(' ')
        LastRun(time.toLong(), signed.toInt(), total.toInt(), success.toBooleanStrict())
    }.getOrNull()

    private fun isDue(context: Context): Boolean {
        if (!Settings.of(context)[SettingKeys.AutoSign]) return false
        if (System.currentTimeMillis() < todayAt(context)) return false
        val today = day(System.currentTimeMillis())
        val last = lastRun(context)
        if (last != null && last.success && day(last.time) == today) return false
        // A day whose signing keeps failing is not tried without end
        val (attemptDay, count) = attempts(context)
        return attemptDay != today || count < MaxAttemptsPerDay
    }

    private fun markAttempt(context: Context) {
        val today = day(System.currentTimeMillis())
        val (attemptDay, count) = attempts(context)
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE).edit {
            putString(AttemptDay, today)
            putInt(Attempts, if (attemptDay == today) count + 1 else 1)
        }
    }

    private fun attempts(context: Context): Pair<String?, Int> {
        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        return preferences.getString(AttemptDay, null) to preferences.getInt(Attempts, 0)
    }

    /** Today at the chosen time, in the device's time zone. */
    private fun todayAt(context: Context): Long {
        val (hour, minute) = Settings.of(context)[SettingKeys.AutoSignTime].split(':')
            .mapNotNull { it.toIntOrNull() }
            .takeIf { it.size == 2 } ?: listOf(9, 0)
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Today at the time if it is still to come, else tomorrow at it. */
    private fun nextTime(context: Context): Long {
        val today = todayAt(context)
        if (today > System.currentTimeMillis()) return today
        return Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
    }

    /** The calendar day [time] falls on, as text to compare. */
    private fun day(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(time))

    private fun setAlarm(context: Context, alarms: AlarmManager, time: Long, intent: PendingIntent) {
        if (canScheduleExactAlarms(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent)
        }
    }

    /** A check every few hours, while online, that catches up a day the alarm missed. */
    private fun scheduleCheck(context: Context) {
        val jobs = context.getSystemService(JobScheduler::class.java) ?: return
        if (jobs.getPendingJob(CheckJobId) != null) return
        jobs.schedule(
            JobInfo.Builder(CheckJobId, ComponentName(context, AutoSignCheckJob::class.java))
                .setPeriodic(CheckIntervalMillis)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build(),
        )
    }

    private fun alarmIntent(context: Context, catchUp: Boolean = false): PendingIntent = PendingIntent.getBroadcast(
        context,
        if (catchUp) 1 else 0,
        Intent(context, AutoSignAlarm::class.java).putExtra(AutoSignAlarm.EXTRA_CATCH_UP, catchUp),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun lastRunFile(context: Context) = File(context.filesDir, "auto_sign_last")

    private const val DefaultTime = "09:00"
    private const val CatchUpDelayMillis = 5_000L
    private const val CheckIntervalMillis = 3 * 60 * 60 * 1000L
    private const val MaxAttemptsPerDay = 3

    /** Above the message check's job id, which is drawn from 0 to 99999. */
    private const val CheckJobId = 200_001

    private const val Preferences = "auto_sign"
    private const val AttemptDay = "attempt_day"
    private const val Attempts = "attempts"
}
