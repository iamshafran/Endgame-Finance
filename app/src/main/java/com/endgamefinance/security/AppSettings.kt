package com.endgamefinance.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide UI preferences (not financial data). Theme mode is reactive so the
 * whole activity recomposes when it changes.
 */
class AppSettings private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM,
    )
    /** THEME_SYSTEM | THEME_LIGHT | THEME_DARK */
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME, mode).apply()
        _themeMode.value = mode
    }

    private val _currencyCode = MutableStateFlow(
        prefs.getString(KEY_CURRENCY, "USD") ?: "USD",
    )
    /** ISO 4217 code — a DISPLAY preference only (no conversion). */
    val currencyCode: StateFlow<String> = _currencyCode.asStateFlow()

    fun setCurrencyCode(code: String) {
        prefs.edit().putString(KEY_CURRENCY, code).apply()
        _currencyCode.value = code
    }

    private val _palette = MutableStateFlow(
        prefs.getString(KEY_PALETTE, PALETTE_DEFAULT) ?: PALETTE_DEFAULT,
    )
    /** Palette key — one of ThemePalette.name (DEFAULT | CYBERPUNK | MARATHON). */
    val palette: StateFlow<String> = _palette.asStateFlow()

    fun setPalette(name: String) {
        prefs.edit().putString(KEY_PALETTE, name).apply()
        _palette.value = name
    }

    private val _fontKey = MutableStateFlow(
        prefs.getString(KEY_FONT, FONT_DEFAULT) ?: FONT_DEFAULT,
    )
    /** AppFont.key — the bundled UI font. */
    val fontKey: StateFlow<String> = _fontKey.asStateFlow()

    fun setFontKey(key: String) {
        prefs.edit().putString(KEY_FONT, key).apply()
        _fontKey.value = key
    }

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val PALETTE_DEFAULT = "DEFAULT"
        const val FONT_DEFAULT = "plex_mono"

        private const val KEY_THEME = "theme_mode"
        private const val KEY_CURRENCY = "currency_code"
        private const val KEY_PALETTE = "palette"
        private const val KEY_FONT = "font"

        @Volatile
        private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
    }
}
