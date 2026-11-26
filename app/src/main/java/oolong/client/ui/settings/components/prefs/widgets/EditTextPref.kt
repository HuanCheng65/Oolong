package oolong.client.ui.settings.components.prefs.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.datastore.preferences.core.edit
import oolong.client.data.settings.Setting
import oolong.client.data.settings.rememberSettings
import oolong.client.data.settings.settingAsState
import oolong.client.ui.designsystem.PromptDialog
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.settings.components.prefs.ifNotNullThen

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
 * @param dialogBackgroundColor Color of the dropdown menu
 * @param textColor Text colour of the [title] and [summary]
 * @param enabled If false, this Pref cannot be clicked.
 */
@ExperimentalComposeUiApi
@ExperimentalMaterialApi
@Composable
fun EditTextPref(
    setting: Setting<String?>,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    dialogTitle: String? = null,
    dialogMessage: String? = null,
    onValueSaved: ((String) -> Unit) = {},
    onValueChange: ((String) -> Unit) = {},
    dialogBackgroundColor: Color = ExtendedTheme.colors.background,
    textColor: Color = ExtendedTheme.colors.text,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val dialogState = rememberDialogState()
    val settings = rememberSettings()
    // Changes only when the dialog is confirmed
    val value = settingAsState(setting).value.orEmpty()
    // The text field's, which changes with every edit
    var textVal by remember { mutableStateOf(value) }

    var dialogSize by remember { mutableStateOf(Size.Zero) }

    fun edit() {
        settings[setting] = textVal
        onValueSaved(textVal)
    }

    TextPref(
        title = title,
        modifier = modifier,
        summary = summary,
        textColor = textColor,
        enabled = enabled,
        leadingIcon = leadingIcon,
        onClick = { if (enabled) dialogState.show() },
    )

    if (dialogState.show) {
        // reset
        LaunchedEffect(null) {
            textVal = value
        }

        PromptDialog(
            onConfirm = {
                textVal = it
                onValueChange(it)
                edit()
            },
            modifier = Modifier.onGloballyPositioned {
                dialogSize = it.size.toSize()
            },
            dialogState = dialogState,
            initialValue = textVal,
            onValueChange = { newVal, _ ->
                textVal = newVal
                onValueChange(newVal)
                true
            },
            title = {
                if (dialogTitle != null) {
                    Text(text = dialogTitle)
                }
            },
        ) {
            if (dialogMessage != null) {
                Text(text = dialogMessage)
            }
        }
    }
//        AlertDialog(
//            modifier = Modifier
//                .fillMaxWidth(0.9f)
//                .onGloballyPositioned {
//                    dialogSize = it.size.toSize()
//                },
//            onDismissRequest = { showDialog = false },
//            title = null,
//            text = null,
//            buttons = {
//                Column(
//                    verticalArrangement = Arrangement.SpaceBetween,
//                ) {
//                    DialogHeader(dialogTitle, dialogMessage)
//
//                    OutlinedTextField(
//                        value = textVal,
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(16.dp)
//                            .weight(1f, fill = false),
//                        onValueChange = {
//                            textVal = it
//                            onValueChange(it)
//                        }
//                    )
//
//                    Row(
//                        verticalAlignment = Alignment.CenterVertically,
//                        horizontalArrangement = Arrangement.End,
//                        modifier = Modifier.width(with(LocalDensity.current) { dialogSize.width.toDp() })
//                    ) {
//                        TextButton(
//                            modifier = Modifier.padding(end = 16.dp),
//                            onClick = { showDialog = false }
//                        ) {
//                            Text("Cancel", style = MaterialTheme.typography.body1)
//                        }
//
//                        TextButton(
//                            modifier = Modifier.padding(end = 16.dp),
//                            onClick = {
//                                edit()
//                                showDialog = false
//                            }
//                        ) {
//                            Text("Save", style = MaterialTheme.typography.body1)
//                        }
//                    }
//
//                }
//
//            },
//            properties = DialogProperties(usePlatformDefaultWidth = false),
//            backgroundColor = dialogBackgroundColor,
//        )
}

@Composable
fun DialogHeader(dialogTitle: String?, dialogMessage: String?) {
    Column(modifier = Modifier.padding(16.dp)) {
        dialogTitle.ifNotNullThen {
            Text(
                text = dialogTitle!!,
                style = MaterialTheme.typography.h6
            )
        }?.invoke()

        dialogMessage.ifNotNullThen {
            Text(
                text = dialogMessage!!,
                style = MaterialTheme.typography.subtitle1
            )
        }?.invoke()
    }
}
