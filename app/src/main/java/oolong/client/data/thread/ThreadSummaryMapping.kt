package oolong.client.data.thread

import kotlinx.collections.immutable.toImmutableList
import oolong.client.data.thread.abstractText
import oolong.client.model.Agree
import oolong.client.model.FeedReply
import oolong.client.model.ForumSummary
import oolong.client.model.SharedThread
import oolong.client.model.ThreadImage
import oolong.client.model.ThreadSummary
import oolong.client.model.ThreadVideo
import oolong.client.model.User
import oolong.client.model.UserPost
import oolong.client.model.UserReplies
import oolong.client.model.UserReply
import oolong.client.model.UserThread
import oolong.core.network.protos.Media
import oolong.core.network.protos.OriginThreadInfo
import oolong.core.network.protos.PbContent
import oolong.core.network.protos.PostInfoList
import oolong.core.network.protos.ThreadInfo
import oolong.core.network.protos.VideoInfo

/**
 * A thread as a list returned it. Feeds name the forum only in its forum info and forums only by
 * name; either way the summary has both.
 */
fun ThreadInfo.toThreadSummary(emoticons: EmoticonRegistry): ThreadSummary {
    val origin = origin_thread_info
    if (thread_type == FeedReplyType && origin != null) return toFeedReplyThread(origin, emoticons)
    return toOwnThreadSummary(emoticons)
}

private fun ThreadInfo.toOwnThreadSummary(emoticons: EmoticonRegistry) = ThreadSummary(
    id = threadId.takeIf { it != 0L } ?: id,
    forum = ForumSummary(
        id = forumInfo?.id ?: forumId,
        name = forumName.ifEmpty { forumInfo?.name.orEmpty() },
        avatar = forumInfo?.avatar.orEmpty(),
    ),
    title = title.takeIf { isNoTitle != 1 }.orEmpty(),
    author = author?.toUser() ?: User(id = authorId, name = "", nameShow = "", portrait = ""),
    createTime = createTime.toLong(),
    lastReplyTime = lastTimeInt.toLong(),
    abstract = richAbstract.abstractText(emoticons),
    firstPostId = firstPostId,
    firstPost = firstPostContent.toPostContent(emoticons),
    images = media.map { it.toThreadImage() }.toImmutableList(),
    video = videoInfo?.toThreadVideo(),
    shared = origin_thread_info?.takeIf { is_share_thread == 1 }?.toSharedThread(emoticons),
    replyCount = replyNum,
    shareCount = shareNum,
    agree = Agree(count = agreeNum.toLong(), agreed = agree?.hasAgree == 1),
    isGood = isGood == 1,
    isActivity = thread_type == ActivityThreadType,
    isTop = isTop == 1,
    tabName = tabName,
    hotNum = hotNum.toLong(),
)

/**
 * A reply a feed passes off as a thread, titled 「回复：」 and the thread's title under an id of its
 * own, which it keeps: the thread it was made in, which [origin] names with neither its author nor
 * its counts, and the reply on it.
 */
private fun ThreadInfo.toFeedReplyThread(origin: OriginThreadInfo, emoticons: EmoticonRegistry): ThreadSummary {
    val forum = ForumSummary(
        id = forumInfo?.id ?: forumId,
        name = forumName.ifEmpty { forumInfo?.name.orEmpty() },
        avatar = forumInfo?.avatar.orEmpty(),
    )
    return ThreadSummary(
        id = id,
        forum = forum,
        title = origin.title,
        author = User(id = 0, name = "", nameShow = "", portrait = ""),
        createTime = 0,
        abstract = origin._abstract.abstractText,
        firstPostId = 0,
        firstPost = firstPostContent.toPostContent(emoticons),
        images = origin.media.map { it.toThreadImage() }.toImmutableList(),
        video = origin.video_info?.toThreadVideo(),
        reply = FeedReply(
            threadId = origin.tid.toLongOrNull() ?: 0L,
            postId = post_id,
            author = author?.toUser() ?: User(id = authorId, name = "", nameShow = "", portrait = ""),
            time = createTime.toLong(),
            text = richAbstract.replyText(emoticons),
            images = media.map { it.toThreadImage() }.toImmutableList(),
        ),
    )
}

