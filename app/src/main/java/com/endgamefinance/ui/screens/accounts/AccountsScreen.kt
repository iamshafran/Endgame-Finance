package com.endgamefinance.ui.screens.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endgamefinance.data.db.entity.Account
import com.endgamefinance.data.db.model.AccountWithBalance
import com.endgamefinance.ui.theme.LocalMoneyColors
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money

private val typeOrder = listOf(
    Account.TYPE_ASSET to "Assets",
    Account.TYPE_LIABILITY to "Liabilities",
    Account.TYPE_INVESTMENT to "Investments",
)

@Composable
fun AccountsScreen(
    onAddAccount: () -> Unit,
    onEditAccount: (String) -> Unit,
    viewModel: AccountsViewModel =
        viewModel(factory = AccountsViewModel.factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "net_worth") {
                NetWorthHeader(netWorthCents = state.netWorthCents)
            }
            typeOrder.forEach { (type, sectionTitle) ->
                val section = state.accounts.filter { it.account.type == type }
                if (section.isNotEmpty()) {
                    item(key = "header_$type") {
                        Text(
                            text = sectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                start = Spacing.md, end = Spacing.md,
                                top = Spacing.lg, bottom = Spacing.xs,
                            ),
                        )
                    }
                    itemsIndexed(section, key = { _, item -> item.account.id }) { index, item ->
                        AccountRow(item = item, onClick = { onEditAccount(item.account.id) })
                        if (index < section.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                        }
                    }
                }
            }
            if (state.accounts.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No accounts yet. Tap + to add your first account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onAddAccount,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.md),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add account")
        }
    }
}

@Composable
private fun NetWorthHeader(netWorthCents: Long) {
    val moneyColors = LocalMoneyColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = "Net worth",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Money.format(netWorthCents),
                style = MaterialTheme.typography.headlineMedium,
                color = if (netWorthCents >= 0) moneyColors.gain else moneyColors.loss,
            )
        }
    }
}

@Composable
private fun AccountRow(item: AccountWithBalance, onClick: () -> Unit) {
    val moneyColors = LocalMoneyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = item.account.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            item.account.creditLimit?.let { limit ->
                Text(
                    text = "Limit ${Money.format(limit)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = Money.format(item.balance),
            style = MaterialTheme.typography.titleMedium,
            color = if (item.balance >= 0) moneyColors.gain else moneyColors.loss,
        )
    }
}
