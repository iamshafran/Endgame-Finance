package com.endgamefinance.ui.screens.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.endgamefinance.data.db.model.categoryChoices
import com.endgamefinance.data.repo.ReminderRepository
import com.endgamefinance.ui.components.DropdownField
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date

private val frequencies = listOf(
    "once" to "Once",
    "daily" to "Daily",
    "weekly" to "Weekly",
    "monthly" to "Monthly",
    "yearly" to "Yearly",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditScreen(
    reminderId: String?,
    onDone: () -> Unit,
    viewModel: RemindersViewModel =
        viewModel(factory = RemindersViewModel.factory(LocalContext.current)),
) {
    val isEditing = reminderId != null
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var loaded by remember { mutableStateOf<Reminder?>(null) }
    var name by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf<String?>(null) }
    var toAccountId by remember { mutableStateOf<String?>(null) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var amountText by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("monthly") }
    var interval by remember { mutableStateOf(1) }
    var dueDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var isAutoPost by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reminderId) {
        if (reminderId != null) {
            viewModel.getReminder(reminderId)?.let { r ->
                loaded = r
                name = r.name
                accountId = r.accountId
                toAccountId = r.toAccountId
                categoryId = r.categoryId
                amountText = r.amount?.let { Money.formatPlain(it) } ?: ""
                frequency = r.frequency
                interval = r.frequencyInterval
                dueDate = r.nextDueDate
                isAutoPost = r.isAutoPost
            }
        }
    }

    com.endgamefinance.ui.components.EndgameScaffold(
        title = if (isEditing) "Edit reminder" else "New reminder",
        onBack = onDone,
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (e.g. Rent, Netflix)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        DropdownField(
            label = if (toAccountId != null) "From account" else "Account",
            options = accounts.map { it.id to it.name },
            selectedId = accountId,
            onSelect = { accountId = it },
        )

        DropdownField(
            label = "Transfer to (optional — for repayments & transfers)",
            options = listOf<Pair<String?, String>>(null to "Not a transfer") +
                accounts.filter { it.id != accountId }.map { it.id to it.name },
            selectedId = toAccountId,
            onSelect = { toAccountId = it; if (it != null) categoryId = null },
            nullLabel = "Not a transfer",
        )

        if (toAccountId == null) {
            val categoryGroups by viewModel.categoryGroups.collectAsState()
            com.endgamefinance.ui.components.CategoryPickerField(
                label = "Category (optional; an income category makes this expected income)",
                items = com.endgamefinance.ui.components.categoryPickItems(
                    categories, categoryGroups,
                ),
                selectedId = categoryId,
                onSelect = { categoryId = it },
                nullLabel = "None",
            )
        } else {
            Text(
                text = "Posts as a transfer: money moves from the source account to the " +
                    "destination (paying a card or loan reduces its debt).",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Amount (leave empty if it varies)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Repeats", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            frequencies.forEach { (value, label) ->
                FilterChip(
                    selected = frequency == value,
                    onClick = { frequency = value },
                    label = { Text(label) },
                )
            }
        }
        if (frequency != "once") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(
                    onClick = { if (interval > 1) interval-- },
                    enabled = interval > 1,
                ) { Text("−") }
                Text(
                    text = "Repeats ${ReminderRepository.frequencyLabel(frequency, interval)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(
                    onClick = { if (interval < 99) interval++ },
                ) { Text("+") }
            }
        }

        OutlinedTextField(
            value = DateFormat.getDateInstance().format(Date(dueDate)),
            onValueChange = {},
            readOnly = true,
            label = { Text("Next due date") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true },
            trailingIcon = {
                TextButton(onClick = { showDatePicker = true }) { Text("Set") }
            },
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-post", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Post to the ledger automatically when due (needs a fixed amount)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = isAutoPost, onCheckedChange = { isAutoPost = it })
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = {
                if (name.isBlank()) { error = "Name is required"; return@Button }
                val account = accountId ?: run { error = "Pick an account"; return@Button }
                val amount = amountText.trim().takeIf { it.isNotEmpty() }?.let {
                    val parsed = Money.parse(it)
                    if (parsed == null || parsed <= 0) {
                        error = "Amount is not a valid positive number"; return@Button
                    }
                    parsed
                }
                if (isAutoPost && amount == null) {
                    error = "Auto-post needs a fixed amount"; return@Button
                }
                viewModel.save(
                    existingId = reminderId,
                    name = name,
                    accountId = account,
                    toAccountId = toAccountId,
                    categoryId = categoryId,
                    amountCents = amount,
                    frequency = frequency,
                    frequencyInterval = interval,
                    nextDueDate = dueDate,
                    isAutoPost = isAutoPost,
                )
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isEditing) "Save" else "Create reminder") }

        if (isEditing) {
            OutlinedButton(
                onClick = {
                    loaded?.let { viewModel.delete(it) }
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete reminder", color = MaterialTheme.colorScheme.error) }
        }
    }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utc ->
                            // Interpret picked day as 9:00 local — a sensible reminder hour
                            val date = Instant.ofEpochMilli(utc)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                            dueDate = date.atTime(9, 0)
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
