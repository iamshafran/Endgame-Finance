package com.endgamefinance.data.db.model

/** Per-payee expense aggregation for the merchant view. */
data class MerchantStat(
    val payee: String,
    val visits: Int,
    val total: Long,
)
