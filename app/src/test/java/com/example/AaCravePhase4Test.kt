package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AaCraveDatabase
import com.example.data.local.DatabaseSeeder
import com.example.data.repository.CustomerLoyaltyRepository
import com.example.data.repository.ExpenseRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.PosRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.model.BillingCalculator
import com.example.domain.model.CartItem
import com.example.domain.model.ExpenseApprovalStatus
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.ExpenseRecurrence
import com.example.domain.model.OrderPriority
import com.example.domain.model.OrderType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PurchaseDraftLine
import com.example.domain.model.PurchasePaymentStatus
import com.example.domain.model.PurchaseStatus
import com.example.domain.model.SplitPaymentBreakdown
import com.example.domain.model.StockMovementType
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.printing.Phase4ReportExporter
import com.example.printing.ReportExportFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AaCravePhase4Test {

    private lateinit var context: Context
    private lateinit var database: AaCraveDatabase
    private lateinit var supplierRepository: SupplierRepository
    private lateinit var purchaseRepository: PurchaseRepository
    private lateinit var customerLoyaltyRepository: CustomerLoyaltyRepository
    private lateinit var expenseRepository: ExpenseRepository
    private lateinit var posRepository: PosRepository
    private lateinit var inventoryRepository: InventoryRepository

    private val managerSession = UserSession(
        uid = "mgr_p4",
        email = "manager@aacrave.local",
        displayName = "Sara Manager",
        role = UserRole.BRANCH_MANAGER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    private val purchaseStaffSession = UserSession(
        uid = "pstaff_p4",
        email = "purchasing@aacrave.local",
        displayName = "Bilal Buyer",
        role = UserRole.PURCHASE_STAFF,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    private val cashierSession = UserSession(
        uid = "cashier_p4",
        email = "cashier@aacrave.local",
        displayName = "Ali Cashier",
        role = UserRole.CASHIER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AaCraveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseSeeder(database).ensureSeeded()
        supplierRepository = SupplierRepository(database)
        purchaseRepository = PurchaseRepository(database)
        customerLoyaltyRepository = CustomerLoyaltyRepository(database)
        expenseRepository = ExpenseRepository(context, database)
        posRepository = PosRepository(database)
        inventoryRepository = InventoryRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `purchase workflow supports partial and full receiving idempotent stock credits and returns`() = runTest {
        val beforeExpenses = expenseRepository.observeExpenses(DatabaseSeeder.MAIN_BRANCH_ID).first().size
        val cheddarBefore = inventoryRepository.observeInventoryItems(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.itemId == "inv_m_cheddar" }.currentQty

        // Purchase staff creates Pending PO (can create, cannot approve)
        val createdPo = purchaseRepository.createOrUpdatePurchaseOrder(
            session = purchaseStaffSession,
            purchaseId = null,
            idempotencyKey = "idem_po_test_1",
            supplierId = "sup_m_dairy",
            supplierInvoiceNumber = "INV-DAIRY-77",
            draftLines = listOf(
                PurchaseDraftLine(
                    inventoryItemId = "inv_m_cheddar",
                    itemName = "Aged Cheddar Cheese",
                    itemType = "RAW_MATERIAL",
                    unit = "kg",
                    orderedQty = 10.0,
                    unitPriceCents = 1450L
                )
            ),
            discountCents = 500L,
            taxCents = 700L,
            notes = "Urgent dairy restock",
            submitAsPending = true
        ).getOrThrow()

        assertEquals(PurchaseStatus.PENDING, createdPo.status)
        assertEquals(14700L, createdPo.totalCents) // 14500 - 500 + 700

        // Purchase staff CANNOT approve PO
        val deniedApprove = purchaseRepository.approvePurchaseOrder(purchaseStaffSession, createdPo.purchaseId)
        assertTrue(deniedApprove.isFailure)
        assertTrue(deniedApprove.exceptionOrNull() is SecurityException)

        // Manager approves PO
        purchaseRepository.approvePurchaseOrder(managerSession, createdPo.purchaseId).getOrThrow()

        // Partial receiving of 4.0 kg out of 10.0 kg -> status MUST be PARTIALLY_RECEIVED
        val lineId = createdPo.items.first().lineId
        val partialPo = purchaseRepository.receiveGoods(
            session = purchaseStaffSession,
            purchaseId = createdPo.purchaseId,
            receivingKey = "recv_key_partial_1",
            referenceNumber = "GRN-5001",
            receivedDateMillis = System.currentTimeMillis(),
            lineQuantities = mapOf(lineId to 4.0),
            notes = "First chilled van drop"
        ).getOrThrow()
        assertEquals(PurchaseStatus.PARTIALLY_RECEIVED, partialPo.status)

        // Retry with same receivingKey -> idempotent, NO duplicate stock addition
        purchaseRepository.receiveGoods(
            session = purchaseStaffSession,
            purchaseId = createdPo.purchaseId,
            receivingKey = "recv_key_partial_1",
            referenceNumber = "GRN-5001",
            receivedDateMillis = System.currentTimeMillis(),
            lineQuantities = mapOf(lineId to 4.0),
            notes = "Retry duplicate"
        ).getOrThrow()

        var cheddarNow = inventoryRepository.observeInventoryItems(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.itemId == "inv_m_cheddar" }.currentQty
        assertEquals(cheddarBefore + 4.0, cheddarNow, 0.001)

        // Remaining 6.0 kg received -> status becomes RECEIVED
        val fullPo = purchaseRepository.receiveGoods(
            session = purchaseStaffSession,
            purchaseId = createdPo.purchaseId,
            receivingKey = "recv_key_final_2",
            referenceNumber = "GRN-5002",
            receivedDateMillis = System.currentTimeMillis(),
            lineQuantities = mapOf(lineId to 6.0),
            notes = "Second delivery"
        ).getOrThrow()
        assertEquals(PurchaseStatus.RECEIVED, fullPo.status)

        cheddarNow = inventoryRepository.observeInventoryItems(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.itemId == "inv_m_cheddar" }.currentQty
        assertEquals(cheddarBefore + 10.0, cheddarNow, 0.001)

        // Return 2.0 kg damaged -> stock decreases by 2.0 and supplier returns increase by 2900c
        val ret = purchaseRepository.returnPurchaseGoods(
            session = managerSession,
            purchaseId = createdPo.purchaseId,
            lineId = lineId,
            returnQty = 2.0,
            reason = "Damaged vacuum seal"
        ).getOrThrow()
        assertEquals(2900L, ret.returnAmountCents)

        cheddarNow = inventoryRepository.observeInventoryItems(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.itemId == "inv_m_cheddar" }.currentQty
        assertEquals(cheddarBefore + 8.0, cheddarNow, 0.001)

        // Verify stock movement history recorded receiving
        val movements = inventoryRepository.observeStockMovements(DatabaseSeeder.MAIN_BRANCH_ID).first()
        assertTrue(movements.any { it.movementType == StockMovementType.RECEIVING && it.referenceOrderId == createdPo.poNumber })

        // Verify strict financial separation: purchasing/receiving/returning did NOT create operating expenses
        val afterExpenses = expenseRepository.observeExpenses(DatabaseSeeder.MAIN_BRANCH_ID).first().size
        assertEquals(beforeExpenses, afterExpenses)
    }

    @Test
    fun `supplier payments prevent duplicates track balances and enforce rbac and expense separation`() = runTest {
        val beforeExpenses = expenseRepository.observeExpenses(DatabaseSeeder.MAIN_BRANCH_ID).first().size
        val supplier = supplierRepository.saveSupplier(
            session = purchaseStaffSession,
            supplierId = null,
            name = "Fresh Produce Farms",
            contactPerson = "Nadia Farm",
            phone = "+1-555-7788",
            email = "orders@freshfarms.example.com",
            address = "90 Valley Rd",
            category = "Dairy & Produce",
            openingBalanceCents = 10000L
        ).getOrThrow()
        assertEquals(10000L, supplier.outstandingBalanceCents)

        // Purchase staff and cashier cannot pay suppliers
        val deniedStaff = supplierRepository.recordSupplierPayment(
            session = purchaseStaffSession,
            idempotencyKey = "spay_denied_1",
            supplierId = supplier.supplierId,
            purchaseId = null,
            amountCents = 4000L,
            paymentMethod = "Bank Transfer",
            referenceNumber = "TX-900",
            paymentDateMillis = System.currentTimeMillis(),
            notes = "Unauthorized test"
        )
        assertTrue(deniedStaff.isFailure)
        assertTrue(deniedStaff.exceptionOrNull() is SecurityException)

        // Manager records payment on seeded partially paid PO (po_m_seed_1: total 42750, paid 30000 -> remaining 12750)
        supplierRepository.recordSupplierPayment(
            session = managerSession,
            idempotencyKey = "spay_idemp_1",
            supplierId = "sup_m_butcher",
            purchaseId = "po_m_seed_1",
            amountCents = 12750L,
            paymentMethod = "Bank Transfer",
            referenceNumber = "ACH-FINAL-1",
            paymentDateMillis = System.currentTimeMillis(),
            notes = "Settle remainder"
        ).getOrThrow()

        // Duplicate submission with same idempotencyKey does NOT double-credit paid total
        supplierRepository.recordSupplierPayment(
            session = managerSession,
            idempotencyKey = "spay_idemp_1",
            supplierId = "sup_m_butcher",
            purchaseId = "po_m_seed_1",
            amountCents = 12750L,
            paymentMethod = "Bank Transfer",
            referenceNumber = "ACH-FINAL-1",
            paymentDateMillis = System.currentTimeMillis(),
            notes = "Retry duplicate"
        ).getOrThrow()

        val updatedSup = supplierRepository.observeSuppliers(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.supplierId == "sup_m_butcher" }
        assertEquals(42750L, updatedSup.totalPaidCents)
        assertEquals(0L, updatedSup.outstandingBalanceCents)

        val updatedPo = purchaseRepository.observePurchaseOrders(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.purchaseId == "po_m_seed_1" }
        assertEquals(PurchasePaymentStatus.PAID, updatedPo.paymentStatus)

        // Build statement & performance metrics
        val statement = supplierRepository.buildSupplierStatement(
            supplier = updatedSup,
            orders = purchaseRepository.observePurchaseOrders(DatabaseSeeder.MAIN_BRANCH_ID).first(),
            returns = purchaseRepository.observeReturns(DatabaseSeeder.MAIN_BRANCH_ID).first(),
            payments = supplierRepository.observePayments(DatabaseSeeder.MAIN_BRANCH_ID).first()
        )
        assertTrue(statement.lines.size >= 3)
        assertEquals(0L, statement.closingBalanceCents)

        // Still ZERO operating expense entries created by supplier payments
        assertEquals(beforeExpenses, expenseRepository.observeExpenses(DatabaseSeeder.MAIN_BRANCH_ID).first().size)
    }

    @Test
    fun `customer crm prevents duplicates integrates pos loyalty earn redeem and enforces manager adjustments`() = runTest {
        val cust = customerLoyaltyRepository.saveCustomer(
            session = cashierSession,
            customerId = null,
            name = "Hina Tariq",
            phone = "+1-555-8800",
            email = "hina@example.com",
            address = "12 Pearl Street",
            notes = "Allergic to sesame",
            accountBalanceCents = 9999L // Cashier cannot view/set balance -> stays 0
        ).getOrThrow()
        assertEquals(0L, cust.accountBalanceCents)

        // Duplicate normalized phone rejected
        val dupPhone = customerLoyaltyRepository.saveCustomer(
            session = cashierSession,
            customerId = null,
            name = "Hina Duplicate",
            phone = "15558800",
            email = "other@example.com",
            address = "",
            notes = "",
            accountBalanceCents = null
        )
        assertTrue(dupPhone.isFailure)

        // Cashier CANNOT adjust points or change loyalty rules
        val deniedAdjust = customerLoyaltyRepository.adjustPointsWithApproval(
            session = cashierSession,
            customerId = cust.customerId,
            pointsDelta = 500,
            reason = "Self promo",
            idempotencyKey = "adj_denied_1"
        )
        assertTrue(deniedAdjust.isFailure)
        assertTrue(deniedAdjust.exceptionOrNull() is SecurityException)

        val deniedConfig = customerLoyaltyRepository.updateLoyaltyConfig(
            session = cashierSession,
            earnRatePerDollar = 5.0,
            redeemCentsPerPoint = 10L,
            minRedeemPoints = 10,
            silverThreshold = 50,
            goldThreshold = 100,
            platinumThreshold = 200,
            expiryEnabled = false,
            expiryDays = 365
        )
        assertTrue(deniedConfig.isFailure)

        // Manager grants 160 welcome bonus points -> customer moves to Silver tier (150 threshold)
        val adjusted = customerLoyaltyRepository.adjustPointsWithApproval(
            session = managerSession,
            customerId = cust.customerId,
            pointsDelta = 160,
            reason = "VIP opening credit",
            idempotencyKey = "adj_mgr_1"
        ).getOrThrow()
        assertEquals(160, adjusted.loyaltyPoints)
        assertEquals("Silver", adjusted.tier)

        // POS purchase using Zara Khan (cust_1, 420 pts) redeeming 100 pts ($5.00) and earning points on net paid
        val posCust = posRepository.observeCustomers(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.customerId == "cust_1" }
        val burger = posRepository.observeProducts(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.productId == "prod_m_1" }
        val cart = listOf(CartItem("line1", burger, burger.variations.first(), emptyList(), "", 2))
        val totals = BillingCalculator.calculate(cart, 10.0, 500L, 8.0, 0.0)

        val order = posRepository.placeOrder(
            session = cashierSession,
            idempotencyKey = "idem_pos_loyalty_1",
            cartItems = cart,
            totals = totals,
            orderType = OrderType.TAKEAWAY,
            tableNumber = null,
            priority = OrderPriority.NORMAL,
            customer = posCust,
            paymentMethod = PaymentMethod.CARD,
            splitBreakdown = SplitPaymentBreakdown(cardCents = totals.totalPayableCents),
            loyaltyPointsToRedeem = 100,
            loyaltyDiscountCents = 500L
        ).getOrThrow()

        // Retry with same idempotencyKey -> no double point earn or redeem
        posRepository.placeOrder(
            session = cashierSession,
            idempotencyKey = "idem_pos_loyalty_1",
            cartItems = cart,
            totals = totals,
            orderType = OrderType.TAKEAWAY,
            tableNumber = null,
            priority = OrderPriority.NORMAL,
            customer = posCust,
            paymentMethod = PaymentMethod.CARD,
            splitBreakdown = SplitPaymentBreakdown(cardCents = totals.totalPayableCents),
            loyaltyPointsToRedeem = 100,
            loyaltyDiscountCents = 500L
        ).getOrThrow()

        val refreshedZara = customerLoyaltyRepository.observeCustomers(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.customerId == "cust_1" }
        val expectedEarned = (totals.totalPayableCents / 100.0).toInt()
        assertEquals(420 - 100 + expectedEarned, refreshedZara.loyaltyPoints)
        assertEquals(84500L + order.totalCents, refreshedZara.totalPurchasesCents)

        // Test loyalty point expiry sweep when enabled for inactive customer
        customerLoyaltyRepository.updateLoyaltyConfig(
            session = managerSession,
            earnRatePerDollar = 1.0,
            redeemCentsPerPoint = 5L,
            minRedeemPoints = 50,
            silverThreshold = 150,
            goldThreshold = 400,
            platinumThreshold = 800,
            expiryEnabled = true,
            expiryDays = 2
        ).getOrThrow()
        // Seeded cust_3 (Amina Patel) had lastOrderAt 5 days ago and 85 pts -> should expire
        val expiredCount = customerLoyaltyRepository.expireOldPointsIfEnabled(managerSession).getOrThrow()
        assertTrue(expiredCount >= 1)
        val refreshedAmina = customerLoyaltyRepository.observeCustomers(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.customerId == "cust_3" }
        assertEquals(0, refreshedAmina.loyaltyPoints)
    }

    @Test
    fun `expense management covers categories recurring approval period reports and rbac`() = runTest {
        // Cashier cannot record or approve operating expenses
        val denied = expenseRepository.saveExpense(
            session = cashierSession,
            idempotencyKey = "exp_denied_1",
            expenseId = null,
            category = ExpenseCategory.RENT.label,
            amountCents = 50000L,
            expenseDateMillis = System.currentTimeMillis(),
            description = "Unauthorized",
            paymentMethod = "Cash",
            receiptUri = null,
            isRecurring = false,
            recurrenceFrequency = ExpenseRecurrence.NONE
        )
        assertTrue(denied.isFailure)
        assertTrue(denied.exceptionOrNull() is SecurityException)

        // Manager records recurring Gas bill & approves pending seeded Maintenance expense (exp_m_3)
        val created = expenseRepository.saveExpense(
            session = managerSession,
            idempotencyKey = "exp_gas_1",
            expenseId = null,
            category = ExpenseCategory.GAS.label,
            amountCents = 19500L,
            expenseDateMillis = System.currentTimeMillis(),
            description = "Kitchen commercial gas cylinders",
            paymentMethod = "Bank Transfer",
            receiptUri = "/tmp/receipt_gas.pdf",
            isRecurring = true,
            recurrenceFrequency = ExpenseRecurrence.WEEKLY
        ).getOrThrow()

        assertEquals(ExpenseApprovalStatus.APPROVED, created.approvalStatus)
        assertTrue(created.isRecurring)
        assertEquals(ExpenseRecurrence.WEEKLY, created.recurrenceFrequency)

        expenseRepository.setExpenseApproval(managerSession, "exp_m_3", approved = true).getOrThrow()

        val allExpenses = expenseRepository.observeExpenses(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val reports = expenseRepository.buildPeriodReports(allExpenses)
        assertEquals(3, reports.size)
        assertEquals("Daily (Today)", reports[0].periodLabel)
        assertTrue(reports[0].totalApprovedCents >= 19500L)
        assertTrue(reports[2].totalApprovedCents >= 19500L + 24000L + 42000L + 18500L)

        // Verify offline sync queue logged the created expense
        val queued = database.syncQueueDao().observeQueue().first()
        assertTrue(queued.any { it.entityType == "EXPENSE" && it.entityId == created.expenseId })
    }

    @Test
    fun `report exporter generates non empty pdf csv and excel files for phase 4 reports`() = runTest {
        val orders = purchaseRepository.observePurchaseOrders(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val suppliers = supplierRepository.observeSuppliers(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val returns = purchaseRepository.observeReturns(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val payments = supplierRepository.observePayments(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val expenses = expenseRepository.observeExpenses(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val periods = expenseRepository.buildPeriodReports(expenses)

        val csvPurchase = Phase4ReportExporter.exportPurchaseReport(context, "Flagship", orders, ReportExportFormat.CSV).getOrThrow()
        assertTrue(csvPurchase.exists() && csvPurchase.readText().contains("PO-2601-0001"))

        val xlsPerf = Phase4ReportExporter.exportSupplierPerformanceReport(
            context,
            "Flagship",
            supplierRepository.buildPerformanceMetrics(suppliers, orders, returns),
            ReportExportFormat.EXCEL
        ).getOrThrow()
        assertTrue(xlsPerf.exists() && xlsPerf.readText().contains("<Workbook"))

        val statement = supplierRepository.buildSupplierStatement(suppliers.first(), orders, returns, payments)
        val pdfStmt = Phase4ReportExporter.exportSupplierStatementReport(context, statement, ReportExportFormat.PDF).getOrThrow()
        assertTrue(pdfStmt.exists() && pdfStmt.length() > 100L)

        val pdfExp = Phase4ReportExporter.exportExpenseReport(context, "Flagship", expenses, periods, ReportExportFormat.PDF).getOrThrow()
        assertTrue(pdfExp.exists() && pdfExp.length() > 100L)
    }

    @Test
    fun `migration 1 to 4 preserves legacy customers and orders and creates phase 4 tables cleanly`() {
        val dbName = "migration_test_v1_to_v4.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `branches` (`branchId` TEXT NOT NULL, `name` TEXT NOT NULL, `code` TEXT NOT NULL, `address` TEXT NOT NULL, `isMainBranch` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`branchId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profiles` (`uid` TEXT NOT NULL, `email` TEXT NOT NULL, `displayName` TEXT NOT NULL, `roleCode` TEXT NOT NULL, `branchId` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `lastLoginAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `dashboard_snapshots` (`branchId` TEXT NOT NULL, `businessDate` TEXT NOT NULL, `todaySales` REAL NOT NULL, `todayOrders` INTEGER NOT NULL, `totalCustomers` INTEGER NOT NULL, `pendingKitchenOrders` INTEGER NOT NULL, `lowStockAlerts` INTEGER NOT NULL, `dailyExpenses` REAL NOT NULL, `estimatedProfit` REAL NOT NULL, `cashPayments` REAL NOT NULL, `digitalPayments` REAL NOT NULL, `isSampleData` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`branchId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `recent_transactions` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `orderNumber` TEXT NOT NULL, `invoiceNumber` TEXT NOT NULL, `customerName` TEXT NOT NULL, `amount` REAL NOT NULL, `paymentMethod` TEXT NOT NULL, `status` TEXT NOT NULL, `timeLabel` TEXT NOT NULL, `isSampleData` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `low_stock_alerts` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `ingredientName` TEXT NOT NULL, `currentStock` REAL NOT NULL, `reorderLevel` REAL NOT NULL, `unit` TEXT NOT NULL, `isSampleData` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `hourly_sales` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `hourOrder` INTEGER NOT NULL, `label` TEXT NOT NULL, `revenue` REAL NOT NULL, `orderCount` INTEGER NOT NULL, `isSampleData` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `branchId` TEXT NOT NULL, `actorUid` TEXT NOT NULL, `actorEmail` TEXT NOT NULL, `actorRole` TEXT NOT NULL, `action` TEXT NOT NULL, `details` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_queue` (`transactionId` TEXT NOT NULL, `branchId` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `status` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `lastError` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`transactionId`))")

                        AaCraveDatabase.MIGRATION_1_2.migrate(db)
                        AaCraveDatabase.MIGRATION_2_3.migrate(db)
                        db.execSQL(
                            """
                            INSERT INTO customers (customerId, branchId, name, phone, email, loyaltyPoints, tier, discountPercent)
                            VALUES ('legacy_c1', 'branch_main_01', 'Legacy VIP', '+1-555-0001', 'vip@example.com', 250, 'Silver', 5)
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()

        val migratedDb = Room.databaseBuilder(context, AaCraveDatabase::class.java, dbName)
            .addMigrations(
                AaCraveDatabase.MIGRATION_1_2,
                AaCraveDatabase.MIGRATION_2_3,
                AaCraveDatabase.MIGRATION_3_4,
                AaCraveDatabase.MIGRATION_4_5,
                AaCraveDatabase.MIGRATION_5_6,
                AaCraveDatabase.MIGRATION_6_7,
                AaCraveDatabase.MIGRATION_7_8,
                AaCraveDatabase.MIGRATION_8_9
            )
            .allowMainThreadQueries()
            .build()

        val custCursor = migratedDb.openHelper.readableDatabase.query(
            "SELECT customerId, loyaltyPoints, totalPurchasesCents, accountBalanceCents FROM customers WHERE customerId = 'legacy_c1'"
        )
        assertTrue(custCursor.moveToFirst())
        assertEquals("legacy_c1", custCursor.getString(0))
        assertEquals(250, custCursor.getInt(1))
        assertEquals(0L, custCursor.getLong(2))
        assertEquals(0L, custCursor.getLong(3))
        custCursor.close()

        listOf("suppliers", "purchase_orders", "purchase_receivings", "supplier_payments", "loyalty_config", "expenses").forEach { tbl ->
            val tblCursor = migratedDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$tbl`")
            assertTrue(tblCursor.moveToFirst())
            assertNotNull(tblCursor.getInt(0))
            tblCursor.close()
        }

        migratedDb.close()
        context.deleteDatabase(dbName)
    }
}
