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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.MonthlyAttendanceSummaryRow
import com.example.domain.model.ShiftCalculator
import com.example.domain.model.StaffProfileRecord
import com.example.domain.model.UserSession
import com.example.domain.model.VerificationMethod
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
import com.example.ui.viewmodel.AttendanceUiState
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceManagementScreen(
    session: UserSession,
    uiState: AttendanceUiState,
    onOpenDrawer: () -> Unit,
    onSelectStatusFilter: (AttendanceStatus?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onShiftMonth: (Int) -> Unit,
    onCheckIn: (staffId: String, method: VerificationMethod, pin: String, notes: String) -> Unit,
    onCheckOut: (staffId: String, method: VerificationMethod, pin: String) -> Unit,
    onManualCorrection: (staffId: String, dateMillis: Long, status: AttendanceStatus, checkInMs: Long?, checkOutMs: Long?, reason: String) -> Unit,
    onReportMessage: (String, Boolean) -> Unit,
    onClearFeedback: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val eff = uiState.effectivePolicy
    val canApproveCorrections = eff?.canApproveAttendanceCorrections ?: session.capabilities.canApproveAttendanceCorrections
    val canExport = eff?.canExportHrReports ?: session.capabilities.canExportReports

    var selectedTab by remember { mutableIntStateOf(0) }
    var correctionTarget by remember { mutableStateOf<AttendanceRecord?>(null) }
    var showNewCorrectionDialog by remember { mutableStateOf(false) }
    var exportMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    val (year, month) = uiState.selectedMonthYear
    val monthLabel = "${DateFormatSymbols(Locale.US).months[month]} $year"
    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    fun exportReport(type: String, format: ReportExportFormat) {
        val res = when (type) {
            "DAILY" -> Phase5ReportExporter.exportDailyAttendanceReport(
                context = context,
                branchName = session.branchName,
                dateLabel = todayKey,
                records = uiState.records.filter { it.attendanceDateStr == todayKey },
                format = format
            )
            "MONTHLY" -> Phase5ReportExporter.exportMonthlyAttendanceReport(
                context = context,
                branchName = session.branchName,
                monthLabel = monthLabel,
                summaryRows = uiState.monthlySummary,
                format = format
            )
            else -> Phase5ReportExporter.exportLateAndOvertimeReport(
                context = context,
                branchName = session.branchName,
                records = uiState.records,
                format = format
            )
        }
        res.fold(
            onSuccess = { file ->
                Phase4ReportExporter.shareReportFile(context, file, format.mimeType, "Share Attendance Report (${format.label})")
                onReportMessage("Exported $type report (${format.label}).", false)
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
                        Text("Attendance & Timecards", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                        Text(session.branchName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (canApproveCorrections) {
                        IconButton(
                            onClick = {
                                correctionTarget = null
                                showNewCorrectionDialog = true
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("attendance_correction_button")
                        ) {
                            Icon(Icons.Filled.EditCalendar, contentDescription = "Manual Attendance Correction", tint = CraveGoldBright)
                        }
                    }
                    if (canExport) {
                        Box {
                            IconButton(onClick = { exportMenuOpen = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Filled.Download, contentDescription = "Export Attendance Reports", tint = CraveGoldBright)
                            }
                            DropdownMenu(expanded = exportMenuOpen, onDismissRequest = { exportMenuOpen = false }) {
                                listOf(
                                    Triple("Daily Attendance", "DAILY", ReportExportFormat.PDF),
                                    Triple("Daily Attendance", "DAILY", ReportExportFormat.CSV),
                                    Triple("Monthly Summary", "MONTHLY", ReportExportFormat.PDF),
                                    Triple("Monthly Summary", "MONTHLY", ReportExportFormat.CSV),
                                    Triple("Late & Overtime", "LATE_OT", ReportExportFormat.PDF),
                                    Triple("Late & Overtime", "LATE_OT", ReportExportFormat.CSV)
                                ).forEach { (title, key, fmt) ->
                                    DropdownMenuItem(
                                        text = { Text("$title (${fmt.label})") },
                                        onClick = {
                                            exportMenuOpen = false
                                            exportReport(key, fmt)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = ObsidianSurface, contentColor = CraveGoldBright) {
                listOf("Check-In & Log", "Monthly Summary", "Late & Overtime").forEachIndexed { idx, label ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(label, maxLines = 1) })
                }
            }

            when (selectedTab) {
                0 -> AttendanceCheckInAndLogTab(
                    uiState = uiState,
                    canApproveCorrections = canApproveCorrections,
                    onSelectStatusFilter = onSelectStatusFilter,
                    onSearchChanged = onSearchChanged,
                    onCheckIn = onCheckIn,
                    onCheckOut = onCheckOut,
                    onOpenCorrection = {
                        correctionTarget = it
                        showNewCorrectionDialog = true
                    }
                )
                1 -> MonthlyAttendanceTab(
                    monthLabel = monthLabel,
                    summaryRows = uiState.monthlySummary,
                    onPrevMonth = { onShiftMonth(-1) },
                    onNextMonth = { onShiftMonth(1) }
                )
                2 -> LateAndOvertimeTab(
                    records = uiState.records.filter { it.status == AttendanceStatus.LATE || it.overtimeMinutes > 0 }
                )
            }
        }
    }

    if (showNewCorrectionDialog) {
        AttendanceCorrectionDialog(
            staffList = uiState.staffList,
            prefill = correctionTarget,
            onDismiss = { showNewCorrectionDialog = false },
            onSave = { staffId, dateMs, status, checkInMs, checkOutMs, reason ->
                onManualCorrection(staffId, dateMs, status, checkInMs, checkOutMs, reason)
                showNewCorrectionDialog = false
            }
        )
    }
}

@Composable
private fun AttendanceCheckInAndLogTab(
    uiState: AttendanceUiState,
    canApproveCorrections: Boolean,
    onSelectStatusFilter: (AttendanceStatus?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onCheckIn: (String, VerificationMethod, String, String) -> Unit,
    onCheckOut: (String, VerificationMethod, String) -> Unit,
    onOpenCorrection: (AttendanceRecord) -> Unit
) {
    var selectedStaff by remember(uiState.staffList) { mutableStateOf(uiState.staffList.firstOrNull()) }
    var staffMenuOpen by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf(VerificationMethod.PIN) }
    var pinInput by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Check-in / Check-out Console Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = CraveGoldBright)
                        Text("Daily Check-In / Check-Out", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                    }

                    if (uiState.staffList.isEmpty()) {
                        Text(
                            "Add active employees in Staff Directory first to record PIN or Manual attendance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber
                        )
                    } else {
                        Box {
                            OutlinedButton(
                                onClick = { staffMenuOpen = true },
                                modifier = Modifier.fillMaxWidth().testTag("attendance_staff_selector")
                            ) {
                                Text(selectedStaff?.let { "${it.employeeCode} — ${it.fullName} (${it.department})" } ?: "Select Staff")
                            }
                            DropdownMenu(expanded = staffMenuOpen, onDismissRequest = { staffMenuOpen = false }) {
                                uiState.staffList.forEach { st ->
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

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(VerificationMethod.PIN, VerificationMethod.MANUAL).forEach { m ->
                                FilterChip(
                                    selected = method == m,
                                    onClick = { method = m },
                                    label = { Text(m.label) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                                )
                            }
                        }

                        if (method == VerificationMethod.PIN) {
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { pinInput = it.filter { ch -> ch.isDigit() }.take(8) },
                                label = { Text("Staff Attendance PIN") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth().testTag("attendance_pin_input")
                            )
                        }

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Shift / Check-In Note (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    selectedStaff?.let { st ->
                                        onCheckIn(st.staffId, method, pinInput, notes)
                                        pinInput = ""
                                        notes = ""
                                    }
                                },
                                enabled = selectedStaff != null,
                                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("attendance_checkin_button")
                            ) {
                                Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check In")
                            }
                            OutlinedButton(
                                onClick = {
                                    selectedStaff?.let { st ->
                                        onCheckOut(st.staffId, method, pinInput)
                                        pinInput = ""
                                    }
                                },
                                enabled = selectedStaff != null,
                                border = BorderStroke(1.dp, CraveGold),
                                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("attendance_checkout_button")
                            ) {
                                Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check Out")
                            }
                        }
                    }
                }
            }
        }

        // Honest Face Attendance Card — NEVER fake recognition
        item {
            val face = uiState.faceProviderStatus
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().testTag("face_attendance_status_card")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.FaceRetouchingNatural, contentDescription = null, tint = StatusAmber)
                            Text("Face Attendance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        StatusPill(
                            text = face.statusBadge.uppercase(),
                            containerColor = StatusAmberBg,
                            contentColor = StatusAmber
                        )
                    }
                    Text(face.providerName, style = MaterialTheme.typography.labelMedium, color = CraveGoldBright)
                    Text(face.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Filters & Search
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Search attendance history (name, Emp ID, YYYY-MM-DD)") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedStatusFilter == null,
                        onClick = { onSelectStatusFilter(null) },
                        label = { Text("All (${uiState.records.size})") }
                    )
                    AttendanceStatus.entries.forEach { st ->
                        FilterChip(
                            selected = uiState.selectedStatusFilter == st,
                            onClick = { onSelectStatusFilter(if (uiState.selectedStatusFilter == st) null else st) },
                            label = { Text(st.label) }
                        )
                    }
                }
            }
        }

        items(uiState.filteredRecords, key = { it.attendanceId }) { rec ->
            AttendanceRecordCard(
                record = rec,
                canCorrect = canApproveCorrections,
                onCorrect = { onOpenCorrection(rec) }
            )
        }
    }
}

