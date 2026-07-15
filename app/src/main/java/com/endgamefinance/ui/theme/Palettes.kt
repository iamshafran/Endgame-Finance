package com.endgamefinance.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Selectable palettes. Each supplies a full light + dark M3 ColorScheme and its
 * own gain/loss money colors, so switching re-skins the entire app coherently
 * while the separate light/dark mode still applies.
 */
enum class ThemePalette(val label: String) {
    // Enum names are persisted in AppSettings — labels may change, names must not.
    DEFAULT("Evergreen"),
    CYBERPUNK("Cyberpunk"),
    MARATHON("Marathoner"),
    GOLDEN_RUSH("Golden Rush"),
    MONOCHROME("Black & White"),
}

fun colorSchemeFor(palette: ThemePalette, dark: Boolean): ColorScheme = when (palette) {
    ThemePalette.DEFAULT -> if (dark) DefaultDark else DefaultLight
    ThemePalette.CYBERPUNK -> if (dark) CyberpunkDark else CyberpunkLight
    ThemePalette.MARATHON -> if (dark) MarathonDark else MarathonLight
    ThemePalette.GOLDEN_RUSH -> if (dark) GoldenRushDark else GoldenRushLight
    ThemePalette.MONOCHROME -> if (dark) MonochromeDark else MonochromeLight
}

// Rule (owner, 2026-07-15): if the palette contains a green, that green IS the
// income/cleared color; if it contains a red, that red IS the expense color.
// Palettes missing one borrow a complementary shade that fits their mood.
// The transfer color is always distinct from both gain and loss.
fun moneyColorsFor(palette: ThemePalette, dark: Boolean): MoneyColors = when (palette) {
    // Evergreen: the brand primary green doubles as income; no red in the
    // palette, so expenses use a complementary red. Transfers wear the gold.
    ThemePalette.DEFAULT ->
        if (dark) MoneyColors(GreenPrimaryDark, LossDark, GoldTertiaryDark)
        else MoneyColors(GreenPrimaryLight, LossLight, GoldTertiaryLight)
    // Cyberpunk: no green (yellow/cyan/magenta) — complementary neon green;
    // its hot magenta-red accent is the expense color. Transfers can't use
    // tertiary (that IS the expense magenta) — the signature cyan instead.
    ThemePalette.CYBERPUNK ->
        if (dark) MoneyColors(Color(0xFF00FF9F), Color(0xFFFF3B6E), Color(0xFF00E5FF))
        else MoneyColors(Color(0xFF00695C), Color(0xFFB00042), Color(0xFF00697A))
    // Marathoner: the signature lime IS the palette's green — income wears it;
    // no red, so expenses use a complementary warm red. Transfers: cool blue.
    ThemePalette.MARATHON ->
        if (dark) MoneyColors(Color(0xFFC0FE04), Color(0xFFFF5A67), Color(0xFF44D9FF))
        else MoneyColors(Color(0xFF4C6600), Color(0xFFB3261E), Color(0xFF006B85))
    // Golden Rush: neither green nor red — warm complementary shades; the
    // metallic gold marks transfers.
    ThemePalette.GOLDEN_RUSH ->
        if (dark) MoneyColors(Color(0xFF8FD98A), Color(0xFFFF8A7D), Color(0xFFE6C97A))
        else MoneyColors(Color(0xFF2E7D32), Color(0xFFB3261E), Color(0xFF7E5A00))
    // Monochrome: gains read bold (near onSurface), losses dimmed grey,
    // transfers a mid grey between them; the +/- sign and the transfer glyph
    // carry meaning, so color is never the only cue.
    ThemePalette.MONOCHROME ->
        if (dark) MoneyColors(Color(0xFFF0F0F0), Color(0xFF9A9A9A), Color(0xFFC4C4C4))
        else MoneyColors(Color(0xFF1A1A1A), Color(0xFF8A8A8A), Color(0xFF4E4E4E))
}

// ---------------------------------------------------------------- Default
// Reuses the evergreen + gold tokens defined in Color.kt.

private val DefaultLight = lightColorScheme(
    primary = GreenPrimaryLight, onPrimary = OnGreenPrimaryLight,
    primaryContainer = GreenPrimaryContainerLight, onPrimaryContainer = OnGreenPrimaryContainerLight,
    secondary = SageSecondaryLight, onSecondary = OnSageSecondaryLight,
    secondaryContainer = SageSecondaryContainerLight, onSecondaryContainer = OnSageSecondaryContainerLight,
    tertiary = GoldTertiaryLight, onTertiary = OnGoldTertiaryLight,
    tertiaryContainer = GoldTertiaryContainerLight, onTertiaryContainer = OnGoldTertiaryContainerLight,
    error = ErrorLight, onError = OnErrorLight,
    errorContainer = ErrorContainerLight, onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight, onBackground = OnBackgroundLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight, outlineVariant = OutlineVariantLight, surfaceTint = GreenPrimaryLight,
    surfaceDim = SurfaceDimLight, surfaceBright = SurfaceBrightLight,
    surfaceContainerLowest = SurfaceContainerLowestLight, surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight, surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    inverseSurface = InverseSurfaceLight, inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = InversePrimaryLight, scrim = ScrimLight,
)

