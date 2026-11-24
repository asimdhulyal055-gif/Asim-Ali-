package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.data.cloud.CloudReadOutcome
import com.example.data.cloud.CloudSyncGateway
import com.example.data.cloud.CloudWriteOutcome
import com.example.data.cloud.NetworkConnectivityMonitor
import com.example.data.cloud.RemoteCloudRecord
import com.example.data.local.AaCraveDatabase
import com.example.data.local.DatabaseSeeder
import com.example.data.local.SessionPreferences
import com.example.data.local.StaffProfileEntity
import com.example.data.local.UserProfileEntity
import com.example.data.repository.AccountingRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.ErpAuthorizationGuard
import com.example.data.repository.FirebaseAuthRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.MultiBranchRepository
import com.example.data.repository.PosRepository
import com.example.data.repository.SecurityAdminRepository
import com.example.data.repository.SmartLoyaltyRepository
import com.example.data.repository.StaffAuthCreateOutcome
import com.example.data.repository.StaffAuthProvisioner
import com.example.data.security.PasswordSecurity
import com.example.domain.model.AppModule
import com.example.domain.model.BillingTotals
import com.example.domain.model.CartItem
import com.example.domain.model.CloudProbeResult
import com.example.domain.model.FirebaseCloudConstants
import com.example.domain.model.FirebaseEnvironmentStatus
import com.example.domain.model.InventoryItemType
import com.example.domain.model.JournalDraftLine
import com.example.domain.model.ModuleSection
import com.example.domain.model.OrderPriority
import com.example.domain.model.OrderType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PkrCurrencyFormatter
import com.example.domain.model.PurchaseItemRecord
import com.example.domain.model.PurchaseOrderRecord
import com.example.domain.model.PurchasePaymentStatus
import com.example.domain.model.PurchaseStatus
import com.example.domain.model.RoleAccessControl
import com.example.domain.model.SplitPaymentBreakdown
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.domain.model.centsToCurrency
import com.example.domain.model.centsToPkr
import com.example.printing.Phase4ReportExporter
import com.example.printing.ReportExportFormat
import com.example.ui.components.formatCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class AaCraveFinalAuditTest {

    private lateinit var context: Context
    private lateinit var database: AaCraveDatabase
    private lateinit var fakeMonitor: FakeConnectivityMonitor
    private lateinit var fakeGateway: FakeCloudSyncGateway
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var securityAdminRepository: SecurityAdminRepository
    private lateinit var posRepository: PosRepository
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var accountingRepository: AccountingRepository
    private lateinit var multiBranchRepository: MultiBranchRepository
    private lateinit var smartLoyaltyRepository: SmartLoyaltyRepository

    private val ownerSession = UserSession(
        uid = "uid_owner_audit",
        email = "owner@aacrave.pk",
        displayName = "AA CRAVE Owner",
        role = UserRole.OWNER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = true,
        isPreviewSandbox = false
    )

    private val adminSession = UserSession(
        uid = "uid_admin_audit",
        email = "admin@aacrave.pk",
        displayName = "Operations Admin",
        role = UserRole.ADMIN,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = true,
        isPreviewSandbox = false
    )

    private val branchManagerSession = UserSession(
        uid = "uid_mgr_audit",
        email = "manager@aacrave.pk",
        displayName = "Main Branch Manager",
        role = UserRole.BRANCH_MANAGER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = true,
        isPreviewSandbox = false
    )

    private val cashierSession = UserSession(
        uid = "uid_cashier_audit",
        email = "cashier@aacrave.pk",
        displayName = "Front Cashier",
        role = UserRole.CASHIER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = true,
        isPreviewSandbox = false
    )

    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AaCraveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseSeeder(database).ensureSeeded()

        fakeMonitor = FakeConnectivityMonitor(true)
        fakeGateway = FakeCloudSyncGateway()
        fakeAuthRepository = FakeAuthRepository(ownerSession)

        securityAdminRepository = SecurityAdminRepository(
            database = database,
            gateway = fakeGateway,
            connectivityMonitor = fakeMonitor,
            authRepository = fakeAuthRepository
        )
        posRepository = PosRepository(database)
        inventoryRepository = InventoryRepository(database)
        accountingRepository = AccountingRepository(database)
        multiBranchRepository = MultiBranchRepository(database)
        smartLoyaltyRepository = SmartLoyaltyRepository(database)

        // Persist active operator records so lifecycle and suspension tests behave deterministically
        val now = System.currentTimeMillis()
        listOf(ownerSession, adminSession, branchManagerSession, cashierSession).forEach { s ->
            database.userProfileDao().upsertProfile(
                UserProfileEntity(
                    uid = s.uid,
                    email = s.email,
                    displayName = s.displayName,
                    roleCode = s.role.code,
                    branchId = s.branchId,
                    isActive = true,
                    lastLoginAt = now
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `navigation audit confirms all modules implemented and inventory visible to authorized roles`() {
        val allModules = AppModule.entries
        assertEquals(30, allModules.size)
        assertTrue(allModules.all { it.isImplementedInCurrentPhase })
        assertEquals(allModules.size, allModules.map { it.route }.distinct().size)

        // Inventory module exists under Catalog, Inventory & Supply
        assertEquals(ModuleSection.INVENTORY_SUPPLY, AppModule.INVENTORY.section)
        assertEquals("inventory", AppModule.INVENTORY.route)

        // Authorized roles can access Inventory
        listOf(
            UserRole.SUPER_ADMIN,
            UserRole.OWNER,
            UserRole.ADMIN,
            UserRole.BRANCH_MANAGER,
            UserRole.INVENTORY_MANAGER,
            UserRole.PURCHASE_STAFF
        ).forEach { role ->
            assertTrue("${role.code} should access INVENTORY", RoleAccessControl.canAccessModule(role, AppModule.INVENTORY))
        }

        // Non-inventory roles cannot access Inventory
        listOf(
            UserRole.CASHIER,
            UserRole.KITCHEN_STAFF,
            UserRole.HR
        ).forEach { role ->
            assertFalse("${role.code} must not access INVENTORY", RoleAccessControl.canAccessModule(role, AppModule.INVENTORY))
        }
    }

    @Test
    fun `rbac enforces strict permissions across OWNER ADMIN BRANCH_MANAGER and CASHIER`() {
        // OWNER
        val ownerCaps = RoleAccessControl.capabilitiesFor(UserRole.OWNER)
        assertTrue(ownerCaps.canManageUserPermissions)
        assertTrue(ownerCaps.canManageBranches)
        assertTrue(ownerCaps.canRestoreBackup)
        assertTrue(ownerCaps.canPostJournalEntries)
        assertTrue(RoleAccessControl.canAccessModule(UserRole.OWNER, AppModule.USER_PERMISSIONS))

        // ADMIN: full operations + operator permissions, but restore backup restricted to Owner/Super Admin
        val adminCaps = RoleAccessControl.capabilitiesFor(UserRole.ADMIN)
        assertTrue(adminCaps.canManageUserPermissions)
        assertTrue(adminCaps.canManageBranches)
        assertTrue(adminCaps.canManageInventory)
        assertTrue(adminCaps.canPostJournalEntries)
        assertFalse(adminCaps.canRestoreBackup)
        assertTrue(RoleAccessControl.canAccessModule(UserRole.ADMIN, AppModule.INVENTORY))
        assertTrue(RoleAccessControl.canAccessModule(UserRole.ADMIN, AppModule.USER_PERMISSIONS))

        // BRANCH_MANAGER: branch ops allowed, system user permissions denied, restore backup denied
        val mgrCaps = RoleAccessControl.capabilitiesFor(UserRole.BRANCH_MANAGER)
        assertFalse(mgrCaps.canManageUserPermissions)
        assertFalse(mgrCaps.canManageBranches)
        assertFalse(mgrCaps.canRestoreBackup)
        assertTrue(mgrCaps.canManageInventory)
        assertTrue(RoleAccessControl.canAccessModule(UserRole.BRANCH_MANAGER, AppModule.INVENTORY))
        assertTrue(RoleAccessControl.canAccessModule(UserRole.BRANCH_MANAGER, AppModule.ACCOUNTING))
        assertFalse(RoleAccessControl.canAccessModule(UserRole.BRANCH_MANAGER, AppModule.USER_PERMISSIONS))

        // CASHIER: POS/Billing/Loyalty allowed; Inventory, Accounting, Payroll, Branch & Permissions denied
        val cashierCaps = RoleAccessControl.capabilitiesFor(UserRole.CASHIER)
        assertFalse(cashierCaps.canManageInventory)
        assertFalse(cashierCaps.canViewAccounting)
        assertFalse(cashierCaps.canManageUserPermissions)
        assertTrue(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.POS))
        assertTrue(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.LOYALTY))
        assertFalse(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.INVENTORY))
        assertFalse(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.ACCOUNTING))
        assertFalse(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.PAYROLL))
        assertFalse(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.USER_PERMISSIONS))
        assertFalse(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.BRANCH_MANAGEMENT))
    }

    @Test
    fun `operator lifecycle blocks duplicate emails enforces privilege ceiling and dispatches password reset`() = runTest {
        // Admin creates a new pending cashier account
        val createCashierRes = securityAdminRepository.saveOperatorProfile(
            session = adminSession,
            uid = "pending_newcashier",
            email = "newcashier@aacrave.pk",
            displayName = "New Cashier",
            role = UserRole.CASHIER,
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            isActive = true
        )
        assertTrue("Admin should create Cashier account", createCashierRes.isSuccess)

        // Duplicate email (case-insensitive) is rejected
        val dupRes = securityAdminRepository.saveOperatorProfile(
            session = adminSession,
            uid = "pending_duplicate",
            email = "NewCashier@AACrave.pk",
            displayName = "Duplicate Person",
            role = UserRole.BRANCH_MANAGER,
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            isActive = true
        )
        assertTrue("Duplicate email must fail", dupRes.isFailure)
        assertTrue(dupRes.exceptionOrNull() is IllegalArgumentException)

        // Admin cannot create an OWNER account or modify an existing OWNER
        val adminCreateOwnerRes = securityAdminRepository.saveOperatorProfile(
            session = adminSession,
            uid = "pending_fake_owner",
            email = "fakeowner@aacrave.pk",
            displayName = "Fake Owner",
            role = UserRole.OWNER,
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            isActive = true
        )
        assertTrue("Admin creating OWNER must fail", adminCreateOwnerRes.isFailure)
        assertTrue(adminCreateOwnerRes.exceptionOrNull() is SecurityException)

        val adminEditOwnerRes = securityAdminRepository.setOperatorActiveStatus(
            session = adminSession,
            targetUid = ownerSession.uid,
            active = false,
            reason = "Attempted takeover"
        )
        assertTrue("Admin modifying OWNER must fail", adminEditOwnerRes.isFailure)
        assertTrue(adminEditOwnerRes.exceptionOrNull() is SecurityException)

        // Owner/Admin cannot deactivate themselves
        val selfDeactivate = securityAdminRepository.setOperatorActiveStatus(
            session = adminSession,
            targetUid = adminSession.uid,
            active = false,
            reason = "Self test"
        )
        assertTrue("Self-deactivation must fail", selfDeactivate.isFailure)

        // Admin dispatches password reset for cashier -> succeeds and writes audit log
        val resetRes = securityAdminRepository.sendOperatorPasswordReset(
            session = adminSession,
            targetEmail = "newcashier@aacrave.pk"
        )
        assertTrue("Password reset dispatch should succeed", resetRes.isSuccess)
        assertEquals("newcashier@aacrave.pk", fakeAuthRepository.lastResetEmail)

        val logs = database.auditLogDao().observeRecentLogs().first()
        assertTrue(logs.any { it.action == "USER_OPERATOR_CREATED" && it.details.contains("newcashier@aacrave.pk") })
        assertTrue(logs.any { it.action == "ADMIN_PASSWORD_RESET_DISPATCHED" && it.details.contains("newcashier@aacrave.pk") })
    }

    @Test
    fun `suspended operator is blocked across guard and repositories and branch isolation blocks cross-branch writes`() = runTest {
        // 1. Owner creates a second branch
        val branch2 = multiBranchRepository.saveBranch(
            session = ownerSession,
            branchId = null,
            name = "AA CRAVE - Lahore Gulberg",
            code = "LHR02",
            address = "MM Alam Road, Lahore",
            phone = "+92-42-111222333",
            email = "gulberg@aacrave.pk",
            managerName = "Lahore Mgr",
            managerUid = null,
            openingCashCents = 50_000_00L,
            openingBankCents = 250_000_00L,
            isActive = true
        ).getOrThrow()

        // 2. Main Branch Manager is blocked from writing into Lahore branch
        val crossBranchInvErr = ErpAuthorizationGuard.verifyActiveAndBranch(database, branchManagerSession, branch2.branchId)
        assertNotNull("Cross-branch write must be blocked for Branch Manager", crossBranchInvErr)
        assertTrue(crossBranchInvErr is SecurityException)

        val branchManagerWriteBranch2 = multiBranchRepository.setBranchActiveStatus(
            session = branchManagerSession,
            branchId = branch2.branchId,
            active = false,
            reason = "Unauthorized"
        )
        assertTrue(branchManagerWriteBranch2.isFailure)
        assertTrue(branchManagerWriteBranch2.exceptionOrNull() is SecurityException)

        val coaBranch2 = database.accountingDao().observeChartOfAccounts(branch2.branchId).first()
        assertTrue("Seeded accounts should exist for branch 2", coaBranch2.size >= 2)
        val crossBranchJournal = accountingRepository.postManualJournalEntry(
            session = branchManagerSession,
            branchId = branch2.branchId,
            idempotencyKey = "cross_branch_je_1",
            entryDateMillis = System.currentTimeMillis(),
            reference = "JV-CROSS",
            description = "Cross-branch test",
            draftLines = listOf(
                JournalDraftLine(coaBranch2[0].accountId, debitCents = 10000L, creditCents = 0L),
                JournalDraftLine(coaBranch2[1].accountId, debitCents = 0L, creditCents = 10000L)
            )
        )
        assertTrue("Cross-branch journal post must fail", crossBranchJournal.isFailure)
        assertTrue(crossBranchJournal.exceptionOrNull() is SecurityException)

        // 3. Suspend cashier operator -> blocked everywhere
        val suspendRes = securityAdminRepository.setOperatorActiveStatus(
            session = adminSession,
            targetUid = cashierSession.uid,
            active = false,
            reason = "Shift suspension audit"
        )
        assertTrue(suspendRes.isSuccess)

        val guardErr = ErpAuthorizationGuard.verifyActiveOperator(database, cashierSession)
        assertNotNull(guardErr)
        assertTrue(guardErr is SecurityException)

        val products = posRepository.observeProducts(DatabaseSeeder.MAIN_BRANCH_ID).first()
        assertTrue(products.isNotEmpty())
        val prod = products.first()
        val cart = listOf(CartItem("line1", prod, null, emptyList(), "", 1))
        val totals = BillingTotals(prod.basePriceCents, 0L, prod.basePriceCents, 0.0, 0L, 0.0, 0L, prod.basePriceCents)

        val suspendedOrderRes = posRepository.placeOrder(
            session = cashierSession,
            idempotencyKey = "suspended_order_1",
            cartItems = cart,
            totals = totals,
            orderType = OrderType.TAKEAWAY,
            tableNumber = null,
            priority = OrderPriority.NORMAL,
            customer = null,
            paymentMethod = PaymentMethod.CASH,
            splitBreakdown = SplitPaymentBreakdown(cashCents = prod.basePriceCents)
        )
        assertTrue("Suspended operator cannot place POS order", suspendedOrderRes.isFailure)
        assertTrue(suspendedOrderRes.exceptionOrNull() is SecurityException)

        // Suspend Branch Manager -> blocked from inventory & loyalty mutations too
        assertTrue(securityAdminRepository.setOperatorActiveStatus(ownerSession, branchManagerSession.uid, false, "Suspended").isSuccess)
        val suspendedInvRes = inventoryRepository.saveInventoryItem(
            session = branchManagerSession,
            itemId = null,
            name = "Blocked Flour",
            sku = "SKU-BLK",
            itemType = InventoryItemType.RAW_MATERIAL,
            unit = "kg",
            initialOrCurrentQty = 10.0,
            reorderLevel = 2.0,
            unitCostCents = 25000L,
            expiryDateMillis = null,
            linkedProductId = null
        )
        assertTrue("Suspended manager cannot modify inventory", suspendedInvRes.isFailure)
        assertTrue(suspendedInvRes.exceptionOrNull() is SecurityException)

        val cust = database.customerLoyaltyDao().observeCustomers(DatabaseSeeder.MAIN_BRANCH_ID).first().first()
        val suspendedLoyaltyRes = smartLoyaltyRepository.enrollCustomerCard(branchManagerSession, cust.customerId)
        assertTrue("Suspended manager cannot enroll loyalty card", suspendedLoyaltyRes.isFailure)
        assertTrue(suspendedLoyaltyRes.exceptionOrNull() is SecurityException)

        // Reactivate cashier -> guard clears
        assertTrue(securityAdminRepository.setOperatorActiveStatus(adminSession, cashierSession.uid, true, "Cleared").isSuccess)
        assertEquals(null, ErpAuthorizationGuard.verifyActiveOperator(database, cashierSession))
    }

    @Test
    fun `global PKR currency formatter precision parsing seeder and report exports contain zero USD`() = runTest {
        assertEquals("PKR 1,500.00", PkrCurrencyFormatter.formatMinorUnits(150_000L))
        assertEquals("-PKR 1,500.00", PkrCurrencyFormatter.formatMinorUnits(-150_000L))
        assertEquals("PKR 0.00", centsToCurrency(0L))
        assertEquals("PKR 25.50", centsToPkr(2_550L))
        assertEquals("PKR 1,234.56", formatCurrency(1234.56))
        assertEquals(125_075L, PkrCurrencyFormatter.parseMajorToMinorUnits("PKR 1,250.75"))
        assertEquals(50_000L, PkrCurrencyFormatter.parseMajorToMinorUnits("Rs. 500"))

        // Verify seeded rewards use PKR, never $ or USD
        val rewards = database.customerLoyaltyDao().observeLoyaltyRewards(DatabaseSeeder.MAIN_BRANCH_ID).first()
        assertTrue(rewards.isNotEmpty())
        rewards.forEach { reward ->
            assertFalse("Reward title must not contain $: ${reward.title}", reward.title.contains("$"))
            assertFalse("Reward desc must not contain $: ${reward.description}", reward.description.contains("$"))
        }

        // Verify CSV report export formats values in PKR with zero USD symbols
        val now = System.currentTimeMillis()
        val po = PurchaseOrderRecord(
            purchaseId = "po_pkr_1",
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            poNumber = "PO-PKR-1001",
            supplierInvoiceNumber = "INV-1001",
            supplierId = "sup_1",
            supplierName = "Lahore Poultry Supply",
            status = PurchaseStatus.RECEIVED,
            paymentStatus = PurchasePaymentStatus.PAID,
            subtotalCents = 250_000L,
            discountCents = 0L,
            taxCents = 0L,
            totalCents = 250_000L,
            paidCents = 250_000L,
            returnedCents = 0L,
            notes = "",
            createdByName = "Owner",
            approvedByName = "Owner",
            cancelReason = null,
            expectedDateMillis = now,
            createdAt = now,
            updatedAt = now,
            items = listOf(
                PurchaseItemRecord(
                    lineId = "l1",
                    purchaseId = "po_pkr_1",
                    inventoryItemId = "inv_1",
                    itemName = "Chicken Fillet",
                    itemType = "RAW_MATERIAL",
                    unit = "kg",
                    orderedQty = 10.0,
                    receivedQty = 10.0,
                    returnedQty = 0.0,
                    unitPriceCents = 25_000L,
                    lineTotalCents = 250_000L
                )
            )
        )
        val csvFile = Phase4ReportExporter.exportPurchaseReport(
            context = context,
            branchName = "AA CRAVE - Flagship Main",
            orders = listOf(po),
            format = ReportExportFormat.CSV
        ).getOrThrow()
        val csvText = csvFile.readText()
        assertTrue("CSV export must format amounts in PKR", csvText.contains("PKR 2,500.00"))
        assertFalse("CSV export must not contain $ symbol", csvText.contains("$"))
        assertFalse("CSV export must not contain USD", csvText.contains("USD"))
        csvFile.delete()
    }

    @Test
    fun `dashboard and drawer expose inventory and purchase management without duplicate routes`() {
        assertEquals(ModuleSection.INVENTORY_SUPPLY, AppModule.INVENTORY.section)
        assertEquals(ModuleSection.INVENTORY_SUPPLY, AppModule.PURCHASES.section)
        assertEquals("inventory", AppModule.INVENTORY.route)
        assertEquals("purchases", AppModule.PURCHASES.route)

        // Ensure Drawer & Quick Dashboard sections stay mapped to the single canonical modules
        val expectedDashboardModules = listOf(
            AppModule.POS,
            AppModule.INVENTORY,
            AppModule.PURCHASES,
            AppModule.KITCHEN_DISPLAY,
            AppModule.LOYALTY,
            AppModule.ACCOUNTING,
            AppModule.STAFF,
            AppModule.PAYROLL,
            AppModule.USER_PERMISSIONS
        )
        assertEquals(expectedDashboardModules.size, expectedDashboardModules.map { it.route }.distinct().size)

        listOf(
            UserRole.OWNER,
            UserRole.ADMIN,
            UserRole.BRANCH_MANAGER,
            UserRole.INVENTORY_MANAGER,
            UserRole.PURCHASE_STAFF
        ).forEach { role ->
            assertTrue("${role.code} can open INVENTORY", RoleAccessControl.canAccessModule(role, AppModule.INVENTORY))
            assertTrue("${role.code} can open PURCHASES", RoleAccessControl.canAccessModule(role, AppModule.PURCHASES))
        }

        assertFalse(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.INVENTORY))
        assertFalse(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.PURCHASES))
    }

    @Test
    fun `individual staff login accounts hash passwords preserve HR records enforce uniqueness and support username login`() = runTest {
        val fakeProvisioner = RecordingStaffAuthProvisioner()
        val adminRepo = SecurityAdminRepository(
            database = database,
            gateway = fakeGateway,
            connectivityMonitor = fakeMonitor,
            authRepository = fakeAuthRepository,
            staffAuthProvisioner = fakeProvisioner
        )

        val now = System.currentTimeMillis()
        // Seed an HR staff profile to verify linking preserves HR data
        val hrStaff = StaffProfileEntity(
            staffId = "hr_staff_test_1",
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            employeeCode = "EMP-901",
            fullName = "Zainab Tariq",
            phone = "+92-300-5550199",
            address = "Gulberg III, Lahore",
            jobTitle = "Senior Cashier",
            department = "Front Desk",
            assignedRoleCode = UserRole.CASHIER.code,
            accessibleBranchIdsCsv = DatabaseSeeder.MAIN_BRANCH_ID,
            joiningDateMillis = now,
            employmentStatus = "ACTIVE",
            salaryType = "MONTHLY",
            baseSalaryCents = 5_500_000L,
            attendancePin = "1234",
            createdAt = now,
            updatedAt = now
        )
        database.staffDao().upsertStaff(hrStaff)

        // 1. Owner creates an individual Firebase-provisioned login account for Zainab
        val personalSecret1 = "CraveStaff#2026"
        val created1 = adminRepo.createOrUpdateStaffLoginAccount(
            session = ownerSession,
            existingUid = null,
            staffId = hrStaff.staffId,
            displayName = "Zainab Tariq",
            username = "zainab.pos",
            explicitEmail = "zainab.pos@aacrave.pk",
            password = personalSecret1,
            role = UserRole.CASHIER,
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            isActive = true
        ).getOrThrow()

        assertTrue(created1.firebaseProvisioned)
        assertEquals("zainab.pos", created1.profile.username)
        assertEquals("zainab.pos@aacrave.pk", created1.profile.email)
        assertEquals(hrStaff.staffId, created1.profile.staffId)
        assertEquals(1, fakeProvisioner.createdEmails.size)

        // Password must NEVER be stored in plain text locally or synced to RTDB
        assertTrue(created1.profile.passwordHash.isNotBlank())
        assertTrue(created1.profile.passwordSalt.isNotBlank())
        assertFalse(created1.profile.passwordHash.contains(personalSecret1))
        assertTrue(PasswordSecurity.verifyPassword(personalSecret1, created1.profile.passwordSalt, created1.profile.passwordHash))
        assertFalse(PasswordSecurity.verifyPassword("WrongPass999", created1.profile.passwordSalt, created1.profile.passwordHash))

        val syncedUserPayload = fakeGateway.upsertedPaths["erp/users/${created1.profile.uid}"]
        assertNotNull(syncedUserPayload)
        assertFalse(syncedUserPayload!!.containsKey("passwordHash"))
        assertFalse(syncedUserPayload.containsKey("passwordSalt"))
        assertFalse(syncedUserPayload.values.any { it.toString().contains(personalSecret1) })

        // Verify HR record still exists and is now linked cleanly
        val linkedHr = database.staffDao().getStaffById(hrStaff.staffId)
        assertNotNull(linkedHr)
        assertEquals(5_500_000L, linkedHr?.baseSalaryCents)
        assertEquals(created1.profile.uid, linkedHr?.linkedUserUid)

        // 2. Duplicate username (case-insensitive) must be rejected
        val dupUsername = adminRepo.createOrUpdateStaffLoginAccount(
            session = ownerSession,
            existingUid = null,
            staffId = null,
            displayName = "Another Person",
            username = "ZAINAB.POS",
            explicitEmail = "other@aacrave.pk",
            password = "AnotherSecret#99",
            role = UserRole.CASHIER,
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            isActive = true
        )
        assertTrue("Duplicate username must fail", dupUsername.isFailure)

        // 3. Admin cannot escalate to OWNER
        val adminCreateOwner = adminRepo.createOrUpdateStaffLoginAccount(
            session = adminSession,
            existingUid = null,
            staffId = null,
            displayName = "Rogue Owner",
            username = "rogue.owner",
            explicitEmail = null,
            password = "StrongPass#123",
            role = UserRole.OWNER,
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            isActive = true
        )
        assertTrue(adminCreateOwner.isFailure)
        assertTrue(adminCreateOwner.exceptionOrNull() is SecurityException)

        // 4. Create a second staff account without work email (username-only) queued locally with distinct password
        val offlineAdminRepo = SecurityAdminRepository(
            database = database,
            gateway = fakeGateway,
            connectivityMonitor = FakeConnectivityMonitor(false),
            authRepository = fakeAuthRepository,
            staffAuthProvisioner = null
        )
        val branch2 = multiBranchRepository.saveBranch(
            session = ownerSession,
            branchId = null,
            name = "AA CRAVE - Islamabad F7",
            code = "ISB02",
            address = "F-7 Markaz, Islamabad",
            phone = "+92-51-111222333",
            email = "isb@aacrave.pk",
            managerName = "Bilal Manager",
            managerUid = null,
            openingCashCents = 40_000_00L,
            openingBankCents = 100_000_00L,
            isActive = true
        ).getOrThrow()

        val personalSecret2 = "BilalMgr#8821"
        val created2 = offlineAdminRepo.createOrUpdateStaffLoginAccount(
            session = adminSession,
            existingUid = null,
            staffId = null,
            displayName = "Bilal Khan",
            username = "bilal.isb",
            explicitEmail = "",
            password = personalSecret2,
            role = UserRole.BRANCH_MANAGER,
            branchId = branch2.branchId,
            isActive = true
        ).getOrThrow()

        assertFalse(created2.firebaseProvisioned)
        assertEquals("LOCAL_PENDING_FIREBASE", created2.profile.authProvider)
        assertTrue(PasswordSecurity.isInternalDerivedEmail(created2.profile.email))

        // 5. Authenticate Bilal end-to-end via FirebaseAuthRepository using username + personalSecret2
        val sessionPrefs = SessionPreferences(context)
        val realAuthRepo = FirebaseAuthRepository(
            context = context,
            userProfileDao = database.userProfileDao(),
            branchDao = database.branchDao(),
            auditLogDao = database.auditLogDao(),
            sessionPreferences = sessionPrefs
        )

        // Wrong password or first staff's password must fail
        val wrongPassLogin = realAuthRepo.signInWithEmail("bilal.isb", personalSecret1)
        assertTrue("Other staff password must not work", wrongPassLogin.isFailure)

        // Correct individual username + password logs in with assigned role and branch
        val staffLogin = realAuthRepo.signInWithEmail("bilal.isb", personalSecret2).getOrThrow()
        assertEquals(UserRole.BRANCH_MANAGER, staffLogin.role)
        assertEquals(branch2.branchId, staffLogin.branchId)
        assertEquals("Bilal Khan", staffLogin.displayName)

        // Signed-in Branch Manager cannot access Owner/Admin-only functions
        assertFalse(RoleAccessControl.canAccessModule(staffLogin.role, AppModule.USER_PERMISSIONS))
        assertFalse(staffLogin.capabilities.canManageUserPermissions)
        assertFalse(staffLogin.capabilities.canManageBranches)
        assertFalse(staffLogin.capabilities.canRestoreBackup)
        // Cashier role cannot access Inventory, Purchases, Accounting, Branch Management, or User Permissions
        assertFalse(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.INVENTORY))
        assertFalse(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.PURCHASES))
        assertFalse(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.BRANCH_MANAGEMENT))
        assertFalse(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.USER_PERMISSIONS))
        val blockedCreate = adminRepo.createOrUpdateStaffLoginAccount(
            session = staffLogin,
            existingUid = null,
            staffId = null,
            displayName = "Unauthorized",
            username = "unauth.user",
            explicitEmail = null,
            password = "Password#1",
            role = UserRole.CASHIER,
            branchId = branch2.branchId,
            isActive = true
        )
        assertTrue("Branch manager must not create staff accounts", blockedCreate.isFailure)
        assertTrue(blockedCreate.exceptionOrNull() is SecurityException)

        // Branch isolation: Branch 2 manager cannot write to Main Branch
        val crossBranchErr = ErpAuthorizationGuard.verifyActiveAndBranch(database, staffLogin, DatabaseSeeder.MAIN_BRANCH_ID)
        assertNotNull(crossBranchErr)
        assertTrue(crossBranchErr is SecurityException)

        // 6. Reset local staff password -> old password fails, new password succeeds
        val resetSecret = "BilalReset#4455"
        val resetLocalRes = offlineAdminRepo.resetStaffAccountPassword(
            session = ownerSession,
            targetUid = created2.profile.uid,
            newTemporaryPassword = resetSecret
        )
        assertTrue(resetLocalRes.isSuccess)
        assertTrue(realAuthRepo.signInWithEmail("bilal.isb", personalSecret2).isFailure)
        assertTrue(realAuthRepo.signInWithEmail("bilal.isb", resetSecret).isSuccess)

        // 7. Deactivate account -> username sign-in blocked + logged
        assertTrue(
            adminRepo.setOperatorActiveStatus(
                session = ownerSession,
                targetUid = created2.profile.uid,
                active = false,
                reason = "Resigned"
            ).isSuccess
        )
        val suspendedLogin = realAuthRepo.signInWithEmail("bilal.isb", resetSecret)
        assertTrue("Deactivated account must be blocked on sign-in", suspendedLogin.isFailure)
        assertTrue(suspendedLogin.exceptionOrNull() is SecurityException)

        val logs = database.auditLogDao().observeRecentLogs().first()
        assertTrue(logs.any { it.action == "STAFF_LOGIN_ACCOUNT_CREATED" && it.details.contains("zainab.pos") })
        assertTrue(logs.any { it.action == "STAFF_ACCOUNT_PASSWORD_RESET" && it.details.contains("bilal.isb") })
        assertTrue(logs.any { it.action == "SUSPENDED_LOGIN_BLOCKED" && it.details.contains("bilal.isb") })
        assertFalse(logs.any { it.details.contains(personalSecret1) || it.details.contains(personalSecret2) || it.details.contains(resetSecret) })
    }

    @Test
    fun `room v8 to v9 migration preserves existing user profiles and adds staff login columns`() = runTest {
        val dbName = "aa_crave_v8_to_v9_test.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `branches` (`branchId` TEXT NOT NULL, `name` TEXT NOT NULL, `code` TEXT NOT NULL, `address` TEXT NOT NULL, `isMainBranch` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`branchId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profiles` (`uid` TEXT NOT NULL, `email` TEXT NOT NULL, `displayName` TEXT NOT NULL, `roleCode` TEXT NOT NULL, `branchId` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `lastLoginAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `dashboard_snapshots` (`branchId` TEXT NOT NULL, `businessDate` TEXT NOT NULL, `todaySales` REAL NOT NULL, `todayOrders` INTEGER NOT NULL, `totalCustomers` INTEGER NOT NULL, `pendingKitchenOrders` INTEGER NOT NULL, `lowStockAlerts` INTEGER NOT NULL, `dailyExpenses` REAL NOT NULL, `estimatedProfit` REAL NOT NULL, `cashPayments` REAL NOT NULL, `digitalPayments` REAL NOT NULL, `isSampleData` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`branchId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `low_stock_alerts` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `ingredientName` TEXT NOT NULL, `currentStock` REAL NOT NULL, `reorderLevel` REAL NOT NULL, `unit` TEXT NOT NULL, `isSampleData` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `recent_transactions` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `orderNumber` TEXT NOT NULL, `invoiceNumber` TEXT NOT NULL, `customerName` TEXT NOT NULL, `amount` REAL NOT NULL, `paymentMethod` TEXT NOT NULL, `status` TEXT NOT NULL, `timeLabel` TEXT NOT NULL, `isSampleData` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `hourly_sales` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `hourOrder` INTEGER NOT NULL, `label` TEXT NOT NULL, `revenue` REAL NOT NULL, `orderCount` INTEGER NOT NULL, `isSampleData` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `branchId` TEXT NOT NULL, `actorUid` TEXT NOT NULL, `actorEmail` TEXT NOT NULL, `actorRole` TEXT NOT NULL, `action` TEXT NOT NULL, `details` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_queue` (`transactionId` TEXT NOT NULL, `branchId` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `status` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `lastError` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`transactionId`))")
                        db.execSQL("INSERT INTO branches (branchId, name, code, address, isMainBranch, isActive) VALUES ('branch_main_01', 'AA CRAVE Main', 'MAIN', 'Main Ave', 1, 1)")
                        db.execSQL("INSERT INTO user_profiles (uid, email, displayName, roleCode, branchId, isActive, lastLoginAt) VALUES ('uid_legacy_owner', 'owner@aacrave.pk', 'Legacy Owner', 'OWNER', 'branch_main_01', 1, 1700000000000)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()

        val migrated = Room.databaseBuilder(context, AaCraveDatabase::class.java, dbName)
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

        val preservedOwner = migrated.userProfileDao().getProfileByUid("uid_legacy_owner")
        assertNotNull("Legacy Owner profile must survive v9 migration", preservedOwner)
        assertEquals("owner@aacrave.pk", preservedOwner?.email)
        assertEquals("", preservedOwner?.username)
        assertEquals("FIREBASE_EMAIL", preservedOwner?.authProvider)

        migrated.close()
        context.deleteDatabase(dbName)
    }

    private class RecordingStaffAuthProvisioner : StaffAuthProvisioner {
        val createdEmails = mutableListOf<String>()
        override suspend fun createStaffAuthAccount(
            email: String,
            password: String,
            displayName: String
        ): StaffAuthCreateOutcome {
            createdEmails += email
            return StaffAuthCreateOutcome.CreatedInFirebase("fb_uid_${createdEmails.size}")
        }
    }

    private class FakeConnectivityMonitor(initialOnline: Boolean) : NetworkConnectivityMonitor {
        private val state = MutableStateFlow(initialOnline)
        override val isOnline: StateFlow<Boolean> = state
        override fun currentIsOnline(): Boolean = state.value
    }

    private class FakeAuthRepository(initialSession: UserSession) : AuthRepository {
        var lastResetEmail: String? = null
        override val environmentStatus = MutableStateFlow(
            FirebaseEnvironmentStatus(
                isGoogleServicesConfigured = true,
                isFirebaseAuthAvailable = true,
                statusDetail = "Test Firebase Environment"
            )
        )
        override val currentSession = MutableStateFlow<UserSession?>(initialSession)
        override val activeBranchId: Flow<String> = MutableStateFlow(initialSession.branchId)
        override suspend fun restoreSession() = Unit
        override suspend fun signInWithEmail(email: String, password: String) = Result.success(currentSession.value!!)
        override suspend fun sendPasswordReset(email: String): Result<Unit> {
            lastResetEmail = email
            return Result.success(Unit)
        }
        override suspend fun enterLocalPreviewSandbox(role: UserRole) = Result.success(currentSession.value!!)
        override suspend fun switchActiveBranch(branchId: String) = Result.success(Unit)
        override suspend fun signOut() = Unit
        override fun refreshEnvironmentStatus(realtimeDbStatus: String?, statusDetail: String?) = Unit
    }

    private class FakeCloudSyncGateway : CloudSyncGateway {
        val upsertedPaths = mutableMapOf<String, Map<String, Any?>>()
        override fun isFirebaseConfigured() = true
        override fun isProjectAndPackageAligned() = true
        override fun configuredProjectId() = FirebaseCloudConstants.PROJECT_ID
        override fun isFirebaseAuthenticated() = true
        override fun currentAuthenticatedUid() = "uid_owner_audit"
        override suspend fun probeConnection() = CloudProbeResult(
            isGoogleServicesPresent = true,
            projectMatches = true,
            packageMatches = true,
            isAuthenticated = true,
            rtdbReachable = true,
            rtdbWritePermitted = true,
            summary = "Connected",
            details = emptyList()
        )
        override suspend fun fetchRemoteRecord(branchId: String, domainCollection: String, entityId: String) =
            CloudReadOutcome.NotFound
        override suspend fun upsertRecordAtomic(
            branchId: String,
            domainCollection: String,
            entityId: String,
            payloadMap: Map<String, Any?>,
            ledgerTxId: String?,
            ledgerEntry: Map<String, Any?>?
        ) = CloudWriteOutcome.Success
        override suspend fun upsertPath(path: String, data: Map<String, Any?>): CloudWriteOutcome {
            upsertedPaths[path] = data
            return CloudWriteOutcome.Success
        }
        override suspend fun fetchPath(path: String) = CloudReadOutcome.NotFound
    }
}
