package com.endgamefinance.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.endgamefinance.ui.components.IconCatalog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
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
import com.endgamefinance.ui.theme.tabular
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money
import java.text.DateFormat
import java.util.Date

@Composable
fun DashboardScreen(
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAssistant: () -> Unit,
    onAddTransaction: () -> Unit,
    viewModel: DashboardViewModel =
        viewModel(factory = DashboardViewModel.factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    val moneyColors = LocalMoneyColors.current

    com.endgamefinance.ui.components.EndgameScaffold(
        title = "Dashboard",
        actions = {
            IconButton(onClick = onOpenAssistant) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = "AI assistant")
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Search transactions")
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransaction) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        },
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState()),
    ) {
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

        val cashFlow by viewModel.cashFlow.collectAsState()
        if (cashFlow.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            ) {
                Column {
                    Text(
                        "Cash flow · last 6 months",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(
                            start = Spacing.md, end = Spacing.md,
                            top = Spacing.md, bottom = Spacing.xs,
                        ),
                    )
                    com.endgamefinance.ui.components.CashFlowChart(months = cashFlow)
                    androidx.compose.foundation.layout.Spacer(
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                }
            }
        }

        val budgetSummary by viewModel.budgetSummary.collectAsState()
        if (budgetSummary.slices.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = Spacing.md, end = Spacing.md,
                                top = Spacing.md, bottom = Spacing.xs,
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Budget · ${budgetSummary.monthLabel}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (budgetSummary.allocatedTotal > 0) {
                            Text(
                                "${Money.format(budgetSummary.spentTotal)} of " +
                                    Money.format(budgetSummary.allocatedTotal),
                                style = MaterialTheme.typography.labelMedium.tabular,
                                color = if (
                                    budgetSummary.spentTotal > budgetSummary.allocatedTotal
                                ) moneyColors.loss else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    com.endgamefinance.ui.components.SpendDonutChart(
                        slices = budgetSummary.slices,
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                }
            }
        }

        val topCategories by viewModel.topCategories.collectAsState()
        if (topCategories.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(
                        "Top spending this month",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                    topCategories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = IconCatalog.get(category.icon)
                                        ?: Icons.Filled.Category,
                                    contentDescription = null,
                                    // Category icons take the accent role, not primary
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier
                                        .padding(end = Spacing.sm)
                                        .size(20.dp),
                                )
                                Text(category.displayName,
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                "${Money.format(category.amount)} · " +
                                    "${(category.share * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium.tabular,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(3.dp),
                                ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(category.share.coerceIn(0f, 1f))
                                    .height(5.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(3.dp),
                                    ),
                            )
                        }
                    }
                }
            }
        }

        val context = LocalContext.current
        val nudgeDue = remember { com.endgamefinance.security.BackupPrefs.isNudgeDue(context) }
        if (nudgeDue) {
            val lastBackup = remember {
                com.endgamefinance.security.BackupPrefs.lastBackupAt(context)
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    .clickable(onClick = onOpenSettings),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Text(
                    text = if (lastBackup == 0L) {
                        "You haven't made a backup yet. Tap to create an encrypted backup."
                    } else {
                        val days = (System.currentTimeMillis() - lastBackup) / 86_400_000L
                        "It's been $days days since your last backup. Tap to back up now."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(Spacing.md),
                )
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
        // Breathing room so the FAB never covers the last card
        Spacer(modifier = Modifier.height(Spacing.fabClearance))
    }
    }
}

@Composable
private fun SafeToSpendCard(sts: SafeToSpend) {
    val moneyColors = LocalMoneyColors.current
    var expanded by remember { mutableStateOf(false) }

    // Hero treatment: the app's most important number owns the strongest surface
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .clickable { expanded = !expanded },
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = "Safe to spend",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = Money.format(sts.amountCents),
                style = MaterialTheme.typography.displaySmall.tabular,
                color = if (sts.amountCents >= 0) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.error,
            )
            Text(
                text = sts.nextIncomeDate?.let {
                    "until your next income on " +
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                } ?: "over the next 30 days (no income scheduled)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
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
