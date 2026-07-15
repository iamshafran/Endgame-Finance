package com.endgamefinance.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.endgamefinance.R

private val PlexMonoFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

private val PlexSansFamily = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

private val AtkinsonFamily = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    // Bold covers Medium/SemiBold via synthesis — Atkinson ships only R/B
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
)

/**
 * Bundled, offline font families (no network — the app has no INTERNET
 * permission). All readability-focused. Keys are persisted in AppSettings;
 * never rename an existing key.
 *
 * [moneyFamily] optionally pairs a second face for amounts (applied through
 * the `tabular` style): Plex Sans text gets Plex Mono digits so money columns
 * align. Same superfamily, so the mix reads as intentional. Atkinson stays
 * uniform — it's the low-vision choice, and consistency aids readability.
 */
enum class AppFont(
    val key: String,
    val label: String,
    val family: FontFamily,
    val moneyFamily: FontFamily? = null,
) {
    PLEX_MONO("plex_mono", "IBM Plex Mono", PlexMonoFamily),
    PLEX_SANS("plex_sans", "IBM Plex Sans", PlexSansFamily, moneyFamily = PlexMonoFamily),
    ATKINSON("atkinson", "Atkinson Hyperlegible", AtkinsonFamily),
    SYSTEM("system", "System default", FontFamily.Default),
    ;

    companion object {
        const val DEFAULT_KEY = "plex_mono"

        fun fromKey(key: String?): AppFont =
            entries.firstOrNull { it.key == key } ?: PLEX_MONO
    }
}
