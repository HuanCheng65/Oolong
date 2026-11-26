package oolong.client.ui.subposts

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import oolong.client.data.post.ReplyTarget
import oolong.client.data.settings.rememberPreferenceAsState
import oolong.client.model.PostContent
import oolong.client.model.SubPost
import oolong.client.navigation.Navigator
import oolong.client.navigation.ProvideNavigator
import oolong.client.navigation.ThreadKey
import oolong.client.navigation.UserProfileKey
import oolong.client.ui.components.LocalAccount
import oolong.client.ui.components.LocalCopySheets
import oolong.client.ui.reply.ReplyTo
import oolong.client.ui.reply.rememberReplyChromeState
import oolong.client.ui.reply.rememberReplyComposer
import oolong.client.ui.thread.LandedFloor
import oolong.client.ui.thread.LocalLandedFloor
import oolong.client.ui.thread.ThreadPreferences
import oolong.client.util.TiebaUtil

/**
 * The sub-post page: connects [SubPostsScreen] to its view model, navigation and the reply dialog
 * it opens in place.
 */
@Composable
fun SubPostsRoute(
    navigator: Navigator,
    isSheet: Boolean,
    viewModel: SubPostsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val copySheets = LocalCopySheets.current
    val coroutineScope = rememberCoroutineScope()
    val account = LocalAccount.current
    val listState = rememberLazyListState()
    val landed = remember { LandedFloor() }
    val hideReply by rememberPreferenceAsState(booleanPreferencesKey("hideReply"), false)
    val hideBlocked by rememberPreferenceAsState(booleanPreferencesKey("hideBlockedContent"), false)

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SubPostsEffect.NavigateUp -> navigator.navigateUp()
                is SubPostsEffect.ScrollToSubPost -> viewModel.state.value.indexOfSubPost(effect.subPostId)
                    ?.let {
                        listState.scrollToItem(it)
                        launch { landed.land(effect.subPostId) }
                    }
                // The reply count header, under the post
                SubPostsEffect.ScrollToReplies -> listState.scrollToItem(1)
            }
        }
    }

    val chrome = rememberReplyChromeState()
    val reply = rememberReplyComposer(chrome)

    fun reply(subPost: SubPost? = null) {
        val post = state.post ?: return
        val forum = state.forum?.takeIf { it.name.isNotEmpty() } ?: return
        val replyTo = subPost?.author ?: post.author
        coroutineScope.launch {
            reply.open(
                target = ReplyTarget(
                    forumId = forum.id,
                    forumName = forum.name,
                    threadId = state.threadId,
                    postId = post.id,
                    subPostId = subPost?.id,
                    replyUserId = replyTo.id,
                ),
                replyTo = ReplyTo(
                    name = replyTo.displayName,
                    portrait = replyTo.portrait,
                    floor = post.floor,
                    quote = (subPost?.content ?: post.content).filterIsInstance<PostContent.Text>()
                        .joinToString("") { text -> text.spans.joinToString("") { it.text } }.trim(),
                ),
                subPostAuthor = subPost?.author?.let { it.portrait to it.displayName },
            )
        }
    }

    val onEvent: (SubPostsEvent) -> Unit = { event ->
        when (event) {
            SubPostsEvent.Back -> navigator.navigateUp()
            SubPostsEvent.Retry -> viewModel.retry()
            SubPostsEvent.LoadMore -> viewModel.loadMore()
            SubPostsEvent.LoadPrevious -> viewModel.loadPrevious()
            SubPostsEvent.OpenThread -> navigator.navigate(
                ThreadKey(
                    threadId = state.threadId,
                    forumId = state.forum?.id,
                    postId = state.post?.id ?: 0L,
                )
            )

            SubPostsEvent.AgreePost -> viewModel.agreePost()
            is SubPostsEvent.AgreeSubPost -> viewModel.agreeSubPost(event.subPostId)
            is SubPostsEvent.OpenUser -> navigator.navigate(UserProfileKey(event.userId))
            is SubPostsEvent.SetSort -> viewModel.setSort(event.sort)
            is SubPostsEvent.Reply -> reply(event.subPost)
            SubPostsEvent.ReplyToPost -> reply()
            is SubPostsEvent.Copy -> copySheets?.show(event.content)
            is SubPostsEvent.Report -> coroutineScope.launch {
                TiebaUtil.reportPost(context, navigator, event.postId.toString())
            }

            is SubPostsEvent.Delete -> viewModel.delete(event.subPost)
        }
    }

    ProvideNavigator(navigator = navigator) {
        CompositionLocalProvider(LocalLandedFloor provides landed) {
            SubPostsScreen(
                state = state,
                onEvent = onEvent,
                isSheet = isSheet,
                replyAvatar = account?.portrait,
                preferences = ThreadPreferences(hideReply = hideReply, hideBlocked = hideBlocked),
                listState = listState,
                chrome = chrome,
                reply = reply,
            )
        }
    }
}
