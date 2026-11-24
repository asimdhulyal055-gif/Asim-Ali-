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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.ShiftAssignmentRecord
import com.example.domain.model.ShiftCalculator
import com.example.domain.model.ShiftDefinitionRecord
import com.example.domain.model.StaffProfileRecord
import com.example.domain.model.UserSession
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
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusEmeraldBg
import com.example.ui.theme.StatusSky
import com.example.ui.theme.StatusSkyBg
import com.example.ui.viewmodel.ShiftUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftManagementScreen(
    session: UserSession,
    uiState: ShiftUiState,
    onOpenDrawer: () -> Unit,
    onSelectDayFilter: (Int?) -> Unit,
    onSaveShift: (
        shiftId: String?,
        shiftName: String,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        isOvernight: Boolean,
        breakMinutes: Int,
        graceMinutes: Int,
        regularThresholdMinutes: Int,
        overtimeMultiplier: Double
    ) -> Unit,
    onDeleteShift: (String) -> Unit,
    onAssignShift: (shiftId: String, staffId: String?, teamDepartment: String?, dayOfWeek: Int) -> Unit,
    onDeleteAssignment: (String) -> Unit,
    onClearFeedback: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val eff = uiState.effectivePolicy
    val canManageShifts = eff?.canManageShifts ?: session.capabilities.canManageShifts

    var selectedTab by remember { mutableIntStateOf(0) }
    var showShiftEditor by remember { mutableStateOf(false) }
    var editingShift by remember { mutableStateOf<ShiftDefinitionRecord?>(null) }
    var showAssignDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
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
                        Text("Shifts & Schedules", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                        Text(session.branchName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        },
        floatingActionButton = {
            if (canManageShifts) {
                FloatingActionButton(
                    onClick = {
                        if (selectedTab == 0) {
                            editingShift = null
                            showShiftEditor = true
                        } else {
                            showAssignDialog = true
                        }
                    },
                    containerColor = CraveGold,
                    contentColor = OnCraveGold,
                    modifier = Modifier.testTag("shift_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = if (selectedTab == 0) "Create Shift" else "Assign Shift")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = ObsidianSurface, contentColor = CraveGoldBright) {
                listOf("Shift Rules (${uiState.shifts.size})", "Daily & Weekly Schedule").forEachIndexed { idx, label ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(label, maxLines = 1) })
                }
            }

            when (selectedTab) {
                0 -> ShiftDefinitionsTab(
                    shifts = uiState.shifts,
                    canManageShifts = canManageShifts,
                    onEdit = {
                        editingShift = it
                        showShiftEditor = true
                    },
                    onDelete = onDeleteShift,
                    onAssignClick = { showAssignDialog = true }
                )
                1 -> ShiftScheduleTab(
                    assignments = uiState.assignments,
                    shifts = uiState.shifts,
                    selectedDayFilter = uiState.selectedDayFilter,
                    canManageShifts = canManageShifts,
                    onSelectDayFilter = onSelectDayFilter,
                    onDeleteAssignment = onDeleteAssignment
                )
            }
        }
    }

    if (showShiftEditor) {
        ShiftEditorDialog(
            existing = editingShift,
            onDismiss = { showShiftEditor = false },
            onSave = { name, startMin, endMin, overnight, breakMin, graceMin, regMin, otMult ->
                onSaveShift(editingShift?.shiftId, name, startMin, endMin, overnight, breakMin, graceMin, regMin, otMult)
                showShiftEditor = false
            }
        )
    }

    if (showAssignDialog) {
        ShiftAssignmentDialog(
            shifts = uiState.shifts,
            staffList = uiState.staffList,
            onDismiss = { showAssignDialog = false },
            onAssign = { shiftId, staffId, dept, dow ->
                onAssignShift(shiftId, staffId, dept, dow)
                showAssignDialog = false
            }
        )
    }
}

