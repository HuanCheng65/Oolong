package oolong.client.data.history

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings

/**
 * Browsing without leaving a trace on this device: while on, no visits or searches are kept. It is
 * held only in memory, so it ends with the app's process. Apart from it, the user can stop keeping
 * history for good in the privacy settings ([keepsHistory]).
 */
object Incognito {
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on.asStateFlow()

    private val _keepsHistory = MutableStateFlow(true)

    /** Whether the user keeps a history at all, as set in the privacy settings ([SettingKeys.KeepHistory]). */
    val keepsHistory: StateFlow<Boolean> = _keepsHistory.asStateFlow()

    /** Whether a visit or a search made now is kept: neither incognito nor history turned off. */
    val recording: Boolean get() = !_on.value && _keepsHistory.value

    fun set(on: Boolean) {
        _on.value = on
    }

    /** Follows [SettingKeys.KeepHistory] from now on, as the app starts. */
    fun follow(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            Settings.of(context).flow(SettingKeys.KeepHistory).collect { _keepsHistory.value = it }
        }
    }
}
