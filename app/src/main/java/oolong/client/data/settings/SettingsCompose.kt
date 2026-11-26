package oolong.client.data.settings

import androidx.compose.runtime.Composable
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
