package com.endgamefinance.data.db.model

/** Denormalized row for ledger lists; total is the sum of the transaction's splits. */
data class TransactionListItem(
    val id: String,
    val payee: String,
    val timestamp: Long,
    val type: String,
    val notes: String?,
    val isCleared: Boolean,
    val isShared: Boolean,
    val accountName: String,
    val toAccountName: String?,
    val totalAmount: Long,
    val categorySummary: String?,
    /** Icon key of the first categorized split, for row display. */
    val categoryIcon: String?,
    val splitCount: Int,
    /** Balance of the source account immediately after this transaction. */
    val runningBalance: Long,
    /** Balance of the destination account after this transaction; NULL unless a transfer. */
    val toRunningBalance: Long?,
)
