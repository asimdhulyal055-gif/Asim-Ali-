package com.example.domain.model

enum class SupplierCategory(val label: String) {
    MEAT_POULTRY("Meat & Poultry"),
    DAIRY_PRODUCE("Dairy & Produce"),
    BAKERY_DRY("Bakery & Dry Goods"),
    BEVERAGES("Beverages"),
    PACKAGING_SUPPLIES("Packaging & Supplies"),
    EQUIPMENT_MAINT("Equipment & Kitchen")
}

enum class PurchaseStatus(val label: String) {
    DRAFT("Draft"),
    PENDING("Pending"),
    APPROVED("Approved"),
    PARTIALLY_RECEIVED("Partially Received"),
    RECEIVED("Received"),
    CANCELLED("Cancelled")
}

enum class PurchasePaymentStatus(val label: String) {
    UNPAID("Unpaid"),
    PARTIALLY_PAID("Partially Paid"),
    PAID("Paid")
}

enum class ExpenseCategory(val label: String) {
    RENT("Rent"),
    ELECTRICITY("Electricity"),
    GAS("Gas"),
    WATER("Water"),
    SALARIES("Salaries"),
    MAINTENANCE("Maintenance"),
    MARKETING("Marketing"),
    TRANSPORT("Transport"),
    PACKAGING("Packaging"),
    INTERNET("Internet"),
    MISCELLANEOUS("Miscellaneous")
}

enum class ExpenseApprovalStatus(val label: String) {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected")
}

