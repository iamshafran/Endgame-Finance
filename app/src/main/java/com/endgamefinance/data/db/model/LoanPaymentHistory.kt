package com.endgamefinance.data.db.model

/** A past payment against a loan: its total and the interest portion (Milestone 8.4). */
data class LoanPaymentHistory(
    val timestamp: Long,
    val totalCents: Long,
    val interestCents: Long,
)
