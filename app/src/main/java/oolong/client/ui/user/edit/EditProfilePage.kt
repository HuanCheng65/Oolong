package oolong.client.ui.user.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.media.PickMediasContract
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.ui.components.ErrorScreen
import oolong.client.ui.designsystem.ActionItem
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.BaseTextField
import oolong.client.ui.designsystem.ContentSwap
import oolong.client.ui.designsystem.CounterTextField
import oolong.client.ui.designsystem.Dialog
import oolong.client.ui.designsystem.DialogNegativeButton
import oolong.client.ui.designsystem.IconButton
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.Toolbar
import oolong.client.ui.designsystem.WavyLoader
import oolong.client.ui.designsystem.picker.ListSinglePicker
import oolong.client.ui.designsystem.placeholder.placeholder
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.states.StateScreenScope
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.editor.CropRequest
import oolong.client.ui.editor.EditorPlace
import oolong.client.ui.editor.LocalImageEditor
import oolong.client.util.StringUtil
import oolong.client.util.toastShort
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.PressFeedback
import java.io.File

@Composable
fun EditProfilePage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                EditProfileEffect.Done -> navigator.navigateUp()
            }
        }
    }
    // Where the avatar is, for the cut picture to go into
    var avatar by remember { mutableStateOf<Rect?>(null) }
    val pickPortrait = rememberPortraitPicker(avatar = { avatar }, onPicked = viewModel::uploadPortrait)
    EditProfileScreen(
        state = state,
        onBack = { navigator.navigateUp() },
        onSave = viewModel::save,
        onNickNameChange = viewModel::setNickName,
        onIntroChange = viewModel::setIntro,
        onSexChange = viewModel::setSex,
        onUploadPortrait = pickPortrait,
        onAvatarPlaced = { avatar = it },
        onReload = viewModel::load,
    )
}

/**
 * Picks an image and crops it square, then calls [onPicked] with the cropped file. Returns what
 * starts it.
 */
@Composable
private fun rememberPortraitPicker(avatar: () -> Rect?, onPicked: (File) -> Unit): () -> Unit {
    val context = LocalContext.current
    val cropped = remember(context) { File(context.cacheDir, "cropped_portrait") }
    val editor = LocalImageEditor.current
    val pickLauncher = rememberLauncherForActivityResult(PickMediasContract) { uris ->
        val source = uris.firstOrNull() ?: return@rememberLauncherForActivityResult
        val back = { written: Boolean -> avatar()?.takeIf { written }?.let { EditorPlace(it, round = true) } }
        editor?.open(CropRequest(source, cropped, aspect = 1f, round = true), back = back) { written ->
            if (written) onPicked(cropped)
        }
    }
    return { pickLauncher.launch(1) }
}

