package com.example.ui.inventory

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.InventoryItemRecord
import com.example.domain.model.InventoryItemType
import com.example.domain.model.InventoryUnit
import com.example.domain.model.PhysicalAuditRecord
import com.example.domain.model.PosProduct
import com.example.domain.model.StockMovementRecord
import com.example.domain.model.StockMovementType
import com.example.domain.model.UserSession
import com.example.domain.model.centsToCurrency
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
import com.example.ui.viewmodel.InventoryAlertFilter
import com.example.ui.viewmodel.InventoryTab
import com.example.ui.viewmodel.InventoryUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

private enum class StockActionDialogType {
    RECEIVE, ADJUST, WASTAGE, AUDIT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryManagementScreen(
    session: UserSession,
    uiState: InventoryUiState,
    onOpenDrawer: () -> Unit,
    onSelectTab: (InventoryTab) -> Unit,
    onSelectAlertFilter: (InventoryAlertFilter) -> Unit,
    onSearchChanged: (String) -> Unit,
    onSaveItem: (
        itemId: String?,
        name: String,
        sku: String,
        itemType: InventoryItemType,
        unit: String,
        qty: Double,
        reorderLevel: Double,
        unitCostCents: Long,
        expiryMillis: Long?,
        linkedProductId: String?
    ) -> Unit,
    onReceiveStock: (String, Double, Long?, String) -> Unit,
    onAdjustStock: (String, Double, String) -> Unit,
    onRecordWastage: (String, Double, String) -> Unit,
    onRecordAudit: (String, Double, String) -> Unit,
    onClearFeedback: () -> Unit
) {
    val canManage = session.capabilities.canManageInventory
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateItemDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<InventoryItemRecord?>(null) }
    var stockActionPair by remember { mutableStateOf<Pair<InventoryItemRecord, StockActionDialogType>?>(null) }

    LaunchedEffect(uiState.feedbackMessage) {
        val msg = uiState.feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_drawer_button")
                    ) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = CraveGoldBright)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "INVENTORY CONTROL",
                            style = MaterialTheme.typography.titleLarge,
                            color = CraveGoldBright
                        )
                        Text(
                            text = "${session.branchName} • Auto-Deduction & Audits",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (canManage) {
                        IconButton(
                            onClick = { showCreateItemDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("inventory_add_item_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add inventory item", tint = CraveGoldBright)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Total Stock Valuation (PKR) summary bar
            val totalStockValueCents = remember(uiState.items) {
                uiState.items.sumOf { (it.currentQty * it.unitCostCents).roundToLong() }
            }
            Surface(
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("inventory_valuation_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tracked SKUs: ${uiState.items.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Stock Valuation: ${centsToCurrency(totalStockValueCents)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = CraveGoldBright
                    )
                }
            }

            // Low Stock & Expiry Alert Banner
            if (uiState.lowStockCount > 0 || uiState.expiringCount > 0) {
                Surface(
                    color = StatusAmberBg,
                    border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(18.dp))
                        Text(
                            text = "${uiState.lowStockCount} Low-Stock Alert(s) • ${uiState.expiringCount} Expiring/Expired Batch(es)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusAmber
                        )
                    }
                }
            }

            TabRow(
                selectedTabIndex = InventoryTab.entries.indexOf(uiState.selectedTab),
                containerColor = ObsidianSurface,
                contentColor = CraveGoldBright
            ) {
                InventoryTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        text = {
                            Text(
                                text = tab.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }

            when (uiState.selectedTab) {
                InventoryTab.RAW_MATERIALS, InventoryTab.FINISHED_PRODUCTS -> {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchChanged,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text("Search item name or SKU...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("inventory_search_input")
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InventoryAlertFilter.entries.forEach { filter ->
                            val label = when (filter) {
                                InventoryAlertFilter.ALL -> filter.label
                                InventoryAlertFilter.LOW_STOCK -> "${filter.label} (${uiState.lowStockCount})"
                                InventoryAlertFilter.EXPIRING -> "${filter.label} (${uiState.expiringCount})"
                            }
                            FilterChip(
                                selected = uiState.alertFilter == filter,
                                onClick = { onSelectAlertFilter(filter) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CraveGold,
                                    selectedLabelColor = OnCraveGold
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = ObsidianBorder)

                    if (uiState.filteredItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Inventory2, contentDescription = null, tint = CraveGold.copy(alpha = 0.6f), modifier = Modifier.size(44.dp))
                                Text("No stock items match current filter", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.filteredItems, key = { it.itemId }) { item ->
                                InventoryItemCard(
                                    item = item,
                                    canManage = canManage,
                                    onEdit = { editingItem = item },
                                    onReceive = { stockActionPair = item to StockActionDialogType.RECEIVE },
                                    onAdjust = { stockActionPair = item to StockActionDialogType.ADJUST },
                                    onWastage = { stockActionPair = item to StockActionDialogType.WASTAGE },
                                    onAudit = { stockActionPair = item to StockActionDialogType.AUDIT }
                                )
                            }
                        }
                    }
                }

                InventoryTab.MOVEMENTS -> {
                    if (uiState.movements.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No stock movements recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.movements, key = { it.movementId }) { movement ->
                                StockMovementCard(movement)
                            }
                        }
                    }
                }

                InventoryTab.AUDITS -> {
                    if (uiState.audits.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No physical stock audits recorded yet. Use 'Audit' on any inventory item.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.audits, key = { it.auditId }) { audit ->
                                PhysicalAuditCard(audit)
                            }
                        }
                    }
                }
            }
        }
    }

    if ((showCreateItemDialog || editingItem != null) && canManage) {
        InventoryItemEditorDialog(
            existing = editingItem,
            defaultType = if (uiState.selectedTab == InventoryTab.FINISHED_PRODUCTS) InventoryItemType.FINISHED_PRODUCT else InventoryItemType.RAW_MATERIAL,
            products = uiState.products,
            onSave = { name, sku, type, unit, qty, reorder, costCents, expiry, linkedProdId ->
                onSaveItem(editingItem?.itemId, name, sku, type, unit, qty, reorder, costCents, expiry, linkedProdId)
                showCreateItemDialog = false
                editingItem = null
            },
            onDismiss = {
                showCreateItemDialog = false
                editingItem = null
            }
        )
    }

    val actionPair = stockActionPair
    if (actionPair != null && canManage) {
        StockOperationDialog(
            item = actionPair.first,
            type = actionPair.second,
            onConfirmReceive = { qty, expiry, note ->
                onReceiveStock(actionPair.first.itemId, qty, expiry, note)
                stockActionPair = null
            },
            onConfirmAdjust = { delta, reason ->
                onAdjustStock(actionPair.first.itemId, delta, reason)
                stockActionPair = null
            },
            onConfirmWastage = { qty, reason ->
                onRecordWastage(actionPair.first.itemId, qty, reason)
                stockActionPair = null
            },
            onConfirmAudit = { counted, notes ->
                onRecordAudit(actionPair.first.itemId, counted, notes)
                stockActionPair = null
            },
            onDismiss = { stockActionPair = null }
        )
    }
}

