package oolong.client.ui.offline

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.flowOf
import oolong.client.R
import oolong.client.data.offline.OfflineThreadInfo
import oolong.client.data.offline.OfflineThreads
import oolong.client.database.OfflineThreadEntity
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.theme.Symbols

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface OfflineThreadsEntryPoint {
    fun offlineThreads(): OfflineThreads
}

internal fun Context.offlineThreads(): OfflineThreads =
    EntryPointAccessors.fromApplication(applicationContext, OfflineThreadsEntryPoint::class.java).offlineThreads()

/** What is saved of thread [threadId] to read offline, if anything, kept up to date. */
@Composable
fun rememberOfflineEntry(threadId: Long): State<OfflineThreadEntity?> {
    val context = LocalContext.current
    // Previews and screen tests run without the app's dependencies: nothing is saved there
    val flow = remember(threadId) { runCatching { context.offlineThreads().entry(threadId) }.getOrElse { flowOf(null) } }
    return flow.collectAsState(initial = null)
}

/** The name of what saving [entry] to read offline offers now. */
fun offlineLabel(context: Context, entry: OfflineThreadEntity?): String = context.getString(
    when (entry?.state) {
        null -> R.string.offline_save
        OfflineThreadEntity.Saving -> R.string.offline_saving
        else -> R.string.offline_saved
    }
)

/**
 * What saving [info]'s thread offers: which floors to save, the author's first when
 * [authorFirst]; once saved, updating or deleting it. [onDone] runs after any of them.
 */
fun offlineMenuItems(
    context: Context,
    info: OfflineThreadInfo,
    entry: OfflineThreadEntity?,
    authorFirst: Boolean,
    onDone: () -> Unit = {},
): List<ContextMenuItem> {
    val threads = context.offlineThreads()
    fun run(block: suspend OfflineThreads.() -> Unit) {
        onDone()
        threads.launch(block)
    }
    if (entry == null) {
        val all = ContextMenuItem(Symbols.Download, context.getString(R.string.offline_save_all)) { run { save(info, onlyAuthor = false) } }
        val author = ContextMenuItem(Symbols.Download, context.getString(R.string.offline_save_author)) { run { save(info, onlyAuthor = true) } }
        return if (authorFirst) listOf(author, all) else listOf(all, author)
    }
    return listOfNotNull(
        ContextMenuItem(Symbols.Refresh, context.getString(R.string.offline_update)) { run { update(info.threadId) } }
            .takeIf { entry.state != OfflineThreadEntity.Saving },
        ContextMenuItem(Symbols.Delete, context.getString(R.string.offline_delete)) { run { delete(listOf(info.threadId)) } },
    )
}
