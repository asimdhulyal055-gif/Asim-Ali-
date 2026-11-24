package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.room.withTransaction
import com.example.data.local.AaCraveDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.InventoryItemEntity
import com.example.data.local.LowStockAlertEntity
import com.example.data.local.OrderItemEntity
import com.example.data.local.PhysicalAuditEntity
import com.example.data.local.ProductAddonEntity
import com.example.data.local.ProductCategoryEntity
import com.example.data.local.ProductEntity
import com.example.data.local.ProductVariationEntity
import com.example.data.local.RecipeEntity
import com.example.data.local.RecipeIngredientEntity
import com.example.data.local.RecipeVersionEntity
import com.example.data.local.StockMovementEntity
import com.example.data.local.SyncQueueEntity
import com.example.domain.model.InventoryItemRecord
import com.example.domain.model.InventoryItemType
import com.example.domain.model.KdsPerformanceSummary
import com.example.domain.model.KdsTicketSummary
import com.example.domain.model.KitchenStation
import com.example.domain.model.KotLineItem
import com.example.domain.model.KotStatus
import com.example.domain.model.OrderPriority
import com.example.domain.model.OrderStatus
import com.example.domain.model.OrderType
import com.example.domain.model.PhysicalAuditRecord
import com.example.domain.model.PosProduct
import com.example.domain.model.ProductAddon
import com.example.domain.model.ProductCategory
import com.example.domain.model.ProductVariation
import com.example.domain.model.RecipeDetail
import com.example.domain.model.RecipeIngredientDraft
import com.example.domain.model.RecipeIngredientLine
import com.example.domain.model.RecipeVersionRecord
import com.example.domain.model.StockMovementRecord
import com.example.domain.model.StockMovementType
import com.example.domain.model.UnitConverter
import com.example.domain.model.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private fun roundQty(value: Double): Double = kotlin.math.round(value * 10_000.0) / 10_000.0

/**
 * Shared atomic, idempotent inventory deduction & low-stock alert sync helper.
 * Must be called inside a Room database.withTransaction block.
 */
internal object InventorySyncHelper {

    suspend fun deductForOrderInTransaction(
        database: AaCraveDatabase,
        branchId: String,
        orderId: String,
        orderNumber: String,
        items: List<OrderItemEntity>,
        actorName: String,
        now: Long
    ): Int {
        val recipeDao = database.recipeDao()
        val inventoryDao = database.inventoryDao()
        var appliedDeductions = 0

        for (line in items) {
            val recipe = recipeDao.getRecipeForProduct(line.productId)
            val deductedItemIdsForLine = mutableSetOf<String>()

            if (recipe != null) {
                val ingredients = recipeDao.getIngredientsForRecipe(recipe.recipeId)
                for (ing in ingredients) {
                    val invItem = inventoryDao.getItemById(ing.inventoryItemId) ?: continue
                    val qtyToDeduct = roundQty(
                        UnitConverter.convert(
                            quantity = ing.quantity * line.quantity,
                            fromUnit = ing.unit,
                            toUnit = invItem.unit
                        )
                    )
                    if (qtyToDeduct <= 0.0) continue

                    val deductionKey = "sale_${orderId}_${line.itemId}_ing_${invItem.itemId}"
                    val movement = StockMovementEntity(
                        movementId = UUID.randomUUID().toString(),
                        branchId = branchId,
                        inventoryItemId = invItem.itemId,
                        itemName = invItem.name,
                        movementType = StockMovementType.SALE_DEDUCTION.code,
                        quantityDelta = -qtyToDeduct,
                        unit = invItem.unit,
                        referenceOrderId = orderId,
                        deductionKey = deductionKey,
                        reason = "Auto deduction for $orderNumber (${line.quantity}x ${line.productName})",
                        actorName = actorName,
                        createdAt = now
                    )
                    val insertedRowId = inventoryDao.insertMovementIfNew(movement)
                    if (insertedRowId > 0L) {
                        val updatedQty = roundQty((invItem.currentQty - qtyToDeduct).coerceAtLeast(0.0))
                        inventoryDao.updateStockQuantity(
                            itemId = invItem.itemId,
                            newQty = updatedQty,
                            expiryMillis = null,
                            updatedAt = now
                        )
                        deductedItemIdsForLine += invItem.itemId
                        appliedDeductions++
                    }
                }
            }

            // Also check linked finished product inventory if not already deducted via recipe
            val finishedItem = inventoryDao.getItemByLinkedProductId(line.productId)
            if (finishedItem != null && finishedItem.itemId !in deductedItemIdsForLine) {
                val qtyToDeduct = line.quantity.toDouble()
                val deductionKey = "sale_${orderId}_${line.itemId}_fp_${finishedItem.itemId}"
                val movement = StockMovementEntity(
                    movementId = UUID.randomUUID().toString(),
                    branchId = branchId,
                    inventoryItemId = finishedItem.itemId,
                    itemName = finishedItem.name,
                    movementType = StockMovementType.SALE_DEDUCTION.code,
                    quantityDelta = -qtyToDeduct,
                    unit = finishedItem.unit,
                    referenceOrderId = orderId,
                    deductionKey = deductionKey,
                    reason = "Finished stock sale deduction for $orderNumber (${line.quantity}x ${line.productName})",
                    actorName = actorName,
                    createdAt = now
                )
                val insertedRowId = inventoryDao.insertMovementIfNew(movement)
                if (insertedRowId > 0L) {
                    val updatedQty = roundQty((finishedItem.currentQty - qtyToDeduct).coerceAtLeast(0.0))
                    inventoryDao.updateStockQuantity(
                        itemId = finishedItem.itemId,
                        newQty = updatedQty,
                        expiryMillis = null,
                        updatedAt = now
                    )
                    appliedDeductions++
                }
            }
        }

        if (appliedDeductions > 0) {
            refreshLowStockAlertsInTransaction(database, branchId, now)
        }
        return appliedDeductions
    }

