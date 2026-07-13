package com.endgamefinance.ui.screens.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.endgamefinance.data.repo.AccountForecast
import com.endgamefinance.ui.theme.LocalMoneyColors
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money
import java.text.DateFormat
import java.util.Date

@Composable
fun ForecastTab(viewModel: RemindersViewModel) {
    val forecasts by viewModel.forecasts.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "intro") {
            Text(
                text = "Next 30 days, from posted balances plus scheduled bills and income.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
        items(forecasts, key = { it.accountId }) { forecast ->
            ForecastCard(forecast)
        }
        if (forecasts.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "No asset accounts to project. Forecasting covers asset accounts " +
                        "(checking, savings) with their scheduled reminders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.lg),
                )
            }
        }
    }
}

@Composable
private fun ForecastCard(forecast: AccountForecast) {
    val moneyColors = LocalMoneyColors.current
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(forecast.accountName, style = MaterialTheme.typography.titleMedium)
                Text(
                    Money.format(forecast.startingBalance),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (forecast.startingBalance >= 0) moneyColors.gain else moneyColors.loss,
                )
            }

            if (forecast.shortfallDate != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.sm),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = buildString {
                            append("Projected to hit ")
                            append(Money.format(forecast.shortfallBalance ?: 0))
                            append(" on ")
                            append(dateFormat.format(Date(forecast.shortfallDate)))
                            if (forecast.shortfallBeforeIncome) {
                                append(" — before any expected income arrives")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }

            if (forecast.events.isEmpty()) {
                Text(
                    "No scheduled activity in the next 30 days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.sm),
                )
            } else {
                forecast.events.forEach { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${dateFormat.format(Date(event.date))} · ${event.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text(
                                text = (if (event.signedAmount > 0) "+" else "−") +
                                    Money.format(kotlin.math.abs(event.signedAmount)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (event.signedAmount > 0) moneyColors.gain
                                else moneyColors.loss,
                            )
                            Text(
                                text = Money.format(event.runningBalance),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (event.runningBalance < 0) moneyColors.loss
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("In 30 days", style = MaterialTheme.typography.labelLarge)
                    Text(
                        Money.format(forecast.endingBalance),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (forecast.endingBalance >= 0) moneyColors.gain
                        else moneyColors.loss,
                    )
                }
            }

            if (forecast.unprojectable.isNotEmpty()) {
                Text(
                    text = "Not projected (amount varies): " +
                        forecast.unprojectable.joinToString(", "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}
