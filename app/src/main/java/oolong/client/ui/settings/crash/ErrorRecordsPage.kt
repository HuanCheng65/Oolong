package oolong.client.ui.settings.crash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oolong.client.R
import oolong.client.diagnostics.ErrorRecords
import oolong.client.navigation.ErrorRecordKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.ConfirmDialog
import oolong.client.ui.designsystem.ContentSwap
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarTitle
import oolong.client.ui.designsystem.IconButton
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.settings.components.prefs.PrefsScreen
import oolong.client.ui.settings.components.prefs.widgets.TextPref
import oolong.client.util.TiebaUtil
import oolong.client.util.toastShort
import oolong.core.designsystem.theme.Spacing
import oolong.core.designsystem.theme.TextStyles

/** The errors kept on the device that the app couldn't explain, the latest first; each opens to its record. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ErrorRecordsPage(
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    var cleared by remember { mutableIntStateOf(0) }
    val entries by produceState<List<ErrorRecords.Entry>?>(initialValue = null, cleared) {
        value = withContext(Dispatchers.IO) { ErrorRecords.list(context) }
    }
    val clearDialog = rememberDialogState()
    ConfirmDialog(
        dialogState = clearDialog,
        onConfirm = {
            ErrorRecords.clear(context)
            cleared++
        },
        confirmText = stringResource(id = R.string.button_clear),
        title = { Text(text = stringResource(id = R.string.title_dialog_clear_error_records)) },
    )
    GlassScaffold(
        largeTitle = true,
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_error_records)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { padding ->
        ContentSwap(targetState = entries, contentKey = { it?.isEmpty() }, modifier = Modifier.fillMaxSize()) { list ->
            when {
                list == null -> Unit
                list.isEmpty() -> Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(id = R.string.error_records_empty),
                        style = TextStyles.label,
                        color = ExtendedTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> PrefsScreen(
                    dividerThickness = 0.dp,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                ) {
                    prefsGroup(header = { Spacer(modifier = Modifier.height(Spacing.xs)) }) {
                        list.forEach { entry ->
                            prefsItem {
                                val time = formatRecordTime(entry.time)
                                TextPref(
                                    title = entry.title,
                                    summary = if (entry.count > 1) {
                                        pluralStringResource(id = R.plurals.error_record_times, count = entry.count, time, entry.count)
                                    } else {
                                        time
                                    },
                                    onClick = { navigator.navigate(ErrorRecordKey(entry.id)) },
                                )
                            }
                        }
                    }
                    prefsGroup(header = { Spacer(modifier = Modifier.height(Spacing.xs)) }) {
                        prefsItem {
                            TextPref(
                                title = stringResource(id = R.string.crash_logs_clear),
                                textColor = MaterialTheme.colors.error,
                                trailingContent = null,
                                onClick = { clearDialog.show() },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One error's full record, to read, copy, share or send. */
@Composable
fun ErrorRecordPage(
    key: ErrorRecordKey,
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    val text by produceState<String?>(initialValue = null, key.id) {
        value = withContext(Dispatchers.IO) { ErrorRecords.read(context, key.id) }
    }
    val scope = rememberCoroutineScope()
    // Offered for a release build's record not yet sent; gone once it is
    var sendable by remember(key.id) { mutableStateOf(false) }
    LaunchedEffect(key.id) {
        sendable = withContext(Dispatchers.IO) { ErrorRecords.canSend(context, key.id) && !ErrorRecords.isSent(context, key.id) }
    }
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_error_records)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
                actions = {
                    val record = text
                    if (record != null) {
                        if (sendable) {
                            IconButton(
                                onClick = {
                                    sendable = false
                                    scope.launch {
                                        val sent = withContext(Dispatchers.IO) { ErrorRecords.send(context, key.id) }
                                        if (!sent) sendable = true
                                        context.toastShort(if (sent) R.string.toast_crash_log_sent else R.string.toast_error_send_failed)
                                    }
                                },
                            ) {
                                Icon(Symbols.Send, contentDescription = stringResource(id = R.string.title_send_crash_log))
                            }
                        }
                        IconButton(onClick = { TiebaUtil.copyText(context, record) }) {
                            Icon(Symbols.ContentCopy, contentDescription = stringResource(id = R.string.title_copy))
                        }
                        IconButton(onClick = { share(context, record) }) {
                            Icon(Symbols.Share, contentDescription = stringResource(id = R.string.title_share))
                        }
                    }
                },
            )
        },
    ) { padding ->
        RecordText(record = text ?: return@GlassScaffold, padding = padding)
    }
}
