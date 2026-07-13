package com.endgamefinance.ui.screens.budget

import android.content.Context
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.entity.Envelope
import com.endgamefinance.data.db.entity.EnvelopeTransfer
import com.endgamefinance.data.repo.EnvelopeRepository
import com.endgamefinance.ui.components.DropdownField
import com.endgamefinance.ui.theme.LocalMoneyColors
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EnvelopeUi(
    val envelope: Envelope,
    val backingAccountName: String?,
)

data class BackingSummary(
    val accountName: String,
    val accountBalance: Long,
    val envelopeTotal: Long,
) {
    val unallocated: Long get() = accountBalance - envelopeTotal
}

data class EnvelopesUiState(
    val rows: List<EnvelopeUi> = emptyList(),
    val summaries: List<BackingSummary> = emptyList(),
)

class EnvelopesViewModel(private val db: EndgameDatabase) : ViewModel() {

    private val repo = EnvelopeRepository(db)

    val uiState: StateFlow<EnvelopesUiState> =
        combine(
            db.envelopeDao().observeAll(),
            db.accountDao().observeActiveWithBalances(),
        ) { envelopes, accounts ->
            val accountsById = accounts.associateBy { it.account.id }
            val rows = envelopes.map { env ->
                EnvelopeUi(env, env.linkedAccountId?.let { accountsById[it]?.account?.name })
            }
            val summaries = envelopes
                .filter { it.linkedAccountId != null }
                .groupBy { it.linkedAccountId!! }
                .mapNotNull { (accountId, envs) ->
                    accountsById[accountId]?.let { acct ->
                        BackingSummary(
                            accountName = acct.account.name,
                            accountBalance = acct.balance,
                            envelopeTotal = envs.sumOf { it.currentAmount },
                        )
                    }
                }
            EnvelopesUiState(rows = rows, summaries = summaries)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EnvelopesUiState())

    val accounts = db.accountDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    fun transfersFor(envelopeId: String) = db.envelopeDao().observeTransfersFor(envelopeId)

    fun create(name: String, targetCents: Long?, accountId: String?) {
        viewModelScope.launch { repo.create(name, targetCents, accountId) }
    }

    fun update(envelope: Envelope) {
        viewModelScope.launch { repo.update(envelope) }
    }

    fun delete(envelope: Envelope) {
        viewModelScope.launch {
            try {
                repo.delete(envelope)
            } catch (e: IllegalArgumentException) {
                _message.value = e.message
            }
        }
    }

    fun transfer(fromId: String?, toId: String?, amountCents: Long) {
        viewModelScope.launch {
            try {
                repo.transfer(fromId, toId, amountCents)
            } catch (e: IllegalArgumentException) {
                _message.value = e.message
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { EnvelopesViewModel(DatabaseProvider.get(context)) }
        }
    }
}

@Composable
fun EnvelopesTab(
    viewModel: EnvelopesViewModel =
        viewModel(factory = EnvelopesViewModel.factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val message by viewModel.message.collectAsState()
    var editTarget by remember { mutableStateOf<Envelope?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var detailTarget by remember { mutableStateOf<Envelope?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.summaries, key = { "summary_${it.accountName}" }) { summary ->
            val moneyColors = LocalMoneyColors.current
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                colors = if (summary.unallocated < 0) {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    )
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(summary.accountName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Balance ${Money.format(summary.accountBalance)} · " +
                            "in envelopes ${Money.format(summary.envelopeTotal)} · " +
                            "unallocated ${Money.format(summary.unallocated)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (summary.unallocated < 0) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (summary.unallocated < 0) {
                        Text(
                            "Envelopes promise more than this account holds.",
                            style = MaterialTheme.typography.labelMedium,
                            color = moneyColors.loss,
                        )
                    }
                }
            }
        }
        items(state.rows, key = { it.envelope.id }) { row ->
            EnvelopeRow(
                row = row,
                onClick = { detailTarget = row.envelope },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
        }
        item(key = "new") {
            OutlinedButton(
                onClick = { showNew = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
            ) { Text("New envelope") }
        }
        if (state.rows.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Envelopes are virtual savings buckets inside your real accounts — " +
                        "e.g. an Emergency Fund living in Checking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { viewModel.consumeMessage() },
            title = { Text("Can't do that") },
            text = { Text(it) },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeMessage() }) { Text("OK") }
            },
        )
    }

    if (showNew) {
        EnvelopeEditDialog(
            existing = null,
            accounts = accounts.map { it.id to it.name },
            onSave = { name, target, accountId ->
                viewModel.create(name, target, accountId)
                showNew = false
            },
            onDelete = null,
            onDismiss = { showNew = false },
        )
    }
    editTarget?.let { env ->
        EnvelopeEditDialog(
            existing = env,
            accounts = accounts.map { it.id to it.name },
            onSave = { name, target, accountId ->
                viewModel.update(env.copy(name = name, targetAmount = target, linkedAccountId = accountId))
                editTarget = null
            },
            onDelete = { viewModel.delete(env); editTarget = null },
            onDismiss = { editTarget = null },
        )
    }
    detailTarget?.let { env ->
        // Re-read latest state so the dialog tracks balance changes live
        val current = state.rows.firstOrNull { it.envelope.id == env.id }?.envelope ?: env
        EnvelopeDetailDialog(
            envelope = current,
            others = state.rows.map { it.envelope }.filter { it.id != env.id },
            viewModel = viewModel,
            onEdit = { detailTarget = null; editTarget = current },
            onDismiss = { detailTarget = null },
        )
    }
}

