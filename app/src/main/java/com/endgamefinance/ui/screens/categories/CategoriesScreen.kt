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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import com.endgamefinance.data.db.entity.CategoryGroup
import com.endgamefinance.ui.components.DropdownField
import com.endgamefinance.ui.components.IconCatalog
import com.endgamefinance.ui.components.IconPickerDialog
import com.endgamefinance.ui.theme.Spacing

/** Sentinel id for the "New group…" choice in the category dialog. */
private const val NEW_GROUP_OPTION = "__new_group__"

@Composable
fun CategoriesScreen(
    onBack: (() -> Unit)? = null,
    viewModel: CategoriesViewModel =
        viewModel(factory = CategoriesViewModel.factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialogTarget by remember { mutableStateOf<DialogTarget?>(null) }

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
            FloatingActionButton(
                onClick = { dialogTarget = DialogTarget.NewCategory(null, null) },
            ) {
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
            section(
                title = "Expense",
                type = Category.TYPE_EXPENSE,
                nodes = state.expense,
                onEditCategory = { dialogTarget = DialogTarget.EditCategory(it) },
                onAddCategory = { group ->
                    dialogTarget = DialogTarget.NewCategory(group.id, group.type)
                },
                onEditGroup = { dialogTarget = DialogTarget.EditGroup(it) },
                onNewGroup = { dialogTarget = DialogTarget.NewGroup(Category.TYPE_EXPENSE) },
            )
            section(
                title = "Income",
                type = Category.TYPE_INCOME,
                nodes = state.income,
                onEditCategory = { dialogTarget = DialogTarget.EditCategory(it) },
                onAddCategory = { group ->
                    dialogTarget = DialogTarget.NewCategory(group.id, group.type)
                },
                onEditGroup = { dialogTarget = DialogTarget.EditGroup(it) },
                onNewGroup = { dialogTarget = DialogTarget.NewGroup(Category.TYPE_INCOME) },
            )
            if (state.expense.isEmpty() && state.income.isEmpty()) {
                item {
                    com.endgamefinance.ui.components.EmptyState(
                        icon = Icons.Filled.Category,
                        title = "No categories yet",
                        body = "Categories live inside groups — create a group like Food, " +
                            "then categories like Groceries inside it. Budgets and " +
                            "reports build on these.",
                        actionLabel = "Create a category",
                        onAction = { dialogTarget = DialogTarget.NewCategory(null, null) },
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
        is DialogTarget.NewCategory -> CategoryDialog(
            existing = null,
            presetGroupId = target.groupId,
            presetType = target.type,
            groups = state.groups,
            onSave = { name, type, groupId, icon ->
                viewModel.create(name, type, groupId, icon)
                dialogTarget = null
            },
            onSaveWithNewGroup = { name, type, groupName, icon ->
                viewModel.createWithNewGroup(name, type, groupName, icon)
                dialogTarget = null
            },
            onDelete = null,
            onDismiss = { dialogTarget = null },
        )
        is DialogTarget.EditCategory -> CategoryDialog(
            existing = target.category,
            presetGroupId = target.category.groupId,
            presetType = target.category.type,
            groups = state.groups,
            onSave = { name, _, groupId, icon ->
                viewModel.update(
                    target.category.copy(name = name, groupId = groupId, icon = icon),
                )
                dialogTarget = null
            },
            onSaveWithNewGroup = { name, _, groupName, icon ->
                viewModel.updateWithNewGroup(target.category, name, groupName, icon)
                dialogTarget = null
            },
            onDelete = {
                viewModel.delete(target.category)
                dialogTarget = null
            },
            onDismiss = { dialogTarget = null },
        )
        is DialogTarget.NewGroup -> GroupDialog(
            existing = null,
            onSave = { name -> viewModel.createGroup(name, target.type); dialogTarget = null },
            onDelete = null,
            onDismiss = { dialogTarget = null },
        )
        is DialogTarget.EditGroup -> GroupDialog(
            existing = target.group,
            onSave = { name -> viewModel.renameGroup(target.group, name); dialogTarget = null },
            onDelete = { viewModel.deleteGroup(target.group); dialogTarget = null },
            onDismiss = { dialogTarget = null },
        )
        null -> Unit
    }
}

private sealed interface DialogTarget {
    data class NewCategory(val groupId: String?, val type: String?) : DialogTarget
    data class EditCategory(val category: Category) : DialogTarget
    data class NewGroup(val type: String) : DialogTarget
    data class EditGroup(val group: CategoryGroup) : DialogTarget
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    type: String,
    nodes: List<GroupNode>,
    onEditCategory: (Category) -> Unit,
    onAddCategory: (CategoryGroup) -> Unit,
    onEditGroup: (CategoryGroup) -> Unit,
    onNewGroup: () -> Unit,
) {
    if (nodes.isEmpty()) return
    item(key = "header_$title") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.lg),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = onNewGroup) { Text("New group") }
        }
    }
    nodes.forEach { node ->
        item(key = "group_${node.group.id}") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditGroup(node.group) }
                    .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm),
            ) {
                Text(
                    text = node.group.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit group",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Spacing.sm),
                )
                IconButton(onClick = { onAddCategory(node.group) }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add category to ${node.group.name}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (node.categories.isEmpty()) {
            item(key = "empty_${node.group.id}") {
                Text(
                    "No categories yet — tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs),
                )
            }
        }
        items(node.categories, key = { it.id }) { category ->
            CategoryRow(category = category, onClick = { onEditCategory(category) })
        }
    }
}

@Composable
private fun CategoryRow(category: Category, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = Spacing.xl,
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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CategoryDialog(
    existing: Category?,
    presetGroupId: String?,
    presetType: String?,
    groups: List<CategoryGroup>,
    onSave: (name: String, type: String, groupId: String, icon: String?) -> Unit,
    onSaveWithNewGroup: (name: String, type: String, groupName: String, icon: String?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(presetType ?: existing?.type ?: Category.TYPE_EXPENSE) }
    var groupId by remember { mutableStateOf(presetGroupId) }
    var newGroupName by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(existing?.icon) }
    var showIconPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val eligibleGroups = groups.filter { it.type == type }

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
                            onClick = { type = Category.TYPE_EXPENSE; groupId = null },
                            label = { Text("Expense") },
                        )
                        FilterChip(
                            selected = type == Category.TYPE_INCOME,
                            onClick = { type = Category.TYPE_INCOME; groupId = null },
                            label = { Text("Income") },
                        )
                    }
                }
                DropdownField(
                    label = "Group",
                    options = eligibleGroups.map { it.id as String? to it.name } +
                        listOf<Pair<String?, String>>(NEW_GROUP_OPTION to "New group…"),
                    selectedId = groupId,
                    onSelect = { groupId = it },
                    nullLabel = "Pick a group",
                )
                if (groupId == NEW_GROUP_OPTION) {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("New group name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    when {
                        trimmed.isEmpty() -> error = "Name is required"
                        groupId == NEW_GROUP_OPTION -> {
                            if (newGroupName.isBlank()) {
                                error = "Give the new group a name"
                            } else {
                                onSaveWithNewGroup(trimmed, type, newGroupName, icon)
                            }
                        }
                        groupId == null -> error = "Every category needs a group"
                        else -> onSave(trimmed, type, groupId!!, icon)
                    }
                },
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

@Composable
private fun GroupDialog(
    existing: CategoryGroup?,
    onSave: (name: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New group" else "Edit group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Groups organize categories — transactions and budgets always " +
                        "attach to the categories inside.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onSave(name) }) { Text("Save") }
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
}
