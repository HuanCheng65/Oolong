package oolong.client

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Browsing without leaving a trace on this device: while on, no visits or searches are kept. It is
 * held only in memory, so it ends with the app's process. Apart from it, the user can stop keeping
 * history for good in the privacy settings ([keepsHistory]).
 */
object Incognito {
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on.asStateFlow()

    private val _keepsHistory = MutableStateFlow(true)

    /** Whether the user keeps a history at all, as set in the privacy settings under [KeepHistoryKey]. */
    val keepsHistory: StateFlow<Boolean> = _keepsHistory.asStateFlow()

    /** Whether a visit or a search made now is kept: neither incognito nor history turned off. */
    val recording: Boolean get() = !_on.value && _keepsHistory.value

    fun set(on: Boolean) {
        _on.value = on
    }

    /** Follows [KeepHistoryKey] in the app's preferences from now on, as the app starts. */
    fun follow(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            context.dataStore.data
                .map { it[KeepHistoryKey] ?: true }
                .distinctUntilChanged()
                .collect { _keepsHistory.value = it }
        }
    }
}

/** Whether visits and searches are kept, unless browsing incognito; on unless the user turns it off. */
val KeepHistoryKey = booleanPreferencesKey("keepHistory")
