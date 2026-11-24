package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AaCraveDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.DailyCashClosingEntity
import com.example.data.local.DashboardSnapshotEntity
import com.example.data.local.KotTicketEntity
import com.example.data.local.OrderItemEntity
import com.example.data.local.PosOrderEntity
import com.example.data.local.SyncQueueEntity
import com.example.data.local.TransactionRecordEntity
import com.example.domain.model.BillingTotals
import com.example.domain.model.CartItem
import com.example.domain.model.CustomerSummary
import com.example.domain.model.DailyCashClosingSummary
import com.example.domain.model.KotLineItem
import com.example.domain.model.OrderItemLine
import com.example.domain.model.OrderPriority
import com.example.domain.model.OrderStatus
import com.example.domain.model.OrderType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PosOrderRecord
import com.example.domain.model.PosProduct
import com.example.domain.model.ProductAddon
import com.example.domain.model.ProductCategory
import com.example.domain.model.ProductVariation
import com.example.domain.model.SplitPaymentBreakdown
import com.example.domain.model.UserSession
import com.example.domain.model.CustomerInvoiceDocument
import com.example.domain.model.KitchenKotDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PosRepository(private val database: AaCraveDatabase) {

    private val posDao = database.posDao()
    private val branchDao = database.branchDao()
    private val dashboardDao = database.dashboardDao()
    private val auditLogDao = database.auditLogDao()
    private val syncQueueDao = database.syncQueueDao()
    private val smartLoyaltyRepository by lazy { SmartLoyaltyRepository(database) }

    fun observeCategories(branchId: String): Flow<List<ProductCategory>> =
        posDao.observeCategories(branchId).map { list ->
            list.map { ProductCategory(it.categoryId, it.branchId, it.name, it.sortOrder) }
        }

    fun observeProducts(branchId: String): Flow<List<PosProduct>> =
        combine(
            posDao.observeProducts(branchId),
            posDao.observeAllVariations(),
            posDao.observeAllAddons()
        ) { products, variations, addons ->
            val varsByProduct = variations.groupBy { it.productId }
            val addonsByProduct = addons.groupBy { it.productId }
            products
                .filterNot { it.isArchived }
                .map { p ->
                    PosProduct(
                        productId = p.productId,
                        branchId = p.branchId,
                        categoryId = p.categoryId,
                        name = p.name,
                        description = p.description,
                        basePriceCents = p.basePriceCents,
                        sku = p.sku,
                        categoryTag = p.categoryTag,
                        isAvailable = p.isAvailable,
                        taxExempt = p.taxExempt,
                        variations = varsByProduct[p.productId].orEmpty().map { v ->
                            ProductVariation(v.variationId, v.productId, v.name, v.priceDeltaCents, v.isDefault)
                        },
                        addons = addonsByProduct[p.productId].orEmpty().map { a ->
                            ProductAddon(a.addonId, a.productId, a.name, a.priceCents)
                        },
                        isArchived = p.isArchived,
                        isCombo = p.isCombo,
                        comboSummary = p.comboSummary,
                        imagePath = p.imagePath,
                        kitchenStation = p.kitchenStation
                    )
                }
        }

    fun observeCustomers(branchId: String): Flow<List<CustomerSummary>> =
        posDao.observeCustomers(branchId).map { list ->
            list.map {
                CustomerSummary(
                    customerId = it.customerId,
                    branchId = it.branchId,
                    name = it.name,
                    phone = it.phone,
                    email = it.email,
                    loyaltyPoints = it.loyaltyPoints,
                    tier = it.tier,
                    discountPercent = it.discountPercent,
                    address = it.address,
                    totalPurchasesCents = it.totalPurchasesCents,
                    accountBalanceCents = it.accountBalanceCents
                )
            }
        }

    fun observeOrders(branchId: String): Flow<List<PosOrderRecord>> =
        combine(
            posDao.observeOrders(branchId),
            posDao.observeAllOrderItems(),
            branchDao.observeBranches(),
            database.smartLoyaltyDao().observeSettings(branchId)
        ) { orders, allItems, branches, loyaltySettings ->
            val itemsByOrder = allItems.groupBy { it.orderId }
            val branchName = branches.firstOrNull { it.branchId == branchId }?.name ?: "AA CRAVE"
            val reqStamps = (loyaltySettings?.requiredStampsForReward ?: 6).coerceAtLeast(1)
            orders.map { o ->
                toOrderRecord(o, branchName, itemsByOrder[o.orderId].orEmpty(), reqStamps)
            }
        }

    fun observeCashClosings(branchId: String): Flow<List<DailyCashClosingSummary>> =
        posDao.observeCashClosings(branchId).map { list ->
            list.map {
                DailyCashClosingSummary(
                    closingId = it.closingId,
                    branchId = it.branchId,
                    businessDate = it.businessDate,
                    cashSalesCents = it.cashSalesCents,
                    cardSalesCents = it.cardSalesCents,
                    bankTransferSalesCents = it.bankTransferSalesCents,
                    digitalSalesCents = it.digitalSalesCents,
                    refundsCents = it.refundsCents,
                    expectedCashCents = it.expectedCashCents,
                    countedCashCents = it.countedCashCents,
                    varianceCents = it.varianceCents,
                    closedByName = it.closedByName,
                    notes = it.notes,
                    closedAt = it.closedAt
                )
            }
        }

    suspend fun placeOrder(
        session: UserSession,
        idempotencyKey: String,
        cartItems: List<CartItem>,
        totals: BillingTotals,
        orderType: OrderType,
        tableNumber: String?,
        priority: OrderPriority,
        customer: CustomerSummary?,
        paymentMethod: PaymentMethod,
        splitBreakdown: SplitPaymentBreakdown,
        loyaltyPointsToRedeem: Int = 0,
        loyaltyDiscountCents: Long = 0L,
        loyaltyCardId: String? = null,
        loyaltyVoucherId: String? = null,
        clockMillis: Long = System.currentTimeMillis()
    ): Result<PosOrderRecord> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (cartItems.isEmpty()) {
            return Result.failure(IllegalArgumentException("Cart is empty."))
        }
        if (orderType == OrderType.DINE_IN && tableNumber.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Table number is required for Dine-in orders."))
        }
        if (loyaltyPointsToRedeem < 0) {
            return Result.failure(IllegalArgumentException("Redeemed loyalty points cannot be negative."))
        }
        val paidCents = splitBreakdown.totalPaidCents
        if (paidCents < 0L) {
            return Result.failure(IllegalArgumentException("Payment amount cannot be negative."))
        }

        return try {
            val savedRecord = database.withTransaction {
                // Duplicate submission prevention
                val existing = posDao.getOrderByIdempotencyKey(idempotencyKey)
                if (existing != null) {
                    val items = posDao.getItemsForOrder(existing.orderId)
                    return@withTransaction toOrderRecord(existing, session.branchName, items)
                }

                val now = clockMillis
                val validatedVoucher = if (!loyaltyVoucherId.isNullOrBlank()) {
                    smartLoyaltyRepository.validateAndComputeVoucherDiscountInTransaction(
                        branchId = session.branchId,
                        cardId = loyaltyCardId,
                        customerId = customer?.customerId,
                        voucherId = loyaltyVoucherId,
                        cartItems = cartItems,
                        preVoucherTotals = totals,
                        nowMillis = now
                    )
                } else {
                    null
                }

                val effectiveTotals = if (validatedVoucher != null && validatedVoucher.discountAppliedCents > 0L) {
                    com.example.domain.model.BillingCalculator.calculate(
                        items = cartItems,
                        discountPercent = 0.0,
                        manualDiscountCents = totals.discountCents + validatedVoucher.discountAppliedCents,
                        taxRatePercent = totals.taxRatePercent,
                        serviceChargeRatePercent = totals.serviceChargeRatePercent
                    )
                } else {
                    totals
                }

                if (paidCents == 0L && effectiveTotals.totalPayableCents > 0L) {
                    throw IllegalArgumentException("Enter a valid payment amount before confirming.")
                }

                val existingCount = posDao.countOrdersForBranch(session.branchId)
                val seq = existingCount + 1
                val dateTag = SimpleDateFormat("yyMMdd", Locale.US).format(Date(now))
                val orderId = UUID.randomUUID().toString()
                val orderNumber = "ORD-$dateTag-%04d".format(Locale.US, seq)
                val invoiceNumber = "INV-$dateTag-%04d".format(Locale.US, seq)
                val kotNumber = "KOT-%03d".format(Locale.US, seq)

                val remaining = (effectiveTotals.totalPayableCents - paidCents).coerceAtLeast(0L)
                val change = (paidCents - effectiveTotals.totalPayableCents).coerceAtLeast(0L)

                val loyaltyDao = database.customerLoyaltyDao()
                val loyaltyConfig = loyaltyDao.getLoyaltyConfig(session.branchId)
                val freshCustomer = customer?.let { posDao.getCustomerById(it.customerId) }
                val effectiveRedeem = if (freshCustomer != null && loyaltyPointsToRedeem > 0) {
                    if (loyaltyPointsToRedeem > freshCustomer.loyaltyPoints) {
                        throw IllegalArgumentException("Customer has insufficient loyalty points (${freshCustomer.loyaltyPoints} pts available).")
                    }
                    loyaltyPointsToRedeem
                } else {
                    0
                }
                val earnRate = loyaltyConfig?.earnRatePerDollar ?: 1.0
                val earnedPoints = if (freshCustomer != null && effectiveTotals.totalPayableCents > 0L) {
                    ((effectiveTotals.totalPayableCents / 100.0) * earnRate).toInt().coerceAtLeast(0)
                } else {
                    0
                }

                val initialOrderEntity = PosOrderEntity(
                    orderId = orderId,
                    branchId = session.branchId,
                    idempotencyKey = idempotencyKey,
                    orderNumber = orderNumber,
                    invoiceNumber = invoiceNumber,
                    kotNumber = kotNumber,
                    orderType = orderType.name,
                    tableNumber = tableNumber?.trim()?.takeIf { it.isNotBlank() },
                    priority = priority.name,
                    customerId = customer?.customerId,
                    customerName = customer?.name ?: "Walk-in Guest",
                    subtotalCents = effectiveTotals.subtotalCents,
                    discountCents = effectiveTotals.discountCents,
                    taxCents = effectiveTotals.taxCents,
                    serviceChargeCents = effectiveTotals.serviceChargeCents,
                    totalCents = effectiveTotals.totalPayableCents,
                    paidCents = paidCents,
                    remainingBalanceCents = remaining,
                    changeCents = change,
                    paymentMethod = paymentMethod.name,
                    cashPaidCents = splitBreakdown.cashCents,
                    cardPaidCents = splitBreakdown.cardCents,
                    bankPaidCents = splitBreakdown.bankTransferCents,
                    digitalPaidCents = splitBreakdown.digitalCents,
                    paymentBreakdown = splitBreakdown.toSummaryString(),
                    status = OrderStatus.CONFIRMED.name,
                    reprintInvoiceCount = 0,
                    reprintKotCount = 0,
                    cashierUid = session.uid,
                    cashierName = session.displayName,
                    approvedBy = null,
                    cancellationReason = null,
                    createdAt = now,
                    loyaltyPointsRedeemed = effectiveRedeem,
                    loyaltyDiscountCents = loyaltyDiscountCents.coerceAtLeast(0L),
                    loyaltyPointsEarned = earnedPoints,
                    loyaltyCardId = loyaltyCardId ?: validatedVoucher?.card?.cardId,
                    loyaltyCardNumber = validatedVoucher?.card?.cardNumber,
                    loyaltyVoucherId = validatedVoucher?.voucher?.voucherId,
                    loyaltyVoucherCode = validatedVoucher?.voucher?.voucherCode,
                    loyaltyVoucherDiscountCents = validatedVoucher?.discountAppliedCents ?: 0L,
                    loyaltyPromoCostCents = validatedVoucher?.actualPromoCostCents ?: 0L
                )

                val itemEntities = cartItems.mapIndexed { idx, line ->
                    OrderItemEntity(
                        itemId = "${orderId}_item_$idx",
                        orderId = orderId,
                        productId = line.product.productId,
                        productName = line.product.name,
                        variationName = line.selectedVariation?.name,
                        addonsSummary = line.addonsSummary,
                        specialInstructions = line.specialInstructions.trim(),
                        quantity = line.quantity,
                        unitPriceCents = line.baseAndVariationUnitCents,
                        addonsUnitCents = line.addonsUnitCents,
                        lineTotalCents = line.lineTotalCents
                    )
                }

                val stations = cartItems
                    .map { it.product.kitchenStation.ifBlank { "MAIN_KITCHEN" } }
                    .distinct()

                posDao.insertOrder(initialOrderEntity)
                posDao.insertOrderItems(itemEntities)
                AccountingIntegrationHelper.postForPosOrderInTransaction(
                    database = database,
                    order = initialOrderEntity,
                    actorUid = session.uid,
                    actorName = session.displayName
                )

                if (stations.size <= 1) {
                    posDao.insertKotTicket(
                        KotTicketEntity(
                            kotId = "kot_$orderId",
                            orderId = orderId,
                            branchId = session.branchId,
                            kotNumber = kotNumber,
                            orderNumber = orderNumber,
                            orderType = orderType.name,
                            tableNumber = initialOrderEntity.tableNumber,
                            priority = priority.name,
                            station = stations.firstOrNull() ?: "MAIN_KITCHEN",
                            status = "NEW",
                            reprintCount = 0,
                            createdAt = now
                        )
                    )
                } else {
                    stations.forEach { stationCode ->
                        posDao.insertKotTicket(
                            KotTicketEntity(
                                kotId = "kot_${orderId}_$stationCode",
                                orderId = orderId,
                                branchId = session.branchId,
                                kotNumber = kotNumber,
                                orderNumber = orderNumber,
                                orderType = orderType.name,
                                tableNumber = initialOrderEntity.tableNumber,
                                priority = priority.name,
                                station = stationCode,
                                status = "NEW",
                                reprintCount = 0,
                                createdAt = now
                            )
                        )
                    }
                }

                // Automatic recipe & finished-product inventory deduction (idempotent via unique deductionKey)
                InventorySyncHelper.deductForOrderInTransaction(
                    database = database,
                    branchId = session.branchId,
                    orderId = orderId,
                    orderNumber = orderNumber,
                    items = itemEntities,
                    actorName = session.displayName,
                    now = now
                )

                // Phase 4: Update customer purchase totals & idempotent loyalty earn/redeem
                if (freshCustomer != null) {
                    var updatedPoints = freshCustomer.loyaltyPoints
                    if (effectiveRedeem > 0) {
                        val redeemRow = loyaltyDao.insertLoyaltyTransactionIfNew(
                            com.example.data.local.LoyaltyTransactionEntity(
                                txId = "ltx_${UUID.randomUUID()}",
                                branchId = session.branchId,
                                customerId = freshCustomer.customerId,
                                customerName = freshCustomer.name,
                                idempotencyKey = "pos_redeem_$idempotencyKey",
                                txType = "REDEEM",
                                pointsDelta = -effectiveRedeem,
                                orderId = orderId,
                                reason = "Redeemed at POS on $orderNumber",
                                approvedBy = session.displayName,
                                actorName = session.displayName,
                                createdAt = now
                            )
                        )
                        if (redeemRow != -1L) {
                            updatedPoints = (updatedPoints - effectiveRedeem).coerceAtLeast(0)
                        }
                    }
                    if (earnedPoints > 0) {
                        val earnRow = loyaltyDao.insertLoyaltyTransactionIfNew(
                            com.example.data.local.LoyaltyTransactionEntity(
                                txId = "ltx_${UUID.randomUUID()}",
                                branchId = session.branchId,
                                customerId = freshCustomer.customerId,
                                customerName = freshCustomer.name,
                                idempotencyKey = "pos_earn_$idempotencyKey",
                                txType = "EARN",
                                pointsDelta = earnedPoints,
                                orderId = orderId,
                                reason = "Earned on POS order $orderNumber",
                                approvedBy = null,
                                actorName = session.displayName,
                                createdAt = now
                            )
                        )
                        if (earnRow != -1L) {
                            updatedPoints += earnedPoints
                        }
                    }
                    val (newTier, newDiscountPct) = CustomerLoyaltyRepository.computeTierAndDiscount(
                        updatedPoints,
                        loyaltyConfig
                    )
                    loyaltyDao.upsertCustomer(
                        freshCustomer.copy(
                            loyaltyPoints = updatedPoints,
                            tier = newTier,
                            discountPercent = newDiscountPct,
                            totalPurchasesCents = freshCustomer.totalPurchasesCents + effectiveTotals.totalPayableCents,
                            accountBalanceCents = freshCustomer.accountBalanceCents + remaining,
                            lastOrderAt = now
                        )
                    )
                }

                val loyaltyOutcome = smartLoyaltyRepository.finalizeLoyaltyInPosTransaction(
                    session = session,
                    orderEntity = initialOrderEntity,
                    cartItems = cartItems,
                    preVoucherTotals = totals,
                    cardId = loyaltyCardId,
                    validatedVoucher = validatedVoucher,
                    nowMillis = now
                )

                val orderEntity = initialOrderEntity.copy(
                    loyaltyCardId = loyaltyOutcome.cardId ?: initialOrderEntity.loyaltyCardId,
                    loyaltyCardNumber = loyaltyOutcome.cardNumber ?: initialOrderEntity.loyaltyCardNumber,
                    loyaltyStampChange = loyaltyOutcome.stampChange,
                    loyaltyStampsAfter = loyaltyOutcome.stampsAfter,
                    loyaltyVoucherId = loyaltyOutcome.voucherId ?: initialOrderEntity.loyaltyVoucherId,
                    loyaltyVoucherCode = loyaltyOutcome.voucherCode ?: initialOrderEntity.loyaltyVoucherCode,
                    loyaltyVoucherDiscountCents = loyaltyOutcome.voucherDiscountCents,
                    loyaltyPromoCostCents = loyaltyOutcome.promoCostCents
                )

                // Update live Dashboard snapshot & Recent Transactions in Room
                val timeLabel = SimpleDateFormat("hh:mm a", Locale.US).format(Date(now))
                dashboardDao.upsertTransactions(
                    listOf(
                        TransactionRecordEntity(
                            id = orderId,
                            branchId = session.branchId,
                            orderNumber = orderNumber,
                            invoiceNumber = invoiceNumber,
                            customerName = orderEntity.customerName ?: "Walk-in",
                            amount = effectiveTotals.totalPayableCents / 100.0,
                            paymentMethod = paymentMethod.label,
                            status = if (remaining > 0) "Partial" else "Paid",
                            timeLabel = timeLabel,
                            isSampleData = false,
                            createdAt = now
                        )
                    )
                )

                val currentSnap = dashboardDao.observeSnapshot(session.branchId).first()
                if (currentSnap != null) {
                    val addedCash = splitBreakdown.cashCents / 100.0
                    val addedDigital = (splitBreakdown.cardCents + splitBreakdown.bankTransferCents + splitBreakdown.digitalCents) / 100.0
                    val netSale = effectiveTotals.totalPayableCents / 100.0
                    dashboardDao.upsertSnapshots(
                        listOf(
                            currentSnap.copy(
                                todaySales = currentSnap.todaySales + netSale,
                                todayOrders = currentSnap.todayOrders + 1,
                                totalCustomers = currentSnap.totalCustomers + 1,
                                pendingKitchenOrders = currentSnap.pendingKitchenOrders + 1,
                                estimatedProfit = currentSnap.estimatedProfit + netSale,
                                cashPayments = currentSnap.cashPayments + addedCash,
                                digitalPayments = currentSnap.digitalPayments + addedDigital,
                                isSampleData = false,
                                updatedAt = now
                            )
                        )
                    )
                }

                // Queue offline sync record (never silently dropped)
                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_order_$idempotencyKey",
                        branchId = session.branchId,
                        entityType = "POS_ORDER",
                        entityId = orderId,
                        payloadJson = """{"orderNumber":"$orderNumber","invoiceNumber":"$invoiceNumber","kotNumber":"$kotNumber","totalCents":${effectiveTotals.totalPayableCents}}""",
                        status = "PENDING",
                        lastError = "Realtime Database in Locked Mode — queued safely in Room"
                    )
                )

                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "ORDER_CONFIRMED",
                        details = "Order $orderNumber | Invoice $invoiceNumber | KOT $kotNumber | Total ${effectiveTotals.totalPayableCents}c"
                    )
                )

                val branchSettings = database.smartLoyaltyDao().getSettings(session.branchId)
                toOrderRecord(
                    o = orderEntity,
                    branchName = session.branchName,
                    items = itemEntities,
                    requiredStamps = (branchSettings?.requiredStampsForReward ?: 6).coerceAtLeast(1),
                    statusMessageOverride = loyaltyOutcome.statusNote
                )
            }
            Result.success(savedRecord)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelOrder(session: UserSession, orderId: String, reason: String): Result<Unit> {
        if (!session.capabilities.canApproveRefundsOrCancellations) {
            return Result.failure(SecurityException("Manager, Owner, or Super Admin approval is required to cancel orders."))
        }
        if (reason.isBlank()) {
            return Result.failure(IllegalArgumentException("Cancellation reason is required."))
        }
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        return try {
            database.withTransaction {
                val order = posDao.getOrderById(orderId)
                    ?: throw IllegalArgumentException("Order not found.")
                ErpAuthorizationGuard.verifyBranchAccess(session, order.branchId)?.let { throw it }
                if (order.status == OrderStatus.CANCELLED.name || order.status == OrderStatus.REFUNDED.name) {
                    throw IllegalStateException("Order is already ${order.status.lowercase()}.")
                }
                posDao.updateOrderStatus(orderId, OrderStatus.CANCELLED.name, session.displayName, reason.trim())
                posDao.updateKotStatusByOrderId(orderId, "CANCELLED")
                smartLoyaltyRepository.reverseLoyaltyForOrderInTransaction(
                    session = session,
                    order = order.copy(status = OrderStatus.CANCELLED.name),
                    reason = reason.trim()
                )
                AccountingIntegrationHelper.postReversalForPosOrderInTransaction(
                    database = database,
                    order = order,
                    actorUid = session.uid,
                    actorName = session.displayName,
                    reason = reason.trim()
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "ORDER_CANCELLED",
                        details = "Cancelled ${order.orderNumber} (${order.invoiceNumber}) — Reason: ${reason.trim()}"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refundOrder(session: UserSession, orderId: String, reason: String): Result<Unit> {
        if (!session.capabilities.canApproveRefundsOrCancellations) {
            return Result.failure(SecurityException("Manager, Owner, or Super Admin approval is required for refunds."))
        }
        if (reason.isBlank()) {
            return Result.failure(IllegalArgumentException("Refund reason is required."))
        }
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        return try {
            database.withTransaction {
                val order = posDao.getOrderById(orderId)
                    ?: throw IllegalArgumentException("Order not found.")
                ErpAuthorizationGuard.verifyBranchAccess(session, order.branchId)?.let { throw it }
                if (order.status == OrderStatus.REFUNDED.name || order.status == OrderStatus.CANCELLED.name) {
                    throw IllegalStateException("Order is already ${order.status.lowercase()}.")
                }
                posDao.updateOrderStatus(orderId, OrderStatus.REFUNDED.name, session.displayName, reason.trim())
                smartLoyaltyRepository.reverseLoyaltyForOrderInTransaction(
                    session = session,
                    order = order.copy(status = OrderStatus.REFUNDED.name),
                    reason = reason.trim()
                )
                AccountingIntegrationHelper.postReversalForPosOrderInTransaction(
                    database = database,
                    order = order,
                    actorUid = session.uid,
                    actorName = session.displayName,
                    reason = reason.trim()
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "ORDER_REFUNDED",
                        details = "Refunded ${order.invoiceNumber} (${order.paidCents}c) — Approved by ${session.displayName}: ${reason.trim()}"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordInvoiceReprint(session: UserSession, orderId: String) {
        database.withTransaction {
            val order = posDao.getOrderById(orderId) ?: return@withTransaction
            posDao.incrementInvoiceReprint(orderId)
            auditLogDao.insertLog(
                AuditLogEntity(
                    branchId = session.branchId,
                    actorUid = session.uid,
                    actorEmail = session.email,
                    actorRole = session.role.displayName,
                    action = "INVOICE_REPRINT",
                    details = "Reprinted Customer Invoice ${order.invoiceNumber} (reprint #${order.reprintInvoiceCount + 1})"
                )
            )
        }
    }

    suspend fun recordKotReprint(session: UserSession, orderId: String) {
        database.withTransaction {
            val order = posDao.getOrderById(orderId) ?: return@withTransaction
            posDao.incrementKotReprint(orderId)
            posDao.incrementKotTicketReprint(orderId)
            auditLogDao.insertLog(
                AuditLogEntity(
                    branchId = session.branchId,
                    actorUid = session.uid,
                    actorEmail = session.email,
                    actorRole = session.role.displayName,
                    action = "KOT_REPRINT",
                    details = "Reprinted Kitchen KOT ${order.kotNumber} for ${order.orderNumber} (reprint #${order.reprintKotCount + 1})"
                )
            )
        }
    }

    suspend fun submitDailyCashClosing(
        session: UserSession,
        countedCashCents: Long,
        notes: String
    ): Result<DailyCashClosingSummary> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        return try {
            val result = database.withTransaction {
                val orders = posDao.observeOrders(session.branchId).first()
                val activeOrders = orders.filter { it.status != OrderStatus.CANCELLED.name }
                val refundedOrders = orders.filter { it.status == OrderStatus.REFUNDED.name }

                val cashSales = activeOrders.sumOf { it.cashPaidCents }
                val cardSales = activeOrders.sumOf { it.cardPaidCents }
                val bankSales = activeOrders.sumOf { it.bankPaidCents }
                val digitalSales = activeOrders.sumOf { it.digitalPaidCents }
                val refunds = refundedOrders.sumOf { it.paidCents }
                val expectedCash = (cashSales - refunds).coerceAtLeast(0L)
                val variance = countedCashCents - expectedCash

                val now = System.currentTimeMillis()
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
                val closingId = "close_${session.branchId}_$now"

                val entity = DailyCashClosingEntity(
                    closingId = closingId,
                    branchId = session.branchId,
                    businessDate = dateStr,
                    cashSalesCents = cashSales,
                    cardSalesCents = cardSales,
                    bankTransferSalesCents = bankSales,
                    digitalSalesCents = digitalSales,
                    refundsCents = refunds,
                    expectedCashCents = expectedCash,
                    countedCashCents = countedCashCents,
                    varianceCents = variance,
                    closedByUid = session.uid,
                    closedByName = session.displayName,
                    notes = notes.trim(),
                    closedAt = now
                )
                posDao.insertCashClosing(entity)
                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_$closingId",
                        branchId = session.branchId,
                        entityType = "CASH_CLOSING",
                        entityId = closingId,
                        payloadJson = """{"date":"$dateStr","expectedCashCents":$expectedCash,"countedCashCents":$countedCashCents}""",
                        status = "PENDING",
                        lastError = "Realtime Database in Locked Mode — queued safely in Room"
                    )
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "DAILY_CASH_CLOSING",
                        details = "Closed register for $dateStr | Expected: ${expectedCash}c | Counted: ${countedCashCents}c | Variance: ${variance}c"
                    )
                )
                DailyCashClosingSummary(
                    closingId = entity.closingId,
                    branchId = entity.branchId,
                    businessDate = entity.businessDate,
                    cashSalesCents = entity.cashSalesCents,
                    cardSalesCents = entity.cardSalesCents,
                    bankTransferSalesCents = entity.bankTransferSalesCents,
                    digitalSalesCents = entity.digitalSalesCents,
                    refundsCents = entity.refundsCents,
                    expectedCashCents = entity.expectedCashCents,
                    countedCashCents = entity.countedCashCents,
                    varianceCents = entity.varianceCents,
                    closedByName = entity.closedByName,
                    notes = entity.notes,
                    closedAt = entity.closedAt
                )
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildCustomerInvoice(order: PosOrderRecord, branchAddress: String, isReprint: Boolean): CustomerInvoiceDocument {
        return CustomerInvoiceDocument(
            orderId = order.orderId,
            branchName = order.branchName,
            branchAddress = branchAddress,
            invoiceNumber = order.invoiceNumber,
            orderNumber = order.orderNumber,
            timestampMillis = order.createdAt,
            customerName = order.customerName,
            orderType = order.orderType,
            tableNumber = order.tableNumber,
            items = order.items,
            subtotalCents = order.subtotalCents,
            discountCents = order.discountCents,
            taxCents = order.taxCents,
            serviceChargeCents = order.serviceChargeCents,
            totalPayableCents = order.totalCents,
            paymentMethodLabel = order.paymentMethod.label,
            paymentBreakdown = order.paymentBreakdown,
            paidAmountCents = order.paidCents,
            remainingBalanceCents = order.remainingBalanceCents,
            changeCents = order.changeCents,
            thankYouMessage = "Thank you for dining with AA CRAVE! We appreciate your visit.",
            isReprint = isReprint || order.reprintInvoiceCount > 0,
            reprintNumber = order.reprintInvoiceCount,
            loyaltyCardId = order.loyaltyCardId,
            loyaltyCardNumber = order.loyaltyCardNumber,
            loyaltyStampChange = order.loyaltyStampChange,
            loyaltyStampsAfter = order.loyaltyStampsAfter,
            loyaltyRequiredStamps = order.loyaltyRequiredStamps,
            loyaltyVoucherId = order.loyaltyVoucherId,
            loyaltyVoucherCode = order.loyaltyVoucherCode,
            loyaltyVoucherDiscountCents = order.loyaltyVoucherDiscountCents,
            loyaltyStatusMessage = order.loyaltyStatusMessage
        )
    }

    fun buildKitchenKot(order: PosOrderRecord, isReprint: Boolean): KitchenKotDocument {
        return KitchenKotDocument(
            kotId = "kot_${order.orderId}",
            branchName = order.branchName,
            kotNumber = order.kotNumber,
            orderNumber = order.orderNumber,
            timestampMillis = order.createdAt,
            orderType = order.orderType,
            tableNumber = order.tableNumber,
            priority = order.priority,
            station = "MAIN KITCHEN",
            items = order.items.map {
                KotLineItem(
                    productName = it.productName,
                    variationName = it.variationName,
                    quantity = it.quantity,
                    addonsSummary = it.addonsSummary,
                    specialInstructions = it.specialInstructions
                )
            },
            isReprint = isReprint || order.reprintKotCount > 0,
            reprintNumber = order.reprintKotCount
        )
    }

    private fun toOrderRecord(
        o: PosOrderEntity,
        branchName: String,
        items: List<OrderItemEntity>,
        requiredStamps: Int = 6,
        statusMessageOverride: String? = null
    ): PosOrderRecord {
        val derivedStatusMsg = statusMessageOverride ?: run {
            val parts = mutableListOf<String>()
            if (!o.loyaltyVoucherCode.isNullOrBlank() && o.loyaltyVoucherDiscountCents > 0L) {
                parts += "Redeemed ${o.loyaltyVoucherCode} (-${com.example.domain.model.centsToPkr(o.loyaltyVoucherDiscountCents)})"
            }
            if (o.loyaltyStampChange > 0) {
                parts += "+${o.loyaltyStampChange} Stamp (${o.loyaltyStampsAfter}/$requiredStamps)"
            } else if (!o.loyaltyCardNumber.isNullOrBlank() && o.loyaltyVoucherCode == null) {
                parts += "Card ${o.loyaltyCardNumber} (${o.loyaltyStampsAfter}/$requiredStamps)"
            }
            parts.joinToString(" • ").ifBlank { null }
        }
        return PosOrderRecord(
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
            items = items.map {
                OrderItemLine(
                    itemId = it.itemId,
                    productId = it.productId,
                    productName = it.productName,
                    variationName = it.variationName,
                    addonsSummary = it.addonsSummary,
                    specialInstructions = it.specialInstructions,
                    quantity = it.quantity,
                    unitPriceCents = it.unitPriceCents,
                    addonsUnitCents = it.addonsUnitCents,
                    lineTotalCents = it.lineTotalCents
                )
            },
            loyaltyCardId = o.loyaltyCardId,
            loyaltyCardNumber = o.loyaltyCardNumber,
            loyaltyStampChange = o.loyaltyStampChange,
            loyaltyStampsAfter = o.loyaltyStampsAfter,
            loyaltyVoucherId = o.loyaltyVoucherId,
            loyaltyVoucherCode = o.loyaltyVoucherCode,
            loyaltyVoucherDiscountCents = o.loyaltyVoucherDiscountCents,
            loyaltyPromoCostCents = o.loyaltyPromoCostCents,
            loyaltyRequiredStamps = requiredStamps,
            loyaltyStatusMessage = derivedStatusMsg
        )
    }
}
