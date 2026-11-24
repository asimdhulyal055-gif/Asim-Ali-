package com.example.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Centralized Pakistani Rupee (PKR) currency formatter for AA CRAVE ERP.
 * All UI screens, invoices, receipts, reports, ledgers, payroll, and CSV/PDF exports
 * delegate to this formatter while preserving exact minor-unit (Long) calculations.
 */
object PkrCurrencyFormatter {

    const val CURRENCY_CODE: String = "PKR"
    val PAKISTAN_LOCALE: Locale = Locale.Builder().setLanguage("en").setRegion("PK").build()

    private val symbols: DecimalFormatSymbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }

    private fun newFormatter(): DecimalFormat = DecimalFormat("#,##0.00", symbols).apply {
        roundingMode = RoundingMode.HALF_UP
    }

    fun formatMinorUnits(minorUnits: Long): String {
        val negative = minorUnits < 0L
        val absBig = BigDecimal.valueOf(minorUnits).abs().movePointLeft(2)
        val formattedNumber = newFormatter().format(absBig)
        return if (negative) "-$CURRENCY_CODE $formattedNumber" else "$CURRENCY_CODE $formattedNumber"
    }

    fun formatMajorAmount(amount: Double): String {
        if (amount.isNaN() || amount.isInfinite()) {
            return "$CURRENCY_CODE 0.00"
        }
        val bd = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
        val negative = bd.signum() < 0
        val absBd = bd.abs()
        val formattedNumber = newFormatter().format(absBd)
        return if (negative) "-$CURRENCY_CODE $formattedNumber" else "$CURRENCY_CODE $formattedNumber"
    }

    fun parseMajorToMinorUnits(input: String): Long? {
        val cleaned = input.trim()
            .replace(CURRENCY_CODE, "", ignoreCase = true)
            .replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true)
            .replace(",", "")
            .trim()
        if (cleaned.isBlank()) return null
        return try {
            BigDecimal(cleaned)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: Exception) {
            null
        }
    }
}
