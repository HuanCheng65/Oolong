package oolong.core.network.sign

import oolong.core.network.CommonParams
import oolong.core.network.TiebaNetwork
import oolong.core.network.TiebaProfile
import oolong.core.network.form.signedFormBody

class SignRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
) {
    private val profile = TiebaProfile.FORM
    private val service = network.service<SignService>(profile)

    /**
     * Signs in to a forum.
     *
     * @param tbs a fresher `tbs` than the account's, such as the one the forum page returned
     */
    suspend fun sign(forumId: Long, forumName: String, tbs: String? = null): SignResponse {
        val common = commonParams.form(profile, includeTbs = tbs == null)
        val params = common +
            listOfNotNull("fid" to forumId.toString(), "kw" to forumName, tbs?.let { "tbs" to it })
        // sig is on by default and only turned off by the android_enc_sig_new switch from sync
        return service.sign(signedFormBody(params, withSig = true))
    }

    /**
     * Signs in to up to [MAX_MSIGN_FORUMS] forums at once.
     *
     * `authsid` is always sent, as the literal `null` until an account access
     * check has issued one.
     */
    suspend fun mSign(forumIds: List<Long>, tbs: String? = null): MSignResponse {
        require(forumIds.size <= MAX_MSIGN_FORUMS) { "At most $MAX_MSIGN_FORUMS forums per request" }
        val session = checkNotNull(commonParams.session()) { "Not logged in" }
        val params = commonParams.form(profile, includeTbs = tbs == null) + listOfNotNull(
            "user_id" to session.uid,
            "forum_ids" to forumIds.joinToString(","),
            "authsid" to "null",
            tbs?.let { "tbs" to it },
        )
        return service.mSign(signedFormBody(params, withSig = true))
    }

    /** The limits of one-click sign-in for the user. */
    suspend fun forumList(): ForumListResponse {
        val session = checkNotNull(commonParams.session()) { "Not logged in" }
        val params = commonParams.form(profile) + listOf("user_id" to session.uid)
        return service.forumList(signedFormBody(params, withSig = false))
    }

    companion object {
        const val MAX_MSIGN_FORUMS = 50
    }
}
