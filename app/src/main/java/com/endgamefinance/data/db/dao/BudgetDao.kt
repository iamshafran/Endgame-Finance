package com.endgamefinance.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.endgamefinance.data.db.entity.Budget
import kotlinx.coroutines.flow.Flow

data class CategorySpend(val categoryId: String, val spent: Long)
data class CategoryMonthSpend(val categoryId: String, val month: String, val spent: Long)
data class DaySpend(val day: String, val spent: Long)

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE month = :month")
    fun observeForMonth(month: String): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE month = :month")
    suspend fun forMonthOnce(month: String): List<Budget>

    @Query("SELECT * FROM budgets WHERE category_id = :categoryId AND month = :month LIMIT 1")
    suspend fun get(categoryId: String, month: String): Budget?

    @Query("SELECT * FROM budgets")
    suspend fun allOnce(): List<Budget>

    @Insert
    suspend fun insert(budget: Budget)

    @Update
    suspend fun update(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)

    /**
     * Category spending in [startMs, endMs). Includes categorized splits inside
     * transfers (loan interest is real category spending) per the M1 convention.
     */
    @Query(
        """
        SELECT s.category_id AS categoryId, SUM(s.amount) AS spent
        FROM transaction_splits s
        JOIN transactions t ON t.id = s.transaction_id
        WHERE s.category_id IS NOT NULL
          AND t.type IN ('expense', 'transfer')
          AND t.timestamp >= :startMs AND t.timestamp < :endMs
        GROUP BY s.category_id
        """,
    )
    fun observeSpentByCategory(startMs: Long, endMs: Long): Flow<List<CategorySpend>>

    /** Month income for the zero-based nag; starting balances are not income. */
    @Query(
        """
        SELECT COALESCE(SUM(s.amount), 0)
        FROM transaction_splits s
        JOIN transactions t ON t.id = s.transaction_id
        WHERE t.type = 'income'
          AND t.payee != 'Starting Balance'
          AND t.timestamp >= :startMs AND t.timestamp < :endMs
        """,
    )
    fun observeIncome(startMs: Long, endMs: Long): Flow<Long>

    /**
     * Spending per local calendar day in [startMs, endMs): expenses plus
     * categorized transfer splits (loan interest), starting balances excluded.
     */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', t.timestamp / 1000, 'unixepoch', 'localtime') AS day,
               SUM(s.amount) AS spent
        FROM transaction_splits s
        JOIN transactions t ON t.id = s.transaction_id
        WHERE t.timestamp >= :startMs AND t.timestamp < :endMs
          AND t.payee != 'Starting Balance'
          AND (t.type = 'expense' OR (t.type = 'transfer' AND s.category_id IS NOT NULL))
        GROUP BY day
        """,
    )
    fun observeSpendByDay(startMs: Long, endMs: Long): Flow<List<DaySpend>>

    /** Uncategorized expense total in [startMs, endMs) — reports must not lose it. */
    @Query(
        """
        SELECT COALESCE(SUM(s.amount), 0)
        FROM transaction_splits s
        JOIN transactions t ON t.id = s.transaction_id
        WHERE t.timestamp >= :startMs AND t.timestamp < :endMs
          AND t.payee != 'Starting Balance'
          AND t.type = 'expense'
          AND s.category_id IS NULL
        """,
    )
    fun observeUncategorizedSpend(startMs: Long, endMs: Long): Flow<Long>

    /** Total spending in [startMs, endMs) — same definition as observeSpendByDay. */
    @Query(
        """
        SELECT COALESCE(SUM(s.amount), 0)
        FROM transaction_splits s
        JOIN transactions t ON t.id = s.transaction_id
        WHERE t.timestamp >= :startMs AND t.timestamp < :endMs
          AND t.payee != 'Starting Balance'
          AND (t.type = 'expense' OR (t.type = 'transfer' AND s.category_id IS NOT NULL))
        """,
    )
    fun observeTotalSpend(startMs: Long, endMs: Long): Flow<Long>

    /** Full per-category monthly spend history (device zone) — feeds carry chains and rolling averages. */
    @Query(
        """
        SELECT s.category_id AS categoryId,
               strftime('%Y-%m', t.timestamp / 1000, 'unixepoch', 'localtime') AS month,
               SUM(s.amount) AS spent
        FROM transaction_splits s
        JOIN transactions t ON t.id = s.transaction_id
        WHERE s.category_id IS NOT NULL
          AND t.type IN ('expense', 'transfer')
        GROUP BY s.category_id, month
        """,
    )
    suspend fun spentByCategoryMonth(): List<CategoryMonthSpend>
}