private val DefaultDark = darkColorScheme(
    primary = GreenPrimaryDark, onPrimary = OnGreenPrimaryDark,
    primaryContainer = GreenPrimaryContainerDark, onPrimaryContainer = OnGreenPrimaryContainerDark,
    secondary = SageSecondaryDark, onSecondary = OnSageSecondaryDark,
    secondaryContainer = SageSecondaryContainerDark, onSecondaryContainer = OnSageSecondaryContainerDark,
    tertiary = GoldTertiaryDark, onTertiary = OnGoldTertiaryDark,
    tertiaryContainer = GoldTertiaryContainerDark, onTertiaryContainer = OnGoldTertiaryContainerDark,
    error = ErrorDark, onError = OnErrorDark,
    errorContainer = ErrorContainerDark, onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark, onBackground = OnBackgroundDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark, onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark, outlineVariant = OutlineVariantDark, surfaceTint = GreenPrimaryDark,
    surfaceDim = SurfaceDimDark, surfaceBright = SurfaceBrightDark,
    surfaceContainerLowest = SurfaceContainerLowestDark, surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark, surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    inverseSurface = InverseSurfaceDark, inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = InversePrimaryDark, scrim = ScrimDark,
)

// ---------------------------------------------------------------- Cyberpunk
// Genre-inspired neon yellow + cyan + hot magenta on blue-black.

private val CyberpunkDark = darkColorScheme(
    primary = Color(0xFFFCEE0A), onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF4A4600), onPrimaryContainer = Color(0xFFFCEE0A),
    secondary = Color(0xFF00E5FF), onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF004E59), onSecondaryContainer = Color(0xFF6FF7FF),
    tertiary = Color(0xFFFF3B6E), onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF66002A), onTertiaryContainer = Color(0xFFFFB0C4),
    error = Color(0xFFFF5252), onError = Color(0xFF000000),
    errorContainer = Color(0xFF5C0000), onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF0A0A0F), onBackground = Color(0xFFE6F7FF),
    surface = Color(0xFF0A0A0F), onSurface = Color(0xFFE6F7FF),
    surfaceVariant = Color(0xFF26262E), onSurfaceVariant = Color(0xFFC4C7D0),
    outline = Color(0xFF7A7D88), outlineVariant = Color(0xFF2E2E38), surfaceTint = Color(0xFFFCEE0A),
    surfaceDim = Color(0xFF0A0A0F), surfaceBright = Color(0xFF303038),
    surfaceContainerLowest = Color(0xFF050507), surfaceContainerLow = Color(0xFF121218),
    surfaceContainer = Color(0xFF16161E), surfaceContainerHigh = Color(0xFF1E1E28),
    surfaceContainerHighest = Color(0xFF26262E),
    inverseSurface = Color(0xFFE6F7FF), inverseOnSurface = Color(0xFF16161E),
    inversePrimary = Color(0xFF4A4600), scrim = Color(0xFF000000),
)

private val CyberpunkLight = lightColorScheme(
    primary = Color(0xFF6B6500), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFCEE0A), onPrimaryContainer = Color(0xFF1F1D00),
    secondary = Color(0xFF00697A), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFACEEFF), onSecondaryContainer = Color(0xFF001F26),
    tertiary = Color(0xFFB00042), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E0), onTertiaryContainer = Color(0xFF3E0018),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFAF0), onBackground = Color(0xFF1B1B14),
    surface = Color(0xFFFBFAF0), onSurface = Color(0xFF1B1B14),
    surfaceVariant = Color(0xFFE7E4D0), onSurfaceVariant = Color(0xFF48473A),
    outline = Color(0xFF7A7869), outlineVariant = Color(0xFFCAC7B5), surfaceTint = Color(0xFF6B6500),
    surfaceDim = Color(0xFFDBDACB), surfaceBright = Color(0xFFFBFAF0),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF5F4E9),
    surfaceContainer = Color(0xFFEFEEE3), surfaceContainerHigh = Color(0xFFEAE8DD),
    surfaceContainerHighest = Color(0xFFE4E2D7),
    inverseSurface = Color(0xFF303027), inverseOnSurface = Color(0xFFF3F1E6),
    inversePrimary = Color(0xFFFCEE0A), scrim = Color(0xFF000000),
)

