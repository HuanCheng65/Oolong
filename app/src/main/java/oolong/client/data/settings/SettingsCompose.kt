package oolong.client.data.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** The app's [Settings], for composition, where they can't be injected. */
@Composable
fun rememberSettings(): Settings {
    val context = LocalContext.current
    return remember(context) { Settings.of(context) }
}

/** [setting]'s value, from the first frame on, following its changes. */
@Composable
fun <T> settingAsState(setting: Setting<T>): State<T> {
    val settings = rememberSettings()
    return remember(settings, setting) { settings.flow(setting) }.collectAsState(initial = settings[setting])
}

/** [setting] as state that writes it when set, for a control that changes it. */
@Composable
fun <T> settingAsMutableState(setting: Setting<T>): MutableState<T> {
    val settings = rememberSettings()
    val state = settingAsState(setting)
    return remember(settings, setting, state) { SettingState(settings, setting, state) }
}

private class SettingState<T>(private val settings: Settings, private val setting: Setting<T>, private val state: State<T>) : MutableState<T> {
    override var value: T
        get() = state.value
        set(value) {
            settings[setting] = value
        }

    override fun component1(): T = value

    override fun component2(): (T) -> Unit = { value = it }
}
