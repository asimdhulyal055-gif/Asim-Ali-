package com.example.printing

import android.content.Context
import com.example.data.local.ExpenseEntity
import com.example.data.local.PayrollRecordEntity
import com.example.data.local.PosOrderEntity
import com.example.data.local.PurchaseOrderEntity
import com.example.domain.model.BalanceSheetReport
import com.example.domain.model.CashBankAccountSummary
import com.example.domain.model.CashFlowReport
import com.example.domain.model.CashbookTransactionRecord
import com.example.domain.model.ConsolidatedFinancialReport
import com.example.domain.model.ExpenseApprovalStatus
import com.example.domain.model.FinancialReportType
import com.example.domain.model.GeneralLedgerAccountSection
import com.example.domain.model.OutstandingBalanceRow
import com.example.domain.model.PayrollStatus
import com.example.domain.model.PayrollSummaryReportData
import com.example.domain.model.ProfitAndLossReport
import com.example.domain.model.PurchaseStatus
import com.example.domain.model.SalesSummaryReportData
import com.example.domain.model.TrialBalanceReport
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.domain.model.centsToCurrency
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FinancialReportBundle(
    val reportType: FinancialReportType,
    val scopeLabel: String,
    val profitAndLoss: ProfitAndLossReport,
    val balanceSheet: BalanceSheetReport,
    val cashFlow: CashFlowReport,
    val trialBalance: TrialBalanceReport,
    val generalLedger: List<GeneralLedgerAccountSection>,
    val cashBankSummaries: List<CashBankAccountSummary>,
    val cashbookTransactions: List<CashbookTransactionRecord>,
    val salesSummary: SalesSummaryReportData,
    val posOrders: List<PosOrderEntity>,
    val purchaseOrders: List<PurchaseOrderEntity>,
    val approvedExpenses: List<ExpenseEntity>,
    val payrollSummary: PayrollSummaryReportData,
    val payrollRecords: List<PayrollRecordEntity>,
    val customerOutstanding: List<OutstandingBalanceRow>,
    val supplierOutstanding: List<OutstandingBalanceRow>,
    val consolidatedReport: ConsolidatedFinancialReport? = null
)

object Phase6ReportExporter {

    private val dateTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun authorizedReportTypesFor(session: UserSession): List<FinancialReportType> {
        if (session.capabilities.canViewAccounting) {
            return FinancialReportType.entries.toList()
        }
        return when (session.role) {
            UserRole.HR -> listOf(FinancialReportType.PAYROLL_SUMMARY)
            UserRole.INVENTORY_MANAGER -> listOf(
                FinancialReportType.PURCHASE_SUMMARY,
                FinancialReportType.SUPPLIER_OUTSTANDING
            )
            else -> emptyList()
        }
    }

    fun canAccessReport(session: UserSession, type: FinancialReportType): Boolean =
        type in authorizedReportTypesFor(session)

