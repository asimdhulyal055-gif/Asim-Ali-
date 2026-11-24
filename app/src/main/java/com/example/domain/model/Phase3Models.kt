package com.example.domain.model

import kotlin.math.roundToLong

enum class KitchenStation(val code: String, val label: String) {
    ALL("ALL", "All Stations"),
    MAIN_KITCHEN("MAIN_KITCHEN", "Main Kitchen"),
    GRILL("GRILL", "Charcoal Grill"),
    FRY_PIZZA("FRY_PIZZA", "Fry & Wood-Fired"),
    BEVERAGES("BEVERAGES", "Bar & Beverages"),
    DESSERTS("DESSERTS", "Pastry & Desserts");

    companion object {
        fun fromCode(code: String): KitchenStation =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: MAIN_KITCHEN

        val assignableStations: List<KitchenStation>
            get() = listOf(MAIN_KITCHEN, GRILL, FRY_PIZZA, BEVERAGES, DESSERTS)
    }
}

enum class KotStatus(val code: String, val label: String) {
    NEW("NEW", "New"),
    PREPARING("PREPARING", "Preparing"),
    READY("READY", "Ready"),
    COMPLETED("COMPLETED", "Completed"),
    CANCELLED("CANCELLED", "Cancelled");

    fun nextStage(): KotStatus? = when (this) {
        NEW -> PREPARING
        PREPARING -> READY
        READY -> COMPLETED
        COMPLETED, CANCELLED -> null
    }

    companion object {
        fun fromCode(code: String): KotStatus =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: NEW
    }
}

data class KdsTicketSummary(
    val kotId: String,
    val orderId: String,
    val branchId: String,
    val kotNumber: String,
    val orderNumber: String,
    val orderType: OrderType,
    val tableNumber: String?,
    val priority: OrderPriority,
    val station: KitchenStation,
    val status: KotStatus,
    val reprintCount: Int,
    val createdAt: Long,
    val startedPreparingAt: Long?,
    val readyAt: Long?,
    val completedAt: Long?,
    val items: List<KotLineItem>
) {
    val totalItemCount: Int
        get() = items.sumOf { it.quantity }
}

data class KdsPerformanceSummary(
    val newCount: Int = 0,
    val preparingCount: Int = 0,
    val readyCount: Int = 0,
    val completedCount: Int = 0,
    val avgPrepMinutes: Int = 0
)

enum class InventoryItemType(val code: String, val label: String) {
    RAW_MATERIAL("RAW_MATERIAL", "Raw Material"),
    FINISHED_PRODUCT("FINISHED_PRODUCT", "Finished Product");

    companion object {
        fun fromCode(code: String): InventoryItemType =
            entries.firstOrNull { it.code == code } ?: RAW_MATERIAL
    }
}

enum class InventoryUnit(val symbol: String, val label: String) {
    KG("kg", "Kilogram (kg)"),
    GRAM("g", "Gram (g)"),
    LITER("L", "Liter (L)"),
    ML("ml", "Milliliter (ml)"),
    PCS("pcs", "Pieces (pcs)"),
    PORTION("portion", "Portion");

    companion object {
        fun fromSymbol(symbol: String): InventoryUnit =
            entries.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) } ?: PCS
    }
}

enum class StockMovementType(val code: String, val label: String) {
    RECEIVING("RECEIVING", "Stock Receiving"),
    SALE_DEDUCTION("SALE_DEDUCTION", "Auto Sale Deduction"),
    ADJUSTMENT("ADJUSTMENT", "Manual Adjustment"),
    WASTAGE("WASTAGE", "Wastage / Spoilage"),
    AUDIT_CORRECTION("AUDIT_CORRECTION", "Audit Variance");

    companion object {
        fun fromCode(code: String): StockMovementType =
            entries.firstOrNull { it.code == code } ?: ADJUSTMENT
    }
}

object UnitConverter {
    /**
     * Converts [quantity] expressed in [fromUnit] into [toUnit].
     * Supports metric mass (kg <-> g), volume (L <-> ml), or identical units.
     */
    fun convert(quantity: Double, fromUnit: String, toUnit: String): Double {
        val from = fromUnit.trim().lowercase()
        val to = toUnit.trim().lowercase()
        if (from == to) return quantity

        return when {
            from == "g" && to == "kg" -> quantity / 1000.0
            from == "kg" && to == "g" -> quantity * 1000.0
            from == "ml" && to == "l" -> quantity / 1000.0
            from == "l" && to == "ml" -> quantity * 1000.0
            else -> quantity
        }
    }

    /**
     * Calculates ingredient cost in cents where [inventoryUnitCostCents] is cost per 1 [inventoryUnit].
     */
    fun calculateIngredientCostCents(
        recipeQty: Double,
        recipeUnit: String,
        inventoryUnit: String,
        inventoryUnitCostCents: Long
    ): Long {
        val qtyInInventoryUnit = convert(recipeQty, recipeUnit, inventoryUnit)
        return (qtyInInventoryUnit * inventoryUnitCostCents).roundToLong().coerceAtLeast(0L)
    }
}

data class InventoryItemRecord(
    val itemId: String,
    val branchId: String,
    val name: String,
    val sku: String,
    val itemType: InventoryItemType,
    val unit: String,
    val currentQty: Double,
    val reorderLevel: Double,
    val unitCostCents: Long,
    val expiryDateMillis: Long?,
    val linkedProductId: String?,
    val updatedAt: Long,
    val isLowStock: Boolean,
    val isExpired: Boolean,
    val isExpiringSoon: Boolean
)

data class StockMovementRecord(
    val movementId: String,
    val branchId: String,
    val inventoryItemId: String,
    val itemName: String,
    val movementType: StockMovementType,
    val quantityDelta: Double,
    val unit: String,
    val referenceOrderId: String?,
    val reason: String,
    val actorName: String,
    val createdAt: Long
)

data class PhysicalAuditRecord(
    val auditId: String,
    val branchId: String,
    val inventoryItemId: String,
    val itemName: String,
    val expectedQty: Double,
    val countedQty: Double,
    val varianceQty: Double,
    val notes: String,
    val auditorName: String,
    val auditedAt: Long
)

data class RecipeIngredientLine(
    val id: String,
    val recipeId: String,
    val inventoryItemId: String,
    val ingredientName: String,
    val quantity: Double,
    val unit: String,
    val unitCostCents: Long,
    val lineCostCents: Long
)

data class RecipeVersionRecord(
    val versionId: String,
    val recipeId: String,
    val productId: String,
    val versionNumber: Int,
    val totalCostCents: Long,
    val ingredientsSnapshot: String,
    val notes: String,
    val editedBy: String,
    val editedAt: Long
)

data class RecipeDetail(
    val recipeId: String,
    val branchId: String,
    val productId: String,
    val productName: String,
    val productSku: String,
    val sellingPriceCents: Long,
    val version: Int,
    val notes: String,
    val totalCostCents: Long,
    val estimatedProfitCents: Long,
    val marginPercent: Double,
    val updatedBy: String,
    val updatedAt: Long,
    val ingredients: List<RecipeIngredientLine>,
    val versionHistory: List<RecipeVersionRecord>
)

data class RecipeIngredientDraft(
    val inventoryItemId: String,
    val quantity: Double,
    val unit: String
)
