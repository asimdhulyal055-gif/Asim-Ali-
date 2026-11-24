package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BranchDao {
    @Query("SELECT * FROM branches ORDER BY isMainBranch DESC, name ASC")
    fun observeBranches(): Flow<List<BranchEntity>>

    @Query("SELECT * FROM branches ORDER BY isMainBranch DESC, name ASC")
    suspend fun getAllBranches(): List<BranchEntity>

    @Query("SELECT * FROM branches WHERE branchId = :branchId LIMIT 1")
    suspend fun getBranchById(branchId: String): BranchEntity?

    @Query("SELECT * FROM branches WHERE UPPER(code) = UPPER(:code) LIMIT 1")
    suspend fun getBranchByCode(code: String): BranchEntity?

    @Query("SELECT COUNT(*) FROM branches")
    suspend fun countBranches(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(branches: List<BranchEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBranch(branch: BranchEntity)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles ORDER BY displayName ASC")
    fun observeAllProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles WHERE uid = :uid LIMIT 1")
    suspend fun getProfileByUid(uid: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getProfileByEmail(email: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE LOWER(username) = LOWER(:username) AND username != '' LIMIT 1")
    suspend fun getProfileByUsername(username: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE staffId = :staffId LIMIT 1")
    suspend fun getProfileByStaffId(staffId: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profiles SET branchId = :branchId WHERE uid = :uid")
    suspend fun updateUserBranch(uid: String, branchId: String)

    @Query("DELETE FROM user_profiles WHERE uid = :uid")
    suspend fun deleteProfileByUid(uid: String)
}

@Dao
interface DashboardDao {
    @Query("SELECT * FROM dashboard_snapshots WHERE branchId = :branchId LIMIT 1")
    fun observeSnapshot(branchId: String): Flow<DashboardSnapshotEntity?>

    @Query("SELECT * FROM hourly_sales WHERE branchId = :branchId ORDER BY hourOrder ASC")
    fun observeHourlySales(branchId: String): Flow<List<HourlySalesEntity>>

    @Query("SELECT * FROM low_stock_alerts WHERE branchId = :branchId ORDER BY currentStock ASC")
    fun observeLowStockAlerts(branchId: String): Flow<List<LowStockAlertEntity>>

    @Query("SELECT * FROM recent_transactions WHERE branchId = :branchId ORDER BY createdAt DESC LIMIT 10")
    fun observeRecentTransactions(branchId: String): Flow<List<TransactionRecordEntity>>

    @Query("SELECT COUNT(*) FROM dashboard_snapshots")
    suspend fun countSnapshots(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshots(snapshots: List<DashboardSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHourlySales(items: List<HourlySalesEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLowStockAlerts(items: List<LowStockAlertEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(items: List<TransactionRecordEntity>)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun observeRecentLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT COUNT(*) FROM audit_logs")
    fun observeLogCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: AuditLogEntity)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status != 'SYNCED'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM sync_queue ORDER BY createdAt DESC LIMIT 50")
    fun observeQueue(): Flow<List<SyncQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueUnique(item: SyncQueueEntity)
}

@Dao
interface PosDao {
    @Query("SELECT * FROM product_categories WHERE branchId = :branchId AND isActive = 1 ORDER BY sortOrder ASC")
    fun observeCategories(branchId: String): Flow<List<ProductCategoryEntity>>

    @Query("SELECT * FROM products WHERE branchId = :branchId ORDER BY name ASC")
    fun observeProducts(branchId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM product_variations")
    fun observeAllVariations(): Flow<List<ProductVariationEntity>>

    @Query("SELECT * FROM product_addons")
    fun observeAllAddons(): Flow<List<ProductAddonEntity>>

    @Query("SELECT * FROM customers WHERE branchId = :branchId ORDER BY name ASC")
    fun observeCustomers(branchId: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM pos_orders WHERE branchId = :branchId ORDER BY createdAt DESC")
    fun observeOrders(branchId: String): Flow<List<PosOrderEntity>>

    @Query("SELECT * FROM order_items")
    fun observeAllOrderItems(): Flow<List<OrderItemEntity>>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun getItemsForOrder(orderId: String): List<OrderItemEntity>

    @Query("SELECT * FROM kot_tickets WHERE branchId = :branchId ORDER BY createdAt DESC")
    fun observeKotTickets(branchId: String): Flow<List<KotTicketEntity>>

    @Query("SELECT * FROM kot_tickets WHERE orderId = :orderId LIMIT 1")
    suspend fun getKotByOrderId(orderId: String): KotTicketEntity?

    @Query("SELECT * FROM kot_tickets WHERE orderId = :orderId")
    suspend fun getKotsByOrderId(orderId: String): List<KotTicketEntity>

    @Query("SELECT * FROM kot_tickets WHERE kotId = :kotId LIMIT 1")
    suspend fun getKotById(kotId: String): KotTicketEntity?

    @Query(
        """
        UPDATE kot_tickets
        SET status = :status,
            startedPreparingAt = COALESCE(:startedAt, startedPreparingAt),
            readyAt = COALESCE(:readyAt, readyAt),
            completedAt = COALESCE(:completedAt, completedAt)
        WHERE kotId = :kotId
        """
    )
    suspend fun updateKotStage(
        kotId: String,
        status: String,
        startedAt: Long?,
        readyAt: Long?,
        completedAt: Long?
    )

    @Query("SELECT * FROM daily_cash_closings WHERE branchId = :branchId ORDER BY closedAt DESC")
    fun observeCashClosings(branchId: String): Flow<List<DailyCashClosingEntity>>

    @Query("SELECT * FROM pos_orders WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getOrderByIdempotencyKey(key: String): PosOrderEntity?

    @Query("SELECT * FROM pos_orders WHERE orderId = :orderId LIMIT 1")
    suspend fun getOrderById(orderId: String): PosOrderEntity?

    @Query("SELECT COUNT(*) FROM pos_orders WHERE branchId = :branchId")
    suspend fun countOrdersForBranch(branchId: String): Int

    @Query("SELECT COUNT(*) FROM products")
    suspend fun countProducts(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(items: List<ProductCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(item: ProductCategoryEntity)

    @Query("DELETE FROM product_categories WHERE categoryId = :categoryId")
    suspend fun deleteCategory(categoryId: String)

    @Query("SELECT COUNT(*) FROM products WHERE categoryId = :categoryId AND isArchived = 0")
    suspend fun countActiveProductsInCategory(categoryId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(items: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(item: ProductEntity)

    @Query("UPDATE products SET isArchived = :archived, isAvailable = :available WHERE productId = :productId")
    suspend fun setProductArchiveState(productId: String, archived: Boolean, available: Boolean)

    @Query("UPDATE products SET isAvailable = :available WHERE productId = :productId")
    suspend fun setProductAvailability(productId: String, available: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVariations(items: List<ProductVariationEntity>)

    @Query("DELETE FROM product_variations WHERE variationId = :variationId")
    suspend fun deleteVariation(variationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAddons(items: List<ProductAddonEntity>)

    @Query("DELETE FROM product_addons WHERE addonId = :addonId")
    suspend fun deleteAddon(addonId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomers(items: List<CustomerEntity>)

    @Query("SELECT * FROM customers WHERE customerId = :customerId LIMIT 1")
    suspend fun getCustomerById(customerId: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrder(order: PosOrderEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKotTicket(ticket: KotTicketEntity)

    @Query("UPDATE pos_orders SET status = :status, approvedBy = :approvedBy, cancellationReason = :reason WHERE orderId = :orderId")
    suspend fun updateOrderStatus(orderId: String, status: String, approvedBy: String?, reason: String?)

    @Query("UPDATE pos_orders SET status = :status WHERE orderId = :orderId")
    suspend fun syncOrderStatusFromKds(orderId: String, status: String)

    @Query("UPDATE kot_tickets SET status = :status WHERE orderId = :orderId")
    suspend fun updateKotStatusByOrderId(orderId: String, status: String)

    @Query("UPDATE pos_orders SET reprintInvoiceCount = reprintInvoiceCount + 1 WHERE orderId = :orderId")
    suspend fun incrementInvoiceReprint(orderId: String)

    @Query("UPDATE pos_orders SET reprintKotCount = reprintKotCount + 1 WHERE orderId = :orderId")
    suspend fun incrementKotReprint(orderId: String)

    @Query("UPDATE kot_tickets SET reprintCount = reprintCount + 1 WHERE orderId = :orderId")
    suspend fun incrementKotTicketReprint(orderId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashClosing(closing: DailyCashClosingEntity)
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes WHERE branchId = :branchId ORDER BY updatedAt DESC")
    fun observeRecipes(branchId: String): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipe_ingredients")
    fun observeAllRecipeIngredients(): Flow<List<RecipeIngredientEntity>>

    @Query("SELECT * FROM recipe_versions ORDER BY versionNumber DESC")
    fun observeAllRecipeVersions(): Flow<List<RecipeVersionEntity>>

    @Query("SELECT * FROM recipes WHERE productId = :productId LIMIT 1")
    suspend fun getRecipeForProduct(productId: String): RecipeEntity?

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun getIngredientsForRecipe(recipeId: String): List<RecipeIngredientEntity>

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun countRecipes(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecipe(recipe: RecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(items: List<RecipeIngredientEntity>)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredientsForRecipe(recipeId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: RecipeVersionEntity)
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items WHERE branchId = :branchId ORDER BY name ASC")
    fun observeInventoryItems(branchId: String): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM stock_movements WHERE branchId = :branchId ORDER BY createdAt DESC LIMIT 150")
    fun observeStockMovements(branchId: String): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM physical_audits WHERE branchId = :branchId ORDER BY auditedAt DESC LIMIT 50")
    fun observePhysicalAudits(branchId: String): Flow<List<PhysicalAuditEntity>>

    @Query("SELECT * FROM inventory_items WHERE itemId = :itemId LIMIT 1")
    suspend fun getItemById(itemId: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE linkedProductId = :productId LIMIT 1")
    suspend fun getItemByLinkedProductId(productId: String): InventoryItemEntity?

    @Query("SELECT COUNT(*) FROM inventory_items")
    suspend fun countInventoryItems(): Int

    @Query("SELECT COUNT(*) FROM inventory_items WHERE branchId = :branchId AND currentQty <= reorderLevel")
    suspend fun countLowStockItems(branchId: String): Int

    @Query("SELECT * FROM inventory_items WHERE branchId = :branchId AND currentQty <= reorderLevel")
    suspend fun getLowStockItems(branchId: String): List<InventoryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<InventoryItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: InventoryItemEntity)

    @Query("UPDATE inventory_items SET currentQty = :newQty, expiryDateMillis = COALESCE(:expiryMillis, expiryDateMillis), updatedAt = :updatedAt WHERE itemId = :itemId")
    suspend fun updateStockQuantity(itemId: String, newQty: Double, expiryMillis: Long?, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMovementIfNew(movement: StockMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhysicalAudit(audit: PhysicalAuditEntity)

    @Query("DELETE FROM low_stock_alerts WHERE branchId = :branchId")
    suspend fun clearLowStockAlertsForBranch(branchId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLowStockAlerts(alerts: List<LowStockAlertEntity>)
}

// Phase 4 DAOs

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers WHERE branchId = :branchId ORDER BY name ASC")
    fun observeSuppliers(branchId: String): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM supplier_payments WHERE branchId = :branchId ORDER BY paymentDateMillis DESC, createdAt DESC")
    fun observeSupplierPayments(branchId: String): Flow<List<SupplierPaymentEntity>>

    @Query("SELECT * FROM suppliers WHERE supplierId = :supplierId LIMIT 1")
    suspend fun getSupplierById(supplierId: String): SupplierEntity?

    @Query("SELECT * FROM suppliers")
    suspend fun getAllSuppliersForAccounting(): List<SupplierEntity>

    @Query("SELECT COUNT(*) FROM suppliers")
    suspend fun countSuppliers(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSuppliers(suppliers: List<SupplierEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSupplier(supplier: SupplierEntity)

    @Query("SELECT * FROM supplier_payments WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getPaymentByIdempotencyKey(key: String): SupplierPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPaymentIfNew(payment: SupplierPaymentEntity): Long
}

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchase_orders WHERE branchId = :branchId ORDER BY createdAt DESC")
    fun observePurchaseOrders(branchId: String): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_items")
    fun observeAllPurchaseItems(): Flow<List<PurchaseItemEntity>>

    @Query("SELECT * FROM purchase_receivings WHERE branchId = :branchId ORDER BY receivedDateMillis DESC")
    fun observeReceivings(branchId: String): Flow<List<PurchaseReceivingEntity>>

    @Query("SELECT * FROM purchase_returns WHERE branchId = :branchId ORDER BY returnedAt DESC")
    fun observeReturns(branchId: String): Flow<List<PurchaseReturnEntity>>

    @Query("SELECT * FROM purchase_orders WHERE purchaseId = :purchaseId LIMIT 1")
    suspend fun getPurchaseById(purchaseId: String): PurchaseOrderEntity?

    @Query("SELECT * FROM purchase_orders WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getPurchaseByIdempotencyKey(key: String): PurchaseOrderEntity?

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun getItemsForPurchase(purchaseId: String): List<PurchaseItemEntity>

    @Query("SELECT * FROM purchase_items WHERE lineId = :lineId LIMIT 1")
    suspend fun getPurchaseItemById(lineId: String): PurchaseItemEntity?

    @Query("SELECT COUNT(*) FROM purchase_orders WHERE branchId = :branchId")
    suspend fun countPurchasesForBranch(branchId: String): Int

    @Query("SELECT COUNT(*) FROM purchase_orders")
    suspend fun countAllPurchases(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPurchaseOrder(po: PurchaseOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPurchaseItems(items: List<PurchaseItemEntity>)

    @Query("DELETE FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun deleteItemsForPurchase(purchaseId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReceivingIfNew(receiving: PurchaseReceivingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturn(ret: PurchaseReturnEntity)
}

@Dao
interface CustomerLoyaltyDao {
    @Query("SELECT * FROM customers WHERE branchId = :branchId ORDER BY name ASC")
    fun observeCustomers(branchId: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE branchId = :branchId")
    suspend fun getCustomersForBranch(branchId: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE customerId = :customerId LIMIT 1")
    suspend fun getCustomerById(customerId: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomer(customer: CustomerEntity)

    @Query("SELECT * FROM loyalty_config WHERE branchId = :branchId LIMIT 1")
    fun observeLoyaltyConfig(branchId: String): Flow<LoyaltyConfigEntity?>

    @Query("SELECT * FROM loyalty_config WHERE branchId = :branchId LIMIT 1")
    suspend fun getLoyaltyConfig(branchId: String): LoyaltyConfigEntity?

    @Query("SELECT COUNT(*) FROM loyalty_config")
    suspend fun countConfigs(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLoyaltyConfig(config: LoyaltyConfigEntity)

    @Query("SELECT * FROM loyalty_rewards WHERE branchId = :branchId ORDER BY pointsRequired ASC")
    fun observeLoyaltyRewards(branchId: String): Flow<List<LoyaltyRewardEntity>>

    @Query("SELECT COUNT(*) FROM loyalty_rewards")
    suspend fun countRewards(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRewards(rewards: List<LoyaltyRewardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReward(reward: LoyaltyRewardEntity)

    @Query("DELETE FROM loyalty_rewards WHERE rewardId = :rewardId")
    suspend fun deleteReward(rewardId: String)

    @Query("SELECT * FROM loyalty_transactions WHERE branchId = :branchId ORDER BY createdAt DESC LIMIT 200")
    fun observeLoyaltyTransactions(branchId: String): Flow<List<LoyaltyTransactionEntity>>

    @Query("SELECT * FROM loyalty_transactions WHERE customerId = :customerId ORDER BY createdAt DESC")
    suspend fun getLoyaltyTransactionsForCustomer(customerId: String): List<LoyaltyTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLoyaltyTransactionIfNew(tx: LoyaltyTransactionEntity): Long

    @Query("SELECT * FROM customer_feedback WHERE branchId = :branchId ORDER BY createdAt DESC LIMIT 150")
    fun observeFeedback(branchId: String): Flow<List<CustomerFeedbackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: CustomerFeedbackEntity)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE branchId = :branchId ORDER BY expenseDateMillis DESC, createdAt DESC")
    fun observeExpenses(branchId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE expenseId = :expenseId LIMIT 1")
    suspend fun getExpenseById(expenseId: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getExpenseByIdempotencyKey(key: String): ExpenseEntity?

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun countExpenses(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpenses(items: List<ExpenseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(item: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE expenseId = :expenseId")
    suspend fun deleteExpense(expenseId: String)
}

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff_profiles WHERE branchId = :branchId ORDER BY fullName ASC")
    fun observeStaffForBranch(branchId: String): Flow<List<StaffProfileEntity>>

    @Query("SELECT * FROM staff_profiles ORDER BY fullName ASC")
    fun observeAllStaff(): Flow<List<StaffProfileEntity>>

    @Query("SELECT * FROM staff_profiles WHERE staffId = :staffId LIMIT 1")
    suspend fun getStaffById(staffId: String): StaffProfileEntity?

    @Query("SELECT * FROM staff_profiles WHERE UPPER(employeeCode) = UPPER(:employeeCode) LIMIT 1")
    suspend fun getStaffByEmployeeCode(employeeCode: String): StaffProfileEntity?

    @Query("SELECT * FROM staff_profiles WHERE branchId = :branchId")
    suspend fun getStaffListForBranch(branchId: String): List<StaffProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStaff(staff: StaffProfileEntity)

    @Query("SELECT * FROM staff_documents WHERE staffId = :staffId ORDER BY uploadedAt DESC")
    fun observeDocumentsForStaff(staffId: String): Flow<List<StaffDocumentEntity>>

    @Query("SELECT * FROM staff_documents WHERE branchId = :branchId ORDER BY uploadedAt DESC")
    fun observeDocumentsForBranch(branchId: String): Flow<List<StaffDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(doc: StaffDocumentEntity)

    @Query("DELETE FROM staff_documents WHERE docId = :docId")
    suspend fun deleteDocument(docId: String)

    @Query("SELECT * FROM hr_permission_policies ORDER BY roleCode ASC")
    fun observeHrPermissionPolicies(): Flow<List<HrPermissionPolicyEntity>>

    @Query("SELECT * FROM hr_permission_policies WHERE roleCode = :roleCode LIMIT 1")
    suspend fun getHrPermissionPolicy(roleCode: String): HrPermissionPolicyEntity?

    @Query("SELECT COUNT(*) FROM hr_permission_policies")
    suspend fun countHrPolicies(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHrPolicies(policies: List<HrPermissionPolicyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHrPolicy(policy: HrPermissionPolicyEntity)
}

@Dao
interface ShiftAttendanceDao {
    @Query("SELECT * FROM shift_definitions WHERE branchId = :branchId ORDER BY startMinuteOfDay ASC")
    fun observeShifts(branchId: String): Flow<List<ShiftDefinitionEntity>>

    @Query("SELECT * FROM shift_definitions WHERE shiftId = :shiftId LIMIT 1")
    suspend fun getShiftById(shiftId: String): ShiftDefinitionEntity?

    @Query("SELECT COUNT(*) FROM shift_definitions")
    suspend fun countShifts(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShifts(shifts: List<ShiftDefinitionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShift(shift: ShiftDefinitionEntity)

    @Query("DELETE FROM shift_definitions WHERE shiftId = :shiftId")
    suspend fun deleteShift(shiftId: String)

    @Query("SELECT * FROM shift_assignments WHERE branchId = :branchId ORDER BY dayOfWeek ASC, createdAt DESC")
    fun observeShiftAssignments(branchId: String): Flow<List<ShiftAssignmentEntity>>

    @Query("SELECT * FROM shift_assignments WHERE branchId = :branchId")
    suspend fun getAssignmentsForBranch(branchId: String): List<ShiftAssignmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignment(assignment: ShiftAssignmentEntity)

    @Query("DELETE FROM shift_assignments WHERE assignmentId = :assignmentId")
    suspend fun deleteAssignment(assignmentId: String)

    @Query("DELETE FROM shift_assignments WHERE shiftId = :shiftId")
    suspend fun deleteAssignmentsForShift(shiftId: String)

    @Query("SELECT * FROM attendance_records WHERE branchId = :branchId ORDER BY attendanceDateMillis DESC, updatedAt DESC")
    fun observeAttendance(branchId: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE staffId = :staffId AND attendanceDateStr = :dateStr LIMIT 1")
    suspend fun getAttendanceForStaffDate(staffId: String, dateStr: String): AttendanceRecordEntity?

    @Query("SELECT * FROM attendance_records WHERE attendanceId = :attendanceId LIMIT 1")
    suspend fun getAttendanceById(attendanceId: String): AttendanceRecordEntity?

    @Query("SELECT * FROM attendance_records WHERE branchId = :branchId AND attendanceDateMillis BETWEEN :startMillis AND :endMillis ORDER BY attendanceDateMillis ASC")
    suspend fun getAttendanceInRange(branchId: String, startMillis: Long, endMillis: Long): List<AttendanceRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendance(record: AttendanceRecordEntity)
}

@Dao
interface LeavePayrollDao {
    @Query("SELECT * FROM leave_requests WHERE branchId = :branchId ORDER BY createdAt DESC")
    fun observeLeaveRequests(branchId: String): Flow<List<LeaveRequestEntity>>

    @Query("SELECT * FROM leave_requests WHERE leaveId = :leaveId LIMIT 1")
    suspend fun getLeaveById(leaveId: String): LeaveRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLeaveRequest(leave: LeaveRequestEntity)

    @Query("SELECT * FROM payroll_records WHERE branchId = :branchId ORDER BY periodStartMillis DESC, employeeCode ASC")
    fun observePayrollRecords(branchId: String): Flow<List<PayrollRecordEntity>>

    @Query("SELECT * FROM payroll_records WHERE payrollId = :payrollId LIMIT 1")
    suspend fun getPayrollById(payrollId: String): PayrollRecordEntity?

    @Query("SELECT * FROM payroll_records WHERE staffId = :staffId AND periodKey = :periodKey LIMIT 1")
    suspend fun getPayrollByStaffAndPeriod(staffId: String, periodKey: String): PayrollRecordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayrollStrict(payroll: PayrollRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updatePayroll(payroll: PayrollRecordEntity)

    @Query("SELECT * FROM payroll_records ORDER BY periodStartMillis DESC, employeeCode ASC")
    fun observeAllPayrolls(): Flow<List<PayrollRecordEntity>>

    @Query("SELECT * FROM payroll_records")
    suspend fun getAllPayrolls(): List<PayrollRecordEntity>
}

@Dao
interface AccountingDao {
    @Query("SELECT * FROM chart_of_accounts WHERE branchId = :branchId ORDER BY accountCode ASC")
    fun observeChartOfAccounts(branchId: String): Flow<List<ChartOfAccountEntity>>

    @Query("SELECT * FROM chart_of_accounts ORDER BY branchId ASC, accountCode ASC")
    fun observeAllChartOfAccounts(): Flow<List<ChartOfAccountEntity>>

    @Query("SELECT * FROM chart_of_accounts WHERE branchId = :branchId ORDER BY accountCode ASC")
    suspend fun getAccountsForBranch(branchId: String): List<ChartOfAccountEntity>

    @Query("SELECT * FROM chart_of_accounts ORDER BY branchId ASC, accountCode ASC")
    suspend fun getAllAccounts(): List<ChartOfAccountEntity>

    @Query("SELECT * FROM chart_of_accounts WHERE accountId = :accountId LIMIT 1")
    suspend fun getAccountById(accountId: String): ChartOfAccountEntity?

    @Query("SELECT * FROM chart_of_accounts WHERE branchId = :branchId AND accountCode = :code LIMIT 1")
    suspend fun getAccountByCode(branchId: String, code: String): ChartOfAccountEntity?

    @Query("SELECT COUNT(*) FROM chart_of_accounts WHERE branchId = :branchId")
    suspend fun countAccountsForBranch(branchId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccountsIfNew(accounts: List<ChartOfAccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: ChartOfAccountEntity)

    @Query("SELECT * FROM journal_entries WHERE branchId = :branchId ORDER BY entryDateMillis DESC, createdAt DESC")
    fun observeJournalEntries(branchId: String): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries ORDER BY entryDateMillis DESC, createdAt DESC")
    fun observeAllJournalEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_lines WHERE branchId = :branchId ORDER BY entryDateMillis ASC")
    fun observeJournalLines(branchId: String): Flow<List<JournalLineEntity>>

    @Query("SELECT * FROM journal_lines ORDER BY entryDateMillis ASC")
    fun observeAllJournalLines(): Flow<List<JournalLineEntity>>

    @Query("SELECT * FROM journal_entries WHERE entryId = :entryId LIMIT 1")
    suspend fun getJournalEntryById(entryId: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun getJournalEntryBySourceKey(sourceKey: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_lines WHERE entryId = :entryId")
    suspend fun getLinesForEntry(entryId: String): List<JournalLineEntity>

    @Query("SELECT COUNT(*) FROM journal_entries WHERE branchId = :branchId")
    suspend fun countJournalsForBranch(branchId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertJournalEntryIfNew(entry: JournalEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateJournalEntry(entry: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalLines(lines: List<JournalLineEntity>)

    @Query("SELECT * FROM cashbook_entries WHERE branchId = :branchId ORDER BY txDateMillis DESC, createdAt DESC")
    fun observeCashbookEntries(branchId: String): Flow<List<CashbookEntryEntity>>

    @Query("SELECT * FROM cashbook_entries ORDER BY txDateMillis DESC, createdAt DESC")
    fun observeAllCashbookEntries(): Flow<List<CashbookEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCashbookEntryIfNew(entry: CashbookEntryEntity): Long

    // Consolidated Phase 6 cross-module queries
    @Query("SELECT * FROM pos_orders ORDER BY createdAt DESC")
    fun observeAllPosOrders(): Flow<List<PosOrderEntity>>

    @Query("SELECT * FROM pos_orders")
    suspend fun getAllPosOrders(): List<PosOrderEntity>

    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun observeAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun observeAllSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM purchase_orders ORDER BY createdAt DESC")
    fun observeAllPurchaseOrders(): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_orders")
    suspend fun getAllPurchaseOrders(): List<PurchaseOrderEntity>

    @Query("SELECT * FROM purchase_returns ORDER BY returnedAt DESC")
    fun observeAllPurchaseReturns(): Flow<List<PurchaseReturnEntity>>

    @Query("SELECT * FROM purchase_returns")
    suspend fun getAllPurchaseReturns(): List<PurchaseReturnEntity>

    @Query("SELECT * FROM supplier_payments ORDER BY paymentDateMillis DESC, createdAt DESC")
    fun observeAllSupplierPayments(): Flow<List<SupplierPaymentEntity>>

    @Query("SELECT * FROM supplier_payments")
    suspend fun getAllSupplierPayments(): List<SupplierPaymentEntity>

    @Query("SELECT * FROM expenses ORDER BY expenseDateMillis DESC, createdAt DESC")
    fun observeAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses")
    suspend fun getAllExpenses(): List<ExpenseEntity>
}




