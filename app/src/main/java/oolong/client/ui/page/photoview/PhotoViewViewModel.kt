package oolong.client.ui.page.photoview

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import oolong.client.arch.Actions
import oolong.client.model.ThreadPicture
import oolong.client.models.LoadPicPageData
import oolong.client.models.PhotoViewData
import oolong.client.models.PicItem
import oolong.client.repository.PhotoRepository
import javax.inject.Inject

@Immutable
data class PhotoViewState(
    val pictures: ImmutableList<ThreadPicture> = persistentListOf(),
    /** How many pictures the thread has in all, which may be more than are loaded. */
    val totalAmount: Int = 0,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    /** The picture to show; changes when pictures are put in front. */
    val currentIndex: Int = 0,
    /** Where to load more pictures from, or null when the viewer only has the ones it was given. */
    val source: LoadPicPageData? = null,
)

/**
 * The picture viewer. It starts with the pictures it was given and, when it knows which thread
 * they are from, loads the rest of the thread's pictures around them as the user pages. One
 * lives as long as the window and serves each opening in turn.
 */
@HiltViewModel
class PhotoViewViewModel @Inject constructor(
    private val repository: PhotoRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PhotoViewState())
    val state: StateFlow<PhotoViewState> = _state.asStateFlow()
    private var session = SupervisorJob(viewModelScope.coroutineContext.job)
    private var loads = Actions(CoroutineScope(viewModelScope.coroutineContext + session))
    private var shown: PhotoViewData? = null

    /** Shows [data], dropping what was shown before; showing the same again does nothing. */
    fun start(data: PhotoViewData) {
        if (data == shown) return
        shown = data
        session.cancel()
        session = SupervisorJob(viewModelScope.coroutineContext.job)
        loads = Actions(CoroutineScope(viewModelScope.coroutineContext + session))
        _state.value = PhotoViewState()
        val source = data.data
        if (source == null) {
            _state.value = given(data)
            return
        }
        loads.launch("start") {
            runCatching { repository.pictures(source, source.picId, source.picIndex, previous = false) }
                .onSuccess { page ->
                    val first = page.pictures.firstOrNull()?.overallIndex ?: source.picIndex
                    // The pictures before the one opened are the ones given, numbered back from the first loaded
                    val loaded = page.pictures.mapTo(HashSet()) { it.picId }
                    val before = data.picItems.take(source.picIndex - 1).filterNot { it.picId in loaded }.let { items ->
                        items.mapIndexed { index, item -> item.toPicture(first - (items.size - index)) }
                    }
                    val pictures = before + page.pictures
                    _state.value = PhotoViewState(
                        pictures = pictures.toImmutableList(),
                        totalAmount = page.total,
                        hasPrevious = (pictures.firstOrNull()?.overallIndex ?: 1) > 1,
                        hasNext = (pictures.lastOrNull()?.overallIndex ?: 0) < page.total,
                        currentIndex = pictures.indexOfFirst { it.picId == source.picId }.takeIf { it != -1 }
                            ?: (source.picIndex - 1),
                        source = source,
                    )
                }
                .onFailure { _state.value = given(data) }
        }
    }

    /** Loads the pictures before the first one loaded, keeping [current] on screen. */
    fun loadPrevious(current: ThreadPicture) = load(current, previous = true)

    fun loadMore(current: ThreadPicture) = load(current, previous = false)

    private fun load(current: ThreadPicture, previous: Boolean) {
        val source = _state.value.source ?: return
        loads.launch(if (previous) "previous" else "next") {
            val page = runCatching { repository.pictures(source, current.picId, current.overallIndex, previous) }
                .getOrNull() ?: return@launch
            _state.update { state ->
                val known = state.pictures.mapTo(HashSet()) { it.picId }
                val new = page.pictures.filterNot { it.picId in known }
                if (previous) {
                    val pictures = new + state.pictures
                    state.copy(
                        pictures = pictures.toImmutableList(),
                        hasPrevious = (page.pictures.firstOrNull()?.overallIndex ?: 1) > 1,
                        currentIndex = pictures.indexOfFirst { it.picId == current.picId },
                    )
                } else {
                    state.copy(
                        pictures = (state.pictures + new).toImmutableList(),
                        hasNext = (page.pictures.lastOrNull()?.overallIndex ?: 0) < page.total,
                    )
                }
            }
        }
    }

    private fun given(data: PhotoViewData) = PhotoViewState(
        pictures = data.picItems.mapIndexed { index, item -> item.toPicture(index + 1) }.toImmutableList(),
        totalAmount = data.picItems.size,
        currentIndex = data.index,
    )
}

private fun PicItem.toPicture(overallIndex: Int) = ThreadPicture(
    picId = picId,
    originUrl = originUrl,
    url = url.takeIf { showOriginBtn },
    overallIndex = overallIndex,
    postId = postId,
)
