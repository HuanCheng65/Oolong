package oolong.client.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.lerp
import oolong.client.R
import oolong.client.navigation.ForumKey
import oolong.client.navigation.Navigator
import oolong.client.navigation.ThreadKey
import oolong.client.navigation.UserProfileKey
import oolong.client.ui.designsystem.BaseTextField
import oolong.client.ui.designsystem.ChromeStyle
import oolong.client.ui.designsystem.GlassTier
import oolong.client.ui.designsystem.GlassTopBarRowHeight
import oolong.client.ui.designsystem.LocalChromeStyle
import oolong.client.ui.designsystem.currentGlassTier
import oolong.client.ui.designsystem.floatingGlass
import oolong.client.ui.designsystem.floatingPress
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.search.SearchFieldPlace
import oolong.client.ui.search.SearchScreen
import oolong.client.ui.search.SearchState
import oolong.client.ui.search.SearchViewModel
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape
import kotlin.math.roundToInt

/**
 * Search on the main page, opened from the round button beside the floating bar: the button
 * stretches into a field above the keyboard, and the search page's history and results show over
 * the tabs. Closing shrinks the field back into the button, on the tab it was opened from.
 *
 * Where the user keeps search at the top ([oolong.client.data.settings.SettingKeys.SearchAtTop]), and beside the rail, it opens from a
 * field at the top instead ([TopSearchField]), which moves up into the top bar.
 */
@Stable
class MainSearchState(open: Boolean) {
    var open by mutableStateOf(open)
        private set

    /** 0 the button, 1 the field. */
    val progress = Animatable(if (open) 1f else 0f, visibilityThreshold = Motion.ProgressThreshold)

    /**
     * What search opens out of, in the root's pixels: the bar's room for the button, or the home
     * page's field; empty while there is none.
     */
    var buttonBounds by mutableStateOf(Rect.Zero)

    val focusRequester = FocusRequester()

    /** Opened by the user, so the field takes the focus and brings up the keyboard once it shows. */
    var wantsFocus by mutableStateOf(false)

    /**
     * With motion reduced the field doesn't stretch out of the button: it gets there in about the
     * time a fade takes, so what shows is search fading in and out where it stands.
     */
    var reduced = false

    suspend fun open() {
        open = true
        wantsFocus = true
        // The search content is composed and laid out, still unseen, a frame before the button
        // starts to stretch, so that work doesn't hold up the motion's first frame
        withFrameNanos {}
        progress.animateTo(1f, if (reduced) Motion.fadeIn() else Motion.morph())
    }

    suspend fun close() {
        open = false
        progress.animateTo(0f, if (reduced) Motion.fadeOut() else Motion.dismiss())
    }
}

@Composable
fun rememberMainSearchState(): MainSearchState {
    var saved by rememberSaveable { mutableStateOf(false) }
    val state = remember { MainSearchState(saved) }
    saved = state.open
    state.reduced = LocalReduceMotion.current
    return state
}

/**
 * The field of a page that is only search, as searching a forum: open from the start, with no
 * button to grow from or shrink back into. [focus], it brings up the keyboard once it shows.
 */
@Composable
fun rememberOpenSearchState(focus: Boolean): MainSearchState = remember {
    MainSearchState(open = true).apply {
        // Any bounds do: at rest on the field, it never moves from there
        buttonBounds = Rect(0f, 0f, 1f, 1f)
        wantsFocus = focus
    }
}

/** Room the field takes at the bottom, for the search content to keep clear of. */
@Composable
fun mainSearchPadding(atTop: Boolean): PaddingValues =
    if (atTop) {
        WindowInsets.ime.union(WindowInsets.navigationBars).asPaddingValues()
    } else {
        WindowInsets.ime.union(WindowInsets.navigationBars)
            .add(WindowInsets(bottom = FieldHeight + NavPillMargin * 2))
            .asPaddingValues()
    }

