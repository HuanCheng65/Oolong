package oolong.client.ui.page.main.user

import android.graphics.Typeface
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import oolong.client.Incognito
import oolong.client.R
import oolong.client.model.HistoryEntry
import oolong.client.models.database.Account
import oolong.client.rememberPreferenceAsMutableState
import oolong.client.ui.common.LocalAccount
import oolong.client.ui.common.theme.compose.Corners
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.navigation.AboutKey
import oolong.client.ui.navigation.AppThemeKey
import oolong.client.ui.navigation.HistoryKey
import oolong.client.ui.navigation.LoginKey
import oolong.client.ui.navigation.PrivacySettingsKey
import oolong.client.ui.navigation.SettingsKey
import oolong.client.ui.navigation.ThreadStoreKey
import oolong.client.ui.navigation.UserFollowsKey
import oolong.client.ui.navigation.UserProfileKey
import oolong.client.ui.navigation.WebViewKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.settings.custom.currentThemeName
import oolong.client.ui.widgets.compose.ActionItem
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.Button
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassToast
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.ListMenuItem
import oolong.client.ui.widgets.compose.LocalChromePadding
import oolong.client.ui.widgets.compose.PullRefresh
import oolong.client.ui.widgets.compose.Sizes
import oolong.client.ui.widgets.compose.placeholder.placeholder
import oolong.client.ui.widgets.compose.verticalScrollUnderBar
import oolong.client.utils.CuidUtils
import oolong.client.utils.StringUtil
import oolong.client.utils.ThemeUtil

@Composable
private fun StatCardPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.placeholder(visible = true, color = ExtendedTheme.colors.chip, shape = RectangleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCardItem(
            statNum = 0,
            statText = stringResource(id = R.string.text_stat_follow)
        )
        StatCardItem(
            statNum = 0,
            statText = stringResource(id = R.string.text_stat_fans)
        )
        StatCardItem(
            statNum = 0,
            statText = stringResource(id = R.string.title_stat_posts_num)
        )
    }
}

@Composable
private fun StatCard(
    account: Account,
    onOpenFollows: (fans: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val postNum by animateIntAsState(targetValue = account.postNum?.toIntOrNull() ?: 0, animationSpec = Motion.settle())
    val fansNum by animateIntAsState(targetValue = account.fansNum?.toIntOrNull() ?: 0, animationSpec = Motion.settle())
    val concernNum by animateIntAsState(targetValue = account.concernNum?.toIntOrNull() ?: 0, animationSpec = Motion.settle())
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCardItem(
            statNum = concernNum,
            statText = stringResource(id = R.string.text_stat_follow),
            onClick = { onOpenFollows(false) },
        )
        StatCardItem(
            statNum = fansNum,
            statText = stringResource(id = R.string.text_stat_fans),
            onClick = { onOpenFollows(true) },
        )
        StatCardItem(
            statNum = postNum,
            statText = stringResource(id = R.string.title_stat_posts_num)
        )
    }
}

@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    userName: String = "",
    userIntro: String = "",
    avatar: String? = null,
    isPlaceholder: Boolean = false
) {
    Row(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.Bottom)
        ) {
            Text(
                text = userName,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                color = ExtendedTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .placeholder(visible = isPlaceholder, color = ExtendedTheme.colors.chip, shape = RectangleShape),
            )
            Spacer(modifier = Modifier.height(4.dp))
            // The arrow says the header opens the profile
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .placeholder(visible = isPlaceholder, color = ExtendedTheme.colors.chip, shape = RectangleShape),
            ) {
                Text(
                    text = userIntro,
                    fontSize = 12.sp,
                    color = ExtendedTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!isPlaceholder) {
                    Icon(
                        imageVector = Symbols.ChevronRight,
                        contentDescription = null,
                        tint = ExtendedTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        if (avatar != null) {
            Avatar(
                data = avatar,
                size = Sizes.Large,
                contentDescription = stringResource(id = R.string.desc_user_avatar),
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .placeholder(visible = isPlaceholder, color = ExtendedTheme.colors.chip, shape = RectangleShape),
            )
        }
    }
}

/**
 * A figure over its label, both from the left, as in a forum's header: the first figure lines up
 * with the name above and the menu icons below.
 */
@Composable
private fun StatCardItem(
    statNum: Int,
    statText: String,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .padding(end = 32.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "$statNum",
            fontSize = 28.sp,
            lineHeight = 28.sp,
            fontFamily = FontFamily(Typeface.createFromAsset(LocalContext.current.assets, "bebas.ttf")),
            color = ExtendedTheme.colors.text,
            style = LocalTextStyle.current.copy(
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = statText,
            fontSize = 12.sp,
            color = ExtendedTheme.colors.textSecondary
        )
    }
}

@Composable
private fun LoginTipCard(onLogin: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.Bottom),
        ) {
            Text(
                text = stringResource(id = R.string.title_not_logged_in),
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                color = ExtendedTheme.colors.text,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onLogin) {
                Text(text = stringResource(id = R.string.title_login), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Symbols.AccountCircle,
            contentDescription = null,
            tint = ExtendedTheme.colors.onChip,
            modifier = Modifier
                .align(Alignment.Bottom)
                .clip(CircleShape)
                .size(Sizes.Large)
                .background(color = ExtendedTheme.colors.chip)
                .padding(16.dp),
        )
    }
}

