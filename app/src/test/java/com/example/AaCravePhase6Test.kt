package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AaCraveDatabase
import com.example.data.local.DatabaseSeeder
import com.example.data.repository.AccountingRepository
import com.example.data.repository.ExpenseRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.LeavePayrollRepository
import com.example.data.repository.MultiBranchRepository
import com.example.data.repository.PosRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.StaffHrRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.model.AccountCategory
import com.example.domain.model.AccountType
import com.example.domain.model.AccountingCalculator
import com.example.domain.model.AppModule
import com.example.domain.model.BillingCalculator
import com.example.domain.model.CartItem
import com.example.domain.model.CashbookTxType
import com.example.domain.model.EmploymentStatus
import com.example.domain.model.ExpenseRecurrence
import com.example.domain.model.FinancialReportType
import com.example.domain.model.JournalDraftLine
import com.example.domain.model.JournalEntryStatus
import com.example.domain.model.JournalSourceModule
import com.example.domain.model.OrderPriority
import com.example.domain.model.OrderType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PurchaseDraftLine
import com.example.domain.model.RoleAccessControl
import com.example.domain.model.SalaryType
import com.example.domain.model.SplitPaymentBreakdown
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.printing.FinancialReportBundle
import com.example.printing.Phase6ReportExporter
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
class AaCravePhase6Test {

    private lateinit var context: Context
    private lateinit var database: AaCraveDatabase
    private lateinit var accountingRepository: AccountingRepository
    private lateinit var multiBranchRepository: MultiBranchRepository
    private lateinit var posRepository: PosRepository
    private lateinit var purchaseRepository: PurchaseRepository
    private lateinit var supplierRepository: SupplierRepository
    private lateinit var expenseRepository: ExpenseRepository
    private lateinit var staffHrRepository: StaffHrRepository
    private lateinit var leavePayrollRepository: LeavePayrollRepository
    private lateinit var inventoryRepository: InventoryRepository

