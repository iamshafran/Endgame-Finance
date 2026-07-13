package com.endgamefinance.data.db.model

/** Minimal transaction projection fed to the recurring-pattern detector. */
data class DetectorRow(
    val payee: String,
    val accountId: String,
    val timestamp: Long,
    val totalAmount: Long,
    val categoryId: String?,
)
