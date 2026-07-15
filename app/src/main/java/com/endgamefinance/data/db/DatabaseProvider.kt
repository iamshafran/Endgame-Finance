package com.endgamefinance.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.endgamefinance.data.ai.QueryViews
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

    /** v4: reminders gained frequency_interval + anchor_day (documented in CLAUDE.md). */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE reminders ADD COLUMN frequency_interval INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE reminders ADD COLUMN anchor_day INTEGER")
        }
    }

    /**
     * v5: reminders gained to_account_id (transfer/repayment reminders).
     * Adding a FK requires the SQLite table-rebuild dance; CREATE statements
     * are copied verbatim from Room's exported 5.json schema.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reminders_new` (`id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, `category_id` TEXT, `account_id` TEXT NOT NULL, " +
                    "`to_account_id` TEXT, `amount` INTEGER, `frequency` TEXT NOT NULL, " +
                    "`frequency_interval` INTEGER NOT NULL DEFAULT 1, `anchor_day` INTEGER, " +
                    "`next_due_date` INTEGER NOT NULL, `is_auto_post` INTEGER NOT NULL DEFAULT 0, " +
                    "`is_auto_detected` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                    "FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                    "FOREIGN KEY(`to_account_id`) REFERENCES `accounts`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            db.execSQL(
                "INSERT INTO reminders_new (id, name, category_id, account_id, amount, " +
                    "frequency, frequency_interval, anchor_day, next_due_date, " +
                    "is_auto_post, is_auto_detected) " +
                    "SELECT id, name, category_id, account_id, amount, frequency, " +
                    "frequency_interval, anchor_day, next_due_date, is_auto_post, " +
                    "is_auto_detected FROM reminders",
            )
            db.execSQL("DROP TABLE reminders")
            db.execSQL("ALTER TABLE reminders_new RENAME TO reminders")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_category_id` ON `reminders` (`category_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_account_id` ON `reminders` (`account_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_to_account_id` ON `reminders` (`to_account_id`)")
        }
    }

    /**
     * v6 (owner-approved 2026-07-15): parent/child categories become category
     * GROUPS + one flat level of categories.
     *  - Each parent that has children becomes a group (reusing its id).
     *  - A parent with its own transactions/budgets/reminders survives as a
     *    same-named category inside its group, so no ledger history changes.
     *  - Purely structural parents (no references) are deleted.
     *  - Categories left without a group land in a sentinel "Other" group of
     *    their type — the app requires every category to have a group.
     *  - parent_id is cleared everywhere and deprecated (column retained).
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `category_groups` (`id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, `type` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL("ALTER TABLE categories ADD COLUMN group_id TEXT")
            // Parents-with-children become groups, keeping their ids
            db.execSQL(
                "INSERT INTO category_groups (id, name, type) " +
                    "SELECT id, name, type FROM categories WHERE id IN " +
                    "(SELECT DISTINCT parent_id FROM categories WHERE parent_id IS NOT NULL)",
            )
            // Children move into their parent's group
            db.execSQL("UPDATE categories SET group_id = parent_id WHERE parent_id IS NOT NULL")
            // Referenced parents survive as a same-named category inside their group
            db.execSQL(
                "UPDATE categories SET group_id = id WHERE id IN (SELECT id FROM category_groups) " +
                    "AND (EXISTS(SELECT 1 FROM transaction_splits s WHERE s.category_id = categories.id) " +
                    "OR EXISTS(SELECT 1 FROM budgets b WHERE b.category_id = categories.id) " +
                    "OR EXISTS(SELECT 1 FROM reminders r WHERE r.category_id = categories.id))",
            )
            // Unreferenced parents were purely structural — the group replaces them
            db.execSQL(
                "DELETE FROM categories WHERE id IN (SELECT id FROM category_groups) " +
                    "AND group_id IS NULL",
            )
            // Strays fold into sentinel "Other" groups (created only if needed)
            db.execSQL(
                "INSERT INTO category_groups (id, name, type) " +
                    "SELECT 'group_other_expense', 'Other', 'expense' " +
                    "WHERE EXISTS(SELECT 1 FROM categories WHERE group_id IS NULL AND type = 'expense')",
            )
            db.execSQL(
                "INSERT INTO category_groups (id, name, type) " +
                    "SELECT 'group_other_income', 'Other', 'income' " +
                    "WHERE EXISTS(SELECT 1 FROM categories WHERE group_id IS NULL AND type = 'income')",
            )
            db.execSQL(
                "UPDATE categories SET group_id = 'group_other_expense' " +
                    "WHERE group_id IS NULL AND type = 'expense'",
            )
            db.execSQL(
                "UPDATE categories SET group_id = 'group_other_income' " +
                    "WHERE group_id IS NULL AND type = 'income'",
            )
            db.execSQL("UPDATE categories SET parent_id = NULL")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_categories_group_id` ON `categories` (`group_id`)",
            )
        }
    }

    /** Recreates the AI read-only views on every open so definitions stay current. */
    private val viewCallback = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            QueryViews.ddl.forEach { db.execSQL(it) }
        }
    }

    private fun build(context: Context): EndgameDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = DbKeyManager.getOrCreatePassphrase(context)
        return Room.databaseBuilder(context, EndgameDatabase::class.java, DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            )
            .addCallback(viewCallback)
            .build()
    }
}
