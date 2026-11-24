package com.example.data.repository

import com.example.data.cloud.CloudSyncGateway
import com.example.data.cloud.NetworkConnectivityMonitor
import com.example.data.local.AaCraveDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.DeviceSessionEntity
import com.example.data.local.ErpNotificationEntity
import com.example.data.local.RolePermissionPolicyEntity
import com.example.data.local.UserProfileEntity
import com.example.data.security.PasswordSecurity
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

data class StaffAccountProvisionResult(
    val profile: UserProfileEntity,
    val firebaseProvisioned: Boolean,
    val statusMessage: String
)

class SecurityAdminRepository(
    private val database: AaCraveDatabase,
    private val gateway: CloudSyncGateway,
    private val connectivityMonitor: NetworkConnectivityMonitor,
    private val authRepository: AuthRepository? = null,
    private val staffAuthProvisioner: StaffAuthProvisioner? = null
) {
    private val userProfileDao = database.userProfileDao()
    private val branchDao = database.branchDao()
    private val phase7Dao = database.phase7Dao()
    private val auditLogDao = database.auditLogDao()

    val allUserProfiles: Flow<List<UserProfileEntity>> = userProfileDao.observeAllProfiles()
    val rolePolicies: Flow<List<RolePermissionPolicyEntity>> = phase7Dao.observeRolePolicies()
    val devices: Flow<List<DeviceSessionEntity>> = phase7Dao.observeDevices()
    val notifications: Flow<List<ErpNotificationEntity>> = phase7Dao.observeNotifications()
    val unreadNotificationCount: Flow<Int> = phase7Dao.observeUnreadNotificationCount()
    val recentAuditLogs: Flow<List<AuditLogEntity>> = auditLogDao.observeRecentLogs()

    suspend fun saveOperatorProfile(
        session: UserSession,
        uid: String,
        email: String,
        displayName: String,
        role: UserRole,
        branchId: String,
        isActive: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return@withContext Result.failure(it)
        }
        if (!session.capabilities.canManageUserPermissions) {
            return@withContext Result.failure(SecurityException("Only Owner, Admin, or Super Admin can manage operator permissions."))
        }
        val cleanUid = uid.trim()
        val cleanEmail = email.trim()
        val cleanName = displayName.trim()
        if (cleanUid.isBlank() || cleanEmail.isBlank() || cleanName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("UID, email, and display name are required."))
        }
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid operator email address."))
        }

        val branch = branchDao.getBranchById(branchId)
            ?: return@withContext Result.failure(IllegalArgumentException("Assigned branch '$branchId' does not exist."))

        val isTopOwnerOrSuper = session.role == UserRole.OWNER || session.role == UserRole.SUPER_ADMIN
        if (!isTopOwnerOrSuper && (role == UserRole.OWNER || role == UserRole.SUPER_ADMIN)) {
            return@withContext Result.failure(
                SecurityException("Only Owner or Super Admin can assign the ${role.displayName} role.")
            )
        }

        if (cleanUid == session.uid && (!isActive || (role != UserRole.OWNER && role != UserRole.SUPER_ADMIN && role != UserRole.ADMIN))) {
            return@withContext Result.failure(IllegalStateException("You cannot deactivate or demote your own administrative account."))
        }

        val allProfiles = userProfileDao.observeAllProfiles().first()
        val existing = allProfiles.firstOrNull { it.uid == cleanUid }
        if (!isTopOwnerOrSuper && existing != null &&
            (existing.roleCode == UserRole.OWNER.code || existing.roleCode == UserRole.SUPER_ADMIN.code)
        ) {
            return@withContext Result.failure(
                SecurityException("Only Owner or Super Admin can modify an existing Owner or Super Admin account.")
            )
        }

        val duplicateEmail = allProfiles.firstOrNull {
            it.uid != cleanUid && it.email.equals(cleanEmail, ignoreCase = true)
        }
        if (duplicateEmail != null) {
            return@withContext Result.failure(
                IllegalArgumentException("Duplicate email rejected: '$cleanEmail' is already assigned to operator ${duplicateEmail.displayName} (${duplicateEmail.uid}).")
            )
        }

        if (existing != null && !isActive) {
            val remainingAdmins = allProfiles.count {
                it.uid != cleanUid && it.isActive && (it.roleCode == UserRole.OWNER.code || it.roleCode == UserRole.SUPER_ADMIN.code)
            }
            if ((existing.roleCode == UserRole.OWNER.code || existing.roleCode == UserRole.SUPER_ADMIN.code) && remainingAdmins == 0) {
                return@withContext Result.failure(IllegalStateException("At least one active Owner or Super Admin must remain enabled."))
            }
        }

        val now = System.currentTimeMillis()
        val entity = UserProfileEntity(
            uid = cleanUid,
            email = cleanEmail,
            displayName = cleanName,
            roleCode = role.code,
            branchId = branch.branchId,
            isActive = isActive,
            lastLoginAt = existing?.lastLoginAt ?: now,
            username = existing?.username.orEmpty(),
            staffId = existing?.staffId,
            passwordHash = existing?.passwordHash.orEmpty(),
            passwordSalt = existing?.passwordSalt.orEmpty(),
            authProvider = existing?.authProvider ?: "FIREBASE_EMAIL",
            mustResetPassword = existing?.mustResetPassword ?: false
        )
        userProfileDao.upsertProfile(entity)

        if (!cleanUid.startsWith("pending_") &&
            !cleanUid.startsWith("local_staff_") &&
            session.isFirebaseAuthenticated &&
            gateway.isFirebaseConfigured() &&
            connectivityMonitor.currentIsOnline()
        ) {
            gateway.upsertPath(
                "erp/users/$cleanUid",
                mapOf(
                    "uid" to cleanUid,
                    "username" to entity.username,
                    "email" to cleanEmail,
                    "displayName" to cleanName,
                    "roleCode" to role.code,
                    "branchId" to branch.branchId,
                    "isActive" to isActive,
                    "updatedAt" to now
                )
            )
        }

        auditLogDao.insertLog(
            AuditLogEntity(
                branchId = branch.branchId,
                actorUid = session.uid,
                actorEmail = session.email,
                actorRole = session.role.displayName,
                action = if (existing == null) "USER_OPERATOR_CREATED" else "USER_PERMISSION_UPDATED",
                details = "Operator $cleanEmail ($cleanUid) -> Role=${role.displayName}, Branch=${branch.name}, Active=$isActive"
            )
        )
        Result.success(Unit)
    }

    suspend fun createOrUpdateStaffLoginAccount(
        session: UserSession,
        existingUid: String?,
        staffId: String?,
        displayName: String,
        username: String,
        explicitEmail: String?,
        password: String?,
        role: UserRole,
        branchId: String,
        isActive: Boolean
    ): Result<StaffAccountProvisionResult> = withContext(Dispatchers.IO) {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return@withContext Result.failure(it)
        }
        if (!session.capabilities.canManageUserPermissions) {
            return@withContext Result.failure(
                SecurityException("Only Owner, Admin, or Super Admin can manage staff login accounts.")
            )
        }

        val cleanName = displayName.trim()
        if (cleanName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Staff full name is required."))
        }
        val cleanUsername = PasswordSecurity.normalizeUsername(username)
        PasswordSecurity.validateUsername(cleanUsername)?.let { err ->
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        val cleanExplicitEmail = explicitEmail?.trim().orEmpty()
        if (cleanExplicitEmail.isNotBlank() && (!cleanExplicitEmail.contains("@") || !cleanExplicitEmail.contains("."))) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid work email address or leave blank to use username login."))
        }
        val resolvedEmail = PasswordSecurity.deriveAuthEmail(cleanUsername, cleanExplicitEmail.takeIf { it.isNotBlank() })

        val branch = branchDao.getBranchById(branchId)
            ?: return@withContext Result.failure(IllegalArgumentException("Assigned branch '$branchId' does not exist."))

        val isTopOwnerOrSuper = session.role == UserRole.OWNER || session.role == UserRole.SUPER_ADMIN
        if (!isTopOwnerOrSuper && (role == UserRole.OWNER || role == UserRole.SUPER_ADMIN)) {
            return@withContext Result.failure(
                SecurityException("Only Owner or Super Admin can assign the ${role.displayName} role.")
            )
        }

        val allProfiles = userProfileDao.observeAllProfiles().first()
        val existing = existingUid?.let { uid -> allProfiles.firstOrNull { it.uid == uid } }
        if (existingUid != null && existing == null) {
            return@withContext Result.failure(IllegalArgumentException("Staff login account '$existingUid' not found."))
        }

        if (!isTopOwnerOrSuper && existing != null &&
            (existing.roleCode == UserRole.OWNER.code || existing.roleCode == UserRole.SUPER_ADMIN.code)
        ) {
            return@withContext Result.failure(
                SecurityException("Only Owner or Super Admin can modify an existing Owner or Super Admin account.")
            )
        }

        if (existing != null && existing.uid == session.uid &&
            (!isActive || (role != UserRole.OWNER && role != UserRole.SUPER_ADMIN && role != UserRole.ADMIN))
        ) {
            return@withContext Result.failure(
                IllegalStateException("You cannot deactivate or demote your own administrative account.")
            )
        }

        if (existing != null && !isActive) {
            val remainingOwners = allProfiles.count {
                it.uid != existing.uid && it.isActive &&
                    (it.roleCode == UserRole.OWNER.code || it.roleCode == UserRole.SUPER_ADMIN.code)
            }
            if ((existing.roleCode == UserRole.OWNER.code || existing.roleCode == UserRole.SUPER_ADMIN.code) && remainingOwners == 0) {
                return@withContext Result.failure(IllegalStateException("At least one active Owner or Super Admin must remain enabled."))
            }
        }

        val duplicateUsername = allProfiles.firstOrNull {
            it.uid != existing?.uid && it.username.isNotBlank() && it.username.equals(cleanUsername, ignoreCase = true)
        }
        if (duplicateUsername != null) {
            return@withContext Result.failure(
                IllegalArgumentException("Duplicate username rejected: '$cleanUsername' is already assigned to ${duplicateUsername.displayName}.")
            )
        }

        val duplicateEmail = allProfiles.firstOrNull {
            it.uid != existing?.uid && it.email.equals(resolvedEmail, ignoreCase = true)
        }
        if (duplicateEmail != null) {
            return@withContext Result.failure(
                IllegalArgumentException("Duplicate login email rejected: '$resolvedEmail' is already assigned to ${duplicateEmail.displayName}.")
            )
        }

        val cleanStaffId = staffId?.takeIf { it.isNotBlank() }
        if (cleanStaffId != null) {
            val alreadyLinked = allProfiles.firstOrNull {
                it.uid != existing?.uid && it.staffId == cleanStaffId
            }
            if (alreadyLinked != null) {
                return@withContext Result.failure(
                    IllegalArgumentException("This HR staff profile is already linked to login account '${alreadyLinked.username.ifBlank { alreadyLinked.email }}'.")
                )
            }
        }

        val now = System.currentTimeMillis()
        if (existing == null) {
            val rawPassword = password.orEmpty()
            PasswordSecurity.validatePassword(rawPassword)?.let { err ->
                return@withContext Result.failure(IllegalArgumentException(err))
            }
            val hashed = PasswordSecurity.hashPassword(rawPassword)

            val provisionOutcome = staffAuthProvisioner?.createStaffAuthAccount(
                email = resolvedEmail,
                password = rawPassword,
                displayName = cleanName
            ) ?: StaffAuthCreateOutcome.PendingLocalOnly("Local cryptographic staff account provisioned.")

            val (assignedUid, authProvider, firebaseProvisioned, statusNote) = when (provisionOutcome) {
                is StaffAuthCreateOutcome.Rejected -> {
                    return@withContext Result.failure(IllegalStateException(provisionOutcome.message))
                }
                is StaffAuthCreateOutcome.CreatedInFirebase -> Quad(
                    provisionOutcome.firebaseUid,
                    "FIREBASE_EMAIL",
                    true,
                    "Staff login '@$cleanUsername' created in Firebase Authentication (${role.displayName} • ${branch.name})."
                )
                is StaffAuthCreateOutcome.PendingLocalOnly -> Quad(
                    "local_staff_${UUID.randomUUID().toString().replace("-", "").take(10)}",
                    "LOCAL_PENDING_FIREBASE",
                    false,
                    "Staff login '@$cleanUsername' secured with PBKDF2 hash (${role.displayName} • ${branch.name}). ${provisionOutcome.reason}"
                )
            }

            val newProfile = UserProfileEntity(
                uid = assignedUid,
                email = resolvedEmail,
                displayName = cleanName,
                roleCode = role.code,
                branchId = branch.branchId,
                isActive = isActive,
                lastLoginAt = now,
                username = cleanUsername,
                staffId = cleanStaffId,
                passwordHash = hashed.hashBase64,
                passwordSalt = hashed.saltBase64,
                authProvider = authProvider,
                mustResetPassword = false
            )
            userProfileDao.upsertProfile(newProfile)
            linkHrStaffProfileIfPresent(cleanStaffId, assignedUid)

            if (firebaseProvisioned &&
                session.isFirebaseAuthenticated &&
                gateway.isFirebaseConfigured() &&
                connectivityMonitor.currentIsOnline()
            ) {
                gateway.upsertPath(
                    "erp/users/$assignedUid",
                    mapOf(
                        "uid" to assignedUid,
                        "username" to cleanUsername,
                        "email" to resolvedEmail,
                        "displayName" to cleanName,
                        "roleCode" to role.code,
                        "branchId" to branch.branchId,
                        "isActive" to isActive,
                        "updatedAt" to now
                    )
                )
            }

            auditLogDao.insertLog(
                AuditLogEntity(
                    branchId = branch.branchId,
                    actorUid = session.uid,
                    actorEmail = session.email,
                    actorRole = session.role.displayName,
                    action = "STAFF_LOGIN_ACCOUNT_CREATED",
                    details = "Created staff login '@$cleanUsername' ($cleanName) -> Role=${role.displayName}, Branch=${branch.name}, FirebaseAuth=$firebaseProvisioned, Active=$isActive"
                )
            )
            Result.success(StaffAccountProvisionResult(newProfile, firebaseProvisioned, statusNote))
        } else {
            val updated = existing.copy(
                email = resolvedEmail,
                displayName = cleanName,
                roleCode = role.code,
                branchId = branch.branchId,
                isActive = isActive,
                username = cleanUsername,
                staffId = cleanStaffId
            )
            userProfileDao.upsertProfile(updated)
            linkHrStaffProfileIfPresent(cleanStaffId, updated.uid)

            if (!updated.uid.startsWith("pending_") &&
                !updated.uid.startsWith("local_staff_") &&
                session.isFirebaseAuthenticated &&
                gateway.isFirebaseConfigured() &&
                connectivityMonitor.currentIsOnline()
            ) {
                gateway.upsertPath(
                    "erp/users/${updated.uid}",
                    mapOf(
                        "uid" to updated.uid,
                        "username" to cleanUsername,
                        "email" to resolvedEmail,
                        "displayName" to cleanName,
                        "roleCode" to role.code,
                        "branchId" to branch.branchId,
                        "isActive" to isActive,
                        "updatedAt" to now
                    )
                )
            }

            auditLogDao.insertLog(
                AuditLogEntity(
                    branchId = branch.branchId,
                    actorUid = session.uid,
                    actorEmail = session.email,
                    actorRole = session.role.displayName,
                    action = "USER_PERMISSION_UPDATED",
                    details = "Updated staff login '@$cleanUsername' (${updated.uid}) -> Role=${role.displayName}, Branch=${branch.name}, Active=$isActive"
                )
            )
            Result.success(
                StaffAccountProvisionResult(
                    profile = updated,
                    firebaseProvisioned = updated.authProvider == "FIREBASE_EMAIL",
                    statusMessage = "Updated staff account '@$cleanUsername' (${role.displayName} • ${branch.name})."
                )
            )
        }
    }

    private suspend fun linkHrStaffProfileIfPresent(staffId: String?, userUid: String) {
        if (staffId.isNullOrBlank()) return
        val staff = database.staffDao().getStaffById(staffId) ?: return
        if (staff.linkedUserUid != userUid) {
            database.staffDao().upsertStaff(staff.copy(linkedUserUid = userUid, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun resetStaffAccountPassword(
        session: UserSession,
        targetUid: String,
        newTemporaryPassword: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return@withContext Result.failure(it)
        }
        if (!session.capabilities.canManageUserPermissions) {
            return@withContext Result.failure(
                SecurityException("Only Owner, Admin, or Super Admin can reset staff accounts.")
            )
        }
        val existing = userProfileDao.getProfileByUid(targetUid)
            ?: return@withContext Result.failure(IllegalArgumentException("Staff account not found."))

        val targetRole = UserRole.fromCode(existing.roleCode)
        val isTopOwnerOrSuper = session.role == UserRole.OWNER || session.role == UserRole.SUPER_ADMIN
        if (!isTopOwnerOrSuper && (targetRole == UserRole.OWNER || targetRole == UserRole.SUPER_ADMIN)) {
            return@withContext Result.failure(
                SecurityException("Only Owner or Super Admin can reset an executive account.")
            )
        }

        if (existing.authProvider == "LOCAL_PENDING_FIREBASE") {
            val rawPass = newTemporaryPassword.orEmpty()
            PasswordSecurity.validatePassword(rawPass)?.let { err ->
                return@withContext Result.failure(IllegalArgumentException(err))
            }
            val hashed = PasswordSecurity.hashPassword(rawPass)
            userProfileDao.upsertProfile(
                existing.copy(
                    passwordHash = hashed.hashBase64,
                    passwordSalt = hashed.saltBase64,
                    mustResetPassword = true
                )
            )
            auditLogDao.insertLog(
                AuditLogEntity(
                    branchId = existing.branchId,
                    actorUid = session.uid,
                    actorEmail = session.email,
                    actorRole = session.role.displayName,
                    action = "STAFF_ACCOUNT_PASSWORD_RESET",
                    details = "Reset local credentials for '@${existing.username.ifBlank { existing.email }}' (${existing.uid})"
                )
            )
            return@withContext Result.success(
                "Temporary password updated securely (PBKDF2 hashed) for '@${existing.username.ifBlank { existing.email }}'."
            )
        }

        if (PasswordSecurity.isInternalDerivedEmail(existing.email)) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Account '@${existing.username}' is linked to Firebase Auth with an internal username-only login email (${existing.email}). Add a real work email on this profile or reset the user in Firebase Console -> Authentication."
                )
            )
        }

        val repo = authRepository
            ?: return@withContext Result.failure(IllegalStateException("AuthRepository is not attached for password reset dispatch."))
        val resetRes = repo.sendPasswordReset(existing.email)
        if (resetRes.isFailure) {
            return@withContext Result.failure(
                resetRes.exceptionOrNull() ?: IllegalStateException("Failed to dispatch Firebase password reset.")
            )
        }
        auditLogDao.insertLog(
            AuditLogEntity(
                branchId = existing.branchId,
                actorUid = session.uid,
                actorEmail = session.email,
                actorRole = session.role.displayName,
                action = "ADMIN_PASSWORD_RESET_DISPATCHED",
                details = "Dispatched Firebase password reset email to ${existing.email} (${existing.uid})"
            )
        )
        Result.success("Firebase password reset email sent to ${existing.email}.")
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    suspend fun setOperatorActiveStatus(
        session: UserSession,
        targetUid: String,
        active: Boolean,
        reason: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return@withContext Result.failure(it)
        }
        if (!session.capabilities.canManageUserPermissions) {
            return@withContext Result.failure(SecurityException("Only Owner, Admin, or Super Admin can suspend or reactivate operators."))
        }
        val existing = userProfileDao.getProfileByUid(targetUid)
            ?: return@withContext Result.failure(IllegalArgumentException("Operator profile not found."))
        val role = UserRole.fromCode(existing.roleCode)
        val saveRes = saveOperatorProfile(
            session = session,
            uid = existing.uid,
            email = existing.email,
            displayName = existing.displayName,
            role = role,
            branchId = existing.branchId,
            isActive = active
        )
        if (saveRes.isSuccess) {
            auditLogDao.insertLog(
                AuditLogEntity(
                    branchId = existing.branchId,
                    actorUid = session.uid,
                    actorEmail = session.email,
                    actorRole = session.role.displayName,
                    action = if (active) "USER_REACTIVATED" else "USER_SUSPENDED",
                    details = "${existing.email} (${existing.uid}) ${if (active) "reactivated" else "suspended"}${if (reason.isNotBlank()) " — ${reason.trim()}" else ""}"
                )
            )
        }
        saveRes
    }

    suspend fun sendOperatorPasswordReset(
        session: UserSession,
        targetEmail: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return@withContext Result.failure(it)
        }
        if (!session.capabilities.canManageUserPermissions) {
            return@withContext Result.failure(SecurityException("Only Owner, Admin, or Super Admin can trigger operator password resets."))
        }
        val repo = authRepository
            ?: return@withContext Result.failure(IllegalStateException("AuthRepository is not attached for password reset dispatch."))
        val result = repo.sendPasswordReset(targetEmail)
        if (result.isSuccess) {
            auditLogDao.insertLog(
                AuditLogEntity(
                    branchId = session.branchId,
                    actorUid = session.uid,
                    actorEmail = session.email,
                    actorRole = session.role.displayName,
                    action = "ADMIN_PASSWORD_RESET_DISPATCHED",
                    details = "Dispatched Firebase password reset email to ${targetEmail.trim()}"
                )
            )
        }
        result
    }

    suspend fun updateRolePermissionPolicy(
        session: UserSession,
        policy: RolePermissionPolicyEntity
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return@withContext Result.failure(it)
        }
        if (!session.capabilities.canManageUserPermissions) {
            return@withContext Result.failure(SecurityException("Only Owner, Admin, or Super Admin can modify role security policies."))
        }
        if (policy.roleCode == UserRole.SUPER_ADMIN.code || policy.roleCode == UserRole.OWNER.code) {
            if (session.role != UserRole.SUPER_ADMIN && session.role != UserRole.OWNER) {
                return@withContext Result.failure(SecurityException("Only Owner or Super Admin can modify executive security policy."))
            }
        }
        if (policy.roleCode == UserRole.SUPER_ADMIN.code) {
            return@withContext Result.failure(IllegalStateException("Super Admin core security policy cannot be restricted."))
        }

        val now = System.currentTimeMillis()
        val updated = policy.copy(updatedBy = session.displayName, updatedAt = now)
        phase7Dao.upsertRolePolicy(updated)

        if (session.isFirebaseAuthenticated && gateway.isFirebaseConfigured() && connectivityMonitor.currentIsOnline()) {
            gateway.upsertPath(
                "erp/security/role_policies/${updated.roleCode}",
                mapOf(
                    "roleCode" to updated.roleCode,
                    "canTriggerCloudSync" to updated.canTriggerCloudSync,
                    "canResolveSyncConflicts" to updated.canResolveSyncConflicts,
                    "canResolveFinancialConflicts" to updated.canResolveFinancialConflicts,
                    "canCreateBackup" to updated.canCreateBackup,
                    "canRestoreBackup" to updated.canRestoreBackup,
                    "canManageDevices" to updated.canManageDevices,
                    "canManageUserPermissions" to updated.canManageUserPermissions,
                    "canApproveFinancialAdjustments" to updated.canApproveFinancialAdjustments,
                    "canExportReports" to updated.canExportReports,
                    "updatedBy" to updated.updatedBy,
                    "updatedAt" to now
                )
            )
        }

        auditLogDao.insertLog(
            AuditLogEntity(
                branchId = session.branchId,
                actorUid = session.uid,
                actorEmail = session.email,
                actorRole = session.role.displayName,
                action = "ROLE_POLICY_UPDATED",
                details = "Updated cloud/security permissions for ${updated.roleCode}"
            )
        )
        Result.success(Unit)
    }

    suspend fun setDeviceAuthorized(
        session: UserSession,
        deviceId: String,
        authorized: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return@withContext Result.failure(it)
        }
        if (!session.capabilities.canManageDevices) {
            return@withContext Result.failure(SecurityException("Only Owner, Admin, or Super Admin can authorize or revoke devices."))
        }
        val existing = phase7Dao.getDeviceById(deviceId)
            ?: return@withContext Result.failure(IllegalArgumentException("Device session not found."))

        val now = System.currentTimeMillis()
        val updated = existing.copy(
            isAuthorized = authorized,
            syncStatus = if (authorized) "ACTIVE" else "REVOKED",
            lastSeenAt = now
        )
        phase7Dao.upsertDevice(updated)

        if (session.isFirebaseAuthenticated && gateway.isFirebaseConfigured() && connectivityMonitor.currentIsOnline()) {
            gateway.upsertPath(
                "erp/devices/$deviceId",
                mapOf(
                    "deviceId" to updated.deviceId,
                    "deviceName" to updated.deviceName,
                    "osVersion" to updated.osVersion,
                    "operatorUid" to updated.operatorUid,
                    "operatorEmail" to updated.operatorEmail,
                    "operatorRole" to updated.operatorRole,
                    "branchId" to updated.branchId,
                    "isAuthorized" to authorized,
                    "lastSeenAt" to now
                )
            )
        }

        phase7Dao.insertNotificationIfNew(
            ErpNotificationEntity(
                notificationId = "notif_dev_${UUID.randomUUID().toString().take(10)}",
                branchId = session.branchId,
                category = "SECURITY",
                severity = if (authorized) "INFO" else "CRITICAL",
                title = if (authorized) "Device Authorized (${updated.deviceName})" else "Device Access Revoked (${updated.deviceName})",
                message = "${session.displayName} set authorization=$authorized for device ${updated.deviceId}.",
                relatedEntityType = "DEVICE",
                relatedEntityId = deviceId,
                createdAt = now
            )
        )
        auditLogDao.insertLog(
            AuditLogEntity(
                branchId = session.branchId,
                actorUid = session.uid,
                actorEmail = session.email,
                actorRole = session.role.displayName,
                action = if (authorized) "DEVICE_AUTHORIZED" else "DEVICE_REVOKED",
                details = "Device ${updated.deviceName} (${updated.deviceId}) authorization=$authorized"
            )
        )
        Result.success(Unit)
    }

    suspend fun markNotificationRead(notificationId: String) = withContext(Dispatchers.IO) {
        phase7Dao.markNotificationRead(notificationId)
    }

    suspend fun markAllNotificationsRead() = withContext(Dispatchers.IO) {
        phase7Dao.markAllNotificationsRead()
    }
}
