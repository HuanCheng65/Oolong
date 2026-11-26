package oolong.client.ui.designsystem.theme

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import oolong.client.data.settings.MonetStyle
import oolong.client.ui.designsystem.theme.colorutilities.DynamicScheme
import oolong.client.ui.designsystem.theme.colorutilities.Hct
import oolong.client.ui.designsystem.theme.colorutilities.SchemeExpressive
import oolong.client.ui.designsystem.theme.colorutilities.SchemeFruitSalad
import oolong.client.ui.designsystem.theme.colorutilities.SchemeMonochrome
import oolong.client.ui.designsystem.theme.colorutilities.SchemeNeutral
import oolong.client.ui.designsystem.theme.colorutilities.SchemeRainbow
import oolong.client.ui.designsystem.theme.colorutilities.SchemeTonalSpot
import oolong.client.ui.designsystem.theme.colorutilities.SchemeVibrant
import oolong.client.ui.designsystem.theme.colorutilities.TonalPalette as MaterialTonalPalette

/**
 * The palette Material's [style] makes from [seed], as the system makes its own from its colour.
 * Material keeps these classes to itself, but they are what the system uses.
 */
@SuppressLint("RestrictedApi")
internal fun monetPalette(seed: Color, style: MonetStyle): TonalPalette {
    val hct = Hct.fromInt(seed.toArgb())
    // A grey, as black, has no hue to build on: any style would make one up
    val scheme: DynamicScheme = when (if (hct.chroma < GREY_CHROMA) MonetStyle.Monochrome else style) {
        MonetStyle.TonalSpot -> SchemeTonalSpot(hct, false, 0.0)
        MonetStyle.Vibrant -> SchemeVibrant(hct, false, 0.0)
        MonetStyle.Expressive -> SchemeExpressive(hct, false, 0.0)
        MonetStyle.Neutral -> SchemeNeutral(hct, false, 0.0)
        MonetStyle.Rainbow -> SchemeRainbow(hct, false, 0.0)
        MonetStyle.FruitSalad -> SchemeFruitSalad(hct, false, 0.0)
        MonetStyle.Monochrome -> SchemeMonochrome(hct, false, 0.0)
    }
    return TonalPalette(
        neutral100 = scheme.neutralPalette.color(100),
        neutral99 = scheme.neutralPalette.color(99),
        neutral95 = scheme.neutralPalette.color(95),
        neutral90 = scheme.neutralPalette.color(90),
        neutral80 = scheme.neutralPalette.color(80),
        neutral70 = scheme.neutralPalette.color(70),
        neutral60 = scheme.neutralPalette.color(60),
        neutral50 = scheme.neutralPalette.color(50),
        neutral40 = scheme.neutralPalette.color(40),
        neutral30 = scheme.neutralPalette.color(30),
        neutral20 = scheme.neutralPalette.color(20),
        neutral10 = scheme.neutralPalette.color(10),
        neutral0 = scheme.neutralPalette.color(0),
        neutralVariant100 = scheme.neutralVariantPalette.color(100),
        neutralVariant99 = scheme.neutralVariantPalette.color(99),
        neutralVariant95 = scheme.neutralVariantPalette.color(95),
        neutralVariant90 = scheme.neutralVariantPalette.color(90),
        neutralVariant80 = scheme.neutralVariantPalette.color(80),
        neutralVariant70 = scheme.neutralVariantPalette.color(70),
        neutralVariant60 = scheme.neutralVariantPalette.color(60),
        neutralVariant50 = scheme.neutralVariantPalette.color(50),
        neutralVariant40 = scheme.neutralVariantPalette.color(40),
        neutralVariant30 = scheme.neutralVariantPalette.color(30),
        neutralVariant20 = scheme.neutralVariantPalette.color(20),
        neutralVariant10 = scheme.neutralVariantPalette.color(10),
        neutralVariant0 = scheme.neutralVariantPalette.color(0),
        primary100 = scheme.primaryPalette.color(100),
        primary99 = scheme.primaryPalette.color(99),
        primary95 = scheme.primaryPalette.color(95),
        primary90 = scheme.primaryPalette.color(90),
        primary80 = scheme.primaryPalette.color(80),
        primary70 = scheme.primaryPalette.color(70),
        primary60 = scheme.primaryPalette.color(60),
        primary50 = scheme.primaryPalette.color(50),
        primary40 = scheme.primaryPalette.color(40),
        primary30 = scheme.primaryPalette.color(30),
        primary20 = scheme.primaryPalette.color(20),
        primary10 = scheme.primaryPalette.color(10),
        primary0 = scheme.primaryPalette.color(0),
        secondary100 = scheme.secondaryPalette.color(100),
        secondary99 = scheme.secondaryPalette.color(99),
        secondary95 = scheme.secondaryPalette.color(95),
        secondary90 = scheme.secondaryPalette.color(90),
        secondary80 = scheme.secondaryPalette.color(80),
        secondary70 = scheme.secondaryPalette.color(70),
        secondary60 = scheme.secondaryPalette.color(60),
        secondary50 = scheme.secondaryPalette.color(50),
        secondary40 = scheme.secondaryPalette.color(40),
        secondary30 = scheme.secondaryPalette.color(30),
        secondary20 = scheme.secondaryPalette.color(20),
        secondary10 = scheme.secondaryPalette.color(10),
        secondary0 = scheme.secondaryPalette.color(0),
        tertiary100 = scheme.tertiaryPalette.color(100),
        tertiary99 = scheme.tertiaryPalette.color(99),
        tertiary95 = scheme.tertiaryPalette.color(95),
        tertiary90 = scheme.tertiaryPalette.color(90),
        tertiary80 = scheme.tertiaryPalette.color(80),
        tertiary70 = scheme.tertiaryPalette.color(70),
        tertiary60 = scheme.tertiaryPalette.color(60),
        tertiary50 = scheme.tertiaryPalette.color(50),
        tertiary40 = scheme.tertiaryPalette.color(40),
        tertiary30 = scheme.tertiaryPalette.color(30),
        tertiary20 = scheme.tertiaryPalette.color(20),
        tertiary10 = scheme.tertiaryPalette.color(10),
        tertiary0 = scheme.tertiaryPalette.color(0),
    )
}

private const val GREY_CHROMA = 8.0

@SuppressLint("RestrictedApi")
private fun MaterialTonalPalette.color(tone: Int) = Color(tone(tone))

/**
 * The colour the system takes from its wallpaper, or its palette's middle tone where it does not
 * say. Before Android 12 the system has none, and the default blue stands in.
 */
internal fun systemColor(context: Context): Color {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Color(0xFF4477E0)
    val fromWallpaper = runCatching {
        WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
    }.getOrNull()
    return fromWallpaper?.let { Color(it) } ?: Color(context.getColor(android.R.color.system_accent1_500))
}
