package oolong.client.ui.settings.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import oolong.client.R
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.settingAsMutableState
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarTitle
import oolong.client.ui.designsystem.theme.ExtendedColors
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.settings.theme.AppearanceOption
import oolong.client.ui.settings.theme.Bar

/**
 * Where search is on a phone, each choice shown as a home page in miniature: a round button
 * beside the bottom bar, or a field under the home page's title.
 */
@Composable
fun SearchPlacementPage(
    navigator: Navigator = LocalNavigator.current,
) {
    var atTop by settingAsMutableState(SettingKeys.SearchAtTop)
    val colors = ExtendedTheme.colors
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_search_placement)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                AppearanceOption(stringResource(id = R.string.search_place_bottom), !atTop, { atTop = false }) {
                    SearchPlacementPreview(colors, atTop = false)
                }
                AppearanceOption(stringResource(id = R.string.search_place_top), atTop, { atTop = true }) {
                    SearchPlacementPreview(colors, atTop = true)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.SearchPlacementPreview(colors: ExtendedColors, atTop: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.matchParentSize().background(colors.background).padding(horizontal = 10.dp, vertical = 14.dp),
    ) {
        Bar(colors.text, width = 0.4f, height = 7.dp)
        if (atTop) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .shadow(2.dp, CircleShape)
                    .background(colors.sheet, CircleShape)
                    .padding(horizontal = 5.dp),
            ) {
                Icon(Symbols.Search, contentDescription = null, tint = colors.text, modifier = Modifier.size(9.dp))
            }
        }
        repeat(if (atTop) 6 else 7) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(modifier = Modifier.size(10.dp).background(colors.chip, CircleShape))
                Bar(colors.textSecondary, width = 0.55f, height = 4.dp)
            }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .width(if (atTop) 92.dp else 76.dp)
                .height(20.dp)
                .shadow(2.dp, CircleShape)
                .background(colors.sheet, CircleShape),
        ) {
            repeat(4) { Box(modifier = Modifier.size(5.dp).background(if (it == 0) colors.emphasis else colors.textSecondary, CircleShape)) }
        }
        if (!atTop) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(20.dp).shadow(2.dp, CircleShape).background(colors.sheet, CircleShape),
            ) {
                Icon(Symbols.Search, contentDescription = null, tint = colors.text, modifier = Modifier.size(10.dp))
            }
        }
    }
}
