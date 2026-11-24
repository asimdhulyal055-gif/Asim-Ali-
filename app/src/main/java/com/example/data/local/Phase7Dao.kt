package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface Phase7Dao {

    // Sync Record Versions (conflict & deduplication metadata)
    @Query("SELECT * FROM sync_record_versions ORDER BY updatedAt DESC")
    fun observeAllSyncMetadata(): Flow<List<SyncMetadataEntity>>

    @Query("SELECT * FROM sync_record_versions WHERE branchId = :branchId AND entityType = :entityType AND entityId = :entityId LIMIT 1")
    suspend fun getSyncMetadata(branchId: String, entityType: String, entityId: String): SyncMetadataEntity?

    @Query("SELECT COUNT(*) FROM sync_record_versions WHERE syncState = 'SYNCED'")
    fun observeSyncedRecordCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncMetadata(metadata: SyncMetadataEntity)

    // Sync Queue extensions
    @Query("SELECT * FROM sync_queue WHERE status != 'SYNCED' ORDER BY createdAt ASC")
    suspend fun getPendingOrFailedQueue(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE status IN ('FAILED_RETRYABLE', 'FAILED_LOCKED_RULES', 'FAILED_AUTH_REQUIRED') ORDER BY createdAt ASC")
    suspend fun getRetryableQueue(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue ORDER BY createdAt DESC LIMIT 150")
    fun observeAllSyncQueue(): Flow<List<SyncQueueEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('FAILED_RETRYABLE', 'FAILED_LOCKED_RULES', 'FAILED_AUTH_REQUIRED', 'CONFLICT')")
    fun observeFailedQueueCount(): Flow<Int>

    @Query("UPDATE sync_queue SET status = :status, retryCount = :retryCount, lastError = :lastError WHERE transactionId = :transactionId")
    suspend fun updateSyncQueueStatus(transactionId: String, status: String, retryCount: Int, lastError: String?)

    // Sync Conflicts
    @Query("SELECT * FROM sync_conflicts ORDER BY CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END, createdAt DESC")
    fun observeAllConflicts(): Flow<List<SyncConflictEntity>>

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE status = 'OPEN'")
    fun observeOpenConflictCount(): Flow<Int>

    @Query("SELECT * FROM sync_conflicts WHERE conflictId = :conflictId LIMIT 1")
    suspend fun getConflictById(conflictId: String): SyncConflictEntity?

    @Query("SELECT * FROM sync_conflicts WHERE branchId = :branchId AND entityType = :entityType AND entityId = :entityId AND status = 'OPEN' LIMIT 1")
    suspend fun getOpenConflictForEntity(branchId: String, entityType: String, entityId: String): SyncConflictEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConflict(conflict: SyncConflictEntity)

    // Backup Snapshots
    @Query("SELECT * FROM backup_snapshots ORDER BY createdAt DESC")
    fun observeBackups(): Flow<List<BackupSnapshotEntity>>

    @Query("SELECT * FROM backup_snapshots WHERE backupId = :backupId LIMIT 1")
    suspend fun getBackupById(backupId: String): BackupSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBackup(backup: BackupSnapshotEntity)

    // Recovery Logs
    @Query("SELECT * FROM recovery_logs ORDER BY createdAt DESC LIMIT 100")
    fun observeRecoveryLogs(): Flow<List<RecoveryLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecoveryLog(log: RecoveryLogEntity)

    // Multi-Device Sessions
    @Query("SELECT * FROM device_sessions ORDER BY isCurrentDevice DESC, lastSeenAt DESC")
    fun observeDevices(): Flow<List<DeviceSessionEntity>>

    @Query("SELECT * FROM device_sessions WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceById(deviceId: String): DeviceSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevice(device: DeviceSessionEntity)

    // Notifications
    @Query("SELECT * FROM erp_notifications ORDER BY isRead ASC, createdAt DESC LIMIT 200")
    fun observeNotifications(): Flow<List<ErpNotificationEntity>>

    @Query("SELECT COUNT(*) FROM erp_notifications WHERE isRead = 0")
    fun observeUnreadNotificationCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotificationIfNew(notification: ErpNotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotification(notification: ErpNotificationEntity)

    @Query("UPDATE erp_notifications SET isRead = 1 WHERE notificationId = :notificationId")
    suspend fun markNotificationRead(notificationId: String)

    @Query("UPDATE erp_notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllNotificationsRead()

    // Role Security Policies
    @Query("SELECT * FROM role_permission_policies ORDER BY roleCode ASC")
    fun observeRolePolicies(): Flow<List<RolePermissionPolicyEntity>>

    @Query("SELECT * FROM role_permission_policies WHERE roleCode = :roleCode LIMIT 1")
    suspend fun getRolePolicy(roleCode: String): RolePermissionPolicyEntity?

    @Query("SELECT COUNT(*) FROM role_permission_policies")
    suspend fun countRolePolicies(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRolePolicies(policies: List<RolePermissionPolicyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRolePolicy(policy: RolePermissionPolicyEntity)
}
