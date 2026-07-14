package com.endgamefinance.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.endgamefinance.R

/**
 * Bundled, offline font families (no network — the app has no INTERNET
 * permission). All readability-focused. Keys are persisted in AppSettings;
 * never rename an existing key.
 */
enum class AppFont(val key: String, val label: String, val family: FontFamily) {
    PLEX_MONO(
        "plex_mono",
        "IBM Plex Mono",
        FontFamily(
            Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
            Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
            Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
        ),
    ),
    PLEX_SANS(
        "plex_sans",
        "IBM Plex Sans",
        FontFamily(
            Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
            Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
            Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
        ),
    ),
    ATKINSON(
        "atkinson",
        "Atkinson Hyperlegible",
        FontFamily(
            Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
            // Bold covers Medium/SemiBold via synthesis — Atkinson ships only R/B
            Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
        ),
    ),
    SYSTEM(
        "system",
        "System default",
        FontFamily.Default,
    ),
    ;

    companion object {
        const val DEFAULT_KEY = "plex_mono"

        fun fromKey(key: String?): AppFont =
            entries.firstOrNull { it.key == key } ?: PLEX_MONO
    }
}
