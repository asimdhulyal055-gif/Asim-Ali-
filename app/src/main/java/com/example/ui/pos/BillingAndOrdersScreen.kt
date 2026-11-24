package com.example.ui.pos

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.OrderStatus
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PosOrderRecord
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
import com.example.ui.viewmodel.PosUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingConsoleScreen(
    session: UserSession,
    uiState: PosUiState,
    onOpenDrawer: () -> Unit,
    onGoToPos: () -> Unit,
    onOpenInvoice: (String) -> Unit
) {
    val activeOrders = uiState.orders.filter { it.status != OrderStatus.CANCELLED }
    val grossCents = activeOrders.sumOf { it.totalCents }
    val collectedCents = activeOrders.sumOf { it.paidCents }
    val outstandingCents = activeOrders.sumOf { it.remainingBalanceCents }

    Scaffold(
        containerColor = ObsidianBg,
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
                        Text("Billing & Payments", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                        Text(session.branchName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Branch Billing Ledger (Room Live)", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                        BillingMetricRow("Total Billed Orders", "${activeOrders.size}")
                        BillingMetricRow("Gross Billed Value", centsToCurrency(grossCents))
                        BillingMetricRow("Total Collected", centsToCurrency(collectedCents))
                        BillingMetricRow("Remaining Balances", centsToCurrency(outstandingCents))
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = onGoToPos,
                            colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PointOfSale, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open POS Register")
                        }
                    }
                }
            }

            items(activeOrders, key = { it.orderId }) { order ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(order.invoiceNumber, style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
                            Text(centsToCurrency(order.totalCents), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text("${order.orderNumber} • ${order.paymentBreakdown}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(
                                text = if (order.remainingBalanceCents > 0) "BALANCE: ${centsToCurrency(order.remainingBalanceCents)}" else "SETTLED",
                                containerColor = if (order.remainingBalanceCents > 0) StatusAmberBg else StatusEmeraldBg,
                                contentColor = if (order.remainingBalanceCents > 0) StatusAmber else StatusEmerald
                            )
                            TextButton(onClick = { onOpenInvoice(order.orderId) }) {
                                Text("View Invoice", color = CraveGoldBright)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersHistoryScreen(
    session: UserSession,
    uiState: PosUiState,
    onOpenDrawer: () -> Unit,
    onOpenInvoice: (String) -> Unit,
    onOpenKot: (String) -> Unit,
    onCancelOrder: (String, String) -> Unit,
    onRefundOrder: (String, String) -> Unit,
    onClearFeedback: () -> Unit
) {
    var statusFilter by remember { mutableStateOf<OrderStatus?>(null) }
    var cancelTarget by remember { mutableStateOf<PosOrderRecord?>(null) }
    var refundTarget by remember { mutableStateOf<PosOrderRecord?>(null) }
    var reasonInput by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearFeedback()
        }
    }

    val filtered = uiState.orders.filter { statusFilter == null || it.status == statusFilter }
    val canApprove = session.capabilities.canApproveRefundsOrCancellations
    val dateFmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                        Text("Orders History", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                        Text("${filtered.size} orders • ${session.branchName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("All") })
                    FilterChip(selected = statusFilter == OrderStatus.CONFIRMED, onClick = { statusFilter = OrderStatus.CONFIRMED }, label = { Text("Confirmed") })
                    FilterChip(selected = statusFilter == OrderStatus.CANCELLED, onClick = { statusFilter = OrderStatus.CANCELLED }, label = { Text("Cancelled") })
                    FilterChip(selected = statusFilter == OrderStatus.REFUNDED, onClick = { statusFilter = OrderStatus.REFUNDED }, label = { Text("Refunded") })
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                        Text(
                            text = "No POS orders recorded yet in this branch. Place an order in POS Terminal to generate an Invoice and separate KOT.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(filtered, key = { it.orderId }) { order ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("${order.orderNumber} • ${order.orderType.label}", style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
                                Text("${order.invoiceNumber} | ${order.kotNumber} | ${dateFmt.format(Date(order.createdAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(
                                text = order.status.label.uppercase(),
                                containerColor = when (order.status) {
                                    OrderStatus.CANCELLED, OrderStatus.REFUNDED -> StatusCrimsonBg
                                    else -> StatusEmeraldBg
                                },
                                contentColor = when (order.status) {
                                    OrderStatus.CANCELLED, OrderStatus.REFUNDED -> StatusCrimson
                                    else -> StatusEmerald
                                }
                            )
                        }
                        Text(
                            text = "${order.customerName ?: "Walk-in"} • ${order.items.sumOf { it.quantity }} items • ${centsToCurrency(order.totalCents)} (${order.paymentMethod.label})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!order.cancellationReason.isNullOrBlank()) {
                            Text(
                                text = "Reason: ${order.cancellationReason} (Approved by ${order.approvedBy ?: "Manager"})",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusCrimson
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onOpenInvoice(order.orderId) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Receipt, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Invoice")
                            }
                            OutlinedButton(
                                onClick = { onOpenKot(order.orderId) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Restaurant, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("KOT")
                            }
                        }
                        if (canApprove && order.status == OrderStatus.CONFIRMED) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = {
                                        reasonInput = ""
                                        cancelTarget = order
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel Order", color = StatusAmber)
                                }
                                TextButton(
                                    onClick = {
                                        reasonInput = ""
                                        refundTarget = order
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Approve Refund", color = StatusCrimson)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    cancelTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            containerColor = ObsidianSurfaceElevated,
            title = { Text("Cancel ${target.orderNumber}", color = CraveGoldBright) },
            text = {
                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text("Cancellation Reason (Required)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCancelOrder(target.orderId, reasonInput)
                        cancelTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson)
                ) { Text("Confirm Cancel") }
            },
            dismissButton = {
                TextButton(onClick = { cancelTarget = null }) { Text("Back") }
            }
        )
    }

    refundTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { refundTarget = null },
            containerColor = ObsidianSurfaceElevated,
            title = { Text("Refund ${target.invoiceNumber}", color = CraveGoldBright) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Refund amount: ${centsToCurrency(target.paidCents)}", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = { Text("Manager Refund Reason (Required)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRefundOrder(target.orderId, reasonInput)
                        refundTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson)
                ) { Text("Approve Refund") }
            },
            dismissButton = {
                TextButton(onClick = { refundTarget = null }) { Text("Back") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashClosingScreen(
    session: UserSession,
    uiState: PosUiState,
    onBack: () -> Unit,
    onSubmitClosing: (Long, String) -> Unit,
    onClearFeedback: () -> Unit
) {
    BackHandler(onBack = onBack)

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearFeedback()
        }
    }

    val activeOrders = uiState.orders.filter { it.status != OrderStatus.CANCELLED }
    val refundedOrders = uiState.orders.filter { it.status == OrderStatus.REFUNDED }
    val cashSales = activeOrders.sumOf { o ->
        if (o.paymentMethod == PaymentMethod.CASH) o.paidCents else 0L
    }
    val nonCashSales = activeOrders.sumOf { o ->
        if (o.paymentMethod != PaymentMethod.CASH) o.paidCents else 0L
    }
    val refundsCents = refundedOrders.sumOf { it.paidCents }
    val expectedCash = (cashSales - refundsCents).coerceAtLeast(0L)

    var countedCashInput by remember(expectedCash) {
        mutableStateOf("%.2f".format(Locale.US, expectedCash / 100.0))
    }
    var notesInput by remember { mutableStateOf("") }
    val countedCents = ((countedCashInput.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0L)
    val varianceCents = countedCents - expectedCash

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianSurface),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CraveGoldBright)
                    }
                },
                title = {
                    Column {
                        Text("Daily Cash Closing", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                        Text(session.branchName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Register Reconciliation", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                        BillingMetricRow("Cash Drawer Sales", centsToCurrency(cashSales))
                        BillingMetricRow("Card / Digital / Bank", centsToCurrency(nonCashSales))
                        BillingMetricRow("Approved Refunds", centsToCurrency(refundsCents))
                        HorizontalDivider(color = ObsidianBorder)
                        BillingMetricRow("Expected Cash in Drawer", centsToCurrency(expectedCash), bold = true)

                        OutlinedTextField(
                            value = countedCashInput,
                            onValueChange = { countedCashInput = it },
                            label = { Text("Actual Counted Cash (PKR)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cash_closing_counted_input")
                        )

                        BillingMetricRow("Cash Variance", centsToCurrency(varianceCents), bold = true)

                        OutlinedTextField(
                            value = notesInput,
                            onValueChange = { notesInput = it },
                            label = { Text("Shift Closing Notes") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = { onSubmitClosing(countedCents, notesInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("submit_cash_closing_button")
                        ) {
                            Text("Close Register & Save Audit Record")
                        }
                    }
                }
            }

            if (uiState.cashClosings.isNotEmpty()) {
                item {
                    Text("Previous Cash Closings", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                }
                items(uiState.cashClosings, key = { it.closingId }) { closing ->
                    Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(closing.businessDate, style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
                                Text("Variance: ${centsToCurrency(closing.varianceCents)}", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "Expected ${centsToCurrency(closing.expectedCashCents)} | Counted ${centsToCurrency(closing.countedCashCents)} • By ${closing.closedByName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BillingMetricRow(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, color = if (bold) CraveGoldBright else MaterialTheme.colorScheme.onSurface)
    }
}
