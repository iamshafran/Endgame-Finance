package com.endgamefinance.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.endgamefinance.ui.theme.Spacing

/** Searchable Material icon grid; returns the catalog key, or null for "no icon". */
@Composable
fun IconPickerDialog(
    currentKey: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = IconCatalog.search(query)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick an icon") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search icons") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(56.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .padding(top = Spacing.sm),
                ) {
                    items(results, key = { it.first }) { (key, vector) ->
                        IconButton(onClick = { onPick(key) }) {
                            Icon(
                                imageVector = vector,
                                contentDescription = key,
                                tint = if (key == currentKey) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                if (results.isEmpty()) {
                    Text(
                        "No icons match \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(null) }) { Text("No icon") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
