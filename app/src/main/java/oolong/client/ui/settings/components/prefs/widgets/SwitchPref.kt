package oolong.client.ui.settings.components.prefs.widgets

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.datastore.preferences.core.edit
import oolong.client.data.settings.Setting
import oolong.client.data.settings.rememberSettings
import oolong.client.data.settings.settingAsState
import oolong.client.ui.designsystem.Switch
import oolong.client.ui.designsystem.theme.ExtendedTheme

@ExperimentalMaterialApi
@Composable
fun SwitchPref(
    setting: Setting<Boolean>,
    title: String,
    modifier: Modifier = Modifier,
    summary: @Composable (value: Boolean) -> String? = { null },
    onCheckedChange: ((Boolean) -> Unit)? = null,
    textColor: Color = ExtendedTheme.colors.text,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val settings = rememberSettings()
    val checked by settingAsState(setting)

    fun edit(newState: Boolean) {
        onCheckedChange?.invoke(newState)
        settings[setting] = newState
    }

    TextPref(
        title = title,
        // The whole row is the switch, also to accessibility services, which read its title and state together
        modifier = modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch) { edit(it) },
        textColor = textColor,
        summary = summary(checked),
        darkenOnDisable = true,
        leadingIcon = leadingIcon,
        enabled = enabled,
    ) {
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = { edit(it) },
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
