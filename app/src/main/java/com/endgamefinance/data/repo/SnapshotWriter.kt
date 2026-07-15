package com.endgamefinance.data.repo

import androidx.room.withTransaction
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.entity.Account
import com.endgamefinance.data.db.entity.NetWorthSnapshot
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Writes net_worth_snapshots: one row per local day, upserted by the
 * WorkManager check (app open + 6-hourly). The trend chart reads ONLY from
 * snapshots — never recomputed live (acceptance criterion).
 *
 * On the very first write, backfills the trailing 90 days exactly by walking
 * per-account daily deltas backward from current derived balances.
 */
object SnapshotWriter {

    private const val MIN_BACKFILL_DAYS = 90
    private const val MAX_BACKFILL_DAYS = 400

    /**
     * Drops all snapshots and backfills from scratch. Call after bulk imports:
     * the first-launch backfill predates imported history, so the trend would
     * otherwise stay flat before the import date.
     */
    suspend fun rebuild(db: EndgameDatabase) {
        db.withTransaction {
            db.netWorthSnapshotDao().clearAll()
            backfill(db)
        }
    }

    suspend fun writeToday(db: EndgameDatabase) {
        db.withTransaction {
            if (db.netWorthSnapshotDao().count() == 0L) {
                backfill(db)
                return@withTransaction
            }
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now()
            val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val accounts = db.accountDao().allWithBalancesOnce()
            val totals = totalsOf(accounts.associate { it.account to it.balance })

            val existing = db.netWorthSnapshotDao().forDay(dayStart, dayEnd)
            if (existing != null) {
                db.netWorthSnapshotDao().update(
                    existing.copy(
                        totalAssets = totals.first,
                        totalLiabilities = totals.second,
                        netWorth = totals.first - totals.second,
                    ),
                )
            } else {
                db.netWorthSnapshotDao().insert(
                    NetWorthSnapshot(
                        id = UUID.randomUUID().toString(),
                        snapshotDate = System.currentTimeMillis(),
                        totalAssets = totals.first,
                        totalLiabilities = totals.second,
                        netWorth = totals.first - totals.second,
                    ),
                )
            }
        }
    }

    private suspend fun backfill(db: EndgameDatabase) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val accounts = db.accountDao().allWithBalancesOnce()
        val deltas = db.netWorthSnapshotDao().dailyAccountDeltas()
            .groupBy { it.day }

        // Reach back to the earliest transaction (bounded), so imported
        // history shows a real trend — not just the days since first launch.
        val earliest = db.netWorthSnapshotDao().earliestTransactionMs()
        val daysToEarliest = earliest?.let {
            java.time.temporal.ChronoUnit.DAYS.between(
                java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate(),
                today,
            ).toInt() + 1
        } ?: 0
        val backfillDays = daysToEarliest.coerceIn(MIN_BACKFILL_DAYS, MAX_BACKFILL_DAYS)

        // Walk backward: end-of-day(D-1) = end-of-day(D) − deltas(D)
        val balances = accounts.associate { it.account to it.balance }.toMutableMap()
        val accountByIdKey = accounts.associateBy { it.account.id }
        val snapshots = mutableListOf<NetWorthSnapshot>()
        var day = today
        repeat(backfillDays) {
            val totals = totalsOf(balances)
            snapshots += NetWorthSnapshot(
                id = UUID.randomUUID().toString(),
                // Noon local: unambiguous, sorts cleanly, never crosses DST midnight
                snapshotDate = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                totalAssets = totals.first,
                totalLiabilities = totals.second,
                netWorth = totals.first - totals.second,
            )
            val key = "%04d-%02d-%02d".format(day.year, day.monthValue, day.dayOfMonth)
            deltas[key]?.forEach { delta ->
                accountByIdKey[delta.accountId]?.let { acct ->
                    balances[acct.account] = (balances[acct.account] ?: 0L) - delta.delta
                }
            }
            day = day.minusDays(1)
        }
        db.netWorthSnapshotDao().insertAll(snapshots)
    }

    /** (assets incl. investments, liabilities as a positive figure). */
    private fun totalsOf(balances: Map<Account, Long>): Pair<Long, Long> {
        var assets = 0L
        var liabilities = 0L
        balances.forEach { (account, balance) ->
            if (account.type == Account.TYPE_LIABILITY) liabilities += -balance
            else assets += balance
        }
        return assets to liabilities
    }
}
