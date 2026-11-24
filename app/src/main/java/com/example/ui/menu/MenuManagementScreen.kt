package com.example.ui.menu

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.KitchenStation
import com.example.domain.model.PosProduct
import com.example.domain.model.ProductCategory
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
import com.example.ui.viewmodel.MenuFilterMode
import com.example.ui.viewmodel.MenuUiState
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuManagementScreen(
    session: UserSession,
    uiState: MenuUiState,
    onOpenDrawer: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectFilterMode: (MenuFilterMode) -> Unit,
    onSearchChanged: (String) -> Unit,
    onSaveCategory: (String?, String, Int) -> Unit,
    onDeleteCategory: (ProductCategory) -> Unit,
    onSaveProduct: (
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
    ) -> Unit,
    onToggleAvailability: (PosProduct, Boolean) -> Unit,
    onSetArchived: (PosProduct, Boolean) -> Unit,
    onSaveVariation: (String?, String, String, Long, Boolean) -> Unit,
    onDeleteVariation: (String) -> Unit,
    onSaveAddon: (String?, String, String, Long) -> Unit,
    onDeleteAddon: (String) -> Unit,
    onImportProductImage: suspend (Uri) -> Result<String>,
    onClearFeedback: () -> Unit
) {
    val canManage = session.capabilities.canManageMenu
    val snackbarHostState = remember { SnackbarHostState() }

    var showCategoryManager by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<PosProduct?>(null) }
    var creatingNewProduct by remember { mutableStateOf(false) }
    var optionsProductId by remember { mutableStateOf<String?>(null) }

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
                            text = "MENU MANAGEMENT",
                            style = MaterialTheme.typography.titleLarge,
                            color = CraveGoldBright
                        )
                        Text(
                            text = if (canManage) "${session.branchName} • Catalog & Combos" else "${session.branchName} • Kitchen View Only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (canManage) {
                        IconButton(
                            onClick = { showCategoryManager = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("menu_manage_categories_btn")
                        ) {
                            Icon(Icons.Filled.Category, contentDescription = "Manage Categories", tint = CraveGoldBright)
                        }
                        IconButton(
                            onClick = { creatingNewProduct = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("menu_add_product_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Product", tint = CraveGoldBright)
                        }
                    } else {
                        StatusPill(
                            text = "VIEW ONLY",
                            containerColor = ObsidianSurfaceElevated,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
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
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChanged,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search menu items, SKUs, or descriptions...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("menu_search_input")
            )

            // Status / Combo / Archive Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MenuFilterMode.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.filterMode == mode,
                        onClick = { onSelectFilterMode(mode) },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CraveGold,
                            selectedLabelColor = OnCraveGold
                        ),
                        modifier = Modifier.testTag("menu_filter_${mode.name}")
                    )
                }
            }

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedCategoryId == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("All Categories (${uiState.products.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CraveGoldContainer,
                        selectedLabelColor = CraveGoldBright
                    )
                )
                uiState.categories.forEach { category ->
                    FilterChip(
                        selected = uiState.selectedCategoryId == category.categoryId,
                        onClick = { onSelectCategory(category.categoryId) },
                        label = { Text(category.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CraveGoldContainer,
                            selectedLabelColor = CraveGoldBright
                        )
                    )
                }
            }

            HorizontalDivider(color = ObsidianBorder)

            if (uiState.filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fastfood,
                            contentDescription = null,
                            tint = CraveGold.copy(alpha = 0.6f),
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "No matching menu products",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Adjust filters or create a new menu product.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.filteredProducts, key = { it.productId }) { product ->
                        MenuProductCard(
                            product = product,
                            canManage = canManage,
                            onToggleAvailability = { available -> onToggleAvailability(product, available) },
                            onEdit = { editingProduct = product },
                            onManageOptions = { optionsProductId = product.productId },
                            onToggleArchive = { onSetArchived(product, !product.isArchived) }
                        )
                    }
                }
            }
        }
    }

    if (showCategoryManager && canManage) {
        CategoryManagerDialog(
            categories = uiState.categories,
            onSaveCategory = onSaveCategory,
            onDeleteCategory = onDeleteCategory,
            onDismiss = { showCategoryManager = false }
        )
    }

    if ((creatingNewProduct || editingProduct != null) && canManage) {
        ProductEditorDialog(
            existing = editingProduct,
            categories = uiState.categories,
            onImportImage = onImportProductImage,
            onSave = { catId, catName, name, desc, priceCents, sku, taxExempt, avail, isCombo, comboSum, station, imagePath ->
                onSaveProduct(
                    editingProduct?.productId,
                    catId,
                    catName,
                    name,
                    desc,
                    priceCents,
                    sku,
                    taxExempt,
                    avail,
                    isCombo,
                    comboSum,
                    station,
                    imagePath
                )
                creatingNewProduct = false
                editingProduct = null
            },
            onDismiss = {
                creatingNewProduct = false
                editingProduct = null
            }
        )
    }

    val optionsProduct = uiState.products.firstOrNull { it.productId == optionsProductId }
    if (optionsProduct != null && canManage) {
        ProductOptionsDialog(
            product = optionsProduct,
            onSaveVariation = { varId, name, deltaCents, isDefault ->
                onSaveVariation(varId, optionsProduct.productId, name, deltaCents, isDefault)
            },
            onDeleteVariation = onDeleteVariation,
            onSaveAddon = { addonId, name, priceCents ->
                onSaveAddon(addonId, optionsProduct.productId, name, priceCents)
            },
            onDeleteAddon = onDeleteAddon,
            onDismiss = { optionsProductId = null }
        )
    }
}

