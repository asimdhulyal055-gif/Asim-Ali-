package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Tv
import com.example.domain.model.AppModule
import com.example.domain.model.BranchSummary
import com.example.domain.model.CloudSyncPhase
import com.example.domain.model.CloudSyncStatus
import com.example.domain.model.DashboardOverview
import com.example.domain.model.FirebaseEnvironmentStatus
import com.example.domain.model.LowStockAlertItem
import com.example.domain.model.RecentTransactionItem
import com.example.domain.model.RoleAccessControl
import com.example.domain.model.UserSession
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatCurrency
import com.example.ui.theme.CraveGold
import com.example.ui.theme.CraveGoldBright
import com.example.ui.theme.CraveGoldContainer
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusAmberBg
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusCrimsonBg
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusEmeraldBg
import com.example.ui.theme.StatusSky
import com.example.ui.theme.StatusSkyBg
import com.example.ui.viewmodel.DashboardUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    session: UserSession,
    uiState: DashboardUiState,
    environmentStatus: FirebaseEnvironmentStatus,
    logoState: com.example.data.brand.BrandLogoState = com.example.data.brand.BrandLogoState(hasOwnerLogo = false),
    cloudSyncStatus: CloudSyncStatus? = null,
    onOpenDrawer: () -> Unit,
    onSwitchBranch: (String) -> Unit,
    onRefreshSampleData: () -> Unit,
    onNavigateToModule: (String) -> Unit = {}
) {
    var branchMenuExpanded by remember { mutableStateOf(false) }

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
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Open navigation menu",
                            tint = CraveGoldBright
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        com.example.ui.components.AaCraveBrandLogo(
                            size = 34.dp,
                            logoState = logoState,
                            showPlaceholderLabel = false
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "AA CRAVE",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CraveGoldBright
                                )
                                StatusPill(
                                    text = session.role.displayName.uppercase(),
                                    containerColor = CraveGoldContainer,
                                    contentColor = CraveGoldBright
                                )
                            }
                            Text(
                                text = session.branchName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (session.capabilities.canSwitchBranches && uiState is DashboardUiState.Success) {
                        Box {
                            IconButton(
                                onClick = { branchMenuExpanded = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("branch_switcher_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Store,
                                    contentDescription = "Switch branch",
                                    tint = CraveGold
                                )
                            }
                            DropdownMenu(
                                expanded = branchMenuExpanded,
                                onDismissRequest = { branchMenuExpanded = false }
                            ) {
                                uiState.branches.forEach { branch ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${branch.name} (${branch.code})",
                                                fontWeight = if (branch.branchId == session.branchId) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            branchMenuExpanded = false
                                            onSwitchBranch(branch.branchId)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = onRefreshSampleData,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_dashboard_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh dashboard metrics",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when (uiState) {
            is DashboardUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CraveGold)
                }
            }
            is DashboardUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.message,
                        color = StatusCrimson,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            is DashboardUiState.Success -> {
                DashboardContent(
                    session = session,
                    overview = uiState.overview,
                    environmentStatus = environmentStatus,
                    cloudSyncStatus = cloudSyncStatus,
                    onNavigateToModule = onNavigateToModule,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    session: UserSession,
    overview: DashboardOverview,
    environmentStatus: FirebaseEnvironmentStatus,
    cloudSyncStatus: CloudSyncStatus?,
    onNavigateToModule: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val capabilities = session.capabilities

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Honest Sample Data & Firebase Sync/Offline Banner
            item {
                EnvironmentNoticeCard(
                    overview = overview,
                    session = session,
                    environmentStatus = environmentStatus,
                    cloudSyncStatus = cloudSyncStatus
                )
            }

            // Quick ERP Module Shortcuts (Role-filtered, highlights Inventory & POS)
            item {
                QuickModulesStrip(
                    session = session,
                    onNavigateToModule = onNavigateToModule
                )
            }

            // KPI Grid Header
            item {
                SectionHeader(
                    title = "Operations Overview",
                    subtitle = "${overview.businessDate} • ${overview.branchName}",
                    badgeText = if (overview.isSampleData) "SAMPLE DATA" else "LIVE ROOM DB"
                )
            }

            // KPI Grid (Role-aware)
            item {
                val kpis = buildKpiList(overview, capabilities.canViewFinancialKpis, capabilities.canViewLowStockAlerts)
                KpiGrid(kpis = kpis, columns = if (isWide) 4 else 2)
            }

            // Financial Visualizations (Executive & Branch Manager)
            if (capabilities.canViewFinancialKpis) {
                item {
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SalesChartCard(
                                points = overview.hourlySales,
                                isSampleData = overview.isSampleData,
                                modifier = Modifier.weight(1.4f)
                            )
                            PaymentSplitCard(
                                cash = overview.cashPayments,
                                digital = overview.digitalPayments,
                                isSampleData = overview.isSampleData,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SalesChartCard(
                                points = overview.hourlySales,
                                isSampleData = overview.isSampleData
                            )
                            PaymentSplitCard(
                                cash = overview.cashPayments,
                                digital = overview.digitalPayments,
                                isSampleData = overview.isSampleData
                            )
                        }
                    }
                }
            }

            // Low-Stock Alerts & Recent Transactions
            if (capabilities.canViewLowStockAlerts || capabilities.canViewRecentTransactions) {
                val canOpenInventory = RoleAccessControl.canAccessModule(session.role, AppModule.INVENTORY)
                item {
                    if (isWide && capabilities.canViewLowStockAlerts && capabilities.canViewRecentTransactions) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LowStockCard(
                                items = overview.lowStockItems,
                                isSampleData = overview.isSampleData,
                                onOpenInventory = if (canOpenInventory) {
                                    { onNavigateToModule(AppModule.INVENTORY.route) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                            RecentTransactionsCard(
                                items = overview.recentTransactions,
                                isSampleData = overview.isSampleData,
                                modifier = Modifier.weight(1.2f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (capabilities.canViewLowStockAlerts) {
                                LowStockCard(
                                    items = overview.lowStockItems,
                                    isSampleData = overview.isSampleData,
                                    onOpenInventory = if (canOpenInventory) {
                                        { onNavigateToModule(AppModule.INVENTORY.route) }
                                    } else null
                                )
                            }
                            if (capabilities.canViewRecentTransactions) {
                                RecentTransactionsCard(
                                    items = overview.recentTransactions,
                                    isSampleData = overview.isSampleData
                                )
                            }
                        }
                    }
                }
            }

            if (capabilities.canViewFinancialKpis || capabilities.canManagePurchases || capabilities.canManageSuppliers) {
                val canOpenPurchases = RoleAccessControl.canAccessModule(session.role, AppModule.PURCHASES)
                val canOpenInventory = RoleAccessControl.canAccessModule(session.role, AppModule.INVENTORY)
                item {
                    Phase4DashboardSection(
                        overview = overview,
                        onOpenInventory = if (canOpenInventory) {
                            { onNavigateToModule(AppModule.INVENTORY.route) }
                        } else null,
                        onOpenPurchases = if (canOpenPurchases) {
                            { onNavigateToModule(AppModule.PURCHASES.route) }
                        } else null
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun QuickModulesStrip(
    session: UserSession,
    onNavigateToModule: (String) -> Unit
) {
    val quickModules = remember(session.role) {
        listOf(
            AppModule.POS,
            AppModule.INVENTORY,
            AppModule.PURCHASES,
            AppModule.KITCHEN_DISPLAY,
            AppModule.LOYALTY,
            AppModule.ACCOUNTING,
            AppModule.STAFF,
            AppModule.PAYROLL,
            AppModule.USER_PERMISSIONS
        ).filter { RoleAccessControl.canAccessModule(session.role, it) }
    }
    if (quickModules.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "QUICK ERP MODULES",
            style = MaterialTheme.typography.labelSmall,
            color = CraveGold,
            fontWeight = FontWeight.SemiBold
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 680.dp) 3 else 2
            val rows = remember(quickModules, columns) { quickModules.chunked(columns) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { rowModules ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowModules.forEach { module ->
                            val icon = when (module) {
                                AppModule.POS -> Icons.Filled.PointOfSale
                                AppModule.INVENTORY -> Icons.Filled.Inventory2
                                AppModule.KITCHEN_DISPLAY -> Icons.Filled.Tv
                                AppModule.PURCHASES -> Icons.Filled.ShoppingBag
                                AppModule.LOYALTY -> Icons.Filled.CardMembership
                                AppModule.ACCOUNTING -> Icons.Filled.ReceiptLong
                                AppModule.STAFF -> Icons.Filled.Badge
                                AppModule.PAYROLL -> Icons.Filled.Payments
                                AppModule.USER_PERMISSIONS -> Icons.Filled.AdminPanelSettings
                                else -> Icons.Filled.Store
                            }
                            val displayTitle = when (module) {
                                AppModule.PURCHASES -> "Purchase Management"
                                AppModule.INVENTORY -> "Inventory & Stock"
                                AppModule.USER_PERMISSIONS -> "Staff Accounts & Roles"
                                else -> module.title
                            }
                            Surface(
                                color = ObsidianSurface,
                                border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.45f)),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .clickable { onNavigateToModule(module.route) }
                                    .testTag("quick_module_${module.route}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = displayTitle,
                                        tint = CraveGoldBright,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = displayTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        repeat(columns - rowModules.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private data class KpiSpec(
    val title: String,
    val value: String,
    val caption: String,
    val icon: ImageVector,
    val accent: Color
)

private fun buildKpiList(
    overview: DashboardOverview,
    includeFinancials: Boolean,
    includeStock: Boolean
): List<KpiSpec> {
    val list = mutableListOf<KpiSpec>()
    if (includeFinancials) {
        list += KpiSpec(
            title = "Today's Sales",
            value = formatCurrency(overview.todaySales),
            caption = "Gross branch receipts",
            icon = Icons.Filled.AttachMoney,
            accent = CraveGoldBright
        )
    }
    list += KpiSpec(
        title = "Today's Orders",
        value = overview.todayOrders.toString(),
        caption = "Dine-in, Takeaway & Delivery",
        icon = Icons.Filled.ReceiptLong,
        accent = StatusSky
    )
    list += KpiSpec(
        title = "Total Customers",
        value = overview.totalCustomers.toString(),
        caption = "Active guests served today",
        icon = Icons.Filled.Groups,
        accent = StatusEmerald
    )
    list += KpiSpec(
        title = "Pending Kitchen",
        value = overview.pendingKitchenOrders.toString(),
        caption = "Active KOT queue",
        icon = Icons.Filled.Restaurant,
        accent = StatusAmber
    )
    if (includeStock) {
        list += KpiSpec(
            title = "Low-Stock Alerts",
            value = overview.lowStockAlertCount.toString(),
            caption = "Below reorder threshold",
            icon = Icons.Filled.WarningAmber,
            accent = StatusCrimson
        )
    }
    if (includeFinancials) {
        list += KpiSpec(
            title = "Daily Expenses",
            value = formatCurrency(overview.dailyExpenses),
            caption = "Logged branch outflow",
            icon = Icons.Filled.AccountBalanceWallet,
            accent = StatusCrimson
        )
        list += KpiSpec(
            title = "Estimated Profit",
            value = formatCurrency(overview.estimatedProfit),
            caption = "Est. only (unaudited)",
            icon = Icons.Filled.TrendingUp,
            accent = StatusEmerald
        )
        list += KpiSpec(
            title = "Digital + Cash",
            value = formatCurrency(overview.digitalPayments),
            caption = "Cash: ${formatCurrency(overview.cashPayments)}",
            icon = Icons.Filled.Payments,
            accent = CraveGold
        )
    }
    return list
}

@Composable
private fun KpiGrid(kpis: List<KpiSpec>, columns: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        kpis.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { kpi ->
                    KpiCard(spec = kpi, modifier = Modifier.weight(1f))
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KpiCard(spec: KpiSpec, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = spec.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = spec.icon,
                    contentDescription = spec.title,
                    tint = spec.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = spec.value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = spec.caption,
                style = MaterialTheme.typography.labelSmall,
                color = spec.accent.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PaymentSplitCard(
    cash: Double,
    digital: Double,
    isSampleData: Boolean,
    modifier: Modifier = Modifier
) {
    val total = (cash + digital).coerceAtLeast(0.01)
    val cashFraction = (cash / total).toFloat().coerceIn(0f, 1f)
    val digitalFraction = (1f - cashFraction).coerceIn(0f, 1f)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cash vs Digital Payments",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isSampleData) {
                    StatusPill(
                        text = "SAMPLE",
                        containerColor = CraveGoldContainer,
                        contentColor = CraveGoldBright
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianSurfaceHigh)
            ) {
                Box(
                    modifier = Modifier
                        .weight(cashFraction.coerceAtLeast(0.05f))
                        .fillMaxHeight()
                        .background(CraveGold)
                )
                Box(
                    modifier = Modifier
                        .weight(digitalFraction.coerceAtLeast(0.05f))
                        .fillMaxHeight()
                        .background(StatusEmerald)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Cash (${(cashFraction * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = CraveGoldBright
                    )
                    Text(
                        text = formatCurrency(cash),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Card / Digital (${(digitalFraction * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusEmerald
                    )
                    Text(
                        text = formatCurrency(digital),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun LowStockCard(
    items: List<LowStockAlertItem>,
    isSampleData: Boolean,
    onOpenInventory: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Inventory2,
                        contentDescription = "Low Stock",
                        tint = StatusAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Low-Stock Alerts",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isSampleData) {
                        StatusPill(
                            text = "SAMPLE",
                            containerColor = CraveGoldContainer,
                            contentColor = CraveGoldBright
                        )
                    }
                    if (onOpenInventory != null) {
                        TextButton(
                            onClick = onOpenInventory,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dashboard_open_inventory_button")
                        ) {
                            Text("Open Inventory", color = CraveGoldBright)
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                Text(
                    text = "All tracked ingredients are above reorder levels.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusEmerald
                )
            } else {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.ingredientName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Reorder below ${item.reorderLevel} ${item.unit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = "${item.currentStock} ${item.unit}",
                            containerColor = StatusCrimsonBg,
                            contentColor = StatusCrimson
                        )
                    }
                    if (index < items.lastIndex) {
                        HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentTransactionsCard(
    items: List<RecentTransactionItem>,
    isSampleData: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isSampleData) {
                    StatusPill(
                        text = "SAMPLE",
                        containerColor = CraveGoldContainer,
                        contentColor = CraveGoldBright
                    )
                }
            }

            items.forEachIndexed { index, tx ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${tx.orderNumber} • ${tx.customerName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${tx.invoiceNumber} • ${tx.timeLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatCurrency(tx.amount),
                            style = MaterialTheme.typography.titleSmall,
                            color = CraveGoldBright
                        )
                        StatusPill(
                            text = "${tx.paymentMethod} • ${tx.status}",
                            containerColor = if (tx.paymentMethod == "Cash") CraveGoldContainer else StatusSkyBg,
                            contentColor = if (tx.paymentMethod == "Cash") CraveGoldBright else StatusSky
                        )
                    }
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun EnvironmentNoticeCard(
    overview: DashboardOverview,
    session: UserSession,
    environmentStatus: FirebaseEnvironmentStatus,
    cloudSyncStatus: CloudSyncStatus?
) {
    val phase = cloudSyncStatus?.phase
    val (pillText, pillBg, pillFg) = when (phase) {
        CloudSyncPhase.ONLINE_READY -> Triple("ONLINE", StatusEmeraldBg, StatusEmerald)
        CloudSyncPhase.SYNCING -> Triple("SYNCING", StatusSkyBg, StatusSky)
        CloudSyncPhase.OFFLINE -> Triple("OFFLINE", StatusAmberBg, StatusAmber)
        CloudSyncPhase.CONFIG_MISSING -> Triple("LOCAL ROOM", StatusAmberBg, StatusAmber)
        CloudSyncPhase.AUTH_REQUIRED -> Triple("AUTH NEEDED", StatusAmberBg, StatusAmber)
        CloudSyncPhase.LOCKED_RULES, CloudSyncPhase.DEVICE_REVOKED, CloudSyncPhase.ERROR ->
            Triple(phase.label, StatusCrimsonBg, StatusCrimson)
        null -> if (overview.isSampleData) Triple("SAMPLE", StatusAmberBg, StatusAmber) else Triple("LOCAL ROOM", CraveGoldContainer, CraveGoldBright)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
        border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.35f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = "Local Room Persistence",
                tint = CraveGold,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (session.isPreviewSandbox) {
                        "Local Sandbox Preview • Room Offline-First Active"
                    } else if (phase == CloudSyncPhase.ONLINE_READY) {
                        "Firebase Connected (${environmentStatus.projectName}) • Room + RTDB Sync Ready"
                    } else {
                        "Firebase Session • ${environmentStatus.realtimeDbStatus}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = CraveGoldBright
                )
                val pending = cloudSyncStatus?.pendingCount ?: overview.pendingSyncCount
                val conflicts = cloudSyncStatus?.openConflictCount ?: 0
                Text(
                    text = "RTDB: ${environmentStatus.realtimeDbStatus} • Pending sync: $pending" +
                        if (conflicts > 0) " • Open conflicts: $conflicts" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(
                text = pillText,
                containerColor = pillBg,
                contentColor = pillFg
            )
        }
    }
}

@Composable
private fun Phase4DashboardSection(
    overview: DashboardOverview,
    onOpenInventory: (() -> Unit)? = null,
    onOpenPurchases: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Procurement, Suppliers & Loyalty",
            subtitle = "Live Room Ledger • Purchases, Payables, Expenses & CRM",
            badgeText = "PHASE 4 LIVE"
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianBorder),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Total Purchases", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatCurrency(overview.totalPurchasesAmount), style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Supplier Payables", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatCurrency(overview.outstandingSupplierBalances), style = MaterialTheme.typography.titleMedium, color = StatusCrimson)
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Operating Expenses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatCurrency(overview.totalExpensesAmount), style = MaterialTheme.typography.titleMedium, color = StatusAmber)
                    }
                }
                HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.6f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Registered Customers: ${overview.totalRegisteredCustomers}", style = MaterialTheme.typography.bodySmall, color = StatusEmerald)
                    Text("Loyalty Issued: ${overview.loyaltyPointsIssued} pts • Redeemed: ${overview.loyaltyPointsRedeemed} pts", style = MaterialTheme.typography.bodySmall, color = CraveGold)
                }

                if (overview.topSuppliers.isNotEmpty()) {
                    HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.6f))
                    Text("Top Suppliers", style = MaterialTheme.typography.labelLarge, color = CraveGoldBright)
                    overview.topSuppliers.forEach { sup ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(sup.supplierName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Spend: ${formatCurrency(sup.totalPurchases)} | Due: ${formatCurrency(sup.outstandingBalance)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (overview.recentPurchases.isNotEmpty()) {
                    HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.6f))
                    Text("Recent Purchases", style = MaterialTheme.typography.labelLarge, color = CraveGoldBright)
                    overview.recentPurchases.take(3).forEach { po ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${po.poNumber} • ${po.supplierName}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${formatCurrency(po.amount)} (${po.status})", style = MaterialTheme.typography.labelSmall, color = StatusSky)
                        }
                    }
                }

                if (overview.recentSupplierPayments.isNotEmpty()) {
                    HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.6f))
                    Text("Recent Supplier Payments", style = MaterialTheme.typography.labelLarge, color = CraveGoldBright)
                    overview.recentSupplierPayments.take(3).forEach { pay ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${pay.supplierName} (${pay.referenceNumber})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatCurrency(pay.amount), style = MaterialTheme.typography.labelSmall, color = StatusEmerald)
                        }
                    }
                }

                if (onOpenInventory != null || onOpenPurchases != null) {
                    HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.6f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (onOpenInventory != null) {
                            OutlinedButton(
                                onClick = onOpenInventory,
                                border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_section_open_inventory")
                            ) {
                                Text("Inventory", color = CraveGoldBright, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (onOpenPurchases != null) {
                            OutlinedButton(
                                onClick = onOpenPurchases,
                                border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_section_open_purchases")
                            ) {
                                Text("Purchase Management", color = CraveGoldBright, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
