package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import com.example.data.repository.CustomerLoyaltyRepository
import com.example.data.repository.ExpenseRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.PosRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.SupplierRepository
import com.example.domain.model.CustomerFeedbackRecord
import com.example.domain.model.CustomerProfileRecord
import com.example.domain.model.ExpenseApprovalStatus
import com.example.domain.model.ExpensePeriodReport
import com.example.domain.model.ExpenseRecord
import com.example.domain.model.ExpenseRecurrence
import com.example.domain.model.InventoryItemRecord
import com.example.domain.model.LoyaltyConfigRecord
import com.example.domain.model.LoyaltyRewardRecord
import com.example.domain.model.LoyaltyTransactionRecord
import com.example.domain.model.PosOrderRecord
import com.example.domain.model.PurchaseDraftLine
import com.example.domain.model.PurchaseOrderRecord
import com.example.domain.model.PurchaseReceivingRecord
import com.example.domain.model.PurchaseReturnRecord
import com.example.domain.model.PurchaseStatus
import com.example.domain.model.SupplierPaymentRecord
import com.example.domain.model.SupplierPerformanceMetric
import com.example.domain.model.SupplierStatementReport
import com.example.domain.model.SupplierSummary
import com.example.domain.model.UserSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

// ---------------- 1. PURCHASE MANAGEMENT VIEWMODEL ----------------

