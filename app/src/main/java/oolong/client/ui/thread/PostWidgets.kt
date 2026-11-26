package oolong.client.ui.thread

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import oolong.client.R
import oolong.client.model.BawuType
import oolong.client.model.User
import oolong.client.ui.components.buildChipInlineContent
import oolong.client.ui.components.inlineContentSafe
import oolong.client.ui.designsystem.Button
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.ToggleIcon
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.util.StringUtil.getShortNumString
import oolong.client.util.Util.getIconColorByLevel
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Motion

@Composable
fun PostAgreeBtn(
    hasAgreed: Boolean,
    agreeNum: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedColor by animateColorAsState(
        targetValue = if (hasAgreed) ExtendedTheme.colors.accent else ExtendedTheme.colors.textSecondary,
        animationSpec = Motion.snap(),
        label = "postAgreeBtnColor"
    )
    Button(
        onClick = onClick,
        shape = Corners.xs,
        minHeight = 0.dp,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = ExtendedTheme.colors.background,
            contentColor = animatedColor
        ),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleIcon(
                checked = hasAgreed,
                on = Symbols.FavoriteFill,
                off = Symbols.Favorite,
                contentDescription = stringResource(id = R.string.title_agree),
                tint = animatedColor,
                modifier = Modifier.size(16.dp)
            )
            Refocus(targetState = agreeNum) { num ->
                if (num > 0) {
                    Text(
                        text = num.getShortNumString(),
                        color = animatedColor,
                        style = MaterialTheme.typography.caption,
                        textAlign = TextAlign.Center,
                        // The gap comes and goes with the count
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun UserNameText(
    userName: AnnotatedString,
    userLevel: Int,
    modifier: Modifier = Modifier,
    isLz: Boolean = false,
    bawuType: String? = null,
) {
    val text = buildAnnotatedString {
        append(userName)
        append(" ")
        if (userLevel > 0) appendInlineContent("Level", alternateText = "$userLevel")
        if (!bawuType.isNullOrBlank()) {
            append(" ")
            appendInlineContent("Bawu", alternateText = bawuType)
        }
        if (isLz) {
            append(" ")
            appendInlineContent("Lz")
        }
    }
    Text(
        text = text,
        inlineContent = mapOf(
            "Level" to buildChipInlineContent(
                "18",
                color = Color(getIconColorByLevel("$userLevel")),
                backgroundColor = Color(getIconColorByLevel("$userLevel")).copy(alpha = 0.25f)
            ),
            "Bawu" to buildChipInlineContent(
                bawuType ?: "",
                color = ExtendedTheme.colors.emphasis,
                backgroundColor = ExtendedTheme.colors.emphasis.copy(alpha = 0.1f)
            ),
            "Lz" to buildChipInlineContent(stringResource(id = R.string.tip_lz)),
        ),
        modifier = modifier.inlineContentSafe()
    )
}

/** How a moderator is labelled next to their name, or null for anyone else. */
@Composable
fun User.bawuName(): String? = when (bawuType) {
    BawuType.Manager -> stringResource(id = R.string.bawu_manager)
    BawuType.Assist -> stringResource(id = R.string.bawu_assist)
    null -> null
}
