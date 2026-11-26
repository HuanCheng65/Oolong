package oolong.client.data.settings

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import oolong.client.data.legacy.LegacyPackageMigration
import oolong.client.data.settings.ThemeSettings

object DataStoreConst {
    const val DATA_STORE_NAME = "app_preferences"
}

private val dataStoreInstance by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
    preferencesDataStore(
        name = DataStoreConst.DATA_STORE_NAME,
        produceMigrations = { context ->
            listOf(
                SharedPreferencesMigration(context, "settings"),
                LegacyPackageMigration,
                object : DataMigration<Preferences> {
                    override suspend fun cleanUp() {}

                    override suspend fun migrate(currentData: Preferences): Preferences {
                        return currentData.toMutablePreferences().apply {
                            set(stringPreferencesKey("dark_theme"), "grey_dark")
                        }.toPreferences()
                    }

                    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
                        return currentData[stringPreferencesKey("dark_theme")] == "dark"
                    }
                },
                // The forum page's button no longer offers posting, which only ever hid the button
                object : DataMigration<Preferences> {
                    override suspend fun cleanUp() {}

                    override suspend fun migrate(currentData: Preferences): Preferences {
                        return currentData.toMutablePreferences().apply {
                            set(stringPreferencesKey("forumFabFunction"), "hide")
                        }.toPreferences()
                    }

                    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
                        return currentData[stringPreferencesKey("forumFabFunction")] == "post"
                    }
                },
                // The theme was one name, rewritten on every switch between light and dark
                object : DataMigration<Preferences> {
                    override suspend fun cleanUp() {}

                    override suspend fun migrate(currentData: Preferences): Preferences {
                        return currentData.toMutablePreferences().apply {
                            ThemeSettings.migrate(currentData).writeTo(this)
                        }.toPreferences()
                    }

                    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
                        return ThemeSettings.needsMigration(currentData)
                    }
                },
                // The wallpaper had a colour of its own; it takes the theme colour now
                object : DataMigration<Preferences> {
                    override suspend fun cleanUp() {}

                    override suspend fun migrate(currentData: Preferences): Preferences {
                        return currentData.toMutablePreferences().apply { ThemeSettings.moveWallpaperColor(this) }.toPreferences()
                    }

                    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
                        return ThemeSettings.hasWallpaperColor(currentData)
                    }
                },
            )
        }
    )
}

val Context.dataStore: DataStore<Preferences> by dataStoreInstance

/** The SharedPreferences file the old app kept a few values of its own in: the device's id, the last push switch. */
const val AppDataPreferences = "appData"