@Composable
private fun MenuProductCard(
    product: PosProduct,
    canManage: Boolean,
    onToggleAvailability: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onManageOptions: () -> Unit,
    onToggleArchive: () -> Unit
) {
    val bitmap = remember(product.imagePath) {
        val path = product.imagePath
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
        } else null
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, if (product.isArchived) StatusCrimson.copy(alpha = 0.5f) else ObsidianBorder),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("menu_product_card_${product.productId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    color = ObsidianSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.size(64.dp)
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = product.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.Fastfood,
                                contentDescription = null,
                                tint = CraveGoldBright,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = centsToCurrency(product.basePriceCents),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = CraveGoldBright
                        )
                    }

                    Text(
                        text = "${product.sku} • ${product.categoryTag} • ${KitchenStation.fromCode(product.kitchenStation).label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CraveGold
                    )

                    if (product.description.isNotBlank()) {
                        Text(
                            text = product.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (product.isArchived) {
                    StatusPill("ARCHIVED", StatusCrimsonBg, StatusCrimson)
                } else {
                    StatusPill(
                        text = if (product.isAvailable) "AVAILABLE" else "OUT OF STOCK",
                        containerColor = if (product.isAvailable) StatusEmeraldBg else StatusAmberBg,
                        contentColor = if (product.isAvailable) StatusEmerald else StatusAmber
                    )
                }
                if (product.isCombo) {
                    StatusPill("COMBO MEAL", CraveGoldContainer, CraveGoldBright)
                }
                StatusPill(
                    text = "${product.variations.size} Vars • ${product.addons.size} Add-ons",
                    containerColor = ObsidianSurfaceElevated,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (product.isCombo && product.comboSummary.isNotBlank()) {
                Text(
                    text = "Includes: ${product.comboSummary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CraveGoldBright
                )
            }

            if (canManage) {
                HorizontalDivider(color = ObsidianBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!product.isArchived) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Available",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = product.isAvailable,
                                onCheckedChange = onToggleAvailability,
                                modifier = Modifier.testTag("menu_avail_switch_${product.productId}")
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = onManageOptions,
                            border = BorderStroke(1.dp, ObsidianBorder),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Options")
                        }
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit product", tint = CraveGoldBright)
                        }
                        IconButton(
                            onClick = onToggleArchive,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (product.isArchived) Icons.Filled.RestoreFromTrash else Icons.Filled.Archive,
                                contentDescription = if (product.isArchived) "Restore product" else "Archive product",
                                tint = if (product.isArchived) StatusEmerald else StatusCrimson
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryManagerDialog(
    categories: List<ProductCategory>,
    onSaveCategory: (String?, String, Int) -> Unit,
    onDeleteCategory: (ProductCategory) -> Unit,
    onDismiss: () -> Unit
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var sortOrderText by remember { mutableStateOf((categories.size + 1).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("Manage Menu Categories", color = CraveGoldBright) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (editingId == null) "New Category Name" else "Edit Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = sortOrderText,
                        onValueChange = { sortOrderText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Sort #") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onSaveCategory(editingId, name, sortOrderText.toIntOrNull() ?: 1)
                            editingId = null
                            name = ""
                            sortOrderText = (categories.size + 2).toString()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text(if (editingId == null) "Add" else "Update")
                    }
                }

                HorizontalDivider(color = ObsidianBorder)

                categories.forEach { cat ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cat.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text("Order #${cat.sortOrder}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row {
                            IconButton(onClick = {
                                editingId = cat.categoryId
                                name = cat.name
                                sortOrderText = cat.sortOrder.toString()
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit ${cat.name}", tint = CraveGoldBright)
                            }
                            IconButton(onClick = { onDeleteCategory(cat) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete ${cat.name}", tint = StatusCrimson)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = CraveGoldBright)
            }
        }
    )
}

@Composable
private fun ProductEditorDialog(
    existing: PosProduct?,
    categories: List<ProductCategory>,
    onImportImage: suspend (Uri) -> Result<String>,
    onSave: (
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
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedCategory by remember {
        mutableStateOf(
            categories.firstOrNull { it.categoryId == existing?.categoryId } ?: categories.firstOrNull()
        )
    }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var sku by remember { mutableStateOf(existing?.sku.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var priceText by remember {
        mutableStateOf(
            if (existing != null) String.format(Locale.US, "%.2f", existing.basePriceCents / 100.0) else ""
        )
    }
    var taxExempt by remember { mutableStateOf(existing?.taxExempt ?: false) }
    var isAvailable by remember { mutableStateOf(existing?.isAvailable ?: true) }
    var isCombo by remember { mutableStateOf(existing?.isCombo ?: false) }
    var comboSummary by remember { mutableStateOf(existing?.comboSummary.orEmpty()) }
    var station by remember { mutableStateOf(KitchenStation.fromCode(existing?.kitchenStation ?: "MAIN_KITCHEN")) }
    var imagePath by remember { mutableStateOf(existing?.imagePath) }
    var imageError by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                onImportImage(uri).fold(
                    onSuccess = {
                        imagePath = it
                        imageError = null
                    },
                    onFailure = {
                        imageError = it.message ?: "Could not load image."
                    }
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text(if (existing == null) "New Menu Product" else "Edit Product", color = CraveGoldBright) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { imagePicker.launch(arrayOf("image/*")) },
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (imagePath.isNullOrBlank()) "Pick Product Photo (PNG/JPG/WEBP)" else "Change Product Photo")
                }
                if (imageError != null) {
                    Text(imageError!!, style = MaterialTheme.typography.bodySmall, color = StatusCrimson)
                }

                Text("Category", style = MaterialTheme.typography.labelMedium, color = CraveGold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory?.categoryId == cat.categoryId,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.name) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        label = { Text("SKU *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Price (PKR) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Kitchen Station Routing", style = MaterialTheme.typography.labelMedium, color = CraveGold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KitchenStation.assignableStations.forEach { st ->
                        FilterChip(
                            selected = station == st,
                            onClick = { station = st },
                            label = { Text(st.label) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isAvailable, onCheckedChange = { isAvailable = it })
                    Text("Available for POS ordering")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = taxExempt, onCheckedChange = { taxExempt = it })
                    Text("Tax Exempt")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isCombo, onCheckedChange = { isCombo = it })
                    Text("Combo Meal Package")
                }
                if (isCombo) {
                    OutlinedTextField(
                        value = comboSummary,
                        onValueChange = { comboSummary = it },
                        label = { Text("Combo Items Included *") },
                        placeholder = { Text("e.g., Smash Burger + Fries + Passionfruit Cooler") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            val cat = selectedCategory
            Button(
                onClick = {
                    if (cat != null) {
                        val priceCents = ((priceText.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
                        onSave(
                            cat.categoryId,
                            cat.name,
                            name,
                            description,
                            priceCents,
                            sku,
                            taxExempt,
                            isAvailable,
                            isCombo,
                            comboSummary,
                            station.code,
                            imagePath
                        )
                    }
                },
                enabled = cat != null && name.isNotBlank() && sku.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) {
                Text("Save Product")
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
private fun ProductOptionsDialog(
    product: PosProduct,
    onSaveVariation: (String?, String, Long, Boolean) -> Unit,
    onDeleteVariation: (String) -> Unit,
    onSaveAddon: (String?, String, Long) -> Unit,
    onDeleteAddon: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var varName by remember { mutableStateOf("") }
    var varDeltaText by remember { mutableStateOf("0.00") }
    var varDefault by remember { mutableStateOf(false) }

    var addonName by remember { mutableStateOf("") }
    var addonPriceText by remember { mutableStateOf("1.50") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("${product.name} Options", color = CraveGoldBright) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Variations", style = MaterialTheme.typography.titleSmall, color = CraveGold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = varName,
                        onValueChange = { varName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = varDeltaText,
                        onValueChange = { varDeltaText = it },
                        label = { Text("+PKR") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = varDefault, onCheckedChange = { varDefault = it })
                        Text("Default", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            val cents = ((varDeltaText.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
                            onSaveVariation(null, varName, cents, varDefault)
                            varName = ""
                            varDeltaText = "0.00"
                            varDefault = false
                        },
                        enabled = varName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
                    ) {
                        Text("Add Variation")
                    }
                }
                product.variations.forEach { v ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${v.name} (+${centsToCurrency(v.priceDeltaCents)})${if (v.isDefault) " • Default" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onDeleteVariation(v.variationId) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete variation", tint = StatusCrimson)
                        }
                    }
                }

                HorizontalDivider(color = ObsidianBorder)

                Text("Add-ons", style = MaterialTheme.typography.titleSmall, color = CraveGold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = addonName,
                        onValueChange = { addonName = it },
                        label = { Text("Add-on Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = addonPriceText,
                        onValueChange = { addonPriceText = it },
                        label = { Text("PKR") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }
                Button(
                    onClick = {
                        val cents = ((addonPriceText.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
                        onSaveAddon(null, addonName, cents)
                        addonName = ""
                    },
                    enabled = addonName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Add Add-on")
                }
                product.addons.forEach { a ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${a.name} (+${centsToCurrency(a.priceCents)})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onDeleteAddon(a.addonId) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete add-on", tint = StatusCrimson)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = CraveGoldBright)
            }
        }
    )
}
