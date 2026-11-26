package oolong.client.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import oolong.client.R
import oolong.client.data.emoticon.EmoticonUtil.emoticonInlineId
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.media.ImageUtil
import oolong.client.model.PhotoViewData
import oolong.client.model.PostContent
import oolong.client.model.SubPost
import oolong.client.model.TextSpan
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.WebViewKey
import oolong.client.ui.components.ContentImage
import oolong.client.ui.components.NetworkImage
import oolong.client.ui.designsystem.VoicePlayer
import oolong.client.ui.designsystem.adaptive.LocalWindowSizeClass
import oolong.client.ui.designsystem.adaptive.WindowWidthSizeClass
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.trimToCharacters
import oolong.client.ui.videoview.VideoCover
import oolong.client.ui.videoview.ViewerVideo
import oolong.client.util.StringUtil
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape

/**
 * The body of a post, block by block.
 *
 * @param photoViewData what opening [PostContent.Image] in the image viewer needs, or null to
 *   show it without opening
 */
@Composable
fun PostBody(
    content: ImmutableList<PostContent>,
    modifier: Modifier = Modifier,
    photoViewData: (PostContent.Image) -> PhotoViewData? = { null },
    fontSize: TextUnit = 15.sp,
    blockSpacing: Dp = 8.dp,
    textPadding: PaddingValues = PaddingValues(0.dp),
    /**
     * Whether the body starts and ends at the characters of its first and last lines of text,
     * without the room the font leaves above and below them, so the space set around the body is
     * the space seen.
     */
    trimEdges: Boolean = false,
) {
    // Images run edge to edge of the body; padding the other blocks lets a caller bleed them
    val padded = Modifier.padding(textPadding)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(blockSpacing),
    ) {
        content.forEachIndexed { index, block ->
            when (block) {
                is PostContent.Text -> {
                    val first = trimEdges && index == 0
                    val last = trimEdges && index == content.lastIndex
                    Box(padded) { PostText(block.spans, fontSize, trimTop = first, trimBottom = last) }
                }
                is PostContent.Image -> PostImage(block, photoViewData(block), rounded = textPadding == PaddingValues(0.dp))
                is PostContent.Video -> Box(padded) { PostVideo(block) }
                is PostContent.Voice -> Box(padded) { PostVoice(block) }
            }
        }
    }
}

@Composable
private fun PostText(spans: ImmutableList<TextSpan>, fontSize: TextUnit, trimTop: Boolean, trimBottom: Boolean) {
    val accent = ExtendedTheme.colors.accent
    val videoTag = stringResource(id = R.string.tag_video)
    val warning = MaterialTheme.colors.error
    val actualHost = stringResource(id = R.string.link_actual_host)
    val links = rememberTextLinkActions()
    val text = remember(spans, accent, videoTag, warning, actualHost, links) {
        spans.toAnnotatedString(accent, videoTag, warning, actualHost, links)
    }
    PbContentText(
        text = text,
        modifier = if (trimTop || trimBottom) Modifier.trimToCharacters(fontSize, trimTop, trimBottom) else Modifier,
        fontSize = fontSize,
        style = MaterialTheme.typography.body1,
    )
}

/** The size an image or video takes: the full width on phones, half of it on wider windows. */
@Composable
private fun mediaWidthFraction(): Float =
    if (LocalWindowSizeClass.current.widthSizeClass == WindowWidthSizeClass.Compact) 1f else 0.5f

