package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.AaCraveDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerFeedbackEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.LowStockAlertEntity
import com.example.data.local.LoyaltyConfigEntity
import com.example.data.local.LoyaltyRewardEntity
import com.example.data.local.LoyaltyTransactionEntity
import com.example.data.local.PurchaseItemEntity
import com.example.data.local.PurchaseOrderEntity
import com.example.data.local.PurchaseReceivingEntity
import com.example.data.local.PurchaseReturnEntity
import com.example.data.local.StockMovementEntity
import com.example.data.local.SupplierEntity
import com.example.data.local.SupplierPaymentEntity
import com.example.data.local.SyncQueueEntity
import com.example.domain.model.CustomerFeedbackRecord
import com.example.domain.model.CustomerProfileRecord
import com.example.domain.model.ExpenseApprovalStatus
import com.example.domain.model.ExpensePeriodReport
import com.example.domain.model.ExpenseRecord
import com.example.domain.model.ExpenseRecurrence
import com.example.domain.model.LoyaltyConfigRecord
import com.example.domain.model.LoyaltyRewardRecord
import com.example.domain.model.LoyaltyTransactionRecord
import com.example.domain.model.PurchaseDraftLine
import com.example.domain.model.PurchaseItemRecord
import com.example.domain.model.PurchaseOrderRecord
import com.example.domain.model.PurchasePaymentStatus
import com.example.domain.model.PurchaseReceivingRecord
import com.example.domain.model.PurchaseReturnRecord
import com.example.domain.model.PurchaseStatus
import com.example.domain.model.SupplierPaymentRecord
import com.example.domain.model.SupplierPerformanceMetric
import com.example.domain.model.SupplierStatementLine
import com.example.domain.model.SupplierStatementReport
import com.example.domain.model.SupplierSummary
import com.example.domain.model.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

class SupplierRepository(private val database: AaCraveDatabase) {

    private val supplierDao = database.supplierDao()
    private val purchaseDao = database.purchaseDao()
    private val auditLogDao = database.auditLogDao()
    private val syncQueueDao = database.syncQueueDao()

    fun observeSuppliers(branchId: String): Flow<List<SupplierSummary>> =
        supplierDao.observeSuppliers(branchId).map { list -> list.map { it.toDomain() } }

    fun observePayments(branchId: String): Flow<List<SupplierPaymentRecord>> =
        supplierDao.observeSupplierPayments(branchId).map { list -> list.map { it.toDomain() } }

