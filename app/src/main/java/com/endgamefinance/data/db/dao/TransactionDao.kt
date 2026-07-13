package com.endgamefinance.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.endgamefinance.data.db.entity.TransactionAudit
import com.endgamefinance.data.db.entity.TransactionEntity
import com.endgamefinance.data.db.entity.TransactionSplit
import com.endgamefinance.data.db.entity.TransactionTag
import com.endgamefinance.data.db.model.TransactionListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert
    suspend fun insertSplits(splits: List<TransactionSplit>)

    @Insert
    suspend fun insertTransactionTags(links: List<TransactionTag>)

    /** A money-touching transaction and its splits must land atomically — always use this. */
    @Transaction
    suspend fun insertWithSplits(transaction: TransactionEntity, splits: List<TransactionSplit>) {
        require(splits.isNotEmpty()) { "A transaction must carry at least one split" }
        require(splits.all { it.transactionId == transaction.id }) { "Split/transaction id mismatch" }
        insertTransaction(transaction)
        insertSplits(splits)
    }

    /**
     * Filterable ledger. Null/empty filters are no-ops. The amount range
     * applies to the transaction's split total; the account filter matches
     * either side of a transfer. runningBalance is the source account's
     * balance immediately after each transaction, computed over the FULL
     * history (independent of active filters), with id as the tiebreaker
     * for identical timestamps.
     */
    @Query(
        """
        SELECT t.id, t.payee, t.timestamp, t.type,
               t.is_cleared AS isCleared, t.is_shared AS isShared,
               a.name AS accountName, b.name AS toAccountName,
               COALESCE((SELECT SUM(s.amount) FROM transaction_splits s
                         WHERE s.transaction_id = t.id), 0) AS totalAmount,
               (SELECT GROUP_CONCAT(c.name, ', ')
                FROM transaction_splits s JOIN categories c ON c.id = s.category_id
                WHERE s.transaction_id = t.id) AS categorySummary,
               (SELECT c.icon FROM transaction_splits s
                JOIN categories c ON c.id = s.category_id
                WHERE s.transaction_id = t.id AND c.icon IS NOT NULL LIMIT 1) AS categoryIcon,
               (SELECT COUNT(*) FROM transaction_splits s
                WHERE s.transaction_id = t.id) AS splitCount,
               COALESCE((
                   SELECT SUM(CASE
                       WHEN t2.type = 'income'   AND t2.account_id    = t.account_id THEN s2.amount
                       WHEN t2.type = 'expense'  AND t2.account_id    = t.account_id THEN -s2.amount
                       WHEN t2.type = 'transfer' AND t2.account_id    = t.account_id THEN -s2.amount
                       WHEN t2.type = 'transfer' AND t2.to_account_id = t.account_id
                           THEN (CASE WHEN s2.category_id IS NULL THEN s2.amount ELSE 0 END)
                       ELSE 0 END)
                   FROM transactions t2
                   JOIN transaction_splits s2 ON s2.transaction_id = t2.id
                   WHERE (t2.account_id = t.account_id OR t2.to_account_id = t.account_id)
                     AND (t2.timestamp < t.timestamp
                          OR (t2.timestamp = t.timestamp AND t2.id <= t.id))
               ), 0) AS runningBalance,
               (CASE WHEN t.to_account_id IS NULL THEN NULL ELSE COALESCE((
                   SELECT SUM(CASE
                       WHEN t2.type = 'income'   AND t2.account_id    = t.to_account_id THEN s2.amount
                       WHEN t2.type = 'expense'  AND t2.account_id    = t.to_account_id THEN -s2.amount
                       WHEN t2.type = 'transfer' AND t2.account_id    = t.to_account_id THEN -s2.amount
                       WHEN t2.type = 'transfer' AND t2.to_account_id = t.to_account_id
                           THEN (CASE WHEN s2.category_id IS NULL THEN s2.amount ELSE 0 END)
                       ELSE 0 END)
                   FROM transactions t2
                   JOIN transaction_splits s2 ON s2.transaction_id = t2.id
                   WHERE (t2.account_id = t.to_account_id OR t2.to_account_id = t.to_account_id)
                     AND (t2.timestamp < t.timestamp
                          OR (t2.timestamp = t.timestamp AND t2.id <= t.id))
               ), 0) END) AS toRunningBalance
        FROM transactions t
        JOIN accounts a ON a.id = t.account_id
        LEFT JOIN accounts b ON b.id = t.to_account_id
        WHERE (:accountId IS NULL OR t.account_id = :accountId OR t.to_account_id = :accountId)
          AND (:payeeQuery = '' OR t.payee LIKE '%' || :payeeQuery || '%')
          AND (:categoryId IS NULL OR EXISTS(
                SELECT 1 FROM transaction_splits s
                WHERE s.transaction_id = t.id AND s.category_id = :categoryId))
          AND (:minCents IS NULL OR COALESCE((SELECT SUM(s.amount) FROM transaction_splits s
                WHERE s.transaction_id = t.id), 0) >= :minCents)
          AND (:maxCents IS NULL OR COALESCE((SELECT SUM(s.amount) FROM transaction_splits s
                WHERE s.transaction_id = t.id), 0) <= :maxCents)
          AND (:startMs IS NULL OR t.timestamp >= :startMs)
          AND (:endMs IS NULL OR t.timestamp < :endMs)
        ORDER BY t.timestamp DESC, t.id DESC
        LIMIT 500
        """,
    )
    fun observeFiltered(
        accountId: String?,
        payeeQuery: String,
        categoryId: String?,
        minCents: Long?,
        maxCents: Long?,
        startMs: Long?,
        endMs: Long?,
    ): Flow<List<TransactionListItem>>

    /** Merchant aggregation: expense spending + visit count per payee in a range. */
    @Query(
        """
        SELECT t.payee AS payee,
               COUNT(DISTINCT t.id) AS visits,
               COALESCE(SUM(s.amount), 0) AS total
        FROM transactions t
        JOIN transaction_splits s ON s.transaction_id = t.id
        WHERE t.type = 'expense'
          AND t.payee != 'Starting Balance'
          AND t.timestamp >= :startMs AND t.timestamp < :endMs
        GROUP BY t.payee
        """,
    )
    fun observeMerchants(startMs: Long, endMs: Long):
        Flow<List<com.endgamefinance.data.db.model.MerchantStat>>

    /** Distinct payees most-recently-used first, filtered by substring. */
    @Query(
        """
        SELECT payee FROM transactions
        WHERE payee LIKE '%' || :query || '%' AND payee != :exclude
        GROUP BY payee
        ORDER BY MAX(timestamp) DESC
        LIMIT 8
        """,
    )
    suspend fun suggestPayees(query: String, exclude: String): List<String>

    @Query(
        """
        SELECT * FROM transactions WHERE payee = :payee
        ORDER BY timestamp DESC LIMIT 1
        """,
    )
    suspend fun latestForPayee(payee: String): TransactionEntity?

    @Query("SELECT * FROM transaction_splits WHERE transaction_id = :transactionId")
    suspend fun splitsFor(transactionId: String): List<TransactionSplit>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transaction_splits WHERE transaction_id = :transactionId")
    suspend fun deleteSplitsFor(transactionId: String)

    @Query("DELETE FROM transaction_tags WHERE transaction_id = :transactionId")
    suspend fun deleteTagLinksFor(transactionId: String)

    @Query("SELECT tag_id FROM transaction_tags WHERE transaction_id = :transactionId")
    suspend fun tagIdsFor(transactionId: String): List<String>

    /** Hard delete; splits, tag links, and audit history cascade away with it. */
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert
    suspend fun insertAuditRows(rows: List<TransactionAudit>)

    @Query(
        """
        SELECT * FROM transaction_audit
        WHERE transaction_id = :transactionId
        ORDER BY changed_at DESC
        """,
    )
    fun observeAuditFor(transactionId: String): Flow<List<TransactionAudit>>

    /** Most-used categories in the trailing window — powers quick-pick chips. */
    @Query(
        """
        SELECT s.category_id FROM transaction_splits s
        JOIN transactions t ON t.id = s.transaction_id
        WHERE s.category_id IS NOT NULL AND t.timestamp >= :sinceMs
        GROUP BY s.category_id
        ORDER BY COUNT(*) DESC
        LIMIT 8
        """,
    )
    fun observeRecentCategoryIds(sinceMs: Long): Flow<List<String>>

    /** Feed for the recurring-pattern detector: real spending/income events only. */
    @Query(
        """
        SELECT t.payee, t.account_id AS accountId, t.timestamp,
               COALESCE((SELECT SUM(s.amount) FROM transaction_splits s
                         WHERE s.transaction_id = t.id), 0) AS totalAmount,
               (SELECT s.category_id FROM transaction_splits s
                WHERE s.transaction_id = t.id AND s.category_id IS NOT NULL
                LIMIT 1) AS categoryId
        FROM transactions t
        WHERE t.type IN ('expense', 'income')
          AND t.payee != 'Starting Balance'
        ORDER BY t.payee, t.timestamp
        """,
    )
    fun observeDetectorRows(): Flow<List<com.endgamefinance.data.db.model.DetectorRow>>
}
