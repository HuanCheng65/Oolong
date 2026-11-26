package oolong.client.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The app's settings, all in one store, each named by a [Setting] (see [SettingKeys]).
 *
 * Values are read from memory: the store is read once, when the settings are first asked for, and
 * followed from then on. A write reaches memory at once and the store in the order written, so a
 * setting read back right after it is set is the new value, and two quick writes of one setting
 * never land the wrong way round.
 */
class Settings internal constructor(private val store: DataStore<Preferences>) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val writes = Dispatchers.IO.limitedParallelism(1)

    private val values = MutableStateFlow(runBlocking { store.data.first() })

    /** The settings written to memory whose writes haven't reached the store, by how many are on the way. */
    private val pending = mutableMapOf<Preferences.Key<*>, Int>()

    init {
        scope.launch {
            store.data.collect { stored ->
                synchronized(pending) {
                    // A write still on its way wins over what the store held before it
                    values.value = if (pending.isEmpty()) {
                        stored
                    } else {
                        stored.toMutablePreferences().apply { pending.keys.forEach { copy(it, values.value, this) } }
                    }
                }
            }
        }
    }

    /** Every stored setting, for what reads several at once. */
    val all: StateFlow<Preferences> = values.asStateFlow()

    operator fun <T> get(setting: Setting<T>): T = values.value[setting.key] ?: setting.default

    /** The setting's value now and whenever it changes. */
    fun <T> flow(setting: Setting<T>): Flow<T> = values.map { it[setting.key] ?: setting.default }.distinctUntilChanged()

    /** Sets [setting]; null, for a setting that may be unset, removes it. */
    operator fun <T> set(setting: Setting<T>, value: T) = write(setting.key) { if (value == null) it.remove(setting.key) else it[setting.key] = value }

    /** Removes [setting], so that it reads as its default again. */
    fun remove(setting: Setting<*>) = write(setting.key) { it.remove(setting.key) }

    private fun write(key: Preferences.Key<*>, change: (MutablePreferences) -> Unit) {
        synchronized(pending) {
            pending[key] = (pending[key] ?: 0) + 1
            values.value = values.value.toMutablePreferences().apply(change)
        }
        scope.launch(writes) {
            try {
                store.edit(change)
            } finally {
                synchronized(pending) {
                    val left = (pending[key] ?: 1) - 1
                    if (left == 0) pending.remove(key) else pending[key] = left
                }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: Settings? = null

        /** The one [Settings] of this process, over the store [dataStore] opens. */
        fun of(context: Context): Settings = instance ?: synchronized(this) {
            instance ?: Settings(context.applicationContext.dataStore).also { instance = it }
        }

        @Suppress("UNCHECKED_CAST")
        private fun copy(key: Preferences.Key<*>, from: Preferences, to: MutablePreferences) {
            val value = from[key]
            if (value == null) to.remove(key) else to[key as Preferences.Key<Any>] = value
        }
    }
}
