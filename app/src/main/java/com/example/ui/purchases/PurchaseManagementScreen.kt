package com.example.ui.purchases

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.domain.model.InventoryItemRecord
import com.example.domain.model.PurchaseDraftLine
import com.example.domain.model.PurchaseItemRecord
import com.example.domain.model.PurchaseOrderRecord
import com.example.domain.model.PurchaseStatus
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
import com.example.ui.theme.StatusAmberBg
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusCrimsonBg
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusEmeraldBg
import com.example.ui.theme.StatusSky
import com.example.ui.theme.StatusSkyBg
import com.example.ui.viewmodel.PurchaseUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseManagementScreen(
    session: UserSession,
    uiState: PurchaseUiState,
    onOpenDrawer: () -> Unit,
    onSelectStatusFilter: (PurchaseStatus?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onSavePurchaseOrder: (String?, String, String, List<PurchaseDraftLine>, Long, Long, String, Boolean) -> Unit,
    onApprovePo: (String) -> Unit,
    onCancelPo: (String, String) -> Unit,
    onReceiveGoods: (String, String, Map<String, Double>, String, String) -> Unit,
    onReturnGoods: (String, String, Double, String) -> Unit,
    onReportMessage: (String, Boolean) -> Unit,
    onClearFeedback: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateEditDialog by remember { mutableStateOf(false) }
    var editingPo by remember { mutableStateOf<PurchaseOrderRecord?>(null) }
    var receivingPo by remember { mutableStateOf<PurchaseOrderRecord?>(null) }
    var returningPo by remember { mutableStateOf<PurchaseOrderRecord?>(null) }
    var cancellingPo by remember { mutableStateOf<PurchaseOrderRecord?>(null) }
    var exportMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    fun triggerExport(format: ReportExportFormat) {
        Phase4ReportExporter.exportPurchaseReport(
            context = context,
            branchName = session.branchName,
            orders = uiState.orders,
            format = format
        ).fold(
            onSuccess = { file ->
                Phase4ReportExporter.shareReportFile(context, file, format.mimeType, "Share Purchase Report (${format.label})")
                onReportMessage("Exported Purchase Report (${format.label}).", false)
            },
            onFailure = { err ->
                onReportMessage(err.message ?: "Report export failed.", true)
            }
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
                        Icon(Icons.Filled.Menu, contentDescription = "Open drawer", tint = CraveGoldBright)
                    }
                },
                title = {
                    Column {
                        Text("Purchase Management", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                        Text(session.branchName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (session.capabilities.canExportReports) {
                        Box {
                            IconButton(
                                onClick = { exportMenuOpen = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("purchase_export_button")
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = "Export Purchase Report", tint = CraveGoldBright)
                            }
                            DropdownMenu(expanded = exportMenuOpen, onDismissRequest = { exportMenuOpen = false }) {
                                ReportExportFormat.entries.forEach { fmt ->
                                    DropdownMenuItem(
                                        text = { Text("Export ${fmt.label}") },
                                        onClick = {
                                            exportMenuOpen = false
                                            triggerExport(fmt)
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
            if (session.capabilities.canManagePurchases && selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        editingPo = null
                        showCreateEditDialog = true
                    },
                    containerColor = CraveGold,
                    contentColor = OnCraveGold,
                    modifier = Modifier.testTag("create_po_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Create Purchase Order")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = ObsidianSurface,
                contentColor = CraveGoldBright
            ) {
                listOf("Orders (${uiState.orders.size})", "Receiving", "Returns", "Reports").forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title, maxLines = 1) }
                    )
                }
            }

            when (selectedTab) {
                0 -> OrdersTab(
                    session = session,
                    uiState = uiState,
                    onSelectStatusFilter = onSelectStatusFilter,
                    onSearchChanged = onSearchChanged,
                    onEdit = { po ->
                        editingPo = po
                        showCreateEditDialog = true
                    },
                    onApprove = { po -> onApprovePo(po.purchaseId) },
                    onCancel = { po -> cancellingPo = po },
                    onReceive = { po -> receivingPo = po },
                    onReturn = { po -> returningPo = po }
                )
                1 -> ReceivingsLogTab(uiState)
                2 -> ReturnsLogTab(uiState)
                3 -> PurchaseReportsTab(
                    uiState = uiState,
                    canExport = session.capabilities.canExportReports,
                    onExport = ::triggerExport
                )
            }
        }
    }

    if (showCreateEditDialog) {
        CreateOrEditPoDialog(
            existing = editingPo,
            suppliers = uiState.suppliers,
            inventoryItems = uiState.inventoryItems,
            onDismiss = { showCreateEditDialog = false },
            onSave = { supplierId, invNo, lines, disc, tax, notes, submit ->
                onSavePurchaseOrder(editingPo?.purchaseId, supplierId, invNo, lines, disc, tax, notes, submit)
                showCreateEditDialog = false
            }
        )
    }

    receivingPo?.let { po ->
        ReceiveGoodsDialog(
            po = po,
            onDismiss = { receivingPo = null },
            onConfirmReceive = { refNo, invNo, qtyMap, notes ->
                onReceiveGoods(po.purchaseId, refNo, qtyMap, notes, invNo)
                receivingPo = null
            }
        )
    }

    returningPo?.let { po ->
        PurchaseReturnDialog(
            po = po,
            onDismiss = { returningPo = null },
            onConfirmReturn = { lineId, qty, reason ->
                onReturnGoods(po.purchaseId, lineId, qty, reason)
                returningPo = null
            }
        )
    }

    cancellingPo?.let { po ->
        CancelPoDialog(
            po = po,
            onDismiss = { cancellingPo = null },
            onConfirm = { reason ->
                onCancelPo(po.purchaseId, reason)
                cancellingPo = null
            }
        )
    }
}

@Composable
private fun OrdersTab(
    session: UserSession,
    uiState: PurchaseUiState,
    onSelectStatusFilter: (PurchaseStatus?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onEdit: (PurchaseOrderRecord) -> Unit,
    onApprove: (PurchaseOrderRecord) -> Unit,
    onCancel: (PurchaseOrderRecord) -> Unit,
    onReceive: (PurchaseOrderRecord) -> Unit,
    onReturn: (PurchaseOrderRecord) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChanged,
                placeholder = { Text("Search PO #, supplier, or invoice...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchChanged("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
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
                    label = { Text("All (${uiState.orders.size})") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                )
                PurchaseStatus.entries.forEach { st ->
                    FilterChip(
                        selected = uiState.selectedStatusFilter == st,
                        onClick = { onSelectStatusFilter(st) },
                        label = { Text(st.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (uiState.filteredOrders.isEmpty()) {
                item {
                    Text(
                        text = "No purchase orders match the selected filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(uiState.filteredOrders, key = { it.purchaseId }) { po ->
                    PurchaseOrderCard(
                        session = session,
                        po = po,
                        onEdit = { onEdit(po) },
                        onApprove = { onApprove(po) },
                        onCancel = { onCancel(po) },
                        onReceive = { onReceive(po) },
                        onReturn = { onReturn(po) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PurchaseOrderCard(
    session: UserSession,
    po: PurchaseOrderRecord,
    onEdit: () -> Unit,
    onApprove: () -> Unit,
    onCancel: () -> Unit,
    onReceive: () -> Unit,
    onReturn: () -> Unit
) {
    val dateStr = remember(po.createdAt) { SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(po.createdAt)) }
    val (bg, fg) = when (po.status) {
        PurchaseStatus.RECEIVED -> StatusEmeraldBg to StatusEmerald
        PurchaseStatus.PARTIALLY_RECEIVED -> StatusAmberBg to StatusAmber
        PurchaseStatus.APPROVED -> StatusSkyBg to StatusSky
        PurchaseStatus.CANCELLED -> StatusCrimsonBg to StatusCrimson
        else -> CraveGoldContainer to CraveGoldBright
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("po_card_${po.poNumber}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(po.poNumber, style = MaterialTheme.typography.titleMedium, color = CraveGoldBright, fontWeight = FontWeight.Bold)
                    Text("${po.supplierName} • $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(text = po.status.label.uppercase(), containerColor = bg, contentColor = fg)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Invoice: ${po.supplierInvoiceNumber}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusPill(text = po.paymentStatus.label, containerColor = ObsidianSurfaceElevated, contentColor = CraveGoldBright)
            }

            po.items.forEach { line ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${line.itemName} (${line.receivedQty}/${line.orderedQty} ${line.unit})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(centsToCurrency(line.lineTotalCents), style = MaterialTheme.typography.bodySmall, color = CraveGold)
                }
            }

            HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.6f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Total: ${centsToCurrency(po.totalCents)}", style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
                    if (po.returnedCents > 0L) {
                        Text("Returns: -${centsToCurrency(po.returnedCents)}", style = MaterialTheme.typography.labelSmall, color = StatusAmber)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Paid: ${centsToCurrency(po.paidCents)}", style = MaterialTheme.typography.bodySmall, color = StatusEmerald)
                    Text("Due: ${centsToCurrency(po.remainingPayableCents)}", style = MaterialTheme.typography.labelSmall, color = if (po.remainingPayableCents > 0) StatusCrimson else StatusEmerald)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (session.capabilities.canManagePurchases && (po.status == PurchaseStatus.DRAFT || po.status == PurchaseStatus.PENDING)) {
                    OutlinedButton(onClick = onEdit, border = BorderStroke(1.dp, CraveGold)) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = CraveGoldBright)
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", color = CraveGoldBright)
                    }
                }
                if (session.capabilities.canApprovePurchaseOrders && (po.status == PurchaseStatus.DRAFT || po.status == PurchaseStatus.PENDING)) {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusEmerald, contentColor = ObsidianBg),
                        modifier = Modifier.testTag("approve_po_${po.poNumber}")
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Approve")
                    }
                }
                if (session.capabilities.canManagePurchases && (po.status == PurchaseStatus.APPROVED || po.status == PurchaseStatus.PARTIALLY_RECEIVED)) {
                    Button(
                        onClick = onReceive,
                        colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                        modifier = Modifier.testTag("receive_po_${po.poNumber}")
                    ) {
                        Icon(Icons.Filled.Inventory, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Receive Goods")
                    }
                }
                if (session.capabilities.canManagePurchases && po.items.any { it.returnableQty > 0.0 }) {
                    OutlinedButton(onClick = onReturn, border = BorderStroke(1.dp, StatusAmber)) {
                        Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusAmber)
                        Spacer(Modifier.width(4.dp))
                        Text("Return", color = StatusAmber)
                    }
                }
                if (session.capabilities.canApprovePurchaseOrders &&
                    po.status != PurchaseStatus.CANCELLED &&
                    po.status != PurchaseStatus.RECEIVED &&
                    po.items.all { it.receivedQty <= 0.0 }
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel PO", color = StatusCrimson)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceivingsLogTab(uiState: PurchaseUiState) {
    val dateFmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.US) }
    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (uiState.receivings.isEmpty()) {
            item { Text("No goods receiving records yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(uiState.receivings, key = { it.receivingId }) { rec ->
                Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${rec.referenceNumber} • ${rec.itemName}", style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
                            Text("By ${rec.receivedByName} • ${dateFmt.format(Date(rec.receivedDateMillis))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (rec.notes.isNotBlank()) {
                                Text(rec.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        StatusPill(text = "+${rec.receivedQty} ${rec.unit}", containerColor = StatusEmeraldBg, contentColor = StatusEmerald)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReturnsLogTab(uiState: PurchaseUiState) {
    val dateFmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.US) }
    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (uiState.returns.isEmpty()) {
            item { Text("No purchase returns recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(uiState.returns, key = { it.returnId }) { ret ->
                Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${ret.poNumber} • ${ret.itemName}", style = MaterialTheme.typography.titleSmall, color = StatusAmber)
                            Text("Reason: ${ret.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            Text("${ret.actorName} • ${dateFmt.format(Date(ret.returnedAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("-${ret.returnQty} ${ret.unit}", style = MaterialTheme.typography.titleSmall, color = StatusCrimson)
                            Text(centsToCurrency(ret.returnAmountCents), style = MaterialTheme.typography.bodySmall, color = CraveGoldBright)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseReportsTab(
    uiState: PurchaseUiState,
    canExport: Boolean,
    onExport: (ReportExportFormat) -> Unit
) {
    val active = uiState.orders.filter { it.status != PurchaseStatus.CANCELLED }
    val totalOrdered = active.sumOf { it.totalCents }
    val totalPaid = active.sumOf { it.paidCents }
    val totalReturns = active.sumOf { it.returnedCents }
    val totalPending = active.sumOf { it.remainingPayableCents }

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Procurement Financial Summary", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                    ReportMetricRow("Total Active PO Value", centsToCurrency(totalOrdered))
                    ReportMetricRow("Total Supplier Paid", centsToCurrency(totalPaid))
                    ReportMetricRow("Purchase Returns Credited", centsToCurrency(totalReturns))
                    ReportMetricRow("Outstanding Payables", centsToCurrency(totalPending))
                }
            }
        }
        if (canExport) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportExportFormat.entries.forEach { fmt ->
                        Button(
                            onClick = { onExport(fmt) },
                            colors = ButtonDefaults.buttonColors(containerColor = CraveGoldContainer, contentColor = CraveGoldBright),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(fmt.label, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportMetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, color = CraveGoldBright, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CreateOrEditPoDialog(
    existing: PurchaseOrderRecord?,
    suppliers: List<SupplierSummary>,
    inventoryItems: List<InventoryItemRecord>,
    onDismiss: () -> Unit,
    onSave: (String, String, List<PurchaseDraftLine>, Long, Long, String, Boolean) -> Unit
) {
    var selectedSupplier by remember {
        mutableStateOf(suppliers.firstOrNull { it.supplierId == existing?.supplierId } ?: suppliers.firstOrNull())
    }
    var supplierMenuOpen by remember { mutableStateOf(false) }
    var invoiceNo by remember { mutableStateOf(existing?.supplierInvoiceNumber ?: "") }
    var discountDollars by remember { mutableStateOf(existing?.let { "%.2f".format(Locale.US, it.discountCents / 100.0) } ?: "0.00") }
    var taxDollars by remember { mutableStateOf(existing?.let { "%.2f".format(Locale.US, it.taxCents / 100.0) } ?: "0.00") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    val draftLines = remember {
        mutableStateListOf<PurchaseDraftLine>().apply {
            if (existing != null) {
                addAll(existing.items.map {
                    PurchaseDraftLine(it.inventoryItemId, it.itemName, it.itemType, it.unit, it.orderedQty, it.unitPriceCents)
                })
            }
        }
    }
    var selectedInv by remember { mutableStateOf(inventoryItems.firstOrNull()) }
    var invMenuOpen by remember { mutableStateOf(false) }
    var lineQtyText by remember { mutableStateOf("10") }
    var linePriceText by remember { mutableStateOf("%.2f".format(Locale.US, (inventoryItems.firstOrNull()?.unitCostCents ?: 100L) / 100.0)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text(if (existing == null) "Create Purchase Order" else "Edit ${existing.poNumber}", color = CraveGoldBright) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box {
                    OutlinedButton(onClick = { supplierMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Supplier: ${selectedSupplier?.name ?: "Select Supplier"}", color = CraveGoldBright)
                    }
                    DropdownMenu(expanded = supplierMenuOpen, onDismissRequest = { supplierMenuOpen = false }) {
                        suppliers.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s.name} (${s.category})") },
                                onClick = {
                                    selectedSupplier = s
                                    supplierMenuOpen = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = invoiceNo,
                    onValueChange = { invoiceNo = it },
                    label = { Text("Supplier Invoice # (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Add Raw Material / Finished Stock", style = MaterialTheme.typography.labelLarge, color = CraveGold)
                Box {
                    OutlinedButton(onClick = { invMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedInv?.let { "${it.name} (${it.unit})" } ?: "Select Inventory Item")
                    }
                    DropdownMenu(expanded = invMenuOpen, onDismissRequest = { invMenuOpen = false }) {
                        inventoryItems.forEach { inv ->
                            DropdownMenuItem(
                                text = { Text("${inv.name} (${inv.unit})") },
                                onClick = {
                                    selectedInv = inv
                                    linePriceText = "%.2f".format(Locale.US, inv.unitCostCents / 100.0)
                                    invMenuOpen = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lineQtyText,
                        onValueChange = { lineQtyText = it },
                        label = { Text("Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = linePriceText,
                        onValueChange = { linePriceText = it },
                        label = { Text("Unit PKR") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = {
                        val inv = selectedInv ?: return@Button
                        val qty = lineQtyText.toDoubleOrNull() ?: 0.0
                        val priceCents = ((linePriceText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                        if (qty > 0.0 && priceCents >= 0L) {
                            draftLines.add(PurchaseDraftLine(inv.itemId, inv.name, inv.itemType.name, inv.unit, qty, priceCents))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CraveGoldContainer, contentColor = CraveGoldBright),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Add Item Line")
                }

                draftLines.forEachIndexed { idx, line ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${line.itemName}: ${line.orderedQty} ${line.unit} @ ${centsToCurrency(line.unitPriceCents)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { draftLines.removeAt(idx) }) { Text("Remove", color = StatusCrimson) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = discountDollars,
                        onValueChange = { discountDollars = it },
                        label = { Text("Discount (PKR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = taxDollars,
                        onValueChange = { taxDollars = it },
                        label = { Text("Tax (PKR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Order Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val sup = selectedSupplier ?: return@OutlinedButton
                        val disc = ((discountDollars.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0L)
                        val tax = ((taxDollars.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0L)
                        onSave(sup.supplierId, invoiceNo, draftLines.toList(), disc, tax, notes, false)
                    }
                ) { Text("Draft") }
                Button(
                    onClick = {
                        val sup = selectedSupplier ?: return@Button
                        val disc = ((discountDollars.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0L)
                        val tax = ((taxDollars.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0L)
                        onSave(sup.supplierId, invoiceNo, draftLines.toList(), disc, tax, notes, true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
                ) { Text("Submit PO") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ReceiveGoodsDialog(
    po: PurchaseOrderRecord,
    onDismiss: () -> Unit,
    onConfirmReceive: (String, String, Map<String, Double>, String) -> Unit
) {
    var refNo by remember { mutableStateOf("GRN-${System.currentTimeMillis() % 100000}") }
    var invNo by remember { mutableStateOf(if (po.supplierInvoiceNumber == "PENDING") "" else po.supplierInvoiceNumber) }
    var notes by remember { mutableStateOf("") }
    val qtyInputs = remember {
        mutableStateMapOf<String, String>().apply {
            po.items.forEach { put(it.lineId, it.remainingToReceiveQty.toString()) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Receive Goods • ${po.poNumber}", color = CraveGoldBright) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = refNo,
                    onValueChange = { refNo = it },
                    label = { Text("GRN / Reference # *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = invNo,
                    onValueChange = { invNo = it },
                    label = { Text("Supplier Invoice #") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                po.items.filter { it.remainingToReceiveQty > 0.0 }.forEach { line ->
                    OutlinedTextField(
                        value = qtyInputs[line.lineId] ?: "0",
                        onValueChange = { qtyInputs[line.lineId] = it },
                        label = { Text("${line.itemName} (Rem: ${line.remainingToReceiveQty} ${line.unit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Inspection / Temperature Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val map = qtyInputs.mapValues { (_, v) -> v.toDoubleOrNull() ?: 0.0 }
                    onConfirmReceive(refNo, invNo, map, notes)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Confirm Receiving") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PurchaseReturnDialog(
    po: PurchaseOrderRecord,
    onDismiss: () -> Unit,
    onConfirmReturn: (String, Double, String) -> Unit
) {
    val returnableLines = remember(po) { po.items.filter { it.returnableQty > 0.0 } }
    var selectedLine by remember { mutableStateOf(returnableLines.firstOrNull()) }
    var qtyText by remember { mutableStateOf("1") }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Purchase Return • ${po.poNumber}", color = StatusAmber) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                returnableLines.forEach { line ->
                    FilterChip(
                        selected = selectedLine?.lineId == line.lineId,
                        onClick = { selectedLine = line },
                        label = { Text("${line.itemName} (Max ${line.returnableQty} ${line.unit})") }
                    )
                }
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Return Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Return Reason *") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val line = selectedLine ?: return@Button
                    val qty = qtyText.toDoubleOrNull() ?: 0.0
                    onConfirmReturn(line.lineId, qty, reason)
                },
                colors = ButtonDefaults.buttonColors(containerColor = StatusAmber, contentColor = ObsidianBg)
            ) { Text("Submit Return") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CancelPoDialog(
    po: PurchaseOrderRecord,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Cancel ${po.poNumber}?", color = StatusCrimson) },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Cancellation Reason *") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson, contentColor = ObsidianBg)
            ) { Text("Confirm Cancel") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } }
    )
}
