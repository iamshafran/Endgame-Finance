package com.endgamefinance.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endgamefinance.data.repo.SafeToSpend
import com.endgamefinance.ui.components.NetWorthChart
import com.endgamefinance.ui.theme.LocalMoneyColors
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money
import java.text.DateFormat
import java.util.Date

@Composable
fun DashboardScreen(
    onSearch: () -> Unit,
    viewModel: DashboardViewModel =
        viewModel(factory = DashboardViewModel.factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    val moneyColors = LocalMoneyColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onSearch) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Search transactions",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        state.safeToSpend?.let { SafeToSpendCard(it) }

        val snapshots by viewModel.snapshots.collectAsState()
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Net worth", style = MaterialTheme.typography.titleMedium)
                    Text(
                        Money.format(state.netWorth),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.netWorth >= 0) moneyColors.gain else moneyColors.loss,
                    )
                }
                NetWorthChart(snapshots = snapshots)
            }
        }

        if (state.dueBillCount > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            ) {
                Text(
                    text = "${state.dueBillCount} bill${if (state.dueBillCount > 1) "s" else ""} " +
                        "waiting on the Reminders tab",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun SafeToSpendCard(sts: SafeToSpend) {
    val moneyColors = LocalMoneyColors.current
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = "Safe to spend",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Money.format(sts.amountCents),
                style = MaterialTheme.typography.displaySmall,
                color = if (sts.amountCents >= 0) moneyColors.gain else moneyColors.loss,
            )
            Text(
                text = sts.nextIncomeDate?.let {
                    "until your next income on " +
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                } ?: "over the next 30 days (no income scheduled)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
                BreakdownRow("Cash in asset accounts", sts.liquidBalances, positive = true)
                BreakdownRow("Set aside in envelopes", -sts.envelopeFunds, positive = false)
                BreakdownRow("Bills before next income", -sts.upcomingBills, positive = false)
                BreakdownRow(
                    "Unspent budget commitments",
                    -sts.remainingBudgetCommitments,
                    positive = false,
                )
                Text(
                    text = "Formula: docs/safe-to-spend.md — cash you have, minus everything " +
                        "already promised. Credit card limits never count.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            } else {
                Text(
                    text = "Tap for the breakdown",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (sts.uncountedVariableBills.isNotEmpty()) {
                Text(
                    text = "Not counted (amount varies): " +
                        sts.uncountedVariableBills.joinToString(", "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, signedCents: Long, positive: Boolean) {
    val moneyColors = LocalMoneyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = (if (positive) "" else "−") + Money.format(kotlin.math.abs(signedCents)),
            style = MaterialTheme.typography.bodyMedium,
            color = if (positive) moneyColors.gain else MaterialTheme.colorScheme.onSurface,
        )
    }
}
