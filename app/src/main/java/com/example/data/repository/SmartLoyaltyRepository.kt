package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AaCraveDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.LoyaltyAuditLogEntity
import com.example.data.local.LoyaltyCardEntity
import com.example.data.local.LoyaltyProgramSettingsEntity
import com.example.data.local.LoyaltyRedemptionEntity
import com.example.data.local.LoyaltyRewardVoucherEntity
import com.example.data.local.LoyaltyStampTransactionEntity
import com.example.data.local.PosOrderEntity
import com.example.data.local.SyncQueueEntity
import com.example.domain.model.BillingTotals
import com.example.domain.model.CartItem
import com.example.domain.model.CustomerLoyaltyBundle
import com.example.domain.model.CustomerLoyaltyLeaderboardRow
import com.example.domain.model.CustomerProfileRecord
import com.example.domain.model.LoyaltyAnalyticsReport
import com.example.domain.model.LoyaltyAuditLogRecord
import com.example.domain.model.LoyaltyBranchReportRow
import com.example.domain.model.LoyaltyCalendarCalculator
import com.example.domain.model.LoyaltyCardRecord
import com.example.domain.model.LoyaltyCardStatus
import com.example.domain.model.LoyaltyProgramSettingsRecord
import com.example.domain.model.LoyaltyRedemptionRecord
import com.example.domain.model.LoyaltyRewardVoucherRecord
import com.example.domain.model.LoyaltyRuleConstants
import com.example.domain.model.LoyaltyStampTransactionRecord
import com.example.domain.model.LoyaltyStampTxType
import com.example.domain.model.LoyaltyVoucherStatus
import com.example.domain.model.OrderPriority
import com.example.domain.model.OrderStatus
import com.example.domain.model.OrderType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PosLoyaltyPreview
import com.example.domain.model.PosOrderRecord
import com.example.domain.model.QualifyingCalculationMethod
import com.example.domain.model.UserSession
import com.example.domain.model.centsToPkr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

data class ValidatedVoucherApplication(
    val voucher: LoyaltyRewardVoucherEntity,
    val card: LoyaltyCardEntity,
    val discountAppliedCents: Long,
    val actualPromoCostCents: Long,
    val eligibleSubtotalCents: Long
)

data class PosLoyaltyOutcome(
    val cardId: String? = null,
    val cardNumber: String? = null,
    val stampChange: Int = 0,
    val stampsAfter: Int = 0,
    val voucherId: String? = null,
    val voucherCode: String? = null,
    val voucherDiscountCents: Long = 0L,
    val promoCostCents: Long = 0L,
    val unlockedVoucher: LoyaltyRewardVoucherRecord? = null,
    val statusNote: String? = null
)

class SmartLoyaltyRepository(private val database: AaCraveDatabase) {

    private val smartDao = database.smartLoyaltyDao()
    private val posDao = database.posDao()
    private val branchDao = database.branchDao()
    private val auditLogDao = database.auditLogDao()
    private val syncQueueDao = database.syncQueueDao()

    fun observeSettings(branchId: String): Flow<LoyaltyProgramSettingsRecord> =
        smartDao.observeSettings(branchId).map { entity ->
            toSettingsRecord(entity ?: LoyaltyProgramSettingsEntity(branchId = branchId))
        }

    fun observeCards(branchId: String, allBranches: Boolean): Flow<List<LoyaltyCardRecord>> {
        val cardsFlow = if (allBranches) smartDao.observeAllCards() else smartDao.observeCardsForBranch(branchId)
        val vouchersFlow = if (allBranches) smartDao.observeAllVouchers() else smartDao.observeVouchersForBranch(branchId)
        return combine(cardsFlow, vouchersFlow, smartDao.observeSettings(branchId)) { cards, vouchers, settings ->
            val now = System.currentTimeMillis()
            val req = (settings?.requiredStampsForReward ?: LoyaltyRuleConstants.DEFAULT_REQUIRED_STAMPS).coerceAtLeast(1)
            val activeVoucherCardIds = vouchers
                .filter {
                    LoyaltyCalendarCalculator.computeEffectiveVoucherStatus(
                        LoyaltyVoucherStatus.fromCode(it.status),
                        it.graceExpiresAt,
                        now
                    ) == LoyaltyVoucherStatus.ACTIVE
                }
                .map { it.cardId }
                .toSet()
            cards.map { c -> toCardRecord(c, req, c.cardId in activeVoucherCardIds, now) }
        }
    }

    fun observeVouchers(branchId: String, allBranches: Boolean): Flow<List<LoyaltyRewardVoucherRecord>> {
        val vouchersFlow = if (allBranches) smartDao.observeAllVouchers() else smartDao.observeVouchersForBranch(branchId)
        val cardsFlow = if (allBranches) smartDao.observeAllCards() else smartDao.observeCardsForBranch(branchId)
        return combine(vouchersFlow, cardsFlow) { list, cards ->
            val now = System.currentTimeMillis()
            val cardMap = cards.associateBy { it.cardId }
            list.map { v ->
                val card = cardMap[v.cardId]
                toVoucherRecord(
                    e = v,
                    nowMillis = now,
                    customerName = card?.customerName ?: "",
                    cardNumber = card?.cardNumber ?: ""
                )
            }
        }
    }

    fun observeStampTransactions(branchId: String, allBranches: Boolean): Flow<List<LoyaltyStampTransactionRecord>> {
        val flow = if (allBranches) smartDao.observeAllStampTransactions() else smartDao.observeStampTransactionsForBranch(branchId)
        return flow.map { list -> list.map(::toStampTxRecord) }
    }

    fun observeRedemptions(branchId: String, allBranches: Boolean): Flow<List<LoyaltyRedemptionRecord>> {
        val flow = if (allBranches) smartDao.observeAllRedemptions() else smartDao.observeRedemptionsForBranch(branchId)
        return flow.map { list -> list.map(::toRedemptionRecord) }
    }

    fun observeAuditLogs(branchId: String, allBranches: Boolean): Flow<List<LoyaltyAuditLogRecord>> {
        val flow = if (allBranches) smartDao.observeAllAuditLogs() else smartDao.observeAuditLogsForBranch(branchId)
        return flow.map { list ->
            list.map {
                LoyaltyAuditLogRecord(
                    logId = it.logId,
                    branchId = it.branchId,
                    actorUid = it.actorUid,
                    actorEmail = it.actorEmail,
                    actorRole = it.actorRole,
                    entityType = it.entityType,
                    entityId = it.entityId,
                    action = it.action,
                    details = it.details,
                    createdAt = it.createdAt
                )
            }
        }
    }

    suspend fun getCustomerLoyaltyBundle(
        customerId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): CustomerLoyaltyBundle? {
        val cust = posDao.getCustomerById(customerId) ?: return null
        val settings = toSettingsRecord(
            smartDao.getSettings(cust.branchId) ?: LoyaltyProgramSettingsEntity(branchId = cust.branchId)
        )
        val historyRaw = smartDao.getCardHistoryForCustomer(customerId)
        val cardMap = historyRaw.associateBy { it.cardId }
        val vouchersRaw = smartDao.getVouchersForCustomer(customerId)
        val voucherRecords = vouchersRaw.map { v ->
            val card = cardMap[v.cardId]
            toVoucherRecord(
                e = v,
                nowMillis = nowMillis,
                customerName = card?.customerName ?: cust.name,
                cardNumber = card?.cardNumber ?: ""
            )
        }
        val activeVoucherCardIds = voucherRecords
            .filter { it.effectiveStatus == LoyaltyVoucherStatus.ACTIVE }
            .map { it.cardId }
            .toSet()
        val cardHistory = historyRaw.map {
            toCardRecord(it, settings.requiredStampsForReward, it.cardId in activeVoucherCardIds, nowMillis)
        }
        val activeCard = cardHistory.firstOrNull { it.status != LoyaltyCardStatus.REPLACED }
        val stamps = smartDao.getStampTransactionsForCustomer(customerId).map(::toStampTxRecord)
        val redemptions = smartDao.getRedemptionsForCustomer(customerId).map(::toRedemptionRecord)
        val branchName = branchDao.getBranchById(cust.branchId)?.name ?: "AA CRAVE"
        val orders = smartDao.getOrdersForCustomerOrCard(activeCard?.cardId, customerId).map { o ->
            PosOrderRecord(
                orderId = o.orderId,
                branchId = o.branchId,
                branchName = branchName,
                orderNumber = o.orderNumber,
                invoiceNumber = o.invoiceNumber,
                kotNumber = o.kotNumber,
                orderType = OrderType.entries.firstOrNull { it.name == o.orderType } ?: OrderType.DINE_IN,
                tableNumber = o.tableNumber,
                priority = OrderPriority.entries.firstOrNull { it.name == o.priority } ?: OrderPriority.NORMAL,
                customerName = o.customerName,
                subtotalCents = o.subtotalCents,
                discountCents = o.discountCents,
                taxCents = o.taxCents,
                serviceChargeCents = o.serviceChargeCents,
                totalCents = o.totalCents,
                paidCents = o.paidCents,
                remainingBalanceCents = o.remainingBalanceCents,
                changeCents = o.changeCents,
                paymentMethod = PaymentMethod.entries.firstOrNull { it.name == o.paymentMethod } ?: PaymentMethod.CASH,
                paymentBreakdown = o.paymentBreakdown,
                status = OrderStatus.entries.firstOrNull { it.name == o.status } ?: OrderStatus.CONFIRMED,
                reprintInvoiceCount = o.reprintInvoiceCount,
                reprintKotCount = o.reprintKotCount,
                cashierName = o.cashierName,
                approvedBy = o.approvedBy,
                cancellationReason = o.cancellationReason,
                createdAt = o.createdAt,
                items = emptyList(),
                loyaltyCardId = o.loyaltyCardId,
                loyaltyCardNumber = o.loyaltyCardNumber,
                loyaltyStampChange = o.loyaltyStampChange,
                loyaltyStampsAfter = o.loyaltyStampsAfter,
                loyaltyVoucherId = o.loyaltyVoucherId,
                loyaltyVoucherCode = o.loyaltyVoucherCode,
                loyaltyVoucherDiscountCents = o.loyaltyVoucherDiscountCents,
                loyaltyPromoCostCents = o.loyaltyPromoCostCents
            )
        }
        val profile = CustomerProfileRecord(
            customerId = cust.customerId,
            branchId = cust.branchId,
            name = cust.name,
            phone = cust.phone,
            email = cust.email,
            address = cust.address,
            notes = cust.notes,
            loyaltyPoints = cust.loyaltyPoints,
            tier = cust.tier,
            discountPercent = cust.discountPercent,
            totalPurchasesCents = cust.totalPurchasesCents,
            lastOrderAt = cust.lastOrderAt,
            accountBalanceCents = cust.accountBalanceCents,
            feedbackSummary = cust.feedbackSummary,
            createdAt = cust.createdAt
        )
        return CustomerLoyaltyBundle(
            customer = profile,
            activeCard = activeCard,
            cardHistory = cardHistory,
            stampTransactions = stamps,
            vouchers = voucherRecords,
            redemptions = redemptions,
            qualifyingOrders = orders,
            settings = settings
        )
    }

