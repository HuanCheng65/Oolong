@file:OptIn(ExperimentalMaterialApi::class)

package oolong.client.ui.settings.storage

import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.data.storage.AppStorage
import oolong.client.data.storage.StorageUse
import oolong.client.media.automask.Ability
import oolong.client.media.automask.AutoMaskDownload
import oolong.client.media.automask.AutoMaskFiles
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.OfflineThreadsKey
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.ConfirmDialog
import oolong.client.ui.designsystem.DialogState
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarTitle
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.editor.rememberAutoMaskFiles
import oolong.client.ui.settings.components.prefs.GroupHeader
import oolong.client.ui.settings.components.prefs.PrefsChevron
import oolong.client.ui.settings.components.prefs.PrefsScreen
import oolong.client.ui.settings.components.prefs.widgets.TextPref
import oolong.core.designsystem.theme.Motion

/** What may be cleared or deleted, waiting on the user's word. */
private sealed interface Asked {
    data object ClearCache : Asked

    data class Download(val ability: Ability) : Asked

    data class Delete(val ability: Ability) : Asked

    data object ClearAll : Asked
}

/**
 * How much of the phone the app takes, as its system app details count it, broken down: cache to
 * clear all at once, the smart extensions to download or delete, and what it keeps, all of which can go at
 * once, as the system's own button would have it.
 */
@Composable
fun StoragePage(
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    val files = rememberAutoMaskFiles()
    val text by files.state(Ability.Text).collectAsState()
    val region by files.state(Ability.Region).collectAsState()
    var cleared by remember { mutableIntStateOf(0) }
    // Measured again as anything is cleared, downloaded or deleted
    val use by produceState<StorageUse?>(null, cleared, text is AutoMaskDownload.Ready, region is AutoMaskDownload.Ready) {
        value = AppStorage.measure(context, files.runtimeBytes + files.bytesOf(Ability.Text) + files.bytesOf(Ability.Region))
    }
    var asked by remember { mutableStateOf<Asked?>(null) }
    val dialog = rememberDialogState()
    AskedDialog(asked, dialog, files) { cleared++ }
    fun ask(what: Asked) {
        asked = what
        dialog.show()
    }
    GlassScaffold(
        largeTitle = true,
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_storage)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { padding ->
        // Measuring takes a moment on opening; the page then fades in rather than appearing at once
        val shown = remember { Animatable(0f) }
        LaunchedEffect(use != null) { if (use != null) shown.animateTo(1f, Motion.fadeIn()) }
        val now = use ?: return@GlassScaffold
        PrefsScreen(
            dividerThickness = 0.dp,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown.value },
            contentPadding = padding,
        ) {
            prefsGroup(header = { Summary(now) }) {}
            prefsGroup(
                header = {
                    Header(
                        title = stringResource(R.string.storage_group_cache),
                        action = stringResource(R.string.storage_clear_cache).takeIf { now.cache > 0 },
                        onAction = { ask(Asked.ClearCache) },
                    )
                },
            ) {
                prefsItem {
                    SizePref(R.string.storage_images, now.images)
                }
                prefsItem {
                    SizePref(R.string.storage_videos, now.videos)
                }
                prefsItem {
                    SizePref(R.string.storage_web, now.web)
                }
                prefsItem {
                    SizePref(R.string.storage_temporary, now.temporary)
                }
            }
            if (files.supported) {
                prefsGroup(
                    header = {
                        Header(
                            title = stringResource(R.string.storage_group_extensions) + " · " + size(now.extensions),
                            note = stringResource(R.string.storage_extensions_note),
                        )
                    },
                ) {
                    prefsItem {
                        val bytes = files.runtimeBytes
                        TextPref(
                            title = stringResource(R.string.storage_runtime),
                            darkenOnDisable = false,
                            trailingContent = { Value(if (bytes > 0) size(bytes) else stringResource(R.string.storage_not_downloaded)) },
                        )
                    }
                    prefsItem {
                        AbilityPref(files, Ability.Text, text, R.string.storage_text, R.string.storage_text_used, ::ask)
                    }
                    prefsItem {
                        AbilityPref(files, Ability.Region, region, R.string.storage_region, R.string.storage_region_used, ::ask)
                    }
                }
            }
            prefsGroup(header = { GroupHeader(stringResource(R.string.storage_group_data)) }) {
                prefsItem {
                    TextPref(title = stringResource(R.string.storage_app), darkenOnDisable = false, trailingContent = { Value(size(now.app)) })
                }
                prefsItem {
                    TextPref(
                        title = stringResource(R.string.title_offline_threads),
                        darkenOnDisable = false,
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Value(size(now.offline))
                                PrefsChevron()
                            }
                        },
                        onClick = { navigator.navigate(OfflineThreadsKey) },
                    )
                }
                prefsItem {
                    TextPref(title = stringResource(R.string.storage_records), darkenOnDisable = false, trailingContent = { Value(size(now.records)) })
                }
            }
            prefsGroup(header = { Spacer(modifier = Modifier.height(16.dp)) }) {
                prefsItem {
                    TextPref(
                        title = stringResource(R.string.storage_clear_all),
                        textColor = MaterialTheme.colors.error,
                        trailingContent = null,
                        onClick = { ask(Asked.ClearAll) },
                    )
                }
            }
        }
    }
}

