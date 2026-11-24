package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AaCraveDatabase
import com.example.data.local.DatabaseSeeder
import com.example.data.repository.InventoryRepository
import com.example.data.repository.KdsRepository
import com.example.data.repository.MenuRepository
import com.example.data.repository.PosRepository
import com.example.data.repository.RecipeRepository
import com.example.domain.model.BillingCalculator
import com.example.domain.model.CartItem
import com.example.domain.model.InventoryItemType
import com.example.domain.model.KitchenStation
import com.example.domain.model.KotStatus
import com.example.domain.model.OrderPriority
import com.example.domain.model.OrderStatus
import com.example.domain.model.OrderType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.RecipeIngredientDraft
import com.example.domain.model.SplitPaymentBreakdown
import com.example.domain.model.StockMovementType
import com.example.domain.model.UnitConverter
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AaCravePhase3Test {

    private lateinit var context: Context
    private lateinit var database: AaCraveDatabase
    private lateinit var posRepository: PosRepository
    private lateinit var kdsRepository: KdsRepository
    private lateinit var menuRepository: MenuRepository
    private lateinit var recipeRepository: RecipeRepository
    private lateinit var inventoryRepository: InventoryRepository

    private val managerSession = UserSession(
        uid = "mgr_p3",
        email = "manager@aacrave.local",
        displayName = "Sara Manager",
        role = UserRole.BRANCH_MANAGER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    private val kitchenSession = UserSession(
        uid = "chef_p3",
        email = "chef@aacrave.local",
        displayName = "Tariq Chef",
        role = UserRole.KITCHEN_STAFF,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    private val cashierSession = UserSession(
        uid = "cashier_p3",
        email = "cashier@aacrave.local",
        displayName = "Ali Cashier",
        role = UserRole.CASHIER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AaCraveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseSeeder(database).ensureSeeded()
        posRepository = PosRepository(database)
        kdsRepository = KdsRepository(database)
        menuRepository = MenuRepository(context, database)
        recipeRepository = RecipeRepository(database)
        inventoryRepository = InventoryRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `kds routes multi station tickets updates stages and syncs original order status`() = runTest {
        val products = posRepository.observeProducts(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val burger = products.first { it.productId == "prod_m_1" } // GRILL
        val cooler = products.first { it.productId == "prod_m_7" } // BEVERAGES

        val cart = listOf(
            CartItem("c1", burger, burger.variations.firstOrNull(), emptyList(), "Well done", 1),
            CartItem("c2", cooler, null, emptyList(), "Less ice", 2)
        )
        val totals = BillingCalculator.calculate(cart, 0.0, 0L, 8.0, 5.0)

        val order = posRepository.placeOrder(
            session = cashierSession,
            idempotencyKey = "idem-kds-multi-1",
            cartItems = cart,
            totals = totals,
            orderType = OrderType.DINE_IN,
            tableNumber = "T-12",
            priority = OrderPriority.RUSH,
            customer = null,
            paymentMethod = PaymentMethod.CASH,
            splitBreakdown = SplitPaymentBreakdown(cashCents = totals.totalPayableCents)
        ).getOrThrow()

        val tickets = kdsRepository.observeKdsTickets(DatabaseSeeder.MAIN_BRANCH_ID).first()
        assertEquals(2, tickets.size)
        assertTrue(tickets.any { it.station == KitchenStation.GRILL })
        assertTrue(tickets.any { it.station == KitchenStation.BEVERAGES })

        // Cashier cannot operate KDS
        val grillKot = tickets.first { it.station == KitchenStation.GRILL }
        val bevKot = tickets.first { it.station == KitchenStation.BEVERAGES }
        val denied = kdsRepository.updateKotStatus(cashierSession, grillKot.kotId, KotStatus.PREPARING)
        assertTrue(denied.isFailure)
        assertTrue(denied.exceptionOrNull() is SecurityException)

        // Chef moves Grill KOT to PREPARING -> Order status becomes PREPARING
        kdsRepository.updateKotStatus(kitchenSession, grillKot.kotId, KotStatus.PREPARING).getOrThrow()
        var syncedOrder = posRepository.observeOrders(DatabaseSeeder.MAIN_BRANCH_ID).first().first { it.orderId == order.orderId }
        assertEquals(OrderStatus.PREPARING, syncedOrder.status)

        // Move both to READY -> Order status becomes READY
        kdsRepository.updateKotStatus(kitchenSession, grillKot.kotId, KotStatus.READY).getOrThrow()
        kdsRepository.updateKotStatus(kitchenSession, bevKot.kotId, KotStatus.READY).getOrThrow()
        syncedOrder = posRepository.observeOrders(DatabaseSeeder.MAIN_BRANCH_ID).first().first { it.orderId == order.orderId }
        assertEquals(OrderStatus.READY, syncedOrder.status)

        // Move both to COMPLETED -> Order status becomes COMPLETED
        kdsRepository.updateKotStatus(kitchenSession, grillKot.kotId, KotStatus.COMPLETED).getOrThrow()
        kdsRepository.updateKotStatus(kitchenSession, bevKot.kotId, KotStatus.COMPLETED).getOrThrow()
        syncedOrder = posRepository.observeOrders(DatabaseSeeder.MAIN_BRANCH_ID).first().first { it.orderId == order.orderId }
        assertEquals(OrderStatus.COMPLETED, syncedOrder.status)
    }

    @Test
    fun `menu management supports category guards combos archiving and pos visibility`() = runTest {
        // Create category
        menuRepository.saveCategory(managerSession, null, "Chef Specials", 10).getOrThrow()
        val specialCat = menuRepository.observeCategories(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.name == "Chef Specials" }

        // Create combo product in that category
        val productId = menuRepository.saveProduct(
            session = managerSession,
            productId = null,
            categoryId = specialCat.categoryId,
            categoryName = specialCat.name,
            name = "Gold Feast Combo",
            description = "Signature burger + truffle fries + saffron cooler",
            basePriceCents = 2190L,
            sku = "CMB-101",
            taxExempt = false,
            isAvailable = true,
            isCombo = true,
            comboSummary = "Gold Wagyu Smash + Truffle Fries + Passionfruit Cooler",
            kitchenStation = KitchenStation.GRILL.code,
            imagePath = null
        ).getOrThrow()

        // Deleting non-empty category must fail
        val deleteBlocked = menuRepository.deleteCategory(managerSession, specialCat.categoryId, specialCat.name)
        assertTrue(deleteBlocked.isFailure)

        // Verify visible in POS
        assertTrue(posRepository.observeProducts(DatabaseSeeder.MAIN_BRANCH_ID).first().any { it.productId == productId && it.isCombo })

        // Archive product -> hidden from POS, still visible in Menu Management
        val createdProd = menuRepository.observeAllMenuProducts(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.productId == productId }
        menuRepository.setProductArchived(managerSession, createdProd, true).getOrThrow()

        assertFalse(posRepository.observeProducts(DatabaseSeeder.MAIN_BRANCH_ID).first().any { it.productId == productId })
        assertTrue(menuRepository.observeAllMenuProducts(DatabaseSeeder.MAIN_BRANCH_ID).first().any { it.productId == productId && it.isArchived })

        // Now category can be deleted cleanly
        menuRepository.deleteCategory(managerSession, specialCat.categoryId, specialCat.name).getOrThrow()
    }

    @Test
    fun `recipe costing converts units computes profit margin and preserves version history`() = runTest {
        // Check unit conversion helper
        val cheddarCost = UnitConverter.calculateIngredientCostCents(
            recipeQty = 40.0,
            recipeUnit = "g",
            inventoryUnit = "kg",
            inventoryUnitCostCents = 1400L
        )
        assertEquals(56L, cheddarCost)

        // Initial seeded recipe for prod_m_1 is v1
        val initialRecipes = recipeRepository.observeRecipes(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val wagyuRecipeV1 = initialRecipes.first { it.productId == "prod_m_1" }
        assertEquals(1, wagyuRecipeV1.version)
        assertEquals(451L, wagyuRecipeV1.totalCostCents)

        // Edit recipe -> creates v2 and keeps v1 in version history
        recipeRepository.saveRecipe(
            session = managerSession,
            productId = "prod_m_1",
            notes = "Double cheddar upgrade",
            draftIngredients = listOf(
                RecipeIngredientDraft("inv_m_patty", 1.0, "pcs"),         // 285c
                RecipeIngredientDraft("inv_m_brioche", 1.0, "pcs"),       // 65c
                RecipeIngredientDraft("inv_m_cheddar", 80.0, "g")         // 112c
            )
        ).getOrThrow()

        val updated = recipeRepository.observeRecipes(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.productId == "prod_m_1" }
        assertEquals(2, updated.version)
        assertEquals(462L, updated.totalCostCents)
        assertEquals(1450L - 462L, updated.estimatedProfitCents)
        assertEquals(2, updated.versionHistory.size)
        assertEquals(2, updated.versionHistory.first().versionNumber)
        assertEquals(1, updated.versionHistory.last().versionNumber)
    }

    @Test
    fun `order confirmation auto deducts recipe ingredients and prevents duplicate deductions`() = runTest {
        // Seeded quantities on MAIN_BRANCH:
        // inv_m_patty = 42.0 pcs, inv_m_brioche = 22.0 pcs, inv_m_cheddar = 1.8 kg, inv_m_truffle_aioli = 0.9 L
        // Seeded recipe for prod_m_1 (per 1 unit): 1 patty, 1 bun, 40g cheddar (0.04 kg), 25ml aioli (0.025 L)
        val products = posRepository.observeProducts(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val burger = products.first { it.productId == "prod_m_1" }
        val cart = listOf(CartItem("line_1", burger, burger.variations.first(), emptyList(), "", 2))
        val totals = BillingCalculator.calculate(cart, 0.0, 0L, 8.0, 0.0)

        val order = posRepository.placeOrder(
            session = cashierSession,
            idempotencyKey = "idem-auto-deduct-1",
            cartItems = cart,
            totals = totals,
            orderType = OrderType.TAKEAWAY,
            tableNumber = null,
            priority = OrderPriority.NORMAL,
            customer = null,
            paymentMethod = PaymentMethod.CASH,
            splitBreakdown = SplitPaymentBreakdown(cashCents = totals.totalPayableCents)
        ).getOrThrow()

        val afterOrder = inventoryRepository.observeInventoryItems(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .associateBy { it.itemId }

        assertEquals(40.0, afterOrder.getValue("inv_m_patty").currentQty, 0.001)
        assertEquals(20.0, afterOrder.getValue("inv_m_brioche").currentQty, 0.001)
        assertEquals(1.72, afterOrder.getValue("inv_m_cheddar").currentQty, 0.001)
        assertEquals(0.85, afterOrder.getValue("inv_m_truffle_aioli").currentQty, 0.001)

        // Retry placeOrder with same idempotencyKey AND invoke syncOrderDeductionIdempotently -> ZERO duplicate deductions
        posRepository.placeOrder(
            session = cashierSession,
            idempotencyKey = "idem-auto-deduct-1",
            cartItems = cart,
            totals = totals,
            orderType = OrderType.TAKEAWAY,
            tableNumber = null,
            priority = OrderPriority.NORMAL,
            customer = null,
            paymentMethod = PaymentMethod.CASH,
            splitBreakdown = SplitPaymentBreakdown(cashCents = totals.totalPayableCents)
        ).getOrThrow()

        val replayedDeductions = inventoryRepository.syncOrderDeductionIdempotently(managerSession, order.orderId).getOrThrow()
        assertEquals(0, replayedDeductions)

        val afterReplay = inventoryRepository.observeInventoryItems(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .associateBy { it.itemId }
        assertEquals(40.0, afterReplay.getValue("inv_m_patty").currentQty, 0.001)

        val saleMovements = inventoryRepository.observeStockMovements(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .filter { it.movementType == StockMovementType.SALE_DEDUCTION && it.referenceOrderId == order.orderId }
        assertEquals(4, saleMovements.size)
    }

    @Test
    fun `inventory receiving wastage and physical audit track movements and low stock alerts`() = runTest {
        // Receive 30 brioche buns (22 -> 52, above reorderLevel 48)
        inventoryRepository.receiveStock(managerSession, "inv_m_brioche", 30.0, null, "Morning bakery delivery").getOrThrow()
        var bun = inventoryRepository.observeInventoryItems(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.itemId == "inv_m_brioche" }
        assertEquals(52.0, bun.currentQty, 0.001)
        assertFalse(bun.isLowStock)

        // Record wastage of 6 buns (52 -> 46, drops below reorderLevel 48 -> Low stock alert triggered!)
        inventoryRepository.recordWastage(managerSession, "inv_m_brioche", 6.0, "Dropped tray").getOrThrow()
        bun = inventoryRepository.observeInventoryItems(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.itemId == "inv_m_brioche" }
        assertEquals(46.0, bun.currentQty, 0.001)
        assertTrue(bun.isLowStock)

        // Physical audit finds 44 buns (expected 46 -> variance -2)
        val audit = inventoryRepository.recordPhysicalAudit(managerSession, "inv_m_brioche", 44.0, "Night shift count").getOrThrow()
        assertEquals(46.0, audit.expectedQty, 0.001)
        assertEquals(44.0, audit.countedQty, 0.001)
        assertEquals(-2.0, audit.varianceQty, 0.001)

        bun = inventoryRepository.observeInventoryItems(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .first { it.itemId == "inv_m_brioche" }
        assertEquals(44.0, bun.currentQty, 0.001)

        val movements = inventoryRepository.observeStockMovements(DatabaseSeeder.MAIN_BRANCH_ID).first()
        assertTrue(movements.any { it.movementType == StockMovementType.RECEIVING && it.quantityDelta == 30.0 })
        assertTrue(movements.any { it.movementType == StockMovementType.WASTAGE && it.quantityDelta == -6.0 })
        assertTrue(movements.any { it.movementType == StockMovementType.AUDIT_CORRECTION && it.quantityDelta == -2.0 })
    }

    @Test
    fun `migration 2 to 3 preserves existing products and creates phase 3 tables cleanly`() {
        val dbName = "migration_test_v2_to_v3.db"
        context.deleteDatabase(dbName)

        // Create a v2 schema database manually and insert a v2 product
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `branches` (`branchId` TEXT NOT NULL, `name` TEXT NOT NULL, `code` TEXT NOT NULL, `address` TEXT NOT NULL, `isMainBranch` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`branchId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profiles` (`uid` TEXT NOT NULL, `email` TEXT NOT NULL, `displayName` TEXT NOT NULL, `roleCode` TEXT NOT NULL, `branchId` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `lastLoginAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `dashboard_snapshots` (`branchId` TEXT NOT NULL, `businessDate` TEXT NOT NULL, `todaySales` REAL NOT NULL, `todayOrders` INTEGER NOT NULL, `totalCustomers` INTEGER NOT NULL, `pendingKitchenOrders` INTEGER NOT NULL, `lowStockAlerts` INTEGER NOT NULL, `dailyExpenses` REAL NOT NULL, `estimatedProfit` REAL NOT NULL, `cashPayments` REAL NOT NULL, `digitalPayments` REAL NOT NULL, `isSampleData` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`branchId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `recent_transactions` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `orderNumber` TEXT NOT NULL, `invoiceNumber` TEXT NOT NULL, `customerName` TEXT NOT NULL, `amount` REAL NOT NULL, `paymentMethod` TEXT NOT NULL, `status` TEXT NOT NULL, `timeLabel` TEXT NOT NULL, `isSampleData` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `low_stock_alerts` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `ingredientName` TEXT NOT NULL, `currentStock` REAL NOT NULL, `reorderLevel` REAL NOT NULL, `unit` TEXT NOT NULL, `isSampleData` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `hourly_sales` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `hourOrder` INTEGER NOT NULL, `label` TEXT NOT NULL, `revenue` REAL NOT NULL, `orderCount` INTEGER NOT NULL, `isSampleData` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `branchId` TEXT NOT NULL, `actorUid` TEXT NOT NULL, `actorEmail` TEXT NOT NULL, `actorRole` TEXT NOT NULL, `action` TEXT NOT NULL, `details` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_queue` (`transactionId` TEXT NOT NULL, `branchId` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `status` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `lastError` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`transactionId`))")

                        AaCraveDatabase.MIGRATION_1_2.migrate(db)
                        db.execSQL(
                            """
                            INSERT INTO products (productId, branchId, categoryId, name, description, basePriceCents, sku, categoryTag, isAvailable, taxExempt)
                            VALUES ('legacy_p1', 'branch_main_01', 'cat_m_burgers', 'Legacy Burger', 'Preserved row', 999, 'LEG-01', 'Burgers', 1, 0)
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()

        // Open with Room v5 + MIGRATION_1_2 .. MIGRATION_4_5
        val migratedDb = Room.databaseBuilder(context, AaCraveDatabase::class.java, dbName)
            .addMigrations(
                AaCraveDatabase.MIGRATION_1_2,
                AaCraveDatabase.MIGRATION_2_3,
                AaCraveDatabase.MIGRATION_3_4,
                AaCraveDatabase.MIGRATION_4_5,
                AaCraveDatabase.MIGRATION_5_6,
                AaCraveDatabase.MIGRATION_6_7,
                AaCraveDatabase.MIGRATION_7_8,
                AaCraveDatabase.MIGRATION_8_9
            )
            .allowMainThreadQueries()
            .build()

        val cursor = migratedDb.openHelper.readableDatabase.query("SELECT productId, isArchived, kitchenStation FROM products WHERE productId = 'legacy_p1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("legacy_p1", cursor.getString(0))
        assertEquals(0, cursor.getInt(1))
        assertEquals("MAIN_KITCHEN", cursor.getString(2))
        cursor.close()

        // Confirm Phase 3 table exists
        val invCursor = migratedDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM inventory_items")
        assertTrue(invCursor.moveToFirst())
        assertEquals(0, invCursor.getInt(0))
        invCursor.close()

        migratedDb.close()
        context.deleteDatabase(dbName)
    }
}
