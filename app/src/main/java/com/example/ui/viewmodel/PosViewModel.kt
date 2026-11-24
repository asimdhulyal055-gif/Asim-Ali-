package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.brand.BrandAssetManager
import com.example.data.brand.BrandLogoState
import com.example.data.repository.AuthRepository
import com.example.data.repository.PosRepository
import com.example.data.repository.SmartLoyaltyRepository
import com.example.domain.model.BillingCalculator
import com.example.domain.model.BillingTotals
import com.example.domain.model.CartItem
import com.example.domain.model.CustomerSummary
import com.example.domain.model.DailyCashClosingSummary
import com.example.domain.model.LoyaltyCardRecord
import com.example.domain.model.LoyaltyCardStatus
import com.example.domain.model.LoyaltyRewardVoucherRecord
import com.example.domain.model.LoyaltyRuleConstants
import com.example.domain.model.OrderPriority
import com.example.domain.model.OrderType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PosLoyaltyPreview
import com.example.domain.model.PosOrderRecord
import com.example.domain.model.PosProduct
import com.example.domain.model.ProductAddon
import com.example.domain.model.ProductCategory
import com.example.domain.model.ProductVariation
import com.example.domain.model.SplitPaymentBreakdown
import com.example.domain.model.UserSession
import com.example.printing.ThermalPrinterService
import com.example.printing.ThermalPrinterStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToLong

data class PosCartState(
    val items: List<CartItem> = emptyList(),
    val orderType: OrderType = OrderType.DINE_IN,
    val tableNumber: String = "T-01",
    val priority: OrderPriority = OrderPriority.NORMAL,
    val selectedCustomer: CustomerSummary? = null,
    val discountPercent: Double = 0.0,
    val manualDiscountCents: Long = 0L,
    val loyaltyPointsToRedeem: Int = 0,
    val loyaltyDiscountCents: Long = 0L,
    val selectedLoyaltyCard: LoyaltyCardRecord? = null,
    val appliedLoyaltyVoucher: LoyaltyRewardVoucherRecord? = null,
    val loyaltyPreview: PosLoyaltyPreview? = null,
    val serviceChargePercent: Double = BillingCalculator.DEFAULT_DINE_IN_SERVICE_PERCENT,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val splitBreakdown: SplitPaymentBreakdown = SplitPaymentBreakdown(),
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val isSubmitting: Boolean = false,
    val lastCompletedOrder: PosOrderRecord? = null
) {
    val preVoucherTotals: BillingTotals
        get() = BillingCalculator.calculate(
            items = items,
            discountPercent = discountPercent,
            manualDiscountCents = manualDiscountCents + loyaltyDiscountCents,
            taxRatePercent = BillingCalculator.DEFAULT_TAX_RATE_PERCENT,
            serviceChargeRatePercent = if (orderType == OrderType.DINE_IN) serviceChargePercent else 0.0
        )

    val estimatedVoucherDiscountCents: Long
        get() {
            val voucher = appliedLoyaltyVoucher ?: return 0L
            val pre = preVoucherTotals
            val netBeforeVoucher = (pre.subtotalCents - pre.discountCents).coerceAtLeast(0L)
            return minOf(
                voucher.rewardValueCents,
                LoyaltyRuleConstants.MAX_REWARD_CAP_CENTS,
                netBeforeVoucher
            ).coerceAtLeast(0L)
        }

    val totals: BillingTotals
        get() {
            val pre = preVoucherTotals
            val voucherDisc = estimatedVoucherDiscountCents
            return if (voucherDisc > 0L) {
                BillingCalculator.calculate(
                    items = items,
                    discountPercent = 0.0,
                    manualDiscountCents = pre.discountCents + voucherDisc,
                    taxRatePercent = pre.taxRatePercent,
                    serviceChargeRatePercent = pre.serviceChargeRatePercent
                )
            } else {
                pre
            }
        }

    val itemCount: Int
        get() = items.sumOf { it.quantity }
}

