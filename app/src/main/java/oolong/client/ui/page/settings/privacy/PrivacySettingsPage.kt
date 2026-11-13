package oolong.client.ui.page.settings.privacy

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import oolong.client.KeepHistoryKey
import oolong.client.R
import oolong.client.ui.common.settings.SettingsHeader
import oolong.client.ui.common.settings.SettingsScreen
import oolong.client.ui.common.settings.settingsPage
import oolong.client.ui.navigation.BlackListKey
import oolong.client.ui.navigation.DislikedForumsKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.PrivacySettingsKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.ProvideNavigator

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
        switch(KeepHistoryKey.name, R.string.settings_keep_history, default = true, aliases = R.string.search_aliases_keep_history)
    }
}

@Composable
fun PrivacySettingsPage(navigator: Navigator = LocalNavigator.current) {
    ProvideNavigator(navigator) { SettingsScreen(PrivacySettings) }
}
