package oolong.client.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.ContextThemeWrapper
import android.view.PixelCopy
import android.view.View
import android.webkit.URLUtil
import android.webkit.WebResourceError
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import oolong.client.R
import oolong.client.data.web.WebSessions
import oolong.client.media.FileUtil
import oolong.client.model.PhotoViewData
import oolong.client.model.PicItem
import oolong.client.ui.components.ErrorTipScreen
import oolong.client.ui.components.ErrorType
import oolong.client.ui.components.TipScreen
import oolong.client.ui.designsystem.AccompanistWebChromeClient
import oolong.client.ui.designsystem.AccompanistWebViewClient
import oolong.client.ui.designsystem.AppDialog
import oolong.client.ui.designsystem.AppDialogs
import oolong.client.ui.designsystem.Button
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarItemHeight
import oolong.client.ui.designsystem.IconButton
import oolong.client.ui.designsystem.LazyLoad
import oolong.client.ui.designsystem.LoadingState
import oolong.client.ui.designsystem.LocalContextMenus
import oolong.client.ui.designsystem.MenuIconButton
import oolong.client.ui.designsystem.Reveal
import oolong.client.ui.designsystem.WebView
import oolong.client.ui.designsystem.WebViewError
import oolong.client.ui.designsystem.WebViewNavigator
import oolong.client.ui.designsystem.WebViewState
import oolong.client.ui.designsystem.floatingGlass
import oolong.client.ui.designsystem.pageScroll
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.photoview.LocalPhotoViewer
import oolong.client.util.TiebaUtil
import oolong.client.util.findActivity
import oolong.client.util.toastShort
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.SmoothCornerShape
import oolong.core.designsystem.theme.TextStyles
import kotlin.coroutines.resume
import android.graphics.Rect as AndroidRect

