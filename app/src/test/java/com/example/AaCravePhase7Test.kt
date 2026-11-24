package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.cloud.CloudReadOutcome
import com.example.data.cloud.CloudSyncGateway
import com.example.data.cloud.CloudWriteOutcome
import com.example.data.cloud.NetworkConnectivityMonitor
import com.example.data.cloud.RemoteCloudRecord
import com.example.data.local.AaCraveDatabase
import com.example.data.local.DatabaseSeeder
import com.example.data.local.SessionPreferences
import com.example.data.local.SyncQueueEntity
import com.example.data.repository.AuthRepository
import com.example.data.repository.BackupRestoreRepository
import com.example.data.repository.CloudSyncRepository
import com.example.data.repository.SecurityAdminRepository
import com.example.domain.model.AppModule
import com.example.domain.model.CloudProbeResult
import com.example.domain.model.CloudSyncPhase
import com.example.domain.model.ConflictResolutionStrategy
import com.example.domain.model.FirebaseCloudConstants
import com.example.domain.model.FirebaseEnvironmentStatus
import com.example.domain.model.RoleAccessControl
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
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
class AaCravePhase7Test {

    private lateinit var context: Context
    private lateinit var database: AaCraveDatabase
    private lateinit var sessionPreferences: SessionPreferences
    private lateinit var fakeMonitor: FakeConnectivityMonitor
    private lateinit var fakeGateway: FakeCloudSyncGateway
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var cloudSyncRepository: CloudSyncRepository
    private lateinit var backupRestoreRepository: BackupRestoreRepository
    private lateinit var securityAdminRepository: SecurityAdminRepository

    private val ownerFirebaseSession = UserSession(
        uid = "firebase_owner_1",
        email = "owner@aacrave.com",
        displayName = "Owner Admin",
        role = UserRole.OWNER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = true,
        isPreviewSandbox = false
    )

    private val managerFirebaseSession = UserSession(
        uid = "firebase_mgr_1",
        email = "mgr@aacrave.com",
        displayName = "Branch Manager",
        role = UserRole.BRANCH_MANAGER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = true,
        isPreviewSandbox = false
    )

