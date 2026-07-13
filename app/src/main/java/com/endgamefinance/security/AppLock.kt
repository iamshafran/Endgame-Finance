package com.endgamefinance.security

import android.content.Context

/**
 * App-lock state. Gates the UI ONLY — deliberately independent of DB
 * encryption (see docs/security.md): losing the credential can never
 * lose data, and background workers keep functioning while locked.
 */
object AppLock {

    private const val PREFS = "security_prefs"
    private const val KEY_ENABLED = "app_lock_enabled"
    private const val KEY_TIMEOUT_MIN = "lock_timeout_min"

    /** Process-lifetime flag: has the user authenticated since process start? */
    @Volatile
    private var unlockedThisProcess = false

    @Volatile
    private var lastBackgroundedAt = 0L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) unlockedThisProcess = true // enabling counts as authenticated now
    }

    /** 0 = lock immediately on backgrounding. */
    fun timeoutMinutes(context: Context): Int =
        prefs(context).getInt(KEY_TIMEOUT_MIN, 1)

    fun setTimeoutMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_TIMEOUT_MIN, minutes).apply()
    }

    fun markUnlocked() {
        unlockedThisProcess = true
        lastBackgroundedAt = 0L
    }

    fun noteBackgrounded() {
        lastBackgroundedAt = System.currentTimeMillis()
    }

    fun requiresUnlock(context: Context): Boolean {
        if (!isEnabled(context)) return false
        if (!unlockedThisProcess) return true
        if (lastBackgroundedAt == 0L) return false
        val elapsed = System.currentTimeMillis() - lastBackgroundedAt
        return elapsed >= timeoutMinutes(context) * 60_000L
    }
}
