package oolong.client.ui.settings.components.prefs.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import oolong.client.R
import oolong.client.data.settings.Setting
import oolong.client.data.settings.rememberSettings
import oolong.client.data.settings.settingAsState
import oolong.client.ui.designsystem.Dialog
import oolong.client.ui.designsystem.DialogNegativeButton
import oolong.client.ui.designsystem.choiceMenuItems
import oolong.client.ui.designsystem.clickMenu
import oolong.client.ui.designsystem.menuAnchor
import oolong.client.ui.designsystem.picker.ListSinglePicker
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.rememberMenuTriggerState
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.settings.components.prefs.PrefsChoiceValue

/**
 * Preference that shows a list of entries in a Dialog where a single entry can be selected at one time.
 *
 * @param setting The setting this Pref shows and sets
 * @param title Main text which describes the Pref. Shown above the summary and in the Dialog.
 * @param modifier Modifier applied to the Text aspect of this Pref
 * @param summary Used to give some more information about what this Pref is for
 * @param onValueChange Will be called with the selected key when an item is selected
 * @param useSelectedAsSummary If true, uses the current selected item as the summary
 * @param dialogBackgroundColor Background color of the Dialog
 * @param contentColor Preferred content color passed to dialog's children
 * @param textColor Text colour of the [title], [summary] and [entries]
 * @param selectionColor Colour of the radiobutton of the selected item
 * @param buttonColor Colour of the cancel button
 * @param enabled If false, this Pref cannot be clicked and the Dialog cannot be shown.
 * @param entries Map of keys to values for entries that should be shown in the Dialog.
 */
@ExperimentalMaterialApi
@ExperimentalComposeUiApi
@Composable
fun ListPref(
    setting: Setting<String>,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    useSelectedAsSummary: Boolean = false,
    textColor: Color = ExtendedTheme.colors.text,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    entries: Map<String, String> = emptyMap(), // TODO: Change to List?
    icons: Map<String, @Composable () -> Unit> = emptyMap(),
) {
    val dialogState = rememberDialogState()
    val settings = rememberSettings()
    val selected by settingAsState(setting)

    fun edit(current: Pair<String, String>) {
        onValueChange?.invoke(current.first)
        settings[setting] = current.first
    }

    val chosen = entries[selected] ?: "Not Set"
    if (icons.isEmpty()) {
        // Chosen in a menu growing out of the value, the way a forum's sort is
        val menu = rememberMenuTriggerState()
        val choice = @Composable {
            TextPref(
                title = title,
                summary = summary.takeUnless { useSelectedAsSummary },
                leadingIcon = leadingIcon,
                modifier = modifier,
                textColor = textColor,
                enabled = enabled,
                trailingContent = { PrefsChoiceValue(text = chosen, modifier = Modifier.menuAnchor(menu)) },
            )
        }
        if (enabled) {
            Box(
                modifier = Modifier.clickMenu(
                    items = { choiceMenuItems(entries.toList(), selected) { value -> if (value != null) edit(value to entries.getValue(value)) } },
                    state = menu,
                ),
            ) {
                choice()
            }
        } else {
            choice()
        }
        return
    }
    TextPref(
        title = title,
        summary = summary.takeUnless { useSelectedAsSummary },
        leadingIcon = leadingIcon,
        modifier = modifier,
        textColor = textColor,
        enabled = enabled,
        onClick = { dialogState.show() },
        trailingContent = { PrefsChoiceValue(text = chosen) },
    )
    val itemTitle = remember(entries) { entries.map { it.value }.toImmutableList() }
    val itemValues = remember(entries) { entries.map { it.key }.toImmutableList() }

    Dialog(
        dialogState = dialogState,
        title = { Text(text = title) },
        buttons = {
            DialogNegativeButton(text = stringResource(id = R.string.button_cancel))
        }
    ) {
        ListSinglePicker(
            itemTitles = itemTitle,
            itemValues = itemValues,
            selectedPosition = entries.keys.indexOf(selected),
            onItemSelected = { _, title, value, _ ->
                edit(current = value to title)
                dismiss()
            },
            modifier = Modifier.padding(bottom = 16.dp),
            itemIcons = icons.toImmutableMap()
        )
    }
//    if (showDialog) {
//        AlertDialog(
//            onDismissRequest = { showDialog = false },
//            text = {
//                Column() {
//                    Text(modifier = Modifier.padding(vertical = 16.dp), text = title)
//                    LazyColumn {
//                        items(entryList) { current ->
//
//                            val isSelected = selected == current.first
//                            val onSelected = {
//                                edit(current)
//                            }
//                            Row(
//                                modifier = Modifier
//                                    .fillMaxWidth()
//                                    .selectable(
//                                        selected = isSelected,
//                                        onClick = { if (!isSelected) onSelected() }
//                                    ),
//                                verticalAlignment = CenterVertically,
//                            ) {
//                                RadioButton(
//                                    selected = isSelected,
//                                    onClick = { if (!isSelected) onSelected() },
//                                    colors = RadioButtonDefaults.colors(selectedColor = selectionColor)
//                                )
//                                Text(
//                                    text = current.second,
//                                    style = MaterialTheme.typography.body2,
//                                    color = textColor
//                                )
//                            }
//                        }
//                    }
//                }
//            },
//            confirmButton = {
//                TextButton(
//                    onClick = { showDialog = false },
//                ) {
//                    Text("Cancel", style = MaterialTheme.typography.body1, color = buttonColor)
//                }
//
//            },
//            backgroundColor = dialogBackgroundColor,
//            contentColor = contentColor,
//            properties = DialogProperties(
//                usePlatformDefaultWidth = true
//            ),
//        )
//    }
}
