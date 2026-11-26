package oolong.client.ui.login

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.delay
import oolong.client.R
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.maskTo
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.glassEdge
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.SmoothCornerShape
import oolong.core.designsystem.theme.TextStyles

/** The login card once the account is in, handed over from the login page as it goes. */
@Immutable
internal data class SignedInNotice(val name: String, val avatar: String?, val from: Rect, val id: Long)

/**
 * Says an account is now signed in, over whatever page the login was opened from: the login page's
 * card turns into a toast at the bottom as the page goes back, which stays a moment, then goes.
 */
@Stable
class SignedInNotices internal constructor() {
    internal var notice by mutableStateOf<SignedInNotice?>(null)
    private var shown = 0L

    /** Takes over the card of [name] and [avatar] standing at [from], in the window. */
    internal fun show(name: String, avatar: String?, from: Rect) {
        notice = SignedInNotice(name, avatar, from, ++shown)
    }
}

val LocalSignedInNotices = staticCompositionLocalOf<SignedInNotices?> { null }

/** Draws [content] with the signed-in notice over it, ready for the login page to hand over. */
@Composable
fun SignedInNoticeHost(content: @Composable () -> Unit) {
    val notices = remember { SignedInNotices() }
    CompositionLocalProvider(LocalSignedInNotices provides notices) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            notices.notice?.let { notice ->
                key(notice.id) {
                    NoticeOverlay(notice) { if (notices.notice?.id == notice.id) notices.notice = null }
                }
            }
        }
    }
}

@Composable
private fun NoticeOverlay(notice: SignedInNotice, onDone: () -> Unit) {
    // With motion reduced the card doesn't fly down into the toast: the toast fades in where it goes
    val reduced = LocalReduceMotion.current
    val morph = remember { Animatable(if (reduced) 1f else 0f) }
    val fade = remember { Animatable(if (reduced) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (reduced) fade.animateTo(1f, Motion.fadeIn()) else morph.animateTo(1f, Motion.morph(Motion.ProgressThreshold))
        delay(StayMillis)
        fade.animateTo(0f, Motion.fadeOut())
        onDone()
    }
    val density = LocalDensity.current
    val text = stringResource(id = R.string.login_signed_in_as, notice.name)
    val measurer = rememberTextMeasurer()
    val textWidth = remember(text, measurer) { with(density) { measurer.measure(text, TextStyles.label).size.width.toDp() } }
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val colors = ExtendedTheme.colors
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .graphicsLayer { alpha = fade.value },
    ) {
        val m = morph.value
        // From the card, where the login page had it, to the toast at the bottom
        val from = with(density) {
            Rect(notice.from.left - origin.x, notice.from.top - origin.y, notice.from.right - origin.x, notice.from.bottom - origin.y)
        }
        val fromLeft = with(density) { from.left.toDp() }
        val fromTop = with(density) { from.top.toDp() }
        val fromWidth = with(density) { from.width.toDp() }
        val fromHeight = with(density) { from.height.toDp() }
        val toastWidth = (ToastStart + ToastAvatar + ToastGap + textWidth + ToastEnd).coerceAtMost(maxWidth - 32.dp)
        val toastLeft = (maxWidth - toastWidth) / 2
        val toastTop = maxHeight - bottomInset - ToastBottom - ToastHeight
        val left = lerp(fromLeft, toastLeft, m)
        val top = lerp(fromTop, toastTop, m)
        val width = lerp(fromWidth, toastWidth, m)
        val height = lerp(fromHeight, ToastHeight, m)
        val shape = SmoothCornerShape(lerp(30.dp, ToastHeight / 2, m))
        Box(
            modifier = Modifier
                .offset { IntOffset(left.roundToPx(), top.roundToPx()) }
                .size(width, height)
                .shadow(lerp(if (colors.isNightMode) 32.dp else 24.dp, 8.dp, m), shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.14f), spotColor = Color.Black.copy(alpha = 0.22f))
                .background(colors.sheet, shape)
                .border(0.5.dp, colors.glassEdge, shape)
                .maskTo(shape),
        ) {
            // The card as it was, giving way to the toast's line midway
            CardBody(
                ring = {},
                title = notice.name,
                text = stringResource(id = R.string.login_succeeded),
                modifier = Modifier
                    .wrapContentSize(Alignment.TopCenter, unbounded = true)
                    .requiredSize(fromWidth, fromHeight)
                    .graphicsLayer { alpha = (1f - m * 2.5f).coerceIn(0f, 1f) },
            )
            Text(
                text = text,
                style = TextStyles.label,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = ToastStart + ToastAvatar + ToastGap, end = ToastEnd)
                    .graphicsLayer { alpha = ((m - 0.45f) * 2.2f).coerceIn(0f, 1f) },
            )
        }
        // The avatar moves from the card's ring to the toast's start
        val avatarSize = lerp(CardAvatarSize, ToastAvatar, m)
        val cardAvatarX = fromLeft + fromWidth / 2
        val cardAvatarY = fromTop + CardPaddingTop + RingSize / 2
        val centreX = lerp(cardAvatarX, toastLeft + ToastStart + ToastAvatar / 2, m)
        val centreY = lerp(cardAvatarY, toastTop + ToastHeight / 2, m)
        Avatar(
            data = notice.avatar,
            size = avatarSize,
            contentDescription = null,
            modifier = Modifier.offset { IntOffset((centreX - avatarSize / 2).roundToPx(), (centreY - avatarSize / 2).roundToPx()) },
        )
    }
}

private fun Dp.coerceAtMost(max: Dp): Dp = if (this > max) max else this

private val ToastHeight = 48.dp
private val ToastStart = 8.dp
private val ToastAvatar = 32.dp
private val ToastGap = 10.dp
private val ToastEnd = 20.dp

/** Above the bottom bar, where pages have one, as the app's other toasts stand. */
private val ToastBottom = 96.dp

private const val StayMillis = 1_500L
