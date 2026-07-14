package com.endgamefinance.data.db.model

/** Projection for the payee→account profile used to guess which account a
 *  captured notification transaction belongs to (Milestone 8.1). */
data class PayeeAccount(
    val payee: String,
    val accountId: String,
)
