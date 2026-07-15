package com.endgamefinance.data.db.model

/** Projection for the auto-category profile: one historical payee/category pairing. */
data class PayeeCategory(
    val payee: String,
    val categoryId: String,
)
