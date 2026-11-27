package oolong.core.network.forum

import oolong.core.network.CommonParams
import oolong.core.network.TiebaNetwork
import oolong.core.network.TiebaProfile
import oolong.core.network.form.signedFormBody

/**
 * Following and unfollowing forums, as sent from the forum page.
 *
 * `authsid` and `st_type` are always sent, as the literal `null` when they have no
 * value; `dev_id` is only sent when there is one.
 */
class LikeRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
) {
    private val profile = TiebaProfile.FORM
    private val service = network.service<LikeService>(profile)

    /**
     * @param hasHead whether the forum page shows a star or activity header image
     */
    suspend fun like(forumId: Long, forumName: String, hasHead: Boolean, tbs: String? = null): LikeForumResponse {
        val session = checkNotNull(commonParams.session()) { "Not logged in" }
        val params = commonParams.form(profile, includeTbs = tbs == null) + listOfNotNull(
            "kw" to forumName,
            "fid" to forumId.toString(),
            "forum_name" to forumName,
            "pagefrom" to "FRS",
            "st_type" to "null",
            "user_name" to session.name,
            "user_id" to session.uid,
            "has_head" to if (hasHead) "1" else "0",
            "authsid" to "null",
            tbs?.let { "tbs" to it },
        )
        return service.like(signedFormBody(params, withSig = true))
    }

    /** Unlike following, unfollowing is sent without `sig`. */
    suspend fun unfavolike(forumId: Long, forumName: String, tbs: String? = null) {
        val params = commonParams.form(profile, includeTbs = tbs == null) + listOfNotNull(
            "fid" to forumId.toString(),
            "kw" to forumName,
            "favo_type" to "1",
            "st_type" to "from_frs",
            "authsid" to "null",
            tbs?.let { "tbs" to it },
        )
        service.unfavolike(signedFormBody(params, withSig = false))
    }

    /**
     * A page of the forums the user follows, [pageSize] at a time. Unlike the forum list of
     * forum recommendations, this has no limit of 200 forums.
     *
     * @param page page number, from 1
     */
    suspend fun forumGuide(page: Int, pageSize: Int = 50): ForumGuideResponse {
        val params = commonParams.form(profile, includeTbs = true) + listOf(
            "page_no" to page.toString(),
            "res_num" to pageSize.toString(),
            "sort_type" to "3",
            "call_from" to "3",
            "top_forum_num" to "0",
        )
        return service.forumGuide(signedFormBody(params, withSig = false))
    }
}
