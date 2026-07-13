package com.endgamefinance.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.endgamefinance.data.security.DbKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Process-wide singleton. Encryption is wired here from day one — never build the DB elsewhere. */
object DatabaseProvider {

    private const val DB_NAME = "endgame.db"

    @Volatile
    private var instance: EndgameDatabase? = null

    fun get(context: Context): EndgameDatabase =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    /** v2: categories gained a nullable icon column. */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE categories ADD COLUMN icon TEXT")
        }
    }

    /** v3: accounts gained original_principal for loan payoff progress (documented in CLAUDE.md). */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN original_principal INTEGER")
        }
    }

    private fun build(context: Context): EndgameDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = DbKeyManager.getOrCreatePassphrase(context)
        return Room.databaseBuilder(context, EndgameDatabase::class.java, DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }
}
