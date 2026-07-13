package com.endgamefinance.data.db.model

import com.endgamefinance.data.db.entity.Category

/** Category with a display name that includes its parent ("Food › Groceries"). */
data class CategoryChoice(val id: String, val displayName: String, val type: String)

fun categoryChoices(all: List<Category>): List<CategoryChoice> {
    val byId = all.associateBy { it.id }
    return all.map { c ->
        val display = c.parentId?.let { pid ->
            byId[pid]?.let { parent -> "${parent.name} › ${c.name}" }
        } ?: c.name
        CategoryChoice(c.id, display, c.type)
    }.sortedBy { it.displayName }
}
