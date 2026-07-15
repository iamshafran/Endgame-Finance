package com.endgamefinance.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * All money is integer minor units ([Long]) end to end; formatting/parsing
 * happens only at the UI boundary, here. Never introduce Float/Double.
 *
 * [currencyCode] is a DISPLAY preference — it swaps the symbol only, using
 * fixed US-style grouping and always two fraction digits (the app's stored
 * model). It never converts values; there is no FX. Set once at startup and
 * whenever the user changes it (see AppSettings); reads are cheap and cached.
 */
object Money {

    @Volatile
    var currencyCode: String = "USD"

    private val formatters = ConcurrentHashMap<String, NumberFormat>()

    private fun formatter(): NumberFormat = formatters.getOrPut(currencyCode) {
        (NumberFormat.getCurrencyInstance(Locale.US) as DecimalFormat).apply {
            try {
                currency = Currency.getInstance(currencyCode)
            } catch (e: Exception) {
                // Unknown code — leave the US default symbol
            }
            // Our stored model is always 2 minor digits, even for currencies
            // that natively use 0 or 3 (JPY, KWD) — keep it uniform.
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    /** Display symbol of the active currency ("$", "€", "RM", …). Falls back to
     *  the code itself for unknown codes. Used anywhere money is rendered as
     *  text outside [format] — e.g. the AI answer prompt. */
    val symbol: String
        get() = try {
            Currency.getInstance(currencyCode).getSymbol(Locale.US)
        } catch (e: Exception) {
            currencyCode
        }

    /** 123456 → "$1,234.56" / "€1,234.56"; -50 → "-$0.50". */
    fun format(cents: Long): String = formatter().format(BigDecimal(cents).movePointLeft(2))

    /** Signed-aware; negatives already render with a leading minus. */
    fun formatSigned(cents: Long): String = format(cents)

    /**
     * Number only — no symbol, no grouping: 123456 → "1234.56". Use this to
     * prefill amount text fields so the value round-trips through [parse]
     * regardless of the active currency symbol.
     */
    fun formatPlain(cents: Long): String =
        BigDecimal(cents).movePointLeft(2).setScale(2).toPlainString()

    /**
     * "1,234.56" / "$1,234.56" / "€1.234" (symbols/grouping stripped) → cents,
     * or null if not a valid amount. Rejects more than two decimal places
     * rather than rounding silently. Decimal separator is always '.'.
     */
    fun parse(input: String): Long? {
        val cleaned = input.trim().filter { it.isDigit() || it == '.' || it == '-' }
        if (cleaned.isEmpty() || cleaned == "-") return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value.scale() > 2) return null
        return try {
            value.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }
}