/** The editing page: the profile card under a bar with save, or placeholders while it loads. */
@Composable
fun EditProfileScreen(
    state: EditProfileState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onNickNameChange: (String) -> Unit,
    onIntroChange: (String) -> Unit,
    onSexChange: (Int) -> Unit,
    onUploadPortrait: () -> Unit,
    onAvatarPlaced: (Rect) -> Unit = {},
    onReload: () -> Unit = {},
) {
    val profile = state.profile
    Scaffold(
        topBar = {
            Toolbar(
                title = stringResource(id = R.string.title_activity_edit_profile),
                navigationIcon = { BackNavigationIcon { onBack() } },
                actions = {
                    if (profile != null) {
                        // Saving, the button turns into the wait for the server
                        Refocus(targetState = state.saving) { saving ->
                            if (saving) {
                                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) { WavyLoader(modifier = Modifier.size(24.dp)) }
                            } else {
                                ActionItem(
                                    icon = ImageVector.vectorResource(id = R.drawable.ic_round_save_24),
                                    contentDescription = stringResource(id = R.string.button_save_profile),
                                    onClick = onSave,
                                )
                            }
                        }
                    }
                }
            )
        },
    ) { contentPadding ->
        // Failing to load, the page says so with a retry, rather than staying on placeholders
        val failed = (state.load as? LoadStatus.Error)?.error?.takeIf { profile == null }
        ContentSwap(targetState = failed != null, modifier = Modifier.fillMaxSize()) { showFailure ->
            if (showFailure && failed != null) {
                Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                    remember { StateScreenScope(onReload) }.ErrorScreen(error = failed, modifier = Modifier.fillMaxSize())
                }
                return@ContentSwap
            }
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(horizontal = dimensionResource(id = R.dimen.card_margin))
                    .imePadding()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                val dialogState = rememberDialogState()
                Dialog(
                    dialogState = dialogState,
                    title = { Text(text = stringResource(id = R.string.title_modify_sex)) },
                    buttons = { DialogNegativeButton(text = stringResource(id = R.string.button_cancel)) }
                ) {
                    ListSinglePicker(
                        itemTitles = persistentListOf(
                            stringResource(id = R.string.profile_sex_male),
                            stringResource(id = R.string.profile_sex_female)
                        ),
                        itemValues = persistentListOf(1, 2),
                        selectedPosition = when (state.sex) {
                            1 -> 0
                            2 -> 1
                            else -> -1
                        },
                        onItemSelected = { _, _, value, _ ->
                            onSexChange(value)
                            dismiss()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.card_margin)))
                EditProfileCard(
                    portrait = profile?.portrait.orEmpty(),
                    name = profile?.name.orEmpty(),
                    nickName = state.nickName,
                    sex = state.sex,
                    intro = state.intro,
                    loading = profile == null,
                    onNickNameChange = onNickNameChange,
                    onIntroChange = onIntroChange,
                    onUploadPortrait = onUploadPortrait,
                    onAvatarPlaced = onAvatarPlaced,
                    onModifySex = { dialogState.show = true },
                    uploading = state.uploading,
                )
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.card_margin)))
            }
        }
    }
}

