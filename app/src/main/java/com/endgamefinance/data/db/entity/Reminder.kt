package com.endgamefinance.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
        ),
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
        ),
    ],
    indices = [Index("category_id"), Index("account_id")],
)
data class Reminder(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(name = "account_id") val accountId: String,
    /** NULL if variable. Cents. */
    @ColumnInfo(name = "amount") val amount: Long? = null,
    /** 'daily','weekly','monthly','yearly','once' */
    @ColumnInfo(name = "frequency") val frequency: String,
    @ColumnInfo(name = "next_due_date") val nextDueDate: Long,
    @ColumnInfo(name = "is_auto_post", defaultValue = "0") val isAutoPost: Boolean = false,
    /** Created via recurring-transaction detection. */
    @ColumnInfo(name = "is_auto_detected", defaultValue = "0") val isAutoDetected: Boolean = false,
)