/** The confirmation for what was [asked], doing it once confirmed; [done] once it is. */
@Composable
private fun AskedDialog(asked: Asked?, dialog: DialogState, files: AutoMaskFiles, done: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val what = asked ?: return
    val name = when (what) {
        is Asked.Download -> stringResource(nameOf(what.ability))
        is Asked.Delete -> stringResource(nameOf(what.ability))
        else -> ""
    }
    ConfirmDialog(
        dialogState = dialog,
        onConfirm = {
            when (what) {
                Asked.ClearCache -> scope.launch {
                    AppStorage.clearCache(context)
                    done()
                }
                is Asked.Download -> files.start(what.ability)
                is Asked.Delete -> scope.launch {
                    files.delete(what.ability)
                    done()
                }
                Asked.ClearAll -> AppStorage.clearAll(context)
            }
        },
        confirmText = stringResource(
            when (what) {
                Asked.ClearCache, Asked.ClearAll -> R.string.button_clear
                is Asked.Download -> R.string.storage_download
                is Asked.Delete -> R.string.storage_delete
            },
        ),
        title = {
            Text(
                when (what) {
                    Asked.ClearCache -> stringResource(R.string.storage_clear_cache_confirm)
                    is Asked.Download -> stringResource(R.string.storage_download_confirm, name)
                    is Asked.Delete -> stringResource(R.string.storage_delete_confirm, name)
                    Asked.ClearAll -> stringResource(R.string.storage_clear_all_confirm)
                },
            )
        },
    ) {
        Text(
            when (what) {
                Asked.ClearCache -> stringResource(R.string.storage_clear_cache_note)
                is Asked.Download -> {
                    val download by files.state(what.ability).collectAsState()
                    stringResource(R.string.storage_download_note, size(missingBytes(download)))
                }
                is Asked.Delete -> stringResource(if (what.ability == Ability.Text) R.string.storage_delete_text_note else R.string.storage_delete_region_note)
                Asked.ClearAll -> stringResource(R.string.storage_clear_all_note)
            },
        )
    }
}

private fun missingBytes(state: AutoMaskDownload) = when (state) {
    is AutoMaskDownload.Missing -> state.bytes
    is AutoMaskDownload.Failed -> state.bytes
    else -> 0L
}

private fun nameOf(ability: Ability) = when (ability) {
    Ability.Text -> R.string.storage_text
    Ability.Region -> R.string.storage_region
}

