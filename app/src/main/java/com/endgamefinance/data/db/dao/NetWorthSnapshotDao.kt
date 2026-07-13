package com.endgamefinance.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.endgamefinance.data.db.entity.NetWorthSnapshot
import kotlinx.coroutines.flow.Flow

/** (day, accountId) → signed balance change, for snapshot backfill. */
data class DayAccountDelta(val day: String, val accountId: String, val delta: Long)

@Dao
interface NetWorthSnapshotDao {

    @Query("SELECT * FROM net_worth_snapshots ORDER BY snapshot_date")
    fun observeAll(): Flow<List<NetWorthSnapshot>>

    @Query("SELECT COUNT(*) FROM net_worth_snapshots")
    suspend fun count(): Long

    @Query(
        """
        SELECT * FROM net_worth_snapshots
        WHERE snapshot_date >= :dayStartMs AND snapshot_date < :dayEndMs
        LIMIT 1
        """,
    )
    suspend fun forDay(dayStartMs: Long, dayEndMs: Long): NetWorthSnapshot?

    @Insert
    suspend fun insert(snapshot: NetWorthSnapshot)

    @Insert
    suspend fun insertAll(snapshots: List<NetWorthSnapshot>)

    @Update
    suspend fun update(snapshot: NetWorthSnapshot)

    /**
     * Per-account signed balance change per local day, across all history.
     * Same CASE logic as AccountDao's BALANCE_SUBQUERY, split by day:
     * source side (income +, expense −, transfer −) UNION the destination
     * side of transfers (+ uncategorized splits only).
     */
    @Query(
        """
        SELECT day, accountId, SUM(delta) AS delta FROM (
            SELECT strftime('%Y-%m-%d', t.timestamp / 1000, 'unixepoch', 'localtime') AS day,
                   t.account_id AS accountId,
                   CASE WHEN t.type = 'income' THEN s.amount
                        WHEN t.type = 'expense' THEN -s.amount
                        WHEN t.type = 'transfer' THEN -s.amount
                        ELSE 0 END AS delta
            FROM transactions t
            JOIN transaction_splits s ON s.transaction_id = t.id
            UNION ALL
            SELECT strftime('%Y-%m-%d', t.timestamp / 1000, 'unixepoch', 'localtime') AS day,
                   t.to_account_id AS accountId,
                   CASE WHEN s.category_id IS NULL THEN s.amount ELSE 0 END AS delta
            FROM transactions t
            JOIN transaction_splits s ON s.transaction_id = t.id
            WHERE t.type = 'transfer' AND t.to_account_id IS NOT NULL
        )
        GROUP BY day, accountId
        """,
    )
    suspend fun dailyAccountDeltas(): List<DayAccountDelta>
}
