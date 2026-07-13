package com.endgamefinance.ui.screens.ledger

import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.model.CategoryChoice
import com.endgamefinance.data.db.model.TransactionListItem
import com.endgamefinance.data.db.model.categoryChoices
import com.endgamefinance.ui.components.DropdownField
import com.endgamefinance.ui.components.IconCatalog
import com.endgamefinance.ui.theme.LocalMoneyColors
import com.endgamefinance.ui.theme.Spacing
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
class LedgerViewModel(db: EndgameDatabase) : ViewModel() {

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

    val categories: StateFlow<List<CategoryChoice>> =
        db.categoryDao().observeAll()
            .map { categoryChoices(it) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    showFiltersInitially: Boolean = false,
    viewModel: LedgerViewModel = viewModel(factory = LedgerViewModel.factory(LocalContext.current)),
) {
    val transactions by viewModel.transactions.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var showFilters by remember { mutableStateOf(showFiltersInitially) }
    var minText by remember { mutableStateOf("") }
    var maxText by remember { mutableStateOf("") }
    var datePickTarget by remember { mutableStateOf<String?>(null) } // "start" | "end"

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ledger", style = MaterialTheme.typography.headlineMedium)
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Toggle filters",
                            tint = if (filters.isActive) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (showFilters) {
                item(key = "filters") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md),
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
                        DropdownField(
                            label = "Category",
                            options = listOf<Pair<String?, String>>(null to "All categories") +
                                categories.map { it.id to it.displayName },
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
                item(key = "day_$day") {
                    DayHeader(day = day, items = dayItems)
                }
                items(dayItems, key = { it.id }) { item ->
                    TransactionRow(item, onClick = { onOpenTransaction(item.id) })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                }
            }
            if (transactions.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = if (filters.isActive) "No transactions match these filters."
                        else "No transactions yet. Tap + to record your first one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onAddTransaction,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.md),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add transaction")
        }
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
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = (if (net > 0) "+" else "") + Money.format(net),
            style = MaterialTheme.typography.titleMedium,
            color = when {
                net > 0 -> moneyColors.gain
                net < 0 -> moneyColors.loss
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
fun TransactionRow(item: TransactionListItem, onClick: () -> Unit = {}) {
    val moneyColors = LocalMoneyColors.current
    val (amountText, amountColor) = when (item.type) {
        "income" -> "+${Money.format(item.totalAmount)}" to moneyColors.gain
        "expense" -> "−${Money.format(item.totalAmount)}" to moneyColors.loss
        else -> Money.format(item.totalAmount) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val leadingIcon = when {
            item.type == "transfer" -> Icons.Filled.SwapHoriz
            item.splitCount > 1 -> Icons.AutoMirrored.Filled.CallSplit
            else -> IconCatalog.get(item.categoryIcon) ?: Icons.Filled.Category
        }
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = if (item.type != "transfer" && IconCatalog.get(item.categoryIcon) != null)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.payee,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (item.isCleared) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Cleared",
                        tint = moneyColors.gain,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
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
            Text(
                text = amountText,
                style = MaterialTheme.typography.titleMedium,
                color = amountColor,
            )
            Text(
                text = "${item.accountName} · ${Money.format(item.runningBalance)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Both sides of a transfer/loan payment show their post-transaction balance
            if (item.toAccountName != null && item.toRunningBalance != null) {
                Text(
                    text = "${item.toAccountName} · ${Money.format(item.toRunningBalance)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