/** One of the smart extensions: its size once here, and deleting it; or downloading it, and how far along. */
@Composable
private fun AbilityPref(files: AutoMaskFiles, ability: Ability, state: AutoMaskDownload, title: Int, used: Int, ask: (Asked) -> Unit) {
    val value = when (state) {
        AutoMaskDownload.Ready -> size(files.bytesOf(ability))
        is AutoMaskDownload.Running -> stringResource(R.string.storage_downloading, (state.progress * 100).toInt())
        is AutoMaskDownload.Failed -> stringResource(R.string.storage_download_failed)
        is AutoMaskDownload.Missing -> stringResource(R.string.storage_not_downloaded)
    }
    TextPref(
        title = stringResource(title),
        summary = stringResource(used),
        darkenOnDisable = false,
        trailingContent = {
            when (state) {
                AutoMaskDownload.Ready -> Action(value, stringResource(R.string.storage_delete)) { ask(Asked.Delete(ability)) }
                is AutoMaskDownload.Missing, is AutoMaskDownload.Failed -> Action(value, stringResource(R.string.storage_download)) { ask(Asked.Download(ability)) }
                is AutoMaskDownload.Running -> Value(value)
            }
        },
    )
}

/** A kind of cache and its size. */
@Composable
private fun SizePref(title: Int, bytes: Long) {
    TextPref(title = stringResource(title), darkenOnDisable = false, trailingContent = { Value(size(bytes)) })
}

/** A row's value with what can be done to it beside. */
@Composable
private fun Action(value: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Value(value)
        Spacer(modifier = Modifier.width(12.dp))
        val accent = ExtendedTheme.colors.accent
        Text(
            text = action,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Value(text: String) {
    // A size measured again, as after clearing, refocuses in place
    Refocus(targetState = text, contentAlignment = Alignment.CenterEnd) { Text(it, color = ExtendedTheme.colors.textSecondary, maxLines = 1) }
}

/** A group's title, as other groups', with a note under it or an action at its end. */
@Composable
private fun Header(title: String, note: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, top = 16.dp, bottom = if (action != null) 0.dp else 8.dp)) {
        // The action's text on the title's line
        Column(modifier = Modifier.weight(1f).alignByBaseline()) {
            Text(title, color = ExtendedTheme.colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            if (note != null) Text(note, color = ExtendedTheme.colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (action != null) {
            Text(
                action,
                color = ExtendedTheme.colors.accent,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .alignByBaseline()
                    .clip(CircleShape)
                    .clickable(onClick = onAction)
                    // Its text ends in line with the sizes below; the rest is room to press
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** The total, and a bar of what it is made of. */
@Composable
private fun Summary(use: StorageUse) {
    val accent = ExtendedTheme.colors.accent
    val all = listOf(
        R.string.storage_group_cache to (use.cache to accent),
        R.string.storage_group_extensions to (use.extensions to accent.copy(alpha = 0.55f)),
        R.string.storage_app to (use.app to accent.copy(alpha = 0.28f)),
        R.string.title_offline_threads to (use.offline to accent.copy(alpha = 0.4f)),
        R.string.storage_records to (use.records to ExtendedTheme.colors.textSecondary.copy(alpha = 0.3f)),
    )
    val parts = all.filter { it.second.first > 0 }
    val total = all.sumOf { it.second.first }.coerceAtLeast(1)
    // Each part's share of the bar moves to its new size, as after clearing, rather than jumping
    val shares = all.map { (name, part) ->
        animateFloatAsState(part.first.toFloat() / total, Motion.settle(Motion.ProgressThreshold), label = "storageShare$name")
    }
    val colors = all.map { it.second.second }
    // Lined up with the large title above it
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Refocus(targetState = size(use.total), contentAlignment = Alignment.CenterStart) {
            Text(it, color = ExtendedTheme.colors.text, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
            // Parts side by side, 2dp apart; one shrinking to nothing takes its gap with it
            val gap = 2.dp.toPx()
            val shown = shares.map { it.value.coerceAtLeast(0f) }
            val gaps = gap * (shown.count { it * size.width > gap } - 1).coerceAtLeast(0)
            val room = size.width - gaps
            var x = 0f
            shown.forEachIndexed { index, share ->
                val width = room * share
                if (width <= 0f) return@forEachIndexed
                drawRect(colors[index], topLeft = Offset(x, 0f), size = Size(width, size.height))
                x += width + if (width > gap) gap else 0f
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            parts.forEach { (name, part) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(part.second))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(name), color = ExtendedTheme.colors.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun size(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)
