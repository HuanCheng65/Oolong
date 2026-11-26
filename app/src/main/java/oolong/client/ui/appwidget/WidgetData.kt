package oolong.client.ui.appwidget

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import oolong.client.arch.ApplicationScope
import oolong.client.data.account.AccountRepository
import oolong.client.data.explore.ExploreRepository
import oolong.client.data.forum.FollowedForums
import oolong.client.data.forum.ForumCursor
import oolong.client.data.forum.ForumRepository
import oolong.client.data.forum.HomeRepository
import oolong.client.model.ForumTab
import oolong.client.model.HotTopic
import oolong.client.model.RankReason
import oolong.client.model.ThreadMeasure
import oolong.core.network.sign.SignRemoteDataSource
import java.io.File
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    @ApplicationScope
    fun applicationScope(): CoroutineScope

    fun explore(): ExploreRepository

    fun forums(): ForumRepository

    fun followedForums(): FollowedForums

    fun home(): HomeRepository

    fun accounts(): AccountRepository

    fun sign(): SignRemoteDataSource
}

internal val Context.widgetEntryPoint: WidgetEntryPoint
    get() = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

/** The hot topics, as the hot topics widget shows them. */
@Serializable
internal data class TopicsContent(val loadedAt: Long, val topics: List<Item>) {
    @Serializable
    data class Item(val id: Long, val name: String, val image: String, val discussCount: Long, val tag: HotTopic.Tag)
}

/** The day's most read threads, as the thread ranking widget shows them. */
@Serializable
internal data class RankingContent(val loadedAt: Long, val threads: List<Item>) {
    @Serializable
    data class Item(val id: Long, val title: String, val forum: String, val forumAvatar: String, val readers: Long, val image: String)
}

/** Which of a forum's threads its widget shows. */
internal enum class ForumShows { Latest, Hot, Good }

/**
 * A forum's threads, as the forum widget shows them, with the viewer's standing in it: [liked],
 * [level] and [signedIn] only mean something when they follow it.
 */
@Serializable
internal data class ForumContent(
    val loadedAt: Long,
    val name: String,
    val avatar: String,
    val signedIn: Boolean,
    val threads: List<Item>,
    val id: Long = 0,
    val liked: Boolean = false,
    val level: Int = 0,
) {
    /** A thread, with [time] in seconds: its last reply among the newest, else when it was posted. */
    @Serializable
    data class Item(
        val id: Long,
        val title: String,
        val replies: Int,
        val image: String,
        val author: String = "",
        val time: Long = 0,
        val good: Boolean = false,
    )
}

/** Forums to open, as the forums widget shows them. */
@Serializable
internal data class ForumsContent(val loadedAt: Long, val forums: List<Item>) {
    @Serializable
    data class Item(val name: String, val avatar: String, val signedIn: Boolean)
}

/** Which forums the forums widget shows. */
@Serializable
internal enum class ForumsSource { Followed, Visited }

/**
 * What the widgets load. Each load is kept in a file, so a widget whose load fails, offline or
 * logged out, still shows what it last had.
 */
internal object WidgetData {
    private val json = Json { ignoreUnknownKeys = true }

    /** How many rows any widget shows at most; more are never asked for. */
    private const val MOST_ROWS = 8

    /**
     * Content to draw in place of the network's, for the still previews the app carries for
     * launchers that don't take drawn ones; set only by the debug build's WidgetPreviewExport.
     */
    interface StandIn {
        val forumName: String

        fun topics(): TopicsContent

        fun ranking(): RankingContent

        fun forum(name: String, shows: ForumShows): ForumContent

        fun forums(): ForumsContent

        fun picture(url: String, width: Int, height: Int, radius: Float): Bitmap?
    }

