package oolong.client.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import oolong.client.R
import oolong.client.data.emoticon.EmoticonUtil.emoticonString
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.media.ImageUtil
import oolong.client.model.FeedReply
import oolong.client.model.ForumSummary
import oolong.client.model.PhotoViewData
import oolong.client.model.SharedThread
import oolong.client.model.ThreadImage
import oolong.client.model.ThreadSummary
import oolong.client.model.ThreadVideo
import oolong.client.model.User
import oolong.client.navigation.transition.PageOrigin
import oolong.client.navigation.transition.pageHero
import oolong.client.navigation.transition.pageOrigin
import oolong.client.navigation.transition.rememberPageHero
import oolong.client.navigation.transition.rememberPageOrigin
import oolong.client.ui.components.PostBody
import oolong.client.ui.components.relativeTime
import oolong.client.ui.components.repliedTime
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.LocalContextMenus
import oolong.client.ui.designsystem.ProvideContentColor
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.Sizes
import oolong.client.ui.designsystem.ToggleIcon
import oolong.client.ui.designsystem.UserHeader
import oolong.client.ui.designsystem.UserHeaderPlaceholder
import oolong.client.ui.designsystem.adaptive.LocalWindowSizeClass
import oolong.client.ui.designsystem.adaptive.WindowWidthSizeClass
import oolong.client.ui.designsystem.onSecondaryClick
import oolong.client.ui.designsystem.placeholder.PlaceholderHighlight
import oolong.client.ui.designsystem.placeholder.fade
import oolong.client.ui.designsystem.placeholder.placeholder
import oolong.client.ui.designsystem.theme.CapTextScale
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.photoview.LocalPhotoViewer
import oolong.client.ui.photoview.getPhotoViewData
import oolong.client.ui.videoview.VideoCover
import oolong.client.ui.videoview.ViewerVideo
import oolong.client.util.StringUtil
import oolong.client.util.StringUtil.getShortNumString
import oolong.client.util.TiebaUtil
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape
import oolong.core.designsystem.theme.pressScale
import kotlin.math.min

/** The size of [image] to show in a card, as the image loading setting picks it. */
@Composable
private fun rememberUrl(image: ThreadImage): String {
    val context = LocalContext.current
    return remember(image, context) { ImageUtil.getUrl(context, true, image.original, image.large, image.animated, image.small) }
}

/** An author's name in a card: medium weight, between the title and the time. */
@Composable
internal fun AuthorName(text: AnnotatedString) {
    Text(text = text, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = ExtendedTheme.colors.text)
}

/** The time and other small facts under a name. */
@Composable
internal fun MetaText(text: String) {
    Text(text = text, fontSize = 12.sp, lineHeight = 16.sp, color = ExtendedTheme.colors.textSecondary)
}

@Composable
fun UserHeader(
    nameProvider: () -> String,
    nameShowProvider: () -> String,
    portraitProvider: () -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    timeProvider: (() -> Int)? = null,
    /** Whether the time is when the thread last had a reply, which says so, rather than when it was posted. */
    replied: Boolean = false,
    content: @Composable RowScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val name = remember(nameProvider) { nameProvider() }
    val nameShow = remember(nameShowProvider) { nameShowProvider() }
    val portrait = remember(portraitProvider) { portraitProvider() }
    val time = remember(timeProvider) { timeProvider?.invoke() }
    // The avatar flies into the profile it opens, which fades in around it
    val hero = rememberPageHero()
    val avatar = StringUtil.getAvatarUrl(portrait)
    UserHeader(
        avatar = {
            Avatar(
                data = avatar,
                size = Sizes.Small,
                // It opens the author's profile, so it reads as the author
                contentDescription = nameShow.ifEmpty { name },
                modifier = Modifier.pageHero(hero),
            )
        },
        name = {
            AuthorName(
                text = StringUtil.getUsernameAnnotatedString(
                    context = LocalContext.current,
                    username = name,
                    nickname = nameShow,
                    color = LocalContentColor.current
                )
            )
        },
        onClick = {
            hero.leave(avatar)
            onClick()
        },
        desc = time?.let { time ->
            @Composable { MetaText(text = if (replied) repliedTime(time.toLong()) else relativeTime(time.toLong())) }
        },
        content = content,
        modifier = modifier
    )
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
    action: @Composable (ColumnScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val cardModifier = when {
        onLongClick != null -> Modifier
            .onSecondaryClick { onLongClick() }
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
            )
        onClick != null -> Modifier.clickable(onClick = onClick)
        else -> Modifier
    }

    val paddingModifier = Modifier.padding(vertical = 16.dp)

    Column(
        modifier = cardModifier
            .then(modifier)
            .then(paddingModifier)
            .padding(contentPadding)
    ) {
        header()
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            content()
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(ActionRowTop))
            action(this)
        }
    }
}

