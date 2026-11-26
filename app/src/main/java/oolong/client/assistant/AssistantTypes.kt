package oolong.client.assistant

import androidx.appfunctions.AppFunctionSerializable

/** A hot topic (热议话题) on Tieba. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantTopic(
    /** Its place on the ranking, from 1. */
    val rank: Int,
    /** The topic, as a short headline. */
    val name: String,
    /** A sentence or two on what it is about. */
    val description: String,
    /** How hot it is: a measure of how much it is discussed. */
    val discussCount: Long,
    /** Whether it is new on the ranking. */
    val isNew: Boolean,
)

/** A thread (帖子) on Tieba. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantThread(
    /** The thread's id. */
    val threadId: Long,
    /** The thread's title. */
    val title: String,
    /** The start of its first post, as text. */
    val summary: String,
    /** The forum (吧) it is in, without the 吧. */
    val forumName: String,
    /** Who posted it. */
    val author: String,
    /** How many replies it has. */
    val replyCount: Int,
    /** How many likes it has. */
    val likeCount: Long,
    /** How many people read it in the last day, when known. */
    val readers: Long?,
    /** Its address on the web, to cite or share. */
    val url: String,
)

/** A post that matched a search. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantSearchResult(
    /** The id of the thread it is in. */
    val threadId: Long,
    /** The title of the thread it is in. */
    val title: String,
    /** The matching post's text. */
    val text: String,
    /** The forum (吧) it is in, without the 吧. */
    val forumName: String,
    /** Who posted it. */
    val author: String,
    /** How many replies the thread has. */
    val replyCount: Int,
    /** When it was posted, in Unix seconds. */
    val postedAt: Long,
    /** The thread's address on the web, to cite or share. */
    val url: String,
)

/** A post (楼层) in a thread, as text. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantPost(
    /** Its floor number (楼), 1 for the first post. */
    val floor: Int,
    /** Who posted it. */
    val author: String,
    /** What it says; pictures, videos and voice are named in brackets. */
    val text: String,
    /** When it was posted, in Unix seconds. */
    val postedAt: Long,
)

/** A thread with a page of its posts. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantThreadContent(
    /** The thread. */
    val thread: AssistantThread,
    /** The thread's first post, on the first page only. */
    val firstPost: AssistantPost?,
    /** The replies on this page, in order. */
    val posts: List<AssistantPost>,
    /** This page's number, from 1. */
    val page: Int,
    /** How many pages of replies the thread has. */
    val totalPages: Int,
)

/** A forum (吧) the user follows. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantForum(
    /** The forum's name, without the 吧. */
    val name: String,
    /** The user's level (等级) in the forum. */
    val level: Int,
    /** Whether the user signed in (签到) to the forum today. */
    val signedInToday: Boolean,
)
