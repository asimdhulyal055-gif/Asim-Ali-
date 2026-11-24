package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey val branchId: String,
    val name: String,
    val code: String,
    val address: String,
    val isMainBranch: Boolean,
    val isActive: Boolean,
    val phone: String = "",
    val email: String = "",
    val managerName: String = "",
    val managerUid: String? = null,
    val openingCashCents: Long = 0L,
    val openingBankCents: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Entity(
    tableName = "user_profiles",
    indices = [androidx.room.Index(value = ["username"])]
)
data class UserProfileEntity(
    @PrimaryKey val uid: String,
    val email: String,
    val displayName: String,
    val roleCode: String,
    val branchId: String,
    val isActive: Boolean,
    val lastLoginAt: Long,
    val username: String = "",
    val staffId: String? = null,
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val authProvider: String = "FIREBASE_EMAIL",
    val mustResetPassword: Boolean = false
)

@Entity(tableName = "dashboard_snapshots")
data class DashboardSnapshotEntity(
    @PrimaryKey val branchId: String,
    val businessDate: String,
    val todaySales: Double,
    val todayOrders: Int,
    val totalCustomers: Int,
    val pendingKitchenOrders: Int,
    val lowStockAlerts: Int,
    val dailyExpenses: Double,
    val estimatedProfit: Double,
    val cashPayments: Double,
    val digitalPayments: Double,
    val isSampleData: Boolean,
    val updatedAt: Long
)

@Entity(tableName = "recent_transactions")
data class TransactionRecordEntity(
    @PrimaryKey val id: String,
    val branchId: String,
    val orderNumber: String,
    val invoiceNumber: String,
    val customerName: String,
    val amount: Double,
    val paymentMethod: String,
    val status: String,
    val timeLabel: String,
    val isSampleData: Boolean,
    val createdAt: Long
)

@Entity(tableName = "low_stock_alerts")
data class LowStockAlertEntity(
    @PrimaryKey val id: String,
    val branchId: String,
    val ingredientName: String,
    val currentStock: Double,
    val reorderLevel: Double,
    val unit: String,
    val isSampleData: Boolean
)

@Entity(tableName = "hourly_sales")
data class HourlySalesEntity(
    @PrimaryKey val id: String,
    val branchId: String,
    val hourOrder: Int,
    val label: String,
    val revenue: Double,
    val orderCount: Int,
    val isSampleData: Boolean
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val branchId: String,
    val actorUid: String,
    val actorEmail: String,
    val actorRole: String,
    val action: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val transactionId: String,
    val branchId: String,
    val entityType: String,
    val entityId: String,
    val payloadJson: String,
    val status: String, // PENDING, SYNCED, FAILED_LOCKED_RULES
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "product_categories")
data class ProductCategoryEntity(
    @PrimaryKey val categoryId: String,
    val branchId: String,
    val name: String,
    val sortOrder: Int,
    val isActive: Boolean
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val productId: String,
    val branchId: String,
    val categoryId: String,
    val name: String,
    val description: String,
    val basePriceCents: Long,
    val sku: String,
    val categoryTag: String,
    val isAvailable: Boolean,
    val taxExempt: Boolean,
    val isArchived: Boolean = false,
    val isCombo: Boolean = false,
    val comboSummary: String = "",
    val imagePath: String? = null,
    val kitchenStation: String = "MAIN_KITCHEN"
)

@Entity(tableName = "product_variations")
data class ProductVariationEntity(
    @PrimaryKey val variationId: String,
    val productId: String,
    val name: String,
    val priceDeltaCents: Long,
    val isDefault: Boolean
)

@Entity(tableName = "product_addons")
data class ProductAddonEntity(
    @PrimaryKey val addonId: String,
    val productId: String,
    val name: String,
    val priceCents: Long
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val customerId: String,
    val branchId: String,
    val name: String,
    val phone: String,
    val email: String,
    val loyaltyPoints: Int,
    val tier: String,
    val discountPercent: Int,
    val address: String = "",
    val notes: String = "",
    val totalPurchasesCents: Long = 0L,
    val lastOrderAt: Long? = null,
    val accountBalanceCents: Long = 0L,
    val feedbackSummary: String = "",
    val createdAt: Long = 0L
)

