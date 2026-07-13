package com.endgamefinance.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.dp
import com.endgamefinance.data.db.entity.NetWorthSnapshot
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money
import java.text.DateFormat
import java.util.Date

/**
 * Single-series net-worth line: 2dp stroke, recessive grid, direct label on
 * the latest point only, tap for a per-point readout. Reads exclusively from
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

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    var selectedIndex by remember(snapshots) { mutableStateOf<Int?>(null) }

    val minVal = snapshots.minOf { it.netWorth }
    val maxVal = snapshots.maxOf { it.netWorth }
    // Pad the range so the line never kisses the frame; degenerate flat series get a band
    val pad = maxOf((maxVal - minVal) / 10, 100L)
    val lo = minVal - pad
    val hi = maxVal + pad
    val minDate = snapshots.first().snapshotDate
    val maxDate = snapshots.last().snapshotDate

    Column(modifier = modifier) {
        // Tap readout replaces the static latest-value label while active
        val readoutIndex = selectedIndex ?: snapshots.lastIndex
        val readout = snapshots[readoutIndex]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(
                text = DateFormat.getDateInstance(DateFormat.MEDIUM)
                    .format(Date(readout.snapshotDate)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Money.format(readout.netWorth),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(Spacing.md)
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
            val w = size.width
            val h = size.height

            fun x(date: Long): Float =
                if (maxDate == minDate) w / 2
                else (date - minDate).toFloat() / (maxDate - minDate) * w

            fun y(value: Long): Float =
                h - (value - lo).toFloat() / (hi - lo) * h

            // Recessive grid: three horizontal lines
            for (i in 1..3) {
                val gy = h * i / 4
                drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx())
            }
            // Zero line, slightly stronger, only when zero is in range
            if (lo < 0 && hi > 0) {
                val zy = y(0)
                drawLine(
                    gridColor.copy(alpha = 0.6f),
                    Offset(0f, zy), Offset(w, zy),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val path = Path()
            snapshots.forEachIndexed { index, snapshot ->
                val px = x(snapshot.snapshotDate)
                val py = y(snapshot.netWorth)
                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))

            // Marker on the active point (latest by default, tapped otherwise)
            val active = snapshots[readoutIndex]
            drawCircle(
                lineColor,
                radius = 4.dp.toPx(),
                center = Offset(x(active.snapshotDate), y(active.netWorth)),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(
                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(minDate)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(maxDate)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
