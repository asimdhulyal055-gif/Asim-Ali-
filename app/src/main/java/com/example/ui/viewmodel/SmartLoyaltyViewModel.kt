package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.brand.BrandAssetManager
import com.example.data.brand.BrandLogoState
import com.example.data.repository.AuthRepository
import com.example.data.repository.CustomerLoyaltyRepository
import com.example.data.repository.MenuRepository
import com.example.data.repository.MultiBranchRepository
import com.example.data.repository.SmartLoyaltyRepository
import com.example.domain.model.BranchSummary
import com.example.domain.model.CustomerLoyaltyBundle
import com.example.domain.model.CustomerProfileRecord
import com.example.domain.model.LoyaltyAnalyticsReport
import com.example.domain.model.LoyaltyAuditLogRecord
import com.example.domain.model.LoyaltyCardRecord
import com.example.domain.model.LoyaltyCardStatus
import com.example.domain.model.LoyaltyProgramSettingsRecord
import com.example.domain.model.LoyaltyRedemptionRecord
import com.example.domain.model.LoyaltyRewardVoucherRecord
import com.example.domain.model.LoyaltyStampTransactionRecord
import com.example.domain.model.ProductCategory
import com.example.domain.model.UserSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SmartLoyaltyUiState(
    val settings: LoyaltyProgramSettingsRecord = LoyaltyProgramSettingsRecord(branchId = "branch_main"),
    val cards: List<LoyaltyCardRecord> = emptyList(),
    val filteredCards: List<LoyaltyCardRecord> = emptyList(),
    val vouchers: List<LoyaltyRewardVoucherRecord> = emptyList(),
    val stampTransactions: List<LoyaltyStampTransactionRecord> = emptyList(),
    val redemptions: List<LoyaltyRedemptionRecord> = emptyList(),
    val auditLogs: List<LoyaltyAuditLogRecord> = emptyList(),
    val customers: List<CustomerProfileRecord> = emptyList(),
    val categories: List<ProductCategory> = emptyList(),
    val branches: List<BranchSummary> = emptyList(),
    val selectedBranchScope: String? = null, // null = All branches (Owner/SuperAdmin) or activeBranch
    val selectedStatusFilter: LoyaltyCardStatus? = null,
    val searchQuery: String = "",
    val selectedCustomerBundle: CustomerLoyaltyBundle? = null,
    val scannedVoucher: LoyaltyRewardVoucherRecord? = null,
    val analyticsReport: LoyaltyAnalyticsReport? = null,
    val reportPresetDays: Int? = 30, // null = All time, 7 = Last 7d, 30 = Last 30d
    val logoState: BrandLogoState = BrandLogoState(hasOwnerLogo = false),
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class SmartLoyaltyViewModel(
    private val authRepository: AuthRepository,
    private val smartLoyaltyRepository: SmartLoyaltyRepository,
    private val customerLoyaltyRepository: CustomerLoyaltyRepository,
    private val menuRepository: MenuRepository,
    private val multiBranchRepository: MultiBranchRepository,
    private val brandAssetManager: BrandAssetManager,
    private val initialSession: UserSession
) : ViewModel() {

    private val _selectedBranchScope = MutableStateFlow<String?>(
        if (initialSession.capabilities.canViewConsolidatedReports) null else initialSession.branchId
    )
    private val _statusFilter = MutableStateFlow<LoyaltyCardStatus?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedBundle = MutableStateFlow<CustomerLoyaltyBundle?>(null)
    private val _scannedVoucher = MutableStateFlow<LoyaltyRewardVoucherRecord?>(null)
    private val _analyticsReport = MutableStateFlow<LoyaltyAnalyticsReport?>(null)
    private val _reportPresetDays = MutableStateFlow<Int?>(30)
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val scopeTrigger = combine(
        authRepository.activeBranchId,
        _selectedBranchScope
    ) { activeBr, chosenScope ->
        activeBr to chosenScope
    }

    private val settingsFlow = authRepository.activeBranchId.flatMapLatest { br ->
        smartLoyaltyRepository.observeSettings(br)
    }

    private val cardsFlow = scopeTrigger.flatMapLatest { (activeBr, scope) ->
        smartLoyaltyRepository.observeCards(
            branchId = scope ?: activeBr,
            allBranches = scope == null && initialSession.capabilities.canViewConsolidatedReports
        )
    }

    private val vouchersFlow = scopeTrigger.flatMapLatest { (activeBr, scope) ->
        smartLoyaltyRepository.observeVouchers(
            branchId = scope ?: activeBr,
            allBranches = scope == null && initialSession.capabilities.canViewConsolidatedReports
        )
    }

    private val stampsFlow = scopeTrigger.flatMapLatest { (activeBr, scope) ->
        smartLoyaltyRepository.observeStampTransactions(
            branchId = scope ?: activeBr,
            allBranches = scope == null && initialSession.capabilities.canViewConsolidatedReports
        )
    }

    private val redemptionsFlow = scopeTrigger.flatMapLatest { (activeBr, scope) ->
        smartLoyaltyRepository.observeRedemptions(
            branchId = scope ?: activeBr,
            allBranches = scope == null && initialSession.capabilities.canViewConsolidatedReports
        )
    }

    private val auditLogsFlow = scopeTrigger.flatMapLatest { (activeBr, scope) ->
        smartLoyaltyRepository.observeAuditLogs(
            branchId = scope ?: activeBr,
            allBranches = scope == null && initialSession.capabilities.canViewConsolidatedReports
        )
    }

    private val customersFlow = authRepository.activeBranchId.flatMapLatest { br ->
        customerLoyaltyRepository.observeCustomers(br)
    }

    private val categoriesFlow = authRepository.activeBranchId.flatMapLatest { br ->
        menuRepository.observeCategories(br)
    }

    val uiState: StateFlow<SmartLoyaltyUiState> = combine(
        settingsFlow,
        cardsFlow,
        vouchersFlow,
        stampsFlow,
        redemptionsFlow,
        auditLogsFlow,
        customersFlow,
        categoriesFlow,
        multiBranchRepository.observeBranches(),
        _selectedBranchScope,
        _statusFilter,
        _searchQuery,
        _selectedBundle,
        _scannedVoucher,
        _analyticsReport,
        _reportPresetDays,
        brandAssetManager.logoState,
        _feedback
    ) { args ->
        val settings = args[0] as LoyaltyProgramSettingsRecord
        @Suppress("UNCHECKED_CAST")
        val cards = args[1] as List<LoyaltyCardRecord>
        @Suppress("UNCHECKED_CAST")
        val vouchers = args[2] as List<LoyaltyRewardVoucherRecord>
        @Suppress("UNCHECKED_CAST")
        val stamps = args[3] as List<LoyaltyStampTransactionRecord>
        @Suppress("UNCHECKED_CAST")
        val redemptions = args[4] as List<LoyaltyRedemptionRecord>
        @Suppress("UNCHECKED_CAST")
        val logs = args[5] as List<LoyaltyAuditLogRecord>
        @Suppress("UNCHECKED_CAST")
        val customers = args[6] as List<CustomerProfileRecord>
        @Suppress("UNCHECKED_CAST")
        val categories = args[7] as List<ProductCategory>
        @Suppress("UNCHECKED_CAST")
        val branches = args[8] as List<BranchSummary>
        val branchScope = args[9] as String?
        val statusFilter = args[10] as LoyaltyCardStatus?
        val query = args[11] as String
        val bundle = args[12] as CustomerLoyaltyBundle?
        val scannedVoucher = args[13] as LoyaltyRewardVoucherRecord?
        val report = args[14] as LoyaltyAnalyticsReport?
        val reportPreset = args[15] as Int?
        val logo = args[16] as BrandLogoState
        @Suppress("UNCHECKED_CAST")
        val feedback = args[17] as Pair<String?, Boolean>

        val q = query.trim()
        val filtered = cards.filter { card ->
            val matchesStatus = statusFilter == null || card.effectiveStatus == statusFilter
            val matchesSearch = q.isBlank() ||
                card.customerName.contains(q, ignoreCase = true) ||
                card.customerPhone.contains(q, ignoreCase = true) ||
                card.cardNumber.contains(q, ignoreCase = true) ||
                card.qrCodeToken.contains(q, ignoreCase = true)
            matchesStatus && matchesSearch
        }

        SmartLoyaltyUiState(
            settings = settings,
            cards = cards,
            filteredCards = filtered,
            vouchers = vouchers,
            stampTransactions = stamps,
            redemptions = redemptions,
            auditLogs = logs,
            customers = customers,
            categories = categories,
            branches = branches,
            selectedBranchScope = branchScope,
            selectedStatusFilter = statusFilter,
            searchQuery = query,
            selectedCustomerBundle = bundle,
            scannedVoucher = scannedVoucher,
            analyticsReport = report,
            reportPresetDays = reportPreset,
            logoState = logo,
            feedbackMessage = feedback.first,
            isErrorFeedback = feedback.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SmartLoyaltyUiState()
    )

    init {
        refreshAnalyticsReport()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectStatusFilter(status: LoyaltyCardStatus?) {
        _statusFilter.value = status
    }

    fun selectBranchScope(branchIdOrNull: String?) {
        viewModelScope.launch {
            if (branchIdOrNull == null && !initialSession.capabilities.canViewConsolidatedReports) {
                _selectedBranchScope.value = authRepository.activeBranchId.first()
            } else {
                _selectedBranchScope.value = branchIdOrNull
            }
            refreshAnalyticsReport()
        }
    }

    fun selectReportPresetDays(days: Int?) {
        _reportPresetDays.value = days
        refreshAnalyticsReport()
    }

    fun refreshAnalyticsReport() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val days = _reportPresetDays.value
            val startMillis = days?.let { now - it.toLong() * 86_400_000L }
            val scopeFilter = _selectedBranchScope.value
            _analyticsReport.value = smartLoyaltyRepository.buildAnalyticsReport(
                branchIdFilter = scopeFilter,
                startMillis = startMillis,
                endMillis = now,
                nowMillis = now
            )
        }
    }

    fun enrollCustomerCard(
        session: UserSession,
        customerId: String,
        customCardNumber: String? = null
    ) {
        viewModelScope.launch {
            smartLoyaltyRepository.enrollCustomerCard(session, customerId, customCardNumber).fold(
                onSuccess = { card ->
                    showFeedback("Issued card ${card.cardNumber} for ${card.customerName}.", false)
                    openCustomerBundle(customerId)
                    refreshAnalyticsReport()
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Card enrollment failed.", true)
                }
            )
        }
    }

    fun openCustomerBundle(customerId: String) {
        viewModelScope.launch {
            _selectedBundle.value = smartLoyaltyRepository.getCustomerLoyaltyBundle(customerId)
        }
    }

    fun clearSelectedBundle() {
        _selectedBundle.value = null
    }

    fun lookupQrOrCode(query: String, onFoundCard: (LoyaltyCardRecord) -> Unit = {}) {
        val clean = query.trim()
        if (clean.isBlank()) {
            showFeedback("Scan or enter a QR token, card number, voucher code, or phone number.", true)
            return
        }
        viewModelScope.launch {
            val v = smartLoyaltyRepository.findVoucherByCodeOrQr(clean)
            _scannedVoucher.value = v
            val matches = smartLoyaltyRepository.lookupByQrOrQuery(clean, branchIdFilter = null)
            val card = matches.firstOrNull()
            if (card != null) {
                openCustomerBundle(card.customerId)
                onFoundCard(card)
                val suffix = v?.let { " • Voucher ${it.voucherCode} (${it.effectiveStatus.label})" } ?: ""
                showFeedback("Matched ${card.customerName} (${card.cardNumber})$suffix", false)
            } else {
                showFeedback("No loyalty card or voucher matched \"$clean\".", true)
            }
        }
    }

    fun clearScannedVoucher() {
        _scannedVoucher.value = null
    }

    fun suspendCard(session: UserSession, cardId: String, customerId: String, reason: String) {
        viewModelScope.launch {
            smartLoyaltyRepository.suspendCard(session, cardId, reason).fold(
                onSuccess = { card ->
                    showFeedback("Card ${card.cardNumber} suspended.", false)
                    openCustomerBundle(customerId)
                    refreshAnalyticsReport()
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Suspension failed.", true)
                }
            )
        }
    }

    fun reactivateCard(session: UserSession, cardId: String, customerId: String, reason: String) {
        viewModelScope.launch {
            smartLoyaltyRepository.reactivateCard(session, cardId, reason).fold(
                onSuccess = { card ->
                    showFeedback("Card ${card.cardNumber} reactivated (${card.effectiveStatus.label}).", false)
                    openCustomerBundle(customerId)
                    refreshAnalyticsReport()
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Reactivation failed.", true)
                }
            )
        }
    }

    fun replaceCard(
        session: UserSession,
        oldCardId: String,
        customerId: String,
        reason: String,
        newCustomCardNumber: String?
    ) {
        viewModelScope.launch {
            smartLoyaltyRepository.replaceCard(session, oldCardId, reason, newCustomCardNumber).fold(
                onSuccess = { newCard ->
                    showFeedback("Issued replacement card ${newCard.cardNumber} with stamps & expiry preserved.", false)
                    openCustomerBundle(customerId)
                    refreshAnalyticsReport()
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Card replacement failed.", true)
                }
            )
        }
    }

    fun renewExpiredCard(session: UserSession, cardId: String, customerId: String, reason: String) {
        viewModelScope.launch {
            smartLoyaltyRepository.renewExpiredCard(session, cardId, reason).fold(
                onSuccess = { card ->
                    showFeedback("Card ${card.cardNumber} renewed for Cycle #${card.cycleCount}.", false)
                    openCustomerBundle(customerId)
                    refreshAnalyticsReport()
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Card renewal failed.", true)
                }
            )
        }
    }

    fun adjustStamps(
        session: UserSession,
        cardId: String,
        customerId: String,
        delta: Int,
        reason: String
    ) {
        viewModelScope.launch {
            smartLoyaltyRepository.adjustStampsManually(session, cardId, delta, reason).fold(
                onSuccess = { card ->
                    showFeedback("Adjusted stamps on ${card.cardNumber} -> ${card.currentCycleStamps}/${card.requiredStamps}.", false)
                    openCustomerBundle(customerId)
                    refreshAnalyticsReport()
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Stamp adjustment rejected.", true)
                }
            )
        }
    }

    fun saveProgramSettings(session: UserSession, updated: LoyaltyProgramSettingsRecord) {
        viewModelScope.launch {
            smartLoyaltyRepository.updateProgramSettings(session, updated).fold(
                onSuccess = {
                    showFeedback("Loyalty program rules saved & audited.", false)
                    refreshAnalyticsReport()
                },
                onFailure = { err ->
                    showFeedback(err.message ?: "Failed to update settings.", true)
                }
            )
        }
    }

    fun showFeedback(message: String, isError: Boolean = false) {
        _feedback.value = message to isError
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val smartLoyaltyRepository: SmartLoyaltyRepository,
        private val customerLoyaltyRepository: CustomerLoyaltyRepository,
        private val menuRepository: MenuRepository,
        private val multiBranchRepository: MultiBranchRepository,
        private val brandAssetManager: BrandAssetManager,
        private val session: UserSession
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SmartLoyaltyViewModel(
                authRepository = authRepository,
                smartLoyaltyRepository = smartLoyaltyRepository,
                customerLoyaltyRepository = customerLoyaltyRepository,
                menuRepository = menuRepository,
                multiBranchRepository = multiBranchRepository,
                brandAssetManager = brandAssetManager,
                initialSession = session
            ) as T
        }
    }
}
