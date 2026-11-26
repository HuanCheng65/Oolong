package oolong.client.ui.settings.privacy

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import oolong.client.R
import oolong.client.data.settings.SettingKeys
import oolong.client.navigation.BlackListKey
import oolong.client.navigation.DislikedForumsKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.PrivacySettingsKey
import oolong.client.navigation.ProvideNavigator
import oolong.client.ui.settings.components.SettingsHeader
import oolong.client.ui.settings.components.SettingsScreen
import oolong.client.ui.settings.components.settingsPage

/**
 * What the signed-in account keeps on the server about whom and what it shuts out, and whether
 * this device keeps a history of what is browsed.
 */
val PrivacySettings = settingsPage(R.string.title_privacy_settings, PrivacySettingsKey) {
    group(SettingsHeader.Gap(8.dp)) {
        link(R.string.title_black_list, to = BlackListKey)
        link(R.string.settings_disliked_forums, to = DislikedForumsKey)
    }
    group(R.string.title_history) {
        switch(SettingKeys.KeepHistory, R.string.settings_keep_history, aliases = R.string.search_aliases_keep_history)
    }
}

@Composable
fun PrivacySettingsPage(navigator: Navigator = LocalNavigator.current) {
    ProvideNavigator(navigator) { SettingsScreen(PrivacySettings) }
}
