package com.example.ui.customers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.domain.model.CustomerFeedbackRecord
import com.example.domain.model.CustomerProfileRecord
import com.example.domain.model.LoyaltyConfigRecord
import com.example.domain.model.LoyaltyRewardRecord
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
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusEmeraldBg
import com.example.ui.theme.StatusSky
import com.example.ui.viewmodel.CustomerLoyaltyUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerCrmScreen(
    session: UserSession,
    uiState: CustomerLoyaltyUiState,
    onOpenDrawer: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onSelectTierFilter: (String?) -> Unit,
    onSaveCustomer: (String?, String, String, String, String, String, Long?) -> Unit,
    onUpdateLoyaltyConfig: (Double, Long, Int, Int, Int, Int, Boolean, Int) -> Unit,
    onSaveReward: (String?, String, String, Int, Long) -> Unit,
    onAdjustPoints: (String, Int, String) -> Unit,
    onRedeemReward: (String, LoyaltyRewardRecord) -> Unit,
    onRecordFeedback: (String, Int, String) -> Unit,
    onRunExpirySweep: () -> Unit,
    onClearFeedback: () -> Unit,
    onOpenSmartLoyalty: (String) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCustomerDialog by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<CustomerProfileRecord?>(null) }
    var viewingCustomer by remember { mutableStateOf<CustomerProfileRecord?>(null) }
    var showRewardDialog by remember { mutableStateOf(false) }
    var adjustingCustomer by remember { mutableStateOf<CustomerProfileRecord?>(null) }
    var feedbackCustomer by remember { mutableStateOf<CustomerProfileRecord?>(null) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    // Keep profile modal synchronized with live DB updates
    val liveViewingCustomer = viewingCustomer?.let { current ->
        uiState.customers.firstOrNull { it.customerId == current.customerId } ?: current
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
                        Text("Customers & Loyalty CRM", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                        Text(session.branchName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0 && session.capabilities.canManageCustomers) {
                FloatingActionButton(
                    onClick = {
                        editingCustomer = null
                        showCustomerDialog = true
                    },
                    containerColor = CraveGold,
                    contentColor = OnCraveGold,
                    modifier = Modifier.testTag("add_customer_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Register Customer")
                }
            } else if (selectedTab == 2 && session.capabilities.canConfigureLoyaltyAndAdjust) {
                FloatingActionButton(
                    onClick = { showRewardDialog = true },
                    containerColor = CraveGold,
                    contentColor = OnCraveGold
                ) {
                    Icon(Icons.Filled.CardGiftcard, contentDescription = "Add Loyalty Reward")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = ObsidianSurface, contentColor = CraveGoldBright) {
                listOf("Customers (${uiState.customers.size})", "Loyalty Rules", "Rewards & Log").forEachIndexed { idx, label ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(label, maxLines = 1) })
                }
            }

            when (selectedTab) {
                0 -> CustomersListTab(
                    session = session,
                    uiState = uiState,
                    onSearchChanged = onSearchChanged,
                    onSelectTier = onSelectTierFilter,
                    onViewProfile = { viewingCustomer = it },
                    onEdit = {
                        editingCustomer = it
                        showCustomerDialog = true
                    }
                )
                1 -> LoyaltyConfigTab(
                    config = uiState.config,
                    canEdit = session.capabilities.canConfigureLoyaltyAndAdjust,
                    onSaveConfig = onUpdateLoyaltyConfig,
                    onRunExpirySweep = onRunExpirySweep
                )
                2 -> RewardsAndHistoryTab(uiState)
            }
        }
    }

    if (showCustomerDialog) {
        CustomerEditorDialog(
            existing = editingCustomer,
            canEditBalance = session.capabilities.canViewCustomerBalance,
            onDismiss = { showCustomerDialog = false },
            onSave = { name, phone, email, address, notes, balance ->
                onSaveCustomer(editingCustomer?.customerId, name, phone, email, address, notes, balance)
                showCustomerDialog = false
            }
        )
    }

    liveViewingCustomer?.let { cust ->
        val custOrders = uiState.orders.filter {
            it.customerName.equals(cust.name, ignoreCase = true)
        }
        val custFeedback = uiState.feedbackList.filter { it.customerId == cust.customerId }
        CustomerProfileDialog(
            session = session,
            customer = cust,
            orders = custOrders,
            rewards = uiState.rewards.filter { it.isActive },
            feedbackList = custFeedback,
            onDismiss = { viewingCustomer = null },
            onEdit = {
                editingCustomer = cust
                showCustomerDialog = true
            },
            onAdjustPoints = { adjustingCustomer = cust },
            onRedeemReward = { rew -> onRedeemReward(cust.customerId, rew) },
            onAddFeedback = { feedbackCustomer = cust },
            onOpenSmartLoyalty = {
                viewingCustomer = null
                onOpenSmartLoyalty(cust.customerId)
            }
        )
    }

    adjustingCustomer?.let { cust ->
        AdjustPointsDialog(
            customer = cust,
            onDismiss = { adjustingCustomer = null },
            onConfirm = { delta, reason ->
                onAdjustPoints(cust.customerId, delta, reason)
                adjustingCustomer = null
            }
        )
    }

    feedbackCustomer?.let { cust ->
        AddFeedbackDialog(
            customer = cust,
            onDismiss = { feedbackCustomer = null },
            onConfirm = { rating, comment ->
                onRecordFeedback(cust.customerId, rating, comment)
                feedbackCustomer = null
            }
        )
    }

    if (showRewardDialog) {
        AddRewardDialog(
            onDismiss = { showRewardDialog = false },
            onConfirm = { title, desc, pts, valueCents ->
                onSaveReward(null, title, desc, pts, valueCents)
                showRewardDialog = false
            }
        )
    }
}

