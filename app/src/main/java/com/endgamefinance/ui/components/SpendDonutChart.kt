package com.endgamefinance.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.ui.theme.tabular
import com.endgamefinance.util.Money

/** One slice of the month's spending. */
data class SpendSlice(
    val label: String,
    val amount: Long,
)

/**
 * Donut of the month's spend split by category, legend alongside. Tap a slice
 * (or its legend row) to put its value in the center; tap again to go back to
 * the total. Colors cycle through the theme's accent roles so every palette
 * re-skins the chart coherently.
 */
@Composable
fun SpendDonutChart(
    slices: List<SpendSlice>,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.amount }
    if (total <= 0 || slices.isEmpty()) {
        Text(
            "The split appears once this month has expenses.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(Spacing.md),
        )
        return
    }

    val scheme = MaterialTheme.colorScheme
    val sliceColors = remember(scheme) {
        listOf(
            scheme.primary,
            scheme.tertiary,
            scheme.secondary,
            scheme.primary.copy(alpha = 0.55f),
            scheme.tertiary.copy(alpha = 0.55f),
            scheme.secondary.copy(alpha = 0.55f),
            scheme.outline,
        )
    }
    fun colorFor(index: Int): Color = sliceColors[index % sliceColors.size]

    var selected by remember(slices) { mutableStateOf<Int?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier.size(148.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .size(148.dp)
                    .pointerInput(slices) {
                        detectTapGestures { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dx = offset.x - center.x
                            val dy = offset.y - center.y
                            // Angle from 12 o'clock, clockwise, in [0, 360)
                            val angle = (
                                Math.toDegrees(
                                    kotlin.math.atan2(dy.toDouble(), dx.toDouble()),
                                ) + 450.0
                                ) % 360.0
                            var cursor = 0.0
                            val hit = slices.indexOfFirst { slice ->
                                cursor += slice.amount.toDouble() / total * 360.0
                                angle < cursor
                            }
                            selected = if (hit < 0 || hit == selected) null else hit
                        }
                    },
            ) {
                val ring = 24.dp.toPx()
                val inset = ring / 2
                val arcSize = Size(size.width - ring, size.height - ring)
                val gap = if (slices.size > 1) 1.6f else 0f
                var start = -90f
                slices.forEachIndexed { index, slice ->
                    val sweep = slice.amount.toFloat() / total * 360f
                    val dim = selected != null && selected != index
                    drawArc(
                        color = colorFor(index).copy(alpha = if (dim) 0.30f else 1f),
                        startAngle = start + gap / 2,
                        sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = ring),
                    )
                    start += sweep
                }
            }
            // Center readout: total by default, tapped slice otherwise
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val active = selected?.let { slices[it] }
                Text(
                    text = Money.format(active?.amount ?: total),
                    style = MaterialTheme.typography.titleMedium.tabular,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = active?.label ?: "spent",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            slices.forEachIndexed { index, slice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(slices) {
                            detectTapGestures {
                                selected = if (selected == index) null else index
                            }
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(colorFor(index), CircleShape),
                    )
                    Text(
                        text = slice.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Spacing.xs),
                    )
                    Text(
                        text = "${(slice.amount * 100 / total)}%",
                        style = MaterialTheme.typography.labelMedium.tabular,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
