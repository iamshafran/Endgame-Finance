package com.endgamefinance.data.db.model

/** Projection for the import repair pass (see BluecoinsImport.repairMistypedTransfers). */
data class RepairLeg(
    val id: String,
    val type: String,
    val accountId: String,
    val payee: String,
    val timestamp: Long,
    val amountCents: Long,
)
