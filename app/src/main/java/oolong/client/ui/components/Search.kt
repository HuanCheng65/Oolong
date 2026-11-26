package oolong.client.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import oolong.client.R
import oolong.client.data.block.BlockVerdict
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.model.QuotedPost
import oolong.client.model.SearchAuthor
import oolong.client.model.SearchedPost
import oolong.client.ui.components.PbContentText
import oolong.client.ui.components.relativeTime
import oolong.client.ui.components.rememberTextLinkActions
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.BaseTextField
import oolong.client.ui.designsystem.MyLazyColumn
import oolong.client.ui.designsystem.Sizes
import oolong.client.ui.designsystem.UserHeader
import oolong.client.ui.designsystem.VerticalDivider
import oolong.client.ui.designsystem.adaptive.LocalWindowSizeClass
import oolong.client.ui.designsystem.adaptive.WindowWidthSizeClass
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.util.StringUtil
import oolong.client.util.StringUtil.buildAnnotatedStringWithUser
import oolong.client.util.TiebaUtil
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.Radius
import oolong.core.designsystem.theme.SmoothCornerShape

@Composable
fun QuotePostCard(
    quoted: QuotedPost,
    thread: QuotedPost,
    onThreadClick: (QuotedPost) -> Unit,
    modifier: Modifier = Modifier,
    pictures: ImmutableList<String> = persistentListOf(),
    mediaCount: Int = pictures.size,
    keyword: String? = null,
) {
    val links = rememberTextLinkActions()
    val context = LocalContext.current
    val quoteContentString = remember(quoted, links) {
        buildAnnotatedStringWithUser(
            quoted.author.id,
            quoted.author.name,
            quoted.author.nameShow,
            quoted.content,
            links,
            context,
        )
    }
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HighlightText(
            text = quoteContentString,
            style = MaterialTheme.typography.body2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            highlightKeywords = (keyword?.split(" ") ?: emptyList()).toImmutableList()
        )
        MainPostCard(
            thread = thread,
            modifier = Modifier
                .fillMaxWidth()
                .background(ExtendedTheme.colors.card, SmoothCornerShape(6.dp))
                .clickable(interactionSource = null, indication = PressFeedback.overlay(SmoothCornerShape(6.dp))) {
                    onThreadClick(thread)
                },
            pictures = pictures,
            mediaCount = mediaCount,
            keyword = keyword
        )
    }
}