    private val cashierSession = UserSession(
        uid = "firebase_cashier_1",
        email = "cashier@aacrave.com",
        displayName = "POS Cashier",
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
        sessionPreferences = SessionPreferences(context)
        fakeMonitor = FakeConnectivityMonitor(true)
        fakeGateway = FakeCloudSyncGateway()
        fakeAuthRepository = FakeAuthRepository(ownerFirebaseSession)

        cloudSyncRepository = CloudSyncRepository(
            context = context,
            database = database,
            sessionPreferences = sessionPreferences,
            connectivityMonitor = fakeMonitor,
            gateway = fakeGateway,
            authRepository = fakeAuthRepository
        )
        backupRestoreRepository = BackupRestoreRepository(
            context = context,
            database = database,
            sessionPreferences = sessionPreferences,
            connectivityMonitor = fakeMonitor,
            gateway = fakeGateway
        )
        securityAdminRepository = SecurityAdminRepository(
            database = database,
            gateway = fakeGateway,
            connectivityMonitor = fakeMonitor
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `offline and unconfigured states report honestly and preserve queued records`() = runTest {
        assertTrue(AppModule.BACKUP_RESTORE.isImplementedInCurrentPhase)
        assertTrue(AppModule.USER_PERMISSIONS.isImplementedInCurrentPhase)
        assertTrue(AppModule.NOTIFICATIONS.isImplementedInCurrentPhase)
        assertTrue(RoleAccessControl.canAccessModule(UserRole.OWNER, AppModule.BACKUP_RESTORE))
        assertTrue(RoleAccessControl.canAccessModule(UserRole.BRANCH_MANAGER, AppModule.BACKUP_RESTORE))
        assertFalse(RoleAccessControl.canAccessModule(UserRole.CASHIER, AppModule.BACKUP_RESTORE))

        // Stage snapshot while missing google-services.json
        fakeGateway.configured = false
        val stagedCount = cloudSyncRepository.enqueueAuthorizedSnapshot(ownerFirebaseSession).getOrThrow()
        assertTrue("Seeded ERP records should stage into Room outbox", stagedCount > 0)

        val unconfiguredSummary = cloudSyncRepository.syncPendingQueue(ownerFirebaseSession)
        assertEquals(CloudSyncPhase.CONFIG_MISSING, unconfiguredSummary.phase)
        assertEquals(0, unconfiguredSummary.syncedCount)
        assertTrue(database.syncQueueDao().observePendingCount().first() >= stagedCount)

        // Configure Firebase, go offline -> remains queued without failure
        fakeGateway.configured = true
        fakeMonitor.setOnline(false)
        val offlineSummary = cloudSyncRepository.syncPendingQueue(ownerFirebaseSession)
        assertEquals(CloudSyncPhase.OFFLINE, offlineSummary.phase)
        assertEquals(0, offlineSummary.syncedCount)
        assertTrue(database.syncQueueDao().observePendingCount().first() >= stagedCount)
    }

    @Test
    fun `online idempotent sync deduplicates repeats and handles locked rules retry cleanly`() = runTest {
        fakeGateway.configured = true
        fakeGateway.authenticated = true
        fakeMonitor.setOnline(true)

        val staged = cloudSyncRepository.enqueueAuthorizedSnapshot(ownerFirebaseSession).getOrThrow()
        assertTrue(staged > 0)

        // First attempt hits Locked Rules in RTDB
        fakeGateway.simulateLockedRules = true
        val lockedSummary = cloudSyncRepository.syncPendingQueue(ownerFirebaseSession)
        assertEquals(CloudSyncPhase.LOCKED_RULES, lockedSummary.phase)
        assertTrue(lockedSummary.failedCount > 0)
        assertEquals(0, fakeGateway.records.size)

        // Admin opens rules -> retry succeeds & records ledger entries
        fakeGateway.simulateLockedRules = false
        val retrySummary = cloudSyncRepository.syncPendingQueue(ownerFirebaseSession, includeRetryableFailed = true)
        assertEquals(CloudSyncPhase.ONLINE_READY, retrySummary.phase)
        assertTrue(retrySummary.syncedCount >= staged)
        assertEquals(0, retrySummary.failedCount)
        assertEquals(0, database.syncQueueDao().observePendingCount().first())
        val initialWriteCalls = fakeGateway.atomicWriteCount
        assertTrue(fakeGateway.ledger.isNotEmpty())

        // Re-staging without local edits produces 0 duplicates
        val secondStage = cloudSyncRepository.enqueueAuthorizedSnapshot(ownerFirebaseSession).getOrThrow()
        assertEquals(0, secondStage)
        assertEquals(initialWriteCalls, fakeGateway.atomicWriteCount)
    }

    @Test
    fun `multi device financial conflict quarantines record and enforces owner rbac resolution`() = runTest {
        fakeGateway.configured = true
        fakeGateway.authenticated = true
        fakeMonitor.setOnline(true)

        val tx = SyncQueueEntity(
            transactionId = "tx_conflict_order_1",
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            entityType = "POS_ORDER",
            entityId = "ord_shared_99",
            payloadJson = """{"orderId":"ord_shared_99","totalCents":2450,"status":"PAID"}""",
            status = "PENDING",
            retryCount = 0,
            createdAt = System.currentTimeMillis()
        )
        database.syncQueueDao().enqueueUnique(tx)

        // Remote device already wrote v2 with different checksum
        fakeGateway.records["erp/branches/${DatabaseSeeder.MAIN_BRANCH_ID}/orders/ord_shared_99"] =
            RemoteCloudRecord(
                entityId = "ord_shared_99",
                branchId = DatabaseSeeder.MAIN_BRANCH_ID,
                version = 2L,
                checksumSha256 = "different_remote_checksum_sha256",
                updatedAt = System.currentTimeMillis(),
                deviceId = "other_pos_terminal_02",
                actorName = "Another Cashier",
                data = mapOf("orderId" to "ord_shared_99", "totalCents" to 2890L, "status" to "REFUNDED")
            )

        val summary = cloudSyncRepository.syncPendingQueue(ownerFirebaseSession)
        assertEquals(1, summary.conflictCount)
        assertEquals(1, cloudSyncRepository.openConflictCount.first())

        val conflict = cloudSyncRepository.conflicts.first().first()
        assertTrue(conflict.isFinancialProtected)
        assertEquals("OPEN", conflict.status)

        // Branch Manager cannot resolve protected financial conflict
        val mgrAttempt = cloudSyncRepository.resolveConflict(
            managerFirebaseSession,
            conflict.conflictId,
            ConflictResolutionStrategy.KEEP_BOTH_ADJUSTED,
            "Manager trying financial override"
        )
        assertTrue(mgrAttempt.isFailure)

        // Owner resolves with mandatory audit note
        val ownerAttempt = cloudSyncRepository.resolveConflict(
            ownerFirebaseSession,
            conflict.conflictId,
            ConflictResolutionStrategy.KEEP_BOTH_ADJUSTED,
            "Verified receipt #99 refund voucher in cashbook"
        )
        assertTrue(ownerAttempt.isSuccess)
        assertEquals(0, cloudSyncRepository.openConflictCount.first())
        assertTrue(
            database.auditLogDao().observeRecentLogs().first().any { it.action == "SYNC_CONFLICT_RESOLVED" }
        )
    }

    @Test
    fun `backup creation sha256 verification tamper rejection and restore workflow`() = runTest {
        fakeGateway.configured = true
        fakeGateway.authenticated = true
        fakeMonitor.setOnline(true)

        val snapshot = backupRestoreRepository.createBackup(
            session = ownerFirebaseSession,
            branchScope = "ALL_BRANCHES",
            notes = "Pre-upgrade verified snapshot",
            uploadToCloud = true
        ).getOrThrow()
        assertTrue(snapshot.recordCount > 0)
        assertEquals("UPLOADED", snapshot.cloudStatus)

        val rawJson = backupRestoreRepository.readBackupFileJson(snapshot.backupId).getOrThrow()
        val validCheck = backupRestoreRepository.validateBackupJson(rawJson, ownerFirebaseSession)
        assertTrue(validCheck.isValid)
        assertTrue(validCheck.checksumMatch)

        // Tamper payload -> checksum must reject
        val tamperedEnvelope = JSONObject(rawJson).apply {
            put("payloadJson", getString("payloadJson").replace("AA CRAVE", "TAMPERED"))
        }.toString()
        val tamperedCheck = backupRestoreRepository.validateBackupJson(tamperedEnvelope, ownerFirebaseSession)
        assertFalse(tamperedCheck.isValid)
        assertFalse(tamperedCheck.checksumMatch)
        assertTrue(
            backupRestoreRepository.restoreFromBackupJson(ownerFirebaseSession, tamperedEnvelope, "RESTORE").isFailure
        )

        // Confirmation keyword guard & cashier RBAC guard
        assertTrue(
            backupRestoreRepository.restoreFromBackupJson(cashierSession, rawJson, "RESTORE").isFailure
        )
        assertTrue(
            backupRestoreRepository.restoreFromBackupJson(ownerFirebaseSession, rawJson, "wrong").isFailure
        )

        // Valid owner restore with exact RESTORE confirmation
        val restoredCount = backupRestoreRepository.restoreFromBackupJson(
            ownerFirebaseSession,
            rawJson,
            "RESTORE"
        ).getOrThrow()
        assertTrue(restoredCount > 0)
        val recLogs = backupRestoreRepository.recoveryLogs.first()
        assertTrue(recLogs.any { it.actionType == "RESTORE_REJECTED" && it.status == "REJECTED_CHECKSUM" })
        assertTrue(recLogs.any { it.actionType == "RESTORE_APPLIED" && it.status == "SUCCESS" })
    }

    @Test
    fun `device revocation blocks cloud sync and operator permission guards enforce anti self demotion`() = runTest {
        fakeGateway.configured = true
        fakeGateway.authenticated = true
        fakeMonitor.setOnline(true)

        val device = cloudSyncRepository.ensureCurrentDeviceRegistered(ownerFirebaseSession)
        assertTrue(device.isAuthorized)

        // Revoke current terminal
        securityAdminRepository.setDeviceAuthorized(ownerFirebaseSession, device.deviceId, authorized = false).getOrThrow()
        val blockedSync = cloudSyncRepository.syncPendingQueue(ownerFirebaseSession)
        assertEquals(CloudSyncPhase.DEVICE_REVOKED, blockedSync.phase)

        // Re-authorize terminal
        securityAdminRepository.setDeviceAuthorized(ownerFirebaseSession, device.deviceId, authorized = true).getOrThrow()
        assertTrue(cloudSyncRepository.ensureCurrentDeviceRegistered(ownerFirebaseSession).isAuthorized)

        // Anti-self-demotion guard
        val selfDemote = securityAdminRepository.saveOperatorProfile(
            session = ownerFirebaseSession,
            uid = ownerFirebaseSession.uid,
            email = ownerFirebaseSession.email,
            displayName = ownerFirebaseSession.displayName,
            role = UserRole.CASHIER,
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            isActive = true
        )
        assertTrue(selfDemote.isFailure)

        // Register & assign new Cashier operator
        securityAdminRepository.saveOperatorProfile(
            session = ownerFirebaseSession,
            uid = "firebase_new_op",
            email = "newpos@aacrave.com",
            displayName = "Bilal Cashier",
            role = UserRole.CASHIER,
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            isActive = true
        ).getOrThrow()
        assertNotNull(database.userProfileDao().getProfileByUid("firebase_new_op"))
        assertTrue(fakeGateway.paths.containsKey("erp/users/firebase_new_op"))

        // Notifications mark read
        assertTrue(securityAdminRepository.unreadNotificationCount.first() > 0)
        securityAdminRepository.markAllNotificationsRead()
        assertEquals(0, securityAdminRepository.unreadNotificationCount.first())
    }

    @Test
    fun `room v1 to v7 migration preserves legacy data and creates all phase 7 tables`() = runTest {
        val dbName = "aa_crave_p7_migration_test.db"
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

        listOf(
            "sync_record_versions",
            "sync_conflicts",
            "backup_snapshots",
            "recovery_logs",
            "device_sessions",
            "erp_notifications",
            "role_permission_policies"
        ).forEach { tbl ->
            val c = migratedDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$tbl`")
            assertTrue(c.moveToFirst())
            assertNotNull(c.getInt(0))
            c.close()
        }

        val br = migratedDb.branchDao().getBranchById("branch_main_01")
        assertNotNull(br)
        assertEquals("AA CRAVE Main", br?.name)

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    private class FakeConnectivityMonitor(initialOnline: Boolean) : NetworkConnectivityMonitor {
        private val state = MutableStateFlow(initialOnline)
        override val isOnline: StateFlow<Boolean> = state
        override fun currentIsOnline(): Boolean = state.value
        fun setOnline(online: Boolean) {
            state.value = online
        }
    }

    private class FakeAuthRepository(initialSession: UserSession) : AuthRepository {
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
        override suspend fun sendPasswordReset(email: String) = Result.success(Unit)
        override suspend fun enterLocalPreviewSandbox(role: UserRole) = Result.success(currentSession.value!!)
        override suspend fun switchActiveBranch(branchId: String) = Result.success(Unit)
        override suspend fun signOut() = Unit
        override fun refreshEnvironmentStatus(realtimeDbStatus: String?, statusDetail: String?) = Unit
    }

    private class FakeCloudSyncGateway : CloudSyncGateway {
        var configured = true
        var authenticated = true
        var simulateLockedRules = false
        var atomicWriteCount = 0
        val records = linkedMapOf<String, RemoteCloudRecord>()
        val ledger = linkedMapOf<String, Map<String, Any?>>()
        val paths = linkedMapOf<String, Map<String, Any?>>()

        override fun isFirebaseConfigured() = configured
        override fun isProjectAndPackageAligned() = configured
        override fun configuredProjectId() = if (configured) FirebaseCloudConstants.PROJECT_ID else null
        override fun isFirebaseAuthenticated() = configured && authenticated
        override fun currentAuthenticatedUid() = if (authenticated) "firebase_owner_1" else null

        override suspend fun probeConnection(): CloudProbeResult = CloudProbeResult(
            isGoogleServicesPresent = configured,
            projectMatches = configured,
            packageMatches = true,
            isAuthenticated = authenticated,
            rtdbReachable = configured && authenticated,
            rtdbWritePermitted = configured && authenticated && !simulateLockedRules,
            summary = if (simulateLockedRules) "Locked rules" else "Connected",
            details = emptyList()
        )

        override suspend fun fetchRemoteRecord(
            branchId: String,
            domainCollection: String,
            entityId: String
        ): CloudReadOutcome {
            if (!configured || !authenticated) return CloudReadOutcome.Error("Not ready")
            if (simulateLockedRules) return CloudReadOutcome.Error("Permission denied", isLockedRules = true)
            val rec = records["erp/branches/$branchId/$domainCollection/$entityId"]
                ?: return CloudReadOutcome.NotFound
            return CloudReadOutcome.Found(rec)
        }

        override suspend fun upsertRecordAtomic(
            branchId: String,
            domainCollection: String,
            entityId: String,
            payloadMap: Map<String, Any?>,
            ledgerTxId: String?,
            ledgerEntry: Map<String, Any?>?
        ): CloudWriteOutcome {
            if (!configured) return CloudWriteOutcome.ConfigMissing("Missing google-services.json")
            if (!authenticated) return CloudWriteOutcome.AuthRequired("Sign in required")
            if (simulateLockedRules) return CloudWriteOutcome.LockedRules("Permission denied")

            atomicWriteCount++
            records["erp/branches/$branchId/$domainCollection/$entityId"] = RemoteCloudRecord(
                entityId = entityId,
                branchId = branchId,
                version = (payloadMap["version"] as? Number)?.toLong() ?: 1L,
                checksumSha256 = payloadMap["checksumSha256"]?.toString().orEmpty(),
                updatedAt = (payloadMap["updatedAt"] as? Number)?.toLong() ?: 0L,
                deviceId = payloadMap["deviceId"]?.toString().orEmpty(),
                actorName = payloadMap["actorName"]?.toString().orEmpty(),
                data = payloadMap
            )
            if (!ledgerTxId.isNullOrBlank() && ledgerEntry != null) {
                ledger["erp/branches/$branchId/sync_ledger/$ledgerTxId"] = ledgerEntry
            }
            return CloudWriteOutcome.Success
        }

        override suspend fun upsertPath(path: String, data: Map<String, Any?>): CloudWriteOutcome {
            if (!configured) return CloudWriteOutcome.ConfigMissing("Missing google-services.json")
            if (!authenticated) return CloudWriteOutcome.AuthRequired("Sign in required")
            if (simulateLockedRules) return CloudWriteOutcome.LockedRules("Permission denied")
            paths[path] = data
            return CloudWriteOutcome.Success
        }

        override suspend fun fetchPath(path: String): CloudReadOutcome {
            val found = paths[path] ?: return CloudReadOutcome.NotFound
            return CloudReadOutcome.RawFound(found)
        }
    }
}