private const val FeedReplyType = 81

/**
 * A reply's text, which starts with the user it answers, if any, as `@name 回复：` and the rest:
 * written `回复 @name：` as a reply to someone reads.
 */
private fun List<PbContent>.replyText(emoticons: EmoticonRegistry): String {
    val mention = firstOrNull()?.takeIf { it.type == MentionType }?.text
    val rest = drop(if (mention != null) 1 else 0).abstractText(emoticons).trim().removePrefix("回复：").trim()
    return if (mention != null) "回复 $mention：$rest" else rest
}

private const val MentionType = 4

/** The thread type of events Tieba itself runs. */
internal const val ActivityThreadType = 41

/**
 * The abstract as one line of text: text pieces with runs of spaces collapsed, emoticons as their
 * `#(name)` codes.
 */
internal fun List<PbContent>.abstractText(emoticons: EmoticonRegistry): String = joinToString(separator = "") {
    when (it.type) {
        in TextAbstractTypes -> it.text.replace(Spaces, " ")
        EmoticonAbstractType -> {
            emoticons.register(it.text, it.c)
            "#(${it.c})"
        }

        else -> ""
    }
}

/**
 * `bigPic` is sized for the card, `srcPic` up to 1920 wide and `originPic` the upload itself. Shared
 * threads may leave some of them empty; each then falls back to the nearest one there is.
 */
internal fun Media.toThreadImage() = ThreadImage(
    small = bigPic.ifEmpty { srcPic }.ifEmpty { originPic },
    large = srcPic.ifEmpty { bigPic }.ifEmpty { originPic },
    original = originPic.ifEmpty { srcPic }.ifEmpty { bigPic },
    animated = dynamicPic,
    width = width,
    height = height,
    isLong = isLongPic == 1,
    postId = postId,
    originalSize = originSize,
    showOriginalButton = showOriginalBtn == 1,
)

internal fun VideoInfo.toThreadVideo() = ThreadVideo(
    url = videoUrl,
    cover = thumbnailUrl,
    coverWidth = thumbnailWidth,
    coverHeight = thumbnailHeight,
    duration = videoDuration.toInt(),
)

internal fun OriginThreadInfo.toSharedThread(emoticons: EmoticonRegistry) = SharedThread(
    id = tid.toLongOrNull() ?: 0L,
    forumId = fid,
    forumName = fname,
    content = content.toPostContent(emoticons),
    images = media.map { it.toThreadImage() }.toImmutableList(),
    video = video_info?.toThreadVideo(),
)

private val TextAbstractTypes = setOf(0, 18, 40)
private const val EmoticonAbstractType = 2
private val Spaces = Regex(" {2,}")

/** A thread or replies on a user's profile, as their posts list returned them. */
fun PostInfoList.toUserPost(emoticons: EmoticonRegistry): UserPost {
    val author = User(id = user_id, name = user_name, nameShow = name_show, portrait = user_portrait)
    if (is_thread != 1) {
        return UserReplies(
            threadId = thread_id,
            postId = post_id,
            threadTitle = title,
            author = author,
            replies = content.map {
                UserReply(
                    text = it.post_content.abstractText,
                    time = it.create_time,
                    postId = it.post_id,
                    isSubPost = it.post_type == 1L,
                )
            }.toImmutableList(),
        )
    }
    return UserThread(
        ThreadSummary(
            id = thread_id,
            forum = ForumSummary(id = forum_id, name = forum_name, avatar = ""),
            title = title.takeIf { is_ntitle != 1 }.orEmpty(),
            author = author,
            createTime = create_time.toLong(),
            abstract = rich_abstract.abstractText(emoticons),
            firstPostId = post_id,
            images = media.map { it.toThreadImage() }.toImmutableList(),
            video = video_info?.toThreadVideo(),
            shared = origin_thread_info?.takeIf { is_share_thread == 1 }?.toSharedThread(emoticons),
            replyCount = reply_num,
            shareCount = share_num.toLong(),
            agree = Agree(count = agree_num.toLong(), agreed = agree?.hasAgree == 1),
        ),
    )
}
