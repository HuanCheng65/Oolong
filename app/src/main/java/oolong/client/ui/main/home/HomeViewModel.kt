package oolong.client.ui.main.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import oolong.client.R
import oolong.client.arch.Actions
import oolong.client.arch.LoadStatus
import oolong.client.arch.MessageController
import oolong.client.arch.query.QueryState
import oolong.client.background.sign.SignProgress
import oolong.client.data.forum.HomeRepository
import oolong.client.model.FollowedForum
import oolong.client.model.VisitedForum
import javax.inject.Inject

@Immutable
data class HomeState(
    val forums: ImmutableList<FollowedForum> = persistentListOf(),
    val pinnedIds: ImmutableSet<Long> = persistentSetOf(),
    val visited: ImmutableList<VisitedForum> = persistentListOf(),
    /** How loading the followed forums went, as long as there are none to show yet. */
    val load: LoadStatus = LoadStatus.Idle,
    /** Whether the user asked for the followed forums again and they are on their way. */
    val refreshing: Boolean = false,
    val loggedIn: Boolean = false,
    /** The signing going on now, or just over; null when there is none. */
    val signing: SignProgress.Run? = null,
) {
    /** The followed forums the user pinned, in the order they are followed. */
    val pinned: ImmutableList<FollowedForum> get() = forums.filter { it.id in pinnedIds }.toImmutableList()
}

/**
 * The home page: the forums the user follows, with the pinned ones first and the visited ones
 * above. The followed forums are the account's kept ones, which load again when stale and
 * whenever a forum is followed, unfollowed or signed in to anywhere; they load quietly behind
 * what is shown, unless the user asked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val messages: MessageController,
) : ViewModel() {
    private val actions = Actions(viewModelScope)
    private val refreshing = MutableStateFlow(false)

    /** Forums unfollowed here, left out until the list loads without them. */
    private val unfollowed = MutableStateFlow(emptySet<Long>())

    private val account = repository.account.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val followed: Flow<Pair<String, QueryState<List<FollowedForum>>>?> = account.flatMapLatest { uid ->
        when {
            uid == null -> flowOf(null)
            uid.isEmpty() -> flowOf(uid to QueryState(data = emptyList()))
            else -> repository.followedForums(uid).map { uid to it }
        }
    }

    /** The followed forums, with those signed by the signing going on now marked so at once. */
    private val followedSigned = combine(followed, SignProgress.state) { followed, signing ->
        val signed = signing?.signed.orEmpty()
        val marked = followed?.let { (uid, query) ->
            if (signed.isEmpty()) uid to query
            else uid to query.copy(data = query.data?.map { if (!it.signedIn && it.id in signed) it.copy(signedIn = true) else it })
        }
        marked to signing
    }

    val state: StateFlow<HomeState> = combine(
        followedSigned,
        unfollowed,
        refreshing,
        repository.pinnedForumIds,
        repository.visitedForums,
    ) { (followed, signing), unfollowed, refreshing, pinned, visited ->
        val (uid, query) = followed ?: (null to QueryState(status = LoadStatus.Loading))
        val forums = query.data
        HomeState(
            forums = forums.orEmpty().filterNot { it.id in unfollowed }.toImmutableList(),
            pinnedIds = pinned.toImmutableSet(),
            visited = visited.toImmutableList(),
            // Until anything is known, it is loading, so a logged-in user never sees the guest page first
            load = if (forums == null) query.status.takeUnless { it == LoadStatus.Idle } ?: LoadStatus.Loading else LoadStatus.Idle,
            refreshing = refreshing,
            loggedIn = uid?.isNotEmpty() ?: true,
            signing = signing,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState(load = LoadStatus.Loading))

    /** Loads the followed forums again because the user asked, saying so if it fails. */
    fun refresh() = actions.launch("refresh") {
        val uid = account.value?.takeIf(String::isNotEmpty) ?: return@launch
        refreshing.value = true
        runCatching { repository.reloadFollowedForums(uid) }
            .onSuccess { unfollowed.value = emptySet() }
            .onFailure { if (state.value.forums.isNotEmpty()) messages.showError(R.string.toast_load_failed, it) }
        refreshing.value = false
    }

    /** Loads the followed forums again if they are stale, as when the page shows again. */
    fun freshen() {
        account.value?.takeIf(String::isNotEmpty)?.let(repository::freshenFollowedForums)
    }

    fun pin(forum: FollowedForum) = actions.launch("pin-${forum.id}") {
        runCatching { repository.pin(forum.id) }.onFailure { messages.showError(R.string.toast_top_forum_failed, it) }
    }

    fun unpin(forum: FollowedForum) = actions.launch("pin-${forum.id}") {
        runCatching { repository.unpin(forum.id) }.onFailure { messages.showError(R.string.toast_top_forum_failed, it) }
    }

    fun unfollow(forum: FollowedForum) = actions.launch("unfollow-${forum.id}") {
        unfollowed.update { it + forum.id }
        runCatching { repository.unfollow(forum) }
            .onFailure {
                unfollowed.update { ids -> ids - forum.id }
                messages.showError(R.string.toast_unlike_failed, it)
            }
    }
}
