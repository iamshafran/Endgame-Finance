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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endgamefinance.data.ai.LoanInterestEstimator
import com.endgamefinance.data.db.entity.Category
import com.endgamefinance.data.db.entity.Reminder
import com.endgamefinance.ui.components.DropdownField
import kotlinx.coroutines.launch
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
    initialSection: String = "bills",
    viewModel: RemindersViewModel =
        viewModel(factory = RemindersViewModel.factory(LocalContext.current)),
) {
    var section by remember { mutableStateOf(initialSection) }

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

    com.endgamefinance.ui.components.EndgameScaffold(
        title = "Reminders",
        floatingActionButton = {
            if (section == "bills") {
                FloatingActionButton(onClick = onAddReminder) {
                    Icon(Icons.Filled.Add, contentDescription = "Add reminder")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
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
    val categories by viewModel.categories.collectAsState()
    var postTarget by remember { mutableStateOf<Reminder?>(null) }
    var loanTarget by remember { mutableStateOf<ReminderUi?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Last row scrolls clear of the FAB
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = Spacing.fabClearance,
            ),
        ) {
            if (state.due.isNotEmpty() || state.upcoming.isNotEmpty()) {
                item(key = "planned_totals_top") {
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
                            when {
                                row.isLoanPayment -> loanTarget = row
                                row.reminder.amount == null -> postTarget = row.reminder
                                else -> viewModel.post(row.reminder)
                            }
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
                        onPost = {
                            when {
                                row.isLoanPayment -> loanTarget = row
                                row.reminder.amount == null -> postTarget = row.reminder
                                else -> viewModel.post(row.reminder)
                            }
                        },
                        onSkip = { viewModel.skip(row.reminder) },
                        postLabel = "Pay early",
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

    loanTarget?.let { row ->
        LoanPaymentDialog(
            row = row,
            categories = categories,
            viewModel = viewModel,
            onDismiss = { loanTarget = null },
        )
    }
}

@Composable
private fun LoanPaymentDialog(
    row: ReminderUi,
    categories: List<Category>,
    viewModel: RemindersViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val loanId = row.reminder.toAccountId ?: return
    var paymentText by remember {
        mutableStateOf(row.reminder.amount?.let { Money.formatPlain(it) } ?: "")
    }
    var interestText by remember { mutableStateOf("") }
    var interestCategoryId by remember { mutableStateOf(viewModel.defaultInterestCategoryId()) }
    var estimating by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val categoryGroups by viewModel.categoryGroups.collectAsState()
    val expenseOptions = remember(categories, categoryGroups) {
        com.endgamefinance.ui.components.categoryPickItems(
            categories.filter { it.type == Category.TYPE_EXPENSE },
            categoryGroups,
        )
    }

    fun runEstimate() {
        val payment = Money.parse(paymentText.trim())
        if (payment == null || payment <= 0) { error = "Enter the payment amount first"; return }
        estimating = true; error = null
        scope.launch {
            val est = viewModel.estimateLoanInterest(loanId, payment)
            interestText = Money.formatPlain(est.interestCents)
            note = when (est.source) {
                LoanInterestEstimator.Source.AI ->
                    "Estimated on-device from your past payments — adjust if needed."
                LoanInterestEstimator.Source.HISTORY ->
                    "Estimated from your most recent payment's split — adjust if needed."
                LoanInterestEstimator.Source.NONE ->
                    "No payment history yet — enter the interest portion yourself."
            }
            estimating = false
        }
    }

    LaunchedEffect(Unit) { if (row.reminder.amount != null) runEstimate() }

    val payment = Money.parse(paymentText.trim())
    val interest = Money.parse(interestText.trim()) ?: 0L
    val principal = payment?.let { (it - interest).coerceAtLeast(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.reminder.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "Split this payment into principal and interest. The interest is a " +
                        "suggestion — check it before posting.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = paymentText,
                    onValueChange = { paymentText = it },
                    label = { Text("Payment amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    TextButton(onClick = { runEstimate() }, enabled = !estimating) {
                        Text("Estimate interest")
                    }
                    if (estimating) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                }
                OutlinedTextField(
                    value = interestText,
                    onValueChange = { interestText = it },
                    label = { Text("Interest") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                com.endgamefinance.ui.components.CategoryPickerField(
                    label = "Interest category",
                    items = expenseOptions,
                    selectedId = interestCategoryId,
                    onSelect = { interestCategoryId = it },
                    nullLabel = "No category",
                )
                principal?.let {
                    Text(
                        "Principal (reduces the loan): ${Money.format(it)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
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
            Button(onClick = {
                val p = Money.parse(paymentText.trim())
                if (p == null || p <= 0) { error = "Payment must be a positive number"; return@Button }
                val i = Money.parse(interestText.trim()) ?: 0L
                if (i < 0 || i > p) { error = "Interest can't exceed the payment"; return@Button }
                if (i > 0 && interestCategoryId == null) {
                    error = "Pick a category for the interest"; return@Button
                }
                viewModel.postLoanPayment(row.reminder, p, i, interestCategoryId)
                onDismiss()
            }) { Text("Post payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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

/** Shared with CalendarTab's day detail — keep it presentation-only. */
@Composable
internal fun ReminderRow(
    row: ReminderUi,
    onClick: () -> Unit,
    onPost: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    postLabel: String = "Post now",
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
            // Leading icon rail, matching ledger rows: category icon for
            // bills/income, transfer glyph for repayments/transfers.
            val isTransferReminder = row.toAccountName != null
            Box(
                modifier = Modifier
                    .padding(end = Spacing.sm)
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        androidx.compose.ui.graphics.RectangleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        isTransferReminder -> Icons.Filled.SwapHoriz
                        else -> com.endgamefinance.ui.components.IconCatalog
                            .get(row.categoryIcon) ?: Icons.Filled.Category
                    },
                    contentDescription = null,
                    tint = when {
                        isTransferReminder -> moneyColors.transfer
                        row.categoryIcon == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        row.isIncome -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
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
                isTransfer -> moneyColors.transfer
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
                TextButton(onClick = onPost) { Text(postLabel) }
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
