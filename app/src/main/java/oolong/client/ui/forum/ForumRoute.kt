package oolong.client.ui.forum

import android.content.Context
import android.content.Intent
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.model.ForumDetail
import oolong.client.navigation.ForumDetailKey
import oolong.client.navigation.ForumRuleDetailKey
import oolong.client.navigation.ForumSearchPostKey
import oolong.client.navigation.Navigator
import oolong.client.navigation.ProvideNavigator
import oolong.client.navigation.ThreadKey
import oolong.client.navigation.UserProfileKey
import oolong.client.ui.designsystem.ConfirmDialog
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.thread.navigateToThread
import oolong.client.util.TiebaUtil
import oolong.client.util.requestPinShortcut
import oolong.client.util.toastShort

/**
 * The forum page: connects [ForumScreen] to its view model, navigation and the things only a page
 * can do, such as sharing.
 */
@Composable
fun ForumRoute(
    navigator: Navigator,
    viewModel: ForumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scaffoldState = rememberScaffoldState()
    val unfollowDialog = rememberDialogState()

    ConfirmDialog(dialogState = unfollowDialog, onConfirm = viewModel::unlike) {
        Text(text = stringResource(id = R.string.title_dialog_unfollow_forum, state.forumName))
    }

    val onEvent: (ForumEvent) -> Unit = { event ->
        val forum = state.forum
        when (event) {
            ForumEvent.Back -> navigator.navigateUp()
            ForumEvent.Search -> forum?.let { navigator.navigate(ForumSearchPostKey(it.name, it.id)) }
            ForumEvent.OpenDetail -> forum?.let { navigator.navigate(ForumDetailKey(forumId = it.id, ruleTitle = it.ruleTitle)) }
            ForumEvent.OpenRule -> forum?.let { navigator.navigate(ForumRuleDetailKey(it.id)) }
            ForumEvent.Share -> shareForum(context, state.forumName)
            ForumEvent.SendToDesktop -> forum?.let {
                coroutineScope.launch { sendToDesktop(context, it) { message -> context.toastShort(message) } }
            }
            ForumEvent.FollowOrSignIn -> viewModel.followOrSignIn()
            ForumEvent.Unfollow -> unfollowDialog.show()
            is ForumEvent.SelectTab -> viewModel.selectTab(event.tab)
            is ForumEvent.SetSort -> viewModel.setSort(event.sortType)
            is ForumEvent.SetGoodClass -> viewModel.setGoodClass(event.classId)
            ForumEvent.Refresh -> viewModel.refresh()
            ForumEvent.LoadMore -> viewModel.loadMore()
            is ForumEvent.OpenThread -> navigator.navigateToThread(event.thread)
            is ForumEvent.OpenReplies -> navigator.navigate(
                ThreadKey(event.thread.id, forumId = event.thread.forum.id, scrollToReply = true)
            )
            is ForumEvent.OpenOriginThread -> navigator.navigate(
                ThreadKey(threadId = event.origin.id, forumId = event.origin.forumId)
            )
            is ForumEvent.OpenUser -> navigator.navigate(UserProfileKey(event.userId))
            is ForumEvent.Agree -> viewModel.agree(event.tab, event.threadId)
        }
    }

    val fab = when (Settings.of(context)[SettingKeys.ForumFabFunction]) {
        "refresh" -> ForumFab.Refresh
        "back_to_top" -> ForumFab.BackToTop
        else -> ForumFab.Hidden
    }
    ProvideNavigator(navigator = navigator) {
        ForumScreen(state = state, onEvent = onEvent, fab = fab, scaffoldState = scaffoldState)
    }
}

private fun shareForum(context: Context, forumName: String) {
    TiebaUtil.shareText(context, "https://tieba.baidu.com/f?kw=$forumName", context.getString(R.string.title_forum, forumName))
}

private suspend fun sendToDesktop(context: Context, forum: ForumDetail, showMessage: suspend (String) -> Unit) {
    var failure: String? = null
    var succeeded = false
    requestPinShortcut(
        context,
        "forum_${forum.id}",
        forum.avatar,
        context.getString(R.string.title_forum, forum.name),
        Intent(Intent.ACTION_VIEW).setData("oolong://forum/${forum.name}".toUri()),
        onSuccess = { succeeded = true },
        onFailure = { failure = it },
    )
    when {
        succeeded -> showMessage(context.getString(R.string.toast_send_to_desktop_success))
        failure != null -> showMessage(context.getString(R.string.toast_send_to_desktop_failed, failure))
    }
}
