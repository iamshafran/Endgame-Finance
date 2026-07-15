package com.endgamefinance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colors that sit outside the Material scheme (gains/losses/transfers). */
data class MoneyColors(
    val gain: Color,
    val loss: Color,
    /** Money moving, not earned or spent — always distinct from gain AND loss. */
    val transfer: Color,
)

val LocalMoneyColors = staticCompositionLocalOf {
    MoneyColors(gain = GainLight, loss = LossLight, transfer = GoldTertiaryLight)
}

/** Optional second face for amounts (see AppFont.moneyFamily); null = text font. */
val LocalMoneyFontFamily =
    staticCompositionLocalOf<androidx.compose.ui.text.font.FontFamily?> { null }

/** Per-palette vibrant accents so each dashboard widget wears its own color. */
val LocalWidgetAccents = staticCompositionLocalOf<List<Color>> {
    listOf(GainLight, GoldTertiaryLight, LossLight, SageSecondaryLight, GainLight, LossLight)
}

@Composable
fun EndgameTheme(
    palette: ThemePalette = ThemePalette.DEFAULT,
    font: AppFont = AppFont.PLEX_MONO,
    darkTheme: Boolean = isSystemInDarkTheme(),
    oledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val effectiveDark = darkTheme || oledBlack
    val base = colorSchemeFor(palette, effectiveDark)
    val colorScheme = when {
        // OLED: every palette's dark scheme on true-black surfaces; the tint
        // survives in the container roles so cards still read as the palette.
        oledBlack -> base.copy(
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceDim = Color(0xFF000000),
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF0D0D0D),
            surfaceContainer = Color(0xFF141414),
            surfaceContainerHigh = Color(0xFF1D1D1D),
            surfaceContainerHighest = Color(0xFF262626),
        )
        // Light mode: pure-white page canvas across all palettes; cards keep
        // their tinted surfaceContainer roles (owner request 2026-07-14).
        !effectiveDark -> base.copy(
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF),
            surfaceBright = Color(0xFFFFFFFF),
        )
        else -> base
    }
    val moneyColors = moneyColorsFor(palette, effectiveDark)

    CompositionLocalProvider(
        LocalMoneyColors provides moneyColors,
        LocalMoneyFontFamily provides font.moneyFamily,
        LocalWidgetAccents provides widgetAccentsFor(palette, effectiveDark),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = endgameTypography(font.family),
            shapes = EndgameShapes,
            content = content,
        )
    }
}