@Composable
private fun AttendanceRecordCard(
    record: AttendanceRecord,
    canCorrect: Boolean,
    onCorrect: () -> Unit
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val statusStyle = when (record.status) {
        AttendanceStatus.PRESENT -> StatusEmeraldBg to StatusEmerald
        AttendanceStatus.LATE, AttendanceStatus.HALF_DAY -> StatusAmberBg to StatusAmber
        AttendanceStatus.ABSENT -> StatusCrimsonBg to StatusCrimson
        AttendanceStatus.LEAVE, AttendanceStatus.HOLIDAY -> StatusSkyBg to StatusSky
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${record.employeeCode} • ${record.staffName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${record.attendanceDateStr} • Shift: ${record.shiftName ?: "Default"} • ${record.verificationMethod.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(record.status.label.uppercase(), statusStyle.first, statusStyle.second)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val inText = record.checkInMillis?.let { timeFmt.format(Date(it)) } ?: "-"
                val outText = record.checkOutMillis?.let { timeFmt.format(Date(it)) } ?: "Open"
                Text("In: $inText  |  Out: $outText", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Worked: ${ShiftCalculator.formatMinutesAsHours(record.workedMinutes)}  |  OT: ${record.overtimeMinutes}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (record.overtimeMinutes > 0) CraveGoldBright else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (record.isManualCorrection && !record.correctionReason.isNullOrBlank()) {
                Text(
                    "Supervisor Correction (${record.approvedByName ?: "Admin"}): ${record.correctionReason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusAmber
                )
            }
            if (canCorrect) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCorrect) {
                        Text("Correct / Override")
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyAttendanceTab(
    monthLabel: String,
    summaryRows: List<MonthlyAttendanceSummaryRow>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevMonth) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous Month", tint = CraveGoldBright)
                    }
                    Text(monthLabel, style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                    IconButton(onClick = onNextMonth) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next Month", tint = CraveGoldBright)
                    }
                }
            }
        }
        items(summaryRows, key = { it.staffId }) { row ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${row.employeeCode} • ${row.staffName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Present: ${row.presentDays} • Late: ${row.lateDays} • Half: ${row.halfDays} • Leave: ${row.leaveDays} • Holiday: ${row.holidayDays} • Absent: ${row.absentDays}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Total Worked: ${ShiftCalculator.formatMinutesAsHours(row.totalWorkedMinutes)}  |  Overtime: ${ShiftCalculator.formatMinutesAsHours(row.totalOvertimeMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CraveGoldBright
                    )
                }
            }
        }
    }
}

