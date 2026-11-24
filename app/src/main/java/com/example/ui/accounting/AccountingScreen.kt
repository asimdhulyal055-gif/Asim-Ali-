package com.example.ui.accounting

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.example.domain.model.AccountCategory
import com.example.domain.model.AccountType
import com.example.domain.model.AccountingCalculator
import com.example.domain.model.CashBankAccountSummary
import com.example.domain.model.CashbookTransactionRecord
import com.example.domain.model.CashbookTxType
import com.example.domain.model.ChartOfAccountRecord
import com.example.domain.model.CustomerProfileRecord
import com.example.domain.model.JournalDraftLine
import com.example.domain.model.JournalEntryRecord
import com.example.domain.model.JournalEntryStatus
import com.example.domain.model.PartyLedgerReport
import com.example.domain.model.SupplierSummary
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
import com.example.ui.theme.StatusSky
import com.example.ui.theme.StatusSkyBg
import com.example.ui.viewmodel.AccountingTab
import com.example.ui.viewmodel.AccountingUiState
import com.example.ui.viewmodel.DateRangeFilterPreset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingScreen(
    session: UserSession,
    uiState: AccountingUiState,
    onOpenDrawer: () -> Unit,
    onSelectBranchScope: (String?) -> Unit,
    onSelectTab: (AccountingTab) -> Unit,
    onSearchChanged: (String) -> Unit,
    onSelectDatePreset: (DateRangeFilterPreset) -> Unit,
    onSyncTransactions: () -> Unit,
    onSelectCustomer: (String) -> Unit,
    onSelectSupplier: (String) -> Unit,
    onSaveAccount: (
        branchId: String,
        accountId: String?,
        code: String,
        name: String,
        type: AccountType,
        category: AccountCategory,
        openingCents: Long,
        isActive: Boolean
    ) -> Unit,
    onPostManualJournal: (
        branchId: String,
        entryDateMillis: Long,
        reference: String,
        description: String,
        lines: List<JournalDraftLine>
    ) -> Unit,
    onReverseJournal: (entryId: String, reason: String) -> Unit,
    onRecordCashbookEntry: (
        branchId: String,
        txType: CashbookTxType,
        cashBankAccId: String,
        counterAccId: String,
        amountCents: Long,
        txDateMillis: Long,
        reference: String,
        counterparty: String,
        description: String
    ) -> Unit,
    onRecordCustomerPayment: (
        branchId: String,
        customerId: String,
        cashBankAccId: String,
        amountCents: Long,
        reference: String,
        paymentDateMillis: Long,
        notes: String
    ) -> Unit,
    onClearFeedback: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    var showAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<ChartOfAccountRecord?>(null) }
    var showJournalDialog by remember { mutableStateOf(false) }
    var reversingEntry by remember { mutableStateOf<JournalEntryRecord?>(null) }
    var showCashbookDialog by remember { mutableStateOf(false) }
    var showCustomerPaymentDialog by remember { mutableStateOf(false) }
    var ledgerPartyMode by remember { mutableStateOf("CUSTOMERS") }
    var branchMenuExpanded by remember { mutableStateOf(false) }

    val activeBranchIdForPosting = uiState.selectedBranchId ?: session.branchId
    val branchAccounts = remember(uiState.accounts, activeBranchIdForPosting) {
        uiState.accounts.filter { it.branchId == activeBranchIdForPosting && it.isActive }
    }

    LaunchedEffect(uiState.feedbackMessage) {
        val msg = uiState.feedbackMessage
        if (!msg.isNullOrBlank()) {
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
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("accounting_menu_button")
                    ) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open Drawer", tint = CraveGoldBright)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Accounting & General Ledger",
                            style = MaterialTheme.typography.titleLarge,
                            color = CraveGoldBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.scopeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (session.capabilities.canViewConsolidatedReports) {
                        TextButton(
                            onClick = { branchMenuExpanded = true },
                            modifier = Modifier.testTag("accounting_branch_selector")
                        ) {
                            Text(
                                text = if (uiState.selectedBranchId == null) "ALL" else "BRANCH",
                                color = CraveGoldBright,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        DropdownMenu(
                            expanded = branchMenuExpanded,
                            onDismissRequest = { branchMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Consolidated (All Branches)") },
                                onClick = {
                                    branchMenuExpanded = false
                                    onSelectBranchScope(null)
                                }
                            )
                            uiState.branches.forEach { br ->
                                DropdownMenuItem(
                                    text = { Text("${br.code} — ${br.name}") },
                                    onClick = {
                                        branchMenuExpanded = false
                                        onSelectBranchScope(br.branchId)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onSyncTransactions,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("accounting_sync_button")
                    ) {
                        Icon(
                            Icons.Filled.Sync,
                            contentDescription = "Synchronize ERP Transactions",
                            tint = if (uiState.isSyncing) StatusAmber else CraveGoldBright
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            when (uiState.selectedTab) {
                AccountingTab.COA -> {
                    FloatingActionButton(
                        onClick = {
                            editingAccount = null
                            showAccountDialog = true
                        },
                        containerColor = CraveGold,
                        contentColor = OnCraveGold,
                        modifier = Modifier.testTag("add_coa_fab")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Account")
                    }
                }
                AccountingTab.JOURNALS -> if (session.capabilities.canPostJournalEntries) {
                    FloatingActionButton(
                        onClick = { showJournalDialog = true },
                        containerColor = CraveGold,
                        contentColor = OnCraveGold,
                        modifier = Modifier.testTag("add_journal_fab")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Post Journal Entry")
                    }
                }
                AccountingTab.CASHBOOK -> if (session.capabilities.canManageCashBankAccounts) {
                    FloatingActionButton(
                        onClick = { showCashbookDialog = true },
                        containerColor = CraveGold,
                        contentColor = OnCraveGold,
                        modifier = Modifier.testTag("add_cashbook_fab")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Record Cashbook Entry")
                    }
                }
                AccountingTab.LEDGERS -> if (session.capabilities.canManageCashBankAccounts && ledgerPartyMode == "CUSTOMERS") {
                    FloatingActionButton(
                        onClick = { showCustomerPaymentDialog = true },
                        containerColor = CraveGold,
                        contentColor = OnCraveGold,
                        modifier = Modifier.testTag("record_customer_payment_fab")
                    ) {
                        Icon(Icons.Filled.Payments, contentDescription = "Record Customer Payment")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Financial KPI Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AccountingKpiPill("Cash & Bank", centsToCurrency(uiState.totalCashAndBankCents), StatusEmerald, StatusEmeraldBg)
                AccountingKpiPill("Receivables (AR)", centsToCurrency(uiState.totalReceivablesCents), StatusSky, StatusSkyBg)
                AccountingKpiPill("Payables (AP)", centsToCurrency(uiState.totalPayablesCents), StatusAmber, StatusAmberBg)
                AccountingKpiPill(
                    "Net Profit",
                    centsToCurrency(uiState.netProfitCents),
                    if (uiState.netProfitCents >= 0L) CraveGoldBright else StatusCrimson,
                    if (uiState.netProfitCents >= 0L) CraveGoldContainer else StatusCrimsonBg
                )
            }

            // Tabs
            TabRow(
                selectedTabIndex = AccountingTab.entries.indexOf(uiState.selectedTab),
                containerColor = ObsidianSurface,
                contentColor = CraveGoldBright,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(positions[AccountingTab.entries.indexOf(uiState.selectedTab)]),
                        color = CraveGold
                    )
                }
            ) {
                AccountingTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        text = {
                            Text(
                                text = when (tab) {
                                    AccountingTab.COA -> "Accounts"
                                    AccountingTab.JOURNALS -> "Journals"
                                    AccountingTab.CASHBOOK -> "Cashbook"
                                    AccountingTab.LEDGERS -> "Ledgers"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("accounting_tab_${tab.name}")
                    )
                }
            }

            // Search & Date Filters
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Search account code, reference, description, party...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = CraveGold) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("accounting_search_input")
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateRangeFilterPreset.entries.filter { it != DateRangeFilterPreset.CUSTOM }.forEach { preset ->
                        FilterChip(
                            selected = uiState.datePreset == preset,
                            onClick = { onSelectDatePreset(preset) },
                            label = { Text(preset.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CraveGoldContainer,
                                selectedLabelColor = CraveGoldBright
                            )
                        )
                    }
                }
            }

            when (uiState.selectedTab) {
                AccountingTab.COA -> ChartOfAccountsList(
                    accounts = uiState.filteredAccounts,
                    onEditAccount = { acc ->
                        editingAccount = acc
                        showAccountDialog = true
                    }
                )
                AccountingTab.JOURNALS -> JournalEntriesList(
                    entries = uiState.filteredJournals,
                    dateFmt = dateFmt,
                    canReverse = session.capabilities.canApproveFinancialAdjustments,
                    onRequestReverse = { entry -> reversingEntry = entry }
                )
                AccountingTab.CASHBOOK -> CashbookList(
                    summaries = uiState.cashBankSummaries,
                    transactions = uiState.filteredCashbook,
                    dateFmt = dateFmt
                )
                AccountingTab.LEDGERS -> PartyLedgersView(
                    mode = ledgerPartyMode,
                    onModeChange = { ledgerPartyMode = it },
                    customers = uiState.customers,
                    suppliers = uiState.suppliers,
                    selectedCustomerId = uiState.selectedCustomerId,
                    selectedSupplierId = uiState.selectedSupplierId,
                    customerLedger = uiState.customerLedger,
                    supplierLedger = uiState.supplierLedger,
                    onSelectCustomer = onSelectCustomer,
                    onSelectSupplier = onSelectSupplier,
                    dateFmt = dateFmt
                )
            }
        }
    }

    if (showAccountDialog) {
        ChartAccountDialog(
            existing = editingAccount,
            onDismiss = { showAccountDialog = false },
            onSave = { code, name, type, cat, openCents, active ->
                onSaveAccount(
                    editingAccount?.branchId ?: activeBranchIdForPosting,
                    editingAccount?.accountId,
                    code,
                    name,
                    type,
                    cat,
                    openCents,
                    active
                )
                showAccountDialog = false
            }
        )
    }

    if (showJournalDialog) {
        ManualJournalEntryDialog(
            accounts = branchAccounts,
            onDismiss = { showJournalDialog = false },
            onPost = { ref, desc, lines ->
                onPostManualJournal(
                    activeBranchIdForPosting,
                    System.currentTimeMillis(),
                    ref,
                    desc,
                    lines
                )
                showJournalDialog = false
            }
        )
    }

    reversingEntry?.let { entry ->
        ReverseJournalDialog(
            entry = entry,
            onDismiss = { reversingEntry = null },
            onConfirmReverse = { reason ->
                onReverseJournal(entry.entryId, reason)
                reversingEntry = null
            }
        )
    }

    if (showCashbookDialog) {
        CashbookTransactionDialog(
            accounts = branchAccounts,
            onDismiss = { showCashbookDialog = false },
            onSave = { txType, cashAccId, offsetAccId, amtCents, ref, party, desc ->
                onRecordCashbookEntry(
                    activeBranchIdForPosting,
                    txType,
                    cashAccId,
                    offsetAccId,
                    amtCents,
                    System.currentTimeMillis(),
                    ref,
                    party,
                    desc
                )
                showCashbookDialog = false
            }
        )
    }

    if (showCustomerPaymentDialog) {
        CustomerReceiptDialog(
            customers = uiState.customers.filter { uiState.selectedBranchId == null || it.branchId == activeBranchIdForPosting },
            cashBankAccounts = branchAccounts.filter { it.isCashOrBank },
            preselectedCustomerId = uiState.selectedCustomerId,
            onDismiss = { showCustomerPaymentDialog = false },
            onSave = { custId, cashAccId, amtCents, ref, notes ->
                onRecordCustomerPayment(
                    activeBranchIdForPosting,
                    custId,
                    cashAccId,
                    amtCents,
                    ref,
                    System.currentTimeMillis(),
                    notes
                )
                showCustomerPaymentDialog = false
            }
        )
    }
}

@Composable
private fun AccountingKpiPill(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    bgColor: androidx.compose.ui.graphics.Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, ObsidianBorder),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
private fun ChartOfAccountsList(
    accounts: List<ChartOfAccountRecord>,
    onEditAccount: (ChartOfAccountRecord) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (accounts.isEmpty()) {
            item {
                Text(
                    "No Chart of Accounts entries found.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        items(accounts, key = { it.accountId }) { acc ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, ObsidianBorder),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(acc.accountCode, CraveGoldContainer, CraveGoldBright)
                            Text(
                                text = acc.accountName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${acc.accountType.label} • ${acc.accountCategory.label}${if (!acc.isActive) " • INACTIVE" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Open: ${centsToCurrency(acc.openingBalanceCents)} | Dr: ${centsToCurrency(acc.debitTurnoverCents)} | Cr: ${centsToCurrency(acc.creditTurnoverCents)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = centsToCurrency(acc.currentBalanceCents),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (acc.currentBalanceCents >= 0L) StatusEmerald else StatusCrimson
                        )
                        IconButton(
                            onClick = { onEditAccount(acc) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit Account", tint = CraveGold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalEntriesList(
    entries: List<JournalEntryRecord>,
    dateFmt: SimpleDateFormat,
    canReverse: Boolean,
    onRequestReverse: (JournalEntryRecord) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (entries.isEmpty()) {
            item {
                Text(
                    "No posted journal entries match the current filter.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(entries, key = { it.entryId }) { je ->
            val isReversed = je.status == JournalEntryStatus.REVERSED
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, if (isReversed) StatusCrimson.copy(alpha = 0.45f) else ObsidianBorder),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${je.entryNumber} • ${je.reference}",
                                style = MaterialTheme.typography.titleSmall,
                                color = CraveGoldBright
                            )
                            Text(
                                text = "${dateFmt.format(Date(je.entryDateMillis))} • ${je.sourceModule.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = je.status.label.uppercase(),
                            containerColor = if (isReversed) StatusCrimsonBg else StatusEmeraldBg,
                            contentColor = if (isReversed) StatusCrimson else StatusEmerald
                        )
                    }
                    Text(
                        text = je.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    HorizontalDivider(color = ObsidianBorder)
                    je.lines.forEach { line ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${line.accountCode} ${line.accountName}${line.partyName?.let { " ($it)" } ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (line.debitCents > 0L) "Dr ${centsToCurrency(line.debitCents)}" else "Cr ${centsToCurrency(line.creditCents)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (line.debitCents > 0L) StatusSky else CraveGoldBright
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Balanced Total: ${centsToCurrency(je.totalDebitCents)} • By ${je.postedByName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (canReverse && !isReversed) {
                            TextButton(
                                onClick = { onRequestReverse(je) },
                                modifier = Modifier.testTag("reverse_je_${je.entryId}")
                            ) {
                                Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusCrimson)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reverse", color = StatusCrimson)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CashbookList(
    summaries: List<CashBankAccountSummary>,
    transactions: List<CashbookTransactionRecord>,
    dateFmt: SimpleDateFormat
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Cash & Bank Account Balances",
                style = MaterialTheme.typography.titleMedium,
                color = CraveGoldBright
            )
        }
        items(summaries, key = { it.account.accountId }) { s ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                border = BorderStroke(1.dp, ObsidianBorder),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${s.account.accountCode} • ${s.account.accountName}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(centsToCurrency(s.closingBalanceCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StatusEmerald)
                    }
                    Text(
                        text = "Opening: ${centsToCurrency(s.openingBalanceCents)} | Inflows: +${centsToCurrency(s.totalInflowsCents)} | Outflows: -${centsToCurrency(s.totalOutflowsCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Cashbook Receipts & Payments (${transactions.size})",
                style = MaterialTheme.typography.titleMedium,
                color = CraveGoldBright
            )
        }
        items(transactions, key = { it.cashbookId }) { tx ->
            val inflow = tx.txType.isInflow
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, ObsidianBorder),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${tx.reference} • ${tx.counterpartyName}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(tx.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${dateFmt.format(Date(tx.txDateMillis))} • ${tx.accountName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = "${if (inflow) "+" else "-"}${centsToCurrency(tx.amountCents)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (inflow) StatusEmerald else StatusCrimson
                    )
                }
            }
        }
    }
}

@Composable
private fun PartyLedgersView(
    mode: String,
    onModeChange: (String) -> Unit,
    customers: List<CustomerProfileRecord>,
    suppliers: List<SupplierSummary>,
    selectedCustomerId: String?,
    selectedSupplierId: String?,
    customerLedger: PartyLedgerReport?,
    supplierLedger: PartyLedgerReport?,
    onSelectCustomer: (String) -> Unit,
    onSelectSupplier: (String) -> Unit,
    dateFmt: SimpleDateFormat
) {
    val activeReport = if (mode == "CUSTOMERS") customerLedger else supplierLedger
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == "CUSTOMERS",
                    onClick = { onModeChange("CUSTOMERS") },
                    label = { Text("Customer Ledger (${customers.size})") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                )
                FilterChip(
                    selected = mode == "SUPPLIERS",
                    onClick = { onModeChange("SUPPLIERS") },
                    label = { Text("Supplier Ledger (${suppliers.size})") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                )
            }
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (mode == "CUSTOMERS") {
                    customers.forEach { c ->
                        FilterChip(
                            selected = c.customerId == selectedCustomerId,
                            onClick = { onSelectCustomer(c.customerId) },
                            label = { Text("${c.name} (${centsToCurrency(c.accountBalanceCents)})") }
                        )
                    }
                } else {
                    suppliers.forEach { s ->
                        FilterChip(
                            selected = s.supplierId == selectedSupplierId,
                            onClick = { onSelectSupplier(s.supplierId) },
                            label = { Text("${s.name} (${centsToCurrency(s.outstandingBalanceCents)})") }
                        )
                    }
                }
            }
        }
        if (activeReport != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(activeReport.partyName, style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                        Text(activeReport.partySubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Opening: ${centsToCurrency(activeReport.openingBalanceCents)}", style = MaterialTheme.typography.labelMedium)
                            Text("Closing Balance: ${centsToCurrency(activeReport.closingBalanceCents)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = StatusAmber)
                        }
                    }
                }
            }
            items(activeReport.lines) { line ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${line.sourceLabel} • ${line.reference}", style = MaterialTheme.typography.labelMedium, color = CraveGoldBright)
                            Text(line.description, style = MaterialTheme.typography.bodySmall)
                            Text(dateFmt.format(Date(line.dateMillis)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (line.debitCents > 0L) Text("Dr ${centsToCurrency(line.debitCents)}", color = StatusSky, style = MaterialTheme.typography.bodySmall)
                            if (line.creditCents > 0L) Text("Cr ${centsToCurrency(line.creditCents)}", color = StatusEmerald, style = MaterialTheme.typography.bodySmall)
                            Text("Bal ${centsToCurrency(line.runningBalanceCents)}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartAccountDialog(
    existing: ChartOfAccountRecord?,
    onDismiss: () -> Unit,
    onSave: (String, String, AccountType, AccountCategory, Long, Boolean) -> Unit
) {
    var code by remember { mutableStateOf(existing?.accountCode ?: "") }
    var name by remember { mutableStateOf(existing?.accountName ?: "") }
    var category by remember { mutableStateOf(existing?.accountCategory ?: AccountCategory.OPERATING_EXPENSE) }
    var type by remember { mutableStateOf(existing?.accountType ?: category.defaultType) }
    var openingStr by remember {
        mutableStateOf(
            if (existing != null) String.format(Locale.US, "%.2f", existing.openingBalanceCents / 100.0) else "0.00"
        )
    }
    var isActive by remember { mutableStateOf(existing?.isActive ?: true) }
    var catMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text(if (existing == null) "New Ledger Account" else "Edit ${existing.accountCode}", color = CraveGoldBright) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Account Code (e.g. 5120)") },
                    enabled = existing?.isSystemAccount != true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("coa_code_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("coa_name_input")
                )
                OutlinedButton(onClick = { catMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Category: ${category.label} (${type.label})")
                }
                DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                    AccountCategory.entries.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text("${cat.label} (${cat.defaultType.label})") },
                            onClick = {
                                category = cat
                                if (existing?.isSystemAccount != true) type = cat.defaultType
                                catMenu = false
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = openingStr,
                    onValueChange = { openingStr = it },
                    label = { Text("Opening Balance (PKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (existing?.isSystemAccount != true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Account")
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val openCents = ((openingStr.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
                    onSave(code, name, type, category, openCents, isActive)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                modifier = Modifier.testTag("coa_save_button")
            ) { Text("Save Account") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ManualJournalEntryDialog(
    accounts: List<ChartOfAccountRecord>,
    onDismiss: () -> Unit,
    onPost: (String, String, List<JournalDraftLine>) -> Unit
) {
    var reference by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val defaultDebitAcc = accounts.firstOrNull { it.accountCode == "5100" } ?: accounts.firstOrNull()
    val defaultCreditAcc = accounts.firstOrNull { it.accountCode == "1010" } ?: accounts.lastOrNull() ?: defaultDebitAcc

    data class EditableLine(
        val accountId: String,
        val isDebit: Boolean,
        val amountStr: String,
        val memo: String
    )

    val lines = remember {
        mutableStateListOf(
            EditableLine(defaultDebitAcc?.accountId.orEmpty(), true, "", ""),
            EditableLine(defaultCreditAcc?.accountId.orEmpty(), false, "", "")
        )
    }

    val draftLines = lines.map { l ->
        val amt = ((l.amountStr.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
        JournalDraftLine(
            accountId = l.accountId,
            debitCents = if (l.isDebit) amt else 0L,
            creditCents = if (!l.isDebit) amt else 0L,
            memo = l.memo
        )
    }
    val validation = AccountingCalculator.validateJournalLines(draftLines)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Post Balanced Journal Entry", color = CraveGoldBright) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("Reference #") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("je_ref_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Journal Description") },
                    modifier = Modifier.fillMaxWidth().testTag("je_desc_input")
                )
                lines.forEachIndexed { index, line ->
                    var accMenu by remember { mutableStateOf(false) }
                    val selectedAcc = accounts.firstOrNull { it.accountId == line.accountId }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = BorderStroke(1.dp, ObsidianBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(onClick = { accMenu = true }, modifier = Modifier.weight(1f)) {
                                    Text(
                                        selectedAcc?.let { "${it.accountCode} ${it.accountName}" } ?: "Select Account",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                DropdownMenu(expanded = accMenu, onDismissRequest = { accMenu = false }) {
                                    accounts.forEach { acc ->
                                        DropdownMenuItem(
                                            text = { Text("${acc.accountCode} — ${acc.accountName}") },
                                            onClick = {
                                                lines[index] = line.copy(accountId = acc.accountId)
                                                accMenu = false
                                            }
                                        )
                                    }
                                }
                                if (lines.size > 2) {
                                    IconButton(onClick = { lines.removeAt(index) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Remove line", tint = StatusCrimson)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = line.isDebit,
                                    onClick = { lines[index] = line.copy(isDebit = true) },
                                    label = { Text("Debit") }
                                )
                                FilterChip(
                                    selected = !line.isDebit,
                                    onClick = { lines[index] = line.copy(isDebit = false) },
                                    label = { Text("Credit") }
                                )
                                OutlinedTextField(
                                    value = line.amountStr,
                                    onValueChange = { lines[index] = line.copy(amountStr = it) },
                                    label = { Text("Amount (PKR)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        lines.add(EditableLine(defaultDebitAcc?.accountId.orEmpty(), true, "", ""))
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Journal Line")
                }
                Text(
                    text = if (validation.isValid) {
                        "Balanced: Dr ${centsToCurrency(validation.totalDebitCents)} = Cr ${centsToCurrency(validation.totalCreditCents)}"
                    } else {
                        validation.errorMessage ?: "Enter balanced debit & credit lines"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (validation.isValid) StatusEmerald else StatusAmber
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPost(reference, description, draftLines) },
                enabled = validation.isValid && description.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                modifier = Modifier.testTag("je_post_button")
            ) { Text("Post Entry") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ReverseJournalDialog(
    entry: JournalEntryRecord,
    onDismiss: () -> Unit,
    onConfirmReverse: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Authorize Journal Reversal", color = StatusCrimson) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Historical posted entries are immutable. Confirming creates an equal opposite reversal entry for ${entry.entryNumber} (${centsToCurrency(entry.totalDebitCents)}) and logs to the audit trail.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Authorization Reason") },
                    modifier = Modifier.fillMaxWidth().testTag("reversal_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmReverse(reason) },
                enabled = reason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson),
                modifier = Modifier.testTag("confirm_reversal_button")
            ) { Text("Post Reversal") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CashbookTransactionDialog(
    accounts: List<ChartOfAccountRecord>,
    onDismiss: () -> Unit,
    onSave: (CashbookTxType, String, String, Long, String, String, String) -> Unit
) {
    val cashBankAccounts = accounts.filter { it.isCashOrBank }
    var txType by remember { mutableStateOf(CashbookTxType.RECEIPT) }
    var cashAccId by remember { mutableStateOf(cashBankAccounts.firstOrNull()?.accountId.orEmpty()) }
    var offsetAccId by remember {
        mutableStateOf(accounts.firstOrNull { it.accountId != cashAccId }?.accountId.orEmpty())
    }
    var amountStr by remember { mutableStateOf("") }
    var ref by remember { mutableStateOf("") }
    var counterparty by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var cashMenu by remember { mutableStateOf(false) }
    var offsetMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Record Cashbook Entry", color = CraveGoldBright) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CashbookTxType.entries.forEach { t ->
                        FilterChip(
                            selected = txType == t,
                            onClick = { txType = t },
                            label = { Text(t.label) }
                        )
                    }
                }
                OutlinedButton(onClick = { cashMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    val sel = cashBankAccounts.firstOrNull { it.accountId == cashAccId }
                    Text("Cash/Bank: ${sel?.let { "${it.accountCode} ${it.accountName}" } ?: "Select"}")
                }
                DropdownMenu(expanded = cashMenu, onDismissRequest = { cashMenu = false }) {
                    cashBankAccounts.forEach { a ->
                        DropdownMenuItem(
                            text = { Text("${a.accountCode} — ${a.accountName}") },
                            onClick = { cashAccId = a.accountId; cashMenu = false }
                        )
                    }
                }
                OutlinedButton(onClick = { offsetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    val sel = accounts.firstOrNull { it.accountId == offsetAccId }
                    Text("Offset Account: ${sel?.let { "${it.accountCode} ${it.accountName}" } ?: "Select"}")
                }
                DropdownMenu(expanded = offsetMenu, onDismissRequest = { offsetMenu = false }) {
                    accounts.filter { it.accountId != cashAccId }.forEach { a ->
                        DropdownMenuItem(
                            text = { Text("${a.accountCode} — ${a.accountName}") },
                            onClick = { offsetAccId = a.accountId; offsetMenu = false }
                        )
                    }
                }
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (PKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = ref, onValueChange = { ref = it }, label = { Text("Reference #") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = counterparty, onValueChange = { counterparty = it }, label = { Text("Counterparty / Payee") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cents = ((amountStr.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
                    onSave(txType, cashAccId, offsetAccId, cents, ref, counterparty, desc)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Post Cashbook") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CustomerReceiptDialog(
    customers: List<CustomerProfileRecord>,
    cashBankAccounts: List<ChartOfAccountRecord>,
    preselectedCustomerId: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, String, String) -> Unit
) {
    var custId by remember { mutableStateOf(preselectedCustomerId ?: customers.firstOrNull()?.customerId.orEmpty()) }
    var cashAccId by remember { mutableStateOf(cashBankAccounts.firstOrNull()?.accountId.orEmpty()) }
    var amountStr by remember { mutableStateOf("") }
    var ref by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var custMenu by remember { mutableStateOf(false) }
    var cashMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Receive Customer Payment", color = CraveGoldBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { custMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    val c = customers.firstOrNull { it.customerId == custId }
                    Text("Customer: ${c?.name ?: "Select"} (Due: ${centsToCurrency(c?.accountBalanceCents ?: 0L)})")
                }
                DropdownMenu(expanded = custMenu, onDismissRequest = { custMenu = false }) {
                    customers.forEach { c ->
                        DropdownMenuItem(
                            text = { Text("${c.name} (${centsToCurrency(c.accountBalanceCents)})") },
                            onClick = { custId = c.customerId; custMenu = false }
                        )
                    }
                }
                OutlinedButton(onClick = { cashMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    val a = cashBankAccounts.firstOrNull { it.accountId == cashAccId }
                    Text("Deposit To: ${a?.accountName ?: "Select"}")
                }
                DropdownMenu(expanded = cashMenu, onDismissRequest = { cashMenu = false }) {
                    cashBankAccounts.forEach { a ->
                        DropdownMenuItem(
                            text = { Text("${a.accountCode} — ${a.accountName}") },
                            onClick = { cashAccId = a.accountId; cashMenu = false }
                        )
                    }
                }
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Receipt Amount (PKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = ref, onValueChange = { ref = it }, label = { Text("Receipt Reference") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cents = ((amountStr.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
                    onSave(custId, cashAccId, cents, ref, notes)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) { Text("Record Receipt") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
