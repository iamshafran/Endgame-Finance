package com.endgamefinance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colors that sit outside the Material scheme (gains/losses). */
data class MoneyColors(
    val gain: Color,
    val loss: Color,
)

val LocalMoneyColors = staticCompositionLocalOf {
    MoneyColors(gain = GainLight, loss = LossLight)
}

@Composable
fun EndgameTheme(
    palette: ThemePalette = ThemePalette.DEFAULT,
    font: AppFont = AppFont.PLEX_MONO,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = colorSchemeFor(palette, darkTheme)
    val moneyColors = moneyColorsFor(palette, darkTheme)

    CompositionLocalProvider(LocalMoneyColors provides moneyColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = endgameTypography(font.family),
            content = content,
        )
    }
}
