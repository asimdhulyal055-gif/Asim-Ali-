package com.example.domain.model

enum class AccountType(val label: String, val isDebitNormal: Boolean) {
    ASSET("Asset", true),
    LIABILITY("Liability", false),
    EQUITY("Equity", false),
    INCOME("Income", false),
    EXPENSE("Expense", true)
}

enum class AccountCategory(val label: String, val defaultType: AccountType) {
    CASH("Cash on Hand", AccountType.ASSET),
    BANK("Bank Account", AccountType.ASSET),
    ACCOUNTS_RECEIVABLE("Accounts Receivable", AccountType.ASSET),
    INVENTORY("Inventory Asset", AccountType.ASSET),
    FIXED_ASSET("Fixed Asset", AccountType.ASSET),
    ACCOUNTS_PAYABLE("Accounts Payable", AccountType.LIABILITY),
    TAX_PAYABLE("Sales Tax Payable", AccountType.LIABILITY),
    PAYROLL_PAYABLE("Payroll & Wages Payable", AccountType.LIABILITY),
    OWNER_EQUITY("Owner Equity & Capital", AccountType.EQUITY),
    RETAINED_EARNINGS("Retained Earnings", AccountType.EQUITY),
    SALES_REVENUE("Sales Revenue", AccountType.INCOME),
    OTHER_INCOME("Other Income", AccountType.INCOME),
    COGS("Purchases & Cost of Goods", AccountType.EXPENSE),
    OPERATING_EXPENSE("Operating Expense", AccountType.EXPENSE),
    PAYROLL_EXPENSE("Salaries & Payroll Expense", AccountType.EXPENSE)
}

enum class JournalEntryStatus(val label: String) {
    POSTED("Posted"),
    REVERSED("Reversed")
}

enum class JournalSourceModule(val label: String) {
    MANUAL("Manual Journal"),
    POS_SALE("POS Sale"),
    POS_REFUND("POS Refund / Cancel"),
    CUSTOMER_PAYMENT("Customer Receipt"),
    PURCHASE_BILL("Purchase Invoice"),
    PURCHASE_RETURN("Purchase Return"),
    SUPPLIER_PAYMENT("Supplier Payment"),
    EXPENSE("Approved Expense"),
    PAYROLL_ACCRUAL("Payroll Approval"),
    PAYROLL_PAYMENT("Payroll Disbursement"),
    CASHBOOK_RECEIPT("Cashbook Receipt"),
    CASHBOOK_PAYMENT("Cashbook Payment"),
    CASHBOOK_TRANSFER("Cash / Bank Transfer"),
    REVERSAL("Authorized Reversal")
}

enum class CashbookTxType(val label: String, val isInflow: Boolean) {
    RECEIPT("Cash / Bank Receipt", true),
    PAYMENT("Cash / Bank Payment", false),
    TRANSFER_IN("Internal Transfer In", true),
    TRANSFER_OUT("Internal Transfer Out", false)
}

enum class FinancialReportType(val title: String, val subtitle: String, val restrictedToAccounting: Boolean = true) {
    PROFIT_AND_LOSS("Profit & Loss (P&L)", "Income vs. COGS, Operating & Payroll Expenses", true),
    BALANCE_SHEET("Balance Sheet", "Assets = Liabilities + Equity + Current Earnings", true),
    CASH_FLOW("Cash Flow Statement", "Operating, Financing & Cash/Bank Reconciliation", true),
    TRIAL_BALANCE("Trial Balance", "Debit & Credit balances across all accounts", true),
    GENERAL_LEDGER("General Ledger", "Account-wise journal lines & running balances", true),
    CASHBOOK("Cashbook Report", "Receipts, payments & cash/bank closing balances", true),
    SALES_SUMMARY("Sales Summary", "POS invoices, payment methods & net revenue", true),
    PURCHASE_SUMMARY("Purchase Summary", "Received purchase orders, returns & AP", false),
    EXPENSE_SUMMARY("Expense Summary", "Approved operating expenses by category", true),
    PAYROLL_SUMMARY("Payroll Summary", "Gross, deductions, net salaries & disbursements", false),
    CUSTOMER_OUTSTANDING("Customer Balances", "Receivables & customer account outstandings", true),
    SUPPLIER_OUTSTANDING("Supplier Balances", "Accounts payable & outstanding vendor balances", false)
}

