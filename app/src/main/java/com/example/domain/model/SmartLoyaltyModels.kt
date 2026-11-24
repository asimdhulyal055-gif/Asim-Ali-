package com.example.domain.model

import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong

enum class LoyaltyCardStatus(val code: String, val label: String) {
    NOT_ACTIVATED("NOT_ACTIVATED", "Not Activated"),
    ACTIVE("ACTIVE", "Active"),
    REWARD_ELIGIBLE("REWARD_ELIGIBLE", "Reward Ready"),
    COMPLETED("COMPLETED", "Cycle Completed"),
    EXPIRED("EXPIRED", "Expired"),
    SUSPENDED("SUSPENDED", "Suspended"),
    REPLACED("REPLACED", "Replaced");

    companion object {
        fun fromCode(code: String?): LoyaltyCardStatus =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: NOT_ACTIVATED
    }
}

enum class LoyaltyVoucherStatus(val code: String, val label: String) {
    ACTIVE("ACTIVE", "Active"),
    REDEEMED("REDEEMED", "Redeemed"),
    EXPIRED("EXPIRED", "Expired"),
    CANCELLED("CANCELLED", "Cancelled");

    companion object {
        fun fromCode(code: String?): LoyaltyVoucherStatus =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ACTIVE
    }
}

enum class LoyaltyStampTxType(val code: String, val label: String) {
    EARN("EARN", "Stamp Earned"),
    REVERSAL("REVERSAL", "Stamp Reversed"),
    MANUAL_ADJUSTMENT("MANUAL_ADJUSTMENT", "Manual Adjustment"),
    CYCLE_RESET("CYCLE_RESET", "New Cycle Started");

    companion object {
        fun fromCode(code: String?): LoyaltyStampTxType =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: EARN
    }
}

enum class QualifyingCalculationMethod(val code: String, val label: String) {
    ELIGIBLE_SUBTOTAL_AFTER_DISCOUNT(
        "ELIGIBLE_SUBTOTAL_AFTER_DISCOUNT",
        "Eligible Food Subtotal After Discount (Excl. Tax & Charges)"
    ),
    TOTAL_BEFORE_TAX_AND_CHARGES(
        "TOTAL_BEFORE_TAX_AND_CHARGES",
        "All Menu Items Subtotal After Discount (Excl. Tax & Charges)"
    ),
    NET_PAYABLE(
        "NET_PAYABLE",
        "Total Payable Invoice Amount"
    );

    companion object {
        fun fromCode(code: String?): QualifyingCalculationMethod =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: ELIGIBLE_SUBTOTAL_AFTER_DISCOUNT
    }
}

data class LoyaltyProgramSettingsRecord(
    val branchId: String,
    val programEnabled: Boolean = true,
    val minQualifyingAmountCents: Long = LoyaltyRuleConstants.DEFAULT_MIN_QUALIFYING_CENTS,
    val requiredStampsForReward: Int = LoyaltyRuleConstants.DEFAULT_REQUIRED_STAMPS,
    val rewardAmountCents: Long = LoyaltyRuleConstants.MAX_REWARD_CAP_CENTS,
    val validityMonths: Int = LoyaltyRuleConstants.DEFAULT_VALIDITY_MONTHS,
    val redemptionGraceDays: Int = LoyaltyRuleConstants.DEFAULT_GRACE_DAYS,
    val qualifyingCalculationMethod: QualifyingCalculationMethod = QualifyingCalculationMethod.ELIGIBLE_SUBTOTAL_AFTER_DISCOUNT,
    val eligibleCategoryIds: Set<String> = emptySet(),
    val redeemableCategoryIds: Set<String> = emptySet(),
    val excludeTaxFromQualifying: Boolean = true,
    val excludeServiceAndDeliveryFromQualifying: Boolean = true,
    val allowPartialVoucherUsage: Boolean = false,
    val cardPrefix: String = "AAC",
    val voucherPrefix: String = "RWD",
    val updatedBy: String = "System Default",
    val updatedAt: Long = 0L
)

