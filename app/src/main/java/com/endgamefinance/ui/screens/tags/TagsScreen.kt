package com.endgamefinance.ui.screens.tags

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.entity.Tag
import com.endgamefinance.ui.theme.Spacing
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TagsViewModel(private val db: EndgameDatabase) : ViewModel() {

    val tags: StateFlow<List<Tag>> =
        db.tagDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) {
        viewModelScope.launch {
            db.tagDao().insert(Tag(id = UUID.randomUUID().toString(), name = name.trim()))
        }
    }

    fun rename(tag: Tag, name: String) {
        viewModelScope.launch { db.tagDao().update(tag.copy(name = name.trim())) }
    }

    fun delete(tag: Tag) {
        viewModelScope.launch { db.tagDao().delete(tag) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { TagsViewModel(DatabaseProvider.get(context)) }
        }
    }
}

@Composable
fun TagsScreen(
    viewModel: TagsViewModel = viewModel(factory = TagsViewModel.factory(LocalContext.current)),
) {
    val tags by viewModel.tags.collectAsState()
    var dialogTag by remember { mutableStateOf<Tag?>(null) }
    var showNewDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "Tags",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
            items(tags, key = { it.id }) { tag ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dialogTag = tag }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("#${tag.name}", style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (tags.isEmpty()) {
                item {
                    Text(
                        text = "No tags yet. Tags cut across categories — e.g. #vacation2026 or #reimbursable.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = { showNewDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.md),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add tag")
        }
    }

    if (showNewDialog) {
        TagDialog(
            initialName = "",
            title = "New tag",
            onSave = { viewModel.create(it); showNewDialog = false },
            onDelete = null,
            onDismiss = { showNewDialog = false },
        )
    }
    dialogTag?.let { tag ->
        TagDialog(
            initialName = tag.name,
            title = "Edit tag",
            onSave = { viewModel.rename(tag, it); dialogTag = null },
            onDelete = { viewModel.delete(tag); dialogTag = null },
            onDismiss = { dialogTag = null },
        )
    }
}

@Composable
private fun TagDialog(
    initialName: String,
    title: String,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
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