data class ChartOfAccountRecord(
    val accountId: String,
    val branchId: String,
    val accountCode: String,
    val accountName: String,
    val accountType: AccountType,
    val accountCategory: AccountCategory,
    val isCashOrBank: Boolean,
    val openingBalanceCents: Long,
    val currentBalanceCents: Long = openingBalanceCents,
    val debitTurnoverCents: Long = 0L,
    val creditTurnoverCents: Long = 0L,
    val isSystemAccount: Boolean,
    val isActive: Boolean,
    val createdAt: Long
)

data class JournalDraftLine(
    val accountId: String,
    val debitCents: Long,
    val creditCents: Long,
    val partyType: String? = null,
    val partyId: String? = null,
    val partyName: String? = null,
    val memo: String = ""
)

data class JournalLineRecord(
    val lineId: String,
    val entryId: String,
    val branchId: String,
    val accountId: String,
    val accountCode: String,
    val accountName: String,
    val accountType: AccountType,
    val debitCents: Long,
    val creditCents: Long,
    val partyType: String?,
    val partyId: String?,
    val partyName: String?,
    val memo: String,
    val entryDateMillis: Long
)

data class JournalEntryRecord(
    val entryId: String,
    val branchId: String,
    val entryNumber: String,
    val entryDateMillis: Long,
    val sourceModule: JournalSourceModule,
    val sourceEntityId: String?,
    val sourceKey: String,
    val reference: String,
    val description: String,
    val totalDebitCents: Long,
    val totalCreditCents: Long,
    val status: JournalEntryStatus,
    val reversedEntryId: String?,
    val postedByUid: String,
    val postedByName: String,
    val approvedByName: String?,
    val createdAt: Long,
    val lines: List<JournalLineRecord> = emptyList()
)

data class CashbookTransactionRecord(
    val cashbookId: String,
    val branchId: String,
    val accountId: String,
    val accountName: String,
    val txType: CashbookTxType,
    val amountCents: Long,
    val reference: String,
    val counterpartyName: String,
    val description: String,
    val txDateMillis: Long,
    val linkedJournalEntryId: String,
    val recordedByName: String,
    val createdAt: Long
)

data class CashBankAccountSummary(
    val account: ChartOfAccountRecord,
    val openingBalanceCents: Long,
    val totalInflowsCents: Long,
    val totalOutflowsCents: Long,
    val closingBalanceCents: Long
)

data class PartyLedgerRow(
    val dateMillis: Long,
    val reference: String,
    val sourceLabel: String,
    val description: String,
    val debitCents: Long,
    val creditCents: Long,
    val runningBalanceCents: Long,
    val branchId: String
)

data class PartyLedgerReport(
    val partyId: String,
    val partyName: String,
    val partySubtitle: String,
    val openingBalanceCents: Long,
    val closingBalanceCents: Long,
    val lines: List<PartyLedgerRow>
)

data class TrialBalanceRow(
    val accountId: String,
    val branchId: String,
    val accountCode: String,
    val accountName: String,
    val accountType: AccountType,
    val debitBalanceCents: Long,
    val creditBalanceCents: Long
)

data class TrialBalanceReport(
    val scopeLabel: String,
    val rows: List<TrialBalanceRow>,
    val totalDebitsCents: Long,
    val totalCreditsCents: Long,
    val isBalanced: Boolean
)

data class StatementLineItem(
    val accountCode: String,
    val label: String,
    val category: String,
    val amountCents: Long
)

