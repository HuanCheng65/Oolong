package oolong.client.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A thread kept to read offline: what its list shows, and how far saving it got. */
@Entity(tableName = "offline_threads")
data class OfflineThreadEntity(
    @PrimaryKey @ColumnInfo(name = "thread_id") val threadId: Long,
    val title: String,
    @ColumnInfo(name = "forum_id") val forumId: Long,
    @ColumnInfo(name = "forum_name") val forumName: String,
    @ColumnInfo(name = "author_name") val authorName: String,
    /** Whether only the author's floors were saved. */
    @ColumnInfo(name = "only_author") val onlyAuthor: Boolean,
    /** Pages saved so far, and of how many. */
    val pages: Int,
    @ColumnInfo(name = "total_pages") val totalPages: Int,
    /** Floors saved, the first floor not counted. */
    val posts: Int,
    val pictures: Int,
    /** Pictures that could not be saved, tried again on the next update. */
    @ColumnInfo(name = "missing_pictures") val missingPictures: Int,
    val bytes: Long,
    @ColumnInfo(name = "saved_at") val savedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** One of [OfflineThreadEntity.Saving] and the rest. */
    val state: Int,
) {
    companion object {
        const val Saving = 0
        const val Saved = 1

        /** Saving stopped before it finished; what was saved is kept. */
        const val Failed = 2

        /** The thread is gone from the forum; what was saved is kept. */
        const val Gone = 3
    }
}

@Dao
interface OfflineThreadDao {
    @Query("SELECT * FROM offline_threads ORDER BY saved_at DESC")
    fun all(): Flow<List<OfflineThreadEntity>>

    @Query("SELECT * FROM offline_threads WHERE thread_id = :threadId")
    fun flow(threadId: Long): Flow<OfflineThreadEntity?>

    @Query("SELECT * FROM offline_threads WHERE thread_id = :threadId")
    suspend fun get(threadId: Long): OfflineThreadEntity?

    @Upsert
    suspend fun upsert(thread: OfflineThreadEntity)

    @Query("DELETE FROM offline_threads WHERE thread_id IN (:threadIds)")
    suspend fun delete(threadIds: Collection<Long>)
}