private val ActionRowTop = 12.dp

@Composable
fun Badge(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    shape: Shape = Corners.full,
    backgroundColor: Color = Color.Black.copy(0.5f),
    contentColor: Color = Color.White,
) {
    Row(
        modifier = modifier
            .background(backgroundColor, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(12.dp)
        )
        Text(text = text, fontSize = 12.sp, color = contentColor)
    }
}

/**
 * A thread's title over its summary: the title bold and at most two lines, the summary regular
 * and at most [maxLines] lines.
 */
@Composable
fun ThreadContent(
    modifier: Modifier = Modifier,
    title: String = "",
    abstractText: String = "",
    tabName: String = "",
    showTitle: Boolean = true,
    showAbstract: Boolean = true,
    isGood: Boolean = false,
    maxLines: Int = 3,
    highlightKeywords: ImmutableList<String> = persistentListOf(),
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showTitle) {
            val goodTip = stringResource(id = R.string.tip_good)
            val accent = ExtendedTheme.colors.accent
            val titleText = remember(isGood, goodTip, accent, tabName, title) {
                buildAnnotatedString {
                    if (isGood) {
                        withStyle(style = SpanStyle(color = accent)) { append(goodTip) }
                        append(" ")
                    }
                    if (tabName.isNotBlank()) {
                        append(tabName)
                        append(" | ")
                    }
                    append(title.emoticonString)
                }
            }
            HighlightText(
                text = titleText,
                fontSize = 16.sp,
                color = ExtendedTheme.colors.text,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                // Bold in the style rather than as a parameter, so highlighted keywords stay bold
                style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold),
                highlightKeywords = highlightKeywords
            )
        }
        if (showAbstract) {
            HighlightText(
                text = remember(abstractText) { abstractText.emoticonString },
                fontSize = 15.sp,
                color = ExtendedTheme.colors.text,
                overflow = TextOverflow.Ellipsis,
                maxLines = maxLines,
                style = MaterialTheme.typography.body1,
                highlightKeywords = highlightKeywords
            )
        }
    }
}

@Composable
fun FeedCardPlaceholder() {
    Card(
        header = { UserHeaderPlaceholder(avatarSize = Sizes.Small) },
        content = {
            Text(
                text = "TitlePlaceholder",
                style = MaterialTheme.typography.subtitle1,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .placeholder(
                        visible = true,
                        highlight = PlaceholderHighlight.fade(),
                    )
            )

            Text(
                text = "Text",
                style = MaterialTheme.typography.body1,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .placeholder(
                        visible = true,
                        highlight = PlaceholderHighlight.fade(),
                    )
            )
        },
        action = {
            FeedCardActions(forum = {}) {
                repeat(3) { ActionBtnPlaceholder() }
            }
        }
    )
}