/**
 * A page showing a web page: the bar with its title and address over the web view, which starts
 * below the bar, as the web view can't be drawn into the bar's glass.
 *
 * @param initialUrl loaded once, when the page first shows.
 * @param title shown in place of the page's own title, as on the login page.
 * @param menuItems the bar's menu; none leaves the menu out.
 * @param attachSession gives the web view, before it loads anything, the login it is to have; see [WebSessions].
 * @param recede how far the page has stepped back, dimmed and taken no touches, for [overlay] to stand
 * over it: 0 not at all, 1 fully.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebPageScaffold(
    initialUrl: String,
    state: WebViewState,
    navigator: WebViewNavigator,
    client: AccompanistWebViewClient,
    chromeClient: AccompanistWebChromeClient,
    onClose: () -> Unit,
    menuItems: () -> List<ContextMenuItem>,
    attachSession: (android.webkit.WebView) -> Unit,
    recede: () -> Float = { 0f },
    overlay: @Composable BoxScope.() -> Unit = {},
    title: String? = null,
    immersive: Boolean = false,
    scaffoldState: ScaffoldState = rememberScaffoldState(),
) {
    var loaded by rememberSaveable { mutableStateOf(false) }
    // The last title the page gave, kept while the next page loads
    var pageTitle by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state) {
        snapshotFlow { state.pageTitle }
            .filterNotNull()
            .filter { it.isNotEmpty() }
            .collect { pageTitle = it }
    }
    LazyLoad(loaded = loaded) {
        navigator.loadUrl(initialUrl)
        loaded = true
    }

    // Before the first page starts, the one it is going to
    val currentUrl by remember { derivedStateOf { state.lastLoadedUrl ?: initialUrl } }
    val fill = rememberLoadProgress(state)
    // The page could not be reached: said by the app, with a way to try again, instead of the web
    // view's own error page, whose title is not the page's either
    val error by remember {
        derivedStateOf { state.errorsForCurrentRequest.firstOrNull { it.isPageFailure() }?.error }
    }
    val menus = LocalContextMenus.current
    val targetMenu = rememberTargetMenu()
    val askDownload = rememberDownloadAsk()
    // Immersive: how far the page has pushed the bar up, going up after it
    var consumedInset by remember { mutableIntStateOf(0) }
    var insetPx by remember { mutableIntStateOf(0) }
    var pageScrollY by remember { mutableIntStateOf(0) }
    // The colour along the page's top, which the status bar and the bar lie on, so they and the
    // page are one piece
    var pageColor by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(state) {
        snapshotFlow { Triple(state.loadingState is LoadingState.Finished, consumedInset, pageScrollY) }
            .collectLatest { (finished, _, _) ->
                if (!finished) return@collectLatest
                // Once the page has settled
                delay(PageColorDelay)
                state.webView?.let { view -> sampleTopColor(view)?.let { pageColor = it } }
            }
    }
    val colors = ExtendedTheme.colors
    val ground by animateColorAsState(
        targetValue = if (error != null) colors.background else pageColor ?: colors.background,
        animationSpec = Motion.fadeIn(),
        label = "pageColor",
    )
    StatusBarIcons(dark = ground.luminance() > 0.5f)

    GlassScaffold(
        scaffoldState = scaffoldState,
        sampleContent = false,
        backgroundColor = ground,
        topBar = {
            GlassTopBar(
                glassButtons = true,
                hiddenFraction = { if (insetPx > 0) consumedInset.toFloat() / insetPx else 0f },
                title = {
                    var bounds by remember { mutableStateOf(Rect.Zero) }
                    AddressCapsule(
                        title = title ?: if (error != null) "" else pageTitle,
                        url = currentUrl,
                        fill = fill,
                        modifier = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
                        onClick = {
                            val items = menuItems()
                            if (menus != null && items.isNotEmpty()) menus.show(items, anchor = bounds)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Symbols.Close,
                            contentDescription = stringResource(id = R.string.btn_close),
                            tint = ExtendedTheme.colors.topBarContent,
                        )
                    }
                },
                actions = {
                    MenuIconButton(
                        icon = Symbols.MoreVert,
                        contentDescription = stringResource(id = R.string.btn_more),
                        items = menuItems,
                    )
                },
            )
        }
    ) { paddingValues ->
        // Until the page first draws, the web view shows the page's background, not white
        val background = colors.background.toArgb()
        LaunchedEffect(state.webView, background) { state.webView?.setBackgroundColor(background) }
        val night = colors.isNightMode
        // Immersive, the page starts below the bar and pushes it up as it scrolls, up to the status
        // bar and never under it, where a header the page keeps at its top would be hidden
        val statusBar = WindowInsets.statusBars.union(WindowInsets.captionBar).asPaddingValues().calculateTopPadding()
        val inset = with(LocalDensity.current) { (paddingValues.calculateTopPadding() - statusBar).roundToPx().coerceAtLeast(0) }
        SideEffect { insetPx = inset }
        LaunchedEffect(state.webView, inset) { (state.webView as? InsetWebView)?.topInset = inset }
        // A new page starts at its top, below the bar
        LaunchedEffect(state.webView, currentUrl) { (state.webView as? InsetWebView)?.showInset() }
        val layoutDirection = LocalLayoutDirection.current
        WebView(
            state = state,
            modifier = if (immersive) {
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = statusBar,
                        start = paddingValues.calculateStartPadding(layoutDirection),
                        end = paddingValues.calculateEndPadding(layoutDirection),
                        bottom = paddingValues.calculateBottomPadding(),
                    )
            } else {
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            }
                // Nothing ever comes under the bar, so its scroll edge never shows
                .pageScroll(scrollable = null) { 0 }
                // Stepped back behind the overlay: smaller, rounded and dimmed
                .graphicsLayer {
                    val r = recede()
                    val scale = 1f - RecedeScale * r
                    scaleX = scale
                    scaleY = scale
                    shape = RoundedCornerShape(RecedeCorner * r)
                    clip = r > 0f
                },
            navigator = navigator,
            onCreated = {
                // Before anything loads: whose login the page gets
                attachSession(it)
                ClipboardGuard.install(it)
                it.setBackgroundColor(background)
                it.setDownloadListener { url, userAgent, disposition, mimeType, length ->
                    // The load became a download and never finishes as a page
                    state.loadingState = LoadingState.Finished
                    // Opened only for the file, the page has nothing to show afterwards
                    val opened = state.webView?.copyBackForwardList()?.size ?: 0
                    val cookie = WebSessions.instance.cookies(it, url)
                    askDownload(url, userAgent, cookie, disposition, mimeType, length) { if (opened == 0) onClose() }
                }
                it.onLongPressTarget { target ->
                    val items = targetMenu(target)
                    if (menus != null && items.isNotEmpty()) menus.show(items, anchor = Rect(target.at, Size(1f, 1f)))
                }
                it.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                // Pages without a dark look of their own are darkened in the dark appearance
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(it.settings, true)
                }
            },
            client = client,
            chromeClient = chromeClient,
            // The web view takes its light or dark look from its context's theme, which follows the
            // app's appearance rather than the system's
            factory = { context ->
                val themed = ContextThemeWrapper(context, if (night) R.style.ThemeOverlay_WebView_Dark else R.style.ThemeOverlay_WebView_Light)
                if (immersive) {
                    InsetWebView(themed).apply {
                        topInset = inset
                        onScrolled = { consumed, scrollY ->
                            consumedInset = consumed
                            pageScrollY = scrollY
                        }
                    }
                } else {
                    android.webkit.WebView(themed)
                }
            },
        )
        // A video full screen covers the page; back leaves full screen first, and so does leaving
        val fullscreen = chromeClient as? MyWebChromeClient
        BackHandler(enabled = fullscreen?.isFullscreen == true) { fullscreen?.exitFullscreen() }
        DisposableEffect(fullscreen) { onDispose { fullscreen?.exitFullscreen() } }
        // A failure fades in over the page, and out once it loads, keeping what it said as it goes
        var lastError by remember { mutableStateOf(error) }
        if (error != null) lastError = error
        AnimatedVisibility(visible = error != null, enter = fadeIn(Motion.fadeIn()), exit = fadeOut(Motion.fadeOut())) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(colors.background),
                contentAlignment = Alignment.Center,
            ) {
                lastError?.let { PageError(error = it, onReload = { navigator.reload() }) }
            }
        }
        val receded by remember { derivedStateOf { recede() > 0f } }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = RecedeDim * recede())) }
                // While stepped back, the page takes no touches
                .then(if (receded) Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } } else Modifier),
            contentAlignment = Alignment.Center,
            content = overlay,
        )
    }
}

private const val RecedeScale = 0.08f
private val RecedeCorner = 24.dp
private const val RecedeDim = 0.35f

/**
 * Why the page could not open: no network at all, which the app's other pages say the same way, or
 * what went wrong with the site.
 */
