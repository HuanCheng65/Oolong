package oolong.client.ui.settings.more

import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.data.legacy.LegacyImport
import oolong.client.data.settings.SettingKeys
import oolong.client.data.storage.AppStorage
import oolong.client.diagnostics.CrashReports
import oolong.client.navigation.CrashLogsKey
import oolong.client.navigation.DiagnosticsKey
import oolong.client.navigation.ErrorRecordsKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.MoreSettingsKey
import oolong.client.navigation.Navigator
import oolong.client.navigation.ProvideNavigator
import oolong.client.navigation.StorageKey
import oolong.client.ui.settings.components.Availability
import oolong.client.ui.settings.components.SettingsHeader
import oolong.client.ui.settings.components.SettingsPage
import oolong.client.ui.settings.components.SettingsScope
import oolong.client.ui.settings.components.SettingsScreen
import oolong.client.ui.settings.components.prefs.PrefsExternal
import oolong.client.ui.settings.components.prefs.widgets.TextPref
import oolong.client.ui.settings.components.res
import oolong.client.ui.settings.components.stored

/** What the other settings page knows only while it shows. */
class MoreState(
    /** How much the app keeps on the device, null until measured. */
    val storageTotal: Long?,
    val legacySource: LegacyImport.Source,
)

/** Everything else: links, storage and troubleshooting. */
object MoreSettings : SettingsPage<MoreState>(R.string.title_settings_more, MoreSettingsKey) {
    /** How the storage row's size is measured; screenshot tests give a fixed one, as the real one moves with every build. */
    @VisibleForTesting
    internal var measureStorage: suspend (Context) -> Long = AppStorage::total

    @Composable
    override fun rememberState(): MoreState {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val legacySource = remember { LegacyImport.source(context) }
        // Measured again on coming back, as the storage page may have cleared some
        var storageTotal by remember { mutableStateOf<Long?>(null) }
        LifecycleResumeEffect(Unit) {
            val job = coroutineScope.launch { storageTotal = measureStorage(context) }
            onPauseOrDispose { job.cancel() }
        }
        return MoreState(storageTotal, legacySource)
    }

    override fun SettingsScope<MoreState>.content() {
        group(R.string.settings_group_links) {
            choice(
                R.string.title_link_opening,
                options = LinkOpening.entries.map { it to it.title },
                value = { LinkOpening.of(useWebView = stored(SettingKeys.UseWebView), useCustomTabs = stored(SettingKeys.UseCustomTabs)) },
                onSelect = { opening ->
                    settings[SettingKeys.UseWebView] = opening == LinkOpening.WebView
                    settings[SettingKeys.UseCustomTabs] = opening == LinkOpening.CustomTabs
                },
            )
            switch(SettingKeys.ClipboardLinks, R.string.settings_clipboard_links)
        }
        // Links to an unverified domain only open in the app once the user allows them
        group(SettingsHeader.Gap(8.dp), available = Availability.sdk(Build.VERSION_CODES.S)) {
            custom(R.string.title_tieba_links) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) TiebaLinksPref() }
            footer(res(R.string.tip_tieba_links))
        }
        group(R.string.settings_group_storage) {
            custom(R.string.title_import_legacy, shown = { legacySource != LegacyImport.Source.None }) { LegacyImportPref(legacySource) }
            link(R.string.title_storage, to = StorageKey, aliases = R.string.search_aliases_storage, value = {
                val context = LocalContext.current
                storageTotal?.let { Formatter.formatShortFileSize(context, it) }.orEmpty()
            })
        }
        group(R.string.settings_group_troubleshooting) {
            switch(SettingKeys.UsageStats, R.string.title_usage_stats)
            choice(
                R.string.title_crash_report_mode,
                options = listOf(
                    CrashReports.Mode.Ask to R.string.crash_report_mode_ask,
                    CrashReports.Mode.Always to R.string.crash_report_mode_always,
                    CrashReports.Mode.Never to R.string.crash_report_mode_never,
                ),
                value = { CrashReports.Mode.entries.firstOrNull { it.value == stored(SettingKeys.CrashReportMode) } ?: CrashReports.Mode.Ask },
                onSelect = { mode -> settings[SettingKeys.CrashReportMode] = mode.value },
            )
            link(R.string.title_crash_logs, to = CrashLogsKey)
            link(R.string.title_error_records, to = ErrorRecordsKey)
            link(R.string.title_diagnostics, to = DiagnosticsKey)
        }
    }
}

@Composable
fun MoreSettingsPage(navigator: Navigator = LocalNavigator.current) {
    ProvideNavigator(navigator) { SettingsScreen(MoreSettings) }
}

/**
 * How links outside Tieba open: in the app's own browser, in Custom Tabs, or in the system's
 * browser. Tieba's own links always open in the app.
 */
private enum class LinkOpening(@StringRes val title: Int) {
    WebView(R.string.link_opening_webview),
    CustomTabs(R.string.link_opening_custom_tabs),
    Browser(R.string.link_opening_browser),
    ;

    companion object {
        fun of(useWebView: Boolean, useCustomTabs: Boolean) = when {
            useWebView -> WebView
            useCustomTabs -> CustomTabs
            else -> Browser
        }
    }
}

/**
 * Whether Tieba's links open in the app or in a browser. Links to an unverified domain only open
 * in the app once the user allows them in the system's settings, which the row opens; the answer
 * is read again on returning.
 */
@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun TiebaLinksPref() {
    val context = LocalContext.current
    var opensInApp by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        val state = context.getSystemService(DomainVerificationManager::class.java)
            ?.getDomainVerificationUserState(context.packageName)
        opensInApp = state != null && state.isLinkHandlingAllowed &&
            state.hostToStateMap[TiebaHost] in setOf(
                DomainVerificationUserState.DOMAIN_STATE_SELECTED,
                DomainVerificationUserState.DOMAIN_STATE_VERIFIED,
            )
        onPauseOrDispose {}
    }
    TextPref(
        title = stringResource(id = R.string.title_tieba_links),
        onClick = {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                        "package:${context.packageName}".toUri()
                    )
                )
            }
        },
        trailingContent = {
            PrefsExternal(
                text = stringResource(
                    id = if (opensInApp) R.string.value_tieba_links_app else R.string.value_tieba_links_browser
                )
            )
        },
    )
}

private const val TiebaHost = "tieba.baidu.com"
