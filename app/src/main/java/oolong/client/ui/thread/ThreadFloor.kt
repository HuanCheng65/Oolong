package oolong.client.ui.thread

import android.content.Context
import android.graphics.Typeface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.media.ImageUtil
import oolong.client.model.PhotoViewData
import oolong.client.model.PicItem
import oolong.client.model.Post
import oolong.client.model.PostContent
import oolong.client.model.SubPost
import oolong.client.ui.components.BlockTip
import oolong.client.ui.components.BlockableContent
import oolong.client.ui.components.PbContentText
import oolong.client.ui.components.PostBody
import oolong.client.ui.components.PostImage
import oolong.client.ui.components.buildChipInlineContent
import oolong.client.ui.components.relativeTime
import oolong.client.ui.components.rememberSubPostText
import oolong.client.ui.components.shownIpLocation
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.Chip
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.Reveal
import oolong.client.ui.designsystem.ToggleIcon
import oolong.client.ui.designsystem.condense
import oolong.client.ui.designsystem.longPressMenu
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.designsystem.trimToCharacters
import oolong.client.ui.photoview.LocalPhotoFloors
import oolong.client.ui.photoview.LocalPhotoViewer
import oolong.client.ui.settings.block.LocalBlockShortcuts
import oolong.client.util.StringUtil
import oolong.client.util.StringUtil.getShortNumString
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape
import oolong.core.designsystem.theme.TextStyles
import oolong.core.designsystem.theme.pressScale

/** The column left of a floor that holds its author's avatar, floor number and rule. */
private val Gutter = 52.dp
private val GutterStart = 4.dp
private val FloorAvatarSize = 36.dp

/**
 * The room above a floor's first line and below its last thing, together the 32dp between floors:
 * twice the widest gap inside a floor, so floors part clearly while what belongs together stays
 * together. Whatever ends a floor (its time, a reply, a link row, the capsule or block of replies)
 * leaves exactly [FloorBottom] under it. Gaps inside a floor run 8, 12 and 16dp, each wider than
 * the space between two lines of the body, and are measured between characters (see
 * [trimToCharacters]), as they are seen.
 */
private val FloorTop = 16.dp
private val FloorBottom = 16.dp

/** Under each reply, before the next. */
private val ReplyGap = 16.dp

/** Between a floor's body and its time, which belongs to it. */
private val BodyToTime = 12.dp

/** From a floor's time to the replies under it, as between replies. */
private val ToReplies = 16.dp

/** From a reply's last line to its time. */
private val ReplyToTime = 8.dp

/** Between a floor's name and its body: they read as one, so closer than anything else in it. */
private val NameToBody = 8.dp

private val SubPostAvatarSize = 22.dp

/** A reply's picture: at most this share of the text's width, and no taller than the cap. */
private const val ReplyImageWidthFraction = 0.6f
private val ReplyImageMaxHeight = 240.dp

/** The rule down a floor and its branches: thin, and a light wash of the text colour. */
private val RuleWidth = 1.25.dp
private const val RuleAlpha = 0.16f
private val BranchRadius = 12.dp

private val ClassicBlockShape = SmoothCornerShape(12.dp)

/** The capsule a floor's replies fold into: its height, and its avatars, how many and how far over each other. */
private val CapsuleHeight = 30.dp
private const val CapsuleAvatars = 3
private val CapsuleAvatarOverlap = 7.dp
private const val ClassicReplyLines = 3

/** A reply in a floor longer than this many lines folds to [FoldToLines]. */
private const val FoldOverLines = 8
private const val FoldToLines = 6

/** Space kept above the end of a floor when its avatar sticks below the bar. */
private val StickyEndGap = 40.dp

/**
 * A reply of the thread, as a floor: its author's avatar and floor number in a gutter on the left,
 * with a rule down the gutter that runs on into the next floor's avatar, and the post beside it.
 * Replies inside the post hang off the rule on short branches.
 *
 * While a long floor scrolls under the top bar, its avatar and floor number stay just below the
 * bar, so it stays clear whose floor is being read, until the end of the floor pushes them away.
 *
 * @param listState the list the floor is in, to keep the avatar in view
 * @param key the floor's key in that list
 * @param stickBelow where the visible top chrome ends, in pixels from the end of the list's top
 * padding, for the avatar to stick below
 * @param startsRule whether this is the first floor of the list, where the rule starts at the avatar
 */
