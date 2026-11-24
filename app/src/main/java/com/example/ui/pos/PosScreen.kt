package com.example.ui.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.example.domain.model.CartItem
import com.example.domain.model.CustomerSummary
import com.example.domain.model.LoyaltyCardStatus
import com.example.domain.model.LoyaltyRewardVoucherRecord
import com.example.domain.model.OrderPriority
import com.example.domain.model.OrderType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PosOrderRecord
import com.example.domain.model.PosProduct
import com.example.domain.model.ProductAddon
import com.example.domain.model.ProductCategory
import com.example.domain.model.ProductVariation
import com.example.domain.model.SplitPaymentBreakdown
import com.example.domain.model.UserSession
import com.example.domain.model.centsToCurrency
import com.example.domain.model.centsToPkr
import com.example.ui.components.StatusPill
import com.example.ui.loyalty.QrCodeCameraScannerDialog
import com.example.ui.loyalty.StampMiniProgressStrip
import com.example.ui.loyalty.cardStatusBgColor
import com.example.ui.loyalty.cardStatusColor
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
import com.example.ui.viewmodel.PosUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    session: UserSession,
    uiState: PosUiState,
    onOpenDrawer: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onAddToCart: (PosProduct, ProductVariation?, List<ProductAddon>, String, Int) -> Unit,
    onIncrementCart: (String) -> Unit,
    onDecrementCart: (String) -> Unit,
    onRemoveCart: (String) -> Unit,
    onClearCart: () -> Unit,
    onOrderTypeChange: (OrderType) -> Unit,
    onTableNumberChange: (String) -> Unit,
    onPriorityChange: (OrderPriority) -> Unit,
    onSelectCustomer: (CustomerSummary?) -> Unit,
    onApplyLoyaltyPoints: (Int) -> Unit = {},
    onDiscountPercentChange: (Double) -> Unit,
    onPaymentMethodChange: (PaymentMethod) -> Unit,
    onSplitPaymentChange: (SplitPaymentBreakdown) -> Unit,
    onConfirmOrder: () -> Unit,
    onDismissCompletedOrder: () -> Unit,
    onOpenInvoiceForOrder: (String) -> Unit,
    onOpenKotForOrder: (String) -> Unit,
    onClearFeedback: () -> Unit,
    onLookupLoyalty: (String) -> Unit = {},
    onApplyLoyaltyVoucher: (LoyaltyRewardVoucherRecord?) -> Unit = {},
    onClearLoyaltySelection: () -> Unit = {},
    onEnrollCustomerLoyalty: (String) -> Unit = {}
) {
    var customizingProduct by remember { mutableStateOf<PosProduct?>(null) }
    var showPhoneCartSheet by remember { mutableStateOf(false) }
    var showLoyaltyQrScanner by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        val msg = uiState.feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = CraveGoldBright)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "AA CRAVE POS",
                            style = MaterialTheme.typography.titleLarge,
                            color = CraveGoldBright
                        )
                        Text(
                            text = "${session.branchName} • ${session.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (uiState.cart.items.isNotEmpty()) {
                        TextButton(onClick = onClearCart) {
                            Text("Clear", color = StatusCrimson)
                        }
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isTabletLayout = maxWidth >= 720.dp

            if (isTabletLayout) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ProductCatalogPane(
                        uiState = uiState,
                        onSelectCategory = onSelectCategory,
                        onSearchChanged = onSearchChanged,
                        onProductTap = { product ->
                            if (product.variations.isEmpty() && product.addons.isEmpty()) {
                                onAddToCart(product, null, emptyList(), "", 1)
                            } else {
                                customizingProduct = product
                            }
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                    )
                    Surface(
                        color = ObsidianSurface,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        CartAndBillingPane(
                            uiState = uiState,
                            onIncrementCart = onIncrementCart,
                            onDecrementCart = onDecrementCart,
                            onRemoveCart = onRemoveCart,
                            onOrderTypeChange = onOrderTypeChange,
                            onTableNumberChange = onTableNumberChange,
                            onPriorityChange = onPriorityChange,
                            onSelectCustomer = onSelectCustomer,
                            onApplyLoyaltyPoints = onApplyLoyaltyPoints,
                            onDiscountPercentChange = onDiscountPercentChange,
                            onPaymentMethodChange = onPaymentMethodChange,
                            onSplitPaymentChange = onSplitPaymentChange,
                            onConfirmOrder = onConfirmOrder,
                            onOpenQrScanner = { showLoyaltyQrScanner = true },
                            onLookupLoyalty = onLookupLoyalty,
                            onApplyLoyaltyVoucher = onApplyLoyaltyVoucher,
                            onClearLoyaltySelection = onClearLoyaltySelection,
                            onEnrollCustomerLoyalty = onEnrollCustomerLoyalty
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    ProductCatalogPane(
                        uiState = uiState,
                        onSelectCategory = onSelectCategory,
                        onSearchChanged = onSearchChanged,
                        onProductTap = { product ->
                            if (product.variations.isEmpty() && product.addons.isEmpty()) {
                                onAddToCart(product, null, emptyList(), "", 1)
                            } else {
                                customizingProduct = product
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Sticky Phone Cart Bar
                    Surface(
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${uiState.cart.itemCount} items in cart",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = centsToCurrency(uiState.cart.totals.totalPayableCents),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = CraveGoldBright
                                )
                            }
                            Button(
                                onClick = { showPhoneCartSheet = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CraveGold,
                                    contentColor = OnCraveGold
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("pos_open_cart_button")
                            ) {
                                Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checkout")
                            }
                        }
                    }
                }

                if (showPhoneCartSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showPhoneCartSheet = false },
                        containerColor = ObsidianSurface
                    ) {
                        CartAndBillingPane(
                            uiState = uiState,
                            onIncrementCart = onIncrementCart,
                            onDecrementCart = onDecrementCart,
                            onRemoveCart = onRemoveCart,
                            onOrderTypeChange = onOrderTypeChange,
                            onTableNumberChange = onTableNumberChange,
                            onPriorityChange = onPriorityChange,
                            onSelectCustomer = onSelectCustomer,
                            onApplyLoyaltyPoints = onApplyLoyaltyPoints,
                            onDiscountPercentChange = onDiscountPercentChange,
                            onPaymentMethodChange = onPaymentMethodChange,
                            onSplitPaymentChange = onSplitPaymentChange,
                            onConfirmOrder = {
                                onConfirmOrder()
                                showPhoneCartSheet = false
                            },
                            onOpenQrScanner = { showLoyaltyQrScanner = true },
                            onLookupLoyalty = onLookupLoyalty,
                            onApplyLoyaltyVoucher = onApplyLoyaltyVoucher,
                            onClearLoyaltySelection = onClearLoyaltySelection,
                            onEnrollCustomerLoyalty = onEnrollCustomerLoyalty,
                            modifier = Modifier.fillMaxHeight(0.88f)
                        )
                    }
                }
            }
        }
    }

    if (showLoyaltyQrScanner) {
        QrCodeCameraScannerDialog(
            title = "Scan Member Card / Voucher QR",
            onDismiss = { showLoyaltyQrScanner = false },
            onCodeCaptured = { captured ->
                showLoyaltyQrScanner = false
                onLookupLoyalty(captured)
            }
        )
    }

    customizingProduct?.let { product ->
        ProductCustomizeDialog(
            product = product,
            onDismiss = { customizingProduct = null },
            onAdd = { variation, addons, notes, qty ->
                onAddToCart(product, variation, addons, notes, qty)
                customizingProduct = null
            }
        )
    }

    uiState.cart.lastCompletedOrder?.let { order ->
        OrderCompletedDocumentDialog(
            order = order,
            onDismiss = onDismissCompletedOrder,
            onOpenInvoice = {
                onDismissCompletedOrder()
                onOpenInvoiceForOrder(order.orderId)
            },
            onOpenKot = {
                onDismissCompletedOrder()
                onOpenKotForOrder(order.orderId)
            }
        )
    }
}

