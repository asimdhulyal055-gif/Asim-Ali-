package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartLoyaltyDao {

    // Settings
    @Query("SELECT * FROM loyalty_program_settings WHERE branchId = :branchId LIMIT 1")
    fun observeSettings(branchId: String): Flow<LoyaltyProgramSettingsEntity?>

    @Query("SELECT * FROM loyalty_program_settings WHERE branchId = :branchId LIMIT 1")
    suspend fun getSettings(branchId: String): LoyaltyProgramSettingsEntity?

    @Query("SELECT * FROM loyalty_program_settings")
    suspend fun getAllSettings(): List<LoyaltyProgramSettingsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: LoyaltyProgramSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSettingsIfNew(settings: LoyaltyProgramSettingsEntity): Long

    // Cards
    @Query("SELECT * FROM loyalty_cards WHERE branchId = :branchId ORDER BY updatedAt DESC")
    fun observeCardsForBranch(branchId: String): Flow<List<LoyaltyCardEntity>>

    @Query("SELECT * FROM loyalty_cards ORDER BY updatedAt DESC")
    fun observeAllCards(): Flow<List<LoyaltyCardEntity>>

    @Query("SELECT * FROM loyalty_cards ORDER BY updatedAt DESC")
    suspend fun getAllCards(): List<LoyaltyCardEntity>

    @Query("SELECT * FROM loyalty_cards WHERE cardId = :cardId LIMIT 1")
    suspend fun getCardById(cardId: String): LoyaltyCardEntity?

    @Query("SELECT * FROM loyalty_cards WHERE cardNumber = :cardNumber LIMIT 1")
    suspend fun getCardByNumber(cardNumber: String): LoyaltyCardEntity?

    @Query("SELECT * FROM loyalty_cards WHERE qrCodeToken = :qrCodeToken LIMIT 1")
    suspend fun getCardByQrToken(qrCodeToken: String): LoyaltyCardEntity?

    @Query(
        """
        SELECT * FROM loyalty_cards
        WHERE customerId = :customerId AND status NOT IN ('REPLACED')
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun getNonReplacedCardForCustomer(customerId: String): LoyaltyCardEntity?

    @Query(
        """
        SELECT * FROM loyalty_cards
        WHERE customerId = :customerId AND status IN ('NOT_ACTIVATED', 'ACTIVE', 'REWARD_ELIGIBLE', 'COMPLETED')
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun getLiveCardForCustomer(customerId: String): LoyaltyCardEntity?

    @Query("SELECT * FROM loyalty_cards WHERE customerId = :customerId ORDER BY issuedAt DESC")
    suspend fun getCardHistoryForCustomer(customerId: String): List<LoyaltyCardEntity>

    @Query(
        """
        SELECT * FROM loyalty_cards
        WHERE (LOWER(cardNumber) = LOWER(:query)
           OR qrCodeToken = :query
           OR LOWER(customerName) LIKE '%' || LOWER(:query) || '%'
           OR REPLACE(REPLACE(customerPhone, '-', ''), ' ', '') LIKE '%' || :digitsOnly || '%')
        ORDER BY CASE WHEN status = 'REPLACED' THEN 1 ELSE 0 END, updatedAt DESC
        """
    )
    suspend fun searchCards(query: String, digitsOnly: String): List<LoyaltyCardEntity>

    @Query("SELECT COUNT(*) FROM loyalty_cards")
    suspend fun countCards(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: LoyaltyCardEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCardIfNew(card: LoyaltyCardEntity): Long

    // Stamp Transactions
    @Query("SELECT * FROM loyalty_stamp_transactions WHERE branchId = :branchId ORDER BY createdAt DESC")
    fun observeStampTransactionsForBranch(branchId: String): Flow<List<LoyaltyStampTransactionEntity>>

    @Query("SELECT * FROM loyalty_stamp_transactions ORDER BY createdAt DESC")
    fun observeAllStampTransactions(): Flow<List<LoyaltyStampTransactionEntity>>

    @Query("SELECT * FROM loyalty_stamp_transactions WHERE customerId = :customerId ORDER BY createdAt DESC")
    suspend fun getStampTransactionsForCustomer(customerId: String): List<LoyaltyStampTransactionEntity>

    @Query("SELECT * FROM loyalty_stamp_transactions WHERE orderId = :orderId AND txType = 'EARN' LIMIT 1")
    suspend fun getEarnTxForOrder(orderId: String): LoyaltyStampTransactionEntity?

    @Query("SELECT * FROM loyalty_stamp_transactions WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getStampTxByIdempotency(key: String): LoyaltyStampTransactionEntity?

    @Query("SELECT * FROM loyalty_stamp_transactions WHERE stampTxId = :stampTxId LIMIT 1")
    suspend fun getStampTxById(stampTxId: String): LoyaltyStampTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStampTransactionIfNew(tx: LoyaltyStampTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStampTransaction(tx: LoyaltyStampTransactionEntity)

    // Vouchers
    @Query("SELECT * FROM loyalty_reward_vouchers WHERE branchId = :branchId ORDER BY earnedAt DESC")
    fun observeVouchersForBranch(branchId: String): Flow<List<LoyaltyRewardVoucherEntity>>

    @Query("SELECT * FROM loyalty_reward_vouchers ORDER BY earnedAt DESC")
    fun observeAllVouchers(): Flow<List<LoyaltyRewardVoucherEntity>>

    @Query("SELECT * FROM loyalty_reward_vouchers ORDER BY earnedAt DESC")
    suspend fun getAllVouchers(): List<LoyaltyRewardVoucherEntity>

    @Query("SELECT * FROM loyalty_reward_vouchers WHERE customerId = :customerId ORDER BY earnedAt DESC")
    suspend fun getVouchersForCustomer(customerId: String): List<LoyaltyRewardVoucherEntity>

    @Query("SELECT * FROM loyalty_reward_vouchers WHERE voucherId = :voucherId LIMIT 1")
    suspend fun getVoucherById(voucherId: String): LoyaltyRewardVoucherEntity?

    @Query("SELECT * FROM loyalty_reward_vouchers WHERE LOWER(voucherCode) = LOWER(:code) OR qrCodeToken = :code LIMIT 1")
    suspend fun getVoucherByCodeOrQr(code: String): LoyaltyRewardVoucherEntity?

    @Query("SELECT * FROM loyalty_reward_vouchers WHERE earnedStampTxId = :stampTxId LIMIT 1")
    suspend fun getVoucherByEarnedStampTx(stampTxId: String): LoyaltyRewardVoucherEntity?

    @Query("SELECT * FROM loyalty_reward_vouchers WHERE redeemedOrderId = :orderId LIMIT 1")
    suspend fun getVoucherRedeemedOnOrder(orderId: String): LoyaltyRewardVoucherEntity?

    @Query("UPDATE loyalty_reward_vouchers SET cardId = :newCardId WHERE cardId = :oldCardId AND status = 'ACTIVE'")
    suspend fun reassignActiveVouchersToCard(oldCardId: String, newCardId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVoucherIfNew(voucher: LoyaltyRewardVoucherEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVoucher(voucher: LoyaltyRewardVoucherEntity)

    // Redemptions
    @Query("SELECT * FROM loyalty_redemptions WHERE branchId = :branchId ORDER BY createdAt DESC")
    fun observeRedemptionsForBranch(branchId: String): Flow<List<LoyaltyRedemptionEntity>>

    @Query("SELECT * FROM loyalty_redemptions ORDER BY createdAt DESC")
    fun observeAllRedemptions(): Flow<List<LoyaltyRedemptionEntity>>

    @Query("SELECT * FROM loyalty_redemptions WHERE customerId = :customerId ORDER BY createdAt DESC")
    suspend fun getRedemptionsForCustomer(customerId: String): List<LoyaltyRedemptionEntity>

    @Query("SELECT * FROM loyalty_redemptions WHERE voucherId = :voucherId LIMIT 1")
    suspend fun getRedemptionForVoucher(voucherId: String): LoyaltyRedemptionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRedemptionIfNew(redemption: LoyaltyRedemptionEntity): Long

    // Audit Logs
    @Query("SELECT * FROM loyalty_audit_logs WHERE branchId = :branchId ORDER BY createdAt DESC LIMIT 120")
    fun observeAuditLogsForBranch(branchId: String): Flow<List<LoyaltyAuditLogEntity>>

    @Query("SELECT * FROM loyalty_audit_logs ORDER BY createdAt DESC LIMIT 120")
    fun observeAllAuditLogs(): Flow<List<LoyaltyAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: LoyaltyAuditLogEntity)

    // Cross-table read & POS checkout patch helpers (read-only / column update)
    @Query("SELECT * FROM customers")
    suspend fun getAllCustomers(): List<CustomerEntity>

    @Query("SELECT * FROM pos_orders ORDER BY createdAt DESC")
    suspend fun getAllPosOrders(): List<PosOrderEntity>

    @Query("SELECT * FROM pos_orders WHERE loyaltyCardId = :cardId OR customerId = :customerId ORDER BY createdAt DESC")
    suspend fun getOrdersForCustomerOrCard(cardId: String?, customerId: String): List<PosOrderEntity>

    @Query(
        """
        UPDATE pos_orders SET
            loyaltyCardId = :cardId,
            loyaltyCardNumber = :cardNumber,
            loyaltyStampChange = :stampChange,
            loyaltyStampsAfter = :stampsAfter,
            loyaltyVoucherId = :voucherId,
            loyaltyVoucherCode = :voucherCode,
            loyaltyVoucherDiscountCents = :voucherDiscountCents,
            loyaltyPromoCostCents = :promoCostCents
        WHERE orderId = :orderId
        """
    )
    suspend fun updateOrderLoyaltyFields(
        orderId: String,
        cardId: String?,
        cardNumber: String?,
        stampChange: Int,
        stampsAfter: Int,
        voucherId: String?,
        voucherCode: String?,
        voucherDiscountCents: Long,
        promoCostCents: Long
    )
}