@Composable
private fun PageError(error: WebResourceError, onReload: () -> Unit) {
    val context = LocalContext.current
    val offline = remember(error) { !hasNetwork(context) }
    val reload: @Composable ColumnScope.() -> Unit = {
        Button(onClick = onReload) {
            Text(text = stringResource(id = R.string.btn_reload))
        }
    }
    if (offline) {
        ErrorTipScreen(errorType = ErrorType.NETWORK, errorMessage = "", actions = reload)
        return
    }
    TipScreen(
        title = { Text(text = stringResource(id = R.string.web_error_title)) },
        message = {
            Text(
                text = stringResource(
                    id = when (error.errorCode) {
                        WebViewClient.ERROR_HOST_LOOKUP -> R.string.web_error_not_found
                        WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT, WebViewClient.ERROR_IO -> R.string.web_error_no_response
                        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> R.string.web_error_insecure
                        else -> R.string.web_error_other
                    }
                )
            )
        },
        actions = reload,
    )
}

/** Whether the device has a network that reaches the internet at all, checked or not. */
private fun hasNetwork(context: Context): Boolean {
    val manager = context.getSystemService<ConnectivityManager>() ?: return true
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** Whether it is the page itself that failed, not something on it, nor a load that was called off. */
private fun WebViewError.isPageFailure(): Boolean =
    request?.isForMainFrame == true && error.description?.contains("ERR_ABORTED") != true

/** How far the page has loaded, and how much the fill showing it shows. */
@Stable
private class LoadProgress {
    val value = Animatable(0f)
    val shown = Animatable(0f)
}

/**
 * Follows [state]'s loading: the fill shows only for loads that take longer than [ProgressDelay],
 * never goes back within a load, and on finishing runs to the end before it fades.
 */
@Composable
private fun rememberLoadProgress(state: WebViewState): LoadProgress {
    val progress = remember { LoadProgress() }
    LaunchedEffect(state) {
        snapshotFlow { state.loadingState is LoadingState.Loading }
            .distinctUntilChanged()
            .collectLatest { loading ->
                if (loading) {
                    // A bar still showing from the last load fades before starting again from 0
                    if (progress.shown.value > 0f) progress.shown.animateTo(0f, Motion.fadeOut())
                    progress.value.snapTo(0f)
                    progress.shown.snapTo(0f)
                    coroutineScope {
                        launch {
                            delay(ProgressDelay)
                            progress.shown.animateTo(1f, Motion.fadeIn())
                        }
                        snapshotFlow { (state.loadingState as? LoadingState.Loading)?.progress ?: 0f }
                            .collect { reported ->
                                if (reported > progress.value.targetValue) {
                                    launch { progress.value.animateTo(reported, Motion.settle(Motion.ProgressThreshold)) }
                                }
                            }
                    }
                } else if (progress.shown.value > 0f) {
                    progress.value.animateTo(1f, Motion.settle(Motion.ProgressThreshold))
                    progress.shown.animateTo(0f, Motion.fadeOut())
                }
            }
    }
    return progress
}

/** Loads quicker than this show no progress, so a fast page doesn't flash it. */
private const val ProgressDelay = 200L

/**
 * The page's title and, under it, the site it is on, in a capsule that fills from its start as the
 * page loads. The domain loses its start rather than its end when it is too long, as its end names
 * the site; a page over plain HTTP is marked as not secure. Until the page gives a title, the
 * domain stands alone in its place. Tapping it opens the page's menu.
 */
@Composable
private fun AddressCapsule(
    title: String,
    url: String?,
    fill: LoadProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ExtendedTheme.colors
    val uri = remember(url) { url?.toUri() }
    val host = uri?.host.orEmpty().lowercase().removePrefix("www.")
    val insecure = host.isNotEmpty() && uri?.scheme.equals("http", ignoreCase = true)
    val fillColor = colors.accent.copy(alpha = if (colors.isNightMode) 0.20f else 0.16f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(GlassTopBarItemHeight)
            .floatingGlass(CapsuleShape, pressable = true)
            // Always in the chain, drawing nothing while idle: the glass under it must not see its
            // modifiers change while it draws. The capsule filled as far as the page has loaded
            .drawBehind {
                val shown = fill.shown.value
                if (shown > 0f) {
                    clipRect(right = size.width * fill.value.value) {
                        drawOutline(
                            CapsuleShape.createOutline(size, layoutDirection, this),
                            fillColor.copy(alpha = fillColor.alpha * shown),
                        )
                    }
                }
            }
            // The glass answers the press
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        // The title opens above the address once the page has one, rather than pushing it in a frame
        val titleLine: @Composable (String) -> Unit = {
            Text(
                text = it,
                style = TextStyles.label,
                color = colors.topBarContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (host.isEmpty()) titleLine(title) else Reveal(title.takeIf { it.isNotEmpty() }) { titleLine(it) }
        if (host.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (insecure) {
                    val warning = MaterialTheme.colors.error
                    Icon(
                        imageVector = Symbols.Warning,
                        contentDescription = null,
                        tint = warning,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = stringResource(id = R.string.web_not_secure),
                        style = TextStyles.footnote,
                        fontWeight = FontWeight.Medium,
                        color = warning,
                        modifier = Modifier.padding(start = 2.dp, end = 4.dp),
                        maxLines = 1,
                    )
                }
                val alone = title.isEmpty()
                Text(
                    text = host,
                    style = if (alone) TextStyles.label else TextStyles.footnote,
                    color = if (alone) colors.topBarContent else colors.topBarContentSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                )
            }
        }
    }
}

private val CapsuleShape = SmoothCornerShape.Capsule

/** How long the page must stay still before its colour is taken. */
private const val PageColorDelay = 150L

/** How many points along the page's top its colour is taken from. */
private const val ColorSamples = 24

/**
 * The colour most of the page's top row has, as drawn on screen, so dimmed in the dark appearance as
 * the page is. Null before Android 8, where the screen can't be read back.
 */
private suspend fun sampleTopColor(view: View): Color? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !view.isAttachedToWindow || view.width == 0) return null
    val window = view.context.findActivity()?.window ?: return null
    val location = IntArray(2)
    // In the window, with the view's move taken in
    view.getLocationInWindow(location)
    val top = location[1] + 1
    val bitmap = Bitmap.createBitmap(ColorSamples, 1, Bitmap.Config.ARGB_8888)
    val copied = suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(
                window,
                AndroidRect(location[0], top, location[0] + view.width, top + 2),
                bitmap,
                { result -> continuation.resume(result == PixelCopy.SUCCESS) },
                Handler(Looper.getMainLooper()),
            )
        } catch (_: IllegalArgumentException) {
            // The window has no surface to copy from, as when the app has gone to the background
            continuation.resume(false)
        }
    }
    if (!copied) {
        bitmap.recycle()
        return null
    }
    val pixels = IntArray(ColorSamples).also { bitmap.getPixels(it, 0, ColorSamples, 0, 0, ColorSamples, 1) }
    bitmap.recycle()
    return Color(pixels.toList().groupingBy { it }.eachCount().maxBy { it.value }.key)
}

