package com.example.ui.loyalty

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.LoyaltyCardStatus
import com.example.domain.model.LoyaltyProgramSettingsRecord
import com.example.domain.model.LoyaltyRewardVoucherRecord
import com.example.domain.model.UserSession
import com.example.ui.theme.CraveGoldBright
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.viewmodel.SmartLoyaltyUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoyaltyHubScreen(
    session: UserSession,
    uiState: SmartLoyaltyUiState,
    onOpenDrawer: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onSelectStatusFilter: (LoyaltyCardStatus?) -> Unit,
    onSelectBranchScope: (String?) -> Unit,
    onSelectPresetDays: (Int?) -> Unit,
    onRefreshReport: () -> Unit,
    onEnrollCustomerCard: (String, String?) -> Unit,
    onOpenCustomerBundle: (String) -> Unit,
    onClearCustomerBundle: () -> Unit,
    onLookupQrOrCode: (String) -> Unit,
    onSuspendCard: (String, String, String) -> Unit,
    onReactivateCard: (String, String, String) -> Unit,
    onReplaceCard: (String, String, String, String?) -> Unit,
    onRenewCard: (String, String, String) -> Unit,
    onAdjustStamps: (String, String, Int, String) -> Unit,
    onSaveSettings: (LoyaltyProgramSettingsRecord) -> Unit,
    onApplyVoucherInPos: (LoyaltyRewardVoucherRecord) -> Unit,
    onShowMessage: (String, Boolean) -> Unit,
    onClearFeedback: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showQrScannerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    val activeBundle = uiState.selectedCustomerBundle
    if (activeBundle != null) {
        val branch = uiState.branches.firstOrNull { it.branchId == activeBundle.customer.branchId }
        CustomerLoyaltyProfileScreen(
            session = session,
            bundle = activeBundle,
            logoState = uiState.logoState,
            branchName = branch?.name ?: session.branchName,
            branchAddress = branch?.address ?: "",
            onBack = onClearCustomerBundle,
            onEnrollCard = { custId -> onEnrollCustomerCard(custId, null) },
            onSuspendCard = { cardId, reason -> onSuspendCard(cardId, activeBundle.customer.customerId, reason) },
            onReactivateCard = { cardId, reason -> onReactivateCard(cardId, activeBundle.customer.customerId, reason) },
            onReplaceCard = { cardId, reason, customNo -> onReplaceCard(cardId, activeBundle.customer.customerId, reason, customNo) },
            onRenewCard = { cardId, reason -> onRenewCard(cardId, activeBundle.customer.customerId, reason) },
            onAdjustStamps = { cardId, delta, reason -> onAdjustStamps(cardId, activeBundle.customer.customerId, delta, reason) },
            onShowMessage = onShowMessage
        )
    } else {
        Scaffold(
            containerColor = ObsidianBg,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianSurface),
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = CraveGoldBright)
                        }
                    },
                    title = {
                        Column {
                            Text("Smart Loyalty & Rewards", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                            Text(
                                "${session.branchName} • ${uiState.cards.size} Cards",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showQrScannerDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("loyalty_topbar_scan_button")
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan Loyalty QR", tint = CraveGoldBright)
                        }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                val tabs = listOf(
                    "Dashboard & Cards",
                    "QR Scanner",
                    "Vouchers (${uiState.vouchers.size})",
                    "Reports",
                    "Rules & Audit"
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = ObsidianSurface,
                    contentColor = CraveGoldBright,
                    edgePadding = 8.dp
                ) {
                    tabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(label, maxLines = 1) },
                            modifier = Modifier.testTag("loyalty_tab_$idx")
                        )
                    }
                }

                when (selectedTab) {
                    0 -> LoyaltyDashboardAndMembersTab(
                        session = session,
                        uiState = uiState,
                        onSearchChanged = onSearchChanged,
                        onSelectStatusFilter = onSelectStatusFilter,
                        onOpenScanner = { showQrScannerDialog = true },
                        onEnrollCustomerCard = onEnrollCustomerCard,
                        onOpenMemberProfile = onOpenCustomerBundle
                    )
                    1 -> LoyaltyQrLookupTab(
                        uiState = uiState,
                        onOpenScannerDialog = { showQrScannerDialog = true },
                        onLookupCode = onLookupQrOrCode,
                        onOpenCardProfile = onOpenCustomerBundle
                    )
                    2 -> LoyaltyVouchersTab(
                        session = session,
                        uiState = uiState,
                        onOpenMemberCard = onOpenCustomerBundle,
                        onApplyVoucherInPos = onApplyVoucherInPos
                    )
                    3 -> LoyaltyReportsAndReconciliationTab(
                        session = session,
                        uiState = uiState,
                        onSelectBranchScope = onSelectBranchScope,
                        onSelectPresetDays = onSelectPresetDays,
                        onRefreshReport = onRefreshReport,
                        onOpenCustomerProfile = onOpenCustomerBundle,
                        onShowMessage = onShowMessage
                    )
                    4 -> LoyaltySettingsAndAuditTab(
                        session = session,
                        uiState = uiState,
                        onSaveSettings = onSaveSettings
                    )
                }
            }
        }
    }

    if (showQrScannerDialog) {
        QrCodeCameraScannerDialog(
            onDismiss = { showQrScannerDialog = false },
            onCodeCaptured = { captured ->
                showQrScannerDialog = false
                onLookupQrOrCode(captured)
            }
        )
    }
}
