package oolong.client.ui.reader

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.settingAsMutableState
import oolong.client.media.ImageUtil
import oolong.client.model.LoadPicPageData
import oolong.client.model.PhotoViewData
import oolong.client.model.PicItem
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.ReaderKey
import oolong.client.navigation.SubPostsSheetKey

/** 沉浸阅读: the thread author's floors read as one text. */
@Composable
fun ReaderPage(
    key: ReaderKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: ReaderViewModel = hiltViewModel<ReaderViewModel, ReaderViewModel.Factory> { it.create(key) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    // The cover comes before the first block
    var scrollTo by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ReaderEffect.ScrollToBlock -> scrollTo = effect.index + 1
            }
        }
    }
    LaunchedEffect(scrollTo) {
        val item = scrollTo ?: return@LaunchedEffect
        // The list may not have the block yet, and would stop short at its end
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > item }
        listState.scrollToItem(item)
        scrollTo = null
    }
    var fontSize by settingAsMutableState(SettingKeys.ReaderFontSize)
    var spacing by settingAsMutableState(SettingKeys.ReaderSpacing)
    var serif by settingAsMutableState(SettingKeys.ReaderSerif)
    var weight by settingAsMutableState(SettingKeys.ReaderWeight)
    var paper by settingAsMutableState(SettingKeys.ReaderPaper)
    val style = ReaderStyle(
        fontSize = fontSize,
        spacing = spacing,
        serif = serif,
        page = ReaderPaper.entries.firstOrNull { it.name == paper } ?: ReaderPaper.White,
        weight = weight,
    )
    val thread = state.thread
    val photoViewData = remember(thread?.id, thread?.forum) {
        { postId: Long, image: oolong.client.model.PostContent.Image ->
            thread?.let {
                PhotoViewData(
                    data = LoadPicPageData(
                        forumId = it.forum.id,
                        forumName = it.forum.name,
                        threadId = it.id,
                        postId = postId,
                        objType = "pb",
                        picId = image.id,
                        picIndex = 1,
                        seeLz = true,
                        originUrl = image.originUrl,
                    ),
                    picItems = persistentListOf(
                        PicItem(
                            picId = image.id,
                            picIndex = 1,
                            url = ImageUtil.getUrl(context, true, image.originUrl, *image.urls.toTypedArray()),
                            originUrl = image.originUrl,
                            showOriginBtn = image.showOriginButton,
                            originSize = image.originSize,
                            postId = postId,
                        )
                    ),
                )
            }
        }
    }
    ReaderScreen(
        state = if (scrollTo != null) state.copy(seeking = true) else state,
        style = style,
        onStyleChange = {
            fontSize = it.fontSize
            spacing = it.spacing
            serif = it.serif
            paper = it.page.name
            weight = it.weight
        },
        onBack = navigator::navigateUp,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        onOpenSubPosts = { postId ->
            thread?.let {
                navigator.navigate(SubPostsSheetKey(threadId = it.id, forumId = it.forum.id, postId = postId, atReplies = true))
            }
        },
        onReadUpTo = viewModel::onReadUpTo,
        photoViewData = photoViewData,
        listState = listState,
    )
}
