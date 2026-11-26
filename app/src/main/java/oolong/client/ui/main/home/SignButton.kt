package oolong.client.ui.main.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import oolong.client.R
import oolong.client.background.sign.SignProgress
import oolong.client.ui.designsystem.ProvideContentColor
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.core.designsystem.theme.Motion

/**
 * The top bar's sign-in button. While the followed forums are being signed a ring round it fills
 * as they are, the icon drawing in to make room; once over, a tick stands in for a moment before
 * the button comes back. Pressed while signing, it does nothing more: the ring is the answer.
 */
@Composable
internal fun SignButton(
    signing: SignProgress.Run?,
    onClick: () -> Unit,
    onDoneShown: () -> Unit,
) {
    val running = signing != null && !signing.finished
    val done = signing?.finished == true
    val currentOnDoneShown by rememberUpdatedState(onDoneShown)
    LaunchedEffect(done) {
        if (done) {
            delay(DoneShownMillis)
            currentOnDoneShown()
        }
    }
    val progress = when {
        signing == null -> null
        signing.total == null -> null
        else -> stringResource(R.string.sign_progress, signing.done, signing.total)
    }
    ProvideContentColor(color = ExtendedTheme.colors.topBarContent) {
        IconButton(
            onClick = { if (!running) onClick() },
            modifier = Modifier.semantics { if (progress != null) stateDescription = progress },
        ) {
            Box(contentAlignment = Alignment.Center) {
                val color = LocalContentColor.current
                AnimatedVisibility(
                    visible = running || done,
                    enter = fadeIn(Motion.fadeIn()) + scaleIn(Motion.settle(), initialScale = 0.8f),
                    exit = fadeOut(Motion.fadeOut()) + scaleOut(Motion.dismiss(), targetScale = 0.8f),
                ) {
                    val fraction = signing?.fraction
                    if (fraction == null) {
                        // The forums to sign aren't known yet
                        CircularProgressIndicator(color = color, strokeWidth = RingStroke, modifier = Modifier.size(RingSize))
                    } else {
                        val shown by animateFloatAsState(fraction, Motion.settle(Motion.ProgressThreshold), label = "sign progress")
                        CircularProgressIndicator(
                            progress = shown,
                            color = color,
                            backgroundColor = color.copy(alpha = TrackAlpha),
                            strokeWidth = RingStroke,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier.size(RingSize),
                        )
                    }
                }
                val iconSize by animateDpAsState(if (running || done) RingedIconSize else IconSize, Motion.settle(), label = "sign icon")
                Crossfade(targetState = done, animationSpec = Motion.fadeIn(), label = "sign done") { over ->
                    Icon(
                        imageVector = if (over) Symbols.Check else ImageVector.vectorResource(id = R.drawable.ic_oksign),
                        contentDescription = stringResource(id = R.string.title_oksign),
                        modifier = Modifier.size(iconSize),
                    )
                }
            }
        }
    }
}

private val RingSize = 32.dp
private val RingStroke = 2.dp
private val IconSize = 24.dp
private val RingedIconSize = 16.dp
private const val TrackAlpha = 0.16f
private const val DoneShownMillis = 1200L