/** A picture of a post, at its own shape; [size] sets its width, the full media width by default. */
@Composable
fun PostImage(
    image: PostContent.Image,
    photoViewData: PhotoViewData?,
    rounded: Boolean = true,
    size: Modifier = Modifier.fillMaxWidth(mediaWidthFraction()),
) {
    val context = LocalContext.current
    val url = remember(image) {
        ImageUtil.getUrl(context, true, image.originUrl, *image.urls.toTypedArray())
    }
    NetworkImage(
        imageUri = url,
        contentDescription = null,
        modifier = Modifier
            .then(size)
            .aspectRatio(image.aspectRatio),
        shape = if (rounded) SmoothCornerShape(Settings.of(context)[SettingKeys.Radius].dp) else null,
        photoViewData = photoViewData,
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun PostVideo(video: PostContent.Video) {
    if (video.coverUrl.isBlank()) return
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val shape = SmoothCornerShape(Settings.of(context)[SettingKeys.Radius].dp)
    if (video.url.isNotBlank()) {
        VideoCover(
            video = remember(video) {
                ViewerVideo(video.url, video.coverUrl, video.width, video.height, video.duration)
            },
            shape = shape,
            modifier = Modifier.fillMaxWidth(mediaWidthFraction()),
        )
    } else {
        val interactions = remember { MutableInteractionSource() }
        Box(
            Modifier
                .fillMaxWidth(mediaWidthFraction())
                .aspectRatio(video.width * 1f / video.height)
                .clickable(interactionSource = interactions, indication = null) { navigator.navigate(WebViewKey(video.webUrl)) }
        ) {
            ContentImage(
                imageUri = video.coverUrl,
                contentDescription = stringResource(id = R.string.desc_video),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                shape = shape,
            )
            Box(
                Modifier
                    .matchParentSize()
                    .indication(interactions, PressFeedback.overlay(shape))
            )
        }
    }
}

@Composable
private fun PostVoice(voice: PostContent.Voice) {
    val url = remember(voice) {
        "https://tiebac.baidu.com/c/p/voice?voice_md5=${voice.md5}&play_from=pb_voice_play"
    }
    VoicePlayer(url = url, duration = voice.duration)
}

private val PostContent.Image.aspectRatio: Float
    get() = if (width > 0 && height > 0) width * 1f / height else 1f

/**
 * Inline text with links, mentions, topics and video links in [accent], each opening through
 * [links], and emoticons as inline images. A link whose text names another site is followed by
 * where it really goes, in [warning], written with [actualHostFormat].
 */
fun List<TextSpan>.toAnnotatedString(
    accent: Color,
    videoTag: String,
    warning: Color,
    actualHostFormat: String,
    links: TextLinkActions,
): AnnotatedString =
    buildAnnotatedString {
        val accentLink = TextLinkStyles(SpanStyle(color = accent))
        forEach { span ->
            when (span) {
                is TextSpan.Plain -> append(span.text)
                is TextSpan.Emoticon -> appendInlineContent(emoticonInlineId(span.id), alternateText = span.text)
                is TextSpan.Link -> {
                    appendInlineContent("link_icon", alternateText = "🔗")
                    withLink(links.url(span.url, accentLink)) { append(span.text) }
                    span.actualHost?.let { host ->
                        withLink(links.url(span.url, TextLinkStyles(SpanStyle(color = warning)))) {
                            append(actualHostFormat.format(host))
                        }
                    }
                }

                is TextSpan.Topic -> withLink(links.topic(span.name, accentLink)) { append(span.text) }

                is TextSpan.Mention -> withLink(links.user(span.userId, accentLink)) { append(span.text) }

                is TextSpan.VideoLink -> {
                    appendInlineContent("video_icon", alternateText = "🎥")
                    withLink(links.url(span.url, accentLink)) {
                        append(videoTag)
                        append(span.text)
                    }
                }
            }
        }
    }

/**
 * A reply inside a post as one line: the author's name in [nameColor], a thread author tag when
 * [isLz], then what they said. With [onImage], each of its pictures is a link that calls it;
 * without, only the text of the reply is kept.
 */
@Composable
fun rememberSubPostText(
    subPost: SubPost,
    isLz: Boolean,
    nameColor: Color = ExtendedTheme.colors.emphasis,
    onImage: ((PostContent.Image) -> Unit)? = null,
): AnnotatedString {
    val context = LocalContext.current
    val accent = ExtendedTheme.colors.accent
    val videoTag = stringResource(id = R.string.tag_video)
    val warning = MaterialTheme.colors.error
    val actualHost = stringResource(id = R.string.link_actual_host)
    val imageLink = stringResource(id = R.string.link_view_image)
    val links = rememberTextLinkActions()
    val currentOnImage by rememberUpdatedState(onImage)
    val imageLinks = onImage != null
    return remember(subPost, isLz, nameColor, accent, videoTag, warning, actualHost, imageLink, imageLinks, links) {
        buildAnnotatedString {
            val nameStyle = TextLinkStyles(SpanStyle(color = nameColor, fontWeight = FontWeight.Bold))
            withLink(links.user(subPost.author.id, nameStyle)) {
                append(StringUtil.getUsernameAnnotatedString(context, subPost.author.name, subPost.author.nameShow))
            }
            if (isLz) appendInlineContent("Lz")
            append(": ")
            subPost.content.forEachIndexed { index, block ->
                when {
                    block is PostContent.Text -> append(block.spans.toAnnotatedString(accent, videoTag, warning, actualHost, links))
                    block is PostContent.Image && imageLinks -> {
                        if (index > 0) append(" ")
                        val style = TextLinkStyles(SpanStyle(color = accent, textDecoration = TextDecoration.None))
                        withLink(LinkAnnotation.Clickable("image$index", style) { currentOnImage?.invoke(block) }) {
                            appendInlineContent("photo_icon", alternateText = "🖼")
                            append(imageLink)
                        }
                    }
                }
            }
        }
    }
}
