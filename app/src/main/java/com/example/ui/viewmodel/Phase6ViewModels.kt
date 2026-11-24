package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ExpenseEntity
import com.example.data.local.PayrollRecordEntity
import com.example.data.local.PosOrderEntity
import com.example.data.local.PurchaseOrderEntity
import com.example.data.local.PurchaseReturnEntity
import com.example.data.local.StaffProfileEntity
import com.example.data.local.SupplierPaymentEntity
import com.example.data.repository.AccountingRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.MultiBranchRepository
import com.example.domain.model.AccountCategory
import com.example.domain.model.AccountType
import com.example.domain.model.AccountingCalculator
import com.example.domain.model.BalanceSheetReport
import com.example.domain.model.BranchSummary
import com.example.domain.model.CashBankAccountSummary
import com.example.domain.model.CashFlowReport
import com.example.domain.model.CashbookTransactionRecord
import com.example.domain.model.CashbookTxType
import com.example.domain.model.ChartOfAccountRecord
import com.example.domain.model.ConsolidatedFinancialReport
import com.example.domain.model.CustomerProfileRecord
import com.example.domain.model.ExpenseApprovalStatus
import com.example.domain.model.FinancialReportType
import com.example.domain.model.GeneralLedgerAccountSection
import com.example.domain.model.JournalDraftLine
import com.example.domain.model.JournalEntryRecord
import com.example.domain.model.JournalEntryStatus
import com.example.domain.model.OutstandingBalanceRow
import com.example.domain.model.PartyLedgerReport
import com.example.domain.model.PayrollSummaryReportData
import com.example.domain.model.ProfitAndLossReport
import com.example.domain.model.PurchaseStatus
import com.example.domain.model.SalesSummaryReportData
import com.example.domain.model.SupplierSummary
import com.example.domain.model.TrialBalanceReport
import com.example.domain.model.UserSession
import com.example.printing.FinancialReportBundle
import com.example.printing.Phase6ReportExporter
import com.example.printing.ReportExportFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.UUID

enum class DateRangeFilterPreset(val label: String) {
    ALL_TIME("All Dates"),
    TODAY("Today"),
    LAST_7_DAYS("7 Days"),
    LAST_30_DAYS("30 Days"),
    CUSTOM("Custom");

    fun resolveBounds(customStart: Long?, customEnd: Long?, now: Long = System.currentTimeMillis()): Pair<Long?, Long?> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        val todayEnd = todayStart + 86_399_999L
        return when (this) {
            ALL_TIME -> null to null
            TODAY -> todayStart to todayEnd
            LAST_7_DAYS -> (todayStart - 6 * 86_400_000L) to todayEnd
            LAST_30_DAYS -> (todayStart - 29 * 86_400_000L) to todayEnd
            CUSTOM -> customStart to customEnd
        }
    }
}

enum class AccountingTab(val label: String) {
    COA("Chart of Accounts"),
    JOURNALS("Journal Entries"),
    CASHBOOK("Cashbook"),
    LEDGERS("Customer & Supplier")
}

