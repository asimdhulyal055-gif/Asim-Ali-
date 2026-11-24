package com.example.data.cloud

import android.content.Context
import com.example.domain.model.CloudProbeResult
import com.example.domain.model.FirebaseCloudConstants
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

data class RemoteCloudRecord(
    val entityId: String,
    val branchId: String,
    val version: Long,
    val checksumSha256: String,
    val updatedAt: Long,
    val deviceId: String,
    val actorName: String,
    val data: Map<String, Any?>
)

sealed interface CloudWriteOutcome {
    data object Success : CloudWriteOutcome
    data class ConfigMissing(val reason: String) : CloudWriteOutcome
    data class AuthRequired(val reason: String) : CloudWriteOutcome
    data class LockedRules(val reason: String) : CloudWriteOutcome
    data class RetryableError(val reason: String) : CloudWriteOutcome
}

sealed interface CloudReadOutcome {
    data object NotFound : CloudReadOutcome
    data class Found(val record: RemoteCloudRecord) : CloudReadOutcome
    data class RawFound(val data: Map<String, Any?>) : CloudReadOutcome
    data class Error(val reason: String, val isLockedRules: Boolean = false) : CloudReadOutcome
}

interface CloudSyncGateway {
    fun isFirebaseConfigured(): Boolean
    fun isProjectAndPackageAligned(): Boolean
    fun configuredProjectId(): String?
    fun isFirebaseAuthenticated(): Boolean
    fun currentAuthenticatedUid(): String?
    suspend fun probeConnection(): CloudProbeResult
    suspend fun fetchRemoteRecord(branchId: String, domainCollection: String, entityId: String): CloudReadOutcome
    suspend fun upsertRecordAtomic(
        branchId: String,
        domainCollection: String,
        entityId: String,
        payloadMap: Map<String, Any?>,
        ledgerTxId: String?,
        ledgerEntry: Map<String, Any?>?
    ): CloudWriteOutcome

    suspend fun upsertPath(path: String, data: Map<String, Any?>): CloudWriteOutcome
    suspend fun fetchPath(path: String): CloudReadOutcome
}

class FirebaseRtdbSyncGateway(private val context: Context) : CloudSyncGateway {

    private val appContext = context.applicationContext