/**
 * The field search opens from where it is at the top: under the home page's title on a phone where
 * the user keeps it there, at the end of the top bar beside the rail. What the search field grows
 * out of, in its shape; it reports where it is to [state] and gives way to that field while search
 * shows. [compact], where there is no room for the field, it is a round button.
 */
@Composable
fun TopSearchField(state: MainSearchState, onOpen: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = ExtendedTheme.colors
    // Without a material it is the plain field it was before there was glass
    val plain = currentGlassTier() == GlassTier.Solid
    val interactionSource = remember { MutableInteractionSource() }
    // Gone, as when the window narrows to a phone's and the bar's button takes over, it leaves no
    // place behind for search to open out of
    var placed by remember { mutableStateOf(Rect.Zero) }
    DisposableEffect(state) {
        onDispose { if (state.buttonBounds == placed) state.buttonBounds = Rect.Zero }
    }
    // A new glass for each form, rather than one whose modifiers change under it
    key(compact) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
            modifier = modifier
                .then(if (compact) Modifier.width(FieldHeight) else Modifier.fillMaxWidth())
                .height(FieldHeight)
                .onGloballyPositioned {
                    placed = it.boundsInRoot()
                    state.buttonBounds = placed
                }
                .graphicsLayer { alpha = if (state.progress.value > 0f) 0f else 1f }
                // The same glass as the field it turns into, which answers the press itself; over
                // the page it can't see through, it shows as the glass's solid form
                .then(if (plain) Modifier.floatingPress(CapsuleShape).background(colors.searchField, CapsuleShape) else Modifier.floatingGlass(CapsuleShape, pressable = true))
                .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onOpen)
                .padding(start = if (compact) 0.dp else (FieldHeight - IconSize) / 2),
        ) {
            Icon(
                imageVector = Symbols.Search,
                contentDescription = if (compact) stringResource(id = R.string.hint_search) else null,
                tint = if (plain) colors.onSearchField else colors.text,
                modifier = Modifier.size(IconSize),
            )
            if (!compact) {
                Spacer(modifier = Modifier.width(FieldIconGap))
                Text(
                    text = stringResource(id = R.string.hint_search),
                    color = if (plain) colors.onSearchField else colors.text.copy(alpha = ContentAlpha.medium),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * [TopSearchField] at the end of a top bar, in [room]: as wide as it may be up to [TopBarFieldWidth],
 * or a round button where there is less room than [TopBarFieldLeast].
 */
@Composable
fun TopBarSearchField(state: MainSearchState, room: Dp, onOpen: () -> Unit) {
    val compact = room < TopBarFieldLeast
    TopSearchField(
        state = state,
        onOpen = onOpen,
        compact = compact,
        modifier = if (compact) Modifier else Modifier.width(minOf(room, TopBarFieldWidth)),
    )
}

/** The widest the field gets in a top bar. */
private val TopBarFieldWidth = 320.dp

/** The least room the field takes in a top bar before it folds to a button. */
private val TopBarFieldLeast = 200.dp

/**
 * The button and the field it stretches into, one piece of glass between the bar's room for it
 * and the bottom of the window above the keyboard. Before the bar for screen readers, so search
 * is found without passing the tabs. [atTop], it grows from the home page's field into the top
 * bar instead, and shows only while search does; the button that closes it is then a back
 * button at the start, where the search page's was.
 */
@Composable
fun MainSearchField(
    state: MainSearchState,
    atTop: Boolean,
    input: String,
    onInputChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    placeholder: String = stringResource(id = R.string.hint_search),
) {
    // Read once it changes rather than each frame of the motion, which would compose all this again
    val shown by remember(state) { derivedStateOf { state.progress.value > 0f } }
    if (state.buttonBounds.isEmpty) return
    // At rest the button is drawn where it sits: at the top, or as a classic bar's item
    if ((atTop || LocalChromeStyle.current == ChromeStyle.Classic) && !shown) return
    var origin by remember { mutableStateOf(Offset.Zero) }
    val colors = ExtendedTheme.colors
    // Without a material the button turns into the plain field, as the one at the top is
    val plain = currentGlassTier() == GlassTier.Solid
    val buttonInteraction = remember { MutableInteractionSource() }
    val bottomInsets = WindowInsets.ime.union(WindowInsets.navigationBars)
    val topInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout)
    val sideInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
    ) {
        // The field's and the close button's places, worked out where they are laid out so the
        // keyboard moving them only lays them out again
        fun Modifier.placedAt(bounds: MeasureScope.(width: Int, height: Int) -> Rect) = layout { measurable, constraints ->
            val rect = bounds(constraints.maxWidth, constraints.maxHeight)
            val placeable = measurable.measure(Constraints.fixed(rect.width.roundToInt().coerceAtLeast(0), rect.height.roundToInt().coerceAtLeast(0)))
            layout(placeable.width, placeable.height) { placeable.place(rect.left.roundToInt(), rect.top.roundToInt()) }
        }
        val margin = NavPillMargin

        // Where the field and the close button beside it end up: above the keyboard, or in the
        // top bar's row under the status bar
        fun MeasureScope.fieldBottom(height: Int): Float = if (atTop) {
            topInsets.getTop(this) + (GlassTopBarRowHeight + FieldHeight).toPx() / 2
        } else {
            height - bottomInsets.getBottom(this) - margin.toPx()
        }
        Box(
            modifier = Modifier
                .placedAt { width, height ->
                    val m = margin.toPx()
                    val bottom = fieldBottom(height)
                    val left = sideInsets.getLeft(this, layoutDirection) + m
                    val right = width - sideInsets.getRight(this, layoutDirection) - m
                    val closeSpace = FieldHeight.toPx() + CloseGap.toPx()
                    val field = Rect(
                        left = if (atTop) left + closeSpace else left,
                        top = bottom - FieldHeight.toPx(),
                        right = if (atTop) right else right - closeSpace,
                        bottom = bottom,
                    )
                    lerp(state.buttonBounds.translate(-origin), field, state.progress.value)
                }
                .semantics { traversalIndex = -1f }
                .floatingGlass(CapsuleShape, pressable = !state.open)
                .then(if (plain) Modifier.drawBehind { drawRect(colors.searchField.copy(alpha = colors.searchField.alpha * state.progress.value)) } else Modifier)
                .then(
                    if (state.open) {
                        Modifier
                    } else {
                        Modifier.clickable(
                            interactionSource = buttonInteraction,
                            // The glass answers the press itself
                            indication = null,
                            onClickLabel = stringResource(id = R.string.title_search),
                            role = Role.Button,
                            onClick = onOpen,
                        )
                    }
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    // The icon keeps the same distance from the left as from the top and bottom, so
                    // it sits in the middle of the round button and at the start of the field.
                    // Worked out as it is laid out, as the height changes each frame of the motion.
                    .layout { measurable, constraints ->
                        val inset = ((constraints.maxHeight - IconSize.roundToPx()) / 2).coerceAtLeast(0)
                        val placeable = measurable.measure(constraints.offset(horizontal = -inset))
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(inset, 0) }
                    },
            ) {
                Icon(
                    imageVector = Symbols.Search,
                    contentDescription = if (state.open) null else stringResource(id = R.string.title_search),
                    tint = colors.text,
                    modifier = Modifier.size(IconSize),
                )
                if (shown) {
                    LaunchedEffect(state.wantsFocus) {
                        if (state.wantsFocus) {
                            state.focusRequester.requestFocus()
                            state.wantsFocus = false
                        }
                    }
                    Spacer(modifier = Modifier.width(FieldIconGap))
                    BaseTextField(
                        value = input,
                        onValueChange = onInputChange,
                        enabled = state.open,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(input) }),
                        placeholder = {
                            Text(
                                text = placeholder,
                                color = colors.text.copy(alpha = ContentAlpha.medium),
                                maxLines = 1,
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // Grown from the home page's field, the hint is already there
                            .graphicsLayer { alpha = if (atTop) 1f else ((state.progress.value - 0.4f) / 0.6f).coerceIn(0f, 1f) }
                            .focusRequester(state.focusRequester),
                    )
                    // Comes with the first character typed and goes with the last
                    val reduced = LocalReduceMotion.current
                    AnimatedVisibility(
                        visible = input.isNotEmpty() && state.open,
                        enter = if (reduced) fadeIn(Motion.fadeIn()) else fadeIn(Motion.fadeIn()) + scaleIn(Motion.settle(), initialScale = 0.6f),
                        exit = if (reduced) fadeOut(Motion.fadeOut()) else fadeOut(Motion.fadeOut()) + scaleOut(Motion.dismiss(), targetScale = 0.6f),
                    ) {
                        CompositionLocalProvider(LocalContentColor provides colors.textSecondary) {
                            FieldButton(
                                icon = Symbols.Close,
                                label = stringResource(id = R.string.button_clear),
                                onClick = { onInputChange("") },
                            )
                        }
                    }
                }
            }
        }

        if (shown) {
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .placedAt { width, height ->
                        val m = margin.toPx()
                        val size = FieldHeight.toPx()
                        val bottom = fieldBottom(height)
                        if (atTop) {
                            val left = sideInsets.getLeft(this, layoutDirection) + m
                            Rect(left, bottom - size, left + size, bottom)
                        } else {
                            val right = width - sideInsets.getRight(this, layoutDirection) - m
                            Rect(right - size, bottom - size, right, bottom)
                        }
                    }
                    .graphicsLayer {
                        val p = state.progress.value
                        alpha = p
                        scaleX = lerp(0.6f, 1f, p)
                        scaleY = scaleX
                    }
                    .floatingGlass(CapsuleShape, pressable = state.open)
                    .clickable(
                        interactionSource = interactionSource,
                        // The glass answers the press itself
                        indication = null,
                        role = Role.Button,
                        enabled = state.open,
                        onClick = onClose,
                    ),
            ) {
                Icon(
                    imageVector = if (atTop) Symbols.ArrowBack else Symbols.Close,
                    contentDescription = stringResource(id = if (atTop) R.string.button_back else R.string.button_close_search),
                    tint = colors.text,
                    modifier = Modifier.size(IconSize),
                )
            }
        }
    }
}

