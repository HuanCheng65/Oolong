package oolong.core.network.upload

import okio.ByteString
import oolong.core.network.CommonParams
import oolong.core.network.TiebaNetwork
import oolong.core.network.TiebaProfile
import oolong.core.network.form.MultipartFormBody
import oolong.core.network.form.signedParams

/** The watermark the server adds to an uploaded picture. */
enum class PictureWatermark(val type: String) {
    NONE("0"),
    USER_NAME("1"),
    FORUM_NAME("2"),
}

/**
 * One chunk of a picture upload. Every chunk of a picture shares [resourceId], [size] and the
 * picture's dimensions.
 */
data class PictureChunk(
    val resourceId: String,
    val chunkNo: Int,
    val isFinish: Boolean,
    val size: Long,
    val width: Int,
    val height: Int,
    val saveOrigin: Boolean,
    val watermark: PictureWatermark,
    /** The forum the picture is posted to, empty when there is none. */
    val forumName: String,
    val bytes: ByteString,
)

/**
 * Picture and portrait uploads, sent through the classic stack as
 * multipart forms: the common parameters and business fields are signed with `sign`, the file
 * part is not.
 */
class UploadRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
) {
    private val profile = TiebaProfile.FORM
    private val service = network.service<UploadService>(profile)

    suspend fun uploadPictureChunk(chunk: PictureChunk): UploadPictureResponse {
        val userName = commonParams.session()?.name
        val fields = listOfNotNull(
            "alt" to "json",
            "resourceId" to chunk.resourceId,
            "chunkNo" to chunk.chunkNo.toString(),
            "isFinish" to if (chunk.isFinish) "1" else "0",
            "is_bjh" to "0",
            "size" to chunk.size.toString(),
            "width" to chunk.width.toString(),
            "height" to chunk.height.toString(),
            "groupId" to "1",
            "saveOrigin" to if (chunk.saveOrigin) "1" else "0",
            "pic_water_type" to chunk.watermark.type,
            userName?.let { "user_name" to it }.takeIf { chunk.watermark == PictureWatermark.USER_NAME },
            ("forum_name" to chunk.forumName)
                .takeIf { chunk.watermark == PictureWatermark.FORUM_NAME && chunk.forumName.isNotEmpty() },
            ("small_flow_fname" to chunk.forumName).takeIf { chunk.forumName.isNotEmpty() },
        )
        return service.uploadPicture(body(fields, "chunk", chunk.bytes))
    }

    suspend fun uploadPortrait(picture: ByteString) {
        service.uploadPortrait(body(emptyList(), "pic", picture))
    }

    private fun body(fields: List<Pair<String, String>>, fileField: String, file: ByteString) =
        MultipartFormBody(signedParams(commonParams.form(profile) + fields, withSig = false), fileField, file)
}
