package oolong.client.ui.main.explore

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import oolong.client.R
import oolong.client.arch.Actions
import oolong.client.arch.MessageController
import oolong.client.data.thread.ThreadRepository
import oolong.client.model.DislikeOption
import oolong.client.model.ThreadSummary
import oolong.client.model.knownFirstFloor
import oolong.client.model.threadId
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.NewWindow
import oolong.client.navigation.transition.PageOrigin
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.ContextMenuPreview
import oolong.client.ui.designsystem.LocalContextMenus
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.main.explore.personalized.blockingForum
import oolong.client.ui.main.explore.personalized.dislikeMenuItems
import oolong.client.ui.main.explore.personalized.shareMenuItems
import oolong.client.ui.main.explore.personalized.shareMenuLabels
import oolong.client.ui.settings.block.LocalBlockShortcuts
import oolong.client.ui.settings.block.blockMenuItems
import oolong.client.ui.settings.block.blockMenuLabels
import oolong.client.ui.thread.FirstPostContent
import oolong.client.ui.thread.key
import oolong.client.ui.thread.navigateToThread
import oolong.client.ui.thread.query
import javax.inject.Inject

/** Storing a thread from its peek, and loading it meanwhile so that opening it shows it at once. */
@HiltViewModel
class ThreadPeekViewModel @Inject constructor(
    private val repository: ThreadRepository,
    private val messages: MessageController,
) : ViewModel() {
    private val actions = Actions(viewModelScope)

    fun prefetch(thread: ThreadSummary) = repository.prefetch(thread.key().query())

    fun store(thread: ThreadSummary) = actions.launch("store-${thread.id}") {
        // A recommended reply is stored at itself
        runCatching { repository.addStore(thread.threadId, thread.reply?.postId ?: thread.firstPostId) }
            .onSuccess { messages.show(R.string.message_add_favorite_success, 1) }
            .onFailure { messages.showError(R.string.message_add_favorite_failure, it) }
    }
}

/** Long-pressing a thread card: a look at its whole first floor, over what can be done with it. */
@Stable
class ThreadPeeker internal constructor(
    private val show: (ThreadSummary, Rect, Offset, PageOrigin, ImmutableList<DislikeOption>, (Long, ImmutableList<DislikeOption>) -> Unit) -> Unit,
) {
    /**
     * Peeks at [thread], whose card is at [from] in the window and was pressed [at], offering the
     * server's [options] for not wanting it besides storing, sharing and copying its link. Opened
     * from the peek, the thread's page grows out of it, and closes back into the card, [origin].
     */
    fun peek(
        thread: ThreadSummary,
        from: Rect,
        at: Offset,
        origin: PageOrigin,
        options: ImmutableList<DislikeOption> = persistentListOf(),
        onDislike: (clickTime: Long, reasons: ImmutableList<DislikeOption>) -> Unit = { _, _ -> },
    ) = show(thread, from, at, origin, options, onDislike)
}

/**
 * What can be done with a thread from a list, the same wherever it is asked for: under its peek
 * and behind its card's ⋮. Common actions first, as one row of small buttons: storing, sharing,
 * copying its link and, where windows sit side by side, opening it in another; then not wanting
 * it; then blocking, last.
 */
@Stable
class ThreadMenu internal constructor(
    private val build: (ThreadSummary, ImmutableList<DislikeOption>, (Long, ImmutableList<DislikeOption>) -> Unit) -> List<ContextMenuItem>,
) {
    fun items(
        thread: ThreadSummary,
        options: ImmutableList<DislikeOption> = persistentListOf(),
        onDislike: (clickTime: Long, reasons: ImmutableList<DislikeOption>) -> Unit = { _, _ -> },
    ): List<ContextMenuItem> = build(thread, options, onDislike)
}

/** The thread menu; null where there are no context menus. */
@Composable
fun rememberThreadMenu(): ThreadMenu? {
    // Checked first: where there are no menus, as in previews and tests, there is no view model either
    val menus = LocalContextMenus.current ?: return null
    val viewModel = hiltViewModel<ThreadPeekViewModel>()
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val collect = stringResource(id = R.string.title_collect)
    val shareLabels = shareMenuLabels()
    val newWindow = stringResource(R.string.menu_open_in_new_window)
    val newWindowShort = stringResource(R.string.menu_open_in_new_window_short)
    val blockLabels = blockMenuLabels()
    val shortcuts = LocalBlockShortcuts.current
    return remember(menus, context, navigator, viewModel, collect, shortcuts, shareLabels, blockLabels, newWindow, newWindowShort) {
        ThreadMenu { thread, options, onDislike ->
            buildList {
                add(ContextMenuItem(Symbols.Star, collect, compact = true) { viewModel.store(thread) })
                addAll(shareMenuItems(shareLabels, context, thread.threadId, thread.title).map { it.copy(compact = true) })
                // Beside this window, where windows can sit side by side
                if (NewWindow.available(context)) {
                    add(ContextMenuItem(Symbols.NewWindow, newWindow, compact = true, shortLabel = newWindowShort) { NewWindow.openThread(context, thread.threadId) })
                }
                addAll(dislikeMenuItems(context, navigator, options, onDislike))
                if (shortcuts != null) {
                    blockMenuItems(blockLabels, shortcuts, thread, options.blockingForum(onDislike)).forEachIndexed { i, item ->
                        add(if (i == 0) item.copy(startsGroup = true) else item)
                    }
                }
            }
        }
    }
}

/** Peeks at threads of this feed; null where there are no context menus. */
@Composable
fun rememberThreadPeeker(): ThreadPeeker? {
    val menus = LocalContextMenus.current ?: return null
    val menu = rememberThreadMenu() ?: return null
    val viewModel = hiltViewModel<ThreadPeekViewModel>()
    val navigator = LocalNavigator.current
    return remember(menus, navigator, viewModel, menu) {
        ThreadPeeker { thread, from, at, origin, options, onDislike ->
            viewModel.prefetch(thread)
            menus.show(
                items = menu.items(thread, options, onDislike),
                anchor = Rect(at, Size.Zero),
                preview = ContextMenuPreview(
                    from = from,
                    origin = origin,
                    onClick = {
                        origin.leave()
                        navigator.navigateToThread(thread)
                    },
                ) {
                    ThreadPeek(thread = thread)
                },
            )
        }
    }
}

/**
 * A thread's first floor in full, as the thread page shows it, from what the list knows of it:
 * lists carry the whole first floor, only their abstract is cut short. A recommended reply shows
 * itself under the thread's title.
 */
@Composable
fun ThreadPeek(thread: ThreadSummary, modifier: Modifier = Modifier) {
    val post = remember(thread) {
        val floor = thread.knownFirstFloor()
        thread.reply?.let { floor.copy(author = it.author, time = it.time) } ?: floor
    }
    FirstPostContent(
        post = post,
        title = post.title,
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
    )
}