    private val ownerSession = UserSession(
        uid = "owner_p6",
        email = "owner@aacrave.local",
        displayName = "Hamza Owner",
        role = UserRole.OWNER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    private val managerSession = UserSession(
        uid = "mgr_p6",
        email = "manager@aacrave.local",
        displayName = "Sara Manager",
        role = UserRole.BRANCH_MANAGER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    private val hrSession = UserSession(
        uid = "hr_p6",
        email = "hr@aacrave.local",
        displayName = "Ayesha HR",
        role = UserRole.HR,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    private val invSession = UserSession(
        uid = "inv_p6",
        email = "inventory@aacrave.local",
        displayName = "Bilal Inventory",
        role = UserRole.INVENTORY_MANAGER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    private val cashierSession = UserSession(
        uid = "cash_p6",
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
        accountingRepository = AccountingRepository(database)
        multiBranchRepository = MultiBranchRepository(database)
        posRepository = PosRepository(database)
        purchaseRepository = PurchaseRepository(database)
        supplierRepository = SupplierRepository(database)
        expenseRepository = ExpenseRepository(context, database)
        staffHrRepository = StaffHrRepository(context, database)
        leavePayrollRepository = LeavePayrollRepository(database)
        inventoryRepository = InventoryRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `chart of accounts manual journal validates balance updates balances and supports authorized reversal`() = runTest {
        val accounts = accountingRepository.observeChartOfAccounts(DatabaseSeeder.MAIN_BRANCH_ID).first()
        assertTrue(accounts.size >= 13)
        val cashAcc = accounts.first { it.accountCode == "1010" }
        val bankAcc = accounts.first { it.accountCode == "1020" }
        val equityAcc = accounts.first { it.accountCode == "3000" }

        // Custom COA account creation + duplicate code prevention
        val createdAcc = accountingRepository.saveChartAccount(
            session = ownerSession,
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            accountId = null,
            accountCode = "6400",
            accountName = "Marketing & Promotions",
            accountType = AccountType.EXPENSE,
            accountCategory = AccountCategory.OPERATING_EXPENSE,
            openingBalanceCents = 0L,
            isActive = true
        ).getOrThrow()
        assertEquals("6400", createdAcc.accountCode)

        val duplicateAcc = accountingRepository.saveChartAccount(
            session = ownerSession,
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            accountId = null,
            accountCode = "6400",
            accountName = "Duplicate Code",
            accountType = AccountType.EXPENSE,
            accountCategory = AccountCategory.OPERATING_EXPENSE,
            openingBalanceCents = 0L,
            isActive = true
        )
        assertTrue(duplicateAcc.isFailure)

        // Unbalanced manual journal must be rejected
        val unbalanced = accountingRepository.postManualJournalEntry(
            session = ownerSession,
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            idempotencyKey = "man_unbal_1",
            entryDateMillis = System.currentTimeMillis(),
            reference = "JV-UNBAL",
            description = "Unbalanced attempt",
            draftLines = listOf(
                JournalDraftLine(createdAcc.accountId, debitCents = 15_000L, creditCents = 0L),
                JournalDraftLine(bankAcc.accountId, debitCents = 0L, creditCents = 10_000L)
            )
        )
        assertTrue(unbalanced.isFailure)

        // Balanced manual journal succeeds
        val posted = accountingRepository.postManualJournalEntry(
            session = ownerSession,
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            idempotencyKey = "man_bal_1",
            entryDateMillis = System.currentTimeMillis(),
            reference = "JV-001",
            description = "Capital & Marketing Allocation",
            draftLines = listOf(
                JournalDraftLine(cashAcc.accountId, debitCents = 25_000L, creditCents = 0L, memo = "Cash injection"),
                JournalDraftLine(equityAcc.accountId, debitCents = 0L, creditCents = 25_000L, memo = "Owner capital")
            )
        ).getOrThrow()
        assertEquals(25_000L, posted.totalDebitCents)
        assertEquals(25_000L, posted.totalCreditCents)
        assertEquals(JournalEntryStatus.POSTED, posted.status)

        val updatedAccounts = accountingRepository.observeChartOfAccounts(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val updatedCash = updatedAccounts.first { it.accountCode == "1010" }
        assertEquals(cashAcc.openingBalanceCents + 25_000L, updatedCash.currentBalanceCents)

        // Cashier cannot reverse historical journal entry
        val cashierRev = accountingRepository.reverseJournalEntry(cashierSession, posted.entryId, "Unauthorized test")
        assertTrue(cashierRev.isFailure)

        // Owner authorized reversal creates opposite balanced entry & audit log
        val reversed = accountingRepository.reverseJournalEntry(ownerSession, posted.entryId, "Entry entered in wrong branch").getOrThrow()
        assertEquals(JournalSourceModule.REVERSAL, reversed.sourceModule)
        assertEquals(25_000L, reversed.totalDebitCents)
        assertEquals(25_000L, reversed.totalCreditCents)

        val journals = accountingRepository.observeJournalEntries(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val origReloaded = journals.first { it.entryId == posted.entryId }
        assertEquals(JournalEntryStatus.REVERSED, origReloaded.status)
        assertEquals(reversed.entryId, origReloaded.reversedEntryId)

        val afterRevAccounts = accountingRepository.observeChartOfAccounts(DatabaseSeeder.MAIN_BRANCH_ID).first()
        assertEquals(cashAcc.openingBalanceCents, afterRevAccounts.first { it.accountCode == "1010" }.currentBalanceCents)

        val logs = database.auditLogDao().observeRecentLogs().first()
        assertTrue(logs.any { it.action == "JOURNAL_REVERSED" })
    }

    @Test
    fun `erp transactions integrate automatically into double entry accounting without duplicates`() = runTest {
        val now = System.currentTimeMillis()
        // Seed initial sync state (clean baseline)
        val initialSync = accountingRepository.syncAllStoredTransactions(ownerSession).getOrThrow()
        assertTrue(initialSync >= 0)

        // 1. POS Order (Cash payment) -> POS_SALE journal + Cashbook receipt
        val product = posRepository.observeProducts(DatabaseSeeder.MAIN_BRANCH_ID).first().first()
        val cart = listOf(
            CartItem(
                cartLineId = "line_1",
                product = product,
                selectedVariation = null,
                selectedAddons = emptyList(),
                specialInstructions = "",
                quantity = 2
            )
        )
        val totals = BillingCalculator.calculate(
            items = cart,
            discountPercent = 0.0,
            manualDiscountCents = 0L,
            serviceChargeRatePercent = 0.0
        )
        val order = posRepository.placeOrder(
            session = cashierSession,
            idempotencyKey = "pos_p6_1",
            cartItems = cart,
            totals = totals,
            orderType = OrderType.TAKEAWAY,
            tableNumber = null,
            priority = OrderPriority.NORMAL,
            customer = null,
            paymentMethod = PaymentMethod.CASH,
            splitBreakdown = SplitPaymentBreakdown(cashCents = totals.totalPayableCents)
        ).getOrThrow()

        // 2. POS Refund -> POS_REFUND reversal journal
        val refundTargetOrder = posRepository.placeOrder(
            session = cashierSession,
            idempotencyKey = "pos_p6_2",
            cartItems = cart,
            totals = totals,
            orderType = OrderType.TAKEAWAY,
            tableNumber = null,
            priority = OrderPriority.NORMAL,
            customer = null,
            paymentMethod = PaymentMethod.CARD,
            splitBreakdown = SplitPaymentBreakdown(cardCents = totals.totalPayableCents)
        ).getOrThrow()
        assertTrue(posRepository.refundOrder(managerSession, refundTargetOrder.orderId, "Customer changed order").isSuccess)

        // 3. Purchase Order -> Approve -> Partial/Full Receive -> Purchase Return -> Supplier Payment
        val supplier = supplierRepository.observeSuppliers(DatabaseSeeder.MAIN_BRANCH_ID).first().first()
        val invItem = inventoryRepository.observeInventoryItems(DatabaseSeeder.MAIN_BRANCH_ID).first().first()
        val po = purchaseRepository.createOrUpdatePurchaseOrder(
            session = ownerSession,
            purchaseId = null,
            idempotencyKey = "po_p6_1",
            supplierId = supplier.supplierId,
            supplierInvoiceNumber = "SUP-INV-601",
            draftLines = listOf(
                PurchaseDraftLine(
                    inventoryItemId = invItem.itemId,
                    itemName = invItem.name,
                    itemType = invItem.itemType.name,
                    unit = invItem.unit,
                    orderedQty = 10.0,
                    unitPriceCents = 1_500L
                )
            ),
            discountCents = 0L,
            taxCents = 0L,
            notes = "Phase 6 test purchase",
            submitAsPending = true
        ).getOrThrow()
        assertTrue(purchaseRepository.approvePurchaseOrder(ownerSession, po.purchaseId).isSuccess)
        val receivedPo = purchaseRepository.receiveGoods(
            session = ownerSession,
            purchaseId = po.purchaseId,
            receivingKey = "grn_p6_1",
            referenceNumber = "GRN-601",
            receivedDateMillis = now,
            lineQuantities = mapOf(po.items.first().lineId to 10.0),
            notes = "Full receipt",
            supplierInvoiceNumber = "SUP-INV-601"
        ).getOrThrow()
        assertEquals(15_000L, receivedPo.totalCents)

        purchaseRepository.returnPurchaseGoods(
            session = ownerSession,
            purchaseId = po.purchaseId,
            lineId = po.items.first().lineId,
            returnQty = 2.0,
            reason = "2 units damaged"
        ).getOrThrow()

        supplierRepository.recordSupplierPayment(
            session = ownerSession,
            idempotencyKey = "spay_p6_1",
            supplierId = supplier.supplierId,
            purchaseId = po.purchaseId,
            amountCents = 8_000L,
            paymentMethod = "Bank Transfer",
            referenceNumber = "WIRE-601",
            paymentDateMillis = now,
            notes = "Partial vendor settlement"
        ).getOrThrow()

        // 4. Approved Expense
        val exp = expenseRepository.saveExpense(
            session = ownerSession,
            idempotencyKey = "exp_p6_1",
            expenseId = null,
            category = "Utilities",
            amountCents = 6_000L,
            expenseDateMillis = now,
            description = "Electricity Bill",
            paymentMethod = "Bank Transfer",
            receiptUri = null,
            isRecurring = false,
            recurrenceFrequency = ExpenseRecurrence.NONE
        ).getOrThrow()

        // 5. Staff + Payroll Approval (accrual) + Paid (disbursement)
        val staff = staffHrRepository.saveStaff(
            session = hrSession,
            staffId = null,
            employeeCode = "EMP-601",
            fullName = "Zain Chef",
            phone = "+1-555-6010",
            address = "Main St",
            jobTitle = "Head Chef",
            department = "Kitchen",
            assignedRole = UserRole.KITCHEN_STAFF,
            accessibleBranchIds = listOf(DatabaseSeeder.MAIN_BRANCH_ID),
            joiningDateMillis = now - 86_400_000L,
            employmentStatus = EmploymentStatus.ACTIVE,
            salaryType = SalaryType.MONTHLY,
            baseSalaryCents = 60_000L,
            defaultAllowanceCents = 0L,
            overtimeMultiplier = 1.5,
            annualLeaveDays = 14,
            sickLeaveDays = 10,
            casualLeaveDays = 6,
            attendancePin = "1234",
            notes = ""
        ).getOrThrow()
        val payroll = leavePayrollRepository.generatePayroll(
            session = ownerSession,
            staffId = staff.staffId,
            periodKey = "2026-11",
            periodLabel = "Nov 2026",
            periodStartMillis = now - 86_400_000L,
            periodEndMillis = now,
            allowancesCentsOverride = 10_000L,
            bonusesCents = 25_000L,
            authorizedDeductionsCents = 0L,
            deductionReason = ""
        ).getOrThrow()
        leavePayrollRepository.approvePayroll(ownerSession, payroll.payrollId).getOrThrow()
        leavePayrollRepository.markPayrollPaid(ownerSession, payroll.payrollId, now, "Bank Transfer", "PAY-601").getOrThrow()

        // Verify all source modules posted into journals & duplicate sync adds 0
        val journals = accountingRepository.observeJournalEntries(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val modulesPresent = journals.map { it.sourceModule }.toSet()
        assertTrue(JournalSourceModule.POS_SALE in modulesPresent)
        assertTrue(JournalSourceModule.POS_REFUND in modulesPresent)
        assertTrue(JournalSourceModule.PURCHASE_BILL in modulesPresent)
        assertTrue(JournalSourceModule.PURCHASE_RETURN in modulesPresent)
        assertTrue(JournalSourceModule.SUPPLIER_PAYMENT in modulesPresent)
        assertTrue(JournalSourceModule.EXPENSE in modulesPresent)
        assertTrue(JournalSourceModule.PAYROLL_ACCRUAL in modulesPresent)
        assertTrue(JournalSourceModule.PAYROLL_PAYMENT in modulesPresent)

        // Every single journal entry is strictly balanced
        assertTrue(journals.all { it.totalDebitCents == it.totalCreditCents && it.totalDebitCents > 0L })

        // Re-running syncAllStoredTransactions produces zero duplicates
        val resyncCount = accountingRepository.syncAllStoredTransactions(ownerSession).getOrThrow()
        assertEquals(0, resyncCount)
        assertEquals(journals.size, accountingRepository.observeJournalEntries(DatabaseSeeder.MAIN_BRANCH_ID).first().size)

        // Delete expense -> verifies reversal entry is created
        assertTrue(expenseRepository.deleteExpense(ownerSession, exp.expenseId).isSuccess)
        val afterDeleteJournals = accountingRepository.observeJournalEntries(DatabaseSeeder.MAIN_BRANCH_ID).first()
        assertTrue(afterDeleteJournals.size > journals.size)

        // Build all financial reports & verify accounting identities
        val accounts = accountingRepository.observeChartOfAccounts(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val allLines = afterDeleteJournals.flatMap { it.lines }
        val tb = AccountingCalculator.buildTrialBalance("Main", accounts, allLines)
        assertTrue("Trial Balance must balance", tb.isBalanced)
        assertEquals(tb.totalDebitsCents, tb.totalCreditsCents)

        val bs = AccountingCalculator.buildBalanceSheet("Main", accounts, allLines)
        assertTrue("Balance Sheet must satisfy Assets = Liabilities + Equity + Earnings", bs.isBalanced)
        assertEquals(bs.totalAssetsCents, bs.totalLiabilitiesCents + bs.totalEquityWithEarningsCents)

        val pnl = AccountingCalculator.buildProfitAndLoss("Main", accounts, allLines)
        assertTrue(pnl.totalIncomeCents >= order.subtotalCents)

        val cb = accountingRepository.observeCashbookTransactions(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val cf = AccountingCalculator.buildCashFlow(
            scopeLabel = "Main",
            cashBankAccounts = accounts.filter { it.isCashOrBank },
            cashbookTxsInRange = cb,
            allCashbookTxsBeforeRange = emptyList(),
            postedEntriesById = afterDeleteJournals.associateBy { it.entryId }
        )
        assertEquals(cf.openingCashAndBankCents + cf.netOperatingCashFlowCents, cf.closingCashAndBankCents)
    }

    @Test
    fun `cashbook transfers customer payments and party ledgers compute accurate balances`() = runTest {
        val now = System.currentTimeMillis()
        val accounts = accountingRepository.observeChartOfAccounts(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val cashAcc = accounts.first { it.accountCode == "1010" }
        val bankAcc = accounts.first { it.accountCode == "1020" }
        val otherIncomeAcc = accounts.first { it.accountCode == "4090" }

        // Cashbook Receipt + Internal Transfer Cash -> Bank
        accountingRepository.recordCashbookTransaction(
            session = ownerSession,
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            idempotencyKey = "cb_rcp_1",
            txType = CashbookTxType.RECEIPT,
            cashBankAccountId = cashAcc.accountId,
            counterAccountId = otherIncomeAcc.accountId,
            amountCents = 20_000L,
            txDateMillis = now,
            reference = "RCP-01",
            counterpartyName = "Catering Deposit",
            description = "Advance deposit"
        ).getOrThrow()

        accountingRepository.recordCashbookTransaction(
            session = ownerSession,
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            idempotencyKey = "cb_trf_1",
            txType = CashbookTxType.TRANSFER_OUT,
            cashBankAccountId = cashAcc.accountId,
            counterAccountId = bankAcc.accountId,
            amountCents = 12_000L,
            txDateMillis = now + 1000L,
            reference = "TRF-01",
            counterpartyName = bankAcc.accountName,
            description = "Bank cash deposit"
        ).getOrThrow()

        val refreshedAccs = accountingRepository.observeChartOfAccounts(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val refreshedCash = refreshedAccs.first { it.accountCode == "1010" }
        val refreshedBank = refreshedAccs.first { it.accountCode == "1020" }
        assertEquals(cashAcc.openingBalanceCents + 8_000L, refreshedCash.currentBalanceCents)
        assertEquals(bankAcc.openingBalanceCents + 12_000L, refreshedBank.currentBalanceCents)

        // Customer credit order -> Customer payment -> Customer Ledger & Outstanding verification
        val custEntity = database.posDao().observeCustomers(DatabaseSeeder.MAIN_BRANCH_ID).first().first()
        val custSummary = accountingRepository.observeCustomers(DatabaseSeeder.MAIN_BRANCH_ID).first().first { it.customerId == custEntity.customerId }
        val product = posRepository.observeProducts(DatabaseSeeder.MAIN_BRANCH_ID).first().first()
        val cart = listOf(CartItem("cl_1", product, null, emptyList(), "", 1))
        val totals = BillingCalculator.calculate(cart, 0.0, 0L, serviceChargeRatePercent = 0.0)
        // Partial payment -> leaves remaining receivable balance
        val partialPaid = totals.totalPayableCents / 2L
        val creditOrder = posRepository.placeOrder(
            session = ownerSession,
            idempotencyKey = "pos_cust_cr_1",
            cartItems = cart,
            totals = totals,
            orderType = OrderType.TAKEAWAY,
            tableNumber = null,
            priority = OrderPriority.NORMAL,
            customer = com.example.domain.model.CustomerSummary(
                customerId = custSummary.customerId,
                branchId = custSummary.branchId,
                name = custSummary.name,
                phone = custSummary.phone,
                email = custSummary.email,
                loyaltyPoints = custSummary.loyaltyPoints,
                tier = custSummary.tier,
                discountPercent = 0,
                totalPurchasesCents = custSummary.totalPurchasesCents,
                accountBalanceCents = custSummary.accountBalanceCents
            ),
            paymentMethod = PaymentMethod.CASH,
            splitBreakdown = SplitPaymentBreakdown(cashCents = partialPaid)
        ).getOrThrow()
        assertTrue(creditOrder.remainingBalanceCents > 0L)

        accountingRepository.recordCustomerPayment(
            session = ownerSession,
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            idempotencyKey = "cust_pay_test_1",
            customerId = custSummary.customerId,
            cashBankAccountId = bankAcc.accountId,
            amountCents = creditOrder.remainingBalanceCents,
            reference = "CRCP-100",
            paymentDateMillis = now + 2000L,
            notes = "Settled receivable"
        ).getOrThrow()

        val updatedCust = accountingRepository.observeCustomers(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.customerId == custSummary.customerId }
        assertEquals(0L, updatedCust.accountBalanceCents)

        val journals = accountingRepository.observeJournalEntries(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val cb = accountingRepository.observeCashbookTransactions(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val orders = accountingRepository.observePosOrders(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val custLedger = accountingRepository.buildCustomerLedger(updatedCust, orders, cb, journals)
        assertEquals(0L, custLedger.closingBalanceCents)
        assertTrue(custLedger.lines.any { it.sourceLabel == "PAYMENT" && it.creditCents == creditOrder.remainingBalanceCents })
    }

    @Test
    fun `rbac and financial report exports enforce strict access and generate pdf and csv files`() = runTest {
        // Phase 6 Accounting & Reports plus Phase 7 Cloud/Backup/Permissions remain active
        assertTrue(AppModule.USER_PERMISSIONS.isImplementedInCurrentPhase)
        assertTrue(AppModule.NOTIFICATIONS.isImplementedInCurrentPhase)
        assertTrue(AppModule.BACKUP_RESTORE.isImplementedInCurrentPhase)
        assertTrue(AppModule.ACCOUNTING.isImplementedInCurrentPhase)
        assertTrue(AppModule.REPORTS.isImplementedInCurrentPhase)

        // Role gating assertions
        assertFalse(cashierSession.capabilities.canViewAccounting)
        assertFalse(cashierSession.capabilities.canExportReports)
        assertFalse(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.ACCOUNTING))
        assertFalse(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.REPORTS))

        assertTrue(Phase6ReportExporter.authorizedReportTypesFor(hrSession) == listOf(FinancialReportType.PAYROLL_SUMMARY))
        assertFalse(Phase6ReportExporter.canAccessReport(hrSession, FinancialReportType.PROFIT_AND_LOSS))
        assertTrue(Phase6ReportExporter.canAccessReport(invSession, FinancialReportType.PURCHASE_SUMMARY))
        assertFalse(Phase6ReportExporter.canAccessReport(invSession, FinancialReportType.BALANCE_SHEET))

        val accounts = accountingRepository.observeChartOfAccounts(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val journals = accountingRepository.observeJournalEntries(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val lines = journals.flatMap { it.lines }
        val bundle = FinancialReportBundle(
            reportType = FinancialReportType.TRIAL_BALANCE,
            scopeLabel = "AA CRAVE - Flagship Main",
            profitAndLoss = AccountingCalculator.buildProfitAndLoss("Main", accounts, lines),
            balanceSheet = AccountingCalculator.buildBalanceSheet("Main", accounts, lines),
            cashFlow = AccountingCalculator.buildCashFlow("Main", accounts.filter { it.isCashOrBank }, emptyList(), emptyList(), emptyMap()),
            trialBalance = AccountingCalculator.buildTrialBalance("Main", accounts, lines),
            generalLedger = accountingRepository.buildGeneralLedger(accounts, journals, null, null, null, ""),
            cashBankSummaries = accountingRepository.buildCashBankSummaries(accounts, emptyList()),
            cashbookTransactions = emptyList(),
            salesSummary = accountingRepository.buildSalesSummary(emptyList()),
            posOrders = emptyList(),
            purchaseOrders = emptyList(),
            approvedExpenses = emptyList(),
            payrollSummary = accountingRepository.buildPayrollSummary(emptyList()),
            payrollRecords = emptyList(),
            customerOutstanding = emptyList(),
            supplierOutstanding = emptyList(),
            consolidatedReport = null
        )

        // Cashier export denied
        assertTrue(Phase6ReportExporter.exportReport(context, cashierSession, bundle, ReportExportFormat.PDF).isFailure)
        // HR P&L export denied, HR Payroll Summary allowed
        assertTrue(
            Phase6ReportExporter.exportReport(
                context, hrSession, bundle.copy(reportType = FinancialReportType.PROFIT_AND_LOSS), ReportExportFormat.CSV
            ).isFailure
        )
        val hrCsv = Phase6ReportExporter.exportReport(
            context, hrSession, bundle.copy(reportType = FinancialReportType.PAYROLL_SUMMARY), ReportExportFormat.CSV
        ).getOrThrow()
        assertTrue(hrCsv.exists() && hrCsv.readText().contains("Payroll"))

        // Owner can export every report in PDF and CSV
        FinancialReportType.entries.forEach { reportType ->
            val pdf = Phase6ReportExporter.exportReport(context, ownerSession, bundle.copy(reportType = reportType), ReportExportFormat.PDF).getOrThrow()
            val csv = Phase6ReportExporter.exportReport(context, ownerSession, bundle.copy(reportType = reportType), ReportExportFormat.CSV).getOrThrow()
            assertTrue("${reportType.name} PDF should exist and be non-empty", pdf.exists() && pdf.length() > 100L)
            assertTrue("${reportType.name} CSV should exist and be non-empty", csv.exists() && csv.length() > 10L)
        }
    }

    @Test
    fun `multi branch management creates edits deactivates assigns staff switches and builds consolidated reports`() = runTest {
        val now = System.currentTimeMillis()
        // Branch Manager cannot create branches, switch branches, or view consolidated reports
        assertFalse(managerSession.capabilities.canManageBranches)
        assertFalse(managerSession.capabilities.canSwitchBranches)
        assertFalse(managerSession.capabilities.canViewConsolidatedReports)
        assertTrue(
            multiBranchRepository.saveBranch(
                session = managerSession,
                branchId = null,
                name = "Unauthorized Branch",
                code = "UNAUTH",
                address = "1 Road",
                phone = "555",
                email = "x@y.com",
                managerName = "",
                managerUid = null,
                openingCashCents = 0L,
                openingBankCents = 0L,
                isActive = true
            ).isFailure
        )
        assertTrue(multiBranchRepository.canSwitchToBranch(managerSession, DatabaseSeeder.DOWNTOWN_BRANCH_ID).isFailure)

        // Owner creates a 3rd branch with opening balances -> seeds dedicated COA cleanly
        val newBranch = multiBranchRepository.saveBranch(
            session = ownerSession,
            branchId = null,
            name = "AA CRAVE Marina Grill",
            code = "BR03",
            address = "88 Marina Blvd",
            phone = "+1-555-3003",
            email = "marina@aacrave.local",
            managerName = "Omar Branch",
            managerUid = null,
            openingCashCents = 50_000L,
            openingBankCents = 150_000L,
            isActive = true
        ).getOrThrow()
        assertEquals("BR03", newBranch.code)

        val br03Coa = accountingRepository.observeChartOfAccounts(newBranch.branchId).first()
        assertEquals(50_000L, br03Coa.first { it.accountCode == "1010" }.openingBalanceCents)
        assertEquals(150_000L, br03Coa.first { it.accountCode == "1020" }.openingBalanceCents)
        assertEquals(200_000L, br03Coa.first { it.accountCode == "3000" }.openingBalanceCents)

        // Main branch cannot be deactivated
        assertTrue(multiBranchRepository.setBranchActiveStatus(ownerSession, DatabaseSeeder.MAIN_BRANCH_ID, false, "Try disable").isFailure)

        // Assign staff as manager to new branch
        val staff = staffHrRepository.saveStaff(
            session = ownerSession,
            staffId = null,
            employeeCode = "EMP-BR3",
            fullName = "Nadia Lead",
            phone = "+1-555-7777",
            address = "Marina",
            jobTitle = "Branch Manager",
            department = "Operations",
            assignedRole = UserRole.BRANCH_MANAGER,
            accessibleBranchIds = listOf(DatabaseSeeder.MAIN_BRANCH_ID),
            joiningDateMillis = now,
            employmentStatus = EmploymentStatus.ACTIVE,
            salaryType = SalaryType.MONTHLY,
            baseSalaryCents = 90_000L,
            defaultAllowanceCents = 0L,
            overtimeMultiplier = 1.5,
            annualLeaveDays = 14,
            sickLeaveDays = 10,
            casualLeaveDays = 6,
            attendancePin = "4321",
            notes = ""
        ).getOrThrow()

        multiBranchRepository.assignStaffToBranch(
            session = ownerSession,
            staffId = staff.staffId,
            primaryBranchId = newBranch.branchId,
            accessibleBranchIds = listOf(newBranch.branchId, DatabaseSeeder.MAIN_BRANCH_ID),
            setAsBranchManager = true
        ).getOrThrow()

        val updatedBr03 = multiBranchRepository.observeBranches().first().first { it.branchId == newBranch.branchId }
        assertEquals("Nadia Lead", updatedBr03.managerName)

        // Deactivate BR03 -> switching to inactive branch fails; reactivate -> succeeds
        assertTrue(multiBranchRepository.setBranchActiveStatus(ownerSession, newBranch.branchId, false, "Renovation").isSuccess)
        assertTrue(multiBranchRepository.canSwitchToBranch(ownerSession, newBranch.branchId).isFailure)
        assertTrue(multiBranchRepository.setBranchActiveStatus(ownerSession, newBranch.branchId, true, "Reopened").isSuccess)
        assertTrue(multiBranchRepository.canSwitchToBranch(ownerSession, newBranch.branchId).isSuccess)

        // Consolidated report aggregates all branches
        val consolidated = accountingRepository.buildConsolidatedFinancialReport(
            branches = multiBranchRepository.observeBranches().first(),
            accounts = accountingRepository.observeChartOfAccounts(null).first(),
            entries = accountingRepository.observeJournalEntries(null).first(),
            posOrders = accountingRepository.observePosOrders(null).first(),
            purchases = accountingRepository.observePurchaseOrders(null).first(),
            expenses = accountingRepository.observeExpenses(null).first(),
            payrolls = accountingRepository.observePayrolls(null).first(),
            staffList = accountingRepository.observeAllStaff().first()
        )
        assertEquals(3, consolidated.branchSnapshots.size)
        assertTrue(consolidated.totalCashBalanceCents >= 50_000L)

        val consPdf = Phase6ReportExporter.exportConsolidatedBranchComparison(
            context, ownerSession, consolidated, ReportExportFormat.PDF
        ).getOrThrow()
        assertTrue(consPdf.exists() && consPdf.length() > 100L)
        assertTrue(
            Phase6ReportExporter.exportConsolidatedBranchComparison(
                context, managerSession, consolidated, ReportExportFormat.CSV
            ).isFailure
        )
    }

    @Test
    fun `database migration from version 1 to version 6 preserves branches and creates phase 6 accounting tables`() = runTest {
        val dbName = "aa_crave_mig_1_to_6.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profiles` (`uid` TEXT NOT NULL, `email` TEXT NOT NULL, `displayName` TEXT NOT NULL, `roleCode` TEXT NOT NULL, `branchId` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `lastLoginAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `branches` (`branchId` TEXT NOT NULL, `name` TEXT NOT NULL, `code` TEXT NOT NULL, `address` TEXT NOT NULL, `isMainBranch` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`branchId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `dashboard_snapshots` (`branchId` TEXT NOT NULL, `businessDate` TEXT NOT NULL, `todaySales` REAL NOT NULL, `todayOrders` INTEGER NOT NULL, `totalCustomers` INTEGER NOT NULL, `pendingKitchenOrders` INTEGER NOT NULL, `lowStockAlerts` INTEGER NOT NULL, `dailyExpenses` REAL NOT NULL, `estimatedProfit` REAL NOT NULL, `cashPayments` REAL NOT NULL, `digitalPayments` REAL NOT NULL, `isSampleData` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`branchId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `low_stock_alerts` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `ingredientName` TEXT NOT NULL, `currentStock` REAL NOT NULL, `reorderLevel` REAL NOT NULL, `unit` TEXT NOT NULL, `isSampleData` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `recent_transactions` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `orderNumber` TEXT NOT NULL, `invoiceNumber` TEXT NOT NULL, `customerName` TEXT NOT NULL, `amount` REAL NOT NULL, `paymentMethod` TEXT NOT NULL, `status` TEXT NOT NULL, `timeLabel` TEXT NOT NULL, `isSampleData` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `hourly_sales` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `hourOrder` INTEGER NOT NULL, `label` TEXT NOT NULL, `revenue` REAL NOT NULL, `orderCount` INTEGER NOT NULL, `isSampleData` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `branchId` TEXT NOT NULL, `actorUid` TEXT NOT NULL, `actorEmail` TEXT NOT NULL, `actorRole` TEXT NOT NULL, `action` TEXT NOT NULL, `details` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_queue` (`transactionId` TEXT NOT NULL, `branchId` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `status` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `lastError` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`transactionId`))")

                        db.execSQL(
                            """
                            INSERT INTO branches (branchId, name, code, address, isMainBranch, isActive)
                            VALUES ('branch_main_01', 'AA CRAVE Legacy Main', 'MAIN', '10 Legacy Ave', 1, 1)
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

        val brCursor = migratedDb.openHelper.readableDatabase.query(
            "SELECT branchId, name, openingCashCents, openingBankCents FROM branches WHERE branchId = 'branch_main_01'"
        )
        assertTrue(brCursor.moveToFirst())
        assertEquals("AA CRAVE Legacy Main", brCursor.getString(1))
        assertEquals(0L, brCursor.getLong(2))
        assertEquals(0L, brCursor.getLong(3))
        brCursor.close()

        listOf(
            "chart_of_accounts",
            "journal_entries",
            "journal_lines",
            "cashbook_entries"
        ).forEach { table ->
            val cursor = migratedDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$table`")
            assertTrue(cursor.moveToFirst())
            assertNotNull(cursor.getInt(0))
            cursor.close()
        }

        migratedDb.close()
        context.deleteDatabase(dbName)
    }
}
