package oolong.client.media.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oolong.client.data.offline.OfflineFiles

/**
 * Loads [data] as a bitmap whose pixels can be read, as for picking colours from it, or null
 * when it cannot be loaded.
 */
suspend fun Context.loadBitmap(data: Any?, configure: ImageRequest.Builder.() -> Unit = {}): Bitmap? {
    val request = ImageRequest.Builder(this).data(data).allowHardware(false).apply(configure).build()
    return (imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
}

/** Loads [data] as a drawable to show outside Compose, or null when it cannot be loaded. */
suspend fun Context.loadDrawable(data: Any?, configure: ImageRequest.Builder.() -> Unit = {}): Drawable? {
    val request = ImageRequest.Builder(this).data(data).apply(configure).build()
    return (imageLoader.execute(request) as? SuccessResult)?.image?.asDrawable(resources)
}

/**
 * The image at [url] as the server sent it, for saving or sharing: from the disk cache when it
 * was shown already, downloaded into the cache otherwise. Null when it cannot be downloaded.
 */
suspend fun Context.downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
    OfflineFiles.picture(this@downloadImage, url)?.let { return@withContext it.readBytes() }
    val loader = imageLoader
    val diskCache = loader.diskCache ?: return@withContext null
    // Under the key the loader stored it by
    val key = diskCacheKey(url) ?: url
    fun cached(): ByteArray? = diskCache.openSnapshot(key)?.use { snapshot ->
        diskCache.fileSystem.read(snapshot.data) { readByteArray() }
    }
    cached() ?: run {
        // Loading it writes what the server sent to the disk cache; a small size keeps decoding cheap
        val request = ImageRequest.Builder(this@downloadImage)
            .data(url)
            .size(64)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        if (loader.execute(request) is SuccessResult) cached() else null
    }
}