    /** Draws what runs inside it from [standIn]; nothing outside, such as the widgets on screen, sees it. */
    class StandInContext(val standIn: StandIn) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<StandInContext>
    }

    suspend fun standIn(): StandIn? = currentCoroutineContext()[StandInContext]?.standIn

    suspend fun topics(context: Context, fresh: Boolean): TopicsContent? = standIn()?.topics() ?: kept(context, "topics", TopicsContent.serializer(), fresh) {
        val topics = context.widgetEntryPoint.explore().topics().take(MOST_ROWS)
        TopicsContent(
            loadedAt = System.currentTimeMillis(),
            topics = topics.map { TopicsContent.Item(it.id, it.name, it.image, it.discussCount, it.tag) },
        )
    }

    suspend fun ranking(context: Context, fresh: Boolean): RankingContent? = standIn()?.ranking() ?: kept(context, "ranking", RankingContent.serializer(), fresh) {
        val threads = context.widgetEntryPoint.explore().threadRanking(ThreadMeasure.MostRead).take(MOST_ROWS)
        RankingContent(
            loadedAt = System.currentTimeMillis(),
            threads = threads.map { ranked ->
                val thread = ranked.thread
                RankingContent.Item(
                    id = thread.id,
                    title = thread.title.ifBlank { thread.abstract },
                    forum = thread.forum.name,
                    forumAvatar = thread.forum.avatar,
                    readers = (ranked.reason as? RankReason.Readers)?.count ?: 0,
                    image = thread.images.firstOrNull()?.small.orEmpty(),
                )
            },
        )
    }

    suspend fun forum(context: Context, name: String, shows: ForumShows, fresh: Boolean): ForumContent? =
        standIn()?.forum(name, shows) ?: kept(context, "forum_${name}_$shows", ForumContent.serializer(), fresh) {
            val page = context.widgetEntryPoint.forums().threads(
                forumName = name,
                forumId = 0,
                tab = when (shows) {
                    ForumShows.Latest -> ForumTab.Latest
                    ForumShows.Hot -> ForumTab.Hot
                    ForumShows.Good -> ForumTab.Good
                },
                cursor = ForumCursor.FrsPage(1),
            )
            val forum = page.forum
            ForumContent(
                loadedAt = System.currentTimeMillis(),
                name = forum?.name ?: name,
                avatar = forum?.avatar.orEmpty(),
                signedIn = forum?.signedIn == true,
                id = forum?.id ?: 0,
                liked = forum?.liked == true,
                level = forum?.level ?: 0,
                threads = page.threads.items
                    .filter { it.isTop != true }
                    .take(MOST_ROWS)
                    .map {
                        ForumContent.Item(
                            id = it.id,
                            title = it.title.ifBlank { it.abstract },
                            replies = it.replyCount,
                            image = it.images.firstOrNull()?.small.orEmpty(),
                            author = it.author.nameShow.ifEmpty { it.author.name },
                            time = if (shows == ForumShows.Latest && it.lastReplyTime > 0) it.lastReplyTime else it.createTime,
                            good = it.isGood,
                        )
                    },
            )
        }

    suspend fun forums(context: Context, source: ForumsSource, fresh: Boolean): ForumsContent? =
        standIn()?.forums() ?: kept(context, "forums_${source.name}", ForumsContent.serializer(), fresh) {
            val home = context.widgetEntryPoint.home()
            val uid = home.account.first()
            val followed = if (uid.isNotEmpty()) context.widgetEntryPoint.followedForums().query.reload(uid) else emptyList()
            val forums = when (source) {
                ForumsSource.Followed -> {
                    val pinned = home.pinnedForumIds.first()
                    followed.sortedByDescending { it.id in pinned }.map { ForumsContent.Item(it.name, it.avatar, it.signedIn) }
                }

                ForumsSource.Visited -> {
                    val signed = followed.filter { it.signedIn }.map { it.name }.toSet()
                    home.visitedForums.first().map { ForumsContent.Item(it.name, it.avatar, it.name in signed) }
                }
            }
            ForumsContent(System.currentTimeMillis(), forums.take(MOST_ROWS))
        }

    /**
     * With [fresh], loads with [load] and keeps the result, or when that fails gives what was kept
     * last time; without, only what was kept, to show at once while a fresh load runs.
     */
    private suspend fun <T> kept(context: Context, name: String, serializer: KSerializer<T>, fresh: Boolean, load: suspend () -> T): T? {
        val file = File(context.noBackupFilesDir, "appwidget/${name.hashCode().toUInt()}.json")
        val loaded = if (fresh) runCatching { load() }.getOrNull() else null
        return withContext(Dispatchers.IO) {
            if (loaded != null) {
                runCatching {
                    file.parentFile?.mkdirs()
                    file.writeText(json.encodeToString(serializer, loaded))
                }
                loaded
            } else {
                runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull()
            }
        }
    }
}
