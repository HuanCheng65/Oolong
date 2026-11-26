package oolong.client.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            folder.newFile("test.preferences_pb").apply { delete() }
        }
    }

    private val flag = Setting.boolean("flag", true)
    private val name = Setting.string("name", "默认")
    private val path = Setting.optionalString("path")

    /** Waits until the store holds what [check] wants. */
    private fun awaitStored(check: (Preferences) -> Boolean) = runBlocking {
        withTimeout(5_000) { store.data.first(check) }
    }

    @Test
    fun `an unset setting reads as its default`() {
        val settings = Settings(store)

        assertEquals(true, settings[flag])
        assertEquals("默认", settings[name])
        assertNull(settings[path])
    }

    @Test
    fun `a setting reads back at once and reaches the store`() {
        val settings = Settings(store)

        settings[flag] = false

        assertEquals(false, settings[flag])
        awaitStored { it[flag.key] == false }
    }

    @Test
    fun `quick writes of one setting leave the last stored`() {
        val settings = Settings(store)

        repeat(50) { settings[name] = "第 $it 次" }

        assertEquals("第 49 次", settings[name])
        awaitStored { it[name.key] == "第 49 次" }
        // Once the store has caught up, memory still holds the last write
        runBlocking { withTimeout(5_000) { settings.flow(name).first { it == "第 49 次" } } }
    }

    @Test
    fun `setting null or removing brings the default back`() {
        val settings = Settings(store)
        settings[path] = "/a.jpg"
        settings[name] = "名字"
        awaitStored { it[path.key] == "/a.jpg" && it[name.key] == "名字" }

        settings[path] = null
        settings.remove(name)

        assertNull(settings[path])
        assertEquals("默认", settings[name])
        awaitStored { it[path.key] == null && it[name.key] == null }
    }

    @Test
    fun `what was stored before is read at the start`() {
        runBlocking { store.edit { it[stringPreferencesKey("name")] = "存过的" } }

        assertEquals("存过的", Settings(store)[name])
    }

    @Test
    fun `a write made straight to the store is followed`() {
        val settings = Settings(store)

        runBlocking { store.edit { it[flag.key] = false } }

        runBlocking { withTimeout(5_000) { settings.flow(flag).first { !it } } }
    }
}
