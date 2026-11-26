package oolong.client.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** A stored setting: the [key] it is stored under, and what it is while nothing is stored. */
class Setting<T>(val key: Preferences.Key<T & Any>, val default: T) {
    override fun toString() = "Setting(${key.name})"

    companion object {
        fun boolean(name: String, default: Boolean): Setting<Boolean> = Setting(booleanPreferencesKey(name), default)

        fun int(name: String, default: Int = 0): Setting<Int> = Setting(intPreferencesKey(name), default)

        fun long(name: String, default: Long = 0L): Setting<Long> = Setting(longPreferencesKey(name), default)

        fun float(name: String, default: Float = 0f): Setting<Float> = Setting(floatPreferencesKey(name), default)

        fun string(name: String, default: String): Setting<String> = Setting(stringPreferencesKey(name), default)

        /** A number that may be unset, as a width not yet dragged. */
        fun optionalFloat(name: String): Setting<Float?> = Setting(floatPreferencesKey(name), null)

        /** A string that may be unset, as a path or a colour not yet chosen. */
        fun optionalString(name: String): Setting<String?> = Setting(stringPreferencesKey(name), null)
    }
}