    suspend fun enrollCustomerCard(
        session: UserSession,
        customerId: String,
        customCardNumber: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<LoyaltyCardRecord> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canManageLoyaltyCards && !session.capabilities.canScanAndRedeemLoyalty) {
            return Result.failure(
                SecurityException("Role ${session.role.displayName} is not authorized to enroll loyalty cards.")
            )
        }
        return try {
            val created = database.withTransaction {
                val cust = posDao.getCustomerById(customerId)
                    ?: throw IllegalArgumentException("Customer not found.")
                ErpAuthorizationGuard.verifyBranchAccess(session, cust.branchId)?.let { throw it }
                val existing = smartDao.getNonReplacedCardForCustomer(customerId)
                if (existing != null) {
                    throw IllegalStateException(
                        "Customer already has a loyalty card (${existing.cardNumber} • ${existing.status}). Renew or replace the existing card instead."
                    )
                }
                val settingsEntity = smartDao.getSettings(cust.branchId)
                    ?: LoyaltyProgramSettingsEntity(branchId = cust.branchId).also { smartDao.insertSettingsIfNew(it) }
                val cardNumber = generateUniqueCardNumber(
                    prefix = settingsEntity.cardPrefix,
                    branchId = cust.branchId,
                    custom = customCardNumber
                )
                val qrToken = generateUniqueQrToken("AACLC")
                val cardId = "lcard_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                val entity = LoyaltyCardEntity(
                    cardId = cardId,
                    branchId = cust.branchId,
                    customerId = cust.customerId,
                    customerName = cust.name,
                    customerPhone = cust.phone,
                    cardNumber = cardNumber,
                    qrCodeToken = qrToken,
                    status = LoyaltyCardStatus.NOT_ACTIVATED.code,
                    issuedAt = nowMillis,
                    firstQualifyingInvoiceAt = null,
                    expiresAt = null,
                    graceExpiresAt = null,
                    currentCycleStamps = 0,
                    totalStampsEarned = 0,
                    totalRewardsEarned = 0,
                    totalRewardsRedeemed = 0,
                    cycleCount = 1,
                    updatedAt = nowMillis
                )
                val inserted = smartDao.insertCardIfNew(entity)
                if (inserted == -1L) {
                    throw IllegalStateException("Duplicate loyalty card number or QR token detected.")
                }
                recordLoyaltyAudit(
                    session = session,
                    branchId = cust.branchId,
                    entityType = "LOYALTY_CARD",
                    entityId = cardId,
                    action = "CARD_ENROLLED",
                    details = "Enrolled ${cust.name} (${cust.phone}) with card $cardNumber [NOT_ACTIVATED until 1st qualifying paid invoice]",
                    newValueJson = """{"cardNumber":"$cardNumber","status":"NOT_ACTIVATED","customerId":"$customerId"}""",
                    nowMillis = nowMillis
                )
                enqueueSync(
                    branchId = cust.branchId,
                    entityType = "LOYALTY_CARD",
                    entityId = cardId,
                    payload = """{"cardId":"$cardId","cardNumber":"$cardNumber","customerId":"$customerId","status":"NOT_ACTIVATED"}""",
                    nowMillis = nowMillis
                )
                toCardRecord(entity, settingsEntity.requiredStampsForReward, false, nowMillis)
            }
            Result.success(created)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun lookupByQrOrQuery(
        query: String,
        branchIdFilter: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): List<LoyaltyCardRecord> {
        val cleaned = query.trim()
        if (cleaned.isBlank()) return emptyList()

        // 1. Direct card QR token or exact card number match
        val directCard = smartDao.getCardByQrToken(cleaned) ?: smartDao.getCardByNumber(cleaned)
        if (directCard != null) {
            if (branchIdFilter != null && directCard.branchId != branchIdFilter) return emptyList()
            val settings = smartDao.getSettings(directCard.branchId) ?: LoyaltyProgramSettingsEntity(directCard.branchId)
            val vouchers = smartDao.getVouchersForCustomer(directCard.customerId)
            val hasActiveVoucher = vouchers.any {
                it.cardId == directCard.cardId &&
                    LoyaltyCalendarCalculator.computeEffectiveVoucherStatus(
                        LoyaltyVoucherStatus.fromCode(it.status),
                        it.graceExpiresAt,
                        nowMillis
                    ) == LoyaltyVoucherStatus.ACTIVE
            }
            return listOf(toCardRecord(directCard, settings.requiredStampsForReward, hasActiveVoucher, nowMillis))
        }

        // 2. Direct voucher QR token or voucher code match -> resolve to card
        val directVoucher = smartDao.getVoucherByCodeOrQr(cleaned)
        if (directVoucher != null) {
            val card = smartDao.getCardById(directVoucher.cardId)
            if (card != null && (branchIdFilter == null || card.branchId == branchIdFilter)) {
                val settings = smartDao.getSettings(card.branchId) ?: LoyaltyProgramSettingsEntity(card.branchId)
                val vouchers = smartDao.getVouchersForCustomer(card.customerId)
                val hasActiveVoucher = vouchers.any {
                    it.cardId == card.cardId &&
                        LoyaltyCalendarCalculator.computeEffectiveVoucherStatus(
                            LoyaltyVoucherStatus.fromCode(it.status),
                            it.graceExpiresAt,
                            nowMillis
                        ) == LoyaltyVoucherStatus.ACTIVE
                }
                return listOf(toCardRecord(card, settings.requiredStampsForReward, hasActiveVoucher, nowMillis))
            }
        }

        // 3. Name / Phone / Card substring search
        val digitsOnly = cleaned.filter { it.isDigit() }.ifBlank { "__NO_DIGITS_MATCH__" }
        val matches = smartDao.searchCards(cleaned, digitsOnly)
            .filter { branchIdFilter == null || it.branchId == branchIdFilter }
        val reqCache = mutableMapOf<String, Int>()
        return matches.map { c ->
            val req = reqCache.getOrPut(c.branchId) {
                smartDao.getSettings(c.branchId)?.requiredStampsForReward ?: LoyaltyRuleConstants.DEFAULT_REQUIRED_STAMPS
            }
            val vouchers = smartDao.getVouchersForCustomer(c.customerId)
            val hasActive = vouchers.any {
                it.cardId == c.cardId &&
                    LoyaltyCalendarCalculator.computeEffectiveVoucherStatus(
                        LoyaltyVoucherStatus.fromCode(it.status),
                        it.graceExpiresAt,
                        nowMillis
                    ) == LoyaltyVoucherStatus.ACTIVE
            }
            toCardRecord(c, req, hasActive, nowMillis)
        }
    }

    suspend fun findVoucherByCodeOrQr(
        codeOrQr: String,
        nowMillis: Long = System.currentTimeMillis()
    ): LoyaltyRewardVoucherRecord? {
        val cleaned = codeOrQr.trim()
        if (cleaned.isBlank()) return null
        val entity = smartDao.getVoucherByCodeOrQr(cleaned) ?: return null
        val card = smartDao.getCardById(entity.cardId)
        return toVoucherRecord(
            e = entity,
            nowMillis = nowMillis,
            customerName = card?.customerName ?: "",
            cardNumber = card?.cardNumber ?: ""
        )
    }

