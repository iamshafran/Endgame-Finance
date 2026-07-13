package com.endgamefinance.ui.screens.accounts

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endgamefinance.data.db.entity.Account
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money

private val typeChoices = listOf(
    Account.TYPE_ASSET to "Asset",
    Account.TYPE_LIABILITY to "Liability",
    Account.TYPE_INVESTMENT to "Investment",
)

/**
 * Create (accountId == null) or edit an account. Type is frozen after creation
 * — changing it would silently re-interpret every past transaction.
 */
@Composable
fun AccountEditScreen(
    accountId: String?,
    onDone: () -> Unit,
    onReconcile: (String) -> Unit = {},
    viewModel: AccountsViewModel =
        viewModel(factory = AccountsViewModel.factory(LocalContext.current)),
) {
    val isEditing = accountId != null
    var loaded by remember { mutableStateOf<Account?>(null) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(Account.TYPE_ASSET) }
    var creditLimitText by remember { mutableStateOf("") }
    var principalText by remember { mutableStateOf("") }
    var startingBalanceText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(accountId) {
        if (accountId != null) {
            viewModel.getAccount(accountId)?.let { account ->
                loaded = account
                name = account.name
                type = account.type
                creditLimitText = account.creditLimit?.let { Money.formatPlain(it) } ?: ""
                principalText =
                    account.originalPrincipal?.let { Money.formatPlain(it) } ?: ""
            }
        }
    }

    com.endgamefinance.ui.components.EndgameScaffold(
        title = if (isEditing) "Edit account" else "New account",
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
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Type", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            typeChoices.forEach { (value, label) ->
                FilterChip(
                    selected = type == value,
                    onClick = { if (!isEditing) type = value },
                    label = { Text(label) },
                    enabled = !isEditing || type == value,
                )
            }
        }

        if (type == Account.TYPE_LIABILITY) {
            OutlinedTextField(
                value = creditLimitText,
                onValueChange = { creditLimitText = it },
                label = { Text("Credit limit (optional)") },
                supportingText = { Text("For revolving credit like cards — shows utilization") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = principalText,
                onValueChange = { principalText = it },
                label = { Text("Original loan amount (optional)") },
                supportingText = { Text("For loans — shows payoff progress") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (!isEditing) {
            OutlinedTextField(
                value = startingBalanceText,
                onValueChange = { startingBalanceText = it },
                label = {
                    Text(
                        if (type == Account.TYPE_LIABILITY) "Current debt (optional)"
                        else "Starting balance (optional)",
                    )
                },
                supportingText = {
                    Text(
                        if (type == Account.TYPE_LIABILITY) {
                            "Recorded as a visible Starting Balance transaction. " +
                                "Overpaid? Enter a negative debt."
                        } else {
                            "Recorded as a visible Starting Balance transaction. " +
                                "Overdrawn? Enter a negative amount."
                        },
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    error = "Name is required"
                    return@Button
                }
                val creditLimit = creditLimitText.trim()
                    .takeIf { it.isNotEmpty() && type == Account.TYPE_LIABILITY }
                    ?.let {
                        Money.parse(it) ?: run {
                            error = "Credit limit is not a valid amount"
                            return@Button
                        }
                    }
                val originalPrincipal = principalText.trim()
                    .takeIf { it.isNotEmpty() && type == Account.TYPE_LIABILITY }
                    ?.let {
                        Money.parse(it) ?: run {
                            error = "Original loan amount is not a valid amount"
                            return@Button
                        }
                    }
                if (isEditing) {
                    val base = loaded ?: return@Button
                    viewModel.updateAccount(
                        base.copy(
                            name = trimmed,
                            creditLimit = creditLimit,
                            originalPrincipal = originalPrincipal,
                        ),
                    )
                } else {
                    val entered = startingBalanceText.trim()
                        .takeIf { it.isNotEmpty() }
                        ?.let {
                            Money.parse(it) ?: run {
                                error = "Starting balance is not a valid amount"
                                return@Button
                            }
                        } ?: 0L
                    // Liability field reads "debt as a positive number" → negate to
                    // the signed convention; a negative debt entry = overpayment credit.
                    val initialBalance =
                        if (type == Account.TYPE_LIABILITY) -entered else entered
                    viewModel.createAccount(trimmed, type, creditLimit, originalPrincipal, initialBalance)
                }
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isEditing) "Save" else "Create account")
        }

        if (isEditing) {
            OutlinedButton(
                onClick = { accountId?.let(onReconcile) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reconcile against a statement")
            }
            OutlinedButton(
                onClick = {
                    loaded?.let { viewModel.archiveAccount(it) }
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Archive account", color = MaterialTheme.colorScheme.error)
            }
            Text(
                text = "Archiving hides the account but keeps all its history.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    }
}