data class LoyaltyCardRecord(
    val cardId: String,
    val branchId: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val cardNumber: String,
    val qrCodeToken: String,
    val status: LoyaltyCardStatus,
    val effectiveStatus: LoyaltyCardStatus,
    val issuedAt: Long,
    val firstQualifyingInvoiceAt: Long?,
    val expiresAt: Long?,
    val graceExpiresAt: Long?,
    val currentCycleStamps: Int,
    val requiredStamps: Int,
    val totalStampsEarned: Int,
    val totalRewardsEarned: Int,
    val totalRewardsRedeemed: Int,
    val cycleCount: Int,
    val replacesCardId: String?,
    val replacedByCardId: String?,
    val suspensionOrReplacementReason: String?,
    val updatedAt: Long
) {
    val canEarnStampsNow: Boolean
        get() = effectiveStatus != LoyaltyCardStatus.EXPIRED &&
            effectiveStatus != LoyaltyCardStatus.SUSPENDED &&
            effectiveStatus != LoyaltyCardStatus.REPLACED
}

data class LoyaltyStampTransactionRecord(
    val stampTxId: String,
    val branchId: String,
    val cardId: String,
    val customerId: String,
    val orderId: String?,
    val invoiceNumber: String?,
    val txType: LoyaltyStampTxType,
    val stampsDelta: Int,
    val stampsAfter: Int,
    val qualifyingAmountCents: Long,
    val cycleNumber: Int,
    val reason: String,
    val approvedBy: String?,
    val actorName: String,
    val createdAt: Long,
    val isReversed: Boolean
)

data class LoyaltyRewardVoucherRecord(
    val voucherId: String,
    val branchId: String,
    val customerId: String,
    val cardId: String,
    val customerName: String = "",
    val cardNumber: String = "",
    val cycleNumber: Int,
    val voucherCode: String,
    val voucherReference: String,
    val qrCodeToken: String,
    val rewardValueCents: Long,
    val earnedFromOrderId: String?,
    val earnedFromInvoiceNumber: String?,
    val earnedAt: Long,
    val expiresAt: Long,
    val graceExpiresAt: Long,
    val status: LoyaltyVoucherStatus,
    val effectiveStatus: LoyaltyVoucherStatus,
    val redeemedOrderId: String?,
    val redeemedInvoiceNumber: String?,
    val redeemedAt: Long?,
    val redeemedAmountCents: Long,
    val actualPromoCostCents: Long,
    val cancelledReason: String?,
    val notes: String
) {
    val isRedeemableNow: Boolean
        get() = effectiveStatus == LoyaltyVoucherStatus.ACTIVE

    val issuedAt: Long
        get() = earnedAt

    val cardExpiresAt: Long
        get() = expiresAt
}

data class LoyaltyRedemptionRecord(
    val redemptionId: String,
    val branchId: String,
    val voucherId: String,
    val voucherCode: String,
    val cardId: String,
    val customerId: String,
    val orderId: String,
    val invoiceNumber: String,
    val voucherFaceValueCents: Long,
    val discountAppliedCents: Long,
    val actualCogsCostCents: Long,
    val eligibleSubtotalCents: Long,
    val redeemedByName: String,
    val createdAt: Long
)

data class LoyaltyAuditLogRecord(
    val logId: String,
    val branchId: String,
    val actorUid: String,
    val actorEmail: String,
    val actorRole: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val details: String,
    val createdAt: Long
)

data class CustomerLoyaltyBundle(
    val customer: CustomerProfileRecord,
    val activeCard: LoyaltyCardRecord?,
    val cardHistory: List<LoyaltyCardRecord>,
    val stampTransactions: List<LoyaltyStampTransactionRecord>,
    val vouchers: List<LoyaltyRewardVoucherRecord>,
    val redemptions: List<LoyaltyRedemptionRecord>,
    val qualifyingOrders: List<PosOrderRecord>,
    val settings: LoyaltyProgramSettingsRecord
)