@Composable
private fun ProductCatalogPane(
    uiState: PosUiState,
    onSelectCategory: (String?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onProductTap: (PosProduct) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchChanged,
            placeholder = { Text("Search dishes, SKU, or category...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            trailingIcon = {
                if (uiState.searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChanged("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pos_search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.selectedCategoryId == null,
                onClick = { onSelectCategory(null) },
                label = { Text("All Menu") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CraveGoldContainer,
                    selectedLabelColor = CraveGoldBright
                )
            )
            uiState.categories.forEach { cat ->
                FilterChip(
                    selected = uiState.selectedCategoryId == cat.categoryId,
                    onClick = { onSelectCategory(cat.categoryId) },
                    label = { Text(cat.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CraveGoldContainer,
                        selectedLabelColor = CraveGoldBright
                    )
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 165.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(uiState.filteredProducts, key = { it.productId }) { product ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProductTap(product) }
                        .testTag("pos_product_${product.productId}"),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                text = product.sku,
                                containerColor = CraveGoldContainer,
                                contentColor = CraveGoldBright
                            )
                            Text(
                                text = centsToCurrency(product.basePriceCents),
                                style = MaterialTheme.typography.titleMedium,
                                color = CraveGoldBright,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = product.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (product.variations.isNotEmpty() || product.addons.isNotEmpty()) {
                            Text(
                                text = "${product.variations.size} sizes • ${product.addons.size} add-ons",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusEmerald
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartAndBillingPane(
    uiState: PosUiState,
    onIncrementCart: (String) -> Unit,
    onDecrementCart: (String) -> Unit,
    onRemoveCart: (String) -> Unit,
    onOrderTypeChange: (OrderType) -> Unit,
    onTableNumberChange: (String) -> Unit,
    onPriorityChange: (OrderPriority) -> Unit,
    onSelectCustomer: (CustomerSummary?) -> Unit,
    onApplyLoyaltyPoints: (Int) -> Unit,
    onDiscountPercentChange: (Double) -> Unit,
    onPaymentMethodChange: (PaymentMethod) -> Unit,
    onSplitPaymentChange: (SplitPaymentBreakdown) -> Unit,
    onConfirmOrder: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onLookupLoyalty: (String) -> Unit,
    onApplyLoyaltyVoucher: (LoyaltyRewardVoucherRecord?) -> Unit,
    onClearLoyaltySelection: () -> Unit,
    onEnrollCustomerLoyalty: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cart = uiState.cart
    val totals = cart.totals
    var loyaltySearchText by remember { mutableStateOf("") }

    var cashText by remember(cart.totals.totalPayableCents, cart.paymentMethod) {
        mutableStateOf(
            if (cart.paymentMethod == PaymentMethod.SPLIT) (cart.splitBreakdown.cashCents / 100.0).toString()
            else "%.2f".format(java.util.Locale.US, cart.totals.totalPayableCents / 100.0)
        )
    }
    var cardText by remember(cart.paymentMethod) { mutableStateOf("0.00") }
    var digitalText by remember(cart.paymentMethod) { mutableStateOf("0.00") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Order & Billing Cart",
            style = MaterialTheme.typography.titleLarge,
            color = CraveGoldBright
        )

        // Order Type + Priority
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrderType.entries.forEach { type ->
                FilterChip(
                    selected = cart.orderType == type,
                    onClick = { onOrderTypeChange(type) },
                    label = { Text(type.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CraveGoldContainer,
                        selectedLabelColor = CraveGoldBright
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (cart.orderType == OrderType.DINE_IN) {
                OutlinedTextField(
                    value = cart.tableNumber,
                    onValueChange = onTableNumberChange,
                    label = { Text("Table #") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.weight(1.5f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OrderPriority.entries.forEach { prio ->
                    FilterChip(
                        selected = cart.priority == prio,
                        onClick = { onPriorityChange(prio) },
                        label = { Text(prio.label) }
                    )
                }
            }
        }

        // Customer Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = CraveGold, modifier = Modifier.size(18.dp))
            FilterChip(
                selected = cart.selectedCustomer == null,
                onClick = { onSelectCustomer(null) },
                label = { Text("Walk-in") }
            )
            uiState.customers.forEach { customer ->
                FilterChip(
                    selected = cart.selectedCustomer?.customerId == customer.customerId,
                    onClick = { onSelectCustomer(customer) },
                    label = { Text("${customer.name} (${customer.loyaltyPoints} pts • ${customer.discountPercent}% off)") }
                )
            }
        }

        cart.selectedCustomer?.let { cust ->
            if (cust.loyaltyPoints >= 50) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Loyalty (${cust.loyaltyPoints} pts):", style = MaterialTheme.typography.labelSmall, color = CraveGoldBright)
                    FilterChip(
                        selected = cart.loyaltyPointsToRedeem == 0,
                        onClick = { onApplyLoyaltyPoints(0) },
                        label = { Text("Earn Only") }
                    )
                    if (cust.loyaltyPoints >= 50) {
                        FilterChip(
                            selected = cart.loyaltyPointsToRedeem == 50,
                            onClick = { onApplyLoyaltyPoints(50) },
                            label = { Text("Redeem 50 (-${centsToCurrency(250L)})") }
                        )
                    }
                    if (cust.loyaltyPoints >= 100) {
                        FilterChip(
                            selected = cart.loyaltyPointsToRedeem == 100,
                            onClick = { onApplyLoyaltyPoints(100) },
                            label = { Text("Redeem 100 (-${centsToCurrency(500L)})") }
                        )
                    }
                }
            }
        }

        // Smart Loyalty 6-Stamp Card & Voucher Block
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianBg),
            border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Stars, contentDescription = null, tint = CraveGoldBright, modifier = Modifier.size(16.dp))
                        Text("Smart Loyalty & Vouchers", style = MaterialTheme.typography.labelLarge, color = CraveGoldBright)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onOpenQrScanner,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("pos_scan_loyalty_qr_button")
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan Loyalty QR", tint = CraveGoldBright)
                        }
                        if (cart.selectedLoyaltyCard != null) {
                            IconButton(
                                onClick = onClearLoyaltySelection,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove Loyalty Card", tint = StatusCrimson)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = loyaltySearchText,
                        onValueChange = { loyaltySearchText = it },
                        placeholder = { Text("Card #, Phone, or Voucher Code") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("pos_loyalty_lookup_input")
                    )
                    Button(
                        onClick = {
                            if (loyaltySearchText.isNotBlank()) {
                                onLookupLoyalty(loyaltySearchText)
                            }
                        },
                        enabled = loyaltySearchText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CraveGoldContainer, contentColor = CraveGoldBright),
                        modifier = Modifier.height(50.dp).testTag("pos_loyalty_lookup_button")
                    ) {
                        Text("Attach")
                    }
                }

                val selectedCard = cart.selectedLoyaltyCard
                if (selectedCard != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${selectedCard.cardNumber} • ${selectedCard.customerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = CraveGoldBright,
                            fontWeight = FontWeight.SemiBold
                        )
                        StatusPill(
                            text = selectedCard.effectiveStatus.label,
                            containerColor = cardStatusBgColor(selectedCard.effectiveStatus),
                            contentColor = cardStatusColor(selectedCard.effectiveStatus)
                        )
                    }
                    StampMiniProgressStrip(
                        current = selectedCard.currentCycleStamps,
                        required = selectedCard.requiredStamps
                    )
                    cart.loyaltyPreview?.let { preview ->
                        Text(
                            preview.eligibilityReason,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (preview.willEarnStamp) StatusEmerald else StatusAmber
                        )
                        if (preview.activeVouchers.isNotEmpty()) {
                            Text("Available Reward Vouchers:", style = MaterialTheme.typography.labelSmall, color = CraveGold)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                preview.activeVouchers.forEach { voucher ->
                                    val isApplied = cart.appliedLoyaltyVoucher?.voucherId == voucher.voucherId
                                    FilterChip(
                                        selected = isApplied,
                                        onClick = {
                                            onApplyLoyaltyVoucher(if (isApplied) null else voucher)
                                        },
                                        label = {
                                            Text(
                                                if (isApplied) "Applied: ${voucher.voucherCode} (-${centsToPkr(cart.estimatedVoucherDiscountCents)})"
                                                else "Redeem ${voucher.voucherCode} (${centsToPkr(voucher.rewardValueCents)})"
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = StatusEmeraldBg,
                                            selectedLabelColor = StatusEmerald
                                        ),
                                        modifier = Modifier.testTag("pos_voucher_chip_${voucher.voucherCode}")
                                    )
                                }
                            }
                        }
                    }
                } else if (cart.selectedCustomer != null) {
                    OutlinedButton(
                        onClick = { onEnrollCustomerLoyalty(cart.selectedCustomer.customerId) },
                        border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth().testTag("pos_enroll_selected_customer_loyalty")
                    ) {
                        Text("Issue 6-Stamp Loyalty Card to ${cart.selectedCustomer.name}", color = CraveGoldBright)
                    }
                }
            }
        }

        HorizontalDivider(color = ObsidianBorder)

        if (cart.items.isEmpty()) {
            Text(
                text = "Tap any dish from the menu to start building an order.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            cart.items.forEach { item ->
                CartLineRow(
                    item = item,
                    onIncrement = { onIncrementCart(item.cartLineId) },
                    onDecrement = { onDecrementCart(item.cartLineId) },
                    onRemove = { onRemoveCart(item.cartLineId) }
                )
            }
        }

        HorizontalDivider(color = ObsidianBorder)

        // Discount Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Discount:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0.0, 5.0, 10.0, 15.0).forEach { pct ->
                FilterChip(
                    selected = cart.discountPercent == pct,
                    onClick = { onDiscountPercentChange(pct) },
                    label = { Text("${pct.toInt()}%") }
                )
            }
        }

        // Totals Summary
        SummaryLine("Subtotal", totals.subtotalCents)
        if (cart.estimatedVoucherDiscountCents > 0L) {
            SummaryLine("Reward Voucher (${cart.appliedLoyaltyVoucher?.voucherCode ?: "Promo"})", -cart.estimatedVoucherDiscountCents)
        }
        val nonVoucherDiscount = (totals.discountCents - cart.estimatedVoucherDiscountCents).coerceAtLeast(0L)
        if (nonVoucherDiscount > 0L) SummaryLine("Discount (${cart.discountPercent.toInt()}%)", -nonVoucherDiscount)
        SummaryLine("Tax (${totals.taxRatePercent}%)", totals.taxCents)
        if (totals.serviceChargeCents > 0L) SummaryLine("Service Charge (${totals.serviceChargeRatePercent}%)", totals.serviceChargeCents)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Total Payable", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
            Text(centsToCurrency(totals.totalPayableCents), style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
        }

        HorizontalDivider(color = ObsidianBorder)

        // Payment Method
        Text("Payment Method", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PaymentMethod.entries.forEach { method ->
                FilterChip(
                    selected = cart.paymentMethod == method,
                    onClick = { onPaymentMethodChange(method) },
                    label = { Text(method.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CraveGoldContainer,
                        selectedLabelColor = CraveGoldBright
                    )
                )
            }
        }

        if (cart.paymentMethod == PaymentMethod.SPLIT) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = cashText,
                    onValueChange = {
                        cashText = it
                        val c = ((it.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0L)
                        val cr = ((cardText.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0L)
                        val dg = ((digitalText.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0L)
                        onSplitPaymentChange(SplitPaymentBreakdown(cashCents = c, cardCents = cr, digitalCents = dg))
                    },
                    label = { Text("Cash PKR") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = cardText,
                    onValueChange = {
                        cardText = it
                        val c = ((cashText.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0L)
                        val cr = ((it.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0L)
                        val dg = ((digitalText.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0L)
                        onSplitPaymentChange(SplitPaymentBreakdown(cashCents = c, cardCents = cr, digitalCents = dg))
                    },
                    label = { Text("Card PKR") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = digitalText,
                    onValueChange = {
                        digitalText = it
                        val c = ((cashText.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0L)
                        val cr = ((cardText.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0L)
                        val dg = ((it.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtLeast(0L)
                        onSplitPaymentChange(SplitPaymentBreakdown(cashCents = c, cardCents = cr, digitalCents = dg))
                    },
                    label = { Text("Digital PKR") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Button(
            onClick = onConfirmOrder,
            enabled = cart.items.isNotEmpty() && !cart.isSubmitting,
            colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("pos_confirm_order_button")
        ) {
            Text(
                text = if (cart.isSubmitting) "Processing Order..." else "Confirm Order & Generate Invoice + KOT",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun CartLineRow(
    item: CartItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.product.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (item.selectedVariation != null) {
                        Text(
                            text = item.selectedVariation.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = CraveGold
                        )
                    }
                    if (item.addonsSummary.isNotBlank()) {
                        Text(
                            text = "+ ${item.addonsSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.specialInstructions.isNotBlank()) {
                        Text(
                            text = "Note: ${item.specialInstructions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusEmerald
                        )
                    }
                }
                Text(
                    text = centsToCurrency(item.lineTotalCents),
                    style = MaterialTheme.typography.titleSmall,
                    color = CraveGoldBright
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDecrement, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
                    }
                    Text(
                        text = "${item.quantity}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onIncrement, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove item", tint = StatusCrimson)
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, cents: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(centsToCurrency(cents), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ProductCustomizeDialog(
    product: PosProduct,
    onDismiss: () -> Unit,
    onAdd: (ProductVariation?, List<ProductAddon>, String, Int) -> Unit
) {
    var selectedVariation by remember {
        mutableStateOf(product.variations.firstOrNull { it.isDefault } ?: product.variations.firstOrNull())
    }
    val chosenAddons = remember { mutableStateListOf<ProductAddon>() }
    var instructions by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text(product.name, color = CraveGoldBright) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (product.variations.isNotEmpty()) {
                    Text("Select Size / Variation", style = MaterialTheme.typography.labelLarge, color = CraveGold)
                    product.variations.forEach { v ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedVariation = v }
                        ) {
                            RadioButton(
                                selected = selectedVariation?.variationId == v.variationId,
                                onClick = { selectedVariation = v }
                            )
                            Text(
                                text = "${v.name} (+${centsToCurrency(v.priceDeltaCents)})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (product.addons.isNotEmpty()) {
                    Text("Add-ons", style = MaterialTheme.typography.labelLarge, color = CraveGold)
                    product.addons.forEach { addon ->
                        val checked = chosenAddons.any { it.addonId == addon.addonId }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) chosenAddons.removeAll { it.addonId == addon.addonId }
                                    else chosenAddons.add(addon)
                                }
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) chosenAddons.add(addon)
                                    else chosenAddons.removeAll { it.addonId == addon.addonId }
                                }
                            )
                            Text(
                                text = "${addon.name} (+${centsToCurrency(addon.priceCents)})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Special Kitchen Instructions") },
                    placeholder = { Text("e.g. No onions, medium-rare, extra crispy") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Qty:", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { if (quantity > 1) quantity-- }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                    }
                    Text("$quantity", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { quantity++ }) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(selectedVariation, chosenAddons.toList(), instructions, quantity) },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) {
                Text("Add to Order")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun OrderCompletedDocumentDialog(
    order: PosOrderRecord,
    onDismiss: () -> Unit,
    onOpenInvoice: () -> Unit,
    onOpenKot: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        icon = {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StatusEmerald, modifier = Modifier.size(36.dp))
        },
        title = {
            Text("Order Confirmed", color = CraveGoldBright)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Order: ${order.orderNumber}", style = MaterialTheme.typography.titleSmall)
                Text("Customer Invoice: ${order.invoiceNumber}", style = MaterialTheme.typography.bodyMedium, color = StatusEmerald)
                Text("Kitchen Ticket: ${order.kotNumber}", style = MaterialTheme.typography.bodyMedium, color = CraveGoldBright)
                if (!order.loyaltyCardNumber.isNullOrBlank()) {
                    Surface(
                        color = ObsidianBg,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Smart Loyalty • ${order.loyaltyCardNumber}", style = MaterialTheme.typography.labelMedium, color = CraveGoldBright)
                            order.loyaltyStatusMessage?.let { msg ->
                                Text(msg, style = MaterialTheme.typography.bodySmall, color = StatusEmerald)
                            }
                        }
                    }
                }
                Text(
                    "Customer Invoice and Kitchen KOT have been generated as separate documents.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onOpenInvoice,
                    colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Customer Invoice")
                }
                OutlinedButton(
                    onClick = onOpenKot,
                    border = BorderStroke(1.dp, CraveGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Restaurant, contentDescription = null, tint = CraveGoldBright, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Separate Kitchen KOT", color = CraveGoldBright)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("New Order")
                }
            }
        }
    )
}
