package oolong.client.data.storage

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.system.Os
import android.webkit.WebView
import coil3.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oolong.client.data.offline.OfflineFiles
import oolong.client.data.update.UpdateRepository
import oolong.client.media.ImageCacheUtil
import oolong.client.ui.videoview.VideoCache
import java.io.File

/**
 * How much of the phone the app takes, as the system's app details count it, and what of it is
 * what. Each kind of cache counts only what clearing it removes: what each cache keeps to find
 * its way around stays, and counts with the rest of what the app keeps.
 */
class StorageUse(
    /** The app: its package and what the system compiled of it. */
    val app: Long,
    /** What the app keeps, its caches included. */
    val data: Long,
    val images: Long,
    val videos: Long,
    val web: Long,
    /** Cached files besides pictures, videos and web pages: what was made to send or share, and the like. */
    val temporary: Long,
    /** The models and their runtime, downloaded: part of [data]. */
    val extensions: Long,
    /** Threads saved to read offline: part of [data], not of the cache. */
    val offline: Long = 0,
) {
    val total: Long get() = app + data

    /** What the user chose to keep: saved threads. */
    val saved: Long get() = offline

    /** What clearing the cache removes. */
    val cache: Long get() = images + videos + web + temporary

    /** What is kept besides the cache, the models and saved threads: the account, settings, history. */
    val records: Long get() = (data - cache - extensions - saved).coerceAtLeast(0)
}

/** Measures and clears what the app keeps on the phone. */
object AppStorage {
    private const val VideoDir = "video"
    private const val WebDir = "WebView"
    private const val EditedDir = "edited"

    /** What [extensions] bytes of downloaded models stand beside: everything measured once. */
    suspend fun measure(context: Context, extensions: Long): StorageUse = withContext(Dispatchers.IO) {
        val (app, data, cache) = systemCounts(context) ?: ownCounts(context)
        // What the system compiled of the app it counts as cache, but nothing here clears it: it
        // is counted with the app instead, the total as the system's
        val compiled = minOf(compiledDirs(context).sumOf(::sizeOf), cache)
        StorageUse(
            app = app + compiled,
            data = data - compiled,
            images = (context.imageLoader.diskCache?.size ?: 0) + oldImageDirs(context).sumOf(::sizeOf),
            videos = VideoCache.size(context),
            web = webFiles(File(context.cacheDir, WebDir)),
            temporary = temporaryFiles(context).sumOf(::sizeOf),
            extensions = extensions,
            offline = sizeOf(OfflineFiles.root(context)),
        )
    }

    /** The pages a web view keeps under [dir]: its cache's own files, not its indexes or its other workings. */
    private fun webFiles(dir: File): Long = dir.walkTopDown()
        .filter { it.isFile && it.path.contains("Cache") && !it.name.startsWith("index") && it.name != "the-real-index" }
        .sumOf(::sizeOf)

    /** The cached files no other kind of cache owns, each a file or a folder of them. */
    private fun temporaryFiles(context: Context): List<File> {
        val owned = (imageDirs(context) + compiledDirs(context) + File(context.cacheDir, VideoDir) + File(context.cacheDir, WebDir))
            .map { it.absoluteFile }
        return listOfNotNull(context.cacheDir, context.externalCacheDir).flatMap { base ->
            base.listFiles().orEmpty().filter { it.absoluteFile !in owned }
        }
    }

    /** How much the app takes in all, quicker than [measure]. */
    suspend fun total(context: Context): Long = withContext(Dispatchers.IO) {
        val (app, data, _) = systemCounts(context) ?: ownCounts(context)
        app + data
    }