    private fun getFirebaseAppOrNull(): FirebaseApp? {
        return try {
            FirebaseApp.getApps(appContext).firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun getDatabaseOrNull(): FirebaseDatabase? {
        val app = getFirebaseAppOrNull() ?: return null
        return try {
            val db = FirebaseDatabase.getInstance(app, FirebaseCloudConstants.RTDB_URL)
            ensurePersistenceEnabled(db)
            db
        } catch (_: Exception) {
            null
        }
    }

    override fun isFirebaseConfigured(): Boolean = getFirebaseAppOrNull() != null

    override fun configuredProjectId(): String? = getFirebaseAppOrNull()?.options?.projectId

    override fun isProjectAndPackageAligned(): Boolean {
        val app = getFirebaseAppOrNull() ?: return false
        val projectOk = app.options.projectId == FirebaseCloudConstants.PROJECT_ID
        val pkgOk = appContext.packageName == FirebaseCloudConstants.EXPECTED_PACKAGE
        return projectOk && pkgOk
    }

    override fun isFirebaseAuthenticated(): Boolean = currentAuthenticatedUid() != null

    override fun currentAuthenticatedUid(): String? {
        if (!isFirebaseConfigured()) return null
        return try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun probeConnection(): CloudProbeResult {
        val details = mutableListOf<String>()
        val app = getFirebaseAppOrNull()
        if (app == null) {
            details += "Missing app/google-services.json — FirebaseApp is not initialized."
            details += "Target project ID: ${FirebaseCloudConstants.PROJECT_ID}"
            details += "Target package: ${FirebaseCloudConstants.EXPECTED_PACKAGE}"
            details += "Target RTDB URL: ${FirebaseCloudConstants.RTDB_URL}"
            return CloudProbeResult(
                isGoogleServicesPresent = false,
                projectMatches = false,
                packageMatches = appContext.packageName == FirebaseCloudConstants.EXPECTED_PACKAGE,
                isAuthenticated = false,
                rtdbReachable = false,
                rtdbWritePermitted = false,
                summary = "google-services.json missing in app/ directory.",
                details = details
            )
        }

        val projectMatches = app.options.projectId == FirebaseCloudConstants.PROJECT_ID
        val pkgMatches = appContext.packageName == FirebaseCloudConstants.EXPECTED_PACKAGE
        details += "Project ID in config: ${app.options.projectId ?: "unknown"} (expected ${FirebaseCloudConstants.PROJECT_ID})"
        details += "Application package: ${appContext.packageName}"

        val uid = currentAuthenticatedUid()
        if (uid.isNullOrBlank()) {
            details += "Firebase Email/Password Auth session is not signed in (local sandbox or unauthenticated)."
            return CloudProbeResult(
                isGoogleServicesPresent = true,
                projectMatches = projectMatches,
                packageMatches = pkgMatches,
                isAuthenticated = false,
                rtdbReachable = false,
                rtdbWritePermitted = false,
                summary = "Sign in with a Firebase Email/Password account to probe live RTDB rules.",
                details = details
            )
        }

        val db = getDatabaseOrNull()
            ?: return CloudProbeResult(
                isGoogleServicesPresent = true,
                projectMatches = projectMatches,
                packageMatches = pkgMatches,
                isAuthenticated = true,
                rtdbReachable = false,
                rtdbWritePermitted = false,
                summary = "Failed to initialize FirebaseDatabase for ${FirebaseCloudConstants.RTDB_URL}.",
                details = details
            )

        return try {
            val snap = db.getReference("erp/users/$uid").get().await()
            val role = snap.child("roleCode").getValue(String::class.java)
            details += "Realtime DB read succeeded at erp/users/$uid (role=${role ?: "unregistered"})."
            CloudProbeResult(
                isGoogleServicesPresent = true,
                projectMatches = projectMatches,
                packageMatches = pkgMatches,
                isAuthenticated = true,
                rtdbReachable = true,
                rtdbWritePermitted = snap.exists(),
                summary = if (snap.exists()) {
                    "Connected to aa-crave-erp Realtime Database with active operator permissions."
                } else {
                    "RTDB reachable, but operator profile erp/users/$uid is not registered in Firebase yet."
                },
                details = details
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val locked = isPermissionDeniedError(e)
            details += if (locked) {
                "Realtime DB rejected access (Permission denied). Ensure database.rules.json is published and erp/users/$uid exists."
            } else {
                "Realtime DB probe error: ${e.message ?: e::class.java.simpleName}"
            }
            CloudProbeResult(
                isGoogleServicesPresent = true,
                projectMatches = projectMatches,
                packageMatches = pkgMatches,
                isAuthenticated = true,
                rtdbReachable = locked,
                rtdbWritePermitted = false,
                summary = if (locked) "RTDB Rules Locked / Permission Denied for $uid." else (e.message ?: "Connection failed."),
                details = details
            )
        }
    }

    override suspend fun fetchRemoteRecord(
        branchId: String,
        domainCollection: String,
        entityId: String
    ): CloudReadOutcome {
        val db = getDatabaseOrNull()
            ?: return CloudReadOutcome.Error("Firebase google-services.json not configured.")
        if (!isFirebaseAuthenticated()) {
            return CloudReadOutcome.Error("Firebase Email/Password authentication required.")
        }

        return try {
            val path = "erp/branches/$branchId/$domainCollection/$entityId"
            val snapshot = db.getReference(path).get().await()
            if (!snapshot.exists()) {
                return CloudReadOutcome.NotFound
            }
            val rawMap = snapshot.value as? Map<*, *> ?: return CloudReadOutcome.NotFound
            val cleanMap = rawMap.entries
                .filter { it.key != null }
                .associate { it.key.toString() to it.value }
            val record = RemoteCloudRecord(
                entityId = cleanMap["entityId"]?.toString() ?: entityId,
                branchId = cleanMap["branchId"]?.toString() ?: branchId,
                version = (cleanMap["version"] as? Number)?.toLong() ?: 1L,
                checksumSha256 = cleanMap["checksumSha256"]?.toString().orEmpty(),
                updatedAt = (cleanMap["updatedAt"] as? Number)?.toLong() ?: 0L,
                deviceId = cleanMap["deviceId"]?.toString().orEmpty(),
                actorName = cleanMap["actorName"]?.toString() ?: "Remote Device",
                data = cleanMap
            )
            CloudReadOutcome.Found(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CloudReadOutcome.Error(
                reason = e.message ?: "Failed reading remote RTDB record.",
                isLockedRules = isPermissionDeniedError(e)
            )
        }
    }

    override suspend fun upsertRecordAtomic(
        branchId: String,
        domainCollection: String,
        entityId: String,
        payloadMap: Map<String, Any?>,
        ledgerTxId: String?,
        ledgerEntry: Map<String, Any?>?
    ): CloudWriteOutcome {
        if (!isFirebaseConfigured()) {
            return CloudWriteOutcome.ConfigMissing(
                "Missing app/google-services.json for project ${FirebaseCloudConstants.PROJECT_ID}."
            )
        }
        if (!isFirebaseAuthenticated()) {
            return CloudWriteOutcome.AuthRequired(
                "Firebase Email/Password sign-in required for cloud upload."
            )
        }
        val db = getDatabaseOrNull()
            ?: return CloudWriteOutcome.ConfigMissing("FirebaseDatabase unavailable.")

        return try {
            val updates = hashMapOf<String, Any?>(
                "erp/branches/$branchId/$domainCollection/$entityId" to payloadMap
            )
            if (!ledgerTxId.isNullOrBlank() && ledgerEntry != null) {
                updates["erp/branches/$branchId/sync_ledger/$ledgerTxId"] = ledgerEntry
            }
            db.reference.updateChildren(updates).await()
            CloudWriteOutcome.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classifyWriteException(e)
        }
    }

    override suspend fun upsertPath(path: String, data: Map<String, Any?>): CloudWriteOutcome {
        if (!isFirebaseConfigured()) {
            return CloudWriteOutcome.ConfigMissing(
                "Missing app/google-services.json for project ${FirebaseCloudConstants.PROJECT_ID}."
            )
        }
        if (!isFirebaseAuthenticated()) {
            return CloudWriteOutcome.AuthRequired("Firebase Email/Password authentication required.")
        }
        val db = getDatabaseOrNull()
            ?: return CloudWriteOutcome.ConfigMissing("FirebaseDatabase unavailable.")

        return try {
            db.getReference(path).setValue(data).await()
            CloudWriteOutcome.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classifyWriteException(e)
        }
    }

    override suspend fun fetchPath(path: String): CloudReadOutcome {
        if (!isFirebaseConfigured()) {
            return CloudReadOutcome.Error("Firebase google-services.json not configured.")
        }
        if (!isFirebaseAuthenticated()) {
            return CloudReadOutcome.Error("Firebase Email/Password authentication required.")
        }
        val db = getDatabaseOrNull()
            ?: return CloudReadOutcome.Error("FirebaseDatabase unavailable.")

        return try {
            val snap = db.getReference(path).get().await()
            if (!snap.exists()) return CloudReadOutcome.NotFound
            val raw = snap.value as? Map<*, *> ?: return CloudReadOutcome.NotFound
            CloudReadOutcome.RawFound(
                raw.entries.filter { it.key != null }.associate { it.key.toString() to it.value }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CloudReadOutcome.Error(
                reason = e.message ?: "Failed fetching path $path.",
                isLockedRules = isPermissionDeniedError(e)
            )
        }
    }

    private fun classifyWriteException(e: Exception): CloudWriteOutcome {
        return if (isPermissionDeniedError(e)) {
            CloudWriteOutcome.LockedRules(
                "Firebase Realtime Database rejected write (Permission denied). Publish database.rules.json and authorize operator UID in erp/users."
            )
        } else {
            CloudWriteOutcome.RetryableError(e.message ?: "Realtime Database write failed.")
        }
    }

    private fun isPermissionDeniedError(e: Throwable): Boolean {
        val msg = generateSequence(e) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" | ")
            .lowercase()
        return e is DatabaseException && msg.contains("permission") || msg.contains("permission denied")
    }

    companion object {
        @Volatile
        private var persistenceInitialized = false

        private fun ensurePersistenceEnabled(db: FirebaseDatabase) {
            if (!persistenceInitialized) {
                synchronized(this) {
                    if (!persistenceInitialized) {
                        try {
                            db.setPersistenceEnabled(true)
                        } catch (_: Exception) {
                            // Persistence already configured or database instance already used
                        } finally {
                            persistenceInitialized = true
                        }
                    }
                }
            }
        }
    }
}
