package oolong.client.data.offline

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import oolong.client.R
import oolong.client.data.emoticon.EmoticonManager
import oolong.client.database.OfflineThreadDao
import oolong.client.database.OfflineThreadEntity
import oolong.client.media.image.downloadImage
import oolong.client.util.StringUtil
import oolong.core.network.TiebaServerException
import oolong.core.network.protos.pbPage.PbPageResponseData
import oolong.core.network.thread.ThreadRemoteDataSource
import java.io.File
import java.io.IOException

/**
 * Saves a thread's pages one after another, each page's pictures right after it while their
 * addresses' signatures are fresh. A thread saved before is updated: from its last page on, or from
 * the start when pictures were missed, whose addresses have expired since.
 */
class OfflineSaveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun threadRemote(): ThreadRemoteDataSource
        fun offlineThreadDao(): OfflineThreadDao
    }

    private val dependencies = EntryPointAccessors.fromApplication(context, Dependencies::class.java)
    private val remote = dependencies.threadRemote()
    private val dao = dependencies.offlineThreadDao()

    override suspend fun doWork(): Result {
        val threadId = inputData.getLong(ThreadId, 0)
        val saved = dao.get(threadId) ?: return Result.success()
        try {
            setForeground(foregroundInfo(saved, 0, 0))
        } catch (_: IllegalStateException) {
            // Not allowed in front from the background: saving goes on without the notification
        }
        return try {
            save(saved)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: TiebaServerException) {
            // The thread is gone, or can't be read any more: what was saved stays
            dao.get(threadId)?.let { dao.upsert(it.copy(state = if (it.pages > 0) OfflineThreadEntity.Gone else OfflineThreadEntity.Failed)) }
            Result.success()
        } catch (_: IOException) {
            dao.get(threadId)?.let { dao.upsert(it.copy(state = OfflineThreadEntity.Failed)) }
            Result.success()
        }
    }

    private suspend fun save(start: OfflineThreadEntity) {
        val context = applicationContext
        val threadId = start.threadId
        // Pages saved before but missing pictures are saved again, with fresh addresses
        val first = if (start.missingPictures > 0 || start.pages == 0) 1 else start.pages
        var entry = start
        var page = first
        var missing = 0
        while (true) {
            val data = remote.page(threadId = threadId, page = page, seeLz = start.onlyAuthor)
            val file = OfflineFiles.pageFile(context, threadId, page)
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                file.writeBytes(data.encode())
            }
            val pictures = data.pictureUrls(StringUtil::getAvatarUrl)
            missing += savePictures(threadId, pictures)
            // Emoticons are kept apart from the cache, where clearing it would take them from the thread
            data.emoticonIds().forEach { EmoticonManager.keep(context, it) }
            val total = data.page?.total_page?.coerceAtLeast(page) ?: page
            entry = entry.copy(
                title = data.thread?.title?.ifEmpty { null } ?: entry.title,
                forumId = data.forum?.id ?: entry.forumId,
                forumName = data.forum?.name?.ifEmpty { null } ?: entry.forumName,
                authorName = data.thread?.author?.let { it.nameShow.ifEmpty { it.name } }?.ifEmpty { null } ?: entry.authorName,
                pages = maxOf(entry.pages, page),
                totalPages = total,
            )
            dao.upsert(entry)
            setProgressNotification(entry, page, total)
            if (data.page?.has_more == 0 || page >= total) break
            page++
            delay(PageInterval)
        }
        // Pages past the last one found, left from a longer save of a thread since cut, would read twice
        OfflineFiles.pageFiles(context, threadId).drop(page).forEach(File::delete)
        val totals = withContext(Dispatchers.IO) {
            val pages = OfflineFiles.pageFiles(context, threadId).mapNotNull {
                runCatching { PbPageResponseData.ADAPTER.decode(it.readBytes()) }.getOrNull()
            }
            val posts = if (pages.isEmpty()) 0 else ArchivedThread(pages).floorCount
            val pictures = OfflineFiles.picturesDir(context, threadId).list()?.size ?: 0
            Triple(posts, pictures, OfflineFiles.sizeOf(OfflineFiles.threadDir(context, threadId)))
        }
        dao.upsert(
            entry.copy(
                pages = page,
                posts = totals.first,
                pictures = totals.second,
                missingPictures = missing,
                bytes = totals.third,
                updatedAt = System.currentTimeMillis(),
                state = OfflineThreadEntity.Saved,
            )
        )
    }

    /** Saves the [urls] not saved yet, a few at a time; returns how many could not be. */
    private suspend fun savePictures(threadId: Long, urls: List<String>): Int = coroutineScope {
        val context = applicationContext
        val dir = OfflineFiles.picturesDir(context, threadId)
        val permits = Semaphore(PicturesAtOnce)
        urls.map { url ->
            async {
                val name = OfflineFiles.nameOf(url) ?: return@async 0
                val file = File(dir, OfflineFiles.fileName(name))
                if (file.exists()) return@async 0
                permits.withPermit {
                    val bytes = context.downloadImage(url) ?: return@withPermit 1
                    withContext(Dispatchers.IO) {
                        dir.mkdirs()
                        val partial = File(dir, file.name + ".part")
                        partial.writeBytes(bytes)
                        partial.renameTo(file)
                    }
                    OfflineFiles.register(context, name, file)
                    0
                }
            }
        }.awaitAll().sum()
    }

    private suspend fun setProgressNotification(entry: OfflineThreadEntity, page: Int, total: Int) {
        try {
            setForeground(foregroundInfo(entry, page, total))
        } catch (_: IllegalStateException) {
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val saved = dao.get(inputData.getLong(ThreadId, 0))
        return foregroundInfo(saved, 0, 0)
    }

    private fun foregroundInfo(entry: OfflineThreadEntity?, page: Int, total: Int): ForegroundInfo {
        val context = applicationContext
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(ChannelId, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.offline_channel))
                .setShowBadge(false)
                .build(),
        )
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_launcher_new_monochrome)
            .setContentTitle(context.getString(R.string.offline_saving))
            .setContentText(entry?.title)
            .setProgress(total, page, total == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val id = NotificationBase + ((entry?.threadId ?: 0) % 10_000).toInt()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    companion object {
        const val ThreadId = "thread_id"
        private const val ChannelId = "offline"
        private const val NotificationBase = 0x7000
        private const val PageInterval = 400L
        private const val PicturesAtOnce = 4
    }
}
