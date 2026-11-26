package oolong.client.data.forum

import oolong.client.data.settings.Setting
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.model.ForumSort
import javax.inject.Inject

/** The sort type each forum's latest threads were last ordered by, or the one set in the settings. */
interface ForumSortStore {
    suspend fun sortType(forumName: String): Int

    suspend fun setSortType(forumName: String, sortType: Int)
}

/**
 * Keeps each forum's sort type in the preferences the old forum page used; the settings page
 * offers 0 (reply time) and 1 (post time) as the default.
 */
class DataStoreForumSortStore @Inject constructor(
    private val settings: Settings,
) : ForumSortStore {
    override suspend fun sortType(forumName: String): Int = settings[setting(forumName)]

    override suspend fun setSortType(forumName: String, sortType: Int) {
        settings[setting(forumName)] = sortType
    }

    private fun setting(forumName: String) =
        Setting.int("${forumName}_sort_type", settings[SettingKeys.DefaultSortType].toIntOrNull() ?: ForumSort.REPLY_TIME)
}
