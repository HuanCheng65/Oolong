package oolong.client.ui.thread

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import oolong.client.R
import oolong.client.data.post.ReplyTarget
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.settingAsState
import oolong.client.data.thread.ThreadSort
import oolong.client.model.Post
import oolong.client.model.PostContent
import oolong.client.navigation.ForumKey
import oolong.client.navigation.Navigator
import oolong.client.navigation.ProvideNavigator
import oolong.client.navigation.ReaderKey
import oolong.client.navigation.SubPostsSheetKey
import oolong.client.navigation.ThreadKey
import oolong.client.navigation.UserProfileKey
import oolong.client.ui.components.LocalCopySheets
import oolong.client.ui.designsystem.AppToast
import oolong.client.ui.designsystem.ConfirmDialog
import oolong.client.ui.designsystem.MyBackHandler
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.photoview.LocalPhotoFloors
import oolong.client.ui.photoview.PhotoFloors
import oolong.client.ui.reply.ChromeSurface
import oolong.client.ui.reply.ReplyTo
import oolong.client.ui.reply.openInOfficialClient
import oolong.client.ui.reply.rememberReplyChromeState
import oolong.client.ui.reply.rememberReplyComposer
import oolong.client.util.TiebaUtil
import oolong.client.util.toastShort

/**
 * The thread page: connects [ThreadScreen] to its view model, navigation and the things only a
 * page can do, such as sharing.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ThreadRoute(
    navigator: Navigator,
    from: String,
    extra: ThreadPageExtra?,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val copySheets = LocalCopySheets.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scaffoldState = rememberScaffoldState()
    val chrome = rememberReplyChromeState()
    val reply = rememberReplyComposer(chrome)
    val hideReply by settingAsState(SettingKeys.HideReply)
    val hideBlocked by settingAsState(SettingKeys.HideBlockedContent)

    val landed = remember { LandedFloor() }
    val photoFloors = remember(viewModel) {
        object : PhotoFloors {
            override fun floorOf(postId: Long): Int? = viewModel.floorOf(postId)
            override fun show(postId: Long) = viewModel.goToPost(postId)
        }
    }
    LaunchedEffect(viewModel) {
        var scrolling: Job? = null
        viewModel.effect.collect { effect ->
            when (effect) {
                ThreadEffect.NavigateUp -> navigator.navigateUp()
                // To the reply count above the replies, once the first reply is under it
                ThreadEffect.ScrollToFirstReply -> {
                    scrolling?.cancel()
                    scrolling = launch {
                        listState.scrollOnceShown(viewModel.state, itemsAfter = 1) { state ->
                            FIRST_REPLY_INDEX.takeIf { state.thread != null && state.posts.items.isNotEmpty() }
                        }
                    }
                }

                is ThreadEffect.ScrollToPost -> {
                    scrolling?.cancel()
                    scrolling = launch {
                        if (listState.scrollOnceShown(viewModel.state) { it.indexOfPost(effect.postId) }) landed.land(effect.postId)
                    }
                }
            }
        }
    }

    // Opened from the thread store while the thread has posts past the stored mark
    var offeredStoreUpdate by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        if (offeredStoreUpdate || from != ThreadPageFrom.FROM_STORE || extra !is ThreadPageFromStoreExtra) {
            return@LaunchedEffect
        }
        offeredStoreUpdate = true
        val load = AppToast.ask(
            resources.getString(R.string.message_store_thread_update, extra.maxFloor),
            resources.getString(R.string.button_load_new),
        )
        if (load) viewModel.jumpToPost(extra.maxPid)
    }

    val storeMarkDialog = rememberDialogState()
    var storeMarkUpdate by remember { mutableStateOf<Post?>(null) }
    // Only a stored thread may ask about its store mark on leaving; any other leaves by the navigation's
    // own back, so the gesture peeks
    MyBackHandler(enabled = chrome.surface == ChromeSurface.None && state.thread?.isStored == true) {
        val update = viewModel.storeMarkUpdate()
        if (update != null) {
            storeMarkUpdate = update
            storeMarkDialog.show()
        } else {
            navigator.navigateUp()
        }
    }
    ConfirmDialog(
        dialogState = storeMarkDialog,
        onConfirm = { storeMarkUpdate?.let(viewModel::leaveUpdatingStoreMark) },
        onCancel = { navigator.navigateUp() },
    ) {
        Text(text = stringResource(R.string.message_update_collect_mark, storeMarkUpdate?.floor ?: 0))
    }

    val onEvent: (ThreadEvent) -> Unit = { event ->
        val thread = state.thread
        // A saved copy is only read: what would change something on the server waits for the thread online
        if (state.query.offline && event.needsServer) {
            context.toastShort(R.string.offline_unavailable)
        } else when (event) {
            ThreadEvent.Back -> navigator.navigateUp()
            ThreadEvent.Refresh -> viewModel.refresh()
            ThreadEvent.Retry -> viewModel.retry()
            ThreadEvent.LoadMore -> viewModel.loadMore()
            ThreadEvent.LoadLatest -> viewModel.loadLatest()
            ThreadEvent.LoadPrevious -> viewModel.loadPrevious()
            is ThreadEvent.SetSeeLz -> {
                if (event.seeLz != state.query.seeLz) listState.keepRepliesStart()
                viewModel.setSeeLz(event.seeLz)
            }

            ThreadEvent.ToggleSort -> {
                listState.keepRepliesStart()
                viewModel.setSort(
                    if (state.query.sort == ThreadSort.NewestFirst) ThreadSort.OldestFirst else ThreadSort.NewestFirst
                )
            }

            ThreadEvent.OpenReader -> navigator.navigate(
                ReaderKey(state.query.threadId, thread?.forum?.id ?: state.query.forumId, floor = viewModel.readingFloor(), offline = state.query.offline)
            )
            is ThreadEvent.JumpToPage -> viewModel.jumpToPage(event.page)
            ThreadEvent.OpenMenu -> Unit
            ThreadEvent.AgreeThread -> viewModel.agreeThread()
            is ThreadEvent.AgreePost -> viewModel.agreePost(event.postId)
            is ThreadEvent.ShowMoreSubPosts -> viewModel.openSubPosts(event.post)
            is ThreadEvent.ShowFewerSubPosts -> viewModel.closeSubPosts(event.postId)
            is ThreadEvent.Vote -> viewModel.vote(event.optionIds)
            ThreadEvent.OpenForum -> thread?.let { navigator.navigate(ForumKey(it.forum.name)) }
            is ThreadEvent.OpenUser -> navigator.navigate(UserProfileKey(event.userId))
            is ThreadEvent.Reply -> if (thread != null) {
                val post = event.post
                val subPost = event.subPost
                val replyTo = subPost?.author ?: post?.author
                coroutineScope.launch {
                    reply.open(
                        target = ReplyTarget(
                            forumId = thread.forum.id,
                            forumName = thread.forum.name,
                            threadId = thread.id,
                            postId = post?.id,
                            subPostId = subPost?.id,
                            replyUserId = replyTo?.id,
                        ),
                        replyTo = if (post != null && replyTo != null) {
                            ReplyTo(
                                name = replyTo.displayName,
                                portrait = replyTo.portrait,
                                floor = post.floor,
                                quote = (subPost?.content ?: post.content).plainText(),
                            )
                        } else null,
                        subPostAuthor = subPost?.author?.let { it.portrait to it.displayName },
                    )
                }
            }

            is ThreadEvent.OpenSubPosts -> if (thread != null) {
                navigator.navigate(
                    SubPostsSheetKey(
                        forumId = thread.forum.id,
                        threadId = thread.id,
                        postId = event.post.id,
                        subPostId = event.subPostId,
                        loadFromSubPost = false,
                        previewIds = event.post.subPosts.map { it.id },
                    )
                )
            }

            is ThreadEvent.OpenOriginThread ->
                navigator.navigate(ThreadKey(threadId = event.threadId, forumId = event.forumId))

            is ThreadEvent.Copy -> copySheets?.show(event.content)
            is ThreadEvent.Report -> coroutineScope.launch {
                TiebaUtil.reportPost(context, navigator, event.postId.toString())
            }

            ThreadEvent.ReportThread -> thread?.let {
                coroutineScope.launch { TiebaUtil.reportPost(context, navigator, it.firstPostId.toString()) }
            }

            ThreadEvent.Share -> TiebaUtil.shareText(
                context,
                "https://tieba.baidu.com/p/${state.query.threadId}",
                thread?.title ?: state.preview?.title,
            )

            ThreadEvent.OpenInOfficialClient -> openInOfficialClient(context, state.query.threadId, postId = null)
            ThreadEvent.CopyLink -> TiebaUtil.copyText(
                context,
                "https://tieba.baidu.com/p/${state.query.threadId}?see_lz=${if (state.query.seeLz) 1 else 0}"
            )

            is ThreadEvent.StoreAt -> viewModel.addStore(event.post)
            ThreadEvent.StoreAtLastRead -> viewModel.addStore()
            ThreadEvent.RemoveStore -> viewModel.removeStore()
            is ThreadEvent.DeletePost -> viewModel.deletePost(event.post)
            ThreadEvent.DeleteThread -> viewModel.deleteThread()
            is ThreadEvent.ReadUpTo -> viewModel.onReadUpTo(event.postId)
        }
    }

    ProvideNavigator(navigator = navigator) {
        CompositionLocalProvider(LocalLandedFloor provides landed, LocalPhotoFloors provides photoFloors) {
            ThreadScreen(
                state = state,
                onEvent = onEvent,
                preferences = ThreadPreferences(hideReply = hideReply, hideBlocked = hideBlocked),
                listState = listState,
                scaffoldState = scaffoldState,
                chrome = chrome,
                reply = reply,
            )
        }
    }
}

/** The words of a post, for quoting it: its text, without pictures or other media. */
private fun List<PostContent>.plainText(): String =
    filterIsInstance<PostContent.Text>().joinToString("") { text -> text.spans.joinToString("") { it.text } }.trim()

/**
 * Scrolls to the item [indexIn] finds, once the list shows it and [itemsAfter] more. A scroll
 * asked for comes with the page that brought what it scrolls to, ahead of the state and the list
 * that show that page; one made at once would find the item missing, or stop short at the end of
 * a list too short yet to bring it to the top. Gives up after a while, as when the page never
 * shows.
 */
private suspend fun LazyListState.scrollOnceShown(
    state: Flow<ThreadState>,
    itemsAfter: Int = 0,
    indexIn: (ThreadState) -> Int?,
): Boolean = withTimeoutOrNull(SCROLL_WAIT_MILLIS) {
    val index = state.mapNotNull(indexIn).first()
    snapshotFlow { layoutInfo.totalItemsCount }.first { it > index + itemsAfter }
    scrollToItem(index)
    true
} ?: false

private const val SCROLL_WAIT_MILLIS = 5_000L
