package com.endgamefinance.ui.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endgamefinance.ui.components.IconCatalog
import com.endgamefinance.ui.theme.LocalMoneyColors
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money
import com.endgamefinance.util.MonthUtil
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date

@Composable
fun ReportsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: ReportsViewModel =
        viewModel(factory = ReportsViewModel.factory(LocalContext.current)),
) {
    var section by remember { mutableStateOf("range") }
    com.endgamefinance.ui.components.EndgameScaffold(
        title = "Reports",
        onBack = onBack,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                FilterChip(selected = section == "range",
                    onClick = { section = "range" }, label = { Text("Date range") })
                FilterChip(selected = section == "yoy",
                    onClick = { section = "yoy" }, label = { Text("Year over year") })
                FilterChip(selected = section == "merchants",
                    onClick = { section = "merchants" }, label = { Text("Merchants") })
            }
            when (section) {
                "range" -> RangeTab(viewModel)
                "yoy" -> YoyTab(viewModel)
                else -> MerchantsTab(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeTab(viewModel: ReportsViewModel) {
    val report by viewModel.rangeReport.collectAsState()
    val moneyColors = LocalMoneyColors.current
    var pickTarget by remember { mutableStateOf<String?>(null) } // "start" | "end"
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "pickers") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(horizontal = Spacing.md),
            ) {
                OutlinedTextField(
                    value = dateFormat.format(Date(report.startMs)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("From") },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { pickTarget = "start" },
                    trailingIcon = {
                        TextButton(onClick = { pickTarget = "start" }) { Text("Set") }
                    },
                )
                OutlinedTextField(
                    value = dateFormat.format(Date(report.endMs - 1)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("To (inclusive)") },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { pickTarget = "end" },
                    trailingIcon = {
                        TextButton(onClick = { pickTarget = "end" }) { Text("Set") }
                    },
                )
            }
        }
        item(key = "totals") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    TotalLine("Income", Money.format(report.income), moneyColors.gain)
                    TotalLine("Spending", Money.format(report.spending), moneyColors.loss)
                    TotalLine(
                        "Net",
                        Money.format(report.net),
                        if (report.net >= 0) moneyColors.gain else moneyColors.loss,
                    )
                }
            }
        }
        items(report.rows, key = { it.categoryId ?: "uncategorized" }) { row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = IconCatalog.get(row.icon) ?: Icons.Filled.Category,
                            contentDescription = null,
                            tint = if (row.icon != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(end = Spacing.sm),
                        )
                        Text(row.displayName, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        "${Money.format(row.spent)} · ${(row.share * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // Proportion bar: single-hue share of total spending
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            androidx.compose.ui.graphics.RectangleShape,
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(row.share.coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                androidx.compose.ui.graphics.RectangleShape,
                            ),
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
        }
        if (report.rows.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No spending in this range.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.lg),
                )
            }
        }
    }

    pickTarget?.let { target ->
        val initial = if (target == "start") report.startMs else report.endMs - 1
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { utc ->
                            val date = Instant.ofEpochMilli(utc)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                            val zone = ZoneId.systemDefault()
                            if (target == "start") {
                                viewModel.setRange(
                                    date.atStartOfDay(zone).toInstant().toEpochMilli(),
                                    report.endMs,
                                )
                            } else {
                                viewModel.setRange(
                                    report.startMs,
                                    date.plusDays(1).atStartOfDay(zone)
                                        .toInstant().toEpochMilli(),
                                )
                            }
                        }
                        pickTarget = null
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickTarget = null }) { Text("Cancel") }
            },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun TotalLine(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun MerchantsTab(viewModel: ReportsViewModel) {
    val merchants by viewModel.merchants.collectAsState()
    val sort by viewModel.merchantSort.collectAsState()
    val report by viewModel.rangeReport.collectAsState()
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val moneyColors = LocalMoneyColors.current

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "info") {
            Text(
                text = "Expenses from ${dateFormat.format(Date(report.startMs))} " +
                    "to ${dateFormat.format(Date(report.endMs - 1))} — " +
                    "change the range on the Date range tab.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }
        item(key = "sort") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(Spacing.md),
            ) {
                FilterChip(selected = sort == "total",
                    onClick = { viewModel.setMerchantSort("total") },
                    label = { Text("By spend") })
                FilterChip(selected = sort == "visits",
                    onClick = { viewModel.setMerchantSort("visits") },
                    label = { Text("By visits") })
            }
        }
        items(merchants, key = { it.payee }) { merchant ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(merchant.payee, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${merchant.visits} visit${if (merchant.visits > 1) "s" else ""} · " +
                            "avg ${Money.format(merchant.total / merchant.visits)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    Money.format(merchant.total),
                    style = MaterialTheme.typography.titleMedium,
                    color = moneyColors.loss,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
        }
        if (merchants.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No merchant spending in this range.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.lg),
                )
            }
        }
    }
}

@Composable
private fun YoyTab(viewModel: ReportsViewModel) {
    val report by viewModel.yoyReport.collectAsState()
    val moneyColors = LocalMoneyColors.current
    val priorMonth = report.month.minusYears(1)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "nav") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.yoyPrevious() }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
                }
                Text(
                    "${MonthUtil.label(report.month)} vs ${MonthUtil.label(priorMonth)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { viewModel.yoyNext() }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
                }
            }
        }
        item(key = "totals") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    TotalLine(
                        MonthUtil.label(report.month),
                        Money.format(report.currentTotal),
                        MaterialTheme.colorScheme.onSurface,
                    )
                    TotalLine(
                        MonthUtil.label(priorMonth),
                        Money.format(report.priorTotal),
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val totalDelta = report.currentTotal - report.priorTotal
                    TotalLine(
                        "Change",
                        (if (totalDelta > 0) "+" else "") + Money.format(totalDelta),
                        if (totalDelta > 0) moneyColors.loss else moneyColors.gain,
                    )
                }
            }
        }
        items(report.rows, key = { it.displayName }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = IconCatalog.get(row.icon) ?: Icons.Filled.Category,
                        contentDescription = null,
                        tint = if (row.icon != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(end = Spacing.sm),
                    )
                    Text(row.displayName, style = MaterialTheme.typography.bodyLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${Money.format(row.current)} vs ${Money.format(row.prior)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        (if (row.delta > 0) "+" else "") + Money.format(row.delta),
                        style = MaterialTheme.typography.labelMedium,
                        // More spending than last year is the bad direction
                        color = if (row.delta > 0) moneyColors.loss else moneyColors.gain,
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
        }
        if (report.rows.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No spending in either month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.lg),
                )
            }
        }
    }
}
