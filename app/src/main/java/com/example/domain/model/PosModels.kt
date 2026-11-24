package com.example.domain.model

import kotlin.math.roundToLong

enum class OrderType(val label: String) {
    DINE_IN("Dine-in"),
    TAKEAWAY("Takeaway"),
    DELIVERY("Delivery")
}

enum class OrderPriority(val label: String) {
    NORMAL("Normal"),
    RUSH("Rush"),
    VIP("VIP")
}

enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    CARD("Card"),
    BANK_TRANSFER("Bank Transfer"),
    DIGITAL("Digital Payment"),
    SPLIT("Split Payment")
}

enum class OrderStatus(val label: String) {
    CONFIRMED("Confirmed"),
    PREPARING("Preparing"),
    READY("Ready"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    REFUNDED("Refunded")
}

enum class InvoicePaperSize(val label: String, val widthPt: Int, val heightPt: Int) {
    THERMAL_80MM("80mm Thermal", 226, 680),
    A4_STANDARD("A4 Invoice", 595, 842)
}

enum class KotPaperWidth(val label: String, val widthPt: Int, val heightPt: Int) {
    THERMAL_58MM("58mm Compact", 164, 520),
    THERMAL_80MM("80mm Standard", 226, 520)
}

data class ProductCategory(
    val categoryId: String,
    val branchId: String,
    val name: String,
    val sortOrder: Int
)

data class ProductVariation(
    val variationId: String,
    val productId: String,
    val name: String,
    val priceDeltaCents: Long,
    val isDefault: Boolean
)

data class ProductAddon(
    val addonId: String,
    val productId: String,
    val name: String,
    val priceCents: Long
)

data class PosProduct(
    val productId: String,
    val branchId: String,
    val categoryId: String,
    val name: String,
    val description: String,
    val basePriceCents: Long,
    val sku: String,
    val categoryTag: String,
    val isAvailable: Boolean,
    val taxExempt: Boolean,
    val variations: List<ProductVariation>,
    val addons: List<ProductAddon>,
    val isArchived: Boolean = false,
    val isCombo: Boolean = false,
    val comboSummary: String = "",
    val imagePath: String? = null,
    val kitchenStation: String = "MAIN_KITCHEN"
)

data class CustomerSummary(
    val customerId: String,
    val branchId: String,
    val name: String,
    val phone: String,
    val email: String,
    val loyaltyPoints: Int,
    val tier: String,
    val discountPercent: Int,
    val address: String = "",
    val totalPurchasesCents: Long = 0L,
    val accountBalanceCents: Long = 0L
)

data class CartItem(
    val cartLineId: String,
    val product: PosProduct,
    val selectedVariation: ProductVariation?,
    val selectedAddons: List<ProductAddon>,
    val specialInstructions: String,
    val quantity: Int
) {
    val baseAndVariationUnitCents: Long
        get() = product.basePriceCents + (selectedVariation?.priceDeltaCents ?: 0L)

    val addonsUnitCents: Long
        get() = selectedAddons.sumOf { it.priceCents }

    val unitTotalCents: Long
        get() = baseAndVariationUnitCents + addonsUnitCents

    val lineTotalCents: Long
        get() = unitTotalCents * quantity.coerceAtLeast(1)

    val addonsSummary: String
        get() = selectedAddons.joinToString(", ") { it.name }
}

data class BillingTotals(
    val subtotalCents: Long,
    val discountCents: Long,
    val taxableAmountCents: Long,
    val taxRatePercent: Double,
    val taxCents: Long,
    val serviceChargeRatePercent: Double,
    val serviceChargeCents: Long,
    val totalPayableCents: Long
)

data class SplitPaymentBreakdown(
    val cashCents: Long = 0L,
    val cardCents: Long = 0L,
    val bankTransferCents: Long = 0L,
    val digitalCents: Long = 0L
) {
    val totalPaidCents: Long
        get() = cashCents + cardCents + bankTransferCents + digitalCents

    fun toSummaryString(): String {
        val parts = mutableListOf<String>()
        if (cashCents > 0) parts += "Cash: ${centsToCurrency(cashCents)}"
        if (cardCents > 0) parts += "Card: ${centsToCurrency(cardCents)}"
        if (bankTransferCents > 0) parts += "Bank: ${centsToCurrency(bankTransferCents)}"
        if (digitalCents > 0) parts += "Digital: ${centsToCurrency(digitalCents)}"
        return if (parts.isEmpty()) "Unpaid" else parts.joinToString(" | ")
    }
}

object BillingCalculator {
    const val DEFAULT_TAX_RATE_PERCENT = 8.0
    const val DEFAULT_DINE_IN_SERVICE_PERCENT = 5.0