/** Dark or light status bar icons while the page shows, as the colour under them needs. */
@Composable
private fun StatusBarIcons(dark: Boolean) {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, dark) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val before = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = dark
        onDispose { controller.isAppearanceLightStatusBars = before }
    }
}

/** The menu for a picture or link pressed and held on the page. */
@Composable
private fun rememberTargetMenu(): (PageTarget) -> List<ContextMenuItem> {
    val context = LocalContext.current
    val photoViewer = LocalPhotoViewer.current
    val viewImage = stringResource(id = R.string.web_view_image)
    val copyImageLink = stringResource(id = R.string.web_copy_image_link)
    val copyLink = stringResource(id = R.string.title_copy_link)
    val openInBrowser = stringResource(id = R.string.title_open_in_browser)
    val share = stringResource(id = R.string.title_share)
    return remember(context, photoViewer) {
        { target ->
            buildList {
                target.image?.let { image ->
                    if (photoViewer != null) {
                        add(
                            ContextMenuItem(Symbols.Image, viewImage) {
                                val item = PicItem(picId = image, picIndex = 1, url = image, originUrl = image, showOriginBtn = false, originSize = 0)
                                photoViewer.open(PhotoViewData(picItems = persistentListOf(item)))
                            }
                        )
                    }
                    add(ContextMenuItem(Symbols.ContentCopy, copyImageLink) { TiebaUtil.copyText(context, image) })
                }
                target.link?.let { link ->
                    add(ContextMenuItem(Symbols.Link, copyLink, startsGroup = target.image != null) { TiebaUtil.copyText(context, link) })
                    add(
                        ContextMenuItem(Symbols.OpenInBrowser, openInBrowser) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri())) }
                        }
                    )
                    add(ContextMenuItem(Symbols.Share, share) { TiebaUtil.shareText(context, link) })
                }
            }
        }
    }
}

