package com.example.ui.expenses

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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Repeat
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
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.domain.model.ExpenseApprovalStatus
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.ExpenseRecord
import com.example.domain.model.ExpenseRecurrence
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
import com.example.ui.theme.StatusSkyBg
import com.example.ui.viewmodel.ExpenseUiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseManagementScreen(
    session: UserSession,
    uiState: ExpenseUiState,
    onOpenDrawer: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectStatus: (ExpenseApprovalStatus?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onCopyReceiptUri: suspend (Uri) -> Result<String>,
    onSaveExpense: (String?, String, Long, Long, String, String, String?, Boolean, ExpenseRecurrence) -> Unit,
    onSetApproval: (String, Boolean) -> Unit,
    onDeleteExpense: (String) -> Unit,
    onReportMessage: (String, Boolean) -> Unit,
    onClearFeedback: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditor by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseRecord?>(null) }
    var exportMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    fun exportExpenses(format: ReportExportFormat) {
        Phase4ReportExporter.exportExpenseReport(
            context = context,
            branchName = session.branchName,
            expenses = uiState.expenses,
            periodReports = uiState.periodReports,
            format = format
        ).fold(
            onSuccess = { file ->
                Phase4ReportExporter.shareReportFile(context, file, format.mimeType, "Share Expense Report (${format.label})")
                onReportMessage("Exported Expense Report (${format.label}).", false)
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
                        Text("Expense Management", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                        Text(session.branchName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (session.capabilities.canExportReports) {
                        Box {
                            IconButton(onClick = { exportMenuOpen = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Filled.Download, contentDescription = "Export Expenses", tint = CraveGoldBright)
                            }
                            DropdownMenu(expanded = exportMenuOpen, onDismissRequest = { exportMenuOpen = false }) {
                                ReportExportFormat.entries.forEach { fmt ->
                                    DropdownMenuItem(
                                        text = { Text("Export ${fmt.label}") },
                                        onClick = {
                                            exportMenuOpen = false
                                            exportExpenses(fmt)
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
            if (session.capabilities.canManageExpenses && selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        editingExpense = null
                        showEditor = true
                    },
                    containerColor = CraveGold,
                    contentColor = OnCraveGold,
                    modifier = Modifier.testTag("add_expense_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Record Expense")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = ObsidianSurface, contentColor = CraveGoldBright) {
                listOf("Expenses (${uiState.expenses.size})", "Daily / Weekly / Monthly").forEachIndexed { idx, label ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(label, maxLines = 1) })
                }
            }

            when (selectedTab) {
                0 -> ExpensesListTab(
                    session = session,
                    uiState = uiState,
                    onSelectCategory = onSelectCategory,
                    onSelectStatus = onSelectStatus,
                    onSearchChanged = onSearchChanged,
                    onEdit = {
                        editingExpense = it
                        showEditor = true
                    },
                    onApprove = { onSetApproval(it.expenseId, true) },
                    onReject = { onSetApproval(it.expenseId, false) },
                    onDelete = { onDeleteExpense(it.expenseId) }
                )
                1 -> ExpensePeriodReportsTab(
                    uiState = uiState,
                    canExport = session.capabilities.canExportReports,
                    onExport = ::exportExpenses
                )
            }
        }
    }

    if (showEditor) {
        ExpenseEditorDialog(
            existing = editingExpense,
            onDismiss = { showEditor = false },
            onCopyReceiptUri = onCopyReceiptUri,
            onSave = { cat, cents, dateMs, desc, method, receipt, recurring, freq ->
                onSaveExpense(editingExpense?.expenseId, cat, cents, dateMs, desc, method, receipt, recurring, freq)
                showEditor = false
            }
        )
    }
}

@Composable
private fun ExpensesListTab(
    session: UserSession,
    uiState: ExpenseUiState,
    onSelectCategory: (String?) -> Unit,
    onSelectStatus: (ExpenseApprovalStatus?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onEdit: (ExpenseRecord) -> Unit,
    onApprove: (ExpenseRecord) -> Unit,
    onReject: (ExpenseRecord) -> Unit,
    onDelete: (ExpenseRecord) -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChanged,
                placeholder = { Text("Search description or category...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchChanged("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = uiState.selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("All Categories") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                )
                ExpenseCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = uiState.selectedCategory == cat.label,
                        onClick = { onSelectCategory(cat.label) },
                        label = { Text(cat.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                    )
                }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = uiState.selectedStatus == null, onClick = { onSelectStatus(null) }, label = { Text("All Status") })
                ExpenseApprovalStatus.entries.forEach { st ->
                    FilterChip(selected = uiState.selectedStatus == st, onClick = { onSelectStatus(st) }, label = { Text(st.label) })
                }
            }
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            items(uiState.filteredExpenses, key = { it.expenseId }) { exp ->
                val (bg, fg) = when (exp.approvalStatus) {
                    ExpenseApprovalStatus.APPROVED -> StatusEmeraldBg to StatusEmerald
                    ExpenseApprovalStatus.REJECTED -> StatusCrimsonBg to StatusCrimson
                    ExpenseApprovalStatus.PENDING -> StatusAmberBg to StatusAmber
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth().testTag("expense_card_${exp.expenseId}")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                StatusPill(text = exp.category, containerColor = CraveGoldContainer, contentColor = CraveGoldBright)
                                if (exp.isRecurring) {
                                    StatusPill(text = exp.recurrenceFrequency.label, containerColor = StatusSkyBg, contentColor = StatusSky)
                                }
                            }
                            Text(centsToCurrency(exp.amountCents), style = MaterialTheme.typography.titleMedium, color = CraveGoldBright, fontWeight = FontWeight.Bold)
                        }
                        Text(exp.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${dateFmt.format(Date(exp.expenseDateMillis))} • ${exp.paymentMethod}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            StatusPill(text = exp.approvalStatus.label.uppercase(), containerColor = bg, contentColor = fg)
                        }
                        if (!exp.receiptUri.isNullOrBlank()) {
                            Text("Receipt attached ✓", style = MaterialTheme.typography.labelSmall, color = StatusEmerald)
                        }
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (session.capabilities.canManageExpenses) {
                                OutlinedButton(onClick = { onEdit(exp) }, border = BorderStroke(1.dp, CraveGold)) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = CraveGoldBright)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit", color = CraveGoldBright)
                                }
                            }
                            if (session.capabilities.canApproveExpenses && exp.approvalStatus == ExpenseApprovalStatus.PENDING) {
                                Button(
                                    onClick = { onApprove(exp) },
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusEmerald, contentColor = ObsidianBg)
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Approve")
                                }
                                TextButton(onClick = { onReject(exp) }) {
                                    Text("Reject", color = StatusCrimson)
                                }
                            }
                            if (session.capabilities.canApproveExpenses) {
                                IconButton(onClick = { onDelete(exp) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete expense", tint = StatusCrimson)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpensePeriodReportsTab(
    uiState: ExpenseUiState,
    canExport: Boolean,
    onExport: (ReportExportFormat) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        if (canExport) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportExportFormat.entries.forEach { fmt ->
                        Button(
                            onClick = { onExport(fmt) },
                            colors = ButtonDefaults.buttonColors(containerColor = CraveGoldContainer, contentColor = CraveGoldBright),
                            modifier = Modifier.weight(1f)
                        ) { Text(fmt.label, maxLines = 1) }
                    }
                }
            }
        }
        items(uiState.periodReports, key = { it.periodLabel }) { report ->
            Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(report.periodLabel, style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                        Text("${report.count} entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Approved: ${centsToCurrency(report.totalApprovedCents)}", style = MaterialTheme.typography.bodyMedium, color = StatusEmerald)
                        Text("Pending: ${centsToCurrency(report.totalPendingCents)}", style = MaterialTheme.typography.bodyMedium, color = StatusAmber)
                    }
                    if (report.byCategory.isNotEmpty()) {
                        HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.6f))
                        report.byCategory.entries.sortedByDescending { it.value }.forEach { (cat, cents) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(cat, style = MaterialTheme.typography.bodySmall)
                                Text(centsToCurrency(cents), style = MaterialTheme.typography.bodySmall, color = CraveGoldBright)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseEditorDialog(
    existing: ExpenseRecord?,
    onDismiss: () -> Unit,
    onCopyReceiptUri: suspend (Uri) -> Result<String>,
    onSave: (String, Long, Long, String, String, String?, Boolean, ExpenseRecurrence) -> Unit
) {
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf(existing?.category ?: ExpenseCategory.RENT.label) }
    var amountText by remember { mutableStateOf(existing?.let { "%.2f".format(Locale.US, it.amountCents / 100.0) } ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var paymentMethod by remember { mutableStateOf(existing?.paymentMethod ?: "Cash") }
    var dateMillis by remember { mutableLongStateOf(existing?.expenseDateMillis ?: System.currentTimeMillis()) }
    var isRecurring by remember { mutableStateOf(existing?.isRecurring ?: false) }
    var recurrence by remember { mutableStateOf(existing?.recurrenceFrequency ?: ExpenseRecurrence.MONTHLY) }
    var receiptPath by remember { mutableStateOf(existing?.receiptUri) }
    var receiptStatus by remember { mutableStateOf(if (existing?.receiptUri != null) "Receipt attached" else "No receipt attached") }

    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                onCopyReceiptUri(uri).fold(
                    onSuccess = { path ->
                        receiptPath = path
                        receiptStatus = "Receipt saved internally ✓"
                    },
                    onFailure = {
                        receiptStatus = it.message ?: "Could not attach receipt"
                    }
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text(if (existing == null) "Record Expense" else "Edit Expense", color = CraveGoldBright) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category", style = MaterialTheme.typography.labelLarge, color = CraveGold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpenseCategory.entries.forEach { cat ->
                        FilterChip(selected = category == cat.label, onClick = { category = cat.label }, label = { Text(cat.label) })
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (PKR) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description *") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Cash", "Card", "Bank Transfer").forEach { m ->
                        FilterChip(selected = paymentMethod == m, onClick = { paymentMethod = m }, label = { Text(m) })
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Repeat, contentDescription = null, tint = StatusSky, modifier = Modifier.size(18.dp))
                        Text("Recurring Expense", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = isRecurring, onCheckedChange = { isRecurring = it })
                }
                if (isRecurring) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(ExpenseRecurrence.WEEKLY, ExpenseRecurrence.MONTHLY, ExpenseRecurrence.QUARTERLY).forEach { freq ->
                            FilterChip(selected = recurrence == freq, onClick = { recurrence = freq }, label = { Text(freq.label) })
                        }
                    }
                }
                OutlinedButton(
                    onClick = { receiptPicker.launch(arrayOf("image/*", "application/pdf")) },
                    border = BorderStroke(1.dp, CraveGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, tint = CraveGoldBright, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(receiptStatus, color = CraveGoldBright)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cents = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                    onSave(category, cents, dateMillis, description, paymentMethod, receiptPath, isRecurring, if (isRecurring) recurrence else ExpenseRecurrence.NONE)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Save Expense") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
