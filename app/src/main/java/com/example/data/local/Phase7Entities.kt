package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_record_versions",
    indices = [Index(value = ["branchId", "entityType", "entityId"], unique = true)]
)
data class SyncMetadataEntity(
    @PrimaryKey val recordKey: String,
    val branchId: String,
    val entityType: String,
    val entityId: String,
    val domainCollection: String,
    val recordVersion: Long,
    val contentSha256: String,
    val deviceId: String,
    val updatedByUid: String,
    val updatedAt: Long,
    val lastSyncedAt: Long? = null,
    val syncState: String // LOCAL_ONLY, PENDING_PUSH, SYNCED, CONFLICT
)

@Entity(
    tableName = "sync_conflicts",
    indices = [Index(value = ["branchId", "status"])]
)
data class SyncConflictEntity(
    @PrimaryKey val conflictId: String,
    val branchId: String,
    val entityType: String,
    val entityId: String,
    val recordLabel: String,
    val isFinancialProtected: Boolean,
    val localVersion: Long,
    val remoteVersion: Long,
    val localUpdatedAt: Long,
    val remoteUpdatedAt: Long,
    val localDeviceId: String,
    val remoteDeviceId: String,
    val localActorName: String,
    val remoteActorName: String,
    val localSummary: String,
    val remoteSummary: String,
    val localPayloadJson: String,
    val remotePayloadJson: String,
    val status: String, // OPEN, RESOLVED_LOCAL, RESOLVED_REMOTE, RESOLVED_MERGED
    val resolutionNotes: String = "",
    val resolvedByName: String? = null,
    val resolvedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "backup_snapshots")
data class BackupSnapshotEntity(
    @PrimaryKey val backupId: String,
    val branchScope: String,
    val createdByUid: String,
    val createdByName: String,
    val createdByRole: String,
    val deviceId: String,
    val schemaVersion: Int,
    val checksumSha256: String,
    val recordCount: Int,
    val summaryJson: String,
    val localFilePath: String,
    val cloudPath: String? = null,
    val cloudStatus: String, // LOCAL_ONLY, UPLOADED, FAILED
    val integrityVerified: Boolean = true,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recovery_logs")
data class RecoveryLogEntity(
    @PrimaryKey val recoveryId: String,
    val backupId: String,
    val actionType: String, // BACKUP_CREATED, BACKUP_UPLOADED, INTEGRITY_CHECK, RESTORE_APPLIED, RESTORE_REJECTED
    val status: String,     // SUCCESS, FAILED, REJECTED_CHECKSUM
    val details: String,
    val actorUid: String,
    val actorName: String,
    val deviceId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "device_sessions")
data class DeviceSessionEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val osVersion: String,
    val appVersion: String,
    val operatorUid: String,
    val operatorEmail: String,
    val operatorRole: String,
    val branchId: String,
    val syncStatus: String, // ACTIVE, SYNCING, OFFLINE, REVOKED
    val isAuthorized: Boolean = true,
    val isCurrentDevice: Boolean = false,
    val lastSeenAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "erp_notifications",
    indices = [Index(value = ["branchId", "isRead", "createdAt"])]
)
data class ErpNotificationEntity(
    @PrimaryKey val notificationId: String,
    val branchId: String,
    val category: String, // SYNC, CONFLICT, SECURITY, BACKUP, OPERATIONS
    val severity: String, // INFO, WARNING, CRITICAL
    val title: String,
    val message: String,
    val relatedEntityType: String? = null,
    val relatedEntityId: String? = null,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "role_permission_policies")
data class RolePermissionPolicyEntity(
    @PrimaryKey val roleCode: String,
    val canTriggerCloudSync: Boolean,
    val canResolveSyncConflicts: Boolean,
    val canResolveFinancialConflicts: Boolean,
    val canCreateBackup: Boolean,
    val canRestoreBackup: Boolean,
    val canManageDevices: Boolean,
    val canManageUserPermissions: Boolean,
    val canApproveFinancialAdjustments: Boolean,
    val canExportReports: Boolean,
    val updatedBy: String,
    val updatedAt: Long = System.currentTimeMillis()
)