    /** The system's own counts, the same as its app details show; null where they cannot be had. */
    private fun systemCounts(context: Context): Triple<Long, Long, Long>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return runCatching {
            val manager = context.getSystemService(StorageStatsManager::class.java)
            // Asking about itself needs no permission
            val stats = manager.queryStatsForPackage(context.applicationInfo.storageUuid, context.packageName, Process.myUserHandle())
            Triple(stats.appBytes, stats.dataBytes, stats.cacheBytes)
        }.getOrNull()
    }

    /** Counted from the files, where the system will not say: close to its own. */
    private fun ownCounts(context: Context): Triple<Long, Long, Long> {
        val info = context.applicationInfo
        val app = (listOf(info.sourceDir) + info.splitSourceDirs.orEmpty()).sumOf { File(it).length() }
        val cache = listOfNotNull(context.cacheDir, context.codeCacheDir, context.externalCacheDir).sumOf(::sizeOf)
        val data = sizeOf(File(info.dataDir)) + sizeOf(context.getExternalFilesDir(null)?.parentFile)
        return Triple(app, data, cache)
    }

    private fun imageDirs(context: Context): List<File> = listOfNotNull(context.imageLoader.diskCache?.directory?.toFile()) + oldImageDirs(context)

    /** Where the image libraries used before kept their caches, left behind on updated installs. */
    private fun oldImageDirs(context: Context): List<File> = listOfNotNull(context.cacheDir, context.externalCacheDir).flatMap {
        listOf(File(it, ImageCacheUtil.DEFAULT_DISK_CACHE_DIR), File(it, "sketch3"))
    }

    private suspend fun clearImages(context: Context) = withContext(Dispatchers.IO) {
        context.imageLoader.diskCache?.clear()
        context.imageLoader.memoryCache?.clear()
        oldImageDirs(context).forEach { it.deleteRecursively() }
    }

    private suspend fun clearVideos(context: Context) = withContext(Dispatchers.IO) { VideoCache.clear(context) }

    /** Web pages' cache, through a web view: one may be open and own the files. */
    private suspend fun clearWeb(context: Context) = withContext(Dispatchers.Main) {
        runCatching { WebView(context).apply { clearCache(true) }.destroy() }
        Unit
    }

    /** Where the system keeps what it compiled of the app. */
    private fun compiledDirs(context: Context): List<File> = listOf(File(context.cacheDir, "oat_primary"), context.codeCacheDir)

    /**
     * Every other cached file: what is left of downloads, pictures made to send or share. Kept
     * only what may be in use: an update still downloading, pictures edited for a reply still
     * being written.
     */
    private suspend fun clearTemporary(context: Context) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val inUse = mapOf(
            File(context.cacheDir, UpdateRepository.UpdateDir).absoluteFile to now - RecentMillis,
            File(context.cacheDir, EditedDir).absoluteFile to now - EditedMillis,
        )
        temporaryFiles(context).forEach { deleteOlder(it, inUse[it.absoluteFile] ?: Long.MAX_VALUE) }
    }

    /**
     * What is left from before the app last started: the update package, installed by now or
     * not to be, as a download only lives as long as the app; pictures edited for a reply more
     * than a day ago, which a reply still being written does not hold on to.
     */
    fun sweep(context: Context) {
        File(context.cacheDir, UpdateRepository.UpdateDir).deleteRecursively()
        deleteOlder(File(context.cacheDir, EditedDir), System.currentTimeMillis() - EditedMillis)
    }

    suspend fun clearCache(context: Context) {
        clearImages(context)
        clearVideos(context)
        clearWeb(context)
        clearTemporary(context)
    }

    /** Everything, as the system's own button does: the app is stopped once it is gone. */
    fun clearAll(context: Context) {
        context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
    }

    private fun deleteOlder(file: File, before: Long) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteOlder(it, before) }
            if (file.listFiles().isNullOrEmpty()) file.delete()
        } else if (file.lastModified() < before) {
            file.delete()
        }
    }

    /** What [file] takes on the disk, in whole blocks as the system counts it, not its length. */
    private fun sizeOf(file: File?): Long {
        if (file == null || !file.exists()) return 0
        val own = runCatching { Os.lstat(file.path).st_blocks * 512 }.getOrElse { file.length() }
        return if (file.isDirectory) own + (file.listFiles()?.sumOf(::sizeOf) ?: 0) else own
    }

    private const val RecentMillis = 10 * 60 * 1000L
    private const val EditedMillis = 24 * 60 * 60 * 1000L
}