    fun exportReport(
        context: Context,
        session: UserSession,
        bundle: FinancialReportBundle,
        format: ReportExportFormat
    ): Result<File> {
        if (!session.capabilities.canExportReports || !canAccessReport(session, bundle.reportType)) {
            return Result.failure(
                SecurityException("Access denied: ${session.role.displayName} is not authorized to export ${bundle.reportType.title}.")
            )
        }

        val ts = System.currentTimeMillis()
        val scope = bundle.scopeLabel
        return when (bundle.reportType) {
            FinancialReportType.PROFIT_AND_LOSS -> {
                val pnl = bundle.profitAndLoss
                val headers = listOf("Section", "Code", "Account", "Amount")
                val rows = buildList {
                    pnl.incomeLines.forEach { add(listOf("Income", it.accountCode, it.label, centsToCurrency(it.amountCents))) }
                    add(listOf("SUMMARY", "", "Total Income", centsToCurrency(pnl.totalIncomeCents)))
                    pnl.cogsLines.forEach { add(listOf("COGS / Purchases", it.accountCode, it.label, centsToCurrency(it.amountCents))) }
                    add(listOf("SUMMARY", "", "Gross Profit", centsToCurrency(pnl.grossProfitCents)))
                    pnl.operatingExpenseLines.forEach { add(listOf("Operating Expense", it.accountCode, it.label, centsToCurrency(it.amountCents))) }
                    pnl.payrollExpenseLines.forEach { add(listOf("Payroll Expense", it.accountCode, it.label, centsToCurrency(it.amountCents))) }
                    add(listOf("NET PROFIT", "", "Net Profit / (Loss)", centsToCurrency(pnl.netProfitCents)))
                }
                Phase4ReportExporter.exportTabularReport(
                    context, "aa_crave_pnl_$ts", "AA CRAVE Profit & Loss — $scope", headers, rows, format
                )
            }

            FinancialReportType.BALANCE_SHEET -> {
                val bs = bundle.balanceSheet
                val headers = listOf("Group", "Code", "Account", "Balance")
                val rows = buildList {
                    bs.assetLines.forEach { add(listOf("Asset", it.accountCode, it.label, centsToCurrency(it.amountCents))) }
                    add(listOf("TOTAL ASSETS", "", "Total Assets", centsToCurrency(bs.totalAssetsCents)))
                    bs.liabilityLines.forEach { add(listOf("Liability", it.accountCode, it.label, centsToCurrency(it.amountCents))) }
                    add(listOf("TOTAL LIABILITIES", "", "Total Liabilities", centsToCurrency(bs.totalLiabilitiesCents)))
                    bs.equityLines.forEach { add(listOf("Equity", it.accountCode, it.label, centsToCurrency(it.amountCents))) }
                    add(listOf("Equity", "RET", "Current Period Earnings", centsToCurrency(bs.currentPeriodEarningsCents)))
                    add(
                        listOf(
                            "CHECK",
                            if (bs.isBalanced) "BALANCED" else "UNBALANCED",
                            "Liabilities + Equity",
                            centsToCurrency(bs.totalLiabilitiesCents + bs.totalEquityWithEarningsCents)
                        )
                    )
                }
                Phase4ReportExporter.exportTabularReport(
                    context, "aa_crave_balance_sheet_$ts", "AA CRAVE Balance Sheet — $scope", headers, rows, format
                )
            }

            FinancialReportType.CASH_FLOW -> {
                val cf = bundle.cashFlow
                val headers = listOf("Cash Flow Line", "Category", "Amount")
                val rows = listOf(
                    listOf("Opening Cash & Bank Balance", "Opening", centsToCurrency(cf.openingCashAndBankCents)),
                    listOf("POS Cash & Card/Bank Receipts", "Inflow", centsToCurrency(cf.cashSalesReceiptsCents)),
                    listOf("Customer Receivable Receipts", "Inflow", centsToCurrency(cf.customerReceiptsCents)),
                    listOf("Other Cashbook Receipts", "Inflow", centsToCurrency(cf.otherReceiptsCents)),
                    listOf("Supplier Payments", "Outflow", centsToCurrency(-cf.supplierPaymentsOutflowCents)),
                    listOf("Approved Expense Payments", "Outflow", centsToCurrency(-cf.expensePaymentsOutflowCents)),
                    listOf("Payroll Disbursements", "Outflow", centsToCurrency(-cf.payrollPaymentsOutflowCents)),
                    listOf("Other Cashbook Outflows", "Outflow", centsToCurrency(-cf.otherPaymentsOutflowCents)),
                    listOf("Net Operating Cash Flow", "Net", centsToCurrency(cf.netOperatingCashFlowCents)),
                    listOf("Closing Cash & Bank Balance", "Closing", centsToCurrency(cf.closingCashAndBankCents))
                )
                Phase4ReportExporter.exportTabularReport(
                    context, "aa_crave_cash_flow_$ts", "AA CRAVE Cash Flow Statement — $scope", headers, rows, format
                )
            }

            FinancialReportType.TRIAL_BALANCE -> {
                val tb = bundle.trialBalance
                val headers = listOf("Code", "Account Name", "Type", "Debit Balance", "Credit Balance")
                val rows = tb.rows.map { r ->
                    listOf(
                        r.accountCode,
                        r.accountName,
                        r.accountType.label,
                        centsToCurrency(r.debitBalanceCents),
                        centsToCurrency(r.creditBalanceCents)
                    )
                } + listOf(
                    listOf(
                        "TOTAL",
                        if (tb.isBalanced) "BALANCED" else "VARIANCE",
                        "",
                        centsToCurrency(tb.totalDebitsCents),
                        centsToCurrency(tb.totalCreditsCents)
                    )
                )
                Phase4ReportExporter.exportTabularReport(
                    context, "aa_crave_trial_balance_$ts", "AA CRAVE Trial Balance — $scope", headers, rows, format
                )
            }

            FinancialReportType.GENERAL_LEDGER -> {
                val headers = listOf("Account", "Date", "JE #", "Reference", "Description", "Debit", "Credit", "Balance")
                val rows = buildList {
                    bundle.generalLedger.forEach { sec ->
                        val accTitle = "${sec.account.accountCode} ${sec.account.accountName}"
                        add(listOf(accTitle, "OPENING", "-", "-", "Opening Balance", "-", "-", centsToCurrency(sec.openingBalanceCents)))
                        sec.entries.forEach { l ->
                            add(
                                listOf(
                                    accTitle,
                                    dateFmt.format(Date(l.entryDateMillis)),
                                    l.entryNumber,
                                    l.reference,
                                    l.description,
                                    centsToCurrency(l.debitCents),
                                    centsToCurrency(l.creditCents),
                                    centsToCurrency(l.runningBalanceCents)
                                )
                            )
                        }
                        add(listOf(accTitle, "CLOSING", "-", "-", "Closing Balance", "-", "-", centsToCurrency(sec.closingBalanceCents)))
                    }
                }
                Phase4ReportExporter.exportTabularReport(
                    context, "aa_crave_general_ledger_$ts", "AA CRAVE General Ledger — $scope", headers, rows, format
                )
            }

            FinancialReportType.CASHBOOK -> {
                val headers = listOf("Date", "Account", "Type", "Reference", "Counterparty", "Description", "Amount")
                val rows = bundle.cashbookTransactions.map { tx ->
                    listOf(
                        dateTimeFmt.format(Date(tx.txDateMillis)),
                        tx.accountName,
                        tx.txType.label,
                        tx.reference,
                        tx.counterpartyName,
                        tx.description,
                        centsToCurrency(if (tx.txType.isInflow) tx.amountCents else -tx.amountCents)
                    )
                } + bundle.cashBankSummaries.map { s ->
                    listOf(
                        "CLOSING",
                        s.account.accountName,
                        "Open: ${centsToCurrency(s.openingBalanceCents)}",
                        "In: ${centsToCurrency(s.totalInflowsCents)}",
                        "Out: ${centsToCurrency(s.totalOutflowsCents)}",
                        "Closing Balance",
                        centsToCurrency(s.closingBalanceCents)
                    )
                }
                Phase4ReportExporter.exportTabularReport(
                    context, "aa_crave_cashbook_$ts", "AA CRAVE Cashbook Report — $scope", headers, rows, format
                )
            }

            FinancialReportType.SALES_SUMMARY -> {
                val headers = listOf("Date", "Invoice #", "Order #", "Customer", "Method", "Status", "Total", "Paid", "Due")
                val rows = bundle.posOrders.map { o ->
                    listOf(
                        dateTimeFmt.format(Date(o.createdAt)),
                        o.invoiceNumber,
                        o.orderNumber,
                        o.customerName ?: "Walk-in",
                        o.paymentMethod,
                        o.status,
                        centsToCurrency(o.totalCents),
                        centsToCurrency(o.paidCents.coerceAtMost(o.totalCents)),
                        centsToCurrency(o.remainingBalanceCents)
                    )
                } + listOf(
                    listOf(
                        "SUMMARY",
                        "${bundle.salesSummary.totalOrders} orders",
                        "Disc: ${centsToCurrency(bundle.salesSummary.discountsCents)}",
                        "Tax: ${centsToCurrency(bundle.salesSummary.taxCollectedCents)}",
                        "Cash: ${centsToCurrency(bundle.salesSummary.cashCollectedCents)}",
                        "Card/Bank: ${centsToCurrency(bundle.salesSummary.cardAndBankCollectedCents)}",
                        centsToCurrency(bundle.salesSummary.netSalesCents),
                        centsToCurrency(bundle.salesSummary.cashCollectedCents + bundle.salesSummary.cardAndBankCollectedCents),
                        centsToCurrency(bundle.salesSummary.receivableBalanceCents)
                    )
                )
                Phase4ReportExporter.exportTabularReport(
                    context, "aa_crave_sales_summary_$ts", "AA CRAVE Sales Summary — $scope", headers, rows, format
                )
            }

            FinancialReportType.PURCHASE_SUMMARY -> {
                val headers = listOf("Date", "PO #", "Supplier", "Invoice #", "Status", "Pay Status", "Net Total", "Paid", "Returned")
                val rows = bundle.purchaseOrders.map { po ->
                    listOf(
                        dateFmt.format(Date(po.createdAt)),
                        po.poNumber,
                        po.supplierName,
                        po.supplierInvoiceNumber,
                        po.status,
                        po.paymentStatus,
                        centsToCurrency((po.totalCents - po.returnedCents).coerceAtLeast(0L)),
                        centsToCurrency(po.paidCents),
                        centsToCurrency(po.returnedCents)
                    )
                }
                Phase4ReportExporter.exportTabularReport(
                    context, "aa_crave_purchase_summary_$ts", "AA CRAVE Purchase Summary — $scope", headers, rows, format
                )
            }

            FinancialReportType.EXPENSE_SUMMARY -> {
                val headers = listOf("Date", "Category", "Description", "Method", "Status", "Approved By", "Amount")
                val approved = bundle.approvedExpenses.filter { it.approvalStatus == ExpenseApprovalStatus.APPROVED.name }
                val rows = approved.map { e ->
                    listOf(
                        dateFmt.format(Date(e.expenseDateMillis)),
                        e.category,
                        e.description,
                        e.paymentMethod,
                        e.approvalStatus,
                        e.approvedByName ?: "-",
                        centsToCurrency(e.amountCents)
                    )
                } + listOf(
                    listOf("TOTAL", "Approved Expenses", "${approved.size} entries", "-", "-", "-", centsToCurrency(approved.sumOf { it.amountCents }))
                )
                Phase4ReportExporter.exportTabularReport(
                    context, "aa_crave_expense_summary_$ts", "AA CRAVE Expense Summary — $scope", headers, rows, format
                )
            }

            FinancialReportType.PAYROLL_SUMMARY -> {
                val headers = listOf("Period", "Emp ID", "Employee", "Type", "Gross", "Deductions", "Net", "Status", "Ref")
                val rows = bundle.payrollRecords.map { p ->
                    listOf(
                        p.periodLabel,
                        p.employeeCode,
                        p.staffName,
                        p.salaryType,
                        centsToCurrency(p.grossSalaryCents),
                        centsToCurrency(p.deductionsCents),
                        centsToCurrency(p.netSalaryCents),
                        p.status,
                        p.paymentReference ?: "-"
                    )
                } + listOf(
                    listOf(
                        "SUMMARY",
                        "${bundle.payrollSummary.recordCount} recs",
                        "Paid: ${bundle.payrollSummary.paidCount}",
                        "Approved: ${bundle.payrollSummary.approvedCount}",
                        centsToCurrency(bundle.payrollSummary.totalGrossCents),
                        centsToCurrency(bundle.payrollSummary.totalDeductionsCents),
                        centsToCurrency(bundle.payrollSummary.totalNetCents),
                        "Paid Out:",
                        centsToCurrency(bundle.payrollSummary.totalPaidOutCents)
                    )
                )
                Phase4ReportExporter.exportTabularReport(
                    context, "aa_crave_payroll_summary_$ts", "AA CRAVE Payroll Summary — $scope", headers, rows, format
                )
            }

            FinancialReportType.CUSTOMER_OUTSTANDING -> {
                val headers = listOf("Branch", "Customer", "Contact", "Total Billed", "Settled", "Outstanding")
                val rows = bundle.customerOutstanding.map { c ->
                    listOf(
                        c.branchName,
                        c.partyName,
                        c.contact,
                        centsToCurrency(c.totalBilledCents),
                        centsToCurrency(c.totalSettledCents),
                        centsToCurrency(c.outstandingCents)
                    )
                }
                Phase4ReportExporter.exportTabularReport(
                    context, "aa_crave_customer_balances_$ts", "AA CRAVE Customer Outstanding — $scope", headers, rows, format
                )
            }

            FinancialReportType.SUPPLIER_OUTSTANDING -> {
                val headers = listOf("Branch", "Supplier", "Contact", "Total Payable", "Paid/Returned", "Outstanding")
                val rows = bundle.supplierOutstanding.map { s ->
                    listOf(
                        s.branchName,
                        s.partyName,
                        s.contact,
                        centsToCurrency(s.totalBilledCents),
                        centsToCurrency(s.totalSettledCents),
                        centsToCurrency(s.outstandingCents)
                    )
                }
                Phase4ReportExporter.exportTabularReport(
                    context, "aa_crave_supplier_balances_$ts", "AA CRAVE Supplier Outstanding — $scope", headers, rows, format
                )
            }
        }
    }

