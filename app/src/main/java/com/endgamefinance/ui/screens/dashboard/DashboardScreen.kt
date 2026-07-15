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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.endgamefinance.ui.components.IconCatalog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.BorderStroke
import com.endgamefinance.data.repo.SafeToSpend
import com.endgamefinance.ui.components.NetWorthChart
import com.endgamefinance.ui.theme.LocalMoneyColors
import com.endgamefinance.ui.theme.tabular
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAssistant: () -> Unit,
    onAddTransaction: () -> Unit,
    onOpenCalendar: () -> Unit = {},
    viewModel: DashboardViewModel =
        viewModel(factory = DashboardViewModel.factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    val moneyColors = LocalMoneyColors.current
    val context = LocalContext.current
    val dashPrefs = remember { DashboardPrefs(context.applicationContext) }
    val widgetOrder by dashPrefs.order.collectAsState()
    val hiddenWidgets by dashPrefs.hidden.collectAsState()
    var showWidgetEditor by remember { mutableStateOf(false) }

    com.endgamefinance.ui.components.EndgameScaffold(
        title = "Dashboard",
        actions = {
            IconButton(onClick = onOpenAssistant) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = "AI assistant")
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Search transactions")
            }
            IconButton(onClick = { showWidgetEditor = true }) {
                Icon(Icons.Filled.Tune, contentDescription = "Customize dashboard")
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
        val snapshots by viewModel.snapshots.collectAsState()
        val cashFlow by viewModel.cashFlow.collectAsState()
        val budgetSummary by viewModel.budgetSummary.collectAsState()
        val topCategories by viewModel.topCategories.collectAsState()
        val miniCalendar by viewModel.miniCalendar.collectAsState()

        // Widgets render in the user's chosen order; hidden ones are skipped.
        // Every widget wears its own accent (strip + wash + frame).
        val accents = com.endgamefinance.ui.theme.LocalWidgetAccents.current
        fun accentFor(key: String) =
            accents[DashboardPrefs.ALL.indexOf(key).coerceIn(0, accents.lastIndex)]
        widgetOrder.filterNot { it in hiddenWidgets }.forEach { key ->
            val accent = accentFor(key)
            when (key) {
                DashboardPrefs.SAFE_TO_SPEND ->
                    state.safeToSpend?.let { SafeToSpendCard(it, accent) }
                DashboardPrefs.NET_WORTH -> NetWorthCard(state.netWorth, snapshots, accent)
                DashboardPrefs.CALENDAR ->
                    MiniCalendarCard(miniCalendar, onOpenCalendar, accent)
                DashboardPrefs.CASH_FLOW ->
                    if (cashFlow.isNotEmpty()) CashFlowCard(cashFlow, accent)
                DashboardPrefs.BUDGET ->
                    if (budgetSummary.slices.isNotEmpty()) {
                        BudgetSummaryCard(budgetSummary, accent)
                    }
                DashboardPrefs.TOP_SPENDING ->
                    if (topCategories.isNotEmpty()) TopSpendingCard(topCategories, accent)
            }
        }

        // Contextual nudges are not configurable — they only appear when they matter
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
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
            ) {
                Text(
                    text = if (lastBackup == 0L) {
                        "You haven't made a backup yet. Tap to create an encrypted backup."
                    } else {
                        val days = (System.currentTimeMillis() - lastBackup) / 86_400_000L
                        "It's been $days days since your last backup. Tap to back up now."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }

        if (state.dueBillCount > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
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

    if (showWidgetEditor) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showWidgetEditor = false },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(bottom = Spacing.xl)) {
                Text(
                    "Dashboard widgets",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                )
                Text(
                    "Reorder with the arrows; toggle to hide. Nudges (backup, due " +
                        "bills) always show when relevant.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
                widgetOrder.forEachIndexed { index, key ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    ) {
                        Text(
                            DashboardPrefs.label(key),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { dashPrefs.move(key, up = true) },
                            enabled = index > 0,
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = { dashPrefs.move(key, up = false) },
                            enabled = index < widgetOrder.lastIndex,
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Move down",
                            )
                        }
                        Switch(
                            checked = key !in hiddenWidgets,
                            onCheckedChange = { dashPrefs.setVisible(key, it) },
                        )
                    }
                }
            }
        }
    }
}

/** Thin accent outline so each panel's frame matches its strip. */
private fun panelBorder(accent: androidx.compose.ui.graphics.Color) =
    BorderStroke(1.dp, accent.copy(alpha = 0.45f))

/** TWIN-TAP-style header: solid accent strip, black uppercase title. */
@Composable
private fun HeaderStrip(
    title: String,
    accent: androidx.compose.ui.graphics.Color,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent)
            .padding(horizontal = Spacing.md, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = com.endgamefinance.ui.components.onChipColor(accent),
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.titleMedium.tabular,
                color = com.endgamefinance.ui.components.onChipColor(accent),
            )
        }
    }
}

/** Subtle accent wash for widget bodies. */
private fun washBrush(accent: androidx.compose.ui.graphics.Color) =
    androidx.compose.ui.graphics.Brush.verticalGradient(
        0f to accent.copy(alpha = 0.10f),
        1f to accent.copy(alpha = 0.02f),
    )

@Composable
private fun NetWorthCard(
    netWorth: Long,
    snapshots: List<com.endgamefinance.data.db.entity.NetWorthSnapshot>,
    accent: androidx.compose.ui.graphics.Color,
) {
    val moneyColors = LocalMoneyColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        // Flat plate — no M3 tonal tint; the border does the work
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = panelBorder(accent),
    ) {
        Column {
            HeaderStrip("Net worth", accent, Money.format(netWorth))
            Column(modifier = Modifier.background(washBrush(accent))) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                NetWorthChart(snapshots = snapshots)
            }
        }
    }
}

