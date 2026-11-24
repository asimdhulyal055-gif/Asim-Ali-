package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.data.cloud.CloudReadOutcome
import com.example.data.cloud.CloudSyncGateway
import com.example.data.cloud.CloudWriteOutcome
import com.example.data.cloud.NetworkConnectivityMonitor
import com.example.data.cloud.RemoteCloudRecord
import com.example.data.cloud.Sha256Util
import com.example.data.local.AaCraveDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.DeviceSessionEntity
import com.example.data.local.ErpNotificationEntity
import com.example.data.local.SessionPreferences
import com.example.data.local.SyncConflictEntity
import com.example.data.local.SyncMetadataEntity
import com.example.data.local.SyncQueueEntity
import com.example.domain.model.CloudProbeResult
import com.example.domain.model.CloudSyncPhase
import com.example.domain.model.CloudSyncStatus
import com.example.domain.model.ConflictResolutionStrategy
import com.example.domain.model.FirebaseCloudConstants
import com.example.domain.model.SyncDomain
import com.example.domain.model.UserSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

data class SyncRunSummary(
    val syncedCount: Int,
    val deduplicatedCount: Int,
    val conflictCount: Int,
    val failedCount: Int,
    val phase: CloudSyncPhase,
    val message: String
)

class CloudSyncRepository(
    private val context: Context,
    private val database: AaCraveDatabase,
    private val sessionPreferences: SessionPreferences,
    private val connectivityMonitor: NetworkConnectivityMonitor,
    private val gateway: CloudSyncGateway,
    private val authRepository: AuthRepository
) {
    private val phase7Dao = database.phase7Dao()
    private val syncQueueDao = database.syncQueueDao()
    private val auditLogDao = database.auditLogDao()
    private val syncMutex = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    private val _lastSyncResultMessage = MutableStateFlow("Local Room offline-first mode active.")
    private val _lastPhaseOverride = MutableStateFlow<CloudSyncPhase?>(null)

    val syncQueue: Flow<List<SyncQueueEntity>> = phase7Dao.observeAllSyncQueue()
    val conflicts: Flow<List<SyncConflictEntity>> = phase7Dao.observeAllConflicts()
    val devices: Flow<List<DeviceSessionEntity>> = phase7Dao.observeDevices()
    val openConflictCount: Flow<Int> = phase7Dao.observeOpenConflictCount()

    fun observeCloudSyncStatus(session: UserSession?): Flow<CloudSyncStatus> {
        return combine(
            connectivityMonitor.isOnline,
            syncQueueDao.observePendingCount(),
            phase7Dao.observeFailedQueueCount(),
            phase7Dao.observeOpenConflictCount(),
            phase7Dao.observeSyncedRecordCount(),
            sessionPreferences.lastCloudSyncAt,
            phase7Dao.observeDevices(),
            _isSyncing,
            _lastSyncResultMessage,
            _lastPhaseOverride
        ) { args ->
            val online = args[0] as Boolean
            val pending = args[1] as Int
            val failed = args[2] as Int
            val openConflicts = args[3] as Int
            val syncedRecords = args[4] as Int
            val lastSyncAt = args[5] as Long?
            @Suppress("UNCHECKED_CAST")
            val devicesList = args[6] as List<DeviceSessionEntity>
            val syncing = args[7] as Boolean
            val lastMsg = args[8] as String
            val overridePhase = args[9] as CloudSyncPhase?

            val currentDev = devicesList.firstOrNull { it.isCurrentDevice }
            val deviceAuthorized = currentDev?.isAuthorized ?: true
            val firebaseConfigured = gateway.isFirebaseConfigured()
            val firebaseAuthed = (session?.isFirebaseAuthenticated == true) || gateway.isFirebaseAuthenticated()

            val computedPhase = when {
                !deviceAuthorized -> CloudSyncPhase.DEVICE_REVOKED
                syncing -> CloudSyncPhase.SYNCING
                !online -> CloudSyncPhase.OFFLINE
                !firebaseConfigured -> CloudSyncPhase.CONFIG_MISSING
                !firebaseAuthed -> CloudSyncPhase.AUTH_REQUIRED
                overridePhase == CloudSyncPhase.LOCKED_RULES -> CloudSyncPhase.LOCKED_RULES
                overridePhase == CloudSyncPhase.ERROR -> CloudSyncPhase.ERROR
                else -> CloudSyncPhase.ONLINE_READY
            }

            CloudSyncStatus(
                isNetworkOnline = online,
                isFirebaseConfigured = firebaseConfigured,
                isFirebaseAuthenticated = firebaseAuthed,
                isDeviceAuthorized = deviceAuthorized,
                isSyncing = syncing,
                phase = computedPhase,
                projectId = gateway.configuredProjectId() ?: FirebaseCloudConstants.PROJECT_ID,
                databaseUrl = FirebaseCloudConstants.RTDB_URL,
                deviceId = currentDev?.deviceId.orEmpty(),
                pendingCount = pending,
                failedCount = failed,
                openConflictCount = openConflicts,
                syncedRecordCount = syncedRecords,
                lastSyncAt = lastSyncAt,
                lastResultMessage = lastMsg
            )
        }
    }

    suspend fun ensureCurrentDeviceRegistered(session: UserSession): DeviceSessionEntity = withContext(Dispatchers.IO) {
        val deviceId = sessionPreferences.getOrCreateDeviceId()
        val existing = phase7Dao.getDeviceById(deviceId)
        val now = System.currentTimeMillis()
        val deviceName = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "AA CRAVE Terminal" }

        val updated = DeviceSessionEntity(
            deviceId = deviceId,
            deviceName = existing?.deviceName ?: deviceName,
            osVersion = "Android ${Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT}",
            appVersion = "Phase 7 (1.0)",
            operatorUid = session.uid,
            operatorEmail = session.email,
            operatorRole = session.role.displayName,
            branchId = session.branchId,
            syncStatus = when {
                existing != null && !existing.isAuthorized -> "REVOKED"
                !connectivityMonitor.currentIsOnline() -> "OFFLINE"
                else -> "ACTIVE"
            },
            isAuthorized = existing?.isAuthorized ?: true,
            isCurrentDevice = true,
            lastSeenAt = now,
            lastSyncAt = existing?.lastSyncAt,
            createdAt = existing?.createdAt ?: now
        )
        phase7Dao.upsertDevice(updated)

        if (updated.isAuthorized && session.isFirebaseAuthenticated && gateway.isFirebaseConfigured() && connectivityMonitor.currentIsOnline()) {
            gateway.upsertPath(
                "erp/devices/$deviceId",
                mapOf(
                    "deviceId" to updated.deviceId,
                    "deviceName" to updated.deviceName,
                    "osVersion" to updated.osVersion,
                    "operatorUid" to session.uid,
                    "operatorEmail" to session.email,
                    "operatorRole" to session.role.code,
                    "branchId" to session.branchId,
                    "isAuthorized" to updated.isAuthorized,
                    "lastSeenAt" to now
                )
            )
        }
        updated
    }

    suspend fun probeCloudConnection(session: UserSession): CloudProbeResult = withContext(Dispatchers.IO) {
        ensureCurrentDeviceRegistered(session)
        val result = gateway.probeConnection()
        val online = connectivityMonitor.currentIsOnline()
        val rtdbStatus = when {
            !result.isGoogleServicesPresent -> "Not Linked (Missing google-services.json)"
            !online -> "Offline — Local Room Persistence Active"
            !result.isAuthenticated -> "Configured • Awaiting Email/Password Sign-In"
            result.rtdbWritePermitted -> "Connected & Verified (${FirebaseCloudConstants.PROJECT_ID})"
            result.rtdbReachable -> "Rules Locked / Operator Profile Pending in RTDB"
            else -> "Unreachable (${result.summary})"
        }
        authRepository.refreshEnvironmentStatus(rtdbStatus, result.summary)
        if (result.isGoogleServicesPresent && result.isAuthenticated && !result.rtdbWritePermitted && result.rtdbReachable) {
            _lastPhaseOverride.value = CloudSyncPhase.LOCKED_RULES
        } else if (result.rtdbWritePermitted) {
            _lastPhaseOverride.value = null
        }
        _lastSyncResultMessage.value = result.summary
        result
    }

    suspend fun enqueueAuthorizedSnapshot(session: UserSession): Result<Int> = withContext(Dispatchers.IO) {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return@withContext Result.failure(it)
        }
        if (!session.capabilities.canTriggerCloudSync) {
            return@withContext Result.failure(SecurityException("Role ${session.role.displayName} is not authorized to stage cloud sync."))
        }
        val device = ensureCurrentDeviceRegistered(session)
        if (!device.isAuthorized) {
            return@withContext Result.failure(SecurityException("This device (${device.deviceId}) has been revoked by an administrator."))
        }

        val branchIds = if (session.capabilities.canSwitchBranches) {
            database.branchDao().getAllBranches().map { it.branchId }
        } else {
            listOf(session.branchId)
        }

        var enqueued = 0
        val now = System.currentTimeMillis()
        branchIds.forEach { bId ->
            val branch = database.branchDao().getBranchById(bId)
            if (branch != null) {
                enqueued += stageEntityIfChanged(
                    branchId = bId,
                    entityType = "BRANCH",
                    entityId = branch.branchId,
                    payload = """{"branchId":"${branch.branchId}","name":${JSONObject.quote(branch.name)},"code":${JSONObject.quote(branch.code)},"isActive":${branch.isActive},"updatedAt":${branch.updatedAt}}""",
                    actorUid = session.uid,
                    deviceId = device.deviceId,
                    timestamp = now
                )
            }
            database.posDao().observeCustomers(bId).first().forEach { cust ->
                enqueued += stageEntityIfChanged(
                    branchId = bId,
                    entityType = "CUSTOMER",
                    entityId = cust.customerId,
                    payload = """{"customerId":"${cust.customerId}","name":${JSONObject.quote(cust.name)},"tier":${JSONObject.quote(cust.tier)},"points":${cust.loyaltyPoints},"balanceCents":${cust.accountBalanceCents}}""",
                    actorUid = session.uid,
                    deviceId = device.deviceId,
                    timestamp = now
                )
            }
            database.posDao().observeOrders(bId).first().forEach { ord ->
                enqueued += stageEntityIfChanged(
                    branchId = bId,
                    entityType = "POS_ORDER",
                    entityId = ord.orderId,
                    payload = """{"orderId":"${ord.orderId}","idempotencyKey":${JSONObject.quote(ord.idempotencyKey)},"orderNumber":${JSONObject.quote(ord.orderNumber)},"invoiceNumber":${JSONObject.quote(ord.invoiceNumber)},"totalCents":${ord.totalCents},"paidCents":${ord.paidCents},"status":${JSONObject.quote(ord.status)}}""",
                    actorUid = session.uid,
                    deviceId = device.deviceId,
                    timestamp = now
                )
            }
            database.posDao().observeProducts(bId).first().forEach { prod ->
                enqueued += stageEntityIfChanged(
                    branchId = bId,
                    entityType = "MENU_PRODUCT",
                    entityId = prod.productId,
                    payload = """{"productId":"${prod.productId}","name":${JSONObject.quote(prod.name)},"priceCents":${prod.basePriceCents},"isAvailable":${prod.isAvailable},"isArchived":${prod.isArchived}}""",
                    actorUid = session.uid,
                    deviceId = device.deviceId,
                    timestamp = now
                )
            }
            database.inventoryDao().observeInventoryItems(bId).first().forEach { inv ->
                enqueued += stageEntityIfChanged(
                    branchId = bId,
                    entityType = "INVENTORY_ITEM",
                    entityId = inv.itemId,
                    payload = """{"itemId":"${inv.itemId}","name":${JSONObject.quote(inv.name)},"qty":${inv.currentQty},"unit":${JSONObject.quote(inv.unit)},"unitCostCents":${inv.unitCostCents}}""",
                    actorUid = session.uid,
                    deviceId = device.deviceId,
                    timestamp = now
                )
            }
            database.supplierDao().observeSuppliers(bId).first().forEach { sup ->
                enqueued += stageEntityIfChanged(
                    branchId = bId,
                    entityType = "SUPPLIER",
                    entityId = sup.supplierId,
                    payload = """{"supplierId":"${sup.supplierId}","name":${JSONObject.quote(sup.name)},"totalPurchasesCents":${sup.totalPurchasesCents},"totalPaidCents":${sup.totalPaidCents}}""",
                    actorUid = session.uid,
                    deviceId = device.deviceId,
                    timestamp = now
                )
            }
            database.purchaseDao().observePurchaseOrders(bId).first().forEach { po ->
                enqueued += stageEntityIfChanged(
                    branchId = bId,
                    entityType = "PURCHASE_ORDER",
                    entityId = po.purchaseId,
                    payload = """{"purchaseId":"${po.purchaseId}","poNumber":${JSONObject.quote(po.poNumber)},"totalCents":${po.totalCents},"status":${JSONObject.quote(po.status)}}""",
                    actorUid = session.uid,
                    deviceId = device.deviceId,
                    timestamp = now
                )
            }
            database.expenseDao().observeExpenses(bId).first().forEach { exp ->
                enqueued += stageEntityIfChanged(
                    branchId = bId,
                    entityType = "EXPENSE",
                    entityId = exp.expenseId,
                    payload = """{"expenseId":"${exp.expenseId}","category":${JSONObject.quote(exp.category)},"amountCents":${exp.amountCents},"approvalStatus":${JSONObject.quote(exp.approvalStatus)}}""",
                    actorUid = session.uid,
                    deviceId = device.deviceId,
                    timestamp = now
                )
            }
            if (session.capabilities.canManageStaff || session.capabilities.canSwitchBranches) {
                database.staffDao().getStaffListForBranch(bId).forEach { staff ->
                    enqueued += stageEntityIfChanged(
                        branchId = bId,
                        entityType = "STAFF_PROFILE",
                        entityId = staff.staffId,
                        payload = """{"staffId":"${staff.staffId}","employeeCode":${JSONObject.quote(staff.employeeCode)},"fullName":${JSONObject.quote(staff.fullName)},"roleCode":${JSONObject.quote(staff.assignedRoleCode)},"isActive":${staff.isActive}}""",
                        actorUid = session.uid,
                        deviceId = device.deviceId,
                        timestamp = now
                    )
                }
                database.shiftAttendanceDao().observeAttendance(bId).first().forEach { att ->
                    enqueued += stageEntityIfChanged(
                        branchId = bId,
                        entityType = "ATTENDANCE",
                        entityId = att.attendanceId,
                        payload = """{"attendanceId":"${att.attendanceId}","staffId":"${att.staffId}","date":${JSONObject.quote(att.attendanceDateStr)},"status":${JSONObject.quote(att.status)},"workedMinutes":${att.workedMinutes}}""",
                        actorUid = session.uid,
                        deviceId = device.deviceId,
                        timestamp = now
                    )
                }
            }
            if (session.capabilities.canViewConfidentialSalary || session.capabilities.canSwitchBranches) {
                database.leavePayrollDao().observePayrollRecords(bId).first().forEach { pay ->
                    enqueued += stageEntityIfChanged(
                        branchId = bId,
                        entityType = "PAYROLL",
                        entityId = pay.payrollId,
                        payload = """{"payrollId":"${pay.payrollId}","staffId":"${pay.staffId}","periodKey":${JSONObject.quote(pay.periodKey)},"netSalaryCents":${pay.netSalaryCents},"status":${JSONObject.quote(pay.status)}}""",
                        actorUid = session.uid,
                        deviceId = device.deviceId,
                        timestamp = now
                    )
                }
            }
            if (session.capabilities.canViewAccounting) {
                database.accountingDao().observeJournalEntries(bId).first().forEach { je ->
                    enqueued += stageEntityIfChanged(
                        branchId = bId,
                        entityType = "JOURNAL_ENTRY",
                        entityId = je.entryId,
                        payload = """{"entryId":"${je.entryId}","entryNumber":${JSONObject.quote(je.entryNumber)},"totalDebitCents":${je.totalDebitCents},"totalCreditCents":${je.totalCreditCents},"status":${JSONObject.quote(je.status)}}""",
                        actorUid = session.uid,
                        deviceId = device.deviceId,
                        timestamp = now
                    )
                }
            }
            if (session.capabilities.canViewLoyalty) {
                val smartDao = database.smartLoyaltyDao()
                smartDao.getSettings(bId)?.let { cfg ->
                    enqueued += stageEntityIfChanged(
                        branchId = bId,
                        entityType = "LOYALTY_SETTINGS",
                        entityId = cfg.branchId,
                        payload = """{"branchId":"${cfg.branchId}","enabled":${cfg.programEnabled},"minQualifyingCents":${cfg.minQualifyingAmountCents},"requiredStamps":${cfg.requiredStampsForReward},"rewardAmountCents":${cfg.rewardAmountCents},"validityMonths":${cfg.validityMonths},"graceDays":${cfg.redemptionGraceDays}}""",
                        actorUid = session.uid,
                        deviceId = device.deviceId,
                        timestamp = now
                    )
                }
                smartDao.observeCardsForBranch(bId).first().forEach { card ->
                    enqueued += stageEntityIfChanged(
                        branchId = bId,
                        entityType = "LOYALTY_CARD",
                        entityId = card.cardId,
                        payload = """{"cardId":"${card.cardId}","cardNumber":${JSONObject.quote(card.cardNumber)},"customerId":"${card.customerId}","status":${JSONObject.quote(card.status)},"currentCycleStamps":${card.currentCycleStamps},"totalStamps":${card.totalStampsEarned},"cycleCount":${card.cycleCount},"expiresAt":${card.expiresAt ?: 0L}}""",
                        actorUid = session.uid,
                        deviceId = device.deviceId,
                        timestamp = now
                    )
                }
                smartDao.observeStampTransactionsForBranch(bId).first().forEach { stx ->
                    enqueued += stageEntityIfChanged(
                        branchId = bId,
                        entityType = "LOYALTY_STAMP",
                        entityId = stx.stampTxId,
                        payload = """{"stampTxId":"${stx.stampTxId}","cardId":"${stx.cardId}","orderId":${JSONObject.quote(stx.orderId ?: "")},"txType":${JSONObject.quote(stx.txType)},"delta":${stx.stampsDelta},"stampsAfter":${stx.stampsAfter},"idempotencyKey":${JSONObject.quote(stx.idempotencyKey)}}""",
                        actorUid = session.uid,
                        deviceId = device.deviceId,
                        timestamp = now
                    )
                }
                smartDao.observeVouchersForBranch(bId).first().forEach { v ->
                    enqueued += stageEntityIfChanged(
                        branchId = bId,
                        entityType = "LOYALTY_VOUCHER",
                        entityId = v.voucherId,
                        payload = """{"voucherId":"${v.voucherId}","voucherCode":${JSONObject.quote(v.voucherCode)},"cardId":"${v.cardId}","valueCents":${v.rewardValueCents},"status":${JSONObject.quote(v.status)},"redeemedOrderId":${JSONObject.quote(v.redeemedOrderId ?: "")}}""",
                        actorUid = session.uid,
                        deviceId = device.deviceId,
                        timestamp = now
                    )
                }
                smartDao.observeRedemptionsForBranch(bId).first().forEach { red ->
                    enqueued += stageEntityIfChanged(
                        branchId = bId,
                        entityType = "LOYALTY_REDEMPTION",
                        entityId = red.redemptionId,
                        payload = """{"redemptionId":"${red.redemptionId}","voucherId":"${red.voucherId}","orderId":"${red.orderId}","discountAppliedCents":${red.discountAppliedCents},"actualCogsCostCents":${red.actualCogsCostCents}}""",
                        actorUid = session.uid,
                        deviceId = device.deviceId,
                        timestamp = now
                    )
                }
            }
        }
        _lastSyncResultMessage.value = if (enqueued > 0) {
            "Staged $enqueued new/updated ERP records for cloud synchronization."
        } else {
            "All authorized local records already queued or up-to-date (no duplicates added)."
        }
        Result.success(enqueued)
    }

    private suspend fun stageEntityIfChanged(
        branchId: String,
        entityType: String,
        entityId: String,
        payload: String,
        actorUid: String,
        deviceId: String,
        timestamp: Long
    ): Int {
        val checksum = Sha256Util.sha256Hex(payload)
        val existingMeta = phase7Dao.getSyncMetadata(branchId, entityType, entityId)
        if (existingMeta != null && existingMeta.contentSha256 == checksum && existingMeta.syncState != "LOCAL_ONLY") {
            return 0 // Deduplicate unchanged snapshot records
        }
        val nextVersion = (existingMeta?.recordVersion ?: 0L) + 1L
        val domain = SyncDomain.fromEntityType(entityType)
        val txId = "sync_${entityType.lowercase()}_${entityId}_v$nextVersion"

        phase7Dao.upsertSyncMetadata(
            SyncMetadataEntity(
                recordKey = "${branchId}__${entityType}__${entityId}",
                branchId = branchId,
                entityType = entityType,
                entityId = entityId,
                domainCollection = domain.collectionKey,
                recordVersion = nextVersion,
                contentSha256 = checksum,
                deviceId = deviceId,
                updatedByUid = actorUid,
                updatedAt = timestamp,
                lastSyncedAt = existingMeta?.lastSyncedAt,
                syncState = "PENDING_PUSH"
            )
        )
        syncQueueDao.enqueueUnique(
            SyncQueueEntity(
                transactionId = txId,
                branchId = branchId,
                entityType = entityType,
                entityId = entityId,
                payloadJson = payload,
                status = "PENDING",
                retryCount = 0,
                lastError = null,
                createdAt = timestamp
            )
        )
        return 1
    }

    suspend fun syncPendingQueue(session: UserSession, includeRetryableFailed: Boolean = true): SyncRunSummary =
        withContext(Dispatchers.IO) {
            syncMutex.withLock {
                ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let { err ->
                    val msg = err.message ?: "Operator account is suspended. Cloud synchronization blocked."
                    _lastPhaseOverride.value = CloudSyncPhase.AUTH_REQUIRED
                    _lastSyncResultMessage.value = msg
                    return@withLock SyncRunSummary(0, 0, 0, 0, CloudSyncPhase.AUTH_REQUIRED, msg)
                }
                val device = ensureCurrentDeviceRegistered(session)
                if (!device.isAuthorized) {
                    val msg = "Device ${device.deviceId} is revoked. Cloud synchronization blocked by security policy."
                    _lastPhaseOverride.value = CloudSyncPhase.DEVICE_REVOKED
                    _lastSyncResultMessage.value = msg
                    emitSyncNotification(
                        branchId = session.branchId,
                        category = "SECURITY",
                        severity = "CRITICAL",
                        title = "Revoked Device Sync Blocked",
                        message = msg
                    )
                    return@withLock SyncRunSummary(0, 0, 0, 0, CloudSyncPhase.DEVICE_REVOKED, msg)
                }

                if (!connectivityMonitor.currentIsOnline()) {
                    val msg = "Offline mode active. All pending records remain safely queued in Room."
                    _lastSyncResultMessage.value = msg
                    return@withLock SyncRunSummary(0, 0, 0, 0, CloudSyncPhase.OFFLINE, msg)
                }

                if (!gateway.isFirebaseConfigured()) {
                    val msg = "Missing app/google-services.json for aa-crave-erp. Records retained safely in Room."
                    _lastSyncResultMessage.value = msg
                    return@withLock SyncRunSummary(0, 0, 0, 0, CloudSyncPhase.CONFIG_MISSING, msg)
                }

                if (!session.isFirebaseAuthenticated || !gateway.isFirebaseAuthenticated()) {
                    val msg = "Sign in with Firebase Email/Password to upload queued records to aa-crave-erp."
                    _lastSyncResultMessage.value = msg
                    return@withLock SyncRunSummary(0, 0, 0, 0, CloudSyncPhase.AUTH_REQUIRED, msg)
                }

                _isSyncing.value = true
                phase7Dao.upsertDevice(device.copy(syncStatus = "SYNCING", lastSeenAt = System.currentTimeMillis()))

                var synced = 0
                var deduped = 0
                var conflicts = 0
                var failed = 0
                var finalPhase = CloudSyncPhase.ONLINE_READY
                var finalMessage = "Synchronization complete."

                try {
                    val candidates = phase7Dao.getPendingOrFailedQueue().filter { item ->
                        if (item.status == "CONFLICT") return@filter false
                        if (item.status != "PENDING" && !includeRetryableFailed) return@filter false
                        session.capabilities.canSwitchBranches || item.branchId == session.branchId
                    }

                    if (candidates.isEmpty()) {
                        val now = System.currentTimeMillis()
                        sessionPreferences.setLastCloudSyncAt(now)
                        phase7Dao.upsertDevice(device.copy(syncStatus = "ACTIVE", lastSeenAt = now, lastSyncAt = now))
                        finalMessage = "All authorized records are already synchronized."
                        _lastSyncResultMessage.value = finalMessage
                        return@withLock SyncRunSummary(0, 0, 0, 0, CloudSyncPhase.ONLINE_READY, finalMessage)
                    }

                    for (item in candidates) {
                        if (item.retryCount >= FirebaseCloudConstants.MAX_AUTO_RETRIES && !includeRetryableFailed) {
                            continue
                        }
                        val domain = SyncDomain.fromEntityType(item.entityType)
                        val checksum = Sha256Util.sha256Hex(item.payloadJson)
                        val localMeta = phase7Dao.getSyncMetadata(item.branchId, item.entityType, item.entityId)
                        val localVersion = localMeta?.recordVersion ?: 1L

                        // 1. Check remote state first to prevent duplicate uploads & silent overwrites
                        when (val remoteRead = gateway.fetchRemoteRecord(item.branchId, domain.collectionKey, item.entityId)) {
                            is CloudReadOutcome.Found -> {
                                val remote = remoteRead.record
                                if (remote.checksumSha256 == checksum) {
                                    // Identical payload already exists remotely -> idempotent dedupe!
                                    markItemSynced(item, domain, localVersion, checksum, device.deviceId, session.uid)
                                    deduped++
                                    continue
                                } else if (remote.deviceId != device.deviceId && remote.version >= localVersion) {
                                    // Multi-device conflict detected! Never silently overwrite financial records.
                                    recordConflict(item, remote, localVersion, device.deviceId, session.displayName)
                                    conflicts++
                                    continue
                                }
                            }
                            is CloudReadOutcome.Error -> {
                                if (remoteRead.isLockedRules) {
                                    phase7Dao.updateSyncQueueStatus(
                                        item.transactionId,
                                        "FAILED_LOCKED_RULES",
                                        item.retryCount + 1,
                                        remoteRead.reason
                                    )
                                    failed++
                                    finalPhase = CloudSyncPhase.LOCKED_RULES
                                    finalMessage = remoteRead.reason
                                    break
                                }
                            }
                            CloudReadOutcome.NotFound, is CloudReadOutcome.RawFound -> Unit
                        }

                        // 2. Push atomically with sync_ledger idempotency guard
                        val now = System.currentTimeMillis()
                        val payloadMap = mapOf<String, Any?>(
                            "entityId" to item.entityId,
                            "entityType" to item.entityType,
                            "branchId" to item.branchId,
                            "version" to localVersion,
                            "checksumSha256" to checksum,
                            "deviceId" to device.deviceId,
                            "actorUid" to session.uid,
                            "actorName" to session.displayName,
                            "payload" to Sha256Util.jsonObjectToMap(runCatching { JSONObject(item.payloadJson) }.getOrElse { JSONObject() }),
                            "updatedAt" to now
                        )
                        val ledgerEntry = mapOf<String, Any?>(
                            "transactionId" to item.transactionId,
                            "branchId" to item.branchId,
                            "entityType" to item.entityType,
                            "entityId" to item.entityId,
                            "checksumSha256" to checksum,
                            "actorUid" to session.uid,
                            "deviceId" to device.deviceId,
                            "createdAt" to now
                        )

                        when (val writeResult = gateway.upsertRecordAtomic(
                            branchId = item.branchId,
                            domainCollection = domain.collectionKey,
                            entityId = item.entityId,
                            payloadMap = payloadMap,
                            ledgerTxId = item.transactionId,
                            ledgerEntry = ledgerEntry
                        )) {
                            CloudWriteOutcome.Success -> {
                                markItemSynced(item, domain, localVersion, checksum, device.deviceId, session.uid)
                                synced++
                            }
                            is CloudWriteOutcome.LockedRules -> {
                                phase7Dao.updateSyncQueueStatus(
                                    item.transactionId,
                                    "FAILED_LOCKED_RULES",
                                    item.retryCount + 1,
                                    writeResult.reason
                                )
                                failed++
                                finalPhase = CloudSyncPhase.LOCKED_RULES
                                finalMessage = writeResult.reason
                                emitSyncNotification(
                                    branchId = session.branchId,
                                    category = "SYNC",
                                    severity = "CRITICAL",
                                    title = "RTDB Security Rules Rejected Write",
                                    message = writeResult.reason
                                )
                                break
                            }
                            is CloudWriteOutcome.RetryableError -> {
                                phase7Dao.updateSyncQueueStatus(
                                    item.transactionId,
                                    "FAILED_RETRYABLE",
                                    item.retryCount + 1,
                                    writeResult.reason
                                )
                                failed++
                                finalPhase = CloudSyncPhase.ERROR
                                finalMessage = writeResult.reason
                            }
                            is CloudWriteOutcome.AuthRequired -> {
                                phase7Dao.updateSyncQueueStatus(
                                    item.transactionId,
                                    "FAILED_AUTH_REQUIRED",
                                    item.retryCount + 1,
                                    writeResult.reason
                                )
                                failed++
                                finalPhase = CloudSyncPhase.AUTH_REQUIRED
                                finalMessage = writeResult.reason
                                break
                            }
                            is CloudWriteOutcome.ConfigMissing -> {
                                failed++
                                finalPhase = CloudSyncPhase.CONFIG_MISSING
                                finalMessage = writeResult.reason
                                break
                            }
                        }
                    }

                    val finishedAt = System.currentTimeMillis()
                    if (synced > 0 || deduped > 0) {
                        sessionPreferences.setLastCloudSyncAt(finishedAt)
                    }
                    phase7Dao.upsertDevice(
                        device.copy(
                            syncStatus = "ACTIVE",
                            lastSeenAt = finishedAt,
                            lastSyncAt = if (synced > 0) finishedAt else device.lastSyncAt
                        )
                    )
                    if (failed == 0) {
                        _lastPhaseOverride.value = null
                        finalMessage = "Synced $synced, deduplicated $deduped, conflicts $conflicts."
                    } else {
                        _lastPhaseOverride.value = finalPhase
                        authRepository.refreshEnvironmentStatus(finalPhase.label, finalMessage)
                    }
                    _lastSyncResultMessage.value = finalMessage
                    SyncRunSummary(synced, deduped, conflicts, failed, finalPhase, finalMessage)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val err = e.message ?: "Unexpected cloud sync error"
                    _lastPhaseOverride.value = CloudSyncPhase.ERROR
                    _lastSyncResultMessage.value = err
                    SyncRunSummary(synced, deduped, conflicts, failed + 1, CloudSyncPhase.ERROR, err)
                } finally {
                    _isSyncing.value = false
                }
            }
        }

    private suspend fun markItemSynced(
        item: SyncQueueEntity,
        domain: SyncDomain,
        version: Long,
        checksum: String,
        deviceId: String,
        uid: String
    ) {
        val now = System.currentTimeMillis()
        phase7Dao.updateSyncQueueStatus(item.transactionId, "SYNCED", item.retryCount, null)
        phase7Dao.upsertSyncMetadata(
            SyncMetadataEntity(
                recordKey = "${item.branchId}__${item.entityType}__${item.entityId}",
                branchId = item.branchId,
                entityType = item.entityType,
                entityId = item.entityId,
                domainCollection = domain.collectionKey,
                recordVersion = version,
                contentSha256 = checksum,
                deviceId = deviceId,
                updatedByUid = uid,
                updatedAt = now,
                lastSyncedAt = now,
                syncState = "SYNCED"
            )
        )
    }

    suspend fun recordConflict(
        item: SyncQueueEntity,
        remote: RemoteCloudRecord,
        localVersion: Long,
        localDeviceId: String,
        localActorName: String
    ): SyncConflictEntity {
        val existingOpen = phase7Dao.getOpenConflictForEntity(item.branchId, item.entityType, item.entityId)
        val domain = SyncDomain.fromEntityType(item.entityType)
        val remoteJson = Sha256Util.mapToJsonObject(remote.data).toString()
        val conflict = SyncConflictEntity(
            conflictId = existingOpen?.conflictId ?: "conf_${UUID.randomUUID().toString().take(12)}",
            branchId = item.branchId,
            entityType = item.entityType,
            entityId = item.entityId,
            recordLabel = "${item.entityType} (${item.entityId})",
            isFinancialProtected = domain.isFinancialProtected,
            localVersion = localVersion,
            remoteVersion = remote.version,
            localUpdatedAt = item.createdAt,
            remoteUpdatedAt = remote.updatedAt,
            localDeviceId = localDeviceId,
            remoteDeviceId = remote.deviceId,
            localActorName = localActorName,
            remoteActorName = remote.actorName,
            localSummary = summarizePayload(item.payloadJson),
            remoteSummary = summarizePayload(remoteJson),
            localPayloadJson = item.payloadJson,
            remotePayloadJson = remoteJson,
            status = "OPEN",
            createdAt = existingOpen?.createdAt ?: System.currentTimeMillis()
        )
        phase7Dao.upsertConflict(conflict)
        phase7Dao.updateSyncQueueStatus(
            item.transactionId,
            "CONFLICT",
            item.retryCount,
            "Multi-device conflict detected against ${remote.deviceId} (v${remote.version}). Manual review required."
        )
        emitSyncNotification(
            branchId = item.branchId,
            category = "CONFLICT",
            severity = if (domain.isFinancialProtected) "CRITICAL" else "WARNING",
            title = if (domain.isFinancialProtected) "Protected Financial Sync Conflict" else "Sync Conflict Detected",
            message = "${item.entityType} (${item.entityId}) modified on device ${remote.deviceId} (v${remote.version}). Open Conflict Resolution before overwriting.",
            relatedEntityType = item.entityType,
            relatedEntityId = item.entityId
        )
        return conflict
    }

    suspend fun resolveConflict(
        session: UserSession,
        conflictId: String,
        strategy: ConflictResolutionStrategy,
        notes: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return@withContext Result.failure(it)
        }
        val conflict = phase7Dao.getConflictById(conflictId)
            ?: return@withContext Result.failure(IllegalArgumentException("Conflict record not found."))
        if (conflict.status != "OPEN") {
            return@withContext Result.failure(IllegalStateException("Conflict was already resolved (${conflict.status})."))
        }
        if (!session.capabilities.canResolveSyncConflicts) {
            return@withContext Result.failure(SecurityException("Role ${session.role.displayName} is not authorized to resolve sync conflicts."))
        }
        if (conflict.isFinancialProtected && !session.capabilities.canResolveFinancialConflicts) {
            return@withContext Result.failure(
                SecurityException("Financial record conflicts require Owner or Super Admin authorization.")
            )
        }
        if (notes.trim().length < 4) {
            return@withContext Result.failure(IllegalArgumentException("Enter an audit reason for resolving this conflict."))
        }

        val now = System.currentTimeMillis()
        val deviceId = sessionPreferences.getOrCreateDeviceId()
        val domain = SyncDomain.fromEntityType(conflict.entityType)
        val nextVersion = maxOf(conflict.localVersion, conflict.remoteVersion) + 1L

        phase7Dao.upsertConflict(
            conflict.copy(
                status = strategy.code,
                resolutionNotes = notes.trim(),
                resolvedByName = session.displayName,
                resolvedAt = now
            )
        )

        when (strategy) {
            ConflictResolutionStrategy.KEEP_LOCAL,
            ConflictResolutionStrategy.KEEP_BOTH_ADJUSTED -> {
                val checksum = Sha256Util.sha256Hex(conflict.localPayloadJson)
                phase7Dao.upsertSyncMetadata(
                    SyncMetadataEntity(
                        recordKey = "${conflict.branchId}__${conflict.entityType}__${conflict.entityId}",
                        branchId = conflict.branchId,
                        entityType = conflict.entityType,
                        entityId = conflict.entityId,
                        domainCollection = domain.collectionKey,
                        recordVersion = nextVersion,
                        contentSha256 = checksum,
                        deviceId = deviceId,
                        updatedByUid = session.uid,
                        updatedAt = now,
                        lastSyncedAt = null,
                        syncState = "PENDING_PUSH"
                    )
                )
                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_resolved_${conflict.conflictId}_v$nextVersion",
                        branchId = conflict.branchId,
                        entityType = conflict.entityType,
                        entityId = conflict.entityId,
                        payloadJson = conflict.localPayloadJson,
                        status = "PENDING",
                        retryCount = 0,
                        lastError = null,
                        createdAt = now
                    )
                )
            }
            ConflictResolutionStrategy.ACCEPT_REMOTE -> {
                val remoteChecksum = Sha256Util.sha256Hex(conflict.remotePayloadJson)
                phase7Dao.upsertSyncMetadata(
                    SyncMetadataEntity(
                        recordKey = "${conflict.branchId}__${conflict.entityType}__${conflict.entityId}",
                        branchId = conflict.branchId,
                        entityType = conflict.entityType,
                        entityId = conflict.entityId,
                        domainCollection = domain.collectionKey,
                        recordVersion = conflict.remoteVersion,
                        contentSha256 = remoteChecksum,
                        deviceId = conflict.remoteDeviceId,
                        updatedByUid = session.uid,
                        updatedAt = now,
                        lastSyncedAt = now,
                        syncState = "SYNCED"
                    )
                )
            }
        }

        // Clear any old CONFLICT status items in sync_queue for this entity
        phase7Dao.getPendingOrFailedQueue()
            .filter { it.status == "CONFLICT" && it.branchId == conflict.branchId && it.entityType == conflict.entityType && it.entityId == conflict.entityId }
            .forEach { q ->
                phase7Dao.updateSyncQueueStatus(
                    q.transactionId,
                    "SYNCED",
                    q.retryCount,
                    "Superseded by conflict resolution ${strategy.code} (${session.displayName})"
                )
            }

        auditLogDao.insertLog(
            AuditLogEntity(
                branchId = conflict.branchId,
                actorUid = session.uid,
                actorEmail = session.email,
                actorRole = session.role.displayName,
                action = "SYNC_CONFLICT_RESOLVED",
                details = "${conflict.entityType} ${conflict.entityId} -> ${strategy.label} | Note: ${notes.trim()}"
            )
        )
        Result.success(Unit)
    }

    private suspend fun emitSyncNotification(
        branchId: String,
        category: String,
        severity: String,
        title: String,
        message: String,
        relatedEntityType: String? = null,
        relatedEntityId: String? = null
    ) {
        phase7Dao.insertNotificationIfNew(
            ErpNotificationEntity(
                notificationId = "notif_${category.lowercase()}_${UUID.randomUUID().toString().take(10)}",
                branchId = branchId,
                category = category,
                severity = severity,
                title = title,
                message = message,
                relatedEntityType = relatedEntityType,
                relatedEntityId = relatedEntityId,
                isRead = false,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun summarizePayload(json: String): String {
        return try {
            val obj = JSONObject(json)
            val keys = mutableListOf<String>()
            val iter = obj.keys()
            while (iter.hasNext() && keys.size < 4) {
                val k = iter.next()
                if (k != "payload") {
                    keys += "$k=${obj.opt(k)}"
                }
            }
            keys.joinToString(" • ").ifBlank { json.take(80) }
        } catch (_: Exception) {
            json.take(80)
        }
    }
}