    suspend fun saveSupplier(
        session: UserSession,
        supplierId: String?,
        name: String,
        contactPerson: String,
        phone: String,
        email: String,
        address: String,
        category: String,
        openingBalanceCents: Long,
        isActive: Boolean = true
    ): Result<SupplierSummary> {
        if (!session.capabilities.canManageSuppliers) {
            return Result.failure(SecurityException("Access denied: unauthorized to manage suppliers."))
        }
        if (name.isBlank() || phone.isBlank()) {
            return Result.failure(IllegalArgumentException("Supplier name and phone number are required."))
        }
        if (openingBalanceCents < 0L) {
            return Result.failure(IllegalArgumentException("Opening balance cannot be negative."))
        }
        return try {
            val saved = database.withTransaction {
                val now = System.currentTimeMillis()
                val existing = supplierId?.let { supplierDao.getSupplierById(it) }
                val entity = if (existing != null) {
                    existing.copy(
                        name = name.trim(),
                        contactPerson = contactPerson.trim(),
                        phone = phone.trim(),
                        email = email.trim(),
                        address = address.trim(),
                        category = category.trim().ifBlank { "General" },
                        openingBalanceCents = openingBalanceCents,
                        isActive = isActive
                    )
                } else {
                    SupplierEntity(
                        supplierId = "sup_${UUID.randomUUID()}",
                        branchId = session.branchId,
                        name = name.trim(),
                        contactPerson = contactPerson.trim(),
                        phone = phone.trim(),
                        email = email.trim(),
                        address = address.trim(),
                        category = category.trim().ifBlank { "General" },
                        openingBalanceCents = openingBalanceCents,
                        totalPurchasesCents = 0L,
                        totalReturnsCents = 0L,
                        totalPaidCents = 0L,
                        isActive = isActive,
                        createdAt = now
                    )
                }
                supplierDao.upsertSupplier(entity)
                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_sup_${entity.supplierId}_$now",
                        branchId = session.branchId,
                        entityType = "SUPPLIER",
                        entityId = entity.supplierId,
                        payloadJson = """{"name":"${entity.name}","category":"${entity.category}"}""",
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
                        action = if (existing == null) "SUPPLIER_CREATED" else "SUPPLIER_UPDATED",
                        details = "Supplier ${entity.name} (${entity.category})"
                    )
                )
                entity.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordSupplierPayment(
        session: UserSession,
        idempotencyKey: String,
        supplierId: String,
        purchaseId: String?,
        amountCents: Long,
        paymentMethod: String,
        referenceNumber: String,
        paymentDateMillis: Long,
        notes: String
    ): Result<SupplierPaymentRecord> {
        if (!session.capabilities.canPaySuppliers) {
            return Result.failure(SecurityException("Access denied: Owner, Branch Manager, or Super Admin approval required for supplier payments."))
        }
        if (amountCents <= 0L) {
            return Result.failure(IllegalArgumentException("Payment amount must be greater than zero."))
        }
        if (referenceNumber.isBlank()) {
            return Result.failure(IllegalArgumentException("Payment reference number is required."))
        }
        return try {
            val saved = database.withTransaction {
                val existingPay = supplierDao.getPaymentByIdempotencyKey(idempotencyKey)
                if (existingPay != null) {
                    return@withTransaction existingPay.toDomain()
                }
                val supplier = supplierDao.getSupplierById(supplierId)
                    ?: throw IllegalArgumentException("Supplier not found.")
                val linkedPo = purchaseId?.let { purchaseDao.getPurchaseById(it) }
                if (purchaseId != null && linkedPo == null) {
                    throw IllegalArgumentException("Linked purchase order not found.")
                }
                if (linkedPo != null) {
                    if (linkedPo.supplierId != supplierId) {
                        throw IllegalArgumentException("Purchase order does not belong to selected supplier.")
                    }
                    if (linkedPo.status == PurchaseStatus.CANCELLED.name) {
                        throw IllegalStateException("Cannot record payment against a cancelled purchase order.")
                    }
                    val remaining = (linkedPo.totalCents - linkedPo.returnedCents - linkedPo.paidCents).coerceAtLeast(0L)
                    if (amountCents > remaining) {
                        throw IllegalArgumentException("Payment exceeds PO remaining payable balance.")
                    }
                }

                val now = System.currentTimeMillis()
                val payment = SupplierPaymentEntity(
                    paymentId = "spay_${UUID.randomUUID()}",
                    branchId = session.branchId,
                    idempotencyKey = idempotencyKey,
                    supplierId = supplier.supplierId,
                    supplierName = supplier.name,
                    purchaseId = linkedPo?.purchaseId,
                    poNumber = linkedPo?.poNumber,
                    amountCents = amountCents,
                    paymentMethod = paymentMethod.trim().ifBlank { "Bank Transfer" },
                    referenceNumber = referenceNumber.trim(),
                    paymentDateMillis = paymentDateMillis,
                    notes = notes.trim(),
                    paidByName = session.displayName,
                    createdAt = now
                )
                val insertedId = supplierDao.insertPaymentIfNew(payment)
                if (insertedId == -1L) {
                    return@withTransaction payment.toDomain()
                }

                // Update supplier paid totals (strictly Accounts Payable, NEVER logged as operating expense)
                supplierDao.upsertSupplier(
                    supplier.copy(totalPaidCents = supplier.totalPaidCents + amountCents)
                )

                if (linkedPo != null) {
                    val newPaid = linkedPo.paidCents + amountCents
                    val netObligation = (linkedPo.totalCents - linkedPo.returnedCents).coerceAtLeast(0L)
                    val newPayStatus = when {
                        newPaid >= netObligation -> PurchasePaymentStatus.PAID.name
                        newPaid > 0L -> PurchasePaymentStatus.PARTIALLY_PAID.name
                        else -> PurchasePaymentStatus.UNPAID.name
                    }
                    purchaseDao.upsertPurchaseOrder(
                        linkedPo.copy(
                            paidCents = newPaid,
                            paymentStatus = newPayStatus,
                            updatedAt = now
                        )
                    )
                }

                AccountingIntegrationHelper.postForSupplierPaymentInTransaction(
                    database = database,
                    payment = payment,
                    actorUid = session.uid,
                    actorName = session.displayName
                )

                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_spay_$idempotencyKey",
                        branchId = session.branchId,
                        entityType = "SUPPLIER_PAYMENT",
                        entityId = payment.paymentId,
                        payloadJson = """{"supplierId":"${supplier.supplierId}","amountCents":$amountCents,"ref":"${payment.referenceNumber}"}""",
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
                        action = "SUPPLIER_PAYMENT",
                        details = "Paid ${amountCents}c to ${supplier.name} (Ref: ${payment.referenceNumber}${linkedPo?.let { ", PO: ${it.poNumber}" } ?: ""})"
                    )
                )
                payment.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildSupplierStatement(
        supplier: SupplierSummary,
        orders: List<PurchaseOrderRecord>,
        returns: List<PurchaseReturnRecord>,
        payments: List<SupplierPaymentRecord>
    ): SupplierStatementReport {
        val rawEvents = mutableListOf<SupplierStatementLine>()
        var running = supplier.openingBalanceCents
        rawEvents += SupplierStatementLine(
            dateMillis = supplier.createdAt,
            reference = "OPENING",
            entryType = "OPENING",
            description = "Opening Supplier Balance",
            debitCents = supplier.openingBalanceCents,
            creditCents = 0L,
            runningBalanceCents = running
        )

        data class LedgerEvent(
            val dateMillis: Long,
            val reference: String,
            val entryType: String,
            val description: String,
            val debitCents: Long,
            val creditCents: Long
        )

        val events = mutableListOf<LedgerEvent>()
        orders.filter {
            it.supplierId == supplier.supplierId &&
                (it.status == PurchaseStatus.PARTIALLY_RECEIVED || it.status == PurchaseStatus.RECEIVED)
        }.forEach { po ->
            events += LedgerEvent(
                dateMillis = po.createdAt,
                reference = po.poNumber,
                entryType = "PURCHASE",
                description = "Purchase Invoice ${po.supplierInvoiceNumber.ifBlank { po.poNumber }}",
                debitCents = po.totalCents,
                creditCents = 0L
            )
        }
        returns.filter { it.supplierId == supplier.supplierId }.forEach { ret ->
            events += LedgerEvent(
                dateMillis = ret.returnedAt,
                reference = ret.poNumber,
                entryType = "RETURN",
                description = "Return: ${ret.returnQty} ${ret.unit} ${ret.itemName}",
                debitCents = 0L,
                creditCents = ret.returnAmountCents
            )
        }
        payments.filter { it.supplierId == supplier.supplierId }.forEach { pay ->
            events += LedgerEvent(
                dateMillis = pay.paymentDateMillis,
                reference = pay.referenceNumber,
                entryType = "PAYMENT",
                description = "Payment (${pay.paymentMethod})${pay.poNumber?.let { " • $it" } ?: ""}",
                debitCents = 0L,
                creditCents = pay.amountCents
            )
        }

        events.sortedBy { it.dateMillis }.forEach { ev ->
            running = (running + ev.debitCents - ev.creditCents).coerceAtLeast(0L)
            rawEvents += SupplierStatementLine(
                dateMillis = ev.dateMillis,
                reference = ev.reference,
                entryType = ev.entryType,
                description = ev.description,
                debitCents = ev.debitCents,
                creditCents = ev.creditCents,
                runningBalanceCents = running
            )
        }

        return SupplierStatementReport(
            supplier = supplier,
            lines = rawEvents,
            closingBalanceCents = supplier.outstandingBalanceCents
        )
    }

    fun buildPerformanceMetrics(
        suppliers: List<SupplierSummary>,
        orders: List<PurchaseOrderRecord>,
        returns: List<PurchaseReturnRecord>
    ): List<SupplierPerformanceMetric> {
        return suppliers.map { sup ->
            val supOrders = orders.filter { it.supplierId == sup.supplierId && it.status != PurchaseStatus.CANCELLED }
            val fulfilled = supOrders.count { it.status == PurchaseStatus.RECEIVED || it.status == PurchaseStatus.PARTIALLY_RECEIVED }
            val supReturnsCents = returns.filter { it.supplierId == sup.supplierId }.sumOf { it.returnAmountCents }
            val totalSpend = sup.totalPurchasesCents
            val returnRate = if (totalSpend > 0L) (supReturnsCents * 100.0) / totalSpend else 0.0
            SupplierPerformanceMetric(
                supplierId = sup.supplierId,
                supplierName = sup.name,
                category = sup.category,
                totalOrders = supOrders.size,
                fulfilledOrders = fulfilled,
                totalSpendCents = totalSpend,
                totalPaidCents = sup.totalPaidCents,
                outstandingBalanceCents = sup.outstandingBalanceCents,
                returnRatePercent = returnRate
            )
        }.sortedByDescending { it.totalSpendCents }
    }
}

class PurchaseRepository(private val database: AaCraveDatabase) {

    private val purchaseDao = database.purchaseDao()
    private val supplierDao = database.supplierDao()
    private val inventoryDao = database.inventoryDao()
    private val dashboardDao = database.dashboardDao()
    private val auditLogDao = database.auditLogDao()
    private val syncQueueDao = database.syncQueueDao()

    fun observePurchaseOrders(branchId: String): Flow<List<PurchaseOrderRecord>> =
        combine(
            purchaseDao.observePurchaseOrders(branchId),
            purchaseDao.observeAllPurchaseItems()
        ) { orders, allItems ->
            val byPurchase = allItems.groupBy { it.purchaseId }
            orders.map { po ->
                po.toDomain(byPurchase[po.purchaseId].orEmpty().map { it.toDomain() })
            }
        }

    fun observeReceivings(branchId: String): Flow<List<PurchaseReceivingRecord>> =
        purchaseDao.observeReceivings(branchId).map { list -> list.map { it.toDomain() } }

    fun observeReturns(branchId: String): Flow<List<PurchaseReturnRecord>> =
        purchaseDao.observeReturns(branchId).map { list -> list.map { it.toDomain() } }

    suspend fun createOrUpdatePurchaseOrder(
        session: UserSession,
        purchaseId: String?,
        idempotencyKey: String,
        supplierId: String,
        supplierInvoiceNumber: String,
        draftLines: List<PurchaseDraftLine>,
        discountCents: Long,
        taxCents: Long,
        notes: String,
        submitAsPending: Boolean,
        expectedDateMillis: Long? = null
    ): Result<PurchaseOrderRecord> {
        if (!session.capabilities.canManagePurchases) {
            return Result.failure(SecurityException("Access denied: unauthorized to create or edit purchase orders."))
        }
        if (draftLines.isEmpty()) {
            return Result.failure(IllegalArgumentException("Add at least one item to the purchase order."))
        }
        if (draftLines.any { it.orderedQty <= 0.0 || it.unitPriceCents < 0L }) {
            return Result.failure(IllegalArgumentException("Line item quantities must be positive and prices non-negative."))
        }
        if (discountCents < 0L || taxCents < 0L) {
            return Result.failure(IllegalArgumentException("Discount and tax cannot be negative."))
        }

        return try {
            val saved = database.withTransaction {
                if (purchaseId == null) {
                    val existingKey = purchaseDao.getPurchaseByIdempotencyKey(idempotencyKey)
                    if (existingKey != null) {
                        val existingItems = purchaseDao.getItemsForPurchase(existingKey.purchaseId).map { it.toDomain() }
                        return@withTransaction existingKey.toDomain(existingItems)
                    }
                }
                val supplier = supplierDao.getSupplierById(supplierId)
                    ?: throw IllegalArgumentException("Selected supplier not found.")

                val existingPo = purchaseId?.let { purchaseDao.getPurchaseById(it) }
                if (existingPo != null && existingPo.status != PurchaseStatus.DRAFT.name && existingPo.status != PurchaseStatus.PENDING.name) {
                    throw IllegalStateException("Only Draft or Pending purchase orders can be edited.")
                }

                val now = System.currentTimeMillis()
                val poId = existingPo?.purchaseId ?: "po_${UUID.randomUUID()}"
                val poNumber = existingPo?.poNumber ?: run {
                    val seq = purchaseDao.countPurchasesForBranch(session.branchId) + 1
                    val tag = SimpleDateFormat("yyMM", Locale.US).format(Date(now))
                    "PO-$tag-%04d".format(Locale.US, seq)
                }

                val subtotal = draftLines.sumOf { it.lineTotalCents }
                val effectiveDiscount = discountCents.coerceAtMost(subtotal)
                val totalCents = (subtotal - effectiveDiscount + taxCents).coerceAtLeast(0L)
                val targetStatus = if (submitAsPending) PurchaseStatus.PENDING.name else PurchaseStatus.DRAFT.name

                val poEntity = PurchaseOrderEntity(
                    purchaseId = poId,
                    branchId = session.branchId,
                    idempotencyKey = existingPo?.idempotencyKey ?: idempotencyKey,
                    poNumber = poNumber,
                    supplierInvoiceNumber = supplierInvoiceNumber.trim().ifBlank { "PENDING" },
                    supplierId = supplier.supplierId,
                    supplierName = supplier.name,
                    status = targetStatus,
                    paymentStatus = existingPo?.paymentStatus ?: PurchasePaymentStatus.UNPAID.name,
                    subtotalCents = subtotal,
                    discountCents = effectiveDiscount,
                    taxCents = taxCents,
                    totalCents = totalCents,
                    paidCents = existingPo?.paidCents ?: 0L,
                    returnedCents = existingPo?.returnedCents ?: 0L,
                    notes = notes.trim(),
                    createdByUid = existingPo?.createdByUid ?: session.uid,
                    createdByName = existingPo?.createdByName ?: session.displayName,
                    approvedByName = existingPo?.approvedByName,
                    expectedDateMillis = expectedDateMillis ?: existingPo?.expectedDateMillis,
                    createdAt = existingPo?.createdAt ?: now,
                    updatedAt = now
                )

                val itemEntities = draftLines.mapIndexed { idx, line ->
                    PurchaseItemEntity(
                        lineId = "${poId}_line_$idx",
                        purchaseId = poId,
                        inventoryItemId = line.inventoryItemId,
                        itemName = line.itemName,
                        itemType = line.itemType,
                        unit = line.unit,
                        orderedQty = line.orderedQty,
                        receivedQty = 0.0,
                        returnedQty = 0.0,
                        unitPriceCents = line.unitPriceCents,
                        lineTotalCents = line.lineTotalCents
                    )
                }

                purchaseDao.upsertPurchaseOrder(poEntity)
                purchaseDao.deleteItemsForPurchase(poId)
                purchaseDao.upsertPurchaseItems(itemEntities)

                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_po_${poEntity.purchaseId}_$now",
                        branchId = session.branchId,
                        entityType = "PURCHASE_ORDER",
                        entityId = poEntity.purchaseId,
                        payloadJson = """{"poNumber":"$poNumber","status":"$targetStatus","totalCents":$totalCents}""",
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
                        action = if (existingPo == null) "PO_CREATED" else "PO_UPDATED",
                        details = "$poNumber | Supplier: ${supplier.name} | Total: ${totalCents}c | Status: $targetStatus"
                    )
                )
                poEntity.toDomain(itemEntities.map { it.toDomain() })
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun approvePurchaseOrder(session: UserSession, purchaseId: String): Result<Unit> {
        if (!session.capabilities.canApprovePurchaseOrders) {
            return Result.failure(SecurityException("Owner, Branch Manager, or Super Admin approval required to approve POs."))
        }
        return try {
            database.withTransaction {
                val po = purchaseDao.getPurchaseById(purchaseId) ?: throw IllegalArgumentException("Purchase order not found.")
                if (po.status != PurchaseStatus.DRAFT.name && po.status != PurchaseStatus.PENDING.name) {
                    throw IllegalStateException("Only Draft or Pending purchase orders can be approved.")
                }
                val now = System.currentTimeMillis()
                purchaseDao.upsertPurchaseOrder(
                    po.copy(
                        status = PurchaseStatus.APPROVED.name,
                        approvedByName = session.displayName,
                        updatedAt = now
                    )
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "PO_APPROVED",
                        details = "Approved ${po.poNumber} (${po.totalCents}c) for ${po.supplierName}"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelPurchaseOrder(session: UserSession, purchaseId: String, reason: String): Result<Unit> {
        if (!session.capabilities.canApprovePurchaseOrders) {
            return Result.failure(SecurityException("Owner, Branch Manager, or Super Admin approval required to cancel POs."))
        }
        if (reason.isBlank()) {
            return Result.failure(IllegalArgumentException("Cancellation reason is required."))
        }
        return try {
            database.withTransaction {
                val po = purchaseDao.getPurchaseById(purchaseId) ?: throw IllegalArgumentException("Purchase order not found.")
                if (po.status == PurchaseStatus.CANCELLED.name || po.status == PurchaseStatus.RECEIVED.name) {
                    throw IllegalStateException("Cannot cancel a ${po.status.lowercase()} purchase order.")
                }
                val items = purchaseDao.getItemsForPurchase(purchaseId)
                if (items.any { it.receivedQty > 0.0 }) {
                    throw IllegalStateException("Cannot cancel PO with already received goods. Use Purchase Returns instead.")
                }
                val now = System.currentTimeMillis()
                purchaseDao.upsertPurchaseOrder(
                    po.copy(
                        status = PurchaseStatus.CANCELLED.name,
                        cancelReason = reason.trim(),
                        updatedAt = now
                    )
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "PO_CANCELLED",
                        details = "Cancelled ${po.poNumber}: ${reason.trim()}"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun receiveGoods(
        session: UserSession,
        purchaseId: String,
        receivingKey: String,
        referenceNumber: String,
        receivedDateMillis: Long,
        lineQuantities: Map<String, Double>,
        notes: String,
        supplierInvoiceNumber: String = ""
    ): Result<PurchaseOrderRecord> {
        if (!session.capabilities.canManagePurchases) {
            return Result.failure(SecurityException("Access denied: unauthorized to receive purchase goods."))
        }
        if (referenceNumber.isBlank()) {
            return Result.failure(IllegalArgumentException("Goods receiving reference number is required."))
        }
        if (lineQuantities.values.all { it <= 0.0 }) {
            return Result.failure(IllegalArgumentException("Enter a positive received quantity for at least one item."))
        }

        return try {
            val updatedPo = database.withTransaction {
                val po = purchaseDao.getPurchaseById(purchaseId) ?: throw IllegalArgumentException("Purchase order not found.")
                if (po.status != PurchaseStatus.APPROVED.name && po.status != PurchaseStatus.PARTIALLY_RECEIVED.name) {
                    throw IllegalStateException("Purchase order must be Approved before goods can be received.")
                }

                val currentItems = purchaseDao.getItemsForPurchase(purchaseId)
                val now = System.currentTimeMillis()
                var anyApplied = false
                val updatedItems = currentItems.map { item ->
                    val qtyToAdd = lineQuantities[item.lineId] ?: 0.0
                    if (qtyToAdd <= 0.0) {
                        item
                    } else {
                        val remaining = (item.orderedQty - item.receivedQty).coerceAtLeast(0.0)
                        if (qtyToAdd > remaining + 0.0001) {
                            throw IllegalArgumentException("Receiving qty for ${item.itemName} exceeds ordered remaining ($remaining ${item.unit}).")
                        }

                        val lineRecvKey = "${receivingKey}_${item.lineId}"
                        val recvEntity = PurchaseReceivingEntity(
                            receivingId = "recv_${UUID.randomUUID()}",
                            branchId = session.branchId,
                            purchaseId = purchaseId,
                            lineId = item.lineId,
                            inventoryItemId = item.inventoryItemId,
                            itemName = item.itemName,
                            receivingKey = lineRecvKey,
                            referenceNumber = referenceNumber.trim(),
                            receivedQty = qtyToAdd,
                            unit = item.unit,
                            receivedDateMillis = receivedDateMillis,
                            receivedByName = session.displayName,
                            notes = notes.trim()
                        )
                        val rowId = purchaseDao.insertReceivingIfNew(recvEntity)
                        if (rowId != -1L) {
                            anyApplied = true
                            val invItem = inventoryDao.getItemById(item.inventoryItemId)
                            if (invItem != null) {
                                val newStock = invItem.currentQty + qtyToAdd
                                inventoryDao.upsertItem(
                                    invItem.copy(
                                        currentQty = newStock,
                                        unitCostCents = item.unitPriceCents,
                                        updatedAt = now
                                    )
                                )
                                inventoryDao.insertMovementIfNew(
                                    StockMovementEntity(
                                        movementId = "mov_${UUID.randomUUID()}",
                                        branchId = session.branchId,
                                        inventoryItemId = invItem.itemId,
                                        itemName = invItem.name,
                                        movementType = "RECEIVING",
                                        quantityDelta = qtyToAdd,
                                        unit = invItem.unit,
                                        referenceOrderId = po.poNumber,
                                        deductionKey = "po_recv_$lineRecvKey",
                                        reason = "PO ${po.poNumber} GRN ${referenceNumber.trim()} (${notes.trim().ifBlank { "Goods received" }})",
                                        actorName = session.displayName,
                                        createdAt = now
                                    )
                                )
                            }
                            item.copy(receivedQty = (item.receivedQty + qtyToAdd).coerceAtMost(item.orderedQty))
                        } else {
                            item
                        }
                    }
                }

                if (!anyApplied) {
                    // Duplicate submission detected via receivingKey — safely return current PO state
                    return@withTransaction po.toDomain(currentItems.map { it.toDomain() })
                }

                purchaseDao.upsertPurchaseItems(updatedItems)

                val allFullyReceived = updatedItems.all { it.receivedQty >= it.orderedQty - 0.0001 }
                val nextStatus = if (allFullyReceived) {
                    PurchaseStatus.RECEIVED.name
                } else {
                    PurchaseStatus.PARTIALLY_RECEIVED.name
                }

                // Post invoice payable to supplier once when transitioning out of APPROVED on first goods receipt
                if (po.status == PurchaseStatus.APPROVED.name) {
                    val supplier = supplierDao.getSupplierById(po.supplierId)
                    if (supplier != null) {
                        supplierDao.upsertSupplier(
                            supplier.copy(totalPurchasesCents = supplier.totalPurchasesCents + po.totalCents)
                        )
                    }
                }

                val updatedInvoice = if (supplierInvoiceNumber.isNotBlank()) {
                    supplierInvoiceNumber.trim()
                } else {
                    po.supplierInvoiceNumber
                }
                val newPo = po.copy(
                    status = nextStatus,
                    supplierInvoiceNumber = updatedInvoice,
                    updatedAt = now
                )
                purchaseDao.upsertPurchaseOrder(newPo)

                AccountingIntegrationHelper.postForPurchaseOrderInTransaction(
                    database = database,
                    po = newPo,
                    actorUid = session.uid,
                    actorName = session.displayName
                )

                refreshLowStockAlertsForBranch(session.branchId)

                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_recv_$receivingKey",
                        branchId = session.branchId,
                        entityType = "PURCHASE_RECEIVING",
                        entityId = purchaseId,
                        payloadJson = """{"poNumber":"${po.poNumber}","ref":"${referenceNumber.trim()}","status":"$nextStatus"}""",
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
                        action = "PO_GOODS_RECEIVED",
                        details = "Received ${po.poNumber} (GRN ${referenceNumber.trim()}) -> $nextStatus"
                    )
                )
                newPo.toDomain(updatedItems.map { it.toDomain() })
            }
            Result.success(updatedPo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun returnPurchaseGoods(
        session: UserSession,
        purchaseId: String,
        lineId: String,
        returnQty: Double,
        reason: String
    ): Result<PurchaseReturnRecord> {
        if (!session.capabilities.canManagePurchases) {
            return Result.failure(SecurityException("Access denied: unauthorized to process purchase returns."))
        }
        if (returnQty <= 0.0 || reason.isBlank()) {
            return Result.failure(IllegalArgumentException("Positive return quantity and reason are required."))
        }
        return try {
            val saved = database.withTransaction {
                val po = purchaseDao.getPurchaseById(purchaseId) ?: throw IllegalArgumentException("Purchase order not found.")
                val line = purchaseDao.getPurchaseItemById(lineId) ?: throw IllegalArgumentException("PO line item not found.")
                val returnable = (line.receivedQty - line.returnedQty).coerceAtLeast(0.0)
                if (returnQty > returnable + 0.0001) {
                    throw IllegalArgumentException("Return qty exceeds received returnable qty ($returnable ${line.unit}).")
                }

                val now = System.currentTimeMillis()
                val returnAmountCents = kotlin.math.round(returnQty * line.unitPriceCents).toLong().coerceAtLeast(0L)
                val updatedLine = line.copy(returnedQty = line.returnedQty + returnQty)
                purchaseDao.upsertPurchaseItems(listOf(updatedLine))

                val invItem = inventoryDao.getItemById(line.inventoryItemId)
                if (invItem != null) {
                    val newStock = (invItem.currentQty - returnQty).coerceAtLeast(0.0)
                    inventoryDao.updateStockQuantity(invItem.itemId, newStock, null, now)
                    inventoryDao.insertMovementIfNew(
                        StockMovementEntity(
                            movementId = "mov_${UUID.randomUUID()}",
                            branchId = session.branchId,
                            inventoryItemId = invItem.itemId,
                            itemName = invItem.name,
                            movementType = "ADJUSTMENT",
                            quantityDelta = -returnQty,
                            unit = invItem.unit,
                            referenceOrderId = po.poNumber,
                            deductionKey = "po_ret_${UUID.randomUUID()}",
                            reason = "Purchase Return (${po.poNumber}): ${reason.trim()}",
                            actorName = session.displayName,
                            createdAt = now
                        )
                    )
                }

                val newReturnedCents = po.returnedCents + returnAmountCents
                val netPayable = (po.totalCents - newReturnedCents).coerceAtLeast(0L)
                val nextPayStatus = when {
                    netPayable == 0L || po.paidCents >= netPayable -> PurchasePaymentStatus.PAID.name
                    po.paidCents > 0L -> PurchasePaymentStatus.PARTIALLY_PAID.name
                    else -> PurchasePaymentStatus.UNPAID.name
                }
                purchaseDao.upsertPurchaseOrder(
                    po.copy(
                        returnedCents = newReturnedCents,
                        paymentStatus = nextPayStatus,
                        updatedAt = now
                    )
                )

                val supplier = supplierDao.getSupplierById(po.supplierId)
                if (supplier != null) {
                    supplierDao.upsertSupplier(
                        supplier.copy(totalReturnsCents = supplier.totalReturnsCents + returnAmountCents)
                    )
                }

                val retEntity = PurchaseReturnEntity(
                    returnId = "pret_${UUID.randomUUID()}",
                    branchId = session.branchId,
                    purchaseId = po.purchaseId,
                    poNumber = po.poNumber,
                    supplierId = po.supplierId,
                    lineId = line.lineId,
                    inventoryItemId = line.inventoryItemId,
                    itemName = line.itemName,
                    returnQty = returnQty,
                    unit = line.unit,
                    returnAmountCents = returnAmountCents,
                    reason = reason.trim(),
                    actorName = session.displayName,
                    returnedAt = now
                )
                purchaseDao.insertReturn(retEntity)
                AccountingIntegrationHelper.postForPurchaseReturnInTransaction(
                    database = database,
                    ret = retEntity,
                    supplierName = supplier?.name ?: po.supplierName,
                    actorUid = session.uid,
                    actorName = session.displayName
                )
                refreshLowStockAlertsForBranch(session.branchId)

                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "PO_RETURN",
                        details = "Returned $returnQty ${line.unit} ${line.itemName} on ${po.poNumber} (${returnAmountCents}c)"
                    )
                )
                retEntity.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun refreshLowStockAlertsForBranch(branchId: String) {
        val lowItems = inventoryDao.getLowStockItems(branchId)
        inventoryDao.clearLowStockAlertsForBranch(branchId)
        inventoryDao.upsertLowStockAlerts(
            lowItems.map {
                LowStockAlertEntity(
                    id = "ls_${it.itemId}",
                    branchId = branchId,
                    ingredientName = it.name,
                    currentStock = it.currentQty,
                    reorderLevel = it.reorderLevel,
                    unit = it.unit,
                    isSampleData = false
                )
            }
        )
        val snap = dashboardDao.observeSnapshot(branchId).first()
        if (snap != null) {
            dashboardDao.upsertSnapshots(
                listOf(snap.copy(lowStockAlerts = lowItems.size, updatedAt = System.currentTimeMillis()))
            )
        }
    }
}

class CustomerLoyaltyRepository(private val database: AaCraveDatabase) {

    private val dao = database.customerLoyaltyDao()
    private val auditLogDao = database.auditLogDao()
    private val syncQueueDao = database.syncQueueDao()

    fun observeCustomers(branchId: String): Flow<List<CustomerProfileRecord>> =
        dao.observeCustomers(branchId).map { list -> list.map { it.toProfileDomain() } }

    fun observeLoyaltyConfig(branchId: String): Flow<LoyaltyConfigRecord> =
        dao.observeLoyaltyConfig(branchId).map { entity ->
            entity?.toDomain() ?: LoyaltyConfigRecord(
                branchId = branchId,
                earnRatePerDollar = 1.0,
                redeemCentsPerPoint = 5L,
                minRedeemPoints = 50,
                silverThresholdPoints = 150,
                goldThresholdPoints = 400,
                platinumThresholdPoints = 800,
                expiryEnabled = false,
                expiryDays = 365,
                updatedBy = "Default",
                updatedAt = System.currentTimeMillis()
            )
        }

    fun observeRewards(branchId: String): Flow<List<LoyaltyRewardRecord>> =
        dao.observeLoyaltyRewards(branchId).map { list -> list.map { it.toDomain() } }

    fun observeLoyaltyTransactions(branchId: String): Flow<List<LoyaltyTransactionRecord>> =
        dao.observeLoyaltyTransactions(branchId).map { list -> list.map { it.toDomain() } }

    fun observeFeedback(branchId: String): Flow<List<CustomerFeedbackRecord>> =
        dao.observeFeedback(branchId).map { list -> list.map { it.toDomain() } }

    suspend fun saveCustomer(
        session: UserSession,
        customerId: String?,
        name: String,
        phone: String,
        email: String,
        address: String,
        notes: String,
        accountBalanceCents: Long?
    ): Result<CustomerProfileRecord> {
        if (!session.capabilities.canManageCustomers) {
            return Result.failure(SecurityException("Access denied: unauthorized to manage customers."))
        }
        if (name.isBlank() || phone.isBlank()) {
            return Result.failure(IllegalArgumentException("Customer name and phone number are required."))
        }
        return try {
            val saved = database.withTransaction {
                val normPhone = phone.filter { it.isDigit() }
                val normEmail = email.trim().lowercase(Locale.US)
                val branchCustomers = dao.getCustomersForBranch(session.branchId)
                val duplicate = branchCustomers.firstOrNull { c ->
                    c.customerId != customerId && (
                        (normPhone.isNotBlank() && c.phone.filter { it.isDigit() } == normPhone) ||
                            (normEmail.isNotBlank() && c.email.trim().lowercase(Locale.US) == normEmail)
                        )
                }
                if (duplicate != null) {
                    throw IllegalArgumentException("Duplicate customer found: ${duplicate.name} (${duplicate.phone}).")
                }

                val now = System.currentTimeMillis()
                val existing = customerId?.let { dao.getCustomerById(it) }
                val resolvedBalance = if (session.capabilities.canViewCustomerBalance && accountBalanceCents != null) {
                    accountBalanceCents
                } else {
                    existing?.accountBalanceCents ?: 0L
                }

                val entity = if (existing != null) {
                    existing.copy(
                        name = name.trim(),
                        phone = phone.trim(),
                        email = email.trim(),
                        address = address.trim(),
                        notes = notes.trim(),
                        accountBalanceCents = resolvedBalance
                    )
                } else {
                    CustomerEntity(
                        customerId = "cust_${UUID.randomUUID()}",
                        branchId = session.branchId,
                        name = name.trim(),
                        phone = phone.trim(),
                        email = email.trim(),
                        loyaltyPoints = 0,
                        tier = "Member",
                        discountPercent = 0,
                        address = address.trim(),
                        notes = notes.trim(),
                        totalPurchasesCents = 0L,
                        lastOrderAt = null,
                        accountBalanceCents = resolvedBalance,
                        feedbackSummary = "",
                        createdAt = now
                    )
                }
                dao.upsertCustomer(entity)
                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_cust_${entity.customerId}_$now",
                        branchId = session.branchId,
                        entityType = "CUSTOMER",
                        entityId = entity.customerId,
                        payloadJson = """{"name":"${entity.name}","phone":"${entity.phone}"}""",
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
                        action = if (existing == null) "CUSTOMER_CREATED" else "CUSTOMER_UPDATED",
                        details = "Customer ${entity.name} (${entity.phone})"
                    )
                )
                entity.toProfileDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateLoyaltyConfig(
        session: UserSession,
        earnRatePerDollar: Double,
        redeemCentsPerPoint: Long,
        minRedeemPoints: Int,
        silverThreshold: Int,
        goldThreshold: Int,
        platinumThreshold: Int,
        expiryEnabled: Boolean,
        expiryDays: Int
    ): Result<LoyaltyConfigRecord> {
        if (!session.capabilities.canConfigureLoyaltyAndAdjust) {
            return Result.failure(SecurityException("Owner, Branch Manager, or Super Admin access required to configure loyalty rules."))
        }
        if (earnRatePerDollar < 0.0 || redeemCentsPerPoint <= 0L || minRedeemPoints < 1 || expiryDays < 1) {
            return Result.failure(IllegalArgumentException("Loyalty rules must have valid positive rates and thresholds."))
        }
        return try {
            val saved = database.withTransaction {
                val now = System.currentTimeMillis()
                val entity = LoyaltyConfigEntity(
                    branchId = session.branchId,
                    earnRatePerDollar = earnRatePerDollar,
                    redeemCentsPerPoint = redeemCentsPerPoint,
                    minRedeemPoints = minRedeemPoints,
                    silverThresholdPoints = silverThreshold,
                    goldThresholdPoints = goldThreshold.coerceAtLeast(silverThreshold + 1),
                    platinumThresholdPoints = platinumThreshold.coerceAtLeast(goldThreshold + 1),
                    expiryEnabled = expiryEnabled,
                    expiryDays = expiryDays,
                    updatedBy = session.displayName,
                    updatedAt = now
                )
                dao.upsertLoyaltyConfig(entity)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "LOYALTY_CONFIG_UPDATED",
                        details = "Earn: $earnRatePerDollar pts/PKR, Redeem: $redeemCentsPerPoint paisa/pt, Expiry: $expiryEnabled (${expiryDays}d)"
                    )
                )
                entity.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveReward(
        session: UserSession,
        rewardId: String?,
        title: String,
        description: String,
        pointsRequired: Int,
        discountCentsValue: Long,
        isActive: Boolean
    ): Result<Unit> {
        if (!session.capabilities.canConfigureLoyaltyAndAdjust) {
            return Result.failure(SecurityException("Manager or Admin privileges required to configure loyalty rewards."))
        }
        if (title.isBlank() || pointsRequired <= 0 || discountCentsValue <= 0L) {
            return Result.failure(IllegalArgumentException("Reward title, points, and discount value must be valid."))
        }
        return try {
            database.withTransaction {
                val entity = LoyaltyRewardEntity(
                    rewardId = rewardId ?: "rew_${UUID.randomUUID()}",
                    branchId = session.branchId,
                    title = title.trim(),
                    description = description.trim(),
                    pointsRequired = pointsRequired,
                    discountCentsValue = discountCentsValue,
                    isActive = isActive
                )
                dao.upsertReward(entity)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "LOYALTY_REWARD_SAVED",
                        details = "${entity.title} (${entity.pointsRequired} pts)"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adjustPointsWithApproval(
        session: UserSession,
        customerId: String,
        pointsDelta: Int,
        reason: String,
        idempotencyKey: String
    ): Result<CustomerProfileRecord> {
        if (!session.capabilities.canConfigureLoyaltyAndAdjust) {
            return Result.failure(SecurityException("Manager, Owner, or Super Admin approval is required for manual loyalty point adjustments."))
        }
        if (pointsDelta == 0 || reason.isBlank()) {
            return Result.failure(IllegalArgumentException("Non-zero point delta and adjustment reason are required."))
        }
        return try {
            val updated = database.withTransaction {
                val customer = dao.getCustomerById(customerId) ?: throw IllegalArgumentException("Customer not found.")
                val newPoints = customer.loyaltyPoints + pointsDelta
                if (newPoints < 0) {
                    throw IllegalArgumentException("Customer points balance cannot drop below 0.")
                }
                val now = System.currentTimeMillis()
                val tx = LoyaltyTransactionEntity(
                    txId = "ltx_${UUID.randomUUID()}",
                    branchId = session.branchId,
                    customerId = customer.customerId,
                    customerName = customer.name,
                    idempotencyKey = idempotencyKey,
                    txType = "ADJUSTMENT",
                    pointsDelta = pointsDelta,
                    orderId = null,
                    reason = reason.trim(),
                    approvedBy = session.displayName,
                    actorName = session.displayName,
                    createdAt = now
                )
                val inserted = dao.insertLoyaltyTransactionIfNew(tx)
                if (inserted == -1L) {
                    return@withTransaction customer.toProfileDomain()
                }
                val config = dao.getLoyaltyConfig(session.branchId)
                val (newTier, newDiscount) = computeTierAndDiscount(newPoints, config)
                val savedCust = customer.copy(
                    loyaltyPoints = newPoints,
                    tier = newTier,
                    discountPercent = newDiscount
                )
                dao.upsertCustomer(savedCust)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "LOYALTY_MANUAL_ADJUSTMENT",
                        details = "Adjusted ${customer.name} by $pointsDelta pts (${reason.trim()})"
                    )
                )
                savedCust.toProfileDomain()
            }
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun redeemReward(
        session: UserSession,
        customerId: String,
        reward: LoyaltyRewardRecord,
        idempotencyKey: String
    ): Result<CustomerProfileRecord> {
        if (!session.capabilities.canManageCustomers) {
            return Result.failure(SecurityException("Access denied: unauthorized to redeem customer rewards."))
        }
        return try {
            val updated = database.withTransaction {
                val customer = dao.getCustomerById(customerId) ?: throw IllegalArgumentException("Customer not found.")
                if (customer.loyaltyPoints < reward.pointsRequired) {
                    throw IllegalArgumentException("Insufficient points (${customer.loyaltyPoints} pts available, ${reward.pointsRequired} pts needed).")
                }
                val now = System.currentTimeMillis()
                val tx = LoyaltyTransactionEntity(
                    txId = "ltx_${UUID.randomUUID()}",
                    branchId = session.branchId,
                    customerId = customer.customerId,
                    customerName = customer.name,
                    idempotencyKey = idempotencyKey,
                    txType = "REDEEM",
                    pointsDelta = -reward.pointsRequired,
                    orderId = null,
                    reason = "Redeemed reward: ${reward.title}",
                    approvedBy = session.displayName,
                    actorName = session.displayName,
                    createdAt = now
                )
                val inserted = dao.insertLoyaltyTransactionIfNew(tx)
                if (inserted == -1L) return@withTransaction customer.toProfileDomain()

                val newPoints = (customer.loyaltyPoints - reward.pointsRequired).coerceAtLeast(0)
                val config = dao.getLoyaltyConfig(session.branchId)
                val (newTier, newDiscount) = computeTierAndDiscount(newPoints, config)
                val savedCust = customer.copy(
                    loyaltyPoints = newPoints,
                    tier = newTier,
                    discountPercent = newDiscount
                )
                dao.upsertCustomer(savedCust)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "LOYALTY_REWARD_REDEEMED",
                        details = "${customer.name} redeemed '${reward.title}' (-${reward.pointsRequired} pts)"
                    )
                )
                savedCust.toProfileDomain()
            }
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordFeedback(
        session: UserSession,
        customerId: String,
        orderId: String?,
        rating: Int,
        comment: String
    ): Result<Unit> {
        if (!session.capabilities.canManageCustomers) {
            return Result.failure(SecurityException("Access denied: unauthorized to record feedback."))
        }
        if (comment.isBlank() || rating !in 1..5) {
            return Result.failure(IllegalArgumentException("Rating (1-5) and feedback comment are required."))
        }
        return try {
            database.withTransaction {
                val customer = dao.getCustomerById(customerId) ?: throw IllegalArgumentException("Customer not found.")
                val now = System.currentTimeMillis()
                dao.insertFeedback(
                    CustomerFeedbackEntity(
                        feedbackId = "fb_${UUID.randomUUID()}",
                        branchId = session.branchId,
                        customerId = customer.customerId,
                        orderId = orderId,
                        rating = rating,
                        comment = comment.trim(),
                        recordedBy = session.displayName,
                        createdAt = now
                    )
                )
                dao.upsertCustomer(
                    customer.copy(feedbackSummary = "$rating.0 ★ • ${comment.trim().take(48)}")
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "CUSTOMER_FEEDBACK_LOGGED",
                        details = "${customer.name}: $rating★ - ${comment.trim()}"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun expireOldPointsIfEnabled(session: UserSession): Result<Int> {
        if (!session.capabilities.canConfigureLoyaltyAndAdjust) {
            return Result.failure(SecurityException("Manager approval required to run loyalty point expiry."))
        }
        return try {
            val expiredCustomersCount = database.withTransaction {
                val config = dao.getLoyaltyConfig(session.branchId) ?: return@withTransaction 0
                if (!config.expiryEnabled) return@withTransaction 0
                val now = System.currentTimeMillis()
                val cutoff = now - config.expiryDays * 86_400_000L
                val dayBucket = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
                val customers = dao.getCustomersForBranch(session.branchId)
                var count = 0
                customers.forEach { cust ->
                    val lastActivity = cust.lastOrderAt ?: cust.createdAt
                    if (cust.loyaltyPoints > 0 && lastActivity in 1 until cutoff) {
                        val key = "exp_pts_${cust.customerId}_$dayBucket"
                        val row = dao.insertLoyaltyTransactionIfNew(
                            LoyaltyTransactionEntity(
                                txId = "ltx_${UUID.randomUUID()}",
                                branchId = session.branchId,
                                customerId = cust.customerId,
                                customerName = cust.name,
                                idempotencyKey = key,
                                txType = "EXPIRED",
                                pointsDelta = -cust.loyaltyPoints,
                                orderId = null,
                                reason = "Expired after ${config.expiryDays} days of inactivity",
                                approvedBy = session.displayName,
                                actorName = session.displayName,
                                createdAt = now
                            )
                        )
                        if (row != -1L) {
                            dao.upsertCustomer(cust.copy(loyaltyPoints = 0, tier = "Member", discountPercent = 0))
                            count++
                        }
                    }
                }
                count
            }
            Result.success(expiredCustomersCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun computeTierAndDiscount(points: Int, config: LoyaltyConfigEntity?): Pair<String, Int> {
            val silver = config?.silverThresholdPoints ?: 150
            val gold = config?.goldThresholdPoints ?: 400
            val platinum = config?.platinumThresholdPoints ?: 800
            return when {
                points >= platinum -> "Platinum" to 12
                points >= gold -> "Gold VIP" to 10
                points >= silver -> "Silver" to 5
                else -> "Member" to 0
            }
        }
    }
}

class ExpenseRepository(
    private val context: Context,
    private val database: AaCraveDatabase
) {
    private val expenseDao = database.expenseDao()
    private val dashboardDao = database.dashboardDao()
    private val auditLogDao = database.auditLogDao()
    private val syncQueueDao = database.syncQueueDao()

    fun observeExpenses(branchId: String): Flow<List<ExpenseRecord>> =
        expenseDao.observeExpenses(branchId).map { list -> list.map { it.toDomain() } }

    suspend fun copyReceiptToInternalStorage(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "expense_receipts").apply { mkdirs() }
            val ext = context.contentResolver.getType(uri)?.substringAfterLast('/')?.takeIf { it.length <= 5 } ?: "dat"
            val outFile = File(dir, "rcpt_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(IllegalArgumentException("Unable to read selected receipt file."))
            Result.success(outFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveExpense(
        session: UserSession,
        idempotencyKey: String,
        expenseId: String?,
        category: String,
        amountCents: Long,
        expenseDateMillis: Long,
        description: String,
        paymentMethod: String,
        receiptUri: String?,
        isRecurring: Boolean,
        recurrenceFrequency: ExpenseRecurrence
    ): Result<ExpenseRecord> {
        if (!session.capabilities.canManageExpenses) {
            return Result.failure(SecurityException("Access denied: unauthorized to record operating expenses."))
        }
        if (category.isBlank() || description.isBlank() || amountCents <= 0L) {
            return Result.failure(IllegalArgumentException("Expense category, description, and positive amount are required."))
        }

        return try {
            val saved = database.withTransaction {
                if (expenseId == null) {
                    val existingKey = expenseDao.getExpenseByIdempotencyKey(idempotencyKey)
                    if (existingKey != null) return@withTransaction existingKey.toDomain()
                }
                val existing = expenseId?.let { expenseDao.getExpenseById(it) }
                val now = System.currentTimeMillis()
                val autoApprove = session.capabilities.canApproveExpenses
                val entity = ExpenseEntity(
                    expenseId = existing?.expenseId ?: "exp_${UUID.randomUUID()}",
                    branchId = session.branchId,
                    idempotencyKey = existing?.idempotencyKey ?: idempotencyKey,
                    category = category.trim(),
                    amountCents = amountCents,
                    expenseDateMillis = expenseDateMillis,
                    description = description.trim(),
                    paymentMethod = paymentMethod.trim().ifBlank { "Cash" },
                    receiptUri = receiptUri ?: existing?.receiptUri,
                    approvalStatus = existing?.approvalStatus
                        ?: if (autoApprove) ExpenseApprovalStatus.APPROVED.name else ExpenseApprovalStatus.PENDING.name,
                    approvedByName = existing?.approvedByName ?: if (autoApprove) session.displayName else null,
                    isRecurring = isRecurring,
                    recurrenceFrequency = if (isRecurring && recurrenceFrequency == ExpenseRecurrence.NONE) {
                        ExpenseRecurrence.MONTHLY.name
                    } else if (!isRecurring) {
                        ExpenseRecurrence.NONE.name
                    } else {
                        recurrenceFrequency.name
                    },
                    createdByUid = existing?.createdByUid ?: session.uid,
                    createdByName = existing?.createdByName ?: session.displayName,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
                expenseDao.upsertExpense(entity)
                syncDailyExpensesSnapshot(session.branchId)
                if (entity.approvalStatus == ExpenseApprovalStatus.APPROVED.name) {
                    AccountingIntegrationHelper.postForApprovedExpenseInTransaction(
                        database = database,
                        expense = entity,
                        actorUid = session.uid,
                        actorName = session.displayName
                    )
                }

                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_exp_${entity.expenseId}_$now",
                        branchId = session.branchId,
                        entityType = "EXPENSE",
                        entityId = entity.expenseId,
                        payloadJson = """{"category":"${entity.category}","amountCents":$amountCents,"status":"${entity.approvalStatus}"}""",
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
                        action = if (existing == null) "EXPENSE_CREATED" else "EXPENSE_UPDATED",
                        details = "${entity.category}: ${amountCents}c (${entity.approvalStatus}) - ${entity.description}"
                    )
                )
                entity.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setExpenseApproval(session: UserSession, expenseId: String, approved: Boolean): Result<Unit> {
        if (!session.capabilities.canApproveExpenses) {
            return Result.failure(SecurityException("Owner, Branch Manager, or Super Admin approval required for expenses."))
        }
        return try {
            database.withTransaction {
                val exp = expenseDao.getExpenseById(expenseId) ?: throw IllegalArgumentException("Expense not found.")
                val status = if (approved) ExpenseApprovalStatus.APPROVED.name else ExpenseApprovalStatus.REJECTED.name
                val updatedExp = exp.copy(
                    approvalStatus = status,
                    approvedByName = session.displayName,
                    updatedAt = System.currentTimeMillis()
                )
                expenseDao.upsertExpense(updatedExp)
                syncDailyExpensesSnapshot(session.branchId)
                if (approved) {
                    AccountingIntegrationHelper.postForApprovedExpenseInTransaction(
                        database = database,
                        expense = updatedExp,
                        actorUid = session.uid,
                        actorName = session.displayName
                    )
                }
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = if (approved) "EXPENSE_APPROVED" else "EXPENSE_REJECTED",
                        details = "${exp.category} (${exp.amountCents}c) marked $status"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteExpense(session: UserSession, expenseId: String): Result<Unit> {
        if (!session.capabilities.canApproveExpenses) {
            return Result.failure(SecurityException("Owner, Branch Manager, or Super Admin approval required to delete expenses."))
        }
        return try {
            database.withTransaction {
                val exp = expenseDao.getExpenseById(expenseId) ?: throw IllegalArgumentException("Expense not found.")
                expenseDao.deleteExpense(expenseId)
                AccountingIntegrationHelper.postReversalForExpenseInTransaction(
                    database = database,
                    expense = exp,
                    actorUid = session.uid,
                    actorName = session.displayName,
                    reason = "Approved expense deleted by ${session.displayName}"
                )
                syncDailyExpensesSnapshot(session.branchId)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "EXPENSE_DELETED",
                        details = "Deleted expense ${exp.category} (${exp.amountCents}c): ${exp.description}"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildPeriodReports(expenses: List<ExpenseRecord>, nowMillis: Long = System.currentTimeMillis()): List<ExpensePeriodReport> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        val weekStart = nowMillis - 7 * 86_400_000L
        val monthStart = nowMillis - 30 * 86_400_000L

        fun buildBucket(label: String, items: List<ExpenseRecord>): ExpensePeriodReport {
            val approved = items.filter { it.approvalStatus == ExpenseApprovalStatus.APPROVED }
            val pending = items.filter { it.approvalStatus == ExpenseApprovalStatus.PENDING }
            return ExpensePeriodReport(
                periodLabel = label,
                totalApprovedCents = approved.sumOf { it.amountCents },
                totalPendingCents = pending.sumOf { it.amountCents },
                byCategory = approved.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amountCents } },
                count = items.size
            )
        }

        return listOf(
            buildBucket("Daily (Today)", expenses.filter { it.expenseDateMillis >= todayStart }),
            buildBucket("Weekly (Last 7 Days)", expenses.filter { it.expenseDateMillis >= weekStart }),
            buildBucket("Monthly (Last 30 Days)", expenses.filter { it.expenseDateMillis >= monthStart })
        )
    }

    private suspend fun syncDailyExpensesSnapshot(branchId: String) {
        val snap = dashboardDao.observeSnapshot(branchId).first() ?: return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        val todayExpensesCents = expenseDao.observeExpenses(branchId).first()
            .filter { it.approvalStatus == ExpenseApprovalStatus.APPROVED.name && it.expenseDateMillis >= todayStart }
            .sumOf { it.amountCents }
        val dailyExpDollars = todayExpensesCents / 100.0
        dashboardDao.upsertSnapshots(
            listOf(
                snap.copy(
                    dailyExpenses = dailyExpDollars,
                    estimatedProfit = (snap.todaySales - dailyExpDollars),
                    updatedAt = System.currentTimeMillis()
                )
            )
        )
    }
}

// Entity <-> Domain mappers
private fun SupplierEntity.toDomain() = SupplierSummary(
    supplierId = supplierId,
    branchId = branchId,
    name = name,
    contactPerson = contactPerson,
    phone = phone,
    email = email,
    address = address,
    category = category,
    openingBalanceCents = openingBalanceCents,
    totalPurchasesCents = totalPurchasesCents,
    totalReturnsCents = totalReturnsCents,
    totalPaidCents = totalPaidCents,
    isActive = isActive,
    createdAt = createdAt
)

private fun SupplierPaymentEntity.toDomain() = SupplierPaymentRecord(
    paymentId = paymentId,
    branchId = branchId,
    supplierId = supplierId,
    supplierName = supplierName,
    purchaseId = purchaseId,
    poNumber = poNumber,
    amountCents = amountCents,
    paymentMethod = paymentMethod,
    referenceNumber = referenceNumber,
    paymentDateMillis = paymentDateMillis,
    notes = notes,
    paidByName = paidByName,
    createdAt = createdAt
)

private fun PurchaseOrderEntity.toDomain(items: List<PurchaseItemRecord>) = PurchaseOrderRecord(
    purchaseId = purchaseId,
    branchId = branchId,
    poNumber = poNumber,
    supplierInvoiceNumber = supplierInvoiceNumber,
    supplierId = supplierId,
    supplierName = supplierName,
    status = PurchaseStatus.entries.firstOrNull { it.name == status } ?: PurchaseStatus.DRAFT,
    paymentStatus = PurchasePaymentStatus.entries.firstOrNull { it.name == paymentStatus } ?: PurchasePaymentStatus.UNPAID,
    subtotalCents = subtotalCents,
    discountCents = discountCents,
    taxCents = taxCents,
    totalCents = totalCents,
    paidCents = paidCents,
    returnedCents = returnedCents,
    notes = notes,
    createdByName = createdByName,
    approvedByName = approvedByName,
    cancelReason = cancelReason,
    expectedDateMillis = expectedDateMillis,
    createdAt = createdAt,
    updatedAt = updatedAt,
    items = items
)

private fun PurchaseItemEntity.toDomain() = PurchaseItemRecord(
    lineId = lineId,
    purchaseId = purchaseId,
    inventoryItemId = inventoryItemId,
    itemName = itemName,
    itemType = itemType,
    unit = unit,
    orderedQty = orderedQty,
    receivedQty = receivedQty,
    returnedQty = returnedQty,
    unitPriceCents = unitPriceCents,
    lineTotalCents = lineTotalCents
)

private fun PurchaseReceivingEntity.toDomain() = PurchaseReceivingRecord(
    receivingId = receivingId,
    branchId = branchId,
    purchaseId = purchaseId,
    lineId = lineId,
    inventoryItemId = inventoryItemId,
    itemName = itemName,
    referenceNumber = referenceNumber,
    receivedQty = receivedQty,
    unit = unit,
    receivedDateMillis = receivedDateMillis,
    receivedByName = receivedByName,
    notes = notes
)

private fun PurchaseReturnEntity.toDomain() = PurchaseReturnRecord(
    returnId = returnId,
    branchId = branchId,
    purchaseId = purchaseId,
    poNumber = poNumber,
    supplierId = supplierId,
    lineId = lineId,
    inventoryItemId = inventoryItemId,
    itemName = itemName,
    returnQty = returnQty,
    unit = unit,
    returnAmountCents = returnAmountCents,
    reason = reason,
    actorName = actorName,
    returnedAt = returnedAt
)

private fun CustomerEntity.toProfileDomain() = CustomerProfileRecord(
    customerId = customerId,
    branchId = branchId,
    name = name,
    phone = phone,
    email = email,
    address = address,
    notes = notes,
    loyaltyPoints = loyaltyPoints,
    tier = tier,
    discountPercent = discountPercent,
    totalPurchasesCents = totalPurchasesCents,
    lastOrderAt = lastOrderAt,
    accountBalanceCents = accountBalanceCents,
    feedbackSummary = feedbackSummary,
    createdAt = createdAt
)

private fun LoyaltyConfigEntity.toDomain() = LoyaltyConfigRecord(
    branchId = branchId,
    earnRatePerDollar = earnRatePerDollar,
    redeemCentsPerPoint = redeemCentsPerPoint,
    minRedeemPoints = minRedeemPoints,
    silverThresholdPoints = silverThresholdPoints,
    goldThresholdPoints = goldThresholdPoints,
    platinumThresholdPoints = platinumThresholdPoints,
    expiryEnabled = expiryEnabled,
    expiryDays = expiryDays,
    updatedBy = updatedBy,
    updatedAt = updatedAt
)

private fun LoyaltyRewardEntity.toDomain() = LoyaltyRewardRecord(
    rewardId = rewardId,
    branchId = branchId,
    title = title,
    description = description,
    pointsRequired = pointsRequired,
    discountCentsValue = discountCentsValue,
    isActive = isActive
)

private fun LoyaltyTransactionEntity.toDomain() = LoyaltyTransactionRecord(
    txId = txId,
    branchId = branchId,
    customerId = customerId,
    customerName = customerName,
    txType = txType,
    pointsDelta = pointsDelta,
    orderId = orderId,
    reason = reason,
    approvedBy = approvedBy,
    actorName = actorName,
    createdAt = createdAt
)

private fun CustomerFeedbackEntity.toDomain() = CustomerFeedbackRecord(
    feedbackId = feedbackId,
    branchId = branchId,
    customerId = customerId,
    orderId = orderId,
    rating = rating,
    comment = comment,
    recordedBy = recordedBy,
    createdAt = createdAt
)

private fun ExpenseEntity.toDomain() = ExpenseRecord(
    expenseId = expenseId,
    branchId = branchId,
    category = category,
    amountCents = amountCents,
    expenseDateMillis = expenseDateMillis,
    description = description,
    paymentMethod = paymentMethod,
    receiptUri = receiptUri,
    approvalStatus = ExpenseApprovalStatus.entries.firstOrNull { it.name == approvalStatus } ?: ExpenseApprovalStatus.PENDING,
    approvedByName = approvedByName,
    isRecurring = isRecurring,
    recurrenceFrequency = ExpenseRecurrence.entries.firstOrNull { it.name == recurrenceFrequency } ?: ExpenseRecurrence.NONE,
    createdByName = createdByName,
    createdAt = createdAt,
    updatedAt = updatedAt
)
