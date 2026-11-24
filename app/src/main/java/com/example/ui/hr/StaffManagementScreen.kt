package com.example.ui.hr

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.BranchSummary
import com.example.domain.model.EmploymentStatus
import com.example.domain.model.HrPermissionPolicy
import com.example.domain.model.SalaryType
import com.example.domain.model.StaffDocType
import com.example.domain.model.StaffDocumentRecord
import com.example.domain.model.StaffProfileRecord
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.domain.model.centsToCurrency
import com.example.printing.Phase4ReportExporter
import com.example.printing.Phase5ReportExporter
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
import com.example.ui.theme.StatusSkyBg
import com.example.ui.viewmodel.StaffUiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffManagementScreen(
    session: UserSession,
    uiState: StaffUiState,
    onOpenDrawer: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onSelectStatusFilter: (EmploymentStatus?) -> Unit,
    onSelectDepartmentFilter: (String?) -> Unit,
    onCopyDocumentUri: suspend (Uri) -> Result<String>,
    onSaveStaff: (
        staffId: String?,
        employeeCode: String,
        fullName: String,
        phone: String,
        address: String,
        jobTitle: String,
        department: String,
        assignedRole: UserRole,
        accessibleBranchIds: List<String>,
        joiningDateMillis: Long,
        employmentStatus: EmploymentStatus,
        salaryType: SalaryType,
        baseSalaryCents: Long?,
        defaultAllowanceCents: Long?,
        overtimeMultiplier: Double,
        annualLeaveDays: Int,
        sickLeaveDays: Int,
        casualLeaveDays: Int,
        attendancePin: String?,
        notes: String
    ) -> Unit,
    onSetActiveStatus: (staffId: String, active: Boolean, reason: String) -> Unit,
    onAddDocument: (staffId: String, title: String, docType: StaffDocType, fileUri: String?, notes: String) -> Unit,
    onDeleteDocument: (docId: String) -> Unit,
    onUpdateHrPolicy: (
        targetRole: UserRole,
        canManageStaff: Boolean,
        canViewConfidentialSalary: Boolean,
        canManageShifts: Boolean,
        canApproveAttendanceCorrections: Boolean,
        canApproveLeave: Boolean,
        canGeneratePayroll: Boolean,
        canApproveAndPayPayroll: Boolean,
        canExportHrReports: Boolean
    ) -> Unit,
    onReportMessage: (String, Boolean) -> Unit,
    onClearFeedback: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val eff = uiState.effectivePolicy
    val canManageStaff = eff?.canManageStaff ?: session.capabilities.canManageStaff
    val canViewSalary = eff?.canViewConfidentialSalary ?: session.capabilities.canViewConfidentialSalary
    val canExport = eff?.canExportHrReports ?: session.capabilities.canExportReports

    var showEditor by remember { mutableStateOf(false) }
    var editingStaff by remember { mutableStateOf<StaffProfileRecord?>(null) }
    var docsForStaff by remember { mutableStateOf<StaffProfileRecord?>(null) }
    var deactivateTarget by remember { mutableStateOf<StaffProfileRecord?>(null) }
    var showHrPolicyDialog by remember { mutableStateOf(false) }
    var exportMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    fun exportStaffReport(format: ReportExportFormat) {
        Phase5ReportExporter.exportStaffStatusReport(
            context = context,
            branchName = session.branchName,
            staffList = uiState.staffList,
            includeSalaryColumns = canViewSalary,
            format = format
        ).fold(
            onSuccess = { file ->
                Phase4ReportExporter.shareReportFile(context, file, format.mimeType, "Share Staff Directory (${format.label})")
                onReportMessage("Exported Staff Report (${format.label}).", false)
            },
            onFailure = { onReportMessage(it.message ?: "Export failed.", true) }
        )
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
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("staff_drawer_button")
                    ) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = CraveGoldBright)
                    }
                },
                title = {
                    Column {
                        Text("Staff Directory & HR", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                        Text(session.branchName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (session.capabilities.canConfigureHrPermissions) {
                        IconButton(
                            onClick = { showHrPolicyDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("hr_permissions_button")
                        ) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Configure HR Role Permissions", tint = CraveGoldBright)
                        }
                    }
                    if (canExport) {
                        Box {
                            IconButton(
                                onClick = { exportMenuOpen = true },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("staff_export_button")
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = "Export Staff Report", tint = CraveGoldBright)
                            }
                            DropdownMenu(expanded = exportMenuOpen, onDismissRequest = { exportMenuOpen = false }) {
                                listOf(ReportExportFormat.PDF, ReportExportFormat.CSV).forEach { fmt ->
                                    DropdownMenuItem(
                                        text = { Text("Export ${fmt.label}") },
                                        onClick = {
                                            exportMenuOpen = false
                                            exportStaffReport(fmt)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (canManageStaff) {
                FloatingActionButton(
                    onClick = {
                        editingStaff = null
                        showEditor = true
                    },
                    containerColor = CraveGold,
                    contentColor = OnCraveGold,
                    modifier = Modifier.testTag("add_staff_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Staff Member")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search & Summary Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Search by Employee ID, name, job title, phone") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("staff_search_input")
                )

                val departments = remember(uiState.staffList) {
                    uiState.staffList.map { it.department }.distinct().sorted()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedStatusFilter == null,
                        onClick = { onSelectStatusFilter(null) },
                        label = { Text("All (${uiState.staffList.size})") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                    )
                    EmploymentStatus.entries.forEach { st ->
                        FilterChip(
                            selected = uiState.selectedStatusFilter == st,
                            onClick = { onSelectStatusFilter(if (uiState.selectedStatusFilter == st) null else st) },
                            label = { Text(st.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                        )
                    }
                    departments.forEach { dept ->
                        FilterChip(
                            selected = uiState.selectedDepartmentFilter == dept,
                            onClick = { onSelectDepartmentFilter(if (uiState.selectedDepartmentFilter == dept) null else dept) },
                            label = { Text(dept) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = StatusSkyBg, selectedLabelColor = StatusSky)
                        )
                    }
                }
            }

            if (uiState.filteredStaff.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Badge,
                            contentDescription = null,
                            tint = CraveGold.copy(alpha = 0.7f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (uiState.staffList.isEmpty()) "No Staff Profiles Recorded Yet" else "No matching employees",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (canManageStaff) {
                                "Tap + to register restaurant employees with unique Employee IDs, roles, branches, and salary rules."
                            } else {
                                "Authorized HR or Branch Managers can register and update employee profiles."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.filteredStaff, key = { it.staffId }) { staff ->
                        val docCount = uiState.documents.count { it.staffId == staff.staffId }
                        StaffProfileCard(
                            staff = staff,
                            documentCount = docCount,
                            canManageStaff = canManageStaff,
                            canViewSalary = canViewSalary,
                            onEdit = {
                                editingStaff = staff
                                showEditor = true
                            },
                            onDocuments = { docsForStaff = staff },
                            onToggleActive = {
                                if (staff.isActive) {
                                    deactivateTarget = staff
                                } else {
                                    onSetActiveStatus(staff.staffId, true, "Reactivated from directory")
                                }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showEditor) {
        StaffEditorDialog(
            existing = editingStaff,
            branches = uiState.branches,
            defaultBranchId = session.branchId,
            canViewSalary = canViewSalary,
            onDismiss = { showEditor = false },
            onSave = { code, name, phone, addr, title, dept, role, branchesSel, joinMs, status, salType, baseCents, allowCents, otMult, ann, sick, cas, pin, notes ->
                onSaveStaff(
                    editingStaff?.staffId,
                    code,
                    name,
                    phone,
                    addr,
                    title,
                    dept,
                    role,
                    branchesSel,
                    joinMs,
                    status,
                    salType,
                    baseCents,
                    allowCents,
                    otMult,
                    ann,
                    sick,
                    cas,
                    pin,
                    notes
                )
                showEditor = false
            }
        )
    }

    docsForStaff?.let { targetStaff ->
        val docs = uiState.documents.filter { it.staffId == targetStaff.staffId }
        StaffDocumentsDialog(
            staff = targetStaff,
            documents = docs,
            canManageStaff = canManageStaff,
            onDismiss = { docsForStaff = null },
            onCopyDocumentUri = onCopyDocumentUri,
            onAddDocument = { title, docType, uri, notes ->
                onAddDocument(targetStaff.staffId, title, docType, uri, notes)
            },
            onDeleteDocument = onDeleteDocument,
            onReportError = { msg -> onReportMessage(msg, true) }
        )
    }

    deactivateTarget?.let { target ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { deactivateTarget = null },
            containerColor = ObsidianSurfaceElevated,
            title = { Text("Deactivate ${target.fullName}?", color = CraveGoldBright) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Deactivating (${target.employeeCode}) prevents new check-ins and leave requests while preserving payroll & attendance history.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetActiveStatus(target.staffId, false, reason)
                        deactivateTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson)
                ) { Text("Deactivate") }
            },
            dismissButton = {
                TextButton(onClick = { deactivateTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (showHrPolicyDialog) {
        HrPermissionsPolicyDialog(
            policies = uiState.hrPolicies,
            onDismiss = { showHrPolicyDialog = false },
            onSavePolicy = onUpdateHrPolicy
        )
    }
}

@Composable
private fun StaffProfileCard(
    staff: StaffProfileRecord,
    documentCount: Int,
    canManageStaff: Boolean,
    canViewSalary: Boolean,
    onEdit: () -> Unit,
    onDocuments: () -> Unit,
    onToggleActive: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val statusColors = when (staff.employmentStatus) {
        EmploymentStatus.ACTIVE -> StatusEmeraldBg to StatusEmerald
        EmploymentStatus.ON_LEAVE -> StatusAmberBg to StatusAmber
        EmploymentStatus.INACTIVE -> StatusCrimsonBg to StatusCrimson
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, if (staff.isActive) ObsidianBorder else StatusCrimson.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().testTag("staff_card_${staff.employeeCode}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusPill(
                            text = staff.employeeCode,
                            containerColor = CraveGoldContainer,
                            contentColor = CraveGoldBright
                        )
                        Text(
                            text = staff.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "${staff.jobTitle} • ${staff.department} • Role: ${staff.assignedRole.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = staff.employmentStatus.label.uppercase(),
                    containerColor = statusColors.first,
                    contentColor = statusColors.second
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Phone: ${staff.phone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Joined: ${dateFmt.format(Date(staff.joiningDateMillis))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canViewSalary && staff.baseSalaryCents != null) {
                    StatusPill(
                        text = "${staff.salaryType.label}: ${centsToCurrency(staff.baseSalaryCents)} (${staff.overtimeMultiplier}x OT)",
                        containerColor = StatusEmeraldBg,
                        contentColor = StatusEmerald
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Salary Confidential",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusAmber
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Leave A:${staff.annualLeaveBalanceDays} S:${staff.sickLeaveBalanceDays} C:${staff.casualLeaveBalanceDays}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (staff.notes.isNotBlank()) {
                Text(
                    text = staff.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = ObsidianBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDocuments,
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Docs ($documentCount)")
                }
                if (canManageStaff) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit")
                    }
                    OutlinedButton(
                        onClick = onToggleActive,
                        border = BorderStroke(1.dp, if (staff.isActive) StatusCrimson.copy(alpha = 0.6f) else StatusEmerald.copy(alpha = 0.6f)),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            if (staff.isActive) Icons.Filled.PersonOff else Icons.Filled.PersonOutline,
                            contentDescription = if (staff.isActive) "Deactivate" else "Reactivate",
                            tint = if (staff.isActive) StatusCrimson else StatusEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffEditorDialog(
    existing: StaffProfileRecord?,
    branches: List<BranchSummary>,
    defaultBranchId: String,
    canViewSalary: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        employeeCode: String,
        fullName: String,
        phone: String,
        address: String,
        jobTitle: String,
        department: String,
        assignedRole: UserRole,
        accessibleBranchIds: List<String>,
        joiningDateMillis: Long,
        employmentStatus: EmploymentStatus,
        salaryType: SalaryType,
        baseSalaryCents: Long?,
        defaultAllowanceCents: Long?,
        overtimeMultiplier: Double,
        annualLeaveDays: Int,
        sickLeaveDays: Int,
        casualLeaveDays: Int,
        attendancePin: String?,
        notes: String
    ) -> Unit
) {
    var employeeCode by remember { mutableStateOf(existing?.employeeCode ?: "") }
    var fullName by remember { mutableStateOf(existing?.fullName ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var jobTitle by remember { mutableStateOf(existing?.jobTitle ?: "") }
    var assignedRole by remember { mutableStateOf(existing?.assignedRole ?: UserRole.CASHIER) }
    var department by remember { mutableStateOf(existing?.department ?: assignedRole.department) }
    var selectedBranches by remember {
        mutableStateOf(
            existing?.accessibleBranchIds?.ifEmpty { listOf(defaultBranchId) } ?: listOf(defaultBranchId)
        )
    }
    var employmentStatus by remember { mutableStateOf(existing?.employmentStatus ?: EmploymentStatus.ACTIVE) }
    var salaryType by remember { mutableStateOf(existing?.salaryType ?: SalaryType.MONTHLY) }
    var baseSalaryStr by remember {
        mutableStateOf(existing?.baseSalaryCents?.let { "%.2f".format(Locale.US, it / 100.0) } ?: "")
    }
    var allowanceStr by remember {
        mutableStateOf(existing?.defaultAllowanceCents?.let { "%.2f".format(Locale.US, it / 100.0) } ?: "0.00")
    }
    var otMultiplierStr by remember {
        mutableStateOf((existing?.overtimeMultiplier ?: 1.5).toString())
    }
    var annualLeaveStr by remember { mutableStateOf((existing?.annualLeaveBalanceDays ?: 14).toString()) }
    var sickLeaveStr by remember { mutableStateOf((existing?.sickLeaveBalanceDays ?: 10).toString()) }
    var casualLeaveStr by remember { mutableStateOf((existing?.casualLeaveBalanceDays ?: 7).toString()) }
    var pinStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var roleMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = {
            Text(
                text = if (existing == null) "Add Staff Profile" else "Edit ${existing.employeeCode}",
                color = CraveGoldBright
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = employeeCode,
                    onValueChange = { employeeCode = it.uppercase(Locale.US) },
                    label = { Text("Employee ID * (e.g. EMP-101)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("staff_code_input")
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("staff_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Residential Address") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = jobTitle,
                    onValueChange = { jobTitle = it },
                    label = { Text("Job Title * (e.g. Sous Chef)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text("Department") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Role Picker
                Box {
                    OutlinedButton(
                        onClick = { roleMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Assigned Role: ${assignedRole.displayName}")
                    }
                    DropdownMenu(expanded = roleMenuOpen, onDismissRequest = { roleMenuOpen = false }) {
                        UserRole.entries.forEach { role ->
                            DropdownMenuItem(
                                text = { Text("${role.displayName} (${role.department})") },
                                onClick = {
                                    assignedRole = role
                                    if (department.isBlank() || department == existing?.department) {
                                        department = role.department
                                    }
                                    roleMenuOpen = false
                                }
                            )
                        }
                    }
                }

                // Multi-branch access
                if (branches.isNotEmpty()) {
                    Text("Branch Access", style = MaterialTheme.typography.labelMedium, color = CraveGoldBright)
                    branches.forEach { b ->
                        val checked = b.branchId in selectedBranches
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedBranches = if (isChecked) {
                                        (selectedBranches + b.branchId).distinct()
                                    } else {
                                        (selectedBranches - b.branchId).ifEmpty { listOf(b.branchId) }
                                    }
                                }
                            )
                            Text(b.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Employment Status
                Text("Employment Status", style = MaterialTheme.typography.labelMedium, color = CraveGoldBright)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EmploymentStatus.entries.forEach { st ->
                        FilterChip(
                            selected = employmentStatus == st,
                            onClick = { employmentStatus = st },
                            label = { Text(st.label) }
                        )
                    }
                }

                // Salary Type & Confidential Salary Inputs
                Text("Salary Type", style = MaterialTheme.typography.labelMedium, color = CraveGoldBright)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SalaryType.entries.forEach { st ->
                        FilterChip(
                            selected = salaryType == st,
                            onClick = { salaryType = st },
                            label = { Text(st.label) }
                        )
                    }
                }

                if (canViewSalary) {
                    OutlinedTextField(
                        value = baseSalaryStr,
                        onValueChange = { baseSalaryStr = it },
                        label = { Text("Base ${salaryType.label} Rate (PKR)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("staff_salary_input")
                    )
                    OutlinedTextField(
                        value = allowanceStr,
                        onValueChange = { allowanceStr = it },
                        label = { Text("Monthly Allowance (PKR)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        "Confidential salary figures are restricted for your role.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber
                    )
                }

                OutlinedTextField(
                    value = otMultiplierStr,
                    onValueChange = { otMultiplierStr = it },
                    label = { Text("Overtime Multiplier (e.g. 1.5)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = annualLeaveStr,
                        onValueChange = { annualLeaveStr = it },
                        label = { Text("Annual") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sickLeaveStr,
                        onValueChange = { sickLeaveStr = it },
                        label = { Text("Sick") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = casualLeaveStr,
                        onValueChange = { casualLeaveStr = it },
                        label = { Text("Casual") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = pinStr,
                    onValueChange = { pinStr = it.filter { ch -> ch.isDigit() }.take(8) },
                    label = {
                        Text(
                            if (existing?.hasAttendancePin == true) "New Attendance PIN (leave blank to keep)"
                            else "Attendance PIN (4–8 digits)"
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("staff_pin_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("HR Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val baseCents = if (canViewSalary) {
                        ((baseSalaryStr.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
                    } else null
                    val allowCents = if (canViewSalary) {
                        ((allowanceStr.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
                    } else null
                    onSave(
                        employeeCode,
                        fullName,
                        phone,
                        address,
                        jobTitle,
                        department,
                        assignedRole,
                        selectedBranches,
                        existing?.joiningDateMillis ?: System.currentTimeMillis(),
                        employmentStatus,
                        salaryType,
                        baseCents,
                        allowCents,
                        otMultiplierStr.toDoubleOrNull() ?: 1.5,
                        annualLeaveStr.toIntOrNull() ?: 14,
                        sickLeaveStr.toIntOrNull() ?: 10,
                        casualLeaveStr.toIntOrNull() ?: 7,
                        pinStr.ifBlank { null },
                        notes
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                modifier = Modifier.testTag("save_staff_button")
            ) {
                Text("Save Staff")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StaffDocumentsDialog(
    staff: StaffProfileRecord,
    documents: List<StaffDocumentRecord>,
    canManageStaff: Boolean,
    onDismiss: () -> Unit,
    onCopyDocumentUri: suspend (Uri) -> Result<String>,
    onAddDocument: (title: String, docType: StaffDocType, fileUri: String?, notes: String) -> Unit,
    onDeleteDocument: (docId: String) -> Unit,
    onReportError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var docType by remember { mutableStateOf(StaffDocType.ID_CARD) }
    var pickedPath by remember { mutableStateOf<String?>(null) }
    var docNotes by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                onCopyDocumentUri(uri).fold(
                    onSuccess = { path -> pickedPath = path },
                    onFailure = { err -> onReportError(err.message ?: "Unable to copy document") }
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("${staff.fullName} — Documents", color = CraveGoldBright) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (documents.isEmpty()) {
                    Text("No documents archived yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    documents.forEach { d ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(d.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${d.docType.label} • By ${d.uploadedByName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!d.fileUri.isNullOrBlank()) {
                                        Text("Stored securely in app vault", style = MaterialTheme.typography.labelSmall, color = StatusEmerald)
                                    }
                                }
                                if (canManageStaff) {
                                    IconButton(onClick = { onDeleteDocument(d.docId) }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete document", tint = StatusCrimson)
                                    }
                                }
                            }
                        }
                    }
                }

                if (canManageStaff) {
                    HorizontalDivider(color = ObsidianBorder)
                    Text("Attach New Document", style = MaterialTheme.typography.labelMedium, color = CraveGoldBright)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Document Title *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StaffDocType.entries.forEach { dt ->
                            FilterChip(
                                selected = docType == dt,
                                onClick = { docType = dt },
                                label = { Text(dt.label) }
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (pickedPath != null) "File Attached (Ready)" else "Pick File (PDF / Image)")
                    }
                    OutlinedTextField(
                        value = docNotes,
                        onValueChange = { docNotes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onAddDocument(title, docType, pickedPath, docNotes)
                            title = ""
                            pickedPath = null
                            docNotes = ""
                        },
                        enabled = title.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Document")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun HrPermissionsPolicyDialog(
    policies: List<HrPermissionPolicy>,
    onDismiss: () -> Unit,
    onSavePolicy: (
        targetRole: UserRole,
        canManageStaff: Boolean,
        canViewConfidentialSalary: Boolean,
        canManageShifts: Boolean,
        canApproveAttendanceCorrections: Boolean,
        canApproveLeave: Boolean,
        canGeneratePayroll: Boolean,
        canApproveAndPayPayroll: Boolean,
        canExportHrReports: Boolean
    ) -> Unit
) {
    val configurableRoles = remember {
        listOf(
            UserRole.BRANCH_MANAGER,
            UserRole.HR,
            UserRole.INVENTORY_MANAGER,
            UserRole.PURCHASE_STAFF,
            UserRole.CASHIER,
            UserRole.KITCHEN_STAFF
        )
    }
    var selectedRole by remember { mutableStateOf(UserRole.BRANCH_MANAGER) }
    val currentPolicy = policies.firstOrNull { it.roleCode == selectedRole.code }

    var manageStaff by remember(selectedRole, currentPolicy) { mutableStateOf(currentPolicy?.canManageStaff ?: false) }
    var viewSalary by remember(selectedRole, currentPolicy) { mutableStateOf(currentPolicy?.canViewConfidentialSalary ?: false) }
    var manageShifts by remember(selectedRole, currentPolicy) { mutableStateOf(currentPolicy?.canManageShifts ?: false) }
    var approveCorrections by remember(selectedRole, currentPolicy) { mutableStateOf(currentPolicy?.canApproveAttendanceCorrections ?: false) }
    var approveLeave by remember(selectedRole, currentPolicy) { mutableStateOf(currentPolicy?.canApproveLeave ?: false) }
    var genPayroll by remember(selectedRole, currentPolicy) { mutableStateOf(currentPolicy?.canGeneratePayroll ?: false) }
    var payPayroll by remember(selectedRole, currentPolicy) { mutableStateOf(currentPolicy?.canApproveAndPayPayroll ?: false) }
    var exportReports by remember(selectedRole, currentPolicy) { mutableStateOf(currentPolicy?.canExportHrReports ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("HR & Salary Permission Controls", color = CraveGoldBright) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Owner & Super Admin control which roles can access confidential salaries, approve leave, or disburse payroll.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    configurableRoles.forEach { role ->
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            label = { Text(role.displayName) }
                        )
                    }
                }
                PolicyToggleRow("Manage Staff Records & Docs", manageStaff) { manageStaff = it }
                PolicyToggleRow("View Confidential Salary Rates", viewSalary) { viewSalary = it }
                PolicyToggleRow("Create / Edit / Assign Shifts", manageShifts) { manageShifts = it }
                PolicyToggleRow("Approve Attendance Corrections", approveCorrections) { approveCorrections = it }
                PolicyToggleRow("Approve / Reject Leave Requests", approveLeave) { approveLeave = it }
                PolicyToggleRow("Generate Payroll Drafts", genPayroll) { genPayroll = it }
                PolicyToggleRow("Approve & Mark Payroll Paid", payPayroll) { payPayroll = it }
                PolicyToggleRow("Export HR & Payroll Reports", exportReports) { exportReports = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSavePolicy(
                        selectedRole,
                        manageStaff,
                        viewSalary,
                        manageShifts,
                        approveCorrections,
                        approveLeave,
                        genPayroll,
                        payPayroll,
                        exportReports
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Save Policy") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun PolicyToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
