package oolong.client.ui.appwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Draws every placed widget again once the app is updated. An update clears what the launcher
 * showed for them to its loading look, which otherwise stayed until the system's next update,
 * up to half an hour later.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                WidgetUpdates.drawAll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
