package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AaCraveDatabase
import com.example.data.local.DatabaseSeeder
import com.example.data.repository.PosRepository
import com.example.domain.model.BillingCalculator
import com.example.domain.model.CartItem
import com.example.domain.model.KitchenKotDocument
import com.example.domain.model.OrderPriority
import com.example.domain.model.OrderStatus
import com.example.domain.model.OrderType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PosProduct
import com.example.domain.model.ProductAddon
import com.example.domain.model.ProductVariation
import com.example.domain.model.SplitPaymentBreakdown
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AaCravePhase2Test {

    private lateinit var database: AaCraveDatabase
    private lateinit var posRepository: PosRepository

    private val cashierSession = UserSession(
        uid = "cashier_1",
        email = "cashier@aacrave.local",
        displayName = "Ali Cashier",
        role = UserRole.CASHIER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    private val managerSession = UserSession(
        uid = "mgr_1",
        email = "manager@aacrave.local",
        displayName = "Sara Manager",
        role = UserRole.BRANCH_MANAGER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AaCraveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseSeeder(database).ensureSeeded()
        posRepository = PosRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sampleCartItems(): List<CartItem> {
        val product = PosProduct(
            productId = "prod_m_1",
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            categoryId = "cat_m_burgers",
            name = "AA Crave Gold Wagyu Smash",
            description = "Double seared wagyu",
            basePriceCents = 1450L,
            sku = "BRG-001",
            categoryTag = "Signature Burgers",
            isAvailable = true,
            taxExempt = false,
            variations = emptyList(),
            addons = emptyList()
        )
        val variation = ProductVariation("var_2", "prod_m_1", "Double Wagyu", 450L, false)
        val addon = ProductAddon("add_1", "prod_m_1", "Extra Aged Cheddar", 150L)

        // Unit price = 1450 + 450 + 150 = 2050 cents; Qty 2 = 4100 cents
        return listOf(
            CartItem(
                cartLineId = "line_1",
                product = product,
                selectedVariation = variation,
                selectedAddons = listOf(addon),
                specialInstructions = "Medium well, no pickles",
                quantity = 2
            )
        )
    }

    @Test
    fun `billing calculator computes exact subtotal discount tax service charge and total`() {
        val items = sampleCartItems()
        val totals = BillingCalculator.calculate(
            items = items,
            discountPercent = 10.0,
            manualDiscountCents = 0L,
            taxRatePercent = 8.0,
            serviceChargeRatePercent = 5.0
        )

        // Subtotal = 4100; 10% discount = 410; Net = 3690
        // Tax 8% of 3690 = 295.2 -> 295; Service 5% of 3690 = 184.5 -> 185
        // Total = 3690 + 295 + 185 = 4170
        assertEquals(4100L, totals.subtotalCents)
        assertEquals(410L, totals.discountCents)
        assertEquals(3690L, totals.taxableAmountCents)
        assertEquals(295L, totals.taxCents)
        assertEquals(185L, totals.serviceChargeCents)
        assertEquals(4170L, totals.totalPayableCents)
    }

    @Test
    fun `placeOrder generates separate invoice and kot numbers and prevents duplicate billing`() = runTest {
        val items = sampleCartItems()
        val totals = BillingCalculator.calculate(items, 0.0, 0L, 8.0, 5.0)
        val idempotencyKey = "idem-test-001"

        val first = posRepository.placeOrder(
            session = cashierSession,
            idempotencyKey = idempotencyKey,
            cartItems = items,
            totals = totals,
            orderType = OrderType.DINE_IN,
            tableNumber = "T-04",
            priority = OrderPriority.RUSH,
            customer = null,
            paymentMethod = PaymentMethod.CASH,
            splitBreakdown = SplitPaymentBreakdown(cashCents = totals.totalPayableCents)
        ).getOrThrow()

        // Duplicate submit with same idempotencyKey must return original without creating a second order
        val duplicate = posRepository.placeOrder(
            session = cashierSession,
            idempotencyKey = idempotencyKey,
            cartItems = items,
            totals = totals,
            orderType = OrderType.DINE_IN,
            tableNumber = "T-04",
            priority = OrderPriority.RUSH,
            customer = null,
            paymentMethod = PaymentMethod.CASH,
            splitBreakdown = SplitPaymentBreakdown(cashCents = totals.totalPayableCents)
        ).getOrThrow()

        assertEquals(first.orderId, duplicate.orderId)
        assertEquals(first.invoiceNumber, duplicate.invoiceNumber)
        assertEquals(first.kotNumber, duplicate.kotNumber)
        assertNotEquals(first.invoiceNumber, first.kotNumber)
        assertTrue(first.invoiceNumber.startsWith("INV-"))
        assertTrue(first.kotNumber.startsWith("KOT-"))

        val ordersInRoom = posRepository.observeOrders(DatabaseSeeder.MAIN_BRANCH_ID).first()
        assertEquals(1, ordersInRoom.size)

        val kotTickets = database.posDao().observeKotTickets(DatabaseSeeder.MAIN_BRANCH_ID).first()
        assertEquals(1, kotTickets.size)
        assertEquals("NEW", kotTickets.first().status)
    }

    @Test
    fun `kitchen kot document strictly excludes payment totals taxes and discounts`() = runTest {
        val items = sampleCartItems()
        val totals = BillingCalculator.calculate(items, 10.0, 0L, 8.0, 5.0)

        val order = posRepository.placeOrder(
            session = cashierSession,
            idempotencyKey = "idem-kot-sep",
            cartItems = items,
            totals = totals,
            orderType = OrderType.DINE_IN,
            tableNumber = "T-07",
            priority = OrderPriority.VIP,
            customer = null,
            paymentMethod = PaymentMethod.SPLIT,
            splitBreakdown = SplitPaymentBreakdown(cashCents = 2000L, cardCents = 2170L)
        ).getOrThrow()

        val invoice = posRepository.buildCustomerInvoice(order, "140 Grand Blvd", false)
        val kot = posRepository.buildKitchenKot(order, false)

        assertEquals(order.invoiceNumber, invoice.invoiceNumber)
        assertEquals(4170L, invoice.totalPayableCents)
        assertEquals(order.kotNumber, kot.kotNumber)
        assertEquals("T-07", kot.tableNumber)
        assertEquals(OrderPriority.VIP, kot.priority)
        assertEquals("Medium well, no pickles", kot.items.first().specialInstructions)

        // Verify KitchenKotDocument has zero financial field names
        val kotFieldNames = KitchenKotDocument::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(kotFieldNames.any { it.contains("price") || it.contains("total") || it.contains("tax") || it.contains("discount") || it.contains("payment") || it.contains("invoice") })
    }

    @Test
    fun `cancellation and refund require manager approval and block cashiers`() = runTest {
        val items = sampleCartItems()
        val totals = BillingCalculator.calculate(items, 0.0, 0L, 8.0, 0.0)
        val order = posRepository.placeOrder(
            session = cashierSession,
            idempotencyKey = "idem-rbac-cancel",
            cartItems = items,
            totals = totals,
            orderType = OrderType.TAKEAWAY,
            tableNumber = null,
            priority = OrderPriority.NORMAL,
            customer = null,
            paymentMethod = PaymentMethod.CARD,
            splitBreakdown = SplitPaymentBreakdown(cardCents = totals.totalPayableCents)
        ).getOrThrow()

        // Cashier must be denied
        val cashierAttempt = posRepository.refundOrder(cashierSession, order.orderId, "Customer changed mind")
        assertTrue(cashierAttempt.isFailure)
        assertTrue(cashierAttempt.exceptionOrNull() is SecurityException)

        // Branch manager succeeds
        val managerAttempt = posRepository.refundOrder(managerSession, order.orderId, "Duplicate kitchen prep delay")
        assertTrue(managerAttempt.isSuccess)

        val updatedOrder = posRepository.observeOrders(DatabaseSeeder.MAIN_BRANCH_ID).first().first()
        assertEquals(OrderStatus.REFUNDED, updatedOrder.status)
        assertEquals(managerSession.displayName, updatedOrder.approvedBy)
    }

    @Test
    fun `daily cash closing reconciles cash sales and records variance`() = runTest {
        val items = sampleCartItems()
        val totals = BillingCalculator.calculate(items, 0.0, 0L, 8.0, 0.0)
        posRepository.placeOrder(
            session = cashierSession,
            idempotencyKey = "idem-cash-close",
            cartItems = items,
            totals = totals,
            orderType = OrderType.TAKEAWAY,
            tableNumber = null,
            priority = OrderPriority.NORMAL,
            customer = null,
            paymentMethod = PaymentMethod.CASH,
            splitBreakdown = SplitPaymentBreakdown(cashCents = totals.totalPayableCents)
        ).getOrThrow()

        val closing = posRepository.submitDailyCashClosing(
            session = managerSession,
            countedCashCents = totals.totalPayableCents - 100L,
            notes = "Shift 1 closing"
        ).getOrThrow()

        assertEquals(totals.totalPayableCents, closing.expectedCashCents)
        assertEquals(-100L, closing.varianceCents)
    }
}
