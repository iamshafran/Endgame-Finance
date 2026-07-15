package com.endgamefinance.data.db.model

/** One expense on a given day, for the calendar anomaly explanation (Milestone 8.3). */
data class DaySpendLine(
    val payee: String,
    val category: String?,
    val amountCents: Long,
)
