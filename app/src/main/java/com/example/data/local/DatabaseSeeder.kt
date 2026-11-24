package com.example.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseSeeder(private val database: AaCraveDatabase) {

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val branchDao = database.branchDao()
        val dashboardDao = database.dashboardDao()

        if (branchDao.countBranches() == 0) {
            val now = System.currentTimeMillis()
            branchDao.upsertAll(
                listOf(
                    BranchEntity(
                        branchId = MAIN_BRANCH_ID,
                        name = "AA CRAVE - Flagship Main",
                        code = "HQ-01",
                        address = "140 Grand Boulevard, Culinary District",
                        isMainBranch = true,
                        isActive = true,
                        phone = "+1-555-0100",
                        email = "flagship@aacrave.local",
                        managerName = "Sara Manager",
                        openingCashCents = 0L,
                        openingBankCents = 0L,
                        createdAt = now,
                        updatedAt = now
                    ),
                    BranchEntity(
                        branchId = DOWNTOWN_BRANCH_ID,
                        name = "AA CRAVE - Downtown Bistro",
                        code = "DT-02",
                        address = "88 Market Square, Suite 4",
                        isMainBranch = false,
                        isActive = true,
                        phone = "+1-555-0200",
                        email = "downtown@aacrave.local",
                        managerName = "Tariq Bistro Lead",
                        openingCashCents = 0L,
                        openingBankCents = 0L,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            )
        }

        if (dashboardDao.countSnapshots() == 0) {
            seedSampleDashboardData()
        }

        if (database.posDao().countProducts() == 0) {
            seedPosCatalogAndCustomers()
        }

        if (database.inventoryDao().countInventoryItems() == 0) {
            seedPhase3InventoryAndRecipes()
        }

        if (database.supplierDao().countSuppliers() == 0) {
            seedPhase4SuppliersAndPurchases()
        }

        if (database.customerLoyaltyDao().countConfigs() == 0) {
            seedPhase4LoyaltyAndFeedback()
        }

        if (database.expenseDao().countExpenses() == 0) {
            seedPhase4Expenses()
        }

        if (database.staffDao().countHrPolicies() == 0) {
            seedPhase5HrPolicies()
        }

        if (database.shiftAttendanceDao().countShifts() == 0) {
            seedPhase5ShiftTemplates()
        }

        // Phase 6: Ensure Chart of Accounts exists for every branch
        branchDao.getAllBranches().forEach { branch ->
            ensureChartOfAccountsForBranch(
                database = database,
                branchId = branch.branchId,
                openingCashCents = branch.openingCashCents,
                openingBankCents = branch.openingBankCents
            )
        }

        // Phase 7: Ensure security role permission policies & setup notification exist
        if (database.phase7Dao().countRolePolicies() == 0) {
            seedPhase7SecurityDefaults()
        }

        // Smart Loyalty & Rewards: Ensure default program rules exist per branch (zero fake stamps/vouchers)
        seedSmartLoyaltyDefaults()
    }

    suspend fun resetSampleDashboardData() = withContext(Dispatchers.IO) {
        seedSampleDashboardData()
    }

    private suspend fun seedPosCatalogAndCustomers() {
        val posDao = database.posDao()
        val categories = listOf(
            // Main Branch
            ProductCategoryEntity("cat_m_burgers", MAIN_BRANCH_ID, "Signature Burgers", 1, true),
            ProductCategoryEntity("cat_m_grills", MAIN_BRANCH_ID, "Grills & Platters", 2, true),
            ProductCategoryEntity("cat_m_pizza", MAIN_BRANCH_ID, "Wood-Fired Pizza", 3, true),
            ProductCategoryEntity("cat_m_sides", MAIN_BRANCH_ID, "Sides & Fries", 4, true),
            ProductCategoryEntity("cat_m_drinks", MAIN_BRANCH_ID, "Beverages", 5, true),
            ProductCategoryEntity("cat_m_desserts", MAIN_BRANCH_ID, "Desserts", 6, true),
            // Downtown Branch
            ProductCategoryEntity("cat_d_burgers", DOWNTOWN_BRANCH_ID, "Signature Burgers", 1, true),
            ProductCategoryEntity("cat_d_grills", DOWNTOWN_BRANCH_ID, "Grills & Platters", 2, true),
            ProductCategoryEntity("cat_d_drinks", DOWNTOWN_BRANCH_ID, "Beverages", 3, true)
        )
        posDao.upsertCategories(categories)

        val products = listOf(
            ProductEntity(
                "prod_m_1", MAIN_BRANCH_ID, "cat_m_burgers",
                "AA Crave Gold Wagyu Smash",
                "Double seared wagyu beef patty, aged cheddar, caramelized shallots, house gold sauce",
                1450L, "BRG-001", "Signature Burgers", true, false,
                kitchenStation = "GRILL"
            ),
            ProductEntity(
                "prod_m_2", MAIN_BRANCH_ID, "cat_m_burgers",
                "Crispy Truffle Chicken Brioche",
                "Buttermilk fried thigh, black truffle aioli, pickled slaw on toasted brioche",
                1200L, "BRG-002", "Signature Burgers", true, false,
                kitchenStation = "GRILL"
            ),
            ProductEntity(
                "prod_m_3", MAIN_BRANCH_ID, "cat_m_grills",
                "Charcoal Lamb Chops Platter",
                "Herb-marinated rack chops, roasted garlic mash, grilled asparagus",
                2650L, "GRL-001", "Grills & Platters", true, false,
                kitchenStation = "GRILL"
            ),
            ProductEntity(
                "prod_m_4", MAIN_BRANCH_ID, "cat_m_grills",
                "Peri-Peri Flame Half Chicken",
                "24-hour citrus chili marinade, flame-grilled with charred lemon",
                1680L, "GRL-002", "Grills & Platters", true, false,
                kitchenStation = "GRILL"
            ),
            ProductEntity(
                "prod_m_5", MAIN_BRANCH_ID, "cat_m_pizza",
                "Artisan Burrata Margherita",
                "San Marzano tomato base, creamy burrata, basil oil, blistered crust",
                1550L, "PZA-001", "Wood-Fired Pizza", true, false,
                kitchenStation = "FRY_PIZZA"
            ),
            ProductEntity(
                "prod_m_6", MAIN_BRANCH_ID, "cat_m_sides",
                "Truffle Parmesan Hand-Cut Fries",
                "Double-fried russet potatoes tossed in white truffle oil and aged parmesan",
                650L, "SID-001", "Sides & Fries", true, false,
                kitchenStation = "FRY_PIZZA"
            ),
            ProductEntity(
                "prod_m_7", MAIN_BRANCH_ID, "cat_m_drinks",
                "Saffron Passionfruit Cooler",
                "House-infused saffron syrup, fresh passionfruit, sparkling citrus",
                550L, "DRK-001", "Beverages", true, false,
                kitchenStation = "BEVERAGES"
            ),
            ProductEntity(
                "prod_m_8", MAIN_BRANCH_ID, "cat_m_drinks",
                "Iced Spanish Caramel Latte",
                "Double ristretto espresso, condensed cream, sea-salt caramel",
                490L, "DRK-002", "Beverages", true, false,
                kitchenStation = "BEVERAGES"
            ),
            ProductEntity(
                "prod_m_9", MAIN_BRANCH_ID, "cat_m_desserts",
                "Molten Obsidian Chocolate Cake",
                "70% dark Belgian ganache core with Madagascar vanilla bean gelato",
                850L, "DST-001", "Desserts", true, false,
                kitchenStation = "DESSERTS"
            ),
            // Downtown subset
            ProductEntity(
                "prod_d_1", DOWNTOWN_BRANCH_ID, "cat_d_burgers",
                "AA Crave Gold Wagyu Smash",
                "Double seared wagyu beef patty, aged cheddar, house gold sauce",
                1450L, "DT-BRG-001", "Signature Burgers", true, false,
                kitchenStation = "GRILL"
            ),
            ProductEntity(
                "prod_d_2", DOWNTOWN_BRANCH_ID, "cat_d_grills",
                "Charcoal Lamb Chops Platter",
                "Herb-marinated rack chops, roasted garlic mash",
                2650L, "DT-GRL-001", "Grills & Platters", true, false,
                kitchenStation = "GRILL"
            ),
            ProductEntity(
                "prod_d_3", DOWNTOWN_BRANCH_ID, "cat_d_drinks",
                "Saffron Passionfruit Cooler",
                "House-infused saffron syrup, fresh passionfruit, sparkling citrus",
                550L, "DT-DRK-001", "Beverages", true, false,
                kitchenStation = "BEVERAGES"
            )
        )
        posDao.upsertProducts(products)

        val variations = listOf(
            ProductVariationEntity("var_1", "prod_m_1", "Single Patty", 0L, true),
            ProductVariationEntity("var_2", "prod_m_1", "Double Wagyu", 450L, false),
            ProductVariationEntity("var_3", "prod_m_1", "Combo (Fries + Drink)", 550L, false),
            ProductVariationEntity("var_4", "prod_m_2", "Regular", 0L, true),
            ProductVariationEntity("var_5", "prod_m_2", "Combo (Fries + Drink)", 500L, false),
            ProductVariationEntity("var_6", "prod_m_3", "Half Rack (3 Chops)", 0L, true),
            ProductVariationEntity("var_7", "prod_m_3", "Full Rack (6 Chops)", 1400L, false),
            ProductVariationEntity("var_8", "prod_m_5", "10-inch Classic", 0L, true),
            ProductVariationEntity("var_9", "prod_m_5", "14-inch Sharing", 600L, false),
            ProductVariationEntity("var_10", "prod_m_8", "Regular (12oz)", 0L, true),
            ProductVariationEntity("var_11", "prod_m_8", "Large (16oz)", 120L, false),
            ProductVariationEntity("var_d1", "prod_d_1", "Single Patty", 0L, true),
            ProductVariationEntity("var_d2", "prod_d_1", "Double Wagyu", 450L, false)
        )
        posDao.upsertVariations(variations)

        val addons = listOf(
            ProductAddonEntity("add_1", "prod_m_1", "Extra Aged Cheddar", 150L),
            ProductAddonEntity("add_2", "prod_m_1", "Crispy Beef Bacon", 250L),
            ProductAddonEntity("add_3", "prod_m_1", "Truffle Aioli Dip", 120L),
            ProductAddonEntity("add_4", "prod_m_2", "Extra Aged Cheddar", 150L),
            ProductAddonEntity("add_5", "prod_m_2", "Jalapeno Relish", 90L),
            ProductAddonEntity("add_6", "prod_m_3", "Mint Chimichurri", 150L),
            ProductAddonEntity("add_7", "prod_m_5", "Extra Fresh Burrata", 350L),
            ProductAddonEntity("add_8", "prod_m_8", "Extra Espresso Shot", 110L),
            ProductAddonEntity("add_d1", "prod_d_1", "Extra Aged Cheddar", 150L)
        )
        posDao.upsertAddons(addons)

        val nowCust = System.currentTimeMillis()
        val dayMsCust = 86_400_000L
        val customers = listOf(
            CustomerEntity(
                customerId = "cust_1",
                branchId = MAIN_BRANCH_ID,
                name = "Zara Khan",
                phone = "+1-555-0142",
                email = "zara.khan@example.com",
                loyaltyPoints = 420,
                tier = "Gold VIP",
                discountPercent = 10,
                address = "42 Kensington Park, Apt 8B",
                notes = "Prefers medium-rare wagyu and corner booth seating.",
                totalPurchasesCents = 84500L,
                lastOrderAt = nowCust - dayMsCust,
                accountBalanceCents = 0L,
                feedbackSummary = "5.0 ★ • Loves truffle brioche & fast table service",
                createdAt = nowCust - 45 * dayMsCust
            ),
            CustomerEntity(
                customerId = "cust_2",
                branchId = MAIN_BRANCH_ID,
                name = "Omar Farooq",
                phone = "+1-555-0188",
                email = "omar.f@example.com",
                loyaltyPoints = 190,
                tier = "Silver",
                discountPercent = 5,
                address = "210 Oakwood Terrace",
                notes = "Weekend family diner, allergic to tree nuts.",
                totalPurchasesCents = 39200L,
                lastOrderAt = nowCust - 3 * dayMsCust,
                accountBalanceCents = 1500L,
                feedbackSummary = "4.5 ★ • Enjoys lamb chops platter",
                createdAt = nowCust - 30 * dayMsCust
            ),
            CustomerEntity(
                customerId = "cust_3",
                branchId = MAIN_BRANCH_ID,
                name = "Amina Patel",
                phone = "+1-555-0193",
                email = "amina.p@example.com",
                loyaltyPoints = 85,
                tier = "Member",
                discountPercent = 0,
                address = "15 Harbor View Lane",
                notes = "Regular takeaway lunch guest.",
                totalPurchasesCents = 14800L,
                lastOrderAt = nowCust - 5 * dayMsCust,
                accountBalanceCents = 0L,
                feedbackSummary = "4.8 ★ • Fresh burrata pizza favorite",
                createdAt = nowCust - 14 * dayMsCust
            ),
            CustomerEntity(
                customerId = "cust_4",
                branchId = DOWNTOWN_BRANCH_ID,
                name = "Corporate Hospitality",
                phone = "+1-555-0210",
                email = "events@corp.example.com",
                loyaltyPoints = 950,
                tier = "Platinum",
                discountPercent = 12,
                address = "500 Commerce Tower, Fl 12",
                notes = "B2B corporate lunch & executive catering account.",
                totalPurchasesCents = 245000L,
                lastOrderAt = nowCust - 2 * dayMsCust,
                accountBalanceCents = 12500L,
                feedbackSummary = "5.0 ★ • Reliable executive platter fulfillment",
                createdAt = nowCust - 90 * dayMsCust
            )
        )
        posDao.upsertCustomers(customers)
    }

    private suspend fun seedPhase3InventoryAndRecipes() {
        val inventoryDao = database.inventoryDao()
        val recipeDao = database.recipeDao()
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L

        val inventoryItems = listOf(
            InventoryItemEntity(
                itemId = "inv_m_patty",
                branchId = MAIN_BRANCH_ID,
                name = "Wagyu Beef Patty (180g)",
                sku = "RAW-BEEF-01",
                itemType = "RAW_MATERIAL",
                unit = "pcs",
                currentQty = 42.0,
                reorderLevel = 30.0,
                unitCostCents = 285L,
                expiryDateMillis = now + 5 * dayMs,
                updatedAt = now
            ),
            InventoryItemEntity(
                itemId = "inv_m_brioche",
                branchId = MAIN_BRANCH_ID,
                name = "Artisan Brioche Buns",
                sku = "RAW-BUN-02",
                itemType = "RAW_MATERIAL",
                unit = "pcs",
                currentQty = 22.0,
                reorderLevel = 48.0,
                unitCostCents = 65L,
                expiryDateMillis = now + 3 * dayMs,
                updatedAt = now
            ),
            InventoryItemEntity(
                itemId = "inv_m_cheddar",
                branchId = MAIN_BRANCH_ID,
                name = "Aged Cheddar Cheese",
                sku = "RAW-CHS-03",
                itemType = "RAW_MATERIAL",
                unit = "kg",
                currentQty = 1.8,
                reorderLevel = 5.0,
                unitCostCents = 1400L,
                expiryDateMillis = now + 18 * dayMs,
                updatedAt = now
            ),
            InventoryItemEntity(
                itemId = "inv_m_chicken",
                branchId = MAIN_BRANCH_ID,
                name = "Crispy Chicken Thigh Fillet",
                sku = "RAW-CHK-04",
                itemType = "RAW_MATERIAL",
                unit = "pcs",
                currentQty = 54.0,
                reorderLevel = 25.0,
                unitCostCents = 195L,
                expiryDateMillis = now + 4 * dayMs,
                updatedAt = now
            ),
            InventoryItemEntity(
                itemId = "inv_m_lamb",
                branchId = MAIN_BRANCH_ID,
                name = "Herb-Crusted Lamb Rack",
                sku = "RAW-LMB-05",
                itemType = "RAW_MATERIAL",
                unit = "kg",
                currentQty = 14.5,
                reorderLevel = 6.0,
                unitCostCents = 2400L,
                expiryDateMillis = now + 6 * dayMs,
                updatedAt = now
            ),
            InventoryItemEntity(
                itemId = "inv_m_burrata",
                branchId = MAIN_BRANCH_ID,
                name = "Fresh Pugliese Burrata",
                sku = "RAW-BRR-06",
                itemType = "RAW_MATERIAL",
                unit = "pcs",
                currentQty = 28.0,
                reorderLevel = 12.0,
                unitCostCents = 275L,
                expiryDateMillis = now + 4 * dayMs,
                updatedAt = now
            ),
            InventoryItemEntity(
                itemId = "inv_m_dough",
                branchId = MAIN_BRANCH_ID,
                name = "Sourdough Pizza Ball (280g)",
                sku = "RAW-DGH-07",
                itemType = "RAW_MATERIAL",
                unit = "pcs",
                currentQty = 64.0,
                reorderLevel = 24.0,
                unitCostCents = 95L,
                expiryDateMillis = now + 2 * dayMs,
                updatedAt = now
            ),
            InventoryItemEntity(
                itemId = "inv_m_potatoes",
                branchId = MAIN_BRANCH_ID,
                name = "Idaho Russet Potatoes",
                sku = "RAW-POT-08",
                itemType = "RAW_MATERIAL",
                unit = "kg",
                currentQty = 35.0,
                reorderLevel = 15.0,
                unitCostCents = 240L,
                expiryDateMillis = now + 25 * dayMs,
                updatedAt = now
            ),
            InventoryItemEntity(
                itemId = "inv_m_espresso",
                branchId = MAIN_BRANCH_ID,
                name = "Arabica Espresso Roast",
                sku = "RAW-ESP-09",
                itemType = "RAW_MATERIAL",
                unit = "kg",
                currentQty = 9.0,
                reorderLevel = 3.0,
                unitCostCents = 2200L,
                expiryDateMillis = now + 90 * dayMs,
                updatedAt = now
            ),
            InventoryItemEntity(
                itemId = "inv_m_truffle_aioli",
                branchId = MAIN_BRANCH_ID,
                name = "Black Truffle Aioli Base",
                sku = "RAW-SAU-10",
                itemType = "RAW_MATERIAL",
                unit = "L",
                currentQty = 0.9,
                reorderLevel = 2.5,
                unitCostCents = 1800L,
                expiryDateMillis = now + 12 * dayMs,
                updatedAt = now
            ),
            InventoryItemEntity(
                itemId = "inv_m_choco_cake",
                branchId = MAIN_BRANCH_ID,
                name = "Belgian Molten Cake Portion",
                sku = "FIN-DST-01",
                itemType = "FINISHED_PRODUCT",
                unit = "pcs",
                currentQty = 24.0,
                reorderLevel = 10.0,
                unitCostCents = 230L,
                expiryDateMillis = now + 5 * dayMs,
                linkedProductId = "prod_m_9",
                updatedAt = now
            ),
            // Downtown Branch Inventory
            InventoryItemEntity(
                itemId = "inv_d_patty",
                branchId = DOWNTOWN_BRANCH_ID,
                name = "Wagyu Beef Patty (180g)",
                sku = "DT-BEEF-01",
                itemType = "RAW_MATERIAL",
                unit = "pcs",
                currentQty = 36.0,
                reorderLevel = 20.0,
                unitCostCents = 285L,
                expiryDateMillis = now + 5 * dayMs,
                updatedAt = now
            ),
            InventoryItemEntity(
                itemId = "inv_d_brioche",
                branchId = DOWNTOWN_BRANCH_ID,
                name = "Artisan Brioche Buns",
                sku = "DT-BUN-02",
                itemType = "RAW_MATERIAL",
                unit = "pcs",
                currentQty = 40.0,
                reorderLevel = 24.0,
                unitCostCents = 65L,
                expiryDateMillis = now + 3 * dayMs,
                updatedAt = now
            ),
            InventoryItemEntity(
                itemId = "inv_d_espresso",
                branchId = DOWNTOWN_BRANCH_ID,
                name = "Arabica Espresso Roast",
                sku = "DT-ESP-03",
                itemType = "RAW_MATERIAL",
                unit = "kg",
                currentQty = 1.2,
                reorderLevel = 3.0,
                unitCostCents = 2200L,
                expiryDateMillis = now + 60 * dayMs,
                updatedAt = now
            )
        )
        inventoryDao.upsertItems(inventoryItems)

        if (recipeDao.countRecipes() == 0) {
            // Recipe 1: AA Crave Gold Wagyu Smash (prod_m_1)
            // 1 patty (285c) + 1 bun (65c) + 40g cheddar (1400 * 0.04 = 56c) + 25ml aioli (1800 * 0.025 = 45c) = 451c
            val r1Cost = 451L
            recipeDao.upsertRecipe(
                RecipeEntity(
                    recipeId = "rec_m_1",
                    branchId = MAIN_BRANCH_ID,
                    productId = "prod_m_1",
                    version = 1,
                    notes = "Sear wagyu patty on 230C flat-top for 2.5 min per side; toast bun in clarified butter.",
                    totalCostCents = r1Cost,
                    updatedBy = "System Chef Spec",
                    updatedAt = now
                )
            )
            recipeDao.insertIngredients(
                listOf(
                    RecipeIngredientEntity("ri_1_1", "rec_m_1", "inv_m_patty", "Wagyu Beef Patty (180g)", 1.0, "pcs", 285L),
                    RecipeIngredientEntity("ri_1_2", "rec_m_1", "inv_m_brioche", "Artisan Brioche Buns", 1.0, "pcs", 65L),
                    RecipeIngredientEntity("ri_1_3", "rec_m_1", "inv_m_cheddar", "Aged Cheddar Cheese", 40.0, "g", 1400L),
                    RecipeIngredientEntity("ri_1_4", "rec_m_1", "inv_m_truffle_aioli", "Black Truffle Aioli Base", 25.0, "ml", 1800L)
                )
            )
            recipeDao.insertVersion(
                RecipeVersionEntity(
                    versionId = "rv_m_1_v1",
                    recipeId = "rec_m_1",
                    productId = "prod_m_1",
                    versionNumber = 1,
                    totalCostCents = r1Cost,
                    ingredientsSnapshot = "1 pcs Wagyu Beef Patty, 1 pcs Brioche Bun, 40 g Aged Cheddar, 25 ml Truffle Aioli",
                    notes = "Initial standard house recipe.",
                    editedBy = "System Chef Spec",
                    editedAt = now
                )
            )

            // Recipe 2: Crispy Truffle Chicken Brioche (prod_m_2)
            // 1 thigh (195c) + 1 bun (65c) + 30ml aioli (54c) = 314c
            val r2Cost = 314L
            recipeDao.upsertRecipe(
                RecipeEntity(
                    recipeId = "rec_m_2",
                    branchId = MAIN_BRANCH_ID,
                    productId = "prod_m_2",
                    version = 1,
                    notes = "Fry chicken thigh at 175C until 74C internal; glaze with black truffle aioli.",
                    totalCostCents = r2Cost,
                    updatedBy = "System Chef Spec",
                    updatedAt = now
                )
            )
            recipeDao.insertIngredients(
                listOf(
                    RecipeIngredientEntity("ri_2_1", "rec_m_2", "inv_m_chicken", "Crispy Chicken Thigh Fillet", 1.0, "pcs", 195L),
                    RecipeIngredientEntity("ri_2_2", "rec_m_2", "inv_m_brioche", "Artisan Brioche Buns", 1.0, "pcs", 65L),
                    RecipeIngredientEntity("ri_2_3", "rec_m_2", "inv_m_truffle_aioli", "Black Truffle Aioli Base", 30.0, "ml", 1800L)
                )
            )
            recipeDao.insertVersion(
                RecipeVersionEntity(
                    versionId = "rv_m_2_v1",
                    recipeId = "rec_m_2",
                    productId = "prod_m_2",
                    versionNumber = 1,
                    totalCostCents = r2Cost,
                    ingredientsSnapshot = "1 pcs Chicken Thigh Fillet, 1 pcs Brioche Bun, 30 ml Truffle Aioli",
                    notes = "Initial standard house recipe.",
                    editedBy = "System Chef Spec",
                    editedAt = now
                )
            )

            // Recipe 3: Artisan Burrata Margherita (prod_m_5)
            // 1 dough (95c) + 1 burrata (275c) = 370c
            val r3Cost = 370L
            recipeDao.upsertRecipe(
                RecipeEntity(
                    recipeId = "rec_m_5",
                    branchId = MAIN_BRANCH_ID,
                    productId = "prod_m_5",
                    version = 1,
                    notes = "Bake at 430C stone deck for 90s; finish with whole burrata post-oven.",
                    totalCostCents = r3Cost,
                    updatedBy = "System Chef Spec",
                    updatedAt = now
                )
            )
            recipeDao.insertIngredients(
                listOf(
                    RecipeIngredientEntity("ri_5_1", "rec_m_5", "inv_m_dough", "Sourdough Pizza Ball (280g)", 1.0, "pcs", 95L),
                    RecipeIngredientEntity("ri_5_2", "rec_m_5", "inv_m_burrata", "Fresh Pugliese Burrata", 1.0, "pcs", 275L)
                )
            )
            recipeDao.insertVersion(
                RecipeVersionEntity(
                    versionId = "rv_m_5_v1",
                    recipeId = "rec_m_5",
                    productId = "prod_m_5",
                    versionNumber = 1,
                    totalCostCents = r3Cost,
                    ingredientsSnapshot = "1 pcs Sourdough Pizza Ball, 1 pcs Fresh Burrata",
                    notes = "Initial standard house recipe.",
                    editedBy = "System Chef Spec",
                    editedAt = now
                )
            )

            // Recipe 4: Truffle Parmesan Fries (prod_m_6)
            // 250g potato (240 * 0.25 = 60c) + 20ml aioli (36c) = 96c
            val r4Cost = 96L
            recipeDao.upsertRecipe(
                RecipeEntity(
                    recipeId = "rec_m_6",
                    branchId = MAIN_BRANCH_ID,
                    productId = "prod_m_6",
                    version = 1,
                    notes = "Blanch at 140C, finish crisp at 185C.",
                    totalCostCents = r4Cost,
                    updatedBy = "System Chef Spec",
                    updatedAt = now
                )
            )
            recipeDao.insertIngredients(
                listOf(
                    RecipeIngredientEntity("ri_6_1", "rec_m_6", "inv_m_potatoes", "Idaho Russet Potatoes", 250.0, "g", 240L),
                    RecipeIngredientEntity("ri_6_2", "rec_m_6", "inv_m_truffle_aioli", "Black Truffle Aioli Base", 20.0, "ml", 1800L)
                )
            )
            recipeDao.insertVersion(
                RecipeVersionEntity(
                    versionId = "rv_m_6_v1",
                    recipeId = "rec_m_6",
                    productId = "prod_m_6",
                    versionNumber = 1,
                    totalCostCents = r4Cost,
                    ingredientsSnapshot = "250 g Russet Potatoes, 20 ml Truffle Aioli",
                    notes = "Initial standard house recipe.",
                    editedBy = "System Chef Spec",
                    editedAt = now
                )
            )

            // Downtown Recipe: Gold Wagyu Smash
            recipeDao.upsertRecipe(
                RecipeEntity(
                    recipeId = "rec_d_1",
                    branchId = DOWNTOWN_BRANCH_ID,
                    productId = "prod_d_1",
                    version = 1,
                    notes = "Downtown flagship spec: 1 wagyu patty + 1 brioche bun.",
                    totalCostCents = 350L,
                    updatedBy = "System Chef Spec",
                    updatedAt = now
                )
            )
            recipeDao.insertIngredients(
                listOf(
                    RecipeIngredientEntity("ri_d1_1", "rec_d_1", "inv_d_patty", "Wagyu Beef Patty (180g)", 1.0, "pcs", 285L),
                    RecipeIngredientEntity("ri_d1_2", "rec_d_1", "inv_d_brioche", "Artisan Brioche Buns", 1.0, "pcs", 65L)
                )
            )
            recipeDao.insertVersion(
                RecipeVersionEntity(
                    versionId = "rv_d_1_v1",
                    recipeId = "rec_d_1",
                    productId = "prod_d_1",
                    versionNumber = 1,
                    totalCostCents = 350L,
                    ingredientsSnapshot = "1 pcs Wagyu Beef Patty, 1 pcs Brioche Bun",
                    notes = "Initial Downtown branch recipe.",
                    editedBy = "System Chef Spec",
                    editedAt = now
                )
            )
        }
    }

    private suspend fun seedSampleDashboardData() {
        val dashboardDao = database.dashboardDao()
        val now = System.currentTimeMillis()
        val todayStr = SimpleDateFormat("EEE, dd MMM yyyy", Locale.US).format(Date(now))

        dashboardDao.upsertSnapshots(
            listOf(
                DashboardSnapshotEntity(
                    branchId = MAIN_BRANCH_ID,
                    businessDate = todayStr,
                    todaySales = 4825.50,
                    todayOrders = 142,
                    totalCustomers = 118,
                    pendingKitchenOrders = 7,
                    lowStockAlerts = 4,
                    dailyExpenses = 1290.00,
                    estimatedProfit = 3535.50,
                    cashPayments = 1640.00,
                    digitalPayments = 3185.50,
                    isSampleData = true,
                    updatedAt = now
                ),
                DashboardSnapshotEntity(
                    branchId = DOWNTOWN_BRANCH_ID,
                    businessDate = todayStr,
                    todaySales = 2940.75,
                    todayOrders = 94,
                    totalCustomers = 81,
                    pendingKitchenOrders = 4,
                    lowStockAlerts = 2,
                    dailyExpenses = 840.25,
                    estimatedProfit = 2100.50,
                    cashPayments = 910.25,
                    digitalPayments = 2030.50,
                    isSampleData = true,
                    updatedAt = now
                )
            )
        )

        dashboardDao.upsertHourlySales(
            listOf(
                HourlySalesEntity("m1", MAIN_BRANCH_ID, 1, "10a", 310.0, 11, true),
                HourlySalesEntity("m2", MAIN_BRANCH_ID, 2, "12p", 980.0, 31, true),
                HourlySalesEntity("m3", MAIN_BRANCH_ID, 3, "2p", 740.0, 22, true),
                HourlySalesEntity("m4", MAIN_BRANCH_ID, 4, "4p", 495.5, 16, true),
                HourlySalesEntity("m5", MAIN_BRANCH_ID, 5, "6p", 1120.0, 30, true),
                HourlySalesEntity("m6", MAIN_BRANCH_ID, 6, "8p", 1180.0, 32, true),
                HourlySalesEntity("d1", DOWNTOWN_BRANCH_ID, 1, "10a", 190.0, 7, true),
                HourlySalesEntity("d2", DOWNTOWN_BRANCH_ID, 2, "12p", 640.0, 21, true),
                HourlySalesEntity("d3", DOWNTOWN_BRANCH_ID, 3, "2p", 450.75, 15, true),
                HourlySalesEntity("d4", DOWNTOWN_BRANCH_ID, 4, "4p", 320.0, 10, true),
                HourlySalesEntity("d5", DOWNTOWN_BRANCH_ID, 5, "6p", 690.0, 21, true),
                HourlySalesEntity("d6", DOWNTOWN_BRANCH_ID, 6, "8p", 650.0, 20, true)
            )
        )

        dashboardDao.upsertLowStockAlerts(
            listOf(
                LowStockAlertEntity("ls1", MAIN_BRANCH_ID, "Prime Beef Patty (180g)", 14.0, 30.0, "pcs", true),
                LowStockAlertEntity("ls2", MAIN_BRANCH_ID, "Aged Cheddar Slices", 1.8, 5.0, "kg", true),
                LowStockAlertEntity("ls3", MAIN_BRANCH_ID, "Brioche Burger Buns", 22.0, 48.0, "pcs", true),
                LowStockAlertEntity("ls4", MAIN_BRANCH_ID, "Truffle Aioli Base", 0.9, 2.5, "L", true),
                LowStockAlertEntity("ls5", DOWNTOWN_BRANCH_ID, "Artisan Mozzarella", 2.1, 4.0, "kg", true),
                LowStockAlertEntity("ls6", DOWNTOWN_BRANCH_ID, "Espresso Arabica Beans", 1.2, 3.0, "kg", true)
            )
        )

        dashboardDao.upsertTransactions(
            listOf(
                TransactionRecordEntity("tx1", MAIN_BRANCH_ID, "ORD-1042", "INV-2026-042", "Table 04 • Walk-in", 86.50, "Card", "Paid", "11:52 AM", true, now - 120_000),
                TransactionRecordEntity("tx2", MAIN_BRANCH_ID, "ORD-1041", "INV-2026-041", "Zara Khan", 42.00, "Digital", "Paid", "11:44 AM", true, now - 360_000),
                TransactionRecordEntity("tx3", MAIN_BRANCH_ID, "ORD-1040", "INV-2026-040", "Table 09 • Dine-in", 124.75, "Split", "Paid", "11:31 AM", true, now - 720_000),
                TransactionRecordEntity("tx4", MAIN_BRANCH_ID, "ORD-1039", "INV-2026-039", "Takeaway #19", 29.50, "Cash", "Paid", "11:18 AM", true, now - 1_200_000),
                TransactionRecordEntity("tx5", DOWNTOWN_BRANCH_ID, "ORD-2019", "INV-DT-019", "Table 02 • Dine-in", 64.25, "Digital", "Paid", "11:47 AM", true, now - 180_000),
                TransactionRecordEntity("tx6", DOWNTOWN_BRANCH_ID, "ORD-2018", "INV-DT-018", "Omar Farooq", 38.50, "Cash", "Paid", "11:25 AM", true, now - 900_000)
            )
        )
    }

    private suspend fun seedPhase4SuppliersAndPurchases() {
        val supplierDao = database.supplierDao()
        val purchaseDao = database.purchaseDao()
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L

        val suppliers = listOf(
            SupplierEntity(
                supplierId = "sup_m_butcher",
                branchId = MAIN_BRANCH_ID,
                name = "Prime Cut Meats & Wagyu Co.",
                contactPerson = "Marcus Vance",
                phone = "+1-555-3101",
                email = "orders@primecutwagyu.example.com",
                address = "74 Butcher Row, Wholesale Market",
                category = "Meat & Poultry",
                openingBalanceCents = 0L,
                totalPurchasesCents = 42750L,
                totalReturnsCents = 0L,
                totalPaidCents = 30000L,
                isActive = true,
                createdAt = now - 60 * dayMs
            ),
            SupplierEntity(
                supplierId = "sup_m_bakery",
                branchId = MAIN_BRANCH_ID,
                name = "Golden Hearth Artisan Bakery",
                contactPerson = "Elena Rossi",
                phone = "+1-555-3102",
                email = "supply@goldenhearth.example.com",
                address = "19 Millstone Ave",
                category = "Bakery & Dry Goods",
                openingBalanceCents = 5000L,
                totalPurchasesCents = 13250L,
                totalReturnsCents = 0L,
                totalPaidCents = 13250L,
                isActive = true,
                createdAt = now - 45 * dayMs
            ),
            SupplierEntity(
                supplierId = "sup_m_dairy",
                branchId = MAIN_BRANCH_ID,
                name = "Alpine Reserve Dairy & Cheeses",
                contactPerson = "Henrik Lind",
                phone = "+1-555-3103",
                email = "dispatch@alpinedairy.example.com",
                address = "302 Coldchain Park",
                category = "Dairy & Produce",
                openingBalanceCents = 0L,
                totalPurchasesCents = 0L,
                totalReturnsCents = 0L,
                totalPaidCents = 0L,
                isActive = true,
                createdAt = now - 30 * dayMs
            ),
            SupplierEntity(
                supplierId = "sup_d_butcher",
                branchId = DOWNTOWN_BRANCH_ID,
                name = "Prime Cut Meats & Wagyu Co.",
                contactPerson = "Marcus Vance",
                phone = "+1-555-3101",
                email = "orders@primecutwagyu.example.com",
                address = "74 Butcher Row, Wholesale Market",
                category = "Meat & Poultry",
                openingBalanceCents = 0L,
                totalPurchasesCents = 28500L,
                totalReturnsCents = 0L,
                totalPaidCents = 15000L,
                isActive = true,
                createdAt = now - 35 * dayMs
            )
        )
        supplierDao.upsertSuppliers(suppliers)

        if (purchaseDao.countAllPurchases() == 0) {
            val po1Id = "po_m_seed_1"
            purchaseDao.upsertPurchaseOrder(
                PurchaseOrderEntity(
                    purchaseId = po1Id,
                    branchId = MAIN_BRANCH_ID,
                    idempotencyKey = "seed_po_1",
                    poNumber = "PO-2601-0001",
                    supplierInvoiceNumber = "SUPINV-8812",
                    supplierId = "sup_m_butcher",
                    supplierName = "Prime Cut Meats & Wagyu Co.",
                    status = "RECEIVED",
                    paymentStatus = "PARTIALLY_PAID",
                    subtotalCents = 42750L,
                    discountCents = 0L,
                    taxCents = 0L,
                    totalCents = 42750L,
                    paidCents = 30000L,
                    returnedCents = 0L,
                    notes = "Weekly chilled wagyu & lamb delivery.",
                    createdByUid = "seed_mgr",
                    createdByName = "Branch Manager",
                    approvedByName = "Owner Admin",
                    expectedDateMillis = now - 2 * dayMs,
                    createdAt = now - 4 * dayMs,
                    updatedAt = now - 2 * dayMs
                )
            )
            purchaseDao.upsertPurchaseItems(
                listOf(
                    PurchaseItemEntity(
                        lineId = "pi_seed_1_1",
                        purchaseId = po1Id,
                        inventoryItemId = "inv_m_patty",
                        itemName = "Wagyu Beef Patty (180g)",
                        itemType = "RAW_MATERIAL",
                        unit = "pcs",
                        orderedQty = 100.0,
                        receivedQty = 100.0,
                        returnedQty = 0.0,
                        unitPriceCents = 285L,
                        lineTotalCents = 28500L
                    ),
                    PurchaseItemEntity(
                        lineId = "pi_seed_1_2",
                        purchaseId = po1Id,
                        inventoryItemId = "inv_m_lamb",
                        itemName = "Herb-Crusted Lamb Rack",
                        itemType = "RAW_MATERIAL",
                        unit = "kg",
                        orderedQty = 6.0,
                        receivedQty = 6.0,
                        returnedQty = 0.0,
                        unitPriceCents = 2375L,
                        lineTotalCents = 14250L
                    )
                )
            )
            purchaseDao.insertReceivingIfNew(
                PurchaseReceivingEntity(
                    receivingId = "rec_seed_1",
                    branchId = MAIN_BRANCH_ID,
                    purchaseId = po1Id,
                    lineId = "pi_seed_1_1",
                    inventoryItemId = "inv_m_patty",
                    itemName = "Wagyu Beef Patty (180g)",
                    receivingKey = "seed_recv_1",
                    referenceNumber = "GRN-0001",
                    receivedQty = 100.0,
                    unit = "pcs",
                    receivedDateMillis = now - 2 * dayMs,
                    receivedByName = "Inventory Manager",
                    notes = "Temperature verified 2.1C"
                )
            )
            supplierDao.insertPaymentIfNew(
                SupplierPaymentEntity(
                    paymentId = "spay_seed_1",
                    branchId = MAIN_BRANCH_ID,
                    idempotencyKey = "seed_spay_1",
                    supplierId = "sup_m_butcher",
                    supplierName = "Prime Cut Meats & Wagyu Co.",
                    purchaseId = po1Id,
                    poNumber = "PO-2601-0001",
                    amountCents = 30000L,
                    paymentMethod = "Bank Transfer",
                    referenceNumber = "ACH-99402",
                    paymentDateMillis = now - dayMs,
                    notes = "First installment on SUPINV-8812",
                    paidByName = "Owner Admin",
                    createdAt = now - dayMs
                )
            )

            // Approved PO ready for receiving demo in Main branch
            val po2Id = "po_m_seed_2"
            purchaseDao.upsertPurchaseOrder(
                PurchaseOrderEntity(
                    purchaseId = po2Id,
                    branchId = MAIN_BRANCH_ID,
                    idempotencyKey = "seed_po_2",
                    poNumber = "PO-2601-0002",
                    supplierInvoiceNumber = "PENDING",
                    supplierId = "sup_m_dairy",
                    supplierName = "Alpine Reserve Dairy & Cheeses",
                    status = "APPROVED",
                    paymentStatus = "UNPAID",
                    subtotalCents = 14600L,
                    discountCents = 0L,
                    taxCents = 0L,
                    totalCents = 14600L,
                    paidCents = 0L,
                    returnedCents = 0L,
                    notes = "Restock low-stock aged cheddar & truffle aioli.",
                    createdByUid = "seed_inv",
                    createdByName = "Inventory Manager",
                    approvedByName = "Branch Manager",
                    expectedDateMillis = now + dayMs,
                    createdAt = now - dayMs,
                    updatedAt = now - dayMs
                )
            )
            purchaseDao.upsertPurchaseItems(
                listOf(
                    PurchaseItemEntity(
                        lineId = "pi_seed_2_1",
                        purchaseId = po2Id,
                        inventoryItemId = "inv_m_cheddar",
                        itemName = "Aged Cheddar Cheese",
                        itemType = "RAW_MATERIAL",
                        unit = "kg",
                        orderedQty = 6.0,
                        receivedQty = 0.0,
                        returnedQty = 0.0,
                        unitPriceCents = 1400L,
                        lineTotalCents = 8400L
                    ),
                    PurchaseItemEntity(
                        lineId = "pi_seed_2_2",
                        purchaseId = po2Id,
                        inventoryItemId = "inv_m_truffle_aioli",
                        itemName = "Black Truffle Aioli Base",
                        itemType = "RAW_MATERIAL",
                        unit = "L",
                        orderedQty = 3.5,
                        receivedQty = 0.0,
                        returnedQty = 0.0,
                        unitPriceCents = 1771L,
                        lineTotalCents = 6200L
                    )
                )
            )

            // Downtown PO
            val po3Id = "po_d_seed_1"
            purchaseDao.upsertPurchaseOrder(
                PurchaseOrderEntity(
                    purchaseId = po3Id,
                    branchId = DOWNTOWN_BRANCH_ID,
                    idempotencyKey = "seed_po_d1",
                    poNumber = "PO-DT-0001",
                    supplierInvoiceNumber = "SUPINV-DT-11",
                    supplierId = "sup_d_butcher",
                    supplierName = "Prime Cut Meats & Wagyu Co.",
                    status = "RECEIVED",
                    paymentStatus = "PARTIALLY_PAID",
                    subtotalCents = 28500L,
                    discountCents = 0L,
                    taxCents = 0L,
                    totalCents = 28500L,
                    paidCents = 15000L,
                    returnedCents = 0L,
                    notes = "Downtown initial patty restock.",
                    createdByUid = "seed_mgr",
                    createdByName = "Branch Manager",
                    approvedByName = "Owner Admin",
                    expectedDateMillis = now - 3 * dayMs,
                    createdAt = now - 4 * dayMs,
                    updatedAt = now - 3 * dayMs
                )
            )
            purchaseDao.upsertPurchaseItems(
                listOf(
                    PurchaseItemEntity(
                        lineId = "pi_seed_d1_1",
                        purchaseId = po3Id,
                        inventoryItemId = "inv_d_patty",
                        itemName = "Wagyu Beef Patty (180g)",
                        itemType = "RAW_MATERIAL",
                        unit = "pcs",
                        orderedQty = 100.0,
                        receivedQty = 100.0,
                        returnedQty = 0.0,
                        unitPriceCents = 285L,
                        lineTotalCents = 28500L
                    )
                )
            )
            supplierDao.insertPaymentIfNew(
                SupplierPaymentEntity(
                    paymentId = "spay_seed_d1",
                    branchId = DOWNTOWN_BRANCH_ID,
                    idempotencyKey = "seed_spay_d1",
                    supplierId = "sup_d_butcher",
                    supplierName = "Prime Cut Meats & Wagyu Co.",
                    purchaseId = po3Id,
                    poNumber = "PO-DT-0001",
                    amountCents = 15000L,
                    paymentMethod = "Bank Transfer",
                    referenceNumber = "ACH-DT-102",
                    paymentDateMillis = now - 2 * dayMs,
                    notes = "Partial payment",
                    paidByName = "Owner Admin",
                    createdAt = now - 2 * dayMs
                )
            )
        }
    }

    private suspend fun seedPhase4LoyaltyAndFeedback() {
        val loyaltyDao = database.customerLoyaltyDao()
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L

        listOf(MAIN_BRANCH_ID, DOWNTOWN_BRANCH_ID).forEach { bId ->
            loyaltyDao.upsertLoyaltyConfig(
                LoyaltyConfigEntity(
                    branchId = bId,
                    earnRatePerDollar = 1.0,
                    redeemCentsPerPoint = 5L, // 100 pts = $5.00
                    minRedeemPoints = 50,
                    silverThresholdPoints = 150,
                    goldThresholdPoints = 400,
                    platinumThresholdPoints = 800,
                    expiryEnabled = false,
                    expiryDays = 365,
                    updatedBy = "System Default",
                    updatedAt = now
                )
            )
        }

        if (loyaltyDao.countRewards() == 0) {
            loyaltyDao.upsertRewards(
                listOf(
                    LoyaltyRewardEntity("rew_m_1", MAIN_BRANCH_ID, "Complimentary Saffron Cooler", "Redeem for any signature beverage up to PKR 600.00", 100, 550L, true),
                    LoyaltyRewardEntity("rew_m_2", MAIN_BRANCH_ID, "PKR 10.00 Dining Credit", "Apply PKR 10.00 discount directly to any dine-in or takeaway bill", 200, 1000L, true),
                    LoyaltyRewardEntity("rew_m_3", MAIN_BRANCH_ID, "PKR 25.00 Chef Table Voucher", "VIP voucher for grill platters & gourmet burgers", 450, 2500L, true),
                    LoyaltyRewardEntity("rew_d_1", DOWNTOWN_BRANCH_ID, "PKR 10.00 Bistro Credit", "Apply PKR 10.00 discount at Downtown Bistro", 200, 1000L, true)
                )
            )
        }

        loyaltyDao.insertLoyaltyTransactionIfNew(
            LoyaltyTransactionEntity(
                txId = "ltx_seed_1",
                branchId = MAIN_BRANCH_ID,
                customerId = "cust_1",
                customerName = "Zara Khan",
                idempotencyKey = "seed_ltx_1",
                txType = "EARN",
                pointsDelta = 420,
                orderId = null,
                reason = "Opening Gold VIP earned points balance",
                approvedBy = "System",
                actorName = "System",
                createdAt = now - 5 * dayMs
            )
        )
        loyaltyDao.insertLoyaltyTransactionIfNew(
            LoyaltyTransactionEntity(
                txId = "ltx_seed_2",
                branchId = MAIN_BRANCH_ID,
                customerId = "cust_2",
                customerName = "Omar Farooq",
                idempotencyKey = "seed_ltx_2",
                txType = "EARN",
                pointsDelta = 190,
                orderId = null,
                reason = "Opening Silver member points balance",
                approvedBy = "System",
                actorName = "System",
                createdAt = now - 4 * dayMs
            )
        )
        loyaltyDao.insertFeedback(
            CustomerFeedbackEntity(
                feedbackId = "fb_seed_1",
                branchId = MAIN_BRANCH_ID,
                customerId = "cust_1",
                orderId = null,
                rating = 5,
                comment = "Gold Wagyu Smash was seared perfectly and arrived piping hot.",
                recordedBy = "Branch Manager",
                createdAt = now - dayMs
            )
        )
    }

    private suspend fun seedPhase4Expenses() {
        val expenseDao = database.expenseDao()
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L

        expenseDao.upsertExpenses(
            listOf(
                ExpenseEntity(
                    expenseId = "exp_m_1",
                    branchId = MAIN_BRANCH_ID,
                    idempotencyKey = "seed_exp_m_1",
                    category = "Electricity",
                    amountCents = 42000L,
                    expenseDateMillis = now,
                    description = "Commercial kitchen HVAC & cold-room electricity",
                    paymentMethod = "Bank Transfer",
                    approvalStatus = "APPROVED",
                    approvedByName = "Owner Admin",
                    isRecurring = true,
                    recurrenceFrequency = "MONTHLY",
                    createdByUid = "seed_mgr",
                    createdByName = "Branch Manager",
                    createdAt = now - 3600_000L,
                    updatedAt = now - 3600_000L
                ),
                ExpenseEntity(
                    expenseId = "exp_m_2",
                    branchId = MAIN_BRANCH_ID,
                    idempotencyKey = "seed_exp_m_2",
                    category = "Packaging",
                    amountCents = 18500L,
                    expenseDateMillis = now - dayMs,
                    description = "Branded foil burger wraps & takeaway Kraft bags",
                    paymentMethod = "Card",
                    approvalStatus = "APPROVED",
                    approvedByName = "Branch Manager",
                    isRecurring = false,
                    recurrenceFrequency = "NONE",
                    createdByUid = "seed_mgr",
                    createdByName = "Branch Manager",
                    createdAt = now - dayMs,
                    updatedAt = now - dayMs
                ),
                ExpenseEntity(
                    expenseId = "exp_m_3",
                    branchId = MAIN_BRANCH_ID,
                    idempotencyKey = "seed_exp_m_3",
                    category = "Maintenance",
                    amountCents = 24000L,
                    expenseDateMillis = now - 3 * dayMs,
                    description = "Charcoal grill exhaust hood deep-clean service",
                    paymentMethod = "Cash",
                    approvalStatus = "PENDING",
                    approvedByName = null,
                    isRecurring = false,
                    recurrenceFrequency = "NONE",
                    createdByUid = "seed_mgr",
                    createdByName = "Branch Manager",
                    createdAt = now - 3 * dayMs,
                    updatedAt = now - 3 * dayMs
                ),
                ExpenseEntity(
                    expenseId = "exp_d_1",
                    branchId = DOWNTOWN_BRANCH_ID,
                    idempotencyKey = "seed_exp_d_1",
                    category = "Rent",
                    amountCents = 185000L,
                    expenseDateMillis = now - 2 * dayMs,
                    description = "Downtown Bistro monthly lease installment",
                    paymentMethod = "Bank Transfer",
                    approvalStatus = "APPROVED",
                    approvedByName = "Owner Admin",
                    isRecurring = true,
                    recurrenceFrequency = "MONTHLY",
                    createdByUid = "seed_owner",
                    createdByName = "Owner Admin",
                    createdAt = now - 2 * dayMs,
                    updatedAt = now - 2 * dayMs
                )
            )
        )
    }

    private suspend fun seedPhase5HrPolicies() {
        val staffDao = database.staffDao()
        val now = System.currentTimeMillis()
        staffDao.upsertHrPolicies(
            listOf(
                HrPermissionPolicyEntity("SUPER_ADMIN", true, true, true, true, true, true, true, true, "System Default", now),
                HrPermissionPolicyEntity("OWNER", true, true, true, true, true, true, true, true, "System Default", now),
                HrPermissionPolicyEntity("BRANCH_MANAGER", true, true, true, true, true, true, true, true, "System Default", now),
                HrPermissionPolicyEntity("HR", true, true, true, true, true, true, true, true, "System Default", now),
                HrPermissionPolicyEntity("CASHIER", false, false, false, false, false, false, false, false, "System Default", now),
                HrPermissionPolicyEntity("KITCHEN_STAFF", false, false, false, false, false, false, false, false, "System Default", now),
                HrPermissionPolicyEntity("INVENTORY_MANAGER", false, false, false, false, false, false, false, false, "System Default", now),
                HrPermissionPolicyEntity("PURCHASE_STAFF", false, false, false, false, false, false, false, false, "System Default", now)
            )
        )
    }

    private suspend fun seedPhase5ShiftTemplates() {
        val dao = database.shiftAttendanceDao()
        val now = System.currentTimeMillis()
        val templates = listOf(MAIN_BRANCH_ID, DOWNTOWN_BRANCH_ID).flatMap { bId ->
            val tag = if (bId == MAIN_BRANCH_ID) "m" else "d"
            listOf(
                ShiftDefinitionEntity(
                    shiftId = "shift_${tag}_morning",
                    branchId = bId,
                    shiftName = "Morning Opening Shift",
                    startMinuteOfDay = 8 * 60,
                    endMinuteOfDay = 16 * 60,
                    isOvernight = false,
                    breakDurationMinutes = 30,
                    graceLateMinutes = 10,
                    regularHoursThresholdMinutes = 480,
                    overtimeMultiplier = 1.5,
                    isActive = true,
                    createdAt = now
                ),
                ShiftDefinitionEntity(
                    shiftId = "shift_${tag}_evening",
                    branchId = bId,
                    shiftName = "Evening Dinner Shift",
                    startMinuteOfDay = 15 * 60,
                    endMinuteOfDay = 23 * 60,
                    isOvernight = false,
                    breakDurationMinutes = 30,
                    graceLateMinutes = 10,
                    regularHoursThresholdMinutes = 480,
                    overtimeMultiplier = 1.5,
                    isActive = true,
                    createdAt = now
                ),
                ShiftDefinitionEntity(
                    shiftId = "shift_${tag}_night",
                    branchId = bId,
                    shiftName = "Late Night Overnight Shift",
                    startMinuteOfDay = 22 * 60,
                    endMinuteOfDay = 6 * 60,
                    isOvernight = true,
                    breakDurationMinutes = 30,
                    graceLateMinutes = 10,
                    regularHoursThresholdMinutes = 480,
                    overtimeMultiplier = 1.5,
                    isActive = true,
                    createdAt = now
                )
            )
        }
        dao.upsertShifts(templates)
    }

    private suspend fun seedPhase7SecurityDefaults() {
        val now = System.currentTimeMillis()
        val policies = com.example.domain.model.UserRole.entries.map { role ->
            val caps = com.example.domain.model.RoleAccessControl.capabilitiesFor(role)
            RolePermissionPolicyEntity(
                roleCode = role.code,
                canTriggerCloudSync = caps.canTriggerCloudSync,
                canResolveSyncConflicts = caps.canResolveSyncConflicts,
                canResolveFinancialConflicts = caps.canResolveFinancialConflicts,
                canCreateBackup = caps.canCreateBackup,
                canRestoreBackup = caps.canRestoreBackup,
                canManageDevices = caps.canManageDevices,
                canManageUserPermissions = caps.canManageUserPermissions,
                canApproveFinancialAdjustments = caps.canApproveFinancialAdjustments,
                canExportReports = caps.canExportReports,
                updatedBy = "SYSTEM_SEED",
                updatedAt = now
            )
        }
        database.phase7Dao().upsertRolePolicies(policies)
        database.phase7Dao().insertNotificationIfNew(
            ErpNotificationEntity(
                notificationId = "notif_seed_p7_firebase_setup",
                branchId = MAIN_BRANCH_ID,
                category = "SECURITY",
                severity = "WARNING",
                title = "Firebase Project Link Pending (aa-crave-erp)",
                message = "Offline Room DB is active. Add app/google-services.json for com.aistudio.aacrave.erpzqx and publish database.rules.json to enable live RTDB sync.",
                relatedEntityType = "FIREBASE_CONFIG",
                relatedEntityId = com.example.domain.model.FirebaseCloudConstants.PROJECT_ID,
                isRead = false,
                createdAt = now
            )
        )
    }

    companion object {
        const val MAIN_BRANCH_ID = "branch_main_01"
        const val DOWNTOWN_BRANCH_ID = "branch_dt_02"

        suspend fun ensureChartOfAccountsForBranch(
            database: AaCraveDatabase,
            branchId: String,
            openingCashCents: Long = 0L,
            openingBankCents: Long = 0L
        ) {
            val accountingDao = database.accountingDao()
            if (accountingDao.countAccountsForBranch(branchId) > 0) return
            val now = System.currentTimeMillis()
            val equityOpening = (openingCashCents + openingBankCents).coerceAtLeast(0L)
            val defaults = listOf(
                ChartOfAccountEntity("coa_${branchId}_1010", branchId, "1010", "Cash on Hand", "ASSET", "CASH", true, openingCashCents, true, true, now),
                ChartOfAccountEntity("coa_${branchId}_1020", branchId, "1020", "Bank Operating Account", "ASSET", "BANK", true, openingBankCents, true, true, now),
                ChartOfAccountEntity("coa_${branchId}_1100", branchId, "1100", "Accounts Receivable", "ASSET", "ACCOUNTS_RECEIVABLE", false, 0L, true, true, now),
                ChartOfAccountEntity("coa_${branchId}_1200", branchId, "1200", "Kitchen & Goods Inventory", "ASSET", "INVENTORY", false, 0L, true, true, now),
                ChartOfAccountEntity("coa_${branchId}_2010", branchId, "2010", "Accounts Payable", "LIABILITY", "ACCOUNTS_PAYABLE", false, 0L, true, true, now),
                ChartOfAccountEntity("coa_${branchId}_2100", branchId, "2100", "Sales Tax Payable", "LIABILITY", "TAX_PAYABLE", false, 0L, true, true, now),
                ChartOfAccountEntity("coa_${branchId}_2200", branchId, "2200", "Payroll & Wages Payable", "LIABILITY", "PAYROLL_PAYABLE", false, 0L, true, true, now),
                ChartOfAccountEntity("coa_${branchId}_3000", branchId, "3000", "Owner Capital & Equity", "EQUITY", "OWNER_EQUITY", false, equityOpening, true, true, now),
                ChartOfAccountEntity("coa_${branchId}_4010", branchId, "4010", "Restaurant Sales Revenue", "INCOME", "SALES_REVENUE", false, 0L, true, true, now),
                ChartOfAccountEntity("coa_${branchId}_4090", branchId, "4090", "Other Operating Income", "INCOME", "OTHER_INCOME", false, 0L, true, true, now),
                ChartOfAccountEntity("coa_${branchId}_5010", branchId, "5010", "Purchases & Cost of Goods", "EXPENSE", "COGS", false, 0L, true, true, now),
                ChartOfAccountEntity("coa_${branchId}_5100", branchId, "5100", "Operating Expenses", "EXPENSE", "OPERATING_EXPENSE", false, 0L, true, true, now),
                ChartOfAccountEntity("coa_${branchId}_5200", branchId, "5200", "Salaries & Payroll Expense", "EXPENSE", "PAYROLL_EXPENSE", false, 0L, true, true, now)
            )
            accountingDao.insertAccountsIfNew(defaults)
        }
    }

    private suspend fun seedSmartLoyaltyDefaults() {
        val smartDao = database.smartLoyaltyDao()
        val branches = database.branchDao().getAllBranches()
        val now = System.currentTimeMillis()
        branches.forEach { branch ->
            smartDao.insertSettingsIfNew(
                LoyaltyProgramSettingsEntity(
                    branchId = branch.branchId,
                    updatedBy = "System Default",
                    updatedAt = now
                )
            )
        }
    }
}