data class PosUiState(
    val categories: List<ProductCategory> = emptyList(),
    val products: List<PosProduct> = emptyList(),
    val filteredProducts: List<PosProduct> = emptyList(),
    val customers: List<CustomerSummary> = emptyList(),
    val orders: List<PosOrderRecord> = emptyList(),
    val cashClosings: List<DailyCashClosingSummary> = emptyList(),
    val loyaltyCards: List<LoyaltyCardRecord> = emptyList(),
    val selectedCategoryId: String? = null,
    val searchQuery: String = "",
    val cart: PosCartState = PosCartState(),
    val selectedOrderForInvoiceId: String? = null,
    val selectedOrderForKotId: String? = null,
    val logoState: BrandLogoState = BrandLogoState(hasOwnerLogo = false),
    val printerStatus: ThermalPrinterStatus,
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class PosViewModel(
    private val authRepository: AuthRepository,
    private val posRepository: PosRepository,
    private val brandAssetManager: BrandAssetManager,
    private val thermalPrinterService: ThermalPrinterService,
    private val smartLoyaltyRepository: SmartLoyaltyRepository? = null
) : ViewModel() {

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _cartState = MutableStateFlow(PosCartState())
    private val _selectedInvoiceOrderId = MutableStateFlow<String?>(null)
    private val _selectedKotOrderId = MutableStateFlow<String?>(null)
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val categoriesFlow = authRepository.activeBranchId.flatMapLatest {
        posRepository.observeCategories(it)
    }
    private val productsFlow = authRepository.activeBranchId.flatMapLatest {
        posRepository.observeProducts(it)
    }
    private val customersFlow = authRepository.activeBranchId.flatMapLatest {
        posRepository.observeCustomers(it)
    }
    private val ordersFlow = authRepository.activeBranchId.flatMapLatest {
        posRepository.observeOrders(it)
    }
    private val closingsFlow = authRepository.activeBranchId.flatMapLatest {
        posRepository.observeCashClosings(it)
    }
    private val loyaltyCardsFlow = authRepository.activeBranchId.flatMapLatest { brId ->
        smartLoyaltyRepository?.observeCards(brId, allBranches = false) ?: flowOf(emptyList())
    }

    val uiState: StateFlow<PosUiState> = combine(
        categoriesFlow,
        productsFlow,
        customersFlow,
        ordersFlow,
        closingsFlow,
        loyaltyCardsFlow,
        _selectedCategoryId,
        _searchQuery,
        _cartState,
        _selectedInvoiceOrderId,
        _selectedKotOrderId,
        brandAssetManager.logoState,
        _feedback
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val categories = args[0] as List<ProductCategory>
        @Suppress("UNCHECKED_CAST")
        val products = args[1] as List<PosProduct>
        @Suppress("UNCHECKED_CAST")
        val customers = args[2] as List<CustomerSummary>
        @Suppress("UNCHECKED_CAST")
        val orders = args[3] as List<PosOrderRecord>
        @Suppress("UNCHECKED_CAST")
        val closings = args[4] as List<DailyCashClosingSummary>
        @Suppress("UNCHECKED_CAST")
        val loyaltyCards = args[5] as List<LoyaltyCardRecord>
        val selectedCat = args[6] as String?
        val query = args[7] as String
        val cart = args[8] as PosCartState
        val invoiceOrderId = args[9] as String?
        val kotOrderId = args[10] as String?
        val logoState = args[11] as BrandLogoState
        @Suppress("UNCHECKED_CAST")
        val feedback = args[12] as Pair<String?, Boolean>

        val filtered = products.filter { product ->
            val matchesCat = selectedCat == null || product.categoryId == selectedCat
            val matchesQuery = query.isBlank() ||
                product.name.contains(query, ignoreCase = true) ||
                product.sku.contains(query, ignoreCase = true) ||
                product.categoryTag.contains(query, ignoreCase = true)
            matchesCat && matchesQuery
        }

        PosUiState(
            categories = categories,
            products = products,
            filteredProducts = filtered,
            customers = customers,
            orders = orders,
            cashClosings = closings,
            loyaltyCards = loyaltyCards,
            selectedCategoryId = selectedCat,
            searchQuery = query,
            cart = cart,
            selectedOrderForInvoiceId = invoiceOrderId ?: orders.firstOrNull()?.orderId,
            selectedOrderForKotId = kotOrderId ?: orders.firstOrNull()?.orderId,
            logoState = logoState,
            printerStatus = thermalPrinterService.getStatus(),
            feedbackMessage = feedback.first,
            isErrorFeedback = feedback.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PosUiState(printerStatus = thermalPrinterService.getStatus())
    )

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun addToCart(
        product: PosProduct,
        variation: ProductVariation?,
        addons: List<ProductAddon>,
        specialInstructions: String,
        quantity: Int = 1
    ) {
        if (!product.isAvailable) {
            setFeedback("${product.name} is currently unavailable.", true)
            return
        }
        val cleanNotes = specialInstructions.trim()
        val sortedAddonIds = addons.map { it.addonId }.sorted().joinToString(",")
        val signature = "${product.productId}|${variation?.variationId.orEmpty()}|$sortedAddonIds|$cleanNotes"

        _cartState.update { state ->
            val existingIndex = state.items.indexOfFirst { line ->
                val lineSig = "${line.product.productId}|${line.selectedVariation?.variationId.orEmpty()}|" +
                    "${line.selectedAddons.map { it.addonId }.sorted().joinToString(",")}|${line.specialInstructions}"
                lineSig == signature
            }
            val updatedItems = if (existingIndex >= 0) {
                state.items.mapIndexed { idx, line ->
                    if (idx == existingIndex) line.copy(quantity = line.quantity + quantity) else line
                }
            } else {
                state.items + CartItem(
                    cartLineId = UUID.randomUUID().toString(),
                    product = product,
                    selectedVariation = variation,
                    selectedAddons = addons,
                    specialInstructions = cleanNotes,
                    quantity = quantity.coerceAtLeast(1)
                )
            }
            state.copy(items = updatedItems, lastCompletedOrder = null)
        }
        refreshLoyaltyPreview()
    }

    fun incrementCartItem(cartLineId: String) {
        _cartState.update { state ->
            state.copy(
                items = state.items.map {
                    if (it.cartLineId == cartLineId) it.copy(quantity = it.quantity + 1) else it
                }
            )
        }
        refreshLoyaltyPreview()
    }

    fun decrementCartItem(cartLineId: String) {
        _cartState.update { state ->
            val updated = state.items.mapNotNull { item ->
                if (item.cartLineId != cartLineId) item
                else if (item.quantity <= 1) null
                else item.copy(quantity = item.quantity - 1)
            }
            state.copy(items = updated)
        }
        refreshLoyaltyPreview()
    }

    fun removeCartItem(cartLineId: String) {
        _cartState.update { state ->
            state.copy(items = state.items.filterNot { it.cartLineId == cartLineId })
        }
        refreshLoyaltyPreview()
    }

    fun clearCart() {
        _cartState.value = PosCartState()
    }

    fun setOrderType(orderType: OrderType) {
        _cartState.update {
            it.copy(
                orderType = orderType,
                serviceChargePercent = if (orderType == OrderType.DINE_IN) BillingCalculator.DEFAULT_DINE_IN_SERVICE_PERCENT else 0.0
            )
        }
        refreshLoyaltyPreview()
    }

    fun setTableNumber(table: String) {
        _cartState.update { it.copy(tableNumber = table) }
    }

    fun setPriority(priority: OrderPriority) {
        _cartState.update { it.copy(priority = priority) }
    }

    fun selectCustomer(customer: CustomerSummary?) {
        val matchingCard = if (customer != null) {
            uiState.value.loyaltyCards.firstOrNull {
                it.customerId == customer.customerId && it.effectiveStatus != LoyaltyCardStatus.REPLACED
            }
        } else null
        _cartState.update {
            it.copy(
                selectedCustomer = customer,
                discountPercent = customer?.discountPercent?.toDouble() ?: 0.0,
                loyaltyPointsToRedeem = 0,
                loyaltyDiscountCents = 0L,
                selectedLoyaltyCard = matchingCard,
                appliedLoyaltyVoucher = null,
                loyaltyPreview = null
            )
        }
        refreshLoyaltyPreview()
    }

    fun lookupAndAttachLoyalty(query: String, branchId: String) {
        val repo = smartLoyaltyRepository ?: return
        val cleaned = query.trim()
        if (cleaned.isBlank()) {
            setFeedback("Enter or scan a Loyalty Card QR code, card number, phone, or voucher code.", true)
            return
        }
        viewModelScope.launch {
            val voucherDirect = repo.findVoucherByCodeOrQr(cleaned)
            val cards = repo.lookupByQrOrQuery(cleaned, branchIdFilter = null)
            val chosenCard = cards.firstOrNull()
            if (chosenCard == null) {
                setFeedback("No loyalty card or voucher found matching \"$cleaned\".", true)
                return@launch
            }
            if (chosenCard.branchId != branchId && chosenCard.effectiveStatus == LoyaltyCardStatus.REPLACED) {
                setFeedback("Scanned card ${chosenCard.cardNumber} was replaced.", true)
                return@launch
            }
            val matchingCustomer = uiState.value.customers.firstOrNull { it.customerId == chosenCard.customerId }
                ?: CustomerSummary(
                    customerId = chosenCard.customerId,
                    branchId = chosenCard.branchId,
                    name = chosenCard.customerName,
                    phone = chosenCard.customerPhone,
                    email = "",
                    loyaltyPoints = 0,
                    tier = "Member",
                    discountPercent = 0
                )
            _cartState.update {
                it.copy(
                    selectedCustomer = matchingCustomer,
                    discountPercent = matchingCustomer.discountPercent.toDouble(),
                    selectedLoyaltyCard = chosenCard,
                    appliedLoyaltyVoucher = voucherDirect?.takeIf { v ->
                        v.cardId == chosenCard.cardId && v.isRedeemableNow
                    }
                )
            }
            refreshLoyaltyPreview()
            val vNote = voucherDirect?.takeIf { it.isRedeemableNow }?.let { " + Voucher ${it.voucherCode} applied" } ?: ""
            setFeedback("Attached loyalty card ${chosenCard.cardNumber} (${chosenCard.customerName})$vNote.", false)
        }
    }

    fun selectLoyaltyCard(card: LoyaltyCardRecord?) {
        if (card == null) {
            clearLoyaltySelection()
            return
        }
        val matchingCustomer = uiState.value.customers.firstOrNull { it.customerId == card.customerId }
        _cartState.update {
            it.copy(
                selectedCustomer = matchingCustomer ?: it.selectedCustomer,
                discountPercent = matchingCustomer?.discountPercent?.toDouble() ?: it.discountPercent,
                selectedLoyaltyCard = card,
                appliedLoyaltyVoucher = null
            )
        }
        refreshLoyaltyPreview()
    }

    fun applyLoyaltyVoucher(voucher: LoyaltyRewardVoucherRecord?) {
        _cartState.update { it.copy(appliedLoyaltyVoucher = voucher) }
        refreshLoyaltyPreview()
    }

    fun clearLoyaltySelection() {
        _cartState.update {
            it.copy(
                selectedLoyaltyCard = null,
                appliedLoyaltyVoucher = null,
                loyaltyPreview = null
            )
        }
    }

    fun enrollCustomerInPos(session: UserSession, customerId: String) {
        val repo = smartLoyaltyRepository ?: return
        viewModelScope.launch {
            repo.enrollCustomerCard(session, customerId).fold(
                onSuccess = { card ->
                    selectLoyaltyCard(card)
                    setFeedback("Enrolled Smart Loyalty Card ${card.cardNumber} for ${card.customerName}.", false)
                },
                onFailure = { err ->
                    setFeedback(err.message ?: "Card enrollment failed.", true)
                }
            )
        }
    }

    private fun refreshLoyaltyPreview() {
        val repo = smartLoyaltyRepository ?: return
        val card = _cartState.value.selectedLoyaltyCard ?: return
        val cartSnap = _cartState.value
        viewModelScope.launch {
            val branchId = authRepository.activeBranchId.first()
            val preview = repo.previewPosLoyalty(
                branchId = branchId,
                cardId = card.cardId,
                cartItems = cartSnap.items,
                preVoucherTotals = cartSnap.preVoucherTotals,
                selectedVoucherDiscountCents = cartSnap.estimatedVoucherDiscountCents
            )
            _cartState.update { it.copy(loyaltyPreview = preview) }
        }
    }

    fun applyLoyaltyRedemption(pointsToRedeem: Int) {
        val customer = _cartState.value.selectedCustomer ?: return
        val clamped = pointsToRedeem.coerceIn(0, customer.loyaltyPoints)
        val discountCents = clamped * 5L // 100 pts = $5.00 standard value
        _cartState.update {
            it.copy(
                loyaltyPointsToRedeem = clamped,
                loyaltyDiscountCents = discountCents
            )
        }
        refreshLoyaltyPreview()
    }

    fun setDiscountPercent(percent: Double) {
        _cartState.update { it.copy(discountPercent = percent.coerceIn(0.0, 100.0)) }
        refreshLoyaltyPreview()
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _cartState.update { state ->
            val total = state.totals.totalPayableCents
            val defaultSplit = when (method) {
                PaymentMethod.CASH -> SplitPaymentBreakdown(cashCents = total)
                PaymentMethod.CARD -> SplitPaymentBreakdown(cardCents = total)
                PaymentMethod.BANK_TRANSFER -> SplitPaymentBreakdown(bankTransferCents = total)
                PaymentMethod.DIGITAL -> SplitPaymentBreakdown(digitalCents = total)
                PaymentMethod.SPLIT -> state.splitBreakdown
            }
            state.copy(paymentMethod = method, splitBreakdown = defaultSplit)
        }
    }

    fun updateSplitPayment(split: SplitPaymentBreakdown) {
        _cartState.update { it.copy(splitBreakdown = split) }
    }

    fun confirmAndPlaceOrder(session: UserSession) {
        val current = _cartState.value
        if (current.isSubmitting) return

        val payableAfterVoucher = current.totals.totalPayableCents
        val effectiveSplit = when (current.paymentMethod) {
            PaymentMethod.SPLIT -> current.splitBreakdown
            PaymentMethod.CASH -> {
                if (current.appliedLoyaltyVoucher != null) SplitPaymentBreakdown(cashCents = payableAfterVoucher)
                else if (current.splitBreakdown.cashCents > 0L) current.splitBreakdown
                else SplitPaymentBreakdown(cashCents = payableAfterVoucher)
            }
            PaymentMethod.CARD -> SplitPaymentBreakdown(cardCents = payableAfterVoucher)
            PaymentMethod.BANK_TRANSFER -> SplitPaymentBreakdown(bankTransferCents = payableAfterVoucher)
            PaymentMethod.DIGITAL -> SplitPaymentBreakdown(digitalCents = payableAfterVoucher)
        }

        viewModelScope.launch {
            _cartState.update { it.copy(isSubmitting = true) }
            val result = posRepository.placeOrder(
                session = session,
                idempotencyKey = current.idempotencyKey,
                cartItems = current.items,
                totals = current.preVoucherTotals,
                orderType = current.orderType,
                tableNumber = current.tableNumber,
                priority = current.priority,
                customer = current.selectedCustomer,
                paymentMethod = current.paymentMethod,
                splitBreakdown = effectiveSplit,
                loyaltyPointsToRedeem = current.loyaltyPointsToRedeem,
                loyaltyDiscountCents = current.loyaltyDiscountCents,
                loyaltyCardId = current.selectedLoyaltyCard?.cardId,
                loyaltyVoucherId = current.appliedLoyaltyVoucher?.voucherId
            )
            result.fold(
                onSuccess = { savedOrder ->
                    _selectedInvoiceOrderId.value = savedOrder.orderId
                    _selectedKotOrderId.value = savedOrder.orderId
                    _cartState.value = PosCartState(lastCompletedOrder = savedOrder)
                    val loyaltyTag = savedOrder.loyaltyStatusMessage?.let { " | Loyalty: $it" } ?: ""
                    setFeedback(
                        "Order ${savedOrder.orderNumber} confirmed! Invoice ${savedOrder.invoiceNumber} & ${savedOrder.kotNumber}$loyaltyTag",
                        false
                    )
                },
                onFailure = { err ->
                    _cartState.update { it.copy(isSubmitting = false) }
                    setFeedback(err.message ?: "Failed to confirm order.", true)
                }
            )
        }
    }

    fun dismissCompletedOrderDialog() {
        _cartState.update { it.copy(lastCompletedOrder = null) }
    }

    fun selectOrderForInvoice(orderId: String) {
        _selectedInvoiceOrderId.value = orderId
    }

    fun selectOrderForKot(orderId: String) {
        _selectedKotOrderId.value = orderId
    }

    fun recordInvoiceReprint(session: UserSession, orderId: String) {
        viewModelScope.launch {
            posRepository.recordInvoiceReprint(session, orderId)
            setFeedback("Customer Invoice marked as REPRINT and logged.", false)
        }
    }

    fun recordKotReprint(session: UserSession, orderId: String) {
        viewModelScope.launch {
            posRepository.recordKotReprint(session, orderId)
            setFeedback("Kitchen KOT marked as REPRINT and logged.", false)
        }
    }

    fun cancelOrder(session: UserSession, orderId: String, reason: String) {
        viewModelScope.launch {
            posRepository.cancelOrder(session, orderId, reason).fold(
                onSuccess = { setFeedback("Order cancelled with authorization.", false) },
                onFailure = { setFeedback(it.message ?: "Cancellation denied.", true) }
            )
        }
    }

    fun refundOrder(session: UserSession, orderId: String, reason: String) {
        viewModelScope.launch {
            posRepository.refundOrder(session, orderId, reason).fold(
                onSuccess = { setFeedback("Refund processed with manager approval.", false) },
                onFailure = { setFeedback(it.message ?: "Refund denied.", true) }
            )
        }
    }

    fun submitDailyCashClosing(session: UserSession, countedCashCents: Long, notes: String) {
        viewModelScope.launch {
            posRepository.submitDailyCashClosing(session, countedCashCents, notes).fold(
                onSuccess = {
                    setFeedback("Daily cash closing saved for ${it.businessDate}.", false)
                },
                onFailure = {
                    setFeedback(it.message ?: "Cash closing failed.", true)
                }
            )
        }
    }

    fun importOwnerLogo(uri: Uri) {
        viewModelScope.launch {
            brandAssetManager.importOwnerLogo(uri).fold(
                onSuccess = {
                    setFeedback("Original AA CRAVE logo updated (${it.format?.name ?: "IMAGE"}).", false)
                },
                onFailure = {
                    setFeedback(it.message ?: "Invalid logo image.", true)
                }
            )
        }
    }

    fun resetLogoToTemporaryPlaceholder() {
        viewModelScope.launch {
            brandAssetManager.clearToTemporaryPlaceholder()
            setFeedback("Reverted to temporary logo placeholder.", false)
        }
    }

    fun reportPrinterAttempt(result: Result<String>) {
        result.fold(
            onSuccess = { setFeedback(it, false) },
            onFailure = { setFeedback(it.message ?: "Printer unavailable.", true) }
        )
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    private fun setFeedback(message: String, isError: Boolean) {
        _feedback.value = message to isError
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val posRepository: PosRepository,
        private val brandAssetManager: BrandAssetManager,
        private val thermalPrinterService: ThermalPrinterService,
        private val smartLoyaltyRepository: SmartLoyaltyRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PosViewModel(
                authRepository = authRepository,
                posRepository = posRepository,
                brandAssetManager = brandAssetManager,
                thermalPrinterService = thermalPrinterService,
                smartLoyaltyRepository = smartLoyaltyRepository
            ) as T
        }
    }
}
