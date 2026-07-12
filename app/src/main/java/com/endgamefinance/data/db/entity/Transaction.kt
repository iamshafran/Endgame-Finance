package com.endgamefinance.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
        ),
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["to_account_id"],
        ),
    ],
    indices = [Index("account_id"), Index("to_account_id"), Index("timestamp")],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    /** Transfers / liability payments only. */
    @ColumnInfo(name = "to_account_id") val toAccountId: String? = null,
    /** Epoch ms. */
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "payee") val payee: String,
    @ColumnInfo(name = "notes") val notes: String? = null,
    /** 'expense', 'income', 'transfer' */
    @ColumnInfo(name = "type") val type: String,
    /** Reconciliation state. */
    @ColumnInfo(name = "is_cleared", defaultValue = "0") val isCleared: Boolean = false,
    /** Shared/reimbursable flag. */
    @ColumnInfo(name = "is_shared", defaultValue = "0") val isShared: Boolean = false,
)

@Entity(
    tableName = "transaction_splits",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
        ),
    ],
    indices = [Index("transaction_id"), Index("category_id")],
)
data class TransactionSplit(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    /** Cents. */
    @ColumnInfo(name = "amount") val amount: Long,
)

/** Immutable edit history — rows are only ever inserted, never updated or deleted by app code. */
@Entity(
    tableName = "transaction_audit",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("transaction_id")],
)
data class TransactionAudit(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "field_name") val fieldName: String,
    @ColumnInfo(name = "old_value") val oldValue: String? = null,
    @ColumnInfo(name = "new_value") val newValue: String? = null,
    @ColumnInfo(name = "changed_at") val changedAt: Long,
)
