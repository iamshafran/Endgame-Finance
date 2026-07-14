package com.endgamefinance.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.endgamefinance.data.db.entity.Account
import com.endgamefinance.data.db.model.AccountWithBalance
import kotlinx.coroutines.flow.Flow

/**
 * Balance derivation (see AccountWithBalance for the sign convention):
 *   income into the account      → +splits
 *   expense from the account     → −splits (on a liability this deepens debt)
 *   transfer out of the account  → −splits (full amount leaves the source)
 *   transfer into the account    → +uncategorized splits only
 *
 * The last rule implements CLAUDE.md's loan-payment accounting: in a transfer,
 * a category-less split is money that arrives at the destination (principal),
 * while a categorized split is a cost paid along the way (interest) that
 * reduces net worth instead of arriving anywhere.
 */
private const val BALANCE_SUBQUERY = """
    COALESCE((
        SELECT SUM(CASE
            WHEN t.type = 'income'   AND t.account_id    = accounts.id THEN s.amount
            WHEN t.type = 'expense'  AND t.account_id    = accounts.id THEN -s.amount
            WHEN t.type = 'transfer' AND t.account_id    = accounts.id THEN -s.amount
            WHEN t.type = 'transfer' AND t.to_account_id = accounts.id
                THEN (CASE WHEN s.category_id IS NULL THEN s.amount ELSE 0 END)
            ELSE 0 END)
        FROM transactions t
        JOIN transaction_splits s ON s.transaction_id = t.id
        WHERE t.account_id = accounts.id OR t.to_account_id = accounts.id
    ), 0)
"""

@Dao
interface AccountDao {

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Long

    @Query(
        """
        SELECT accounts.*, $BALANCE_SUBQUERY AS balance
        FROM accounts
        WHERE is_active = 1
        ORDER BY type, name
        """,
    )
    fun observeActiveWithBalances(): Flow<List<AccountWithBalance>>

    @Query("SELECT accounts.*, $BALANCE_SUBQUERY AS balance FROM accounts WHERE id = :id")
    suspend fun getWithBalance(id: String): AccountWithBalance?

    /** ALL accounts including archived — history math must span everything. */
    @Query("SELECT accounts.*, $BALANCE_SUBQUERY AS balance FROM accounts")
    suspend fun allWithBalancesOnce(): List<AccountWithBalance>

    /** Balance counting CLEARED transactions only — the figure a bank statement shows. */
    @Query(
        """
        SELECT COALESCE((
            SELECT SUM(CASE
                WHEN t.type = 'income'   AND t.account_id    = :accountId THEN s.amount
                WHEN t.type = 'expense'  AND t.account_id    = :accountId THEN -s.amount
                WHEN t.type = 'transfer' AND t.account_id    = :accountId THEN -s.amount
                WHEN t.type = 'transfer' AND t.to_account_id = :accountId
                    THEN (CASE WHEN s.category_id IS NULL THEN s.amount ELSE 0 END)
                ELSE 0 END)
            FROM transactions t
            JOIN transaction_splits s ON s.transaction_id = t.id
            WHERE (t.account_id = :accountId OR t.to_account_id = :accountId)
              AND t.is_cleared = 1
        ), 0)
        """,
    )
    fun observeClearedBalance(accountId: String): Flow<Long>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): Account?

    @Query("SELECT * FROM accounts")
    suspend fun getAllOnce(): List<Account>

    @Query("SELECT * FROM accounts WHERE is_active = 1 ORDER BY type, name")
    fun observeActive(): Flow<List<Account>>

    @Insert
    suspend fun insert(account: Account)

    @Update
    suspend fun update(account: Account)
}
