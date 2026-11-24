package com.example.domain.model

data class UserSession(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val branchId: String,
    val branchName: String,
    val isFirebaseAuthenticated: Boolean,
    val isPreviewSandbox: Boolean,
    val sessionStartedAt: Long = System.currentTimeMillis()
) {
    val capabilities: RoleCapabilities
        get() = RoleAccessControl.capabilitiesFor(role)
}

data class FirebaseEnvironmentStatus(
    val isGoogleServicesConfigured: Boolean,
    val isFirebaseAuthAvailable: Boolean,
    val projectName: String = FirebaseCloudConstants.PROJECT_ID,
    val realtimeDbUrl: String = FirebaseCloudConstants.RTDB_URL,
    val expectedPackageName: String = FirebaseCloudConstants.EXPECTED_PACKAGE,
    val realtimeDbStatus: String = "Locked Mode (Rules Restricted)",
    val statusDetail: String
)