@Composable
fun ThreadFloor(
    item: PostItem,
    options: PostCardOptions,
    onEvent: (ThreadEvent) -> Unit,
    listState: LazyListState,
    key: Any,
    modifier: Modifier = Modifier,
    stickBelow: () -> Int = { 0 },
    startsRule: Boolean = false,
    isStoreMark: Boolean = false,
    canDelete: Boolean = false,
    photoViewData: (PostContent.Image) -> PhotoViewData? = { null },
    /** Whether the floor's menu offers storing the thread up to it. */
    canStore: Boolean = true,
    /** More of the replies inside it, once opened in place; null where they open on their own page. */
    replies: FloorReplies? = null,
    /** Whether its replies fold into a capsule, as in a thread; not in a sample of how floors look. */
    collapsible: Boolean = false,
) {
    val post = item.post
    val context = LocalContext.current
    val shortcuts = LocalBlockShortcuts.current
    val labels = postMenuLabels()
    val rule = floorRuleColor()
    val style = rememberSubPostStyle()
    // Each floor folds and opens on its own; the setting is only where it starts
    val collapsedByDefault = rememberSubPostsCollapsed()
    var collapsed by rememberSaveable(post.id, collapsedByDefault) { mutableStateOf(collapsible && collapsedByDefault) }
    var floorHeight by remember { mutableIntStateOf(0) }
    var badgeHeight by remember { mutableIntStateOf(0) }
    // Where the replies sit, for the branches to reach them, by reply id
    var floorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val branches = remember { mutableStateMapOf<Long, LayoutCoordinates>() }
    val replyCenter = replyLineCenter()
    val hasReplies = post.subPostCount > 0 && post.subPosts.isNotEmpty()
    BlockableContent(
        block = item.block,
        tip = { reason, onShow ->
            BlockTip(
                reason = reason,
                onShow = onShow,
                label = stringResource(id = R.string.block_tip_floor, post.floor),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { floorHeight = it.height }
                .onPlaced { floorCoordinates = it },
        ) {
            // The classic style keeps replies in a block of their own, with no rule to hang them off
            if (style == SubPostStyle.Timeline) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val x = (GutterStart + Gutter / 2).toPx()
                    val top = if (startsRule) (FloorTop + FloorAvatarSize / 2).toPx() else 0f
                    val floor = floorCoordinates
                    // One path, so a branch leaving the rule doesn't paint over it a second time
                    val path = Path().apply {
                        moveTo(x, top)
                        lineTo(x, size.height)
                        if (floor != null && floor.isAttached) {
                            branches.values.forEach { reply ->
                                if (reply.isAttached) {
                                    val y = floor.localPositionOf(reply, Offset.Zero).y + replyCenter.toPx()
                                    branch(x, y, (GutterStart + Gutter).toPx(), BranchRadius.toPx())
                                }
                            }
                        }
                    }
                    drawPath(path, rule, style = Stroke(RuleWidth.toPx()))
                }
            }
            Column(modifier = Modifier.padding(start = GutterStart, end = 16.dp)) {
                Box(
                    modifier = Modifier.longPressMenu(
                        items = { postMenuItems(labels, context, post, options.hideReply, isStoreMark, canStore, canDelete, shortcuts, onEvent) },
                        onClick = { onEvent(ThreadEvent.Reply(post)) }.takeIf { !options.hideReply },
                    ),
                ) {
                    Row {
                        Spacer(modifier = Modifier.width(Gutter))
                        FloorBody(post, options, isStoreMark, photoViewData, onEvent, endsFloor = !hasReplies)
                    }
                }
                if (hasReplies) {
                    // Full width, so only the height moves between the capsule and the replies: the
                    // space opens on settle while the replies condense into it, and closes as they fade
                    val shows = when {
                        collapsed -> SubPostsShown.Capsule
                        style == SubPostStyle.Timeline -> SubPostsShown.Timeline
                        else -> SubPostsShown.Classic
                    }
                    AnimatedContent(
                        targetState = shows,
                        modifier = Modifier.fillMaxWidth(),
                        transitionSpec = {
                            EnterTransition.None togetherWith fadeOut(Motion.fadeOut()) using
                                SizeTransform(clip = true) { _, _ -> Motion.settle(IntSize.VisibilityThreshold) }
                        },
                        contentAlignment = Alignment.TopStart,
                        label = "subPosts",
                    ) { now ->
                        val focus = transition.animateFloat(transitionSpec = { Motion.settle(Motion.ProgressThreshold) }, label = "subPostsFocus") {
                            if (it == EnterExitState.PreEnter) 0f else 1f
                        }
                        Box(modifier = Modifier.fillMaxWidth().condense({ focus.value }, blur = SubPostsBlur, scale = 0.98f, fadeFaster = 2.5f)) {
                            when (now) {
                                // On the timeline the replies open in place; the classic block goes to their page
                                SubPostsShown.Capsule -> SubPostsCapsule(
                                    item = item,
                                    opensInPlace = style == SubPostStyle.Timeline,
                                    onClick = {
                                        if (style == SubPostStyle.Timeline) {
                                            collapsed = false
                                        } else {
                                            onEvent(ThreadEvent.OpenSubPosts(post, subPostId = 0))
                                        }
                                    },
                                )
                                SubPostsShown.Timeline -> TimelineSubPosts(
                                    item = item,
                                    replies = replies,
                                    options = options,
                                    onEvent = onEvent,
                                    onPlaced = { id, coordinates -> if (branches[id] !== coordinates) branches[id] = coordinates },
                                    onGone = { id -> branches.remove(id) },
                                    onCollapse = if (!collapsible) null else ({
                                        // Back at the floor's top when its replies had carried the page past it
                                        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                                        if (info != null && info.index == listState.firstVisibleItemIndex && listState.firstVisibleItemScrollOffset > 0) {
                                            coroutineScope.launch { listState.animateScrollToItem(info.index) }
                                        }
                                        collapsed = true
                                        onEvent(ThreadEvent.ShowFewerSubPosts(post.id))
                                    }),
                                )
                                SubPostsShown.Classic -> ClassicSubPosts(item, options, onEvent)
                            }
                        }
                    }
                }
            }
            FloorBadge(
                post = post,
                onClick = { onEvent(ThreadEvent.OpenUser(post.author.id)) },
                modifier = Modifier
                    .padding(start = GutterStart, top = FloorTop)
                    .width(Gutter)
                    .offset {
                        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                        val room = floorHeight - badgeHeight - StickyEndGap.roundToPx()
                        IntOffset(0, if (info == null) 0 else (stickBelow() - info.offset).coerceIn(0, room.coerceAtLeast(0)))
                    }
                    .onSizeChanged { badgeHeight = it.height },
            )
        }
    }
}