@Composable
fun MainPostCard(
    thread: QuotedPost,
    modifier: Modifier = Modifier,
    pictures: ImmutableList<String> = persistentListOf(),
    mediaCount: Int = pictures.size,
    keyword: String? = null,
) {
    val links = rememberTextLinkActions()
    val context = LocalContext.current
    val titleString = remember(thread, links) {
        buildAnnotatedStringWithUser(
            thread.author.id,
            thread.author.name,
            thread.author.nameShow,
            thread.title,
            links,
            context,
        )
    }
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HighlightText(
            text = titleString,
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            highlightKeywords = (keyword?.split(" ") ?: emptyList()).toImmutableList()
        )
        if (thread.content.isNotBlank()) {
            PbContentText(
                text = thread.content,
                style = MaterialTheme.typography.body2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SearchMedia(pictures = pictures, mediaCount = mediaCount)
    }
}

@Composable
fun SearchThreadList(
    data: ImmutableList<SearchedPost>,
    lazyListState: LazyListState,
    onItemClick: (SearchedPost) -> Unit,
    onItemUserClick: (SearchAuthor) -> Unit,
    onItemForumClick: (forumName: String) -> Unit,
    modifier: Modifier = Modifier,
    onQuotePostClick: (QuotedPost) -> Unit = {},
    onMainPostClick: (QuotedPost) -> Unit = {},
    hideForum: Boolean = false,
    searchKeyword: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    blockOf: (SearchedPost) -> BlockVerdict? = { null },
    footer: LazyListScope.() -> Unit = {},
    header: LazyListScope.() -> Unit = {},
) {
    MyLazyColumn(
        state = lazyListState,
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        header()
        itemsIndexed(data) { index, item ->
            if (index > 0) {
                VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            BlockableContent(block = blockOf(item), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SearchThreadItem(
                    item = item,
                    onClick = onItemClick,
                    onUserClick = onItemUserClick,
                    onForumClick = onItemForumClick,
                    onQuotePostClick = onQuotePostClick,
                    onMainPostClick = onMainPostClick,
                    hideForum = hideForum,
                    searchKeyword = searchKeyword
                )
            }
        }
        footer()
    }
}

@Composable
fun SearchThreadUserHeader(
    user: SearchAuthor,
    time: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UserHeader(
        avatar = {
            Avatar(
                data = StringUtil.getAvatarUrl(user.portrait),
                size = Sizes.Small,
                contentDescription = null
            )
        },
        name = {
            AuthorName(
                text = StringUtil.getUsernameAnnotatedString(
                    LocalContext.current,
                    user.name,
                    user.nameShow,
                    color = LocalContentColor.current
                )
            )
        },
        desc = {
            MetaText(text = relativeTime(time))
        },
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun SearchThreadItem(
    item: SearchedPost,
    onClick: (SearchedPost) -> Unit,
    onUserClick: (SearchAuthor) -> Unit,
    onForumClick: (forumName: String) -> Unit,
    modifier: Modifier = Modifier,
    onQuotePostClick: (QuotedPost) -> Unit = {},
    onMainPostClick: (QuotedPost) -> Unit = {},
    hideForum: Boolean = false,
    searchKeyword: String? = null,
) {
    val context = LocalContext.current
    Card(
        modifier = modifier,
        header = {
            SearchThreadUserHeader(
                user = item.author,
                time = item.time,
                onClick = { onUserClick(item.author) }
            )
        },
        content = {
            ThreadContent(
                title = item.title,
                abstractText = item.content,
                showTitle = item.thread == null && item.title.isNotBlank(),
                showAbstract = item.content.isNotBlank(),
                maxLines = 2,
                highlightKeywords = (searchKeyword?.split(" ") ?: emptyList()).toImmutableList(),
            )
            val thread = item.thread
            val quoted = item.quoted
            if (thread != null) {
                if (quoted != null) {
                    QuotePostCard(
                        quoted = quoted,
                        thread = thread,
                        onThreadClick = onMainPostClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ExtendedTheme.colors.inset, SmoothCornerShape(6.dp))
                            .clickable(interactionSource = null, indication = PressFeedback.overlay(SmoothCornerShape(6.dp))) {
                                onQuotePostClick(quoted)
                            },
                        pictures = item.pictures,
                        mediaCount = item.mediaCount,
                        keyword = searchKeyword
                    )
                } else {
                    MainPostCard(
                        thread = thread,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ExtendedTheme.colors.inset, SmoothCornerShape(6.dp))
                            .clickable(interactionSource = null, indication = PressFeedback.overlay(SmoothCornerShape(6.dp))) {
                                onMainPostClick(thread)
                            },
                        pictures = item.pictures,
                        mediaCount = item.mediaCount,
                        keyword = searchKeyword
                    )
                }
            } else {
                SearchMedia(pictures = item.pictures, mediaCount = item.mediaCount)
                item.matchedReply?.let { reply ->
                    MatchedReply(
                        reply = reply,
                        keyword = searchKeyword,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ExtendedTheme.colors.inset, SmoothCornerShape(6.dp)),
                    )
                }
            }
        },
        action = {
            FeedCardActions(
                forum = {
                    if (!hideForum && item.forumName.isNotEmpty()) {
                        ForumInfoChip(
                            imageUriProvider = { item.forumAvatar },
                            nameProvider = { item.forumName }
                        ) {
                            onForumClick(item.forumName)
                        }
                    }
                }
            ) {
                ThreadShareBtn(shareNum = item.shareCount, onClick = {
                    TiebaUtil.shareText(context, "https://tieba.baidu.com/p/${item.threadId}", item.title.takeIf(String::isNotBlank))
                })
                ThreadReplyBtn(replyNum = item.replyCount, onClick = { onClick(item) })
                // Search doesn't say whether the user likes it, so the count only shows
                ThreadAgreeBtn(hasAgree = false, agreeNum = item.agreeCount, onClick = null)
            }
        },
        onClick = { onClick(item) },
    )
}

/** The reply a thread was found by, after its author's name, with the keyword marked. */
@Composable
private fun MatchedReply(reply: QuotedPost, keyword: String?, modifier: Modifier = Modifier) {
    val links = rememberTextLinkActions()
    val context = LocalContext.current
    val text = remember(reply, keyword, links) {
        buildAnnotatedStringWithUser(reply.author.id, reply.author.name, reply.author.nameShow, reply.content.around(keyword), links, context)
    }
    HighlightText(
        text = text,
        style = MaterialTheme.typography.body2,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        highlightKeywords = (keyword?.split(" ") ?: emptyList()).toImmutableList(),
        modifier = modifier.padding(16.dp),
    )
}

/**
 * This text from a little before the first word of [keyword] in it, so that three lines show the
 * match even deep in a long reply; whole when the match comes early or not at all.
 */
private fun String.around(keyword: String?): String {
    val first = keyword?.split(" ")?.filter { it.isNotEmpty() }?.mapNotNull { word ->
        indexOf(word, ignoreCase = true).takeIf { it >= 0 }
    }?.minOrNull() ?: return this
    if (first <= MATCH_LEAD * 2) return this
    // Not between the halves of an emoji
    val start = (first - MATCH_LEAD).let { if (this[it].isLowSurrogate()) it - 1 else it }
    return "…" + substring(start).trimStart()
}

/** How many characters before a match a shortened reply keeps. */
private const val MATCH_LEAD = 20

/** Up to three of a search result's pictures, with a badge counting its media when there are more. */
@Composable
fun SearchMedia(
    pictures: ImmutableList<String>,
    modifier: Modifier = Modifier,
    mediaCount: Int = pictures.size,
) {
    val context = LocalContext.current

    val picCount = pictures.size
    val hasPhoto = picCount > 0
    val isSinglePhoto = picCount == 1
    val hideMedia = Settings.of(context)[SettingKeys.HideMedia]

    val windowWidthSizeClass = LocalWindowSizeClass.current.widthSizeClass
    val singleMediaFraction = remember(windowWidthSizeClass) {
        if (windowWidthSizeClass == WindowWidthSizeClass.Compact)
            1f
        else 0.5f
    }

    if (hasPhoto && !hideMedia) {
        val mediaWidthFraction = if (isSinglePhoto) singleMediaFraction else 1f
        val mediaAspectRatio = if (isSinglePhoto) 2f else 3f
        val showMedias = remember(pictures) { pictures.take(3) }
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(mediaWidthFraction)
                        .aspectRatio(mediaAspectRatio),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // One rounded shape across the row: its outer corners only
                    showMedias.fastForEachIndexed { index, pic ->
                        val start = CornerSize(if (index == 0) Radius.sm else 0.dp)
                        val end = CornerSize(if (index == showMedias.lastIndex) Radius.sm else 0.dp)
                        NetworkImage(
                            imageUri = pic,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f),
                            shape = remember(start, end) { SmoothCornerShape(start, end, end, start) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                if (picCount > 3) {
                    Badge(
                        icon = Symbols.Image,
                        text = "$mediaCount",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SearchBox(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onKeywordSubmit: (String) -> Unit = {},
    placeholder: @Composable () -> Unit = {},
    prependIcon: @Composable () -> Unit = {},
    appendIcon: @Composable () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    shape: Shape = RectangleShape,
    color: Color = ExtendedTheme.colors.searchField,
    contentColor: Color = ExtendedTheme.colors.onSearchField,
    elevation: Dp = 0.dp,
    // Without it, as where the field narrows as it is typed in, the clear button stays shown
    submitButton: Boolean = true,
) {
    val isKeywordNotEmpty = remember(keyword) { keyword.isNotEmpty() }
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        elevation = elevation
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            prependIcon()
            BaseTextField(
                value = keyword,
                onValueChange = {
                    onKeywordChange(it)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onKeywordSubmit(keyword)
                    }
                ),
                placeholder = placeholder,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusEvent {
                        isFocused = it.isFocused
                    }
            )
            appendIcon()
            AnimatedVisibility(visible = isKeywordNotEmpty && (isFocused || !submitButton), enter = ButtonEnter, exit = ButtonExit) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = PressFeedback.overlay(Corners.full),
                                role = Role.Button
                            ) { onKeywordChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Symbols.Close,
                            contentDescription = stringResource(id = R.string.button_clear)
                        )
                    }
                }
            }
            AnimatedVisibility(visible = isKeywordNotEmpty && submitButton, enter = ButtonEnter, exit = ButtonExit) {
                Box(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = PressFeedback.overlay(Corners.full),
                            role = Role.Button
                        ) { onKeywordSubmit(keyword) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Symbols.Search,
                        contentDescription = stringResource(id = R.string.button_search)
                    )
                }
            }
        }
    }
}

/** A field's trailing button making room for itself as it fades in, and giving it back as it goes. */
private val ButtonEnter get() = fadeIn(Motion.fadeIn()) + expandHorizontally(Motion.settle())
private val ButtonExit get() = fadeOut(Motion.fadeOut()) + shrinkHorizontally(Motion.dismiss())