data class ProfitAndLossReport(
    val scopeLabel: String,
    val incomeLines: List<StatementLineItem>,
    val cogsLines: List<StatementLineItem>,
    val operatingExpenseLines: List<StatementLineItem>,
    val payrollExpenseLines: List<StatementLineItem>,
    val totalIncomeCents: Long,
    val totalCogsCents: Long,
    val grossProfitCents: Long,
    val totalOperatingExpenseCents: Long,
    val totalPayrollExpenseCents: Long,
    val netProfitCents: Long
)

data class BalanceSheetReport(
    val scopeLabel: String,
    val assetLines: List<StatementLineItem>,
    val liabilityLines: List<StatementLineItem>,
    val equityLines: List<StatementLineItem>,
    val currentPeriodEarningsCents: Long,
    val totalAssetsCents: Long,
    val totalLiabilitiesCents: Long,
    val totalEquityWithEarningsCents: Long,
    val isBalanced: Boolean
)

data class CashFlowReport(
    val scopeLabel: String,
    val openingCashAndBankCents: Long,
    val cashSalesReceiptsCents: Long,
    val customerReceiptsCents: Long,
    val otherReceiptsCents: Long,
    val supplierPaymentsOutflowCents: Long,
    val expensePaymentsOutflowCents: Long,
    val payrollPaymentsOutflowCents: Long,
    val otherPaymentsOutflowCents: Long,
    val netOperatingCashFlowCents: Long,
    val closingCashAndBankCents: Long
)

data class GeneralLedgerAccountSection(
    val account: ChartOfAccountRecord,
    val openingBalanceCents: Long,
    val entries: List<GeneralLedgerLineItem>,
    val closingBalanceCents: Long
)

data class GeneralLedgerLineItem(
    val entryDateMillis: Long,
    val entryNumber: String,
    val reference: String,
    val description: String,
    val debitCents: Long,
    val creditCents: Long,
    val runningBalanceCents: Long,
    val isReversed: Boolean
)

data class BranchFinancialSnapshot(
    val branch: BranchSummary,
    val totalSalesCents: Long,
    val totalPurchasesCents: Long,
    val totalExpensesCents: Long,
    val totalPayrollCents: Long,
    val netProfitCents: Long,
    val cashBalanceCents: Long,
    val bankBalanceCents: Long,
    val staffCount: Int
)

data class ConsolidatedFinancialReport(
    val branchSnapshots: List<BranchFinancialSnapshot>,
    val totalSalesCents: Long,
    val totalPurchasesCents: Long,
    val totalExpensesCents: Long,
    val totalPayrollCents: Long,
    val netProfitCents: Long,
    val totalCashBalanceCents: Long,
    val totalBankBalanceCents: Long
)

data class OutstandingBalanceRow(
    val partyId: String,
    val branchId: String,
    val branchName: String,
    val partyName: String,
    val contact: String,
    val totalBilledCents: Long,
    val totalSettledCents: Long,
    val outstandingCents: Long
)

data class SalesSummaryReportData(
    val totalOrders: Int,
    val grossSalesCents: Long,
    val discountsCents: Long,
    val taxCollectedCents: Long,
    val netSalesCents: Long,
    val cashCollectedCents: Long,
    val cardAndBankCollectedCents: Long,
    val receivableBalanceCents: Long
)

data class PayrollSummaryReportData(
    val recordCount: Int,
    val draftCount: Int,
    val approvedCount: Int,
    val paidCount: Int,
    val totalGrossCents: Long,
    val totalDeductionsCents: Long,
    val totalNetCents: Long,
    val totalPaidOutCents: Long
)

object AccountingCalculator {

    data class ValidationResult(
        val isValid: Boolean,
        val totalDebitCents: Long,
        val totalCreditCents: Long,
        val errorMessage: String? = null
    )