@Composable
private fun InventoryItemCard(
    item: InventoryItemRecord,
    canManage: Boolean,
    onEdit: () -> Unit,
    onReceive: () -> Unit,
    onAdjust: () -> Unit,
    onWastage: () -> Unit,
    onAudit: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(
            1.dp,
            when {
                item.isExpired || item.isLowStock -> StatusCrimson.copy(alpha = 0.7f)
                item.isExpiringSoon -> StatusAmber.copy(alpha = 0.7f)
                else -> ObsidianBorder
            }
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inventory_item_${item.itemId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${item.sku} • Cost ${centsToCurrency(item.unitCostCents)} / ${item.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CraveGold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${item.currentQty} ${item.unit}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (item.isLowStock) StatusCrimson else CraveGoldBright
                    )
                    Text(
                        text = "Reorder <= ${item.reorderLevel} ${item.unit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(item.itemType.label.uppercase(), ObsidianSurfaceElevated, MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.isLowStock) {
                    StatusPill("LOW STOCK", StatusCrimsonBg, StatusCrimson)
                }
                if (item.isExpired) {
                    StatusPill("EXPIRED", StatusCrimsonBg, StatusCrimson)
                } else if (item.isExpiringSoon) {
                    StatusPill("EXPIRING SOON", StatusAmberBg, StatusAmber)
                }
            }

            if (item.expiryDateMillis != null) {
                Text(
                    text = "Batch Expiry: ${dateFormatter.format(Date(item.expiryDateMillis))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.isExpired) StatusCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canManage) {
                HorizontalDivider(color = ObsidianBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onReceive,
                        colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.AddBox, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Receive")
                    }
                    OutlinedButton(
                        onClick = onAdjust,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adjust")
                    }
                    OutlinedButton(
                        onClick = onWastage,
                        border = BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = StatusCrimson, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Wastage", color = StatusCrimson)
                    }
                    OutlinedButton(
                        onClick = onAudit,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Audit")
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit item", tint = CraveGoldBright)
                    }
                }
            }
        }
    }
}

