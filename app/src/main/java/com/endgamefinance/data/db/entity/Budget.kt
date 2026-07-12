package com.endgamefinance.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
        ),
    ],
    indices = [Index("category_id")],
)
data class Budget(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    /** 'YYYY-MM' */
    @ColumnInfo(name = "month") val month: String,
    /** Cents. */
    @ColumnInfo(name = "allocated_amount") val allocatedAmount: Long,
    /** 'reset' or 'carry' */
    @ColumnInfo(name = "rollover_mode", defaultValue = "reset") val rolloverMode: String = "reset",
)
