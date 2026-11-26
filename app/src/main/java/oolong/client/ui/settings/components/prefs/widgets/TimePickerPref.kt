package oolong.client.ui.settings.components.prefs.widgets

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import androidx.datastore.preferences.core.edit
import oolong.client.data.settings.Setting
import oolong.client.data.settings.rememberSettings
import oolong.client.data.settings.settingAsState
import oolong.client.ui.designsystem.TimePickerDialog
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.theme.ExtendedTheme

/**
 * Preference which shows a TextField in a Dialog
 *
 * @param setting The setting this Pref shows and sets
 * @param title Main text which describes the Pref
 * @param modifier Modifier applied to the Text aspect of this Pref
 * @param summary Used to give some more information about what this Pref is for
 * @param dialogTitle Title shown in the dialog. No title if null.
 * @param dialogMessage Summary shown underneath [dialogTitle]. No summary if null.
 * @param onValueSaved Will be called with new TextField value when the confirm button is clicked. It is NOT called every time the value changes. Use [onValueChange] for that.
 * @param onValueChange Will be called every time the TextField value is changed.
 * @param textColor Text colour of the [title] and [summary]
 * @param enabled If false, this Pref cannot be clicked.
 */
@ExperimentalComposeUiApi
@ExperimentalMaterialApi
@Composable
fun TimePickerPerf(
    setting: Setting<String>,
    title: String,
    modifier: Modifier = Modifier,
    summary: @Composable (value: String) -> String? = { null },
    dialogTitle: String? = null,
    dialogMessage: String? = null,
    onValueSaved: (String) -> Unit = {},
    onValueChange: (String) -> Unit = {},
    textColor: Color = ExtendedTheme.colors.text,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val dialogState = rememberDialogState()
    val settings = rememberSettings()
    // Changes only when the dialog is confirmed
    val value by settingAsState(setting)
    // The picker's, which changes with every turn
    var timeVal by remember { mutableStateOf(value) }

    var dialogSize by remember { mutableStateOf(Size.Zero) }

    fun edit() {
        settings[setting] = timeVal
        onValueSaved(timeVal)
    }

    TextPref(
        title = title,
        modifier = modifier,
        summary = summary(value),
        textColor = textColor,
        trailingContent = { Text(text = value) },
        enabled = enabled,
        leadingIcon = leadingIcon,
        onClick = { if (enabled) dialogState.show() },
    )

    if (dialogState.show) {
        // reset
        LaunchedEffect(null) {
            timeVal = value
        }

        TimePickerDialog(
            title = {
                if (dialogTitle != null) {
                    Text(text = dialogTitle)
                }
            },
            currentTime = timeVal,
            onConfirm = {
                timeVal = it
                onValueChange(it)
                edit()
            },
            modifier = Modifier.onGloballyPositioned {
                dialogSize = it.size.toSize()
            },
            dialogState = dialogState,
            onValueChange = {
                timeVal = it.toString()
                onValueChange(it.toString())
            },
        ) {
            if (dialogMessage != null) {
                Text(text = dialogMessage)
            }
        }
    }
}