data class AccountingUiState(
    val branches: List<BranchSummary> = emptyList(),
    val selectedBranchId: String? = null, // null == Consolidated (Owner/SuperAdmin only)
    val scopeLabel: String = "Active Branch",
    val selectedTab: AccountingTab = AccountingTab.COA,
    val searchQuery: String = "",
    val datePreset: DateRangeFilterPreset = DateRangeFilterPreset.ALL_TIME,
    val customStartMillis: Long? = null,
    val customEndMillis: Long? = null,
    val accounts: List<ChartOfAccountRecord> = emptyList(),
    val filteredAccounts: List<ChartOfAccountRecord> = emptyList(),
    val journalEntries: List<JournalEntryRecord> = emptyList(),
    val filteredJournals: List<JournalEntryRecord> = emptyList(),
    val cashBankSummaries: List<CashBankAccountSummary> = emptyList(),
    val cashbookTransactions: List<CashbookTransactionRecord> = emptyList(),
    val filteredCashbook: List<CashbookTransactionRecord> = emptyList(),
    val customers: List<CustomerProfileRecord> = emptyList(),
    val suppliers: List<SupplierSummary> = emptyList(),
    val selectedCustomerId: String? = null,
    val selectedSupplierId: String? = null,
    val customerLedger: PartyLedgerReport? = null,
    val supplierLedger: PartyLedgerReport? = null,
    val totalCashAndBankCents: Long = 0L,
    val totalReceivablesCents: Long = 0L,
    val totalPayablesCents: Long = 0L,
    val netProfitCents: Long = 0L,
    val isSyncing: Boolean = false,
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class AccountingViewModel(
    private val authRepository: AuthRepository,
    private val accountingRepository: AccountingRepository,
    private val initialSession: UserSession
) : ViewModel() {

    // Non-consolidated roles stay locked to their active branch; Owners default to active branch too and can switch to Consolidated (null)
    private val _branchOverride = MutableStateFlow<String?>(initialSession.branchId)
    private val _selectedTab = MutableStateFlow(AccountingTab.COA)
    private val _searchQuery = MutableStateFlow("")
    private val _datePreset = MutableStateFlow(DateRangeFilterPreset.ALL_TIME)
    private val _customStart = MutableStateFlow<Long?>(null)
    private val _customEnd = MutableStateFlow<Long?>(null)
    private val _selectedCustomerId = MutableStateFlow<String?>(null)
    private val _selectedSupplierId = MutableStateFlow<String?>(null)
    private val _isSyncing = MutableStateFlow(false)
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val effectiveBranchFlow: Flow<String?> = combine(
        authRepository.activeBranchId,
        _branchOverride
    ) { activeBranch, override ->
        if (initialSession.capabilities.canViewConsolidatedReports) override else activeBranch
    }

    init {
        syncStoredTransactions(initialSession, silent = true)
    }

    val uiState: StateFlow<AccountingUiState> = combine(
        accountingRepository.observeBranches(),
        effectiveBranchFlow,
        effectiveBranchFlow.flatMapLatest { accountingRepository.observeChartOfAccounts(it) },
        effectiveBranchFlow.flatMapLatest { accountingRepository.observeJournalEntries(it) },
        effectiveBranchFlow.flatMapLatest { accountingRepository.observeCashbookTransactions(it) },
        effectiveBranchFlow.flatMapLatest { accountingRepository.observeCustomers(it) },
        effectiveBranchFlow.flatMapLatest { accountingRepository.observeSuppliers(it) },
        effectiveBranchFlow.flatMapLatest { accountingRepository.observePosOrders(it) },
        effectiveBranchFlow.flatMapLatest { accountingRepository.observePurchaseOrders(it) },
        combine(
            accountingRepository.observeAllPurchaseReturns(),
            accountingRepository.observeAllSupplierPayments()
        ) { r, p -> r to p },
        combine(
            _selectedTab,
            _searchQuery,
            _datePreset,
            _customStart,
            _customEnd
        ) { tab, q, preset, cs, ce -> FilterTuple(tab, q, preset, cs, ce) },
        combine(
            _selectedCustomerId,
            _selectedSupplierId,
            _isSyncing,
            _feedback
        ) { cId, sId, syncing, fb -> MiscTuple(cId, sId, syncing, fb) }
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val branches = args[0] as List<BranchSummary>
        val selectedBranchId = args[1] as String?
        @Suppress("UNCHECKED_CAST")
        val accounts = args[2] as List<ChartOfAccountRecord>
        @Suppress("UNCHECKED_CAST")
        val journals = args[3] as List<JournalEntryRecord>
        @Suppress("UNCHECKED_CAST")
        val cashbook = args[4] as List<CashbookTransactionRecord>
        @Suppress("UNCHECKED_CAST")
        val customers = args[5] as List<CustomerProfileRecord>
        @Suppress("UNCHECKED_CAST")
        val suppliers = args[6] as List<SupplierSummary>
        @Suppress("UNCHECKED_CAST")
        val posOrders = args[7] as List<PosOrderEntity>
        @Suppress("UNCHECKED_CAST")
        val purchases = args[8] as List<PurchaseOrderEntity>
        @Suppress("UNCHECKED_CAST")
        val returnsAndPays = args[9] as Pair<List<PurchaseReturnEntity>, List<SupplierPaymentEntity>>
        val filters = args[10] as FilterTuple
        val misc = args[11] as MiscTuple

        val q = filters.searchQuery.trim()
        val (startMs, endMs) = filters.datePreset.resolveBounds(filters.customStart, filters.customEnd)

        val filteredAccs = if (q.isBlank()) accounts else {
            accounts.filter {
                it.accountCode.contains(q, true) ||
                    it.accountName.contains(q, true) ||
                    it.accountType.label.contains(q, true)
            }
        }

        val filteredJournals = journals.filter { je ->
            val afterStart = startMs == null || je.entryDateMillis >= startMs
            val beforeEnd = endMs == null || je.entryDateMillis <= endMs
            val matchQ = q.isBlank() ||
                je.entryNumber.contains(q, true) ||
                je.reference.contains(q, true) ||
                je.description.contains(q, true) ||
                je.sourceModule.label.contains(q, true) ||
                je.lines.any { it.accountName.contains(q, true) || (it.partyName?.contains(q, true) == true) }
            afterStart && beforeEnd && matchQ
        }

        val filteredCb = cashbook.filter { cb ->
            val afterStart = startMs == null || cb.txDateMillis >= startMs
            val beforeEnd = endMs == null || cb.txDateMillis <= endMs
            val matchQ = q.isBlank() ||
                cb.accountName.contains(q, true) ||
                cb.reference.contains(q, true) ||
                cb.counterpartyName.contains(q, true) ||
                cb.description.contains(q, true)
            afterStart && beforeEnd && matchQ
        }

        val cbSummaries = accountingRepository.buildCashBankSummaries(accounts, filteredCb)
        val totalCashBank = accounts.filter { it.isCashOrBank }.sumOf { it.currentBalanceCents }
        val totalAr = accounts.filter { it.accountCategory == AccountCategory.ACCOUNTS_RECEIVABLE }.sumOf { it.currentBalanceCents }
        val totalAp = accounts.filter { it.accountCategory == AccountCategory.ACCOUNTS_PAYABLE }.sumOf { it.currentBalanceCents }

        val validLines = filteredJournals.flatMap { it.lines }
        val pnl = AccountingCalculator.buildProfitAndLoss("Scope", accounts, validLines)

        val activeCustId = misc.selectedCustomerId ?: customers.firstOrNull()?.customerId
        val activeSupId = misc.selectedSupplierId ?: suppliers.firstOrNull()?.supplierId
        val branchReturns = returnsAndPays.first.filter { selectedBranchId == null || it.branchId == selectedBranchId }
        val branchPays = returnsAndPays.second.filter { selectedBranchId == null || it.branchId == selectedBranchId }

        val custLedger = activeCustId?.let { cid ->
            customers.firstOrNull { it.customerId == cid }?.let { cust ->
                accountingRepository.buildCustomerLedger(cust, posOrders, cashbook, journals)
            }
        }
        val supLedger = activeSupId?.let { sid ->
            suppliers.firstOrNull { it.supplierId == sid }?.let { sup ->
                accountingRepository.buildSupplierLedger(sup, purchases, branchReturns, branchPays)
            }
        }

        val scopeLabel = if (selectedBranchId == null) {
            "Consolidated (All Branches)"
        } else {
            branches.firstOrNull { it.branchId == selectedBranchId }?.name ?: initialSession.branchName
        }

        AccountingUiState(
            branches = branches,
            selectedBranchId = selectedBranchId,
            scopeLabel = scopeLabel,
            selectedTab = filters.tab,
            searchQuery = filters.searchQuery,
            datePreset = filters.datePreset,
            customStartMillis = filters.customStart,
            customEndMillis = filters.customEnd,
            accounts = accounts,
            filteredAccounts = filteredAccs,
            journalEntries = journals,
            filteredJournals = filteredJournals,
            cashBankSummaries = cbSummaries,
            cashbookTransactions = cashbook,
            filteredCashbook = filteredCb,
            customers = customers,
            suppliers = suppliers,
            selectedCustomerId = activeCustId,
            selectedSupplierId = activeSupId,
            customerLedger = custLedger,
            supplierLedger = supLedger,
            totalCashAndBankCents = totalCashBank,
            totalReceivablesCents = totalAr,
            totalPayablesCents = totalAp,
            netProfitCents = pnl.netProfitCents,
            isSyncing = misc.isSyncing,
            feedbackMessage = misc.feedback.first,
            isErrorFeedback = misc.feedback.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountingUiState())

    fun selectBranchScope(session: UserSession, branchId: String?) {
        if (branchId == null && !session.capabilities.canViewConsolidatedReports) {
            _feedback.value = "Consolidated multi-branch view requires Owner or Super Admin authorization." to true
            return
        }
        if (branchId != null && !session.capabilities.canSwitchBranches && branchId != session.branchId) {
            _feedback.value = "Access denied: unauthorized to view another branch's accounting." to true
            return
        }
        _branchOverride.value = branchId
    }

    fun selectTab(tab: AccountingTab) {
        _selectedTab.value = tab
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectDatePreset(preset: DateRangeFilterPreset) {
        _datePreset.value = preset
    }

    fun setCustomDateRange(startMillis: Long?, endMillis: Long?) {
        _customStart.value = startMillis
        _customEnd.value = endMillis
        _datePreset.value = DateRangeFilterPreset.CUSTOM
    }

    fun selectCustomerForLedger(customerId: String) {
        _selectedCustomerId.value = customerId
    }

    fun selectSupplierForLedger(supplierId: String) {
        _selectedSupplierId.value = supplierId
    }

    fun syncStoredTransactions(session: UserSession, silent: Boolean = false) {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = accountingRepository.syncAllStoredTransactions(session)
            _isSyncing.value = false
            res.onSuccess { count ->
                if (!silent || count > 0) {
                    _feedback.value = if (count > 0) {
                        "Synchronized $count ERP transaction(s) into double-entry accounting."
                    } else {
                        "All POS, Purchase, Expense & Payroll records are already synchronized."
                    } to false
                }
            }.onFailure {
                if (!silent) _feedback.value = (it.message ?: "Accounting sync failed.") to true
            }
        }
    }

    fun saveChartAccount(
        session: UserSession,
        branchId: String,
        accountId: String?,
        accountCode: String,
        accountName: String,
        accountType: AccountType,
        accountCategory: AccountCategory,
        openingBalanceCents: Long,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            val res = accountingRepository.saveChartAccount(
                session, branchId, accountId, accountCode, accountName, accountType, accountCategory, openingBalanceCents, isActive
            )
            res.onSuccess {
                _feedback.value = "Saved account ${it.accountCode} ${it.accountName}." to false
            }.onFailure {
                _feedback.value = (it.message ?: "Failed to save account.") to true
            }
        }
    }

    fun postManualJournal(
        session: UserSession,
        branchId: String,
        entryDateMillis: Long,
        reference: String,
        description: String,
        draftLines: List<JournalDraftLine>
    ) {
        viewModelScope.launch {
            val res = accountingRepository.postManualJournalEntry(
                session = session,
                branchId = branchId,
                idempotencyKey = "manual_${UUID.randomUUID()}",
                entryDateMillis = entryDateMillis,
                reference = reference,
                description = description,
                draftLines = draftLines
            )
            res.onSuccess {
                _feedback.value = "Posted balanced journal entry ${it.entryNumber}." to false
            }.onFailure {
                _feedback.value = (it.message ?: "Failed to post journal entry.") to true
            }
        }
    }

    fun reverseJournalEntry(session: UserSession, entryId: String, reason: String) {
        viewModelScope.launch {
            val res = accountingRepository.reverseJournalEntry(session, entryId, reason)
            res.onSuccess {
                _feedback.value = "Posted authorized reversal ${it.entryNumber}." to false
            }.onFailure {
                _feedback.value = (it.message ?: "Failed to reverse journal entry.") to true
            }
        }
    }

    fun recordCashbookTransaction(
        session: UserSession,
        branchId: String,
        txType: CashbookTxType,
        cashBankAccountId: String,
        counterAccountId: String,
        amountCents: Long,
        txDateMillis: Long,
        reference: String,
        counterpartyName: String,
        description: String
    ) {
        viewModelScope.launch {
            val res = accountingRepository.recordCashbookTransaction(
                session = session,
                branchId = branchId,
                idempotencyKey = "cb_${UUID.randomUUID()}",
                txType = txType,
                cashBankAccountId = cashBankAccountId,
                counterAccountId = counterAccountId,
                amountCents = amountCents,
                txDateMillis = txDateMillis,
                reference = reference,
                counterpartyName = counterpartyName,
                description = description
            )
            res.onSuccess {
                _feedback.value = "Recorded ${it.txType.label} (${it.reference})." to false
            }.onFailure {
                _feedback.value = (it.message ?: "Failed to record cashbook entry.") to true
            }
        }
    }

    fun recordCustomerPayment(
        session: UserSession,
        branchId: String,
        customerId: String,
        cashBankAccountId: String,
        amountCents: Long,
        reference: String,
        paymentDateMillis: Long,
        notes: String
    ) {
        viewModelScope.launch {
            val res = accountingRepository.recordCustomerPayment(
                session = session,
                branchId = branchId,
                idempotencyKey = "custpay_${UUID.randomUUID()}",
                customerId = customerId,
                cashBankAccountId = cashBankAccountId,
                amountCents = amountCents,
                reference = reference,
                paymentDateMillis = paymentDateMillis,
                notes = notes
            )
            res.onSuccess {
                _feedback.value = "Customer receipt ${it.reference} posted & customer balance updated." to false
            }.onFailure {
                _feedback.value = (it.message ?: "Failed to record customer payment.") to true
            }
        }
    }

    fun showFeedback(message: String, isError: Boolean = false) {
        _feedback.value = message to isError
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    private data class FilterTuple(
        val tab: AccountingTab,
        val searchQuery: String,
        val datePreset: DateRangeFilterPreset,
        val customStart: Long?,
        val customEnd: Long?
    )

    private data class MiscTuple(
        val selectedCustomerId: String?,
        val selectedSupplierId: String?,
        val isSyncing: Boolean,
        val feedback: Pair<String?, Boolean>
    )

    class Factory(
        private val authRepository: AuthRepository,
        private val accountingRepository: AccountingRepository,
        private val initialSession: UserSession
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AccountingViewModel(authRepository, accountingRepository, initialSession) as T
    }
}

// ---------------- 2. FINANCIAL REPORTS VIEWMODEL ----------------

data class FinancialReportsUiState(
    val branches: List<BranchSummary> = emptyList(),
    val authorizedReportTypes: List<FinancialReportType> = emptyList(),
    val selectedReportType: FinancialReportType = FinancialReportType.PROFIT_AND_LOSS,
    val selectedBranchId: String? = null,
    val scopeLabel: String = "Active Branch",
    val datePreset: DateRangeFilterPreset = DateRangeFilterPreset.ALL_TIME,
    val customStartMillis: Long? = null,
    val customEndMillis: Long? = null,
    val searchQuery: String = "",
    val selectedGlAccountId: String? = null,
    val accounts: List<ChartOfAccountRecord> = emptyList(),
    val profitAndLoss: ProfitAndLossReport = ProfitAndLossReport("", emptyList(), emptyList(), emptyList(), emptyList(), 0, 0, 0, 0, 0, 0),
    val balanceSheet: BalanceSheetReport = BalanceSheetReport("", emptyList(), emptyList(), emptyList(), 0, 0, 0, 0, true),
    val cashFlow: CashFlowReport = CashFlowReport("", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    val trialBalance: TrialBalanceReport = TrialBalanceReport("", emptyList(), 0, 0, true),
    val generalLedger: List<GeneralLedgerAccountSection> = emptyList(),
    val cashBankSummaries: List<CashBankAccountSummary> = emptyList(),
    val cashbookTransactions: List<CashbookTransactionRecord> = emptyList(),
    val salesSummary: SalesSummaryReportData = SalesSummaryReportData(0, 0, 0, 0, 0, 0, 0, 0),
    val salesOrders: List<PosOrderEntity> = emptyList(),
    val purchaseOrders: List<PurchaseOrderEntity> = emptyList(),
    val approvedExpenses: List<ExpenseEntity> = emptyList(),
    val expenseByCategory: Map<String, Long> = emptyMap(),
    val payrollSummary: PayrollSummaryReportData = PayrollSummaryReportData(0, 0, 0, 0, 0, 0, 0, 0),
    val payrollRecords: List<PayrollRecordEntity> = emptyList(),
    val customerOutstanding: List<OutstandingBalanceRow> = emptyList(),
    val supplierOutstanding: List<OutstandingBalanceRow> = emptyList(),
    val consolidatedReport: ConsolidatedFinancialReport? = null,
    val lastExportedFile: File? = null,
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
) {
    fun toExportBundle(): FinancialReportBundle = FinancialReportBundle(
        reportType = selectedReportType,
        scopeLabel = scopeLabel,
        profitAndLoss = profitAndLoss,
        balanceSheet = balanceSheet,
        cashFlow = cashFlow,
        trialBalance = trialBalance,
        generalLedger = generalLedger,
        cashBankSummaries = cashBankSummaries,
        cashbookTransactions = cashbookTransactions,
        salesSummary = salesSummary,
        posOrders = salesOrders,
        purchaseOrders = purchaseOrders,
        approvedExpenses = approvedExpenses,
        payrollSummary = payrollSummary,
        payrollRecords = payrollRecords,
        customerOutstanding = customerOutstanding,
        supplierOutstanding = supplierOutstanding,
        consolidatedReport = consolidatedReport
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class FinancialReportsViewModel(
    private val authRepository: AuthRepository,
    private val accountingRepository: AccountingRepository,
    private val initialSession: UserSession
) : ViewModel() {

    private val authorizedTypes = Phase6ReportExporter.authorizedReportTypesFor(initialSession)
    private val _branchOverride = MutableStateFlow<String?>(initialSession.branchId)
    private val _reportType = MutableStateFlow(authorizedTypes.firstOrNull() ?: FinancialReportType.PURCHASE_SUMMARY)
    private val _datePreset = MutableStateFlow(DateRangeFilterPreset.ALL_TIME)
    private val _customStart = MutableStateFlow<Long?>(null)
    private val _customEnd = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedGlAccountId = MutableStateFlow<String?>(null)
    private val _lastExported = MutableStateFlow<File?>(null)
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val effectiveBranchFlow: Flow<String?> = combine(
        authRepository.activeBranchId,
        _branchOverride
    ) { activeBranch, override ->
        if (initialSession.capabilities.canViewConsolidatedReports) override else activeBranch
    }

    val uiState: StateFlow<FinancialReportsUiState> = combine(
        accountingRepository.observeBranches(),
        effectiveBranchFlow,
        effectiveBranchFlow.flatMapLatest { accountingRepository.observeChartOfAccounts(it) },
        effectiveBranchFlow.flatMapLatest { accountingRepository.observeJournalEntries(it) },
        effectiveBranchFlow.flatMapLatest { accountingRepository.observeCashbookTransactions(it) },
        effectiveBranchFlow.flatMapLatest { accountingRepository.observePosOrders(it) },
        effectiveBranchFlow.flatMapLatest { accountingRepository.observePurchaseOrders(it) },
        effectiveBranchFlow.flatMapLatest { accountingRepository.observeExpenses(it) },
        effectiveBranchFlow.flatMapLatest { accountingRepository.observePayrolls(it) },
        combine(
            effectiveBranchFlow.flatMapLatest { accountingRepository.observeCustomers(it) },
            effectiveBranchFlow.flatMapLatest { accountingRepository.observeSuppliers(it) }
        ) { c, s -> c to s },
        combine(
            accountingRepository.observeChartOfAccounts(null),
            accountingRepository.observeJournalEntries(null),
            accountingRepository.observePosOrders(null),
            accountingRepository.observePurchaseOrders(null),
            accountingRepository.observeExpenses(null),
            accountingRepository.observePayrolls(null),
            accountingRepository.observeAllStaff()
        ) { allArgs ->
            ConsolidatedRawBundle(
                accounts = @Suppress("UNCHECKED_CAST") (allArgs[0] as List<ChartOfAccountRecord>),
                journals = @Suppress("UNCHECKED_CAST") (allArgs[1] as List<JournalEntryRecord>),
                orders = @Suppress("UNCHECKED_CAST") (allArgs[2] as List<PosOrderEntity>),
                purchases = @Suppress("UNCHECKED_CAST") (allArgs[3] as List<PurchaseOrderEntity>),
                expenses = @Suppress("UNCHECKED_CAST") (allArgs[4] as List<ExpenseEntity>),
                payrolls = @Suppress("UNCHECKED_CAST") (allArgs[5] as List<PayrollRecordEntity>),
                staff = @Suppress("UNCHECKED_CAST") (allArgs[6] as List<StaffProfileEntity>)
            )
        },
        combine(
            _reportType,
            _datePreset,
            _customStart,
            _customEnd,
            _searchQuery,
            _selectedGlAccountId,
            _lastExported,
            _feedback
        ) { fArgs ->
            ReportsControlBundle(
                reportType = fArgs[0] as FinancialReportType,
                datePreset = fArgs[1] as DateRangeFilterPreset,
                customStart = fArgs[2] as Long?,
                customEnd = fArgs[3] as Long?,
                searchQuery = fArgs[4] as String,
                glAccountId = fArgs[5] as String?,
                lastExported = fArgs[6] as File?,
                feedback = @Suppress("UNCHECKED_CAST") (fArgs[7] as Pair<String?, Boolean>)
            )
        }
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val branches = args[0] as List<BranchSummary>
        val selectedBranchId = args[1] as String?
        @Suppress("UNCHECKED_CAST")
        val accounts = args[2] as List<ChartOfAccountRecord>
        @Suppress("UNCHECKED_CAST")
        val journals = args[3] as List<JournalEntryRecord>
        @Suppress("UNCHECKED_CAST")
        val cashbook = args[4] as List<CashbookTransactionRecord>
        @Suppress("UNCHECKED_CAST")
        val posOrders = args[5] as List<PosOrderEntity>
        @Suppress("UNCHECKED_CAST")
        val purchases = args[6] as List<PurchaseOrderEntity>
        @Suppress("UNCHECKED_CAST")
        val expenses = args[7] as List<ExpenseEntity>
        @Suppress("UNCHECKED_CAST")
        val payrolls = args[8] as List<PayrollRecordEntity>
        @Suppress("UNCHECKED_CAST")
        val parties = args[9] as Pair<List<CustomerProfileRecord>, List<SupplierSummary>>
        val consolidatedRaw = args[10] as ConsolidatedRawBundle
        val ctrl = args[11] as ReportsControlBundle

        val (startMs, endMs) = ctrl.datePreset.resolveBounds(ctrl.customStart, ctrl.customEnd)
        val q = ctrl.searchQuery.trim()
        val scopeLabel = if (selectedBranchId == null) {
            "Consolidated (All Branches)"
        } else {
            branches.firstOrNull { it.branchId == selectedBranchId }?.name ?: initialSession.branchName
        }

        val journalsInRange = journals.filter { j ->
            (startMs == null || j.entryDateMillis >= startMs) &&
                (endMs == null || j.entryDateMillis <= endMs)
        }
        val journalsUpToEnd = journals.filter { j -> endMs == null || j.entryDateMillis <= endMs }
        val linesInRange = journalsInRange.flatMap { it.lines }
        val linesUpToEnd = journalsUpToEnd.flatMap { it.lines }

        val pnl = AccountingCalculator.buildProfitAndLoss(scopeLabel, accounts, linesInRange)
        val bs = AccountingCalculator.buildBalanceSheet(scopeLabel, accounts, linesUpToEnd)
        val tb = AccountingCalculator.buildTrialBalance(scopeLabel, accounts, linesUpToEnd)
        val gl = accountingRepository.buildGeneralLedger(
            accounts = accounts,
            entries = journals,
            selectedAccountId = ctrl.glAccountId,
            startMillis = startMs,
            endMillis = endMs,
            searchQuery = q
        )

        val cbInRange = cashbook.filter { tx ->
            (startMs == null || tx.txDateMillis >= startMs) &&
                (endMs == null || tx.txDateMillis <= endMs) &&
                (q.isBlank() || tx.reference.contains(q, true) || tx.counterpartyName.contains(q, true) || tx.description.contains(q, true))
        }
        val cbBeforeRange = if (startMs == null) emptyList() else cashbook.filter { it.txDateMillis < startMs }
        val cashBankAccounts = accounts.filter { it.isCashOrBank }
        val cf = AccountingCalculator.buildCashFlow(
            scopeLabel = scopeLabel,
            cashBankAccounts = cashBankAccounts,
            cashbookTxsInRange = cbInRange,
            allCashbookTxsBeforeRange = cbBeforeRange,
            postedEntriesById = journals.associateBy { it.entryId }
        )
        val cbSummaries = accountingRepository.buildCashBankSummaries(accounts, cbInRange)

        val filteredOrders = posOrders.filter { o ->
            (startMs == null || o.createdAt >= startMs) &&
                (endMs == null || o.createdAt <= endMs) &&
                (q.isBlank() || o.invoiceNumber.contains(q, true) || o.orderNumber.contains(q, true) || (o.customerName?.contains(q, true) == true))
        }
        val salesSummary = accountingRepository.buildSalesSummary(filteredOrders)

        val filteredPos = purchases.filter { po ->
            (startMs == null || po.createdAt >= startMs) &&
                (endMs == null || po.createdAt <= endMs) &&
                (q.isBlank() || po.poNumber.contains(q, true) || po.supplierName.contains(q, true))
        }

        val approvedExpenses = expenses.filter { e ->
            e.approvalStatus == ExpenseApprovalStatus.APPROVED.name &&
                (startMs == null || e.expenseDateMillis >= startMs) &&
                (endMs == null || e.expenseDateMillis <= endMs) &&
                (q.isBlank() || e.category.contains(q, true) || e.description.contains(q, true))
        }
        val expenseByCat = approvedExpenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amountCents } }

        val filteredPayrolls = payrolls.filter { p ->
            (startMs == null || p.periodEndMillis >= startMs) &&
                (endMs == null || p.periodStartMillis <= endMs) &&
                (q.isBlank() || p.staffName.contains(q, true) || p.employeeCode.contains(q, true) || p.periodLabel.contains(q, true))
        }
        val payrollSummary = accountingRepository.buildPayrollSummary(filteredPayrolls)

        val custRows = accountingRepository.buildCustomerOutstandingRows(branches, parties.first, posOrders)
            .filter { q.isBlank() || it.partyName.contains(q, true) || it.contact.contains(q, true) }
        val supRows = accountingRepository.buildSupplierOutstandingRows(branches, parties.second)
            .filter { q.isBlank() || it.partyName.contains(q, true) || it.contact.contains(q, true) }

        val consolidated = if (initialSession.capabilities.canViewConsolidatedReports) {
            accountingRepository.buildConsolidatedFinancialReport(
                branches = branches,
                accounts = consolidatedRaw.accounts,
                entries = consolidatedRaw.journals,
                posOrders = consolidatedRaw.orders,
                purchases = consolidatedRaw.purchases,
                expenses = consolidatedRaw.expenses,
                payrolls = consolidatedRaw.payrolls,
                staffList = consolidatedRaw.staff
            )
        } else null

        FinancialReportsUiState(
            branches = branches,
            authorizedReportTypes = authorizedTypes,
            selectedReportType = ctrl.reportType,
            selectedBranchId = selectedBranchId,
            scopeLabel = scopeLabel,
            datePreset = ctrl.datePreset,
            customStartMillis = ctrl.customStart,
            customEndMillis = ctrl.customEnd,
            searchQuery = ctrl.searchQuery,
            selectedGlAccountId = ctrl.glAccountId,
            accounts = accounts,
            profitAndLoss = pnl,
            balanceSheet = bs,
            cashFlow = cf,
            trialBalance = tb,
            generalLedger = gl,
            cashBankSummaries = cbSummaries,
            cashbookTransactions = cbInRange,
            salesSummary = salesSummary,
            salesOrders = filteredOrders,
            purchaseOrders = filteredPos,
            approvedExpenses = approvedExpenses,
            expenseByCategory = expenseByCat,
            payrollSummary = payrollSummary,
            payrollRecords = filteredPayrolls,
            customerOutstanding = custRows,
            supplierOutstanding = supRows,
            consolidatedReport = consolidated,
            lastExportedFile = ctrl.lastExported,
            feedbackMessage = ctrl.feedback.first,
            isErrorFeedback = ctrl.feedback.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinancialReportsUiState(authorizedReportTypes = authorizedTypes))

    fun selectReportType(session: UserSession, type: FinancialReportType) {
        if (!Phase6ReportExporter.canAccessReport(session, type)) {
            _feedback.value = "Access denied: ${session.role.displayName} cannot view ${type.title}." to true
            return
        }
        _reportType.value = type
    }

    fun selectBranchScope(session: UserSession, branchId: String?) {
        if (branchId == null && !session.capabilities.canViewConsolidatedReports) {
            _feedback.value = "Consolidated multi-branch reports require Owner or Super Admin authorization." to true
            return
        }
        if (branchId != null && !session.capabilities.canSwitchBranches && branchId != session.branchId) {
            _feedback.value = "Access denied: unauthorized to view reports for another branch." to true
            return
        }
        _branchOverride.value = branchId
    }

    fun selectGlAccount(accountId: String?) {
        _selectedGlAccountId.value = accountId
    }

    fun selectDatePreset(preset: DateRangeFilterPreset) {
        _datePreset.value = preset
    }

    fun setCustomDateRange(startMillis: Long?, endMillis: Long?) {
        _customStart.value = startMillis
        _customEnd.value = endMillis
        _datePreset.value = DateRangeFilterPreset.CUSTOM
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun exportCurrentReport(
        context: Context,
        session: UserSession,
        format: ReportExportFormat,
        onReadyToShare: (File, ReportExportFormat) -> Unit = { _, _ -> }
    ) {
        val snapshot = uiState.value
        val result = Phase6ReportExporter.exportReport(
            context = context,
            session = session,
            bundle = snapshot.toExportBundle(),
            format = format
        )
        result.onSuccess { file ->
            _lastExported.value = file
            _feedback.value = "Exported ${snapshot.selectedReportType.title} (${format.label}): ${file.name}" to false
            onReadyToShare(file, format)
        }.onFailure {
            _feedback.value = (it.message ?: "Report export failed.") to true
        }
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    private data class ConsolidatedRawBundle(
        val accounts: List<ChartOfAccountRecord>,
        val journals: List<JournalEntryRecord>,
        val orders: List<PosOrderEntity>,
        val purchases: List<PurchaseOrderEntity>,
        val expenses: List<ExpenseEntity>,
        val payrolls: List<PayrollRecordEntity>,
        val staff: List<StaffProfileEntity>
    )

    private data class ReportsControlBundle(
        val reportType: FinancialReportType,
        val datePreset: DateRangeFilterPreset,
        val customStart: Long?,
        val customEnd: Long?,
        val searchQuery: String,
        val glAccountId: String?,
        val lastExported: File?,
        val feedback: Pair<String?, Boolean>
    )

    class Factory(
        private val authRepository: AuthRepository,
        private val accountingRepository: AccountingRepository,
        private val initialSession: UserSession
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FinancialReportsViewModel(authRepository, accountingRepository, initialSession) as T
    }
}

// ---------------- 3. MULTI-BRANCH MANAGEMENT VIEWMODEL ----------------

data class MultiBranchUiState(
    val branches: List<BranchSummary> = emptyList(),
    val staffList: List<StaffProfileEntity> = emptyList(),
    val consolidatedReport: ConsolidatedFinancialReport = ConsolidatedFinancialReport(emptyList(), 0, 0, 0, 0, 0, 0, 0),
    val searchQuery: String = "",
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

class MultiBranchViewModel(
    private val authRepository: AuthRepository,
    private val multiBranchRepository: MultiBranchRepository,
    private val accountingRepository: AccountingRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    val uiState: StateFlow<MultiBranchUiState> = combine(
        multiBranchRepository.observeBranches(),
        multiBranchRepository.observeAllStaff(),
        accountingRepository.observeChartOfAccounts(null),
        accountingRepository.observeJournalEntries(null),
        accountingRepository.observePosOrders(null),
        accountingRepository.observePurchaseOrders(null),
        accountingRepository.observeExpenses(null),
        accountingRepository.observePayrolls(null),
        _searchQuery,
        _feedback
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val branches = args[0] as List<BranchSummary>
        @Suppress("UNCHECKED_CAST")
        val staff = args[1] as List<StaffProfileEntity>
        @Suppress("UNCHECKED_CAST")
        val accounts = args[2] as List<ChartOfAccountRecord>
        @Suppress("UNCHECKED_CAST")
        val journals = args[3] as List<JournalEntryRecord>
        @Suppress("UNCHECKED_CAST")
        val orders = args[4] as List<PosOrderEntity>
        @Suppress("UNCHECKED_CAST")
        val purchases = args[5] as List<PurchaseOrderEntity>
        @Suppress("UNCHECKED_CAST")
        val expenses = args[6] as List<ExpenseEntity>
        @Suppress("UNCHECKED_CAST")
        val payrolls = args[7] as List<PayrollRecordEntity>
        val q = (args[8] as String).trim()
        @Suppress("UNCHECKED_CAST")
        val fb = args[9] as Pair<String?, Boolean>

        val report = accountingRepository.buildConsolidatedFinancialReport(
            branches = branches,
            accounts = accounts,
            entries = journals,
            posOrders = orders,
            purchases = purchases,
            expenses = expenses,
            payrolls = payrolls,
            staffList = staff
        )
        val filteredSnapshots = if (q.isBlank()) report.branchSnapshots else {
            report.branchSnapshots.filter {
                it.branch.name.contains(q, true) ||
                    it.branch.code.contains(q, true) ||
                    it.branch.managerName.contains(q, true) ||
                    it.branch.address.contains(q, true)
            }
        }

        MultiBranchUiState(
            branches = branches,
            staffList = staff,
            consolidatedReport = report.copy(branchSnapshots = filteredSnapshots),
            searchQuery = args[8] as String,
            feedbackMessage = fb.first,
            isErrorFeedback = fb.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MultiBranchUiState())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun saveBranch(
        session: UserSession,
        branchId: String?,
        name: String,
        code: String,
        address: String,
        phone: String,
        email: String,
        managerName: String,
        managerUid: String?,
        openingCashCents: Long,
        openingBankCents: Long,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            val res = multiBranchRepository.saveBranch(
                session, branchId, name, code, address, phone, email, managerName, managerUid, openingCashCents, openingBankCents, isActive
            )
            res.onSuccess {
                _feedback.value = "Saved branch ${it.code} (${it.name}) with dedicated Chart of Accounts." to false
            }.onFailure {
                _feedback.value = (it.message ?: "Failed to save branch.") to true
            }
        }
    }

    fun setBranchActiveStatus(session: UserSession, branchId: String, active: Boolean, reason: String) {
        viewModelScope.launch {
            val res = multiBranchRepository.setBranchActiveStatus(session, branchId, active, reason)
            res.onSuccess {
                _feedback.value = (if (active) "Branch reactivated." else "Branch deactivated.") to false
            }.onFailure {
                _feedback.value = (it.message ?: "Failed to update branch status.") to true
            }
        }
    }

    fun assignStaffToBranch(
        session: UserSession,
        staffId: String,
        primaryBranchId: String,
        accessibleBranchIds: List<String>,
        setAsBranchManager: Boolean
    ) {
        viewModelScope.launch {
            val res = multiBranchRepository.assignStaffToBranch(
                session, staffId, primaryBranchId, accessibleBranchIds, setAsBranchManager
            )
            res.onSuccess {
                _feedback.value = "Updated branch employee assignment & audit trail." to false
            }.onFailure {
                _feedback.value = (it.message ?: "Failed to assign employee to branch.") to true
            }
        }
    }

    fun switchActiveBranch(session: UserSession, targetBranchId: String) {
        viewModelScope.launch {
            val check = multiBranchRepository.canSwitchToBranch(session, targetBranchId)
            check.onSuccess { branch ->
                authRepository.switchActiveBranch(branch.branchId)
                _feedback.value = "Switched workspace to ${branch.name}." to false
            }.onFailure {
                _feedback.value = (it.message ?: "Unauthorized to switch branch.") to true
            }
        }
    }

    fun exportConsolidated(
        context: Context,
        session: UserSession,
        format: ReportExportFormat,
        onReadyToShare: (File, ReportExportFormat) -> Unit = { _, _ -> }
    ) {
        val res = Phase6ReportExporter.exportConsolidatedBranchComparison(
            context = context,
            session = session,
            report = uiState.value.consolidatedReport,
            format = format
        )
        res.onSuccess { f ->
            _feedback.value = "Exported Multi-Branch Consolidated (${format.label}): ${f.name}" to false
            onReadyToShare(f, format)
        }.onFailure {
            _feedback.value = (it.message ?: "Export failed.") to true
        }
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val multiBranchRepository: MultiBranchRepository,
        private val accountingRepository: AccountingRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MultiBranchViewModel(authRepository, multiBranchRepository, accountingRepository) as T
    }
}