@Composable
fun ForumInfoChip(
    imageUriProvider: () -> String?,
    nameProvider: () -> String,
    onClick: () -> Unit,
) {
    val imageUri = imageUriProvider()
    val name = nameProvider()
    val press = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .pressScale(press)
            .heightIn(min = 28.dp)
            .background(color = ExtendedTheme.colors.chip, shape = SmoothCornerShape.Capsule)
            .clickable(interactionSource = press, indication = PressFeedback.overlay(SmoothCornerShape.Capsule), onClick = onClick)
            .padding(start = if (imageUri != null) 4.dp else 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        imageUri?.let {
            Avatar(
                data = imageUri,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                shape = CircleShape
            )
        }
        Text(
            text = stringResource(id = R.string.title_forum_name, name),
            style = MaterialTheme.typography.body2,
            color = ExtendedTheme.colors.onChip,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MediaPlaceholder(
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .background(ExtendedTheme.colors.chip, SmoothCornerShape(6.dp))
            .clickable(
                interactionSource = null,
                indication = PressFeedback.overlay(SmoothCornerShape(6.dp)),
                enabled = onClick != null,
            ) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProvideContentColor(color = ExtendedTheme.colors.onChip) {
            Box(
                modifier = Modifier.size(16.dp),
            ) {
                icon()
            }
            ProvideTextStyle(
                value = MaterialTheme.typography.subtitle2,
                content = text
            )
        }
    }
}

@Composable
private fun ThreadMedia(
    forumId: Long,
    forumName: String,
    threadId: Long,
    modifier: Modifier = Modifier,
    medias: ImmutableList<ThreadImage> = persistentListOf(),
    videoInfo: ThreadVideo? = null,
    /** Who posted the thread and what it says, shown under its video when it plays. */
    videoAuthor: String? = null,
    videoText: String? = null,
) {
    val context = LocalContext.current

    val mediaCount = remember(medias) {
        medias.size
    }
    val hasPhoto = remember(mediaCount) { mediaCount > 0 }
    val isSinglePhoto = remember(mediaCount) { mediaCount == 1 }

    val hideMedia = Settings.of(context)[SettingKeys.HideMedia]

    val windowWidthSizeClass = LocalWindowSizeClass.current.widthSizeClass
    val singleMediaFraction = remember(windowWidthSizeClass) {
        if (windowWidthSizeClass == WindowWidthSizeClass.Compact)
            1f
        else 0.5f
    }

    val hasMedia = remember(hasPhoto, videoInfo) {
        hasPhoto || videoInfo != null
    }

    if (hasMedia) {
        Box(modifier = modifier) {
            if (videoInfo != null) {
                if (hideMedia) {
                    MediaPlaceholder(
                        icon = {
                            Icon(
                                imageVector = Symbols.SmartDisplay,
                                contentDescription = stringResource(id = R.string.desc_video)
                            )
                        },
                        text = {
                            Text(text = stringResource(id = R.string.desc_video))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val video = remember(videoInfo, videoAuthor, videoText) {
                        ViewerVideo(
                            url = videoInfo.url,
                            cover = videoInfo.cover,
                            width = videoInfo.coverWidth,
                            height = videoInfo.coverHeight,
                            duration = videoInfo.duration,
                            author = videoAuthor,
                            text = videoText,
                        )
                    }
                    VideoCover(video = video, modifier = Modifier.fillMaxWidth(singleMediaFraction))
                }
            } else if (hasPhoto) {
                if (hideMedia) {
                    val photoViewer = LocalPhotoViewer.current
                    val photoViewData = remember(
                        medias, forumId, forumName, threadId
                    ) {
                        getPhotoViewData(
                            images = medias,
                            forumId = forumId,
                            forumName = forumName,
                            threadId = threadId,
                            index = 0
                        )
                    }
                    MediaPlaceholder(
                        icon = {
                            Icon(
                                imageVector = if (isSinglePhoto) Symbols.Photo else Symbols.PhotoLibrary,
                                contentDescription = stringResource(id = R.string.desc_photo)
                            )
                        },
                        text = {
                            Text(text = pluralStringResource(id = R.plurals.btn_open_photos, count = mediaCount, mediaCount))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { photoViewer?.open(photoViewData) }
                    )
                } else {
                    val photoViewData = { index: Int ->
                        getPhotoViewData(
                            images = medias,
                            forumId = forumId,
                            forumName = forumName,
                            threadId = threadId,
                            index = index
                        )
                    }
                    if (isSinglePhoto) {
                        SinglePhoto(
                            media = medias[0],
                            photoViewData = remember(medias, forumId, forumName, threadId) { photoViewData(0) },
                            modifier = Modifier.fillMaxWidth(singleMediaFraction),
                        )
                    } else {
                        // Equal squares, at most three, the last one counting the rest
                        Row(
                            modifier = Modifier.fillMaxWidth(if (singleMediaFraction < 1f) 0.75f else 1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val shown = medias.subList(0, min(medias.size, 3))
                            shown.fastForEachIndexed { index, media ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                ) {
                                    NetworkImage(
                                        imageUri = rememberUrl(media),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        shape = MediaShape,
                                        photoViewData = remember(index, medias, forumId, forumName, threadId) { photoViewData(index) },
                                        contentScale = ContentScale.Crop
                                    )
                                    if (index == shown.lastIndex && medias.size > shown.size) {
                                        Badge(
                                            icon = Symbols.Image,
                                            text = "${medias.size}",
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One picture at its own shape: a wide one fills the width, no taller than [SinglePhotoMaxHeight];
 * a tall one is narrower, cut to no taller than 4:3.
 */
@Composable
private fun SinglePhoto(
    media: ThreadImage,
    photoViewData: PhotoViewData,
    modifier: Modifier = Modifier,
) {
    val ratio = remember(media) {
        val width = media.width.toFloat()
        val height = media.height.toFloat()
        if (width > 0f && height > 0f && !media.isLong) width / height else 3f / 4
    }
    BoxWithConstraints(modifier = modifier) {
        val wide = ratio >= 1f
        val width = if (wide) maxWidth else maxWidth * TallPhotoWidthFraction
        val height = if (wide) minOf(width / ratio, SinglePhotoMaxHeight) else minOf(width / ratio, width * 4 / 3)
        NetworkImage(
            imageUri = rememberUrl(media),
            contentDescription = null,
            modifier = Modifier.size(width, height),
            shape = MediaShape,
            photoViewData = photoViewData,
            contentScale = ContentScale.Crop
        )
    }
}

private val MediaShape = Corners.sm
private val SinglePhotoMaxHeight = 220.dp
private const val TallPhotoWidthFraction = 0.6f

@Composable
private fun ThreadForumInfo(
    forumName: String,
    forumAvatar: String?,
    onClick: () -> Unit,
) {
    val hasForum = remember(forumName) { forumName.isNotBlank() }
    if (hasForum) {
        ForumInfoChip(
            imageUriProvider = { forumAvatar },
            nameProvider = { forumName },
            onClick = onClick
        )
    }
}

@Composable
fun ThreadReplyBtn(
    replyNum: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionBtn(
        icon = {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_comment_new),
                contentDescription = stringResource(id = R.string.desc_comment),
            )
        },
        text = {
            Text(
                text = if (replyNum == 0)
                    stringResource(id = R.string.title_reply)
                else replyNum.getShortNumString()
            )
        },
        modifier = modifier,
        onClick = onClick,
        color = ExtendedTheme.colors.textSecondary,
    )
}

@Composable
fun ThreadAgreeBtn(
    hasAgree: Boolean,
    agreeNum: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val contentColor =
        if (hasAgree) ExtendedTheme.colors.emphasis else ExtendedTheme.colors.textSecondary
    val animatedColor by animateColorAsState(contentColor, Motion.snap(), label = "agreeBtnContentColor")

    ActionBtn(
        icon = {
            ToggleIcon(
                checked = hasAgree,
                on = Symbols.FavoriteFill,
                off = Symbols.Favorite,
                contentDescription = stringResource(id = R.string.desc_like),
            )
        },
        text = {
            Refocus(targetState = agreeNum) { num ->
                Text(
                    text = if (num == 0)
                        stringResource(id = R.string.title_agree)
                    else num.getShortNumString()
                )
            }
        },
        modifier = modifier,
        color = animatedColor,
        onClick = onClick
    )
}

@Composable
fun ThreadShareBtn(
    shareNum: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionBtn(
        icon = {
            Icon(
                imageVector = Symbols.SwapCalls,
                contentDescription = stringResource(id = R.string.desc_share),
            )
        },
        text = {
            Text(
                text = if (shareNum == 0L)
                    stringResource(id = R.string.title_share)
                else shareNum.getShortNumString()
            )
        },
        modifier = modifier,
        onClick = onClick,
        color = ExtendedTheme.colors.textSecondary,
    )
}

/**
 * A thread in a list: its author and when, its title and abstract, its pictures or video, the
 * thread it shares, then its forum, shares, replies and likes.
 *
 * A reply recommended in its place shows the thread under its forum, which the feed tells of
 * instead of its author, and the reply in a block of its own, which [onClickFeedReply] opens at.
 * The feed has no counts for the thread, so the card ends there.
 */
@Composable
fun FeedCard(
    thread: ThreadSummary,
    onClick: (ThreadSummary) -> Unit,
    onAgree: (ThreadSummary) -> Unit,
    modifier: Modifier = Modifier,
    onClickReply: (ThreadSummary) -> Unit = {},
    onClickUser: (User) -> Unit = {},
    onClickForum: (ForumSummary) -> Unit = {},
    onClickShared: (SharedThread) -> Unit = {},
    onClickFeedReply: (ThreadSummary) -> Unit = onClick,
    /** Whether to show the thread's forum, which a forum's own list leaves out. */
    showForum: Boolean = true,
    /**
     * Whether to tell when the thread last had a reply, as lists ordered by replies do, rather than
     * when it was posted. A thread with no reply after it was posted tells the latter either way.
     */
    showReplyTime: Boolean = false,
    /** Long-pressing the card, at its bounds and the press in the window, with the card as an origin. */
    onLongPress: ((ThreadSummary, from: Rect, at: Offset, origin: PageOrigin) -> Unit)? = null,
    dislikeAction: @Composable () -> Unit = {},
) {
    // A long press opens the card's menu, which the pressing finger can then slide onto and lift
    val menus = LocalContextMenus.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pressAt by remember { mutableStateOf(Offset.Zero) }
    var peeking by remember { mutableStateOf(false) }
    val pressTracking = if (onLongPress == null) {
        Modifier
    } else {
        Modifier
            .onGloballyPositioned { coordinates = it }
            .pointerInput(menus) {
                awaitEachGesture {
                    // Ahead of the card's own handling, so a right click already knows where it landed
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pressAt = coordinates?.localToWindow(down.position) ?: Offset.Zero
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        val position = coordinates?.localToWindow(change.position) ?: break
                        if (!change.pressed) {
                            if (peeking) menus?.liftFinger(position)
                            peeking = false
                            break
                        }
                        if (peeking) menus?.moveFinger(position)
                    }
                }
            }
    }
    val context = LocalContext.current
    val author = thread.author
    val reply = thread.reply
    val replied = showReplyTime && thread.lastReplyTime > thread.createTime
    // The thread page opens out of the card, and closes back into it
    val origin = rememberPageOrigin()
    Card(
        header = {
            if (reply != null) {
                ForumHeader(forum = thread.forum, onClick = { onClickForum(thread.forum) }) { dislikeAction() }
            } else {
                UserHeader(
                    nameProvider = { author.name },
                    nameShowProvider = { author.nameShow },
                    portraitProvider = { author.portrait },
                    timeProvider = { (if (replied) thread.lastReplyTime else thread.createTime).toInt() },
                    replied = replied,
                    onClick = { onClickUser(author) },
                ) { dislikeAction() }
            }
        },
        content = {
            ThreadContent(
                title = thread.title,
                abstractText = thread.abstract,
                tabName = thread.tabName,
                showTitle = thread.title.isNotBlank(),
                showAbstract = thread.abstract.isNotBlank(),
                isGood = thread.isGood,
            )

            ThreadMedia(
                forumId = thread.forum.id,
                forumName = thread.forum.name,
                threadId = thread.id,
                medias = thread.images,
                videoInfo = thread.video,
                videoAuthor = author.nameShow.ifBlank { author.name },
                videoText = thread.title.ifBlank { thread.abstract },
            )

            thread.shared?.let { shared ->
                SharedThreadCard(
                    shared = shared,
                    modifier = Modifier
                        .background(ExtendedTheme.colors.inset, SmoothCornerShape(6.dp))
                        .clickable(interactionSource = null, indication = PressFeedback.overlay(SmoothCornerShape(6.dp))) { onClickShared(shared) }
                        .padding(16.dp)
                )
            }

            if (reply != null) {
                FeedReplyBlock(
                    reply = reply,
                    onClickUser = onClickUser,
                    modifier = Modifier
                        .background(ExtendedTheme.colors.inset, SmoothCornerShape(6.dp))
                        .clickable(interactionSource = null, indication = PressFeedback.overlay(SmoothCornerShape(6.dp))) { onClickFeedReply(thread) }
                        .padding(12.dp)
                )
            }
        },
        action = if (reply != null) null else ({
            FeedCardActions(
                forum = {
                    if (showForum) {
                        ThreadForumInfo(
                            forumName = thread.forum.name,
                            forumAvatar = thread.forum.avatar.ifEmpty { null },
                            onClick = { onClickForum(thread.forum) },
                        )
                    }
                }
            ) {
                ThreadShareBtn(
                    shareNum = thread.shareCount,
                    onClick = {
                        TiebaUtil.shareText(context, "https://tieba.baidu.com/p/${thread.id}", thread.title.takeIf(String::isNotBlank))
                    },
                )
                ThreadReplyBtn(
                    replyNum = thread.replyCount,
                    onClick = { onClickReply(thread) },
                )
                ThreadAgreeBtn(
                    hasAgree = thread.agree.agreed,
                    agreeNum = thread.agree.count.toInt(),
                    onClick = { onAgree(thread) },
                )
            }
        }),
        onClick = {
            origin.leave()
            onClick(thread)
        },
        onLongClickLabel = stringResource(R.string.btn_preview),
        onLongClick = onLongPress?.let { peek ->
            {
                coordinates?.let {
                    peeking = true
                    peek(thread, Rect(it.positionInWindow(), it.size.toSize()), pressAt, origin)
                }
            }
        },
        modifier = modifier.pageOrigin(origin).then(pressTracking),
    )
}

/** A card's header naming its forum, where the card has no author to name. */
@Composable
private fun ForumHeader(
    forum: ForumSummary,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    UserHeader(
        avatar = { Avatar(data = forum.avatar, size = Sizes.Small, contentDescription = null) },
        name = { AuthorName(text = AnnotatedString(stringResource(id = R.string.title_forum, forum.name))) },
        onClick = onClick,
        content = content,
    )
}

/**
 * A recommended reply under its thread: who made it and when, what it says in a few lines, and
 * its first picture small beside that.
 */
@Composable
private fun FeedReplyBlock(
    reply: FeedReply,
    onClickUser: (User) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val authorPress = remember { MutableInteractionSource() }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .pressScale(authorPress)
                    .clickable(interactionSource = authorPress, indication = PressFeedback.overlay(Corners.full)) { onClickUser(reply.author) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Avatar(data = StringUtil.getAvatarUrl(reply.author.portrait), size = ReplyAvatarSize, contentDescription = null)
                AuthorName(
                    text = StringUtil.getUsernameAnnotatedString(
                        context = context,
                        username = reply.author.name,
                        nickname = reply.author.nameShow,
                        color = LocalContentColor.current,
                    )
                )
                MetaText(text = relativeTime(reply.time))
            }
            EmoticonText(
                text = reply.text,
                fontSize = 15.sp,
                color = ExtendedTheme.colors.text,
                maxLines = ReplyMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        reply.images.firstOrNull()?.let { image ->
            NetworkImage(
                imageUri = image.small,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(ReplyImageSize),
                shape = MediaShape,
            )
        }
    }
}

private val ReplyAvatarSize = 20.dp
private val ReplyImageSize = 64.dp
private const val ReplyMaxLines = 3

/** The thread a thread shares: what it says, then its pictures or video. */
@Composable
fun SharedThreadCard(
    shared: SharedThread,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PostBody(content = shared.content)
        ThreadMedia(
            forumId = shared.forumId,
            forumName = shared.forumName,
            threadId = shared.id,
            medias = shared.images,
            videoInfo = shared.video,
        )
    }
}

/**
 * A card's last row: the thread's forum on the left, and share, reply and like on the right, the
 * last one's text lined up with the card's edge.
 */
@Composable
internal fun FeedCardActions(
    forum: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    // Sized like the bars, so the forum keeps room beside the buttons however large the text
    CapTextScale {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) { forum() }
            Row(modifier = Modifier.offset(x = ActionPadding), content = actions)
        }
    }
}

private val ActionPadding = 8.dp

/**
 * As tall as the forum chip beside them, so the row is as tall as what shows in it and the card's
 * spacing reads true. Compose widens the place a tap lands on to 48dp around each.
 */
private val ActionHeight = 28.dp

@Composable
private fun ActionBtnPlaceholder(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .height(ActionHeight)
            .padding(horizontal = ActionPadding)
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Button",
            style = MaterialTheme.typography.caption,
            modifier = Modifier
                .placeholder(
                    visible = true,
                    highlight = PlaceholderHighlight.fade(),
                ),
        )
    }
}

@Composable
private fun ActionBtn(
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    onClick: (() -> Unit)? = null,
) {
    // Pressed, the button sinks in as other small buttons do
    val press = remember { MutableInteractionSource() }
    val clickableModifier = if (onClick != null) {
        Modifier
            .pressScale(press)
            .clickable(interactionSource = press, indication = PressFeedback.overlay(SmoothCornerShape.Capsule), onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = clickableModifier
            .height(ActionHeight)
            .padding(horizontal = ActionPadding)
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        ProvideContentColor(color = color) {
            Box(modifier = Modifier.size(18.dp)) {
                icon()
            }
            Spacer(modifier = Modifier.width(4.dp))
            ProvideTextStyle(value = MaterialTheme.typography.caption.copy(fontSize = 13.sp)) {
                text()
            }
        }
    }
}

@Preview("FeedCardPreview")
@Composable
fun FeedCardPreview() {
    FeedCard(
        thread = ThreadSummary(
            id = 1,
            forum = ForumSummary(id = 1, name = "", avatar = ""),
            title = "预览",
            author = User(id = 1, name = "", nameShow = "", portrait = ""),
            createTime = System.currentTimeMillis() / 1000,
            lastReplyTime = System.currentTimeMillis() / 1000,
            abstract = "",
            firstPostId = 1,
        ),
        onClick = {},
        onAgree = {},
        modifier = Modifier.background(ExtendedTheme.colors.card),
    )
}
