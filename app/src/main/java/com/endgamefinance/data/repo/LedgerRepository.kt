package com.endgamefinance.data.repo

import androidx.room.withTransaction
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.entity.Account
import com.endgamefinance.data.db.entity.TransactionAudit
import com.endgamefinance.data.db.entity.TransactionEntity
import com.endgamefinance.data.db.entity.TransactionSplit
import com.endgamefinance.data.db.entity.TransactionTag
import com.endgamefinance.util.Money
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/** Ledger write operations. Multi-table effects are wrapped in DB transactions here. */
class LedgerRepository(private val db: EndgameDatabase) {

    companion object {
        const val STARTING_BALANCE_PAYEE = "Starting Balance"
    }

    /**
     * Creates an account. A nonzero starting balance becomes a visible
     * "Starting Balance" transaction so the ledger stays fully derivable.
     *
     * [initialBalanceCents] is the SIGNED desired balance in the app's sign
     * convention (liability debt = negative, overdrawn asset = negative,
     * overpaid liability = positive). Direction follows uniformly:
     * positive → income into the account; negative → expense of the magnitude.
     */
    suspend fun createAccount(account: Account, initialBalanceCents: Long) {
        db.withTransaction {
            db.accountDao().insert(account)
            if (initialBalanceCents != 0L) {
                val txId = UUID.randomUUID().toString()
                val type = if (initialBalanceCents > 0) "income" else "expense"
                db.transactionDao().insertWithSplits(
                    TransactionEntity(
                        id = txId,
                        accountId = account.id,
                        timestamp = System.currentTimeMillis(),
                        payee = STARTING_BALANCE_PAYEE,
                        type = type,
                        isCleared = true,
                    ),
                    listOf(
                        TransactionSplit(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            categoryId = null,
                            amount = kotlin.math.abs(initialBalanceCents),
                        ),
                    ),
                )
            }
        }
    }

    /**
     * Writes a complete transaction: entity + splits + tag links, atomically.
     * Splits carry positive magnitudes; [transaction.type] determines direction.
     */
    suspend fun createTransaction(
        transaction: TransactionEntity,
        splits: List<TransactionSplit>,
        tagIds: List<String>,
    ) {
        require(splits.all { it.amount > 0 }) { "Split amounts must be positive magnitudes" }
        if (transaction.type == "transfer") {
            requireNotNull(transaction.toAccountId) { "Transfers need a destination account" }
            require(transaction.toAccountId != transaction.accountId) {
                "Transfers need two distinct accounts"
            }
        }
        db.withTransaction {
            db.transactionDao().insertWithSplits(transaction, splits)
            if (tagIds.isNotEmpty()) {
                db.transactionDao().insertTransactionTags(
                    tagIds.map { TransactionTag(transactionId = transaction.id, tagId = it) },
                )
            }
        }
    }

    /**
     * Updates a transaction, recording every changed field as an immutable
     * audit row (human-readable old → new) in the same DB transaction.
     * Splits and tag links are replaced wholesale; their change is audited as
     * 'amount' and 'categories' diffs.
     */
    suspend fun updateTransaction(
        updated: TransactionEntity,
        newSplits: List<TransactionSplit>,
        newTagIds: List<String>,
    ) {
        require(newSplits.isNotEmpty()) { "A transaction must carry at least one split" }
        db.withTransaction {
            val old = requireNotNull(db.transactionDao().getById(updated.id)) {
                "Transaction ${updated.id} no longer exists"
            }
            val oldSplits = db.transactionDao().splitsFor(updated.id)
            val now = System.currentTimeMillis()
            val categoryNames = db.categoryDao().getAllOnce().associate { it.id to it.name }

            val audits = mutableListOf<TransactionAudit>()
            fun audit(field: String, oldValue: String?, newValue: String?) {
                if (oldValue != newValue) {
                    audits += TransactionAudit(
                        id = UUID.randomUUID().toString(),
                        transactionId = updated.id,
                        fieldName = field,
                        oldValue = oldValue,
                        newValue = newValue,
                        changedAt = now,
                    )
                }
            }

            suspend fun nameOf(accountId: String?): String? =
                accountId?.let { db.accountDao().getById(it)?.name ?: it }

            fun dateOf(ts: Long): String = DateFormat.getDateInstance().format(Date(ts))
            fun categoriesOf(splits: List<TransactionSplit>): String =
                splits.joinToString(", ") { split ->
                    val name = split.categoryId?.let { categoryNames[it] } ?: "Uncategorized"
                    "$name ${Money.format(split.amount)}"
                }

            audit("payee", old.payee, updated.payee)
            audit("type", old.type, updated.type)
            audit("account", nameOf(old.accountId), nameOf(updated.accountId))
            audit("to_account", nameOf(old.toAccountId), nameOf(updated.toAccountId))
            audit("date", dateOf(old.timestamp), dateOf(updated.timestamp))
            audit("notes", old.notes, updated.notes)
            audit("is_cleared", if (old.isCleared) "cleared" else "uncleared",
                if (updated.isCleared) "cleared" else "uncleared")
            audit("is_shared", if (old.isShared) "shared" else "not shared",
                if (updated.isShared) "shared" else "not shared")
            audit("amount", Money.format(oldSplits.sumOf { it.amount }),
                Money.format(newSplits.sumOf { it.amount }))
            audit("categories", categoriesOf(oldSplits), categoriesOf(newSplits))

            db.transactionDao().updateTransaction(updated)
            db.transactionDao().deleteSplitsFor(updated.id)
            db.transactionDao().insertSplits(newSplits)
            db.transactionDao().deleteTagLinksFor(updated.id)
            if (newTagIds.isNotEmpty()) {
                db.transactionDao().insertTransactionTags(
                    newTagIds.map { TransactionTag(transactionId = updated.id, tagId = it) },
                )
            }
            if (audits.isNotEmpty()) {
                db.transactionDao().insertAuditRows(audits)
            }
        }
    }

    /** Reconciliation check-off: flips is_cleared with an audit row, touching nothing else. */
    suspend fun setCleared(transactionId: String, cleared: Boolean) {
        db.withTransaction {
            val old = db.transactionDao().getById(transactionId) ?: return@withTransaction
            if (old.isCleared == cleared) return@withTransaction
            db.transactionDao().updateTransaction(old.copy(isCleared = cleared))
            db.transactionDao().insertAuditRows(
                listOf(
                    TransactionAudit(
                        id = UUID.randomUUID().toString(),
                        transactionId = transactionId,
                        fieldName = "is_cleared",
                        oldValue = if (old.isCleared) "cleared" else "uncleared",
                        newValue = if (cleared) "cleared" else "uncleared",
                        changedAt = System.currentTimeMillis(),
                    ),
                ),
            )
        }
    }

    /** Hard delete after user confirmation; cascades take splits/tags/audit with it. */
    suspend fun deleteTransaction(transactionId: String) =
        db.transactionDao().deleteById(transactionId)

    suspend fun updateAccount(account: Account) = db.accountDao().update(account)

    /** "Delete" for accounts is archival — history must survive. */
    suspend fun archiveAccount(account: Account) =
        db.accountDao().update(account.copy(isActive = false))
}