    fun validateJournalLines(lines: List<JournalDraftLine>): ValidationResult {
        if (lines.size < 2) {
            return ValidationResult(
                isValid = false,
                totalDebitCents = 0L,
                totalCreditCents = 0L,
                errorMessage = "A journal entry requires at least two lines (one debit and one credit)."
            )
        }
        if (lines.any { it.debitCents < 0L || it.creditCents < 0L }) {
            return ValidationResult(
                isValid = false,
                totalDebitCents = 0L,
                totalCreditCents = 0L,
                errorMessage = "Debit and credit amounts cannot be negative."
            )
        }
        if (lines.any { (it.debitCents == 0L && it.creditCents == 0L) || (it.debitCents > 0L && it.creditCents > 0L) }) {
            return ValidationResult(
                isValid = false,
                totalDebitCents = 0L,
                totalCreditCents = 0L,
                errorMessage = "Each journal line must contain either a positive Debit OR a positive Credit."
            )
        }
        val totalDebits = lines.sumOf { it.debitCents }
        val totalCredits = lines.sumOf { it.creditCents }
        if (totalDebits <= 0L) {
            return ValidationResult(
                isValid = false,
                totalDebitCents = totalDebits,
                totalCreditCents = totalCredits,
                errorMessage = "Total journal entry amount must be greater than zero."
            )
        }
        if (totalDebits != totalCredits) {
            return ValidationResult(
                isValid = false,
                totalDebitCents = totalDebits,
                totalCreditCents = totalCredits,
                errorMessage = "Unbalanced entry: Debits (${centsToCurrency(totalDebits)}) != Credits (${centsToCurrency(totalCredits)})."
            )
        }
        return ValidationResult(
            isValid = true,
            totalDebitCents = totalDebits,
            totalCreditCents = totalCredits
        )
    }

    fun computeAccountNetBalance(
        account: ChartOfAccountRecord,
        postedLines: List<JournalLineRecord>
    ): Long {
        val debits = postedLines.sumOf { it.debitCents }
        val credits = postedLines.sumOf { it.creditCents }
        return if (account.accountType.isDebitNormal) {
            account.openingBalanceCents + debits - credits
        } else {
            account.openingBalanceCents + credits - debits
        }
    }

    fun buildTrialBalance(
        scopeLabel: String,
        accounts: List<ChartOfAccountRecord>,
        postedLines: List<JournalLineRecord>
    ): TrialBalanceReport {
        val linesByAccount = postedLines.groupBy { it.accountId }
        val rows = accounts.map { acc ->
            val net = computeAccountNetBalance(acc, linesByAccount[acc.accountId].orEmpty())
            val (debitCol, creditCol) = if (acc.accountType.isDebitNormal) {
                if (net >= 0L) net to 0L else 0L to -net
            } else {
                if (net >= 0L) 0L to net else -net to 0L
            }
            TrialBalanceRow(
                accountId = acc.accountId,
                branchId = acc.branchId,
                accountCode = acc.accountCode,
                accountName = acc.accountName,
                accountType = acc.accountType,
                debitBalanceCents = debitCol,
                creditBalanceCents = creditCol
            )
        }.filter { it.debitBalanceCents != 0L || it.creditBalanceCents != 0L }
            .sortedBy { it.accountCode }

        val totalDebits = rows.sumOf { it.debitBalanceCents }
        val totalCredits = rows.sumOf { it.creditBalanceCents }
        return TrialBalanceReport(
            scopeLabel = scopeLabel,
            rows = rows,
            totalDebitsCents = totalDebits,
            totalCreditsCents = totalCredits,
            isBalanced = totalDebits == totalCredits
        )
    }

