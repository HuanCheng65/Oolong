package oolong.client.navigation

import kotlinx.serialization.Serializable
import oolong.client.ui.thread.ThreadPageExtra
import oolong.client.ui.user.UserTab

/** A page the app can show, with what it needs to open. Kept small, as it is saved with the back stack. */
@Serializable
sealed interface Key

@Serializable
data object MainKey : Key

@Serializable
data class ThreadKey(
    val threadId: Long,
    val forumId: Long? = null,
    val postId: Long = 0,
    val seeLz: Boolean = false,
    val sortType: Int = 0,
    val from: String = "",
    val extra: ThreadPageExtra? = null,
    val scrollToReply: Boolean = false,
    /** Read from the copy saved to read offline. */
    val offline: Boolean = false,
) : Key

/** 沉浸阅读 of a thread: its author's floors as one text. */
@Serializable
data class ReaderKey(
    val threadId: Long,
    val forumId: Long? = null,
    /** The floor to open at: the author's floor at or before it. 0 opens at the start. */
    val floor: Int = 0,
    /** Read from the copy saved to read offline. */
    val offline: Boolean = false,
) : Key

@Serializable
data class SubPostsKey(
    val threadId: Long,
    val forumId: Long = 0L,
    val postId: Long = 0L,
    val subPostId: Long = 0L,
    val loadFromSubPost: Boolean = false,
    /** Opens at the replies rather than the post, for a post long enough to hide them. */
    val atReplies: Boolean = false,
    /** The replies the thread page shows under the post, which the hottest first start with. */
    val previewIds: List<Long> = emptyList(),
) : Key

/** [SubPostsKey] shown as a sheet over the thread. */
@Serializable
data class SubPostsSheetKey(
    val threadId: Long,
    val forumId: Long = 0L,
    val postId: Long = 0L,
    val subPostId: Long = 0L,
    val loadFromSubPost: Boolean = false,
    val atReplies: Boolean = false,
    val previewIds: List<Long> = emptyList(),
) : Key {
    fun asPage() = SubPostsKey(threadId, forumId, postId, subPostId, loadFromSubPost, atReplies, previewIds)
}

@Serializable
data class ForumKey(val forumName: String) : Key

@Serializable
data class ForumDetailKey(
    val forumId: Long,
    /** The title of the forum's rules, which its page names; null when it has none. */
    val ruleTitle: String? = null,
) : Key

@Serializable
data class ForumRuleDetailKey(val forumId: Long) : Key

@Serializable
data class ForumSearchPostKey(val forumName: String, val forumId: Long) : Key

/** User [uid]'s profile, open at [tab]. */
@Serializable
data class UserProfileKey(val uid: Long, val tab: UserTab = UserTab.Threads) : Key

/** Whom user [uid] follows, or with [fans] who follows them; [name] heads the page. */
@Serializable
data class UserFollowsKey(val uid: Long, val fans: Boolean, val name: String) : Key

@Serializable
data object EditProfileKey : Key

@Serializable
data class NotificationsKey(val initialTab: Int = 0) : Key

/** Searching, for [keyword] at once when it is given. */
@Serializable
data class SearchKey(val keyword: String = "") : Key

@Serializable
data object HistoryKey : Key

@Serializable
data object ThreadStoreKey : Key

/** The threads and replies the user liked. */
@Serializable
data object LikesKey : Key

/** The threads saved to read offline. */
@Serializable
data object OfflineThreadsKey : Key

@Serializable
data object HotTopicListKey : Key

/** A hot topic, by [id], or by [name] alone when only that is known, as in links in posts. */
@Serializable
data class TopicKey(val id: Long, val name: String) : Key

/**
 * A web page at [initialUrl]. A [task] is one opened to get something done and leave, as
 * reporting a post, which on a wide window shows as a sheet over the pages rather than a page.
 */
@Serializable
data class WebViewKey(val initialUrl: String, val task: Boolean = false) : Key

/**
 * The server's captcha at [url], asked for by a post waiting to be sent again; [request] names
 * that post, so the pass reaches the one that asked.
 */
@Serializable
data class CaptchaKey(val url: String, val request: String) : Key

@Serializable
data object LoginKey : Key

@Serializable
data object SettingsKey : Key

@Serializable
data object AccountManageKey : Key

@Serializable
data object AppThemeKey : Key

@Serializable
data object CustomSettingsKey : Key

@Serializable
data object FontSizeKey : Key

@Serializable
data object SearchPlacementKey : Key

@Serializable
data object TranslucentThemeKey : Key

@Serializable
data object HabitSettingsKey : Key

@Serializable
data object PostSettingsKey : Key

@Serializable
data object BlockSettingsKey : Key

@Serializable
data object BlockListKey : Key

@Serializable
data object DislikedForumsKey : Key

@Serializable
data object PrivacySettingsKey : Key

@Serializable
data object BlackListKey : Key

/** A block rule to edit, or a new one when [id] is 0. */
@Serializable
data class BlockRuleKey(val id: Long = 0) : Key

@Serializable
data object OKSignSettingsKey : Key

@Serializable
data object MoreSettingsKey : Key

@Serializable
data object AboutKey : Key

@Serializable
data object UpdateKey : Key

@Serializable
data object StorageKey : Key

@Serializable
data object CrashLogsKey : Key

@Serializable
data class CrashLogKey(val id: String) : Key

@Serializable
data object ErrorRecordsKey : Key

@Serializable
data object WidgetsKey : Key

@Serializable
data class ErrorRecordKey(val id: String) : Key

@Serializable
data object DiagnosticsKey : Key

/** The design system's controls on one page; debug builds only. */
@Serializable
data object ControlCatalogKey : Key
