package com.example.data.repository

import com.example.data.local.AuditLogDao
import com.example.data.local.AuditLogEntity
import com.example.data.local.BranchDao
import com.example.data.local.CustomerLoyaltyDao
import com.example.data.local.DashboardDao
import com.example.data.local.DatabaseSeeder
import com.example.data.local.ExpenseDao
import com.example.data.local.PurchaseDao
import com.example.data.local.SupplierDao
import com.example.data.local.SyncQueueDao
import com.example.domain.model.BranchSummary
import com.example.domain.model.DashboardOverview
import com.example.domain.model.ExpenseApprovalStatus
import com.example.domain.model.LowStockAlertItem
import com.example.domain.model.PurchaseStatus
import com.example.domain.model.RecentPurchaseSummary
import com.example.domain.model.RecentSupplierPaymentSummary
import com.example.domain.model.RecentTransactionItem
import com.example.domain.model.SalesChartPoint
import com.example.domain.model.TopSupplierSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class DashboardRepository(
    private val branchDao: BranchDao,
    private val dashboardDao: DashboardDao,
    private val auditLogDao: AuditLogDao,
    private val syncQueueDao: SyncQueueDao,
    private val databaseSeeder: DatabaseSeeder,
    private val supplierDao: SupplierDao? = null,
    private val purchaseDao: PurchaseDao? = null,
    private val customerLoyaltyDao: CustomerLoyaltyDao? = null,
    private val expenseDao: ExpenseDao? = null
) {

    val branches: Flow<List<BranchSummary>> = branchDao.observeBranches().map { list ->
        list.map { it.toBranchSummary() }
    }

    val auditLogs: Flow<List<AuditLogEntity>> = auditLogDao.observeRecentLogs()

    val auditLogCount: Flow<Int> = auditLogDao.observeLogCount()

    val pendingSyncCount: Flow<Int> = syncQueueDao.observePendingCount()

    fun observeDashboard(branchId: String): Flow<DashboardOverview?> {
        val coreFlow = combine(
            branchDao.observeBranches(),
            dashboardDao.observeSnapshot(branchId),
            dashboardDao.observeHourlySales(branchId),
            dashboardDao.observeLowStockAlerts(branchId),
            dashboardDao.observeRecentTransactions(branchId),
            syncQueueDao.observePendingCount()
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val branchesList = args[0] as List<com.example.data.local.BranchEntity>
            val snapshot = args[1] as? com.example.data.local.DashboardSnapshotEntity
            @Suppress("UNCHECKED_CAST")
            val hourly = args[2] as List<com.example.data.local.HourlySalesEntity>
            @Suppress("UNCHECKED_CAST")
            val lowStock = args[3] as List<com.example.data.local.LowStockAlertEntity>
            @Suppress("UNCHECKED_CAST")
            val transactions = args[4] as List<com.example.data.local.TransactionRecordEntity>
            val pendingSync = args[5] as Int

            val branchName = branchesList.firstOrNull { it.branchId == branchId }?.name
                ?: "AA CRAVE - Flagship Main"

            snapshot?.let { snap ->
                DashboardOverview(
                    branchId = snap.branchId,
                    branchName = branchName,
                    businessDate = snap.businessDate,
                    todaySales = snap.todaySales,
                    todayOrders = snap.todayOrders,
                    totalCustomers = snap.totalCustomers,
                    pendingKitchenOrders = snap.pendingKitchenOrders,
                    lowStockAlertCount = snap.lowStockAlerts,
                    dailyExpenses = snap.dailyExpenses,
                    estimatedProfit = snap.estimatedProfit,
                    cashPayments = snap.cashPayments,
                    digitalPayments = snap.digitalPayments,
                    isSampleData = snap.isSampleData,
                    hourlySales = hourly.map {
                        SalesChartPoint(label = it.label, revenue = it.revenue, orderCount = it.orderCount)
                    },
                    lowStockItems = lowStock.map {
                        LowStockAlertItem(
                            id = it.id,
                            ingredientName = it.ingredientName,
                            currentStock = it.currentStock,
                            reorderLevel = it.reorderLevel,
                            unit = it.unit,
                            isSampleData = it.isSampleData
                        )
                    },
                    recentTransactions = transactions.map {
                        RecentTransactionItem(
                            id = it.id,
                            orderNumber = it.orderNumber,
                            invoiceNumber = it.invoiceNumber,
                            customerName = it.customerName,
                            amount = it.amount,
                            paymentMethod = it.paymentMethod,
                            status = it.status,
                            timeLabel = it.timeLabel,
                            isSampleData = it.isSampleData
                        )
                    },
                    pendingSyncCount = pendingSync
                )
            }
        }

        if (supplierDao == null || purchaseDao == null || customerLoyaltyDao == null || expenseDao == null) {
            return coreFlow
        }

        val phase4Flow = combine(
            supplierDao.observeSuppliers(branchId),
            supplierDao.observeSupplierPayments(branchId),
            purchaseDao.observePurchaseOrders(branchId),
            expenseDao.observeExpenses(branchId),
            customerLoyaltyDao.observeCustomers(branchId),
            customerLoyaltyDao.observeLoyaltyTransactions(branchId)
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val suppliers = args[0] as List<com.example.data.local.SupplierEntity>
            @Suppress("UNCHECKED_CAST")
            val payments = args[1] as List<com.example.data.local.SupplierPaymentEntity>
            @Suppress("UNCHECKED_CAST")
            val purchases = args[2] as List<com.example.data.local.PurchaseOrderEntity>
            @Suppress("UNCHECKED_CAST")
            val expenses = args[3] as List<com.example.data.local.ExpenseEntity>
            @Suppress("UNCHECKED_CAST")
            val customers = args[4] as List<com.example.data.local.CustomerEntity>
            @Suppress("UNCHECKED_CAST")
            val loyaltyTxs = args[5] as List<com.example.data.local.LoyaltyTransactionEntity>

            val activePurchases = purchases.filter {
                it.status == PurchaseStatus.RECEIVED.name || it.status == PurchaseStatus.PARTIALLY_RECEIVED.name
            }
            val totalPurchasesAmount = activePurchases.sumOf { (it.totalCents - it.returnedCents).coerceAtLeast(0L) } / 100.0
            val outstandingSupplierBalances = suppliers.sumOf {
                (it.openingBalanceCents + it.totalPurchasesCents - it.totalReturnsCents - it.totalPaidCents).coerceAtLeast(0L)
            } / 100.0
            val totalExpensesAmount = expenses
                .filter { it.approvalStatus == ExpenseApprovalStatus.APPROVED.name }
                .sumOf { it.amountCents } / 100.0

            val issuedPts = loyaltyTxs.filter { it.pointsDelta > 0 }.sumOf { it.pointsDelta }
            val redeemedPts = loyaltyTxs.filter { it.pointsDelta < 0 && it.txType == "REDEEM" }.sumOf { -it.pointsDelta }

            val topSup = suppliers
                .sortedByDescending { it.totalPurchasesCents }
                .take(3)
                .map {
                    TopSupplierSummary(
                        supplierId = it.supplierId,
                        supplierName = it.name,
                        totalPurchases = it.totalPurchasesCents / 100.0,
                        outstandingBalance = (it.openingBalanceCents + it.totalPurchasesCents - it.totalReturnsCents - it.totalPaidCents)
                            .coerceAtLeast(0L) / 100.0
                    )
                }

            val recentPos = purchases.take(4).map {
                RecentPurchaseSummary(
                    purchaseId = it.purchaseId,
                    poNumber = it.poNumber,
                    supplierName = it.supplierName,
                    status = it.status,
                    amount = it.totalCents / 100.0
                )
            }

            val recentPays = payments.take(4).map {
                RecentSupplierPaymentSummary(
                    paymentId = it.paymentId,
                    supplierName = it.supplierName,
                    referenceNumber = it.referenceNumber,
                    amount = it.amountCents / 100.0
                )
            }

            Phase4DashboardSummaryBundle(
                totalPurchasesAmount = totalPurchasesAmount,
                outstandingSupplierBalances = outstandingSupplierBalances,
                totalExpensesAmount = totalExpensesAmount,
                totalRegisteredCustomers = customers.size,
                loyaltyPointsIssued = issuedPts,
                loyaltyPointsRedeemed = redeemedPts,
                recentPurchases = recentPos,
                recentSupplierPayments = recentPays,
                topSuppliers = topSup
            )
        }

        return combine(coreFlow, phase4Flow) { overview, p4 ->
            overview?.copy(
                totalPurchasesAmount = p4.totalPurchasesAmount,
                outstandingSupplierBalances = p4.outstandingSupplierBalances,
                totalExpensesAmount = p4.totalExpensesAmount,
                totalRegisteredCustomers = p4.totalRegisteredCustomers,
                loyaltyPointsIssued = p4.loyaltyPointsIssued,
                loyaltyPointsRedeemed = p4.loyaltyPointsRedeemed,
                recentPurchases = p4.recentPurchases,
                recentSupplierPayments = p4.recentSupplierPayments,
                topSuppliers = p4.topSuppliers
            )
        }
    }

    suspend fun refreshSampleData() {
        databaseSeeder.resetSampleDashboardData()
    }

    private data class Phase4DashboardSummaryBundle(
        val totalPurchasesAmount: Double,
        val outstandingSupplierBalances: Double,
        val totalExpensesAmount: Double,
        val totalRegisteredCustomers: Int,
        val loyaltyPointsIssued: Int,
        val loyaltyPointsRedeemed: Int,
        val recentPurchases: List<RecentPurchaseSummary>,
        val recentSupplierPayments: List<RecentSupplierPaymentSummary>,
        val topSuppliers: List<TopSupplierSummary>
    )
}
