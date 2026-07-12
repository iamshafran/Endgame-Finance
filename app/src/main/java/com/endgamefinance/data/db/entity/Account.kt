package com.endgamefinance.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    /** 'asset', 'liability', 'investment' */
    @ColumnInfo(name = "type") val type: String,
    /** NULL unless type='liability' and it's a credit line. Cents. */
    @ColumnInfo(name = "credit_limit") val creditLimit: Long? = null,
    @ColumnInfo(name = "currency", defaultValue = "USD") val currency: String = "USD",
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Boolean = true,
)