// ---------------------------------------------------------------- Marathoner
// Racing-inspired lime green (#C0FE04) on near-black charcoal, with
// cool blue + pale-lime supporting accents.

// Tuned to the game's actual loadout screens (owner refs 2026-07-15):
// acid lime + hot magenta + electric cyan on deep blue-black, not sage.
private val MarathonDark = darkColorScheme(
    primary = Color(0xFFC0FE04), onPrimary = Color(0xFF141A00),
    primaryContainer = Color(0xFF303D00), onPrimaryContainer = Color(0xFFD4FF5B),
    secondary = Color(0xFFF04FD4), onSecondary = Color(0xFF3A0032),
    secondaryContainer = Color(0xFF52004A), onSecondaryContainer = Color(0xFFFFB8EE),
    tertiary = Color(0xFF44D9FF), onTertiary = Color(0xFF00303D),
    tertiaryContainer = Color(0xFF004C60), onTertiaryContainer = Color(0xFFB8EEFF),
    error = Color(0xFFFF5449), onError = Color(0xFF000000),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0A0C10), onBackground = Color(0xFFE2E4E8),
    surface = Color(0xFF0A0C10), onSurface = Color(0xFFE2E4E8),
    surfaceVariant = Color(0xFF3C4048), onSurfaceVariant = Color(0xFFBCC2CC),
    outline = Color(0xFF848B96), outlineVariant = Color(0xFF33373E), surfaceTint = Color(0xFFC0FE04),
    surfaceDim = Color(0xFF0A0C10), surfaceBright = Color(0xFF2E323A),
    surfaceContainerLowest = Color(0xFF05070A), surfaceContainerLow = Color(0xFF12151A),
    surfaceContainer = Color(0xFF171A20), surfaceContainerHigh = Color(0xFF1F232B),
    surfaceContainerHighest = Color(0xFF292E37),
    inverseSurface = Color(0xFFE2E4E8), inverseOnSurface = Color(0xFF2B2E33),
    inversePrimary = Color(0xFF3F5300), scrim = Color(0xFF000000),
)

private val MarathonLight = lightColorScheme(
    primary = Color(0xFF4C6600), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3FF6B), onPrimaryContainer = Color(0xFF141F00),
    secondary = Color(0xFF9C0084), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD7F0), onSecondaryContainer = Color(0xFF36002C),
    tertiary = Color(0xFF006B85), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB8EAFF), onTertiaryContainer = Color(0xFF001F29),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFCF3), onBackground = Color(0xFF1A1D14),
    surface = Color(0xFFFBFCF3), onSurface = Color(0xFF1A1D14),
    surfaceVariant = Color(0xFFE2E4D3), onSurfaceVariant = Color(0xFF45483C),
    outline = Color(0xFF767869), outlineVariant = Color(0xFFC6C8B8), surfaceTint = Color(0xFF4C6600),
    surfaceDim = Color(0xFFDBDCD0), surfaceBright = Color(0xFFFBFCF3),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF5F6EC),
    surfaceContainer = Color(0xFFEFF1E6), surfaceContainerHigh = Color(0xFFE9EBE0),
    surfaceContainerHighest = Color(0xFFE3E5DB),
    inverseSurface = Color(0xFF2F3128), inverseOnSurface = Color(0xFFF1F2E8),
    inversePrimary = Color(0xFFC0FE04), scrim = Color(0xFF000000),
)

// ---------------------------------------------------------------- Golden Rush
// Luxe metallic gold on warm near-black; champagne + amber accents.

private val GoldenRushDark = darkColorScheme(
    primary = Color(0xFFE8C15A), onPrimary = Color(0xFF3D2E00),
    primaryContainer = Color(0xFF574400), onPrimaryContainer = Color(0xFFFFE08B),
    secondary = Color(0xFFD4B483), onSecondary = Color(0xFF3A2D12),
    secondaryContainer = Color(0xFF524027), onSecondaryContainer = Color(0xFFF2DDBC),
    tertiary = Color(0xFFE6C97A), onTertiary = Color(0xFF3D2E00),
    tertiaryContainer = Color(0xFF5C4A14), onTertiaryContainer = Color(0xFFFFE9A8),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF17150F), onBackground = Color(0xFFEAE1CF),
    surface = Color(0xFF17150F), onSurface = Color(0xFFEAE1CF),
    surfaceVariant = Color(0xFF4C4636), onSurfaceVariant = Color(0xFFCFC5AE),
    outline = Color(0xFF988E76), outlineVariant = Color(0xFF4C4636), surfaceTint = Color(0xFFE8C15A),
    surfaceDim = Color(0xFF17150F), surfaceBright = Color(0xFF3D392E),
    surfaceContainerLowest = Color(0xFF110F0A), surfaceContainerLow = Color(0xFF1F1C14),
    surfaceContainer = Color(0xFF232016), surfaceContainerHigh = Color(0xFF2E2A1E),
    surfaceContainerHighest = Color(0xFF393428),
    inverseSurface = Color(0xFFEAE1CF), inverseOnSurface = Color(0xFF332F22),
    inversePrimary = Color(0xFF6F5A00), scrim = Color(0xFF000000),
)