/**
 * Asks before downloading a file the page links to, naming it and its size, then hands it to the
 * system's downloads with the page's cookies, which a download behind a login needs.
 */
@Composable
private fun rememberDownloadAsk(): (url: String, userAgent: String?, cookie: String?, disposition: String?, mimeType: String?, length: Long, onAnswered: () -> Unit) -> Unit {
    val context = LocalContext.current
    val title = stringResource(id = R.string.web_download_title)
    val download = stringResource(id = R.string.web_download)
    val cancel = stringResource(id = R.string.button_cancel)
    val started = stringResource(id = R.string.web_download_started)
    return remember(context) {
        { url, userAgent, cookie, disposition, mimeType, length, onAnswered ->
            val fileName = URLUtil.guessFileName(url, disposition, mimeType)
            val size = if (length > 0) "（${Formatter.formatShortFileSize(context, length)}）" else ""
            AppDialogs.show(
                AppDialog.Message(
                    message = fileName + size,
                    title = title,
                    confirm = download,
                    onConfirm = {
                        val headers = buildMap {
                            cookie?.let { put("Cookie", it) }
                            userAgent?.let { put("User-Agent", it) }
                        }
                        FileUtil.downloadBySystem(context, FileUtil.FILE_TYPE_DOWNLOAD, url, fileName, headers, mimeType)
                        context.toastShort(started)
                        onAnswered()
                    },
                    cancel = cancel,
                    onCancel = onAnswered,
                )
            )
        }
    }
}
