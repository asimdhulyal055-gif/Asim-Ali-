package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BranchEntity::class,
        UserProfileEntity::class,
        DashboardSnapshotEntity::class,
        TransactionRecordEntity::class,
        LowStockAlertEntity::class,
        HourlySalesEntity::class,
        AuditLogEntity::class,
        SyncQueueEntity::class,
        ProductCategoryEntity::class,
        ProductEntity::class,
        ProductVariationEntity::class,
        ProductAddonEntity::class,
        CustomerEntity::class,
        PosOrderEntity::class,
        OrderItemEntity::class,
        KotTicketEntity::class,
        DailyCashClosingEntity::class,
        InventoryItemEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        RecipeVersionEntity::class,
        StockMovementEntity::class,
        PhysicalAuditEntity::class,
        SupplierEntity::class,
        PurchaseOrderEntity::class,
        PurchaseItemEntity::class,
        PurchaseReceivingEntity::class,
        PurchaseReturnEntity::class,
        SupplierPaymentEntity::class,
        LoyaltyConfigEntity::class,
        LoyaltyRewardEntity::class,
        LoyaltyTransactionEntity::class,
        CustomerFeedbackEntity::class,
        ExpenseEntity::class,
        StaffProfileEntity::class,
        StaffDocumentEntity::class,
        ShiftDefinitionEntity::class,
        ShiftAssignmentEntity::class,
        AttendanceRecordEntity::class,
        LeaveRequestEntity::class,
        PayrollRecordEntity::class,
        HrPermissionPolicyEntity::class,
        ChartOfAccountEntity::class,
        JournalEntryEntity::class,
        JournalLineEntity::class,
        CashbookEntryEntity::class,
        SyncMetadataEntity::class,
        SyncConflictEntity::class,
        BackupSnapshotEntity::class,
        RecoveryLogEntity::class,
        DeviceSessionEntity::class,
        ErpNotificationEntity::class,
        RolePermissionPolicyEntity::class,
        LoyaltyProgramSettingsEntity::class,
        LoyaltyCardEntity::class,
        LoyaltyStampTransactionEntity::class,
        LoyaltyRewardVoucherEntity::class,
        LoyaltyRedemptionEntity::class,
        LoyaltyAuditLogEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AaCraveDatabase : RoomDatabase() {
    abstract fun branchDao(): BranchDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun posDao(): PosDao
    abstract fun recipeDao(): RecipeDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun supplierDao(): SupplierDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun customerLoyaltyDao(): CustomerLoyaltyDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun staffDao(): StaffDao
    abstract fun shiftAttendanceDao(): ShiftAttendanceDao
    abstract fun leavePayrollDao(): LeavePayrollDao
    abstract fun accountingDao(): AccountingDao
    abstract fun phase7Dao(): Phase7Dao
    abstract fun smartLoyaltyDao(): SmartLoyaltyDao

    companion object {
        @Volatile
        private var INSTANCE: AaCraveDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `product_categories` (
                        `categoryId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        PRIMARY KEY(`categoryId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `products` (
                        `productId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `categoryId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `basePriceCents` INTEGER NOT NULL,
                        `sku` TEXT NOT NULL,
                        `categoryTag` TEXT NOT NULL,
                        `isAvailable` INTEGER NOT NULL,
                        `taxExempt` INTEGER NOT NULL,
                        PRIMARY KEY(`productId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `product_variations` (
                        `variationId` TEXT NOT NULL,
                        `productId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `priceDeltaCents` INTEGER NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        PRIMARY KEY(`variationId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `product_addons` (
                        `addonId` TEXT NOT NULL,
                        `productId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `priceCents` INTEGER NOT NULL,
                        PRIMARY KEY(`addonId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `customers` (
                        `customerId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `phone` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `loyaltyPoints` INTEGER NOT NULL,
                        `tier` TEXT NOT NULL,
                        `discountPercent` INTEGER NOT NULL,
                        PRIMARY KEY(`customerId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pos_orders` (
                        `orderId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `idempotencyKey` TEXT NOT NULL,
                        `orderNumber` TEXT NOT NULL,
                        `invoiceNumber` TEXT NOT NULL,
                        `kotNumber` TEXT NOT NULL,
                        `orderType` TEXT NOT NULL,
                        `tableNumber` TEXT,
                        `priority` TEXT NOT NULL,
                        `customerId` TEXT,
                        `customerName` TEXT,
                        `subtotalCents` INTEGER NOT NULL,
                        `discountCents` INTEGER NOT NULL,
                        `taxCents` INTEGER NOT NULL,
                        `serviceChargeCents` INTEGER NOT NULL,
                        `totalCents` INTEGER NOT NULL,
                        `paidCents` INTEGER NOT NULL,
                        `remainingBalanceCents` INTEGER NOT NULL,
                        `changeCents` INTEGER NOT NULL,
                        `paymentMethod` TEXT NOT NULL,
                        `cashPaidCents` INTEGER NOT NULL,
                        `cardPaidCents` INTEGER NOT NULL,
                        `bankPaidCents` INTEGER NOT NULL,
                        `digitalPaidCents` INTEGER NOT NULL,
                        `paymentBreakdown` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `reprintInvoiceCount` INTEGER NOT NULL,
                        `reprintKotCount` INTEGER NOT NULL,
                        `cashierUid` TEXT NOT NULL,
                        `cashierName` TEXT NOT NULL,
                        `approvedBy` TEXT,
                        `cancellationReason` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`orderId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `order_items` (
                        `itemId` TEXT NOT NULL,
                        `orderId` TEXT NOT NULL,
                        `productId` TEXT NOT NULL,
                        `productName` TEXT NOT NULL,
                        `variationName` TEXT,
                        `addonsSummary` TEXT NOT NULL,
                        `specialInstructions` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `unitPriceCents` INTEGER NOT NULL,
                        `addonsUnitCents` INTEGER NOT NULL,
                        `lineTotalCents` INTEGER NOT NULL,
                        PRIMARY KEY(`itemId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `kot_tickets` (
                        `kotId` TEXT NOT NULL,
                        `orderId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `kotNumber` TEXT NOT NULL,
                        `orderNumber` TEXT NOT NULL,
                        `orderType` TEXT NOT NULL,
                        `tableNumber` TEXT,
                        `priority` TEXT NOT NULL,
                        `station` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `reprintCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`kotId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_cash_closings` (
                        `closingId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `businessDate` TEXT NOT NULL,
                        `cashSalesCents` INTEGER NOT NULL,
                        `cardSalesCents` INTEGER NOT NULL,
                        `bankTransferSalesCents` INTEGER NOT NULL,
                        `digitalSalesCents` INTEGER NOT NULL,
                        `refundsCents` INTEGER NOT NULL,
                        `expectedCashCents` INTEGER NOT NULL,
                        `countedCashCents` INTEGER NOT NULL,
                        `varianceCents` INTEGER NOT NULL,
                        `closedByUid` TEXT NOT NULL,
                        `closedByName` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `closedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`closingId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `products` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `products` ADD COLUMN `isCombo` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `products` ADD COLUMN `comboSummary` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `products` ADD COLUMN `imagePath` TEXT")
                db.execSQL("ALTER TABLE `products` ADD COLUMN `kitchenStation` TEXT NOT NULL DEFAULT 'MAIN_KITCHEN'")

                db.execSQL("ALTER TABLE `kot_tickets` ADD COLUMN `startedPreparingAt` INTEGER")
                db.execSQL("ALTER TABLE `kot_tickets` ADD COLUMN `readyAt` INTEGER")
                db.execSQL("ALTER TABLE `kot_tickets` ADD COLUMN `completedAt` INTEGER")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `inventory_items` (
                        `itemId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sku` TEXT NOT NULL,
                        `itemType` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `currentQty` REAL NOT NULL,
                        `reorderLevel` REAL NOT NULL,
                        `unitCostCents` INTEGER NOT NULL,
                        `expiryDateMillis` INTEGER,
                        `linkedProductId` TEXT,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`itemId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recipes` (
                        `recipeId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `productId` TEXT NOT NULL,
                        `version` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `totalCostCents` INTEGER NOT NULL,
                        `updatedBy` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`recipeId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recipe_ingredients` (
                        `id` TEXT NOT NULL,
                        `recipeId` TEXT NOT NULL,
                        `inventoryItemId` TEXT NOT NULL,
                        `ingredientName` TEXT NOT NULL,
                        `quantity` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `unitCostCents` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recipe_versions` (
                        `versionId` TEXT NOT NULL,
                        `recipeId` TEXT NOT NULL,
                        `productId` TEXT NOT NULL,
                        `versionNumber` INTEGER NOT NULL,
                        `totalCostCents` INTEGER NOT NULL,
                        `ingredientsSnapshot` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `editedBy` TEXT NOT NULL,
                        `editedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`versionId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stock_movements` (
                        `movementId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `inventoryItemId` TEXT NOT NULL,
                        `itemName` TEXT NOT NULL,
                        `movementType` TEXT NOT NULL,
                        `quantityDelta` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `referenceOrderId` TEXT,
                        `deductionKey` TEXT,
                        `reason` TEXT NOT NULL,
                        `actorName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`movementId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_stock_movements_deductionKey` ON `stock_movements` (`deductionKey`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `physical_audits` (
                        `auditId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `inventoryItemId` TEXT NOT NULL,
                        `itemName` TEXT NOT NULL,
                        `expectedQty` REAL NOT NULL,
                        `countedQty` REAL NOT NULL,
                        `varianceQty` REAL NOT NULL,
                        `notes` TEXT NOT NULL,
                        `auditorName` TEXT NOT NULL,
                        `auditedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`auditId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `customers` ADD COLUMN `address` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `customers` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `customers` ADD COLUMN `totalPurchasesCents` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `customers` ADD COLUMN `lastOrderAt` INTEGER")
                db.execSQL("ALTER TABLE `customers` ADD COLUMN `accountBalanceCents` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `customers` ADD COLUMN `feedbackSummary` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `customers` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE `pos_orders` ADD COLUMN `loyaltyPointsRedeemed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `pos_orders` ADD COLUMN `loyaltyDiscountCents` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `pos_orders` ADD COLUMN `loyaltyPointsEarned` INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `suppliers` (
                        `supplierId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `contactPerson` TEXT NOT NULL,
                        `phone` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `openingBalanceCents` INTEGER NOT NULL,
                        `totalPurchasesCents` INTEGER NOT NULL,
                        `totalReturnsCents` INTEGER NOT NULL,
                        `totalPaidCents` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`supplierId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `purchase_orders` (
                        `purchaseId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `idempotencyKey` TEXT NOT NULL,
                        `poNumber` TEXT NOT NULL,
                        `supplierInvoiceNumber` TEXT NOT NULL,
                        `supplierId` TEXT NOT NULL,
                        `supplierName` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `paymentStatus` TEXT NOT NULL,
                        `subtotalCents` INTEGER NOT NULL,
                        `discountCents` INTEGER NOT NULL,
                        `taxCents` INTEGER NOT NULL,
                        `totalCents` INTEGER NOT NULL,
                        `paidCents` INTEGER NOT NULL,
                        `returnedCents` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdByUid` TEXT NOT NULL,
                        `createdByName` TEXT NOT NULL,
                        `approvedByName` TEXT,
                        `cancelReason` TEXT,
                        `expectedDateMillis` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`purchaseId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_orders_idempotencyKey` ON `purchase_orders` (`idempotencyKey`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `purchase_items` (
                        `lineId` TEXT NOT NULL,
                        `purchaseId` TEXT NOT NULL,
                        `inventoryItemId` TEXT NOT NULL,
                        `itemName` TEXT NOT NULL,
                        `itemType` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `orderedQty` REAL NOT NULL,
                        `receivedQty` REAL NOT NULL,
                        `returnedQty` REAL NOT NULL,
                        `unitPriceCents` INTEGER NOT NULL,
                        `lineTotalCents` INTEGER NOT NULL,
                        PRIMARY KEY(`lineId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `purchase_receivings` (
                        `receivingId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `purchaseId` TEXT NOT NULL,
                        `lineId` TEXT NOT NULL,
                        `inventoryItemId` TEXT NOT NULL,
                        `itemName` TEXT NOT NULL,
                        `receivingKey` TEXT NOT NULL,
                        `referenceNumber` TEXT NOT NULL,
                        `receivedQty` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `receivedDateMillis` INTEGER NOT NULL,
                        `receivedByName` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        PRIMARY KEY(`receivingId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_receivings_receivingKey` ON `purchase_receivings` (`receivingKey`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `purchase_returns` (
                        `returnId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `purchaseId` TEXT NOT NULL,
                        `poNumber` TEXT NOT NULL,
                        `supplierId` TEXT NOT NULL,
                        `lineId` TEXT NOT NULL,
                        `inventoryItemId` TEXT NOT NULL,
                        `itemName` TEXT NOT NULL,
                        `returnQty` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `returnAmountCents` INTEGER NOT NULL,
                        `reason` TEXT NOT NULL,
                        `actorName` TEXT NOT NULL,
                        `returnedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`returnId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `supplier_payments` (
                        `paymentId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `idempotencyKey` TEXT NOT NULL,
                        `supplierId` TEXT NOT NULL,
                        `supplierName` TEXT NOT NULL,
                        `purchaseId` TEXT,
                        `poNumber` TEXT,
                        `amountCents` INTEGER NOT NULL,
                        `paymentMethod` TEXT NOT NULL,
                        `referenceNumber` TEXT NOT NULL,
                        `paymentDateMillis` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `paidByName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`paymentId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_supplier_payments_idempotencyKey` ON `supplier_payments` (`idempotencyKey`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `loyalty_config` (
                        `branchId` TEXT NOT NULL,
                        `earnRatePerDollar` REAL NOT NULL,
                        `redeemCentsPerPoint` INTEGER NOT NULL,
                        `minRedeemPoints` INTEGER NOT NULL,
                        `silverThresholdPoints` INTEGER NOT NULL,
                        `goldThresholdPoints` INTEGER NOT NULL,
                        `platinumThresholdPoints` INTEGER NOT NULL,
                        `expiryEnabled` INTEGER NOT NULL,
                        `expiryDays` INTEGER NOT NULL,
                        `updatedBy` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`branchId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `loyalty_rewards` (
                        `rewardId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `pointsRequired` INTEGER NOT NULL,
                        `discountCentsValue` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        PRIMARY KEY(`rewardId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `loyalty_transactions` (
                        `txId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `customerId` TEXT NOT NULL,
                        `customerName` TEXT NOT NULL,
                        `idempotencyKey` TEXT NOT NULL,
                        `txType` TEXT NOT NULL,
                        `pointsDelta` INTEGER NOT NULL,
                        `orderId` TEXT,
                        `reason` TEXT NOT NULL,
                        `approvedBy` TEXT,
                        `actorName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`txId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_loyalty_transactions_idempotencyKey` ON `loyalty_transactions` (`idempotencyKey`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `customer_feedback` (
                        `feedbackId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `customerId` TEXT NOT NULL,
                        `orderId` TEXT,
                        `rating` INTEGER NOT NULL,
                        `comment` TEXT NOT NULL,
                        `recordedBy` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`feedbackId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expenses` (
                        `expenseId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `idempotencyKey` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `expenseDateMillis` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        `paymentMethod` TEXT NOT NULL,
                        `receiptUri` TEXT,
                        `approvalStatus` TEXT NOT NULL,
                        `approvedByName` TEXT,
                        `isRecurring` INTEGER NOT NULL,
                        `recurrenceFrequency` TEXT NOT NULL,
                        `createdByUid` TEXT NOT NULL,
                        `createdByName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`expenseId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_expenses_idempotencyKey` ON `expenses` (`idempotencyKey`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `staff_profiles` (
                        `staffId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `employeeCode` TEXT NOT NULL,
                        `linkedUserUid` TEXT,
                        `fullName` TEXT NOT NULL,
                        `phone` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `jobTitle` TEXT NOT NULL,
                        `department` TEXT NOT NULL,
                        `assignedRoleCode` TEXT NOT NULL,
                        `accessibleBranchIdsCsv` TEXT NOT NULL,
                        `joiningDateMillis` INTEGER NOT NULL,
                        `employmentStatus` TEXT NOT NULL,
                        `salaryType` TEXT NOT NULL,
                        `baseSalaryCents` INTEGER NOT NULL,
                        `defaultAllowanceCents` INTEGER NOT NULL,
                        `overtimeMultiplier` REAL NOT NULL,
                        `annualLeaveBalanceDays` INTEGER NOT NULL,
                        `sickLeaveBalanceDays` INTEGER NOT NULL,
                        `casualLeaveBalanceDays` INTEGER NOT NULL,
                        `attendancePin` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`staffId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_staff_profiles_employeeCode` ON `staff_profiles` (`employeeCode`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `staff_documents` (
                        `docId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `staffId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `docType` TEXT NOT NULL,
                        `fileUri` TEXT,
                        `notes` TEXT NOT NULL,
                        `uploadedByName` TEXT NOT NULL,
                        `uploadedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`docId`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shift_definitions` (
                        `shiftId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `shiftName` TEXT NOT NULL,
                        `startMinuteOfDay` INTEGER NOT NULL,
                        `endMinuteOfDay` INTEGER NOT NULL,
                        `isOvernight` INTEGER NOT NULL,
                        `breakDurationMinutes` INTEGER NOT NULL,
                        `graceLateMinutes` INTEGER NOT NULL,
                        `regularHoursThresholdMinutes` INTEGER NOT NULL,
                        `overtimeMultiplier` REAL NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`shiftId`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shift_assignments` (
                        `assignmentId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `shiftId` TEXT NOT NULL,
                        `shiftName` TEXT NOT NULL,
                        `staffId` TEXT,
                        `staffName` TEXT NOT NULL,
                        `teamDepartment` TEXT,
                        `dayOfWeek` INTEGER NOT NULL,
                        `effectiveDateMillis` INTEGER NOT NULL,
                        `assignedByName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`assignmentId`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `attendance_records` (
                        `attendanceId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `staffId` TEXT NOT NULL,
                        `employeeCode` TEXT NOT NULL,
                        `staffName` TEXT NOT NULL,
                        `attendanceDateStr` TEXT NOT NULL,
                        `attendanceDateMillis` INTEGER NOT NULL,
                        `shiftId` TEXT,
                        `shiftName` TEXT,
                        `checkInMillis` INTEGER,
                        `checkOutMillis` INTEGER,
                        `status` TEXT NOT NULL,
                        `verificationMethod` TEXT NOT NULL,
                        `workedMinutes` INTEGER NOT NULL,
                        `overtimeMinutes` INTEGER NOT NULL,
                        `isManualCorrection` INTEGER NOT NULL,
                        `correctionReason` TEXT,
                        `approvedByName` TEXT,
                        `notes` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`attendanceId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attendance_records_staffId_attendanceDateStr` ON `attendance_records` (`staffId`, `attendanceDateStr`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `leave_requests` (
                        `leaveId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `staffId` TEXT NOT NULL,
                        `employeeCode` TEXT NOT NULL,
                        `staffName` TEXT NOT NULL,
                        `leaveType` TEXT NOT NULL,
                        `startDateMillis` INTEGER NOT NULL,
                        `endDateMillis` INTEGER NOT NULL,
                        `daysCount` INTEGER NOT NULL,
                        `reason` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `reviewedByName` TEXT,
                        `reviewNotes` TEXT,
                        `reviewedAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`leaveId`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payroll_records` (
                        `payrollId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `staffId` TEXT NOT NULL,
                        `employeeCode` TEXT NOT NULL,
                        `staffName` TEXT NOT NULL,
                        `periodKey` TEXT NOT NULL,
                        `periodLabel` TEXT NOT NULL,
                        `periodStartMillis` INTEGER NOT NULL,
                        `periodEndMillis` INTEGER NOT NULL,
                        `salaryType` TEXT NOT NULL,
                        `baseRateCents` INTEGER NOT NULL,
                        `daysPresent` INTEGER NOT NULL,
                        `daysHalf` INTEGER NOT NULL,
                        `paidLeaveDays` INTEGER NOT NULL,
                        `holidayDays` INTEGER NOT NULL,
                        `absentDays` INTEGER NOT NULL,
                        `regularMinutes` INTEGER NOT NULL,
                        `overtimeMinutes` INTEGER NOT NULL,
                        `baseEarningsCents` INTEGER NOT NULL,
                        `overtimePayCents` INTEGER NOT NULL,
                        `allowancesCents` INTEGER NOT NULL,
                        `bonusesCents` INTEGER NOT NULL,
                        `deductionsCents` INTEGER NOT NULL,
                        `deductionReason` TEXT NOT NULL,
                        `grossSalaryCents` INTEGER NOT NULL,
                        `netSalaryCents` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `approvedByName` TEXT,
                        `approvedAt` INTEGER,
                        `paymentDateMillis` INTEGER,
                        `paymentMethod` TEXT,
                        `paymentReference` TEXT,
                        `generatedByName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`payrollId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payroll_records_staffId_periodKey` ON `payroll_records` (`staffId`, `periodKey`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `hr_permission_policies` (
                        `roleCode` TEXT NOT NULL,
                        `canManageStaff` INTEGER NOT NULL,
                        `canViewConfidentialSalary` INTEGER NOT NULL,
                        `canManageShifts` INTEGER NOT NULL,
                        `canApproveAttendanceCorrections` INTEGER NOT NULL,
                        `canApproveLeave` INTEGER NOT NULL,
                        `canGeneratePayroll` INTEGER NOT NULL,
                        `canApproveAndPayPayroll` INTEGER NOT NULL,
                        `canExportHrReports` INTEGER NOT NULL,
                        `updatedBy` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`roleCode`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `branches` ADD COLUMN `phone` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `branches` ADD COLUMN `email` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `branches` ADD COLUMN `managerName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `branches` ADD COLUMN `managerUid` TEXT")
                db.execSQL("ALTER TABLE `branches` ADD COLUMN `openingCashCents` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `branches` ADD COLUMN `openingBankCents` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `branches` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `branches` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chart_of_accounts` (
                        `accountId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `accountCode` TEXT NOT NULL,
                        `accountName` TEXT NOT NULL,
                        `accountType` TEXT NOT NULL,
                        `accountCategory` TEXT NOT NULL,
                        `isCashOrBank` INTEGER NOT NULL,
                        `openingBalanceCents` INTEGER NOT NULL,
                        `isSystemAccount` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`accountId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chart_of_accounts_branchId_accountCode` ON `chart_of_accounts` (`branchId`, `accountCode`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `journal_entries` (
                        `entryId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `entryNumber` TEXT NOT NULL,
                        `entryDateMillis` INTEGER NOT NULL,
                        `sourceModule` TEXT NOT NULL,
                        `sourceEntityId` TEXT,
                        `sourceKey` TEXT NOT NULL,
                        `reference` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `totalDebitCents` INTEGER NOT NULL,
                        `totalCreditCents` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `reversedEntryId` TEXT,
                        `postedByUid` TEXT NOT NULL,
                        `postedByName` TEXT NOT NULL,
                        `approvedByName` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`entryId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_journal_entries_sourceKey` ON `journal_entries` (`sourceKey`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `journal_lines` (
                        `lineId` TEXT NOT NULL,
                        `entryId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `accountCode` TEXT NOT NULL,
                        `accountName` TEXT NOT NULL,
                        `accountType` TEXT NOT NULL,
                        `debitCents` INTEGER NOT NULL,
                        `creditCents` INTEGER NOT NULL,
                        `partyType` TEXT,
                        `partyId` TEXT,
                        `partyName` TEXT,
                        `memo` TEXT NOT NULL,
                        `entryDateMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`lineId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_lines_entryId` ON `journal_lines` (`entryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_lines_accountId` ON `journal_lines` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_lines_branchId` ON `journal_lines` (`branchId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cashbook_entries` (
                        `cashbookId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `accountName` TEXT NOT NULL,
                        `txType` TEXT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `reference` TEXT NOT NULL,
                        `counterpartyName` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `txDateMillis` INTEGER NOT NULL,
                        `linkedJournalEntryId` TEXT NOT NULL,
                        `sourceKey` TEXT NOT NULL,
                        `recordedByName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`cashbookId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cashbook_entries_sourceKey` ON `cashbook_entries` (`sourceKey`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_record_versions` (
                        `recordKey` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `domainCollection` TEXT NOT NULL,
                        `recordVersion` INTEGER NOT NULL,
                        `contentSha256` TEXT NOT NULL,
                        `deviceId` TEXT NOT NULL,
                        `updatedByUid` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `lastSyncedAt` INTEGER,
                        `syncState` TEXT NOT NULL,
                        PRIMARY KEY(`recordKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_record_versions_branchId_entityType_entityId` ON `sync_record_versions` (`branchId`, `entityType`, `entityId`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_conflicts` (
                        `conflictId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `recordLabel` TEXT NOT NULL,
                        `isFinancialProtected` INTEGER NOT NULL,
                        `localVersion` INTEGER NOT NULL,
                        `remoteVersion` INTEGER NOT NULL,
                        `localUpdatedAt` INTEGER NOT NULL,
                        `remoteUpdatedAt` INTEGER NOT NULL,
                        `localDeviceId` TEXT NOT NULL,
                        `remoteDeviceId` TEXT NOT NULL,
                        `localActorName` TEXT NOT NULL,
                        `remoteActorName` TEXT NOT NULL,
                        `localSummary` TEXT NOT NULL,
                        `remoteSummary` TEXT NOT NULL,
                        `localPayloadJson` TEXT NOT NULL,
                        `remotePayloadJson` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `resolutionNotes` TEXT NOT NULL,
                        `resolvedByName` TEXT,
                        `resolvedAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`conflictId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sync_conflicts_branchId_status` ON `sync_conflicts` (`branchId`, `status`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `backup_snapshots` (
                        `backupId` TEXT NOT NULL,
                        `branchScope` TEXT NOT NULL,
                        `createdByUid` TEXT NOT NULL,
                        `createdByName` TEXT NOT NULL,
                        `createdByRole` TEXT NOT NULL,
                        `deviceId` TEXT NOT NULL,
                        `schemaVersion` INTEGER NOT NULL,
                        `checksumSha256` TEXT NOT NULL,
                        `recordCount` INTEGER NOT NULL,
                        `summaryJson` TEXT NOT NULL,
                        `localFilePath` TEXT NOT NULL,
                        `cloudPath` TEXT,
                        `cloudStatus` TEXT NOT NULL,
                        `integrityVerified` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`backupId`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recovery_logs` (
                        `recoveryId` TEXT NOT NULL,
                        `backupId` TEXT NOT NULL,
                        `actionType` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `actorUid` TEXT NOT NULL,
                        `actorName` TEXT NOT NULL,
                        `deviceId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`recoveryId`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `device_sessions` (
                        `deviceId` TEXT NOT NULL,
                        `deviceName` TEXT NOT NULL,
                        `osVersion` TEXT NOT NULL,
                        `appVersion` TEXT NOT NULL,
                        `operatorUid` TEXT NOT NULL,
                        `operatorEmail` TEXT NOT NULL,
                        `operatorRole` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `isAuthorized` INTEGER NOT NULL,
                        `isCurrentDevice` INTEGER NOT NULL,
                        `lastSeenAt` INTEGER NOT NULL,
                        `lastSyncAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`deviceId`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `erp_notifications` (
                        `notificationId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `severity` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `relatedEntityType` TEXT,
                        `relatedEntityId` TEXT,
                        `isRead` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`notificationId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_erp_notifications_branchId_isRead_createdAt` ON `erp_notifications` (`branchId`, `isRead`, `createdAt`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `role_permission_policies` (
                        `roleCode` TEXT NOT NULL,
                        `canTriggerCloudSync` INTEGER NOT NULL,
                        `canResolveSyncConflicts` INTEGER NOT NULL,
                        `canResolveFinancialConflicts` INTEGER NOT NULL,
                        `canCreateBackup` INTEGER NOT NULL,
                        `canRestoreBackup` INTEGER NOT NULL,
                        `canManageDevices` INTEGER NOT NULL,
                        `canManageUserPermissions` INTEGER NOT NULL,
                        `canApproveFinancialAdjustments` INTEGER NOT NULL,
                        `canExportReports` INTEGER NOT NULL,
                        `updatedBy` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`roleCode`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pos_orders` ADD COLUMN `loyaltyCardId` TEXT")
                db.execSQL("ALTER TABLE `pos_orders` ADD COLUMN `loyaltyCardNumber` TEXT")
                db.execSQL("ALTER TABLE `pos_orders` ADD COLUMN `loyaltyStampChange` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `pos_orders` ADD COLUMN `loyaltyStampsAfter` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `pos_orders` ADD COLUMN `loyaltyVoucherId` TEXT")
                db.execSQL("ALTER TABLE `pos_orders` ADD COLUMN `loyaltyVoucherCode` TEXT")
                db.execSQL("ALTER TABLE `pos_orders` ADD COLUMN `loyaltyVoucherDiscountCents` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `pos_orders` ADD COLUMN `loyaltyPromoCostCents` INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `loyalty_program_settings` (
                        `branchId` TEXT NOT NULL,
                        `programEnabled` INTEGER NOT NULL,
                        `minQualifyingAmountCents` INTEGER NOT NULL,
                        `requiredStampsForReward` INTEGER NOT NULL,
                        `rewardAmountCents` INTEGER NOT NULL,
                        `validityMonths` INTEGER NOT NULL,
                        `redemptionGraceDays` INTEGER NOT NULL,
                        `qualifyingCalculationMethod` TEXT NOT NULL,
                        `eligibleCategoryIdsCsv` TEXT NOT NULL,
                        `redeemableCategoryIdsCsv` TEXT NOT NULL,
                        `excludeTaxFromQualifying` INTEGER NOT NULL,
                        `excludeServiceAndDeliveryFromQualifying` INTEGER NOT NULL,
                        `allowPartialVoucherUsage` INTEGER NOT NULL,
                        `cardPrefix` TEXT NOT NULL,
                        `voucherPrefix` TEXT NOT NULL,
                        `updatedBy` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`branchId`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `loyalty_cards` (
                        `cardId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `customerId` TEXT NOT NULL,
                        `customerName` TEXT NOT NULL,
                        `customerPhone` TEXT NOT NULL,
                        `cardNumber` TEXT NOT NULL,
                        `qrCodeToken` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `issuedAt` INTEGER NOT NULL,
                        `firstQualifyingInvoiceAt` INTEGER,
                        `expiresAt` INTEGER,
                        `graceExpiresAt` INTEGER,
                        `currentCycleStamps` INTEGER NOT NULL,
                        `totalStampsEarned` INTEGER NOT NULL,
                        `totalRewardsEarned` INTEGER NOT NULL,
                        `totalRewardsRedeemed` INTEGER NOT NULL,
                        `cycleCount` INTEGER NOT NULL,
                        `replacesCardId` TEXT,
                        `replacedByCardId` TEXT,
                        `suspensionOrReplacementReason` TEXT,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`cardId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_loyalty_cards_cardNumber` ON `loyalty_cards` (`cardNumber`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_loyalty_cards_qrCodeToken` ON `loyalty_cards` (`qrCodeToken`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loyalty_cards_customerId` ON `loyalty_cards` (`customerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loyalty_cards_branchId_status` ON `loyalty_cards` (`branchId`, `status`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `loyalty_stamp_transactions` (
                        `stampTxId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `cardId` TEXT NOT NULL,
                        `customerId` TEXT NOT NULL,
                        `orderId` TEXT,
                        `invoiceNumber` TEXT,
                        `txType` TEXT NOT NULL,
                        `stampsDelta` INTEGER NOT NULL,
                        `stampsAfter` INTEGER NOT NULL,
                        `qualifyingAmountCents` INTEGER NOT NULL,
                        `cycleNumber` INTEGER NOT NULL,
                        `idempotencyKey` TEXT NOT NULL,
                        `uniqueOrderEarnKey` TEXT,
                        `reversedByTxId` TEXT,
                        `reason` TEXT NOT NULL,
                        `approvedBy` TEXT,
                        `actorUid` TEXT NOT NULL,
                        `actorName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`stampTxId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_loyalty_stamp_transactions_idempotencyKey` ON `loyalty_stamp_transactions` (`idempotencyKey`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_loyalty_stamp_transactions_uniqueOrderEarnKey` ON `loyalty_stamp_transactions` (`uniqueOrderEarnKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loyalty_stamp_transactions_cardId_createdAt` ON `loyalty_stamp_transactions` (`cardId`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loyalty_stamp_transactions_customerId_createdAt` ON `loyalty_stamp_transactions` (`customerId`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loyalty_stamp_transactions_orderId` ON `loyalty_stamp_transactions` (`orderId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `loyalty_reward_vouchers` (
                        `voucherId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `customerId` TEXT NOT NULL,
                        `cardId` TEXT NOT NULL,
                        `cycleNumber` INTEGER NOT NULL,
                        `voucherCode` TEXT NOT NULL,
                        `voucherReference` TEXT NOT NULL,
                        `qrCodeToken` TEXT NOT NULL,
                        `rewardValueCents` INTEGER NOT NULL,
                        `earnedFromOrderId` TEXT,
                        `earnedFromInvoiceNumber` TEXT,
                        `earnedStampTxId` TEXT NOT NULL,
                        `earnedAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER NOT NULL,
                        `graceExpiresAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `redeemedOrderId` TEXT,
                        `redeemedInvoiceNumber` TEXT,
                        `redeemedAt` INTEGER,
                        `redeemedAmountCents` INTEGER NOT NULL,
                        `actualPromoCostCents` INTEGER NOT NULL,
                        `cancelledReason` TEXT,
                        `notes` TEXT NOT NULL,
                        PRIMARY KEY(`voucherId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_loyalty_reward_vouchers_voucherCode` ON `loyalty_reward_vouchers` (`voucherCode`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_loyalty_reward_vouchers_qrCodeToken` ON `loyalty_reward_vouchers` (`qrCodeToken`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loyalty_reward_vouchers_customerId_status` ON `loyalty_reward_vouchers` (`customerId`, `status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loyalty_reward_vouchers_cardId` ON `loyalty_reward_vouchers` (`cardId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loyalty_reward_vouchers_earnedFromOrderId` ON `loyalty_reward_vouchers` (`earnedFromOrderId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `loyalty_redemptions` (
                        `redemptionId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `voucherId` TEXT NOT NULL,
                        `voucherCode` TEXT NOT NULL,
                        `cardId` TEXT NOT NULL,
                        `customerId` TEXT NOT NULL,
                        `orderId` TEXT NOT NULL,
                        `invoiceNumber` TEXT NOT NULL,
                        `voucherFaceValueCents` INTEGER NOT NULL,
                        `discountAppliedCents` INTEGER NOT NULL,
                        `actualCogsCostCents` INTEGER NOT NULL,
                        `eligibleSubtotalCents` INTEGER NOT NULL,
                        `redeemedByUid` TEXT NOT NULL,
                        `redeemedByName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`redemptionId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_loyalty_redemptions_voucherId` ON `loyalty_redemptions` (`voucherId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loyalty_redemptions_orderId` ON `loyalty_redemptions` (`orderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loyalty_redemptions_customerId` ON `loyalty_redemptions` (`customerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loyalty_redemptions_branchId_createdAt` ON `loyalty_redemptions` (`branchId`, `createdAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `loyalty_audit_logs` (
                        `logId` TEXT NOT NULL,
                        `branchId` TEXT NOT NULL,
                        `actorUid` TEXT NOT NULL,
                        `actorEmail` TEXT NOT NULL,
                        `actorRole` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `action` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `previousValueJson` TEXT NOT NULL,
                        `newValueJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`logId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loyalty_audit_logs_branchId_createdAt` ON `loyalty_audit_logs` (`branchId`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loyalty_audit_logs_entityId` ON `loyalty_audit_logs` (`entityId`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_profiles` ADD COLUMN `username` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `user_profiles` ADD COLUMN `staffId` TEXT")
                db.execSQL("ALTER TABLE `user_profiles` ADD COLUMN `passwordHash` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `user_profiles` ADD COLUMN `passwordSalt` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `user_profiles` ADD COLUMN `authProvider` TEXT NOT NULL DEFAULT 'FIREBASE_EMAIL'")
                db.execSQL("ALTER TABLE `user_profiles` ADD COLUMN `mustResetPassword` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_profiles_username` ON `user_profiles` (`username`)")
            }
        }

        fun getInstance(context: Context): AaCraveDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AaCraveDatabase::class.java,
                    "aa_crave_erp.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
