package oolong.client.ui.page.settings.crash

import android.content.Context
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oolong.client.BuildConfig
import oolong.client.R
import oolong.client.components.UsageStats
import oolong.client.dataStore
import oolong.client.repository.RecentResponses
import oolong.client.repository.UpdateChannel
import oolong.client.repository.UpdateRepository
import oolong.client.toastShort
import oolong.client.ui.common.prefs.PrefsScreen
import oolong.client.ui.common.prefs.widgets.TextPref
import oolong.client.ui.common.theme.compose.Corners
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Spacing
import oolong.client.ui.common.theme.compose.TextStyles
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.SecondaryButton
import oolong.client.utils.TiebaUtil
import java.text.SimpleDateFormat
import java.util.Date

/**
 * What this device is, as usage statistics and crash logs describe it, what each of them sends, and
 * the last heartbeat as it went out. The device part can be copied for reporting a problem, and the
 * lists' last pages as the server sent them exported for looking into what a list showed.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DiagnosticsPage(
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val channel by produceState<String?>(initialValue = null) {
        value = context.dataStore.data.first()[UpdateRepository.ChannelKey] ?: UpdateChannel.default.key
    }
    val device = remember(channel) { channel?.let { UsageStats.device(context, it) } }
    val last by produceState<Pair<Long, String>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { UsageStats.lastSent(context) }
    }
    val scope = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { exportResponses(context, uri) }
    }
    GlassScaffold(
        largeTitle = true,
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_diagnostics)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { padding ->
        val info = device ?: return@GlassScaffold
        val channelName = stringResource(if (info.channel == UpdateChannel.Stable.key) R.string.update_channel_stable else R.string.update_channel_beta)
        val rom = info.rom.ifEmpty { stringResource(id = R.string.diagnostics_rom_other) }
        val form = stringResource(
            when (info.form) {
                "foldable" -> R.string.diagnostics_form_foldable
                "tablet" -> R.string.diagnostics_form_tablet
                else -> R.string.diagnostics_form_phone
            },
        )
        val rows = listOf(
            stringResource(id = R.string.diagnostics_app_version) to "${info.version} (${BuildConfig.VERSION_CODE}) · $channelName",
            stringResource(id = R.string.diagnostics_system) to "Android ${Build.VERSION.RELEASE} (API ${info.sdk})",
            stringResource(id = R.string.diagnostics_model) to "${info.brand} ${info.model}",
            stringResource(id = R.string.diagnostics_rom) to "$rom\n${info.romVersion}",
            stringResource(id = R.string.diagnostics_form) to form,
            stringResource(id = R.string.diagnostics_locale) to info.locale,
        )
        PrefsScreen(
            dataStore = context.dataStore,
            dividerThickness = 0.dp,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            prefsGroup(resources.getString(R.string.diagnostics_device)) {
                prefsItem {
                    Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                        rows.forEach { (label, value) -> InfoRow(label, value) }
                        Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs), contentAlignment = Alignment.CenterEnd) {
                            SecondaryButton(onClick = { TiebaUtil.copyText(context, rows.joinToString("\n") { (label, value) -> "$label: ${value.replace("\n", " · ")}" }) }) {
                                Text(text = stringResource(id = R.string.title_copy), style = TextStyles.label)
                            }
                        }
                    }
                }
            }
            prefsGroup(resources.getString(R.string.diagnostics_usage_stats)) {
                prefsItem {
                    Column(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Fields(stringResource(id = R.string.diagnostics_daily), stringResource(id = R.string.diagnostics_daily_fields))
                        Fields(stringResource(id = R.string.diagnostics_monthly), stringResource(id = R.string.diagnostics_monthly_fields))
                        Fields(stringResource(id = R.string.diagnostics_never), stringResource(id = R.string.diagnostics_never_fields))
                        val sent = last
                        Text(
                            text = if (sent == null) stringResource(id = R.string.diagnostics_not_sent) else stringResource(id = R.string.diagnostics_last_sent, formatTime(sent.first)),
                            style = TextStyles.caption,
                            color = ExtendedTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                        if (sent != null) {
                            SelectionContainer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(Corners.md)
                                    .background(ExtendedTheme.colors.inset)
                                    .padding(Spacing.sm),
                            ) {
                                Text(
                                    text = sent.second,
                                    style = TextStyles.footnote.copy(fontFamily = FontFamily.Monospace),
                                    color = ExtendedTheme.colors.textSecondary,
                                    softWrap = false,
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                )
                            }
                        }
                    }
                }
            }
            prefsGroup(resources.getString(R.string.diagnostics_responses)) {
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.diagnostics_export_responses),
                        onClick = { exporter.launch("oolong-lists-${System.currentTimeMillis() / 1000}.zip") },
                    )
                }
            }
            prefsGroup(resources.getString(R.string.diagnostics_crash_logs)) {
                prefsItem {
                    Text(
                        text = stringResource(id = R.string.diagnostics_crash_fields),
                        style = TextStyles.caption,
                        color = ExtendedTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    )
                }
            }
        }
    }
}

/** Writes the lists' last pages as the server sent them to [uri], and says how that went. */
private suspend fun exportResponses(context: Context, uri: Uri) {
    val result = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { RecentResponses.export(context, it) }
        }
    }
    result
        .onSuccess { context.toastShort(if (it) R.string.diagnostics_responses_exported else R.string.diagnostics_responses_none) }
        .onFailure { context.toastShort(R.string.diagnostics_responses_export_failed, it.message.orEmpty()) }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(text = label, style = TextStyles.caption, color = ExtendedTheme.colors.textSecondary)
        Text(
            text = value,
            style = TextStyles.caption,
            color = ExtendedTheme.colors.text,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = Spacing.sm),
        )
    }
}

@Composable
private fun Fields(title: String, fields: String) {
    Column {
        Text(text = title, style = TextStyles.label, color = ExtendedTheme.colors.text)
        Text(text = fields, style = TextStyles.caption, color = ExtendedTheme.colors.textSecondary)
    }
}

@Composable
private fun formatTime(time: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(time, locale) { SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMdHHmm"), locale).format(Date(time)) }
}
