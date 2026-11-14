package oolong.client.ui.page.main.explore

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
import oolong.client.model.DislikeOption
import oolong.client.model.ThreadSummary
import oolong.client.model.knownFirstFloor
import oolong.client.model.threadId
import oolong.client.repository.ThreadRepository
import oolong.client.ui.common.navigation.PageOrigin
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.main.explore.personalized.blockingForum
import oolong.client.ui.page.main.explore.personalized.dislikeMenuItems
import oolong.client.ui.page.main.explore.personalized.shareMenuItems
import oolong.client.ui.page.main.explore.personalized.shareMenuLabels
import oolong.client.ui.page.settings.block.LocalBlockShortcuts
import oolong.client.ui.page.settings.block.blockMenuItems
import oolong.client.ui.page.settings.block.blockMenuLabels
import oolong.client.ui.page.thread.FirstPostContent
import oolong.client.ui.page.thread.key
import oolong.client.ui.page.thread.navigateToThread
import oolong.client.ui.page.thread.query
import oolong.client.ui.widgets.compose.ContextMenuItem
import oolong.client.ui.widgets.compose.ContextMenuPreview
import oolong.client.ui.widgets.compose.LocalContextMenus
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

/** Peeks at threads of this feed; null where there are no context menus. */
@Composable
fun rememberThreadPeeker(): ThreadPeeker? {
    // Checked first: where there are no menus, as in previews and tests, there is no view model either
    val menus = LocalContextMenus.current ?: return null
    val viewModel = hiltViewModel<ThreadPeekViewModel>()
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val collect = stringResource(id = R.string.title_collect)
    val shareLabels = shareMenuLabels()
    val blockLabels = blockMenuLabels()
    val shortcuts = LocalBlockShortcuts.current
    return remember(menus, context, navigator, viewModel, collect, shortcuts, shareLabels, blockLabels) {
        ThreadPeeker { thread, from, at, origin, options, onDislike ->
            viewModel.prefetch(thread)
            val items = buildList {
                add(ContextMenuItem(Symbols.Star, collect) { viewModel.store(thread) })
                addAll(shareMenuItems(shareLabels, context, thread.threadId, thread.title))
                dislikeMenuItems(context, navigator, options, onDislike).forEachIndexed { i, item ->
                    add(if (i == 0) item.copy(startsGroup = true) else item)
                }
                if (shortcuts != null) addAll(blockMenuItems(blockLabels, shortcuts, thread, options.blockingForum(onDislike)))
            }
            menus.show(
                items = items,
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
