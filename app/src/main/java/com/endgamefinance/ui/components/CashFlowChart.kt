package com.endgamefinance.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.endgamefinance.ui.theme.LocalMoneyColors
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.ui.theme.tabular
import com.endgamefinance.util.Money

data class MonthCashFlow(
    val label: String,
    val income: Long,
    val spending: Long,
)

/**
 * Paired monthly columns: income (gain) vs spending (loss) — the app's
 * semantic money colors, legend above, month axis below, tap for values.
 * Bars have rounded data-ends anchored to the zero baseline.
 */
@Composable
fun CashFlowChart(
    months: List<MonthCashFlow>,
    modifier: Modifier = Modifier,
) {
    if (months.all { it.income == 0L && it.spending == 0L }) {
        Text(
            "Cash flow appears once there's a month of activity.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(Spacing.md),
        )
        return
    }

    val moneyColors = LocalMoneyColors.current
    var selected by remember(months) { mutableStateOf(months.lastIndex) }
    val active = months[selected]

    Column(modifier = modifier) {
        // Legend + tap readout share one line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendDot(moneyColors.gain, "In")
                LegendDot(moneyColors.loss, "Out")
            }
            Text(
                text = "${active.label}: +${Money.format(active.income)} · " +
                    "−${Money.format(active.spending)}",
                style = MaterialTheme.typography.labelMedium.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .pointerInput(months) {
                    detectTapGestures { offset ->
                        val slot = size.width / months.size.toFloat()
                        selected = (offset.x / slot).toInt().coerceIn(0, months.lastIndex)
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val maxValue = months.maxOf { maxOf(it.income, it.spending) }.coerceAtLeast(1)
            val slot = w / months.size
            val barWidth = (slot * 0.30f).coerceAtMost(24.dp.toPx())
            val gap = 2.dp.toPx()
            val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

            // Recessive grid
            for (i in 1..3) {
                val gy = h * i / 4
                drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx())
            }
            drawLine(gridColor.copy(alpha = 0.5f), Offset(0f, h), Offset(w, h),
                strokeWidth = 1.dp.toPx())

            fun bar(x: Float, value: Long, color: androidx.compose.ui.graphics.Color) {
                if (value <= 0) return
                val barHeight = (value.toFloat() / maxValue * (h - 4.dp.toPx()))
                    .coerceAtLeast(2.dp.toPx())
                val rect = Rect(x, h - barHeight, x + barWidth, h)
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect,
                            topLeft = corner, topRight = corner,
                            bottomLeft = CornerRadius.Zero, bottomRight = CornerRadius.Zero,
                        ),
                    )
                }
                drawPath(path, color)
            }

            months.forEachIndexed { index, month ->
                val center = slot * index + slot / 2
                bar(center - barWidth - gap / 2, month.income, moneyColors.gain)
                bar(center + gap / 2, month.spending, moneyColors.loss)
                // Selection marker: subtle underline beneath the active month
                if (index == selected) {
                    drawLine(
                        gridColor.copy(alpha = 0.9f),
                        Offset(slot * index + slot * 0.2f, h + 1.dp.toPx()),
                        Offset(slot * (index + 1) - slot * 0.2f, h + 1.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
        }

        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)) {
            months.forEach { month ->
                Text(
                    text = month.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, androidx.compose.ui.graphics.RectangleShape),
        )
        Text(
            " $label",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