/**
 * A big entry for what the user goes back to most, on the inset colour like the home page's
 * search field: a button, not a card holding things. [detail] is a line under its name.
 */
@Composable
private fun EntryBlock(
    icon: ImageVector,
    text: String,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(Corners.lg)
            .background(ExtendedTheme.colors.inset)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 92.dp)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = ExtendedTheme.colors.text, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Column {
            Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ExtendedTheme.colors.text, maxLines = 1)
            Text(
                // Kept even when empty, so the two blocks stay the same height
                text = detail.orEmpty(),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = ExtendedTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** What ends a row that opens a page: the value it shows, then the arrow. */
@Composable
private fun RowValue(text: String) {
    Text(text = text, color = ExtendedTheme.colors.textSecondary, fontSize = 12.sp, maxLines = 1)
    Spacer(modifier = Modifier.width(4.dp))
    Icon(
        imageVector = Symbols.ChevronRight,
        contentDescription = null,
        tint = ExtendedTheme.colors.textSecondary.copy(alpha = 0.6f),
        modifier = Modifier.size(20.dp),
    )
}

@Composable
fun UserPage(
    viewModel: UserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account = LocalAccount.current
    LaunchedEffect(account?.uid) { viewModel.refresh() }
    UserScreen(
        account = account,
        isLoading = state.isLoading,
        onRefresh = viewModel::refresh,
        lastThread = state.lastThread,
        storedWithReplies = state.storedWithReplies,
        updateAvailable = state.updateAvailable,
    )
}

private val StatsPadding = Modifier
    .fillMaxWidth()
    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

/**
 * The "me" tab: who is logged in with their figures, or a prompt to log in; the stored threads and
 * the history as two big entries; then the other menus. The night switch is a button in the bar.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun UserScreen(
    account: Account?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    lastThread: HistoryEntry.Thread? = null,
    storedWithReplies: Int = 0,
    updateAvailable: Boolean = false,
) {
    val navigator = LocalNavigator.current
    val dark = ExtendedTheme.colors.isNightMode
    val incognito by Incognito.on.collectAsState()
    val keepsHistory by Incognito.keepsHistory.collectAsState()
    var explained by rememberPreferenceAsMutableState(IncognitoExplainedKey, false)
    // Said each time it is turned on; the first time, with what it does and doesn't cover
    var incognitoHint by remember { mutableStateOf(false) }
    var explaining by remember { mutableStateOf(false) }
    // With history turned off in the settings the mask shows on, and tapped says why
    var historyOffHint by remember { mutableStateOf(false) }
    LaunchedEffect(incognitoHint) {
        if (incognitoHint) {
            delay(if (explaining) IncognitoExplainMillis else IncognitoHintMillis)
            incognitoHint = false
        }
    }
    LaunchedEffect(historyOffHint) {
        if (historyOffHint) {
            delay(IncognitoHintMillis)
            historyOffHint = false
        }
    }
    LaunchedEffect(incognito) { if (!incognito) incognitoHint = false }
    LaunchedEffect(keepsHistory) { if (keepsHistory) historyOffHint = false }
    val masked = incognito || !keepsHistory
    val chromeBottom = LocalChromePadding.current.calculateBottomPadding()

    GlassScaffold(
        backgroundColor = Color.Transparent,
        overlay = {
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = chromeBottom + IncognitoHintGap),
            ) {
                GlassToast(
                    visible = incognitoHint,
                    text = stringResource(id = R.string.incognito_on_hint),
                    action = stringResource(id = R.string.button_undo),
                    onAction = { Incognito.set(false) },
                    icon = painterResource(id = R.drawable.ic_me_incognito_on),
                    detail = if (explaining) stringResource(id = R.string.incognito_explain) else null,
                )
                GlassToast(
                    visible = historyOffHint,
                    text = stringResource(id = R.string.history_off),
                    action = stringResource(id = R.string.title_settings),
                    onAction = {
                        historyOffHint = false
                        navigator.navigate(PrivacySettingsKey)
                    },
                    icon = painterResource(id = R.drawable.ic_me_incognito_on),
                )
            }
        },
        topBar = {
            GlassTopBar(
                title = {},
                actions = {
                    ActionItem(
                        icon = ImageVector.vectorResource(id = if (masked) R.drawable.ic_me_incognito_on else R.drawable.ic_me_incognito),
                        contentDescription = stringResource(
                            id = when {
                                !keepsHistory -> R.string.history_off
                                incognito -> R.string.incognito_off
                                else -> R.string.incognito_on
                            }
                        ),
                        onClick = {
                            if (!keepsHistory) {
                                historyOffHint = true
                            } else if (incognito) {
                                Incognito.set(false)
                            } else {
                                Incognito.set(true)
                                explaining = !explained
                                explained = true
                                incognitoHint = true
                            }
                        },
                    )
                    ActionItem(
                        icon = ImageVector.vectorResource(id = if (dark) R.drawable.ic_me_night_on else R.drawable.ic_me_night),
                        contentDescription = stringResource(id = if (dark) R.string.night_mode_off else R.string.night_mode_on),
                        onClick = { ThemeUtil.toggleDark() },
                    )
                },
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { contentPaddings ->
        PullRefresh(
            refreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicatorTop = contentPaddings.calculateTopPadding(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScrollUnderBar()
                    .fillMaxSize()
                    .padding(contentPaddings)
            ) {
                if (account != null) {
                    InfoCard(
                        modifier = Modifier
                            .clickable {
                                navigator.navigate(UserProfileKey(account.uid.toLong()))
                            }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        userName = account.nameShow ?: account.name,
                        userIntro = account.intro ?: stringResource(id = R.string.tip_no_intro),
                        avatar = StringUtil.getAvatarUrl(account.portrait),
                    )
                    StatCard(
                        account = account,
                        onOpenFollows = { fans ->
                            navigator.navigate(UserFollowsKey(account.uid.toLong(), fans, account.nameShow ?: account.name))
                        },
                        modifier = StatsPadding,
                    )
                } else if (isLoading) {
                    InfoCard(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        isPlaceholder = true,
                    )
                    StatCardPlaceholder(modifier = StatsPadding)
                } else {
                    LoginTipCard(
                        onLogin = { navigator.navigate(LoginKey) },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                ) {
                    if (account != null) {
                        EntryBlock(
                            icon = ImageVector.vectorResource(id = R.drawable.ic_me_collect),
                            text = stringResource(id = R.string.title_my_collect),
                            detail = storedWithReplies.takeIf { it > 0 }?.let {
                                pluralStringResource(id = R.plurals.stored_with_replies, count = it, it)
                            },
                            onClick = { navigator.navigate(ThreadStoreKey) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    EntryBlock(
                        icon = ImageVector.vectorResource(id = R.drawable.ic_me_history),
                        text = stringResource(id = R.string.title_history),
                        detail = lastThread?.title,
                        onClick = { navigator.navigate(HistoryKey) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                ListMenuItem(
                    iconColor = ExtendedTheme.colors.text,
                    icon = ImageVector.vectorResource(id = R.drawable.ic_me_theme),
                    text = stringResource(id = R.string.title_theme),
                    onClick = {
                        navigator.navigate(AppThemeKey)
                    }
                ) {
                    RowValue(text = currentThemeName())
                }
                if (account != null) {
                    ListMenuItem(
                        iconColor = ExtendedTheme.colors.text,
                        icon = ImageVector.vectorResource(id = R.drawable.ic_me_help),
                        text = stringResource(id = R.string.my_info_service_center),
                        onClick = {
                            navigator.navigate(
                                WebViewKey(
                                    initialUrl = "https://tieba.baidu.com/mo/q/hybrid-main-service/uegServiceCenter?cuid=${CuidUtils.getNewCuid()}&cuid_galaxy2=${CuidUtils.getNewCuid()}&cuid_gid=&timestamp=${System.currentTimeMillis()}&_client_version=12.52.1.0&nohead=1"
                                )
                            )
                        },
                    ) {
                        // It opens a web page
                        Icon(
                            imageVector = Symbols.OpenInNew,
                            contentDescription = null,
                            tint = ExtendedTheme.colors.textSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                // The two groups of menus are set apart by space alone
                Spacer(modifier = Modifier.height(16.dp))
                ListMenuItem(
                    iconColor = ExtendedTheme.colors.text,
                    icon = ImageVector.vectorResource(id = R.drawable.ic_me_settings),
                    text = stringResource(id = R.string.my_info_settings),
                    onClick = { navigator.navigate(SettingsKey) },
                )
                ListMenuItem(
                    iconColor = ExtendedTheme.colors.text,
                    icon = ImageVector.vectorResource(id = R.drawable.ic_me_about),
                    text = stringResource(id = R.string.my_info_about),
                    onClick = { navigator.navigate(AboutKey) },
                ) {
                    if (updateAvailable) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NewVersionDot))
                    }
                }
            }
        }
    }
}

/** The red of the About page's new-version mark. */
private val NewVersionDot = Color(0xFFE5484D)

/** Whether browsing incognito has been explained, as it is the first time it is turned on. */
private val IncognitoExplainedKey = booleanPreferencesKey("incognitoExplained")

/** Between the incognito hint and the bottom bar under it. */
private val IncognitoHintGap = 12.dp

private const val IncognitoHintMillis = 4_000L

/** Long enough to read the explanation. */
private const val IncognitoExplainMillis = 8_000L
