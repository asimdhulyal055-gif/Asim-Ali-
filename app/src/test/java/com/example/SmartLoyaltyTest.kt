package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AaCraveDatabase
import com.example.data.local.DatabaseSeeder
import com.example.data.repository.AccountingRepository
import com.example.data.repository.BackupPayloadSerializer
import com.example.data.repository.PosRepository
import com.example.data.repository.SmartLoyaltyRepository
import com.example.domain.model.BillingCalculator
import com.example.domain.model.CartItem
import com.example.domain.model.CustomerSummary
import com.example.domain.model.LoyaltyCalendarCalculator
import com.example.domain.model.LoyaltyCardStatus
import com.example.domain.model.LoyaltyProgramSettingsRecord
import com.example.domain.model.LoyaltyRuleConstants
import com.example.domain.model.LoyaltyStampTxType
import com.example.domain.model.LoyaltyVoucherStatus
import com.example.domain.model.OrderPriority
import com.example.domain.model.OrderType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PosProduct
import com.example.domain.model.SplitPaymentBreakdown
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.printing.LoyaltyStatementPdfHelper
import com.example.printing.QrCodeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartLoyaltyTest {

    private lateinit var context: Context
    private lateinit var database: AaCraveDatabase
    private lateinit var smartLoyaltyRepo: SmartLoyaltyRepository
    private lateinit var posRepo: PosRepository
    private lateinit var accountingRepo: AccountingRepository

    private val ownerSession = UserSession(
        uid = "uid_owner",
        email = "owner@aacrave.com",
        displayName = "Owner Admin",
        role = UserRole.OWNER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = true,
        isPreviewSandbox = false
    )

    private val managerSession = UserSession(
        uid = "uid_mgr",
        email = "manager@aacrave.com",
        displayName = "Sara Manager",
        role = UserRole.BRANCH_MANAGER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = true,
        isPreviewSandbox = false
    )

    private val cashierSession = UserSession(
        uid = "uid_cashier",
        email = "cashier@aacrave.com",
        displayName = "Bilal Cashier",
        role = UserRole.CASHIER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = true,
        isPreviewSandbox = false
    )

    private val customer1 = CustomerSummary(
        customerId = "cust_1",
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        name = "Zara Khan",
        phone = "+1-555-0142",
        email = "zara.khan@example.com",
        loyaltyPoints = 420,
        tier = "Gold VIP",
        discountPercent = 10
    )

    private val customer3 = CustomerSummary(
        customerId = "cust_3",
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        name = "Amina Patel",
        phone = "+1-555-0193",
        email = "amina.p@example.com",
        loyaltyPoints = 85,
        tier = "Member",
        discountPercent = 0
    )

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AaCraveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseSeeder(database).ensureSeeded()
        smartLoyaltyRepo = SmartLoyaltyRepository(database)
        posRepo = PosRepository(database)
        accountingRepo = AccountingRepository(database)
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun testCart(priceCents: Long, productId: String = "prod_m_1", qty: Int = 1): List<CartItem> {
        return listOf(
            CartItem(
                cartLineId = UUID.randomUUID().toString(),
                product = PosProduct(
                    productId = productId,
                    branchId = DatabaseSeeder.MAIN_BRANCH_ID,
                    categoryId = "cat_m_burgers",
                    name = "Wagyu Craft Burger",
                    description = "Test Dish",
                    basePriceCents = priceCents,
                    sku = "TST-1",
                    categoryTag = "Burgers",
                    isAvailable = true,
                    taxExempt = false,
                    variations = emptyList(),
                    addons = emptyList()
                ),
                selectedVariation = null,
                selectedAddons = emptyList(),
                specialInstructions = "",
                quantity = qty
            )
        )
    }

    private suspend fun placePaidPosOrder(
        customer: CustomerSummary,
        cardId: String,
        subtotalCents: Long,
        discountPercent: Double = 0.0,
        voucherId: String? = null,
        idempotencyKey: String = UUID.randomUUID().toString(),
        clockMillis: Long = 1_740_000_000_000L,
        payFraction: Double = 1.0,
        productId: String = "prod_m_1",
        taxPercent: Double = 0.0
    ) = posRepo.placeOrder(
        session = cashierSession,
        idempotencyKey = idempotencyKey,
        cartItems = testCart(subtotalCents, productId = productId),
        totals = BillingCalculator.calculate(
            items = testCart(subtotalCents, productId = productId),
            discountPercent = discountPercent,
            manualDiscountCents = 0L,
            taxRatePercent = taxPercent,
            serviceChargeRatePercent = 0.0
        ),
        orderType = OrderType.TAKEAWAY,
        tableNumber = null,
        priority = OrderPriority.NORMAL,
        customer = customer,
        paymentMethod = PaymentMethod.CASH,
        splitBreakdown = run {
            val netAfterTax = BillingCalculator.calculate(
                items = testCart(subtotalCents, productId = productId),
                discountPercent = discountPercent,
                manualDiscountCents = 0L,
                taxRatePercent = taxPercent,
                serviceChargeRatePercent = 0.0
            ).totalPayableCents
            val paid = if (voucherId != null && subtotalCents <= 150_000L) 0L else (netAfterTax * payFraction).toLong()
            SplitPaymentBreakdown(cashCents = paid)
        },
        loyaltyCardId = cardId,
        loyaltyVoucherId = voucherId,
        clockMillis = clockMillis
    )

    @Test
    fun `test 01 - enroll customer issues unique card and prevents duplicate cards`() = runBlocking {
        val card1 = smartLoyaltyRepo.enrollCustomerCard(cashierSession, "cust_1").getOrThrow()
        assertEquals(LoyaltyCardStatus.NOT_ACTIVATED, card1.effectiveStatus)
        assertNull(card1.firstQualifyingInvoiceAt)
        assertNull(card1.expiresAt)
        assertEquals(0, card1.currentCycleStamps)
        assertTrue(card1.cardNumber.isNotBlank())
        assertTrue(card1.qrCodeToken.startsWith("AACLC_"))

        val dup = smartLoyaltyRepo.enrollCustomerCard(cashierSession, "cust_1")
        assertTrue(dup.isFailure)
    }

    @Test
    fun `test 02 and 03 - below 1500 pkr earns 0 stamps and qualifying order activates 3 month card`() = runBlocking {
        val card = smartLoyaltyRepo.enrollCustomerCard(cashierSession, "cust_3").getOrThrow()
        val t0 = 1_740_000_000_000L

        // Below PKR 1,500 (149,900 cents) -> 0 stamps, remains NOT_ACTIVATED
        val lowOrd = placePaidPosOrder(
            customer = customer3,
            cardId = card.cardId,
            subtotalCents = 149_900L,
            clockMillis = t0
        ).getOrThrow()
        assertEquals(0, lowOrd.loyaltyStampChange)
        val afterLow = smartLoyaltyRepo.getCustomerLoyaltyBundle("cust_3", t0)!!.activeCard!!
        assertEquals(LoyaltyCardStatus.NOT_ACTIVATED, afterLow.effectiveStatus)
        assertNull(afterLow.expiresAt)

        // Exact PKR 1,500 (150,000 cents) -> +1 stamp & activates 3-month expiry
        val qualOrd = placePaidPosOrder(
            customer = customer3,
            cardId = card.cardId,
            subtotalCents = 150_000L,
            clockMillis = t0 + 10_000L
        ).getOrThrow()
        assertEquals(1, qualOrd.loyaltyStampChange)
        assertEquals(1, qualOrd.loyaltyStampsAfter)

        val afterQual = smartLoyaltyRepo.getCustomerLoyaltyBundle("cust_3", t0 + 10_000L)!!.activeCard!!
        assertEquals(LoyaltyCardStatus.ACTIVE, afterQual.effectiveStatus)
        assertEquals(t0 + 10_000L, afterQual.firstQualifyingInvoiceAt)
        val expectedExpiry = LoyaltyCalendarCalculator.addCalendarMonths(t0 + 10_000L, 3)
        assertEquals(expectedExpiry, afterQual.expiresAt)
    }

    @Test
    fun `test 04 - exact calendar 3 month expiry handles Jan 31 and leap year Feb 29 accurately`() {
        val jan31 = Calendar.getInstance().apply {
            set(2025, Calendar.JANUARY, 31, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val apr30Cal = Calendar.getInstance().apply {
            timeInMillis = LoyaltyCalendarCalculator.addCalendarMonths(jan31, 3)
        }
        assertEquals(Calendar.APRIL, apr30Cal.get(Calendar.MONTH))
        assertEquals(30, apr30Cal.get(Calendar.DAY_OF_MONTH))

        val nov30Leap = Calendar.getInstance().apply {
            set(2023, Calendar.NOVEMBER, 30, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val feb29Cal = Calendar.getInstance().apply {
            timeInMillis = LoyaltyCalendarCalculator.addCalendarMonths(nov30Leap, 3)
        }
        assertEquals(Calendar.FEBRUARY, feb29Cal.get(Calendar.MONTH))
        assertEquals(29, feb29Cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `test 05 - duplicate order idempotency key never awards second stamp`() = runBlocking {
        val card = smartLoyaltyRepo.enrollCustomerCard(cashierSession, "cust_3").getOrThrow()
        val key = "idem_fixed_retry_1"
        val first = placePaidPosOrder(customer3, card.cardId, 200_000L, idempotencyKey = key).getOrThrow()
        val second = placePaidPosOrder(customer3, card.cardId, 200_000L, idempotencyKey = key).getOrThrow()

        assertEquals(first.orderId, second.orderId)
        val bundle = smartLoyaltyRepo.getCustomerLoyaltyBundle("cust_3")!!
        assertEquals(1, bundle.activeCard!!.currentCycleStamps)
        assertEquals(1, bundle.stampTransactions.count { it.txType == LoyaltyStampTxType.EARN })
    }

    @Test
    fun `test 06 and 07 - partial payment tax exclusion and discount reduction block false stamp earning`() = runBlocking {
        val card = smartLoyaltyRepo.enrollCustomerCard(cashierSession, "cust_3").getOrThrow()
        val t0 = 1_740_000_000_000L

        // Partial paid order (50% paid of PKR 2,000) -> NO stamp
        val partial = placePaidPosOrder(customer3, card.cardId, 200_000L, payFraction = 0.5, clockMillis = t0).getOrThrow()
        assertEquals(0, partial.loyaltyStampChange)

        // Subtotal PKR 1,400 + 16% tax = PKR 1,624 payable, but tax excluded -> NO stamp!
        val taxInflated = placePaidPosOrder(customer3, card.cardId, 140_000L, taxPercent = 16.0, clockMillis = t0 + 1000L).getOrThrow()
        assertEquals(0, taxInflated.loyaltyStampChange)

        // Subtotal PKR 1,600 - 10% regular discount = PKR 1,440 < PKR 1,500 -> NO stamp!
        val discReduced = placePaidPosOrder(customer3, card.cardId, 160_000L, discountPercent = 10.0, clockMillis = t0 + 2000L).getOrThrow()
        assertEquals(0, discReduced.loyaltyStampChange)

        val bundle = smartLoyaltyRepo.getCustomerLoyaltyBundle("cust_3", t0 + 2000L)!!
        assertEquals(0, bundle.activeCard!!.currentCycleStamps)
    }

    @Test
    fun `test 08 09 10 11 - 6th stamp auto-mints voucher single redemption real cogs no cashout and cycle 2 rollover`() = runBlocking {
        val card = smartLoyaltyRepo.enrollCustomerCard(cashierSession, "cust_3").getOrThrow()
        val t0 = 1_740_000_000_000L

        // Stamps 1..5
        for (i in 1..5) {
            val o = placePaidPosOrder(customer3, card.cardId, 155_000L, clockMillis = t0 + i * 10_000L).getOrThrow()
            assertEquals(1, o.loyaltyStampChange)
            assertEquals(i, o.loyaltyStampsAfter)
        }
        assertTrue(smartLoyaltyRepo.getCustomerLoyaltyBundle("cust_3", t0 + 60_000L)!!.vouchers.isEmpty())

        // 6th qualifying order -> 6/6, REWARD_ELIGIBLE, 1 ACTIVE voucher <= PKR 1,500
        val ord6 = placePaidPosOrder(customer3, card.cardId, 160_000L, clockMillis = t0 + 70_000L).getOrThrow()
        assertEquals(6, ord6.loyaltyStampsAfter)
        val bundleAfter6 = smartLoyaltyRepo.getCustomerLoyaltyBundle("cust_3", t0 + 70_000L)!!
        assertEquals(LoyaltyCardStatus.REWARD_ELIGIBLE, bundleAfter6.activeCard!!.effectiveStatus)
        assertEquals(1, bundleAfter6.vouchers.size)
        val voucher = bundleAfter6.vouchers.first()
        assertEquals(LoyaltyVoucherStatus.ACTIVE, voucher.effectiveStatus)
        assertEquals(LoyaltyRuleConstants.MAX_REWARD_CAP_CENTS, voucher.rewardValueCents)

        // Attempt redemption on PKR 1,000 order -> capped at PKR 1,000 (NO negative bill / NO cash-out!),
        // and remaining paid 0 < 1,500 so Cycle 2 does NOT start yet
        val ordRedeem = posRepo.placeOrder(
            session = cashierSession,
            idempotencyKey = UUID.randomUUID().toString(),
            cartItems = testCart(100_000L, productId = "prod_m_1"),
            totals = BillingCalculator.calculate(testCart(100_000L, productId = "prod_m_1"), 0.0, 0L, 0.0, 0.0),
            orderType = OrderType.TAKEAWAY,
            tableNumber = null,
            priority = OrderPriority.NORMAL,
            customer = customer3,
            paymentMethod = PaymentMethod.CASH,
            splitBreakdown = SplitPaymentBreakdown(cashCents = 0L),
            loyaltyCardId = card.cardId,
            loyaltyVoucherId = voucher.voucherId,
            clockMillis = t0 + 80_000L
        ).getOrThrow()
        assertEquals(100_000L, ordRedeem.loyaltyVoucherDiscountCents)
        assertEquals(0L, ordRedeem.totalCents)
        assertTrue(ordRedeem.loyaltyPromoCostCents > 0L) // Real Recipe COGS from seeded prod_m_1!

        // Double redemption of same voucher is blocked cleanly
        val doubleRedeem = posRepo.placeOrder(
            session = cashierSession,
            idempotencyKey = UUID.randomUUID().toString(),
            cartItems = testCart(200_000L),
            totals = BillingCalculator.calculate(testCart(200_000L), 0.0, 0L, 0.0, 0.0),
            orderType = OrderType.TAKEAWAY,
            tableNumber = null,
            priority = OrderPriority.NORMAL,
            customer = customer3,
            paymentMethod = PaymentMethod.CASH,
            splitBreakdown = SplitPaymentBreakdown(cashCents = 50_000L),
            loyaltyCardId = card.cardId,
            loyaltyVoucherId = voucher.voucherId,
            clockMillis = t0 + 90_000L
        )
        assertTrue(doubleRedeem.isFailure)

        // Next qualifying order >= PKR 1,500 rolls cleanly into Cycle 2 Stamp 1!
        val ordCycle2 = placePaidPosOrder(customer3, card.cardId, 180_000L, clockMillis = t0 + 100_000L).getOrThrow()
        assertEquals(1, ordCycle2.loyaltyStampChange)
        assertEquals(1, ordCycle2.loyaltyStampsAfter)
        val finalCard = smartLoyaltyRepo.getCustomerLoyaltyBundle("cust_3", t0 + 100_000L)!!.activeCard!!
        assertEquals(2, finalCard.cycleCount)
        assertEquals(7, finalCard.totalStampsEarned)
        assertEquals(LoyaltyCardStatus.ACTIVE, finalCard.effectiveStatus)
    }

    @Test
    fun `test 12 - order cancellation reverses stamp and auto-cancels unredeemed reward voucher`() = runBlocking {
        val card = smartLoyaltyRepo.enrollCustomerCard(cashierSession, "cust_3").getOrThrow()
        val t0 = 1_740_000_000_000L

        for (i in 1..5) {
            placePaidPosOrder(customer3, card.cardId, 160_000L, clockMillis = t0 + i * 5000L).getOrThrow()
        }
        val ord6 = placePaidPosOrder(customer3, card.cardId, 160_000L, clockMillis = t0 + 30_000L).getOrThrow()
        var bundle = smartLoyaltyRepo.getCustomerLoyaltyBundle("cust_3", t0 + 30_000L)!!
        assertEquals(1, bundle.vouchers.size)
        assertEquals(LoyaltyVoucherStatus.ACTIVE, bundle.vouchers.first().effectiveStatus)

        // Cancel order 6 -> stamp reversed to 5/6 & unredeemed voucher auto-cancelled
        posRepo.cancelOrder(managerSession, ord6.orderId, "Customer cancelled takeaway").getOrThrow()
        bundle = smartLoyaltyRepo.getCustomerLoyaltyBundle("cust_3", t0 + 40_000L)!!
        assertEquals(5, bundle.activeCard!!.currentCycleStamps)
        assertEquals(LoyaltyCardStatus.ACTIVE, bundle.activeCard.effectiveStatus)
        assertEquals(LoyaltyVoucherStatus.CANCELLED, bundle.vouchers.first().effectiveStatus)
        assertTrue(bundle.stampTransactions.any { it.txType == LoyaltyStampTxType.REVERSAL && it.stampsDelta == -1 })
    }

    @Test
    fun `test 13 14 15 - expiry blocks earning while keeping grace voucher active card replace and suspend preserve exact expiry`() = runBlocking {
        val card = smartLoyaltyRepo.enrollCustomerCard(cashierSession, "cust_3").getOrThrow()
        val t0 = 1_740_000_000_000L

        for (i in 1..6) {
            placePaidPosOrder(customer3, card.cardId, 160_000L, clockMillis = t0 + i * 1000L).getOrThrow()
        }
        val activeCard = smartLoyaltyRepo.getCustomerLoyaltyBundle("cust_3", t0 + 6000L)!!.activeCard!!
        val origExpiry = activeCard.expiresAt!!
        val graceExpiry = activeCard.graceExpiresAt!!
        val voucher = smartLoyaltyRepo.getCustomerLoyaltyBundle("cust_3", t0 + 6000L)!!.vouchers.first()

        // Suspend -> Reactivate preserves exact original expiry
        smartLoyaltyRepo.suspendCard(managerSession, card.cardId, "Lost wallet report", t0 + 10_000L).getOrThrow()
        val reactivated = smartLoyaltyRepo.reactivateCard(managerSession, card.cardId, "Found card", t0 + 20_000L).getOrThrow()
        assertEquals(origExpiry, reactivated.expiresAt)

        // Replace Card -> Old card blocked, new card inherits 6/6, exact original expiry, & active voucher
        val replaced = smartLoyaltyRepo.replaceCard(managerSession, card.cardId, "Damaged QR", null, t0 + 30_000L).getOrThrow()
        assertNotEquals(card.cardId, replaced.cardId)
        assertEquals(origExpiry, replaced.expiresAt)
        assertEquals(6, replaced.currentCycleStamps)
        assertTrue(smartLoyaltyRepo.lookupByQrOrQuery(card.qrCodeToken).first().effectiveStatus == LoyaltyCardStatus.REPLACED)

        // After card expires (origExpiry + 1000) earning stamps is blocked, but voucher inside graceExpiresAt is still redeemable!
        val afterExpiryTime = origExpiry + 10_000L
        assertTrue(afterExpiryTime < graceExpiry)
        val previewAfterExpiry = smartLoyaltyRepo.previewPosLoyalty(
            branchId = DatabaseSeeder.MAIN_BRANCH_ID,
            cardId = replaced.cardId,
            cartItems = testCart(200_000L),
            preVoucherTotals = BillingCalculator.calculate(testCart(200_000L), 0.0, 0L, 0.0, 0.0),
            nowMillis = afterExpiryTime
        )!!
        assertFalse(previewAfterExpiry.willEarnStamp)
        assertEquals(1, previewAfterExpiry.activeVouchers.size)

        val graceRedeem = posRepo.placeOrder(
            session = cashierSession,
            idempotencyKey = UUID.randomUUID().toString(),
            cartItems = testCart(200_000L),
            totals = BillingCalculator.calculate(testCart(200_000L), 0.0, 0L, 0.0, 0.0),
            orderType = OrderType.TAKEAWAY,
            tableNumber = null,
            priority = OrderPriority.NORMAL,
            customer = customer3,
            paymentMethod = PaymentMethod.CASH,
            splitBreakdown = SplitPaymentBreakdown(cashCents = 50_000L),
            loyaltyCardId = replaced.cardId,
            loyaltyVoucherId = voucher.voucherId,
            clockMillis = afterExpiryTime
        ).getOrThrow()
        assertEquals(150_000L, graceRedeem.loyaltyVoucherDiscountCents)
        assertEquals(0, graceRedeem.loyaltyStampChange) // Card expired so no Cycle 2 stamp!

        // Renew expired card cleanly starts Cycle 2 NOT_ACTIVATED with no stale expiry
        val renewed = smartLoyaltyRepo.renewExpiredCard(managerSession, replaced.cardId, "Customer renewed", afterExpiryTime + 1000L).getOrThrow()
        assertEquals(LoyaltyCardStatus.NOT_ACTIVATED, renewed.effectiveStatus)
        assertNull(renewed.expiresAt)
        assertEquals(0, renewed.currentCycleStamps)
    }

    @Test
    fun `test 16 - rbac blocks cashier from settings change manual adjustment and suspension`() = runBlocking {
        val card = smartLoyaltyRepo.enrollCustomerCard(cashierSession, "cust_3").getOrThrow()

        val adjustAttempt = smartLoyaltyRepo.adjustStampsManually(cashierSession, card.cardId, 2, "Cashier self adjust")
        assertTrue(adjustAttempt.isFailure)
        assertTrue(adjustAttempt.exceptionOrNull() is SecurityException)

        val suspendAttempt = smartLoyaltyRepo.suspendCard(cashierSession, card.cardId, "Cashier suspend")
        assertTrue(suspendAttempt.isFailure)
        assertTrue(suspendAttempt.exceptionOrNull() is SecurityException)

        val settingsAttempt = smartLoyaltyRepo.updateProgramSettings(
            cashierSession,
            LoyaltyProgramSettingsRecord(branchId = DatabaseSeeder.MAIN_BRANCH_ID)
        )
        assertTrue(settingsAttempt.isFailure)
        assertTrue(settingsAttempt.exceptionOrNull() is SecurityException)

        // Cap > PKR 1,500 blocked even for Owner!
        val overCap = smartLoyaltyRepo.updateProgramSettings(
            ownerSession,
            LoyaltyProgramSettingsRecord(branchId = DatabaseSeeder.MAIN_BRANCH_ID, rewardAmountCents = 200_000L)
        )
        assertTrue(overCap.isFailure)
    }

    @Test
    fun `test 17 - accounting reconciliation excludes voucher discount from paid sales and tracks real promo cogs`() = runBlocking {
        val card = smartLoyaltyRepo.enrollCustomerCard(cashierSession, "cust_3").getOrThrow()
        val t0 = 1_740_000_000_000L

        for (i in 1..6) {
            placePaidPosOrder(customer3, card.cardId, 160_000L, clockMillis = t0 + i * 1000L).getOrThrow()
        }
        val voucher = smartLoyaltyRepo.getCustomerLoyaltyBundle("cust_3", t0 + 6000L)!!.vouchers.first()

        // Redeem PKR 1,500 on a PKR 2,000 order -> Customer pays net PKR 500
        val preTot = BillingCalculator.calculate(testCart(200_000L), 0.0, 0L, 0.0, 0.0)
        val redeemedOrder = posRepo.placeOrder(
            session = cashierSession,
            idempotencyKey = UUID.randomUUID().toString(),
            cartItems = testCart(200_000L),
            totals = preTot,
            orderType = OrderType.TAKEAWAY,
            tableNumber = null,
            priority = OrderPriority.NORMAL,
            customer = customer3,
            paymentMethod = PaymentMethod.CASH,
            splitBreakdown = SplitPaymentBreakdown(cashCents = 50_000L),
            loyaltyCardId = card.cardId,
            loyaltyVoucherId = voucher.voucherId,
            clockMillis = t0 + 10_000L
        ).getOrThrow()

        assertEquals(50_000L, redeemedOrder.totalCents)
        assertEquals(150_000L, redeemedOrder.loyaltyVoucherDiscountCents)

        // Journal entries for that order must be balanced at 50,000 (cash debit 50,000, sales credit 50,000; NO synthetic 150,000 expense!)
        val journalEntries = database.accountingDao().observeJournalEntries(DatabaseSeeder.MAIN_BRANCH_ID).first()
        val redeemJe = journalEntries.first { it.sourceEntityId == redeemedOrder.orderId }
        assertEquals(50_000L, redeemJe.totalDebitCents)
        assertEquals(50_000L, redeemJe.totalCreditCents)

        val report = smartLoyaltyRepo.buildAnalyticsReport(DatabaseSeeder.MAIN_BRANCH_ID, nowMillis = t0 + 20_000L)
        assertEquals(150_000L, report.totalPromoDiscountGivenCents)
        assertTrue(report.totalActualPromoCostCents > 0L)
        assertEquals((6 * 160_000L) + 50_000L, report.totalPaidSalesRevenueCents)
    }

    @Test
    fun `test 18 - qr bitmap generation and customer loyalty A4 pdf statement generation succeed`() = runBlocking {
        val card = smartLoyaltyRepo.enrollCustomerCard(cashierSession, "cust_1").getOrThrow()
        placePaidPosOrder(customer1, card.cardId, 200_000L).getOrThrow()

        val bmp = QrCodeHelper.generateQrBitmap(card.qrCodeToken, 256)
        assertNotNull(bmp)
        assertEquals(256, bmp.width)

        val found = smartLoyaltyRepo.lookupByQrOrQuery(card.qrCodeToken)
        assertEquals(1, found.size)
        assertEquals(card.cardId, found.first().cardId)

        val bundle = smartLoyaltyRepo.getCustomerLoyaltyBundle("cust_1")!!
        val pdfFile = LoyaltyStatementPdfHelper.generateCustomerStatementPdf(
            context = context,
            bundle = bundle,
            branchName = "AA CRAVE - Flagship Main",
            branchAddress = "120 Culinary Ave",
            logoBitmap = null
        ).getOrThrow()
        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 300L)
        assertTrue(pdfFile.readText(Charsets.ISO_8859_1).startsWith("%PDF-"))
    }

    @Test
    fun `test 19 - backup serializer roundtrip and room migration 1 to 8 preserve smart loyalty data`() = runBlocking {
        val card = smartLoyaltyRepo.enrollCustomerCard(cashierSession, "cust_3").getOrThrow()
        placePaidPosOrder(customer3, card.cardId, 180_000L).getOrThrow()

        val (tablesObj, _) = BackupPayloadSerializer.exportDatabaseTablesJson(database)
        val jsonPayload = tablesObj.toString()
        assertTrue(jsonPayload.contains(card.cardNumber))
        assertTrue(jsonPayload.contains(card.qrCodeToken))

        // Verify sync queue staged Smart Loyalty entries
        val pendingSync = database.phase7Dao().getPendingOrFailedQueue()
        assertTrue(pendingSync.any { it.entityType == "LOYALTY_CARD" })
        assertTrue(pendingSync.any { it.entityType == "LOYALTY_STAMP" })

        // Verify BackupPayloadSerializer restore onto a clean v8 DB preserves Smart Loyalty entities cleanly
        val db2 = Room.inMemoryDatabaseBuilder(context, AaCraveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        BackupPayloadSerializer.restoreTablesFromJson(db2, tablesObj)
        val restoredCards = db2.smartLoyaltyDao().getAllCards()
        assertEquals(1, restoredCards.size)
        assertEquals(card.cardNumber, restoredCards.first().cardNumber)
        assertEquals(1, restoredCards.first().currentCycleStamps)
        db2.close()

        // Verify full Room v1 -> v8 migration chain
        val migDbName = "aa_crave_loyalty_v1_to_v8.db"
        context.deleteDatabase(migDbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(migDbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `branches` (`branchId` TEXT NOT NULL, `name` TEXT NOT NULL, `code` TEXT NOT NULL, `address` TEXT NOT NULL, `isMainBranch` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`branchId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profiles` (`uid` TEXT NOT NULL, `email` TEXT NOT NULL, `displayName` TEXT NOT NULL, `roleCode` TEXT NOT NULL, `branchId` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `lastLoginAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `dashboard_snapshots` (`branchId` TEXT NOT NULL, `businessDate` TEXT NOT NULL, `todaySales` REAL NOT NULL, `todayOrders` INTEGER NOT NULL, `totalCustomers` INTEGER NOT NULL, `pendingKitchenOrders` INTEGER NOT NULL, `lowStockAlerts` INTEGER NOT NULL, `dailyExpenses` REAL NOT NULL, `estimatedProfit` REAL NOT NULL, `cashPayments` REAL NOT NULL, `digitalPayments` REAL NOT NULL, `isSampleData` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`branchId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `low_stock_alerts` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `ingredientName` TEXT NOT NULL, `currentStock` REAL NOT NULL, `reorderLevel` REAL NOT NULL, `unit` TEXT NOT NULL, `isSampleData` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `recent_transactions` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `orderNumber` TEXT NOT NULL, `invoiceNumber` TEXT NOT NULL, `customerName` TEXT NOT NULL, `amount` REAL NOT NULL, `paymentMethod` TEXT NOT NULL, `status` TEXT NOT NULL, `timeLabel` TEXT NOT NULL, `isSampleData` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `hourly_sales` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `hourOrder` INTEGER NOT NULL, `label` TEXT NOT NULL, `revenue` REAL NOT NULL, `orderCount` INTEGER NOT NULL, `isSampleData` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `branchId` TEXT NOT NULL, `actorUid` TEXT NOT NULL, `actorEmail` TEXT NOT NULL, `actorRole` TEXT NOT NULL, `action` TEXT NOT NULL, `details` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_queue` (`transactionId` TEXT NOT NULL, `branchId` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `status` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `lastError` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`transactionId`))")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()

        val migrated = Room.databaseBuilder(context, AaCraveDatabase::class.java, migDbName)
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

        listOf(
            "loyalty_program_settings",
            "loyalty_cards",
            "loyalty_stamp_transactions",
            "loyalty_reward_vouchers",
            "loyalty_redemptions",
            "loyalty_audit_logs"
        ).forEach { table ->
            val cursor = migrated.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$table`")
            assertTrue(cursor.moveToFirst())
            assertNotNull(cursor.getInt(0))
            cursor.close()
        }
        migrated.close()
        assertTrue(context.deleteDatabase(migDbName))
    }
}
