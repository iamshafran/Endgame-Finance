package com.endgamefinance.ui.screens.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endgamefinance.ui.components.IconCatalog
import com.endgamefinance.ui.theme.LocalMoneyColors
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money

@Composable
fun BudgetScreen() {
    var section by remember { mutableStateOf("budgets") }
    com.endgamefinance.ui.components.EndgameScaffold(title = "Budget") { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                FilterChip(
                    selected = section == "budgets",
                    onClick = { section = "budgets" },
                    label = { Text("Budgets") },
                )
                FilterChip(
                    selected = section == "envelopes",
                    onClick = { section = "envelopes" },
                    label = { Text("Envelopes") },
                )
            }
            when (section) {
                "budgets" -> BudgetsTab()
                else -> EnvelopesTab()
            }
        }
    }
}

@Composable
private fun BudgetsTab(
    viewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    var editRow by remember { mutableStateOf<BudgetRowUi?>(null) }
    var showModeDialog by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.previousMonth() }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
                }
                Text(state.monthLabel, style = MaterialTheme.typography.titleLarge)
                Row {
                    IconButton(onClick = { viewModel.nextMonth() }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
                    }
                    IconButton(onClick = { showModeDialog = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = "Budget mode")
                    }
                }
            }
        }
        item(key = "summary") {
            SummaryCard(state)
        }
        if (state.canCopyLastMonth) {
            item(key = "copy") {
                OutlinedButton(
                    onClick = { viewModel.copyLastMonth() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                ) {
                    Text("Copy last month's budget")
                }
            }
        }
        items(state.rows, key = { it.categoryId }) { row ->
            BudgetRow(row = row, onClick = { editRow = row })
            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
        }
        if (state.rows.isEmpty()) {
            item(key = "empty") {
                com.endgamefinance.ui.components.EmptyState(
                    icon = androidx.compose.material.icons.Icons.Filled.Category,
                    title = "Nothing to budget yet",
                    body = "Budgets attach to expense categories. Create categories under " +
                        "More → Categories, then set monthly amounts here.",
                )
            }
        }
    }

    editRow?.let { row ->
        BudgetEditDialog(
            row = row,
            viewModel = viewModel,
            onSave = { cents, rollover ->
                viewModel.setBudget(row.categoryId, cents, rollover)
                editRow = null
            },
            onDismiss = { editRow = null },
        )
    }

    if (showModeDialog) {
        ModeDialog(
            current = state.mode,
            onPick = { viewModel.setMode(it); showModeDialog = false },
            onDismiss = { showModeDialog = false },
        )
    }
}

@Composable
private fun SummaryCard(state: BudgetUiState) {
    val moneyColors = LocalMoneyColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            if (state.mode == BudgetMode.ZERO_BASED) {
                SummaryLine("Income", Money.format(state.income), null)
                SummaryLine("Allocated", Money.format(state.allocatedTotal), null)
                SummaryLine("Spent", Money.format(state.spentTotal), null)
            } else {
                SummaryLine("Income", Money.format(state.income), null)
                SummaryLine("Spent", Money.format(state.spentTotal), null)
                val net = state.income - state.spentTotal
                SummaryLine(
                    "Net", Money.format(net),
                    if (net >= 0) moneyColors.gain else moneyColors.loss,
                )
            }
        }
    }
    if (state.mode == BudgetMode.ZERO_BASED && state.unallocated != 0L) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            colors = CardDefaults.cardColors(
                containerColor = if (state.unallocated > 0)
                    MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(
                text = if (state.unallocated > 0) {
                    "${Money.format(state.unallocated)} of ${state.monthLabel} income " +
                        "is not allocated yet. Every dollar deserves a job."
                } else {
                    "Allocations exceed income by ${Money.format(-state.unallocated)}."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.unallocated > 0) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.md),
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value, style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BudgetRow(row: BudgetRowUi, onClick: () -> Unit) {
    val moneyColors = LocalMoneyColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = IconCatalog.get(row.icon) ?: Icons.Filled.Category,
                    contentDescription = null,
                    tint = if (row.icon != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = Spacing.sm),
                )
                Column {
                    Text(row.displayName, style = MaterialTheme.typography.bodyLarge)
                    if (row.carryIn > 0) {
                        Text(
                            "includes ${Money.format(row.carryIn)} carried over",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            Text(
                text = if (row.available > 0)
                    "${Money.format(row.spent)} / ${Money.format(row.available)}"
                else Money.format(row.spent),
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    row.overBudget -> moneyColors.loss
                    row.available > 0 -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (row.available > 0) {
            LinearProgressIndicator(
                progress = { (row.spent.toFloat() / row.available).coerceIn(0f, 1f) },
                color = if (row.overBudget) moneyColors.loss else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
            )
        }
        row.pacing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = if (row.overBudget) moneyColors.loss
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun BudgetEditDialog(
    row: BudgetRowUi,
    viewModel: BudgetViewModel,
    onSave: (cents: Long?, rollover: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember {
        mutableStateOf(row.allocated?.let { Money.formatPlain(it) } ?: "")
    }
    var rollover by remember { mutableStateOf(row.rolloverMode) }
    var suggestion by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(row.categoryId) {
        suggestion = viewModel.rollingAverage(row.categoryId)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monthly budget") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                suggestion?.let { avg ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Averaged ${Money.format(avg)}/mo over the last 12 months",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { amountText = Money.formatPlain(avg) },
                        ) { Text("Use") }
                    }
                }
                Text("At month end", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(
                        selected = rollover == "reset",
                        onClick = { rollover = "reset" },
                        label = { Text("Reset") },
                    )
                    FilterChip(
                        selected = rollover == "carry",
                        onClick = { rollover = "carry" },
                        label = { Text("Carry leftover") },
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = amountText.trim()
                    if (trimmed.isEmpty()) {
                        onSave(null, rollover)
                    } else {
                        val cents = Money.parse(trimmed)
                        if (cents == null || cents < 0) {
                            error = "Not a valid amount"
                        } else {
                            onSave(cents, rollover)
                        }
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (row.allocated != null) {
                    TextButton(onClick = { onSave(null, rollover) }) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ModeDialog(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ModeOption(
                    title = "Zero-Based",
                    description = "Give every dollar of income a job. You'll get a gentle " +
                        "reminder when income is unallocated — never a block.",
                    selected = current == BudgetMode.ZERO_BASED,
                    onClick = { onPick(BudgetMode.ZERO_BASED) },
                )
                ModeOption(
                    title = "Cash-Flow",
                    description = "High-level tracking of income vs spending. " +
                        "No allocation requirement.",
                    selected = current == BudgetMode.CASH_FLOW,
                    onClick = { onPick(BudgetMode.CASH_FLOW) },
                )
                Text(
                    "Switching modes never deletes budget data.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun ModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
