package com.endgamefinance.ui.screens.reminders

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.endgamefinance.data.db.entity.Reminder
import com.endgamefinance.ui.screens.PlaceholderScreen
import com.endgamefinance.ui.theme.LocalMoneyColors
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.ui.theme.tabular
import com.endgamefinance.util.Money
import java.text.DateFormat
import java.util.Date

@Composable
fun RemindersScreen(
    onAddReminder: () -> Unit,
    onEditReminder: (String) -> Unit,
    viewModel: RemindersViewModel =
        viewModel(factory = RemindersViewModel.factory(LocalContext.current)),
) {
    var section by remember { mutableStateOf("bills") }

    // Ask for the notification permission when the user first lands here —
    // the one place its purpose is self-evident. Declining just means
    // reminders are visible in-app only.
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            FilterChip(selected = section == "bills",
                onClick = { section = "bills" }, label = { Text("Bills") })
            FilterChip(selected = section == "calendar",
                onClick = { section = "calendar" }, label = { Text("Calendar") })
            FilterChip(selected = section == "forecast",
                onClick = { section = "forecast" }, label = { Text("Forecast") })
        }
        when (section) {
            "bills" -> BillsTab(viewModel, onAddReminder, onEditReminder)
            "calendar" -> CalendarTab(viewModel)
            else -> ForecastTab(viewModel)
        }
    }
}

@Composable
private fun BillsTab(
    viewModel: RemindersViewModel,
    onAddReminder: () -> Unit,
    onEditReminder: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val message by viewModel.message.collectAsState()
    var postTarget by remember { mutableStateOf<Reminder?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (state.due.isNotEmpty() || state.upcoming.isNotEmpty()) {
                item(key = "planned_totals") {
                    PlannedTotalsCard(state)
                }
            }
            if (suggestions.isNotEmpty()) {
                item(key = "suggestions_header") {
                    SectionHeader("Looks recurring", MaterialTheme.colorScheme.tertiary)
                }
                items(suggestions, key = { "suggest_${it.payee}_${it.accountId}" }) { s ->
                    SuggestionCard(
                        suggestion = s,
                        onAccept = { viewModel.acceptSuggestion(s) },
                        onDismiss = { viewModel.dismissSuggestion(s) },
                    )
                }
            }
            if (state.due.isNotEmpty()) {
                item(key = "due_header") {
                    SectionHeader("Due now", MaterialTheme.colorScheme.error)
                }
                items(state.due, key = { it.reminder.id }) { row ->
                    ReminderRow(
                        row = row,
                        onClick = { onEditReminder(row.reminder.id) },
                        onPost = {
                            if (row.reminder.amount == null) postTarget = row.reminder
                            else viewModel.post(row.reminder)
                        },
                        onSkip = { viewModel.skip(row.reminder) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                }
            }
            if (state.upcoming.isNotEmpty()) {
                item(key = "upcoming_header") {
                    SectionHeader("Upcoming", MaterialTheme.colorScheme.primary)
                }
                items(state.upcoming, key = { it.reminder.id }) { row ->
                    ReminderRow(
                        row = row,
                        onClick = { onEditReminder(row.reminder.id) },
                        onPost = null,
                        onSkip = null,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                }
            }
            if (state.due.isEmpty() && state.upcoming.isEmpty() && suggestions.isEmpty()) {
                item(key = "empty") {
                    com.endgamefinance.ui.components.EmptyState(
                        icon = Icons.Filled.Add,
                        title = "No bills or reminders",
                        body = "Recurring bills stay pending here until posted — and can " +
                            "auto-post and notify you, even with the app closed.",
                        actionLabel = "Add a reminder",
                        onAction = onAddReminder,
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onAddReminder,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.md),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add reminder")
        }
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { viewModel.consumeMessage() },
            title = { Text("Can't post") },
            text = { Text(it) },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeMessage() }) { Text("OK") }
            },
        )
    }

    postTarget?.let { reminder ->
        VariableAmountDialog(
            reminder = reminder,
            onPost = { cents ->
                viewModel.post(reminder, cents)
                postTarget = null
            },
            onDismiss = { postTarget = null },
        )
    }
}

