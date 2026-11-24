package com.example

import android.app.Application
import com.example.data.brand.BrandAssetManager
import com.example.data.cloud.AndroidNetworkConnectivityMonitor
import com.example.data.cloud.CloudSyncGateway
import com.example.data.cloud.FirebaseRtdbSyncGateway
import com.example.data.cloud.NetworkConnectivityMonitor
import com.example.data.local.AaCraveDatabase
import com.example.data.local.DatabaseSeeder
import com.example.data.local.SessionPreferences
import com.example.data.repository.AccountingRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.BackupRestoreRepository
import com.example.data.repository.CloudSyncRepository
import com.example.data.repository.CustomerLoyaltyRepository
import com.example.data.repository.DashboardRepository
import com.example.data.repository.ExpenseRepository
import com.example.data.repository.FirebaseAuthRepository
import com.example.data.repository.FirebaseStaffAuthProvisioner
import com.example.data.repository.InventoryRepository
import com.example.data.repository.KdsRepository
import com.example.data.repository.LeavePayrollRepository
import com.example.data.repository.MenuRepository
import com.example.data.repository.MultiBranchRepository
import com.example.data.repository.PosRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.RecipeRepository
import com.example.data.repository.SecurityAdminRepository
import com.example.data.repository.ShiftAttendanceRepository
import com.example.data.repository.StaffHrRepository
import com.example.data.repository.SupplierRepository
import com.example.printing.ThermalPrinterService
import com.example.printing.UnconfiguredThermalPrinterService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AaCraveApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AaCraveDatabase by lazy { AaCraveDatabase.getInstance(this) }
    val sessionPreferences: SessionPreferences by lazy { SessionPreferences(this) }
    val databaseSeeder: DatabaseSeeder by lazy { DatabaseSeeder(database) }
    val brandAssetManager: BrandAssetManager by lazy { BrandAssetManager(this) }
    val thermalPrinterService: ThermalPrinterService by lazy { UnconfiguredThermalPrinterService() }

    val connectivityMonitor: NetworkConnectivityMonitor by lazy {
        AndroidNetworkConnectivityMonitor(this)
    }

    val cloudSyncGateway: CloudSyncGateway by lazy {
        FirebaseRtdbSyncGateway(this)
    }

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(
            context = this,
            userProfileDao = database.userProfileDao(),
            branchDao = database.branchDao(),
            auditLogDao = database.auditLogDao(),
            sessionPreferences = sessionPreferences
        )
    }

    val cloudSyncRepository: CloudSyncRepository by lazy {
        CloudSyncRepository(
            context = this,
            database = database,
            sessionPreferences = sessionPreferences,
            connectivityMonitor = connectivityMonitor,
            gateway = cloudSyncGateway,
            authRepository = authRepository
        )
    }

    val backupRestoreRepository: BackupRestoreRepository by lazy {
        BackupRestoreRepository(
            context = this,
            database = database,
            sessionPreferences = sessionPreferences,
            connectivityMonitor = connectivityMonitor,
            gateway = cloudSyncGateway
        )
    }

    val staffAuthProvisioner: FirebaseStaffAuthProvisioner by lazy {
        FirebaseStaffAuthProvisioner(this)
    }

    val securityAdminRepository: SecurityAdminRepository by lazy {
        SecurityAdminRepository(
            database = database,
            gateway = cloudSyncGateway,
            connectivityMonitor = connectivityMonitor,
            authRepository = authRepository,
            staffAuthProvisioner = staffAuthProvisioner
        )
    }

    val dashboardRepository: DashboardRepository by lazy {
        DashboardRepository(
            branchDao = database.branchDao(),
            dashboardDao = database.dashboardDao(),
            auditLogDao = database.auditLogDao(),
            syncQueueDao = database.syncQueueDao(),
            databaseSeeder = databaseSeeder,
            supplierDao = database.supplierDao(),
            purchaseDao = database.purchaseDao(),
            customerLoyaltyDao = database.customerLoyaltyDao(),
            expenseDao = database.expenseDao()
        )
    }

    val posRepository: PosRepository by lazy {
        PosRepository(database)
    }

    val kdsRepository: KdsRepository by lazy {
        KdsRepository(database)
    }

    val menuRepository: MenuRepository by lazy {
        MenuRepository(this, database)
    }

    val recipeRepository: RecipeRepository by lazy {
        RecipeRepository(database)
    }

    val inventoryRepository: InventoryRepository by lazy {
        InventoryRepository(database)
    }

    val supplierRepository: SupplierRepository by lazy {
        SupplierRepository(database)
    }

    val purchaseRepository: PurchaseRepository by lazy {
        PurchaseRepository(database)
    }

    val customerLoyaltyRepository: CustomerLoyaltyRepository by lazy {
        CustomerLoyaltyRepository(database)
    }

    val smartLoyaltyRepository: com.example.data.repository.SmartLoyaltyRepository by lazy {
        com.example.data.repository.SmartLoyaltyRepository(database)
    }

    val expenseRepository: ExpenseRepository by lazy {
        ExpenseRepository(this, database)
    }

    val staffHrRepository: StaffHrRepository by lazy {
        StaffHrRepository(this, database)
    }

    val shiftAttendanceRepository: ShiftAttendanceRepository by lazy {
        ShiftAttendanceRepository(database)
    }

    val leavePayrollRepository: LeavePayrollRepository by lazy {
        LeavePayrollRepository(database)
    }

    val accountingRepository: AccountingRepository by lazy {
        AccountingRepository(database)
    }

    val multiBranchRepository: MultiBranchRepository by lazy {
        MultiBranchRepository(database)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            databaseSeeder.ensureSeeded()
        }
        applicationScope.launch {
            connectivityMonitor.isOnline
                .collect { online ->
                    val session = authRepository.currentSession.value
                    if (online && session != null && session.isFirebaseAuthenticated && cloudSyncGateway.isFirebaseConfigured()) {
                        cloudSyncRepository.syncPendingQueue(session, includeRetryableFailed = false)
                    }
                }
        }
    }
}
