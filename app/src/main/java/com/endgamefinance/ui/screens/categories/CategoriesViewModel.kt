package com.endgamefinance.ui.screens.categories

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.entity.Category
import com.endgamefinance.data.db.entity.CategoryGroup
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A category group with its (flat) categories, for sectioned display. */
data class GroupNode(
    val group: CategoryGroup,
    val categories: List<Category>,
)

data class CategoriesUiState(
    val expense: List<GroupNode> = emptyList(),
    val income: List<GroupNode> = emptyList(),
    val groups: List<CategoryGroup> = emptyList(),
)

class CategoriesViewModel(private val db: EndgameDatabase) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> =
        combine(
            db.categoryDao().observeAll(),
            db.categoryGroupDao().observeAll(),
        ) { categories, groups ->
            fun nodes(type: String): List<GroupNode> =
                groups.filter { it.type == type }
                    .sortedBy { it.name.lowercase() }
                    .map { group ->
                        GroupNode(
                            group = group,
                            categories = categories
                                .filter { it.groupId == group.id }
                                .sortedBy { it.name.lowercase() },
                        )
                    }
            CategoriesUiState(
                expense = nodes(Category.TYPE_EXPENSE),
                income = nodes(Category.TYPE_INCOME),
                groups = groups,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    fun create(name: String, type: String, groupId: String, icon: String?) {
        viewModelScope.launch {
            db.categoryDao().insert(
                Category(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    type = type,
                    icon = icon,
                    groupId = groupId,
                ),
            )
        }
    }

    /** Creates the group and the category together (dialog's "New group…" path). */
    fun createWithNewGroup(name: String, type: String, groupName: String, icon: String?) {
        viewModelScope.launch {
            val group = CategoryGroup(
                id = UUID.randomUUID().toString(),
                name = groupName.trim(),
                type = type,
            )
            db.categoryGroupDao().insert(group)
            db.categoryDao().insert(
                Category(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    type = type,
                    icon = icon,
                    groupId = group.id,
                ),
            )
        }
    }

    fun update(category: Category) {
        viewModelScope.launch { db.categoryDao().update(category) }
    }

    /** Moves an existing category into a freshly created group. */
    fun updateWithNewGroup(category: Category, name: String, groupName: String, icon: String?) {
        viewModelScope.launch {
            val group = CategoryGroup(
                id = UUID.randomUUID().toString(),
                name = groupName.trim(),
                type = category.type,
            )
            db.categoryGroupDao().insert(group)
            db.categoryDao().update(
                category.copy(name = name.trim(), groupId = group.id, icon = icon),
            )
        }
    }

    fun delete(category: Category) {
        viewModelScope.launch {
            try {
                db.categoryDao().delete(category)
            } catch (e: SQLiteConstraintException) {
                _message.value =
                    "\"${category.name}\" is in use by transactions, budgets, or reminders and can't be deleted."
            }
        }
    }

    fun createGroup(name: String, type: String) {
        viewModelScope.launch {
            db.categoryGroupDao().insert(
                CategoryGroup(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    type = type,
                ),
            )
        }
    }

    fun renameGroup(group: CategoryGroup, newName: String) {
        viewModelScope.launch {
            db.categoryGroupDao().update(group.copy(name = newName.trim()))
        }
    }

    /** Groups must be empty before deletion — categories always need a group. */
    fun deleteGroup(group: CategoryGroup) {
        viewModelScope.launch {
            if (db.categoryGroupDao().categoryCount(group.id) > 0) {
                _message.value =
                    "\"${group.name}\" still contains categories. Move or delete them first."
            } else {
                db.categoryGroupDao().delete(group)
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { CategoriesViewModel(DatabaseProvider.get(context)) }
        }
    }
}