@Composable
private fun PlannedTotalsCard(state: RemindersUiState) {
    val moneyColors = LocalMoneyColors.current
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text("Planned", style = MaterialTheme.typography.titleMedium)
            TotalRow("Bills & expenses", "−" + Money.format(state.plannedExpenses), moneyColors.loss)
            TotalRow("Expected income", "+" + Money.format(state.plannedIncome), moneyColors.gain)
            if (state.plannedTransfers > 0) {
                TotalRow(
                    "Transfers & repayments",
                    Money.format(state.plannedTransfers),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TotalRow(
                "Net per cycle",
                (if (state.plannedNet > 0) "+" else "") + Money.format(state.plannedNet),
                if (state.plannedNet >= 0) moneyColors.gain else moneyColors.loss,
            )
            if (state.variableCount > 0) {
                Text(
                    "${state.variableCount} variable-amount reminder" +
                        (if (state.variableCount > 1) "s" else "") + " not counted",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium.tabular, color = color)
    }
}

@Composable
private fun SuggestionCard(
    suggestion: com.endgamefinance.data.repo.RecurringSuggestion,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = suggestion.payee,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = "${suggestion.occurrences} payments of ~${Money.format(suggestion.amountCents)}, " +
                    com.endgamefinance.data.repo.ReminderRepository.frequencyLabel(
                        suggestion.frequency, suggestion.frequencyInterval,
                    ) +
                    ". Next expected " +
                    DateFormat.getDateInstance(DateFormat.MEDIUM)
                        .format(Date(suggestion.nextDueDate)) + ".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onAccept) { Text("Create reminder") }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier.padding(
            start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.xs,
        ),
    )
}

@Composable
private fun ReminderRow(
    row: ReminderUi,
    onClick: () -> Unit,
    onPost: (() -> Unit)?,
    onSkip: (() -> Unit)?,
) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(row.reminder.name, style = MaterialTheme.typography.bodyLarge)
                val subtitle = buildString {
                    append(DateFormat.getDateInstance(DateFormat.MEDIUM)
                        .format(Date(row.reminder.nextDueDate)))
                    if (row.toAccountName != null) {
                        append(" · ${row.accountName} → ${row.toAccountName}")
                    } else {
                        append(" · ${row.accountName}")
                    }
                    row.categoryName?.let { append(" · $it") }
                    append(
                        " · ${
                            com.endgamefinance.data.repo.ReminderRepository.frequencyLabel(
                                row.reminder.frequency, row.reminder.frequencyInterval,
                            )
                        }",
                    )
                    if (row.reminder.isAutoPost) append(" · auto-post")
                    if (row.reminder.isAutoDetected) append(" · detected")
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (row.isOverdue) moneyColors.loss
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Same semantic coloring as ledger rows: income green, expense red,
            // transfers neutral
            val isTransfer = row.toAccountName != null
            val amountColor = when {
                isTransfer -> MaterialTheme.colorScheme.onSurfaceVariant
                row.isIncome -> moneyColors.gain
                else -> moneyColors.loss
            }
            Text(
                text = row.reminder.amount?.let {
                    val prefix = when {
                        isTransfer -> ""
                        row.isIncome -> "+"
                        else -> "−"
                    }
                    prefix + Money.format(it)
                } ?: "varies",
                style = MaterialTheme.typography.titleMedium,
                color = amountColor,
            )
        }
        if (onPost != null && onSkip != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onPost) { Text("Post now") }
                TextButton(onClick = onSkip) {
                    Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun VariableAmountDialog(
    reminder: Reminder,
    onPost: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(reminder.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("This bill varies — enter this occurrence's amount.")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cents = Money.parse(amountText.trim())
                    if (cents == null || cents <= 0) error = "Amount must be a positive number"
                    else onPost(cents)
                },
            ) { Text("Post") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
