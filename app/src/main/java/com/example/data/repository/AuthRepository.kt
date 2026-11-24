package com.example.data.repository

import android.content.Context
import com.example.data.local.AuditLogDao
import com.example.data.local.AuditLogEntity
import com.example.data.local.BranchDao
import com.example.data.local.DatabaseSeeder
import com.example.data.local.SessionPreferences
import com.example.data.local.UserProfileDao
import com.example.data.local.UserProfileEntity
import com.example.data.security.PasswordSecurity
import com.example.domain.model.FirebaseEnvironmentStatus
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.Locale

sealed interface StaffAuthCreateOutcome {
    data class CreatedInFirebase(val firebaseUid: String) : StaffAuthCreateOutcome
    data class PendingLocalOnly(val reason: String) : StaffAuthCreateOutcome
    data class Rejected(val message: String) : StaffAuthCreateOutcome
}

interface StaffAuthProvisioner {
    suspend fun createStaffAuthAccount(
        email: String,
        password: String,
        displayName: String
    ): StaffAuthCreateOutcome
}

/**
 * Provisions new staff Email/Password accounts in the existing Firebase project
 * using a secondary FirebaseApp instance so the active Owner/Admin remains signed in.
 */
class FirebaseStaffAuthProvisioner(private val context: Context) : StaffAuthProvisioner {
    override suspend fun createStaffAuthAccount(
        email: String,
        password: String,
        displayName: String
    ): StaffAuthCreateOutcome {
        val apps = try {
            FirebaseApp.getApps(context)
        } catch (_: Exception) {
            emptyList()
        }
        val primaryApp = apps.firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME } ?: apps.firstOrNull()
            ?: return StaffAuthCreateOutcome.PendingLocalOnly("Firebase configuration is unavailable on this device.")

        val secondaryApp = apps.firstOrNull { it.name == SECONDARY_APP_NAME }
            ?: try {
                FirebaseApp.initializeApp(context.applicationContext, primaryApp.options, SECONDARY_APP_NAME)
            } catch (e: Exception) {
                return StaffAuthCreateOutcome.PendingLocalOnly(e.message ?: "Unable to initialize Firebase provisioning helper.")
            }

        val secondaryAuth = try {
            FirebaseAuth.getInstance(secondaryApp)
        } catch (e: Exception) {
            return StaffAuthCreateOutcome.PendingLocalOnly(e.message ?: "Firebase Auth helper unavailable.")
        }

        return try {
            val result = secondaryAuth.createUserWithEmailAndPassword(email, password).await()
            val createdUser = result.user
                ?: return StaffAuthCreateOutcome.PendingLocalOnly("Firebase Auth returned no user record.")
            if (displayName.isNotBlank()) {
                runCatching {
                    val req = UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName)
                        .build()
                    createdUser.updateProfile(req).await()
                }
            }
            StaffAuthCreateOutcome.CreatedInFirebase(createdUser.uid)
        } catch (e: CancellationException) {
            throw e
        } catch (_: FirebaseAuthUserCollisionException) {
            StaffAuthCreateOutcome.Rejected("Login email/username '$email' is already registered in Firebase Authentication.")
        } catch (e: FirebaseAuthWeakPasswordException) {
            StaffAuthCreateOutcome.Rejected(e.reason ?: "Password does not meet Firebase security requirements (min 6 characters).")
        } catch (e: FirebaseNetworkException) {
            StaffAuthCreateOutcome.PendingLocalOnly(
                "Offline: account secured locally with PBKDF2 hash and queued for Firebase Auth on first online login (${e.message ?: "network"})."
            )
        } catch (e: FirebaseAuthException) {
            StaffAuthCreateOutcome.Rejected(e.localizedMessage ?: "Firebase Auth rejected account creation (${e.errorCode}).")
        } catch (e: Exception) {
            StaffAuthCreateOutcome.PendingLocalOnly(e.message ?: "Saved locally; Firebase Auth provisioning deferred.")
        } finally {
            runCatching { secondaryAuth.signOut() }
        }
    }

    private companion object {
        const val SECONDARY_APP_NAME = "aa_crave_staff_provisioner"
    }
}

