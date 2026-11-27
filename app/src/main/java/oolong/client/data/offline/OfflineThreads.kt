package oolong.client.data.offline

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import oolong.client.arch.ApplicationScope
import oolong.client.arch.IoDispatcher
import oolong.client.data.thread.ThreadCursor
import oolong.client.data.thread.ThreadQuery
import oolong.client.database.OfflineThreadDao
import oolong.client.database.OfflineThreadEntity
import oolong.core.network.protos.pbPage.PbPageResponseData
import javax.inject.Inject
import javax.inject.Singleton

/** What a thread is saved under before its first page says more. */
data class OfflineThreadInfo(
    val threadId: Long,
    val title: String,
    val forumId: Long,
    val forumName: String,
    val authorName: String,
)

/** Threads kept to read offline: saving, updating and deleting them, and reading them back. */
@Singleton
class OfflineThreads @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: OfflineThreadDao,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Runs [block] on, whether or not the page that asked is left. */
    fun launch(block: suspend OfflineThreads.() -> Unit) {
        scope.launch { block() }
    }

    /** Every saved thread, the last saved first. */
    val all: Flow<List<OfflineThreadEntity>> = dao.all()

    fun entry(threadId: Long): Flow<OfflineThreadEntity?> = dao.flow(threadId)

    /** Starts saving [info]'s thread, every floor or with [onlyAuthor] its author's alone. */
    suspend fun save(info: OfflineThreadInfo, onlyAuthor: Boolean) {
        val now = System.currentTimeMillis()
        dao.upsert(
            OfflineThreadEntity(
                threadId = info.threadId, title = info.title, forumId = info.forumId, forumName = info.forumName,
                authorName = info.authorName, onlyAuthor = onlyAuthor, pages = 0, totalPages = 0, posts = 0,
                pictures = 0, missingPictures = 0, bytes = 0, savedAt = now, updatedAt = now,
                state = OfflineThreadEntity.Saving,
            )
        )
        enqueue(info.threadId)
    }

    /** Adds the floors written since [threadId] was saved, and the pictures missed before. */
    suspend fun update(threadId: Long) {
        val saved = dao.get(threadId) ?: return
        dao.upsert(saved.copy(state = OfflineThreadEntity.Saving))
        enqueue(threadId)
    }

    suspend fun delete(threadIds: Collection<Long>) {
        val work = WorkManager.getInstance(context)
        threadIds.forEach { work.cancelUniqueWork(workName(it)) }
        dao.delete(threadIds)
        withContext(io) {
            threadIds.forEach { OfflineFiles.threadDir(context, it).deleteRecursively() }
            OfflineFiles.reindex()
        }
        archives.withLock { threadIds.forEach(read::remove) }
    }

    private fun enqueue(threadId: Long) {
        val request = OneTimeWorkRequestBuilder<OfflineSaveWorker>()
            .setInputData(workDataOf(OfflineSaveWorker.ThreadId to threadId))
            // No network constraint: the system counts a network unvalidated (as where its check is
            // blocked) as none, and saving would wait for good; without a network it fails instead
            .addTag(WorkTag)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(threadId), ExistingWorkPolicy.REPLACE, request)
    }

    private val archives = Mutex()

    /** Saved threads read back, each with the time it was last written, read again once it changes. */
    private val read = HashMap<Long, Pair<Long, ArchivedThread>>()

    /** Whether [threadId] has pages saved to read. */
    suspend fun has(threadId: Long): Boolean = (dao.get(threadId)?.pages ?: 0) > 0

    /** The page of saved thread [query] that [cursor] asks for; null when nothing of it is saved. */
    suspend fun page(query: ThreadQuery, cursor: ThreadCursor): PbPageResponseData? {
        val archive = archive(query.threadId) ?: return null
        return archive.page(query.sort, query.seeLz, cursor)
    }

    private suspend fun archive(threadId: Long): ArchivedThread? {
        val saved = dao.get(threadId)?.takeIf { it.pages > 0 } ?: return null
        return archives.withLock {
            read[threadId]?.takeIf { it.first == saved.updatedAt }?.second ?: withContext(io) {
                val pages = OfflineFiles.pageFiles(context, threadId).mapNotNull { file ->
                    runCatching { PbPageResponseData.ADAPTER.decode(file.readBytes()) }.getOrNull()
                }
                pages.takeIf { it.isNotEmpty() }?.let(::ArchivedThread)
            }?.also { read[threadId] = saved.updatedAt to it }
        }
    }

    companion object {
        private const val WorkTag = "offline"

        internal fun workName(threadId: Long) = "offline-$threadId"
    }
}
