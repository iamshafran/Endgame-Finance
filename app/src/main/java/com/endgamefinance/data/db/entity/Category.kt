package com.endgamefinance.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("parent_id"), Index("group_id")],
)
data class Category(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    /**
     * DEPRECATED as of DB v6: the parent/child hierarchy was replaced by
     * category groups. Always NULL after the v6 migration; the column stays
     * per CLAUDE.md's no-removal rule (and for pre-v6 backup compatibility).
     */
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    /** 'expense' or 'income' */
    @ColumnInfo(name = "type") val type: String,
    /** Key into IconCatalog; NULL = no icon chosen. Added in DB v2. */
    @ColumnInfo(name = "icon") val icon: String? = null,
    /**
     * Owning [CategoryGroup]. Nullable at the DB level for migration safety,
     * but the app requires every category to have a group (v6, owner-approved
     * 2026-07-15); strays are folded into the sentinel "Other" groups.
     */
    @ColumnInfo(name = "group_id") val groupId: String? = null,
) {
    companion object {
        const val TYPE_EXPENSE = "expense"
        const val TYPE_INCOME = "income"
    }
}
