package oolong.client.ui.main.notifications.likes

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.arch.pager
import oolong.client.data.notification.NotificationRepository
import oolong.client.model.Like
import oolong.client.model.User
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

/** The likes on one post on one day: who liked it, newest first, and the newest like. */
@Immutable
data class LikeGroup(
    val latest: Like,
    val likers: ImmutableList<User>,
) {
    val key: String get() = "${latest.postId}_${latest.time}"
}

/**
 * Groups [this] (newest first) by post and day, each group where its newest like is, so one post
 * liked by many people takes one row. Someone who liked the same post twice counts once.
 */
fun List<Like>.groupedByPostAndDay(zone: TimeZone = TimeZone.getDefault()): List<LikeGroup> {
    val calendar = Calendar.getInstance(zone)
    val groups = LinkedHashMap<Pair<Long, Int>, MutableList<Like>>()
    for (like in this) {
        calendar.timeInMillis = like.time * 1000
        val day = calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
        groups.getOrPut(like.postId to day) { mutableListOf() } += like
    }
    return groups.values.map { likes ->
        LikeGroup(latest = likes.first(), likers = likes.map { it.liker }.distinctBy { it.id }.toImmutableList())
    }
}

/** The likes on the user's posts, grouped by post and day, a page of likes at a time. */
@HiltViewModel
class ReceivedLikesViewModel @Inject constructor(
    private val repository: NotificationRepository,
) : ViewModel() {
    private val pager = pager(initialKey = 0L, itemKey = { like: Like -> like.id }) { after -> repository.likes(after) }

    val state: StateFlow<PagerState<LikeGroup>> = pager.state.map { list ->
        PagerState(
            items = list.items.groupedByPostAndDay().toImmutableList(),
            refresh = list.refresh,
            append = list.append,
            hasMore = list.hasMore,
            loaded = list.loaded,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PagerState(refresh = LoadStatus.Loading))

    fun refresh() = pager.refresh()

    fun loadMore() = pager.loadMore()
}