@Entity(tableName = "pos_orders")
data class PosOrderEntity(
    @PrimaryKey val orderId: String,
    val branchId: String,
    val idempotencyKey: String,
    val orderNumber: String,
    val invoiceNumber: String,
    val kotNumber: String,
    val orderType: String,
    val tableNumber: String?,
    val priority: String,
    val customerId: String?,
    val customerName: String?,
    val subtotalCents: Long,
    val discountCents: Long,
    val taxCents: Long,
    val serviceChargeCents: Long,
    val totalCents: Long,
    val paidCents: Long,
    val remainingBalanceCents: Long,
    val changeCents: Long,
    val paymentMethod: String,
    val cashPaidCents: Long,
    val cardPaidCents: Long,
    val bankPaidCents: Long,
    val digitalPaidCents: Long,
    val paymentBreakdown: String,
    val status: String,
    val reprintInvoiceCount: Int,
    val reprintKotCount: Int,
    val cashierUid: String,
    val cashierName: String,
    val approvedBy: String?,
    val cancellationReason: String?,
    val createdAt: Long,
    val loyaltyPointsRedeemed: Int = 0,
    val loyaltyDiscountCents: Long = 0L,
    val loyaltyPointsEarned: Int = 0,
    val loyaltyCardId: String? = null,
    val loyaltyCardNumber: String? = null,
    val loyaltyStampChange: Int = 0,
    val loyaltyStampsAfter: Int = 0,
    val loyaltyVoucherId: String? = null,
    val loyaltyVoucherCode: String? = null,
    val loyaltyVoucherDiscountCents: Long = 0L,
    val loyaltyPromoCostCents: Long = 0L
)

@Entity(tableName = "order_items")
data class OrderItemEntity(
    @PrimaryKey val itemId: String,
    val orderId: String,
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

@Entity(tableName = "kot_tickets")
data class KotTicketEntity(
    @PrimaryKey val kotId: String,
    val orderId: String,
    val branchId: String,
    val kotNumber: String,
    val orderNumber: String,
    val orderType: String,
    val tableNumber: String?,
    val priority: String,
    val station: String,
    val status: String,
    val reprintCount: Int,
    val createdAt: Long,
    val startedPreparingAt: Long? = null,
    val readyAt: Long? = null,
    val completedAt: Long? = null
)

@Entity(tableName = "daily_cash_closings")
data class DailyCashClosingEntity(
    @PrimaryKey val closingId: String,
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
    val closedByUid: String,
    val closedByName: String,
    val notes: String,
    val closedAt: Long
)

// Phase 3: Inventory, Recipes, Stock Movements & Physical Audits

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey val itemId: String,
    val branchId: String,
    val name: String,
    val sku: String,
    val itemType: String, // RAW_MATERIAL or FINISHED_PRODUCT
    val unit: String,     // kg, g, L, ml, pcs
    val currentQty: Double,
    val reorderLevel: Double,
    val unitCostCents: Long,
    val expiryDateMillis: Long? = null,
    val linkedProductId: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val recipeId: String,
    val branchId: String,
    val productId: String,
    val version: Int,
    val notes: String,
    val totalCostCents: Long,
    val updatedBy: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recipe_ingredients")
data class RecipeIngredientEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val inventoryItemId: String,
    val ingredientName: String,
    val quantity: Double,
    val unit: String,
    val unitCostCents: Long
)

@Entity(tableName = "recipe_versions")
data class RecipeVersionEntity(
    @PrimaryKey val versionId: String,
    val recipeId: String,
    val productId: String,
    val versionNumber: Int,
    val totalCostCents: Long,
    val ingredientsSnapshot: String,
    val notes: String,
    val editedBy: String,
    val editedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "stock_movements",
    indices = [androidx.room.Index(value = ["deductionKey"], unique = true)]
)
data class StockMovementEntity(
    @PrimaryKey val movementId: String,
    val branchId: String,
    val inventoryItemId: String,
    val itemName: String,
    val movementType: String, // RECEIVING, SALE_DEDUCTION, ADJUSTMENT, WASTAGE, AUDIT_CORRECTION
    val quantityDelta: Double,
    val unit: String,
    val referenceOrderId: String? = null,
    val deductionKey: String? = null,
    val reason: String,
    val actorName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "physical_audits")