@Composable
private fun EnvelopeRow(row: EnvelopeUi, onClick: () -> Unit) {
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
            Column {
                Text(row.envelope.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    row.backingAccountName?.let { "in $it" } ?: "not linked to an account",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                row.envelope.targetAmount?.let {
                    "${Money.format(row.envelope.currentAmount)} / ${Money.format(it)}"
                } ?: Money.format(row.envelope.currentAmount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        row.envelope.targetAmount?.let { target ->
            if (target > 0) {
                LinearProgressIndicator(
                    progress = { (row.envelope.currentAmount.toFloat() / target).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun EnvelopeEditDialog(
    existing: Envelope?,
    accounts: List<Pair<String, String>>,
    onSave: (name: String, targetCents: Long?, accountId: String?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var targetText by remember {
        mutableStateOf(existing?.targetAmount?.let { Money.format(it).replace("$", "") } ?: "")
    }
    var accountId by remember { mutableStateOf(existing?.linkedAccountId) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New envelope" else "Edit envelope") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text("Target amount (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                DropdownField(
                    label = "Backing account",
                    options = listOf<Pair<String?, String>>(null to "None") + accounts,
                    selectedId = accountId,
                    onSelect = { accountId = it },
                    nullLabel = "None",
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
                    if (name.isBlank()) { error = "Name is required"; return@Button }
                    val target = targetText.trim().takeIf { it.isNotEmpty() }?.let {
                        val parsed = Money.parse(it)
                        if (parsed == null || parsed <= 0) {
                            error = "Target is not a valid amount"; return@Button
                        }
                        parsed
                    }
                    onSave(name.trim(), target, accountId)
                },
            ) { Text("Save") }
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

@Composable
private fun EnvelopeDetailDialog(
    envelope: Envelope,
    others: List<Envelope>,
    viewModel: EnvelopesViewModel,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var direction by remember { mutableStateOf("add") }
    var moveTargetId by remember { mutableStateOf<String?>(null) }
    var amountText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val transfers by remember(envelope.id) { viewModel.transfersFor(envelope.id) }
        .collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(envelope.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    "Holding ${Money.format(envelope.currentAmount)}" +
                        (envelope.targetAmount?.let { " of ${Money.format(it)} target" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = direction == "add",
                        onClick = { direction = "add" }, label = { Text("Add") })
                    FilterChip(selected = direction == "withdraw",
                        onClick = { direction = "withdraw" }, label = { Text("Withdraw") })
                    if (others.isNotEmpty()) {
                        FilterChip(selected = direction == "move",
                            onClick = { direction = "move" }, label = { Text("Move") })
                    }
                }
                if (direction == "move") {
                    DropdownField(
                        label = "Move to",
                        options = others.map { it.id to it.name },
                        selectedId = moveTargetId,
                        onSelect = { moveTargetId = it },
                    )
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (transfers.isNotEmpty()) {
                    Text("Recent moves", style = MaterialTheme.typography.labelLarge)
                    transfers.take(5).forEach { t ->
                        TransferLine(t, envelope, others)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cents = Money.parse(amountText.trim())
                    if (cents == null || cents <= 0) {
                        error = "Amount must be a positive number"; return@Button
                    }
                    when (direction) {
                        "add" -> viewModel.transfer(null, envelope.id, cents)
                        "withdraw" -> viewModel.transfer(envelope.id, null, cents)
                        "move" -> {
                            val target = moveTargetId ?: run {
                                error = "Pick a destination envelope"; return@Button
                            }
                            viewModel.transfer(envelope.id, target, cents)
                        }
                    }
                    amountText = ""
                    error = null
                },
            ) { Text("Transfer") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun TransferLine(t: EnvelopeTransfer, self: Envelope, others: List<Envelope>) {
    fun nameOf(id: String?): String = when (id) {
        null -> "Unallocated"
        self.id -> self.name
        else -> others.firstOrNull { it.id == id }?.name ?: "(deleted)"
    }
    Text(
        text = "${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(t.timestamp))} · " +
            "${nameOf(t.fromEnvelopeId)} → ${nameOf(t.toEnvelopeId)} · ${Money.format(t.amount)}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