enum class ExpenseRecurrence(val label: String) {
    NONE("One-Time"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly")
}

data class SupplierSummary(
    val supplierId: String,
    val branchId: String,
    val name: String,
    val contactPerson: String,
    val phone: String,
    val email: String,
    val address: String,
    val category: String,
    val openingBalanceCents: Long,
    val totalPurchasesCents: Long,
    val totalReturnsCents: Long,
    val totalPaidCents: Long,
    val isActive: Boolean,
    val createdAt: Long
) {
    val outstandingBalanceCents: Long
        get() = (openingBalanceCents + totalPurchasesCents - totalReturnsCents - totalPaidCents).coerceAtLeast(0L)
}

data class SupplierStatementLine(
    val dateMillis: Long,
    val reference: String,
    val entryType: String, // OPENING, PURCHASE, RETURN, PAYMENT
    val description: String,
    val debitCents: Long,  // Increases payable
    val creditCents: Long, // Decreases payable
    val runningBalanceCents: Long
)

data class SupplierStatementReport(
    val supplier: SupplierSummary,
    val lines: List<SupplierStatementLine>,
    val closingBalanceCents: Long
)

data class SupplierPerformanceMetric(
    val supplierId: String,
    val supplierName: String,
    val category: String,
    val totalOrders: Int,
    val fulfilledOrders: Int,
    val totalSpendCents: Long,
    val totalPaidCents: Long,
    val outstandingBalanceCents: Long,
    val returnRatePercent: Double
)

data class PurchaseDraftLine(
    val inventoryItemId: String,
    val itemName: String,
    val itemType: String,
    val unit: String,
    val orderedQty: Double,
    val unitPriceCents: Long
) {
    val lineTotalCents: Long
        get() = kotlin.math.round(orderedQty * unitPriceCents).toLong().coerceAtLeast(0L)
}

data class PurchaseItemRecord(
    val lineId: String,
    val purchaseId: String,
    val inventoryItemId: String,
    val itemName: String,
    val itemType: String,
    val unit: String,
    val orderedQty: Double,
    val receivedQty: Double,
    val returnedQty: Double,
    val unitPriceCents: Long,
    val lineTotalCents: Long
) {
    val remainingToReceiveQty: Double
        get() = (orderedQty - receivedQty).coerceAtLeast(0.0)

    val returnableQty: Double
        get() = (receivedQty - returnedQty).coerceAtLeast(0.0)
}

data class PurchaseOrderRecord(
    val purchaseId: String,
    val branchId: String,
    val poNumber: String,
    val supplierInvoiceNumber: String,
    val supplierId: String,
    val supplierName: String,
    val status: PurchaseStatus,
    val paymentStatus: PurchasePaymentStatus,
    val subtotalCents: Long,
    val discountCents: Long,
    val taxCents: Long,
    val totalCents: Long,
    val paidCents: Long,
    val returnedCents: Long,
    val notes: String,
    val createdByName: String,
    val approvedByName: String?,
    val cancelReason: String?,
    val expectedDateMillis: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val items: List<PurchaseItemRecord>
) {
    val remainingPayableCents: Long
        get() = (totalCents - returnedCents - paidCents).coerceAtLeast(0L)
}

data class PurchaseReceivingRecord(
    val receivingId: String,
    val branchId: String,
    val purchaseId: String,
    val lineId: String,
    val inventoryItemId: String,
    val itemName: String,
    val referenceNumber: String,
    val receivedQty: Double,
    val unit: String,
    val receivedDateMillis: Long,
    val receivedByName: String,
    val notes: String
)

data class PurchaseReturnRecord(
    val returnId: String,
    val branchId: String,
    val purchaseId: String,
    val poNumber: String,
    val supplierId: String,
    val lineId: String,
    val inventoryItemId: String,
    val itemName: String,
    val returnQty: Double,
    val unit: String,
    val returnAmountCents: Long,
    val reason: String,
    val actorName: String,
    val returnedAt: Long
)

data class SupplierPaymentRecord(
    val paymentId: String,
    val branchId: String,
    val supplierId: String,
    val supplierName: String,
    val purchaseId: String?,
    val poNumber: String?,
    val amountCents: Long,
    val paymentMethod: String,
    val referenceNumber: String,
    val paymentDateMillis: Long,
    val notes: String,
    val paidByName: String,
    val createdAt: Long
)

data class CustomerProfileRecord(
    val customerId: String,
    val branchId: String,
    val name: String,
    val phone: String,
    val email: String,
    val address: String,
    val notes: String,
    val loyaltyPoints: Int,
    val tier: String,
    val discountPercent: Int,
    val totalPurchasesCents: Long,
    val lastOrderAt: Long?,
    val accountBalanceCents: Long,
    val feedbackSummary: String,
    val createdAt: Long
)

data class LoyaltyConfigRecord(
    val branchId: String,
    val earnRatePerDollar: Double,
    val redeemCentsPerPoint: Long,
    val minRedeemPoints: Int,
    val silverThresholdPoints: Int,
    val goldThresholdPoints: Int,
    val platinumThresholdPoints: Int,
    val expiryEnabled: Boolean,
    val expiryDays: Int,
    val updatedBy: String,
    val updatedAt: Long
)

data class LoyaltyRewardRecord(
    val rewardId: String,
    val branchId: String,
    val title: String,
    val description: String,
    val pointsRequired: Int,
    val discountCentsValue: Long,
    val isActive: Boolean
)

data class LoyaltyTransactionRecord(
    val txId: String,
    val branchId: String,
    val customerId: String,
    val customerName: String,
    val txType: String,
    val pointsDelta: Int,
    val orderId: String?,
    val reason: String,
    val approvedBy: String?,
    val actorName: String,
    val createdAt: Long
)

data class CustomerFeedbackRecord(
    val feedbackId: String,
    val branchId: String,
    val customerId: String,
    val orderId: String?,
    val rating: Int,
    val comment: String,
    val recordedBy: String,
    val createdAt: Long
)

data class ExpenseRecord(
    val expenseId: String,
    val branchId: String,
    val category: String,
    val amountCents: Long,
    val expenseDateMillis: Long,
    val description: String,
    val paymentMethod: String,
    val receiptUri: String?,
    val approvalStatus: ExpenseApprovalStatus,
    val approvedByName: String?,
    val isRecurring: Boolean,
    val recurrenceFrequency: ExpenseRecurrence,
    val createdByName: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class ExpensePeriodReport(
    val periodLabel: String,
    val totalApprovedCents: Long,
    val totalPendingCents: Long,
    val byCategory: Map<String, Long>,
    val count: Int
)