@Composable
private fun CashFlowCard(
    cashFlow: List<com.endgamefinance.ui.components.MonthCashFlow>,
    accent: androidx.compose.ui.graphics.Color,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        // Flat plate — no M3 tonal tint; the border does the work
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = panelBorder(accent),
    ) {
        Column {
            HeaderStrip("Cash flow · last 6 months", accent)
            Column(modifier = Modifier.background(washBrush(accent))) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                com.endgamefinance.ui.components.CashFlowChart(months = cashFlow)
                Spacer(modifier = Modifier.padding(bottom = Spacing.sm))
            }
        }
    }
}

@Composable
private fun BudgetSummaryCard(
    budgetSummary: BudgetSummaryUi,
    accent: androidx.compose.ui.graphics.Color,
) {
    val moneyColors = LocalMoneyColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        // Flat plate — no M3 tonal tint; the border does the work
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = panelBorder(accent),
    ) {
        Column {
            HeaderStrip(
                "Budget · ${budgetSummary.monthLabel}",
                accent = accent,
                trailing = if (budgetSummary.allocatedTotal > 0) {
                    "${Money.format(budgetSummary.spentTotal)} of " +
                        Money.format(budgetSummary.allocatedTotal)
                } else null,
            )
            Column(modifier = Modifier.background(washBrush(accent))) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                com.endgamefinance.ui.components.SpendDonutChart(slices = budgetSummary.slices)
                Spacer(modifier = Modifier.height(Spacing.sm))
            }
        }
    }
}

@Composable
private fun TopSpendingCard(
    topCategories: List<TopCategory>,
    accent: androidx.compose.ui.graphics.Color,
) {
    val moneyColors = LocalMoneyColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        // Flat plate — no M3 tonal tint; the border does the work
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = panelBorder(accent),
    ) {
        Column {
            HeaderStrip("Top spending this month", accent)
            Column(
                modifier = Modifier
                    .background(washBrush(accent))
                    .padding(Spacing.md),
            ) {
            topCategories.forEach { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Black glyph on the expense color — owner's tile rule
                        com.endgamefinance.ui.components.CategoryIconTile(
                            icon = IconCatalog.get(category.icon) ?: Icons.Filled.Category,
                            background = moneyColors.loss,
                            tileSize = 26.dp,
                            iconSize = 16.dp,
                            modifier = Modifier.padding(end = Spacing.sm),
                        )
                        Text(category.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "${Money.format(category.amount)} · " +
                            "${(category.share * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.tabular,
                        color = moneyColors.loss,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            androidx.compose.ui.graphics.RectangleShape,
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(category.share.coerceIn(0f, 1f))
                            .height(5.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                androidx.compose.ui.graphics.RectangleShape,
                            ),
                    )
                }
            }
            }
        }
    }
}

/** Compact month grid: spend momentum tints + bill dots; opens the full calendar. */
@Composable
private fun MiniCalendarCard(
    ui: MiniCalendarUi,
    onOpenCalendar: () -> Unit,
    accent: androidx.compose.ui.graphics.Color,
) {
    val moneyColors = LocalMoneyColors.current
    if (ui.days.isEmpty()) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .clickable(onClick = onOpenCalendar),
        // Flat plate — no M3 tonal tint; the border does the work
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = panelBorder(accent),
    ) {
        Column {
            HeaderStrip("Calendar · ${ui.monthLabel}", accent, trailing = "tap to open")
            Column(
                modifier = Modifier
                    .background(washBrush(accent))
                    .padding(Spacing.md),
            ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val tertiary = MaterialTheme.colorScheme.tertiary
            val cells: List<MiniDay?> = List(ui.leadingBlanks) { null } + ui.days
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(1.dp)
                                .height(30.dp)
                                .background(
                                    when (day?.momentum) {
                                        1 -> moneyColors.gain.copy(alpha = 0.18f)
                                        2 -> tertiary.copy(alpha = 0.30f)
                                        3 -> moneyColors.loss.copy(alpha = 0.35f)
                                        else -> androidx.compose.ui.graphics.Color.Transparent
                                    },
                                    androidx.compose.ui.graphics.RectangleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = day.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (day.isToday) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    if (day.hasBill) {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .background(tertiary, androidx.compose.ui.graphics.RectangleShape),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun SafeToSpendCard(sts: SafeToSpend, accent: androidx.compose.ui.graphics.Color) {
    val moneyColors = LocalMoneyColors.current
    var expanded by remember { mutableStateOf(false) }

    // Hero treatment, loadout-screen style: dark plate, accent gradient wash,
    // the number itself wears the palette's acid primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .clickable { expanded = !expanded },
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        // The hero panel gets the accent frame
        border = panelBorder(accent),
    ) {
        Column {
        HeaderStrip("Safe to spend", accent)
        Column(
            // Full-plate wash: never fades to nothing, so collapsed and
            // expanded states read the same
            modifier = Modifier
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to accent.copy(alpha = 0.22f),
                        1f to accent.copy(alpha = 0.05f),
                    ),
                )
                .padding(Spacing.md),
        ) {
            Text(
                text = Money.format(sts.amountCents),
                style = MaterialTheme.typography.displaySmall.tabular,
                color = if (sts.amountCents >= 0) accent
                else MaterialTheme.colorScheme.error,
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
                // The exact bills behind that number, so it explains itself
                sts.countedBills.forEach { bill ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            bill.name + if (bill.overdue) " · overdue" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (bill.overdue) LocalMoneyColors.current.loss
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            Money.format(bill.amountCents),
                            style = MaterialTheme.typography.labelMedium.tabular,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
            color = if (positive) moneyColors.gain else moneyColors.loss,
        )
    }
}
