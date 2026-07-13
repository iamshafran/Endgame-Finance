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
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A parent category with its children, for indented display. */
data class CategoryNode(
    val category: Category,
    val children: List<Category>,
)

data class CategoriesUiState(
    val expense: List<CategoryNode> = emptyList(),
    val income: List<CategoryNode> = emptyList(),
)

class CategoriesViewModel(private val db: EndgameDatabase) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> =
        db.categoryDao().observeAll()
            .map { all ->
                CategoriesUiState(
                    expense = buildNodes(all.filter { it.type == Category.TYPE_EXPENSE }),
                    income = buildNodes(all.filter { it.type == Category.TYPE_INCOME }),
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    private fun buildNodes(categories: List<Category>): List<CategoryNode> {
        val byParent = categories.filter { it.parentId != null }.groupBy { it.parentId }
        return categories
            .filter { it.parentId == null }
            .map { parent -> CategoryNode(parent, byParent[parent.id].orEmpty()) }
    }

    fun create(name: String, type: String, parentId: String?, icon: String?) {
        viewModelScope.launch {
            db.categoryDao().insert(
                Category(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    parentId = parentId,
                    type = type,
                    icon = icon,
                ),
            )
        }
    }

    fun update(category: Category) {
        viewModelScope.launch { db.categoryDao().update(category) }
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

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { CategoriesViewModel(DatabaseProvider.get(context)) }
        }
    }
}
