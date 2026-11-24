package com.example.ui.suppliers

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
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
import androidx.compose.ui.unit.dp
import com.example.domain.model.PurchaseOrderRecord
import com.example.domain.model.SupplierCategory
import com.example.domain.model.SupplierStatementReport
import com.example.domain.model.SupplierSummary
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
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusEmeraldBg
import com.example.ui.theme.StatusSky
import com.example.ui.viewmodel.SupplierUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierManagementScreen(
    session: UserSession,
    uiState: SupplierUiState,
    onOpenDrawer: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onSaveSupplier: (String?, String, String, String, String, String, String, Long) -> Unit,
    onRecordPayment: (String, String?, Long, String, String, String) -> Unit,
    onBuildStatement: (SupplierSummary) -> SupplierStatementReport,
    onReportMessage: (String, Boolean) -> Unit,
    onClearFeedback: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSupplierDialog by remember { mutableStateOf(false) }
    var editingSupplier by remember { mutableStateOf<SupplierSummary?>(null) }
    var paymentSupplier by remember { mutableStateOf<SupplierSummary?>(null) }
    var statementReport by remember { mutableStateOf<SupplierStatementReport?>(null) }
    var exportMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    fun exportPerformance(format: ReportExportFormat) {
        Phase4ReportExporter.exportSupplierPerformanceReport(
            context = context,
            branchName = session.branchName,
            metrics = uiState.performanceMetrics,
            format = format
        ).fold(
            onSuccess = { file ->
                Phase4ReportExporter.shareReportFile(context, file, format.mimeType, "Share Supplier Performance (${format.label})")
                onReportMessage("Exported Supplier Performance (${format.label}).", false)
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
                        Text("Supplier Management", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                        Text(session.branchName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (session.capabilities.canExportReports) {
                        Box {
                            IconButton(onClick = { exportMenuOpen = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Filled.Download, contentDescription = "Export Supplier Report", tint = CraveGoldBright)
                            }
                            DropdownMenu(expanded = exportMenuOpen, onDismissRequest = { exportMenuOpen = false }) {
                                ReportExportFormat.entries.forEach { fmt ->
                                    DropdownMenuItem(
                                        text = { Text("Performance (${fmt.label})") },
                                        onClick = {
                                            exportMenuOpen = false
                                            exportPerformance(fmt)
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
            if (session.capabilities.canManageSuppliers && selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        editingSupplier = null
                        showSupplierDialog = true
                    },
                    containerColor = CraveGold,
                    contentColor = OnCraveGold,
                    modifier = Modifier.testTag("add_supplier_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Register Supplier")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = ObsidianSurface, contentColor = CraveGoldBright) {
                listOf("Suppliers (${uiState.suppliers.size})", "Payments (${uiState.payments.size})", "Performance").forEachIndexed { idx, label ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(label, maxLines = 1) })
                }
            }

            when (selectedTab) {
                0 -> SuppliersListTab(
                    session = session,
                    uiState = uiState,
                    onSearchChanged = onSearchChanged,
                    onEdit = {
                        editingSupplier = it
                        showSupplierDialog = true
                    },
                    onPay = { paymentSupplier = it },
                    onStatement = { statementReport = onBuildStatement(it) }
                )
                1 -> SupplierPaymentsTab(uiState)
                2 -> SupplierPerformanceTab(uiState, session.capabilities.canExportReports, ::exportPerformance)
            }
        }
    }

    if (showSupplierDialog) {
        SupplierEditorDialog(
            existing = editingSupplier,
            onDismiss = { showSupplierDialog = false },
            onSave = { name, contact, phone, email, address, cat, opening ->
                onSaveSupplier(editingSupplier?.supplierId, name, contact, phone, email, address, cat, opening)
                showSupplierDialog = false
            }
        )
    }

    paymentSupplier?.let { supplier ->
        val supplierPos = uiState.purchaseOrders.filter { it.supplierId == supplier.supplierId && it.remainingPayableCents > 0L }
        RecordSupplierPaymentDialog(
            supplier = supplier,
            unpaidOrders = supplierPos,
            onDismiss = { paymentSupplier = null },
            onConfirm = { poId, amt, method, ref, notes ->
                onRecordPayment(supplier.supplierId, poId, amt, method, ref, notes)
                paymentSupplier = null
            }
        )
    }

    statementReport?.let { report ->
        SupplierStatementDialog(
            report = report,
            canExport = session.capabilities.canExportReports,
            onDismiss = { statementReport = null },
            onExport = { fmt ->
                Phase4ReportExporter.exportSupplierStatementReport(context, report, fmt).fold(
                    onSuccess = { file ->
                        Phase4ReportExporter.shareReportFile(context, file, fmt.mimeType, "Share Statement (${fmt.label})")
                        onReportMessage("Exported statement (${fmt.label}).", false)
                    },
                    onFailure = { onReportMessage(it.message ?: "Export failed.", true) }
                )
            }
        )
    }
}

@Composable
private fun SuppliersListTab(
    session: UserSession,
    uiState: SupplierUiState,
    onSearchChanged: (String) -> Unit,
    onEdit: (SupplierSummary) -> Unit,
    onPay: (SupplierSummary) -> Unit,
    onStatement: (SupplierSummary) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchChanged,
            placeholder = { Text("Search supplier name, contact, category...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChanged("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(14.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(uiState.filteredSuppliers, key = { it.supplierId }) { sup ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth().testTag("supplier_card_${sup.supplierId}")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sup.name, style = MaterialTheme.typography.titleMedium, color = CraveGoldBright, fontWeight = FontWeight.Bold)
                                Text("${sup.contactPerson} • ${sup.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(text = sup.category, containerColor = CraveGoldContainer, contentColor = CraveGoldBright)
                        }
                        if (sup.address.isNotBlank()) {
                            Text(sup.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.6f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Purchases: ${centsToCurrency(sup.totalPurchasesCents)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Paid: ${centsToCurrency(sup.totalPaidCents)}", style = MaterialTheme.typography.bodySmall, color = StatusEmerald)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Outstanding", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    centsToCurrency(sup.outstandingBalanceCents),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (sup.outstandingBalanceCents > 0) StatusCrimson else StatusEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onStatement(sup) }, border = BorderStroke(1.dp, StatusSky)) {
                                Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusSky)
                                Spacer(Modifier.width(4.dp))
                                Text("Statement", color = StatusSky)
                            }
                            if (session.capabilities.canPaySuppliers) {
                                Button(
                                    onClick = { onPay(sup) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                                    modifier = Modifier.testTag("pay_supplier_${sup.supplierId}")
                                ) {
                                    Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pay Supplier")
                                }
                            }
                            if (session.capabilities.canManageSuppliers) {
                                OutlinedButton(onClick = { onEdit(sup) }, border = BorderStroke(1.dp, CraveGold)) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = CraveGoldBright)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit", color = CraveGoldBright)
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
private fun SupplierPaymentsTab(uiState: SupplierUiState) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        if (uiState.payments.isEmpty()) {
            item { Text("No supplier payments recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(uiState.payments, key = { it.paymentId }) { pay ->
                Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pay.supplierName, style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
                            Text("Ref: ${pay.referenceNumber} • ${pay.paymentMethod}${pay.poNumber?.let { " • $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            Text("${dateFmt.format(Date(pay.paymentDateMillis))} • By ${pay.paidByName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(centsToCurrency(pay.amountCents), style = MaterialTheme.typography.titleMedium, color = StatusEmerald, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SupplierPerformanceTab(
    uiState: SupplierUiState,
    canExport: Boolean,
    onExport: (ReportExportFormat) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
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
        items(uiState.performanceMetrics, key = { it.supplierId }) { m ->
            Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(m.supplierName, style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
                        StatusPill(text = "${m.fulfilledOrders}/${m.totalOrders} POs", containerColor = StatusEmeraldBg, contentColor = StatusEmerald)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Spend: ${centsToCurrency(m.totalSpendCents)}", style = MaterialTheme.typography.bodySmall)
                        Text("Return Rate: %.1f%%".format(Locale.US, m.returnRatePercent), style = MaterialTheme.typography.bodySmall, color = if (m.returnRatePercent > 5.0) StatusAmber else StatusEmerald)
                    }
                }
            }
        }
    }
}

@Composable
private fun SupplierEditorDialog(
    existing: SupplierSummary?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, Long) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var contact by remember { mutableStateOf(existing?.contactPerson ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: SupplierCategory.MEAT_POULTRY.label) }
    var openingText by remember { mutableStateOf(existing?.let { "%.2f".format(Locale.US, it.openingBalanceCents / 100.0) } ?: "0.00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text(if (existing == null) "Register Supplier" else "Edit Supplier", color = CraveGoldBright) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Supplier Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = contact, onValueChange = { contact = it }, label = { Text("Contact Person") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SupplierCategory.entries.forEach { cat ->
                        FilterChip(selected = category == cat.label, onClick = { category = cat.label }, label = { Text(cat.label) })
                    }
                }
                OutlinedTextField(
                    value = openingText,
                    onValueChange = { openingText = it },
                    label = { Text("Opening Balance (PKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val opening = ((openingText.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0L)
                    onSave(name, contact, phone, email, address, category, opening)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Save Supplier") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RecordSupplierPaymentDialog(
    supplier: SupplierSummary,
    unpaidOrders: List<PurchaseOrderRecord>,
    onDismiss: () -> Unit,
    onConfirm: (String?, Long, String, String, String) -> Unit
) {
    var selectedPo by remember { mutableStateOf(unpaidOrders.firstOrNull()) }
    var amountText by remember {
        mutableStateOf(
            "%.2f".format(Locale.US, ((selectedPo?.remainingPayableCents ?: supplier.outstandingBalanceCents) / 100.0))
        )
    }
    var method by remember { mutableStateOf("Bank Transfer") }
    var refNo by remember { mutableStateOf("PAY-${System.currentTimeMillis() % 100000}") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Pay ${supplier.name}", color = CraveGoldBright) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Outstanding Balance: ${centsToCurrency(supplier.outstandingBalanceCents)}", style = MaterialTheme.typography.bodyMedium, color = StatusCrimson)
                if (unpaidOrders.isNotEmpty()) {
                    Text("Link to Purchase Order:", style = MaterialTheme.typography.labelMedium, color = CraveGold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = selectedPo == null, onClick = { selectedPo = null }, label = { Text("General Account") })
                        unpaidOrders.forEach { po ->
                            FilterChip(
                                selected = selectedPo?.purchaseId == po.purchaseId,
                                onClick = {
                                    selectedPo = po
                                    amountText = "%.2f".format(Locale.US, po.remainingPayableCents / 100.0)
                                },
                                label = { Text("${po.poNumber} (${centsToCurrency(po.remainingPayableCents)})") }
                            )
                        }
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Bank Transfer", "Cash", "Cheque").forEach { m ->
                        FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m) })
                    }
                }
                OutlinedTextField(value = refNo, onValueChange = { refNo = it }, label = { Text("Payment Reference # *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cents = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                    onConfirm(selectedPo?.purchaseId, cents, method, refNo, notes)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Record Payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SupplierStatementDialog(
    report: SupplierStatementReport,
    canExport: Boolean,
    onDismiss: () -> Unit,
    onExport: (ReportExportFormat) -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM", Locale.US) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Statement • ${report.supplier.name}", color = CraveGoldBright) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Closing Payable: ${centsToCurrency(report.closingBalanceCents)}", style = MaterialTheme.typography.titleSmall, color = StatusCrimson)
                HorizontalDivider(color = ObsidianBorder)
                report.lines.forEach { line ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${dateFmt.format(Date(line.dateMillis))} • ${line.reference}", style = MaterialTheme.typography.labelSmall, color = CraveGold)
                            Text(line.description, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (line.debitCents > 0L) Text("+${centsToCurrency(line.debitCents)}", style = MaterialTheme.typography.bodySmall, color = StatusCrimson)
                            if (line.creditCents > 0L) Text("-${centsToCurrency(line.creditCents)}", style = MaterialTheme.typography.bodySmall, color = StatusEmerald)
                            Text("Bal: ${centsToCurrency(line.runningBalanceCents)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.4f))
                }
                if (canExport) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        ReportExportFormat.entries.forEach { fmt ->
                            OutlinedButton(onClick = { onExport(fmt) }, modifier = Modifier.weight(1f)) {
                                Text(fmt.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