@Composable
private fun ShiftDefinitionsTab(
    shifts: List<ShiftDefinitionRecord>,
    canManageShifts: Boolean,
    onEdit: (ShiftDefinitionRecord) -> Unit,
    onDelete: (String) -> Unit,
    onAssignClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (canManageShifts && shifts.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = onAssignClick,
                    border = BorderStroke(1.dp, CraveGold),
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = CraveGoldBright)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Assign Shift to Employee or Team", color = CraveGoldBright)
                }
            }
        }
        items(shifts, key = { it.shiftId }) { sh ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sh.shiftName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(sh.formattedWindow, style = MaterialTheme.typography.bodyMedium, color = CraveGoldBright)
                        }
                        if (sh.isOvernight) {
                            StatusPill("OVERNIGHT", StatusSkyBg, StatusSky)
                        } else {
                            StatusPill("DAY SHIFT", StatusEmeraldBg, StatusEmerald)
                        }
                    }

                    Text(
                        "Net Scheduled: ${ShiftCalculator.formatMinutesAsHours(sh.scheduledDurationMinutes)} • Break: ${sh.breakDurationMinutes}m • Grace: ${sh.graceLateMinutes}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "OT Threshold: ${ShiftCalculator.formatMinutesAsHours(sh.regularHoursThresholdMinutes)} • OT Multiplier: ${sh.overtimeMultiplier}x",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (canManageShifts) {
                        HorizontalDivider(color = ObsidianBorder)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { onEdit(sh) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit shift", tint = CraveGoldBright)
                            }
                            IconButton(onClick = { onDelete(sh.shiftId) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete shift", tint = StatusCrimson)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun ShiftScheduleTab(
    assignments: List<ShiftAssignmentRecord>,
    shifts: List<ShiftDefinitionRecord>,
    selectedDayFilter: Int?,
    canManageShifts: Boolean,
    onSelectDayFilter: (Int?) -> Unit,
    onDeleteAssignment: (String) -> Unit
) {
    val days = listOf(
        null to "All",
        0 to "Every Day",
        1 to "Mon",
        2 to "Tue",
        3 to "Wed",
        4 to "Thu",
        5 to "Fri",
        6 to "Sat",
        7 to "Sun"
    )
    val visible = remember(assignments, selectedDayFilter) {
        if (selectedDayFilter == null) assignments
        else assignments.filter { it.dayOfWeek == selectedDayFilter || (selectedDayFilter != 0 && it.dayOfWeek == 0) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            days.forEach { (code, label) ->
                FilterChip(
                    selected = selectedDayFilter == code,
                    onClick = { onSelectDayFilter(code) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                )
            }
        }

        if (visible.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No shift assignments for the selected schedule filter.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visible, key = { it.assignmentId }) { asg ->
                    val shift = shifts.firstOrNull { it.shiftId == asg.shiftId }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StatusPill(asg.dayLabel.uppercase(), CraveGoldContainer, CraveGoldBright)
                                    Text(asg.staffName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                }
                                Text(
                                    "${asg.shiftName}${shift?.let { " (${it.formattedWindow})" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CraveGoldBright
                                )
                                Text(
                                    "Dept: ${asg.teamDepartment ?: "All"} • Assigned by ${asg.assignedByName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (canManageShifts) {
                                IconButton(onClick = { onDeleteAssignment(asg.assignmentId) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove assignment", tint = StatusCrimson)
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun ShiftEditorDialog(
    existing: ShiftDefinitionRecord?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        startMin: Int,
        endMin: Int,
        isOvernight: Boolean,
        breakMin: Int,
        graceMin: Int,
        regThresholdMin: Int,
        otMult: Double
    ) -> Unit
) {
    var name by remember { mutableStateOf(existing?.shiftName ?: "") }
    var startTimeStr by remember { mutableStateOf(existing?.let { ShiftDefinitionRecord.formatMinute(it.startMinuteOfDay) } ?: "09:00") }
    var endTimeStr by remember { mutableStateOf(existing?.let { ShiftDefinitionRecord.formatMinute(it.endMinuteOfDay) } ?: "17:00") }
    var isOvernight by remember { mutableStateOf(existing?.isOvernight ?: false) }
    var breakMinStr by remember { mutableStateOf((existing?.breakDurationMinutes ?: 30).toString()) }
    var graceMinStr by remember { mutableStateOf((existing?.graceLateMinutes ?: 10).toString()) }
    var regThresholdStr by remember { mutableStateOf((existing?.regularHoursThresholdMinutes ?: 480).toString()) }
    var otMultStr by remember { mutableStateOf((existing?.overtimeMultiplier ?: 1.5).toString()) }

    fun parseTime(hhmm: String, fallback: Int): Int {
        val parts = hhmm.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: return fallback
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return h * 60 + m
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text(if (existing == null) "Create Shift" else "Edit Shift", color = CraveGoldBright) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Shift Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("shift_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startTimeStr,
                        onValueChange = { startTimeStr = it },
                        label = { Text("Start (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTimeStr,
                        onValueChange = { endTimeStr = it },
                        label = { Text("End (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.NightsStay, contentDescription = null, tint = StatusSky, modifier = Modifier.size(18.dp))
                        Text("Overnight Shift (crosses midnight)", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = isOvernight, onCheckedChange = { isOvernight = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = breakMinStr,
                        onValueChange = { breakMinStr = it },
                        label = { Text("Break (min)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = graceMinStr,
                        onValueChange = { graceMinStr = it },
                        label = { Text("Grace (min)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = regThresholdStr,
                        onValueChange = { regThresholdStr = it },
                        label = { Text("Regular Mins (480=8h)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = otMultStr,
                        onValueChange = { otMultStr = it },
                        label = { Text("OT Mult") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startMin = parseTime(startTimeStr, 540)
                    val endMin = parseTime(endTimeStr, 1020)
                    onSave(
                        name,
                        startMin,
                        endMin,
                        isOvernight || endMin <= startMin,
                        breakMinStr.toIntOrNull() ?: 30,
                        graceMinStr.toIntOrNull() ?: 10,
                        regThresholdStr.toIntOrNull() ?: 480,
                        otMultStr.toDoubleOrNull() ?: 1.5
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Save Shift") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ShiftAssignmentDialog(
    shifts: List<ShiftDefinitionRecord>,
    staffList: List<StaffProfileRecord>,
    onDismiss: () -> Unit,
    onAssign: (shiftId: String, staffId: String?, teamDepartment: String?, dayOfWeek: Int) -> Unit
) {
    var selectedShift by remember { mutableStateOf(shifts.firstOrNull()) }
    var shiftMenuOpen by remember { mutableStateOf(false) }
    var assignToTeam by remember { mutableStateOf(false) }
    var selectedStaff by remember { mutableStateOf(staffList.firstOrNull()) }
    var staffMenuOpen by remember { mutableStateOf(false) }
    var teamDepartment by remember { mutableStateOf("Culinary / KDS") }
    var dayOfWeek by remember { mutableIntStateOf(0) }

    val dayChoices = listOf(0 to "All Days", 1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Assign Shift Schedule", color = CraveGoldBright) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { shiftMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedShift?.let { "${it.shiftName} (${it.formattedWindow})" } ?: "Select Shift")
                    }
                    DropdownMenu(expanded = shiftMenuOpen, onDismissRequest = { shiftMenuOpen = false }) {
                        shifts.forEach { sh ->
                            DropdownMenuItem(
                                text = { Text("${sh.shiftName} • ${sh.formattedWindow}") },
                                onClick = {
                                    selectedShift = sh
                                    shiftMenuOpen = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !assignToTeam, onClick = { assignToTeam = false }, label = { Text("Individual Staff") })
                    FilterChip(selected = assignToTeam, onClick = { assignToTeam = true }, label = { Text("Team Department") })
                }

                if (!assignToTeam) {
                    Box {
                        OutlinedButton(onClick = { staffMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedStaff?.let { "${it.employeeCode} — ${it.fullName}" } ?: "No Active Staff")
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
                } else {
                    OutlinedTextField(
                        value = teamDepartment,
                        onValueChange = { teamDepartment = it },
                        label = { Text("Team / Department") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("Schedule Day", style = MaterialTheme.typography.labelMedium, color = CraveGoldBright)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayChoices.forEach { (code, label) ->
                        FilterChip(
                            selected = dayOfWeek == code,
                            onClick = { dayOfWeek = code },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sh = selectedShift ?: return@Button
                    if (assignToTeam) {
                        onAssign(sh.shiftId, null, teamDepartment, dayOfWeek)
                    } else {
                        val st = selectedStaff ?: return@Button
                        onAssign(sh.shiftId, st.staffId, null, dayOfWeek)
                    }
                },
                enabled = selectedShift != null && (assignToTeam && teamDepartment.isNotBlank() || !assignToTeam && selectedStaff != null),
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Assign") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
