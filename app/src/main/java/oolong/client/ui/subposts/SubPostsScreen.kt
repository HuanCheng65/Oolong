package oolong.client.ui.subposts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.data.thread.SubPostSort
import oolong.client.model.SubPost
import oolong.client.ui.components.ErrorScreen
import oolong.client.ui.components.LoadMoreEffect
import oolong.client.ui.components.loadMoreFooter
import oolong.client.ui.designsystem.ConfirmDialog
import oolong.client.ui.designsystem.EndPull
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarTitle
import oolong.client.ui.designsystem.IconButton
import oolong.client.ui.designsystem.MyLazyColumn
import oolong.client.ui.designsystem.MyScaffold
import oolong.client.ui.designsystem.PullRefresh
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.TitleCentredToolbar
import oolong.client.ui.designsystem.WavyLoader
import oolong.client.ui.designsystem.choiceMenuItems
import oolong.client.ui.designsystem.clickMenu
import oolong.client.ui.designsystem.plus
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.states.StateScreenScope
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.reply.Content
import oolong.client.ui.reply.Panel
import oolong.client.ui.reply.ReplyCapsuleContent
import oolong.client.ui.reply.ReplyChrome
import oolong.client.ui.reply.ReplyChromeBarHeight
import oolong.client.ui.reply.ReplyChromeState
import oolong.client.ui.reply.ReplyComposer
import oolong.client.ui.reply.panelShown
import oolong.client.ui.reply.rememberReplyChromeState
import oolong.client.ui.thread.LocalLandedFloor
import oolong.client.ui.thread.PostCardOptions
import oolong.client.ui.thread.PostItem
import oolong.client.ui.thread.ThreadEvent
import oolong.client.ui.thread.ThreadFloor
import oolong.client.ui.thread.ThreadPreferences
import oolong.client.ui.thread.landedGlow

/**
 * The replies inside a post, as a page of its own or, with [isSheet], in a bottom sheet over the
 * thread.
 *
 * @param replyAvatar the logged-in user's portrait for the reply box, or null to show none
 */
@Composable
fun SubPostsScreen(
    state: SubPostsState,
    onEvent: (SubPostsEvent) -> Unit,
    modifier: Modifier = Modifier,
    isSheet: Boolean = false,
    replyAvatar: String? = null,
    preferences: ThreadPreferences = ThreadPreferences(),
    listState: LazyListState = rememberLazyListState(),
    chrome: ReplyChromeState = rememberReplyChromeState(),
    reply: ReplyComposer? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val deleteDialog = rememberDialogState()
    var deleting by remember { mutableStateOf<SubPost?>(null) }
    val handleEvent: (SubPostsEvent) -> Unit = { event ->
        if (event is SubPostsEvent.Delete) {
            deleting = event.subPost
            deleteDialog.show()
        } else {
            onEvent(event)
        }
    }
    val title = state.post?.let { stringResource(id = R.string.title_sub_posts, it.floor) }
        ?: stringResource(id = R.string.title_sub_posts_default)
    val canReply = replyAvatar != null && !preferences.hideReply && state.post != null
    // The capsule is drawn over the page, so the composer can grow out of it; this keeps the
    // content clear of it at rest
    val replyBar: @Composable () -> Unit = {
        if (canReply) {
            Spacer(
                modifier = Modifier
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Bottom)
                    )
                    .height(ReplyChromeBarHeight)
            )
        }
    }
    val replyChrome: @Composable () -> Unit = {
        if (canReply && replyAvatar != null) {
            ReplyChrome(
                state = chrome,
                replyCapsule = { ReplyCapsuleContent(replyAvatar, reply?.editor?.text.orEmpty()) },
                actions = null,
                menu = null,
                menuFooter = {},
                composer = { handle, fill -> reply?.Content(handle, fill) },
                panel = { reply?.Panel() },
                panelShown = reply?.panelShown() ?: false,
                onOpenComposer = { handleEvent(SubPostsEvent.ReplyToPost) },
                onDismiss = { reply?.close() ?: chrome.close() },
                onExitFullScreen = { chrome.fullScreen = false },
                onEnterFullScreen = { chrome.fullScreen = true },
                hiddenFraction = { 0f },
            )
        }
    }
    val content: @Composable (PaddingValues) -> Unit = { padding ->
        SubPostsContent(state, preferences, listState, padding, handleEvent)
    }

    if (isSheet) {
        Box(modifier = modifier.fillMaxSize()) {
            MyScaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TitleCentredToolbar(
                        title = { Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.h6) },
                        navigationIcon = {
                            IconButton(onClick = { onEvent(SubPostsEvent.Back) }) {
                                Icon(imageVector = Symbols.Close, contentDescription = stringResource(id = R.string.btn_close))
                            }
                        },
                        // The sheet's list isn't a page's, so the bar is handed it
                        scrollToTop = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                    )
                },
                bottomBar = replyBar,
                content = content,
            )
            replyChrome()
        }
    } else {
        GlassScaffold(
            modifier = modifier,
            topBar = {
                GlassTopBar(
                    title = { GlassTopBarTitle(title) },
                    navigationIcon = {
                        IconButton(onClick = { onEvent(SubPostsEvent.Back) }) {
                            Icon(
                                imageVector = Symbols.ArrowBack,
                                contentDescription = stringResource(id = R.string.btn_close)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onEvent(SubPostsEvent.OpenThread) }) {
                            Icon(
                                imageVector = Symbols.OpenInBrowser,
                                contentDescription = stringResource(id = R.string.btn_open_origin_thread)
                            )
                        }
                    },
                )
            },
            bottomBar = replyBar,
            overlay = replyChrome,
            content = content,
        )
    }

    ConfirmDialog(
        dialogState = deleteDialog,
        onConfirm = { onEvent(SubPostsEvent.Delete(deleting)) },
    ) {
        Text(
            text = stringResource(
                id = R.string.message_confirm_delete,
                if (deleting == null && state.post != null) stringResource(id = R.string.tip_post_floor, state.post.floor)
                else stringResource(id = R.string.this_reply)
            )
        )
    }
}

