package com.example.domain.model

data class BranchSummary(
    val branchId: String,
    val name: String,
    val code: String,
    val address: String,
    val isMainBranch: Boolean,
    val isActive: Boolean,
    val phone: String = "",
    val email: String = "",
    val managerName: String = "",
    val managerUid: String? = null,
    val openingCashCents: Long = 0L,
    val openingBankCents: Long = 0L
)

data class SalesChartPoint(
    val label: String,
    val revenue: Double,
    val orderCount: Int
)

data class DashboardOverview(
    val branchId: String,
    val branchName: String,
    val businessDate: String,
    val todaySales: Double,
    val todayOrders: Int,
    val totalCustomers: Int,
    val pendingKitchenOrders: Int,
    val lowStockAlertCount: Int,
    val dailyExpenses: Double,
    val estimatedProfit: Double,
    val cashPayments: Double,
    val digitalPayments: Double,
    val isSampleData: Boolean,
    val hourlySales: List<SalesChartPoint>,
    val lowStockItems: List<LowStockAlertItem>,
    val recentTransactions: List<RecentTransactionItem>,
    val pendingSyncCount: Int,
    val totalPurchasesAmount: Double = 0.0,
    val outstandingSupplierBalances: Double = 0.0,
    val totalExpensesAmount: Double = 0.0,
    val totalRegisteredCustomers: Int = 0,
    val loyaltyPointsIssued: Int = 0,
    val loyaltyPointsRedeemed: Int = 0,
    val recentPurchases: List<RecentPurchaseSummary> = emptyList(),
    val recentSupplierPayments: List<RecentSupplierPaymentSummary> = emptyList(),
    val topSuppliers: List<TopSupplierSummary> = emptyList()
)

data class RecentPurchaseSummary(
    val purchaseId: String,
    val poNumber: String,
    val supplierName: String,
    val status: String,
    val amount: Double
)

data class RecentSupplierPaymentSummary(
    val paymentId: String,
    val supplierName: String,
    val referenceNumber: String,
    val amount: Double
)

data class TopSupplierSummary(
    val supplierId: String,
    val supplierName: String,
    val totalPurchases: Double,
    val outstandingBalance: Double
)

data class LowStockAlertItem(
    val id: String,
    val ingredientName: String,
    val currentStock: Double,
    val reorderLevel: Double,
    val unit: String,
    val isSampleData: Boolean
)

data class RecentTransactionItem(
    val id: String,
    val orderNumber: String,
    val invoiceNumber: String,
    val customerName: String,
    val amount: Double,
    val paymentMethod: String,
    val status: String,
    val timeLabel: String,
    val isSampleData: Boolean
)
