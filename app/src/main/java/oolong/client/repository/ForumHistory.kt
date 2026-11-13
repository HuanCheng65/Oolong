package oolong.client.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import oolong.client.Incognito
import oolong.client.R
import oolong.client.arch.ApplicationScope
import oolong.client.database.HistoryDao
import oolong.client.database.HistoryEntity
import oolong.client.model.ForumDetail
import oolong.client.models.ForumHistoryExtra
import javax.inject.Inject

/** The browsing history of forums. */
fun interface ForumHistory {
    fun save(forum: ForumDetail)
}

class DatabaseForumHistory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: HistoryDao,
    private val owner: HistoryOwner,
    @ApplicationScope private val scope: CoroutineScope,
) : ForumHistory {
    override fun save(forum: ForumDetail) {
        if (!Incognito.recording) return
        val entry = HistoryEntity(
            owner = owner.uid.value,
            type = HistoryEntity.TYPE_FORUM,
            data = forum.name,
            title = context.getString(R.string.title_forum, forum.name),
            extras = Json.encodeToString(ForumHistoryExtra(forum.id)),
            avatar = forum.avatar,
        )
        scope.launch { dao.visit(entry, System.currentTimeMillis()) }
    }
}
