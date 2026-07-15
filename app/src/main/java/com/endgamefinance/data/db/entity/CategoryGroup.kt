package com.endgamefinance.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Purely organizational container for categories (YNAB-style). Groups are NOT
 * assignable to transactions and NOT budgetable — only their categories are.
 * Added in DB v6 (owner-approved 2026-07-15), replacing the parent/child
 * category hierarchy. Every category must belong to a group.
 */
@Entity(tableName = "category_groups")
data class CategoryGroup(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    /** 'expense' or 'income' — its categories must match. */
    @ColumnInfo(name = "type") val type: String,
) {
    companion object {
        /** Sentinel groups that absorb categories left without one. */
        const val OTHER_EXPENSE_ID = "group_other_expense"
        const val OTHER_INCOME_ID = "group_other_income"
    }
}
