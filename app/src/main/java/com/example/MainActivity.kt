package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.brand.BrandLogoState
import com.example.domain.model.AppModule
import com.example.domain.model.FirebaseEnvironmentStatus
import com.example.domain.model.RoleAccessControl
import com.example.domain.model.UserSession
import com.example.printing.Phase4ReportExporter
import com.example.ui.accounting.AccountingScreen
import com.example.ui.accounting.FinancialReportsScreen
import com.example.ui.accounting.MultiBranchScreen
import com.example.ui.components.AaCraveDrawerContent
import com.example.ui.customers.CustomerCrmScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.documents.CustomerInvoiceScreen
import com.example.ui.documents.KitchenKotScreen
import com.example.ui.expenses.ExpenseManagementScreen
import com.example.ui.hr.AttendanceManagementScreen
import com.example.ui.hr.LeaveManagementScreen
import com.example.ui.hr.PayrollManagementScreen
import com.example.ui.hr.ShiftManagementScreen
import com.example.ui.hr.StaffManagementScreen
import com.example.ui.inventory.InventoryManagementScreen
import com.example.ui.kds.KdsScreen
import com.example.ui.login.LoginScreen
import com.example.ui.menu.MenuManagementScreen
import com.example.ui.phase7.BackupSyncScreen
import com.example.ui.phase7.NotificationsScreen
import com.example.ui.phase7.UserPermissionsScreen
import com.example.ui.pos.BillingConsoleScreen
import com.example.ui.pos.CashClosingScreen
import com.example.ui.pos.OrdersHistoryScreen
import com.example.ui.pos.PosScreen
import com.example.ui.purchases.PurchaseManagementScreen
import com.example.ui.recipes.RecipeManagementScreen
import com.example.ui.secondary.AuditLogsScreen
import com.example.ui.secondary.ProfileScreen
import com.example.ui.secondary.SettingsScreen
import com.example.ui.suppliers.SupplierManagementScreen
import com.example.ui.theme.AaCraveTheme
import com.example.ui.theme.CraveGold
import com.example.ui.theme.CraveGoldBright
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnCraveGold
import com.example.ui.theme.StatusCrimson
import com.example.ui.loyalty.LoyaltyHubScreen
import com.example.ui.viewmodel.AccountingViewModel
import com.example.ui.viewmodel.SmartLoyaltyViewModel
import com.example.ui.viewmodel.AttendanceViewModel
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.BackupSyncViewModel
import com.example.ui.viewmodel.CustomerLoyaltyViewModel
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.ui.viewmodel.FinancialReportsViewModel
import com.example.ui.viewmodel.InventoryViewModel
import com.example.ui.viewmodel.KdsViewModel
import com.example.ui.viewmodel.LeaveViewModel
import com.example.ui.viewmodel.MenuViewModel
import com.example.ui.viewmodel.MultiBranchViewModel
import com.example.ui.viewmodel.NotificationsViewModel
import com.example.ui.viewmodel.PayrollViewModel
import com.example.ui.viewmodel.PosViewModel
import com.example.ui.viewmodel.PurchaseViewModel
import com.example.ui.viewmodel.RecipeViewModel
import com.example.ui.viewmodel.SessionUiState
import com.example.ui.viewmodel.ShiftViewModel
import com.example.ui.viewmodel.StaffViewModel
import com.example.ui.viewmodel.SupplierViewModel
import com.example.ui.viewmodel.UserPermissionsViewModel
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as AaCraveApplication

        setContent {
            AaCraveTheme {
                val authViewModel: AuthViewModel = viewModel(
                    factory = AuthViewModel.Factory(app.authRepository)
                )
                val authState by authViewModel.uiState.collectAsStateWithLifecycle()
                val logoState by app.brandAssetManager.logoState.collectAsStateWithLifecycle(
                    initialValue = BrandLogoState(hasOwnerLogo = false)
                )

                when (val sessionState = authState.sessionState) {
                    is SessionUiState.Initializing -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ObsidianBg),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = CraveGold)
                        }
                    }

                    is SessionUiState.Unauthenticated -> {
                        LoginScreen(
                            formState = authState.form,
                            environmentStatus = authState.environmentStatus,
                            logoState = logoState,
                            onEmailChanged = authViewModel::onEmailChanged,
                            onPasswordChanged = authViewModel::onPasswordChanged,
                            onSignInClick = authViewModel::signIn,
                            onOpenResetDialog = authViewModel::openResetDialog,
                            onDismissResetDialog = authViewModel::closeResetDialog,
                            onResetEmailChanged = authViewModel::onResetEmailChanged,
                            onSubmitPasswordReset = authViewModel::submitPasswordReset,
                            onEnterPreviewSandbox = authViewModel::enterLocalPreviewSandbox
                        )
                    }

                    is SessionUiState.Authenticated -> {
                        key(sessionState.session.uid) {
                            AuthenticatedErpHost(
                                session = sessionState.session,
                                environmentStatus = authState.environmentStatus,
                                logoState = logoState,
                                app = app,
                                onSignOut = authViewModel::signOut
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedErpHost(
    session: UserSession,
    environmentStatus: FirebaseEnvironmentStatus,
    logoState: BrandLogoState,
    app: AaCraveApplication,
    onSignOut: () -> Unit
) {
    val dashboardViewModel: DashboardViewModel = viewModel(
        key = "dash_${session.uid}",
        factory = DashboardViewModel.Factory(app.authRepository, app.dashboardRepository)
    )
    val posViewModel: PosViewModel = viewModel(
        key = "pos_${session.uid}",
        factory = PosViewModel.Factory(
            app.authRepository,
            app.posRepository,
            app.brandAssetManager,
            app.thermalPrinterService,
            app.smartLoyaltyRepository
        )
    )
    val kdsViewModel: KdsViewModel = viewModel(
        key = "kds_${session.uid}",
        factory = KdsViewModel.Factory(app.authRepository, app.kdsRepository)
    )
    val menuViewModel: MenuViewModel = viewModel(
        key = "menu_${session.uid}",
        factory = MenuViewModel.Factory(app.authRepository, app.menuRepository)
    )
    val recipeViewModel: RecipeViewModel = viewModel(
        key = "recipe_${session.uid}",
        factory = RecipeViewModel.Factory(
            app.authRepository,
            app.menuRepository,
            app.recipeRepository,
            app.inventoryRepository
        )
    )
    val inventoryViewModel: InventoryViewModel = viewModel(
        key = "inv_${session.uid}",
        factory = InventoryViewModel.Factory(
            app.authRepository,
            app.inventoryRepository,
            app.menuRepository
        )
    )
    val purchaseViewModel: PurchaseViewModel = viewModel(
        key = "pur_${session.uid}",
        factory = PurchaseViewModel.Factory(
            app.authRepository,
            app.purchaseRepository,
            app.supplierRepository,
            app.inventoryRepository
        )
    )
    val supplierViewModel: SupplierViewModel = viewModel(
        key = "sup_${session.uid}",
        factory = SupplierViewModel.Factory(
            app.authRepository,
            app.supplierRepository,
            app.purchaseRepository
        )
    )
    val customerLoyaltyViewModel: CustomerLoyaltyViewModel = viewModel(
        key = "cust_${session.uid}",
        factory = CustomerLoyaltyViewModel.Factory(
            app.authRepository,
            app.customerLoyaltyRepository,
            app.posRepository
        )
    )
    val smartLoyaltyViewModel: SmartLoyaltyViewModel = viewModel(
        key = "sloy_${session.uid}",
        factory = SmartLoyaltyViewModel.Factory(
            authRepository = app.authRepository,
            smartLoyaltyRepository = app.smartLoyaltyRepository,
            customerLoyaltyRepository = app.customerLoyaltyRepository,
            menuRepository = app.menuRepository,
            multiBranchRepository = app.multiBranchRepository,
            brandAssetManager = app.brandAssetManager,
            session = session
        )
    )
    val expenseViewModel: ExpenseViewModel = viewModel(
        key = "exp_${session.uid}",
        factory = ExpenseViewModel.Factory(
            app.authRepository,
            app.expenseRepository
        )
    )
    val staffViewModel: StaffViewModel = viewModel(
        key = "stf_${session.uid}",
        factory = StaffViewModel.Factory(
            app.authRepository,
            app.dashboardRepository,
            app.staffHrRepository,
            session.role
        )
    )
    val attendanceViewModel: AttendanceViewModel = viewModel(
        key = "att_${session.uid}",
        factory = AttendanceViewModel.Factory(
            app.authRepository,
            app.staffHrRepository,
            app.shiftAttendanceRepository,
            session.role
        )
    )
    val shiftViewModel: ShiftViewModel = viewModel(
        key = "shf_${session.uid}",
        factory = ShiftViewModel.Factory(
            app.authRepository,
            app.staffHrRepository,
            app.shiftAttendanceRepository,
            session.role
        )
    )
    val leaveViewModel: LeaveViewModel = viewModel(
        key = "lvr_${session.uid}",
        factory = LeaveViewModel.Factory(
            app.authRepository,
            app.staffHrRepository,
            app.leavePayrollRepository,
            session.role
        )
    )
    val payrollViewModel: PayrollViewModel = viewModel(
        key = "pay_${session.uid}",
        factory = PayrollViewModel.Factory(
            app.authRepository,
            app.staffHrRepository,
            app.leavePayrollRepository,
            session.role
        )
    )
    val accountingViewModel: AccountingViewModel = viewModel(
        key = "acc_${session.uid}",
        factory = AccountingViewModel.Factory(
            app.authRepository,
            app.accountingRepository,
            session
        )
    )
    val financialReportsViewModel: FinancialReportsViewModel = viewModel(
        key = "rep_${session.uid}",
        factory = FinancialReportsViewModel.Factory(
            app.authRepository,
            app.accountingRepository,
            session
        )
    )
    val multiBranchViewModel: MultiBranchViewModel = viewModel(
        key = "mbr_${session.uid}",
        factory = MultiBranchViewModel.Factory(
            app.authRepository,
            app.multiBranchRepository,
            app.accountingRepository
        )
    )
    val backupSyncViewModel: BackupSyncViewModel = viewModel(
        key = "bkp_${session.uid}",
        factory = BackupSyncViewModel.Factory(
            session = session,
            cloudSyncRepository = app.cloudSyncRepository,
            backupRestoreRepository = app.backupRestoreRepository
        )
    )
    val userPermissionsViewModel: UserPermissionsViewModel = viewModel(
        key = "perm_${session.uid}",
        factory = UserPermissionsViewModel.Factory(
            session = session,
            database = app.database,
            securityAdminRepository = app.securityAdminRepository
        )
    )
    val notificationsViewModel: NotificationsViewModel = viewModel(
        key = "notif_${session.uid}",
        factory = NotificationsViewModel.Factory(app.securityAdminRepository)
    )

    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val posState by posViewModel.uiState.collectAsStateWithLifecycle()
    val kdsState by kdsViewModel.uiState.collectAsStateWithLifecycle()
    val menuState by menuViewModel.uiState.collectAsStateWithLifecycle()
    val recipeState by recipeViewModel.uiState.collectAsStateWithLifecycle()
    val inventoryState by inventoryViewModel.uiState.collectAsStateWithLifecycle()
    val purchaseState by purchaseViewModel.uiState.collectAsStateWithLifecycle()
    val supplierState by supplierViewModel.uiState.collectAsStateWithLifecycle()
    val customerState by customerLoyaltyViewModel.uiState.collectAsStateWithLifecycle()
    val smartLoyaltyState by smartLoyaltyViewModel.uiState.collectAsStateWithLifecycle()
    val expenseState by expenseViewModel.uiState.collectAsStateWithLifecycle()
    val staffState by staffViewModel.uiState.collectAsStateWithLifecycle()
    val attendanceState by attendanceViewModel.uiState.collectAsStateWithLifecycle()
    val shiftState by shiftViewModel.uiState.collectAsStateWithLifecycle()
    val leaveState by leaveViewModel.uiState.collectAsStateWithLifecycle()
    val payrollState by payrollViewModel.uiState.collectAsStateWithLifecycle()
    val accountingState by accountingViewModel.uiState.collectAsStateWithLifecycle()
    val financialReportsState by financialReportsViewModel.uiState.collectAsStateWithLifecycle()
    val multiBranchState by multiBranchViewModel.uiState.collectAsStateWithLifecycle()
    val backupSyncState by backupSyncViewModel.uiState.collectAsStateWithLifecycle()
    val userPermissionsState by userPermissionsViewModel.uiState.collectAsStateWithLifecycle()
    val notificationsState by notificationsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppModule.DASHBOARD.route

    fun navigateTo(route: String) {
        scope.launch { drawerState.close() }
        if (route != currentRoute) {
            navController.navigate(route) {
                popUpTo(AppModule.DASHBOARD.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AaCraveDrawerContent(
                session = session,
                logoState = logoState,
                currentRoute = currentRoute,
                onNavigate = ::navigateTo,
                onSignOutClick = {
                    scope.launch { drawerState.close() }
                    onSignOut()
                }
            )
        }
    ) {
        val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
        val returnToDashboard: () -> Unit = {
            navController.popBackStack(AppModule.DASHBOARD.route, inclusive = false)
        }

        NavHost(
            navController = navController,
            startDestination = AppModule.DASHBOARD.route
        ) {
            composable(AppModule.DASHBOARD.route) {
                DashboardScreen(
                    session = session,
                    uiState = dashboardState,
                    environmentStatus = environmentStatus,
                    logoState = logoState,
                    cloudSyncStatus = backupSyncState.syncStatus,
                    onOpenDrawer = openDrawer,
                    onSwitchBranch = dashboardViewModel::switchBranch,
                    onRefreshSampleData = dashboardViewModel::refreshSampleData,
                    onNavigateToModule = ::navigateTo
                )
            }

            composable(AppModule.POS.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.POS)) {
                    PosScreen(
                        session = session,
                        uiState = posState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectCategory = posViewModel::selectCategory,
                        onSearchChanged = posViewModel::onSearchQueryChanged,
                        onAddToCart = posViewModel::addToCart,
                        onIncrementCart = posViewModel::incrementCartItem,
                        onDecrementCart = posViewModel::decrementCartItem,
                        onRemoveCart = posViewModel::removeCartItem,
                        onClearCart = posViewModel::clearCart,
                        onOrderTypeChange = posViewModel::setOrderType,
                        onTableNumberChange = posViewModel::setTableNumber,
                        onPriorityChange = posViewModel::setPriority,
                        onSelectCustomer = posViewModel::selectCustomer,
                        onApplyLoyaltyPoints = posViewModel::applyLoyaltyRedemption,
                        onDiscountPercentChange = posViewModel::setDiscountPercent,
                        onPaymentMethodChange = posViewModel::setPaymentMethod,
                        onSplitPaymentChange = posViewModel::updateSplitPayment,
                        onConfirmOrder = { posViewModel.confirmAndPlaceOrder(session) },
                        onDismissCompletedOrder = posViewModel::dismissCompletedOrderDialog,
                        onOpenInvoiceForOrder = { orderId ->
                            posViewModel.selectOrderForInvoice(orderId)
                            navigateTo(AppModule.CUSTOMER_INVOICES.route)
                        },
                        onOpenKotForOrder = { orderId ->
                            posViewModel.selectOrderForKot(orderId)
                            navigateTo(AppModule.KITCHEN_KOT.route)
                        },
                        onClearFeedback = posViewModel::clearFeedback,
                        onLookupLoyalty = { q -> posViewModel.lookupAndAttachLoyalty(q, session.branchId) },
                        onApplyLoyaltyVoucher = posViewModel::applyLoyaltyVoucher,
                        onClearLoyaltySelection = posViewModel::clearLoyaltySelection,
                        onEnrollCustomerLoyalty = { custId -> posViewModel.enrollCustomerInPos(session, custId) }
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.POS,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.BILLING.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.BILLING)) {
                    BillingConsoleScreen(
                        session = session,
                        uiState = posState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onGoToPos = { navigateTo(AppModule.POS.route) },
                        onOpenInvoice = { orderId ->
                            posViewModel.selectOrderForInvoice(orderId)
                            navigateTo(AppModule.CUSTOMER_INVOICES.route)
                        }
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.BILLING,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.CUSTOMER_INVOICES.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.CUSTOMER_INVOICES)) {
                    val branches = (dashboardState as? DashboardUiState.Success)?.branches.orEmpty()
                    CustomerInvoiceScreen(
                        session = session,
                        uiState = posState,
                        branches = branches,
                        brandAssetManager = app.brandAssetManager,
                        thermalPrinterService = app.thermalPrinterService,
                        buildInvoice = app.posRepository::buildCustomerInvoice,
                        onSelectOrder = posViewModel::selectOrderForInvoice,
                        onRecordReprint = { orderId -> posViewModel.recordInvoiceReprint(session, orderId) },
                        onPrinterFeedback = posViewModel::reportPrinterAttempt,
                        onClearFeedback = posViewModel::clearFeedback,
                        onBack = returnToDashboard
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.CUSTOMER_INVOICES,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.KITCHEN_KOT.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.KITCHEN_KOT)) {
                    KitchenKotScreen(
                        uiState = posState,
                        thermalPrinterService = app.thermalPrinterService,
                        buildKot = app.posRepository::buildKitchenKot,
                        onSelectOrder = posViewModel::selectOrderForKot,
                        onRecordReprint = { orderId -> posViewModel.recordKotReprint(session, orderId) },
                        onPrinterFeedback = posViewModel::reportPrinterAttempt,
                        onClearFeedback = posViewModel::clearFeedback,
                        onBack = returnToDashboard
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.KITCHEN_KOT,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.ORDERS.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.ORDERS)) {
                    OrdersHistoryScreen(
                        session = session,
                        uiState = posState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenInvoice = { orderId ->
                            posViewModel.selectOrderForInvoice(orderId)
                            navigateTo(AppModule.CUSTOMER_INVOICES.route)
                        },
                        onOpenKot = { orderId ->
                            posViewModel.selectOrderForKot(orderId)
                            navigateTo(AppModule.KITCHEN_KOT.route)
                        },
                        onCancelOrder = { orderId, reason -> posViewModel.cancelOrder(session, orderId, reason) },
                        onRefundOrder = { orderId, reason -> posViewModel.refundOrder(session, orderId, reason) },
                        onClearFeedback = posViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.ORDERS,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.KITCHEN_DISPLAY.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.KITCHEN_DISPLAY)) {
                    KdsScreen(
                        session = session,
                        uiState = kdsState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectStation = kdsViewModel::selectStation,
                        onSelectStageFilter = kdsViewModel::selectStageFilter,
                        onToggleSound = kdsViewModel::toggleSound,
                        onUpdateStatus = { kotId, status -> kdsViewModel.updateKotStatus(session, kotId, status) },
                        onOpenKotSlip = { orderId ->
                            posViewModel.selectOrderForKot(orderId)
                            navigateTo(AppModule.KITCHEN_KOT.route)
                        },
                        onClearFeedback = kdsViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.KITCHEN_DISPLAY,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.MENU.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.MENU)) {
                    MenuManagementScreen(
                        session = session,
                        uiState = menuState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectCategory = menuViewModel::selectCategory,
                        onSelectFilterMode = menuViewModel::selectFilterMode,
                        onSearchChanged = menuViewModel::onSearchQueryChanged,
                        onSaveCategory = { catId, name, sort ->
                            menuViewModel.saveCategory(session, catId, name, sort)
                        },
                        onDeleteCategory = { cat -> menuViewModel.deleteCategory(session, cat) },
                        onSaveProduct = { prodId, catId, catName, name, desc, priceCents, sku, taxExempt, avail, isCombo, comboSum, station, img ->
                            menuViewModel.saveProduct(
                                session = session,
                                productId = prodId,
                                categoryId = catId,
                                categoryName = catName,
                                name = name,
                                description = desc,
                                basePriceCents = priceCents,
                                sku = sku,
                                taxExempt = taxExempt,
                                isAvailable = avail,
                                isCombo = isCombo,
                                comboSummary = comboSum,
                                kitchenStation = station,
                                imagePath = img
                            )
                        },
                        onToggleAvailability = { prod, avail ->
                            menuViewModel.toggleAvailability(session, prod, avail)
                        },
                        onSetArchived = { prod, archived ->
                            menuViewModel.setArchived(session, prod, archived)
                        },
                        onSaveVariation = { varId, prodId, name, delta, isDefault ->
                            menuViewModel.saveVariation(session, varId, prodId, name, delta, isDefault)
                        },
                        onDeleteVariation = { varId -> menuViewModel.deleteVariation(session, varId) },
                        onSaveAddon = { addonId, prodId, name, price ->
                            menuViewModel.saveAddon(session, addonId, prodId, name, price)
                        },
                        onDeleteAddon = { addonId -> menuViewModel.deleteAddon(session, addonId) },
                        onImportProductImage = menuViewModel::importProductImage,
                        onClearFeedback = menuViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.MENU,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.RECIPES.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.RECIPES)) {
                    RecipeManagementScreen(
                        session = session,
                        uiState = recipeState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearchChanged = recipeViewModel::onSearchQueryChanged,
                        onToggleOnlyMissing = recipeViewModel::toggleOnlyMissing,
                        onSaveRecipe = { prodId, notes, ingredients ->
                            recipeViewModel.saveRecipe(session, prodId, notes, ingredients)
                        },
                        onClearFeedback = recipeViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.RECIPES,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.INVENTORY.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.INVENTORY)) {
                    InventoryManagementScreen(
                        session = session,
                        uiState = inventoryState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectTab = inventoryViewModel::selectTab,
                        onSelectAlertFilter = inventoryViewModel::selectAlertFilter,
                        onSearchChanged = inventoryViewModel::onSearchQueryChanged,
                        onSaveItem = { itemId, name, sku, type, unit, qty, reorder, cost, expiry, linkedProdId ->
                            inventoryViewModel.saveInventoryItem(
                                session = session,
                                itemId = itemId,
                                name = name,
                                sku = sku,
                                itemType = type,
                                unit = unit,
                                initialOrCurrentQty = qty,
                                reorderLevel = reorder,
                                unitCostCents = cost,
                                expiryDateMillis = expiry,
                                linkedProductId = linkedProdId
                            )
                        },
                        onReceiveStock = { itemId, qty, expiry, note ->
                            inventoryViewModel.receiveStock(session, itemId, qty, expiry, note)
                        },
                        onAdjustStock = { itemId, delta, reason ->
                            inventoryViewModel.adjustStock(session, itemId, delta, reason)
                        },
                        onRecordWastage = { itemId, qty, reason ->
                            inventoryViewModel.recordWastage(session, itemId, qty, reason)
                        },
                        onRecordAudit = { itemId, counted, notes ->
                            inventoryViewModel.recordPhysicalAudit(session, itemId, counted, notes)
                        },
                        onClearFeedback = inventoryViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.INVENTORY,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.CASH_CLOSING.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.CASH_CLOSING)) {
                    CashClosingScreen(
                        session = session,
                        uiState = posState,
                        onBack = { navController.popBackStack(AppModule.DASHBOARD.route, inclusive = false) },
                        onSubmitClosing = { countedCents, notes ->
                            posViewModel.submitDailyCashClosing(session, countedCents, notes)
                        },
                        onClearFeedback = posViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.CASH_CLOSING,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.PURCHASES.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.PURCHASES)) {
                    PurchaseManagementScreen(
                        session = session,
                        uiState = purchaseState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectStatusFilter = purchaseViewModel::selectStatusFilter,
                        onSearchChanged = purchaseViewModel::onSearchQueryChanged,
                        onSavePurchaseOrder = { id, supId, inv, lines, disc, tax, notes, submit ->
                            purchaseViewModel.savePurchaseOrder(session, id, supId, inv, lines, disc, tax, notes, submit)
                        },
                        onApprovePo = { poId -> purchaseViewModel.approvePurchaseOrder(session, poId) },
                        onCancelPo = { poId, reason -> purchaseViewModel.cancelPurchaseOrder(session, poId, reason) },
                        onReceiveGoods = { poId, ref, qtyMap, notes, inv ->
                            purchaseViewModel.receiveGoods(session, poId, ref, qtyMap, notes, inv)
                        },
                        onReturnGoods = { poId, lineId, qty, reason ->
                            purchaseViewModel.returnPurchaseGoods(session, poId, lineId, qty, reason)
                        },
                        onReportMessage = purchaseViewModel::showFeedback,
                        onClearFeedback = purchaseViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.PURCHASES,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.SUPPLIERS.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.SUPPLIERS)) {
                    SupplierManagementScreen(
                        session = session,
                        uiState = supplierState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearchChanged = supplierViewModel::onSearchQueryChanged,
                        onSaveSupplier = { id, name, contact, phone, email, address, cat, opening ->
                            supplierViewModel.saveSupplier(session, id, name, contact, phone, email, address, cat, opening)
                        },
                        onRecordPayment = { supId, poId, amt, method, ref, notes ->
                            supplierViewModel.recordPayment(session, supId, poId, amt, method, ref, notes)
                        },
                        onBuildStatement = supplierViewModel::buildStatement,
                        onReportMessage = supplierViewModel::showFeedback,
                        onClearFeedback = supplierViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.SUPPLIERS,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.CUSTOMERS.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.CUSTOMERS)) {
                    CustomerCrmScreen(
                        session = session,
                        uiState = customerState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearchChanged = customerLoyaltyViewModel::onSearchQueryChanged,
                        onSelectTierFilter = customerLoyaltyViewModel::selectTierFilter,
                        onSaveCustomer = { id, name, phone, email, address, notes, bal ->
                            customerLoyaltyViewModel.saveCustomer(session, id, name, phone, email, address, notes, bal)
                        },
                        onUpdateLoyaltyConfig = { earn, redeem, minPts, silver, gold, plat, expEn, expDays ->
                            customerLoyaltyViewModel.updateLoyaltyConfig(session, earn, redeem, minPts, silver, gold, plat, expEn, expDays)
                        },
                        onSaveReward = { id, title, desc, pts, valCents ->
                            customerLoyaltyViewModel.saveReward(session, id, title, desc, pts, valCents)
                        },
                        onAdjustPoints = { custId, delta, reason ->
                            customerLoyaltyViewModel.adjustPoints(session, custId, delta, reason)
                        },
                        onRedeemReward = { custId, rew ->
                            customerLoyaltyViewModel.redeemReward(session, custId, rew)
                        },
                        onRecordFeedback = { custId, rating, comment ->
                            customerLoyaltyViewModel.recordFeedback(session, custId, rating, comment)
                        },
                        onRunExpirySweep = { customerLoyaltyViewModel.runExpirySweep(session) },
                        onClearFeedback = customerLoyaltyViewModel::clearFeedback,
                        onOpenSmartLoyalty = { custId ->
                            smartLoyaltyViewModel.openCustomerBundle(custId)
                            navigateTo(AppModule.LOYALTY.route)
                        }
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.CUSTOMERS,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.LOYALTY.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.LOYALTY)) {
                    LoyaltyHubScreen(
                        session = session,
                        uiState = smartLoyaltyState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearchChanged = smartLoyaltyViewModel::onSearchQueryChanged,
                        onSelectStatusFilter = smartLoyaltyViewModel::selectStatusFilter,
                        onSelectBranchScope = smartLoyaltyViewModel::selectBranchScope,
                        onSelectPresetDays = smartLoyaltyViewModel::selectReportPresetDays,
                        onRefreshReport = smartLoyaltyViewModel::refreshAnalyticsReport,
                        onEnrollCustomerCard = { custId, customNo ->
                            smartLoyaltyViewModel.enrollCustomerCard(session, custId, customNo)
                        },
                        onOpenCustomerBundle = smartLoyaltyViewModel::openCustomerBundle,
                        onClearCustomerBundle = smartLoyaltyViewModel::clearSelectedBundle,
                        onLookupQrOrCode = { q -> smartLoyaltyViewModel.lookupQrOrCode(q) },
                        onSuspendCard = { cardId, custId, reason ->
                            smartLoyaltyViewModel.suspendCard(session, cardId, custId, reason)
                        },
                        onReactivateCard = { cardId, custId, reason ->
                            smartLoyaltyViewModel.reactivateCard(session, cardId, custId, reason)
                        },
                        onReplaceCard = { cardId, custId, reason, customNo ->
                            smartLoyaltyViewModel.replaceCard(session, cardId, custId, reason, customNo)
                        },
                        onRenewCard = { cardId, custId, reason ->
                            smartLoyaltyViewModel.renewExpiredCard(session, cardId, custId, reason)
                        },
                        onAdjustStamps = { cardId, custId, delta, reason ->
                            smartLoyaltyViewModel.adjustStamps(session, cardId, custId, delta, reason)
                        },
                        onSaveSettings = { cfg -> smartLoyaltyViewModel.saveProgramSettings(session, cfg) },
                        onApplyVoucherInPos = { voucher ->
                            posViewModel.lookupAndAttachLoyalty(voucher.voucherCode, session.branchId)
                            navigateTo(AppModule.POS.route)
                        },
                        onShowMessage = smartLoyaltyViewModel::showFeedback,
                        onClearFeedback = smartLoyaltyViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.LOYALTY,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.EXPENSES.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.EXPENSES)) {
                    ExpenseManagementScreen(
                        session = session,
                        uiState = expenseState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectCategory = expenseViewModel::selectCategory,
                        onSelectStatus = expenseViewModel::selectStatus,
                        onSearchChanged = expenseViewModel::onSearchQueryChanged,
                        onCopyReceiptUri = expenseViewModel::copyReceiptUri,
                        onSaveExpense = { id, cat, amt, dateMs, desc, method, receipt, rec, freq ->
                            expenseViewModel.saveExpense(session, id, cat, amt, dateMs, desc, method, receipt, rec, freq)
                        },
                        onSetApproval = { expId, approved ->
                            expenseViewModel.setApproval(session, expId, approved)
                        },
                        onDeleteExpense = { expId -> expenseViewModel.deleteExpense(session, expId) },
                        onReportMessage = expenseViewModel::showFeedback,
                        onClearFeedback = expenseViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.EXPENSES,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.STAFF.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.STAFF)) {
                    StaffManagementScreen(
                        session = session,
                        uiState = staffState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearchChanged = staffViewModel::onSearchQueryChanged,
                        onSelectStatusFilter = staffViewModel::selectStatusFilter,
                        onSelectDepartmentFilter = staffViewModel::selectDepartmentFilter,
                        onCopyDocumentUri = staffViewModel::copyDocumentUri,
                        onSaveStaff = { id, code, name, phone, addr, title, dept, role, branchesSel, joinMs, status, salType, base, allow, ot, ann, sick, cas, pin, notes ->
                            staffViewModel.saveStaff(
                                session, id, code, name, phone, addr, title, dept, role, branchesSel, joinMs,
                                status, salType, base, allow, ot, ann, sick, cas, pin, notes
                            )
                        },
                        onSetActiveStatus = { staffId, active, reason ->
                            staffViewModel.setStaffActiveStatus(session, staffId, active, reason)
                        },
                        onAddDocument = { staffId, title, docType, uri, notes ->
                            staffViewModel.addDocument(session, staffId, title, docType, uri, notes)
                        },
                        onDeleteDocument = { docId -> staffViewModel.deleteDocument(session, docId) },
                        onUpdateHrPolicy = { targetRole, mStaff, vSal, mShifts, aCorr, aLeave, gPay, aPay, eRep ->
                            staffViewModel.updateHrPermissionPolicy(
                                session, targetRole, mStaff, vSal, mShifts, aCorr, aLeave, gPay, aPay, eRep
                            )
                        },
                        onReportMessage = staffViewModel::showFeedback,
                        onClearFeedback = staffViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.STAFF,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.ATTENDANCE.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.ATTENDANCE)) {
                    AttendanceManagementScreen(
                        session = session,
                        uiState = attendanceState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectStatusFilter = attendanceViewModel::selectStatusFilter,
                        onSearchChanged = attendanceViewModel::onSearchQueryChanged,
                        onShiftMonth = attendanceViewModel::shiftSelectedMonth,
                        onCheckIn = { staffId, method, pin, notes ->
                            attendanceViewModel.checkIn(session, staffId, method, pin, notes)
                        },
                        onCheckOut = { staffId, method, pin ->
                            attendanceViewModel.checkOut(session, staffId, method, pin)
                        },
                        onManualCorrection = { staffId, dateMs, status, inMs, outMs, reason ->
                            attendanceViewModel.recordManualCorrection(session, staffId, dateMs, status, inMs, outMs, reason)
                        },
                        onReportMessage = attendanceViewModel::showFeedback,
                        onClearFeedback = attendanceViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.ATTENDANCE,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.SHIFTS.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.SHIFTS)) {
                    ShiftManagementScreen(
                        session = session,
                        uiState = shiftState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectDayFilter = shiftViewModel::selectDayFilter,
                        onSaveShift = { shiftId, name, startMin, endMin, overnight, breakMin, graceMin, regMin, otMult ->
                            shiftViewModel.saveShift(session, shiftId, name, startMin, endMin, overnight, breakMin, graceMin, regMin, otMult)
                        },
                        onDeleteShift = { shiftId -> shiftViewModel.deleteShift(session, shiftId) },
                        onAssignShift = { shiftId, staffId, dept, dow ->
                            shiftViewModel.assignShift(session, shiftId, staffId, dept, dow)
                        },
                        onDeleteAssignment = { assignmentId -> shiftViewModel.deleteAssignment(session, assignmentId) },
                        onClearFeedback = shiftViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.SHIFTS,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.LEAVE_MANAGEMENT.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.LEAVE_MANAGEMENT)) {
                    LeaveManagementScreen(
                        session = session,
                        uiState = leaveState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectStatusFilter = leaveViewModel::selectStatusFilter,
                        onSubmitLeave = { staffId, type, startMs, endMs, reason ->
                            leaveViewModel.submitLeaveRequest(session, staffId, type, startMs, endMs, reason)
                        },
                        onReviewLeave = { leaveId, approve, notes ->
                            leaveViewModel.reviewLeaveRequest(session, leaveId, approve, notes)
                        },
                        onReportMessage = leaveViewModel::showFeedback,
                        onClearFeedback = leaveViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.LEAVE_MANAGEMENT,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.PAYROLL.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.PAYROLL)) {
                    PayrollManagementScreen(
                        session = session,
                        uiState = payrollState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectStatusFilter = payrollViewModel::selectStatusFilter,
                        onSelectStaffFilter = payrollViewModel::selectStaffFilter,
                        onSearchChanged = payrollViewModel::onSearchQueryChanged,
                        onGeneratePayroll = { staffId, key, label, startMs, endMs, allow, bonus, ded, reason ->
                            payrollViewModel.generatePayroll(session, staffId, key, label, startMs, endMs, allow, bonus, ded, reason)
                        },
                        onApprovePayroll = { payrollId -> payrollViewModel.approvePayroll(session, payrollId) },
                        onMarkPaid = { payrollId, payDateMs, method, ref ->
                            payrollViewModel.markPayrollPaid(session, payrollId, payDateMs, method, ref)
                        },
                        onReportMessage = payrollViewModel::showFeedback,
                        onClearFeedback = payrollViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.PAYROLL,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.USER_PROFILE.route) {
                ProfileScreen(
                    session = session,
                    logoState = logoState,
                    onBack = { navController.popBackStack(AppModule.DASHBOARD.route, inclusive = false) },
                    onSignOut = onSignOut
                )
            }

            composable(AppModule.ACCOUNTING.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.ACCOUNTING)) {
                    AccountingScreen(
                        session = session,
                        uiState = accountingState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectBranchScope = { brId -> accountingViewModel.selectBranchScope(session, brId) },
                        onSelectTab = accountingViewModel::selectTab,
                        onSearchChanged = accountingViewModel::onSearchQueryChanged,
                        onSelectDatePreset = accountingViewModel::selectDatePreset,
                        onSyncTransactions = { accountingViewModel.syncStoredTransactions(session) },
                        onSelectCustomer = accountingViewModel::selectCustomerForLedger,
                        onSelectSupplier = accountingViewModel::selectSupplierForLedger,
                        onSaveAccount = { brId, accId, code, name, type, cat, openCents, active ->
                            accountingViewModel.saveChartAccount(session, brId, accId, code, name, type, cat, openCents, active)
                        },
                        onPostManualJournal = { brId, dateMs, ref, desc, lines ->
                            accountingViewModel.postManualJournal(session, brId, dateMs, ref, desc, lines)
                        },
                        onReverseJournal = { entryId, reason ->
                            accountingViewModel.reverseJournalEntry(session, entryId, reason)
                        },
                        onRecordCashbookEntry = { brId, txType, cbAccId, counterAccId, amt, dateMs, ref, party, desc ->
                            accountingViewModel.recordCashbookTransaction(session, brId, txType, cbAccId, counterAccId, amt, dateMs, ref, party, desc)
                        },
                        onRecordCustomerPayment = { brId, custId, cbAccId, amt, ref, dateMs, notes ->
                            accountingViewModel.recordCustomerPayment(session, brId, custId, cbAccId, amt, ref, dateMs, notes)
                        },
                        onClearFeedback = accountingViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.ACCOUNTING,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.REPORTS.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.REPORTS)) {
                    FinancialReportsScreen(
                        session = session,
                        uiState = financialReportsState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectReportType = { type -> financialReportsViewModel.selectReportType(session, type) },
                        onSelectBranchScope = { brId -> financialReportsViewModel.selectBranchScope(session, brId) },
                        onSelectDatePreset = financialReportsViewModel::selectDatePreset,
                        onSearchChanged = financialReportsViewModel::onSearchQueryChanged,
                        onSelectGlAccount = financialReportsViewModel::selectGlAccount,
                        onExportReport = { format ->
                            financialReportsViewModel.exportCurrentReport(context, session, format) { file, fmt ->
                                Phase4ReportExporter.shareReportFile(context, file, fmt.mimeType, "Share ${financialReportsState.selectedReportType.title}")
                            }
                        },
                        onClearFeedback = financialReportsViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.REPORTS,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.BRANCH_MANAGEMENT.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.BRANCH_MANAGEMENT)) {
                    MultiBranchScreen(
                        session = session,
                        uiState = multiBranchState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearchChanged = multiBranchViewModel::onSearchQueryChanged,
                        onSaveBranch = { brId, name, code, addr, phone, email, mgrName, mgrUid, openCash, openBank, active ->
                            multiBranchViewModel.saveBranch(session, brId, name, code, addr, phone, email, mgrName, mgrUid, openCash, openBank, active)
                        },
                        onSetBranchActive = { brId, active, reason ->
                            multiBranchViewModel.setBranchActiveStatus(session, brId, active, reason)
                        },
                        onAssignStaff = { staffId, primaryId, accessIds, setMgr ->
                            multiBranchViewModel.assignStaffToBranch(session, staffId, primaryId, accessIds, setMgr)
                        },
                        onSwitchBranch = { targetId ->
                            multiBranchViewModel.switchActiveBranch(session, targetId)
                        },
                        onExportConsolidated = { format, onReady ->
                            multiBranchViewModel.exportConsolidated(context, session, format) { file, fmt ->
                                onReady(file, fmt)
                                Phase4ReportExporter.shareReportFile(context, file, fmt.mimeType, "Consolidated Branch Report")
                            }
                        },
                        onClearFeedback = multiBranchViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.BRANCH_MANAGEMENT,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.BACKUP_RESTORE.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.BACKUP_RESTORE)) {
                    BackupSyncScreen(
                        session = session,
                        uiState = backupSyncState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectTab = backupSyncViewModel::selectTab,
                        onProbeConnection = backupSyncViewModel::probeCloudConnection,
                        onStageAndSyncAll = backupSyncViewModel::stageAndSyncAll,
                        onRetryFailed = backupSyncViewModel::retryFailedOnly,
                        onResolveConflict = backupSyncViewModel::resolveConflict,
                        onCreateBackup = backupSyncViewModel::createBackup,
                        onInspectBackup = backupSyncViewModel::inspectBackupSnapshot,
                        onExportBackup = { snapshot ->
                            val file = File(snapshot.localFilePath)
                            if (file.exists()) {
                                Phase4ReportExporter.shareReportFile(
                                    context,
                                    file,
                                    "application/json",
                                    "Export AA CRAVE Backup ${snapshot.backupId}"
                                )
                            }
                        },
                        onImportBackupUri = { uri -> backupSyncViewModel.importBackupUri(context, uri) },
                        onDismissRestoreInspector = backupSyncViewModel::dismissRestoreInspector,
                        onConfirmRestore = backupSyncViewModel::confirmRestoreFromInspector,
                        onClearFeedback = backupSyncViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.BACKUP_RESTORE,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.USER_PERMISSIONS.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.USER_PERMISSIONS)) {
                    UserPermissionsScreen(
                        session = session,
                        uiState = userPermissionsState,
                        onOpenDrawer = openDrawer,
                        onSelectTab = userPermissionsViewModel::selectTab,
                        onSaveOperator = userPermissionsViewModel::saveOperator,
                        onSaveStaffAccount = userPermissionsViewModel::createOrUpdateStaffAccount,
                        onResetStaffPassword = userPermissionsViewModel::resetStaffAccountPassword,
                        onSetOperatorActive = userPermissionsViewModel::suspendOrReactivateOperator,
                        onSendPasswordReset = userPermissionsViewModel::sendPasswordReset,
                        onUpdateRolePolicy = userPermissionsViewModel::updateRolePolicy,
                        onSetDeviceAuthorized = userPermissionsViewModel::setDeviceAuthorized,
                        onClearFeedback = userPermissionsViewModel::clearFeedback
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.USER_PERMISSIONS,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.NOTIFICATIONS.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.NOTIFICATIONS)) {
                    NotificationsScreen(
                        uiState = notificationsState,
                        onOpenDrawer = openDrawer,
                        onSelectCategory = notificationsViewModel::selectCategory,
                        onMarkRead = notificationsViewModel::markRead,
                        onMarkAllRead = notificationsViewModel::markAllRead
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.NOTIFICATIONS,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.AUDIT_LOGS.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.AUDIT_LOGS)) {
                    val logs = (dashboardState as? DashboardUiState.Success)?.auditLogs.orEmpty()
                    AuditLogsScreen(
                        logs = logs,
                        onBack = returnToDashboard
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.AUDIT_LOGS,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }

            composable(AppModule.SETTINGS.route) {
                if (RoleAccessControl.canAccessModule(session.role, AppModule.SETTINGS)) {
                    val successState = dashboardState as? DashboardUiState.Success
                    SettingsScreen(
                        environmentStatus = environmentStatus,
                        logoState = logoState,
                        pendingSyncCount = backupSyncState.syncStatus.pendingCount.takeIf { it > 0 }
                            ?: (successState?.pendingSyncCount ?: 0),
                        auditLogCount = successState?.auditLogs?.size ?: 0,
                        probeResult = backupSyncState.probeResult,
                        onProbeFirebaseConnection = backupSyncViewModel::probeCloudConnection,
                        onImportLogoUri = posViewModel::importOwnerLogo,
                        onResetLogo = posViewModel::resetLogoToTemporaryPlaceholder,
                        onRefreshSampleData = dashboardViewModel::refreshSampleData,
                        onBack = returnToDashboard
                    )
                } else {
                    UnauthorizedModuleScreen(
                        module = AppModule.SETTINGS,
                        session = session,
                        onOpenDrawer = openDrawer,
                        onBackToDashboard = returnToDashboard
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnauthorizedModuleScreen(
    module: AppModule,
    session: UserSession,
    onOpenDrawer: () -> Unit,
    onBackToDashboard: () -> Unit
) {
    BackHandler { onBackToDashboard() }

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open navigation menu",
                            tint = CraveGold
                        )
                    }
                },
                title = {
                    Text(
                        text = module.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .testTag("unauthorized_module_card"),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Access restricted",
                        tint = StatusCrimson,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Module Access Restricted",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = CraveGoldBright,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Your role (${session.role.displayName}) is not authorized to open ${module.title}. Contact an Owner or Admin if you need access.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onBackToDashboard,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CraveGold,
                            contentColor = OnCraveGold
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("unauthorized_back_dashboard_button")
                    ) {
                        Text("Return to Dashboard", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("unauthorized_open_drawer_button")
                    ) {
                        Text("Choose Allowed Module")
                    }
                }
            }
        }
    }
}