@Composable
private fun StockMovementCard(movement: StockMovementRecord) {
    val formatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.US) }
    val isPositive = movement.quantityDelta >= 0.0
    val badgeColors = when (movement.movementType) {
        StockMovementType.RECEIVING -> StatusEmeraldBg to StatusEmerald
        StockMovementType.SALE_DEDUCTION -> CraveGoldContainer to CraveGoldBright
        StockMovementType.WASTAGE -> StatusCrimsonBg to StatusCrimson
        StockMovementType.AUDIT_CORRECTION -> StatusAmberBg to StatusAmber
        StockMovementType.ADJUSTMENT -> ObsidianSurfaceElevated to MaterialTheme.colorScheme.onSurface
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(movement.itemName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "${if (isPositive) "+" else ""}${movement.quantityDelta} ${movement.unit}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isPositive) StatusEmerald else StatusCrimson
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(movement.movementType.label.uppercase(), badgeColors.first, badgeColors.second)
                Text(
                    text = "${movement.actorName} • ${formatter.format(Date(movement.createdAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(movement.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PhysicalAuditCard(audit: PhysicalAuditRecord) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }
    val varianceColor = when {
        audit.varianceQty == 0.0 -> StatusEmerald
        audit.varianceQty < 0.0 -> StatusCrimson
        else -> StatusAmber
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(audit.itemName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "Variance: ${if (audit.varianceQty > 0) "+" else ""}${audit.varianceQty}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = varianceColor
                )
            }
            Text(
                text = "Expected: ${audit.expectedQty} | Physical Count: ${audit.countedQty}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${audit.auditorName} • ${formatter.format(Date(audit.auditedAt))} • ${audit.notes}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InventoryItemEditorDialog(
    existing: InventoryItemRecord?,
    defaultType: InventoryItemType,
    products: List<PosProduct>,
    onSave: (
        name: String,
        sku: String,
        type: InventoryItemType,
        unit: String,
        qty: Double,
        reorderLevel: Double,
        costCents: Long,
        expiryMillis: Long?,
        linkedProductId: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var sku by remember { mutableStateOf(existing?.sku.orEmpty()) }
    var itemType by remember { mutableStateOf(existing?.itemType ?: defaultType) }
    var unit by remember { mutableStateOf(existing?.unit ?: InventoryUnit.KG.symbol) }
    var qtyText by remember { mutableStateOf(existing?.currentQty?.toString() ?: "0.0") }
    var reorderText by remember { mutableStateOf(existing?.reorderLevel?.toString() ?: "5.0") }
    var costText by remember {
        mutableStateOf(
            if (existing != null) String.format(Locale.US, "%.2f", existing.unitCostCents / 100.0) else "1.00"
        )
    }
    var expiryDaysText by remember {
        mutableStateOf(
            if (existing?.expiryDateMillis != null) {
                val days = ((existing.expiryDateMillis - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0L)
                days.toString()
            } else ""
        )
    }
    var linkedProductId by remember { mutableStateOf(existing?.linkedProductId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text(if (existing == null) "New Inventory Item" else "Edit Stock Item", color = CraveGoldBright) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InventoryItemType.entries.forEach { t ->
                        FilterChip(
                            selected = itemType == t,
                            onClick = { itemType = t },
                            label = { Text(t.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        label = { Text("SKU *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text("Unit Cost (PKR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Unit of Measure", style = MaterialTheme.typography.labelMedium, color = CraveGold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InventoryUnit.entries.forEach { u ->
                        FilterChip(
                            selected = unit.equals(u.symbol, ignoreCase = true),
                            onClick = { unit = u.symbol },
                            label = { Text(u.symbol) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (existing == null) {
                        OutlinedTextField(
                            value = qtyText,
                            onValueChange = { qtyText = it },
                            label = { Text("Opening Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = reorderText,
                        onValueChange = { reorderText = it },
                        label = { Text("Reorder Level") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = expiryDaysText,
                    onValueChange = { expiryDaysText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Shelf Life / Expiry in Days (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (itemType == InventoryItemType.FINISHED_PRODUCT && products.isNotEmpty()) {
                    Text("Link Finished POS Product (Optional)", style = MaterialTheme.typography.labelMedium, color = CraveGold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = linkedProductId == null,
                            onClick = { linkedProductId = null },
                            label = { Text("None") }
                        )
                        products.forEach { prod ->
                            FilterChip(
                                selected = linkedProductId == prod.productId,
                                onClick = { linkedProductId = prod.productId },
                                label = { Text(prod.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = qtyText.toDoubleOrNull() ?: 0.0
                    val reorder = reorderText.toDoubleOrNull() ?: 0.0
                    val costCents = ((costText.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
                    val expiryMillis = expiryDaysText.toLongOrNull()?.let { days ->
                        System.currentTimeMillis() + days * 86_400_000L
                    }
                    onSave(name, sku, itemType, unit, qty, reorder, costCents, expiryMillis, linkedProductId)
                },
                enabled = name.isNotBlank() && sku.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) {
                Text("Save Item")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StockOperationDialog(
    item: InventoryItemRecord,
    type: StockActionDialogType,
    onConfirmReceive: (Double, Long?, String) -> Unit,
    onConfirmAdjust: (Double, String) -> Unit,
    onConfirmWastage: (Double, String) -> Unit,
    onConfirmAudit: (Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var qtyText by remember {
        mutableStateOf(if (type == StockActionDialogType.AUDIT) item.currentQty.toString() else "")
    }
    var reasonOrNote by remember { mutableStateOf("") }
    var isAddAdjustment by remember { mutableStateOf(true) }
    var expiryDaysText by remember { mutableStateOf("") }

    val title = when (type) {
        StockActionDialogType.RECEIVE -> "Receive Stock • ${item.name}"
        StockActionDialogType.ADJUST -> "Adjust Stock • ${item.name}"
        StockActionDialogType.WASTAGE -> "Record Wastage • ${item.name}"
        StockActionDialogType.AUDIT -> "Physical Stock Audit • ${item.name}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text(title, color = CraveGoldBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Current Balance: ${item.currentQty} ${item.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CraveGold
                )

                if (type == StockActionDialogType.ADJUST) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isAddAdjustment,
                            onClick = { isAddAdjustment = true },
                            label = { Text("Add (+)") }
                        )
                        FilterChip(
                            selected = !isAddAdjustment,
                            onClick = { isAddAdjustment = false },
                            label = { Text("Deduct (-)") }
                        )
                    }
                }

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = {
                        Text(
                            when (type) {
                                StockActionDialogType.AUDIT -> "Physical Counted Qty (${item.unit}) *"
                                else -> "Quantity (${item.unit}) *"
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (type == StockActionDialogType.AUDIT) {
                    val counted = qtyText.toDoubleOrNull()
                    if (counted != null) {
                        val variance = counted - item.currentQty
                        Text(
                            text = "Variance Preview: ${if (variance > 0) "+" else ""}$variance ${item.unit}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (variance == 0.0) StatusEmerald else StatusAmber
                        )
                    }
                }

                if (type == StockActionDialogType.RECEIVE) {
                    OutlinedTextField(
                        value = expiryDaysText,
                        onValueChange = { expiryDaysText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("New Batch Expiry (days from today, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = reasonOrNote,
                    onValueChange = { reasonOrNote = it },
                    label = {
                        Text(
                            when (type) {
                                StockActionDialogType.RECEIVE -> "Supplier / Batch Reference"
                                StockActionDialogType.ADJUST -> "Adjustment Reason *"
                                StockActionDialogType.WASTAGE -> "Wastage Reason (Spoilage, Burnt, etc.) *"
                                StockActionDialogType.AUDIT -> "Audit Notes"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val numericQty = qtyText.toDoubleOrNull()
            val isValid = when (type) {
                StockActionDialogType.RECEIVE -> numericQty != null && numericQty > 0.0
                StockActionDialogType.ADJUST -> numericQty != null && numericQty > 0.0 && reasonOrNote.isNotBlank()
                StockActionDialogType.WASTAGE -> numericQty != null && numericQty > 0.0 && reasonOrNote.isNotBlank()
                StockActionDialogType.AUDIT -> numericQty != null && numericQty >= 0.0
            }

            Button(
                onClick = {
                    val q = numericQty ?: return@Button
                    when (type) {
                        StockActionDialogType.RECEIVE -> {
                            val exp = expiryDaysText.toLongOrNull()?.let { d ->
                                System.currentTimeMillis() + d * 86_400_000L
                            }
                            onConfirmReceive(q, exp, reasonOrNote)
                        }
                        StockActionDialogType.ADJUST -> {
                            onConfirmAdjust(if (isAddAdjustment) q else -q, reasonOrNote)
                        }
                        StockActionDialogType.WASTAGE -> {
                            onConfirmWastage(q, reasonOrNote)
                        }
                        StockActionDialogType.AUDIT -> {
                            onConfirmAudit(q, reasonOrNote)
                        }
                    }
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) {
                Text("Confirm")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
