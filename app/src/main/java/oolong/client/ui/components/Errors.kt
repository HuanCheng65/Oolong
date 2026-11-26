package oolong.client.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import oolong.client.R
import oolong.client.data.error.TiebaNotLoggedInException
import oolong.client.data.error.getErrorCode
import oolong.client.data.error.getErrorMessage
import oolong.client.data.error.isNetworkError
import oolong.client.data.error.isUnexpectedError
import oolong.client.ui.designsystem.Button
import oolong.client.ui.designsystem.DecorativeLottie
import oolong.client.ui.designsystem.adaptive.LocalWindowSizeClass
import oolong.client.ui.designsystem.adaptive.WindowWidthSizeClass
import oolong.client.ui.designsystem.states.StateScreenScope
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.core.network.TiebaServerException

@Composable
fun TipScreen(
    title: @Composable (ColumnScope.() -> Unit),
    modifier: Modifier = Modifier,
    image: @Composable (ColumnScope.() -> Unit) = {},
    message: @Composable (ColumnScope.() -> Unit) = {},
    actions: @Composable (ColumnScope.() -> Unit) = {},
    scrollable: Boolean = true,
) {
    val scrollableModifier =
        if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    val widthFraction =
        if (LocalWindowSizeClass.current.widthSizeClass == WindowWidthSizeClass.Compact) 0.9f else 0.5f
    // The full width, so the narrower column inside is centred in it rather than left at the start
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(fraction = widthFraction)
                .padding(16.dp)
                .then(scrollableModifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterVertically)
        ) {
            Column(modifier = Modifier.requiredWidthIn(max = 400.dp)) {
                image()
            }
            ProvideTextStyle(
                value = MaterialTheme.typography.h6.copy(
                    color = ExtendedTheme.colors.text,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            ) {
                title()
            }
            ProvideTextStyle(
                value = MaterialTheme.typography.body1.copy(
                    color = ExtendedTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            ) {
                message()
            }
            actions()
        }
    }
}

enum class ErrorType {
    NETWORK,
    SERVER,
    NOT_LOGGED_IN,
    UNKNOWN
}

@Composable
fun StateScreenScope.ErrorScreen(
    error: Throwable?,
    modifier: Modifier = Modifier,
    showReload: Boolean = true,
    actions: @Composable (ColumnScope.() -> Unit) = {},
) {
    error?.let {
        ErrorTipScreen(
            error = it,
            modifier = modifier,
            actions = {
                if (showReload && canReload) {
                    Button(onClick = { reload() }) {
                        Text(text = stringResource(id = R.string.btn_reload))
                    }
                }

                actions()
            }
        )
    }
}

@Composable
fun ErrorTipScreen(
    error: Throwable,
    modifier: Modifier = Modifier,
    actions: @Composable (ColumnScope.() -> Unit) = {},
) {
    val errorType = when (error) {
        is TiebaServerException -> ErrorType.SERVER
        is TiebaNotLoggedInException -> ErrorType.NOT_LOGGED_IN
        else -> if (error.isNetworkError()) ErrorType.NETWORK else ErrorType.UNKNOWN
    }
    val errorMessage = error.getErrorMessage(LocalContext.current)
    val errorCode = error.getErrorCode()
    ErrorTipScreen(
        errorType = errorType,
        errorMessage = errorMessage,
        modifier = modifier,
        errorCode = errorCode,
        actions = {
            actions()
            // An error the app can't explain is kept, and can be passed on from here
            if (error.isUnexpectedError()) ErrorDetailsButton(error)
        },
    )
}

@Composable
fun ErrorTipScreen(
    errorType: ErrorType,
    errorMessage: String,
    modifier: Modifier = Modifier,
    errorCode: Int? = null,
    appendMessage: @Composable (ColumnScope.() -> Unit) = {},
    actions: @Composable (ColumnScope.() -> Unit) = {},
) {
    TipScreen(
        title = {
            when (errorType) {
                ErrorType.NETWORK -> {
                    Text(text = stringResource(id = R.string.title_no_internet_connectivity))
                }

                ErrorType.SERVER -> {
                    Text(text = stringResource(id = R.string.title_api_error))
                }

                ErrorType.UNKNOWN -> {
                    Text(text = stringResource(id = R.string.title_unknown_error))
                }

                ErrorType.NOT_LOGGED_IN -> {
                    Text(text = stringResource(id = R.string.title_not_logged_in))
                }
            }
        },
        modifier = modifier,
        image = {
            when (errorType) {
                ErrorType.NETWORK -> {
                    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_no_internet))
                    DecorativeLottie(
                        composition = composition,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f)
                    )
                }

                ErrorType.SERVER -> {
                    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_error))
                    DecorativeLottie(
                        composition = composition,
                        loops = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f)
                    )
                }

                ErrorType.UNKNOWN -> {
                    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_bug_hunting))
                    DecorativeLottie(
                        composition = composition,
                        loops = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f)
                    )
                }

                ErrorType.NOT_LOGGED_IN -> {
                    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_astronaut))
                    DecorativeLottie(
                        composition = composition,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f)
                    )
                }
            }
        },
        message = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (errorType) {
                    ErrorType.NETWORK -> {
                        Text(text = stringResource(id = R.string.message_no_internet_connectivity))
                    }

                    ErrorType.SERVER -> {
                        val errorCodeText = "($errorCode)".takeIf { errorCode != null }.orEmpty()
                        Text(text = "$errorMessage$errorCodeText")
                    }

                    ErrorType.UNKNOWN -> {
                        Text(text = stringResource(id = R.string.message_unknown_error))
                    }

                    ErrorType.NOT_LOGGED_IN -> {
                        Text(text = stringResource(id = R.string.message_not_logged_in))
                    }
                }
                appendMessage()
            }
        },
        actions = actions
    )
}
