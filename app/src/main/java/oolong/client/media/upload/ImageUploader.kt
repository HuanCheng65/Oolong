package oolong.client.media.upload

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import oolong.client.data.error.getErrorCode
import oolong.client.data.error.getErrorMessage
import oolong.client.media.upload.PicInfo
import oolong.client.media.upload.PicInfoItem
import oolong.client.media.upload.UploadPicture
import oolong.client.media.upload.UploadPictureException
import oolong.client.media.upload.UploadPictureResultBean
import oolong.client.media.upload.preparePictureForUpload
import oolong.client.util.MD5Util
import oolong.core.network.TiebaException
import oolong.core.network.upload.PictureChunk
import oolong.core.network.upload.PictureWatermark
import oolong.core.network.upload.UploadPictureResponse
import oolong.core.network.upload.UploadRemoteDataSource
import java.io.File
import java.io.RandomAccessFile

class ImageUploader(
    private val remote: UploadRemoteDataSource,
    private val forumName: String,
    /** Which watermark Tieba puts on the pictures: one of the PIC_WATER_TYPE_ values. */
    private val watermarkType: String,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) {
    companion object {
        const val DEFAULT_CHUNK_SIZE = 512000

        const val IMAGE_MAX_SIZE = 5242880
        const val ORIGIN_IMAGE_MAX_SIZE = 10485760

        /** The most pixels a picture drawn again for upload keeps, compressed and as the original. */
        private const val IMAGE_MAX_PIXELS = 16_000_000L
        private const val ORIGIN_IMAGE_MAX_PIXELS = 32_000_000L

        const val PIC_WATER_TYPE_NO = "0"
        const val PIC_WATER_TYPE_USER_NAME = "1"
        const val PIC_WATER_TYPE_FORUM_NAME = "2"
    }

    fun uploadImages(
        filePaths: List<String>,
        isOriginImage: Boolean = false,
    ): Flow<List<UploadPictureResultBean>> {
        return filePaths.asFlow()
            .map { filePath ->
                uploadSinglePicture(filePath, isOriginImage)
            }
            .runningFold<UploadPictureResultBean, MutableList<UploadPictureResultBean>>(initial = mutableListOf()) { list, result ->
                list.add(result)
                list
            }
            .filter { it.size == filePaths.size }
    }

    /** [filePath] made ready to upload, into a file of its own that is deleted once sent. */
    private suspend fun prepare(filePath: String, isOriginImage: Boolean): UploadPicture = withContext(Dispatchers.IO) {
        val output = File.createTempFile("upload", ".tmp")
        try {
            preparePictureForUpload(
                source = File(filePath),
                output = output,
                maxBytes = (if (isOriginImage) ORIGIN_IMAGE_MAX_SIZE else IMAGE_MAX_SIZE).toLong(),
                maxPixels = if (isOriginImage) ORIGIN_IMAGE_MAX_PIXELS else IMAGE_MAX_PIXELS,
            )
        } catch (e: Exception) {
            output.delete()
            if (e is UploadPictureException) throw UploadPictureFailedException(message = e.message.orEmpty())
            throw e
        }
    }

    suspend fun uploadSinglePicture(
        filePath: String,
        isOriginImage: Boolean = false,
    ): UploadPictureResultBean {
        val picture = prepare(filePath, isOriginImage)
        val file = picture.file
        val width = picture.width
        val height = picture.height
        val fileLength = file.length()
        val fileMd5 = MD5Util.toMd5(file)
        val isMultipleChunkSize = fileLength % chunkSize == 0L
        val totalChunkNum = fileLength / chunkSize + if (isMultipleChunkSize) 0 else 1
        val watermark = when (watermarkType) {
            PIC_WATER_TYPE_USER_NAME -> PictureWatermark.USER_NAME
            PIC_WATER_TYPE_FORUM_NAME -> PictureWatermark.FORUM_NAME
            else -> PictureWatermark.NONE
        }
        try {
            var last: UploadPictureResponse? = null
            for (chunk in 0 until totalChunkNum) {
                val isFinish = chunk == totalChunkNum - 1
                val curChunkSize = if (isFinish && !isMultipleChunkSize) {
                    fileLength % chunkSize
                } else {
                    chunkSize
                }.toInt()
                val chunkBytes = ByteArray(curChunkSize)
                withContext(Dispatchers.IO) {
                    RandomAccessFile(file, "r").use {
                        it.seek(chunk * chunkSize.toLong())
                        it.read(chunkBytes)
                    }
                }
                last = remote.uploadPictureChunk(
                    PictureChunk(
                        resourceId = "$fileMd5$chunkSize",
                        chunkNo = chunk.toInt() + 1,
                        isFinish = isFinish,
                        size = fileLength,
                        width = width,
                        height = height,
                        saveOrigin = isOriginImage,
                        watermark = watermark,
                        forumName = forumName,
                        bytes = chunkBytes.toByteString(),
                    )
                )
            }
            val result = checkNotNull(last).toUploadPictureResultBean()
            if (result.picId.isNullOrEmpty() || result.picInfo == null) {
                throw UploadPictureFailedException()
            }
            return result
        } catch (e: UploadPictureFailedException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw UploadPictureFailedException(e.getErrorCode(), e.getErrorMessage())
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                file.delete()
            }
        }
    }
}

class UploadPictureFailedException(
    override val code: Int = -1,
    override val message: String = "上传图片失败",
) : TiebaException(message)

private fun UploadPictureResponse.toUploadPictureResultBean(): UploadPictureResultBean {
    fun UploadPictureResponse.Pic?.toItem() = PicInfoItem(
        width = this?.width.orEmpty(),
        height = this?.height.orEmpty(),
        type = this?.type.orEmpty(),
        picUrl = this?.picUrl.orEmpty(),
    )
    return UploadPictureResultBean(
        errorCode = "0",
        errorMsg = "",
        resourceId = resourceId,
        chunkNo = chunkNo.orEmpty(),
        picId = picId,
        picInfo = picInfo?.let {
            PicInfo(originPic = it.originPic.toItem(), bigPic = it.bigPic.toItem(), smallPic = it.smallPic.toItem())
        },
    )
}
