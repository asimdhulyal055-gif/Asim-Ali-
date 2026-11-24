package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AaCraveDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.BackupSnapshotEntity
import com.example.data.local.DeviceSessionEntity
import com.example.data.local.ErpNotificationEntity
import com.example.data.local.RecoveryLogEntity
import com.example.data.local.RolePermissionPolicyEntity
import com.example.data.local.StaffProfileEntity
import com.example.data.local.SyncConflictEntity
import com.example.data.local.SyncQueueEntity
import com.example.data.local.UserProfileEntity
import com.example.data.repository.BackupRestoreRepository
import com.example.data.repository.CloudSyncRepository
import com.example.data.repository.SecurityAdminRepository
import com.example.domain.model.BackupIntegrityResult
import com.example.domain.model.BranchSummary
import com.example.domain.model.CloudProbeResult
import com.example.domain.model.CloudSyncStatus
import com.example.domain.model.ConflictResolutionStrategy
import com.example.domain.model.NotificationCategory
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BackupSyncTab(val label: String) {
    CLOUD_SYNC("Cloud Sync"),
    CONFLICTS("Conflicts"),
    BACKUPS("Backups"),
    RECOVERY_LOGS("Recovery Logs")
}

data class BackupSyncUiState(
    val selectedTab: BackupSyncTab = BackupSyncTab.CLOUD_SYNC,
    val syncStatus: CloudSyncStatus = CloudSyncStatus(),
    val syncQueue: List<SyncQueueEntity> = emptyList(),
    val conflicts: List<SyncConflictEntity> = emptyList(),
    val backups: List<BackupSnapshotEntity> = emptyList(),
    val recoveryLogs: List<RecoveryLogEntity> = emptyList(),
    val probeResult: CloudProbeResult? = null,
    val selectedBackupForRestore: BackupSnapshotEntity? = null,
    val importedBackupEnvelopeJson: String? = null,
    val importedBackupLabel: String? = null,
    val integrityResult: BackupIntegrityResult? = null,
    val isWorking: Boolean = false,
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

class BackupSyncViewModel(
    private val session: UserSession,
    private val cloudSyncRepository: CloudSyncRepository,
    private val backupRestoreRepository: BackupRestoreRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(BackupSyncTab.CLOUD_SYNC)
    private val _probeResult = MutableStateFlow<CloudProbeResult?>(null)
    private val _selectedBackupForRestore = MutableStateFlow<BackupSnapshotEntity?>(null)
    private val _importedBackupEnvelope = MutableStateFlow<Pair<String, String>?>(null)
    private val _integrityResult = MutableStateFlow<BackupIntegrityResult?>(null)
    private val _isWorking = MutableStateFlow(false)
    private val _feedback = MutableStateFlow<Pair<String, Boolean>?>(null)

    val uiState: StateFlow<BackupSyncUiState> = combine(
        _selectedTab,
        cloudSyncRepository.observeCloudSyncStatus(session),
        cloudSyncRepository.syncQueue,
        cloudSyncRepository.conflicts,
        backupRestoreRepository.backups,
        backupRestoreRepository.recoveryLogs,
        _probeResult,
        _selectedBackupForRestore,
        _importedBackupEnvelope,
        _integrityResult,
        _isWorking,
        _feedback
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        BackupSyncUiState(
            selectedTab = args[0] as BackupSyncTab,
            syncStatus = args[1] as CloudSyncStatus,
            syncQueue = args[2] as List<SyncQueueEntity>,
            conflicts = args[3] as List<SyncConflictEntity>,
            backups = args[4] as List<BackupSnapshotEntity>,
            recoveryLogs = args[5] as List<RecoveryLogEntity>,
            probeResult = args[6] as CloudProbeResult?,
            selectedBackupForRestore = args[7] as BackupSnapshotEntity?,
            importedBackupEnvelopeJson = (args[8] as Pair<String, String>?)?.first,
            importedBackupLabel = (args[8] as Pair<String, String>?)?.second,
            integrityResult = args[9] as BackupIntegrityResult?,
            isWorking = args[10] as Boolean,
            feedbackMessage = (args[11] as Pair<String, Boolean>?)?.first,
            isErrorFeedback = (args[11] as Pair<String, Boolean>?)?.second ?: false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BackupSyncUiState()
    )

    init {
        viewModelScope.launch {
            cloudSyncRepository.ensureCurrentDeviceRegistered(session)
        }
    }

    fun selectTab(tab: BackupSyncTab) {
        _selectedTab.value = tab
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun probeCloudConnection() {
        viewModelScope.launch {
            _isWorking.value = true
            try {
                val res = cloudSyncRepository.probeCloudConnection(session)
                _probeResult.value = res
                _feedback.value = res.summary to (!res.rtdbWritePermitted && res.isGoogleServicesPresent && res.isAuthenticated)
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun stageAndSyncAll() {
        viewModelScope.launch {
            _isWorking.value = true
            try {
                val stageRes = cloudSyncRepository.enqueueAuthorizedSnapshot(session)
                stageRes.onFailure { err ->
                    _feedback.value = (err.message ?: "Failed staging sync records.") to true
                    return@launch
                }
                val summary = cloudSyncRepository.syncPendingQueue(session, includeRetryableFailed = true)
                _feedback.value = summary.message to (summary.failedCount > 0)
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun retryFailedOnly() {
        viewModelScope.launch {
            _isWorking.value = true
            try {
                val summary = cloudSyncRepository.syncPendingQueue(session, includeRetryableFailed = true)
                _feedback.value = summary.message to (summary.failedCount > 0)
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun resolveConflict(conflictId: String, strategy: ConflictResolutionStrategy, notes: String) {
        viewModelScope.launch {
            _isWorking.value = true
            try {
                val res = cloudSyncRepository.resolveConflict(session, conflictId, strategy, notes)
                res.fold(
                    onSuccess = {
                        _feedback.value = "Conflict resolved (${strategy.label})." to false
                    },
                    onFailure = { err ->
                        _feedback.value = (err.message ?: "Conflict resolution failed.") to true
                    }
                )
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun createBackup(branchScope: String, notes: String, uploadToCloud: Boolean) {
        viewModelScope.launch {
            _isWorking.value = true
            try {
                val res = backupRestoreRepository.createBackup(session, branchScope, notes, uploadToCloud)
                res.fold(
                    onSuccess = { snap ->
                        _feedback.value = "Backup ${snap.backupId} created (${snap.recordCount} records, ${snap.cloudStatus})." to false
                    },
                    onFailure = { err ->
                        _feedback.value = (err.message ?: "Backup creation failed.") to true
                    }
                )
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun inspectBackupSnapshot(snapshot: BackupSnapshotEntity) {
        viewModelScope.launch {
            _isWorking.value = true
            try {
                val rawRes = backupRestoreRepository.readBackupFileJson(snapshot.backupId)
                rawRes.fold(
                    onSuccess = { json ->
                        val integrity = backupRestoreRepository.validateBackupJson(json, session)
                        _selectedBackupForRestore.value = snapshot
                        _importedBackupEnvelope.value = json to snapshot.backupId
                        _integrityResult.value = integrity
                        _feedback.value = (integrity.errorMessage ?: "SHA-256 verified (${integrity.totalRecords} records).") to !integrity.isValid
                    },
                    onFailure = { err ->
                        _feedback.value = (err.message ?: "Unable to read backup file.") to true
                    }
                )
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun importBackupUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isWorking.value = true
            try {
                val rawJson = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Could not read selected backup file.")
                val integrity = backupRestoreRepository.validateBackupJson(rawJson, session)
                _selectedBackupForRestore.value = null
                _importedBackupEnvelope.value = rawJson to (uri.lastPathSegment ?: "Imported JSON")
                _integrityResult.value = integrity
                _feedback.value = (integrity.errorMessage ?: "Imported backup SHA-256 verified (${integrity.totalRecords} records).") to !integrity.isValid
            } catch (e: Exception) {
                _feedback.value = (e.message ?: "Failed to import backup JSON.") to true
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun dismissRestoreInspector() {
        _selectedBackupForRestore.value = null
        _importedBackupEnvelope.value = null
        _integrityResult.value = null
    }

    fun confirmRestoreFromInspector(confirmationText: String) {
        val rawJson = _importedBackupEnvelope.value?.first ?: return
        viewModelScope.launch {
            _isWorking.value = true
            try {
                val res = backupRestoreRepository.restoreFromBackupJson(session, rawJson, confirmationText)
                res.fold(
                    onSuccess = { restoredRows ->
                        dismissRestoreInspector()
                        _feedback.value = "Restore completed ($restoredRows records restored & verified)." to false
                    },
                    onFailure = { err ->
                        _feedback.value = (err.message ?: "Backup restore rejected.") to true
                    }
                )
            } finally {
                _isWorking.value = false
            }
        }
    }

    suspend fun getBackupJsonForShare(backupId: String): Result<String> {
        return backupRestoreRepository.readBackupFileJson(backupId)
    }

    class Factory(
        private val session: UserSession,
        private val cloudSyncRepository: CloudSyncRepository,
        private val backupRestoreRepository: BackupRestoreRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BackupSyncViewModel(session, cloudSyncRepository, backupRestoreRepository) as T
        }
    }
}

enum class UserPermissionsTab(val label: String) {
    OPERATORS("Operators"),
    ROLE_MATRIX("Security Matrix"),
    DEVICES("Device Sessions")
}

data class UserPermissionsUiState(
    val selectedTab: UserPermissionsTab = UserPermissionsTab.OPERATORS,
    val operators: List<UserProfileEntity> = emptyList(),
    val rolePolicies: List<RolePermissionPolicyEntity> = emptyList(),
    val devices: List<DeviceSessionEntity> = emptyList(),
    val branches: List<BranchSummary> = emptyList(),
    val recentAuditLogs: List<AuditLogEntity> = emptyList(),
    val staffDirectory: List<StaffProfileEntity> = emptyList(),
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

class UserPermissionsViewModel(
    private val session: UserSession,
    private val database: AaCraveDatabase,
    private val securityAdminRepository: SecurityAdminRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(UserPermissionsTab.OPERATORS)
    private val _feedback = MutableStateFlow<Pair<String, Boolean>?>(null)

    private val branchSummariesFlow = database.branchDao().observeBranches().map { list ->
        list.map {
            BranchSummary(
                branchId = it.branchId,
                name = it.name,
                code = it.code,
                address = it.address,
                isMainBranch = it.isMainBranch,
                isActive = it.isActive
            )
        }
    }

    val uiState: StateFlow<UserPermissionsUiState> = combine(
        _selectedTab,
        securityAdminRepository.allUserProfiles,
        securityAdminRepository.rolePolicies,
        securityAdminRepository.devices,
        branchSummariesFlow,
        securityAdminRepository.recentAuditLogs,
        database.staffDao().observeAllStaff(),
        _feedback
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        UserPermissionsUiState(
            selectedTab = args[0] as UserPermissionsTab,
            operators = args[1] as List<UserProfileEntity>,
            rolePolicies = args[2] as List<RolePermissionPolicyEntity>,
            devices = args[3] as List<DeviceSessionEntity>,
            branches = args[4] as List<BranchSummary>,
            recentAuditLogs = args[5] as List<AuditLogEntity>,
            staffDirectory = args[6] as List<StaffProfileEntity>,
            feedbackMessage = (args[7] as Pair<String, Boolean>?)?.first,
            isErrorFeedback = (args[7] as Pair<String, Boolean>?)?.second ?: false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPermissionsUiState()
    )

    fun selectTab(tab: UserPermissionsTab) {
        _selectedTab.value = tab
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun saveOperator(
        uid: String,
        email: String,
        displayName: String,
        role: UserRole,
        branchId: String,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            securityAdminRepository.saveOperatorProfile(
                session = session,
                uid = uid,
                email = email,
                displayName = displayName,
                role = role,
                branchId = branchId,
                isActive = isActive
            ).fold(
                onSuccess = {
                    _feedback.value = "Saved operator profile $email (${role.displayName})." to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Failed saving operator profile.") to true
                }
            )
        }
    }

    fun suspendOrReactivateOperator(uid: String, active: Boolean, reason: String) {
        viewModelScope.launch {
            securityAdminRepository.setOperatorActiveStatus(session, uid, active, reason).fold(
                onSuccess = {
                    val verb = if (active) "reactivated" else "suspended"
                    _feedback.value = "Operator $verb successfully." to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Failed updating operator status.") to true
                }
            )
        }
    }

    fun createOrUpdateStaffAccount(
        existingUid: String?,
        staffId: String?,
        displayName: String,
        username: String,
        explicitEmail: String?,
        password: String?,
        role: UserRole,
        branchId: String,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            securityAdminRepository.createOrUpdateStaffLoginAccount(
                session = session,
                existingUid = existingUid,
                staffId = staffId,
                displayName = displayName,
                username = username,
                explicitEmail = explicitEmail,
                password = password,
                role = role,
                branchId = branchId,
                isActive = isActive
            ).fold(
                onSuccess = { res ->
                    _feedback.value = res.statusMessage to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Failed saving staff login account.") to true
                }
            )
        }
    }

    fun resetStaffAccountPassword(targetUid: String, newTemporaryPassword: String?) {
        viewModelScope.launch {
            securityAdminRepository.resetStaffAccountPassword(session, targetUid, newTemporaryPassword).fold(
                onSuccess = { msg ->
                    _feedback.value = msg to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Failed resetting staff account.") to true
                }
            )
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            securityAdminRepository.sendOperatorPasswordReset(session, email).fold(
                onSuccess = {
                    _feedback.value = "Password reset link dispatched to $email via Firebase Auth." to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Failed sending password reset email.") to true
                }
            )
        }
    }

    fun updateRolePolicy(policy: RolePermissionPolicyEntity) {
        viewModelScope.launch {
            securityAdminRepository.updateRolePermissionPolicy(session, policy).fold(
                onSuccess = {
                    _feedback.value = "Updated security policy for ${policy.roleCode}." to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Failed updating role policy.") to true
                }
            )
        }
    }

    fun setDeviceAuthorized(deviceId: String, authorized: Boolean) {
        viewModelScope.launch {
            securityAdminRepository.setDeviceAuthorized(session, deviceId, authorized).fold(
                onSuccess = {
                    val msg = if (authorized) "Device $deviceId authorized." else "Device $deviceId revoked."
                    _feedback.value = msg to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Device action failed.") to true
                }
            )
        }
    }

    class Factory(
        private val session: UserSession,
        private val database: AaCraveDatabase,
        private val securityAdminRepository: SecurityAdminRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UserPermissionsViewModel(session, database, securityAdminRepository) as T
        }
    }
}

data class NotificationsUiState(
    val selectedCategory: NotificationCategory = NotificationCategory.ALL,
    val allNotifications: List<ErpNotificationEntity> = emptyList(),
    val filteredNotifications: List<ErpNotificationEntity> = emptyList(),
    val unreadCount: Int = 0
)

class NotificationsViewModel(
    private val securityAdminRepository: SecurityAdminRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow(NotificationCategory.ALL)

    val uiState: StateFlow<NotificationsUiState> = combine(
        _selectedCategory,
        securityAdminRepository.notifications,
        securityAdminRepository.unreadNotificationCount
    ) { category, list, unread ->
        val filtered = if (category == NotificationCategory.ALL) {
            list
        } else {
            list.filter { it.category.equals(category.code, ignoreCase = true) }
        }
        NotificationsUiState(
            selectedCategory = category,
            allNotifications = list,
            filteredNotifications = filtered,
            unreadCount = unread
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotificationsUiState()
    )

    fun selectCategory(category: NotificationCategory) {
        _selectedCategory.value = category
    }

    fun markRead(notificationId: String) {
        viewModelScope.launch {
            securityAdminRepository.markNotificationRead(notificationId)
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            securityAdminRepository.markAllNotificationsRead()
        }
    }

    class Factory(
        private val securityAdminRepository: SecurityAdminRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NotificationsViewModel(securityAdminRepository) as T
        }
    }
}
