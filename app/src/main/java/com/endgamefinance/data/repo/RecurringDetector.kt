package com.endgamefinance.data.repo

import com.endgamefinance.data.db.entity.Reminder
import com.endgamefinance.data.db.model.DetectorRow
import kotlin.math.abs

data class RecurringSuggestion(
    val payee: String,
    val accountId: String,
    val categoryId: String?,
    /** Median of the observed amounts. */
    val amountCents: Long,
    val frequency: String,
    val frequencyInterval: Int,
    val nextDueDate: Long,
    val occurrences: Int,
)

/**
 * Pure pattern scan, no I/O. A payee+account group is suggested when it has
 * 3+ occurrences, every amount within ±15% of the median amount, and every
 * gap between consecutive occurrences within tolerance of the median gap,
 * which must map onto a supported cadence.
 */
object RecurringDetector {

    private const val MIN_OCCURRENCES = 3
    private const val AMOUNT_TOLERANCE = 0.15
    private const val DAY_MS = 86_400_000L

    // (minDays..maxDays) → frequency unit + interval
    private data class Cadence(
        val range: ClosedFloatingPointRange<Double>,
        val frequency: String,
        val interval: Int,
    )

    private val cadences = listOf(
        Cadence(0.5..1.5, "daily", 1),
        Cadence(6.0..8.0, "weekly", 1),
        Cadence(12.5..15.5, "weekly", 2),
        Cadence(26.0..34.0, "monthly", 1),
        Cadence(55.0..67.0, "monthly", 2),
        Cadence(83.0..97.0, "monthly", 3),
        Cadence(170.0..195.0, "monthly", 6),
        Cadence(350.0..380.0, "yearly", 1),
    )

    fun detect(
        rows: List<DetectorRow>,
        existingReminders: List<Reminder>,
        dismissedPayees: Set<String>,
    ): List<RecurringSuggestion> {
        val reminderNames = existingReminders.map { it.name.lowercase() }.toSet()
        return rows
            .groupBy { it.payee to it.accountId }
            .mapNotNull { (key, group) ->
                val (payee, accountId) = key
                if (payee.lowercase() in reminderNames) return@mapNotNull null
                if (payee.lowercase() in dismissedPayees) return@mapNotNull null
                analyze(payee, accountId, group.sortedBy { it.timestamp })
            }
            .sortedByDescending { it.occurrences }
    }

    private fun analyze(
        payee: String,
        accountId: String,
        occurrences: List<DetectorRow>,
    ): RecurringSuggestion? {
        if (occurrences.size < MIN_OCCURRENCES) return null

        val amounts = occurrences.map { it.totalAmount }.sorted()
        val medianAmount = amounts[amounts.size / 2]
        if (medianAmount <= 0) return null
        if (amounts.any { abs(it - medianAmount) > medianAmount * AMOUNT_TOLERANCE }) return null

        val gapsDays = occurrences.zipWithNext { a, b ->
            (b.timestamp - a.timestamp).toDouble() / DAY_MS
        }
        val sortedGaps = gapsDays.sorted()
        val medianGap = sortedGaps[sortedGaps.size / 2]
        val cadence = cadences.firstOrNull { medianGap in it.range } ?: return null

        // Every gap must stay near the median: ±15% but never tighter than ±2 days
        val tolerance = maxOf(2.0, medianGap * 0.15)
        if (gapsDays.any { abs(it - medianGap) > tolerance }) return null

        // Most common category across occurrences, if any
        val categoryId = occurrences.mapNotNull { it.categoryId }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key

        return RecurringSuggestion(
            payee = payee,
            accountId = accountId,
            categoryId = categoryId,
            amountCents = medianAmount,
            frequency = cadence.frequency,
            frequencyInterval = cadence.interval,
            nextDueDate = occurrences.last().timestamp + (medianGap * DAY_MS).toLong(),
            occurrences = occurrences.size,
        )
    }
}
