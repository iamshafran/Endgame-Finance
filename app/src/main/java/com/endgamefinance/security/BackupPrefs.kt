package com.endgamefinance.security

import android.content.Context

object BackupPrefs {

    private const val PREFS = "backup_prefs"
    private const val KEY_LAST_BACKUP = "last_backup_at"
    private const val KEY_NUDGE_DAYS = "nudge_days"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastBackupAt(context: Context): Long = prefs(context).getLong(KEY_LAST_BACKUP, 0L)

    fun markBackupNow(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_BACKUP, System.currentTimeMillis()).apply()
    }

    /** 0 = nudge disabled. Default 30 days per the milestone spec. */
    fun nudgeDays(context: Context): Int = prefs(context).getInt(KEY_NUDGE_DAYS, 30)

    fun setNudgeDays(context: Context, days: Int) {
        prefs(context).edit().putInt(KEY_NUDGE_DAYS, days).apply()
    }

    fun isNudgeDue(context: Context): Boolean {
        val days = nudgeDays(context)
        if (days <= 0) return false
        val last = lastBackupAt(context)
        if (last == 0L) return true // never backed up
        return System.currentTimeMillis() - last >= days * 86_400_000L
    }
}
