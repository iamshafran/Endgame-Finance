package com.endgamefinance.ui.screens.reconcile

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.endgamefinance.data.db.model.TransactionListItem
import com.endgamefinance.data.repo.LedgerRepository
import com.endgamefinance.ui.components.EmptyState
import com.endgamefinance.ui.theme.LocalMoneyColors
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReconcileUiState(
    val accountName: String = "",
    val clearedBalance: Long = 0,
    val uncleared: List<TransactionListItem> = emptyList(),
    val cleared: List<TransactionListItem> = emptyList(),
)

class ReconcileViewModel(
    private val db: EndgameDatabase,
    private val accountId: String,
) : ViewModel() {

    private val repo = LedgerRepository(db)

    val uiState: StateFlow<ReconcileUiState> =
        combine(
            db.transactionDao().observeFiltered(
                accountId = accountId, payeeQuery = "", categoryId = null,
                minCents = null, maxCents = null, startMs = null, endMs = null,
            ),
            db.accountDao().observeClearedBalance(accountId),
            db.accountDao().observeActive(),
        ) { transactions, clearedBalance, accounts ->
            ReconcileUiState(
                accountName = accounts.firstOrNull { it.id == accountId }?.name ?: "",
                clearedBalance = clearedBalance,
                uncleared = transactions.filter { !it.isCleared },
                cleared = transactions.filter { it.isCleared },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReconcileUiState())

    fun setCleared(transactionId: String, cleared: Boolean) {
        viewModelScope.launch { repo.setCleared(transactionId, cleared) }
    }

    companion object {
        fun factory(context: Context, accountId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ReconcileViewModel(DatabaseProvider.get(context), accountId) }
            }
    }
}

@Composable
fun ReconcileScreen(
    accountId: String,
    onBack: (() -> Unit)? = null,
    viewModel: ReconcileViewModel = viewModel(
        factory = ReconcileViewModel.factory(LocalContext.current, accountId),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val moneyColors = LocalMoneyColors.current

    com.endgamefinance.ui.components.EndgameScaffold(
        title = "Reconcile",
        onBack = onBack,
    ) { innerPadding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        item(key = "header") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(
                        "Reconcile ${state.accountName}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Cleared balance",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                    Text(
                        Money.format(state.clearedBalance),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "This should match your bank statement. Check off each " +
                            "transaction as you find it on the statement.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.uncleared.isNotEmpty()) {
            item(key = "uncleared_header") {
                Text(
                    "To reconcile (${state.uncleared.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(
                        start = Spacing.md, end = Spacing.md,
                        top = Spacing.sm, bottom = Spacing.xs,
                    ),
                )
            }
            items(state.uncleared, key = { "u_${it.id}" }) { item ->
                ReconcileRow(item) { viewModel.setCleared(item.id, true) }
                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
            }
        }

        if (state.cleared.isNotEmpty()) {
            item(key = "cleared_header") {
                Text(
                    "Cleared (${state.cleared.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        start = Spacing.md, end = Spacing.md,
                        top = Spacing.md, bottom = Spacing.xs,
                    ),
                )
            }
            items(state.cleared, key = { "c_${it.id}" }) { item ->
                ReconcileRow(item) { viewModel.setCleared(item.id, false) }
                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
            }
        }

        if (state.uncleared.isEmpty() && state.cleared.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Filled.Check,
                    title = "Nothing to reconcile",
                    body = "This account has no transactions yet. Once it does, check " +
                        "them off here against your bank statement.",
                )
            }
        }
    }
    }
}

@Composable
private fun ReconcileRow(item: TransactionListItem, onToggle: () -> Unit) {
    val moneyColors = LocalMoneyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Checkbox(checked = item.isCleared, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(item.payee, style = MaterialTheme.typography.bodyLarge)
            Text(
                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(item.timestamp)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = when (item.type) {
                "income" -> "+${Money.format(item.totalAmount)}"
                "expense" -> "−${Money.format(item.totalAmount)}"
                else -> Money.format(item.totalAmount)
            },
            style = MaterialTheme.typography.titleMedium,
            color = when (item.type) {
                "income" -> moneyColors.gain
                "expense" -> moneyColors.loss
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
