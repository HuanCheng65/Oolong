package oolong.client.repository

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import oolong.client.R

/**
 * Hears how an update's install session went: passes on the system's confirmation when it asks for
 * one, and says so when the install failed. On success the app is replaced and restarted.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm == null || !context.tryStart(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                    // Some systems have nothing to show the confirmation with; their installer takes the package instead
                    val session = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
                    if (session != -1) runCatching { context.packageManager.packageInstaller.abandonSession(session) }
                    if (!context.updateRepository.installWithSystem()) failed(context)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> Unit

            // Turned down by the user, which needs no word from the app
            PackageInstaller.STATUS_FAILURE_ABORTED -> context.updateRepository.installEnded()

            else -> {
                Log.w("UpdateInstall", "status $status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                failed(context)
            }
        }
    }

    private fun failed(context: Context) {
        context.updateRepository.installEnded()
        Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_SHORT).show()
    }
}

/** Starts [intent], or says it could not. */
internal fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    Log.w("UpdateInstall", "nothing to start ${intent.action}", e)
    false
} catch (e: SecurityException) {
    Log.w("UpdateInstall", "not allowed to start ${intent.action}", e)
    false
}