@Composable
private fun SubPostsContent(
    state: SubPostsState,
    preferences: ThreadPreferences,
    listState: LazyListState,
    padding: PaddingValues,
    onEvent: (SubPostsEvent) -> Unit,
) {
    val post = state.post
    val subPosts = state.subPosts
    if (post == null) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            val error = (subPosts.refresh as? LoadStatus.Error)?.error
            if (error != null) {
                remember { StateScreenScope { onEvent(SubPostsEvent.Retry) } }
                    .ErrorScreen(error = error, modifier = Modifier.fillMaxSize())
            } else {
                WavyLoader()
            }
        }
        return
    }

    val postEvents: (ThreadEvent) -> Unit = { event ->
        when (event) {
            is ThreadEvent.Reply -> onEvent(SubPostsEvent.Reply(post))
            is ThreadEvent.AgreePost -> onEvent(SubPostsEvent.AgreePost)
            is ThreadEvent.OpenUser -> onEvent(SubPostsEvent.OpenUser(event.userId))
            is ThreadEvent.Copy -> onEvent(SubPostsEvent.Copy(event.content))
            is ThreadEvent.Report -> onEvent(SubPostsEvent.Report(event.postId))
            is ThreadEvent.DeletePost -> onEvent(SubPostsEvent.Delete(null))
            else -> Unit
        }
    }
    LoadMoreEffect(listState, enabled = subPosts.hasMore && subPosts.append == LoadStatus.Idle, onLoadMore = { onEvent(SubPostsEvent.LoadMore) })
    PullRefresh(refreshing = false, onRefresh = {}, enabled = false, endPull = if (subPosts.hasMore) EndPull.None else EndPull.Tease) {
        MyLazyColumn(
            state = listState,
            contentPadding = padding.plus(PaddingValues(bottom = 8.dp)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "Post") {
                // The floor as the thread shows it, its replies listed below instead of inside it
                ThreadFloor(
                    item = PostItem(post.copy(subPosts = persistentListOf()), block = state.postBlock),
                    options = PostCardOptions(threadAuthorId = state.threadAuthorId, hideBlocked = preferences.hideBlocked, hideReply = preferences.hideReply),
                    onEvent = postEvents,
                    listState = listState,
                    key = "Post",
                    startsRule = true,
                    canDelete = state.viewerId != null && (post.author.id == state.viewerId || state.threadAuthorId == state.viewerId),
                    canStore = false,
                )
            }
            // Content, scrolling away with the floor, as the reply count over a thread's replies
            // does: pinned, it showed the replies through it under the glass bar
            item(key = "SubPostsHeader") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = pluralStringResource(id = R.plurals.title_sub_posts_header, count = state.totalCount, state.totalCount),
                        style = MaterialTheme.typography.subtitle1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                    )
                    SubPostSortButton(sort = state.sort, onSort = { onEvent(SubPostsEvent.SetSort(it)) })
                }
            }
            if (subPosts.hasPrevious) {
                item(key = "LoadPrevious") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEvent(SubPostsEvent.LoadPrevious) }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Symbols.AlignVerticalTop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = stringResource(id = R.string.btn_load_previous), color = ExtendedTheme.colors.text, fontSize = 14.sp)
                    }
                }
            }
            itemsIndexed(items = subPosts.items, key = { _, it -> it.subPost.id }) { index, item ->
                // The reply jumped to lights up once, as a floor jumped to does
                Box(modifier = Modifier.landedGlow(item.subPost.id, LocalLandedFloor.current)) {
                    SubPostReply(
                        post = post,
                        item = item,
                        threadAuthorId = state.threadAuthorId,
                        canDelete = state.viewerId != null && (item.subPost.author.id == state.viewerId || state.threadAuthorId == state.viewerId),
                        hideReply = preferences.hideReply,
                        last = index == subPosts.items.lastIndex && !subPosts.hasMore,
                        onEvent = onEvent,
                    )
                }
            }
            loadMoreFooter(subPosts.append, subPosts.hasMore, onRetry = { onEvent(SubPostsEvent.LoadMore) })
        }
    }
}

/** Where reply [subPostId] is in the list, or null when it isn't loaded. */
internal fun SubPostsState.indexOfSubPost(subPostId: Long): Int? {
    val index = subPosts.items.indexOfFirst { it.subPost.id == subPostId }
    if (index == -1) return null
    // The post and the reply count header come first, then the load-previous row when shown
    return 2 + (if (subPosts.hasPrevious) 1 else 0) + index
}

/** The order of the replies, shown as its name, picked from a menu. */
@Composable
private fun SubPostSortButton(sort: SubPostSort, onSort: (SubPostSort) -> Unit) {
    val options = listOf(
        SubPostSort.Hot to stringResource(id = R.string.sub_post_sort_hot),
        SubPostSort.Earliest to stringResource(id = R.string.sub_post_sort_earliest),
    )
    val color = ExtendedTheme.colors.textSecondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .clickMenu(items = { choiceMenuItems(options, sort, onSort) })
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        // The order chosen refocuses in place as the menu closes
        Refocus(targetState = options.first { it.first == sort }.second, contentAlignment = Alignment.CenterEnd) {
            Text(text = it, style = MaterialTheme.typography.body2, color = color)
        }
        Icon(imageVector = Symbols.UnfoldMore, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
    }
}
