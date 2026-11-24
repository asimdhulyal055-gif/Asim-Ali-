package com.example.domain.model

enum class SyncDomain(
    val collectionKey: String,
    val displayName: String,
    val isFinancialProtected: Boolean
) {
    BRANCHES("meta", "Branches", false),
    CUSTOMERS("customers", "Customers & Loyalty", false),
    ORDERS("orders", "Orders & KOT", true),
    INVOICES("invoices", "Customer Invoices", true),
    MENU("menu", "Menu & Recipes", false),
    INVENTORY("inventory", "Inventory & Stock", false),
    SUPPLIERS("suppliers", "Suppliers & Payments", true),
    PURCHASES("purchases", "Purchases & Receiving", true),
    EXPENSES("expenses", "Expenses", true),
    STAFF("staff", "Staff & Shifts", false),
    ATTENDANCE("attendance", "Attendance & Leave", false),
    PAYROLL("payroll", "Payroll", true),
    ACCOUNTING("accounting", "Accounting & Ledgers", true),
    LOYALTY("loyalty", "Smart Loyalty & Rewards", true);

    companion object {
        fun fromEntityType(entityType: String): SyncDomain = when {
            entityType.contains("LOYALTY_CARD", ignoreCase = true) ||
                entityType.contains("LOYALTY_STAMP", ignoreCase = true) ||
                entityType.contains("LOYALTY_VOUCHER", ignoreCase = true) ||
                entityType.contains("LOYALTY_REDEMPTION", ignoreCase = true) ||
                entityType.contains("LOYALTY_SETTINGS", ignoreCase = true) -> LOYALTY
            entityType.contains("INVOICE", ignoreCase = true) -> INVOICES
            entityType.contains("ORDER", ignoreCase = true) ||
                entityType.contains("KOT", ignoreCase = true) ||
                entityType.contains("CLOSING", ignoreCase = true) -> ORDERS
            entityType.contains("CUSTOMER", ignoreCase = true) ||
                entityType.contains("LOYALTY", ignoreCase = true) -> CUSTOMERS
            entityType.contains("MENU", ignoreCase = true) ||
                entityType.contains("PRODUCT", ignoreCase = true) ||
                entityType.contains("RECIPE", ignoreCase = true) ||
                entityType.contains("CATEGORY", ignoreCase = true) -> MENU
            entityType.contains("INVENTORY", ignoreCase = true) ||
                entityType.contains("STOCK", ignoreCase = true) ||
                entityType.contains("AUDIT", ignoreCase = true) -> INVENTORY
            entityType.contains("SUPPLIER", ignoreCase = true) -> SUPPLIERS
            entityType.contains("PURCHASE", ignoreCase = true) -> PURCHASES
            entityType.contains("EXPENSE", ignoreCase = true) -> EXPENSES
            entityType.contains("PAYROLL", ignoreCase = true) -> PAYROLL
            entityType.contains("ATTENDANCE", ignoreCase = true) ||
                entityType.contains("LEAVE", ignoreCase = true) -> ATTENDANCE
            entityType.contains("STAFF", ignoreCase = true) ||
                entityType.contains("SHIFT", ignoreCase = true) -> STAFF
            entityType.contains("ACCOUNT", ignoreCase = true) ||
                entityType.contains("JOURNAL", ignoreCase = true) ||
                entityType.contains("CASHBOOK", ignoreCase = true) -> ACCOUNTING
            else -> BRANCHES
        }
    }
}

enum class CloudSyncPhase(val label: String) {
    CONFIG_MISSING("CONFIG NEEDED"),
    AUTH_REQUIRED("AUTH REQUIRED"),
    OFFLINE("OFFLINE"),
    DEVICE_REVOKED("DEVICE REVOKED"),
    LOCKED_RULES("RULES LOCKED"),
    SYNCING("SYNCING"),
    ONLINE_READY("ONLINE"),
    ERROR("SYNC ERROR")
}

data class CloudSyncStatus(
    val isNetworkOnline: Boolean = false,
    val isFirebaseConfigured: Boolean = false,
    val isFirebaseAuthenticated: Boolean = false,
    val isDeviceAuthorized: Boolean = true,
    val isSyncing: Boolean = false,
    val phase: CloudSyncPhase = CloudSyncPhase.CONFIG_MISSING,
    val projectId: String = FirebaseCloudConstants.PROJECT_ID,
    val databaseUrl: String = FirebaseCloudConstants.RTDB_URL,
    val deviceId: String = "",
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val openConflictCount: Int = 0,
    val syncedRecordCount: Int = 0,
    val lastSyncAt: Long? = null,
    val lastResultMessage: String = "Local Room offline-first mode active."
)

enum class ConflictResolutionStrategy(val code: String, val label: String) {
    KEEP_LOCAL("RESOLVED_LOCAL", "Keep Local & Push"),
    ACCEPT_REMOTE("RESOLVED_REMOTE", "Accept Cloud Version"),
    KEEP_BOTH_ADJUSTED("RESOLVED_MERGED", "Audit & Compensate (Non-Destructive)")
}

enum class NotificationCategory(val code: String, val label: String) {
    ALL("ALL", "All Alerts"),
    SYNC("SYNC", "Cloud Sync"),
    CONFLICT("CONFLICT", "Conflicts"),
    SECURITY("SECURITY", "Security"),
    BACKUP("BACKUP", "Backup & Restore"),
    OPERATIONS("OPERATIONS", "Operations")
}

data class BackupIntegrityResult(
    val isValid: Boolean,
    val checksumMatch: Boolean,
    val schemaVersion: Int,
    val expectedChecksum: String,
    val computedChecksum: String,
    val totalRecords: Int,
    val tableCounts: Map<String, Int>,
    val createdAt: Long,
    val createdByName: String,
    val branchScope: String,
    val errorMessage: String? = null
)

data class CloudProbeResult(
    val isGoogleServicesPresent: Boolean,
    val projectMatches: Boolean,
    val packageMatches: Boolean,
    val isAuthenticated: Boolean,
    val rtdbReachable: Boolean,
    val rtdbWritePermitted: Boolean,
    val summary: String,
    val details: List<String>
)

object FirebaseCloudConstants {
    const val PROJECT_ID = "aa-crave-erp"
    const val RTDB_URL = "https://aa-crave-erp-default-rtdb.asia-southeast1.firebasedatabase.app"
    const val EXPECTED_PACKAGE = "com.aistudio.aacrave.erpzqx"
    const val BACKUP_SCHEMA_VERSION = 8
    const val MAX_AUTO_RETRIES = 5
}