    fun calculate(
        items: List<CartItem>,
        discountPercent: Double,
        manualDiscountCents: Long,
        taxRatePercent: Double = DEFAULT_TAX_RATE_PERCENT,
        serviceChargeRatePercent: Double
    ): BillingTotals {
        val subtotal = items.sumOf { it.lineTotalCents }.coerceAtLeast(0L)
        val pctDiscount = ((subtotal * discountPercent.coerceIn(0.0, 100.0)) / 100.0).roundToLong()
        val combinedDiscount = (pctDiscount + manualDiscountCents.coerceAtLeast(0L)).coerceIn(0L, subtotal)
        val netSubtotal = (subtotal - combinedDiscount).coerceAtLeast(0L)

        val taxableItemsSubtotal = items
            .filterNot { it.product.taxExempt }
            .sumOf { it.lineTotalCents }
            .coerceAtLeast(0L)
        val taxableRatio = if (subtotal > 0) taxableItemsSubtotal.toDouble() / subtotal.toDouble() else 0.0
        val taxableBase = (netSubtotal * taxableRatio).roundToLong()

        val taxCents = ((taxableBase * taxRatePercent.coerceAtLeast(0.0)) / 100.0).roundToLong()
        val serviceCents = ((netSubtotal * serviceChargeRatePercent.coerceAtLeast(0.0)) / 100.0).roundToLong()
        val total = netSubtotal + taxCents + serviceCents

        return BillingTotals(
            subtotalCents = subtotal,
            discountCents = combinedDiscount,
            taxableAmountCents = taxableBase,
            taxRatePercent = taxRatePercent,
            taxCents = taxCents,
            serviceChargeRatePercent = serviceChargeRatePercent,
            serviceChargeCents = serviceCents,
            totalPayableCents = total
        )
    }
}

data class OrderItemLine(
    val itemId: String,
    val productId: String,
    val productName: String,
    val variationName: String?,
    val addonsSummary: String,
    val specialInstructions: String,
    val quantity: Int,
    val unitPriceCents: Long,
    val addonsUnitCents: Long,
    val lineTotalCents: Long
)

data class PosOrderRecord(
    val orderId: String,
    val branchId: String,
    val branchName: String,
    val orderNumber: String,
    val invoiceNumber: String,
    val kotNumber: String,
    val orderType: OrderType,
    val tableNumber: String?,
    val priority: OrderPriority,
    val customerName: String?,
    val subtotalCents: Long,
    val discountCents: Long,
    val taxCents: Long,
    val serviceChargeCents: Long,
    val totalCents: Long,
    val paidCents: Long,
    val remainingBalanceCents: Long,
    val changeCents: Long,
    val paymentMethod: PaymentMethod,
    val paymentBreakdown: String,
    val status: OrderStatus,
    val reprintInvoiceCount: Int,
    val reprintKotCount: Int,
    val cashierName: String,
    val approvedBy: String?,
    val cancellationReason: String?,
    val createdAt: Long,
    val items: List<OrderItemLine>,
    val loyaltyCardId: String? = null,
    val loyaltyCardNumber: String? = null,
    val loyaltyStampChange: Int = 0,
    val loyaltyStampsAfter: Int = 0,
    val loyaltyVoucherId: String? = null,
    val loyaltyVoucherCode: String? = null,
    val loyaltyVoucherDiscountCents: Long = 0L,
    val loyaltyPromoCostCents: Long = 0L,
    val loyaltyRequiredStamps: Int = 6,
    val loyaltyStatusMessage: String? = null
)

/**
 * Strictly separated Customer Invoice document.
 * Contains financial breakdown, payments, tax, discounts, and thank-you footer.
 */
data class CustomerInvoiceDocument(
    val orderId: String,
    val branchName: String,
    val branchAddress: String,
    val invoiceNumber: String,
    val orderNumber: String,
    val timestampMillis: Long,
    val customerName: String?,
    val orderType: OrderType,
    val tableNumber: String?,
    val items: List<OrderItemLine>,
    val subtotalCents: Long,
    val discountCents: Long,
    val taxCents: Long,
    val serviceChargeCents: Long,
    val totalPayableCents: Long,
    val paymentMethodLabel: String,
    val paymentBreakdown: String,
    val paidAmountCents: Long,
    val remainingBalanceCents: Long,
    val changeCents: Long,
    val thankYouMessage: String,
    val isReprint: Boolean,
    val reprintNumber: Int,
    val loyaltyCardId: String? = null,
    val loyaltyCardNumber: String? = null,
    val loyaltyStampChange: Int = 0,
    val loyaltyStampsAfter: Int = 0,
    val loyaltyRequiredStamps: Int = 6,
    val loyaltyVoucherId: String? = null,
    val loyaltyVoucherCode: String? = null,
    val loyaltyVoucherDiscountCents: Long = 0L,
    val loyaltyStatusMessage: String? = null
)

/**
 * Strictly separated Kitchen Order Ticket (KOT) document.
 * NEVER contains customer payment info, totals, tax, discounts, or invoice numbers.
 */
data class KitchenKotDocument(
    val kotId: String,
    val branchName: String,
    val kotNumber: String,
    val orderNumber: String,
    val timestampMillis: Long,
    val orderType: OrderType,
    val tableNumber: String?,
    val priority: OrderPriority,
    val station: String,
    val items: List<KotLineItem>,
    val isReprint: Boolean,
    val reprintNumber: Int
)

data class KotLineItem(
    val productName: String,
    val variationName: String?,
    val quantity: Int,
    val addonsSummary: String,
    val specialInstructions: String
)

data class DailyCashClosingSummary(
    val closingId: String,
    val branchId: String,
    val businessDate: String,
    val cashSalesCents: Long,
    val cardSalesCents: Long,
    val bankTransferSalesCents: Long,
    val digitalSalesCents: Long,
    val refundsCents: Long,
    val expectedCashCents: Long,
    val countedCashCents: Long,
    val varianceCents: Long,
    val closedByName: String,
    val notes: String,
    val closedAt: Long
)

fun centsToCurrency(cents: Long): String = PkrCurrencyFormatter.formatMinorUnits(cents)