/**
 * The avatar with the floor number on a tab across its lower edge. A long number widens the tab
 * past the avatar, up to the gutter, and past that gets smaller, keeping to one line.
 */
@Composable
private fun FloorBadge(post: Post, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background = ExtendedTheme.colors.background
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Box(contentAlignment = Alignment.BottomCenter) {
            // Pressed, the avatar sinks in as other small buttons do
            val press = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .pressScale(press)
                    .clip(CircleShape)
                    .background(background)
                    .clickable(interactionSource = press, indication = PressFeedback.overlay(CircleShape), onClick = onClick)
                    .padding(bottom = 8.dp),
            ) {
                Avatar(
                    data = StringUtil.getAvatarUrl(post.author.portrait),
                    size = FloorAvatarSize,
                    // It opens the author's profile, so it reads as the author
                    contentDescription = post.author.nameShow.ifEmpty { post.author.name },
                )
            }
            // Outside the avatar's clip, so it can be wider than the avatar
            Box(
                modifier = Modifier
                    .widthIn(max = Gutter)
                    .background(background, SmoothCornerShape.Capsule)
                    .padding(2.dp)
                    .background(ExtendedTheme.colors.chip, SmoothCornerShape.Capsule)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = stringResource(R.string.floor_short, post.floor),
                    style = TextStyle(
                        color = ExtendedTheme.colors.textSecondary,
                        fontFamily = rememberNumeralFamily(),
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
                )
            }
        }
    }
}