    suspend fun suspendCard(
        session: UserSession,
        cardId: String,
        reason: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<LoyaltyCardRecord> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canManageLoyaltyCards) {
            return Result.failure(SecurityException("Only Super Admin, Owner, or Branch Manager can suspend loyalty cards."))
        }
        val cleanReason = reason.trim()
        if (cleanReason.length < 3) {
            return Result.failure(IllegalArgumentException("Suspension reason must be at least 3 characters."))
        }
        return try {
            val updated = database.withTransaction {
                val card = smartDao.getCardById(cardId) ?: throw IllegalArgumentException("Loyalty card not found.")
                ErpAuthorizationGuard.verifyBranchAccess(session, card.branchId)?.let { throw it }
                if (card.status == LoyaltyCardStatus.REPLACED.code) {
                    throw IllegalStateException("Replaced cards cannot be modified.")
                }
                val next = card.copy(
                    status = LoyaltyCardStatus.SUSPENDED.code,
                    suspensionOrReplacementReason = cleanReason,
                    updatedAt = nowMillis
                )
                smartDao.upsertCard(next)
                recordLoyaltyAudit(
                    session = session,
                    branchId = card.branchId,
                    entityType = "LOYALTY_CARD",
                    entityId = cardId,
                    action = "CARD_SUSPENDED",
                    details = "Suspended ${card.cardNumber}: $cleanReason",
                    previousValueJson = """{"status":"${card.status}"}""",
                    newValueJson = """{"status":"SUSPENDED","reason":${JSONObject.quote(cleanReason)}}""",
                    nowMillis = nowMillis
                )
                val req = smartDao.getSettings(card.branchId)?.requiredStampsForReward ?: LoyaltyRuleConstants.DEFAULT_REQUIRED_STAMPS
                toCardRecord(next, req, false, nowMillis)
            }
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reactivateCard(
        session: UserSession,
        cardId: String,
        reason: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<LoyaltyCardRecord> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canManageLoyaltyCards) {
            return Result.failure(SecurityException("Only Super Admin, Owner, or Branch Manager can reactivate loyalty cards."))
        }
        val cleanReason = reason.trim()
        if (cleanReason.length < 3) {
            return Result.failure(IllegalArgumentException("Reactivation reason is required."))
        }
        return try {
            val updated = database.withTransaction {
                val card = smartDao.getCardById(cardId) ?: throw IllegalArgumentException("Loyalty card not found.")
                ErpAuthorizationGuard.verifyBranchAccess(session, card.branchId)?.let { throw it }
                if (card.status == LoyaltyCardStatus.REPLACED.code) {
                    throw IllegalStateException("Replaced cards are permanently deactivated.")
                }
                val settings = smartDao.getSettings(card.branchId) ?: LoyaltyProgramSettingsEntity(card.branchId)
                val vouchers = smartDao.getVouchersForCustomer(card.customerId)
                val hasActiveVoucher = vouchers.any {
                    it.cardId == card.cardId &&
                        LoyaltyCalendarCalculator.computeEffectiveVoucherStatus(
                            LoyaltyVoucherStatus.fromCode(it.status),
                            it.graceExpiresAt,
                            nowMillis
                        ) == LoyaltyVoucherStatus.ACTIVE
                }
                // Preserve exact original activation and expiry dates; never silently extend expiry!
                val restoredStatus = when {
                    card.firstQualifyingInvoiceAt == null -> LoyaltyCardStatus.NOT_ACTIVATED
                    card.expiresAt != null && nowMillis > card.expiresAt -> LoyaltyCardStatus.EXPIRED
                    hasActiveVoucher || card.currentCycleStamps >= settings.requiredStampsForReward -> LoyaltyCardStatus.REWARD_ELIGIBLE
                    else -> LoyaltyCardStatus.ACTIVE
                }
                val next = card.copy(
                    status = restoredStatus.code,
                    suspensionOrReplacementReason = cleanReason,
                    updatedAt = nowMillis
                )
                smartDao.upsertCard(next)
                recordLoyaltyAudit(
                    session = session,
                    branchId = card.branchId,
                    entityType = "LOYALTY_CARD",
                    entityId = cardId,
                    action = "CARD_REACTIVATED",
                    details = "Reactivated ${card.cardNumber} as ${restoredStatus.label}: $cleanReason",
                    previousValueJson = """{"status":"${card.status}"}""",
                    newValueJson = """{"status":"${restoredStatus.code}"}""",
                    nowMillis = nowMillis
                )
                toCardRecord(next, settings.requiredStampsForReward, hasActiveVoucher, nowMillis)
            }
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun replaceCard(
        session: UserSession,
        oldCardId: String,
        reason: String,
        newCustomCardNumber: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<LoyaltyCardRecord> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canManageLoyaltyCards) {
            return Result.failure(SecurityException("Only Super Admin, Owner, or Branch Manager can replace loyalty cards."))
        }
        val cleanReason = reason.trim()
        if (cleanReason.length < 3) {
            return Result.failure(IllegalArgumentException("Replacement reason is required."))
        }
        return try {
            val replacement = database.withTransaction {
                val oldCard = smartDao.getCardById(oldCardId) ?: throw IllegalArgumentException("Loyalty card not found.")
                ErpAuthorizationGuard.verifyBranchAccess(session, oldCard.branchId)?.let { throw it }
                if (oldCard.status == LoyaltyCardStatus.REPLACED.code) {
                    throw IllegalStateException("Card ${oldCard.cardNumber} has already been replaced.")
                }
                val settings = smartDao.getSettings(oldCard.branchId) ?: LoyaltyProgramSettingsEntity(oldCard.branchId)
                val newCardNumber = generateUniqueCardNumber(settings.cardPrefix, oldCard.branchId, newCustomCardNumber)
                val newQrToken = generateUniqueQrToken("AACLC")
                val newCardId = "lcard_${UUID.randomUUID().toString().replace("-", "").take(14)}"

                val vouchers = smartDao.getVouchersForCustomer(oldCard.customerId)
                val hasActiveVoucher = vouchers.any {
                    it.cardId == oldCard.cardId &&
                        LoyaltyCalendarCalculator.computeEffectiveVoucherStatus(
                            LoyaltyVoucherStatus.fromCode(it.status),
                            it.graceExpiresAt,
                            nowMillis
                        ) == LoyaltyVoucherStatus.ACTIVE
                }
                val carriedStatus = when {
                    oldCard.status == LoyaltyCardStatus.SUSPENDED.code -> LoyaltyCardStatus.ACTIVE
                    oldCard.firstQualifyingInvoiceAt == null -> LoyaltyCardStatus.NOT_ACTIVATED
                    oldCard.expiresAt != null && nowMillis > oldCard.expiresAt -> LoyaltyCardStatus.EXPIRED
                    hasActiveVoucher || oldCard.currentCycleStamps >= settings.requiredStampsForReward -> LoyaltyCardStatus.REWARD_ELIGIBLE
                    else -> LoyaltyCardStatus.ACTIVE
                }

                smartDao.upsertCard(
                    oldCard.copy(
                        status = LoyaltyCardStatus.REPLACED.code,
                        replacedByCardId = newCardId,
                        suspensionOrReplacementReason = cleanReason,
                        updatedAt = nowMillis
                    )
                )

                val newEntity = LoyaltyCardEntity(
                    cardId = newCardId,
                    branchId = oldCard.branchId,
                    customerId = oldCard.customerId,
                    customerName = oldCard.customerName,
                    customerPhone = oldCard.customerPhone,
                    cardNumber = newCardNumber,
                    qrCodeToken = newQrToken,
                    status = carriedStatus.code,
                    issuedAt = nowMillis,
                    firstQualifyingInvoiceAt = oldCard.firstQualifyingInvoiceAt,
                    expiresAt = oldCard.expiresAt,
                    graceExpiresAt = oldCard.graceExpiresAt,
                    currentCycleStamps = oldCard.currentCycleStamps,
                    totalStampsEarned = oldCard.totalStampsEarned,
                    totalRewardsEarned = oldCard.totalRewardsEarned,
                    totalRewardsRedeemed = oldCard.totalRewardsRedeemed,
                    cycleCount = oldCard.cycleCount,
                    replacesCardId = oldCardId,
                    suspensionOrReplacementReason = cleanReason,
                    updatedAt = nowMillis
                )
                smartDao.insertCardIfNew(newEntity)
                smartDao.reassignActiveVouchersToCard(oldCardId, newCardId)

                recordLoyaltyAudit(
                    session = session,
                    branchId = oldCard.branchId,
                    entityType = "LOYALTY_CARD",
                    entityId = newCardId,
                    action = "CARD_REPLACED",
                    details = "Replaced ${oldCard.cardNumber} -> $newCardNumber (preserved ${oldCard.currentCycleStamps} stamps, original expiry kept): $cleanReason",
                    previousValueJson = """{"oldCardId":"$oldCardId","oldCardNumber":"${oldCard.cardNumber}"}""",
                    newValueJson = """{"newCardId":"$newCardId","newCardNumber":"$newCardNumber"}""",
                    nowMillis = nowMillis
                )
                enqueueSync(
                    branchId = oldCard.branchId,
                    entityType = "LOYALTY_CARD",
                    entityId = oldCardId,
                    payload = """{"cardId":"$oldCardId","status":"REPLACED","replacedByCardId":"$newCardId"}""",
                    nowMillis = nowMillis
                )
                enqueueSync(
                    branchId = oldCard.branchId,
                    entityType = "LOYALTY_CARD",
                    entityId = newCardId,
                    payload = """{"cardId":"$newCardId","cardNumber":"$newCardNumber","replacesCardId":"$oldCardId","status":"${carriedStatus.code}"}""",
                    nowMillis = nowMillis
                )
                toCardRecord(newEntity, settings.requiredStampsForReward, hasActiveVoucher, nowMillis)
            }
            Result.success(replacement)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renewExpiredCard(
        session: UserSession,
        cardId: String,
        reason: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<LoyaltyCardRecord> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canManageLoyaltyCards) {
            return Result.failure(SecurityException("Only Super Admin, Owner, or Branch Manager can renew expired loyalty cards."))
        }
        val cleanReason = reason.trim()
        if (cleanReason.length < 3) {
            return Result.failure(IllegalArgumentException("Renewal reason is required."))
        }
        return try {
            val renewed = database.withTransaction {
                val card = smartDao.getCardById(cardId) ?: throw IllegalArgumentException("Loyalty card not found.")
                ErpAuthorizationGuard.verifyBranchAccess(session, card.branchId)?.let { throw it }
                if (card.status == LoyaltyCardStatus.REPLACED.code || card.status == LoyaltyCardStatus.SUSPENDED.code) {
                    throw IllegalStateException("Suspended or replaced cards cannot be renewed directly.")
                }
                val settings = smartDao.getSettings(card.branchId) ?: LoyaltyProgramSettingsEntity(card.branchId)
                val isEffectivelyExpired = card.expiresAt != null && nowMillis > card.expiresAt
                if (!isEffectivelyExpired && card.status != LoyaltyCardStatus.EXPIRED.code) {
                    throw IllegalStateException("Card ${card.cardNumber} is not expired yet; active expiry dates cannot be prematurely reset.")
                }
                val nextCycle = card.cycleCount + 1
                val updatedCard = card.copy(
                    status = LoyaltyCardStatus.NOT_ACTIVATED.code,
                    firstQualifyingInvoiceAt = null,
                    expiresAt = null,
                    graceExpiresAt = null,
                    currentCycleStamps = 0,
                    cycleCount = nextCycle,
                    suspensionOrReplacementReason = "Renewed: $cleanReason",
                    updatedAt = nowMillis
                )
                smartDao.upsertCard(updatedCard)
                smartDao.insertStampTransactionIfNew(
                    LoyaltyStampTransactionEntity(
                        stampTxId = "stx_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                        branchId = card.branchId,
                        cardId = card.cardId,
                        customerId = card.customerId,
                        orderId = null,
                        invoiceNumber = null,
                        txType = LoyaltyStampTxType.CYCLE_RESET.code,
                        stampsDelta = 0,
                        stampsAfter = 0,
                        qualifyingAmountCents = 0L,
                        cycleNumber = nextCycle,
                        idempotencyKey = "renew_${card.cardId}_c$nextCycle",
                        uniqueOrderEarnKey = null,
                        reason = "Card renewed for cycle #$nextCycle: $cleanReason",
                        approvedBy = session.displayName,
                        actorUid = session.uid,
                        actorName = session.displayName,
                        createdAt = nowMillis
                    )
                )
                recordLoyaltyAudit(
                    session = session,
                    branchId = card.branchId,
                    entityType = "LOYALTY_CARD",
                    entityId = card.cardId,
                    action = "CARD_RENEWED",
                    details = "Renewed expired card ${card.cardNumber} -> Cycle #$nextCycle ($cleanReason)",
                    previousValueJson = """{"cycle":${card.cycleCount},"expiresAt":${card.expiresAt ?: 0L}}""",
                    newValueJson = """{"cycle":$nextCycle,"status":"NOT_ACTIVATED"}""",
                    nowMillis = nowMillis
                )
                toCardRecord(updatedCard, settings.requiredStampsForReward, false, nowMillis)
            }
            Result.success(renewed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adjustStampsManually(
        session: UserSession,
        cardId: String,
        stampsDelta: Int,
        reason: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<LoyaltyCardRecord> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canAdjustLoyaltyStamps) {
            return Result.failure(SecurityException("Role ${session.role.displayName} is not authorized for manual stamp adjustments."))
        }
        if (stampsDelta == 0) {
            return Result.failure(IllegalArgumentException("Stamp adjustment delta cannot be zero."))
        }
        val cleanReason = reason.trim()
        if (cleanReason.length < 3) {
            return Result.failure(IllegalArgumentException("Enter an audit reason for manual stamp adjustment."))
        }
        return try {
            val updated = database.withTransaction {
                val card = smartDao.getCardById(cardId) ?: throw IllegalArgumentException("Loyalty card not found.")
                ErpAuthorizationGuard.verifyBranchAccess(session, card.branchId)?.let { throw it }
                if (card.status == LoyaltyCardStatus.REPLACED.code || card.status == LoyaltyCardStatus.SUSPENDED.code) {
                    throw IllegalStateException("Cannot adjust stamps on a ${card.status.lowercase()} card.")
                }
                if (card.expiresAt != null && nowMillis > card.expiresAt) {
                    throw IllegalStateException("Card ${card.cardNumber} is expired. Renew card before adjusting stamps.")
                }
                val settings = smartDao.getSettings(card.branchId) ?: LoyaltyProgramSettingsEntity(card.branchId)
                val req = settings.requiredStampsForReward.coerceAtLeast(1)

                var firstActivated = card.firstQualifyingInvoiceAt
                var expiresAt = card.expiresAt
                var graceExpiresAt = card.graceExpiresAt
                if (firstActivated == null && stampsDelta > 0) {
                    firstActivated = nowMillis
                    expiresAt = LoyaltyCalendarCalculator.addCalendarMonths(nowMillis, settings.validityMonths)
                    graceExpiresAt = LoyaltyCalendarCalculator.addDays(expiresAt, settings.redemptionGraceDays)
                }

                val newCycleStamps = (card.currentCycleStamps + stampsDelta).coerceIn(0, req)
                val newTotalEarned = (card.totalStampsEarned + stampsDelta).coerceAtLeast(0)
                val stampTxId = "stx_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                smartDao.insertStampTransactionIfNew(
                    LoyaltyStampTransactionEntity(
                        stampTxId = stampTxId,
                        branchId = card.branchId,
                        cardId = card.cardId,
                        customerId = card.customerId,
                        orderId = null,
                        invoiceNumber = null,
                        txType = LoyaltyStampTxType.MANUAL_ADJUSTMENT.code,
                        stampsDelta = stampsDelta,
                        stampsAfter = newCycleStamps,
                        qualifyingAmountCents = 0L,
                        cycleNumber = card.cycleCount,
                        idempotencyKey = "manual_${card.cardId}_${UUID.randomUUID().toString().take(8)}",
                        uniqueOrderEarnKey = null,
                        reason = "Manual adjustment: $cleanReason",
                        approvedBy = session.displayName,
                        actorUid = session.uid,
                        actorName = session.displayName,
                        createdAt = nowMillis
                    )
                )

                var unlockedVoucherAdded = 0
                if (newCycleStamps == req && card.currentCycleStamps < req && expiresAt != null && graceExpiresAt != null) {
                    mintVoucherInTransaction(
                        card = card,
                        settings = settings,
                        orderId = null,
                        invoiceNumber = null,
                        stampTxId = stampTxId,
                        expiresAt = expiresAt,
                        graceExpiresAt = graceExpiresAt,
                        nowMillis = nowMillis,
                        notes = "Unlocked via manual adjustment ($cleanReason)"
                    )
                    unlockedVoucherAdded = 1
                }

                val vouchers = smartDao.getVouchersForCustomer(card.customerId)
                val hasActiveVoucher = vouchers.any {
                    it.cardId == card.cardId &&
                        LoyaltyCalendarCalculator.computeEffectiveVoucherStatus(
                            LoyaltyVoucherStatus.fromCode(it.status),
                            it.graceExpiresAt,
                            nowMillis
                        ) == LoyaltyVoucherStatus.ACTIVE
                }
                val newStatus = when {
                    firstActivated == null -> LoyaltyCardStatus.NOT_ACTIVATED
                    hasActiveVoucher || newCycleStamps >= req -> LoyaltyCardStatus.REWARD_ELIGIBLE
                    else -> LoyaltyCardStatus.ACTIVE
                }
                val nextCard = card.copy(
                    status = newStatus.code,
                    firstQualifyingInvoiceAt = firstActivated,
                    expiresAt = expiresAt,
                    graceExpiresAt = graceExpiresAt,
                    currentCycleStamps = newCycleStamps,
                    totalStampsEarned = newTotalEarned,
                    totalRewardsEarned = card.totalRewardsEarned + unlockedVoucherAdded,
                    updatedAt = nowMillis
                )
                smartDao.upsertCard(nextCard)
                recordLoyaltyAudit(
                    session = session,
                    branchId = card.branchId,
                    entityType = "LOYALTY_CARD",
                    entityId = card.cardId,
                    action = "STAMP_MANUAL_ADJUSTMENT",
                    details = "Adjusted ${card.cardNumber} stamps by ${if (stampsDelta > 0) "+$stampsDelta" else "$stampsDelta"} -> $newCycleStamps/$req ($cleanReason)",
                    previousValueJson = """{"stamps":${card.currentCycleStamps}}""",
                    newValueJson = """{"stamps":$newCycleStamps,"delta":$stampsDelta}""",
                    nowMillis = nowMillis
                )
                toCardRecord(nextCard, req, hasActiveVoucher, nowMillis)
            }
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProgramSettings(
        session: UserSession,
        updated: LoyaltyProgramSettingsRecord,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<LoyaltyProgramSettingsRecord> {
        ErpAuthorizationGuard.verifyActiveAndBranch(database, session, updated.branchId)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canConfigureLoyaltyProgram) {
            return Result.failure(SecurityException("Role ${session.role.displayName} is not authorized to change Loyalty program rules."))
        }
        if (updated.minQualifyingAmountCents < 100L) {
            return Result.failure(IllegalArgumentException("Minimum qualifying invoice amount must be positive."))
        }
        if (updated.requiredStampsForReward !in 1..30) {
            return Result.failure(IllegalArgumentException("Required stamps per reward must be between 1 and 30."))
        }
        if (updated.rewardAmountCents !in 100L..LoyaltyRuleConstants.MAX_REWARD_CAP_CENTS) {
            return Result.failure(
                IllegalArgumentException("Reward voucher cap cannot exceed ${centsToPkr(LoyaltyRuleConstants.MAX_REWARD_CAP_CENTS)}.")
            )
        }
        if (updated.validityMonths !in 1..24) {
            return Result.failure(IllegalArgumentException("Validity months must be between 1 and 24."))
        }
        if (updated.redemptionGraceDays !in 0..180) {
            return Result.failure(IllegalArgumentException("Grace period days must be between 0 and 180."))
        }

        return try {
            val saved = database.withTransaction {
                val previous = smartDao.getSettings(updated.branchId) ?: LoyaltyProgramSettingsEntity(updated.branchId)
                val entity = LoyaltyProgramSettingsEntity(
                    branchId = updated.branchId,
                    programEnabled = updated.programEnabled,
                    minQualifyingAmountCents = updated.minQualifyingAmountCents,
                    requiredStampsForReward = updated.requiredStampsForReward,
                    rewardAmountCents = updated.rewardAmountCents.coerceAtMost(LoyaltyRuleConstants.MAX_REWARD_CAP_CENTS),
                    validityMonths = updated.validityMonths,
                    redemptionGraceDays = updated.redemptionGraceDays,
                    qualifyingCalculationMethod = updated.qualifyingCalculationMethod.code,
                    eligibleCategoryIdsCsv = updated.eligibleCategoryIds.joinToString(","),
                    redeemableCategoryIdsCsv = updated.redeemableCategoryIds.joinToString(","),
                    excludeTaxFromQualifying = updated.excludeTaxFromQualifying,
                    excludeServiceAndDeliveryFromQualifying = updated.excludeServiceAndDeliveryFromQualifying,
                    allowPartialVoucherUsage = updated.allowPartialVoucherUsage,
                    cardPrefix = updated.cardPrefix.trim().uppercase(Locale.US).ifBlank { "AAC" }.take(6),
                    voucherPrefix = updated.voucherPrefix.trim().uppercase(Locale.US).ifBlank { "RWD" }.take(6),
                    updatedBy = session.displayName,
                    updatedAt = nowMillis
                )
                smartDao.upsertSettings(entity)
                recordLoyaltyAudit(
                    session = session,
                    branchId = updated.branchId,
                    entityType = "LOYALTY_SETTINGS",
                    entityId = updated.branchId,
                    action = "PROGRAM_SETTINGS_UPDATED",
                    details = "Updated rules: Min=${centsToPkr(entity.minQualifyingAmountCents)}, Stamps=${entity.requiredStampsForReward}, RewardCap=${centsToPkr(entity.rewardAmountCents)}, Validity=${entity.validityMonths}m + ${entity.redemptionGraceDays}d grace",
                    previousValueJson = """{"minQualifyingCents":${previous.minQualifyingAmountCents},"stamps":${previous.requiredStampsForReward},"rewardCents":${previous.rewardAmountCents},"validityMonths":${previous.validityMonths},"enabled":${previous.programEnabled}}""",
                    newValueJson = """{"minQualifyingCents":${entity.minQualifyingAmountCents},"stamps":${entity.requiredStampsForReward},"rewardCents":${entity.rewardAmountCents},"validityMonths":${entity.validityMonths},"enabled":${entity.programEnabled}}""",
                    nowMillis = nowMillis
                )
                enqueueSync(
                    branchId = updated.branchId,
                    entityType = "LOYALTY_SETTINGS",
                    entityId = updated.branchId,
                    payload = """{"branchId":"${updated.branchId}","enabled":${entity.programEnabled},"minQualifyingCents":${entity.minQualifyingAmountCents},"rewardCents":${entity.rewardAmountCents}}""",
                    nowMillis = nowMillis
                )
                toSettingsRecord(entity)
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun previewPosLoyalty(
        branchId: String,
        cardId: String,
        cartItems: List<CartItem>,
        preVoucherTotals: BillingTotals,
        selectedVoucherDiscountCents: Long = 0L,
        nowMillis: Long = System.currentTimeMillis()
    ): PosLoyaltyPreview? {
        val cardEntity = smartDao.getCardById(cardId) ?: return null
        val settingsEntity = smartDao.getSettings(branchId) ?: LoyaltyProgramSettingsEntity(branchId)
        val settings = toSettingsRecord(settingsEntity)
        val vouchers = smartDao.getVouchersForCustomer(cardEntity.customerId)
            .filter { it.cardId == cardEntity.cardId || it.customerId == cardEntity.customerId }
            .map { toVoucherRecord(it, nowMillis) }
        val activeVouchers = vouchers.filter { it.effectiveStatus == LoyaltyVoucherStatus.ACTIVE }
        val cardRecord = toCardRecord(cardEntity, settings.requiredStampsForReward, activeVouchers.isNotEmpty(), nowMillis)
        val qualifyingCents = LoyaltyCalendarCalculator.computeQualifyingOrderAmountCents(
            items = cartItems,
            totals = preVoucherTotals,
            voucherDiscountCents = selectedVoucherDiscountCents,
            settings = settings
        )

        val eligibleForStamp = settings.programEnabled &&
            cardRecord.canEarnStampsNow &&
            qualifyingCents >= settings.minQualifyingAmountCents

        val projectedStamps = if (eligibleForStamp) {
            if (cardRecord.currentCycleStamps >= settings.requiredStampsForReward) 1
            else cardRecord.currentCycleStamps + 1
        } else {
            cardRecord.currentCycleStamps
        }

        val reason = when {
            !settings.programEnabled -> "Loyalty program is currently paused in this branch."
            cardRecord.effectiveStatus == LoyaltyCardStatus.REPLACED -> "This card was replaced. Scan the active replacement card."
            cardRecord.effectiveStatus == LoyaltyCardStatus.SUSPENDED -> "Card is suspended (${cardRecord.suspensionOrReplacementReason ?: "Admin hold"})."
            cardRecord.effectiveStatus == LoyaltyCardStatus.EXPIRED -> "Card expired on ${cardRecord.expiresAt?.let { formatDate(it) } ?: "N/A"}. Renew card to earn stamps."
            qualifyingCents < settings.minQualifyingAmountCents -> {
                val needed = settings.minQualifyingAmountCents - qualifyingCents
                "Add ${centsToPkr(needed)} more eligible spend (min ${centsToPkr(settings.minQualifyingAmountCents)}) to earn a stamp."
            }
            else -> "Qualifies for +1 stamp on full payment (${centsToPkr(qualifyingCents)} eligible)."
        }

        return PosLoyaltyPreview(
            card = cardRecord,
            activeVouchers = activeVouchers,
            qualifyingAmountCents = qualifyingCents,
            minQualifyingCents = settings.minQualifyingAmountCents,
            willEarnStamp = eligibleForStamp,
            projectedStampsAfter = projectedStamps,
            willUnlockVoucherOnCheckout = eligibleForStamp && projectedStamps == settings.requiredStampsForReward,
            eligibilityReason = reason
        )
    }

    /**
     * Validates a selected voucher and computes exact discount + actual promo COGS inside a DB transaction
     * BEFORE invoice totals are finalized. Enforces zero negative invoice and zero cash-out.
     */
    internal suspend fun validateAndComputeVoucherDiscountInTransaction(
        branchId: String,
        cardId: String?,
        customerId: String?,
        voucherId: String,
        cartItems: List<CartItem>,
        preVoucherTotals: BillingTotals,
        nowMillis: Long
    ): ValidatedVoucherApplication {
        val voucher = smartDao.getVoucherById(voucherId)
            ?: throw IllegalArgumentException("Reward voucher not found.")
        val card = cardId?.let { smartDao.getCardById(it) } ?: smartDao.getCardById(voucher.cardId)
            ?: throw IllegalArgumentException("Loyalty card linked to voucher not found.")

        if (card.status == LoyaltyCardStatus.REPLACED.code) {
            throw IllegalStateException("This card (${card.cardNumber}) has been replaced. Scan the replacement card.")
        }
        if (card.status == LoyaltyCardStatus.SUSPENDED.code) {
            throw IllegalStateException("Loyalty card ${card.cardNumber} is suspended.")
        }
        if (customerId != null && voucher.customerId != customerId) {
            throw SecurityException("Voucher ${voucher.voucherCode} does not belong to the selected customer.")
        }
        if (cardId != null && voucher.cardId != cardId && voucher.customerId != card.customerId) {
            throw SecurityException("Voucher ${voucher.voucherCode} does not match card ${card.cardNumber}.")
        }
        val effectiveVoucherStatus = LoyaltyCalendarCalculator.computeEffectiveVoucherStatus(
            storedStatus = LoyaltyVoucherStatus.fromCode(voucher.status),
            graceExpiresAt = voucher.graceExpiresAt,
            nowMillis = nowMillis
        )
        if (effectiveVoucherStatus == LoyaltyVoucherStatus.REDEEMED || smartDao.getRedemptionForVoucher(voucher.voucherId) != null) {
            throw IllegalStateException("Voucher ${voucher.voucherCode} has already been redeemed.")
        }
        if (effectiveVoucherStatus == LoyaltyVoucherStatus.CANCELLED) {
            throw IllegalStateException("Voucher ${voucher.voucherCode} was cancelled.")
        }
        if (effectiveVoucherStatus == LoyaltyVoucherStatus.EXPIRED) {
            throw IllegalStateException("Voucher ${voucher.voucherCode} expired after grace period (${formatDate(voucher.graceExpiresAt)}).")
        }

        val settingsEntity = smartDao.getSettings(branchId) ?: LoyaltyProgramSettingsEntity(branchId)
        val settings = toSettingsRecord(settingsEntity)
        if (!settings.programEnabled) {
            throw IllegalStateException("Smart Loyalty program is paused for this branch.")
        }

        val redeemableItems = if (settings.redeemableCategoryIds.isEmpty()) {
            cartItems
        } else {
            cartItems.filter { it.product.categoryId in settings.redeemableCategoryIds }
        }
        val rawRedeemableSubtotal = redeemableItems.sumOf { it.lineTotalCents }.coerceAtLeast(0L)
        if (rawRedeemableSubtotal <= 0L) {
            throw IllegalArgumentException("Order contains no items eligible for reward voucher redemption.")
        }

        val totalSubtotal = preVoucherTotals.subtotalCents.coerceAtLeast(1L)
        val allocatedRegularDiscount =
            (preVoucherTotals.discountCents * (rawRedeemableSubtotal.toDouble() / totalSubtotal.toDouble())).roundToLong()
        val netRedeemableSubtotal = (rawRedeemableSubtotal - allocatedRegularDiscount).coerceAtLeast(0L)
        if (netRedeemableSubtotal <= 0L) {
            throw IllegalArgumentException("Eligible subtotal after discounts is zero; cannot redeem voucher without cash-out.")
        }

        val cappedFaceValue = minOf(
            voucher.rewardValueCents,
            LoyaltyRuleConstants.MAX_REWARD_CAP_CENTS,
            settings.rewardAmountCents
        )
        val discountApplied = minOf(cappedFaceValue, netRedeemableSubtotal).coerceAtLeast(0L)

        val totalItemCostCents = computeActualPromoCogsInTransaction(redeemableItems)
        val promoCostRatio = if (rawRedeemableSubtotal > 0L) {
            (discountApplied.toDouble() / rawRedeemableSubtotal.toDouble()).coerceIn(0.0, 1.0)
        } else 0.0
        val actualPromoCostCents = (totalItemCostCents * promoCostRatio).roundToLong()

        return ValidatedVoucherApplication(
            voucher = voucher,
            card = card,
            discountAppliedCents = discountApplied,
            actualPromoCostCents = actualPromoCostCents,
            eligibleSubtotalCents = netRedeemableSubtotal
        )
    }

    private suspend fun computeActualPromoCogsInTransaction(items: List<CartItem>): Long {
        val recipeDao = database.recipeDao()
        val inventoryDao = database.inventoryDao()
        var totalCost = 0L
        for (line in items) {
            val recipe = recipeDao.getRecipeForProduct(line.product.productId)
            val unitCost = if (recipe != null && recipe.totalCostCents > 0L) {
                recipe.totalCostCents
            } else {
                val linkedInv = inventoryDao.getItemByLinkedProductId(line.product.productId)
                linkedInv?.unitCostCents ?: 0L
            }
            totalCost += unitCost * line.quantity.coerceAtLeast(1)
        }
        return totalCost.coerceAtLeast(0L)
    }

    internal suspend fun finalizeLoyaltyInPosTransaction(
        session: UserSession,
        orderEntity: PosOrderEntity,
        cartItems: List<CartItem>,
        preVoucherTotals: BillingTotals,
        cardId: String?,
        validatedVoucher: ValidatedVoucherApplication?,
        nowMillis: Long
    ): PosLoyaltyOutcome {
        val effectiveCardId = cardId ?: validatedVoucher?.card?.cardId
        if (effectiveCardId == null && validatedVoucher == null) {
            return PosLoyaltyOutcome()
        }

        val settingsEntity = smartDao.getSettings(orderEntity.branchId) ?: LoyaltyProgramSettingsEntity(orderEntity.branchId)
        val settings = toSettingsRecord(settingsEntity)
        var card = effectiveCardId?.let { smartDao.getCardById(it) } ?: validatedVoucher?.card
            ?: return PosLoyaltyOutcome()

        var redeemedVoucherCode: String? = null
        var redeemedVoucherId: String? = null
        var discountAppliedCents = 0L
        var promoCostCents = 0L

        if (validatedVoucher != null) {
            val v = validatedVoucher.voucher
            discountAppliedCents = validatedVoucher.discountAppliedCents
            promoCostCents = validatedVoucher.actualPromoCostCents
            redeemedVoucherCode = v.voucherCode
            redeemedVoucherId = v.voucherId

            val redemptionId = "lred_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val insertedRed = smartDao.insertRedemptionIfNew(
                LoyaltyRedemptionEntity(
                    redemptionId = redemptionId,
                    branchId = orderEntity.branchId,
                    voucherId = v.voucherId,
                    voucherCode = v.voucherCode,
                    cardId = card.cardId,
                    customerId = card.customerId,
                    orderId = orderEntity.orderId,
                    invoiceNumber = orderEntity.invoiceNumber,
                    voucherFaceValueCents = v.rewardValueCents,
                    discountAppliedCents = discountAppliedCents,
                    actualCogsCostCents = promoCostCents,
                    eligibleSubtotalCents = validatedVoucher.eligibleSubtotalCents,
                    redeemedByUid = session.uid,
                    redeemedByName = session.displayName,
                    createdAt = nowMillis
                )
            )
            if (insertedRed == -1L) {
                throw IllegalStateException("Voucher ${v.voucherCode} was already redeemed.")
            }

            smartDao.upsertVoucher(
                v.copy(
                    status = LoyaltyVoucherStatus.REDEEMED.code,
                    redeemedOrderId = orderEntity.orderId,
                    redeemedInvoiceNumber = orderEntity.invoiceNumber,
                    redeemedAt = nowMillis,
                    redeemedAmountCents = discountAppliedCents,
                    actualPromoCostCents = promoCostCents
                )
            )

            card = card.copy(
                totalRewardsRedeemed = card.totalRewardsRedeemed + 1,
                updatedAt = nowMillis
            )
            smartDao.upsertCard(card)

            recordLoyaltyAudit(
                session = session,
                branchId = orderEntity.branchId,
                entityType = "LOYALTY_VOUCHER",
                entityId = v.voucherId,
                action = "VOUCHER_REDEEMED",
                details = "Redeemed ${v.voucherCode} on ${orderEntity.invoiceNumber}: Discount ${centsToPkr(discountAppliedCents)} (Actual COGS ${centsToPkr(promoCostCents)})",
                previousValueJson = """{"status":"ACTIVE"}""",
                newValueJson = """{"status":"REDEEMED","orderId":"${orderEntity.orderId}","discountCents":$discountAppliedCents}""",
                nowMillis = nowMillis
            )
            enqueueSync(
                branchId = orderEntity.branchId,
                entityType = "LOYALTY_REDEMPTION",
                entityId = redemptionId,
                payload = """{"redemptionId":"$redemptionId","voucherId":"${v.voucherId}","orderId":"${orderEntity.orderId}","discountCents":$discountAppliedCents}""",
                nowMillis = nowMillis
            )
        }

        var stampChange = 0
        var unlockedVoucherRecord: LoyaltyRewardVoucherRecord? = null
        val statusNotes = mutableListOf<String>()

        if (redeemedVoucherCode != null) {
            statusNotes += "Redeemed $redeemedVoucherCode (-${centsToPkr(discountAppliedCents)})"
        }

        val isFullyPaid = orderEntity.paidCents >= orderEntity.totalCents && orderEntity.remainingBalanceCents == 0L
        val qualifyingCents = LoyaltyCalendarCalculator.computeQualifyingOrderAmountCents(
            items = cartItems,
            totals = preVoucherTotals,
            voucherDiscountCents = discountAppliedCents,
            settings = settings
        )
        val isCardBlocked = card.status == LoyaltyCardStatus.SUSPENDED.code ||
            card.status == LoyaltyCardStatus.REPLACED.code ||
            (card.expiresAt != null && nowMillis > card.expiresAt)

        if (settings.programEnabled && !isCardBlocked && isFullyPaid && qualifyingCents >= settings.minQualifyingAmountCents) {
            val req = settings.requiredStampsForReward.coerceAtLeast(1)
            val stampTxId = "stx_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val earnKey = "EARN_${orderEntity.orderId}"
            val idempKey = "stamp_earn_${orderEntity.idempotencyKey}"

            val isNewCycleRollover = card.currentCycleStamps >= req
            val nextCycleNumber = if (isNewCycleRollover) card.cycleCount + 1 else card.cycleCount
            val nextCycleStamps = if (isNewCycleRollover) 1 else (card.currentCycleStamps + 1)

            val firstActivated = card.firstQualifyingInvoiceAt ?: nowMillis
            val cardExpiresAt = card.expiresAt ?: LoyaltyCalendarCalculator.addCalendarMonths(firstActivated, settings.validityMonths)
            val cardGraceExpiresAt = card.graceExpiresAt ?: LoyaltyCalendarCalculator.addDays(cardExpiresAt, settings.redemptionGraceDays)

            val insertedStamp = smartDao.insertStampTransactionIfNew(
                LoyaltyStampTransactionEntity(
                    stampTxId = stampTxId,
                    branchId = orderEntity.branchId,
                    cardId = card.cardId,
                    customerId = card.customerId,
                    orderId = orderEntity.orderId,
                    invoiceNumber = orderEntity.invoiceNumber,
                    txType = LoyaltyStampTxType.EARN.code,
                    stampsDelta = 1,
                    stampsAfter = nextCycleStamps,
                    qualifyingAmountCents = qualifyingCents,
                    cycleNumber = nextCycleNumber,
                    idempotencyKey = idempKey,
                    uniqueOrderEarnKey = earnKey,
                    reason = "Qualifying paid order ${orderEntity.invoiceNumber} (${centsToPkr(qualifyingCents)})",
                    actorUid = session.uid,
                    actorName = session.displayName,
                    createdAt = nowMillis
                )
            )

            if (insertedStamp != -1L) {
                stampChange = 1
                var rewardsEarnedDelta = 0
                if (nextCycleStamps == req) {
                    val minted = mintVoucherInTransaction(
                        card = card.copy(cycleCount = nextCycleNumber),
                        settings = settingsEntity,
                        orderId = orderEntity.orderId,
                        invoiceNumber = orderEntity.invoiceNumber,
                        stampTxId = stampTxId,
                        expiresAt = cardExpiresAt,
                        graceExpiresAt = cardGraceExpiresAt,
                        nowMillis = nowMillis,
                        notes = "Completed $req stamps in Cycle #$nextCycleNumber"
                    )
                    unlockedVoucherRecord = toVoucherRecord(
                        e = minted,
                        nowMillis = nowMillis,
                        customerName = card.customerName,
                        cardNumber = card.cardNumber
                    )
                    rewardsEarnedDelta = 1
                    statusNotes += "Unlocked ${minted.voucherCode} (${centsToPkr(minted.rewardValueCents)})"
                } else {
                    statusNotes += "+1 Stamp ($nextCycleStamps/$req)"
                }

                val remainingActiveVouchers = smartDao.getVouchersForCustomer(card.customerId).any {
                    it.cardId == card.cardId &&
                        LoyaltyCalendarCalculator.computeEffectiveVoucherStatus(
                            LoyaltyVoucherStatus.fromCode(it.status),
                            it.graceExpiresAt,
                            nowMillis
                        ) == LoyaltyVoucherStatus.ACTIVE
                }
                val newCardStatus = if (remainingActiveVouchers || nextCycleStamps >= req) {
                    LoyaltyCardStatus.REWARD_ELIGIBLE
                } else {
                    LoyaltyCardStatus.ACTIVE
                }

                card = card.copy(
                    status = newCardStatus.code,
                    firstQualifyingInvoiceAt = firstActivated,
                    expiresAt = cardExpiresAt,
                    graceExpiresAt = cardGraceExpiresAt,
                    currentCycleStamps = nextCycleStamps,
                    totalStampsEarned = card.totalStampsEarned + 1,
                    totalRewardsEarned = card.totalRewardsEarned + rewardsEarnedDelta,
                    cycleCount = nextCycleNumber,
                    updatedAt = nowMillis
                )
                smartDao.upsertCard(card)

                recordLoyaltyAudit(
                    session = session,
                    branchId = orderEntity.branchId,
                    entityType = "LOYALTY_STAMP",
                    entityId = stampTxId,
                    action = "STAMP_EARNED",
                    details = "${card.cardNumber}: +1 stamp ($nextCycleStamps/$req, Cycle #$nextCycleNumber) on ${orderEntity.invoiceNumber}",
                    newValueJson = """{"stampsAfter":$nextCycleStamps,"cycle":$nextCycleNumber,"qualifyingCents":$qualifyingCents}""",
                    nowMillis = nowMillis
                )
                enqueueSync(
                    branchId = orderEntity.branchId,
                    entityType = "LOYALTY_STAMP",
                    entityId = stampTxId,
                    payload = """{"stampTxId":"$stampTxId","cardId":"${card.cardId}","orderId":"${orderEntity.orderId}","stampsAfter":$nextCycleStamps}""",
                    nowMillis = nowMillis
                )
            }
        } else {
            when {
                !isFullyPaid -> statusNotes += "No stamp (unpaid/partial invoice)"
                card.expiresAt != null && nowMillis > card.expiresAt -> statusNotes += "No stamp (card expired)"
                card.status == LoyaltyCardStatus.SUSPENDED.code -> statusNotes += "No stamp (card suspended)"
                qualifyingCents < settings.minQualifyingAmountCents ->
                    statusNotes += "No stamp (below ${centsToPkr(settings.minQualifyingAmountCents)} min)"
            }
        }

        smartDao.updateOrderLoyaltyFields(
            orderId = orderEntity.orderId,
            cardId = card.cardId,
            cardNumber = card.cardNumber,
            stampChange = stampChange,
            stampsAfter = card.currentCycleStamps,
            voucherId = redeemedVoucherId,
            voucherCode = redeemedVoucherCode,
            voucherDiscountCents = discountAppliedCents,
            promoCostCents = promoCostCents
        )

        return PosLoyaltyOutcome(
            cardId = card.cardId,
            cardNumber = card.cardNumber,
            stampChange = stampChange,
            stampsAfter = card.currentCycleStamps,
            voucherId = redeemedVoucherId,
            voucherCode = redeemedVoucherCode,
            voucherDiscountCents = discountAppliedCents,
            promoCostCents = promoCostCents,
            unlockedVoucher = unlockedVoucherRecord,
            statusNote = statusNotes.joinToString(" • ").ifBlank { null }
        )
    }

    internal suspend fun reverseLoyaltyForOrderInTransaction(
        session: UserSession,
        order: PosOrderEntity,
        reason: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val earnTx = smartDao.getEarnTxForOrder(order.orderId)
        if (earnTx != null && earnTx.reversedByTxId == null) {
            val card = smartDao.getCardById(earnTx.cardId)
            if (card != null) {
                val settings = smartDao.getSettings(card.branchId) ?: LoyaltyProgramSettingsEntity(card.branchId)
                val req = settings.requiredStampsForReward.coerceAtLeast(1)
                val reversalId = "stx_rev_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                val newStamps = (card.currentCycleStamps - 1).coerceAtLeast(0)
                val newTotal = (card.totalStampsEarned - 1).coerceAtLeast(0)

                val inserted = smartDao.insertStampTransactionIfNew(
                    LoyaltyStampTransactionEntity(
                        stampTxId = reversalId,
                        branchId = card.branchId,
                        cardId = card.cardId,
                        customerId = card.customerId,
                        orderId = order.orderId,
                        invoiceNumber = order.invoiceNumber,
                        txType = LoyaltyStampTxType.REVERSAL.code,
                        stampsDelta = -1,
                        stampsAfter = newStamps,
                        qualifyingAmountCents = earnTx.qualifyingAmountCents,
                        cycleNumber = earnTx.cycleNumber,
                        idempotencyKey = "rev_${earnTx.stampTxId}",
                        uniqueOrderEarnKey = null,
                        reversedByTxId = earnTx.stampTxId,
                        reason = "Reversed due to order ${order.orderNumber} cancellation/refund: $reason",
                        approvedBy = session.displayName,
                        actorUid = session.uid,
                        actorName = session.displayName,
                        createdAt = nowMillis
                    )
                )
                if (inserted != -1L) {
                    smartDao.upsertStampTransaction(earnTx.copy(reversedByTxId = reversalId))

                    val unlockedVoucher = smartDao.getVoucherByEarnedStampTx(earnTx.stampTxId)
                    var rewardsDelta = 0
                    if (unlockedVoucher != null && unlockedVoucher.status == LoyaltyVoucherStatus.ACTIVE.code) {
                        smartDao.upsertVoucher(
                            unlockedVoucher.copy(
                                status = LoyaltyVoucherStatus.CANCELLED.code,
                                cancelledReason = "Auto-cancelled: Order ${order.invoiceNumber} ${order.status.lowercase()} ($reason)"
                            )
                        )
                        rewardsDelta = -1
                        recordLoyaltyAudit(
                            session = session,
                            branchId = card.branchId,
                            entityType = "LOYALTY_VOUCHER",
                            entityId = unlockedVoucher.voucherId,
                            action = "VOUCHER_AUTO_CANCELLED",
                            details = "Auto-cancelled ${unlockedVoucher.voucherCode} after reversal of ${order.invoiceNumber}",
                            previousValueJson = """{"status":"ACTIVE"}""",
                            newValueJson = """{"status":"CANCELLED"}""",
                            nowMillis = nowMillis
                        )
                    }

                    val hasOtherActiveVoucher = smartDao.getVouchersForCustomer(card.customerId).any {
                        it.cardId == card.cardId &&
                            LoyaltyCalendarCalculator.computeEffectiveVoucherStatus(
                                LoyaltyVoucherStatus.fromCode(it.status),
                                it.graceExpiresAt,
                                nowMillis
                            ) == LoyaltyVoucherStatus.ACTIVE
                    }
                    val recomputedStatus = when {
                        card.status == LoyaltyCardStatus.SUSPENDED.code -> LoyaltyCardStatus.SUSPENDED
                        card.status == LoyaltyCardStatus.REPLACED.code -> LoyaltyCardStatus.REPLACED
                        card.expiresAt != null && nowMillis > card.expiresAt -> LoyaltyCardStatus.EXPIRED
                        hasOtherActiveVoucher || newStamps >= req -> LoyaltyCardStatus.REWARD_ELIGIBLE
                        card.firstQualifyingInvoiceAt != null -> LoyaltyCardStatus.ACTIVE
                        else -> LoyaltyCardStatus.NOT_ACTIVATED
                    }

                    smartDao.upsertCard(
                        card.copy(
                            status = recomputedStatus.code,
                            currentCycleStamps = newStamps,
                            totalStampsEarned = newTotal,
                            totalRewardsEarned = (card.totalRewardsEarned + rewardsDelta).coerceAtLeast(0),
                            updatedAt = nowMillis
                        )
                    )
                    smartDao.updateOrderLoyaltyFields(
                        orderId = order.orderId,
                        cardId = order.loyaltyCardId,
                        cardNumber = order.loyaltyCardNumber,
                        stampChange = 0,
                        stampsAfter = newStamps,
                        voucherId = order.loyaltyVoucherId,
                        voucherCode = order.loyaltyVoucherCode,
                        voucherDiscountCents = order.loyaltyVoucherDiscountCents,
                        promoCostCents = order.loyaltyPromoCostCents
                    )
                    recordLoyaltyAudit(
                        session = session,
                        branchId = card.branchId,
                        entityType = "LOYALTY_STAMP",
                        entityId = reversalId,
                        action = "STAMP_REVERSED",
                        details = "Reversed +1 stamp on ${card.cardNumber} from ${order.invoiceNumber} ($reason)",
                        previousValueJson = """{"stamps":${card.currentCycleStamps}}""",
                        newValueJson = """{"stamps":$newStamps}""",
                        nowMillis = nowMillis
                    )
                }
            }
        }
    }

    suspend fun buildAnalyticsReport(
        branchIdFilter: String?,
        startMillis: Long? = null,
        endMillis: Long? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): LoyaltyAnalyticsReport {
        val branches = branchDao.getAllBranches()
        val targetBranches = if (branchIdFilter == null) branches else branches.filter { it.branchId == branchIdFilter }
        val branchMap = branches.associateBy { it.branchId }
        val allSettings = smartDao.getAllSettings().associateBy { it.branchId }
        val allCards = smartDao.getAllCards()
        val allVouchers = smartDao.getAllVouchers()
        val allOrders = smartDao.getAllPosOrders()
        val allCustomers = smartDao.getAllCustomers().associateBy { it.customerId }

        fun inPeriod(ts: Long): Boolean {
            if (startMillis != null && ts < startMillis) return false
            if (endMillis != null && ts > endMillis) return false
            return true
        }

        val vouchersByCard = allVouchers.groupBy { it.cardId }
        val cardRecords = allCards.map { c ->
            val req = allSettings[c.branchId]?.requiredStampsForReward ?: LoyaltyRuleConstants.DEFAULT_REQUIRED_STAMPS
            val hasActive = vouchersByCard[c.cardId].orEmpty().any {
                LoyaltyCalendarCalculator.computeEffectiveVoucherStatus(
                    LoyaltyVoucherStatus.fromCode(it.status),
                    it.graceExpiresAt,
                    nowMillis
                ) == LoyaltyVoucherStatus.ACTIVE
            }
            toCardRecord(c, req, hasActive, nowMillis)
        }.filter { branchIdFilter == null || it.branchId == branchIdFilter }

        val nonReplacedCards = cardRecords.filter { it.status != LoyaltyCardStatus.REPLACED }
        val voucherRecords = allVouchers
            .filter { branchIdFilter == null || it.branchId == branchIdFilter }
            .map { toVoucherRecord(it, nowMillis) }

        val stampsList = if (branchIdFilter == null) {
            smartDao.observeAllStampTransactions().first()
        } else {
            smartDao.observeStampTransactionsForBranch(branchIdFilter).first()
        }
        val redemptionsList = if (branchIdFilter == null) {
            smartDao.observeAllRedemptions().first()
        } else {
            smartDao.observeRedemptionsForBranch(branchIdFilter).first()
        }

        val periodStamps = stampsList.filter { inPeriod(it.createdAt) }
        val periodVouchersEarned = voucherRecords.filter { inPeriod(it.earnedAt) }
        val periodRedemptions = redemptionsList.filter { inPeriod(it.createdAt) }
        val periodLoyaltyOrders = allOrders.filter { o ->
            (branchIdFilter == null || o.branchId == branchIdFilter) &&
                o.status != OrderStatus.CANCELLED.name &&
                o.status != OrderStatus.REFUNDED.name &&
                inPeriod(o.createdAt) &&
                (!o.loyaltyCardId.isNullOrBlank() || !o.loyaltyVoucherId.isNullOrBlank())
        }

        val branchRows = targetBranches.map { branch ->
            val bCards = nonReplacedCards.filter { it.branchId == branch.branchId }
            val bStamps = periodStamps.filter { it.branchId == branch.branchId }
            val bVouchers = voucherRecords.filter { it.branchId == branch.branchId }
            val bRedemptions = periodRedemptions.filter { it.branchId == branch.branchId }
            val bOrders = periodLoyaltyOrders.filter { it.branchId == branch.branchId }

            LoyaltyBranchReportRow(
                branchId = branch.branchId,
                branchName = branch.name,
                enrolledCustomers = bCards.map { it.customerId }.distinct().size,
                activeCards = bCards.count {
                    it.effectiveStatus == LoyaltyCardStatus.ACTIVE || it.effectiveStatus == LoyaltyCardStatus.REWARD_ELIGIBLE
                },
                expiredCards = bCards.count { it.effectiveStatus == LoyaltyCardStatus.EXPIRED },
                rewardEligibleCards = bCards.count { it.effectiveStatus == LoyaltyCardStatus.REWARD_ELIGIBLE },
                stampsIssued = bStamps.filter { it.stampsDelta > 0 }.sumOf { it.stampsDelta },
                stampsReversed = bStamps.filter { it.stampsDelta < 0 }.sumOf { kotlin.math.abs(it.stampsDelta) },
                vouchersEarned = bVouchers.count { inPeriod(it.earnedAt) },
                vouchersRedeemed = bRedemptions.size,
                vouchersExpiredOrUnused = bVouchers.count {
                    it.effectiveStatus == LoyaltyVoucherStatus.ACTIVE || it.effectiveStatus == LoyaltyVoucherStatus.EXPIRED
                },
                promoDiscountTotalCents = bRedemptions.sumOf { it.discountAppliedCents },
                actualPromoCogsCents = bRedemptions.sumOf { it.actualCogsCostCents },
                netPaidSalesWithLoyaltyCents = bOrders.sumOf { it.paidCents }
            )
        }

        val topCustomers = nonReplacedCards
            .sortedWith(compareByDescending<LoyaltyCardRecord> { it.totalStampsEarned }.thenByDescending { it.currentCycleStamps })
            .take(15)
            .map { c ->
                CustomerLoyaltyLeaderboardRow(
                    customerId = c.customerId,
                    customerName = c.customerName,
                    cardNumber = c.cardNumber,
                    status = c.effectiveStatus,
                    currentStamps = c.currentCycleStamps,
                    totalStampsEarned = c.totalStampsEarned,
                    totalRewardsEarned = c.totalRewardsEarned,
                    totalRewardsRedeemed = c.totalRewardsRedeemed,
                    totalSpendCents = allCustomers[c.customerId]?.totalPurchasesCents ?: 0L
                )
            }

        val scopeLabel = if (branchIdFilter == null) {
            "All Branches Consolidated"
        } else {
            branchMap[branchIdFilter]?.name ?: branchIdFilter
        }

        return LoyaltyAnalyticsReport(
            branchScopeLabel = scopeLabel,
            totalEnrolledCustomers = nonReplacedCards.map { it.customerId }.distinct().size,
            activeCardsCount = nonReplacedCards.count { it.effectiveStatus == LoyaltyCardStatus.ACTIVE },
            notActivatedCardsCount = nonReplacedCards.count { it.effectiveStatus == LoyaltyCardStatus.NOT_ACTIVATED },
            rewardEligibleCardsCount = nonReplacedCards.count { it.effectiveStatus == LoyaltyCardStatus.REWARD_ELIGIBLE },
            expiredCardsCount = nonReplacedCards.count { it.effectiveStatus == LoyaltyCardStatus.EXPIRED },
            suspendedOrReplacedCardsCount = cardRecords.count {
                it.effectiveStatus == LoyaltyCardStatus.SUSPENDED || it.effectiveStatus == LoyaltyCardStatus.REPLACED
            },
            newEnrollmentsInPeriod = cardRecords.count { inPeriod(it.issuedAt) },
            stampsIssuedInPeriod = periodStamps.filter { it.stampsDelta > 0 }.sumOf { it.stampsDelta },
            stampsReversedInPeriod = periodStamps.filter { it.stampsDelta < 0 }.sumOf { kotlin.math.abs(it.stampsDelta) },
            rewardsEarnedInPeriod = periodVouchersEarned.size,
            rewardsRedeemedInPeriod = periodRedemptions.size,
            unusedActiveVouchersCount = voucherRecords.count { it.effectiveStatus == LoyaltyVoucherStatus.ACTIVE },
            expiredVouchersCount = voucherRecords.count { it.effectiveStatus == LoyaltyVoucherStatus.EXPIRED },
            cancelledVouchersCount = voucherRecords.count { it.effectiveStatus == LoyaltyVoucherStatus.CANCELLED },
            totalPromoDiscountGivenCents = periodRedemptions.sumOf { it.discountAppliedCents },
            totalActualPromoCostCents = periodRedemptions.sumOf { it.actualCogsCostCents },
            totalPaidSalesRevenueCents = periodLoyaltyOrders.sumOf { it.paidCents },
            totalRegularDiscountsCents = periodLoyaltyOrders.sumOf {
                (it.discountCents - it.loyaltyVoucherDiscountCents).coerceAtLeast(0L)
            },
            branchRows = branchRows,
            topCustomers = topCustomers
        )
    }

    private suspend fun mintVoucherInTransaction(
        card: LoyaltyCardEntity,
        settings: LoyaltyProgramSettingsEntity,
        orderId: String?,
        invoiceNumber: String?,
        stampTxId: String,
        expiresAt: Long,
        graceExpiresAt: Long,
        nowMillis: Long,
        notes: String
    ): LoyaltyRewardVoucherEntity {
        val code = generateUniqueVoucherCode(settings.voucherPrefix, card.branchId)
        val qrToken = generateUniqueQrToken("AACRV")
        val voucherId = "lvouch_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val rewardValue = settings.rewardAmountCents.coerceIn(100L, LoyaltyRuleConstants.MAX_REWARD_CAP_CENTS)
        val entity = LoyaltyRewardVoucherEntity(
            voucherId = voucherId,
            branchId = card.branchId,
            customerId = card.customerId,
            cardId = card.cardId,
            cycleNumber = card.cycleCount,
            voucherCode = code,
            voucherReference = "CY${card.cycleCount}-${card.cardNumber.takeLast(4)}",
            qrCodeToken = qrToken,
            rewardValueCents = rewardValue,
            earnedFromOrderId = orderId,
            earnedFromInvoiceNumber = invoiceNumber,
            earnedStampTxId = stampTxId,
            earnedAt = nowMillis,
            expiresAt = expiresAt,
            graceExpiresAt = graceExpiresAt,
            status = LoyaltyVoucherStatus.ACTIVE.code,
            notes = notes
        )
        smartDao.insertVoucherIfNew(entity)
        enqueueSync(
            branchId = card.branchId,
            entityType = "LOYALTY_VOUCHER",
            entityId = voucherId,
            payload = """{"voucherId":"$voucherId","voucherCode":"$code","cardId":"${card.cardId}","valueCents":$rewardValue,"status":"ACTIVE"}""",
            nowMillis = nowMillis
        )
        return entity
    }

    private suspend fun generateUniqueCardNumber(prefix: String, branchId: String, custom: String?): String {
        if (!custom.isNullOrBlank()) {
            val normalized = custom.trim().uppercase(Locale.US)
            if (smartDao.getCardByNumber(normalized) != null) {
                throw IllegalArgumentException("Card number '$normalized' is already assigned to another loyalty member.")
            }
            return normalized
        }
        val cleanPrefix = prefix.trim().uppercase(Locale.US).ifBlank { "AAC" }
        val branchTag = branchId.takeLast(2).uppercase(Locale.US).ifBlank { "01" }
        var seq = smartDao.countCards() + 1
        while (true) {
            val candidate = "$cleanPrefix-$branchTag-${"%04d".format(Locale.US, seq)}"
            if (smartDao.getCardByNumber(candidate) == null) return candidate
            seq++
        }
    }

    private suspend fun generateUniqueVoucherCode(prefix: String, branchId: String): String {
        val cleanPrefix = prefix.trim().uppercase(Locale.US).ifBlank { "RWD" }
        val branchTag = branchId.takeLast(2).uppercase(Locale.US).ifBlank { "01" }
        while (true) {
            val token = UUID.randomUUID().toString().replace("-", "").take(6).uppercase(Locale.US)
            val candidate = "$cleanPrefix-$branchTag-$token"
            if (smartDao.getVoucherByCodeOrQr(candidate) == null) return candidate
        }
    }

    private suspend fun generateUniqueQrToken(tag: String): String {
        while (true) {
            val token = "${tag}_${UUID.randomUUID().toString().replace("-", "").take(16).uppercase(Locale.US)}"
            if (smartDao.getCardByQrToken(token) == null && smartDao.getVoucherByCodeOrQr(token) == null) {
                return token
            }
        }
    }

    private suspend fun recordLoyaltyAudit(
        session: UserSession,
        branchId: String,
        entityType: String,
        entityId: String,
        action: String,
        details: String,
        previousValueJson: String = "",
        newValueJson: String = "",
        nowMillis: Long
    ) {
        smartDao.insertAuditLog(
            LoyaltyAuditLogEntity(
                logId = "laud_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                branchId = branchId,
                actorUid = session.uid,
                actorEmail = session.email,
                actorRole = session.role.displayName,
                entityType = entityType,
                entityId = entityId,
                action = action,
                details = details,
                previousValueJson = previousValueJson,
                newValueJson = newValueJson,
                createdAt = nowMillis
            )
        )
        auditLogDao.insertLog(
            AuditLogEntity(
                branchId = branchId,
                actorUid = session.uid,
                actorEmail = session.email,
                actorRole = session.role.displayName,
                action = "LOYALTY_$action",
                details = details,
                timestamp = nowMillis
            )
        )
    }

    private suspend fun enqueueSync(
        branchId: String,
        entityType: String,
        entityId: String,
        payload: String,
        nowMillis: Long
    ) {
        syncQueueDao.enqueueUnique(
            SyncQueueEntity(
                transactionId = "sync_${entityType.lowercase()}_${entityId}_$nowMillis",
                branchId = branchId,
                entityType = entityType,
                entityId = entityId,
                payloadJson = payload,
                status = "PENDING",
                createdAt = nowMillis
            )
        )
    }

    private fun toSettingsRecord(e: LoyaltyProgramSettingsEntity) = LoyaltyProgramSettingsRecord(
        branchId = e.branchId,
        programEnabled = e.programEnabled,
        minQualifyingAmountCents = e.minQualifyingAmountCents,
        requiredStampsForReward = e.requiredStampsForReward,
        rewardAmountCents = e.rewardAmountCents.coerceAtMost(LoyaltyRuleConstants.MAX_REWARD_CAP_CENTS),
        validityMonths = e.validityMonths,
        redemptionGraceDays = e.redemptionGraceDays,
        qualifyingCalculationMethod = QualifyingCalculationMethod.fromCode(e.qualifyingCalculationMethod),
        eligibleCategoryIds = e.eligibleCategoryIdsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        redeemableCategoryIds = e.redeemableCategoryIdsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        excludeTaxFromQualifying = e.excludeTaxFromQualifying,
        excludeServiceAndDeliveryFromQualifying = e.excludeServiceAndDeliveryFromQualifying,
        allowPartialVoucherUsage = e.allowPartialVoucherUsage,
        cardPrefix = e.cardPrefix,
        voucherPrefix = e.voucherPrefix,
        updatedBy = e.updatedBy,
        updatedAt = e.updatedAt
    )

    private fun toCardRecord(
        e: LoyaltyCardEntity,
        requiredStamps: Int,
        hasActiveVoucher: Boolean,
        nowMillis: Long
    ): LoyaltyCardRecord {
        val stored = LoyaltyCardStatus.fromCode(e.status)
        val effective = LoyaltyCalendarCalculator.computeEffectiveCardStatus(
            storedStatus = stored,
            expiresAt = e.expiresAt,
            currentCycleStamps = e.currentCycleStamps,
            requiredStamps = requiredStamps,
            hasActiveVoucher = hasActiveVoucher,
            nowMillis = nowMillis
        )
        return LoyaltyCardRecord(
            cardId = e.cardId,
            branchId = e.branchId,
            customerId = e.customerId,
            customerName = e.customerName,
            customerPhone = e.customerPhone,
            cardNumber = e.cardNumber,
            qrCodeToken = e.qrCodeToken,
            status = stored,
            effectiveStatus = effective,
            issuedAt = e.issuedAt,
            firstQualifyingInvoiceAt = e.firstQualifyingInvoiceAt,
            expiresAt = e.expiresAt,
            graceExpiresAt = e.graceExpiresAt,
            currentCycleStamps = e.currentCycleStamps,
            requiredStamps = requiredStamps,
            totalStampsEarned = e.totalStampsEarned,
            totalRewardsEarned = e.totalRewardsEarned,
            totalRewardsRedeemed = e.totalRewardsRedeemed,
            cycleCount = e.cycleCount,
            replacesCardId = e.replacesCardId,
            replacedByCardId = e.replacedByCardId,
            suspensionOrReplacementReason = e.suspensionOrReplacementReason,
            updatedAt = e.updatedAt
        )
    }

    private fun toStampTxRecord(e: LoyaltyStampTransactionEntity) = LoyaltyStampTransactionRecord(
        stampTxId = e.stampTxId,
        branchId = e.branchId,
        cardId = e.cardId,
        customerId = e.customerId,
        orderId = e.orderId,
        invoiceNumber = e.invoiceNumber,
        txType = LoyaltyStampTxType.fromCode(e.txType),
        stampsDelta = e.stampsDelta,
        stampsAfter = e.stampsAfter,
        qualifyingAmountCents = e.qualifyingAmountCents,
        cycleNumber = e.cycleNumber,
        reason = e.reason,
        approvedBy = e.approvedBy,
        actorName = e.actorName,
        createdAt = e.createdAt,
        isReversed = e.reversedByTxId != null
    )

    private fun toVoucherRecord(
        e: LoyaltyRewardVoucherEntity,
        nowMillis: Long,
        customerName: String = "",
        cardNumber: String = ""
    ): LoyaltyRewardVoucherRecord {
        val stored = LoyaltyVoucherStatus.fromCode(e.status)
        val effective = LoyaltyCalendarCalculator.computeEffectiveVoucherStatus(stored, e.graceExpiresAt, nowMillis)
        return LoyaltyRewardVoucherRecord(
            voucherId = e.voucherId,
            branchId = e.branchId,
            customerId = e.customerId,
            cardId = e.cardId,
            customerName = customerName,
            cardNumber = cardNumber,
            cycleNumber = e.cycleNumber,
            voucherCode = e.voucherCode,
            voucherReference = e.voucherReference,
            qrCodeToken = e.qrCodeToken,
            rewardValueCents = e.rewardValueCents,
            earnedFromOrderId = e.earnedFromOrderId,
            earnedFromInvoiceNumber = e.earnedFromInvoiceNumber,
            earnedAt = e.earnedAt,
            expiresAt = e.expiresAt,
            graceExpiresAt = e.graceExpiresAt,
            status = stored,
            effectiveStatus = effective,
            redeemedOrderId = e.redeemedOrderId,
            redeemedInvoiceNumber = e.redeemedInvoiceNumber,
            redeemedAt = e.redeemedAt,
            redeemedAmountCents = e.redeemedAmountCents,
            actualPromoCostCents = e.actualPromoCostCents,
            cancelledReason = e.cancelledReason,
            notes = e.notes
        )
    }

    private fun toRedemptionRecord(e: LoyaltyRedemptionEntity) = LoyaltyRedemptionRecord(
        redemptionId = e.redemptionId,
        branchId = e.branchId,
        voucherId = e.voucherId,
        voucherCode = e.voucherCode,
        cardId = e.cardId,
        customerId = e.customerId,
        orderId = e.orderId,
        invoiceNumber = e.invoiceNumber,
        voucherFaceValueCents = e.voucherFaceValueCents,
        discountAppliedCents = e.discountAppliedCents,
        actualCogsCostCents = e.actualCogsCostCents,
        eligibleSubtotalCents = e.eligibleSubtotalCents,
        redeemedByName = e.redeemedByName,
        createdAt = e.createdAt
    )

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(millis))
}
