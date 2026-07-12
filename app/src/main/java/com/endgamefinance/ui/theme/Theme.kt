package com.endgamefinance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimaryLight,
    onPrimary = OnGreenPrimaryLight,
    primaryContainer = GreenPrimaryContainerLight,
    onPrimaryContainer = OnGreenPrimaryContainerLight,
    secondary = SageSecondaryLight,
    onSecondary = OnSageSecondaryLight,
    secondaryContainer = SageSecondaryContainerLight,
    onSecondaryContainer = OnSageSecondaryContainerLight,
    tertiary = GoldTertiaryLight,
    onTertiary = OnGoldTertiaryLight,
    tertiaryContainer = GoldTertiaryContainerLight,
    onTertiaryContainer = OnGoldTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = OnGreenPrimaryDark,
    primaryContainer = GreenPrimaryContainerDark,
    onPrimaryContainer = OnGreenPrimaryContainerDark,
    secondary = SageSecondaryDark,
    onSecondary = OnSageSecondaryDark,
    secondaryContainer = SageSecondaryContainerDark,
    onSecondaryContainer = OnSageSecondaryContainerDark,
    tertiary = GoldTertiaryDark,
    onTertiary = OnGoldTertiaryDark,
    tertiaryContainer = GoldTertiaryContainerDark,
    onTertiaryContainer = OnGoldTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
)

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
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val moneyColors =
        if (darkTheme) MoneyColors(gain = GainDark, loss = LossDark)
        else MoneyColors(gain = GainLight, loss = LossLight)

    androidx.compose.runtime.CompositionLocalProvider(LocalMoneyColors provides moneyColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EndgameTypography,
            content = content,
        )
    }
}