@Composable
private fun FloorBody(
    post: Post,
    options: PostCardOptions,
    isStoreMark: Boolean,
    photoViewData: (PostContent.Image) -> PhotoViewData?,
    onEvent: (ThreadEvent) -> Unit,
    endsFloor: Boolean,
) {
    val context = LocalContext.current
    val author = post.author
    Column(modifier = Modifier.fillMaxWidth().padding(top = FloorTop, bottom = if (endsFloor) FloorBottom else 0.dp)) {
        // The name's characters start level with the avatar's top, and the body follows close under
        // them: the avatar marks where the floor starts, the name heads the body
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProvideTextStyle(
                MaterialTheme.typography.subtitle1.copy(
                    color = ExtendedTheme.colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            ) {
                UserNameText(
                    userName = StringUtil.getUsernameAnnotatedString(context, author.name, author.nameShow),
                    userLevel = author.level,
                    isLz = author.id == options.threadAuthorId,
                    bawuType = author.bawuName(),
                    modifier = Modifier
                        .clickable { onEvent(ThreadEvent.OpenUser(author.id)) }
                        .trimToCharacters(15.sp),
                )
            }
        }
        // Stored up to here, the mark opens in place
        Reveal(Unit.takeIf { isStoreMark }) {
            Chip(
                text = stringResource(id = R.string.title_collected_floor),
                invertColor = true,
                prefixIcon = {
                    Icon(imageVector = Symbols.StarFill, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(NameToBody))
        PostBody(content = post.content, photoViewData = photoViewData, fontSize = 16.sp, trimEdges = true)
        Spacer(modifier = Modifier.height(BodyToTime))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val style = MaterialTheme.typography.caption
            Text(
                text = listOfNotNull(
                    relativeTime(post.time),
                    shownIpLocation(author.ipLocation),
                ).joinToString(" · "),
                style = style,
                color = ExtendedTheme.colors.textSecondary,
                modifier = Modifier.weight(1f).trimToCharacters(style.fontSize),
            )
            // The button pads its icon; shift it so the icon lines up with the text's right edge
            PostAgreeBtn(
                hasAgreed = post.agree.agreed,
                agreeNum = post.agree.count,
                onClick = { onEvent(ThreadEvent.AgreePost(post.id)) },
                modifier = Modifier.offset(x = 8.dp).overhanging(),
            )
        }
    }
}

/**
 * The replies inside a post, each on a branch off the floor's rule: those the page brought, or as
 * many as [replies] has opened, then a link to open more in place, and one to fold them all.
 */
@Composable
private fun TimelineSubPosts(
    item: PostItem,
    replies: FloorReplies?,
    options: PostCardOptions,
    onEvent: (ThreadEvent) -> Unit,
    onPlaced: (Long, LayoutCoordinates) -> Unit,
    onGone: (Long) -> Unit,
    onCollapse: (() -> Unit)?,
) {
    val post = item.post
    val context = LocalContext.current
    val shortcuts = LocalBlockShortcuts.current
    val labels = postMenuLabels()
    val subPosts = replies?.subPosts ?: post.subPosts
    val blocks = replies?.blocks ?: item.subPostBlocks
    val remaining = if (replies?.complete == true) 0 else (post.subPostCount - subPosts.size).coerceAtLeast(0)
    // With nothing else to say under the replies, the link to fold them ends the last one's line
    // rather than taking a row of its own, unless the rules fold that reply away
    val linkRow = replies?.loading == true || replies?.failed == true || remaining > 0
    val collapseOnLast = onCollapse != null && !linkRow && subPosts.lastOrNull()?.let { blocks[it.id] == null } == true
    val endsOnReply = !linkRow && (onCollapse == null || collapseOnLast)
    // A reply leaves the room between replies under it, as much as ends a floor
    Column(modifier = Modifier.padding(top = ToReplies)) {
        subPosts.forEach { subPost ->
            key(subPost.id) {
                DisposableEffect(subPost.id) { onDispose { onGone(subPost.id) } }
                BlockableContent(
                    block = blocks[subPost.id],
                    tip = { reason, onShow ->
                        BlockTip(reason = reason, onShow = onShow, boxed = false, modifier = Modifier.padding(start = Gutter, end = 16.dp, bottom = 12.dp))
                    },
                ) {
                    Row(modifier = Modifier.onPlaced { onPlaced(subPost.id, it) }) {
                        Spacer(modifier = Modifier.width(Gutter))
                        SubPostLine(
                            subPost = subPost,
                            isLz = subPost.author.id == options.threadAuthorId,
                            onClick = { onEvent(ThreadEvent.OpenSubPosts(post, subPost.id)) },
                            menuItems = { subPostMenuItems(labels, context, post, subPost, options.hideReply, shortcuts, onEvent) },
                            foldLong = true,
                            photoViewData = { image -> replyPhotoViewData(context, post, image) },
                            imageLinks = true,
                            trailing = if (collapseOnLast && subPost === subPosts.last()) {
                                { CollapseLink(onCollapse!!) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
        // The last reply's own bottom space then parts it from the next floor, as wide as between floors
        if (endsOnReply) return@Column
        val showMore = { onEvent(ThreadEvent.ShowMoreSubPosts(post)) }.takeIf { replies?.loading != true && (replies?.failed == true || remaining > 0) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(bottom = FloorBottom)
                .fillMaxWidth(),
        ) {
            // The whole row up to 收起 opens more, not only its words
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Gutter)
                    .then(if (showMore != null) Modifier.linkPress(showMore) else Modifier),
            ) {
                val link = when {
                    replies?.loading == true -> stringResource(id = R.string.text_loading) to ExtendedTheme.colors.textSecondary
                    replies?.failed == true -> stringResource(id = R.string.sub_posts_load_failed) to ExtendedTheme.colors.accent
                    remaining > 0 -> pluralStringResource(id = R.plurals.show_more_sub_posts, count = remaining, remaining) to ExtendedTheme.colors.accent
                    else -> null
                }
                // Loading, failing and the count left refocus into one another in place
                Refocus(targetState = link, contentAlignment = Alignment.CenterStart) { shown ->
                    if (shown != null) SubPostsLink(text = shown.first, color = shown.second)
                }
            }
            if (onCollapse != null) CollapseLink(onCollapse, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** The link that folds a floor's replies back into their capsule. */
@Composable
private fun CollapseLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SubPostsLink(
        text = stringResource(id = R.string.button_show_fewer_sub_posts),
        color = ExtendedTheme.colors.textSecondary,
        weight = FontWeight.Normal,
        modifier = modifier.linkPress(onClick),
    )
}

/** A link's words under a floor's replies, in the row lined up with their avatars. */
@Composable
private fun SubPostsLink(text: String, color: Color, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.SemiBold) {
    Text(text = text, style = TextStyles.caption, fontWeight = weight, color = color, modifier = modifier.trimToCharacters(TextStyles.caption.fontSize))
}

/**
 * A floor's replies folded into a capsule: the avatars of who replied, of the replies the floor
 * came with, then how many replies there are. It opens them in place, or on their own page.
 */
@Composable
private fun SubPostsCapsule(item: PostItem, opensInPlace: Boolean, onClick: () -> Unit) {
    val post = item.post
    val colors = ExtendedTheme.colors
    // Each author once, and none of the replies the block rules fold or hide
    val repliers = remember(post.subPosts, item.subPostBlocks) {
        post.subPosts.filter { item.subPostBlocks[it.id] == null }.map { it.author }.distinctBy { it.id }.take(CapsuleAvatars)
    }
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .padding(start = Gutter, top = ToReplies, bottom = FloorBottom)
            .pressScale(interaction)
            .height(CapsuleHeight)
            .background(colors.chip, SmoothCornerShape.Capsule)
            .clickable(
                interactionSource = interaction,
                indication = PressFeedback.overlay(SmoothCornerShape.Capsule),
                role = Role.Button,
                onClickLabel = if (opensInPlace) stringResource(id = R.string.button_expand_reply) else null,
                onClick = onClick,
            )
            .padding(start = if (repliers.isEmpty()) 12.dp else 4.dp, end = 8.dp),
    ) {
        if (repliers.isNotEmpty()) {
            // Each a little over the one before, ringed in the page's colour to keep them apart
            Box {
                repliers.forEachIndexed { index, author ->
                    Avatar(
                        data = StringUtil.getAvatarUrl(author.portrait),
                        size = SubPostAvatarSize,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = (SubPostAvatarSize - CapsuleAvatarOverlap) * index)
                            .border(2.dp, colors.background, CircleShape),
                    )
                }
            }
        }
        Text(
            text = pluralStringResource(id = R.plurals.title_sub_posts_header, count = post.subPostCount, post.subPostCount),
            style = TextStyles.caption,
            color = colors.textSecondary,
        )
        Icon(
            imageVector = if (opensInPlace) Symbols.ExpandMore else Symbols.ChevronRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * The first replies inside a post as a block under it, each a paragraph of a few lines that opens
 * the replies' own page at it, then a link to all of them there.
 */
@Composable
private fun ClassicSubPosts(
    item: PostItem,
    options: PostCardOptions,
    onEvent: (ThreadEvent) -> Unit,
) {
    val post = item.post
    val context = LocalContext.current
    val shortcuts = LocalBlockShortcuts.current
    val labels = postMenuLabels()
    val colors = ExtendedTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(start = Gutter, top = ToReplies, bottom = FloorBottom)
            .fillMaxWidth()
            .background(if (colors.isNightMode) Color.White.copy(alpha = 0.05f) else colors.accent.copy(alpha = 0.06f), ClassicBlockShape)
            .padding(12.dp),
    ) {
        post.subPosts.forEach { subPost ->
            BlockableContent(
                block = item.subPostBlocks[subPost.id],
                tip = { reason, onShow -> BlockTip(reason = reason, onShow = onShow, boxed = false) },
            ) {
                ClassicSubPostLine(
                    subPost = subPost,
                    isLz = subPost.author.id == options.threadAuthorId,
                    photoViewData = { image -> replyPhotoViewData(context, post, image) },
                    onClick = { onEvent(ThreadEvent.OpenSubPosts(post, subPost.id)) },
                    menuItems = { subPostMenuItems(labels, context, post, subPost, options.hideReply, shortcuts, onEvent) },
                )
            }
        }
        if (post.subPostCount > post.subPosts.size) {
            Text(
                text = pluralStringResource(id = R.plurals.open_all_sub_posts, count = post.subPostCount, post.subPostCount),
                style = TextStyles.caption,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
                // The block's whole width opens them, not only the words
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(ThreadEvent.OpenSubPosts(post, subPostId = 0)) },
            )
        }
    }
}

/** A reply in the classic block: who, what and when in one paragraph, cut to a few lines. */
@Composable
private fun ClassicSubPostLine(
    subPost: SubPost,
    isLz: Boolean,
    photoViewData: (PostContent.Image) -> PhotoViewData?,
    onClick: () -> Unit,
    menuItems: () -> List<ContextMenuItem>,
) {
    val colors = ExtendedTheme.colors
    val photoViewer = LocalPhotoViewer.current
    val photoFloors = LocalPhotoFloors.current
    val text = rememberSubPostText(
        subPost,
        isLz,
        nameColor = colors.textSecondary,
        onImage = { image ->
            val data = photoViewData(image)
            if (data != null) photoViewer?.open(data, floors = photoFloors)
        },
    )
    val time = relativeTime(subPost.time)
    val paragraph = remember(text, time, colors.textSecondary) {
        buildAnnotatedString {
            append(text)
            // The time keeps to one line, wrapping whole when it must
            withStyle(SpanStyle(color = colors.textSecondary, fontSize = TextStyles.footnote.fontSize)) { append("  " + time.replace(' ', '\u00A0')) }
        }
    }
    Box(modifier = Modifier.fillMaxWidth().longPressMenu(items = menuItems, onClick = onClick)) {
        PbContentText(
            text = paragraph,
            style = TextStyles.replyDense.copy(color = colors.text),
            emoticonSize = 0.9f,
            overflow = TextOverflow.Ellipsis,
            maxLines = ClassicReplyLines,
            inlineContent = mapOf("Lz" to lzChip()),
        )
    }
}

@Composable
private fun lzChip() = buildChipInlineContent(
    stringResource(id = R.string.tip_lz),
    backgroundColor = ExtendedTheme.colors.textSecondary.copy(alpha = 0.1f),
    color = ExtendedTheme.colors.textSecondary,
)

/** The colour of the rule down a floor and the branches off it to its replies. */
@Composable
fun floorRuleColor(): Color = ExtendedTheme.colors.text.copy(alpha = RuleAlpha)

/**
 * A branch off a rule running down at [x], to [endX] at [y]: it leaves the rule on a quarter
 * circle of [radius], then runs straight. Added to the rule's own path, it paints nothing twice.
 */
private fun Path.branch(x: Float, y: Float, endX: Float, radius: Float) {
    moveTo(x, y - radius)
    arcTo(Rect(x, y - 2 * radius, x + 2 * radius, y), startAngleDegrees = 180f, sweepAngleDegrees = -90f, forceMoveTo = false)
    lineTo(endX, y)
}

/** Lines content up with a floor's body, past the avatar column the floor's rule runs down. */
fun Modifier.floorIndent(): Modifier = padding(start = GutterStart, end = 16.dp)

/**
 * [content], a reply, on a branch off a floor's rule. Inside a floor the floor draws the rule;
 * elsewhere [rail] draws it here too, on down to the next reply unless this is the [last]. With no
 * [rule], as in the classic style, the reply only keeps its place past the rule's column.
 */
@Composable
fun SubPostBranch(
    rule: Color?,
    modifier: Modifier = Modifier,
    rail: Boolean = false,
    last: Boolean = false,
    content: @Composable () -> Unit,
) {
    val replyCenter = replyLineCenter()
    // Drawn behind the row rather than as a part of it, so the row is as tall as the reply
    Row(
        modifier = modifier.drawBehind {
            rule ?: return@drawBehind
            val x = Gutter.toPx() / 2
            val y = replyCenter.toPx()
            val r = BranchRadius.toPx()
            val path = Path().apply {
                if (rail) {
                    moveTo(x, 0f)
                    lineTo(x, if (last) y - r else size.height)
                }
                branch(x, y, Gutter.toPx(), r)
            }
            drawPath(path, rule, style = Stroke(RuleWidth.toPx()))
        },
    ) {
        Spacer(modifier = Modifier.width(Gutter))
        content()
    }
}

/**
 * A reply inside a post: the avatar, then who and what in one paragraph, then when. In a floor it
 * shows its likes, and with [foldLong] a long one shows its first lines and a link to the rest; on
 * the replies' own page it shows in full, with [onAgree] a like button. [trailing] ends the line
 * with when, after the likes. Its pictures, opened in the viewer with [photoViewData], follow the
 * text, or with [imageLinks] are links in it.
 */
@Composable
fun SubPostLine(
    subPost: SubPost,
    isLz: Boolean,
    onClick: (() -> Unit)?,
    menuItems: () -> List<ContextMenuItem>,
    modifier: Modifier = Modifier,
    foldLong: Boolean = false,
    onAgree: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    photoViewData: (PostContent.Image) -> PhotoViewData? = { null },
    imageLinks: Boolean = false,
) {
    val colors = ExtendedTheme.colors
    val photoViewer = LocalPhotoViewer.current
    val photoFloors = LocalPhotoFloors.current
    val images = remember(subPost.content, imageLinks) {
        if (imageLinks) emptyList() else subPost.content.filterIsInstance<PostContent.Image>()
    }
    Box(modifier = modifier.fillMaxWidth().longPressMenu(items = menuItems, onClick = onClick)) {
        Column(modifier = Modifier.padding(bottom = ReplyGap)) {
            Row(verticalAlignment = Alignment.Top) {
                val center = replyLineCenter()
                Avatar(
                    data = StringUtil.getAvatarUrl(subPost.author.portrait),
                    size = SubPostAvatarSize,
                    contentDescription = null,
                    // Centred on the first line, and taller than it without parting the replies
                    modifier = Modifier.overhanging(center),
                )
                Spacer(modifier = Modifier.width(8.dp))
                FoldingReplyText(
                    text = rememberSubPostText(
                        subPost,
                        isLz,
                        nameColor = colors.textSecondary,
                        onImage = { image: PostContent.Image ->
                            val data = photoViewData(image)
                            if (data != null) photoViewer?.open(data, floors = photoFloors)
                        }.takeIf { imageLinks },
                    ),
                    fold = foldLong,
                    key = subPost.id,
                    modifier = Modifier.weight(1f),
                )
            }
            images.forEach { image ->
                PostImage(
                    image = image,
                    photoViewData = photoViewData(image),
                    size = Modifier
                        .padding(start = SubPostAvatarSize + 8.dp, top = 8.dp)
                        .fillMaxWidth(ReplyImageWidthFraction)
                        .wrapContentWidth(Alignment.Start)
                        .heightIn(max = ReplyImageMaxHeight),
                )
            }
            Row(
                modifier = Modifier.padding(start = SubPostAvatarSize + 8.dp, top = ReplyToTime),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = listOfNotNull(
                        relativeTime(subPost.time),
                        shownIpLocation(subPost.author.ipLocation),
                    ).joinToString(" · "),
                    style = TextStyles.footnote,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f).trimToCharacters(TextStyles.footnote.fontSize),
                )
                if (onAgree != null) {
                    // The button pads its icon; shift it so the icon lines up with the text's right edge
                    PostAgreeBtn(
                        hasAgreed = subPost.agree.agreed,
                        agreeNum = subPost.agree.count,
                        onClick = onAgree,
                        modifier = Modifier.offset(x = 8.dp).overhanging(),
                    )
                } else if (subPost.agree.count > 0) {
                    Icon(
                        imageVector = Symbols.Favorite,
                        contentDescription = stringResource(id = R.string.title_agree),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = subPost.agree.count.getShortNumString(),
                        style = TextStyles.footnote,
                        color = colors.textSecondary,
                    )
                }
                if (trailing != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    trailing()
                }
            }
        }
    }
}

/**
 * A reply's paragraph. With [fold], one longer than [FoldOverLines] lines shows its first
 * [FoldToLines], the last of them ending in a link that shows the rest in place.
 */
@Composable
private fun FoldingReplyText(text: AnnotatedString, fold: Boolean, key: Long, modifier: Modifier = Modifier) {
    val colors = ExtendedTheme.colors
    var unfolded by rememberSaveable(key) { mutableStateOf(false) }
    // Where the paragraph is cut, found from a layout of it in full; null while it isn't cut
    var cut by remember(text) { mutableStateOf<Int?>(null) }
    var measured by remember(text) { mutableStateOf(false) }
    val folding = fold && !unfolded
    val more = stringResource(id = R.string.button_expand_reply)
    val shown = cut?.takeIf { folding }?.let { end ->
        buildAnnotatedString {
            append(text.subSequence(0, end))
            append("…")
            // Styled as the link, which is otherwise underlined
            val style = TextLinkStyles(SpanStyle(color = colors.accent, fontWeight = FontWeight.SemiBold))
            withLink(LinkAnnotation.Clickable("expand", style) { unfolded = true }) { append(more) }
        }
    } ?: text
    val moreWidth = with(LocalDensity.current) { (TextStyles.reply.fontSize * (more.length + 1.5f)).toPx() }
    PbContentText(
        text = shown,
        style = TextStyles.reply.copy(color = colors.text),
        emoticonSize = 0.9f,
        // Laid out once past the fold to tell whether it folds at all
        maxLines = if (folding && !measured) FoldOverLines + 1 else Int.MAX_VALUE,
        onTextLayout = { layout ->
            if (folding && !measured) {
                measured = true
                if (layout.lineCount > FoldOverLines) {
                    val line = FoldToLines - 1
                    val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2
                    val end = layout.getLineEnd(line, visibleEnd = true)
                    cut = minOf(end, layout.getOffsetForPosition(Offset(layout.size.width - moreWidth, y)))
                }
            }
        },
        inlineContent = mapOf("Lz" to lzChip()),
        modifier = modifier.trimToCharacters(TextStyles.reply.fontSize),
    )
}

/**
 * The first floor, laid out for reading: the title first, its author on one line under it, the
 * body in a larger size with images across the full width, then liking, collecting and sharing.
 */
@Composable
fun ThreadFirstPost(
    post: Post,
    state: ThreadState,
    options: PostCardOptions,
    isStoreMark: Boolean,
    canDelete: Boolean,
    photoViewData: (PostContent.Image) -> PhotoViewData?,
    onEvent: (ThreadEvent) -> Unit,
) {
    val thread = state.thread
    val context = LocalContext.current
    val shortcuts = LocalBlockShortcuts.current
    val labels = postMenuLabels()
    Box(
        modifier = Modifier.longPressMenu(
            items = {
                postMenuItems(labels, context, post, options.hideReply, isStoreMark, canStore = true, canDelete, shortcuts) { event ->
                    onEvent(if (event is ThreadEvent.Reply) ThreadEvent.Reply(null) else event)
                }
            },
            // Replying to the first floor replies to the thread
            onClick = { onEvent(ThreadEvent.Reply(null)) }.takeIf { !options.hideReply },
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp)) {
            FirstPostContent(
                post = post,
                title = post.title.ifBlank { thread?.title.orEmpty() },
                isStoreMark = isStoreMark,
                photoViewData = photoViewData,
                onOpenUser = { onEvent(ThreadEvent.OpenUser(it)) },
            )
            thread?.poll?.let { poll ->
                PollCard(
                    poll = poll,
                    canVote = state.viewer.isLoggedIn,
                    onVote = { onEvent(ThreadEvent.Vote(it)) },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp),
                )
            }
            if (thread != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionPill(
                        icon = Symbols.Favorite,
                        activeIcon = Symbols.FavoriteFill,
                        text = if (thread.agree.count > 0) thread.agree.count.getShortNumString() else stringResource(R.string.title_agree),
                        active = thread.agree.agreed,
                    ) { onEvent(ThreadEvent.AgreeThread) }
                    ActionPill(
                        icon = Symbols.Star,
                        activeIcon = Symbols.StarFill,
                        text = stringResource(if (thread.isStored) R.string.title_collected else R.string.title_collect),
                        active = thread.isStored,
                    ) { onEvent(if (thread.isStored) ThreadEvent.RemoveStore else ThreadEvent.StoreAtLastRead) }
                    ActionPill(icon = Symbols.Share, text = stringResource(R.string.title_share), active = false) {
                        onEvent(ThreadEvent.Share)
                    }
                }
            }
        }
    }
}

/**
 * A thread's first floor as the thread page shows it: its [title], the author, and the content
 * at reading size. Shared by the thread page and the peek at a thread from a list.
 *
 * @param onOpenUser opens the author's profile; null where the author can't be tapped
 */
@Composable
fun FirstPostContent(
    post: Post,
    title: String,
    modifier: Modifier = Modifier,
    isStoreMark: Boolean = false,
    photoViewData: (PostContent.Image) -> PhotoViewData? = { null },
    onOpenUser: ((Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val author = post.author
    Column(modifier = modifier) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                fontSize = 22.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                color = ExtendedTheme.colors.text,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.clip(CircleShape).clickable(enabled = onOpenUser != null) { onOpenUser?.invoke(author.id) }) {
                Avatar(data = StringUtil.getAvatarUrl(author.portrait), size = 28.dp, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            ProvideTextStyle(
                MaterialTheme.typography.subtitle1.copy(
                    color = ExtendedTheme.colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            ) {
                UserNameText(
                    userName = StringUtil.getUsernameAnnotatedString(context, author.name, author.nameShow),
                    userLevel = author.level,
                    bawuType = author.bawuName(),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = onOpenUser != null) { onOpenUser?.invoke(author.id) },
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = listOfNotNull(
                    relativeTime(post.time),
                    shownIpLocation(author.ipLocation),
                ).joinToString(" · "),
                style = MaterialTheme.typography.caption,
                color = ExtendedTheme.colors.textSecondary,
            )
        }
        // Stored up to here, the mark opens in place
        Reveal(Unit.takeIf { isStoreMark }) {
            Chip(
                text = stringResource(id = R.string.title_collected_floor),
                invertColor = true,
                prefixIcon = {
                    Icon(imageVector = Symbols.StarFill, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.padding(start = 16.dp, top = 12.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        PostBody(
            content = post.content,
            photoViewData = photoViewData,
            fontSize = 17.sp,
            blockSpacing = 16.dp,
            textPadding = PaddingValues(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ActionPill(icon: ImageVector, text: String, active: Boolean, activeIcon: ImageVector = icon, onClick: () -> Unit) {
    val color by animateColorAsState(if (active) ExtendedTheme.colors.accent else ExtendedTheme.colors.text, Motion.snap(), label = "actionPill")
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .pressScale(interactionSource)
            .height(36.dp)
            .background(ExtendedTheme.colors.chip, SmoothCornerShape.Capsule)
            .clickable(interactionSource = interactionSource, indication = PressFeedback.overlay(SmoothCornerShape.Capsule), onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ToggleIcon(checked = active, on = activeIcon, off = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Refocus(targetState = text) { Text(text = it, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color) }
    }
}

/** The narrow display face the app uses for figures. */
@Composable
private fun rememberNumeralFamily(): FontFamily {
    val context = LocalContext.current
    return numeralFamily ?: FontFamily(Typeface.createFromAsset(context.assets, "bebas.ttf")).also { numeralFamily = it }
}

/** Loaded once for every floor: loading it took about a millisecond each time a floor came in. */
private var numeralFamily: FontFamily? = null

/** A reply's picture alone in the viewer: the thread's picture list doesn't have the replies' pictures. */
internal fun replyPhotoViewData(context: Context, post: Post, image: PostContent.Image) = PhotoViewData(
    picItems = persistentListOf(
        PicItem(
            picId = image.id,
            picIndex = 1,
            url = ImageUtil.getUrl(context, true, image.originUrl, *image.urls.toTypedArray()),
            originUrl = image.originUrl,
            showOriginBtn = image.showOriginButton,
            originSize = image.originSize,
            postId = post.id,
        ),
    ),
)

/** Where the middle of a reply's first line is, from the top of its characters. */
@Composable
private fun replyLineCenter(): Dp = with(LocalDensity.current) { (TextStyles.reply.fontSize.toPx() / 2).toDp() }

/**
 * Lays this out as no height at all, centred where it would be, or with its middle [at] a height
 * from the top: taller than the things beside it, it doesn't push apart what is above and below.
 */
private fun Modifier.overhanging(at: Dp? = null): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    layout(placeable.width, 0) {
        placeable.place(0, (at?.roundToPx() ?: 0) - placeable.height / 2)
    }
}

/** How far a link's press shows past its words, all round. */
private val LinkPressPad = 8.dp

/**
 * Makes this a link that can be pressed anywhere within [LinkPressPad] of its words, and shows the
 * press there, while it takes only the room of its words: the space around it stays as set.
 */
private fun Modifier.linkPress(onClick: () -> Unit): Modifier = this
    .layout { measurable, constraints ->
        val pad = LinkPressPad.roundToPx()
        val placeable = measurable.measure(constraints.offset(2 * pad, 2 * pad))
        layout(placeable.width - 2 * pad, placeable.height - 2 * pad) { placeable.place(-pad, -pad) }
    }
    .clickable(interactionSource = null, indication = PressFeedback.overlay(Corners.sm), onClick = onClick)
    .padding(LinkPressPad)

/** What shows under a floor for its replies. */
private enum class SubPostsShown { Capsule, Timeline, Classic }

private val SubPostsBlur = 6.dp
