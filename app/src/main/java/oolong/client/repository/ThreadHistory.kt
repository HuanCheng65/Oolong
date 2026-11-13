package oolong.client.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import oolong.client.Incognito
import oolong.client.arch.ApplicationScope
import oolong.client.database.HistoryDao
import oolong.client.database.HistoryEntity
import oolong.client.model.Post
import oolong.client.model.PostContent
import oolong.client.model.ThreadDetail
import oolong.client.models.ThreadHistoryInfoBean
import oolong.client.utils.StringUtil
import javax.inject.Inject

/** The browsing history of threads, which remembers where in a thread the user was. */
fun interface ThreadHistory {
    /** Remembers a visit to [thread], read up to [readUpTo]; [firstPost] gives it a picture if it has one. */
    fun save(thread: ThreadDetail, seeLz: Boolean, readUpTo: Post?, firstPost: Post?)
}

class DatabaseThreadHistory @Inject constructor(
    private val dao: HistoryDao,
    private val owner: HistoryOwner,
    @ApplicationScope private val scope: CoroutineScope,
) : ThreadHistory {
    override fun save(thread: ThreadDetail, seeLz: Boolean, readUpTo: Post?, firstPost: Post?) {
        if (!Incognito.recording) return
        val entry = HistoryEntity(
            owner = owner.uid.value,
            type = HistoryEntity.TYPE_THREAD,
            data = thread.id.toString(),
            title = thread.title,
            extras = Json.encodeToString(
                ThreadHistoryInfoBean(
                    isSeeLz = seeLz,
                    pid = readUpTo?.id?.toString(),
                    forumName = thread.forum.name,
                    floor = readUpTo?.floor?.toString(),
                    thumbnail = thumbnail(thread, firstPost),
                    forumAvatar = thread.forum.avatar.ifEmpty { null },
                ),
            ),
            avatar = StringUtil.getAvatarUrl(thread.author.portrait),
            username = thread.author.nameShow,
        )
        scope.launch { dao.visit(entry, System.currentTimeMillis()) }
    }
}

/** The thread's first picture, not a sticker, or its video's cover; null when it has neither. */
private fun thumbnail(thread: ThreadDetail, firstPost: Post?): String? =
    thread.summary.images.firstOrNull()?.small?.ifEmpty { null }
        ?: firstPost?.content?.firstNotNullOfOrNull { content ->
            (content as? PostContent.Image)?.takeUnless { it.sticker }?.urls?.lastOrNull { it.isNotEmpty() }
        }
        ?: thread.summary.video?.cover?.ifEmpty { null }