data class PosLoyaltyPreview(
    val card: LoyaltyCardRecord,
    val activeVouchers: List<LoyaltyRewardVoucherRecord>,
    val qualifyingAmountCents: Long,
    val minQualifyingCents: Long,
    val willEarnStamp: Boolean,
    val projectedStampsAfter: Int,
    val willUnlockVoucherOnCheckout: Boolean,
    val eligibilityReason: String
)

data class LoyaltyBranchReportRow(
    val branchId: String,
    val branchName: String,
    val enrolledCustomers: Int,
    val activeCards: Int,
    val expiredCards: Int,
    val rewardEligibleCards: Int,
    val stampsIssued: Int,
    val stampsReversed: Int,
    val vouchersEarned: Int,
    val vouchersRedeemed: Int,
    val vouchersExpiredOrUnused: Int,
    val promoDiscountTotalCents: Long,
    val actualPromoCogsCents: Long,
    val netPaidSalesWithLoyaltyCents: Long
)

data class LoyaltyAnalyticsReport(
    val branchScopeLabel: String,
    val totalEnrolledCustomers: Int,
    val activeCardsCount: Int,
    val notActivatedCardsCount: Int,
    val rewardEligibleCardsCount: Int,
    val expiredCardsCount: Int,
    val suspendedOrReplacedCardsCount: Int,
    val newEnrollmentsInPeriod: Int,
    val stampsIssuedInPeriod: Int,
    val stampsReversedInPeriod: Int,
    val rewardsEarnedInPeriod: Int,
    val rewardsRedeemedInPeriod: Int,
    val unusedActiveVouchersCount: Int,
    val expiredVouchersCount: Int,
    val cancelledVouchersCount: Int,
    val totalPromoDiscountGivenCents: Long,
    val totalActualPromoCostCents: Long,
    val totalPaidSalesRevenueCents: Long,
    val totalRegularDiscountsCents: Long,
    val branchRows: List<LoyaltyBranchReportRow>,
    val topCustomers: List<CustomerLoyaltyLeaderboardRow>
)

data class CustomerLoyaltyLeaderboardRow(
    val customerId: String,
    val customerName: String,
    val cardNumber: String,
    val status: LoyaltyCardStatus,
    val currentStamps: Int,
    val totalStampsEarned: Int,
    val totalRewardsEarned: Int,
    val totalRewardsRedeemed: Int,
    val totalSpendCents: Long
)

object LoyaltyRuleConstants {
    const val DEFAULT_MIN_QUALIFYING_CENTS = 150_000L // PKR 1,500.00
    const val MAX_REWARD_CAP_CENTS = 150_000L         // PKR 1,500.00 hard cap
    const val DEFAULT_REQUIRED_STAMPS = 6
    const val DEFAULT_VALIDITY_MONTHS = 3
    const val DEFAULT_GRACE_DAYS = 30
    const val MILLIS_PER_DAY = 86_400_000L
}

object LoyaltyCalendarCalculator {

    /**
     * Adds exact calendar months using local Calendar rules (handles 28/29/30/31-day months & leap years).
     * Example: Jan 31 + 3 months -> Apr 30; Nov 30 + 3 months -> Feb 28 (or Feb 29 on leap year).
     */
    fun addCalendarMonths(startMillis: Long, months: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startMillis
        cal.add(Calendar.MONTH, months.coerceAtLeast(1))
        return cal.timeInMillis
    }

    fun addDays(startMillis: Long, days: Int): Long {
        if (days <= 0) return startMillis
        val cal = Calendar.getInstance()
        cal.timeInMillis = startMillis
        cal.add(Calendar.DAY_OF_YEAR, days)
        return cal.timeInMillis
    }