data class PhysicalAuditEntity(
    @PrimaryKey val auditId: String,
    val branchId: String,
    val inventoryItemId: String,
    val itemName: String,
    val expectedQty: Double,
    val countedQty: Double,
    val varianceQty: Double,
    val notes: String,
    val auditorName: String,
    val auditedAt: Long = System.currentTimeMillis()
)

// Phase 4: Suppliers, Purchases, Customers/Loyalty & Expense Management

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey val supplierId: String,
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
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "purchase_orders",
    indices = [androidx.room.Index(value = ["idempotencyKey"], unique = true)]
)
data class PurchaseOrderEntity(
    @PrimaryKey val purchaseId: String,
    val branchId: String,
    val idempotencyKey: String,
    val poNumber: String,
    val supplierInvoiceNumber: String,
    val supplierId: String,
    val supplierName: String,
    val status: String, // DRAFT, PENDING, APPROVED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED
    val paymentStatus: String, // UNPAID, PARTIALLY_PAID, PAID
    val subtotalCents: Long,
    val discountCents: Long,
    val taxCents: Long,
    val totalCents: Long,
    val paidCents: Long,
    val returnedCents: Long,
    val notes: String,
    val createdByUid: String,
    val createdByName: String,
    val approvedByName: String? = null,
    val cancelReason: String? = null,
    val expectedDateMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "purchase_items")
data class PurchaseItemEntity(
    @PrimaryKey val lineId: String,
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
)

@Entity(
    tableName = "purchase_receivings",
    indices = [androidx.room.Index(value = ["receivingKey"], unique = true)]
)
data class PurchaseReceivingEntity(
    @PrimaryKey val receivingId: String,
    val branchId: String,
    val purchaseId: String,
    val lineId: String,
    val inventoryItemId: String,
    val itemName: String,
    val receivingKey: String,
    val referenceNumber: String,
    val receivedQty: Double,
    val unit: String,
    val receivedDateMillis: Long,
    val receivedByName: String,
    val notes: String
)

@Entity(tableName = "purchase_returns")
data class PurchaseReturnEntity(
    @PrimaryKey val returnId: String,
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
    val returnedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "supplier_payments",
    indices = [androidx.room.Index(value = ["idempotencyKey"], unique = true)]
)
data class SupplierPaymentEntity(
    @PrimaryKey val paymentId: String,
    val branchId: String,
    val idempotencyKey: String,
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
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "loyalty_config")
data class LoyaltyConfigEntity(
    @PrimaryKey val branchId: String,
    val earnRatePerDollar: Double,
    val redeemCentsPerPoint: Long,
    val minRedeemPoints: Int,
    val silverThresholdPoints: Int,
    val goldThresholdPoints: Int,
    val platinumThresholdPoints: Int,
    val expiryEnabled: Boolean,
    val expiryDays: Int,
    val updatedBy: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "loyalty_rewards")
data class LoyaltyRewardEntity(
    @PrimaryKey val rewardId: String,
    val branchId: String,
    val title: String,
    val description: String,
    val pointsRequired: Int,
    val discountCentsValue: Long,
    val isActive: Boolean = true
)

@Entity(
    tableName = "loyalty_transactions",
    indices = [androidx.room.Index(value = ["idempotencyKey"], unique = true)]
)
data class LoyaltyTransactionEntity(
    @PrimaryKey val txId: String,
    val branchId: String,
    val customerId: String,
    val customerName: String,
    val idempotencyKey: String,
    val txType: String, // EARN, REDEEM, ADJUSTMENT, EXPIRED
    val pointsDelta: Int,
    val orderId: String? = null,
    val reason: String,
    val approvedBy: String? = null,
    val actorName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "customer_feedback")
