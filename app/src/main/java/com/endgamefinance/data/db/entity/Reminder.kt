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
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["to_account_id"],
        ),
    ],
    indices = [Index("category_id"), Index("account_id"), Index("to_account_id")],
)
data class Reminder(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(name = "account_id") val accountId: String,
    /** Destination for transfer/repayment reminders — posts as a transfer. Added in DB v5. */
    @ColumnInfo(name = "to_account_id") val toAccountId: String? = null,
    /** NULL if variable. Cents. */
    @ColumnInfo(name = "amount") val amount: Long? = null,
    /** 'daily','weekly','monthly','yearly','once' */
    @ColumnInfo(name = "frequency") val frequency: String,
    /** Every N units of [frequency] (2 + weekly = biweekly). Added in DB v4. */
    @ColumnInfo(name = "frequency_interval", defaultValue = "1") val frequencyInterval: Int = 1,
    /** Intended day-of-month for monthly/yearly cadences (prevents month-end drift). Added in DB v4. */
    @ColumnInfo(name = "anchor_day") val anchorDay: Int? = null,
    @ColumnInfo(name = "next_due_date") val nextDueDate: Long,
    @ColumnInfo(name = "is_auto_post", defaultValue = "0") val isAutoPost: Boolean = false,
    /** Created via recurring-transaction detection. */
    @ColumnInfo(name = "is_auto_detected", defaultValue = "0") val isAutoDetected: Boolean = false,
)
