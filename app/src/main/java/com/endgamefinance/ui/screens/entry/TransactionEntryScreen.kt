package com.endgamefinance.ui.screens.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endgamefinance.data.db.entity.Account
import com.endgamefinance.ui.components.DropdownField
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlinx.coroutines.launch

private class SplitRow(categoryId: String?, amountText: String) {
    var categoryId by mutableStateOf(categoryId)
    var amountText by mutableStateOf(amountText)
}

private class AccountSplitRow(accountId: String?, categoryId: String?, amountText: String) {
    var accountId by mutableStateOf(accountId)
    var categoryId by mutableStateOf(categoryId)
    var amountText by mutableStateOf(amountText)
}

/** DatePicker returns UTC-midnight; keep the already-chosen local time of day. */
private fun combineDateKeepTime(dateUtcMillis: Long, existing: Long): Long {
    val date = Instant.ofEpochMilli(dateUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val time = Instant.ofEpochMilli(existing).atZone(ZoneId.systemDefault()).toLocalTime()
    return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun withTimeOfDay(existing: Long, hour: Int, minute: Int): Long =
    Instant.ofEpochMilli(existing).atZone(ZoneId.systemDefault())
        .withHour(hour).withMinute(minute)
        .toInstant().toEpochMilli()

/** Set the date part of [existing] to [target], keeping its time of day. */
private fun setDateKeepTime(existing: Long, target: LocalDate): Long {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(existing).atZone(zone).toLocalTime()
    return target.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEntryScreen(
    transactionId: String?,
    onDone: () -> Unit,
    viewModel: TransactionEntryViewModel =
        viewModel(factory = TransactionEntryViewModel.factory(LocalContext.current)),
) {
    val isEditing = transactionId != null
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val payeeSuggestions by viewModel.payeeSuggestions.collectAsState()
    val recentCategoryIds by viewModel.recentCategoryIds.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val amountFocus = remember { FocusRequester() }

    var type by remember { mutableStateOf("expense") }
    // Default account = last one used (feature 5); overwritten in edit mode below
    var accountId by remember { mutableStateOf(viewModel.lastUsedAccountId()) }
    var toAccountId by remember { mutableStateOf<String?>(null) }
    var payee by remember { mutableStateOf("") }
    var suppressSuggestions by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var timestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var splitByAccount by remember { mutableStateOf(false) }
    val accountSplitRows = remember {
        mutableStateListOf(AccountSplitRow(null, null, ""), AccountSplitRow(null, null, ""))
    }
    var splitMode by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var principalText by remember { mutableStateOf("") }
    var interestText by remember { mutableStateOf("") }
    var interestCategoryId by remember { mutableStateOf<String?>(null) }
    val splitRows = remember { mutableStateListOf(SplitRow(null, ""), SplitRow(null, "")) }
    var isCleared by remember { mutableStateOf(false) }
    var isShared by remember { mutableStateOf(false) }
    val selectedTagIds = remember { mutableStateListOf<String>() }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showNewTag by remember { mutableStateOf(false) }

    LaunchedEffect(transactionId) {
        if (transactionId != null) {
            viewModel.load(transactionId)?.let { loaded ->
                type = loaded.entity.type
                accountId = loaded.entity.accountId
                toAccountId = loaded.entity.toAccountId
                payee = loaded.entity.payee
                suppressSuggestions = true
                notes = loaded.entity.notes ?: ""
                timestamp = loaded.entity.timestamp
                isCleared = loaded.entity.isCleared
                isShared = loaded.entity.isShared
                selectedTagIds.clear()
                selectedTagIds.addAll(loaded.tagIds)
                val isLoanPayment = loaded.entity.type == "transfer" &&
                    loaded.splits.any { it.categoryId != null }
                if (isLoanPayment) {
                    type = "loan_payment"
                    loaded.splits.firstOrNull { it.categoryId == null }?.let {
                        principalText = Money.formatPlain(it.amount)
                    }
                    loaded.splits.firstOrNull { it.categoryId != null }?.let {
                        interestText = Money.formatPlain(it.amount)
                        interestCategoryId = it.categoryId
                    }
                } else if (loaded.splits.size > 1) {
                    splitMode = true
                    splitRows.clear()
                    loaded.splits.forEach { split ->
                        splitRows.add(
                            SplitRow(split.categoryId, Money.formatPlain(split.amount)),
                        )
                    }
                } else {
                    loaded.splits.firstOrNull()?.let { split ->
                        amountText = Money.formatPlain(split.amount)
                        categoryId = split.categoryId
                    }
                }
            }
        }
    }

    val categoryChoices = categories.filter { it.type == viewModel.categoryTypeFor(type) }
    // Single-amount modes get the hero amount field; splits/loan carry their own
    val heroAmountMode = (type == "expense" || type == "income" || type == "transfer") &&
        !(splitMode && (type == "expense" || type == "income"))

    // Autofocus the amount on a fresh, simple entry (feature 1)
    LaunchedEffect(heroAmountMode) {
        if (!isEditing && heroAmountMode) {
            amountFocus.requestFocus()
            keyboard?.show()
        }
    }

    fun resetForNext() {
        payee = ""
        amountText = ""
        categoryId = null
        notes = ""
        selectedTagIds.clear()
        splitRows.forEach { it.categoryId = null; it.amountText = "" }
        principalText = ""; interestText = ""
        isCleared = false; isShared = false
        error = null
        suppressSuggestions = true
    }

    /** Runs validation + save; [then] decides navigation vs reset. Errors set [error]. */
    fun performSave(then: () -> Unit) {
        val effectivePayee = payee.trim().ifEmpty {
            when (type) {
                "transfer" -> "Transfer"
                "loan_payment" -> "Loan Payment"
                else -> { error = "Payee is required"; return }
            }
        }
        val accountSplitSave = splitMode && splitByAccount && !isEditing &&
            type != "transfer" && type != "loan_payment"
        if (accountSplitSave) {
            val rows = accountSplitRows.map { row ->
                val rowAccount = row.accountId ?: run {
                    error = "Every account split needs an account"; return
                }
                val cents = Money.parse(row.amountText)
                if (cents == null || cents <= 0) {
                    error = "Every account split needs a valid positive amount"; return
                }
                Triple(rowAccount, row.categoryId, cents)
            }
            viewModel.saveAccountSplits(
                type = type, rows = rows, payee = effectivePayee, notes = notes,
                timestamp = timestamp, isCleared = isCleared, isShared = isShared,
                tagIds = selectedTagIds.toList(), onSaved = then,
            )
            return
        }
        val account = accountId ?: run { error = "Pick an account"; return }
        val splits: List<Pair<String?, Long>> = when {
            type == "loan_payment" -> {
                val principal = Money.parse(principalText)
                if (principal == null || principal <= 0) {
                    error = "Principal must be a positive amount"; return
                }
                val interest = interestText.trim().takeIf { it.isNotEmpty() }?.let {
                    Money.parse(it) ?: run { error = "Interest is not a valid amount"; return }
                } ?: 0L
                if (interest > 0 && interestCategoryId == null) {
                    error = "Pick a category for the interest (e.g. Loan Interest)"; return
                }
                if (toAccountId == null) { error = "Pick the loan account"; return }
                listOf<Pair<String?, Long>>(null to principal) +
                    if (interest > 0) listOf(interestCategoryId to interest) else emptyList()
            }
            type != "transfer" && splitMode -> {
                val parsed = splitRows.mapNotNull { row ->
                    val cents = Money.parse(row.amountText)
                    if (cents == null || cents <= 0) null else row.categoryId to cents
                }
                if (parsed.size != splitRows.size) {
                    error = "Every split needs a valid positive amount"; return
                }
                parsed
            }
            else -> {
                val cents = Money.parse(amountText)
                if (cents == null || cents <= 0) {
                    error = "Amount must be a positive number"; return
                }
                listOf((if (type == "transfer") null else categoryId) to cents)
            }
        }
        if (type == "transfer" && toAccountId == null) {
            error = "Pick a destination account"; return
        }
        viewModel.save(
            existingId = transactionId,
            type = if (type == "loan_payment") "transfer" else type,
            accountId = account,
            toAccountId = if (type == "transfer" || type == "loan_payment") toAccountId else null,
            payee = effectivePayee, notes = notes, timestamp = timestamp, splits = splits,
            isCleared = isCleared, isShared = isShared,
            tagIds = selectedTagIds.toList(), onSaved = then,
        )
    }

    com.endgamefinance.ui.components.EndgameScaffold(
        title = if (isEditing) "Edit transaction" else "New transaction",
        onBack = onDone,
    ) { innerPadding ->
    // Scrollable body + sticky action bar (feature 2)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Type as a segmented control (feature 10)
            val typeOptions = listOf(
                "expense" to "Expense",
                "income" to "Income",
                "transfer" to "Transfer",
                "loan_payment" to "Loan",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                typeOptions.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = type == value,
                        onClick = {
                            type = value
                            toAccountId = null
                            categoryId = null
                            splitRows.forEach { it.categoryId = null }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, typeOptions.size),
                    ) { Text(label) }
                }
            }

            // ---------------- ZONE 1: the money ----------------
            SectionCard {
                if (type == "loan_payment") {
                    Text(
                        "One transaction, two effects: the principal reduces the loan, " +
                            "the interest is recorded as a borrowing cost.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Hero amount (feature 1)
                if (heroAmountMode) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineMedium
                            .copy(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(amountFocus),
                    )
                }

                val accountSplitActive = splitMode && splitByAccount && !isEditing &&
                    type != "transfer" && type != "loan_payment"
                if (!accountSplitActive) {
                    DropdownField(
                        label = when (type) {
                            "transfer" -> "From account"
                            "loan_payment" -> "Pay from"
                            else -> "Account"
                        },
                        options = accounts
                            .filter { type != "loan_payment" || it.type != Account.TYPE_LIABILITY }
                            .map { it.id to it.name },
                        selectedId = accountId,
                        onSelect = { accountId = it },
                    )
                }
                if (type == "transfer") {
                    DropdownField(
                        label = "To account",
                        options = accounts.filter { it.id != accountId }.map { it.id to it.name },
                        selectedId = toAccountId,
                        onSelect = { toAccountId = it },
                    )
                }
                if (type == "loan_payment") {
                    DropdownField(
                        label = "Loan account",
                        options = accounts
                            .filter { it.type == Account.TYPE_LIABILITY && it.id != accountId }
                            .map { it.id to it.name },
                        selectedId = toAccountId,
                        onSelect = { toAccountId = it },
                    )
                }

                // Loan principal / interest
                if (type == "loan_payment") {
                    OutlinedTextField(
                        value = principalText,
                        onValueChange = { principalText = it },
                        label = { Text("Principal") },
                        supportingText = { Text("Reduces the loan balance") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = interestText,
                        onValueChange = { interestText = it },
                        label = { Text("Interest") },
                        supportingText = { Text("The cost of borrowing — an expense") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownField(
                        label = "Interest category",
                        options = categoryChoices.map { it.id to it.displayName },
                        selectedId = interestCategoryId,
                        onSelect = { interestCategoryId = it },
                    )
                    val loanTotal =
                        (Money.parse(principalText) ?: 0L) + (Money.parse(interestText) ?: 0L)
                    Text(
                        "Total payment: ${Money.format(loanTotal)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Category for simple expense/income + recent-category chips (feature 3)
                if ((type == "expense" || type == "income") && !splitMode) {
                    val recentChoices = recentCategoryIds
                        .mapNotNull { id -> categoryChoices.firstOrNull { it.id == id } }
                        .take(6)
                    if (recentChoices.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            recentChoices.forEach { choice ->
                                FilterChip(
                                    selected = categoryId == choice.id,
                                    onClick = {
                                        categoryId =
                                            if (categoryId == choice.id) null else choice.id
                                    },
                                    label = { Text(choice.displayName) },
                                )
                            }
                        }
                    }
                    DropdownField(
                        label = "Category",
                        options = listOf<Pair<String?, String>>(null to "Uncategorized") +
                            categoryChoices.map { it.id to it.displayName },
                        selectedId = categoryId,
                        onSelect = { categoryId = it },
                        nullLabel = "Uncategorized",
                    )
                }

                // Split toggle + editors
                if (type != "transfer" && type != "loan_payment") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Split this transaction", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = splitMode, onCheckedChange = { splitMode = it })
                    }
                    if (splitMode && !isEditing) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            FilterChip(
                                selected = !splitByAccount,
                                onClick = { splitByAccount = false },
                                label = { Text("By category") },
                            )
                            FilterChip(
                                selected = splitByAccount,
                                onClick = { splitByAccount = true },
                                label = { Text("By account") },
                            )
                        }
                    }
                }

                if (splitMode && splitByAccount && !isEditing &&
                    type != "transfer" && type != "loan_payment"
                ) {
                    accountSplitRows.forEachIndexed { index, row ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    DropdownField(
                                        label = "Account ${index + 1}",
                                        options = accounts.map { it.id to it.name },
                                        selectedId = row.accountId,
                                        onSelect = { row.accountId = it },
                                    )
                                }
                                OutlinedTextField(
                                    value = row.amountText,
                                    onValueChange = { row.amountText = it },
                                    label = { Text("Amount") },
                                    singleLine = true,
                                    keyboardOptions =
                                    KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(0.7f),
                                )
                                if (accountSplitRows.size > 1) {
                                    IconButton(onClick = { accountSplitRows.removeAt(index) }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Remove split")
                                    }
                                }
                            }
                            DropdownField(
                                label = "Category ${index + 1}",
                                options = listOf<Pair<String?, String>>(null to "Uncategorized") +
                                    categoryChoices.map { it.id to it.displayName },
                                selectedId = row.categoryId,
                                onSelect = { row.categoryId = it },
                                nullLabel = "Uncategorized",
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { accountSplitRows.add(AccountSplitRow(null, null, "")) },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Add account split", modifier = Modifier.padding(start = Spacing.xs))
                    }
                    val accountTotal = accountSplitRows.sumOf { Money.parse(it.amountText) ?: 0L }
                    Text(
                        "Total: ${Money.format(accountTotal)} — one transaction per account",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (splitMode && (type == "expense" || type == "income")) {
                    splitRows.forEachIndexed { index, row ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                DropdownField(
                                    label = "Category ${index + 1}",
                                    options = listOf<Pair<String?, String>>(null to "Uncategorized") +
                                        categoryChoices.map { it.id to it.displayName },
                                    selectedId = row.categoryId,
                                    onSelect = { row.categoryId = it },
                                    nullLabel = "Uncategorized",
                                )
                            }
                            OutlinedTextField(
                                value = row.amountText,
                                onValueChange = { row.amountText = it },
                                label = { Text("Amount") },
                                singleLine = true,
                                keyboardOptions =
                                KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(0.7f),
                            )
                            if (splitRows.size > 1) {
                                IconButton(onClick = { splitRows.removeAt(index) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove split")
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = { splitRows.add(SplitRow(null, "")) }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Add split", modifier = Modifier.padding(start = Spacing.xs))
                    }
                    val runningTotal = splitRows.sumOf { Money.parse(it.amountText) ?: 0L }
                    Text(
                        "Total: ${Money.format(runningTotal)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // ---------------- ZONE 2: the story ----------------
            SectionCard {
                Column {
                    OutlinedTextField(
                        value = payee,
                        onValueChange = {
                            payee = it
                            suppressSuggestions = false
                            viewModel.searchPayees(it)
                        },
                        label = {
                            Text(
                                if (type == "transfer" || type == "loan_payment")
                                    "Payee (optional)" else "Payee",
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!suppressSuggestions && payeeSuggestions.isNotEmpty()) {
                        Surface(tonalElevation = 2.dp) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                payeeSuggestions.forEach { suggestion ->
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                payee = suggestion
                                                suppressSuggestions = true
                                                viewModel.clearPayeeSuggestions()
                                                scope.launch {
                                                    val prefill =
                                                        viewModel.prefillForPayee(suggestion)
                                                    if (amountText.isBlank() &&
                                                        prefill.amountCents != null
                                                    ) {
                                                        amountText = Money.format(
                                                            prefill.amountCents,
                                                        ).replace("$", "")
                                                    }
                                                    if (categoryId == null) {
                                                        categoryId = prefill.categoryId
                                                    }
                                                }
                                            }
                                            .padding(
                                                horizontal = Spacing.md, vertical = Spacing.sm,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }

                // Date quick-chips (feature 6), preserving the time of day
                val today = LocalDate.now()
                val chosenDate = Instant.ofEpochMilli(timestamp)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(
                        selected = chosenDate == today,
                        onClick = { timestamp = setDateKeepTime(timestamp, today) },
                        label = { Text("Today") },
                    )
                    FilterChip(
                        selected = chosenDate == today.minusDays(1),
                        onClick = {
                            timestamp = setDateKeepTime(timestamp, today.minusDays(1))
                        },
                        label = { Text("Yesterday") },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = DateFormat.getDateInstance().format(Date(timestamp)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Date") },
                        modifier = Modifier
                            .weight(1.2f)
                            .clickable { showDatePicker = true },
                        trailingIcon = {
                            TextButton(onClick = { showDatePicker = true }) { Text("Set") }
                        },
                    )
                    OutlinedTextField(
                        value = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("h:mm a")),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Time") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showTimePicker = true },
                        trailingIcon = {
                            TextButton(onClick = { showTimePicker = true }) { Text("Set") }
                        },
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---------------- ZONE 3: the extras ----------------
            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cleared", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = isCleared, onCheckedChange = { isCleared = it })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Shared / reimbursable", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = isShared, onCheckedChange = { isShared = it })
                }

                Text("Tags", style = MaterialTheme.typography.labelLarge)
                // Tags wrap (feature 9) + inline "+ new" chip
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in selectedTagIds,
                            onClick = {
                                if (tag.id in selectedTagIds) selectedTagIds.remove(tag.id)
                                else selectedTagIds.add(tag.id)
                            },
                            label = { Text("#${tag.name}") },
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { showNewTag = true },
                        label = { Text("+ New tag") },
                    )
                }
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (isEditing && transactionId != null) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete transaction", color = MaterialTheme.colorScheme.error)
                }
                val auditRows by remember(transactionId) { viewModel.auditFor(transactionId) }
                    .collectAsState(initial = emptyList())
                if (auditRows.isNotEmpty()) {
                    Text("Change history", style = MaterialTheme.typography.titleMedium)
                    auditRows.forEach { row ->
                        Column(modifier = Modifier.padding(bottom = Spacing.sm)) {
                            Text(
                                "${row.fieldName}: ${row.oldValue ?: "—"} → ${row.newValue ?: "—"}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                DateFormat.getDateTimeInstance(
                                    DateFormat.MEDIUM, DateFormat.SHORT,
                                ).format(Date(row.changedAt)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Sticky action bar (features 2 & 4)
        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (!isEditing) {
                    OutlinedButton(
                        onClick = {
                            keyboard?.hide()
                            performSave {
                                resetForNext()
                                if (heroAmountMode) amountFocus.requestFocus()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Save & add") }
                }
                Button(
                    onClick = { keyboard?.hide(); performSave(onDone) },
                    modifier = Modifier.weight(1f),
                ) { Text(if (isEditing) "Save changes" else "Save") }
            }
        }
    }
    }

    if (showNewTag) {
        var newTagName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewTag = false },
            title = { Text("New tag") },
            text = {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("Name (without #)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTagName.isNotBlank()) {
                            viewModel.createTag(newTagName) { id -> selectedTagIds.add(id) }
                            showNewTag = false
                        }
                    },
                ) { Text("Create & apply") }
            },
            dismissButton = {
                TextButton(onClick = { showNewTag = false }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteConfirm && transactionId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this transaction?") },
            text = {
                Text(
                    "This permanently removes the transaction, its splits, tags, " +
                        "and change history, and re-derives account balances. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.delete(transactionId, onDone)
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = timestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            timestamp = combineDateKeepTime(it, timestamp)
                        }
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        val zoned = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        val timePickerState = rememberTimePickerState(
            initialHour = zoned.hour,
            initialMinute = zoned.minute,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        timestamp = withTimeOfDay(
                            timestamp, timePickerState.hour, timePickerState.minute,
                        )
                        showTimePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }
}

/** A visually grouped zone of the form (feature 8). */
@Composable
private fun SectionCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
    }
}
