package oolong.client.data.update

import android.content.Context
import android.os.Build
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import oolong.client.App
import oolong.client.data.settings.AppDataPreferences

/**
 * Install-level state that `c/s/login` and `c/s/sync` report.
 */
object InstallState {
    private const val KEY_LOGGED_IN_BEFORE = "logged_in_before"
    private const val KEY_LAST_PUSH_SWITCH = "last_push_system_switch"
    private const val KEY_LAST_PUSH_SWITCH_TIME = "last_push_system_switch_time"

    private val prefs
        get() = App.INSTANCE.getSharedPreferences(AppDataPreferences, Context.MODE_PRIVATE)

    /** `first_login`: `"1"` until an account has logged in on this device, as [hasAccounts] also tells. */
    fun firstLogin(hasAccounts: Boolean): String =
        if (prefs.getBoolean(KEY_LOGGED_IN_BEFORE, false) || hasAccounts) "0" else "1"

    fun onLoginSucceeded() {
        prefs.edit { putBoolean(KEY_LOGGED_IN_BEFORE, true) }
    }

    val runningAbi: String get() = if (Process.is64Bit()) "64" else "32"

    val supportAbi: String get() = if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "64" else "32"

    data class PushSwitch(val current: String, val last: String, val lastChangeTime: Long)

    /**
     * Whether notifications are enabled, with the value and time of the last change seen, which
     * is recorded when the switch differs from what was saved.
     */
    fun pushSwitch(): PushSwitch {
        val current =
            if (NotificationManagerCompat.from(App.INSTANCE).areNotificationsEnabled()) "1" else "0"
        val last = prefs.getString(KEY_LAST_PUSH_SWITCH, null) ?: current
        val lastChangeTime = prefs.getLong(KEY_LAST_PUSH_SWITCH_TIME, 0L)
        if (current != prefs.getString(KEY_LAST_PUSH_SWITCH, null)) {
            prefs.edit {
                putString(KEY_LAST_PUSH_SWITCH, current)
                if (current != last) putLong(KEY_LAST_PUSH_SWITCH_TIME, System.currentTimeMillis())
            }
        }
        return PushSwitch(current, last, lastChangeTime)
    }
}