    suspend fun refreshLowStockAlertsInTransaction(
        database: AaCraveDatabase,
        branchId: String,
        now: Long = System.currentTimeMillis()
    ) {
        val inventoryDao = database.inventoryDao()
        val dashboardDao = database.dashboardDao()

        val lowItems = inventoryDao.getLowStockItems(branchId)
        inventoryDao.clearLowStockAlertsForBranch(branchId)
        if (lowItems.isNotEmpty()) {
            inventoryDao.upsertLowStockAlerts(
                lowItems.map { item ->
                    LowStockAlertEntity(
                        id = "alert_${branchId}_${item.itemId}",
                        branchId = branchId,
                        ingredientName = item.name,
                        currentStock = item.currentQty,
                        reorderLevel = item.reorderLevel,
                        unit = item.unit,
                        isSampleData = false
                    )
                }
            )
        }

        val currentSnap = dashboardDao.observeSnapshot(branchId).first()
        if (currentSnap != null) {
            dashboardDao.upsertSnapshots(
                listOf(
                    currentSnap.copy(
                        lowStockAlerts = lowItems.size,
                        updatedAt = now
                    )
                )
            )
        }
    }
}

class KdsRepository(private val database: AaCraveDatabase) {

    private val posDao = database.posDao()
    private val dashboardDao = database.dashboardDao()
    private val auditLogDao = database.auditLogDao()
    private val syncQueueDao = database.syncQueueDao()

    fun observeKdsTickets(branchId: String): Flow<List<KdsTicketSummary>> =
        combine(
            posDao.observeKotTickets(branchId),
            posDao.observeAllOrderItems(),
            posDao.observeProducts(branchId)
        ) { kots, allOrderItems, products ->
            val itemsByOrder = allOrderItems.groupBy { it.orderId }
            val kotsByOrder = kots.groupBy { it.orderId }
            val stationByProductId = products.associate { it.productId to it.kitchenStation }

            kots.map { kot ->
                val siblingKots = kotsByOrder[kot.orderId].orEmpty()
                val rawOrderItems = itemsByOrder[kot.orderId].orEmpty()
                val scopedItems = if (siblingKots.size <= 1) {
                    rawOrderItems
                } else {
                    val stationMatches = rawOrderItems.filter { line ->
                        (stationByProductId[line.productId] ?: "MAIN_KITCHEN") == kot.station
                    }
                    stationMatches.ifEmpty { rawOrderItems }
                }

                KdsTicketSummary(
                    kotId = kot.kotId,
                    orderId = kot.orderId,
                    branchId = kot.branchId,
                    kotNumber = kot.kotNumber,
                    orderNumber = kot.orderNumber,
                    orderType = OrderType.entries.firstOrNull { it.name == kot.orderType } ?: OrderType.DINE_IN,
                    tableNumber = kot.tableNumber,
                    priority = OrderPriority.entries.firstOrNull { it.name == kot.priority } ?: OrderPriority.NORMAL,
                    station = KitchenStation.fromCode(kot.station),
                    status = KotStatus.fromCode(kot.status),
                    reprintCount = kot.reprintCount,
                    createdAt = kot.createdAt,
                    startedPreparingAt = kot.startedPreparingAt,
                    readyAt = kot.readyAt,
                    completedAt = kot.completedAt,
                    items = scopedItems.map { line ->
                        KotLineItem(
                            productName = line.productName,
                            variationName = line.variationName,
                            quantity = line.quantity,
                            addonsSummary = line.addonsSummary,
                            specialInstructions = line.specialInstructions
                        )
                    }
                )
            }
        }

    fun computePerformanceSummary(tickets: List<KdsTicketSummary>): KdsPerformanceSummary {
        val completedDurationsMin = tickets
            .filter { it.status == KotStatus.COMPLETED && it.completedAt != null }
            .map { ((it.completedAt!! - it.createdAt).coerceAtLeast(0L) / 60_000.0).roundToInt() }

        return KdsPerformanceSummary(
            newCount = tickets.count { it.status == KotStatus.NEW },
            preparingCount = tickets.count { it.status == KotStatus.PREPARING },
            readyCount = tickets.count { it.status == KotStatus.READY },
            completedCount = tickets.count { it.status == KotStatus.COMPLETED },
            avgPrepMinutes = if (completedDurationsMin.isEmpty()) 0 else completedDurationsMin.average().roundToInt()
        )
    }

