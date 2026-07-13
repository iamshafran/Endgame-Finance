package com.endgamefinance.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * All money is integer cents ([Long]) end to end; formatting/parsing happens
 * only at the UI boundary, here. Never introduce Float/Double for amounts.
 */
object Money {

    private val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)

    /** 123456 → "$1,234.56"; -50 → "-$0.50" */
    fun format(cents: Long): String = currencyFormat.format(BigDecimal(cents).movePointLeft(2))

    /** Signed-aware: negative amounts formatted like "-$0.50" already; kept for call-site clarity. */
    fun formatSigned(cents: Long): String = format(cents)

    /**
     * "1,234.56" / "$1,234.56" / "1234" → cents, or null if not a valid amount.
     * Rejects more than two decimal places rather than rounding silently.
     */
    fun parse(input: String): Long? {
        val cleaned = input.trim().replace("$", "").replace(",", "")
        if (cleaned.isEmpty()) return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value.scale() > 2) return null
        return try {
            value.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }
}
