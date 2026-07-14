package com.endgamefinance.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.endgamefinance.data.db.entity.NetWorthSnapshot
import com.endgamefinance.ui.theme.LocalMoneyColors
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.ui.theme.tabular
import com.endgamefinance.util.Money
import java.text.DateFormat
import java.util.Date

/** Compact axis label for a cents value: 1.2k, 3.4M, -800. */
private fun compactCents(cents: Long): String {
    val units = cents / 100.0
    val abs = kotlin.math.abs(units)
    return when {
        abs >= 1_000_000 -> "%.1fM".format(units / 1_000_000)
        abs >= 10_000 -> "%.0fk".format(units / 1_000)
        abs >= 1_000 -> "%.1fk".format(units / 1_000)
        else -> "%.0f".format(units)
    }
}

/**
 * Net-worth trend: three lines (net worth, assets, liabilities) with a labeled
 * y-axis, date x-axis, and tap for a per-point readout. Reads exclusively from
 * snapshots — the caller must never feed it live-derived values.
 */
@Composable
fun NetWorthChart(
    snapshots: List<NetWorthSnapshot>,
    modifier: Modifier = Modifier,
) {
    if (snapshots.size < 2) {
        Text(
            text = "The trend appears once a few daily snapshots exist.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(Spacing.md),
        )
        return
    }

    val moneyColors = LocalMoneyColors.current
    val netColor = MaterialTheme.colorScheme.primary
    val assetColor = moneyColors.gain
    val liabilityColor = moneyColors.loss
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    var selectedIndex by remember(snapshots) { mutableStateOf<Int?>(null) }

    // Shared scale across all three series so their relationship is honest
    val minVal = snapshots.minOf { minOf(it.netWorth, it.totalAssets, it.totalLiabilities) }
    val maxVal = snapshots.maxOf { maxOf(it.netWorth, it.totalAssets, it.totalLiabilities) }
    // Pad the range so lines never kiss the frame; degenerate flat series get a band
    val pad = maxOf((maxVal - minVal) / 10, 100L)
    val lo = minVal - pad
    val hi = maxVal + pad
    val minDate = snapshots.first().snapshotDate
    val maxDate = snapshots.last().snapshotDate

    val textMeasurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp)

    Column(modifier = modifier.padding(bottom = Spacing.md)) {
        // Tap readout replaces the static latest-value line while active
        val readoutIndex = selectedIndex ?: snapshots.lastIndex
        val readout = snapshots[readoutIndex]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = DateFormat.getDateInstance(DateFormat.MEDIUM)
                    .format(Date(readout.snapshotDate)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Money.format(readout.netWorth),
                style = MaterialTheme.typography.labelLarge.tabular,
                color = netColor,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeriesDot(netColor, "Net worth")
            SeriesDot(assetColor, "Assets")
            SeriesDot(liabilityColor, "Liabilities")
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .layoutId("networth_chart")
                .pointerInput(snapshots) {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val targetDate = minDate + ((maxDate - minDate) * fraction).toLong()
                        selectedIndex = snapshots.indices.minByOrNull {
                            kotlin.math.abs(snapshots[it].snapshotDate - targetDate)
                        }
                    }
                },
        ) {
            val h = size.height
            // Left gutter sized to the widest y label so lines never overlap text
            val gutterLabels = (0..4).map { i ->
                compactCents(hi - (hi - lo) * i / 4)
            }
            val measured = gutterLabels.map {
                textMeasurer.measure(it, style = axisStyle)
            }
            val gutter = measured.maxOf { it.size.width } + 6.dp.toPx()
            val w = size.width
            val plotW = w - gutter

            fun x(date: Long): Float =
                gutter + if (maxDate == minDate) plotW / 2
                else (date - minDate).toFloat() / (maxDate - minDate) * plotW

            fun y(value: Long): Float =
                h - (value - lo).toFloat() / (hi - lo) * h

            // Y axis: gridlines with value labels (top, quarters, bottom)
            for (i in 0..4) {
                val gy = h * i / 4
                if (i in 1..3) {
                    drawLine(
                        gridColor, Offset(gutter, gy), Offset(w, gy),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                val label = measured[i]
                drawText(
                    label,
                    color = axisTextColor,
                    topLeft = Offset(
                        0f,
                        (gy - label.size.height / 2).coerceIn(0f, h - label.size.height),
                    ),
                )
            }
            // Zero line, slightly stronger, only when zero is in range
            if (lo < 0 && hi > 0) {
                val zy = y(0)
                drawLine(
                    gridColor.copy(alpha = 0.6f),
                    Offset(gutter, zy), Offset(w, zy),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            fun drawSeries(color: Color, width: Float, value: (NetWorthSnapshot) -> Long) {
                val path = Path()
                snapshots.forEachIndexed { index, snapshot ->
                    val px = x(snapshot.snapshotDate)
                    val py = y(value(snapshot))
                    if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, color, style = Stroke(width = width))
            }

            // Supporting series first so the net-worth line stays on top
            drawSeries(assetColor.copy(alpha = 0.85f), 1.5.dp.toPx()) { it.totalAssets }
            drawSeries(liabilityColor.copy(alpha = 0.85f), 1.5.dp.toPx()) { it.totalLiabilities }
            drawSeries(netColor, 2.dp.toPx()) { it.netWorth }

            // Markers on the active point (latest by default, tapped otherwise)
            val active = snapshots[readoutIndex]
            val ax = x(active.snapshotDate)
            drawCircle(assetColor, radius = 3.dp.toPx(), center = Offset(ax, y(active.totalAssets)))
            drawCircle(
                liabilityColor, radius = 3.dp.toPx(),
                center = Offset(ax, y(active.totalLiabilities)),
            )
            drawCircle(netColor, radius = 4.dp.toPx(), center = Offset(ax, y(active.netWorth)))
        }

        // X axis: start / middle / end dates
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md),
        ) {
            val fmt = DateFormat.getDateInstance(DateFormat.SHORT)
            Text(
                fmt.format(Date(minDate)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                fmt.format(Date(minDate + (maxDate - minDate) / 2)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                fmt.format(Date(maxDate)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }

        // Active point's asset/liability split under the axis
        Text(
            text = "Assets ${Money.format(readout.totalAssets)} · " +
                "Liabilities ${Money.format(readout.totalLiabilities)}",
            style = MaterialTheme.typography.labelMedium.tabular,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun SeriesDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            " $label",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
