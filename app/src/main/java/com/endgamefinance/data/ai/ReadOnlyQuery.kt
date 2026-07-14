package com.endgamefinance.data.ai

import android.content.Context
import android.database.Cursor
import com.endgamefinance.data.security.DbKeyManager
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Executes a validated SELECT against a SEPARATE, physically read-only
 * SQLCipher connection that can only see the whitelisted views. This is the
 * hard guarantee: even if [SqlGuard] were somehow bypassed, OPEN_READONLY
 * makes every write fail at the SQLite layer. The app's writable Room
 * connection is never used for AI queries.
 */
object ReadOnlyQuery {

    data class Result(
        val columns: List<String>,
        val rows: List<List<Any?>>,
        val truncated: Boolean,
    )

    class UnsafeQueryException(message: String) : Exception(message)

    private const val MAX_ROWS = 500

    fun run(context: Context, sql: String): Result {
        SqlGuard.reject(sql)?.let { throw UnsafeQueryException(it) }

        System.loadLibrary("sqlcipher")
        val path = context.getDatabasePath("endgame.db").absolutePath
        val passphrase = DbKeyManager.getOrCreatePassphrase(context)

        val db = SQLiteDatabase.openDatabase(
            path, passphrase, null, SQLiteDatabase.OPEN_READONLY, null,
        )
        try {
            db.rawQuery(sql, null).use { c ->
                val cols = c.columnNames.toList()
                val rows = ArrayList<List<Any?>>()
                var truncated = false
                while (c.moveToNext()) {
                    if (rows.size >= MAX_ROWS) {
                        truncated = true
                        break
                    }
                    rows.add(
                        (0 until c.columnCount).map { i ->
                            when (c.getType(i)) {
                                Cursor.FIELD_TYPE_NULL -> null
                                Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                                Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                                else -> c.getString(i)
                            }
                        },
                    )
                }
                return Result(cols, rows, truncated)
            }
        } finally {
            db.close()
        }
    }
}
