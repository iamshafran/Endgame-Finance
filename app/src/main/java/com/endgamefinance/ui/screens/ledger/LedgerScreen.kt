package com.endgamefinance.ui.screens.ledger

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.model.TransactionListItem
import com.endgamefinance.ui.components.DropdownField
import com.endgamefinance.ui.components.IconCatalog
import com.endgamefinance.ui.theme.LocalMoneyColors
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.ui.theme.tabular
import com.endgamefinance.util.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LedgerFilters(
    val accountId: String? = null,
    val payeeQuery: String = "",
    val categoryId: String? = null,
    val minCents: Long? = null,
    val maxCents: Long? = null,
    val startMs: Long? = null,
    val endMs: Long? = null,
) {
    val isActive: Boolean
        get() = accountId != null || payeeQuery.isNotBlank() ||
            categoryId != null || minCents != null || maxCents != null ||
            startMs != null || endMs != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(private val db: EndgameDatabase) : ViewModel() {

    private val repo = com.endgamefinance.data.repo.LedgerRepository(db)

    /** Swipe-right action: audited cleared toggle. */
    fun toggleCleared(item: TransactionListItem) {
        viewModelScope.launch { repo.setCleared(item.id, !item.isCleared) }
    }

    /** Swipe-left action, after the confirm dialog. */
    fun delete(transactionId: String) {
        viewModelScope.launch { repo.deleteTransaction(transactionId) }
    }

    // ---------------- multi-select ----------------

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    val tags = db.tagDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleSelected(id: String) {
        _selected.value = _selected.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
    }

    fun startSelection(id: String) { _selected.value = _selected.value + id }
    fun clearSelection() { _selected.value = emptySet() }
    fun selectAll() { _selected.value = transactions.value.map { it.id }.toSet() }

    private fun currentIds(): List<String> = _selected.value.toList()

    fun deleteSelected() {
        val ids = currentIds()
        viewModelScope.launch {
            repo.deleteTransactions(ids)
            _message.value = "Deleted ${ids.size} transaction${if (ids.size == 1) "" else "s"}."
            clearSelection()
        }
    }

    fun setClearedSelected(cleared: Boolean) {
        val ids = currentIds()
        viewModelScope.launch {
            repo.setClearedBulk(ids, cleared)
            _message.value = "Marked ${ids.size} ${if (cleared) "cleared" else "uncleared"}."
            clearSelection()
        }
    }

    fun setSharedSelected(shared: Boolean) {
        val ids = currentIds()
        viewModelScope.launch {
            repo.setSharedBulk(ids, shared)
            _message.value = "${ids.size} marked ${if (shared) "shared" else "not shared"}."
            clearSelection()
        }
    }

    fun setCategorySelected(categoryId: String?) {
        val ids = currentIds()
        viewModelScope.launch {
            val r = repo.setCategoryBulk(ids, categoryId)
            _message.value = buildString {
                append("Re-categorized ${r.applied}.")
                if (r.skipped > 0) append(" ${r.skipped} skipped (transfers or split rows).")
            }
            clearSelection()
        }
    }

    fun addTagSelected(tagId: String) {
        val ids = currentIds()
        viewModelScope.launch {
            repo.addTagBulk(ids, tagId)
            _message.value = "Tag added to ${ids.size}."
            clearSelection()
        }
    }

    fun removeTagSelected(tagId: String) {
        val ids = currentIds()
        viewModelScope.launch {
            repo.removeTagBulk(ids, tagId)
            _message.value = "Tag removed from ${ids.size}."
            clearSelection()
        }
    }

    private val _filters = MutableStateFlow(LedgerFilters())
    val filters: StateFlow<LedgerFilters> = _filters.asStateFlow()

    val transactions: StateFlow<List<TransactionListItem>> =
        _filters.flatMapLatest { f ->
            db.transactionDao().observeFiltered(
                accountId = f.accountId,
                payeeQuery = f.payeeQuery.trim(),
                categoryId = f.categoryId,
                minCents = f.minCents,
                maxCents = f.maxCents,
                startMs = f.startMs,
                endMs = f.endMs,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts = db.accountDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<com.endgamefinance.ui.components.CategoryPickItem>> =
        kotlinx.coroutines.flow.combine(
            db.categoryDao().observeAll(),
            db.categoryGroupDao().observeAll(),
        ) { cats, groups -> com.endgamefinance.ui.components.categoryPickItems(cats, groups) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilters(filters: LedgerFilters) {
        _filters.value = filters
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { LedgerViewModel(DatabaseProvider.get(context)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LedgerScreen(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    showFiltersInitially: Boolean = false,
    title: String = "Ledger",
    onBack: (() -> Unit)? = null,
    viewModel: LedgerViewModel = viewModel(factory = LedgerViewModel.factory(LocalContext.current)),
) {
    val transactions by viewModel.transactions.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val message by viewModel.message.collectAsState()
    var showFilters by remember { mutableStateOf(showFiltersInitially) }
    var minText by remember { mutableStateOf("") }
    var maxText by remember { mutableStateOf("") }
    var datePickTarget by remember { mutableStateOf<String?>(null) } // "start" | "end"
    var pendingDelete by remember { mutableStateOf<TransactionListItem?>(null) }

    val selectionMode = selected.isNotEmpty()
    var showBulkDelete by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAddTag by remember { mutableStateOf(false) }
    var showRemoveTag by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }

    // System back exits selection mode instead of leaving the ledger.
    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }

    // The filter panel is the first list item; snap to the top when it opens so
    // it's actually visible even if the ledger was scrolled down.
    val listState = rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(showFilters) {
        if (showFilters) listState.animateScrollToItem(0)
    }

    // Transient confirmations (e.g. "Deleted 3", category-skip note).
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    com.endgamefinance.ui.components.EndgameScaffold(
        title = if (selectionMode) "${selected.size} selected" else title,
        onBack = if (selectionMode) ({ viewModel.clearSelection() }) else onBack,
        actions = {
            if (selectionMode) {
                IconButton(onClick = { showCategoryPicker = true }) {
                    Icon(Icons.Filled.Category, contentDescription = "Set category")
                }
                IconButton(onClick = { viewModel.setClearedSelected(true) }) {
                    Icon(Icons.Filled.Check, contentDescription = "Mark cleared")
                }
                IconButton(onClick = { showBulkDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                }
                IconButton(onClick = { overflowOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        leadingIcon = { Icon(Icons.Filled.DoneAll, null) },
                        onClick = { viewModel.selectAll(); overflowOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Mark uncleared") },
                        onClick = { viewModel.setClearedSelected(false); overflowOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Mark shared") },
                        onClick = { viewModel.setSharedSelected(true); overflowOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Mark not shared") },
                        onClick = { viewModel.setSharedSelected(false); overflowOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Add tag…") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                        onClick = { showAddTag = true; overflowOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove tag…") },
                        onClick = { showRemoveTag = true; overflowOpen = false },
                    )
                }
            } else {
                IconButton(onClick = { showFilters = !showFilters }) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Toggle filters",
                        tint = if (filters.isActive) MaterialTheme.colorScheme.tertiary
                        else LocalContentColor.current,
                    )
                }
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onClick = onAddTransaction) {
                    Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // Last row scrolls clear of the FAB
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = Spacing.fabClearance,
            ),
        ) {
            if (showFilters) {
                item(key = "filters") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md)
                            // Clear separation between the filter form and the list
                            .padding(top = Spacing.sm, bottom = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        OutlinedTextField(
                            value = filters.payeeQuery,
                            onValueChange = { viewModel.setFilters(filters.copy(payeeQuery = it)) },
                            label = { Text("Payee contains") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownField(
                            label = "Account",
                            options = listOf<Pair<String?, String>>(null to "All accounts") +
                                accounts.map { it.id to it.name },
                            selectedId = filters.accountId,
                            onSelect = { viewModel.setFilters(filters.copy(accountId = it)) },
                            nullLabel = "All accounts",
                        )
                        com.endgamefinance.ui.components.CategoryPickerField(
                            label = "Category",
                            items = categories,
                            selectedId = filters.categoryId,
                            onSelect = { viewModel.setFilters(filters.copy(categoryId = it)) },
                            nullLabel = "All categories",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedTextField(
                                value = minText,
                                onValueChange = {
                                    minText = it
                                    viewModel.setFilters(filters.copy(minCents = Money.parse(it)))
                                },
                                label = { Text("Min amount") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = maxText,
                                onValueChange = {
                                    maxText = it
                                    viewModel.setFilters(filters.copy(maxCents = Money.parse(it)))
                                },
                                label = { Text("Max amount") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedTextField(
                                value = filters.startMs?.let {
                                    java.text.DateFormat.getDateInstance(
                                        java.text.DateFormat.SHORT,
                                    ).format(java.util.Date(it))
                                } ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("From date") },
                                placeholder = { Text("Any") },
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { datePickTarget = "start" },
                                trailingIcon = {
                                    TextButton(onClick = { datePickTarget = "start" }) {
                                        Text("Set")
                                    }
                                },
                            )
                            OutlinedTextField(
                                value = filters.endMs?.let {
                                    java.text.DateFormat.getDateInstance(
                                        java.text.DateFormat.SHORT,
                                    ).format(java.util.Date(it - 1))
                                } ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("To date") },
                                placeholder = { Text("Any") },
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { datePickTarget = "end" },
                                trailingIcon = {
                                    TextButton(onClick = { datePickTarget = "end" }) {
                                        Text("Set")
                                    }
                                },
                            )
                        }
                        if (filters.isActive) {
                            TextButton(
                                onClick = {
                                    minText = ""
                                    maxText = ""
                                    viewModel.setFilters(LedgerFilters())
                                },
                            ) { Text("Clear filters") }
                        }
                    }
                }
            }
            val zone = ZoneId.systemDefault()
            val byDay = transactions.groupBy {
                Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
            }
            byDay.forEach { (day, dayItems) ->
                stickyHeader(key = "day_$day") {
                    DayHeader(day = day, items = dayItems)
                }
                items(dayItems, key = { it.id }) { item ->
                    if (selectionMode) {
                        SelectableTransactionRow(
                            item = item,
                            selected = item.id in selected,
                            onToggle = { viewModel.toggleSelected(item.id) },
                        )
                    } else {
                        SwipeableTransactionRow(
                            item = item,
                            onClick = { onOpenTransaction(item.id) },
                            onLongClick = { viewModel.startSelection(item.id) },
                            onToggleCleared = { viewModel.toggleCleared(item) },
                            onRequestDelete = { pendingDelete = item },
                        )
                    }
                }
            }
            if (transactions.isEmpty()) {
                item(key = "empty") {
                    if (filters.isActive) {
                        com.endgamefinance.ui.components.EmptyState(
                            icon = Icons.Filled.Search,
                            title = "Nothing matches",
                            body = "No transactions fit these filters. Loosen one, or clear them all.",
                        )
                    } else {
                        com.endgamefinance.ui.components.EmptyState(
                            icon = Icons.Filled.Add,
                            title = "Your ledger is empty",
                            body = "Every expense, income, and transfer you record lands here, " +
                                "grouped by day with running balances.",
                            actionLabel = "Add your first transaction",
                            onAction = onAddTransaction,
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${item.payee}\"?") },
            text = {
                Text(
                    "This permanently removes the transaction and its history, " +
                        "and re-derives account balances. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(item.id)
                        pendingDelete = null
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    datePickTarget?.let { target ->
        val initial = if (target == "start") filters.startMs ?: System.currentTimeMillis()
        else (filters.endMs?.minus(1)) ?: System.currentTimeMillis()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { datePickTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { utc ->
                            val date = java.time.Instant.ofEpochMilli(utc)
                                .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                            val zone = java.time.ZoneId.systemDefault()
                            if (target == "start") {
                                viewModel.setFilters(
                                    filters.copy(
                                        startMs = date.atStartOfDay(zone)
                                            .toInstant().toEpochMilli(),
                                    ),
                                )
                            } else {
                                viewModel.setFilters(
                                    filters.copy(
                                        endMs = date.plusDays(1).atStartOfDay(zone)
                                            .toInstant().toEpochMilli(),
                                    ),
                                )
                            }
                        }
                        datePickTarget = null
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { datePickTarget = null }) { Text("Cancel") }
            },
        ) { DatePicker(state = state) }
    }

    if (showBulkDelete) {
        AlertDialog(
            onDismissRequest = { showBulkDelete = false },
            title = { Text("Delete ${selected.size} transactions?") },
            text = {
                Text(
                    "This permanently removes the selected transactions and their " +
                        "history, and re-derives account balances. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    showBulkDelete = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDelete = false }) { Text("Cancel") }
            },
        )
    }

    if (showCategoryPicker) {
        com.endgamefinance.ui.components.CategoryPickerSheet(
            title = "Set category for ${selected.size}",
            items = categories,
            selectedId = null,
            onPick = { id ->
                viewModel.setCategorySelected(id)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
            nullLabel = "Uncategorized",
        )
    }

    if (showAddTag) {
        TagPickerDialog(
            title = "Add tag to ${selected.size}",
            tags = tags,
            onPick = { id -> viewModel.addTagSelected(id); showAddTag = false },
            onDismiss = { showAddTag = false },
        )
    }

    if (showRemoveTag) {
        TagPickerDialog(
            title = "Remove tag from ${selected.size}",
            tags = tags,
            onPick = { id -> viewModel.removeTagSelected(id); showRemoveTag = false },
            onDismiss = { showRemoveTag = false },
        )
    }
}

@Composable
private fun PickerDialog(
    title: String,
    options: List<Pair<String?, String>>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(options, key = { it.first ?: "__null__" }) { (id, label) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(id) }
                            .padding(vertical = Spacing.sm),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TagPickerDialog(
    title: String,
    tags: List<com.endgamefinance.data.db.entity.Tag>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (tags.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("No tags yet") },
            text = { Text("Create tags first in More → Tags, then you can apply them here.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        return
    }
    PickerDialog(
        title = title,
        options = tags.map { it.id as String? to it.name },
        onPick = { it?.let(onPick) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun DayHeader(day: LocalDate, items: List<TransactionListItem>) {
    val moneyColors = LocalMoneyColors.current
    val today = LocalDate.now()
    val label = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
    }
    // Net cash flow for the day: income adds, expense subtracts, transfers are neutral.
    val net = items.sumOf {
        when (it.type) {
            "income" -> it.totalAmount
            "expense" -> -it.totalAmount
            else -> 0L
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = (if (net > 0) "+" else "") + Money.format(net),
            style = MaterialTheme.typography.titleMedium.tabular,
            color = when {
                net > 0 -> moneyColors.gain
                net < 0 -> moneyColors.loss
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Swipe right = toggle cleared (snaps back); swipe left = delete after confirm.
 *  Long-press enters multi-select. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTransactionRow(
    item: TransactionListItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleCleared: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val moneyColors = LocalMoneyColors.current
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd ->
                    onToggleCleared()
                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart ->
                    onRequestDelete()
                else -> Unit
            }
            false // always snap back; delete happens via the confirm dialog
        },
    )
    androidx.compose.material3.SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val direction = state.dismissDirection
            Row(
                modifier = Modifier
                    .fillMaxSize() // fill the row's full height, not just its content
                    .background(
                        when (direction) {
                            androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd ->
                                moneyColors.gain.copy(alpha = 0.25f)
                            androidx.compose.material3.SwipeToDismissBoxValue.EndToStart ->
                                moneyColors.loss.copy(alpha = 0.25f)
                            else -> MaterialTheme.colorScheme.surface
                        },
                    )
                    .padding(horizontal = Spacing.md),
                horizontalArrangement =
                if (direction == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd)
                    Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (direction == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) {
                    Icon(Icons.Filled.Check, contentDescription = "Toggle cleared",
                        tint = moneyColors.gain)
                    Text(
                        if (item.isCleared) " Unclear" else " Clear",
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else if (direction ==
                    androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
                ) {
                    Text("Delete ", style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Filled.Close, contentDescription = "Delete",
                        tint = moneyColors.loss)
                }
            }
        },
    ) {
        // Solid surface so row content covers the action background at rest
        Row(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            TransactionRow(item, onClick = onClick, onLongClick = onLongClick)
        }
    }
}

/** A ledger row in multi-select mode: whole row toggles selection. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableTransactionRow(
    item: TransactionListItem,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surface,
            )
            .combinedClickable(onClick = onToggle, onLongClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle
            else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (selected) "Selected" else "Not selected",
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.md),
        )
        Box(modifier = Modifier.weight(1f)) {
            TransactionRow(item, onClick = onToggle)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    item: TransactionListItem,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val moneyColors = LocalMoneyColors.current
    val (amountText, amountColor) = when (item.type) {
        "income" -> "+${Money.format(item.totalAmount)}" to moneyColors.gain
        "expense" -> "−${Money.format(item.totalAmount)}" to moneyColors.loss
        else -> Money.format(item.totalAmount) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val leadingIcon = when {
            item.type == "transfer" -> Icons.Filled.SwapHoriz
            item.splitCount > 1 -> Icons.AutoMirrored.Filled.CallSplit
            else -> IconCatalog.get(item.categoryIcon) ?: Icons.Filled.Category
        }
        // Tonal circle gives rows a scannable left rail; falls back to the
        // payee's initial when there's no category icon (Gmail-style)
        val hasIcon = item.type == "transfer" || item.splitCount > 1 ||
            IconCatalog.get(item.categoryIcon) != null
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Expense icons take the accent (tertiary); income takes primary
            val accent = if (item.type == "income") MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.tertiary
            if (hasIcon) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (item.type != "transfer" &&
                        IconCatalog.get(item.categoryIcon) != null
                    ) accent
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(
                    text = item.payee.trim().take(1).uppercase().ifEmpty { "?" },
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.payee,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (item.isShared) {
                    Text(
                        text = " ⇄",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            val subtitle = if (item.type == "transfer") {
                "${item.accountName} → ${item.toAccountName ?: "?"}"
            } else {
                item.categorySummary ?: "Uncategorized"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium.tabular.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                    color = amountColor,
                )
                // Slot is always reserved so amounts stay column-aligned
                Box(
                    modifier = Modifier
                        .padding(start = Spacing.xs)
                        .size(14.dp),
                ) {
                    if (item.isCleared) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Cleared",
                            tint = moneyColors.gain,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            // End padding matches the tick slot so balances align under the amounts
            Text(
                text = "${item.accountName} · ${Money.format(item.runningBalance)}",
                style = MaterialTheme.typography.labelMedium.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 18.dp),
            )
            // Both sides of a transfer/loan payment show their post-transaction balance
            if (item.toAccountName != null && item.toRunningBalance != null) {
                Text(
                    text = "${item.toAccountName} · ${Money.format(item.toRunningBalance)}",
                    style = MaterialTheme.typography.labelMedium.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 18.dp),
                )
            }
        }
    }
}
