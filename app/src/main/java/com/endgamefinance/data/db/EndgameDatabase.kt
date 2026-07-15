package com.endgamefinance.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.endgamefinance.data.db.dao.AccountDao
import com.endgamefinance.data.db.dao.BudgetDao
import com.endgamefinance.data.db.dao.CategoryDao
import com.endgamefinance.data.db.dao.EnvelopeDao
import com.endgamefinance.data.db.dao.NetWorthSnapshotDao
import com.endgamefinance.data.db.dao.ReminderDao
import com.endgamefinance.data.db.dao.TagDao
import com.endgamefinance.data.db.dao.TransactionDao
import com.endgamefinance.data.db.entity.Account
import com.endgamefinance.data.db.entity.Budget
import com.endgamefinance.data.db.entity.Category
import com.endgamefinance.data.db.entity.Envelope
import com.endgamefinance.data.db.entity.EnvelopeTransfer
import com.endgamefinance.data.db.entity.NetWorthSnapshot
import com.endgamefinance.data.db.entity.Reminder
import com.endgamefinance.data.db.entity.Tag
import com.endgamefinance.data.db.entity.TransactionAudit
import com.endgamefinance.data.db.entity.TransactionEntity
import com.endgamefinance.data.db.entity.TransactionSplit
import com.endgamefinance.data.db.entity.TransactionTag

@Database(
    version = 6,
    exportSchema = true,
    entities = [
        Account::class,
        Category::class,
        com.endgamefinance.data.db.entity.CategoryGroup::class,
        Tag::class,
        TransactionTag::class,
        TransactionEntity::class,
        TransactionSplit::class,
        TransactionAudit::class,
        Reminder::class,
        Budget::class,
        Envelope::class,
        EnvelopeTransfer::class,
        NetWorthSnapshot::class,
    ],
)
abstract class EndgameDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryGroupDao(): com.endgamefinance.data.db.dao.CategoryGroupDao
    abstract fun tagDao(): TagDao
    abstract fun budgetDao(): BudgetDao
    abstract fun envelopeDao(): EnvelopeDao
    abstract fun reminderDao(): ReminderDao
    abstract fun netWorthSnapshotDao(): NetWorthSnapshotDao
    abstract fun backupDao(): com.endgamefinance.data.backup.BackupDao
}
