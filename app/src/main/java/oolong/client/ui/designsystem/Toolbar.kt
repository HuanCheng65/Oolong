package oolong.client.ui.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.ui.designsystem.theme.ExtendedTheme

@Composable
fun ActionItem(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    ProvideContentColor(color = ExtendedTheme.colors.topBarContent) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription
            )
        }
    }
}

@Composable
fun BackNavigationIcon(onBackPressed: () -> Unit) {
    ProvideContentColor(color = ExtendedTheme.colors.topBarContent) {
        IconButton(onClick = onBackPressed) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_round_arrow_back),
                contentDescription = stringResource(id = R.string.button_back)
            )
        }
    }
}

@Deprecated(
    "Use the non deprecated overload",
    ReplaceWith(
        """TitleCentredToolbar(
                title = { Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.h6) },
                modifier = modifier,
                insets = insets,
                navigationIcon = navigationIcon,
                actions = actions,
                content = content
            )""",
        "androidx.compose.ui.text.font.FontWeight",
        "androidx.compose.material.MaterialTheme",
        "androidx.compose.material.Text"
    )
)
@Composable
fun TitleCentredToolbar(
    title: String,
    modifier: Modifier = Modifier,
    insets: Boolean = true,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    TitleCentredToolbar(
        title = {
            Text(text = title)
        },
        modifier = modifier,
        insets = insets,
        navigationIcon = navigationIcon,
        actions = actions,
        content = content
    )
}

@Composable
fun TitleCentredToolbar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    insets: Boolean = true,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollToTop: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    TopAppBarContainer(
        topBar = {
            TopAppBar(
                backgroundColor = ExtendedTheme.colors.background,
                contentColor = ExtendedTheme.colors.topBarContent,
                elevation = 0.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        ProvideContentColor(color = ExtendedTheme.colors.topBarContent) {
                            navigationIcon?.invoke()

                            Spacer(modifier = Modifier.weight(1f))

                            actions()
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxHeight()
                            .align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ProvideTextStyle(value = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold)) {
                            ProvideContentColor(color = ExtendedTheme.colors.topBarContent) {
                                title()
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier,
        insets = insets,
        scrollToTop = scrollToTop,
        content = content
    )
}

@Composable
fun Toolbar(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Toolbar(
        title = {
            Text(text = title)
        },
        navigationIcon = navigationIcon,
        actions = actions,
        content = content
    )
}

@Composable
fun Toolbar(
    title: @Composable (() -> Unit),
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    backgroundColor: Color = ExtendedTheme.colors.background,
    contentColor: Color = ExtendedTheme.colors.topBarContent,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    TopAppBarContainer(
        topBar = {
            TopAppBar(
                title = {
                    ProvideTextStyle(value = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold)) {
                        ProvideContentColor(color = contentColor, content = title)
                    }
                },
                actions = {
                    ProvideContentColor(color = contentColor) {
                        actions()
                    }
                },
                navigationIcon = (@Composable {
                    ProvideContentColor(color = contentColor) {
                        navigationIcon?.invoke()
                    }
                }).takeIf { navigationIcon != null },
                backgroundColor = backgroundColor,
                contentColor = contentColor,
                elevation = 0.dp
            )
        },
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopAppBarContainer(
    topBar: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    insets: Boolean = true,
    // What a double tap on the bar does; by default the page's content in view goes back to its top
    scrollToTop: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val statusBarModifier = if (insets) {
        Modifier.windowInsetsTopHeight(WindowInsets.statusBars)
    } else {
        Modifier
    }
    val coroutineScope = rememberCoroutineScope()
    val page = LocalPageScroll.current
    val onDoubleTap by rememberUpdatedState(scrollToTop ?: { coroutineScope.launch { page?.scrollToTop() } })
    Column(modifier) {
        Spacer(
            modifier = statusBarModifier
                .fillMaxWidth()
                .background(color = ExtendedTheme.colors.background)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // A touch shortcut only, as on the glass bar
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap() }) },
            content = topBar
        )
        content?.let {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = ExtendedTheme.colors.background),
            ) {
                content()
            }
        }
    }
}
