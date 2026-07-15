package com.endgamefinance.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tabular figures: every digit takes the same width, so money columns align
 * vertically. Apply to ANY text that renders an amount in a list. Also swaps
 * in the theme's money font when the chosen [AppFont] pairs one (Plex Sans
 * text + Plex Mono digits).
 */
val TextStyle.tabular: TextStyle
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = copy(
        fontFeatureSettings = "tnum",
        fontFamily = LocalMoneyFontFamily.current ?: fontFamily,
    )

/** Material 3 scale bound to the chosen [family]; weights match the loaded faces. */
fun endgameTypography(family: FontFamily): Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp, lineHeight = 44.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
)