@Composable
private fun CustomersListTab(
    session: UserSession,
    uiState: CustomerLoyaltyUiState,
    onSearchChanged: (String) -> Unit,
    onSelectTier: (String?) -> Unit,
    onViewProfile: (CustomerProfileRecord) -> Unit,
    onEdit: (CustomerProfileRecord) -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChanged,
                placeholder = { Text("Search name, phone, or email...") },
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
                listOf(null to "All", "Member" to "Member", "Silver" to "Silver", "Gold VIP" to "Gold VIP", "Platinum" to "Platinum").forEach { (tierKey, label) ->
                    FilterChip(
                        selected = uiState.selectedTierFilter == tierKey,
                        onClick = { onSelectTier(tierKey) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                    )
                }
            }
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            items(uiState.filteredCustomers, key = { it.customerId }) { cust ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewProfile(cust) }
                        .testTag("customer_card_${cust.customerId}")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = CraveGoldBright, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(cust.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text("${cust.phone} • ${cust.email.ifBlank { "No email" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            StatusPill(text = "${cust.tier} (${cust.discountPercent}%)", containerColor = CraveGoldContainer, contentColor = CraveGoldBright)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Loyalty: ${cust.loyaltyPoints} pts", style = MaterialTheme.typography.bodyMedium, color = StatusEmerald, fontWeight = FontWeight.Medium)
                            Text("Lifetime: ${centsToCurrency(cust.totalPurchasesCents)}", style = MaterialTheme.typography.bodyMedium, color = CraveGoldBright)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = cust.lastOrderAt?.let { "Last order: ${dateFmt.format(Date(it))}" } ?: "No orders yet",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (session.capabilities.canViewCustomerBalance) {
                                Text("Acct Bal: ${centsToCurrency(cust.accountBalanceCents)}", style = MaterialTheme.typography.labelSmall, color = StatusSky)
                            }
                        }
                        if (cust.feedbackSummary.isNotBlank()) {
                            Text(cust.feedbackSummary, style = MaterialTheme.typography.labelSmall, color = StatusAmber)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoyaltyConfigTab(
    config: LoyaltyConfigRecord?,
    canEdit: Boolean,
    onSaveConfig: (Double, Long, Int, Int, Int, Int, Boolean, Int) -> Unit,
    onRunExpirySweep: () -> Unit
) {
    val cfg = config ?: return
    var earnRate by remember(cfg) { mutableStateOf(cfg.earnRatePerDollar.toString()) }
    var redeemCents by remember(cfg) { mutableStateOf(cfg.redeemCentsPerPoint.toString()) }
    var minRedeem by remember(cfg) { mutableStateOf(cfg.minRedeemPoints.toString()) }
    var silverPts by remember(cfg) { mutableStateOf(cfg.silverThresholdPoints.toString()) }
    var goldPts by remember(cfg) { mutableStateOf(cfg.goldThresholdPoints.toString()) }
    var platPts by remember(cfg) { mutableStateOf(cfg.platinumThresholdPoints.toString()) }
    var expiryEnabled by remember(cfg) { mutableStateOf(cfg.expiryEnabled) }
    var expiryDays by remember(cfg) { mutableStateOf(cfg.expiryDays.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Loyalty Program Configuration", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
        if (!canEdit) {
            StatusPill(text = "READ ONLY • MANAGER OR ADMIN REQUIRED TO EDIT", containerColor = ObsidianSurfaceElevated, contentColor = StatusAmber)
        }
        OutlinedTextField(value = earnRate, onValueChange = { earnRate = it }, enabled = canEdit, label = { Text("Points Earned per PKR 1 Spent") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = redeemCents, onValueChange = { redeemCents = it }, enabled = canEdit, label = { Text("Redeem Value (paisa per point)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = minRedeem, onValueChange = { minRedeem = it }, enabled = canEdit, label = { Text("Minimum Redeemable Points") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = silverPts, onValueChange = { silverPts = it }, enabled = canEdit, label = { Text("Silver Pts") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            OutlinedTextField(value = goldPts, onValueChange = { goldPts = it }, enabled = canEdit, label = { Text("Gold Pts") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            OutlinedTextField(value = platPts, onValueChange = { platPts = it }, enabled = canEdit, label = { Text("Plat Pts") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Enable Point Expiry on Inactivity", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = expiryEnabled, onCheckedChange = { if (canEdit) expiryEnabled = it }, enabled = canEdit)
        }
        if (expiryEnabled) {
            OutlinedTextField(value = expiryDays, onValueChange = { expiryDays = it }, enabled = canEdit, label = { Text("Expire After Inactivity (Days)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        }
        if (canEdit) {
            Button(
                onClick = {
                    onSaveConfig(
                        earnRate.toDoubleOrNull() ?: 1.0,
                        redeemCents.toLongOrNull() ?: 5L,
                        minRedeem.toIntOrNull() ?: 50,
                        silverPts.toIntOrNull() ?: 150,
                        goldPts.toIntOrNull() ?: 400,
                        platPts.toIntOrNull() ?: 800,
                        expiryEnabled,
                        expiryDays.toIntOrNull() ?: 365
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save Loyalty Rules") }

            if (expiryEnabled) {
                OutlinedButton(onClick = onRunExpirySweep, border = BorderStroke(1.dp, StatusAmber), modifier = Modifier.fillMaxWidth()) {
                    Text("Run Expiry Check Now", color = StatusAmber)
                }
            }
        }
    }
}

@Composable
private fun RewardsAndHistoryTab(uiState: CustomerLoyaltyUiState) {
    val dateFmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.US) }
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item { Text("Configured Rewards", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright) }
        items(uiState.rewards, key = { it.rewardId }) { rew ->
            Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rew.title, style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
                        Text(rew.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusPill(text = "${rew.pointsRequired} pts • ${centsToCurrency(rew.discountCentsValue)}", containerColor = StatusEmeraldBg, contentColor = StatusEmerald)
                }
            }
        }
        item {
            Spacer(Modifier.height(6.dp))
            Text("Loyalty Transaction History", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
        }
        items(uiState.transactions, key = { it.txId }) { tx ->
            Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${tx.customerName} • ${tx.txType}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(tx.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${dateFmt.format(Date(tx.createdAt))} • By ${tx.actorName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = if (tx.pointsDelta >= 0) "+${tx.pointsDelta} pts" else "${tx.pointsDelta} pts",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (tx.pointsDelta >= 0) StatusEmerald else StatusAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerProfileDialog(
    session: UserSession,
    customer: CustomerProfileRecord,
    orders: List<PosOrderRecord>,
    rewards: List<LoyaltyRewardRecord>,
    feedbackList: List<CustomerFeedbackRecord>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onAdjustPoints: () -> Unit,
    onRedeemReward: (LoyaltyRewardRecord) -> Unit,
    onAddFeedback: () -> Unit,
    onOpenSmartLoyalty: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(customer.name, color = CraveGoldBright, modifier = Modifier.weight(1f))
                StatusPill(text = customer.tier, containerColor = CraveGoldContainer, contentColor = CraveGoldBright)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Phone: ${customer.phone}", style = MaterialTheme.typography.bodySmall)
                if (customer.email.isNotBlank()) Text("Email: ${customer.email}", style = MaterialTheme.typography.bodySmall)
                if (customer.address.isNotBlank()) Text("Address: ${customer.address}", style = MaterialTheme.typography.bodySmall)
                if (customer.notes.isNotBlank()) Text("Notes: ${customer.notes}", style = MaterialTheme.typography.bodySmall, color = StatusAmber)
                HorizontalDivider(color = ObsidianBorder)
                Text("Points: ${customer.loyaltyPoints} pts • Lifetime Spend: ${centsToCurrency(customer.totalPurchasesCents)}", style = MaterialTheme.typography.titleSmall, color = StatusEmerald)
                if (session.capabilities.canViewCustomerBalance) {
                    Text("Account Balance: ${centsToCurrency(customer.accountBalanceCents)}", style = MaterialTheme.typography.bodySmall, color = StatusSky)
                }

                if (rewards.isNotEmpty()) {
                    Text("Redeem Reward:", style = MaterialTheme.typography.labelLarge, color = CraveGold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rewards.forEach { rew ->
                            OutlinedButton(
                                onClick = { onRedeemReward(rew) },
                                enabled = customer.loyaltyPoints >= rew.pointsRequired
                            ) {
                                Text("${rew.title} (${rew.pointsRequired} pts)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                    OutlinedButton(onClick = onAddFeedback, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Feedback")
                    }
                }
                if (session.capabilities.canConfigureLoyaltyAndAdjust) {
                    Button(
                        onClick = onAdjustPoints,
                        colors = ButtonDefaults.buttonColors(containerColor = CraveGoldContainer, contentColor = CraveGoldBright),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Manager Point Adjustment") }
                }
                OutlinedButton(
                    onClick = onOpenSmartLoyalty,
                    border = BorderStroke(1.dp, CraveGold),
                    modifier = Modifier.fillMaxWidth().testTag("crm_open_smart_loyalty_card_button")
                ) {
                    Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = CraveGoldBright, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open 6-Stamp Smart Loyalty Card", color = CraveGoldBright)
                }

                HorizontalDivider(color = ObsidianBorder)
                Text("Order History (${orders.size})", style = MaterialTheme.typography.labelLarge, color = CraveGold)
                if (orders.isEmpty()) {
                    Text("No POS orders linked yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    orders.take(5).forEach { ord ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${ord.orderNumber} (${ord.status.label})", style = MaterialTheme.typography.bodySmall)
                            Text(centsToCurrency(ord.totalCents), style = MaterialTheme.typography.bodySmall, color = CraveGoldBright)
                        }
                    }
                }
                if (feedbackList.isNotEmpty()) {
                    Text("Recent Feedback", style = MaterialTheme.typography.labelLarge, color = CraveGold)
                    feedbackList.take(3).forEach { fb ->
                        Text("${fb.rating}★ • ${fb.comment}", style = MaterialTheme.typography.bodySmall, color = StatusAmber)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun CustomerEditorDialog(
    existing: CustomerProfileRecord?,
    canEditBalance: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Long?) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var balanceText by remember { mutableStateOf(existing?.let { "%.2f".format(Locale.US, it.accountBalanceCents / 100.0) } ?: "0.00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text(if (existing == null) "Register Customer" else "Edit Customer", color = CraveGoldBright) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Customer Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Customer Notes / Preferences") }, modifier = Modifier.fillMaxWidth())
                if (canEditBalance) {
                    OutlinedTextField(
                        value = balanceText,
                        onValueChange = { balanceText = it },
                        label = { Text("Authorized Account Balance (PKR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bal = if (canEditBalance) ((balanceText.toDoubleOrNull() ?: 0.0) * 100).toLong() else null
                    onSave(name, phone, email, address, notes, bal)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AdjustPointsDialog(
    customer: CustomerProfileRecord,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var deltaText by remember { mutableStateOf("50") }
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Adjust Points • ${customer.name}", color = CraveGoldBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Current Balance: ${customer.loyaltyPoints} pts", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = deltaText, onValueChange = { deltaText = it }, label = { Text("Points Delta (use - to deduct)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Manager Approval Reason *") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(deltaText.toIntOrNull() ?: 0, reason) },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Apply Adjustment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddFeedbackDialog(
    customer: CustomerProfileRecord,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var rating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Customer Feedback • ${customer.name}", color = CraveGoldBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { star ->
                        FilterChip(selected = rating == star, onClick = { rating = star }, label = { Text("$star★") })
                    }
                }
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Feedback Comment *") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(rating, comment) },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Save Feedback") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddRewardDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var ptsText by remember { mutableStateOf("150") }
    var valueText by remember { mutableStateOf("7.50") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("New Loyalty Reward", color = CraveGoldBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Reward Title *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ptsText, onValueChange = { ptsText = it }, label = { Text("Points Required") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = valueText, onValueChange = { valueText = it }, label = { Text("Discount Value (PKR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pts = ptsText.toIntOrNull() ?: 0
                    val cents = ((valueText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                    onConfirm(title, desc, pts, cents)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Save Reward") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
