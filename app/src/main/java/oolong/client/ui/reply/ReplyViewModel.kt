package oolong.client.ui.reply

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import oolong.client.arch.Actions
import oolong.client.arch.ApplicationScope
import oolong.client.data.error.getErrorCode
import oolong.client.data.post.PostRepository
import oolong.client.data.post.PostedReply
import oolong.client.data.post.ReplyTarget
import oolong.client.database.DraftDao
import oolong.client.database.DraftEntity
import oolong.core.network.VerificationChallenge
import oolong.core.network.VerificationRequiredException
import javax.inject.Inject

enum class ReplyPanelType {
    NONE,
    EMOJI,
    IMAGE,
    VOICE
}

/** Why the last attempt to send did not go through, shown in the composer above the text kept. */
sealed interface ReplyError {
    /** The server wants the account verified first. */
    data class Verification(val challenge: VerificationChallenge) : ReplyError

    /** A picture could not be uploaded; [message] may be empty. */
    data class Upload(val message: String) : ReplyError

    /** The server turned the reply down, or it never got there; [message] may be empty. */
    data class Send(val code: Int, val message: String) : ReplyError
}

@Immutable
data class ReplyState(
    val uploading: Boolean = false,
    val sending: Boolean = false,
    /** Whether the reply went through, after which the draft is no longer saved. */
    val sent: Boolean = false,
    val panel: ReplyPanelType = ReplyPanelType.NONE,
    val images: ImmutableList<String> = persistentListOf(),
    /** Whether to upload the images as they are rather than compressed. */
    val originalImages: Boolean = false,
    /** Whether the little tail set in settings goes at the end of this reply. */
    val withTail: Boolean = true,
    val error: ReplyError? = null,
) {
    val busy: Boolean get() = uploading || sending
}

/**
 * Writing a reply: the panel shown under the editor, the pictures picked, and sending, which
 * uploads the pictures first and appends them to the text.
 */
@HiltViewModel
class ReplyViewModel @Inject constructor(
    private val repository: PostRepository,
    private val drafts: DraftDao,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val actions = Actions(viewModelScope)
    private val _state = MutableStateFlow(ReplyState())
    val state: StateFlow<ReplyState> = _state.asStateFlow()

    private val _sent = Channel<PostedReply>(Channel.BUFFERED)

    /** Each reply that went through, once. */
    val sent: Flow<PostedReply> = _sent.receiveAsFlow()

    /** Starts a new reply: what the last one picked, chose or ran into is cleared. */
    fun begin() {
        _state.value = ReplyState()
    }

    fun switchPanel(panel: ReplyPanelType) = _state.update { it.copy(panel = panel) }

    fun setWithTail(withTail: Boolean) = _state.update { it.copy(withTail = withTail) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun addImages(uris: List<String>) = _state.update { it.copy(images = (it.images + uris).toImmutableList()) }

    fun removeImage(index: Int) = _state.update { state ->
        state.copy(images = state.images.filterIndexed { i, _ -> i != index }.toImmutableList())
    }

    fun replaceImage(index: Int, uri: String) = _state.update { state ->
        state.copy(images = state.images.mapIndexed { i, old -> if (i == index) uri else old }.toImmutableList())
    }

    fun setOriginalImages(original: Boolean) = _state.update { it.copy(originalImages = original) }

    /** The unsent reply saved under [hash], a hash of what it replies to. */
    suspend fun draft(hash: String): String? = drafts.content(hash)

    fun saveDraft(hash: String, text: String) {
        appScope.launch { drafts.upsert(DraftEntity(hash, text)) }
    }

    fun deleteDraft(hash: String) {
        appScope.launch { drafts.delete(hash) }
    }

    /**
     * Uploads the picked pictures, if any, and posts [text] with them to [target]. What goes wrong
     * is kept in [ReplyState.error] for the composer to show, with the text left as it was.
     *
     * @param tbs a fresher `tbs` than the account's, such as the one the thread page returned
     * @param tail the little tail to add at the end, when [ReplyState.withTail] is on
     */
    fun send(target: ReplyTarget, text: String, tbs: String?, tail: String? = null) = actions.launch("send") {
        _state.update { it.copy(error = null) }
        val withTail = if (!tail.isNullOrBlank() && _state.value.withTail) "$text\n$tail" else text
        val images = _state.value.images
        val content = if (images.isEmpty()) {
            withTail
        } else {
            _state.update { it.copy(uploading = true) }
            val pictures = try {
                repository.uploadPictures(target.forumName, images, _state.value.originalImages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(error = ReplyError.Upload(e.message.orEmpty())) }
                null
            } finally {
                _state.update { it.copy(uploading = false) }
            }
            pictures ?: return@launch
            "$withTail\n${pictures.joinToString("\n")}"
        }

        _state.update { it.copy(sending = true) }
        try {
            val reply = repository.reply(target, content, tbs)
            _state.update { it.copy(sending = false, sent = true) }
            _sent.send(reply)
        } catch (e: CancellationException) {
            throw e
        } catch (e: VerificationRequiredException) {
            _state.update { it.copy(sending = false, error = ReplyError.Verification(e.challenge)) }
        } catch (e: Throwable) {
            _state.update { it.copy(sending = false, error = ReplyError.Send(e.getErrorCode(), e.message.orEmpty())) }
        }
    }
}
