package com.example.ui.recipes

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.domain.model.InventoryItemRecord
import com.example.domain.model.InventoryUnit
import com.example.domain.model.PosProduct
import com.example.domain.model.RecipeDetail
import com.example.domain.model.RecipeIngredientDraft
import com.example.domain.model.UnitConverter
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
import com.example.ui.viewmodel.RecipeUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeManagementScreen(
    session: UserSession,
    uiState: RecipeUiState,
    onOpenDrawer: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onToggleOnlyMissing: () -> Unit,
    onSaveRecipe: (productId: String, notes: String, ingredients: List<RecipeIngredientDraft>) -> Unit,
    onClearFeedback: () -> Unit
) {
    val canManage = session.capabilities.canManageRecipes
    val snackbarHostState = remember { SnackbarHostState() }

    var editingPair by remember { mutableStateOf<Pair<PosProduct, RecipeDetail?>?>(null) }
    var historyRecipe by remember { mutableStateOf<RecipeDetail?>(null) }

    LaunchedEffect(uiState.feedbackMessage) {
        val msg = uiState.feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    val recipeByProductId = remember(uiState.recipes) {
        uiState.recipes.associateBy { it.productId }
    }
    val avgMargin = remember(uiState.recipes) {
        if (uiState.recipes.isEmpty()) 0.0 else uiState.recipes.map { it.marginPercent }.average()
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
                            text = "RECIPE & COSTING",
                            style = MaterialTheme.typography.titleLarge,
                            color = CraveGoldBright
                        )
                        Text(
                            text = if (canManage) "${session.branchName} • Costing & Version History" else "${session.branchName} • Chef Spec View",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (!canManage) {
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
            // KPI summary strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecipeSummaryChip("Menu Items", uiState.products.size.toString(), CraveGoldBright)
                RecipeSummaryChip("Configured Recipes", uiState.recipes.size.toString(), StatusEmerald)
                RecipeSummaryChip(
                    "Avg Margin",
                    String.format(Locale.US, "%.1f%%", avgMargin),
                    if (avgMargin >= 60.0) StatusEmerald else StatusAmber
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChanged,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search product or SKU...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("recipe_search_input")
                )
                FilterChip(
                    selected = uiState.onlyMissingRecipes,
                    onClick = onToggleOnlyMissing,
                    label = { Text("Missing Only") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CraveGold,
                        selectedLabelColor = OnCraveGold
                    )
                )
            }

            HorizontalDivider(color = ObsidianBorder, modifier = Modifier.padding(top = 8.dp))

            if (uiState.products.isEmpty()) {
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
                            imageVector = Icons.Filled.MenuBook,
                            contentDescription = null,
                            tint = CraveGold.copy(alpha = 0.6f),
                            modifier = Modifier.size(44.dp)
                        )
                        Text("No matching products for recipes", style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.products, key = { it.productId }) { product ->
                        val recipe = recipeByProductId[product.productId]
                        ProductRecipeCard(
                            product = product,
                            recipe = recipe,
                            canManage = canManage,
                            onEditRecipe = { editingPair = product to recipe },
                            onViewHistory = { if (recipe != null) historyRecipe = recipe }
                        )
                    }
                }
            }
        }
    }

    val currentPair = editingPair
    if (currentPair != null && canManage) {
        RecipeEditorDialog(
            product = currentPair.first,
            existingRecipe = currentPair.second,
            inventoryItems = uiState.inventoryItems,
            onSave = { notes, drafts ->
                onSaveRecipe(currentPair.first.productId, notes, drafts)
                editingPair = null
            },
            onDismiss = { editingPair = null }
        )
    }

    val history = historyRecipe
    if (history != null) {
        RecipeVersionHistoryDialog(
            recipe = history,
            onDismiss = { historyRecipe = null }
        )
    }
}

@Composable
private fun RecipeSummaryChip(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = ObsidianSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun ProductRecipeCard(
    product: PosProduct,
    recipe: RecipeDetail?,
    canManage: Boolean,
    onEditRecipe: () -> Unit,
    onViewHistory: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, if (recipe != null) ObsidianBorder else StatusAmber.copy(alpha = 0.6f)),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recipe_card_${product.productId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${product.sku} • Sell Price ${centsToCurrency(product.basePriceCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CraveGold
                    )
                }

                if (recipe != null) {
                    StatusPill("SPEC v${recipe.version}", CraveGoldContainer, CraveGoldBright)
                } else {
                    StatusPill("NO RECIPE", StatusAmberBg, StatusAmber)
                }
            }

            if (recipe != null) {
                // Costing & Profit Margin Row
                val marginColor = when {
                    recipe.marginPercent >= 60.0 -> StatusEmerald
                    recipe.marginPercent >= 40.0 -> CraveGoldBright
                    else -> StatusCrimson
                }
                Surface(
                    color = ObsidianSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Recipe Cost", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(centsToCurrency(recipe.totalCostCents), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Est. Profit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(centsToCurrency(recipe.estimatedProfitCents), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = marginColor)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Margin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = String.format(Locale.US, "%.1f%%", recipe.marginPercent),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = marginColor
                            )
                        }
                    }
                }

                // Ingredients breakdown
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ingredients (${recipe.ingredients.size})", style = MaterialTheme.typography.labelMedium, color = CraveGold)
                    recipe.ingredients.forEach { ing ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "• ${ing.quantity} ${ing.unit} ${ing.ingredientName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = centsToCurrency(ing.lineCostCents),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (recipe.notes.isNotBlank()) {
                    Text(
                        text = "Prep Notes: ${recipe.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Link inventory ingredients to compute dish cost, profit margin, and enable automatic stock deductions on POS checkout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (recipe != null) {
                    OutlinedButton(
                        onClick = onViewHistory,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("recipe_history_${product.productId}")
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("History (${recipe.versionHistory.size})")
                    }
                }
                if (canManage) {
                    Button(
                        onClick = onEditRecipe,
                        colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("recipe_edit_${product.productId}")
                    ) {
                        Icon(
                            imageVector = if (recipe == null) Icons.Filled.Add else Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (recipe == null) "Link Recipe" else "Edit Recipe")
                    }
                }
            }
        }
    }
}

