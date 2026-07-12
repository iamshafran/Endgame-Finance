package com.endgamefinance.data.db

import android.content.Context
import androidx.room.Room
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

    private fun build(context: Context): EndgameDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = DbKeyManager.getOrCreatePassphrase(context)
        return Room.databaseBuilder(context, EndgameDatabase::class.java, DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
    }
}