    fun buildProfitAndLoss(
        scopeLabel: String,
        accounts: List<ChartOfAccountRecord>,
        postedLines: List<JournalLineRecord>,
        includeOpeningBalances: Boolean = false
    ): ProfitAndLossReport {
        val linesByAccount = postedLines.groupBy { it.accountId }

        fun netForPeriod(acc: ChartOfAccountRecord): Long {
            val accLines = linesByAccount[acc.accountId].orEmpty()
            val d = accLines.sumOf { it.debitCents }
            val c = accLines.sumOf { it.creditCents }
            val opening = if (includeOpeningBalances) acc.openingBalanceCents else 0L
            return if (acc.accountType.isDebitNormal) opening + d - c else opening + c - d
        }

        val incomeAccounts = accounts.filter { it.accountType == AccountType.INCOME }
        val expenseAccounts = accounts.filter { it.accountType == AccountType.EXPENSE }

        val incomeLines = incomeAccounts
            .groupBy { it.accountCode to it.accountName }
            .map { (key, list) ->
                StatementLineItem(key.first, key.second, "Income", list.sumOf(::netForPeriod))
            }.filter { it.amountCents != 0L }.sortedBy { it.accountCode }

        val cogsLines = expenseAccounts
            .filter { it.accountCategory == AccountCategory.COGS }
            .groupBy { it.accountCode to it.accountName }
            .map { (key, list) ->
                StatementLineItem(key.first, key.second, "COGS", list.sumOf(::netForPeriod))
            }.filter { it.amountCents != 0L }.sortedBy { it.accountCode }

        val payrollLines = expenseAccounts
            .filter { it.accountCategory == AccountCategory.PAYROLL_EXPENSE }
            .groupBy { it.accountCode to it.accountName }
            .map { (key, list) ->
                StatementLineItem(key.first, key.second, "Payroll", list.sumOf(::netForPeriod))
            }.filter { it.amountCents != 0L }.sortedBy { it.accountCode }

        val opexLines = expenseAccounts
            .filter { it.accountCategory != AccountCategory.COGS && it.accountCategory != AccountCategory.PAYROLL_EXPENSE }
            .groupBy { it.accountCode to it.accountName }
            .map { (key, list) ->
                StatementLineItem(key.first, key.second, "Operating", list.sumOf(::netForPeriod))
            }.filter { it.amountCents != 0L }.sortedBy { it.accountCode }

        val totalIncome = incomeLines.sumOf { it.amountCents }
        val totalCogs = cogsLines.sumOf { it.amountCents }
        val grossProfit = totalIncome - totalCogs
        val totalOpex = opexLines.sumOf { it.amountCents }
        val totalPayroll = payrollLines.sumOf { it.amountCents }
        val netProfit = grossProfit - totalOpex - totalPayroll

        return ProfitAndLossReport(
            scopeLabel = scopeLabel,
            incomeLines = incomeLines,
            cogsLines = cogsLines,
            operatingExpenseLines = opexLines,
            payrollExpenseLines = payrollLines,
            totalIncomeCents = totalIncome,
            totalCogsCents = totalCogs,
            grossProfitCents = grossProfit,
            totalOperatingExpenseCents = totalOpex,
            totalPayrollExpenseCents = totalPayroll,
            netProfitCents = netProfit
        )
    }

    fun buildBalanceSheet(
        scopeLabel: String,
        accounts: List<ChartOfAccountRecord>,
        postedLinesUpToEnd: List<JournalLineRecord>
    ): BalanceSheetReport {
        val linesByAccount = postedLinesUpToEnd.groupBy { it.accountId }

        fun netCumulative(acc: ChartOfAccountRecord): Long =
            computeAccountNetBalance(acc, linesByAccount[acc.accountId].orEmpty())

        val pnl = buildProfitAndLoss(
            scopeLabel = scopeLabel,
            accounts = accounts,
            postedLines = postedLinesUpToEnd,
            includeOpeningBalances = true
        )

        val assetLines = accounts.filter { it.accountType == AccountType.ASSET }
            .groupBy { it.accountCode to it.accountName }
            .map { (key, list) ->
                StatementLineItem(key.first, key.second, "Asset", list.sumOf(::netCumulative))
            }.filter { it.amountCents != 0L }.sortedBy { it.accountCode }

        val liabilityLines = accounts.filter { it.accountType == AccountType.LIABILITY }
            .groupBy { it.accountCode to it.accountName }
            .map { (key, list) ->
                StatementLineItem(key.first, key.second, "Liability", list.sumOf(::netCumulative))
            }.filter { it.amountCents != 0L }.sortedBy { it.accountCode }

        val equityLines = accounts.filter { it.accountType == AccountType.EQUITY }
            .groupBy { it.accountCode to it.accountName }
            .map { (key, list) ->
                StatementLineItem(key.first, key.second, "Equity", list.sumOf(::netCumulative))
            }.filter { it.amountCents != 0L }.sortedBy { it.accountCode }

        val totalAssets = assetLines.sumOf { it.amountCents }
        val totalLiabilities = liabilityLines.sumOf { it.amountCents }
        val totalEquity = equityLines.sumOf { it.amountCents } + pnl.netProfitCents

        return BalanceSheetReport(
            scopeLabel = scopeLabel,
            assetLines = assetLines,
            liabilityLines = liabilityLines,
            equityLines = equityLines,
            currentPeriodEarningsCents = pnl.netProfitCents,
            totalAssetsCents = totalAssets,
            totalLiabilitiesCents = totalLiabilities,
            totalEquityWithEarningsCents = totalEquity,
            isBalanced = totalAssets == (totalLiabilities + totalEquity)
        )
    }

