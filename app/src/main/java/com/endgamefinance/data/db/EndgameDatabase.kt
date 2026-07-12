package com.endgamefinance.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.endgamefinance.data.db.dao.AccountDao
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
    entities = [
        Account::class,
        Category::class,
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
    version = 1,
    exportSchema = true,
)
abstract class EndgameDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
}