private val GoldenRushLight = lightColorScheme(
    primary = Color(0xFF6F5A00), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE08B), onPrimaryContainer = Color(0xFF221B00),
    secondary = Color(0xFF6B5D3F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5E0BB), onSecondaryContainer = Color(0xFF241A04),
    tertiary = Color(0xFF7E5A00), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDE9E), onTertiaryContainer = Color(0xFF281900),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBF1), onBackground = Color(0xFF1E1B13),
    surface = Color(0xFFFFFBF1), onSurface = Color(0xFF1E1B13),
    surfaceVariant = Color(0xFFEDE1C8), onSurfaceVariant = Color(0xFF4C4636),
    outline = Color(0xFF7E7767), outlineVariant = Color(0xFFD0C6AE), surfaceTint = Color(0xFF6F5A00),
    surfaceDim = Color(0xFFE0D8C6), surfaceBright = Color(0xFFFFFBF1),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFBF3E4),
    surfaceContainer = Color(0xFFF5EDDE), surfaceContainerHigh = Color(0xFFEFE7D8),
    surfaceContainerHighest = Color(0xFFEAE1D2),
    inverseSurface = Color(0xFF332F22), inverseOnSurface = Color(0xFFF7EFDE),
    inversePrimary = Color(0xFFE8C15A), scrim = Color(0xFF000000),
)

// ---------------------------------------------------------------- Monochrome
// Pure grayscale chrome; error keeps a functional red.

private val MonochromeLight = lightColorScheme(
    primary = Color(0xFF1F1F1F), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCDCDC), onPrimaryContainer = Color(0xFF141414),
    secondary = Color(0xFF4F4F4F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E2E2), onSecondaryContainer = Color(0xFF191919),
    tertiary = Color(0xFF5F5F5F), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8E8E8), onTertiaryContainer = Color(0xFF191919),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFCFCFC), onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFCFCFC), onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE2E2E2), onSurfaceVariant = Color(0xFF474747),
    outline = Color(0xFF7A7A7A), outlineVariant = Color(0xFFC9C9C9), surfaceTint = Color(0xFF1F1F1F),
    surfaceDim = Color(0xFFDBDBDB), surfaceBright = Color(0xFFFCFCFC),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF4F4F4),
    surfaceContainer = Color(0xFFEEEEEE), surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE2E2E2),
    inverseSurface = Color(0xFF2E2E2E), inverseOnSurface = Color(0xFFF1F1F1),
    inversePrimary = Color(0xFFC6C6C6), scrim = Color(0xFF000000),
)

private val MonochromeDark = darkColorScheme(
    primary = Color(0xFFE4E4E4), onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF3A3A3A), onPrimaryContainer = Color(0xFFEDEDED),
    secondary = Color(0xFFC2C2C2), onSecondary = Color(0xFF2A2A2A),
    secondaryContainer = Color(0xFF3F3F3F), onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFFABABAB), onTertiary = Color(0xFF2A2A2A),
    tertiaryContainer = Color(0xFF3A3A3A), onTertiaryContainer = Color(0xFFE0E0E0),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121212), onBackground = Color(0xFFE4E4E4),
    surface = Color(0xFF121212), onSurface = Color(0xFFE4E4E4),
    surfaceVariant = Color(0xFF474747), onSurfaceVariant = Color(0xFFC9C9C9),
    outline = Color(0xFF919191), outlineVariant = Color(0xFF474747), surfaceTint = Color(0xFFE4E4E4),
    surfaceDim = Color(0xFF121212), surfaceBright = Color(0xFF383838),
    surfaceContainerLowest = Color(0xFF0D0D0D), surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1E1E1E), surfaceContainerHigh = Color(0xFF282828),
    surfaceContainerHighest = Color(0xFF333333),
    inverseSurface = Color(0xFFE4E4E4), inverseOnSurface = Color(0xFF2E2E2E),
    inversePrimary = Color(0xFF5E5E5E), scrim = Color(0xFF000000),
)