interface AuthRepository {
    val environmentStatus: StateFlow<FirebaseEnvironmentStatus>
    val currentSession: StateFlow<UserSession?>
    val activeBranchId: Flow<String>

    suspend fun restoreSession()
    suspend fun signInWithEmail(email: String, password: String): Result<UserSession>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun enterLocalPreviewSandbox(role: UserRole): Result<UserSession>
    suspend fun switchActiveBranch(branchId: String): Result<Unit>
    suspend fun signOut()
    fun refreshEnvironmentStatus(realtimeDbStatus: String? = null, statusDetail: String? = null)
}

class FirebaseAuthRepository(
    private val context: Context,
    private val userProfileDao: UserProfileDao,
    private val branchDao: BranchDao,
    private val auditLogDao: AuditLogDao,
    private val sessionPreferences: SessionPreferences
) : AuthRepository {

    private val _environmentStatus = MutableStateFlow(evaluateFirebaseEnvironment())
    override val environmentStatus: StateFlow<FirebaseEnvironmentStatus> = _environmentStatus.asStateFlow()

    private val _currentSession = MutableStateFlow<UserSession?>(null)
    override val currentSession: StateFlow<UserSession?> = _currentSession.asStateFlow()

    override val activeBranchId: Flow<String> = sessionPreferences.activeBranchId

    private fun getFirebaseAuthOrNull(): FirebaseAuth? {
        return try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else {
                null
            }
        } catch (_: IllegalStateException) {
            null
        }
    }

    private fun evaluateFirebaseEnvironment(): FirebaseEnvironmentStatus {
        val apps = try {
            FirebaseApp.getApps(context)
        } catch (_: Exception) {
            emptyList()
        }
        val isConfigured = apps.isNotEmpty()
        return if (isConfigured) {
            val appOptions = apps.first().options
            val configuredProjectId = appOptions.projectId ?: com.example.domain.model.FirebaseCloudConstants.PROJECT_ID
            FirebaseEnvironmentStatus(
                isGoogleServicesConfigured = true,
                isFirebaseAuthAvailable = true,
                projectName = configuredProjectId,
                realtimeDbUrl = com.example.domain.model.FirebaseCloudConstants.RTDB_URL,
                expectedPackageName = com.example.domain.model.FirebaseCloudConstants.EXPECTED_PACKAGE,
                realtimeDbStatus = "Configured • Ready to Probe Rules",
                statusDetail = "Firebase Auth (Email/Password) ready for project '$configuredProjectId'. RTDB URL: ${com.example.domain.model.FirebaseCloudConstants.RTDB_URL}."
            )
        } else {
            FirebaseEnvironmentStatus(
                isGoogleServicesConfigured = false,
                isFirebaseAuthAvailable = false,
                projectName = com.example.domain.model.FirebaseCloudConstants.PROJECT_ID,
                realtimeDbUrl = com.example.domain.model.FirebaseCloudConstants.RTDB_URL,
                expectedPackageName = com.example.domain.model.FirebaseCloudConstants.EXPECTED_PACKAGE,
                realtimeDbStatus = "Not Linked (Missing google-services.json)",
                statusDetail = "Add app/google-services.json for project '${com.example.domain.model.FirebaseCloudConstants.PROJECT_ID}' (package ${com.example.domain.model.FirebaseCloudConstants.EXPECTED_PACKAGE}) to enable live Email/Password Auth and RTDB sync."
            )
        }
    }

    override fun refreshEnvironmentStatus(realtimeDbStatus: String?, statusDetail: String?) {
        val base = evaluateFirebaseEnvironment()
        _environmentStatus.value = base.copy(
            realtimeDbStatus = realtimeDbStatus ?: base.realtimeDbStatus,
            statusDetail = statusDetail ?: base.statusDetail
        )
    }

    override suspend fun restoreSession() {
        _environmentStatus.value = evaluateFirebaseEnvironment()
        val branchId = sessionPreferences.activeBranchId.first()
        val auth = getFirebaseAuthOrNull()
        val firebaseUser = auth?.currentUser

        if (firebaseUser != null) {
            val session = buildFirebaseSession(firebaseUser, branchId)
            if (session == null) {
                auth.signOut()
                _currentSession.value = null
            } else {
                _currentSession.value = session
            }
            return
        }

        val localStaffUid = sessionPreferences.localStaffUid.first()
        if (!localStaffUid.isNullOrBlank()) {
            val profile = userProfileDao.getProfileByUid(localStaffUid)
            if (profile != null && profile.isActive) {
                val role = UserRole.fromCode(profile.roleCode)
                val branch = branchDao.getBranchById(profile.branchId) ?: branchDao.getBranchById(branchId)
                _currentSession.value = UserSession(
                    uid = profile.uid,
                    email = profile.email,
                    displayName = profile.displayName,
                    role = role,
                    branchId = branch?.branchId ?: profile.branchId,
                    branchName = branch?.name ?: "AA CRAVE - Flagship Main",
                    isFirebaseAuthenticated = false,
                    isPreviewSandbox = false
                )
                return
            } else {
                sessionPreferences.setLocalStaffUid(null)
            }
        }

        val sandboxRoleCode = sessionPreferences.previewRoleCode.first()
        if (!sandboxRoleCode.isNullOrBlank()) {
            val role = UserRole.fromCode(sandboxRoleCode)
            val branch = branchDao.getBranchById(branchId)
            _currentSession.value = UserSession(
                uid = "local-preview-${role.code.lowercase()}",
                email = "${role.code.lowercase()}@preview.local",
                displayName = "${role.displayName} (Preview)",
                role = role,
                branchId = branch?.branchId ?: DatabaseSeeder.MAIN_BRANCH_ID,
                branchName = branch?.name ?: "AA CRAVE - Flagship Main",
                isFirebaseAuthenticated = false,
                isPreviewSandbox = true
            )
        } else {
            _currentSession.value = null
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<UserSession> {
        val identifier = email.trim()
        if (identifier.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Username or email and password are required."))
        }

        val isEmailInput = identifier.contains("@")
        val matchedProfile = if (isEmailInput) {
            userProfileDao.getProfileByEmail(identifier)
        } else {
            userProfileDao.getProfileByUsername(identifier)
        }

        if (matchedProfile != null && !matchedProfile.isActive) {
            auditLogDao.insertLog(
                AuditLogEntity(
                    branchId = matchedProfile.branchId,
                    actorUid = matchedProfile.uid,
                    actorEmail = matchedProfile.email,
                    actorRole = UserRole.fromCode(matchedProfile.roleCode).displayName,
                    action = "SUSPENDED_LOGIN_BLOCKED",
                    details = "Blocked sign-in attempt for deactivated account ${matchedProfile.username.ifBlank { matchedProfile.email }}"
                )
            )
            return Result.failure(
                SecurityException("This staff account is deactivated or suspended. Contact an Owner or Admin.")
            )
        }

        val resolvedEmail = when {
            isEmailInput -> identifier
            matchedProfile != null -> matchedProfile.email
            else -> PasswordSecurity.deriveAuthEmail(identifier, null)
        }

        val auth = getFirebaseAuthOrNull()
        if (auth == null) {
            if (matchedProfile != null && matchedProfile.passwordHash.isNotBlank()) {
                return authenticateWithLocalProfileHash(matchedProfile, password)
            }
            return Result.failure(
                IllegalStateException(
                    "Firebase configuration is missing (app/google-services.json not found for 'AA CRAVE ERP'). Add google-services.json to authenticate live users, or use Local Sandbox Preview below."
                )
            )
        }

        // If this staff account was created offline and queued for Firebase Auth provisioning, verify PBKDF2 hash then mint in Firebase Auth.
        if (matchedProfile != null && matchedProfile.authProvider == "LOCAL_PENDING_FIREBASE") {
            if (!PasswordSecurity.verifyPassword(password, matchedProfile.passwordSalt, matchedProfile.passwordHash)) {
                return Result.failure(SecurityException("Invalid username/email or password. Please verify your credentials."))
            }
            return try {
                val created = auth.createUserWithEmailAndPassword(resolvedEmail, password).await()
                val user = created.user
                    ?: return authenticateWithLocalProfileHash(matchedProfile, password)
                runCatching {
                    val req = UserProfileChangeRequest.Builder()
                        .setDisplayName(matchedProfile.displayName)
                        .build()
                    user.updateProfile(req).await()
                }
                promotePendingLocalAccountToFirebase(matchedProfile, user)
            } catch (e: CancellationException) {
                throw e
            } catch (_: FirebaseAuthUserCollisionException) {
                // Already exists in Firebase Auth; proceed with standard Firebase sign-in below.
                signInViaFirebaseAuth(auth, resolvedEmail, password)
            } catch (_: FirebaseNetworkException) {
                authenticateWithLocalProfileHash(matchedProfile, password)
            } catch (e: FirebaseAuthException) {
                Result.failure(IllegalStateException(mapFirebaseAuthError(e)))
            } catch (e: Exception) {
                Result.failure(IllegalStateException(e.message ?: "Authentication failed."))
            }
        }

        return signInViaFirebaseAuth(auth, resolvedEmail, password)
    }

    private suspend fun signInViaFirebaseAuth(
        auth: FirebaseAuth,
        resolvedEmail: String,
        password: String
    ): Result<UserSession> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(resolvedEmail, password).await()
            val user = authResult.user
                ?: return Result.failure(IllegalStateException("Firebase sign-in returned no user session."))

            sessionPreferences.setPreviewRoleCode(null)
            sessionPreferences.setLocalStaffUid(null)
            val branchId = sessionPreferences.activeBranchId.first()
            val session = buildFirebaseSession(user, branchId)
            if (session == null) {
                auth.signOut()
                _currentSession.value = null
                return Result.failure(
                    SecurityException("This operator account is suspended or deactivated. Contact an Owner or Admin.")
                )
            }
            _currentSession.value = session

            auditLogDao.insertLog(
                AuditLogEntity(
                    branchId = session.branchId,
                    actorUid = session.uid,
                    actorEmail = session.email,
                    actorRole = session.role.displayName,
                    action = "FIREBASE_LOGIN",
                    details = "Authenticated via Firebase Email/Password (${session.branchName})"
                )
            )
            Result.success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthException) {
            Result.failure(IllegalStateException(mapFirebaseAuthError(e)))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Authentication failed."))
        }
    }

    private suspend fun promotePendingLocalAccountToFirebase(
        localProfile: UserProfileEntity,
        user: FirebaseUser
    ): Result<UserSession> {
        sessionPreferences.setPreviewRoleCode(null)
        sessionPreferences.setLocalStaffUid(null)
        val now = System.currentTimeMillis()
        val role = UserRole.fromCode(localProfile.roleCode)
        val branch = branchDao.getBranchById(localProfile.branchId)
        val promoted = localProfile.copy(
            uid = user.uid,
            authProvider = "FIREBASE_EMAIL",
            mustResetPassword = false,
            lastLoginAt = now
        )
        if (localProfile.uid != user.uid) {
            userProfileDao.deleteProfileByUid(localProfile.uid)
        }
        userProfileDao.upsertProfile(promoted)

        val session = UserSession(
            uid = user.uid,
            email = promoted.email,
            displayName = promoted.displayName,
            role = role,
            branchId = branch?.branchId ?: promoted.branchId,
            branchName = branch?.name ?: "AA CRAVE - Flagship Main",
            isFirebaseAuthenticated = true,
            isPreviewSandbox = false
        )
        _currentSession.value = session
        auditLogDao.insertLog(
            AuditLogEntity(
                branchId = session.branchId,
                actorUid = session.uid,
                actorEmail = session.email,
                actorRole = session.role.displayName,
                action = "STAFF_FIREBASE_PROVISIONED_ON_LOGIN",
                details = "Linked pre-provisioned staff account ${promoted.username} to Firebase Auth UID ${user.uid}"
            )
        )
        return Result.success(session)
    }

    private suspend fun authenticateWithLocalProfileHash(
        profile: UserProfileEntity,
        password: String
    ): Result<UserSession> {
        if (!profile.isActive) {
            return Result.failure(
                SecurityException("This staff account is deactivated or suspended. Contact an Owner or Admin.")
            )
        }
        if (!PasswordSecurity.verifyPassword(password, profile.passwordSalt, profile.passwordHash)) {
            return Result.failure(SecurityException("Invalid username/email or password. Please verify your credentials."))
        }
        val role = UserRole.fromCode(profile.roleCode)
        val branch = branchDao.getBranchById(profile.branchId)
        val now = System.currentTimeMillis()
        userProfileDao.upsertProfile(profile.copy(lastLoginAt = now))
        sessionPreferences.setPreviewRoleCode(null)
        sessionPreferences.setLocalStaffUid(profile.uid)

        val session = UserSession(
            uid = profile.uid,
            email = profile.email,
            displayName = profile.displayName,
            role = role,
            branchId = branch?.branchId ?: profile.branchId,
            branchName = branch?.name ?: "AA CRAVE - Flagship Main",
            isFirebaseAuthenticated = false,
            isPreviewSandbox = false
        )
        _currentSession.value = session
        auditLogDao.insertLog(
            AuditLogEntity(
                branchId = session.branchId,
                actorUid = session.uid,
                actorEmail = session.email,
                actorRole = session.role.displayName,
                action = "STAFF_ACCOUNT_LOGIN",
                details = "Staff account '${profile.username.ifBlank { profile.email }}' signed in (${role.displayName} • ${session.branchName})"
            )
        )
        return Result.success(session)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Enter a valid work email address."))
        }
        val auth = getFirebaseAuthOrNull()
            ?: return Result.failure(
                IllegalStateException(
                    "Cannot send Firebase password reset because app/google-services.json for 'AA CRAVE ERP' is not configured yet."
                )
            )

        return try {
            auth.sendPasswordResetEmail(trimmedEmail).await()
            val branchId = sessionPreferences.activeBranchId.first()
            auditLogDao.insertLog(
                AuditLogEntity(
                    branchId = branchId,
                    actorUid = "password-reset",
                    actorEmail = trimmedEmail,
                    actorRole = "Unauthenticated",
                    action = "PASSWORD_RESET_REQUEST",
                    details = "Firebase password reset email dispatched to $trimmedEmail"
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Failed to send password reset email."))
        }
    }

    override suspend fun enterLocalPreviewSandbox(role: UserRole): Result<UserSession> {
        return try {
            val branchId = sessionPreferences.activeBranchId.first()
            val branch = branchDao.getBranchById(branchId)
            sessionPreferences.setPreviewRoleCode(role.code)

            val session = UserSession(
                uid = "local-preview-${role.code.lowercase()}",
                email = "${role.code.lowercase()}@preview.local",
                displayName = "${role.displayName} (Sandbox)",
                role = role,
                branchId = branch?.branchId ?: DatabaseSeeder.MAIN_BRANCH_ID,
                branchName = branch?.name ?: "AA CRAVE - Flagship Main",
                isFirebaseAuthenticated = false,
                isPreviewSandbox = true
            )
            _currentSession.value = session

            auditLogDao.insertLog(
                AuditLogEntity(
                    branchId = session.branchId,
                    actorUid = session.uid,
                    actorEmail = session.email,
                    actorRole = session.role.displayName,
                    action = "SANDBOX_PREVIEW_SESSION",
                    details = "Entered offline local preview mode as ${role.displayName} (non-Firebase sample data)"
                )
            )
            Result.success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun switchActiveBranch(branchId: String): Result<Unit> {
        val current = _currentSession.value ?: return Result.failure(IllegalStateException("No active session."))
        if (!current.capabilities.canSwitchBranches) {
            return Result.failure(SecurityException("Role '${current.role.displayName}' is not authorized to switch branches."))
        }
        val branch = branchDao.getBranchById(branchId)
            ?: return Result.failure(IllegalArgumentException("Branch not found."))

        sessionPreferences.setActiveBranchId(branch.branchId)
        _currentSession.value = current.copy(branchId = branch.branchId, branchName = branch.name)

        auditLogDao.insertLog(
            AuditLogEntity(
                branchId = branch.branchId,
                actorUid = current.uid,
                actorEmail = current.email,
                actorRole = current.role.displayName,
                action = "BRANCH_SWITCH",
                details = "Switched active workspace to ${branch.name} (${branch.code})"
            )
        )
        return Result.success(Unit)
    }

    override suspend fun signOut() {
        val current = _currentSession.value
        if (current != null) {
            auditLogDao.insertLog(
                AuditLogEntity(
                    branchId = current.branchId,
                    actorUid = current.uid,
                    actorEmail = current.email,
                    actorRole = current.role.displayName,
                    action = "LOGOUT",
                    details = "Operator signed out of AA CRAVE ERP"
                )
            )
        }
        getFirebaseAuthOrNull()?.signOut()
        sessionPreferences.setPreviewRoleCode(null)
        sessionPreferences.setLocalStaffUid(null)
        _currentSession.value = null
    }

    private suspend fun buildFirebaseSession(user: FirebaseUser, fallbackBranchId: String): UserSession? {
        val email = user.email.orEmpty().trim()
        val byUid = userProfileDao.getProfileByUid(user.uid)
        val byEmail = if (byUid == null && email.isNotBlank()) {
            userProfileDao.getProfileByEmail(email)
        } else {
            null
        }
        val existingProfile = byUid ?: byEmail

        if (existingProfile != null && !existingProfile.isActive) {
            auditLogDao.insertLog(
                AuditLogEntity(
                    branchId = existingProfile.branchId,
                    actorUid = user.uid,
                    actorEmail = email,
                    actorRole = UserRole.fromCode(existingProfile.roleCode).displayName,
                    action = "SUSPENDED_LOGIN_BLOCKED",
                    details = "Blocked Firebase sign-in for deactivated/suspended operator $email"
                )
            )
            return null
        }

        val assignedRole = if (existingProfile != null) {
            UserRole.fromCode(existingProfile.roleCode)
        } else {
            // Least-privilege default until assigned by administrator in User Permissions
            UserRole.CASHIER
        }
        val targetBranchId = existingProfile?.branchId ?: fallbackBranchId
        val branch = branchDao.getBranchById(targetBranchId)

        val displayName = existingProfile?.displayName?.takeIf { it.isNotBlank() }
            ?: user.displayName?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@").takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
            ?: "Operator"

        if (byEmail != null && byEmail.uid != user.uid &&
            (byEmail.uid.startsWith("pending_") || byEmail.uid.startsWith("local_staff_"))
        ) {
            userProfileDao.deleteProfileByUid(byEmail.uid)
        }

        userProfileDao.upsertProfile(
            UserProfileEntity(
                uid = user.uid,
                email = email,
                displayName = displayName,
                roleCode = assignedRole.code,
                branchId = branch?.branchId ?: DatabaseSeeder.MAIN_BRANCH_ID,
                isActive = existingProfile?.isActive ?: true,
                lastLoginAt = System.currentTimeMillis(),
                username = existingProfile?.username.orEmpty(),
                staffId = existingProfile?.staffId,
                passwordHash = existingProfile?.passwordHash.orEmpty(),
                passwordSalt = existingProfile?.passwordSalt.orEmpty(),
                authProvider = "FIREBASE_EMAIL",
                mustResetPassword = false
            )
        )

        return UserSession(
            uid = user.uid,
            email = email,
            displayName = displayName,
            role = assignedRole,
            branchId = branch?.branchId ?: DatabaseSeeder.MAIN_BRANCH_ID,
            branchName = branch?.name ?: "AA CRAVE - Flagship Main",
            isFirebaseAuthenticated = true,
            isPreviewSandbox = false
        )
    }

    private fun mapFirebaseAuthError(exception: FirebaseAuthException): String {
        return when (exception.errorCode) {
            "ERROR_INVALID_EMAIL" -> "The email address format is invalid."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "Invalid email or password. Please verify your credentials."
            "ERROR_USER_NOT_FOUND" -> "No AA CRAVE operator account found for this email."
            "ERROR_USER_DISABLED" -> "This operator account has been disabled by an administrator."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many failed attempts. Please wait a moment and try again."
            "ERROR_NETWORK_REQUEST_FAILED" -> "Network unavailable. Check internet connectivity for Firebase sign-in."
            else -> exception.localizedMessage ?: "Firebase authentication failed (${exception.errorCode})."
        }
    }
}
