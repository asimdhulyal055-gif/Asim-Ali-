package com.example.ui.hr

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.LeaveRequestRecord
import com.example.domain.model.LeaveStatus
import com.example.domain.model.LeaveType
import com.example.domain.model.StaffProfileRecord
import com.example.domain.model.UserSession
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
import com.example.ui.viewmodel.LeaveUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveManagementScreen(
    session: UserSession,
    uiState: LeaveUiState,
    onOpenDrawer: () -> Unit,
    onSelectStatusFilter: (LeaveStatus?) -> Unit,
    onSubmitLeave: (staffId: String, type: LeaveType, startDateMs: Long, endDateMs: Long, reason: String) -> Unit,
    onReviewLeave: (leaveId: String, approve: Boolean, notes: String) -> Unit,
    onReportMessage: (String, Boolean) -> Unit,
    onClearFeedback: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val eff = uiState.effectivePolicy
    val canApproveLeave = eff?.canApproveLeave ?: session.capabilities.canApproveLeave
    val canExport = eff?.canExportHrReports ?: session.capabilities.canExportReports

    var showRequestDialog by remember { mutableStateOf(false) }
    var exportMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    fun exportLeaves(format: ReportExportFormat) {
        Phase5ReportExporter.exportLeaveReport(
            context = context,
            branchName = session.branchName,
            leaves = uiState.requests,
            format = format
        ).fold(
            onSuccess = { file ->
                Phase4ReportExporter.shareReportFile(context, file, format.mimeType, "Share Leave Report (${format.label})")
                onReportMessage("Exported Leave Report (${format.label}).", false)
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
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = CraveGoldBright)
                    }
                },
                title = {
                    Column {
                        Text("Leave Management", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                        Text(session.branchName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (canExport) {
                        Box {
                            IconButton(onClick = { exportMenuOpen = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Filled.Download, contentDescription = "Export Leave Report", tint = CraveGoldBright)
                            }
                            DropdownMenu(expanded = exportMenuOpen, onDismissRequest = { exportMenuOpen = false }) {
                                listOf(ReportExportFormat.PDF, ReportExportFormat.CSV).forEach { fmt ->
                                    DropdownMenuItem(
                                        text = { Text("Export ${fmt.label}") },
                                        onClick = {
                                            exportMenuOpen = false
                                            exportLeaves(fmt)
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
            if (uiState.staffList.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showRequestDialog = true },
                    containerColor = CraveGold,
                    contentColor = OnCraveGold,
                    modifier = Modifier.testTag("request_leave_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Submit Leave Request")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedStatusFilter == null,
                    onClick = { onSelectStatusFilter(null) },
                    label = { Text("All (${uiState.requests.size})") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                )
                LeaveStatus.entries.forEach { st ->
                    FilterChip(
                        selected = uiState.selectedStatusFilter == st,
                        onClick = { onSelectStatusFilter(if (uiState.selectedStatusFilter == st) null else st) },
                        label = { Text(st.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                    )
                }
            }

            if (uiState.filteredRequests.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = CraveGold.copy(alpha = 0.7f), modifier = Modifier.size(48.dp))
                        Text("No Leave Requests", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Approved leave automatically marks employee Attendance as LEAVE and updates paid/unpaid leave balances.",
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
                    items(uiState.filteredRequests, key = { it.leaveId }) { req ->
                        LeaveRequestCard(
                            request = req,
                            canApprove = canApproveLeave,
                            onApprove = { onReviewLeave(req.leaveId, true, "Approved") },
                            onReject = { onReviewLeave(req.leaveId, false, "Declined by manager") }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showRequestDialog) {
        LeaveRequestDialog(
            staffList = uiState.staffList,
            onDismiss = { showRequestDialog = false },
            onSubmit = { staffId, type, startMs, endMs, reason ->
                onSubmitLeave(staffId, type, startMs, endMs, reason)
                showRequestDialog = false
            }
        )
    }
}

@Composable
private fun LeaveRequestCard(
    request: LeaveRequestRecord,
    canApprove: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val statusColors = when (request.status) {
        LeaveStatus.PENDING -> StatusAmberBg to StatusAmber
        LeaveStatus.APPROVED -> StatusEmeraldBg to StatusEmerald
        LeaveStatus.REJECTED -> StatusCrimsonBg to StatusCrimson
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${request.employeeCode} • ${request.staffName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${request.leaveType.label} (${if (request.leaveType.isPaid) "Paid" else "Unpaid"}) • ${request.daysCount} day(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = CraveGoldBright
                    )
                }
                StatusPill(request.status.label.uppercase(), statusColors.first, statusColors.second)
            }

            Text(
                "${dateFmt.format(Date(request.startDateMillis))} → ${dateFmt.format(Date(request.endDateMillis))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(request.reason, style = MaterialTheme.typography.bodySmall)

            if (request.status == LeaveStatus.APPROVED) {
                StatusPill("SYNCED TO ATTENDANCE (LEAVE)", StatusSkyBg, StatusSky)
            }
            if (!request.reviewedByName.isNullOrBlank()) {
                Text("Reviewed by ${request.reviewedByName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (canApprove && request.status == LeaveStatus.PENDING) {
                HorizontalDivider(color = ObsidianBorder)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusEmerald, contentColor = ObsidianBg),
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Approve")
                    }
                    OutlinedButton(
                        onClick = onReject,
                        border = BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.7f)),
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = StatusCrimson, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reject", color = StatusCrimson)
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaveRequestDialog(
    staffList: List<StaffProfileRecord>,
    onDismiss: () -> Unit,
    onSubmit: (staffId: String, type: LeaveType, startMs: Long, endMs: Long, reason: String) -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    var selectedStaff by remember { mutableStateOf(staffList.firstOrNull()) }
    var staffMenuOpen by remember { mutableStateOf(false) }
    var leaveType by remember { mutableStateOf(LeaveType.ANNUAL) }
    var startDateStr by remember { mutableStateOf(dateFmt.format(Date())) }
    var endDateStr by remember { mutableStateOf(dateFmt.format(Date())) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Submit Leave Request", color = CraveGoldBright) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { staffMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedStaff?.let { "${it.employeeCode} — ${it.fullName}" } ?: "Select Staff")
                    }
                    DropdownMenu(expanded = staffMenuOpen, onDismissRequest = { staffMenuOpen = false }) {
                        staffList.forEach { st ->
                            DropdownMenuItem(
                                text = { Text("${st.employeeCode} — ${st.fullName}") },
                                onClick = {
                                    selectedStaff = st
                                    staffMenuOpen = false
                                }
                            )
                        }
                    }
                }
                selectedStaff?.let { st ->
                    Text(
                        "Balances — Annual: ${st.annualLeaveBalanceDays}d • Sick: ${st.sickLeaveBalanceDays}d • Casual: ${st.casualLeaveBalanceDays}d",
                        style = MaterialTheme.typography.labelSmall,
                        color = CraveGoldBright
                    )
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LeaveType.entries.forEach { lt ->
                        FilterChip(selected = leaveType == lt, onClick = { leaveType = lt }, label = { Text(lt.label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDateStr,
                        onValueChange = { startDateStr = it },
                        label = { Text("From (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endDateStr,
                        onValueChange = { endDateStr = it },
                        label = { Text("To (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason *") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val st = selectedStaff ?: return@Button
                    val sMs = runCatching { dateFmt.parse(startDateStr.trim())?.time }.getOrNull() ?: System.currentTimeMillis()
                    val eMs = runCatching { dateFmt.parse(endDateStr.trim())?.time }.getOrNull() ?: sMs
                    onSubmit(st.staffId, leaveType, sMs, eMs, reason)
                },
                enabled = selectedStaff != null && reason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Submit Request") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
