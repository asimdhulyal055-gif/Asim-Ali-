package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.LoyaltyRuleConstants

@Entity(tableName = "loyalty_program_settings")
data class LoyaltyProgramSettingsEntity(
    @PrimaryKey val branchId: String,
    val programEnabled: Boolean = true,
    val minQualifyingAmountCents: Long = LoyaltyRuleConstants.DEFAULT_MIN_QUALIFYING_CENTS,
    val requiredStampsForReward: Int = LoyaltyRuleConstants.DEFAULT_REQUIRED_STAMPS,
    val rewardAmountCents: Long = LoyaltyRuleConstants.MAX_REWARD_CAP_CENTS,
    val validityMonths: Int = LoyaltyRuleConstants.DEFAULT_VALIDITY_MONTHS,
    val redemptionGraceDays: Int = LoyaltyRuleConstants.DEFAULT_GRACE_DAYS,
    val qualifyingCalculationMethod: String = "ELIGIBLE_SUBTOTAL_AFTER_DISCOUNT",
    val eligibleCategoryIdsCsv: String = "",
    val redeemableCategoryIdsCsv: String = "",
    val excludeTaxFromQualifying: Boolean = true,
    val excludeServiceAndDeliveryFromQualifying: Boolean = true,
    val allowPartialVoucherUsage: Boolean = false,
    val cardPrefix: String = "AAC",
    val voucherPrefix: String = "RWD",
    val updatedBy: String = "System Default",
    val updatedAt: Long = 0L
)

@Entity(
    tableName = "loyalty_cards",
    indices = [
        Index(value = ["cardNumber"], unique = true),
        Index(value = ["qrCodeToken"], unique = true),
        Index(value = ["customerId"]),
        Index(value = ["branchId", "status"])
    ]
)
data class LoyaltyCardEntity(
    @PrimaryKey val cardId: String,
    val branchId: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val cardNumber: String,
    val qrCodeToken: String,
    val status: String,
    val issuedAt: Long,
    val firstQualifyingInvoiceAt: Long? = null,
    val expiresAt: Long? = null,
    val graceExpiresAt: Long? = null,
    val currentCycleStamps: Int = 0,
    val totalStampsEarned: Int = 0,
    val totalRewardsEarned: Int = 0,
    val totalRewardsRedeemed: Int = 0,
    val cycleCount: Int = 1,
    val replacesCardId: String? = null,
    val replacedByCardId: String? = null,
    val suspensionOrReplacementReason: String? = null,
    val updatedAt: Long
)

@Entity(
    tableName = "loyalty_stamp_transactions",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["uniqueOrderEarnKey"], unique = true),
        Index(value = ["cardId", "createdAt"]),
        Index(value = ["customerId", "createdAt"]),
        Index(value = ["orderId"])
    ]
)
data class LoyaltyStampTransactionEntity(
    @PrimaryKey val stampTxId: String,
    val branchId: String,
    val cardId: String,
    val customerId: String,
    val orderId: String?,
    val invoiceNumber: String?,
    val txType: String, // EARN, REVERSAL, MANUAL_ADJUSTMENT, CYCLE_RESET
    val stampsDelta: Int,
    val stampsAfter: Int,
    val qualifyingAmountCents: Long,
    val cycleNumber: Int,
    val idempotencyKey: String,
    val uniqueOrderEarnKey: String?, // "EARN_$orderId" for EARN rows to guarantee <= 1 stamp per invoice
    val reversedByTxId: String? = null,
    val reason: String,
    val approvedBy: String? = null,
    val actorUid: String,
    val actorName: String,
    val createdAt: Long
)

@Entity(
    tableName = "loyalty_reward_vouchers",
    indices = [
        Index(value = ["voucherCode"], unique = true),
        Index(value = ["qrCodeToken"], unique = true),
        Index(value = ["customerId", "status"]),
        Index(value = ["cardId"]),
        Index(value = ["earnedFromOrderId"])
    ]
)
data class LoyaltyRewardVoucherEntity(
    @PrimaryKey val voucherId: String,
    val branchId: String,
    val customerId: String,
    val cardId: String,
    val cycleNumber: Int,
    val voucherCode: String,
    val voucherReference: String,
    val qrCodeToken: String,
    val rewardValueCents: Long,
    val earnedFromOrderId: String?,
    val earnedFromInvoiceNumber: String?,
    val earnedStampTxId: String,
    val earnedAt: Long,
    val expiresAt: Long,
    val graceExpiresAt: Long,
    val status: String, // ACTIVE, REDEEMED, EXPIRED, CANCELLED
    val redeemedOrderId: String? = null,
    val redeemedInvoiceNumber: String? = null,
    val redeemedAt: Long? = null,
    val redeemedAmountCents: Long = 0L,
    val actualPromoCostCents: Long = 0L,
    val cancelledReason: String? = null,
    val notes: String = ""
)

@Entity(
    tableName = "loyalty_redemptions",
    indices = [
        Index(value = ["voucherId"], unique = true),
        Index(value = ["orderId"]),
        Index(value = ["customerId"]),
        Index(value = ["branchId", "createdAt"])
    ]
)
data class LoyaltyRedemptionEntity(
    @PrimaryKey val redemptionId: String,
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
    val redeemedByUid: String,
    val redeemedByName: String,
    val createdAt: Long
)

@Entity(
    tableName = "loyalty_audit_logs",
    indices = [
        Index(value = ["branchId", "createdAt"]),
        Index(value = ["entityId"])
    ]
)
data class LoyaltyAuditLogEntity(
    @PrimaryKey val logId: String,
    val branchId: String,
    val actorUid: String,
    val actorEmail: String,
    val actorRole: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val details: String,
    val previousValueJson: String = "",
    val newValueJson: String = "",
    val createdAt: Long
)
