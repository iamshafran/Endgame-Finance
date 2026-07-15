package com.endgamefinance.data.db.model

import com.endgamefinance.data.db.entity.Category
import com.endgamefinance.data.db.entity.CategoryGroup

/** Category with a display name that includes its group ("Food › Groceries"). */
data class CategoryChoice(val id: String, val displayName: String, val type: String)

fun categoryChoices(all: List<Category>, groups: List<CategoryGroup>): List<CategoryChoice> {
    val groupsById = groups.associateBy { it.id }
    return all.map { c ->
        val group = c.groupId?.let { groupsById[it] }
        // A migrated parent keeps its name inside its own group — don't render "Food › Food"
        val display = if (group != null && group.name != c.name) {
            "${group.name} › ${c.name}"
        } else {
            c.name
        }
        CategoryChoice(c.id, display, c.type)
    }.sortedBy { it.displayName }
}
