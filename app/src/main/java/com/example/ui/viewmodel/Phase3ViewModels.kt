package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.KdsRepository
import com.example.data.repository.MenuRepository
import com.example.data.repository.RecipeRepository
import com.example.domain.model.InventoryItemRecord
import com.example.domain.model.InventoryItemType
import com.example.domain.model.KdsPerformanceSummary
import com.example.domain.model.KdsTicketSummary
import com.example.domain.model.KitchenStation
import com.example.domain.model.KotStatus
import com.example.domain.model.PhysicalAuditRecord
import com.example.domain.model.PosProduct
import com.example.domain.model.ProductCategory
import com.example.domain.model.RecipeDetail
import com.example.domain.model.RecipeIngredientDraft
import com.example.domain.model.StockMovementRecord
import com.example.domain.model.UserSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// -----------------------------------------------------------------------------
// 1. Kitchen Display System (KDS) ViewModel
// -----------------------------------------------------------------------------

enum class KdsStageFilter(val label: String) {
    ACTIVE("Active Queue"),
    NEW("New"),
    PREPARING("Preparing"),
    READY("Ready"),
    COMPLETED("Completed")
}

data class KdsUiState(
    val allTickets: List<KdsTicketSummary> = emptyList(),
    val filteredTickets: List<KdsTicketSummary> = emptyList(),
    val selectedStation: KitchenStation = KitchenStation.ALL,
    val stageFilter: KdsStageFilter = KdsStageFilter.ACTIVE,
    val performance: KdsPerformanceSummary = KdsPerformanceSummary(),
    val soundEnabled: Boolean = true,
    val isUpdating: Boolean = false,
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class KdsViewModel(
    private val authRepository: AuthRepository,
    private val kdsRepository: KdsRepository
) : ViewModel() {

    private val _selectedStation = MutableStateFlow(KitchenStation.ALL)
    private val _stageFilter = MutableStateFlow(KdsStageFilter.ACTIVE)
    private val _soundEnabled = MutableStateFlow(true)
    private val _isUpdating = MutableStateFlow(false)
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val ticketsFlow = authRepository.activeBranchId.flatMapLatest { branchId ->
        kdsRepository.observeKdsTickets(branchId)
    }

    val uiState: StateFlow<KdsUiState> = combine(
        ticketsFlow,
        _selectedStation,
        _stageFilter,
        _soundEnabled,
        _isUpdating,
        _feedback
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val tickets = args[0] as List<KdsTicketSummary>
        val station = args[1] as KitchenStation
        val stageFilter = args[2] as KdsStageFilter
        val soundEnabled = args[3] as Boolean
        val isUpdating = args[4] as Boolean
        @Suppress("UNCHECKED_CAST")
        val feedback = args[5] as Pair<String?, Boolean>

        val stationFiltered = if (station == KitchenStation.ALL) {
            tickets
        } else {
            tickets.filter { it.station == station }
        }

        val stageFiltered = when (stageFilter) {
            KdsStageFilter.ACTIVE -> stationFiltered.filter {
                it.status == KotStatus.NEW || it.status == KotStatus.PREPARING || it.status == KotStatus.READY
            }
            KdsStageFilter.NEW -> stationFiltered.filter { it.status == KotStatus.NEW }
            KdsStageFilter.PREPARING -> stationFiltered.filter { it.status == KotStatus.PREPARING }
            KdsStageFilter.READY -> stationFiltered.filter { it.status == KotStatus.READY }
            KdsStageFilter.COMPLETED -> stationFiltered.filter { it.status == KotStatus.COMPLETED }
        }.sortedWith(
            compareByDescending<KdsTicketSummary> { it.priority.ordinal }
                .thenBy { it.createdAt }
        )

        KdsUiState(
            allTickets = tickets,
            filteredTickets = stageFiltered,
            selectedStation = station,
            stageFilter = stageFilter,
            performance = kdsRepository.computePerformanceSummary(stationFiltered),
            soundEnabled = soundEnabled,
            isUpdating = isUpdating,
            feedbackMessage = feedback.first,
            isErrorFeedback = feedback.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KdsUiState()
    )

    fun selectStation(station: KitchenStation) {
        _selectedStation.value = station
    }

    fun selectStageFilter(filter: KdsStageFilter) {
        _stageFilter.value = filter
    }

    fun toggleSound() {
        _soundEnabled.value = !_soundEnabled.value
    }

    fun updateKotStatus(session: UserSession, kotId: String, targetStatus: KotStatus) {
        if (_isUpdating.value) return
        viewModelScope.launch {
            _isUpdating.value = true
            kdsRepository.updateKotStatus(session, kotId, targetStatus).fold(
                onSuccess = {
                    _feedback.value = "Ticket moved to ${targetStatus.label} & order synchronized." to false
                },
                onFailure = {
                    _feedback.value = (it.message ?: "Unable to update ticket stage.") to true
                }
            )
            _isUpdating.value = false
        }
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val kdsRepository: KdsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return KdsViewModel(authRepository, kdsRepository) as T
        }
    }
}

// -----------------------------------------------------------------------------
// 2. Menu Management ViewModel
// -----------------------------------------------------------------------------

enum class MenuFilterMode(val label: String) {
    ACTIVE("Active"),
    AVAILABLE("Available"),
    UNAVAILABLE("Out of Stock"),
    COMBO("Combos"),
    ARCHIVED("Archived")
}

data class MenuUiState(
    val categories: List<ProductCategory> = emptyList(),
    val products: List<PosProduct> = emptyList(),
    val filteredProducts: List<PosProduct> = emptyList(),
    val selectedCategoryId: String? = null,
    val filterMode: MenuFilterMode = MenuFilterMode.ACTIVE,
    val searchQuery: String = "",
    val isBusy: Boolean = false,
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class MenuViewModel(
    private val authRepository: AuthRepository,
    private val menuRepository: MenuRepository
) : ViewModel() {

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    private val _filterMode = MutableStateFlow(MenuFilterMode.ACTIVE)
    private val _searchQuery = MutableStateFlow("")
    private val _isBusy = MutableStateFlow(false)
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val categoriesFlow = authRepository.activeBranchId.flatMapLatest {
        menuRepository.observeCategories(it)
    }
    private val productsFlow = authRepository.activeBranchId.flatMapLatest {
        menuRepository.observeAllMenuProducts(it)
    }

    val uiState: StateFlow<MenuUiState> = combine(
        categoriesFlow,
        productsFlow,
        _selectedCategoryId,
        _filterMode,
        _searchQuery,
        _isBusy,
        _feedback
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val categories = args[0] as List<ProductCategory>
        @Suppress("UNCHECKED_CAST")
        val products = args[1] as List<PosProduct>
        val selectedCat = args[2] as String?
        val mode = args[3] as MenuFilterMode
        val query = args[4] as String
        val isBusy = args[5] as Boolean
        @Suppress("UNCHECKED_CAST")
        val feedback = args[6] as Pair<String?, Boolean>

        val filtered = products.filter { product ->
            val matchesCategory = selectedCat == null || product.categoryId == selectedCat
            val matchesMode = when (mode) {
                MenuFilterMode.ACTIVE -> !product.isArchived
                MenuFilterMode.AVAILABLE -> !product.isArchived && product.isAvailable
                MenuFilterMode.UNAVAILABLE -> !product.isArchived && !product.isAvailable
                MenuFilterMode.COMBO -> !product.isArchived && product.isCombo
                MenuFilterMode.ARCHIVED -> product.isArchived
            }
            val matchesQuery = query.isBlank() ||
                product.name.contains(query, ignoreCase = true) ||
                product.sku.contains(query, ignoreCase = true) ||
                product.description.contains(query, ignoreCase = true)
            matchesCategory && matchesMode && matchesQuery
        }

        MenuUiState(
            categories = categories,
            products = products,
            filteredProducts = filtered,
            selectedCategoryId = selectedCat,
            filterMode = mode,
            searchQuery = query,
            isBusy = isBusy,
            feedbackMessage = feedback.first,
            isErrorFeedback = feedback.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MenuUiState()
    )

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun selectFilterMode(mode: MenuFilterMode) {
        _filterMode.value = mode
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun saveCategory(session: UserSession, categoryId: String?, name: String, sortOrder: Int) {
        viewModelScope.launch {
            _isBusy.value = true
            menuRepository.saveCategory(session, categoryId, name, sortOrder).fold(
                onSuccess = { _feedback.value = "Category '$name' saved." to false },
                onFailure = { _feedback.value = (it.message ?: "Failed to save category.") to true }
            )
            _isBusy.value = false
        }
    }

    fun deleteCategory(session: UserSession, category: ProductCategory) {
        viewModelScope.launch {
            _isBusy.value = true
            menuRepository.deleteCategory(session, category.categoryId, category.name).fold(
                onSuccess = {
                    if (_selectedCategoryId.value == category.categoryId) {
                        _selectedCategoryId.value = null
                    }
                    _feedback.value = "Category '${category.name}' deleted." to false
                },
                onFailure = { _feedback.value = (it.message ?: "Cannot delete category.") to true }
            )
            _isBusy.value = false
        }
    }

    fun saveProduct(
        session: UserSession,
        productId: String?,
        categoryId: String,
        categoryName: String,
        name: String,
        description: String,
        basePriceCents: Long,
        sku: String,
        taxExempt: Boolean,
        isAvailable: Boolean,
        isCombo: Boolean,
        comboSummary: String,
        kitchenStation: String,
        imagePath: String?
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            menuRepository.saveProduct(
                session = session,
                productId = productId,
                categoryId = categoryId,
                categoryName = categoryName,
                name = name,
                description = description,
                basePriceCents = basePriceCents,
                sku = sku,
                taxExempt = taxExempt,
                isAvailable = isAvailable,
                isCombo = isCombo,
                comboSummary = comboSummary,
                kitchenStation = kitchenStation,
                imagePath = imagePath
            ).fold(
                onSuccess = { _feedback.value = "Product '$name' saved to menu." to false },
                onFailure = { _feedback.value = (it.message ?: "Failed to save product.") to true }
            )
            _isBusy.value = false
        }
    }

    fun toggleAvailability(session: UserSession, product: PosProduct, available: Boolean) {
        viewModelScope.launch {
            menuRepository.toggleProductAvailability(session, product, available).fold(
                onSuccess = {
                    _feedback.value = "${product.name} marked ${if (available) "Available" else "Unavailable"}." to false
                },
                onFailure = { _feedback.value = (it.message ?: "Action denied.") to true }
            )
        }
    }

    fun setArchived(session: UserSession, product: PosProduct, archived: Boolean) {
        viewModelScope.launch {
            menuRepository.setProductArchived(session, product, archived).fold(
                onSuccess = {
                    _feedback.value = "${product.name} ${if (archived) "archived" else "restored"}." to false
                },
                onFailure = { _feedback.value = (it.message ?: "Action denied.") to true }
            )
        }
    }

    fun saveVariation(
        session: UserSession,
        variationId: String?,
        productId: String,
        name: String,
        priceDeltaCents: Long,
        isDefault: Boolean
    ) {
        viewModelScope.launch {
            menuRepository.saveVariation(session, variationId, productId, name, priceDeltaCents, isDefault).fold(
                onSuccess = { _feedback.value = "Variation '$name' saved." to false },
                onFailure = { _feedback.value = (it.message ?: "Failed to save variation.") to true }
            )
        }
    }

    fun deleteVariation(session: UserSession, variationId: String) {
        viewModelScope.launch {
            menuRepository.deleteVariation(session, variationId).fold(
                onSuccess = { _feedback.value = "Variation removed." to false },
                onFailure = { _feedback.value = (it.message ?: "Action denied.") to true }
            )
        }
    }

    fun saveAddon(session: UserSession, addonId: String?, productId: String, name: String, priceCents: Long) {
        viewModelScope.launch {
            menuRepository.saveAddon(session, addonId, productId, name, priceCents).fold(
                onSuccess = { _feedback.value = "Add-on '$name' saved." to false },
                onFailure = { _feedback.value = (it.message ?: "Failed to save add-on.") to true }
            )
        }
    }

    fun deleteAddon(session: UserSession, addonId: String) {
        viewModelScope.launch {
            menuRepository.deleteAddon(session, addonId).fold(
                onSuccess = { _feedback.value = "Add-on removed." to false },
                onFailure = { _feedback.value = (it.message ?: "Action denied.") to true }
            )
        }
    }

    suspend fun importProductImage(uri: Uri): Result<String> =
        menuRepository.copyProductImageToAppStorage(uri)

    fun clearFeedback() {
        _feedback.value = null to false
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val menuRepository: MenuRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MenuViewModel(authRepository, menuRepository) as T
        }
    }
}

// -----------------------------------------------------------------------------
// 3. Recipe Management ViewModel
// -----------------------------------------------------------------------------

data class RecipeUiState(
    val products: List<PosProduct> = emptyList(),
    val recipes: List<RecipeDetail> = emptyList(),
    val inventoryItems: List<InventoryItemRecord> = emptyList(),
    val searchQuery: String = "",
    val onlyMissingRecipes: Boolean = false,
    val isBusy: Boolean = false,
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeViewModel(
    private val authRepository: AuthRepository,
    private val menuRepository: MenuRepository,
    private val recipeRepository: RecipeRepository,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _onlyMissingRecipes = MutableStateFlow(false)
    private val _isBusy = MutableStateFlow(false)
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val productsFlow = authRepository.activeBranchId.flatMapLatest {
        menuRepository.observeAllMenuProducts(it)
    }
    private val recipesFlow = authRepository.activeBranchId.flatMapLatest {
        recipeRepository.observeRecipes(it)
    }
    private val inventoryFlow = authRepository.activeBranchId.flatMapLatest {
        inventoryRepository.observeInventoryItems(it)
    }

    val uiState: StateFlow<RecipeUiState> = combine(
        productsFlow,
        recipesFlow,
        inventoryFlow,
        _searchQuery,
        _onlyMissingRecipes,
        _isBusy,
        _feedback
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val products = (args[0] as List<PosProduct>).filterNot { it.isArchived }
        @Suppress("UNCHECKED_CAST")
        val recipes = args[1] as List<RecipeDetail>
        @Suppress("UNCHECKED_CAST")
        val inventory = args[2] as List<InventoryItemRecord>
        val query = args[3] as String
        val onlyMissing = args[4] as Boolean
        val isBusy = args[5] as Boolean
        @Suppress("UNCHECKED_CAST")
        val feedback = args[6] as Pair<String?, Boolean>

        val recipeProductIds = recipes.map { it.productId }.toSet()
        val filteredProducts = products.filter { prod ->
            val matchesQuery = query.isBlank() ||
                prod.name.contains(query, ignoreCase = true) ||
                prod.sku.contains(query, ignoreCase = true)
            val matchesMissing = !onlyMissing || prod.productId !in recipeProductIds
            matchesQuery && matchesMissing
        }

        RecipeUiState(
            products = filteredProducts,
            recipes = recipes,
            inventoryItems = inventory,
            searchQuery = query,
            onlyMissingRecipes = onlyMissing,
            isBusy = isBusy,
            feedbackMessage = feedback.first,
            isErrorFeedback = feedback.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecipeUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleOnlyMissing() {
        _onlyMissingRecipes.value = !_onlyMissingRecipes.value
    }

    fun saveRecipe(
        session: UserSession,
        productId: String,
        notes: String,
        ingredients: List<RecipeIngredientDraft>
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            recipeRepository.saveRecipe(session, productId, notes, ingredients).fold(
                onSuccess = { _feedback.value = "Recipe & cost breakdown saved (new version recorded)." to false },
                onFailure = { _feedback.value = (it.message ?: "Failed to save recipe.") to true }
            )
            _isBusy.value = false
        }
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val menuRepository: MenuRepository,
        private val recipeRepository: RecipeRepository,
        private val inventoryRepository: InventoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecipeViewModel(authRepository, menuRepository, recipeRepository, inventoryRepository) as T
        }
    }
}

// -----------------------------------------------------------------------------
// 4. Inventory Management ViewModel
// -----------------------------------------------------------------------------

enum class InventoryTab(val label: String) {
    RAW_MATERIALS("Raw Materials"),
    FINISHED_PRODUCTS("Finished Products"),
    MOVEMENTS("Movement Ledger"),
    AUDITS("Stock Audits")
}

enum class InventoryAlertFilter(val label: String) {
    ALL("All Stock"),
    LOW_STOCK("Low Stock"),
    EXPIRING("Expiring / Expired")
}

data class InventoryUiState(
    val items: List<InventoryItemRecord> = emptyList(),
    val filteredItems: List<InventoryItemRecord> = emptyList(),
    val products: List<PosProduct> = emptyList(),
    val movements: List<StockMovementRecord> = emptyList(),
    val audits: List<PhysicalAuditRecord> = emptyList(),
    val selectedTab: InventoryTab = InventoryTab.RAW_MATERIALS,
    val alertFilter: InventoryAlertFilter = InventoryAlertFilter.ALL,
    val searchQuery: String = "",
    val lowStockCount: Int = 0,
    val expiringCount: Int = 0,
    val isBusy: Boolean = false,
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModel(
    private val authRepository: AuthRepository,
    private val inventoryRepository: InventoryRepository,
    private val menuRepository: MenuRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(InventoryTab.RAW_MATERIALS)
    private val _alertFilter = MutableStateFlow(InventoryAlertFilter.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _isBusy = MutableStateFlow(false)
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val itemsFlow = authRepository.activeBranchId.flatMapLatest {
        inventoryRepository.observeInventoryItems(it)
    }
    private val productsFlow = authRepository.activeBranchId.flatMapLatest {
        menuRepository.observeAllMenuProducts(it)
    }
    private val movementsFlow = authRepository.activeBranchId.flatMapLatest {
        inventoryRepository.observeStockMovements(it)
    }
    private val auditsFlow = authRepository.activeBranchId.flatMapLatest {
        inventoryRepository.observePhysicalAudits(it)
    }

    val uiState: StateFlow<InventoryUiState> = combine(
        itemsFlow,
        productsFlow,
        movementsFlow,
        auditsFlow,
        _selectedTab,
        _alertFilter,
        _searchQuery,
        _isBusy,
        _feedback
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val items = args[0] as List<InventoryItemRecord>
        @Suppress("UNCHECKED_CAST")
        val products = (args[1] as List<PosProduct>).filterNot { it.isArchived }
        @Suppress("UNCHECKED_CAST")
        val movements = args[2] as List<StockMovementRecord>
        @Suppress("UNCHECKED_CAST")
        val audits = args[3] as List<PhysicalAuditRecord>
        val tab = args[4] as InventoryTab
        val alertFilter = args[5] as InventoryAlertFilter
        val query = args[6] as String
        val isBusy = args[7] as Boolean
        @Suppress("UNCHECKED_CAST")
        val feedback = args[8] as Pair<String?, Boolean>

        val typeScoped = when (tab) {
            InventoryTab.RAW_MATERIALS -> items.filter { it.itemType == InventoryItemType.RAW_MATERIAL }
            InventoryTab.FINISHED_PRODUCTS -> items.filter { it.itemType == InventoryItemType.FINISHED_PRODUCT }
            else -> items
        }

        val filtered = typeScoped.filter { item ->
            val matchesAlert = when (alertFilter) {
                InventoryAlertFilter.ALL -> true
                InventoryAlertFilter.LOW_STOCK -> item.isLowStock
                InventoryAlertFilter.EXPIRING -> item.isExpired || item.isExpiringSoon
            }
            val matchesQuery = query.isBlank() ||
                item.name.contains(query, ignoreCase = true) ||
                item.sku.contains(query, ignoreCase = true)
            matchesAlert && matchesQuery
        }

        InventoryUiState(
            items = items,
            filteredItems = filtered,
            products = products,
            movements = movements,
            audits = audits,
            selectedTab = tab,
            alertFilter = alertFilter,
            searchQuery = query,
            lowStockCount = items.count { it.isLowStock },
            expiringCount = items.count { it.isExpired || it.isExpiringSoon },
            isBusy = isBusy,
            feedbackMessage = feedback.first,
            isErrorFeedback = feedback.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InventoryUiState()
    )

    fun selectTab(tab: InventoryTab) {
        _selectedTab.value = tab
    }

    fun selectAlertFilter(filter: InventoryAlertFilter) {
        _alertFilter.value = filter
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun saveInventoryItem(
        session: UserSession,
        itemId: String?,
        name: String,
        sku: String,
        itemType: InventoryItemType,
        unit: String,
        initialOrCurrentQty: Double,
        reorderLevel: Double,
        unitCostCents: Long,
        expiryDateMillis: Long?,
        linkedProductId: String?
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            inventoryRepository.saveInventoryItem(
                session = session,
                itemId = itemId,
                name = name,
                sku = sku,
                itemType = itemType,
                unit = unit,
                initialOrCurrentQty = initialOrCurrentQty,
                reorderLevel = reorderLevel,
                unitCostCents = unitCostCents,
                expiryDateMillis = expiryDateMillis,
                linkedProductId = linkedProductId
            ).fold(
                onSuccess = { _feedback.value = "Inventory item '$name' saved." to false },
                onFailure = { _feedback.value = (it.message ?: "Failed to save item.") to true }
            )
            _isBusy.value = false
        }
    }

    fun receiveStock(session: UserSession, itemId: String, addedQty: Double, newExpiryMillis: Long?, note: String) {
        viewModelScope.launch {
            _isBusy.value = true
            inventoryRepository.receiveStock(session, itemId, addedQty, newExpiryMillis, note).fold(
                onSuccess = { _feedback.value = "Stock receiving recorded & alerts updated." to false },
                onFailure = { _feedback.value = (it.message ?: "Receiving failed.") to true }
            )
            _isBusy.value = false
        }
    }

    fun adjustStock(session: UserSession, itemId: String, deltaQty: Double, reason: String) {
        viewModelScope.launch {
            _isBusy.value = true
            inventoryRepository.adjustStock(session, itemId, deltaQty, reason).fold(
                onSuccess = { _feedback.value = "Stock adjustment recorded." to false },
                onFailure = { _feedback.value = (it.message ?: "Adjustment failed.") to true }
            )
            _isBusy.value = false
        }
    }

    fun recordWastage(session: UserSession, itemId: String, wastedQty: Double, reason: String) {
        viewModelScope.launch {
            _isBusy.value = true
            inventoryRepository.recordWastage(session, itemId, wastedQty, reason).fold(
                onSuccess = { _feedback.value = "Wastage logged & deducted." to false },
                onFailure = { _feedback.value = (it.message ?: "Wastage entry failed.") to true }
            )
            _isBusy.value = false
        }
    }

    fun recordPhysicalAudit(session: UserSession, itemId: String, countedQty: Double, notes: String) {
        viewModelScope.launch {
            _isBusy.value = true
            inventoryRepository.recordPhysicalAudit(session, itemId, countedQty, notes).fold(
                onSuccess = {
                    val varianceNote = if (it.varianceQty == 0.0) "Exact match" else "Variance ${it.varianceQty}"
                    _feedback.value = "Physical audit saved for ${it.itemName} ($varianceNote)." to false
                },
                onFailure = { _feedback.value = (it.message ?: "Physical audit failed.") to true }
            )
            _isBusy.value = false
        }
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val inventoryRepository: InventoryRepository,
        private val menuRepository: MenuRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InventoryViewModel(authRepository, inventoryRepository, menuRepository) as T
        }
    }
}
