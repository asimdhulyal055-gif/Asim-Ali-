package com.example.ui.accounting

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.StaffProfileEntity
import com.example.domain.model.BranchFinancialSnapshot
import com.example.domain.model.BranchSummary
import com.example.domain.model.UserSession
import com.example.domain.model.centsToCurrency
import com.example.printing.Phase4ReportExporter
import com.example.printing.ReportExportFormat
import com.example.ui.components.StatusPill
import com.example.ui.theme.CraveGold
import com.example.ui.theme.CraveGoldBright
import com.example.ui.theme.CraveGoldContainer
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.OnCraveGold
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusAmberBg
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusCrimsonBg
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusEmeraldBg
import com.example.ui.theme.StatusSky
import com.example.ui.viewmodel.MultiBranchUiState
import java.io.File
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiBranchScreen(
    session: UserSession,
    uiState: MultiBranchUiState,
    onOpenDrawer: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onSaveBranch: (
        branchId: String?,
        name: String,
        code: String,
        address: String,
        phone: String,
        email: String,
        managerName: String,
        managerUid: String?,
        openingCashCents: Long,
        openingBankCents: Long,
        isActive: Boolean
    ) -> Unit,
    onSetBranchActive: (branchId: String, active: Boolean, reason: String) -> Unit,
    onAssignStaff: (
        staffId: String,
        primaryBranchId: String,
        accessibleBranchIds: List<String>,
        setAsManager: Boolean
    ) -> Unit,
    onSwitchBranch: (String) -> Unit,
    onExportConsolidated: (ReportExportFormat, (File, ReportExportFormat) -> Unit) -> Unit,
    onClearFeedback: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showBranchDialog by remember { mutableStateOf(false) }
    var editingBranch by remember { mutableStateOf<BranchSummary?>(null) }
    var statusChangeBranch by remember { mutableStateOf<BranchSummary?>(null) }
    var showAssignStaffDialog by remember { mutableStateOf(false) }
    var preselectedAssignBranchId by remember { mutableStateOf(session.branchId) }
    var lastExportedFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(uiState.feedbackMessage) {
        val msg = uiState.feedbackMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    val visibleSnapshots = remember(uiState.consolidatedReport.branchSnapshots, session) {
        if (session.capabilities.canViewConsolidatedReports) {
            uiState.consolidatedReport.branchSnapshots
        } else {
            uiState.consolidatedReport.branchSnapshots.filter { it.branch.branchId == session.branchId }
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianSurface),
                navigationIcon = {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("multibranch_menu_button")
                    ) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open Drawer", tint = CraveGoldBright)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Multi-Branch Management",
                            style = MaterialTheme.typography.titleLarge,
                            color = CraveGoldBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Active Workspace: ${session.branchName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (session.capabilities.canAssignBranchStaff) {
                        IconButton(
                            onClick = {
                                preselectedAssignBranchId = session.branchId
                                showAssignStaffDialog = true
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("assign_branch_staff_button")
                        ) {
                            Icon(Icons.Filled.GroupAdd, contentDescription = "Assign Staff to Branch", tint = CraveGoldBright)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (session.capabilities.canManageBranches) {
                FloatingActionButton(
                    onClick = {
                        editingBranch = null
                        showBranchDialog = true
                    },
                    containerColor = CraveGold,
                    contentColor = OnCraveGold,
                    modifier = Modifier.testTag("add_branch_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Branch")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Search branch code, name, address, or manager...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = CraveGold) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("multibranch_search_input")
                )
            }

            if (session.capabilities.canViewConsolidatedReports) {
                item {
                    val cons = uiState.consolidatedReport
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Consolidated Company Overview",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = CraveGoldBright,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${uiState.branches.count { it.isActive }} active / ${uiState.branches.size} total branches",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPill(
                                    text = "ALL BRANCHES",
                                    containerColor = CraveGoldContainer,
                                    contentColor = CraveGoldBright
                                )
                            }

                            HorizontalDivider(color = ObsidianBorder)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                BranchMetricColumn("Total Sales", centsToCurrency(cons.totalSalesCents), StatusEmerald)
                                BranchMetricColumn("Total Purchases", centsToCurrency(cons.totalPurchasesCents), StatusAmber)
                                BranchMetricColumn(
                                    "Net Profit",
                                    centsToCurrency(cons.netProfitCents),
                                    if (cons.netProfitCents >= 0) CraveGoldBright else StatusCrimson
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                BranchMetricColumn("Expenses", centsToCurrency(cons.totalExpensesCents), MaterialTheme.colorScheme.onSurface)
                                BranchMetricColumn("Payroll", centsToCurrency(cons.totalPayrollCents), MaterialTheme.colorScheme.onSurface)
                                BranchMetricColumn(
                                    "Cash & Bank",
                                    centsToCurrency(cons.totalCashBalanceCents + cons.totalBankBalanceCents),
                                    StatusSky
                                )
                            }

                            if (session.capabilities.canExportReports) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            onExportConsolidated(ReportExportFormat.PDF) { f, _ -> lastExportedFile = f }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                                        modifier = Modifier.testTag("export_consolidated_pdf_button")
                                    ) {
                                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Export PDF")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onExportConsolidated(ReportExportFormat.CSV) { f, _ -> lastExportedFile = f }
                                        },
                                        border = BorderStroke(1.dp, CraveGold),
                                        modifier = Modifier.testTag("export_consolidated_csv_button")
                                    ) {
                                        Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(16.dp), tint = CraveGoldBright)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Export CSV", color = CraveGoldBright)
                                    }
                                    lastExportedFile?.let { file ->
                                        TextButton(
                                            onClick = {
                                                val fmt = if (file.extension.equals("pdf", true)) ReportExportFormat.PDF else ReportExportFormat.CSV
                                                Phase4ReportExporter.shareReportFile(context, file, fmt.mimeType, "Consolidated Branch Report")
                                            }
                                        ) {
                                            Text("Share", color = StatusSky)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            items(visibleSnapshots, key = { it.branch.branchId }) { snap ->
                BranchFinancialCard(
                    snapshot = snap,
                    isCurrentBranch = snap.branch.branchId == session.branchId,
                    canSwitch = session.capabilities.canSwitchBranches && snap.branch.isActive && snap.branch.branchId != session.branchId,
                    canEdit = session.capabilities.canManageBranches,
                    canAssignStaff = session.capabilities.canAssignBranchStaff && snap.branch.isActive,
                    onSwitch = { onSwitchBranch(snap.branch.branchId) },
                    onEdit = {
                        editingBranch = snap.branch
                        showBranchDialog = true
                    },
                    onToggleActive = { statusChangeBranch = snap.branch },
                    onAssignStaff = {
                        preselectedAssignBranchId = snap.branch.branchId
                        showAssignStaffDialog = true
                    }
                )
            }
        }
    }

    if (showBranchDialog) {
        BranchEditorDialog(
            existing = editingBranch,
            staffList = uiState.staffList,
            onDismiss = { showBranchDialog = false },
            onSave = { name, code, addr, phone, email, mgrName, mgrUid, openCash, openBank, active ->
                onSaveBranch(
                    editingBranch?.branchId,
                    name,
                    code,
                    addr,
                    phone,
                    email,
                    mgrName,
                    mgrUid,
                    openCash,
                    openBank,
                    active
                )
                showBranchDialog = false
            }
        )
    }

    statusChangeBranch?.let { br ->
        BranchStatusDialog(
            branch = br,
            onDismiss = { statusChangeBranch = null },
            onConfirm = { reason ->
                onSetBranchActive(br.branchId, !br.isActive, reason)
                statusChangeBranch = null
            }
        )
    }

    if (showAssignStaffDialog) {
        AssignBranchStaffDialog(
            session = session,
            branches = uiState.branches.filter { it.isActive },
            staffList = uiState.staffList,
            initialBranchId = preselectedAssignBranchId,
            onDismiss = { showAssignStaffDialog = false },
            onAssign = { staffId, primaryId, accessIds, setMgr ->
                onAssignStaff(staffId, primaryId, accessIds, setMgr)
                showAssignStaffDialog = false
            }
        )
    }
}

@Composable
private fun BranchMetricColumn(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BranchFinancialCard(
    snapshot: BranchFinancialSnapshot,
    isCurrentBranch: Boolean,
    canSwitch: Boolean,
    canEdit: Boolean,
    canAssignStaff: Boolean,
    onSwitch: () -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onAssignStaff: () -> Unit
) {
    val br = snapshot.branch
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentBranch) ObsidianSurfaceElevated else ObsidianSurface
        ),
        border = BorderStroke(
            width = if (isCurrentBranch) 1.5.dp else 1.dp,
            color = if (isCurrentBranch) CraveGold else ObsidianBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("branch_card_${br.code}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Store,
                        contentDescription = null,
                        tint = if (br.isActive) CraveGoldBright else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = "${br.code} — ${br.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (br.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = br.address.ifBlank { "No address recorded" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusPill(
                        text = if (br.isActive) "ACTIVE" else "INACTIVE",
                        containerColor = if (br.isActive) StatusEmeraldBg else StatusCrimsonBg,
                        contentColor = if (br.isActive) StatusEmerald else StatusCrimson
                    )
                    if (isCurrentBranch) {
                        StatusPill(
                            text = "CURRENT",
                            containerColor = CraveGoldContainer,
                            contentColor = CraveGoldBright
                        )
                    } else if (br.isMainBranch) {
                        StatusPill(
                            text = "FLAGSHIP",
                            containerColor = StatusAmberBg,
                            contentColor = StatusAmber
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Manager: ${br.managerName.ifBlank { "Unassigned" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CraveGoldBright
                )
                Text(
                    text = "Staff: ${snapshot.staffCount} • ${br.phone.ifBlank { "No phone" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = ObsidianBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BranchMetricColumn("Sales", centsToCurrency(snapshot.totalSalesCents), StatusEmerald)
                BranchMetricColumn("Expenses", centsToCurrency(snapshot.totalExpensesCents), StatusAmber)
                BranchMetricColumn(
                    "Net Profit",
                    centsToCurrency(snapshot.netProfitCents),
                    if (snapshot.netProfitCents >= 0) CraveGoldBright else StatusCrimson
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BranchMetricColumn("Purchases", centsToCurrency(snapshot.totalPurchasesCents), MaterialTheme.colorScheme.onSurface)
                BranchMetricColumn("Cash", centsToCurrency(snapshot.cashBalanceCents), StatusSky)
                BranchMetricColumn("Bank", centsToCurrency(snapshot.bankBalanceCents), StatusSky)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canSwitch) {
                    Button(
                        onClick = onSwitch,
                        colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                        modifier = Modifier.testTag("switch_branch_button_${br.code}")
                    ) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Switch")
                    }
                }
                if (canAssignStaff) {
                    OutlinedButton(
                        onClick = onAssignStaff,
                        border = BorderStroke(1.dp, ObsidianBorder)
                    ) {
                        Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp), tint = CraveGold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Staff", color = CraveGoldBright)
                    }
                }
                if (canEdit) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("edit_branch_button_${br.code}")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Branch", tint = CraveGoldBright)
                    }
                    if (!br.isMainBranch) {
                        IconButton(
                            onClick = onToggleActive,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("toggle_branch_button_${br.code}")
                        ) {
                            Icon(
                                Icons.Filled.PowerSettingsNew,
                                contentDescription = if (br.isActive) "Deactivate Branch" else "Reactivate Branch",
                                tint = if (br.isActive) StatusCrimson else StatusEmerald
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchEditorDialog(
    existing: BranchSummary?,
    staffList: List<StaffProfileEntity>,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        code: String,
        address: String,
        phone: String,
        email: String,
        managerName: String,
        managerUid: String?,
        openingCashCents: Long,
        openingBankCents: Long,
        isActive: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var code by remember { mutableStateOf(existing?.code ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var managerName by remember { mutableStateOf(existing?.managerName ?: "") }
    var managerUid by remember { mutableStateOf(existing?.managerUid) }
    var openCashStr by remember { mutableStateOf(if (existing != null && existing.openingCashCents > 0) String.format("%.2f", existing.openingCashCents / 100.0) else "0.00") }
    var openBankStr by remember { mutableStateOf(if (existing != null && existing.openingBankCents > 0) String.format("%.2f", existing.openingBankCents / 100.0) else "0.00") }
    var isActive by remember { mutableStateOf(existing?.isActive ?: true) }
    var mgrMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text(
                text = if (existing == null) "Create New Branch" else "Edit Branch (${existing.code})",
                color = CraveGoldBright
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Branch Code (e.g. BR03)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Branch Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Street Address") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Branch Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = managerName,
                    onValueChange = { managerName = it },
                    label = { Text("Branch Manager Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (staffList.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { mgrMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pick Manager from Staff Directory", color = CraveGoldBright)
                    }
                    DropdownMenu(expanded = mgrMenu, onDismissRequest = { mgrMenu = false }) {
                        staffList.filter { it.employmentStatus == "ACTIVE" }.forEach { st ->
                            DropdownMenuItem(
                                text = { Text("${st.fullName} (${st.jobTitle})") },
                                onClick = {
                                    managerName = st.fullName
                                    managerUid = st.linkedUserUid ?: st.staffId
                                    mgrMenu = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = openCashStr,
                        onValueChange = { openCashStr = it },
                        label = { Text("Opening Cash") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = openBankStr,
                        onValueChange = { openBankStr = it },
                        label = { Text("Opening Bank") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (existing?.isMainBranch != true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Branch Active", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cashCents = ((openCashStr.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
                    val bankCents = ((openBankStr.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
                    onSave(name, code, address, phone, email, managerName, managerUid, cashCents, bankCents, isActive)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) {
                Text("Save Branch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BranchStatusDialog(
    branch: BranchSummary,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text(
                text = if (branch.isActive) "Deactivate ${branch.name}?" else "Reactivate ${branch.name}?",
                color = if (branch.isActive) StatusCrimson else StatusEmerald
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (branch.isActive) {
                        "Deactivating prevents switching into this branch while preserving all historical financial journals and orders."
                    } else {
                        "Reactivating enables POS, HR, and accounting operations at ${branch.name}."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Audit Reason / Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (branch.isActive) StatusCrimson else StatusEmerald,
                    contentColor = OnCraveGold
                )
            ) {
                Text(if (branch.isActive) "Deactivate" else "Reactivate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AssignBranchStaffDialog(
    session: UserSession,
    branches: List<BranchSummary>,
    staffList: List<StaffProfileEntity>,
    initialBranchId: String,
    onDismiss: () -> Unit,
    onAssign: (staffId: String, primaryBranchId: String, accessibleBranchIds: List<String>, setAsManager: Boolean) -> Unit
) {
    val activeStaff = remember(staffList) { staffList.filter { it.employmentStatus == "ACTIVE" } }
    var selectedStaff by remember { mutableStateOf(activeStaff.firstOrNull()) }
    var staffMenu by remember { mutableStateOf(false) }

    val availableBranches = remember(branches, session) {
        if (session.capabilities.canManageBranches) branches else branches.filter { it.branchId == session.branchId }
    }
    var primaryBranch by remember {
        mutableStateOf(availableBranches.firstOrNull { it.branchId == initialBranchId } ?: availableBranches.firstOrNull())
    }
    var primaryMenu by remember { mutableStateOf(false) }
    val checkedBranches = remember(selectedStaff, primaryBranch) {
        mutableStateListOf<String>().apply {
            selectedStaff?.accessibleBranchIdsCsv
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.forEach { if (!contains(it)) add(it) }
            primaryBranch?.branchId?.let { if (!contains(it)) add(it) }
        }
    }
    var setAsManager by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("Assign Employee to Branch", color = CraveGoldBright) },
        text = {
            if (activeStaff.isEmpty() || primaryBranch == null) {
                Text("No active staff or target branches available.")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = { staffMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedStaff?.let { "${it.employeeCode} — ${it.fullName}" } ?: "Select Staff", color = CraveGoldBright)
                    }
                    DropdownMenu(expanded = staffMenu, onDismissRequest = { staffMenu = false }) {
                        activeStaff.forEach { st ->
                            DropdownMenuItem(
                                text = { Text("${st.employeeCode} — ${st.fullName} (${st.jobTitle})") },
                                onClick = { selectedStaff = st; staffMenu = false }
                            )
                        }
                    }

                    OutlinedButton(onClick = { primaryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Primary: ${primaryBranch?.code} — ${primaryBranch?.name}", color = CraveGoldBright)
                    }
                    DropdownMenu(expanded = primaryMenu, onDismissRequest = { primaryMenu = false }) {
                        availableBranches.forEach { br ->
                            DropdownMenuItem(
                                text = { Text("${br.code} — ${br.name}") },
                                onClick = {
                                    primaryBranch = br
                                    if (!checkedBranches.contains(br.branchId)) checkedBranches.add(br.branchId)
                                    primaryMenu = false
                                }
                            )
                        }
                    }

                    Text("Accessible Branches:", style = MaterialTheme.typography.labelMedium, color = CraveGold)
                    availableBranches.forEach { br ->
                        val isChecked = br.branchId in checkedBranches || br.branchId == primaryBranch?.branchId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (br.branchId !in checkedBranches) checkedBranches.add(br.branchId)
                                    } else if (br.branchId != primaryBranch?.branchId) {
                                        checkedBranches.remove(br.branchId)
                                    }
                                }
                            )
                            Text("${br.code} — ${br.name}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Assign as Branch Manager", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = setAsManager, onCheckedChange = { setAsManager = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedStaff != null && primaryBranch != null,
                onClick = {
                    val st = selectedStaff ?: return@Button
                    val pb = primaryBranch ?: return@Button
                    onAssign(st.staffId, pb.branchId, checkedBranches.toList(), setAsManager)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) {
                Text("Assign Staff")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
