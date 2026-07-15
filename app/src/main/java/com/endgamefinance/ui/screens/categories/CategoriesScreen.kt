package com.endgamefinance.ui.screens.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endgamefinance.data.db.entity.Category
import com.endgamefinance.ui.components.IconCatalog
import com.endgamefinance.ui.components.IconPickerDialog
import com.endgamefinance.ui.theme.Spacing

@Composable
fun CategoriesScreen(
    onBack: (() -> Unit)? = null,
    viewModel: CategoriesViewModel =
        viewModel(factory = CategoriesViewModel.factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialogTarget by remember { mutableStateOf<CategoryDialogTarget?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    com.endgamefinance.ui.components.EndgameScaffold(
        title = "Categories",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { dialogTarget = CategoryDialogTarget.New }) {
                Icon(Icons.Filled.Add, contentDescription = "Add category")
            }
        },
    ) { innerPadding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            section("Expense", state.expense) { dialogTarget = CategoryDialogTarget.Edit(it) }
            section("Income", state.income) { dialogTarget = CategoryDialogTarget.Edit(it) }
            if (state.expense.isEmpty() && state.income.isEmpty()) {
                item {
                    com.endgamefinance.ui.components.EmptyState(
                        icon = Icons.Filled.Category,
                        title = "No categories yet",
                        body = "Categories organize your spending — create Food, then " +
                            "Groceries nested under it. Budgets and reports build on these.",
                        actionLabel = "Create a category",
                        onAction = { dialogTarget = CategoryDialogTarget.New },
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    }

    when (val target = dialogTarget) {
        is CategoryDialogTarget.New -> CategoryDialog(
            existing = null,
            parents = state.expense.map { it.category } + state.income.map { it.category },
            onSave = { name, type, parentId, icon ->
                viewModel.create(name, type, parentId, icon)
                dialogTarget = null
            },
            onDelete = null,
            onDismiss = { dialogTarget = null },
        )
        is CategoryDialogTarget.Edit -> CategoryDialog(
            existing = target.category,
            // A category that already has children can't be nested under another
            // parent — the app supports exactly one level of nesting.
            parents = if ((state.expense + state.income)
                    .any { it.category.id == target.category.id && it.children.isNotEmpty() }
            ) {
                emptyList()
            } else {
                (state.expense.map { it.category } + state.income.map { it.category })
                    .filter { it.id != target.category.id }
            },
            onSave = { name, _, parentId, icon ->
                viewModel.update(
                    target.category.copy(name = name, parentId = parentId, icon = icon),
                )
                dialogTarget = null
            },
            onDelete = {
                viewModel.delete(target.category)
                dialogTarget = null
            },
            onDismiss = { dialogTarget = null },
        )
        null -> Unit
    }
}

private sealed interface CategoryDialogTarget {
    data object New : CategoryDialogTarget
    data class Edit(val category: Category) : CategoryDialogTarget
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    nodes: List<CategoryNode>,
    onClick: (Category) -> Unit,
) {
    if (nodes.isEmpty()) return
    item(key = "header_$title") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                start = Spacing.md, end = Spacing.md, top = Spacing.lg, bottom = Spacing.xs,
            ),
        )
    }
    nodes.forEach { node ->
        item(key = node.category.id) {
            CategoryRow(category = node.category, indent = false, onClick = { onClick(node.category) })
        }
        items(node.children, key = { it.id }) { child ->
            CategoryRow(category = child, indent = true, onClick = { onClick(child) })
        }
    }
}

@Composable
private fun CategoryRow(category: Category, indent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = if (indent) Spacing.xl else Spacing.md,
                end = Spacing.md,
                top = Spacing.sm,
                bottom = Spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = IconCatalog.get(category.icon) ?: Icons.Filled.Category,
            contentDescription = null,
            // Expense icons take the accent (tertiary); income takes primary
            tint = when {
                category.icon == null -> MaterialTheme.colorScheme.outline
                category.type == Category.TYPE_INCOME -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.tertiary
            },
            modifier = Modifier.padding(end = Spacing.sm),
        )
        Text(
            text = category.name,
            style = if (indent) MaterialTheme.typography.bodyMedium
            else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDialog(
    existing: Category?,
    parents: List<Category>,
    onSave: (name: String, type: String, parentId: String?, icon: String?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: Category.TYPE_EXPENSE) }
    var parentId by remember { mutableStateOf(existing?.parentId) }
    var icon by remember { mutableStateOf(existing?.icon) }
    var showIconPicker by remember { mutableStateOf(false) }
    var parentMenuOpen by remember { mutableStateOf(false) }

    // Only same-type, top-level categories can be parents (one level of nesting).
    val eligibleParents = parents.filter { it.type == type && it.parentId == null }
    val parentName = eligibleParents.firstOrNull { it.id == parentId }?.name ?: "None (top level)"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    IconButton(onClick = { showIconPicker = true }) {
                        Icon(
                            imageVector = IconCatalog.get(icon) ?: Icons.Filled.Category,
                            contentDescription = "Pick icon",
                            tint = when {
                                icon == null -> MaterialTheme.colorScheme.outline
                                type == Category.TYPE_INCOME ->
                                    MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.tertiary
                            },
                        )
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                }
                if (existing == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(
                            selected = type == Category.TYPE_EXPENSE,
                            onClick = { type = Category.TYPE_EXPENSE; parentId = null },
                            label = { Text("Expense") },
                        )
                        FilterChip(
                            selected = type == Category.TYPE_INCOME,
                            onClick = { type = Category.TYPE_INCOME; parentId = null },
                            label = { Text("Income") },
                        )
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = parentMenuOpen,
                    onExpandedChange = { parentMenuOpen = it },
                ) {
                    OutlinedTextField(
                        value = parentName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Parent category") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentMenuOpen)
                        },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = parentMenuOpen,
                        onDismissRequest = { parentMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (top level)") },
                            onClick = { parentId = null; parentMenuOpen = false },
                        )
                        eligibleParents.forEach { parent ->
                            DropdownMenuItem(
                                text = { Text(parent.name) },
                                onClick = { parentId = parent.id; parentMenuOpen = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name.trim(), type, parentId, icon) },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (showIconPicker) {
        IconPickerDialog(
            currentKey = icon,
            onPick = { icon = it; showIconPicker = false },
            onDismiss = { showIconPicker = false },
        )
    }
}