@Composable
private fun FieldButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(FieldHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = PressFeedback.overlay(CapsuleShape),
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

private val CapsuleShape = SmoothCornerShape.Capsule
private val FieldHeight = 48.dp
private val CloseGap = 8.dp
private val FieldIconGap = 10.dp
private val IconSize = 24.dp

/** The search page's history, suggestions and results, for the field over them. */
@Composable
fun MainSearchContent(
    state: SearchState,
    atTop: Boolean,
    input: String,
    viewModel: SearchViewModel,
    navigator: Navigator,
    onSearch: (String) -> Unit,
) {
    SearchScreen(
        state = state,
        input = input,
        onInputChange = viewModel::onInputChange,
        onSearch = onSearch,
        onBack = {},
        onOpenTab = viewModel::open,
        onRefreshTab = viewModel::refresh,
        onLoadMoreThreads = viewModel::loadMoreThreads,
        onSetOrder = viewModel::setOrder,
        onForget = viewModel::forget,
        onClearHistory = viewModel::clearHistory,
        onOpenForum = { navigator.navigate(ForumKey(it)) },
        onOpenUser = { navigator.navigate(UserProfileKey(it)) },
        onOpenThread = { navigator.navigate(ThreadKey(threadId = it.threadId)) },
        fieldPlace = if (atTop) SearchFieldPlace.TopBar else SearchFieldPlace.Elsewhere,
    )
}
