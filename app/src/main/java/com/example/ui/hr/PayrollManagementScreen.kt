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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.PayrollRecord
import com.example.domain.model.PayrollStatus
import com.example.domain.model.ShiftCalculator
import com.example.domain.model.StaffProfileRecord
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
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusEmeraldBg
import com.example.ui.theme.StatusSky
import com.example.ui.theme.StatusSkyBg
import com.example.ui.viewmodel.PayrollUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayrollManagementScreen(
    session: UserSession,
    uiState: PayrollUiState,
    onOpenDrawer: () -> Unit,
    onSelectStatusFilter: (PayrollStatus?) -> Unit,
    onSelectStaffFilter: (String?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onGeneratePayroll: (
        staffId: String,
        periodKey: String,
        periodLabel: String,
        periodStartMillis: Long,
        periodEndMillis: Long,
        allowancesCentsOverride: Long?,
        bonusesCents: Long,
        deductionsCents: Long,
        deductionReason: String
    ) -> Unit,
    onApprovePayroll: (String) -> Unit,
    onMarkPaid: (payrollId: String, paymentDateMs: Long, method: String, reference: String) -> Unit,
    onReportMessage: (String, Boolean) -> Unit,
    onClearFeedback: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val eff = uiState.effectivePolicy
    val canViewSalary = eff?.canViewConfidentialSalary ?: session.capabilities.canViewConfidentialSalary
    val canGenerate = (eff?.canGeneratePayroll ?: session.capabilities.canGeneratePayroll) && canViewSalary
    val canApproveAndPay = (eff?.canApproveAndPayPayroll ?: session.capabilities.canApproveAndPayPayroll) && canViewSalary
    val canExport = (eff?.canExportHrReports ?: session.capabilities.canExportReports) && canViewSalary

    var showGenerateDialog by remember { mutableStateOf(false) }
    var payDialogTarget by remember { mutableStateOf<PayrollRecord?>(null) }
    var exportMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    fun exportPayroll(format: ReportExportFormat) {
        Phase5ReportExporter.exportPayrollReport(
            context = context,
            session = session,
            hasConfidentialAccess = canViewSalary,
            payrollList = uiState.filteredRecords,
            format = format
        ).fold(
            onSuccess = { file ->
                Phase4ReportExporter.shareReportFile(context, file, format.mimeType, "Share Payroll Summary (${format.label})")
                onReportMessage("Exported Payroll Summary (${format.label}).", false)
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
                        Text("Payroll & Disbursements", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                        Text(session.branchName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (canExport) {
                        Box {
                            IconButton(onClick = { exportMenuOpen = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Filled.Download, contentDescription = "Export Payroll Report", tint = CraveGoldBright)
                            }
                            DropdownMenu(expanded = exportMenuOpen, onDismissRequest = { exportMenuOpen = false }) {
                                listOf(ReportExportFormat.PDF, ReportExportFormat.CSV).forEach { fmt ->
                                    DropdownMenuItem(
                                        text = { Text("Export ${fmt.label}") },
                                        onClick = {
                                            exportMenuOpen = false
                                            exportPayroll(fmt)
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
            if (canGenerate && uiState.staffList.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showGenerateDialog = true },
                    containerColor = CraveGold,
                    contentColor = OnCraveGold,
                    modifier = Modifier.testTag("generate_payroll_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Generate Payroll")
                }
            }
        }
    ) { padding ->
        if (!canViewSalary) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(40.dp))
                        Text("Confidential Payroll Restricted", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                        Text(
                            "Your role (${session.role.displayName}) is not authorized to view confidential staff salaries or generate payroll. Contact Owner/Super Admin to review HR Permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchChanged,
                        placeholder = { Text("Search payroll by employee or period") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = uiState.selectedStatusFilter == null,
                            onClick = { onSelectStatusFilter(null) },
                            label = { Text("All (${uiState.payrollRecords.size})") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                        )
                        PayrollStatus.entries.forEach { st ->
                            FilterChip(
                                selected = uiState.selectedStatusFilter == st,
                                onClick = { onSelectStatusFilter(if (uiState.selectedStatusFilter == st) null else st) },
                                label = { Text(st.label) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                            )
                        }
                    }
                    if (uiState.staffList.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = uiState.selectedStaffFilterId == null,
                                onClick = { onSelectStaffFilter(null) },
                                label = { Text("All Staff History") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = StatusSkyBg, selectedLabelColor = StatusSky)
                            )
                            uiState.staffList.forEach { st ->
                                FilterChip(
                                    selected = uiState.selectedStaffFilterId == st.staffId,
                                    onClick = { onSelectStaffFilter(if (uiState.selectedStaffFilterId == st.staffId) null else st.staffId) },
                                    label = { Text(st.employeeCode) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = StatusSkyBg, selectedLabelColor = StatusSky)
                                )
                            }
                        }
                    }
                }

                if (uiState.filteredRecords.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Payments, contentDescription = null, tint = CraveGold.copy(alpha = 0.7f), modifier = Modifier.size(48.dp))
                            Text("No Payroll Runs Recorded", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tap + to calculate gross, overtime, allowances, bonuses, authorized deductions, and net salary from Attendance & Leave records.",
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
                        items(uiState.filteredRecords, key = { it.payrollId }) { pay ->
                            PayrollRecordCard(
                                record = pay,
                                canApproveAndPay = canApproveAndPay,
                                onApprove = { onApprovePayroll(pay.payrollId) },
                                onMarkPaid = { payDialogTarget = pay }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(72.dp)) }
                    }
                }
            }
        }
    }

    if (showGenerateDialog) {
        GeneratePayrollDialog(
            staffList = uiState.staffList.filter { it.isActive },
            onDismiss = { showGenerateDialog = false },
            onGenerate = { staffId, key, label, startMs, endMs, allow, bonus, ded, reason ->
                onGeneratePayroll(staffId, key, label, startMs, endMs, allow, bonus, ded, reason)
                showGenerateDialog = false
            }
        )
    }

    payDialogTarget?.let { target ->
        MarkPayrollPaidDialog(
            record = target,
            onDismiss = { payDialogTarget = null },
            onConfirm = { payDateMs, method, ref ->
                onMarkPaid(target.payrollId, payDateMs, method, ref)
                payDialogTarget = null
            }
        )
    }
}

@Composable
private fun PayrollRecordCard(
    record: PayrollRecord,
    canApproveAndPay: Boolean,
    onApprove: () -> Unit,
    onMarkPaid: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val statusColors = when (record.status) {
        PayrollStatus.DRAFT -> StatusAmberBg to StatusAmber
        PayrollStatus.APPROVED -> StatusSkyBg to StatusSky
        PayrollStatus.PAID -> StatusEmeraldBg to StatusEmerald
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${record.employeeCode} • ${record.staffName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${record.periodLabel} (${record.salaryType.label})", style = MaterialTheme.typography.bodySmall, color = CraveGoldBright)
                }
                StatusPill(record.status.label.uppercase(), statusColors.first, statusColors.second)
            }

            Text(
                "Present: ${record.daysPresent} • Half: ${record.daysHalf} • Paid Leave: ${record.paidLeaveDays} • Holiday: ${record.holidayDays} • Absent: ${record.absentDays}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Regular: ${ShiftCalculator.formatMinutesAsHours(record.regularMinutes)} • OT: ${ShiftCalculator.formatMinutesAsHours(record.overtimeMinutes)} (${centsToCurrency(record.overtimePayCents)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Base: ${centsToCurrency(record.baseEarningsCents)}", style = MaterialTheme.typography.bodySmall)
                Text("Allow+Bonus: ${centsToCurrency(record.allowancesCents + record.bonusesCents)}", style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Deductions: -${centsToCurrency(record.deductionsCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (record.deductionsCents > 0) StatusCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Gross: ${centsToCurrency(record.grossSalaryCents)}", style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(color = ObsidianBorder)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Net Payable", style = MaterialTheme.typography.labelLarge)
                Text(centsToCurrency(record.netSalaryCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CraveGoldBright)
            }

            if (record.deductionsCents > 0 && record.deductionReason.isNotBlank()) {
                Text("Deduction Reason: ${record.deductionReason}", style = MaterialTheme.typography.labelSmall, color = StatusAmber)
            }
            if (record.status == PayrollStatus.PAID && record.paymentDateMillis != null) {
                Text(
                    "Paid ${dateFmt.format(Date(record.paymentDateMillis))} • ${record.paymentMethod ?: ""} (Ref: ${record.paymentReference ?: ""})",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusEmerald
                )
            }

            if (canApproveAndPay && record.status != PayrollStatus.PAID) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (record.status == PayrollStatus.DRAFT) {
                        Button(
                            onClick = onApprove,
                            colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Review & Approve")
                        }
                    } else if (record.status == PayrollStatus.APPROVED) {
                        Button(
                            onClick = onMarkPaid,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusEmerald, contentColor = ObsidianBg),
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mark as Paid")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratePayrollDialog(
    staffList: List<StaffProfileRecord>,
    onDismiss: () -> Unit,
    onGenerate: (
        staffId: String,
        periodKey: String,
        periodLabel: String,
        periodStartMillis: Long,
        periodEndMillis: Long,
        allowancesOverride: Long?,
        bonusesCents: Long,
        deductionsCents: Long,
        deductionReason: String
    ) -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val cal = remember { Calendar.getInstance() }
    val defaultKey = remember { "%04d-%02d".format(Locale.US, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1) }
    val defaultLabel = remember { SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time) }
    val monthStart = remember {
        (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
    }
    val monthEnd = remember {
        (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) }.timeInMillis
    }

    var selectedStaff by remember { mutableStateOf(staffList.firstOrNull()) }
    var staffMenuOpen by remember { mutableStateOf(false) }
    var periodKey by remember { mutableStateOf(defaultKey) }
    var periodLabel by remember { mutableStateOf(defaultLabel) }
    var startDateStr by remember { mutableStateOf(dateFmt.format(Date(monthStart))) }
    var endDateStr by remember { mutableStateOf(dateFmt.format(Date(monthEnd))) }
    var allowancesStr by remember {
        mutableStateOf(selectedStaff?.defaultAllowanceCents?.let { "%.2f".format(Locale.US, it / 100.0) } ?: "")
    }
    var bonusesStr by remember { mutableStateOf("0.00") }
    var deductionsStr by remember { mutableStateOf("0.00") }
    var deductionReason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Generate Payroll Draft", color = CraveGoldBright) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { staffMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedStaff?.let { "${it.employeeCode} — ${it.fullName} (${it.salaryType.label})" } ?: "Select Staff")
                    }
                    DropdownMenu(expanded = staffMenuOpen, onDismissRequest = { staffMenuOpen = false }) {
                        staffList.forEach { st ->
                            DropdownMenuItem(
                                text = { Text("${st.employeeCode} — ${st.fullName}") },
                                onClick = {
                                    selectedStaff = st
                                    allowancesStr = st.defaultAllowanceCents?.let { "%.2f".format(Locale.US, it / 100.0) } ?: ""
                                    staffMenuOpen = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = periodKey,
                    onValueChange = { periodKey = it },
                    label = { Text("Unique Period Key (e.g. 2026-11)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = periodLabel,
                    onValueChange = { periodLabel = it },
                    label = { Text("Period Label (e.g. November 2026)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDateStr,
                        onValueChange = { startDateStr = it },
                        label = { Text("Start Date") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endDateStr,
                        onValueChange = { endDateStr = it },
                        label = { Text("End Date") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = allowancesStr,
                        onValueChange = { allowancesStr = it },
                        label = { Text("Allowances (PKR)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = bonusesStr,
                        onValueChange = { bonusesStr = it },
                        label = { Text("Bonuses (PKR)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = deductionsStr,
                    onValueChange = { deductionsStr = it },
                    label = { Text("Authorized Deductions (PKR)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deductionReason,
                    onValueChange = { deductionReason = it },
                    label = { Text("Deduction Reason (required if > 0)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val st = selectedStaff ?: return@Button
                    val sMs = runCatching { dateFmt.parse(startDateStr.trim())?.time }.getOrNull() ?: monthStart
                    val eMs = runCatching { dateFmt.parse(endDateStr.trim())?.time }.getOrNull() ?: monthEnd
                    val allowCents = allowancesStr.toDoubleOrNull()?.let { (it * 100.0).roundToLong() }
                    val bonusCents = ((bonusesStr.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
                    val dedCents = ((deductionsStr.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
                    onGenerate(st.staffId, periodKey, periodLabel, sMs, eMs, allowCents, bonusCents, dedCents, deductionReason)
                },
                enabled = selectedStaff != null && periodKey.isNotBlank() && periodLabel.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Calculate Payroll") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun MarkPayrollPaidDialog(
    record: PayrollRecord,
    onDismiss: () -> Unit,
    onConfirm: (paymentDateMillis: Long, method: String, reference: String) -> Unit
) {
    var paymentMethod by remember { mutableStateOf("Bank Transfer") }
    var reference by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Disburse Salary — ${record.staffName}", color = CraveGoldBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Net Payable: ${centsToCurrency(record.netSalaryCents)} (${record.periodLabel})",
                    style = MaterialTheme.typography.titleSmall,
                    color = CraveGoldBright
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Bank Transfer", "Cash", "Cheque", "Mobile Wallet").forEach { m ->
                        FilterChip(selected = paymentMethod == m, onClick = { paymentMethod = m }, label = { Text(m) })
                    }
                }
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("Payment Reference Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(System.currentTimeMillis(), paymentMethod, reference) },
                enabled = reference.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = StatusEmerald, contentColor = ObsidianBg)
            ) { Text("Confirm Paid") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
