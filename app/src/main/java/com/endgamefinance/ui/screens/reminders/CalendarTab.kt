package com.endgamefinance.ui.screens.reminders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.endgamefinance.ui.theme.LocalMoneyColors
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money
import com.endgamefinance.util.MonthUtil
import java.time.LocalDate

@Composable
fun CalendarTab(viewModel: RemindersViewModel) {
    val state by viewModel.calendarState.collectAsState()
    val moneyColors = LocalMoneyColors.current
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.calPreviousMonth(); selected = null }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
            }
            Text(MonthUtil.label(state.month), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { viewModel.calNextMonth(); selected = null }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
            }
        }

        // Weekday header, Sunday-first
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val tertiary = MaterialTheme.colorScheme.tertiary
        val momentumColor: (Momentum) -> Color = { momentum ->
            when (momentum) {
                Momentum.NONE -> Color.Transparent
                Momentum.LOW -> moneyColors.gain.copy(alpha = 0.18f)
                Momentum.NORMAL -> tertiary.copy(alpha = 0.30f)
                Momentum.HIGH -> moneyColors.loss.copy(alpha = 0.35f)
            }
        }

        // Sunday-first offset: java.time DayOfWeek is Mon=1..Sun=7
        val firstOffset = state.days.firstOrNull()?.date?.dayOfWeek?.value?.rem(7) ?: 0
        val cells: List<CalendarDay?> = List(firstOffset) { null } + state.days
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        isSelected = day != null && day.date == selected,
                        color = day?.let { momentumColor(it.momentum) } ?: Color.Transparent,
                        onClick = { day?.let { selected = it.date } },
                        onLongClick = { day?.let { viewModel.explainDay(it) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(7 - week.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }

        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendSwatch(moneyColors.gain.copy(alpha = 0.18f), "below avg")
            LegendSwatch(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.30f), "near avg")
            LegendSwatch(moneyColors.loss.copy(alpha = 0.35f), "above avg")
            LegendDot(moneyColors.loss, "overdue")
            LegendDot(MaterialTheme.colorScheme.tertiary, "upcoming")
        }
        Text(
            text = "Momentum vs your ${Money.format(state.avgDailySpend)}/day 90-day average. " +
                "Long-press a day to ask why you spent.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )

        selected?.let { date ->
            state.days.firstOrNull { it.date == date }?.let { day ->
                DayDetail(day, state.forecasts)
            }
        }
    }

    ExplainSheet(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExplainSheet(viewModel: RemindersViewModel) {
    val explain by viewModel.explain.collectAsState()
    val state = explain ?: return
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = { viewModel.dismissExplain() },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
        ) {
            Text(
                "Why did I spend on ${state.dateLabel}?",
                style = MaterialTheme.typography.titleMedium,
            )
            Box(modifier = Modifier.padding(top = Spacing.md)) {
                when {
                    state.loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            "Reading that day's purchases…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    state.error != null -> Text(
                        state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Text(
                        state.text.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    day: CalendarDay?,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val moneyColors = LocalMoneyColors.current
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .background(color, RoundedCornerShape(8.dp))
            .then(
                if (isSelected) Modifier.border(
                    2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp),
                ) else Modifier,
            )
            .combinedClickable(
                enabled = day != null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (day != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = day.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (day.overdueBills.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(moneyColors.loss, CircleShape),
                        )
                    }
                    if (day.upcomingBills.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(3.dp)),
        )
        Text(
            " $label",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape),
        )
        Text(
            " $label",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayDetail(
    day: CalendarDay,
    forecasts: List<com.endgamefinance.data.repo.AccountForecast>,
) {
    val moneyColors = LocalMoneyColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = day.date.toString(),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (day.spent > 0) "Spent ${Money.format(day.spent)}"
                else "No spending",
                style = MaterialTheme.typography.bodyMedium,
                color = if (day.momentum == Momentum.HIGH) moneyColors.loss
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            day.overdueBills.forEach { bill ->
                Text(
                    "Overdue: ${bill.name} · " +
                        (bill.amountCents?.let { Money.format(it) } ?: "varies"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = moneyColors.loss,
                )
            }
            day.upcomingBills.forEach { bill ->
                Text(
                    "Due: ${bill.name} · " +
                        (bill.amountCents?.let { Money.format(it) } ?: "varies"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            // Projected balances make sense only from today forward
            if (!day.date.isBefore(java.time.LocalDate.now()) && forecasts.isNotEmpty()) {
                val dayEndMs = day.date.plusDays(1)
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
                val balances = com.endgamefinance.data.repo.ForecastBuilder
                    .balancesAt(forecasts, dayEndMs)
                Text(
                    text = "Projected balances at end of day",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                forecasts.forEach { forecast ->
                    val balance = balances[forecast.accountId] ?: forecast.startingBalance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            forecast.accountName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            Money.format(balance),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (balance >= 0) moneyColors.gain else moneyColors.loss,
                        )
                    }
                }
            }
        }
    }
}
