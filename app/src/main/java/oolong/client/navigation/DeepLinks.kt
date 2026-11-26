package oolong.client.navigation

import android.net.Uri

/**
 * The page an oolong:// link opens, or null when it names none. Such links are stored outside
 * the app, in launcher shortcuts, pinned forum shortcuts and notifications, so they must keep
 * parsing as they are: forum names in pinned shortcuts are not percent-encoded.
 */
fun Uri.toDeepLinkKey(): Key? {
    if (scheme?.lowercase() != "oolong") return null
    val argument = pathSegments.firstOrNull()
    return when (host) {
        "forum" -> argument?.takeIf { it.isNotEmpty() }?.let(::ForumKey)
        "thread" -> argument?.toLongOrNull()?.let(::ThreadKey)
        "notifications" -> NotificationsKey(argument?.toIntOrNull() ?: 0)
        "history" -> HistoryKey
        "search" -> SearchKey(getQueryParameter("keyword").orEmpty())
        "favorite" -> ThreadStoreKey
        "topics" -> HotTopicListKey
        "topic" -> argument?.toLongOrNull()?.let { TopicKey(it, getQueryParameter("name").orEmpty()) }
        else -> null
    }
}