data class CustomerFeedbackEntity(
    @PrimaryKey val feedbackId: String,
    val branchId: String,
    val customerId: String,
    val orderId: String?,
    val rating: Int,
    val comment: String,
    val recordedBy: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "expenses",
    indices = [androidx.room.Index(value = ["idempotencyKey"], unique = true)]
)
data class ExpenseEntity(
    @PrimaryKey val expenseId: String,
    val branchId: String,
    val idempotencyKey: String,
    val category: String,
    val amountCents: Long,
    val expenseDateMillis: Long,
    val description: String,
    val paymentMethod: String,
    val receiptUri: String? = null,
    val approvalStatus: String, // PENDING, APPROVED, REJECTED
    val approvedByName: String? = null,
    val isRecurring: Boolean = false,
    val recurrenceFrequency: String = "NONE", // NONE, WEEKLY, MONTHLY, QUARTERLY
    val createdByUid: String,
    val createdByName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "staff_profiles",
    indices = [androidx.room.Index(value = ["employeeCode"], unique = true)]
)
data class StaffProfileEntity(
    @PrimaryKey val staffId: String,
    val branchId: String,
    val employeeCode: String,
    val linkedUserUid: String? = null,
    val fullName: String,
    val phone: String,
    val address: String,
    val jobTitle: String,
    val department: String,
    val assignedRoleCode: String,
    val accessibleBranchIdsCsv: String,
    val joiningDateMillis: Long,
    val employmentStatus: String, // ACTIVE, ON_LEAVE, INACTIVE
    val salaryType: String,       // MONTHLY, DAILY, HOURLY
    val baseSalaryCents: Long,
    val defaultAllowanceCents: Long = 0L,
    val overtimeMultiplier: Double = 1.5,
    val annualLeaveBalanceDays: Int = 14,
    val sickLeaveBalanceDays: Int = 10,
    val casualLeaveBalanceDays: Int = 7,
    val attendancePin: String = "",
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "staff_documents")
data class StaffDocumentEntity(
    @PrimaryKey val docId: String,
    val branchId: String,
    val staffId: String,
    val title: String,
    val docType: String, // ID_CARD, CONTRACT, CERTIFICATE, OTHER
    val fileUri: String? = null,
    val notes: String = "",
    val uploadedByName: String,
    val uploadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shift_definitions")
data class ShiftDefinitionEntity(
    @PrimaryKey val shiftId: String,
    val branchId: String,
    val shiftName: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val isOvernight: Boolean,
    val breakDurationMinutes: Int,
    val graceLateMinutes: Int,
    val regularHoursThresholdMinutes: Int,
    val overtimeMultiplier: Double,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shift_assignments")
data class ShiftAssignmentEntity(
    @PrimaryKey val assignmentId: String,
    val branchId: String,
    val shiftId: String,
    val shiftName: String,
    val staffId: String? = null,
    val staffName: String,
    val teamDepartment: String? = null,
    val dayOfWeek: Int = 0, // 0 = All days, 1..7 = Mon..Sun
    val effectiveDateMillis: Long,
    val assignedByName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attendance_records",
    indices = [androidx.room.Index(value = ["staffId", "attendanceDateStr"], unique = true)]
)
data class AttendanceRecordEntity(
    @PrimaryKey val attendanceId: String,
    val branchId: String,
    val staffId: String,
    val employeeCode: String,
    val staffName: String,
    val attendanceDateStr: String,
    val attendanceDateMillis: Long,
    val shiftId: String? = null,
    val shiftName: String? = null,
    val checkInMillis: Long? = null,
    val checkOutMillis: Long? = null,
    val status: String,             // PRESENT, ABSENT, LATE, HALF_DAY, LEAVE, HOLIDAY
    val verificationMethod: String, // MANUAL, PIN, SUPERVISOR, LEAVE_SYNC
    val workedMinutes: Int = 0,
    val overtimeMinutes: Int = 0,
    val isManualCorrection: Boolean = false,
    val correctionReason: String? = null,
    val approvedByName: String? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "leave_requests")
data class LeaveRequestEntity(
    @PrimaryKey val leaveId: String,
    val branchId: String,
    val staffId: String,
    val employeeCode: String,
    val staffName: String,
    val leaveType: String, // ANNUAL, SICK, CASUAL, UNPAID
    val startDateMillis: Long,
    val endDateMillis: Long,
    val daysCount: Int,
    val reason: String,
    val status: String,    // PENDING, APPROVED, REJECTED
    val reviewedByName: String? = null,
    val reviewNotes: String? = null,
    val reviewedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payroll_records",
    indices = [androidx.room.Index(value = ["staffId", "periodKey"], unique = true)]
)
data class PayrollRecordEntity(
    @PrimaryKey val payrollId: String,
    val branchId: String,
    val staffId: String,
    val employeeCode: String,
    val staffName: String,
    val periodKey: String,
    val periodLabel: String,
    val periodStartMillis: Long,
    val periodEndMillis: Long,
    val salaryType: String,
    val baseRateCents: Long,
    val daysPresent: Int,
    val daysHalf: Int,
    val paidLeaveDays: Int,
    val holidayDays: Int,
    val absentDays: Int,
    val regularMinutes: Int,
    val overtimeMinutes: Int,
    val baseEarningsCents: Long,
    val overtimePayCents: Long,
    val allowancesCents: Long,
    val bonusesCents: Long,
    val deductionsCents: Long,
    val deductionReason: String = "",
    val grossSalaryCents: Long,
    val netSalaryCents: Long,
    val status: String, // DRAFT, APPROVED, PAID
    val approvedByName: String? = null,
    val approvedAt: Long? = null,
    val paymentDateMillis: Long? = null,
    val paymentMethod: String? = null,
    val paymentReference: String? = null,
    val generatedByName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "hr_permission_policies")
data class HrPermissionPolicyEntity(
    @PrimaryKey val roleCode: String,
    val canManageStaff: Boolean,
    val canViewConfidentialSalary: Boolean,
    val canManageShifts: Boolean,
    val canApproveAttendanceCorrections: Boolean,
    val canApproveLeave: Boolean,
    val canGeneratePayroll: Boolean,
    val canApproveAndPayPayroll: Boolean,
    val canExportHrReports: Boolean,
    val updatedBy: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chart_of_accounts",
    indices = [androidx.room.Index(value = ["branchId", "accountCode"], unique = true)]
)
data class ChartOfAccountEntity(
    @PrimaryKey val accountId: String,
    val branchId: String,
    val accountCode: String,
    val accountName: String,
    val accountType: String,     // ASSET, LIABILITY, EQUITY, INCOME, EXPENSE
    val accountCategory: String, // CASH, BANK, ACCOUNTS_RECEIVABLE, COGS, etc.
    val isCashOrBank: Boolean,
    val openingBalanceCents: Long = 0L,
    val isSystemAccount: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "journal_entries",
    indices = [androidx.room.Index(value = ["sourceKey"], unique = true)]
)
data class JournalEntryEntity(
    @PrimaryKey val entryId: String,
    val branchId: String,
    val entryNumber: String,
    val entryDateMillis: Long,
    val sourceModule: String,
    val sourceEntityId: String? = null,
    val sourceKey: String,
    val reference: String,
    val description: String,
    val totalDebitCents: Long,
    val totalCreditCents: Long,
    val status: String, // POSTED, REVERSED
    val reversedEntryId: String? = null,
    val postedByUid: String,
    val postedByName: String,
    val approvedByName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "journal_lines",
    indices = [
        androidx.room.Index(value = ["entryId"]),
        androidx.room.Index(value = ["accountId"]),
        androidx.room.Index(value = ["branchId"])
    ]
)
data class JournalLineEntity(
    @PrimaryKey val lineId: String,
    val entryId: String,
    val branchId: String,
    val accountId: String,
    val accountCode: String,
    val accountName: String,
    val accountType: String,
    val debitCents: Long,
    val creditCents: Long,
    val partyType: String? = null, // CUSTOMER, SUPPLIER, STAFF
    val partyId: String? = null,
    val partyName: String? = null,
    val memo: String = "",
    val entryDateMillis: Long
)

@Entity(
    tableName = "cashbook_entries",
    indices = [androidx.room.Index(value = ["sourceKey"], unique = true)]
)
data class CashbookEntryEntity(
    @PrimaryKey val cashbookId: String,
    val branchId: String,
    val accountId: String,
    val accountName: String,
    val txType: String, // RECEIPT, PAYMENT, TRANSFER_IN, TRANSFER_OUT
    val amountCents: Long,
    val reference: String,
    val counterpartyName: String,
    val description: String,
    val txDateMillis: Long,
    val linkedJournalEntryId: String,
    val sourceKey: String,
    val recordedByName: String,
    val createdAt: Long = System.currentTimeMillis()
)




