package oolong.client.ui.login

import android.graphics.Bitmap
import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.data.error.userMessage
import oolong.client.data.identity.ClientUtils
import oolong.client.data.web.WebSessions
import oolong.client.data.web.parseCookie
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.ui.components.LocalAccountActions
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.rememberSaveableWebViewState
import oolong.client.ui.designsystem.rememberWebViewNavigator
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.webview.MyWebViewClient
import oolong.client.ui.webview.WebPageScaffold
import oolong.client.ui.webview.rememberWebChromeClient
import oolong.client.util.StringUtil
import oolong.core.designsystem.theme.Motion

const val LOGIN_URL =
    "https://wappass.baidu.com/passport?login&u=https%3A%2F%2Ftieba.baidu.com%2Findex%2Ftbwise%2Fmine"

@Composable
fun LoginPage(
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    val accounts = LocalAccountActions.current
    val notices = LocalSignedInNotices.current
    val coroutineScope = rememberCoroutineScope()
    val webViewState = rememberSaveableWebViewState()
    val webViewNavigator = rememberWebViewNavigator()
    val refresh = stringResource(id = R.string.title_refresh)
    var phase by remember { mutableStateOf<LoginPhase>(LoginPhase.Web) }
    // What the web page handed over, kept to try again with
    var handed by remember { mutableStateOf<Credentials?>(null) }
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    var handedOver by remember { mutableStateOf(false) }

    fun logIn(credentials: Credentials) {
        phase = LoginPhase.LoggingIn
        coroutineScope.launch {
            runCatching { accounts.logIn(credentials.bduss, credentials.sToken, credentials.cookie) }
                .onSuccess { account ->
                    WebSessions.instance.adoptLogin(credentials.webView, account)
                    accounts.switchTo(account.id)
                    phase = LoginPhase.Succeeded(
                        name = account.nameShow?.takeIf { it.isNotBlank() } ?: account.name,
                        avatar = StringUtil.getAvatarUrl(account.portrait),
                    )
                }
                .onFailure { phase = LoginPhase.Failed(it.userMessage(context)) }
        }
    }
    // In: the ring closes round the avatar, it stays a moment, and the card goes on as a toast over
    // the page the login was opened from while this page goes back
    LaunchedEffect(phase is LoginPhase.Succeeded) {
        val succeeded = phase as? LoginPhase.Succeeded ?: return@LaunchedEffect
        delay(ShowSuccessMillis)
        if (notices != null) {
            notices.show(succeeded.name, succeeded.avatar, cardBounds)
            handedOver = true
        }
        navigator.navigateUp()
    }

    val recede by animateFloatAsState(
        targetValue = if (phase != LoginPhase.Web) 1f else 0f,
        animationSpec = Motion.page(Motion.ProgressThreshold),
        label = "recede",
    )
    WebPageScaffold(
        initialUrl = LOGIN_URL,
        state = webViewState,
        navigator = webViewNavigator,
        client = remember(navigator) {
            LoginWebViewClient(navigator) { credentials ->
                // Once: the page may report the same login more than once as it goes on
                if (phase == LoginPhase.Web) {
                    handed = credentials
                    logIn(credentials)
                }
            }
        },
        chromeClient = rememberWebChromeClient(),
        onClose = { navigator.navigateUp() },
        menuItems = { listOf(ContextMenuItem(Symbols.Refresh, refresh) { webViewNavigator.reload() }) },
        // A session with no login in it, so the current account isn't found logged in already
        attachSession = { WebSessions.instance.attachForLogin(it) },
        title = stringResource(id = R.string.title_login_baidu),
        recede = { recede },
        overlay = {
            LoginCard(
                phase = phase,
                onRetry = { handed?.let { logIn(it) } },
                onBack = { phase = LoginPhase.Web },
                modifier = Modifier
                    .onGloballyPositioned { cardBounds = it.boundsInWindow() }
                    // The toast took over from here
                    .graphicsLayer { alpha = if (handedOver) 0f else 1f },
            )
        },
    )
}

/** What the web page hands over once logged in, and the web view it is logged in. */
class Credentials(val bduss: String, val sToken: String, val cookie: String, val webView: WebView)

/**
 * Watches the login page for the moment it is logged in: as it goes on to Tieba with the login's
 * cookies set, it is stopped there, and [onLoggedIn] is given them, so the app takes over without
 * the page first showing Tieba.
 */
class LoginWebViewClient(
    nativeNavigator: Navigator?,
    private val onLoggedIn: (Credentials) -> Unit,
) : MyWebViewClient(nativeNavigator) {
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        check(view, url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        check(view, url)
    }

    private fun check(view: WebView, url: String?) {
        if (url == null || LoggedInPages.none { url.startsWith(it) }) return
        // Read from the page's own session, which the login is made in
        val cookie = WebSessions.instance.cookies(view, url) ?: return
        val cookies = parseCookie(cookie).mapKeys { it.key.uppercase() }
        val bduss = cookies["BDUSS"] ?: return
        val sToken = cookies["STOKEN"] ?: return
        view.stopLoading()
        val baiduId = cookies["BAIDUID"]
        if (!baiduId.isNullOrEmpty() && ClientUtils.baiduId.isNullOrEmpty()) {
            MainScope().launch { ClientUtils.saveBaiduId(context, baiduId) }
        }
        onLoggedIn(Credentials(bduss, sToken, cookie, view))
    }

    private companion object {
        /** Where the login page goes once logged in. */
        val LoggedInPages = listOf("https://tieba.baidu.com/index/tbwise/", "https://tiebac.baidu.com/index/tbwise/")
    }
}

/** How long the card shows the account before it goes on as a toast: the ring closing, the avatar coming up, a moment. */
private const val ShowSuccessMillis = 1_200L