@Composable
private fun LateAndOvertimeTab(records: List<AttendanceRecord>) {
    if (records.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No late arrivals or overtime entries logged yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(records, key = { it.attendanceId }) { r ->
                AttendanceRecordCard(record = r, canCorrect = false, onCorrect = {})
            }
        }
    }
}

@Composable
private fun AttendanceCorrectionDialog(
    staffList: List<StaffProfileRecord>,
    prefill: AttendanceRecord?,
    onDismiss: () -> Unit,
    onSave: (staffId: String, dateMillis: Long, status: AttendanceStatus, checkInMs: Long?, checkOutMs: Long?, reason: String) -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    var selectedStaff by remember {
        mutableStateOf(
            prefill?.let { p -> staffList.firstOrNull { it.staffId == p.staffId } } ?: staffList.firstOrNull()
        )
    }
    var staffMenuOpen by remember { mutableStateOf(false) }
    var dateText by remember { mutableStateOf(prefill?.attendanceDateStr ?: dateFmt.format(Date())) }
    var status by remember { mutableStateOf(prefill?.status ?: AttendanceStatus.PRESENT) }
    var hoursWorkedText by remember { mutableStateOf(if ((prefill?.workedMinutes ?: 0) > 0) ((prefill!!.workedMinutes) / 60.0).toString() else "8") }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Authorized Attendance Correction", color = CraveGoldBright) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { staffMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedStaff?.let { "${it.employeeCode} — ${it.fullName}" } ?: "Select Employee")
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
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Attendance Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AttendanceStatus.entries.forEach { st ->
                        FilterChip(selected = status == st, onClick = { status = st }, label = { Text(st.label) })
                    }
                }
                OutlinedTextField(
                    value = hoursWorkedText,
                    onValueChange = { hoursWorkedText = it },
                    label = { Text("Hours Worked (optional, e.g. 8 or 9.5)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Correction Reason * (Audit Required)") },
                    modifier = Modifier.fillMaxWidth().testTag("correction_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = selectedStaff ?: return@Button
                    val parsedDate = runCatching { dateFmt.parse(dateText.trim())?.time }.getOrNull() ?: System.currentTimeMillis()
                    val hrs = hoursWorkedText.toDoubleOrNull()
                    val checkInMs = if (hrs != null && hrs > 0.0 && (status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE || status == AttendanceStatus.HALF_DAY)) {
                        parsedDate + 9 * 3_600_000L
                    } else null
                    val checkOutMs = if (checkInMs != null && hrs != null) {
                        checkInMs + (hrs * 3_600_000.0).toLong()
                    } else null
                    onSave(target.staffId, parsedDate, status, checkInMs, checkOutMs, reason)
                },
                enabled = selectedStaff != null && reason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Save Correction") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