    fun computeEffectiveCardStatus(
        storedStatus: LoyaltyCardStatus,
        expiresAt: Long?,
        currentCycleStamps: Int,
        requiredStamps: Int,
        hasActiveVoucher: Boolean,
        nowMillis: Long
    ): LoyaltyCardStatus {
        return when (storedStatus) {
            LoyaltyCardStatus.SUSPENDED -> LoyaltyCardStatus.SUSPENDED
            LoyaltyCardStatus.REPLACED -> LoyaltyCardStatus.REPLACED
            LoyaltyCardStatus.NOT_ACTIVATED -> LoyaltyCardStatus.NOT_ACTIVATED
            else -> {
                if (expiresAt != null && nowMillis > expiresAt) {
                    LoyaltyCardStatus.EXPIRED
                } else if (hasActiveVoucher || currentCycleStamps >= requiredStamps) {
                    LoyaltyCardStatus.REWARD_ELIGIBLE
                } else if (storedStatus == LoyaltyCardStatus.COMPLETED) {
                    LoyaltyCardStatus.COMPLETED
                } else {
                    LoyaltyCardStatus.ACTIVE
                }
            }
        }
    }

    fun computeEffectiveVoucherStatus(
        storedStatus: LoyaltyVoucherStatus,
        graceExpiresAt: Long,
        nowMillis: Long
    ): LoyaltyVoucherStatus {
        return when (storedStatus) {
            LoyaltyVoucherStatus.REDEEMED -> LoyaltyVoucherStatus.REDEEMED
            LoyaltyVoucherStatus.CANCELLED -> LoyaltyVoucherStatus.CANCELLED
            LoyaltyVoucherStatus.EXPIRED -> LoyaltyVoucherStatus.EXPIRED
            LoyaltyVoucherStatus.ACTIVE -> if (nowMillis > graceExpiresAt) LoyaltyVoucherStatus.EXPIRED else LoyaltyVoucherStatus.ACTIVE
        }
    }

    fun computeQualifyingOrderAmountCents(
        items: List<CartItem>,
        totals: BillingTotals,
        voucherDiscountCents: Long,
        settings: LoyaltyProgramSettingsRecord
    ): Long {
        if (items.isEmpty()) return 0L
        val rawSubtotal = totals.subtotalCents.coerceAtLeast(0L)
        if (rawSubtotal <= 0L) return 0L

        val eligibleRawSubtotal = when (settings.qualifyingCalculationMethod) {
            QualifyingCalculationMethod.NET_PAYABLE -> return (totals.totalPayableCents - voucherDiscountCents).coerceAtLeast(0L)
            QualifyingCalculationMethod.TOTAL_BEFORE_TAX_AND_CHARGES -> rawSubtotal
            QualifyingCalculationMethod.ELIGIBLE_SUBTOTAL_AFTER_DISCOUNT -> {
                if (settings.eligibleCategoryIds.isEmpty()) {
                    rawSubtotal
                } else {
                    items.filter { it.product.categoryId in settings.eligibleCategoryIds }
                        .sumOf { it.lineTotalCents }
                }
            }
        }

        if (eligibleRawSubtotal <= 0L) return 0L

        // Allocate order discounts proportionally to eligible subtotal; exclude tax & delivery/service charges by default
        val ratio = eligibleRawSubtotal.toDouble() / rawSubtotal.toDouble()
        val allocatedOrderDiscount = (totals.discountCents * ratio).roundToLong()
        val afterOrderDiscount = (eligibleRawSubtotal - allocatedOrderDiscount - voucherDiscountCents.coerceAtLeast(0L))
            .coerceAtLeast(0L)

        var qualifying = afterOrderDiscount
        if (!settings.excludeTaxFromQualifying) {
            qualifying += (totals.taxCents * ratio).roundToLong()
        }
        if (!settings.excludeServiceAndDeliveryFromQualifying) {
            qualifying += (totals.serviceChargeCents * ratio).roundToLong()
        }
        return qualifying.coerceAtLeast(0L)
    }
}

fun centsToPkr(cents: Long): String = PkrCurrencyFormatter.formatMinorUnits(cents)