@Composable
fun EditProfileCard(
    portrait: String,
    name: String,
    nickName: String,
    sex: Int,
    intro: String,
    // birthdayShowStatus: Boolean,
    // birthdayTime: Long,
    loading: Boolean,
    onNickNameChange: ((String) -> Unit)? = null,
    onIntroChange: ((String) -> Unit)? = null,
    onUploadPortrait: (() -> Unit)? = null,
    onModifySex: (() -> Unit)? = null,
    color: Color = ExtendedTheme.colors.background,
    onAvatarPlaced: (Rect) -> Unit = {},
    uploading: Boolean = false,
) {
    val context = LocalContext.current
    Card(elevation = 0.dp, backgroundColor = color) {
        Column(
            modifier = Modifier
                .padding(dimensionResource(id = R.dimen.card_margin))
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .onGloballyPositioned { onAvatarPlaced(it.boundsInWindow()) }
                    .clip(CircleShape)
                    .align(Alignment.CenterHorizontally)
                    .placeholder(visible = loading)
            ) {
                // The new portrait fades in over the old once the server has it
                Avatar(data = StringUtil.getAvatarUrl(portrait), size = 64.dp, contentDescription = null)
                IconButton(
                    modifier = Modifier
                        .background(color = Color(0x77000000))
                        .fillMaxSize(),
                    onClick = {
                        onUploadPortrait?.invoke()
                    }
                ) {
                    // Uploading, the camera turns into the wait
                    Refocus(targetState = uploading) { busy ->
                        if (busy) {
                            WavyLoader(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                imageVector = Symbols.PhotoCamera,
                                contentDescription = stringResource(id = R.string.upload_portrait),
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            ConstraintLayout(
                modifier = Modifier.fillMaxWidth()
            ) {
                val (
                    nameTitle,
                    nameContent,
                    nickNameTitle,
                    nickNameContent,
                    sexTitle,
                    sexContent,
                    introTitle,
                    introContent,
                ) = createRefs()

                val titleEndBarrier = createEndBarrier(
                    nameTitle,
                    nickNameTitle,
                    sexTitle,
                    introTitle,
                    margin = 24.dp
                )

                Text(
                    text = stringResource(id = R.string.title_username),
                    color = ExtendedTheme.colors.textDisabled,
                    modifier = Modifier.constrainAs(nameTitle) {
                        top.linkTo(parent.top)
                        bottom.linkTo(nameContent.bottom)
                        start.linkTo(parent.start)
                    }
                )
                Text(
                    text = name,
                    color = ExtendedTheme.colors.textDisabled,
                    modifier = Modifier
                        .constrainAs(nameContent) {
                            top.linkTo(nameTitle.top)
                            bottom.linkTo(nameTitle.bottom)
                            start.linkTo(titleEndBarrier)
                            end.linkTo(parent.end)
                            width = Dimension.fillToConstraints
                        }
                        .placeholder(visible = loading)
                )

                Text(
                    text = stringResource(id = R.string.title_nickname),
                    color = ExtendedTheme.colors.textDisabled,
                    modifier = Modifier.constrainAs(nickNameTitle) {
                        top.linkTo(nameTitle.bottom, margin = 16.dp)
                        start.linkTo(parent.start)
                    }
                )
                Row(
                    modifier = Modifier
                        .constrainAs(nickNameContent) {
                            top.linkTo(nickNameTitle.top)
                            bottom.linkTo(nickNameTitle.bottom)
                            start.linkTo(titleEndBarrier)
                            end.linkTo(parent.end)
                            width = Dimension.fillToConstraints
                        }
                        .placeholder(visible = loading)
                ) {
                    BaseTextField(
                        value = nickName,
                        onValueChange = { onNickNameChange?.invoke(it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        maxLines = 1,
                    )
                }

                Text(
                    text = stringResource(id = R.string.profile_sex),
                    color = ExtendedTheme.colors.textDisabled,
                    modifier = Modifier.constrainAs(sexTitle) {
                        top.linkTo(nickNameTitle.bottom, margin = 16.dp)
                        start.linkTo(parent.start)
                    }
                )
                Row(
                    modifier = Modifier
                        .constrainAs(sexContent) {
                            top.linkTo(sexTitle.top)
                            bottom.linkTo(sexTitle.bottom)
                            start.linkTo(titleEndBarrier)
                            end.linkTo(parent.end)
                            width = Dimension.fillToConstraints
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = PressFeedback.overlay(Corners.xs)
                        ) { onModifySex?.invoke() }
                        .placeholder(visible = loading)
                ) {
                    Text(
                        text = stringResource(
                            id = when (sex) {
                                1 -> R.string.profile_sex_male
                                2 -> R.string.profile_sex_female
                                else -> R.string.profile_sex_unset
                            }
                        ),
                        color = ExtendedTheme.colors.text,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_round_chevron_right),
                        contentDescription = null,
                        tint = ExtendedTheme.colors.textSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.CenterVertically)
                    )
                }

                Text(
                    text = stringResource(id = R.string.title_intro),
                    color = ExtendedTheme.colors.textDisabled,
                    modifier = Modifier.constrainAs(introTitle) {
                        top.linkTo(sexTitle.bottom, margin = 16.dp)
                        start.linkTo(parent.start)
                    }
                )
                CounterTextField(
                    value = intro,
                    onValueChange = { onIntroChange?.invoke(it) },
                    maxLength = 500,
                    countWhitespace = false,
                    onLengthBeyondRestrict = { context.toastShort(R.string.toast_intro_length_beyond_restrict) },
                    placeholder = { Text(text = stringResource(id = R.string.tip_no_intro)) },
                    modifier = Modifier
                        .constrainAs(introContent) {
                            top.linkTo(introTitle.top)
                            start.linkTo(titleEndBarrier)
                            end.linkTo(parent.end)
                            width = Dimension.fillToConstraints
                        }
                        .placeholder(visible = loading),
                )
            }
        }
    }
}

@Preview
@Composable
fun EditProfileCardPreview() {
    EditProfileCard(
        portrait = "",
        name = "test",
        nickName = "test",
        sex = 1,
        intro = "test",
        loading = false,
        color = Color.White
    )
}

@Preview
@Composable
fun EditProfileCardLoadingPreview() {
    EditProfileCard(
        portrait = "",
        name = "",
        nickName = "",
        sex = 0,
        intro = "",
        loading = true,
        color = Color.White
    )
}
