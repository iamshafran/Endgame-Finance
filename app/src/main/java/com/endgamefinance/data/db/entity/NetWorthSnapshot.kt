package com.endgamefinance.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Periodic snapshot, not derived live. All amounts in cents. */
@Entity(tableName = "net_worth_snapshots")
data class NetWorthSnapshot(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "snapshot_date") val snapshotDate: Long,
    @ColumnInfo(name = "total_assets") val totalAssets: Long,
    @ColumnInfo(name = "total_liabilities") val totalLiabilities: Long,
    @ColumnInfo(name = "net_worth") val netWorth: Long,
)