data class PurchaseUiState(
    val orders: List<PurchaseOrderRecord> = emptyList(),
    val filteredOrders: List<PurchaseOrderRecord> = emptyList(),
    val suppliers: List<SupplierSummary> = emptyList(),
    val inventoryItems: List<InventoryItemRecord> = emptyList(),
    val receivings: List<PurchaseReceivingRecord> = emptyList(),
    val returns: List<PurchaseReturnRecord> = emptyList(),
    val selectedStatusFilter: PurchaseStatus? = null,
    val searchQuery: String = "",
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseViewModel(
    private val authRepository: AuthRepository,
    private val purchaseRepository: PurchaseRepository,
    private val supplierRepository: SupplierRepository,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _statusFilter = MutableStateFlow<PurchaseStatus?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val ordersFlow = authRepository.activeBranchId.flatMapLatest { purchaseRepository.observePurchaseOrders(it) }
    private val suppliersFlow = authRepository.activeBranchId.flatMapLatest { supplierRepository.observeSuppliers(it) }
    private val inventoryFlow = authRepository.activeBranchId.flatMapLatest { inventoryRepository.observeInventoryItems(it) }
    private val receivingsFlow = authRepository.activeBranchId.flatMapLatest { purchaseRepository.observeReceivings(it) }
    private val returnsFlow = authRepository.activeBranchId.flatMapLatest { purchaseRepository.observeReturns(it) }

    val uiState: StateFlow<PurchaseUiState> = combine(
        ordersFlow,
        suppliersFlow,
        inventoryFlow,
        receivingsFlow,
        returnsFlow,
        _statusFilter,
        _searchQuery,
        _feedback
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val orders = args[0] as List<PurchaseOrderRecord>
        @Suppress("UNCHECKED_CAST")
        val suppliers = args[1] as List<SupplierSummary>
        @Suppress("UNCHECKED_CAST")
        val inventory = args[2] as List<InventoryItemRecord>
        @Suppress("UNCHECKED_CAST")
        val receivings = args[3] as List<PurchaseReceivingRecord>
        @Suppress("UNCHECKED_CAST")
        val returns = args[4] as List<PurchaseReturnRecord>
        val statusFilter = args[5] as PurchaseStatus?
        val query = args[6] as String
        @Suppress("UNCHECKED_CAST")
        val feedback = args[7] as Pair<String?, Boolean>

        val filtered = orders.filter { po ->
            val matchStatus = statusFilter == null || po.status == statusFilter
            val matchQuery = query.isBlank() ||
                po.poNumber.contains(query, ignoreCase = true) ||
                po.supplierName.contains(query, ignoreCase = true) ||
                po.supplierInvoiceNumber.contains(query, ignoreCase = true)
            matchStatus && matchQuery
        }

        PurchaseUiState(
            orders = orders,
            filteredOrders = filtered,
            suppliers = suppliers.filter { it.isActive },
            inventoryItems = inventory,
            receivings = receivings,
            returns = returns,
            selectedStatusFilter = statusFilter,
            searchQuery = query,
            feedbackMessage = feedback.first,
            isErrorFeedback = feedback.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PurchaseUiState())

    fun selectStatusFilter(status: PurchaseStatus?) {
        _statusFilter.value = status
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun savePurchaseOrder(
        session: UserSession,
        purchaseId: String?,
        supplierId: String,
        supplierInvoiceNumber: String,
        lines: List<PurchaseDraftLine>,
        discountCents: Long,
        taxCents: Long,
        notes: String,
        submitAsPending: Boolean
    ) {
        viewModelScope.launch {
            purchaseRepository.createOrUpdatePurchaseOrder(
                session = session,
                purchaseId = purchaseId,
                idempotencyKey = UUID.randomUUID().toString(),
                supplierId = supplierId,
                supplierInvoiceNumber = supplierInvoiceNumber,
                draftLines = lines,
                discountCents = discountCents,
                taxCents = taxCents,
                notes = notes,
                submitAsPending = submitAsPending
            ).fold(
                onSuccess = { po -> _feedback.value = "${po.poNumber} saved (${po.status.label})." to false },
                onFailure = { err -> _feedback.value = (err.message ?: "Failed to save PO.") to true }
            )
        }
    }

    fun approvePurchaseOrder(session: UserSession, purchaseId: String) {
        viewModelScope.launch {
            purchaseRepository.approvePurchaseOrder(session, purchaseId).fold(
                onSuccess = { _feedback.value = "Purchase order approved." to false },
                onFailure = { _feedback.value = (it.message ?: "Approval denied.") to true }
            )
        }
    }

    fun cancelPurchaseOrder(session: UserSession, purchaseId: String, reason: String) {
        viewModelScope.launch {
            purchaseRepository.cancelPurchaseOrder(session, purchaseId, reason).fold(
                onSuccess = { _feedback.value = "Purchase order cancelled." to false },
                onFailure = { _feedback.value = (it.message ?: "Cancellation denied.") to true }
            )
        }
    }

    fun receiveGoods(
        session: UserSession,
        purchaseId: String,
        referenceNumber: String,
        lineQuantities: Map<String, Double>,
        notes: String,
        supplierInvoiceNumber: String
    ) {
        viewModelScope.launch {
            purchaseRepository.receiveGoods(
                session = session,
                purchaseId = purchaseId,
                receivingKey = UUID.randomUUID().toString(),
                referenceNumber = referenceNumber,
                receivedDateMillis = System.currentTimeMillis(),
                lineQuantities = lineQuantities,
                notes = notes,
                supplierInvoiceNumber = supplierInvoiceNumber
            ).fold(
                onSuccess = { po ->
                    _feedback.value = "Goods received for ${po.poNumber} (${po.status.label}). Inventory updated." to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Failed to receive goods.") to true
                }
            )
        }
    }

    fun returnPurchaseGoods(
        session: UserSession,
        purchaseId: String,
        lineId: String,
        returnQty: Double,
        reason: String
    ) {
        viewModelScope.launch {
            purchaseRepository.returnPurchaseGoods(session, purchaseId, lineId, returnQty, reason).fold(
                onSuccess = { ret ->
                    _feedback.value = "Returned ${ret.returnQty} ${ret.unit} ${ret.itemName} to supplier." to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Purchase return failed.") to true
                }
            )
        }
    }

    fun showFeedback(msg: String, isError: Boolean = false) {
        _feedback.value = msg to isError
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val purchaseRepository: PurchaseRepository,
        private val supplierRepository: SupplierRepository,
        private val inventoryRepository: InventoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PurchaseViewModel(authRepository, purchaseRepository, supplierRepository, inventoryRepository) as T
    }
}

// ---------------- 2. SUPPLIER MANAGEMENT VIEWMODEL ----------------

data class SupplierUiState(
    val suppliers: List<SupplierSummary> = emptyList(),
    val filteredSuppliers: List<SupplierSummary> = emptyList(),
    val payments: List<SupplierPaymentRecord> = emptyList(),
    val purchaseOrders: List<PurchaseOrderRecord> = emptyList(),
    val returns: List<PurchaseReturnRecord> = emptyList(),
    val performanceMetrics: List<SupplierPerformanceMetric> = emptyList(),
    val searchQuery: String = "",
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class SupplierViewModel(
    private val authRepository: AuthRepository,
    private val supplierRepository: SupplierRepository,
    private val purchaseRepository: PurchaseRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val suppliersFlow = authRepository.activeBranchId.flatMapLatest { supplierRepository.observeSuppliers(it) }
    private val paymentsFlow = authRepository.activeBranchId.flatMapLatest { supplierRepository.observePayments(it) }
    private val ordersFlow = authRepository.activeBranchId.flatMapLatest { purchaseRepository.observePurchaseOrders(it) }
    private val returnsFlow = authRepository.activeBranchId.flatMapLatest { purchaseRepository.observeReturns(it) }

    val uiState: StateFlow<SupplierUiState> = combine(
        suppliersFlow,
        paymentsFlow,
        ordersFlow,
        returnsFlow,
        _searchQuery,
        _feedback
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val suppliers = args[0] as List<SupplierSummary>
        @Suppress("UNCHECKED_CAST")
        val payments = args[1] as List<SupplierPaymentRecord>
        @Suppress("UNCHECKED_CAST")
        val orders = args[2] as List<PurchaseOrderRecord>
        @Suppress("UNCHECKED_CAST")
        val returns = args[3] as List<PurchaseReturnRecord>
        val query = args[4] as String
        @Suppress("UNCHECKED_CAST")
        val feedback = args[5] as Pair<String?, Boolean>

        val filtered = suppliers.filter { s ->
            query.isBlank() ||
                s.name.contains(query, ignoreCase = true) ||
                s.contactPerson.contains(query, ignoreCase = true) ||
                s.category.contains(query, ignoreCase = true)
        }
        val metrics = supplierRepository.buildPerformanceMetrics(suppliers, orders, returns)

        SupplierUiState(
            suppliers = suppliers,
            filteredSuppliers = filtered,
            payments = payments,
            purchaseOrders = orders,
            returns = returns,
            performanceMetrics = metrics,
            searchQuery = query,
            feedbackMessage = feedback.first,
            isErrorFeedback = feedback.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupplierUiState())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun saveSupplier(
        session: UserSession,
        supplierId: String?,
        name: String,
        contactPerson: String,
        phone: String,
        email: String,
        address: String,
        category: String,
        openingBalanceCents: Long
    ) {
        viewModelScope.launch {
            supplierRepository.saveSupplier(
                session = session,
                supplierId = supplierId,
                name = name,
                contactPerson = contactPerson,
                phone = phone,
                email = email,
                address = address,
                category = category,
                openingBalanceCents = openingBalanceCents
            ).fold(
                onSuccess = { s -> _feedback.value = "Supplier ${s.name} saved." to false },
                onFailure = { e -> _feedback.value = (e.message ?: "Failed to save supplier.") to true }
            )
        }
    }

    fun recordPayment(
        session: UserSession,
        supplierId: String,
        purchaseId: String?,
        amountCents: Long,
        paymentMethod: String,
        referenceNumber: String,
        notes: String
    ) {
        viewModelScope.launch {
            supplierRepository.recordSupplierPayment(
                session = session,
                idempotencyKey = UUID.randomUUID().toString(),
                supplierId = supplierId,
                purchaseId = purchaseId,
                amountCents = amountCents,
                paymentMethod = paymentMethod,
                referenceNumber = referenceNumber,
                paymentDateMillis = System.currentTimeMillis(),
                notes = notes
            ).fold(
                onSuccess = { p -> _feedback.value = "Payment recorded (Ref: ${p.referenceNumber})." to false },
                onFailure = { e -> _feedback.value = (e.message ?: "Payment failed.") to true }
            )
        }
    }

    fun buildStatement(supplier: SupplierSummary): SupplierStatementReport {
        val current = uiState.value
        return supplierRepository.buildSupplierStatement(
            supplier = supplier,
            orders = current.purchaseOrders,
            returns = current.returns,
            payments = current.payments
        )
    }

    fun showFeedback(msg: String, isError: Boolean = false) {
        _feedback.value = msg to isError
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val supplierRepository: SupplierRepository,
        private val purchaseRepository: PurchaseRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SupplierViewModel(authRepository, supplierRepository, purchaseRepository) as T
    }
}

// ---------------- 3. CUSTOMER CRM & LOYALTY VIEWMODEL ----------------

data class CustomerLoyaltyUiState(
    val customers: List<CustomerProfileRecord> = emptyList(),
    val filteredCustomers: List<CustomerProfileRecord> = emptyList(),
    val orders: List<PosOrderRecord> = emptyList(),
    val config: LoyaltyConfigRecord? = null,
    val rewards: List<LoyaltyRewardRecord> = emptyList(),
    val transactions: List<LoyaltyTransactionRecord> = emptyList(),
    val feedbackList: List<CustomerFeedbackRecord> = emptyList(),
    val searchQuery: String = "",
    val selectedTierFilter: String? = null,
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerLoyaltyViewModel(
    private val authRepository: AuthRepository,
    private val customerLoyaltyRepository: CustomerLoyaltyRepository,
    private val posRepository: PosRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _tierFilter = MutableStateFlow<String?>(null)
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val customersFlow = authRepository.activeBranchId.flatMapLatest { customerLoyaltyRepository.observeCustomers(it) }
    private val ordersFlow = authRepository.activeBranchId.flatMapLatest { posRepository.observeOrders(it) }
    private val configFlow = authRepository.activeBranchId.flatMapLatest { customerLoyaltyRepository.observeLoyaltyConfig(it) }
    private val rewardsFlow = authRepository.activeBranchId.flatMapLatest { customerLoyaltyRepository.observeRewards(it) }
    private val txsFlow = authRepository.activeBranchId.flatMapLatest { customerLoyaltyRepository.observeLoyaltyTransactions(it) }
    private val custFeedbackFlow = authRepository.activeBranchId.flatMapLatest { customerLoyaltyRepository.observeFeedback(it) }

    val uiState: StateFlow<CustomerLoyaltyUiState> = combine(
        customersFlow,
        ordersFlow,
        configFlow,
        rewardsFlow,
        txsFlow,
        custFeedbackFlow,
        _searchQuery,
        _tierFilter,
        _feedback
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val customers = args[0] as List<CustomerProfileRecord>
        @Suppress("UNCHECKED_CAST")
        val orders = args[1] as List<PosOrderRecord>
        val config = args[2] as LoyaltyConfigRecord
        @Suppress("UNCHECKED_CAST")
        val rewards = args[3] as List<LoyaltyRewardRecord>
        @Suppress("UNCHECKED_CAST")
        val txs = args[4] as List<LoyaltyTransactionRecord>
        @Suppress("UNCHECKED_CAST")
        val feedbackList = args[5] as List<CustomerFeedbackRecord>
        val query = args[6] as String
        val tier = args[7] as String?
        @Suppress("UNCHECKED_CAST")
        val feedback = args[8] as Pair<String?, Boolean>

        val filtered = customers.filter { c ->
            val matchTier = tier == null || c.tier.equals(tier, ignoreCase = true)
            val matchQuery = query.isBlank() ||
                c.name.contains(query, ignoreCase = true) ||
                c.phone.contains(query, ignoreCase = true) ||
                c.email.contains(query, ignoreCase = true)
            matchTier && matchQuery
        }

        CustomerLoyaltyUiState(
            customers = customers,
            filteredCustomers = filtered,
            orders = orders,
            config = config,
            rewards = rewards,
            transactions = txs,
            feedbackList = feedbackList,
            searchQuery = query,
            selectedTierFilter = tier,
            feedbackMessage = feedback.first,
            isErrorFeedback = feedback.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomerLoyaltyUiState())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectTierFilter(tier: String?) {
        _tierFilter.value = tier
    }

    fun saveCustomer(
        session: UserSession,
        customerId: String?,
        name: String,
        phone: String,
        email: String,
        address: String,
        notes: String,
        accountBalanceCents: Long?
    ) {
        viewModelScope.launch {
            customerLoyaltyRepository.saveCustomer(
                session = session,
                customerId = customerId,
                name = name,
                phone = phone,
                email = email,
                address = address,
                notes = notes,
                accountBalanceCents = accountBalanceCents
            ).fold(
                onSuccess = { c -> _feedback.value = "Customer ${c.name} saved." to false },
                onFailure = { e -> _feedback.value = (e.message ?: "Failed to save customer.") to true }
            )
        }
    }

    fun updateLoyaltyConfig(
        session: UserSession,
        earnRate: Double,
        redeemCentsPerPoint: Long,
        minRedeemPoints: Int,
        silverThreshold: Int,
        goldThreshold: Int,
        platinumThreshold: Int,
        expiryEnabled: Boolean,
        expiryDays: Int
    ) {
        viewModelScope.launch {
            customerLoyaltyRepository.updateLoyaltyConfig(
                session, earnRate, redeemCentsPerPoint, minRedeemPoints,
                silverThreshold, goldThreshold, platinumThreshold, expiryEnabled, expiryDays
            ).fold(
                onSuccess = { _feedback.value = "Loyalty program rules updated." to false },
                onFailure = { _feedback.value = (it.message ?: "Failed to update rules.") to true }
            )
        }
    }

    fun saveReward(
        session: UserSession,
        rewardId: String?,
        title: String,
        description: String,
        pointsRequired: Int,
        discountCentsValue: Long
    ) {
        viewModelScope.launch {
            customerLoyaltyRepository.saveReward(session, rewardId, title, description, pointsRequired, discountCentsValue, true).fold(
                onSuccess = { _feedback.value = "Loyalty reward saved." to false },
                onFailure = { _feedback.value = (it.message ?: "Failed to save reward.") to true }
            )
        }
    }

    fun adjustPoints(session: UserSession, customerId: String, pointsDelta: Int, reason: String) {
        viewModelScope.launch {
            customerLoyaltyRepository.adjustPointsWithApproval(
                session = session,
                customerId = customerId,
                pointsDelta = pointsDelta,
                reason = reason,
                idempotencyKey = UUID.randomUUID().toString()
            ).fold(
                onSuccess = { c -> _feedback.value = "${c.name} points updated (${c.loyaltyPoints} pts)." to false },
                onFailure = { _feedback.value = (it.message ?: "Adjustment denied.") to true }
            )
        }
    }

    fun redeemReward(session: UserSession, customerId: String, reward: LoyaltyRewardRecord) {
        viewModelScope.launch {
            customerLoyaltyRepository.redeemReward(
                session = session,
                customerId = customerId,
                reward = reward,
                idempotencyKey = UUID.randomUUID().toString()
            ).fold(
                onSuccess = { c -> _feedback.value = "Redeemed '${reward.title}' for ${c.name}." to false },
                onFailure = { _feedback.value = (it.message ?: "Redemption failed.") to true }
            )
        }
    }

    fun recordFeedback(session: UserSession, customerId: String, rating: Int, comment: String) {
        viewModelScope.launch {
            customerLoyaltyRepository.recordFeedback(session, customerId, null, rating, comment).fold(
                onSuccess = { _feedback.value = "Customer feedback recorded." to false },
                onFailure = { _feedback.value = (it.message ?: "Failed to save feedback.") to true }
            )
        }
    }

    fun runExpirySweep(session: UserSession) {
        viewModelScope.launch {
            customerLoyaltyRepository.expireOldPointsIfEnabled(session).fold(
                onSuccess = { count -> _feedback.value = "Expiry sweep complete: $count customer balances expired." to false },
                onFailure = { _feedback.value = (it.message ?: "Expiry sweep denied.") to true }
            )
        }
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val customerLoyaltyRepository: CustomerLoyaltyRepository,
        private val posRepository: PosRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CustomerLoyaltyViewModel(authRepository, customerLoyaltyRepository, posRepository) as T
    }
}

// ---------------- 4. EXPENSE MANAGEMENT VIEWMODEL ----------------

data class ExpenseUiState(
    val expenses: List<ExpenseRecord> = emptyList(),
    val filteredExpenses: List<ExpenseRecord> = emptyList(),
    val periodReports: List<ExpensePeriodReport> = emptyList(),
    val selectedCategory: String? = null,
    val selectedStatus: ExpenseApprovalStatus? = null,
    val searchQuery: String = "",
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModel(
    private val authRepository: AuthRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _selectedStatus = MutableStateFlow<ExpenseApprovalStatus?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val expensesFlow = authRepository.activeBranchId.flatMapLatest { expenseRepository.observeExpenses(it) }

    val uiState: StateFlow<ExpenseUiState> = combine(
        expensesFlow,
        _selectedCategory,
        _selectedStatus,
        _searchQuery,
        _feedback
    ) { expenses, category, status, query, feedback ->
        val filtered = expenses.filter { exp ->
            val matchCat = category == null || exp.category.equals(category, ignoreCase = true)
            val matchStatus = status == null || exp.approvalStatus == status
            val matchQuery = query.isBlank() ||
                exp.description.contains(query, ignoreCase = true) ||
                exp.category.contains(query, ignoreCase = true)
            matchCat && matchStatus && matchQuery
        }
        ExpenseUiState(
            expenses = expenses,
            filteredExpenses = filtered,
            periodReports = expenseRepository.buildPeriodReports(expenses),
            selectedCategory = category,
            selectedStatus = status,
            searchQuery = query,
            feedbackMessage = feedback.first,
            isErrorFeedback = feedback.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseUiState())

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun selectStatus(status: ExpenseApprovalStatus?) {
        _selectedStatus.value = status
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    suspend fun copyReceiptUri(uri: Uri): Result<String> = expenseRepository.copyReceiptToInternalStorage(uri)

    fun saveExpense(
        session: UserSession,
        expenseId: String?,
        category: String,
        amountCents: Long,
        expenseDateMillis: Long,
        description: String,
        paymentMethod: String,
        receiptUri: String?,
        isRecurring: Boolean,
        recurrence: ExpenseRecurrence
    ) {
        viewModelScope.launch {
            expenseRepository.saveExpense(
                session = session,
                idempotencyKey = UUID.randomUUID().toString(),
                expenseId = expenseId,
                category = category,
                amountCents = amountCents,
                expenseDateMillis = expenseDateMillis,
                description = description,
                paymentMethod = paymentMethod,
                receiptUri = receiptUri,
                isRecurring = isRecurring,
                recurrenceFrequency = recurrence
            ).fold(
                onSuccess = { exp -> _feedback.value = "Expense '${exp.category}' saved (${exp.approvalStatus.label})." to false },
                onFailure = { _feedback.value = (it.message ?: "Failed to save expense.") to true }
            )
        }
    }

    fun setApproval(session: UserSession, expenseId: String, approved: Boolean) {
        viewModelScope.launch {
            expenseRepository.setExpenseApproval(session, expenseId, approved).fold(
                onSuccess = { _feedback.value = (if (approved) "Expense approved." else "Expense rejected.") to false },
                onFailure = { _feedback.value = (it.message ?: "Approval denied.") to true }
            )
        }
    }

    fun deleteExpense(session: UserSession, expenseId: String) {
        viewModelScope.launch {
            expenseRepository.deleteExpense(session, expenseId).fold(
                onSuccess = { _feedback.value = "Expense deleted." to false },
                onFailure = { _feedback.value = (it.message ?: "Delete denied.") to true }
            )
        }
    }

    fun showFeedback(msg: String, isError: Boolean = false) {
        _feedback.value = msg to isError
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val expenseRepository: ExpenseRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExpenseViewModel(authRepository, expenseRepository) as T
    }
}