    suspend fun updateKotStatus(
        session: UserSession,
        kotId: String,
        targetStatus: KotStatus
    ): Result<Unit> {
        if (!session.capabilities.canOperateKds) {
            return Result.failure(SecurityException("Role ${session.role.displayName} is not authorized to update Kitchen Display tickets."))
        }
        return try {
            database.withTransaction {
                val existing = posDao.getKotById(kotId)
                    ?: throw IllegalArgumentException("KOT ticket not found.")
                if (existing.status == KotStatus.CANCELLED.code) {
                    throw IllegalStateException("Cancelled KOT tickets cannot change stage.")
                }

                val now = System.currentTimeMillis()
                val startedAt = if (targetStatus == KotStatus.PREPARING || targetStatus == KotStatus.READY || targetStatus == KotStatus.COMPLETED) {
                    existing.startedPreparingAt ?: now
                } else {
                    null
                }
                val readyAt = if (targetStatus == KotStatus.READY || targetStatus == KotStatus.COMPLETED) {
                    existing.readyAt ?: now
                } else {
                    null
                }
                val completedAt = if (targetStatus == KotStatus.COMPLETED) {
                    existing.completedAt ?: now
                } else {
                    null
                }

                posDao.updateKotStage(
                    kotId = kotId,
                    status = targetStatus.code,
                    startedAt = startedAt,
                    readyAt = readyAt,
                    completedAt = completedAt
                )

                // Synchronize status back to the original PosOrderEntity cleanly
                val allOrderKots = posDao.getKotsByOrderId(existing.orderId)
                val syncedOrderStatus = deriveOrderStatusFromKots(allOrderKots.map {
                    if (it.kotId == kotId) targetStatus else KotStatus.fromCode(it.status)
                })
                posDao.syncOrderStatusFromKds(existing.orderId, syncedOrderStatus.name)

                // Update pendingKitchenOrders count in dashboard snapshot
                val activeBranchKots = posDao.observeKotTickets(existing.branchId).first()
                val pendingCount = activeBranchKots.count {
                    it.status == KotStatus.NEW.code || it.status == KotStatus.PREPARING.code || it.status == KotStatus.READY.code
                }
                val snap = dashboardDao.observeSnapshot(existing.branchId).first()
                if (snap != null) {
                    dashboardDao.upsertSnapshots(
                        listOf(snap.copy(pendingKitchenOrders = pendingCount, updatedAt = now))
                    )
                }

                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_kds_${kotId}_${targetStatus.code}_$now",
                        branchId = existing.branchId,
                        entityType = "KDS_STATUS",
                        entityId = existing.orderId,
                        payloadJson = """{"kotId":"$kotId","kotNumber":"${existing.kotNumber}","status":"${targetStatus.code}","orderStatus":"${syncedOrderStatus.name}"}""",
                        status = "PENDING",
                        lastError = "Realtime Database in Locked Mode — queued safely in Room"
                    )
                )

                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = existing.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "KDS_STATUS_UPDATE",
                        details = "${existing.kotNumber} (${existing.orderNumber}) moved to ${targetStatus.label}; order synced to ${syncedOrderStatus.label}"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deriveOrderStatusFromKots(statuses: List<KotStatus>): OrderStatus {
        if (statuses.isEmpty()) return OrderStatus.CONFIRMED
        val active = statuses.filter { it != KotStatus.CANCELLED }
        if (active.isEmpty()) return OrderStatus.CANCELLED
        return when {
            active.all { it == KotStatus.COMPLETED } -> OrderStatus.COMPLETED
            active.all { it == KotStatus.READY || it == KotStatus.COMPLETED } -> OrderStatus.READY
            active.any { it == KotStatus.PREPARING || it == KotStatus.READY || it == KotStatus.COMPLETED } -> OrderStatus.PREPARING
            else -> OrderStatus.CONFIRMED
        }
    }
}

class MenuRepository(
    private val context: Context,
    private val database: AaCraveDatabase
) {
    private val posDao = database.posDao()
    private val auditLogDao = database.auditLogDao()
    private val syncQueueDao = database.syncQueueDao()

    fun observeCategories(branchId: String): Flow<List<ProductCategory>> =
        posDao.observeCategories(branchId).map { list ->
            list.map { ProductCategory(it.categoryId, it.branchId, it.name, it.sortOrder) }
        }

    /**
     * Menu management observes ALL branch products (including archived and unavailable ones).
     */
    fun observeAllMenuProducts(branchId: String): Flow<List<PosProduct>> =
        combine(
            posDao.observeProducts(branchId),
            posDao.observeAllVariations(),
            posDao.observeAllAddons()
        ) { products, variations, addons ->
            val varsByProduct = variations.groupBy { it.productId }
            val addonsByProduct = addons.groupBy { it.productId }
            products.map { p ->
                PosProduct(
                    productId = p.productId,
                    branchId = p.branchId,
                    categoryId = p.categoryId,
                    name = p.name,
                    description = p.description,
                    basePriceCents = p.basePriceCents,
                    sku = p.sku,
                    categoryTag = p.categoryTag,
                    isAvailable = p.isAvailable,
                    taxExempt = p.taxExempt,
                    variations = varsByProduct[p.productId].orEmpty().map { v ->
                        ProductVariation(v.variationId, v.productId, v.name, v.priceDeltaCents, v.isDefault)
                    },
                    addons = addonsByProduct[p.productId].orEmpty().map { a ->
                        ProductAddon(a.addonId, a.productId, a.name, a.priceCents)
                    },
                    isArchived = p.isArchived,
                    isCombo = p.isCombo,
                    comboSummary = p.comboSummary,
                    imagePath = p.imagePath,
                    kitchenStation = p.kitchenStation
                )
            }
        }

    suspend fun saveCategory(
        session: UserSession,
        categoryId: String?,
        name: String,
        sortOrder: Int
    ): Result<Unit> {
        if (!session.capabilities.canManageMenu) {
            return Result.failure(SecurityException("Only authorized managers or admins can edit menu categories."))
        }
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Category name cannot be empty."))
        }
        return try {
            database.withTransaction {
                val id = categoryId ?: "cat_${UUID.randomUUID().toString().take(8)}"
                posDao.upsertCategory(
                    ProductCategoryEntity(
                        categoryId = id,
                        branchId = session.branchId,
                        name = cleanName,
                        sortOrder = sortOrder.coerceAtLeast(1),
                        isActive = true
                    )
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = if (categoryId == null) "CATEGORY_CREATED" else "CATEGORY_UPDATED",
                        details = "Category '$cleanName' (sort #$sortOrder)"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(session: UserSession, categoryId: String, categoryName: String): Result<Unit> {
        if (!session.capabilities.canManageMenu) {
            return Result.failure(SecurityException("Only authorized managers or admins can delete categories."))
        }
        return try {
            database.withTransaction {
                val activeCount = posDao.countActiveProductsInCategory(categoryId)
                if (activeCount > 0) {
                    throw IllegalStateException("Cannot delete '$categoryName' while $activeCount active products belong to it. Archive or reassign those products first.")
                }
                posDao.deleteCategory(categoryId)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "CATEGORY_DELETED",
                        details = "Deleted empty category '$categoryName'"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveProduct(
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
    ): Result<String> {
        if (!session.capabilities.canManageMenu) {
            return Result.failure(SecurityException("Only authorized managers or admins can create or edit products."))
        }
        val cleanName = name.trim()
        val cleanSku = sku.trim().uppercase()
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Product name is required."))
        if (cleanSku.isBlank()) return Result.failure(IllegalArgumentException("SKU is required."))
        if (basePriceCents < 0L) return Result.failure(IllegalArgumentException("Price cannot be negative."))
        if (isCombo && comboSummary.isBlank()) {
            return Result.failure(IllegalArgumentException("Specify included combo items for combo meals."))
        }

        return try {
            val savedId = database.withTransaction {
                val id = productId ?: "prod_${UUID.randomUUID().toString().take(8)}"
                posDao.upsertProduct(
                    ProductEntity(
                        productId = id,
                        branchId = session.branchId,
                        categoryId = categoryId,
                        name = cleanName,
                        description = description.trim(),
                        basePriceCents = basePriceCents,
                        sku = cleanSku,
                        categoryTag = categoryName,
                        isAvailable = isAvailable,
                        taxExempt = taxExempt,
                        isArchived = false,
                        isCombo = isCombo,
                        comboSummary = if (isCombo) comboSummary.trim() else "",
                        imagePath = imagePath,
                        kitchenStation = KitchenStation.fromCode(kitchenStation).code
                    )
                )
                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_prod_${id}_${System.currentTimeMillis()}",
                        branchId = session.branchId,
                        entityType = "MENU_PRODUCT",
                        entityId = id,
                        payloadJson = """{"productId":"$id","name":"$cleanName","priceCents":$basePriceCents}""",
                        status = "PENDING",
                        lastError = "Realtime Database in Locked Mode — queued safely in Room"
                    )
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = if (productId == null) "PRODUCT_CREATED" else "PRODUCT_UPDATED",
                        details = "Menu item '$cleanName' ($cleanSku) @ ${basePriceCents}c [${kitchenStation}]"
                    )
                )
                id
            }
            Result.success(savedId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleProductAvailability(session: UserSession, product: PosProduct, available: Boolean): Result<Unit> {
        if (!session.capabilities.canManageMenu) {
            return Result.failure(SecurityException("Only authorized managers can toggle menu availability."))
        }
        return try {
            database.withTransaction {
                posDao.setProductAvailability(product.productId, available)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "PRODUCT_AVAILABILITY",
                        details = "${product.name} marked ${if (available) "Available" else "Unavailable"}"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setProductArchived(session: UserSession, product: PosProduct, archived: Boolean): Result<Unit> {
        if (!session.capabilities.canManageMenu) {
            return Result.failure(SecurityException("Only authorized managers can archive products."))
        }
        return try {
            database.withTransaction {
                posDao.setProductArchiveState(
                    productId = product.productId,
                    archived = archived,
                    available = if (archived) false else true
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = if (archived) "PRODUCT_ARCHIVED" else "PRODUCT_RESTORED",
                        details = "${product.name} (${product.sku})"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveVariation(
        session: UserSession,
        variationId: String?,
        productId: String,
        name: String,
        priceDeltaCents: Long,
        isDefault: Boolean
    ): Result<Unit> {
        if (!session.capabilities.canManageMenu) {
            return Result.failure(SecurityException("Only authorized managers can edit variations."))
        }
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Variation name is required."))
        return try {
            database.withTransaction {
                posDao.upsertVariations(
                    listOf(
                        ProductVariationEntity(
                            variationId = variationId ?: "var_${UUID.randomUUID().toString().take(8)}",
                            productId = productId,
                            name = name.trim(),
                            priceDeltaCents = priceDeltaCents,
                            isDefault = isDefault
                        )
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteVariation(session: UserSession, variationId: String): Result<Unit> {
        if (!session.capabilities.canManageMenu) {
            return Result.failure(SecurityException("Only authorized managers can remove variations."))
        }
        return try {
            posDao.deleteVariation(variationId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveAddon(
        session: UserSession,
        addonId: String?,
        productId: String,
        name: String,
        priceCents: Long
    ): Result<Unit> {
        if (!session.capabilities.canManageMenu) {
            return Result.failure(SecurityException("Only authorized managers can edit add-ons."))
        }
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Add-on name is required."))
        if (priceCents < 0L) return Result.failure(IllegalArgumentException("Add-on price cannot be negative."))
        return try {
            database.withTransaction {
                posDao.upsertAddons(
                    listOf(
                        ProductAddonEntity(
                            addonId = addonId ?: "add_${UUID.randomUUID().toString().take(8)}",
                            productId = productId,
                            name = name.trim(),
                            priceCents = priceCents
                        )
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAddon(session: UserSession, addonId: String): Result<Unit> {
        if (!session.capabilities.canManageMenu) {
            return Result.failure(SecurityException("Only authorized managers can remove add-ons."))
        }
        return try {
            posDao.deleteAddon(addonId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun copyProductImageToAppStorage(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val mime = context.contentResolver.getType(uri) ?: ""
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.lowercase() ?: "jpg"
            val allowed = setOf("png", "jpg", "jpeg", "webp")
            if (ext !in allowed && !mime.startsWith("image/")) {
                return@withContext Result.failure(IllegalArgumentException("Select a valid PNG, JPG, or WEBP product photo."))
            }
            val dir = File(context.filesDir, "product_images").apply { mkdirs() }
            val dest = File(dir, "prod_${UUID.randomUUID().toString().take(10)}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(IllegalStateException("Unable to read selected image."))
            Result.success(dest.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class RecipeRepository(private val database: AaCraveDatabase) {

    private val recipeDao = database.recipeDao()
    private val inventoryDao = database.inventoryDao()
    private val posDao = database.posDao()
    private val auditLogDao = database.auditLogDao()
    private val syncQueueDao = database.syncQueueDao()

    fun observeRecipes(branchId: String): Flow<List<RecipeDetail>> =
        combine(
            posDao.observeProducts(branchId),
            recipeDao.observeRecipes(branchId),
            recipeDao.observeAllRecipeIngredients(),
            recipeDao.observeAllRecipeVersions(),
            inventoryDao.observeInventoryItems(branchId)
        ) { products, recipes, allIngredients, allVersions, inventory ->
            val productById = products.associateBy { it.productId }
            val invById = inventory.associateBy { it.itemId }
            val ingredientsByRecipe = allIngredients.groupBy { it.recipeId }
            val versionsByRecipe = allVersions.groupBy { it.recipeId }

            recipes.mapNotNull { rec ->
                val prod = productById[rec.productId] ?: return@mapNotNull null
                val rawIngredients = ingredientsByRecipe[rec.recipeId].orEmpty()
                val mappedLines = rawIngredients.map { ing ->
                    val liveInv = invById[ing.inventoryItemId]
                    val invUnit = liveInv?.unit ?: ing.unit
                    val unitCost = liveInv?.unitCostCents ?: ing.unitCostCents
                    val lineCost = UnitConverter.calculateIngredientCostCents(
                        recipeQty = ing.quantity,
                        recipeUnit = ing.unit,
                        inventoryUnit = invUnit,
                        inventoryUnitCostCents = unitCost
                    )
                    RecipeIngredientLine(
                        id = ing.id,
                        recipeId = ing.recipeId,
                        inventoryItemId = ing.inventoryItemId,
                        ingredientName = liveInv?.name ?: ing.ingredientName,
                        quantity = ing.quantity,
                        unit = ing.unit,
                        unitCostCents = unitCost,
                        lineCostCents = lineCost
                    )
                }

                val computedCost = if (mappedLines.isNotEmpty()) mappedLines.sumOf { it.lineCostCents } else rec.totalCostCents
                val profit = prod.basePriceCents - computedCost
                val marginPct = if (prod.basePriceCents > 0L) {
                    (profit.toDouble() / prod.basePriceCents.toDouble()) * 100.0
                } else {
                    0.0
                }

                RecipeDetail(
                    recipeId = rec.recipeId,
                    branchId = rec.branchId,
                    productId = rec.productId,
                    productName = prod.name,
                    productSku = prod.sku,
                    sellingPriceCents = prod.basePriceCents,
                    version = rec.version,
                    notes = rec.notes,
                    totalCostCents = computedCost,
                    estimatedProfitCents = profit,
                    marginPercent = marginPct,
                    updatedBy = rec.updatedBy,
                    updatedAt = rec.updatedAt,
                    ingredients = mappedLines,
                    versionHistory = versionsByRecipe[rec.recipeId].orEmpty().map { v ->
                        RecipeVersionRecord(
                            versionId = v.versionId,
                            recipeId = v.recipeId,
                            productId = v.productId,
                            versionNumber = v.versionNumber,
                            totalCostCents = v.totalCostCents,
                            ingredientsSnapshot = v.ingredientsSnapshot,
                            notes = v.notes,
                            editedBy = v.editedBy,
                            editedAt = v.editedAt
                        )
                    }
                )
            }
        }

    suspend fun saveRecipe(
        session: UserSession,
        productId: String,
        notes: String,
        draftIngredients: List<RecipeIngredientDraft>
    ): Result<Unit> {
        if (!session.capabilities.canManageRecipes) {
            return Result.failure(SecurityException("Only authorized managers or inventory staff can edit recipes."))
        }
        if (draftIngredients.isEmpty()) {
            return Result.failure(IllegalArgumentException("Add at least one ingredient to save a recipe."))
        }
        if (draftIngredients.any { it.quantity <= 0.0 }) {
            return Result.failure(IllegalArgumentException("All ingredient quantities must be greater than zero."))
        }

        return try {
            database.withTransaction {
                val now = System.currentTimeMillis()
                val existing = recipeDao.getRecipeForProduct(productId)
                val recipeId = existing?.recipeId ?: "rec_${UUID.randomUUID().toString().take(8)}"
                val nextVersion = (existing?.version ?: 0) + 1

                val entities = mutableListOf<RecipeIngredientEntity>()
                val snapshotParts = mutableListOf<String>()
                var totalCostCents = 0L

                for (draft in draftIngredients) {
                    val invItem = inventoryDao.getItemById(draft.inventoryItemId)
                        ?: throw IllegalArgumentException("Referenced inventory item not found.")
                    val lineCost = UnitConverter.calculateIngredientCostCents(
                        recipeQty = draft.quantity,
                        recipeUnit = draft.unit,
                        inventoryUnit = invItem.unit,
                        inventoryUnitCostCents = invItem.unitCostCents
                    )
                    totalCostCents += lineCost
                    entities += RecipeIngredientEntity(
                        id = UUID.randomUUID().toString(),
                        recipeId = recipeId,
                        inventoryItemId = invItem.itemId,
                        ingredientName = invItem.name,
                        quantity = roundQty(draft.quantity),
                        unit = draft.unit.trim(),
                        unitCostCents = invItem.unitCostCents
                    )
                    snapshotParts += "${roundQty(draft.quantity)} ${draft.unit.trim()} ${invItem.name}"
                }

                recipeDao.upsertRecipe(
                    RecipeEntity(
                        recipeId = recipeId,
                        branchId = session.branchId,
                        productId = productId,
                        version = nextVersion,
                        notes = notes.trim(),
                        totalCostCents = totalCostCents,
                        updatedBy = session.displayName,
                        updatedAt = now
                    )
                )
                recipeDao.deleteIngredientsForRecipe(recipeId)
                recipeDao.insertIngredients(entities)
                recipeDao.insertVersion(
                    RecipeVersionEntity(
                        versionId = "${recipeId}_v$nextVersion",
                        recipeId = recipeId,
                        productId = productId,
                        versionNumber = nextVersion,
                        totalCostCents = totalCostCents,
                        ingredientsSnapshot = snapshotParts.joinToString(", "),
                        notes = notes.trim().ifBlank { "Updated ingredients" },
                        editedBy = session.displayName,
                        editedAt = now
                    )
                )

                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_rec_${recipeId}_v$nextVersion",
                        branchId = session.branchId,
                        entityType = "RECIPE",
                        entityId = recipeId,
                        payloadJson = """{"productId":"$productId","version":$nextVersion,"costCents":$totalCostCents}""",
                        status = "PENDING",
                        lastError = "Realtime Database in Locked Mode — queued safely in Room"
                    )
                )

                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = if (existing == null) "RECIPE_CREATED" else "RECIPE_UPDATED",
                        details = "Recipe v$nextVersion for product $productId | Cost: ${totalCostCents}c"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class InventoryRepository(private val database: AaCraveDatabase) {

    private val inventoryDao = database.inventoryDao()
    private val auditLogDao = database.auditLogDao()
    private val syncQueueDao = database.syncQueueDao()

    fun observeInventoryItems(branchId: String): Flow<List<InventoryItemRecord>> =
        inventoryDao.observeInventoryItems(branchId).map { list ->
            val now = System.currentTimeMillis()
            val sevenDaysMs = 7 * 86_400_000L
            list.map { item ->
                val expired = item.expiryDateMillis != null && item.expiryDateMillis < now
                val expiringSoon = item.expiryDateMillis != null && !expired && item.expiryDateMillis <= now + sevenDaysMs
                InventoryItemRecord(
                    itemId = item.itemId,
                    branchId = item.branchId,
                    name = item.name,
                    sku = item.sku,
                    itemType = InventoryItemType.fromCode(item.itemType),
                    unit = item.unit,
                    currentQty = item.currentQty,
                    reorderLevel = item.reorderLevel,
                    unitCostCents = item.unitCostCents,
                    expiryDateMillis = item.expiryDateMillis,
                    linkedProductId = item.linkedProductId,
                    updatedAt = item.updatedAt,
                    isLowStock = item.currentQty <= item.reorderLevel,
                    isExpired = expired,
                    isExpiringSoon = expiringSoon
                )
            }
        }

    fun observeStockMovements(branchId: String): Flow<List<StockMovementRecord>> =
        inventoryDao.observeStockMovements(branchId).map { list ->
            list.map {
                StockMovementRecord(
                    movementId = it.movementId,
                    branchId = it.branchId,
                    inventoryItemId = it.inventoryItemId,
                    itemName = it.itemName,
                    movementType = StockMovementType.fromCode(it.movementType),
                    quantityDelta = it.quantityDelta,
                    unit = it.unit,
                    referenceOrderId = it.referenceOrderId,
                    reason = it.reason,
                    actorName = it.actorName,
                    createdAt = it.createdAt
                )
            }
        }

    fun observePhysicalAudits(branchId: String): Flow<List<PhysicalAuditRecord>> =
        inventoryDao.observePhysicalAudits(branchId).map { list ->
            list.map {
                PhysicalAuditRecord(
                    auditId = it.auditId,
                    branchId = it.branchId,
                    inventoryItemId = it.inventoryItemId,
                    itemName = it.itemName,
                    expectedQty = it.expectedQty,
                    countedQty = it.countedQty,
                    varianceQty = it.varianceQty,
                    notes = it.notes,
                    auditorName = it.auditorName,
                    auditedAt = it.auditedAt
                )
            }
        }

    suspend fun saveInventoryItem(
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
    ): Result<Unit> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canManageInventory) {
            return Result.failure(SecurityException("Only authorized managers or inventory staff can configure inventory items."))
        }
        val cleanName = name.trim()
        val cleanSku = sku.trim().uppercase()
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Item name is required."))
        if (cleanSku.isBlank()) return Result.failure(IllegalArgumentException("SKU is required."))
        if (initialOrCurrentQty < 0.0) return Result.failure(IllegalArgumentException("Stock quantity cannot be negative."))
        if (reorderLevel < 0.0) return Result.failure(IllegalArgumentException("Reorder level cannot be negative."))
        if (unitCostCents < 0L) return Result.failure(IllegalArgumentException("Unit cost cannot be negative."))

        return try {
            database.withTransaction {
                val now = System.currentTimeMillis()
                val existing = if (itemId != null) inventoryDao.getItemById(itemId) else null
                val id = existing?.itemId ?: "inv_${UUID.randomUUID().toString().take(8)}"
                val qty = roundQty(existing?.currentQty ?: initialOrCurrentQty)

                inventoryDao.upsertItem(
                    InventoryItemEntity(
                        itemId = id,
                        branchId = session.branchId,
                        name = cleanName,
                        sku = cleanSku,
                        itemType = itemType.code,
                        unit = unit.trim(),
                        currentQty = qty,
                        reorderLevel = roundQty(reorderLevel),
                        unitCostCents = unitCostCents,
                        expiryDateMillis = expiryDateMillis,
                        linkedProductId = linkedProductId?.takeIf { it.isNotBlank() },
                        updatedAt = now
                    )
                )

                if (existing == null && qty > 0.0) {
                    inventoryDao.insertMovementIfNew(
                        StockMovementEntity(
                            movementId = UUID.randomUUID().toString(),
                            branchId = session.branchId,
                            inventoryItemId = id,
                            itemName = cleanName,
                            movementType = StockMovementType.RECEIVING.code,
                            quantityDelta = qty,
                            unit = unit.trim(),
                            deductionKey = "init_$id",
                            reason = "Opening stock balance on item creation",
                            actorName = session.displayName,
                            createdAt = now
                        )
                    )
                }

                InventorySyncHelper.refreshLowStockAlertsInTransaction(database, session.branchId, now)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = if (existing == null) "INVENTORY_ITEM_CREATED" else "INVENTORY_ITEM_UPDATED",
                        details = "$cleanName ($cleanSku) [${itemType.label}] | Reorder <= $reorderLevel ${unit.trim()}"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun receiveStock(
        session: UserSession,
        itemId: String,
        addedQty: Double,
        newExpiryMillis: Long?,
        note: String
    ): Result<Unit> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canManageInventory) {
            return Result.failure(SecurityException("Only authorized staff can receive stock."))
        }
        if (addedQty <= 0.0) return Result.failure(IllegalArgumentException("Received quantity must be greater than 0."))

        return try {
            database.withTransaction {
                val now = System.currentTimeMillis()
                val item = inventoryDao.getItemById(itemId)
                    ?: throw IllegalArgumentException("Inventory item not found.")
                ErpAuthorizationGuard.verifyBranchAccess(session, item.branchId)?.let { throw it }
                val newQty = roundQty(item.currentQty + addedQty)

                inventoryDao.updateStockQuantity(
                    itemId = item.itemId,
                    newQty = newQty,
                    expiryMillis = newExpiryMillis,
                    updatedAt = now
                )
                inventoryDao.insertMovementIfNew(
                    StockMovementEntity(
                        movementId = UUID.randomUUID().toString(),
                        branchId = item.branchId,
                        inventoryItemId = item.itemId,
                        itemName = item.name,
                        movementType = StockMovementType.RECEIVING.code,
                        quantityDelta = roundQty(addedQty),
                        unit = item.unit,
                        reason = note.trim().ifBlank { "Stock receiving batch" },
                        actorName = session.displayName,
                        createdAt = now
                    )
                )
                InventorySyncHelper.refreshLowStockAlertsInTransaction(database, item.branchId, now)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = item.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "STOCK_RECEIVED",
                        details = "+${roundQty(addedQty)} ${item.unit} ${item.name} (New balance: $newQty)"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adjustStock(
        session: UserSession,
        itemId: String,
        deltaQty: Double,
        reason: String
    ): Result<Unit> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canManageInventory) {
            return Result.failure(SecurityException("Only authorized staff can adjust stock."))
        }
        if (deltaQty == 0.0) return Result.failure(IllegalArgumentException("Adjustment quantity cannot be zero."))
        if (reason.isBlank()) return Result.failure(IllegalArgumentException("Reason is required for stock adjustments."))

        return try {
            database.withTransaction {
                val now = System.currentTimeMillis()
                val item = inventoryDao.getItemById(itemId)
                    ?: throw IllegalArgumentException("Inventory item not found.")
                ErpAuthorizationGuard.verifyBranchAccess(session, item.branchId)?.let { throw it }
                val newQty = roundQty((item.currentQty + deltaQty).coerceAtLeast(0.0))

                inventoryDao.updateStockQuantity(item.itemId, newQty, null, now)
                inventoryDao.insertMovementIfNew(
                    StockMovementEntity(
                        movementId = UUID.randomUUID().toString(),
                        branchId = item.branchId,
                        inventoryItemId = item.itemId,
                        itemName = item.name,
                        movementType = StockMovementType.ADJUSTMENT.code,
                        quantityDelta = roundQty(deltaQty),
                        unit = item.unit,
                        reason = reason.trim(),
                        actorName = session.displayName,
                        createdAt = now
                    )
                )
                InventorySyncHelper.refreshLowStockAlertsInTransaction(database, item.branchId, now)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = item.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "STOCK_ADJUSTED",
                        details = "${if (deltaQty > 0) "+" else ""}${roundQty(deltaQty)} ${item.unit} ${item.name} — ${reason.trim()}"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordWastage(
        session: UserSession,
        itemId: String,
        wastedQty: Double,
        reason: String
    ): Result<Unit> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canManageInventory) {
            return Result.failure(SecurityException("Only authorized staff can record wastage."))
        }
        if (wastedQty <= 0.0) return Result.failure(IllegalArgumentException("Wastage quantity must be greater than zero."))
        if (reason.isBlank()) return Result.failure(IllegalArgumentException("Wastage reason is required."))

        return try {
            database.withTransaction {
                val now = System.currentTimeMillis()
                val item = inventoryDao.getItemById(itemId)
                    ?: throw IllegalArgumentException("Inventory item not found.")
                ErpAuthorizationGuard.verifyBranchAccess(session, item.branchId)?.let { throw it }
                val newQty = roundQty((item.currentQty - wastedQty).coerceAtLeast(0.0))

                inventoryDao.updateStockQuantity(item.itemId, newQty, null, now)
                inventoryDao.insertMovementIfNew(
                    StockMovementEntity(
                        movementId = UUID.randomUUID().toString(),
                        branchId = item.branchId,
                        inventoryItemId = item.itemId,
                        itemName = item.name,
                        movementType = StockMovementType.WASTAGE.code,
                        quantityDelta = -roundQty(wastedQty),
                        unit = item.unit,
                        reason = reason.trim(),
                        actorName = session.displayName,
                        createdAt = now
                    )
                )
                InventorySyncHelper.refreshLowStockAlertsInTransaction(database, item.branchId, now)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = item.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "WASTAGE_RECORDED",
                        details = "-${roundQty(wastedQty)} ${item.unit} ${item.name} — ${reason.trim()}"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordPhysicalAudit(
        session: UserSession,
        itemId: String,
        countedQty: Double,
        notes: String
    ): Result<PhysicalAuditRecord> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canManageInventory) {
            return Result.failure(SecurityException("Only authorized staff can perform physical stock audits."))
        }
        if (countedQty < 0.0) return Result.failure(IllegalArgumentException("Counted quantity cannot be negative."))

        return try {
            val audit = database.withTransaction {
                val now = System.currentTimeMillis()
                val item = inventoryDao.getItemById(itemId)
                    ?: throw IllegalArgumentException("Inventory item not found.")
                ErpAuthorizationGuard.verifyBranchAccess(session, item.branchId)?.let { throw it }
                val expected = roundQty(item.currentQty)
                val counted = roundQty(countedQty)
                val variance = roundQty(counted - expected)
                val auditId = "audit_${UUID.randomUUID().toString().take(8)}"

                val entity = PhysicalAuditEntity(
                    auditId = auditId,
                    branchId = item.branchId,
                    inventoryItemId = item.itemId,
                    itemName = item.name,
                    expectedQty = expected,
                    countedQty = counted,
                    varianceQty = variance,
                    notes = notes.trim().ifBlank { "Scheduled physical count" },
                    auditorName = session.displayName,
                    auditedAt = now
                )
                inventoryDao.insertPhysicalAudit(entity)

                if (variance != 0.0) {
                    inventoryDao.updateStockQuantity(item.itemId, counted, null, now)
                    inventoryDao.insertMovementIfNew(
                        StockMovementEntity(
                            movementId = UUID.randomUUID().toString(),
                            branchId = item.branchId,
                            inventoryItemId = item.itemId,
                            itemName = item.name,
                            movementType = StockMovementType.AUDIT_CORRECTION.code,
                            quantityDelta = variance,
                            unit = item.unit,
                            reason = "Physical audit ($expected -> $counted): ${entity.notes}",
                            actorName = session.displayName,
                            createdAt = now
                        )
                    )
                }

                InventorySyncHelper.refreshLowStockAlertsInTransaction(database, item.branchId, now)
                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_$auditId",
                        branchId = item.branchId,
                        entityType = "PHYSICAL_AUDIT",
                        entityId = auditId,
                        payloadJson = """{"item":"${item.name}","expected":$expected,"counted":$counted,"variance":$variance}""",
                        status = "PENDING",
                        lastError = "Realtime Database in Locked Mode — queued safely in Room"
                    )
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = item.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "PHYSICAL_STOCK_AUDIT",
                        details = "${item.name}: expected $expected, counted $counted (variance $variance ${item.unit})"
                    )
                )

                PhysicalAuditRecord(
                    auditId = entity.auditId,
                    branchId = entity.branchId,
                    inventoryItemId = entity.inventoryItemId,
                    itemName = entity.itemName,
                    expectedQty = entity.expectedQty,
                    countedQty = entity.countedQty,
                    varianceQty = entity.varianceQty,
                    notes = entity.notes,
                    auditorName = entity.auditorName,
                    auditedAt = entity.auditedAt
                )
            }
            Result.success(audit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Explicit idempotent sync hook: replays recipe deduction for an order if needed,
     * guaranteed by unique deductionKey to never deduct twice.
     */
    suspend fun syncOrderDeductionIdempotently(session: UserSession, orderId: String): Result<Int> {
        return try {
            val count = database.withTransaction {
                val order = database.posDao().getOrderById(orderId)
                    ?: throw IllegalArgumentException("Order not found.")
                val items = database.posDao().getItemsForOrder(orderId)
                InventorySyncHelper.deductForOrderInTransaction(
                    database = database,
                    branchId = order.branchId,
                    orderId = order.orderId,
                    orderNumber = order.orderNumber,
                    items = items,
                    actorName = session.displayName,
                    now = System.currentTimeMillis()
                )
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