    fun buildCashFlow(
        scopeLabel: String,
        cashBankAccounts: List<ChartOfAccountRecord>,
        cashbookTxsInRange: List<CashbookTransactionRecord>,
        allCashbookTxsBeforeRange: List<CashbookTransactionRecord>,
        postedEntriesById: Map<String, JournalEntryRecord>
    ): CashFlowReport {
        val initialOpening = cashBankAccounts.sumOf { it.openingBalanceCents }
        val priorDelta = allCashbookTxsBeforeRange.sumOf {
            if (it.txType.isInflow) it.amountCents else -it.amountCents
        }
        val openingCashAndBank = initialOpening + priorDelta

        var cashSalesReceipts = 0L
        var customerReceipts = 0L
        var otherReceipts = 0L
        var supplierOutflows = 0L
        var expenseOutflows = 0L
        var payrollOutflows = 0L
        var otherOutflows = 0L

        cashbookTxsInRange.forEach { tx ->
            val src = postedEntriesById[tx.linkedJournalEntryId]?.sourceModule
            when (tx.txType) {
                CashbookTxType.RECEIPT -> when (src) {
                    JournalSourceModule.POS_SALE -> cashSalesReceipts += tx.amountCents
                    JournalSourceModule.CUSTOMER_PAYMENT -> customerReceipts += tx.amountCents
                    else -> otherReceipts += tx.amountCents
                }
                CashbookTxType.PAYMENT -> when (src) {
                    JournalSourceModule.SUPPLIER_PAYMENT -> supplierOutflows += tx.amountCents
                    JournalSourceModule.EXPENSE -> expenseOutflows += tx.amountCents
                    JournalSourceModule.PAYROLL_PAYMENT -> payrollOutflows += tx.amountCents
                    else -> otherOutflows += tx.amountCents
                }
                CashbookTxType.TRANSFER_IN, CashbookTxType.TRANSFER_OUT -> {
                    // Internal cash/bank transfers cancel out in consolidated cash & bank total
                }
            }
        }

        val totalInflows = cashSalesReceipts + customerReceipts + otherReceipts
        val totalOutflows = supplierOutflows + expenseOutflows + payrollOutflows + otherOutflows
        val netFlow = totalInflows - totalOutflows

        return CashFlowReport(
            scopeLabel = scopeLabel,
            openingCashAndBankCents = openingCashAndBank,
            cashSalesReceiptsCents = cashSalesReceipts,
            customerReceiptsCents = customerReceipts,
            otherReceiptsCents = otherReceipts,
            supplierPaymentsOutflowCents = supplierOutflows,
            expensePaymentsOutflowCents = expenseOutflows,
            payrollPaymentsOutflowCents = payrollOutflows,
            otherPaymentsOutflowCents = otherOutflows,
            netOperatingCashFlowCents = netFlow,
            closingCashAndBankCents = openingCashAndBank + netFlow
        )
    }
}
