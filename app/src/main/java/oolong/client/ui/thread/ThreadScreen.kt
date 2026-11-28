package oolong.client.ui.thread

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import oolong.client.R
import oolong.client.data.offline.OfflineThreadInfo
import oolong.client.data.thread.ThreadSort
import oolong.client.model.Post
import oolong.client.ui.designsystem.ConfirmDialog
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBarRowHeight
import oolong.client.ui.designsystem.PromptDialog
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.rememberPageScrollState
import oolong.client.ui.designsystem.rememberScrollProgress
import oolong.client.ui.offline.offlineLabel
import oolong.client.ui.offline.offlineMenuItems
import oolong.client.ui.offline.rememberOfflineEntry
import oolong.client.ui.reply.ChromeSurface
import oolong.client.ui.reply.Content
import oolong.client.ui.reply.Panel
import oolong.client.ui.reply.ReplyCapsuleContent
import oolong.client.ui.reply.ReplyChrome
import oolong.client.ui.reply.ReplyChromeBarHeight
import oolong.client.ui.reply.ReplyChromeState
import oolong.client.ui.reply.ReplyComposer
import oolong.client.ui.reply.panelShown
import oolong.client.ui.reply.rememberReplyChromeState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ThreadScreen(
    state: ThreadState,
    onEvent: (ThreadEvent) -> Unit,
    modifier: Modifier = Modifier,
    preferences: ThreadPreferences = ThreadPreferences(),
    listState: LazyListState = rememberLazyListState(),
    scaffoldState: ScaffoldState = rememberScaffoldState(),
    chrome: ReplyChromeState = rememberReplyChromeState(),
    reply: ReplyComposer? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val jumpDialog = rememberDialogState()
    val deleteDialog = rememberDialogState()
    var deleting by remember { mutableStateOf<Post?>(null) }
    val newestFirst = state.query.sort == ThreadSort.NewestFirst
    val viewer = state.viewer.user
    val context = LocalContext.current
    val offlineEntry by rememberOfflineEntry(state.query.threadId)

    val handleEvent: (ThreadEvent) -> Unit = { event ->
        when (event) {
            ThreadEvent.OpenMenu -> chrome.openMenu()

            is ThreadEvent.DeletePost -> {
                deleting = event.post
                deleteDialog.show()
            }

            else -> onEvent(event)
        }
    }

    Box(modifier = modifier) {
        val statusBarTop = WindowInsets.statusBars.union(WindowInsets.captionBar).union(WindowInsets.displayCutout).getTop(LocalDensity.current)
        var contentTop by remember { mutableIntStateOf(0) }
        // A thread that can't scroll the bar's height away would leave an empty band where the bar was
        val tooShortToHide by remember(listState) {
            derivedStateOf {
                !listState.canScrollForward &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset < contentTop
            }
        }
        // The bars follow the scroll away and back
        val page = rememberPageScrollState()
        val hide = rememberScrollProgress(away = GlassTopBarRowHeight, enabled = { !tooShortToHide }, page = page)
        // The bar's switches come as the reply header goes under the bar. Off screen, the header
        // is past when the list starts after it, as when the thread opened at a later floor
        val headerUnderBar = rememberScrollProgress(ReplyHeader, page = page)
        val filterShown: () -> Float = {
            val headerShown = listState.layoutInfo.visibleItemsInfo.any { it.index == THREAD_HEADER_INDEX }
            when {
                headerShown -> headerUnderBar.value
                listState.firstVisibleItemIndex > THREAD_HEADER_INDEX -> 1f
                else -> 0f
            }
        }
        GlassScaffold(
            pageScroll = page,
            scaffoldState = scaffoldState,
            topBar = {
                ThreadTopBar(
                    forum = state.thread?.forum,
                    onEvent = handleEvent,
                    filter = if (state.thread != null) {
                        {
                            ThreadFilterSwitches(
                                seeLz = state.query.seeLz,
                                newestFirst = state.query.sort == ThreadSort.NewestFirst,
                                onEvent = handleEvent,
                                shown = filterShown,
                            )
                        }
                    } else null,
                ) { hide.value }
            },
            // The capsules are drawn by the overlay, so the composer and the menu can grow out of
            // them; this keeps the content clear of them at rest
            bottomBar = {
                if (state.thread != null) {
                    Spacer(
                        modifier = Modifier
                            .windowInsetsPadding(
                                WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Bottom)
                            )
                            .height(ReplyChromeBarHeight)
                    )
                }
            },
            overlay = {
                val thread = state.thread
                if (thread != null) {
                    val draft = reply?.editor?.text.orEmpty()
                    ReplyChrome(
                        state = chrome,
                        replyCapsule = if (viewer != null && !preferences.hideReply && !state.query.offline) {
                            { ReplyCapsuleContent(viewer.portrait, draft) }
                        } else null,
                        actions = { ThreadActions(thread.agree, handleEvent) },
                        menu = {
                            ThreadMenu(
                                seeLz = state.query.seeLz,
                                stored = thread.isStored,
                                newestFirst = newestFirst,
                                currentPage = state.currentPage,
                                totalPage = state.totalPage,
                                onEvent = onEvent,
                                offlineLabel = offlineLabel(context, offlineEntry),
                                offlineSaved = offlineEntry != null,
                                offlineItems = {
                                    offlineMenuItems(
                                        context = context,
                                        info = OfflineThreadInfo(thread.id, thread.title, thread.forum.id, thread.forum.name, thread.author.displayName),
                                        entry = offlineEntry,
                                        authorFirst = state.query.seeLz,
                                        onDone = chrome::close,
                                    )
                                },
                                onJumpToPage = jumpDialog::show,
                                onClose = chrome::close,
                            )
                        },
                        menuFooter = {
                            ThreadMenuFooter(thread.agree, handleEvent, onClose = chrome::close) {
                                ThreadMenuActions(
                                    canDelete = viewer != null && thread.author.id == viewer.id,
                                    onEvent = onEvent,
                                    onDelete = {
                                        deleting = null
                                        deleteDialog.show()
                                    },
                                    onClose = chrome::close,
                                )
                            }
                        },
                        composer = { handle, fill -> reply?.Content(handle, fill) },
                        panel = { reply?.Panel() },
                        panelShown = reply?.panelShown() ?: false,
                        onOpenComposer = { handleEvent(ThreadEvent.Reply(post = null)) },
                        onDismiss = { if (reply != null && chrome.surface == ChromeSurface.Composer) reply.close() else chrome.close() },
                        onExitFullScreen = { chrome.fullScreen = false },
                        onEnterFullScreen = { chrome.fullScreen = true },
                        hiddenFraction = { hide.value },
                    )
                }
            },
        ) { padding ->
            val density = LocalDensity.current
            SideEffect { contentTop = with(density) { padding.calculateTopPadding().roundToPx() } }
            // Where the visible chrome ends, from the end of the top padding: the bar slides up as it hides
            val chromeEnd = { -((contentTop - statusBarTop) * hide.value).roundToInt() }
            ThreadContent(state, preferences, listState, padding, handleEvent, chromeEnd)
        }
    }

    ConfirmDialog(
        dialogState = deleteDialog,
        onConfirm = {
            val post = deleting
            onEvent(if (post == null) ThreadEvent.DeleteThread else ThreadEvent.DeletePost(post))
        },
    ) {
        Text(
            text = stringResource(
                id = R.string.message_confirm_delete,
                deleting?.let { stringResource(id = R.string.tip_post_floor, it.floor) }
                    ?: stringResource(id = R.string.this_thread)
            )
        )
    }

    PromptDialog(
        onConfirm = { it.toIntOrNull()?.let { page -> onEvent(ThreadEvent.JumpToPage(page)) } },
        dialogState = jumpDialog,
        onValueChange = { newVal, _ -> "^[0-9]*$".toRegex().matches(newVal) },
        title = { Text(text = stringResource(id = R.string.title_jump_page)) },
    ) {
        Text(text = stringResource(id = R.string.tip_jump_page, state.currentPage, state.totalPage))
    }
}
