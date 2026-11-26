package oolong.client.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.settingAsState

/** [ipLocation] as floors and profiles show it: null when it is empty or the viewer hides it. */
@Composable
fun shownIpLocation(ipLocation: String): String? {
    val hidden by settingAsState(SettingKeys.HideIpLocation)
    return ipLocation.takeUnless { hidden || it.isEmpty() }
}