private data class EditableIngredientRow(
    val inventoryItemId: String,
    val quantityText: String,
    val unit: String
)

@Composable
private fun RecipeEditorDialog(
    product: PosProduct,
    existingRecipe: RecipeDetail?,
    inventoryItems: List<InventoryItemRecord>,
    onSave: (String, List<RecipeIngredientDraft>) -> Unit,
    onDismiss: () -> Unit
) {
    var notes by remember { mutableStateOf(existingRecipe?.notes.orEmpty()) }
    val rows = remember {
        mutableStateListOf<EditableIngredientRow>().apply {
            if (existingRecipe != null && existingRecipe.ingredients.isNotEmpty()) {
                existingRecipe.ingredients.forEach {
                    add(EditableIngredientRow(it.inventoryItemId, it.quantity.toString(), it.unit))
                }
            } else if (inventoryItems.isNotEmpty()) {
                val first = inventoryItems.first()
                add(EditableIngredientRow(first.itemId, "1.0", first.unit))
            }
        }
    }

    val invById = remember(inventoryItems) { inventoryItems.associateBy { it.itemId } }
    val liveTotalCostCents = rows.sumOf { row ->
        val inv = invById[row.inventoryItemId] ?: return@sumOf 0L
        val qty = row.quantityText.toDoubleOrNull() ?: 0.0
        UnitConverter.calculateIngredientCostCents(qty, row.unit, inv.unit, inv.unitCostCents)
    }
    val liveProfitCents = product.basePriceCents - liveTotalCostCents
    val liveMargin = if (product.basePriceCents > 0L) (liveProfitCents.toDouble() / product.basePriceCents.toDouble()) * 100.0 else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Column {
                Text(if (existingRecipe == null) "Link Recipe" else "Edit Recipe v${existingRecipe.version}", color = CraveGoldBright)
                Text(product.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = ObsidianSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Est. Cost", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(centsToCurrency(liveTotalCostCents), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Sell", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(centsToCurrency(product.basePriceCents), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Margin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = String.format(Locale.US, "%.1f%%", liveMargin),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (liveMargin >= 50.0) StatusEmerald else StatusAmber
                            )
                        }
                    }
                }

                if (inventoryItems.isEmpty()) {
                    Text(
                        text = "No inventory items available. Add raw materials in Inventory Management first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusCrimson
                    )
                } else {
                    rows.forEachIndexed { index, row ->
                        val selectedInv = invById[row.inventoryItemId] ?: inventoryItems.first()
                        Surface(
                            color = ObsidianSurfaceElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Ingredient #${index + 1}: ${selectedInv.name}", style = MaterialTheme.typography.labelMedium, color = CraveGold)
                                    if (rows.size > 1) {
                                        IconButton(onClick = { rows.removeAt(index) }) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove ingredient", tint = StatusCrimson)
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    inventoryItems.forEach { item ->
                                        FilterChip(
                                            selected = item.itemId == row.inventoryItemId,
                                            onClick = {
                                                rows[index] = row.copy(inventoryItemId = item.itemId, unit = item.unit)
                                            },
                                            label = { Text(item.name) }
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = row.quantityText,
                                        onValueChange = { rows[index] = row.copy(quantityText = it) },
                                        label = { Text("Qty") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .weight(1.4f)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        InventoryUnit.entries.forEach { u ->
                                            FilterChip(
                                                selected = row.unit.equals(u.symbol, ignoreCase = true),
                                                onClick = { rows[index] = row.copy(unit = u.symbol) },
                                                label = { Text(u.symbol) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val first = inventoryItems.first()
                            rows.add(EditableIngredientRow(first.itemId, "1.0", first.unit))
                        },
                        border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Ingredient")
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Chef Prep Notes / Change Summary") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val drafts = rows.mapNotNull { row ->
                        val qty = row.quantityText.toDoubleOrNull()
                        if (qty != null && qty > 0.0) {
                            RecipeIngredientDraft(row.inventoryItemId, qty, row.unit)
                        } else null
                    }
                    onSave(notes, drafts)
                },
                enabled = rows.isNotEmpty() && rows.all { (it.quantityText.toDoubleOrNull() ?: 0.0) > 0.0 },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) {
                Text("Save Recipe")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RecipeVersionHistoryDialog(
    recipe: RecipeDetail,
    onDismiss: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Column {
                Text("Recipe Version History", color = CraveGoldBright)
                Text(recipe.productName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (recipe.versionHistory.isEmpty()) {
                    Text("No historical revisions recorded yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    recipe.versionHistory.forEach { ver ->
                        Surface(
                            color = ObsidianSurfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Version ${ver.versionNumber}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CraveGoldBright)
                                    Text(centsToCurrency(ver.totalCostCents), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "${ver.editedBy} • ${formatter.format(Date(ver.editedAt))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = ver.ingredientsSnapshot,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (ver.notes.isNotBlank()) {
                                    Text(
                                        text = "Notes: ${ver.notes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CraveGold
                                    )
                                }
                            }
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