    fun exportConsolidatedBranchComparison(
        context: Context,
        session: UserSession,
        report: ConsolidatedFinancialReport,
        format: ReportExportFormat
    ): Result<File> {
        if (!session.capabilities.canViewConsolidatedReports || !session.capabilities.canExportReports) {
            return Result.failure(SecurityException("Access denied: unauthorized to export consolidated multi-branch reports."))
        }
        val headers = listOf("Code", "Branch", "Active", "Staff", "Sales", "Purchases", "Expenses", "Payroll", "Net Profit", "Cash", "Bank")
        val rows = report.branchSnapshots.map { s ->
            listOf(
                s.branch.code,
                s.branch.name,
                if (s.branch.isActive) "Active" else "Inactive",
                s.staffCount.toString(),
                centsToCurrency(s.totalSalesCents),
                centsToCurrency(s.totalPurchasesCents),
                centsToCurrency(s.totalExpensesCents),
                centsToCurrency(s.totalPayrollCents),
                centsToCurrency(s.netProfitCents),
                centsToCurrency(s.cashBalanceCents),
                centsToCurrency(s.bankBalanceCents)
            )
        } + listOf(
            listOf(
                "TOTAL",
                "Consolidated Company",
                "-",
                report.branchSnapshots.sumOf { it.staffCount }.toString(),
                centsToCurrency(report.totalSalesCents),
                centsToCurrency(report.totalPurchasesCents),
                centsToCurrency(report.totalExpensesCents),
                centsToCurrency(report.totalPayrollCents),
                centsToCurrency(report.netProfitCents),
                centsToCurrency(report.totalCashBalanceCents),
                centsToCurrency(report.totalBankBalanceCents)
            )
        )
        return Phase4ReportExporter.exportTabularReport(
            context = context,
            fileBaseName = "aa_crave_consolidated_branches_${System.currentTimeMillis()}",
            title = "AA CRAVE Multi-Branch Consolidated Financial Report",
            headers = headers,
            rows = rows,
            format = format
        )
    }
}
