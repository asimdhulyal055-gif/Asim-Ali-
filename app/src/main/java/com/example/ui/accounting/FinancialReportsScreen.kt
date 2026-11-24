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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.FinancialReportType
import com.example.domain.model.OutstandingBalanceRow
import com.example.domain.model.StatementLineItem
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
import com.example.ui.theme.StatusCrimsonBg
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusEmeraldBg
import com.example.ui.theme.StatusSky
import com.example.ui.viewmodel.DateRangeFilterPreset
import com.example.ui.viewmodel.FinancialReportsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialReportsScreen(
    session: UserSession,
    uiState: FinancialReportsUiState,
    onOpenDrawer: () -> Unit,
    onSelectReportType: (FinancialReportType) -> Unit,
    onSelectBranchScope: (String?) -> Unit,
    onSelectDatePreset: (DateRangeFilterPreset) -> Unit,
    onSearchChanged: (String) -> Unit,
    onSelectGlAccount: (String?) -> Unit,
    onExportReport: (ReportExportFormat) -> Unit,
    onClearFeedback: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    var branchMenu by remember { mutableStateOf(false) }

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
                            .testTag("reports_menu_button")
                    ) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open Drawer", tint = CraveGoldBright)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Financial Reports",
                            style = MaterialTheme.typography.titleLarge,
                            color = CraveGoldBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${uiState.selectedReportType.title} • ${uiState.scopeLabel}",
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
                            onClick = { branchMenu = true },
                            modifier = Modifier.testTag("reports_branch_selector")
                        ) {
                            Text(
                                if (uiState.selectedBranchId == null) "ALL" else "BRANCH",
                                color = CraveGoldBright,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        DropdownMenu(expanded = branchMenu, onDismissRequest = { branchMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Consolidated (All Branches)") },
                                onClick = { branchMenu = false; onSelectBranchScope(null) }
                            )
                            uiState.branches.forEach { br ->
                                DropdownMenuItem(
                                    text = { Text("${br.code} — ${br.name}") },
                                    onClick = { branchMenu = false; onSelectBranchScope(br.branchId) }
                                )
                            }
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
            // Report Type Selector (RBAC-filtered)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.authorizedReportTypes.forEach { type ->
                    FilterChip(
                        selected = uiState.selectedReportType == type,
                        onClick = { onSelectReportType(type) },
                        label = { Text(type.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CraveGoldContainer,
                            selectedLabelColor = CraveGoldBright
                        ),
                        modifier = Modifier.testTag("report_chip_${type.name}")
                    )
                }
            }

            // Date Presets + Export PDF / CSV Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DateRangeFilterPreset.entries.filter { it != DateRangeFilterPreset.CUSTOM }.forEach { preset ->
                    FilterChip(
                        selected = uiState.datePreset == preset,
                        onClick = { onSelectDatePreset(preset) },
                        label = { Text(preset.label) }
                    )
                }
                if (session.capabilities.canExportReports && uiState.authorizedReportTypes.isNotEmpty()) {
                    Button(
                        onClick = { onExportReport(ReportExportFormat.PDF) },
                        colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                        modifier = Modifier.height(36.dp).testTag("export_report_pdf_button")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF")
                    }
                    OutlinedButton(
                        onClick = { onExportReport(ReportExportFormat.CSV) },
                        border = BorderStroke(1.dp, CraveGold),
                        modifier = Modifier.height(36.dp).testTag("export_report_csv_button")
                    ) {
                        Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(16.dp), tint = CraveGoldBright)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV", color = CraveGoldBright)
                    }
                    uiState.lastExportedFile?.let { f ->
                        TextButton(
                            onClick = {
                                val fmt = if (f.extension.equals("pdf", true)) ReportExportFormat.PDF else ReportExportFormat.CSV
                                Phase4ReportExporter.shareReportFile(context, f, fmt.mimeType, "Share ${uiState.selectedReportType.title}")
                            }
                        ) {
                            Text("Share", color = StatusSky)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChanged,
                placeholder = { Text("Filter report entries by code, party, invoice, or reference...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = CraveGold) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (uiState.selectedReportType) {
                    FinancialReportType.PROFIT_AND_LOSS -> {
                        val pnl = uiState.profitAndLoss
                        item {
                            ReportSummaryHeader(
                                title = "Profit & Loss Statement",
                                subtitle = pnl.scopeLabel,
                                metricLabel = "Net Profit",
                                metricValue = centsToCurrency(pnl.netProfitCents),
                                positive = pnl.netProfitCents >= 0L
                            )
                        }
                        item { StatementSectionCard("Income", pnl.incomeLines, "Total Income", pnl.totalIncomeCents, StatusEmerald) }
                        item { StatementSectionCard("Purchases & COGS", pnl.cogsLines, "Gross Profit", pnl.grossProfitCents, CraveGoldBright) }
                        item { StatementSectionCard("Operating Expenses", pnl.operatingExpenseLines, "Total Operating Expenses", pnl.totalOperatingExpenseCents, StatusAmber) }
                        item { StatementSectionCard("Salaries & Payroll", pnl.payrollExpenseLines, "Total Payroll Expense", pnl.totalPayrollExpenseCents, StatusAmber) }
                    }

                    FinancialReportType.BALANCE_SHEET -> {
                        val bs = uiState.balanceSheet
                        item {
                            ReportSummaryHeader(
                                title = "Balance Sheet",
                                subtitle = "${bs.scopeLabel} • ${if (bs.isBalanced) "Assets = Liabilities + Equity (Balanced)" else "Variance Detected"}",
                                metricLabel = "Total Assets",
                                metricValue = centsToCurrency(bs.totalAssetsCents),
                                positive = bs.isBalanced
                            )
                        }
                        item { StatementSectionCard("Assets", bs.assetLines, "Total Assets", bs.totalAssetsCents, StatusEmerald) }
                        item { StatementSectionCard("Liabilities", bs.liabilityLines, "Total Liabilities", bs.totalLiabilitiesCents, StatusAmber) }
                        item {
                            val eqWithRet = bs.equityLines + StatementLineItem("RET", "Current Period Earnings", "Equity", bs.currentPeriodEarningsCents)
                            StatementSectionCard("Equity & Current Earnings", eqWithRet, "Total Equity", bs.totalEquityWithEarningsCents, CraveGoldBright)
                        }
                    }

                    FinancialReportType.CASH_FLOW -> {
                        val cf = uiState.cashFlow
                        item {
                            ReportSummaryHeader(
                                title = "Cash Flow Statement",
                                subtitle = cf.scopeLabel,
                                metricLabel = "Closing Cash & Bank",
                                metricValue = centsToCurrency(cf.closingCashAndBankCents),
                                positive = cf.netOperatingCashFlowCents >= 0L
                            )
                        }
                        item {
                            KeyValueReportCard(
                                title = "Cash & Bank Flow Breakdown",
                                rows = listOf(
                                    "Opening Cash & Bank Balance" to centsToCurrency(cf.openingCashAndBankCents),
                                    "POS Sales Receipts (+)" to centsToCurrency(cf.cashSalesReceiptsCents),
                                    "Customer Receivable Receipts (+)" to centsToCurrency(cf.customerReceiptsCents),
                                    "Other Cashbook Receipts (+)" to centsToCurrency(cf.otherReceiptsCents),
                                    "Supplier Payments (-)" to centsToCurrency(-cf.supplierPaymentsOutflowCents),
                                    "Expense Payments (-)" to centsToCurrency(-cf.expensePaymentsOutflowCents),
                                    "Payroll Disbursements (-)" to centsToCurrency(-cf.payrollPaymentsOutflowCents),
                                    "Other Cashbook Outflows (-)" to centsToCurrency(-cf.otherPaymentsOutflowCents),
                                    "Net Operating Cash Flow" to centsToCurrency(cf.netOperatingCashFlowCents),
                                    "Closing Cash & Bank Balance" to centsToCurrency(cf.closingCashAndBankCents)
                                )
                            )
                        }
                    }

                    FinancialReportType.TRIAL_BALANCE -> {
                        val tb = uiState.trialBalance
                        item {
                            ReportSummaryHeader(
                                title = "Trial Balance",
                                subtitle = if (tb.isBalanced) "Debits Equal Credits" else "Out of Balance",
                                metricLabel = "Total Dr / Cr",
                                metricValue = "${centsToCurrency(tb.totalDebitsCents)} / ${centsToCurrency(tb.totalCreditsCents)}",
                                positive = tb.isBalanced
                            )
                        }
                        items(tb.rows, key = { it.accountId }) { r ->
                            Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${r.accountCode} ${r.accountName}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text(if (r.debitBalanceCents > 0L) "Dr ${centsToCurrency(r.debitBalanceCents)}" else "Cr ${centsToCurrency(r.creditBalanceCents)}", color = if (r.debitBalanceCents > 0L) StatusSky else CraveGoldBright, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    FinancialReportType.GENERAL_LEDGER -> {
                        item {
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = uiState.selectedGlAccountId == null, onClick = { onSelectGlAccount(null) }, label = { Text("All Accounts") })
                                uiState.accounts.forEach { acc ->
                                    FilterChip(
                                        selected = uiState.selectedGlAccountId == acc.accountId,
                                        onClick = { onSelectGlAccount(acc.accountId) },
                                        label = { Text("${acc.accountCode} ${acc.accountName}") }
                                    )
                                }
                            }
                        }
                        items(uiState.generalLedger, key = { it.account.accountId }) { sec ->
                            Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${sec.account.accountCode} • ${sec.account.accountName}", style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
                                        Text("Close: ${centsToCurrency(sec.closingBalanceCents)}", fontWeight = FontWeight.Bold, color = StatusEmerald)
                                    }
                                    Text("Opening: ${centsToCurrency(sec.openingBalanceCents)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    HorizontalDivider(color = ObsidianBorder)
                                    sec.entries.forEach { line ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("${line.entryNumber} • ${line.description}", style = MaterialTheme.typography.bodySmall)
                                                Text(dateFmt.format(Date(line.entryDateMillis)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(
                                                text = if (line.debitCents > 0L) "Dr ${centsToCurrency(line.debitCents)} (Bal ${centsToCurrency(line.runningBalanceCents)})" else "Cr ${centsToCurrency(line.creditCents)} (Bal ${centsToCurrency(line.runningBalanceCents)})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (line.debitCents > 0L) StatusSky else CraveGoldBright
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    FinancialReportType.CASHBOOK -> {
                        items(uiState.cashBankSummaries, key = { it.account.accountId }) { s ->
                            KeyValueReportCard(
                                title = "${s.account.accountCode} ${s.account.accountName}",
                                rows = listOf(
                                    "Opening Balance" to centsToCurrency(s.openingBalanceCents),
                                    "Total Inflows" to centsToCurrency(s.totalInflowsCents),
                                    "Total Outflows" to centsToCurrency(s.totalOutflowsCents),
                                    "Closing Balance" to centsToCurrency(s.closingBalanceCents)
                                )
                            )
                        }
                        items(uiState.cashbookTransactions, key = { it.cashbookId }) { tx ->
                            Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${tx.reference} • ${tx.counterpartyName}", style = MaterialTheme.typography.titleSmall)
                                        Text("${dateFmt.format(Date(tx.txDateMillis))} • ${tx.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = "${if (tx.txType.isInflow) "+" else "-"}${centsToCurrency(tx.amountCents)}",
                                        color = if (tx.txType.isInflow) StatusEmerald else StatusCrimson,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    FinancialReportType.SALES_SUMMARY -> {
                        val ss = uiState.salesSummary
                        item {
                            KeyValueReportCard(
                                title = "Sales Summary (${ss.totalOrders} Active Orders)",
                                rows = listOf(
                                    "Gross Sales" to centsToCurrency(ss.grossSalesCents),
                                    "Discounts Given" to centsToCurrency(ss.discountsCents),
                                    "Tax Collected" to centsToCurrency(ss.taxCollectedCents),
                                    "Net Sales Revenue" to centsToCurrency(ss.netSalesCents),
                                    "Cash Collected" to centsToCurrency(ss.cashCollectedCents),
                                    "Card / Bank / Digital" to centsToCurrency(ss.cardAndBankCollectedCents),
                                    "Unpaid Receivables" to centsToCurrency(ss.receivableBalanceCents)
                                )
                            )
                        }
                        items(uiState.salesOrders, key = { it.orderId }) { o ->
                            Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${o.invoiceNumber} • ${o.customerName ?: "Walk-in"}", style = MaterialTheme.typography.titleSmall)
                                        Text("${o.paymentMethod} • ${o.status} • ${dateFmt.format(Date(o.createdAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(centsToCurrency(o.totalCents), fontWeight = FontWeight.Bold, color = CraveGoldBright)
                                }
                            }
                        }
                    }

                    FinancialReportType.PURCHASE_SUMMARY -> {
                        val totalPo = uiState.purchaseOrders.sumOf { (it.totalCents - it.returnedCents).coerceAtLeast(0L) }
                        item {
                            ReportSummaryHeader(
                                title = "Purchase Summary",
                                subtitle = "${uiState.purchaseOrders.size} Purchase Orders",
                                metricLabel = "Net Purchase Volume",
                                metricValue = centsToCurrency(totalPo),
                                positive = true
                            )
                        }
                        items(uiState.purchaseOrders, key = { it.purchaseId }) { po ->
                            Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${po.poNumber} • ${po.supplierName}", style = MaterialTheme.typography.titleSmall)
                                        Text("${po.status} • Paid: ${centsToCurrency(po.paidCents)} • Returned: ${centsToCurrency(po.returnedCents)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(centsToCurrency((po.totalCents - po.returnedCents).coerceAtLeast(0L)), fontWeight = FontWeight.Bold, color = StatusAmber)
                                }
                            }
                        }
                    }

                    FinancialReportType.EXPENSE_SUMMARY -> {
                        val expTotal = uiState.approvedExpenses.sumOf { it.amountCents }
                        item {
                            KeyValueReportCard(
                                title = "Approved Expenses by Category (${centsToCurrency(expTotal)})",
                                rows = uiState.expenseByCategory.entries.sortedByDescending { it.value }
                                    .map { it.key to centsToCurrency(it.value) }
                            )
                        }
                        items(uiState.approvedExpenses, key = { it.expenseId }) { exp ->
                            Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${exp.category} • ${exp.paymentMethod}", style = MaterialTheme.typography.titleSmall)
                                        Text("${exp.description} • ${dateFmt.format(Date(exp.expenseDateMillis))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(centsToCurrency(exp.amountCents), fontWeight = FontWeight.Bold, color = StatusCrimson)
                                }
                            }
                        }
                    }

                    FinancialReportType.PAYROLL_SUMMARY -> {
                        val pr = uiState.payrollSummary
                        item {
                            KeyValueReportCard(
                                title = "Payroll Summary (${pr.recordCount} Records)",
                                rows = listOf(
                                    "Draft / Approved / Paid" to "${pr.draftCount} / ${pr.approvedCount} / ${pr.paidCount}",
                                    "Total Gross Salaries" to centsToCurrency(pr.totalGrossCents),
                                    "Total Deductions" to centsToCurrency(pr.totalDeductionsCents),
                                    "Total Net Payroll" to centsToCurrency(pr.totalNetCents),
                                    "Total Paid Out" to centsToCurrency(pr.totalPaidOutCents)
                                )
                            )
                        }
                        items(uiState.payrollRecords, key = { it.payrollId }) { pay ->
                            Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${pay.employeeCode} • ${pay.staffName}", style = MaterialTheme.typography.titleSmall)
                                        Text("${pay.periodLabel} • ${pay.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(centsToCurrency(pay.netSalaryCents), fontWeight = FontWeight.Bold, color = CraveGoldBright)
                                }
                            }
                        }
                    }

                    FinancialReportType.CUSTOMER_OUTSTANDING -> {
                        val totalDue = uiState.customerOutstanding.sumOf { it.outstandingCents }
                        item {
                            ReportSummaryHeader(
                                title = "Customer Outstanding Balances",
                                subtitle = "${uiState.customerOutstanding.size} Customers",
                                metricLabel = "Total Receivable",
                                metricValue = centsToCurrency(totalDue),
                                positive = totalDue == 0L
                            )
                        }
                        items(uiState.customerOutstanding, key = { it.partyId }) { row ->
                            OutstandingPartyRowCard(row)
                        }
                    }

                    FinancialReportType.SUPPLIER_OUTSTANDING -> {
                        val totalPayable = uiState.supplierOutstanding.sumOf { it.outstandingCents }
                        item {
                            ReportSummaryHeader(
                                title = "Supplier Outstanding Balances",
                                subtitle = "${uiState.supplierOutstanding.size} Suppliers",
                                metricLabel = "Total Payable",
                                metricValue = centsToCurrency(totalPayable),
                                positive = totalPayable == 0L
                            )
                        }
                        items(uiState.supplierOutstanding, key = { it.partyId }) { row ->
                            OutstandingPartyRowCard(row)
                        }
                    }
                }

                // Consolidated comparison snapshot for Owners/Super Admins
                uiState.consolidatedReport?.let { cons ->
                    if (uiState.selectedBranchId == null && uiState.authorizedReportTypes.contains(FinancialReportType.PROFIT_AND_LOSS)) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            KeyValueReportCard(
                                title = "Consolidated Multi-Branch Comparison (${cons.branchSnapshots.size} Branches)",
                                rows = cons.branchSnapshots.map { s ->
                                    "${s.branch.code} (${s.branch.name})" to "Sales ${centsToCurrency(s.totalSalesCents)} | Net ${centsToCurrency(s.netProfitCents)}"
                                } + listOf(
                                    "Company Total Sales" to centsToCurrency(cons.totalSalesCents),
                                    "Company Net Profit" to centsToCurrency(cons.netProfitCents)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportSummaryHeader(
    title: String,
    subtitle: String,
    metricLabel: String,
    metricValue: String,
    positive: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
        border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(
                text = "$metricLabel: $metricValue",
                containerColor = if (positive) StatusEmeraldBg else StatusCrimsonBg,
                contentColor = if (positive) StatusEmerald else StatusCrimson
            )
        }
    }
}

@Composable
private fun StatementSectionCard(
    heading: String,
    lines: List<StatementLineItem>,
    totalLabel: String,
    totalCents: Long,
    accent: androidx.compose.ui.graphics.Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(heading, style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
            if (lines.isEmpty()) {
                Text("No posted activity in period ($totalLabel: ${centsToCurrency(totalCents)})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                lines.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${item.accountCode} ${item.label}", style = MaterialTheme.typography.bodySmall)
                        Text(centsToCurrency(item.amountCents), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
            HorizontalDivider(color = ObsidianBorder)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(totalLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(centsToCurrency(totalCents), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
            }
        }
    }
}

@Composable
private fun KeyValueReportCard(
    title: String,
    rows: List<Pair<String, String>>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
            HorizontalDivider(color = ObsidianBorder)
            if (rows.isEmpty()) {
                Text("No records in selected filter.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                rows.forEach { (k, v) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(k, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = CraveGoldBright)
                    }
                }
            }
        }
    }
}

@Composable
private fun OutstandingPartyRowCard(row: OutstandingBalanceRow) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(row.partyName, style = MaterialTheme.typography.titleSmall)
                Text("${row.branchName} • ${row.contact}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Billed: ${centsToCurrency(row.totalBilledCents)} | Settled: ${centsToCurrency(row.totalSettledCents)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = centsToCurrency(row.outstandingCents),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (row.outstandingCents > 0L) StatusAmber else StatusEmerald
            )
        }
    }
}
