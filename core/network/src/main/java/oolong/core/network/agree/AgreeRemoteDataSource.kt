package oolong.core.network.agree

import oolong.core.network.CommonParams
import oolong.core.network.TiebaNetwork
import oolong.core.network.TiebaProfile
import oolong.core.network.form.signedFormBody

/** What an agree (like) or disagree applies to. */
sealed interface AgreeTarget {
    val threadId: Long

    /** @property firstPostId the first floor, which identifies the thread itself */
    data class Thread(override val threadId: Long, val firstPostId: Long) : AgreeTarget
    data class Post(override val threadId: Long, val postId: Long) : AgreeTarget
    data class SubPost(override val threadId: Long, val subPostId: Long) : AgreeTarget
}

class AgreeRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
) {
    private val profile = TiebaProfile.FORM
    private val service = network.service<AgreeService>(profile)

    /**
     * Agrees or disagrees with [target], or takes it back when [undo].
     *
     * @param forumId the forum of the thread
     */
    suspend fun opAgree(
        target: AgreeTarget,
        forumId: Long?,
        undo: Boolean = false,
        disagree: Boolean = false,
    ): AgreeResponse {
        val (objType, postId) = when (target) {
            is AgreeTarget.Thread -> 3 to target.firstPostId
            is AgreeTarget.Post -> 1 to target.postId
            is AgreeTarget.SubPost -> 2 to target.subPostId
        }
        val params = commonParams.form(profile, includeTbs = true) + listOfNotNull(
            "thread_id" to target.threadId.toString(),
            "post_id" to postId.toString(),
            "op_type" to if (undo) "1" else "0",
            "obj_type" to objType.toString(),
            "agree_type" to if (disagree) "5" else "2",
            forumId?.let { "forum_id" to it.toString() },
        )
        return service.opAgree(signedFormBody(params, withSig = true))
    }
}
