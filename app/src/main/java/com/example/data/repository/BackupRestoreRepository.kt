package com.example.data.repository

import android.content.Context
import com.example.data.cloud.CloudSyncGateway
import com.example.data.cloud.CloudWriteOutcome
import com.example.data.cloud.NetworkConnectivityMonitor
import com.example.data.cloud.Sha256Util
import com.example.data.local.AaCraveDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.BackupSnapshotEntity
import com.example.data.local.ErpNotificationEntity
import com.example.data.local.RecoveryLogEntity
import com.example.data.local.SessionPreferences
import com.example.domain.model.BackupIntegrityResult
import com.example.domain.model.FirebaseCloudConstants
import com.example.domain.model.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

class BackupRestoreRepository(
    private val context: Context,
    private val database: AaCraveDatabase,
    private val sessionPreferences: SessionPreferences,
    private val connectivityMonitor: NetworkConnectivityMonitor,
    private val gateway: CloudSyncGateway
) {
    private val phase7Dao = database.phase7Dao()
    private val auditLogDao = database.auditLogDao()

    val backups: Flow<List<BackupSnapshotEntity>> = phase7Dao.observeBackups()
    val recoveryLogs: Flow<List<RecoveryLogEntity>> = phase7Dao.observeRecoveryLogs()

    private fun backupDir(): File {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun createBackup(
        session: UserSession,
        branchScope: String = "ALL_BRANCHES",
        notes: String = "",
        uploadToCloud: Boolean = true
    ): Result<BackupSnapshotEntity> = withContext(Dispatchers.IO) {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return@withContext Result.failure(it)
        }
        if (!session.capabilities.canCreateBackup) {
            return@withContext Result.failure(SecurityException("Role ${session.role.displayName} is not authorized to create backups."))
        }

        val now = System.currentTimeMillis()
        val backupId = "bkp_${now}_${UUID.randomUUID().toString().take(6)}"
        val deviceId = sessionPreferences.getOrCreateDeviceId()
        val (tablesObj, counts) = BackupPayloadSerializer.exportDatabaseTablesJson(database)
        val totalRecords = counts.values.sum()
        val payloadJson = tablesObj.toString()
        val checksum = Sha256Util.sha256Hex(payloadJson)

        val summaryObj = JSONObject()
        counts.forEach { (k, v) -> summaryObj.put(k, v) }

        val manifestObj = JSONObject().apply {
            put("backupId", backupId)
            put("projectId", FirebaseCloudConstants.PROJECT_ID)
            put("schemaVersion", FirebaseCloudConstants.BACKUP_SCHEMA_VERSION)
            put("branchScope", branchScope)
            put("createdByUid", session.uid)
            put("createdByName", session.displayName)
            put("createdByRole", session.role.displayName)
            put("deviceId", deviceId)
            put("createdAt", now)
            put("recordCount", totalRecords)
            put("checksumSha256", checksum)
            put("tableCounts", summaryObj)
            put("notes", notes.trim())
        }
        val envelopeJson = JSONObject().apply {
            put("manifest", manifestObj)
            put("payloadJson", payloadJson)
        }.toString()

        val localFile = File(backupDir(), "$backupId.aacrave.json")
        localFile.writeText(envelopeJson, Charsets.UTF_8)

        var cloudStatus = "LOCAL_ONLY"
        var cloudPath: String? = null
        if (uploadToCloud && gateway.isFirebaseConfigured() && session.isFirebaseAuthenticated && connectivityMonitor.currentIsOnline()) {
            val targetPath = "erp/backups/$backupId"
            val rtdbPayload = mapOf<String, Any?>(
                "backupId" to backupId,
                "schemaVersion" to FirebaseCloudConstants.BACKUP_SCHEMA_VERSION,
                "branchScope" to branchScope,
                "checksumSha256" to checksum,
                "recordCount" to totalRecords,
                "createdByUid" to session.uid,
                "createdByName" to session.displayName,
                "deviceId" to deviceId,
                "createdAt" to now,
                "tableCounts" to counts,
                "notes" to notes.trim()
            )
            when (gateway.upsertPath(targetPath, rtdbPayload)) {
                CloudWriteOutcome.Success -> {
                    cloudStatus = "UPLOADED"
                    cloudPath = targetPath
                }
                else -> cloudStatus = "FAILED"
            }
        }

        val entity = BackupSnapshotEntity(
            backupId = backupId,
            branchScope = branchScope,
            createdByUid = session.uid,
            createdByName = session.displayName,
            createdByRole = session.role.displayName,
            deviceId = deviceId,
            schemaVersion = FirebaseCloudConstants.BACKUP_SCHEMA_VERSION,
            checksumSha256 = checksum,
            recordCount = totalRecords,
            summaryJson = summaryObj.toString(),
            localFilePath = localFile.absolutePath,
            cloudPath = cloudPath,
            cloudStatus = cloudStatus,
            integrityVerified = true,
            notes = notes.trim(),
            createdAt = now
        )
        phase7Dao.upsertBackup(entity)
        phase7Dao.insertRecoveryLog(
            RecoveryLogEntity(
                recoveryId = "rec_${UUID.randomUUID().toString().take(12)}",
                backupId = backupId,
                actionType = "BACKUP_CREATED",
                status = "SUCCESS",
                details = "Created backup $backupId ($totalRecords records, SHA-256 ${checksum.take(12)}..., cloud=$cloudStatus)",
                actorUid = session.uid,
                actorName = session.displayName,
                deviceId = deviceId,
                createdAt = now
            )
        )
        auditLogDao.insertLog(
            AuditLogEntity(
                branchId = session.branchId,
                actorUid = session.uid,
                actorEmail = session.email,
                actorRole = session.role.displayName,
                action = "BACKUP_CREATED",
                details = "Backup $backupId | Records: $totalRecords | Cloud: $cloudStatus"
            )
        )
        Result.success(entity)
    }

    suspend fun readBackupFileJson(backupId: String): Result<String> = withContext(Dispatchers.IO) {
        val snapshot = phase7Dao.getBackupById(backupId)
            ?: return@withContext Result.failure(IllegalArgumentException("Backup snapshot $backupId not found."))
        val file = File(snapshot.localFilePath)
        if (!file.exists()) {
            return@withContext Result.failure(IllegalStateException("Backup file missing at ${snapshot.localFilePath}."))
        }
        Result.success(file.readText(Charsets.UTF_8))
    }

    suspend fun validateBackupJson(
        rawEnvelopeJson: String,
        session: UserSession? = null
    ): BackupIntegrityResult = withContext(Dispatchers.IO) {
        try {
            val envelope = JSONObject(rawEnvelopeJson)
            val manifest = envelope.optJSONObject("manifest")
                ?: return@withContext invalidIntegrityResult("Missing backup manifest header.")
            val payloadJson = envelope.optString("payloadJson", "")
            if (payloadJson.isBlank()) {
                return@withContext invalidIntegrityResult("Backup payloadJson is empty.")
            }
            val schemaVersion = manifest.optInt("schemaVersion", -1)
            if (schemaVersion !in 1..FirebaseCloudConstants.BACKUP_SCHEMA_VERSION) {
                return@withContext invalidIntegrityResult("Unsupported backup schemaVersion=$schemaVersion.")
            }

            val expectedChecksum = manifest.optString("checksumSha256", "")
            val computedChecksum = Sha256Util.sha256Hex(payloadJson)
            val checksumMatch = expectedChecksum.isNotBlank() && expectedChecksum.equals(computedChecksum, ignoreCase = true)

            val tablesObj = JSONObject(payloadJson)
            val counts = linkedMapOf<String, Int>()
            var total = 0
            val keys = tablesObj.keys()
            while (keys.hasNext()) {
                val t = keys.next()
                val len = tablesObj.optJSONArray(t)?.length() ?: 0
                counts[t] = len
                total += len
            }
            val hasBranches = (counts["branches"] ?: 0) > 0
            val valid = checksumMatch && hasBranches

            val backupId = manifest.optString("backupId", "imported")
            if (session != null) {
                val deviceId = sessionPreferences.getOrCreateDeviceId()
                phase7Dao.insertRecoveryLog(
                    RecoveryLogEntity(
                        recoveryId = "rec_${UUID.randomUUID().toString().take(12)}",
                        backupId = backupId,
                        actionType = "INTEGRITY_CHECK",
                        status = if (valid) "SUCCESS" else "REJECTED_CHECKSUM",
                        details = if (valid) {
                            "SHA-256 verified ($total records across ${counts.size} tables)."
                        } else {
                            "Integrity failure: checksumMatch=$checksumMatch, hasBranches=$hasBranches."
                        },
                        actorUid = session.uid,
                        actorName = session.displayName,
                        deviceId = deviceId
                    )
                )
            }

            BackupIntegrityResult(
                isValid = valid,
                checksumMatch = checksumMatch,
                schemaVersion = schemaVersion,
                expectedChecksum = expectedChecksum,
                computedChecksum = computedChecksum,
                totalRecords = total,
                tableCounts = counts,
                createdAt = manifest.optLong("createdAt", 0L),
                createdByName = manifest.optString("createdByName", "Unknown"),
                branchScope = manifest.optString("branchScope", "ALL_BRANCHES"),
                errorMessage = when {
                    !checksumMatch -> "SHA-256 checksum mismatch! Backup file may be corrupted or modified."
                    !hasBranches -> "Backup contains zero branch records; refusing empty restore."
                    else -> null
                }
            )
        } catch (e: Exception) {
            invalidIntegrityResult("Invalid backup JSON: ${e.message}")
        }
    }

    suspend fun restoreFromBackupJson(
        session: UserSession,
        rawEnvelopeJson: String,
        confirmationText: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return@withContext Result.failure(it)
        }
        if (!session.capabilities.canRestoreBackup) {
            return@withContext Result.failure(SecurityException("Only Owner or Super Admin can restore backups."))
        }
        if (confirmationText.trim() != "RESTORE") {
            return@withContext Result.failure(IllegalArgumentException("Type RESTORE exactly to confirm overwriting local ERP data."))
        }

        val integrity = validateBackupJson(rawEnvelopeJson, session)
        val envelope = runCatching { JSONObject(rawEnvelopeJson) }.getOrNull()
        val backupId = envelope?.optJSONObject("manifest")?.optString("backupId", "imported") ?: "imported"
        val deviceId = sessionPreferences.getOrCreateDeviceId()

        if (!integrity.isValid) {
            phase7Dao.insertRecoveryLog(
                RecoveryLogEntity(
                    recoveryId = "rec_${UUID.randomUUID().toString().take(12)}",
                    backupId = backupId,
                    actionType = "RESTORE_REJECTED",
                    status = "REJECTED_CHECKSUM",
                    details = integrity.errorMessage ?: "Integrity check failed.",
                    actorUid = session.uid,
                    actorName = session.displayName,
                    deviceId = deviceId
                )
            )
            return@withContext Result.failure(IllegalStateException(integrity.errorMessage ?: "Backup integrity verification failed."))
        }

        val tablesObj = JSONObject(envelope!!.getString("payloadJson"))
        val restoredCount = BackupPayloadSerializer.restoreTablesFromJson(database, tablesObj)

        val now = System.currentTimeMillis()
        phase7Dao.insertRecoveryLog(
            RecoveryLogEntity(
                recoveryId = "rec_${UUID.randomUUID().toString().take(12)}",
                backupId = backupId,
                actionType = "RESTORE_APPLIED",
                status = "SUCCESS",
                details = "Restored $restoredCount rows from backup $backupId (SHA-256 ${integrity.computedChecksum.take(12)}...)",
                actorUid = session.uid,
                actorName = session.displayName,
                deviceId = deviceId,
                createdAt = now
            )
        )
        phase7Dao.insertNotificationIfNew(
            ErpNotificationEntity(
                notificationId = "notif_restore_${UUID.randomUUID().toString().take(10)}",
                branchId = session.branchId,
                category = "BACKUP",
                severity = "WARNING",
                title = "ERP Backup Restored ($backupId)",
                message = "${session.displayName} restored $restoredCount records after SHA-256 checksum validation.",
                relatedEntityType = "BACKUP",
                relatedEntityId = backupId,
                createdAt = now
            )
        )
        auditLogDao.insertLog(
            AuditLogEntity(
                branchId = session.branchId,
                actorUid = session.uid,
                actorEmail = session.email,
                actorRole = session.role.displayName,
                action = "BACKUP_RESTORED",
                details = "Restored backup $backupId ($restoredCount records, checksum verified)"
            )
        )
        Result.success(restoredCount)
    }

    private fun invalidIntegrityResult(message: String) = BackupIntegrityResult(
        isValid = false,
        checksumMatch = false,
        schemaVersion = 0,
        expectedChecksum = "",
        computedChecksum = "",
        totalRecords = 0,
        tableCounts = emptyMap(),
        createdAt = 0L,
        createdByName = "",
        branchScope = "",
        errorMessage = message
    )
}
