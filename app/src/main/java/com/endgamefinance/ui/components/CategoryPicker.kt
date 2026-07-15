package com.endgamefinance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.endgamefinance.data.db.entity.Category
import com.endgamefinance.ui.theme.Spacing

/** A category as the grid picker renders it. */
data class CategoryPickItem(
    val id: String,
    /** Short name shown under the icon. */
    val name: String,
    /** "Group › Category" form, used in the collapsed field. */
    val displayName: String,
    val type: String,
    val icon: String?,
    val groupId: String? = null,
    val groupName: String? = null,
)

/** Builds picker items from raw categories + their groups. */
fun categoryPickItems(
    all: List<Category>,
    groups: List<com.endgamefinance.data.db.entity.CategoryGroup>,
): List<CategoryPickItem> {
    val groupsById = groups.associateBy { it.id }
    return all.map { c ->
        val group = c.groupId?.let { groupsById[it] }
        CategoryPickItem(
            id = c.id,
            name = c.name,
            // A migrated parent keeps its name inside its own group — skip "Food › Food"
            displayName = if (group != null && group.name != c.name) {
                "${group.name} › ${c.name}"
            } else {
                c.name
            },
            type = c.type,
            icon = c.icon,
            groupId = group?.id,
            groupName = group?.name,
        )
    }.sortedBy { it.displayName.lowercase() }
}

/**
 * Bottom-sheet category picker: a grid of icon tiles grouped by type
 * (Expense / Income). Replaces the old flat dropdown, which got confusing
 * once categories multiplied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    title: String,
    items: List<CategoryPickItem>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    nullLabel: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(84.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .padding(horizontal = Spacing.md),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = Spacing.xl,
            ),
        ) {
            if (nullLabel != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        nullLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selectedId == null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(null) }
                            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                    )
                }
            }
            val groups = listOf(
                "Expense" to items.filter { it.type == Category.TYPE_EXPENSE },
                "Income" to items.filter { it.type == Category.TYPE_INCOME },
            ).filter { it.second.isNotEmpty() }
            val showHeaders = groups.size > 1
            groups.forEach { (groupTitle, groupItems) ->
                if (showHeaders) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "header_$groupTitle") {
                        Text(
                            groupTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                top = Spacing.sm, bottom = Spacing.xs, start = Spacing.xs,
                            ),
                        )
                    }
                }
                // One subheaded block per category group. Groupless strays
                // (shouldn't exist — groups are required) render first, bare.
                val loose = groupItems.filter { it.groupId == null }
                items(loose, key = { it.id }) { item ->
                    CategoryTile(
                        item = item,
                        selected = item.id == selectedId,
                        onClick = { onPick(item.id) },
                    )
                }
                groupItems.filter { it.groupId != null }
                    .groupBy { it.groupId!! to it.groupName.orEmpty() }
                    .entries
                    .sortedBy { it.key.second.lowercase() }
                    .forEach { (groupKey, members) ->
                        // Key scoped by section: a group holding BOTH types (seen
                        // in pre-v6 Bluecoins imports) appears under Expense AND
                        // Income, and bare group ids would collide → crash.
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            key = "${groupTitle}_group_${groupKey.first}",
                        ) {
                            Text(
                                groupKey.second,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    top = Spacing.sm, bottom = Spacing.xs, start = Spacing.xs,
                                ),
                            )
                        }
                        items(
                            members.sortedBy { it.name.lowercase() },
                            key = { it.id },
                        ) { item ->
                            CategoryTile(
                                item = item,
                                selected = item.id == selectedId,
                                onClick = { onPick(item.id) },
                            )
                        }
                    }
            }
            if (groups.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No categories yet — create them under More → Categories.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    item: CategoryPickItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    androidx.compose.ui.graphics.RectangleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = IconCatalog.get(item.icon) ?: Icons.Filled.Category,
                contentDescription = null,
                // Expense icons take the accent (tertiary); income takes primary
                tint = when {
                    selected -> MaterialTheme.colorScheme.onPrimaryContainer
                    item.type == Category.TYPE_INCOME -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.tertiary
                },
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/**
 * Drop-in replacement for a category DropdownField: renders as the same
 * outlined field, but opens the grid bottom sheet instead of a menu.
 */
@Composable
fun CategoryPickerField(
    label: String,
    items: List<CategoryPickItem>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    nullLabel: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val selectedLabel = items.firstOrNull { it.id == selectedId }?.displayName
        ?: nullLabel ?: "Select…"

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            },
            // Disabled field + overlay = reliably clickable; restyle the
            // disabled colors so it reads as an ordinary enabled field.
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { open = true },
        )
    }

    if (open) {
        CategoryPickerSheet(
            title = label,
            items = items,
            selectedId = selectedId,
            onPick = { picked ->
                onSelect(picked)
                open = false
            },
            onDismiss = { open = false },
            nullLabel = nullLabel,
        )
    }
}
