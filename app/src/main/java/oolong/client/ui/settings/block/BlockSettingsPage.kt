package oolong.client.ui.settings.block

import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import oolong.client.R
import oolong.client.data.block.DefaultLowLevel
import oolong.client.data.block.HideBlockedContentKey
import oolong.client.data.block.LowLevelKey
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.dataStore
import oolong.client.data.settings.putBoolean
import oolong.client.data.settings.putString
import oolong.client.navigation.BlockListKey
import oolong.client.navigation.BlockSettingsKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.ProvideNavigator
import oolong.client.ui.designsystem.DialogState
import oolong.client.ui.designsystem.PromptDialog
import oolong.client.ui.designsystem.SegmentedControl
import oolong.client.ui.designsystem.Stepper
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.settings.components.SettingsPage
import oolong.client.ui.settings.components.SettingsScope
import oolong.client.ui.settings.components.SettingsScreen
import oolong.client.ui.settings.components.prefs.widgets.TextPref
import oolong.client.ui.settings.components.stored

/** What the hidden-content page works with while it shows: the rules, and the ways to add and move them. */
class BlockState(
    val viewModel: BlockSettingsViewModel,
    /** How many rules are on, null until counted. */
    val activeRules: Int?,
    val spoilerDialog: DialogState,
    val exporter: ManagedActivityResultLauncher<String, android.net.Uri?>,
    val importer: ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
)

/** Hiding content: common cases, how hidden content shows, and the rules themselves. */
@OptIn(ExperimentalMaterialApi::class)
object BlockSettings : SettingsPage<BlockState>(R.string.title_block_settings, BlockSettingsKey) {
    @Composable
    override fun rememberState(): BlockState {
        val viewModel: BlockSettingsViewModel = hiltViewModel()
        val activeRules by viewModel.activeRules.collectAsStateWithLifecycle()
        return BlockState(
            viewModel = viewModel,
            activeRules = activeRules,
            spoilerDialog = rememberDialogState(),
            exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::export) },
            importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::import) },
        )
    }

    @Composable
    override fun Effects(state: BlockState) {
        PromptDialog(
            dialogState = state.spoilerDialog,
            title = { Text(text = stringResource(id = R.string.settings_block_spoiler)) },
            confirmText = stringResource(id = R.string.button_block_7_days),
            onConfirm = state.viewModel::blockSpoiler,
        ) {
            Text(text = stringResource(id = R.string.settings_block_spoiler_hint))
        }
    }

    override fun SettingsScope<BlockState>.content() {
        group(R.string.settings_block_common) {
            switch(SettingKeys.BlockVideo, R.string.settings_block_video)
            switch(SettingKeys.BlockLowLevel, R.string.settings_block_low_level)
            // Found by the switch above, which it belongs to
            custom(
                R.string.settings_block_low_level_below,
                shown = { stored(SettingKeys.BlockLowLevel) },
                searchable = false,
            ) { modifier -> LowLevelPref(modifier) }
            action(
                R.string.settings_block_spoiler,
                summary = { stringResource(id = R.string.settings_block_spoiler_summary) },
                more = true,
                onClick = { state.spoilerDialog.show() },
            )
        }
        group(R.string.settings_blocked_content) {
            // Its options say what it is; search finds it by the group's title
            custom(null) { modifier ->
                val context = LocalContext.current
                SegmentedControl(
                    options = persistentListOf(stringResource(id = R.string.settings_blocked_fold), stringResource(id = R.string.settings_blocked_hide)),
                    selected = if (stored(SettingKeys.HideBlockedContent)) 1 else 0,
                    onSelect = { context.dataStore.putBoolean(HideBlockedContentKey, it == 1) },
                    modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            footer { stringResource(id = R.string.settings_blocked_content_note) }
        }
        group(R.string.settings_block_rules_group) {
            link(R.string.title_block_rules, to = BlockListKey, value = {
                activeRules?.let { pluralStringResource(id = R.plurals.rules_count, count = it, it) }.orEmpty()
            })
            action(R.string.settings_block_export, onClick = { state.exporter.launch(BackupFileName) })
            action(
                R.string.settings_block_import,
                onClick = { state.importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            )
        }
    }
}

@Composable
fun BlockSettingsPage(navigator: Navigator = LocalNavigator.current) {
    ProvideNavigator(navigator) { SettingsScreen(BlockSettings) }
}

/** Below which level users are hidden, in steps. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun LowLevelPref(modifier: Modifier) {
    val context = LocalContext.current
    val level = stored(SettingKeys.BlockLowLevelBelow).toIntOrNull() ?: DefaultLowLevel
    TextPref(
        title = stringResource(id = R.string.settings_block_low_level_below),
        modifier = modifier,
        enabled = true,
        trailingContent = {
            Stepper(
                value = level,
                range = LevelRange,
                text = stringResource(id = R.string.rule_level, level),
                down = stringResource(id = R.string.rule_level_down),
                up = stringResource(id = R.string.rule_level_up),
                onChange = { context.dataStore.putString(LowLevelKey, it.toString()) },
            )
        },
    )
}

private const val BackupFileName = "oolong-block-rules.json"
