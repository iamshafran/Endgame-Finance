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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endgamefinance.data.repo.SafeToSpend
import com.endgamefinance.ui.components.NetWorthChart
import com.endgamefinance.ui.theme.LocalMoneyColors
import com.endgamefinance.ui.theme.ThemePalette
import com.endgamefinance.ui.theme.colorSchemeFor
import com.endgamefinance.ui.theme.moneyColorsFor
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
        titleStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
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

        DashboardCardTheme {
            // Widgets render in the user's chosen order; hidden ones are skipped
            widgetOrder.filterNot { it in hiddenWidgets }.forEach { key ->
                when (key) {
                    DashboardPrefs.SAFE_TO_SPEND -> state.safeToSpend?.let { SafeToSpendCard(it) }
                    DashboardPrefs.NET_WORTH -> NetWorthCard(state.netWorth, snapshots)
                    DashboardPrefs.CALENDAR ->
                        MiniCalendarCard(miniCalendar, onOpenCalendar)
                    DashboardPrefs.CASH_FLOW -> if (cashFlow.isNotEmpty()) CashFlowCard(cashFlow)
                    DashboardPrefs.BUDGET ->
                        if (budgetSummary.slices.isNotEmpty()) BudgetSummaryCard(budgetSummary)
                    DashboardPrefs.TOP_SPENDING ->
                        if (topCategories.isNotEmpty()) TopSpendingCard(topCategories)
                }
            }

            // Contextual nudges are not configurable — they only appear when they matter
            val nudgeDue = remember {
                com.endgamefinance.security.BackupPrefs.isNudgeDue(context)
            }
            if (nudgeDue) {
                val lastBackup = remember {
                    com.endgamefinance.security.BackupPrefs.lastBackupAt(context)
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        .clickable(onClick = onOpenSettings),
                    colors = CardDefaults.cardColors(
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
                        text = "${state.dueBillCount} bill" +
                            "${if (state.dueBillCount > 1) "s" else ""} " +
                            "waiting on the Reminders tab",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
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

/**
 * Fixed lime-on-black skin for the dashboard's widget cards — reuses the
 * app's own Marathoner palette (already lime-on-near-black) pushed to true
 * OLED black, so cards read as bold, high-contrast tiles regardless of the
 * app's selected theme/palette. Only wraps the card column, not the page
 * canvas or app bar, which keep the ambient theme.
 */
@Composable
private fun DashboardCardTheme(content: @Composable () -> Unit) {
    val base = colorSchemeFor(ThemePalette.MARATHON, dark = true)
    val blackScheme = base.copy(
        background = Color(0xFF000000),
        surface = Color(0xFF000000),
        surfaceDim = Color(0xFF000000),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0D0D0D),
        surfaceContainer = Color(0xFF141414),
        surfaceContainerHigh = Color(0xFF1D1D1D),
        surfaceContainerHighest = Color(0xFF262626),
    )
    val moneyColors = moneyColorsFor(ThemePalette.MARATHON, dark = true)
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    MaterialTheme(colorScheme = blackScheme, typography = typography, shapes = shapes) {
        CompositionLocalProvider(LocalMoneyColors provides moneyColors, content = content)
    }
}

@Composable
private fun NetWorthCard(
    netWorth: Long,
    snapshots: List<com.endgamefinance.data.db.entity.NetWorthSnapshot>,
) {
    val moneyColors = LocalMoneyColors.current
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
                Text(
                    "Net Worth",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    Money.format(netWorth),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (netWorth >= 0) moneyColors.gain else moneyColors.loss,
                )
            }
            NetWorthChart(snapshots = snapshots)
        }
    }
}

@Composable
private fun CashFlowCard(cashFlow: List<com.endgamefinance.ui.components.MonthCashFlow>) {
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
            Spacer(modifier = Modifier.padding(bottom = Spacing.sm))
        }
    }
}

@Composable
private fun BudgetSummaryCard(budgetSummary: BudgetSummaryUi) {
    val moneyColors = LocalMoneyColors.current
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
                        text = androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(
                                androidx.compose.ui.text.SpanStyle(color = moneyColors.loss),
                            ) { append(Money.format(budgetSummary.spentTotal)) }
                            append(" of ${Money.format(budgetSummary.allocatedTotal)}")
                        },
                        style = MaterialTheme.typography.labelMedium.tabular,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            com.endgamefinance.ui.components.SpendDonutChart(slices = budgetSummary.slices)
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
    }
}

@Composable
private fun TopSpendingCard(topCategories: List<TopCategory>) {
    val moneyColors = LocalMoneyColors.current
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

/** Compact month grid: spend momentum tints + bill dots; opens the full calendar. */
@Composable
private fun MiniCalendarCard(ui: MiniCalendarUi, onOpenCalendar: () -> Unit) {
    val moneyColors = LocalMoneyColors.current
    if (ui.days.isEmpty()) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .clickable(onClick = onOpenCalendar),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Calendar · ${ui.monthLabel}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Tap to open",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                                    RoundedCornerShape(6.dp),
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
                                                .background(tertiary, CircleShape),
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

@Composable
private fun SafeToSpendCard(sts: SafeToSpend) {
    var expanded by remember { mutableStateOf(false) }

    // Hero block: the app's most important number owns the boldest surface —
    // full primary (lime), not the softer primaryContainer, per the redesign.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Safe to Spend",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "Collapse breakdown" else "Show breakdown",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = Money.format(sts.amountCents),
                style = MaterialTheme.typography.displaySmall.tabular.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = if (sts.amountCents >= 0) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.error,
            )
            Text(
                text = sts.nextIncomeDate?.let {
                    "Until your next income on " +
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                } ?: "Over the next 30 days (no income scheduled)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
            if (sts.uncountedVariableBills.isNotEmpty()) {
                Text(
                    text = "Not counted (amount varies): " +
                        sts.uncountedVariableBills.joinToString(", "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }

    // Breakdown block: a separate near-black card beneath the hero, matching
    // the design's two-tile stack; only present once the hero is tapped open.
    if (expanded) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                BreakdownRow("Cash in Asset Accounts", sts.liquidBalances, positive = true)
                BreakdownRow("Set aside in Envelopes", -sts.envelopeFunds, positive = false)
                BreakdownRow("Bills before next Income", -sts.upcomingBills, positive = false)
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
