package com.endgamefinance.data.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

/** One CSV row = one split, denormalized with names. */
data class CsvRow(
    val timestamp: Long,
    val type: String,
    val payee: String,
    val accountName: String,
    val toAccountName: String?,
    val categoryName: String?,
    val amount: Long,
    val notes: String?,
    val isCleared: Boolean,
    val isShared: Boolean,
)

@Dao
interface BackupDao {

    // ---- Full-table reads for backup ----
    @Query("SELECT * FROM accounts") suspend fun allAccounts(): List<Account>
    @Query("SELECT * FROM categories") suspend fun allCategories(): List<Category>
    @Query("SELECT * FROM category_groups")
    suspend fun allCategoryGroups(): List<com.endgamefinance.data.db.entity.CategoryGroup>
    @Query("SELECT * FROM tags") suspend fun allTags(): List<Tag>
    @Query("SELECT * FROM transaction_tags") suspend fun allTransactionTags(): List<TransactionTag>
    @Query("SELECT * FROM transactions") suspend fun allTransactions(): List<TransactionEntity>
    @Query("SELECT * FROM transaction_splits") suspend fun allSplits(): List<TransactionSplit>
    @Query("SELECT * FROM transaction_audit") suspend fun allAudit(): List<TransactionAudit>
    @Query("SELECT * FROM reminders") suspend fun allReminders(): List<Reminder>
    @Query("SELECT * FROM budgets") suspend fun allBudgets(): List<Budget>
    @Query("SELECT * FROM envelopes") suspend fun allEnvelopes(): List<Envelope>
    @Query("SELECT * FROM envelope_transfers") suspend fun allEnvelopeTransfers(): List<EnvelopeTransfer>
    @Query("SELECT * FROM net_worth_snapshots") suspend fun allSnapshots(): List<NetWorthSnapshot>

    // ---- Clears, child tables first (FK-safe order) ----
    @Query("DELETE FROM transaction_tags") suspend fun clearTransactionTags()
    @Query("DELETE FROM transaction_splits") suspend fun clearSplits()
    @Query("DELETE FROM transaction_audit") suspend fun clearAudit()
    @Query("DELETE FROM envelope_transfers") suspend fun clearEnvelopeTransfers()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM reminders") suspend fun clearReminders()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM envelopes") suspend fun clearEnvelopes()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM category_groups") suspend fun clearCategoryGroups()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM net_worth_snapshots") suspend fun clearSnapshots()

    // ---- Bulk inserts for restore ----
    @Insert suspend fun insertAccounts(rows: List<Account>)
    @Insert suspend fun insertCategories(rows: List<Category>)
    @Insert
    suspend fun insertCategoryGroups(rows: List<com.endgamefinance.data.db.entity.CategoryGroup>)
    @Insert suspend fun insertTags(rows: List<Tag>)
    @Insert suspend fun insertTransactionTags(rows: List<TransactionTag>)
    @Insert suspend fun insertTransactions(rows: List<TransactionEntity>)
    @Insert suspend fun insertSplits(rows: List<TransactionSplit>)
    @Insert suspend fun insertAudit(rows: List<TransactionAudit>)
    @Insert suspend fun insertReminders(rows: List<Reminder>)
    @Insert suspend fun insertBudgets(rows: List<Budget>)
    @Insert suspend fun insertEnvelopes(rows: List<Envelope>)
    @Insert suspend fun insertEnvelopeTransfers(rows: List<EnvelopeTransfer>)
    @Insert suspend fun insertSnapshots(rows: List<NetWorthSnapshot>)

    /** Denormalized split rows for the plain CSV export. */
    @Query(
        """
        SELECT t.timestamp, t.type, t.payee,
               a.name AS accountName, b.name AS toAccountName, c.name AS categoryName,
               s.amount, t.notes,
               t.is_cleared AS isCleared, t.is_shared AS isShared
        FROM transaction_splits s
        JOIN transactions t ON t.id = s.transaction_id
        JOIN accounts a ON a.id = t.account_id
        LEFT JOIN accounts b ON b.id = t.to_account_id
        LEFT JOIN categories c ON c.id = s.category_id
        ORDER BY t.timestamp
        """,
    )
    suspend fun csvRows(): List<CsvRow>
}
