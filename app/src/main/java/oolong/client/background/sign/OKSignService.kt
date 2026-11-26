package oolong.client.background.sign

import android.Manifest
import android.app.IntentService
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import oolong.client.R
import oolong.client.background.sign.ProgressListener
import oolong.client.background.sign.SingleAccountSigner
import oolong.client.data.account.AccountRepository
import oolong.client.data.forum.FollowedForums
import oolong.client.model.SignDataBean
import oolong.client.ui.designsystem.theme.currentColors
import oolong.client.util.addFlag
import oolong.client.util.pendingIntentFlagImmutable
import oolong.core.network.forum.LikeRemoteDataSource
import oolong.core.network.sign.SignRemoteDataSource
import oolong.core.network.sign.SignResponse
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class OKSignService : IntentService(TAG), CoroutineScope, ProgressListener {
    @Inject
    lateinit var signs: SignRemoteDataSource

    @Inject
    lateinit var likes: LikeRemoteDataSource

    @Inject
    lateinit var accounts: AccountRepository

    @Inject
    lateinit var followed: FollowedForums

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private var lastSignData: SignDataBean? = null

    private val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")
        if (intent?.action == ACTION_START_SIGN) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    progressNotification(getString(R.string.title_loading_data), getString(R.string.text_please_wait), 0, 0)
                )
            } catch (e: RuntimeException) {
                // Some systems refuse the foreground even with the permission declared, or refuse
                // it from the background (IllegalStateException from Android 12); the signing
                // then runs as an ordinary service, with the same notifications
                Log.w(TAG, "startForeground refused", e)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onHandleIntent(intent: Intent?) {
        Log.i(TAG, "onHandleWork")
        if (intent?.action == ACTION_START_SIGN) {
            // Begun already when started from the app; this covers any other way in
            SignProgress.begin()
            try {
                sign()
            } finally {
                SignProgress.finish()
            }
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    private fun sign() {
        val loginInfo = accounts.current.value
        if (loginInfo != null) {
            runBlocking {
                SingleAccountSigner(
                    this@OKSignService,
                    loginInfo,
                    accounts,
                    signs,
                    likes,
                )
                    .apply {
                        setProgressListener(this@OKSignService)
                    }
                    .start()
            }
        } else {
            updateNotification(
                getString(R.string.title_oksign_fail),
                getString(R.string.text_login_first)
            )
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        }
    }

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannelCompat.Builder(
                NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
                .setName(getString(R.string.title_oksign))
                .setLightsEnabled(false)
                .setShowBadge(false)
                .build()
        )
    }

    private fun buildNotification(title: String, text: String?): NotificationCompat.Builder {
        createNotificationChannel()
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)
            .setContentText(text)
            .setContentTitle(title)
            .setSubText(getString(R.string.title_oksign))
            .setSmallIcon(R.drawable.ic_oksign)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle())
            .setColor(currentColors(this).accent.toArgb())
    }

    private fun updateNotification(title: String, text: String, intent: Intent?) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(title, text)
                .apply {
                    if (intent != null) {
                        setContentIntent(
                            PendingIntent.getActivity(
                                this@OKSignService,
                                0,
                                intent,
                                pendingIntentFlagImmutable()
                            )
                        )
                    }
                }
                .build()
        )
    }

    private fun updateNotification(title: String, text: String?) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = buildNotification(title, text)
            .build()
        notification.flags = notification.flags.addFlag(NotificationCompat.FLAG_ONGOING_EVENT)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun progressNotification(title: String, text: String?, done: Int, total: Int): Notification =
        signProgress(buildNotification(title, text), done, total)

    private fun updateProgress(title: String, text: String?, done: Int, total: Int) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(NOTIFICATION_ID, progressNotification(title, text, done, total))
    }

    /**
     * The live update, finished: it stays where the progress was for a while, then goes; tapping
     * it opens the app and clears it.
     */
    private fun showDone(text: String, total: Int, intent: Intent?) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val builder = buildNotification(getString(R.string.title_oksign_finish), text)
            .setTimeoutAfter(DONE_SHOWN_MILLIS)
        if (intent != null) {
            builder.setContentIntent(PendingIntent.getActivity(this, 0, intent, pendingIntentFlagImmutable()))
        }
        notificationManager.notify(
            NOTIFICATION_ID,
            signProgress(builder, done = total, total = total, chip = getString(R.string.sign_done_chip)),
        )
    }

    private fun clearNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    override fun onStart(total: Int) {
        SignProgress.total(total)
        updateProgress(getString(R.string.title_start_sign), null, 0, total)
        // Where the progress shows on screen, it says the signing began
        if (total > 0 && !SignProgress.watched) Toast.makeText(
            this,
            R.string.toast_oksign_start,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onMSigned(forumIds: List<Long>, done: Int) {
        SignProgress.signed(forumIds, done)
    }

    override fun onMSignRefused(reason: String) {
        Toast.makeText(this, getString(R.string.toast_msign_refused, reason), Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancel()
    }

    override fun onProgressStart(signDataBean: SignDataBean, current: Int, total: Int) {
        lastSignData = signDataBean
        updateProgress(
            getString(
                R.string.title_signing_progress,
                signDataBean.userName,
                current,
                total
            ),
            getString(
                R.string.title_forum_name,
                signDataBean.forumName
            ),
            current,
            total,
        )
    }

    override fun onProgressFinish(
        signDataBean: SignDataBean,
        signResultBean: SignResponse,
        current: Int,
        total: Int
    ) {
        signDataBean.forumId.toLongOrNull()?.let { SignProgress.signed(listOf(it), current + 1) }
        updateProgress(
            getString(
                R.string.title_signing_progress,
                signDataBean.userName,
                current + 1,
                total
            ),
            signResultBean.userInfo?.signBonusPoint?.let {
                getString(R.string.text_singing_progress_exp, signDataBean.forumName, it)
            } ?: getString(R.string.text_singing_progress, signDataBean.forumName),
            current + 1,
            total,
        )
    }

    override fun onFinish(success: Boolean, signedCount: Int, total: Int) {
        AutoSign.record(this, signedCount, total, success)
        SignProgress.finish()
        // The followed forums shown now pick up their new signed state
        followed.changed()
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        if (total > 0) {
            showDone(resources.getQuantityString(R.plurals.text_oksign_done, signedCount, signedCount), total, launch)
        } else {
            updateNotification(getString(R.string.title_oksign_finish), getString(R.string.text_oksign_no_signable), launch)
        }
        sendBroadcast(Intent(ACTION_SIGN_SUCCESS_ALL))
    }

    override fun onFailure(current: Int, total: Int, errorCode: Int, errorMsg: String) {
        lastSignData.let {
            if (it == null) {
                AutoSign.record(this, 0, total, success = false)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                updateNotification(getString(R.string.title_oksign_fail), errorMsg)
            } else {
                SignProgress.tried(current + 1)
                updateProgress(
                    getString(
                        R.string.title_signing_progress,
                        it.userName,
                        current + 1,
                        total
                    ),
                    getString(R.string.text_singing_progress_fail, it.forumName, errorMsg),
                    current + 1,
                    total,
                )
            }
        }
    }

    companion object {
//        fun enqueueWork(context: Context, work: Intent) {
//            enqueueWork(context, OKSignService::class.java, JOB_ID, work)
//        }
//
//        private const val JOB_ID = 233

        const val TAG = "OKSignService"
        const val NOTIFICATION_CHANNEL_ID = "1"
        const val NOTIFICATION_ID = 1
        private const val DONE_SHOWN_MILLIS = 30 * 60 * 1000L
        const val ACTION_SIGN_SUCCESS_ALL =
            "oolong.client.service.action.SIGN_SUCCESS_ALL"
        const val ACTION_START_SIGN = "oolong.client.service.action.ACTION_SIGN_START"
    }
}

/**
 * The signing under way as a live update: kept at the top of the shade and on the lock screen,
 * with how far it has come in a chip in the status bar, where the system shows them, or [chip] in
 * its place. Before the forums are known, [total] is 0 and the bar runs without an end.
 */
private fun signProgress(builder: NotificationCompat.Builder, done: Int, total: Int, chip: String? = null): Notification {
    // Not apply {}: a style has an apply of its own
    val progress = NotificationCompat.ProgressStyle()
    if (total > 0) {
        progress.addProgressSegment(NotificationCompat.ProgressStyle.Segment(total)).setProgress(done)
    } else {
        progress.setProgressIndeterminate(true)
    }
    return builder
        .setStyle(progress)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setRequestPromotedOngoing(true)
        .apply {
            val text = chip ?: "$done/$total".takeIf { total > 0 }
            if (text != null) setShortCriticalText(text)
        }
        .build()
}
