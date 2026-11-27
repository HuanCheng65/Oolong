package oolong.client.data.post

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import oolong.client.arch.IoDispatcher
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.media.FileUtil
import oolong.client.media.upload.ImageUploader
import oolong.core.network.post.PostRemoteDataSource
import oolong.core.network.protos.addPost.AddPostRequestData
import oolong.core.network.upload.UploadRemoteDataSource
import javax.inject.Inject

/** Where a reply goes: a thread, a floor of it, or a reply inside a floor. */
data class ReplyTarget(
    val forumId: Long,
    val forumName: String,
    val threadId: Long,
    /** The floor replied to, null for a reply to the thread. */
    val postId: Long? = null,
    /** The reply inside the floor replied to, null for a reply to the floor. */
    val subPostId: Long? = null,
    val replyUserId: Long? = null,
)

/** A reply the server took. */
data class PostedReply(
    val threadId: Long,
    val postId: Long,
    /** The experience it earned, may be empty. */
    val expInc: String
)

/** What the user answered a captcha with, sent along with the same post again. */
data class CaptchaAnswer(val vcode: String, val md5: String, val type: String)

/** The display name of the account that posts, sent along with each post. */
fun interface PosterName {
    fun nameShow(): String?
}

/** Replying to threads, with pictures uploaded first. */
class PostRepository @Inject constructor(
    private val remote: PostRemoteDataSource,
    private val uploads: UploadRemoteDataSource,
    private val posterName: PosterName,
    private val sentReplies: SentReplies,
    private val settings: Settings,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * Posts [content] to [target].
     *
     * @param tbs a fresher `tbs` than the account's, such as the one the thread page returned
     */
    suspend fun reply(target: ReplyTarget, content: String, tbs: String?, captcha: CaptchaAnswer? = null): PostedReply = withContext(io) {
        val request = target.toRequest(content).let {
            if (captcha == null) it else it.copy(vcode = captcha.vcode, vcode_md5 = captcha.md5, vcode_type = captcha.type)
        }
        val response = remote.addPost(request, tbs)
        val reply = PostedReply(
            threadId = response.tid.toLongOrNull() ?: target.threadId,
            postId = checkNotNull(response.pid.toLongOrNull()) { "No post id" },
            expInc = response.exp?.inc.orEmpty(),
        )
        sentReplies.add(SentReply(target.threadId, target.postId, reply.postId))
        reply
    }

    /**
     * Uploads the pictures at [uris] to [forumName] and returns how to embed each in a post.
     *
     * @param original whether to upload them as they are rather than compressed
     */
    suspend fun uploadPictures(forumName: String, uris: List<String>, original: Boolean): List<String> = withContext(io) {
        // Edited pictures are files of our own; picked ones are found through the media store
        val paths = uris.map {
            val uri = it.toUri()
            if (uri.scheme == "file") uri.path.orEmpty() else FileUtil.getRealPathFromUri(context, uri)
        }
        ImageUploader(uploads, forumName, settings[SettingKeys.PicWatermarkType]).uploadImages(paths, original).first().map { image ->
            // ImageUploader only returns results that carry picId and picInfo
            val originPic = checkNotNull(image.picInfo).originPic
            "#(pic,${image.picId},${originPic.width},${originPic.height})"
        }
    }

    /** The business fields as TiebaLite has always sent them; the data source adds the common ones. */
    private fun ReplyTarget.toRequest(content: String): AddPostRequestData {
        val postId = postId?.toString()
        val subPostId = subPostId?.toString()
        return AddPostRequestData(
            anonymous = "1",
            barrage_time = "0".takeIf { postId.isNullOrEmpty() },
            can_no_forum = "0",
            content = content,
            entrance_type = "0",
            fid = forumId.toString(),
            floor_num = "0",
            kw = forumName,
            is_ad = "0",
            is_addition = "0",
            is_barrage = "0",
            is_feedback = "0",
            is_giftpost = "0",
            is_pictxt = "0",
            is_show_bless = 0,
            is_twzhibo_thread = "0",
            name_show = posterName.nameShow().orEmpty(),
            new_vcode = "1",
            post_from = if (postId.isNullOrEmpty() && subPostId.isNullOrEmpty()) "13" else if (subPostId.isNullOrEmpty()) "0" else null,
            quote_id = postId,
            reply_uid = replyUserId?.toString().takeIf { !postId.isNullOrEmpty() },
            repostid = postId,
            sub_post_id = subPostId,
            show_custom_figure = 0,
            takephoto_num = "0",
            tid = threadId.toString(),
            v_fid = "".takeIf { postId.isNullOrEmpty() },
            v_fname = "".takeIf { postId.isNullOrEmpty() },
            vcode_tag = "12",
        )
    }
}
