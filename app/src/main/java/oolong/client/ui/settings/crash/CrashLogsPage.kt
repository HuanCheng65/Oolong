package oolong.client.ui.settings.crash

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oolong.client.R
import oolong.client.diagnostics.CrashLogs
import oolong.client.diagnostics.CrashReports
import oolong.client.navigation.CrashLogKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.ConfirmDialog
import oolong.client.ui.designsystem.ContentSwap
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarTitle
import oolong.client.ui.designsystem.IconButton
import oolong.client.ui.designsystem.MyLazyColumn
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.settings.components.prefs.PrefsScreen
import oolong.client.ui.settings.components.prefs.widgets.TextPref
import oolong.client.util.TiebaUtil
import oolong.client.util.toastShort
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Spacing
import oolong.core.designsystem.theme.TextStyles
import java.text.SimpleDateFormat
import java.util.Date

/** The crashes kept on the device, newest first; each opens to its full record. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CrashLogsPage(
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    var cleared by remember { mutableIntStateOf(0) }
    val entries by produceState<List<CrashLogs.Entry>?>(initialValue = null, cleared) {
        value = withContext(Dispatchers.IO) { CrashLogs.list(context) }
    }
    val clearDialog = rememberDialogState()
    ConfirmDialog(
        dialogState = clearDialog,
        onConfirm = {
            CrashLogs.clear(context)
            cleared++
        },
        confirmText = stringResource(id = R.string.button_clear),
        title = { Text(text = stringResource(id = R.string.title_dialog_clear_crash_logs)) },
    )
    GlassScaffold(
        largeTitle = true,
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_crash_logs)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { padding ->
        // Cleared, the list gives way to the empty note as a whole
        ContentSwap(targetState = entries, contentKey = { it?.isEmpty() }, modifier = Modifier.fillMaxSize()) { list ->
            when {
                list == null -> Unit
                list.isEmpty() -> Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(id = R.string.crash_logs_empty),
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
                                TextPref(
                                    title = entry.label(),
                                    summary = formatRecordTime(entry.time),
                                    onClick = { navigator.navigate(CrashLogKey(entry.id)) },
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

/** One crash's full record, to read, copy or share. */
@Composable
fun CrashLogPage(
    key: CrashLogKey,
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    val text by produceState<String?>(initialValue = null, key.id) {
        value = withContext(Dispatchers.IO) { CrashLogs.read(context, key.id) }
    }
    val entry = remember(key.id) { CrashLogs.list(context).firstOrNull { it.id == key.id } }
    val scope = rememberCoroutineScope()
    // Offered for a release build's record not yet sent; gone once it is
    var sendable by remember(key.id) { mutableStateOf(false) }
    LaunchedEffect(entry) {
        sendable = entry != null && withContext(Dispatchers.IO) { CrashReports.canSend(context, entry) && !CrashReports.isSent(context, entry) }
    }
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = entry?.label() ?: stringResource(id = R.string.title_crash_logs)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
                actions = {
                    val record = text
                    if (record != null) {
                        if (sendable && entry != null) {
                            IconButton(
                                onClick = {
                                    sendable = false
                                    scope.launch {
                                        val sent = withContext(Dispatchers.IO) { CrashReports.send(context, entry) }
                                        context.toastShort(if (sent) R.string.toast_crash_log_sent else R.string.toast_crash_log_send_failed)
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

/** A kept record in full, selectable, in a card. */
@Composable
internal fun RecordText(record: String, padding: PaddingValues) {
    MyLazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        item {
            SelectionContainer(
                modifier = Modifier
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    .fillMaxWidth()
                    .background(ExtendedTheme.colors.card, Corners.md)
                    .padding(Spacing.md),
            ) {
                // Unwrapped, so each frame of a stack trace keeps to its line
                Text(
                    text = record,
                    style = TextStyles.footnote.copy(fontFamily = FontFamily.Monospace),
                    color = ExtendedTheme.colors.text,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/** What went down: the exception's name for a crash, or what kind of end it was. */
@Composable
private fun CrashLogs.Entry.label(): String = when (kind) {
    CrashLogs.Kind.Crash -> title.substringBefore(':').substringAfterLast('.').substringAfterLast('$').ifBlank { stringResource(id = R.string.crash_kind_crash) }
    CrashLogs.Kind.Freeze -> stringResource(id = R.string.crash_kind_freeze)
    CrashLogs.Kind.Native -> stringResource(id = R.string.crash_kind_native)
}

@Composable
internal fun formatRecordTime(time: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(time, locale) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "yMMMdHHmmss"), locale).format(Date(time))
    }
}

internal fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
