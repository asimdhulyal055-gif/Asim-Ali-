package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AaCraveDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.BranchEntity
import com.example.data.local.CashbookEntryEntity
import com.example.data.local.ChartOfAccountEntity
import com.example.data.local.DashboardSnapshotEntity
import com.example.data.local.DatabaseSeeder
import com.example.data.local.ExpenseEntity
import com.example.data.local.JournalEntryEntity
import com.example.data.local.JournalLineEntity
import com.example.data.local.PayrollRecordEntity
import com.example.data.local.PosOrderEntity
import com.example.data.local.PurchaseOrderEntity
import com.example.data.local.PurchaseReturnEntity
import com.example.data.local.SupplierPaymentEntity
import com.example.data.local.SyncQueueEntity
import com.example.domain.model.AccountCategory
import com.example.domain.model.AccountType
import com.example.domain.model.AccountingCalculator
import com.example.domain.model.BalanceSheetReport
import com.example.domain.model.BranchFinancialSnapshot
import com.example.domain.model.BranchSummary
import com.example.domain.model.CashBankAccountSummary
import com.example.domain.model.CashFlowReport
import com.example.domain.model.CashbookTransactionRecord
import com.example.domain.model.CashbookTxType
import com.example.domain.model.ChartOfAccountRecord
import com.example.domain.model.ConsolidatedFinancialReport
import com.example.domain.model.CustomerProfileRecord
import com.example.domain.model.ExpenseApprovalStatus
import com.example.domain.model.ExpenseRecord
import com.example.domain.model.ExpenseRecurrence
import com.example.domain.model.GeneralLedgerAccountSection
import com.example.domain.model.GeneralLedgerLineItem
import com.example.domain.model.JournalDraftLine
import com.example.domain.model.JournalEntryRecord
import com.example.domain.model.JournalEntryStatus
import com.example.domain.model.JournalLineRecord
import com.example.domain.model.JournalSourceModule
import com.example.domain.model.OrderStatus
import com.example.domain.model.OutstandingBalanceRow
import com.example.domain.model.PartyLedgerReport
import com.example.domain.model.PartyLedgerRow
import com.example.domain.model.PayrollRecord
import com.example.domain.model.PayrollStatus
import com.example.domain.model.PayrollSummaryReportData
import com.example.domain.model.ProfitAndLossReport
import com.example.domain.model.PurchaseOrderRecord
import com.example.domain.model.PurchasePaymentStatus
import com.example.domain.model.PurchaseStatus
import com.example.domain.model.SalaryType
import com.example.domain.model.SalesSummaryReportData
import com.example.domain.model.StaffProfileRecord
import com.example.domain.model.SupplierSummary
import com.example.domain.model.TrialBalanceReport
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object AccountingIntegrationHelper {

    suspend fun postForPosOrderInTransaction(
        database: AaCraveDatabase,
        order: PosOrderEntity,
        actorUid: String,
        actorName: String
    ) {
        if (order.totalCents <= 0L || order.status == OrderStatus.CANCELLED.name) return
        DatabaseSeeder.ensureChartOfAccountsForBranch(database, order.branchId)
        val dao = database.accountingDao()
        val sourceKey = "POS_ORDER_${order.orderId}"
        if (dao.getJournalEntryBySourceKey(sourceKey) != null) return

        val accounts = dao.getAccountsForBranch(order.branchId)
        val cashAcc = accounts.firstOrNull { it.accountCode == "1010" } ?: return
        val bankAcc = accounts.firstOrNull { it.accountCode == "1020" } ?: cashAcc
        val arAcc = accounts.firstOrNull { it.accountCode == "1100" } ?: bankAcc
        val salesAcc = accounts.firstOrNull { it.accountCode == "4010" } ?: return
        val taxAcc = accounts.firstOrNull { it.accountCode == "2100" }

        val netCashRaw = (order.cashPaidCents - order.changeCents).coerceAtLeast(0L)
        val bankRaw = (order.cardPaidCents + order.bankPaidCents + order.digitalPaidCents).coerceAtLeast(0L)
        val effectiveCash = netCashRaw.coerceAtMost(order.totalCents)
        val effectiveBank = bankRaw.coerceAtMost((order.totalCents - effectiveCash).coerceAtLeast(0L))
        val receivable = (order.totalCents - effectiveCash - effectiveBank).coerceAtLeast(0L)

        val draftLines = mutableListOf<JournalDraftLine>()
        if (effectiveCash > 0L) {
            draftLines += JournalDraftLine(
                accountId = cashAcc.accountId,
                debitCents = effectiveCash,
                creditCents = 0L,
                partyType = order.customerId?.let { "CUSTOMER" },
                partyId = order.customerId,
                partyName = order.customerName,
                memo = "Cash receipt for ${order.invoiceNumber}"
            )
        }
        if (effectiveBank > 0L) {
            draftLines += JournalDraftLine(
                accountId = bankAcc.accountId,
                debitCents = effectiveBank,
                creditCents = 0L,
                partyType = order.customerId?.let { "CUSTOMER" },
                partyId = order.customerId,
                partyName = order.customerName,
                memo = "Card/Bank/Digital receipt for ${order.invoiceNumber}"
            )
        }
        if (receivable > 0L) {
            draftLines += JournalDraftLine(
                accountId = arAcc.accountId,
                debitCents = receivable,
                creditCents = 0L,
                partyType = "CUSTOMER",
                partyId = order.customerId,
                partyName = order.customerName ?: "Walk-in Receivable",
                memo = "Customer receivable balance on ${order.invoiceNumber}"
            )
        }

        val taxPart = if (taxAcc != null) order.taxCents.coerceIn(0L, order.totalCents) else 0L
        val revenuePart = (order.totalCents - taxPart).coerceAtLeast(0L)
        if (revenuePart > 0L) {
            draftLines += JournalDraftLine(
                accountId = salesAcc.accountId,
                debitCents = 0L,
                creditCents = revenuePart,
                partyType = order.customerId?.let { "CUSTOMER" },
                partyId = order.customerId,
                partyName = order.customerName,
                memo = "POS revenue ${order.orderNumber}"
            )
        }
        if (taxPart > 0L && taxAcc != null) {
            draftLines += JournalDraftLine(
                accountId = taxAcc.accountId,
                debitCents = 0L,
                creditCents = taxPart,
                memo = "Sales tax on ${order.invoiceNumber}"
            )
        }

        val entry = insertBalancedJournalInTransaction(
            database = database,
            branchId = order.branchId,
            entryDateMillis = order.createdAt,
            sourceModule = JournalSourceModule.POS_SALE,
            sourceEntityId = order.orderId,
            sourceKey = sourceKey,
            reference = order.invoiceNumber,
            description = "POS Sale ${order.orderNumber} (${order.customerName ?: "Walk-in"})",
            draftLines = draftLines,
            postedByUid = actorUid,
            postedByName = actorName,
            approvedByName = actorName
        ) ?: return

        if (effectiveCash > 0L) {
            dao.insertCashbookEntryIfNew(
                CashbookEntryEntity(
                    cashbookId = "cb_${UUID.randomUUID()}",
                    branchId = order.branchId,
                    accountId = cashAcc.accountId,
                    accountName = cashAcc.accountName,
                    txType = CashbookTxType.RECEIPT.name,
                    amountCents = effectiveCash,
                    reference = order.invoiceNumber,
                    counterpartyName = order.customerName ?: "Walk-in Guest",
                    description = "POS Cash Sale (${order.orderNumber})",
                    txDateMillis = order.createdAt,
                    linkedJournalEntryId = entry.entryId,
                    sourceKey = "CB_POS_CASH_${order.orderId}",
                    recordedByName = actorName,
                    createdAt = order.createdAt
                )
            )
        }
        if (effectiveBank > 0L) {
            dao.insertCashbookEntryIfNew(
                CashbookEntryEntity(
                    cashbookId = "cb_${UUID.randomUUID()}",
                    branchId = order.branchId,
                    accountId = bankAcc.accountId,
                    accountName = bankAcc.accountName,
                    txType = CashbookTxType.RECEIPT.name,
                    amountCents = effectiveBank,
                    reference = order.invoiceNumber,
                    counterpartyName = order.customerName ?: "Walk-in Guest",
                    description = "POS Card/Bank Sale (${order.orderNumber})",
                    txDateMillis = order.createdAt,
                    linkedJournalEntryId = entry.entryId,
                    sourceKey = "CB_POS_BANK_${order.orderId}",
                    recordedByName = actorName,
                    createdAt = order.createdAt
                )
            )
        }
    }

    suspend fun postReversalForPosOrderInTransaction(
        database: AaCraveDatabase,
        order: PosOrderEntity,
        actorUid: String,
        actorName: String,
        reason: String
    ) {
        val dao = database.accountingDao()
        val original = dao.getJournalEntryBySourceKey("POS_ORDER_${order.orderId}") ?: return
        if (original.status == JournalEntryStatus.REVERSED.name) return
        reverseJournalInternalInTransaction(
            database = database,
            original = original,
            sourceKey = "REV_POS_${order.orderId}",
            sourceModule = JournalSourceModule.POS_REFUND,
            reason = reason,
            actorUid = actorUid,
            actorName = actorName
        )
    }

    suspend fun postForPurchaseOrderInTransaction(
        database: AaCraveDatabase,
        po: PurchaseOrderEntity,
        actorUid: String,
        actorName: String
    ) {
        if (po.totalCents <= 0L) return
        if (po.status != PurchaseStatus.RECEIVED.name && po.status != PurchaseStatus.PARTIALLY_RECEIVED.name) return
        DatabaseSeeder.ensureChartOfAccountsForBranch(database, po.branchId)
        val dao = database.accountingDao()
        val sourceKey = "PO_RECV_${po.purchaseId}"
        if (dao.getJournalEntryBySourceKey(sourceKey) != null) return

        val accounts = dao.getAccountsForBranch(po.branchId)
        val cogsAcc = accounts.firstOrNull { it.accountCode == "5010" } ?: return
        val apAcc = accounts.firstOrNull { it.accountCode == "2010" } ?: return

        insertBalancedJournalInTransaction(
            database = database,
            branchId = po.branchId,
            entryDateMillis = po.updatedAt,
            sourceModule = JournalSourceModule.PURCHASE_BILL,
            sourceEntityId = po.purchaseId,
            sourceKey = sourceKey,
            reference = po.poNumber,
            description = "Supplier Purchase ${po.poNumber} (${po.supplierName})",
            draftLines = listOf(
                JournalDraftLine(
                    accountId = cogsAcc.accountId,
                    debitCents = po.totalCents,
                    creditCents = 0L,
                    partyType = "SUPPLIER",
                    partyId = po.supplierId,
                    partyName = po.supplierName,
                    memo = "Goods received on ${po.poNumber}"
                ),
                JournalDraftLine(
                    accountId = apAcc.accountId,
                    debitCents = 0L,
                    creditCents = po.totalCents,
                    partyType = "SUPPLIER",
                    partyId = po.supplierId,
                    partyName = po.supplierName,
                    memo = "Accounts Payable to ${po.supplierName}"
                )
            ),
            postedByUid = actorUid,
            postedByName = actorName,
            approvedByName = po.approvedByName ?: actorName
        )
    }

    suspend fun postForPurchaseReturnInTransaction(
        database: AaCraveDatabase,
        ret: PurchaseReturnEntity,
        supplierName: String,
        actorUid: String,
        actorName: String
    ) {
        if (ret.returnAmountCents <= 0L) return
        DatabaseSeeder.ensureChartOfAccountsForBranch(database, ret.branchId)
        val dao = database.accountingDao()
        val sourceKey = "PO_RET_${ret.returnId}"
        if (dao.getJournalEntryBySourceKey(sourceKey) != null) return

        val accounts = dao.getAccountsForBranch(ret.branchId)
        val cogsAcc = accounts.firstOrNull { it.accountCode == "5010" } ?: return
        val apAcc = accounts.firstOrNull { it.accountCode == "2010" } ?: return

        insertBalancedJournalInTransaction(
            database = database,
            branchId = ret.branchId,
            entryDateMillis = ret.returnedAt,
            sourceModule = JournalSourceModule.PURCHASE_RETURN,
            sourceEntityId = ret.returnId,
            sourceKey = sourceKey,
            reference = ret.poNumber,
            description = "Purchase Return on ${ret.poNumber}: ${ret.itemName}",
            draftLines = listOf(
                JournalDraftLine(
                    accountId = apAcc.accountId,
                    debitCents = ret.returnAmountCents,
                    creditCents = 0L,
                    partyType = "SUPPLIER",
                    partyId = ret.supplierId,
                    partyName = supplierName,
                    memo = "AP credit for purchase return ${ret.poNumber}"
                ),
                JournalDraftLine(
                    accountId = cogsAcc.accountId,
                    debitCents = 0L,
                    creditCents = ret.returnAmountCents,
                    partyType = "SUPPLIER",
                    partyId = ret.supplierId,
                    partyName = supplierName,
                    memo = "Purchase return ${ret.returnQty} ${ret.unit} ${ret.itemName}"
                )
            ),
            postedByUid = actorUid,
            postedByName = actorName,
            approvedByName = actorName
        )
    }

    suspend fun postForSupplierPaymentInTransaction(
        database: AaCraveDatabase,
        payment: SupplierPaymentEntity,
        actorUid: String,
        actorName: String
    ) {
        if (payment.amountCents <= 0L) return
        DatabaseSeeder.ensureChartOfAccountsForBranch(database, payment.branchId)
        val dao = database.accountingDao()
        val sourceKey = "SUP_PAY_${payment.paymentId}"
        if (dao.getJournalEntryBySourceKey(sourceKey) != null) return

        val accounts = dao.getAccountsForBranch(payment.branchId)
        val apAcc = accounts.firstOrNull { it.accountCode == "2010" } ?: return
        val payAcc = pickCashOrBankAccount(accounts, payment.paymentMethod) ?: return

        val entry = insertBalancedJournalInTransaction(
            database = database,
            branchId = payment.branchId,
            entryDateMillis = payment.paymentDateMillis,
            sourceModule = JournalSourceModule.SUPPLIER_PAYMENT,
            sourceEntityId = payment.paymentId,
            sourceKey = sourceKey,
            reference = payment.referenceNumber,
            description = "Supplier Payment to ${payment.supplierName} (${payment.paymentMethod})",
            draftLines = listOf(
                JournalDraftLine(
                    accountId = apAcc.accountId,
                    debitCents = payment.amountCents,
                    creditCents = 0L,
                    partyType = "SUPPLIER",
                    partyId = payment.supplierId,
                    partyName = payment.supplierName,
                    memo = "Settlement ref ${payment.referenceNumber}"
                ),
                JournalDraftLine(
                    accountId = payAcc.accountId,
                    debitCents = 0L,
                    creditCents = payment.amountCents,
                    partyType = "SUPPLIER",
                    partyId = payment.supplierId,
                    partyName = payment.supplierName,
                    memo = "Paid via ${payment.paymentMethod}"
                )
            ),
            postedByUid = actorUid,
            postedByName = actorName,
            approvedByName = payment.paidByName
        ) ?: return

        dao.insertCashbookEntryIfNew(
            CashbookEntryEntity(
                cashbookId = "cb_${UUID.randomUUID()}",
                branchId = payment.branchId,
                accountId = payAcc.accountId,
                accountName = payAcc.accountName,
                txType = CashbookTxType.PAYMENT.name,
                amountCents = payment.amountCents,
                reference = payment.referenceNumber,
                counterpartyName = payment.supplierName,
                description = "Supplier Payment (${payment.poNumber ?: "On Account"})",
                txDateMillis = payment.paymentDateMillis,
                linkedJournalEntryId = entry.entryId,
                sourceKey = "CB_SUP_PAY_${payment.paymentId}",
                recordedByName = payment.paidByName,
                createdAt = payment.createdAt
            )
        )
    }

    suspend fun postForApprovedExpenseInTransaction(
        database: AaCraveDatabase,
        expense: ExpenseEntity,
        actorUid: String,
        actorName: String
    ) {
        if (expense.amountCents <= 0L || expense.approvalStatus != ExpenseApprovalStatus.APPROVED.name) return
        DatabaseSeeder.ensureChartOfAccountsForBranch(database, expense.branchId)
        val dao = database.accountingDao()
        val sourceKey = "EXP_APP_${expense.expenseId}"
        if (dao.getJournalEntryBySourceKey(sourceKey) != null) return

        val accounts = dao.getAccountsForBranch(expense.branchId)
        val expAcc = accounts.firstOrNull { it.accountCode == "5100" } ?: return
        val payAcc = pickCashOrBankAccount(accounts, expense.paymentMethod) ?: return

        val entry = insertBalancedJournalInTransaction(
            database = database,
            branchId = expense.branchId,
            entryDateMillis = expense.expenseDateMillis,
            sourceModule = JournalSourceModule.EXPENSE,
            sourceEntityId = expense.expenseId,
            sourceKey = sourceKey,
            reference = "EXP-${expense.category.take(6).uppercase(Locale.US)}",
            description = "Approved Expense [${expense.category}]: ${expense.description}",
            draftLines = listOf(
                JournalDraftLine(
                    accountId = expAcc.accountId,
                    debitCents = expense.amountCents,
                    creditCents = 0L,
                    memo = "${expense.category}: ${expense.description}"
                ),
                JournalDraftLine(
                    accountId = payAcc.accountId,
                    debitCents = 0L,
                    creditCents = expense.amountCents,
                    memo = "Paid via ${expense.paymentMethod}"
                )
            ),
            postedByUid = actorUid,
            postedByName = actorName,
            approvedByName = expense.approvedByName ?: actorName
        ) ?: return

        dao.insertCashbookEntryIfNew(
            CashbookEntryEntity(
                cashbookId = "cb_${UUID.randomUUID()}",
                branchId = expense.branchId,
                accountId = payAcc.accountId,
                accountName = payAcc.accountName,
                txType = CashbookTxType.PAYMENT.name,
                amountCents = expense.amountCents,
                reference = "EXP-${expense.category.take(6).uppercase(Locale.US)}",
                counterpartyName = expense.category,
                description = expense.description,
                txDateMillis = expense.expenseDateMillis,
                linkedJournalEntryId = entry.entryId,
                sourceKey = "CB_EXP_${expense.expenseId}",
                recordedByName = expense.approvedByName ?: actorName,
                createdAt = expense.createdAt
            )
        )
    }

    suspend fun postReversalForExpenseInTransaction(
        database: AaCraveDatabase,
        expense: ExpenseEntity,
        actorUid: String,
        actorName: String,
        reason: String
    ) {
        val dao = database.accountingDao()
        val original = dao.getJournalEntryBySourceKey("EXP_APP_${expense.expenseId}") ?: return
        if (original.status == JournalEntryStatus.REVERSED.name) return
        reverseJournalInternalInTransaction(
            database = database,
            original = original,
            sourceKey = "REV_EXP_${expense.expenseId}",
            sourceModule = JournalSourceModule.REVERSAL,
            reason = reason,
            actorUid = actorUid,
            actorName = actorName
        )
    }

    suspend fun postForPayrollApprovalInTransaction(
        database: AaCraveDatabase,
        payroll: PayrollRecordEntity,
        actorUid: String,
        actorName: String
    ) {
        if (payroll.netSalaryCents <= 0L) return
        if (payroll.status != PayrollStatus.APPROVED.name && payroll.status != PayrollStatus.PAID.name) return
        DatabaseSeeder.ensureChartOfAccountsForBranch(database, payroll.branchId)
        val dao = database.accountingDao()
        val sourceKey = "PAY_ACCRUAL_${payroll.payrollId}"
        if (dao.getJournalEntryBySourceKey(sourceKey) != null) return

        val accounts = dao.getAccountsForBranch(payroll.branchId)
        val salExpAcc = accounts.firstOrNull { it.accountCode == "5200" } ?: return
        val salPayAcc = accounts.firstOrNull { it.accountCode == "2200" } ?: return

        insertBalancedJournalInTransaction(
            database = database,
            branchId = payroll.branchId,
            entryDateMillis = payroll.approvedAt ?: payroll.updatedAt,
            sourceModule = JournalSourceModule.PAYROLL_ACCRUAL,
            sourceEntityId = payroll.payrollId,
            sourceKey = sourceKey,
            reference = "PAY-${payroll.periodKey}-${payroll.employeeCode}",
            description = "Payroll Approved: ${payroll.staffName} (${payroll.periodLabel})",
            draftLines = listOf(
                JournalDraftLine(
                    accountId = salExpAcc.accountId,
                    debitCents = payroll.netSalaryCents,
                    creditCents = 0L,
                    partyType = "STAFF",
                    partyId = payroll.staffId,
                    partyName = payroll.staffName,
                    memo = "Payroll expense ${payroll.periodLabel}"
                ),
                JournalDraftLine(
                    accountId = salPayAcc.accountId,
                    debitCents = 0L,
                    creditCents = payroll.netSalaryCents,
                    partyType = "STAFF",
                    partyId = payroll.staffId,
                    partyName = payroll.staffName,
                    memo = "Payroll payable ${payroll.employeeCode}"
                )
            ),
            postedByUid = actorUid,
            postedByName = actorName,
            approvedByName = payroll.approvedByName ?: actorName
        )
    }

    suspend fun postForPayrollPaymentInTransaction(
        database: AaCraveDatabase,
        payroll: PayrollRecordEntity,
        actorUid: String,
        actorName: String
    ) {
        if (payroll.netSalaryCents <= 0L || payroll.status != PayrollStatus.PAID.name) return
        postForPayrollApprovalInTransaction(database, payroll, actorUid, actorName)
        val dao = database.accountingDao()
        val sourceKey = "PAY_PAID_${payroll.payrollId}"
        if (dao.getJournalEntryBySourceKey(sourceKey) != null) return

        val accounts = dao.getAccountsForBranch(payroll.branchId)
        val salPayAcc = accounts.firstOrNull { it.accountCode == "2200" } ?: return
        val payAcc = pickCashOrBankAccount(accounts, payroll.paymentMethod ?: "Bank Transfer") ?: return
        val payDate = payroll.paymentDateMillis ?: payroll.updatedAt
        val ref = payroll.paymentReference ?: "PAY-${payroll.periodKey}"

        val entry = insertBalancedJournalInTransaction(
            database = database,
            branchId = payroll.branchId,
            entryDateMillis = payDate,
            sourceModule = JournalSourceModule.PAYROLL_PAYMENT,
            sourceEntityId = payroll.payrollId,
            sourceKey = sourceKey,
            reference = ref,
            description = "Payroll Paid: ${payroll.staffName} (${payroll.periodLabel})",
            draftLines = listOf(
                JournalDraftLine(
                    accountId = salPayAcc.accountId,
                    debitCents = payroll.netSalaryCents,
                    creditCents = 0L,
                    partyType = "STAFF",
                    partyId = payroll.staffId,
                    partyName = payroll.staffName,
                    memo = "Disbursement ref $ref"
                ),
                JournalDraftLine(
                    accountId = payAcc.accountId,
                    debitCents = 0L,
                    creditCents = payroll.netSalaryCents,
                    partyType = "STAFF",
                    partyId = payroll.staffId,
                    partyName = payroll.staffName,
                    memo = "Paid via ${payroll.paymentMethod ?: "Bank Transfer"}"
                )
            ),
            postedByUid = actorUid,
            postedByName = actorName,
            approvedByName = payroll.approvedByName ?: actorName
        ) ?: return

        dao.insertCashbookEntryIfNew(
            CashbookEntryEntity(
                cashbookId = "cb_${UUID.randomUUID()}",
                branchId = payroll.branchId,
                accountId = payAcc.accountId,
                accountName = payAcc.accountName,
                txType = CashbookTxType.PAYMENT.name,
                amountCents = payroll.netSalaryCents,
                reference = ref,
                counterpartyName = payroll.staffName,
                description = "Salary Disbursement (${payroll.periodLabel})",
                txDateMillis = payDate,
                linkedJournalEntryId = entry.entryId,
                sourceKey = "CB_PAY_${payroll.payrollId}",
                recordedByName = actorName,
                createdAt = payroll.updatedAt
            )
        )
    }

    internal suspend fun insertBalancedJournalInTransaction(
        database: AaCraveDatabase,
        branchId: String,
        entryDateMillis: Long,
        sourceModule: JournalSourceModule,
        sourceEntityId: String?,
        sourceKey: String,
        reference: String,
        description: String,
        draftLines: List<JournalDraftLine>,
        postedByUid: String,
        postedByName: String,
        approvedByName: String?,
        reversedEntryId: String? = null
    ): JournalEntryEntity? {
        val validation = AccountingCalculator.validateJournalLines(draftLines)
        if (!validation.isValid) {
            throw IllegalArgumentException(validation.errorMessage ?: "Invalid unbalanced journal entry.")
        }

        val dao = database.accountingDao()
        val accountsById = dao.getAccountsForBranch(branchId).associateBy { it.accountId }
        if (draftLines.any { accountsById[it.accountId] == null }) {
            throw IllegalArgumentException("One or more selected accounts do not belong to branch $branchId.")
        }
        if (draftLines.any { accountsById[it.accountId]?.isActive != true }) {
            throw IllegalStateException("Cannot post journal entry to an inactive account.")
        }

        val now = System.currentTimeMillis()
        val seq = dao.countJournalsForBranch(branchId) + 1
        val dateTag = SimpleDateFormat("yyMM", Locale.US).format(Date(entryDateMillis))
        val entryId = "je_${UUID.randomUUID()}"
        val entryNumber = "JE-$dateTag-%04d".format(Locale.US, seq)

        val entryEntity = JournalEntryEntity(
            entryId = entryId,
            branchId = branchId,
            entryNumber = entryNumber,
            entryDateMillis = entryDateMillis,
            sourceModule = sourceModule.name,
            sourceEntityId = sourceEntityId,
            sourceKey = sourceKey,
            reference = reference.trim().ifBlank { entryNumber },
            description = description.trim(),
            totalDebitCents = validation.totalDebitCents,
            totalCreditCents = validation.totalCreditCents,
            status = JournalEntryStatus.POSTED.name,
            reversedEntryId = reversedEntryId,
            postedByUid = postedByUid,
            postedByName = postedByName,
            approvedByName = approvedByName,
            createdAt = now
        )

        val insertedRow = dao.insertJournalEntryIfNew(entryEntity)
        if (insertedRow == -1L) {
            return null // Duplicate sourceKey prevented idempotently
        }

        val lineEntities = draftLines.mapIndexed { idx, line ->
            val acc = accountsById.getValue(line.accountId)
            JournalLineEntity(
                lineId = "${entryId}_l_$idx",
                entryId = entryId,
                branchId = branchId,
                accountId = acc.accountId,
                accountCode = acc.accountCode,
                accountName = acc.accountName,
                accountType = acc.accountType,
                debitCents = line.debitCents,
                creditCents = line.creditCents,
                partyType = line.partyType,
                partyId = line.partyId,
                partyName = line.partyName,
                memo = line.memo.trim(),
                entryDateMillis = entryDateMillis
            )
        }
        dao.insertJournalLines(lineEntities)

        database.syncQueueDao().enqueueUnique(
            SyncQueueEntity(
                transactionId = "sync_je_$sourceKey",
                branchId = branchId,
                entityType = "ACCOUNTING_JOURNAL_REDACTED",
                entityId = entryId,
                payloadJson = """{"entryNumber":"$entryNumber","module":"${sourceModule.name}","status":"POSTED","redacted":true}""",
                status = "PENDING",
                lastError = "Encrypted/Auth accounting sync pending — financial ledger stored in Room"
            )
        )
        database.auditLogDao().insertLog(
            AuditLogEntity(
                branchId = branchId,
                actorUid = postedByUid,
                actorEmail = postedByName,
                actorRole = sourceModule.label,
                action = "JOURNAL_POSTED",
                details = "$entryNumber | ${sourceModule.label} | Ref: ${entryEntity.reference} | Dr/Cr: ${entryEntity.totalDebitCents}c"
            )
        )
        return entryEntity
    }

    internal suspend fun reverseJournalInternalInTransaction(
        database: AaCraveDatabase,
        original: JournalEntryEntity,
        sourceKey: String,
        sourceModule: JournalSourceModule,
        reason: String,
        actorUid: String,
        actorName: String
    ): JournalEntryEntity {
        val dao = database.accountingDao()
        val origLines = dao.getLinesForEntry(original.entryId)
        val reversedDrafts = origLines.map { line ->
            JournalDraftLine(
                accountId = line.accountId,
                debitCents = line.creditCents,
                creditCents = line.debitCents,
                partyType = line.partyType,
                partyId = line.partyId,
                partyName = line.partyName,
                memo = "Reversal of ${original.entryNumber}: ${reason.trim()}"
            )
        }

        val revEntry = insertBalancedJournalInTransaction(
            database = database,
            branchId = original.branchId,
            entryDateMillis = System.currentTimeMillis(),
            sourceModule = sourceModule,
            sourceEntityId = original.entryId,
            sourceKey = sourceKey,
            reference = "REV-${original.entryNumber}",
            description = "Reversal of ${original.entryNumber} (${original.description}) — ${reason.trim()}",
            draftLines = reversedDrafts,
            postedByUid = actorUid,
            postedByName = actorName,
            approvedByName = actorName,
            reversedEntryId = original.entryId
        ) ?: return dao.getJournalEntryBySourceKey(sourceKey) ?: original

        dao.updateJournalEntry(
            original.copy(
                status = JournalEntryStatus.REVERSED.name,
                reversedEntryId = revEntry.entryId
            )
        )

        val accountsById = dao.getAccountsForBranch(original.branchId).associateBy { it.accountId }
        reversedDrafts.forEachIndexed { idx, line ->
            val acc = accountsById[line.accountId]
            if (acc != null && acc.isCashOrBank) {
                val txType = if (line.debitCents > 0L) CashbookTxType.RECEIPT else CashbookTxType.PAYMENT
                val amount = if (line.debitCents > 0L) line.debitCents else line.creditCents
                dao.insertCashbookEntryIfNew(
                    CashbookEntryEntity(
                        cashbookId = "cb_${UUID.randomUUID()}",
                        branchId = original.branchId,
                        accountId = acc.accountId,
                        accountName = acc.accountName,
                        txType = txType.name,
                        amountCents = amount,
                        reference = revEntry.reference,
                        counterpartyName = line.partyName ?: "Reversal",
                        description = "Reversal ${original.entryNumber}: ${reason.trim()}",
                        txDateMillis = revEntry.entryDateMillis,
                        linkedJournalEntryId = revEntry.entryId,
                        sourceKey = "CB_${sourceKey}_$idx",
                        recordedByName = actorName,
                        createdAt = revEntry.createdAt
                    )
                )
            }
        }

        database.auditLogDao().insertLog(
            AuditLogEntity(
                branchId = original.branchId,
                actorUid = actorUid,
                actorEmail = actorName,
                actorRole = "Financial Approver",
                action = "JOURNAL_REVERSED",
                details = "Reversed ${original.entryNumber} via ${revEntry.entryNumber} — Reason: ${reason.trim()}"
            )
        )
        return revEntry
    }

    private fun pickCashOrBankAccount(
        accounts: List<ChartOfAccountEntity>,
        paymentMethod: String
    ): ChartOfAccountEntity? {
        val isCash = paymentMethod.contains("cash", ignoreCase = true)
        return if (isCash) {
            accounts.firstOrNull { it.accountCode == "1010" } ?: accounts.firstOrNull { it.isCashOrBank }
        } else {
            accounts.firstOrNull { it.accountCode == "1020" } ?: accounts.firstOrNull { it.isCashOrBank }
        }
    }
}

class AccountingRepository(private val database: AaCraveDatabase) {

    private val dao = database.accountingDao()
    private val branchDao = database.branchDao()
    private val staffDao = database.staffDao()
    private val supplierDao = database.supplierDao()
    private val customerDao = database.customerLoyaltyDao()
    private val auditLogDao = database.auditLogDao()

    fun observeBranches(): Flow<List<BranchSummary>> =
        branchDao.observeBranches().map { list -> list.map { it.toBranchSummary() } }

    fun observeChartOfAccounts(branchId: String?): Flow<List<ChartOfAccountRecord>> {
        val accFlow = if (branchId != null) dao.observeChartOfAccounts(branchId) else dao.observeAllChartOfAccounts()
        val entryFlow = if (branchId != null) dao.observeJournalEntries(branchId) else dao.observeAllJournalEntries()
        val lineFlow = if (branchId != null) dao.observeJournalLines(branchId) else dao.observeAllJournalLines()
        return combine(accFlow, entryFlow, lineFlow) { accounts, entries, lines ->
            val validEntryIds = entries.filter { it.status == JournalEntryStatus.POSTED.name || it.status == JournalEntryStatus.REVERSED.name }
                .map { it.entryId }.toSet()
            val linesByAcc = lines.filter { it.entryId in validEntryIds }.groupBy { it.accountId }
            accounts.map { acc ->
                val accLines = linesByAcc[acc.accountId].orEmpty().map { it.toDomain() }
                val rec = acc.toDomain()
                val net = AccountingCalculator.computeAccountNetBalance(rec, accLines)
                rec.copy(
                    currentBalanceCents = net,
                    debitTurnoverCents = accLines.sumOf { it.debitCents },
                    creditTurnoverCents = accLines.sumOf { it.creditCents }
                )
            }
        }
    }

    fun observeJournalEntries(branchId: String?): Flow<List<JournalEntryRecord>> {
        val entryFlow = if (branchId != null) dao.observeJournalEntries(branchId) else dao.observeAllJournalEntries()
        val lineFlow = if (branchId != null) dao.observeJournalLines(branchId) else dao.observeAllJournalLines()
        return combine(entryFlow, lineFlow) { entries, lines ->
            val byEntry = lines.groupBy { it.entryId }
            entries.map { e -> e.toDomain(byEntry[e.entryId].orEmpty().map { it.toDomain() }) }
        }
    }

    fun observeCashbookTransactions(branchId: String?): Flow<List<CashbookTransactionRecord>> {
        val flow = if (branchId != null) dao.observeCashbookEntries(branchId) else dao.observeAllCashbookEntries()
        return flow.map { list -> list.map { it.toDomain() } }
    }

    fun observePosOrders(branchId: String?): Flow<List<PosOrderEntity>> =
        if (branchId != null) database.posDao().observeOrders(branchId) else dao.observeAllPosOrders()

    fun observePurchaseOrders(branchId: String?): Flow<List<PurchaseOrderEntity>> =
        if (branchId != null) database.purchaseDao().observePurchaseOrders(branchId) else dao.observeAllPurchaseOrders()

    fun observeExpenses(branchId: String?): Flow<List<ExpenseEntity>> =
        if (branchId != null) database.expenseDao().observeExpenses(branchId) else dao.observeAllExpenses()

    fun observePayrolls(branchId: String?): Flow<List<PayrollRecordEntity>> =
        if (branchId != null) database.leavePayrollDao().observePayrollRecords(branchId) else database.leavePayrollDao().observeAllPayrolls()

    fun observeSuppliers(branchId: String?): Flow<List<SupplierSummary>> =
        (if (branchId != null) supplierDao.observeSuppliers(branchId) else dao.observeAllSuppliers())
            .map { list -> list.map { it.toDomainSummary() } }

    fun observeCustomers(branchId: String?): Flow<List<CustomerProfileRecord>> =
        (if (branchId != null) customerDao.observeCustomers(branchId) else dao.observeAllCustomers())
            .map { list -> list.map { it.toCustomerDomain() } }

    fun observeAllPurchaseReturns(): Flow<List<PurchaseReturnEntity>> = dao.observeAllPurchaseReturns()

    fun observeAllSupplierPayments(): Flow<List<SupplierPaymentEntity>> = dao.observeAllSupplierPayments()

    fun observeAllStaff(): Flow<List<com.example.data.local.StaffProfileEntity>> = staffDao.observeAllStaff()

    suspend fun syncAllStoredTransactions(session: UserSession): Result<Int> {
        if (!session.capabilities.canViewAccounting && !session.capabilities.canExportReports) {
            return Result.failure(SecurityException("Access denied: unauthorized to sync financial records."))
        }
        return try {
            val postedBefore = dao.observeAllJournalEntries().first().size
            database.withTransaction {
                val branches = branchDao.getAllBranches()
                branches.forEach { b ->
                    DatabaseSeeder.ensureChartOfAccountsForBranch(
                        database = database,
                        branchId = b.branchId,
                        openingCashCents = b.openingCashCents,
                        openingBankCents = b.openingBankCents
                    )
                }

                val suppliersById = supplierDao.getAllSuppliersForAccounting().associateBy { it.supplierId }

                dao.getAllPosOrders().forEach { order ->
                    if (order.status != OrderStatus.CANCELLED.name) {
                        AccountingIntegrationHelper.postForPosOrderInTransaction(database, order, session.uid, session.displayName)
                        if (order.status == OrderStatus.REFUNDED.name) {
                            AccountingIntegrationHelper.postReversalForPosOrderInTransaction(
                                database = database,
                                order = order,
                                actorUid = session.uid,
                                actorName = order.approvedBy ?: session.displayName,
                                reason = order.cancellationReason ?: "POS Order Refunded"
                            )
                        }
                    }
                }

                dao.getAllPurchaseOrders().forEach { po ->
                    AccountingIntegrationHelper.postForPurchaseOrderInTransaction(database, po, session.uid, session.displayName)
                }

                dao.getAllPurchaseReturns().forEach { ret ->
                    val supName = suppliersById[ret.supplierId]?.name ?: "Supplier"
                    AccountingIntegrationHelper.postForPurchaseReturnInTransaction(database, ret, supName, session.uid, session.displayName)
                }

                dao.getAllSupplierPayments().forEach { pay ->
                    AccountingIntegrationHelper.postForSupplierPaymentInTransaction(database, pay, session.uid, session.displayName)
                }

                dao.getAllExpenses().forEach { exp ->
                    AccountingIntegrationHelper.postForApprovedExpenseInTransaction(database, exp, session.uid, session.displayName)
                }

                database.leavePayrollDao().getAllPayrolls().forEach { pay ->
                    AccountingIntegrationHelper.postForPayrollApprovalInTransaction(database, pay, session.uid, session.displayName)
                    AccountingIntegrationHelper.postForPayrollPaymentInTransaction(database, pay, session.uid, session.displayName)
                }
            }
            val postedAfter = dao.observeAllJournalEntries().first().size
            Result.success((postedAfter - postedBefore).coerceAtLeast(0))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveChartAccount(
        session: UserSession,
        branchId: String,
        accountId: String?,
        accountCode: String,
        accountName: String,
        accountType: AccountType,
        accountCategory: AccountCategory,
        openingBalanceCents: Long,
        isActive: Boolean
    ): Result<ChartOfAccountRecord> {
        val branchErr = checkBranchAccess(session, branchId, requireConsolidated = false)
        if (branchErr != null) return Result.failure(branchErr)
        if (!session.capabilities.canViewAccounting) {
            return Result.failure(SecurityException("Access denied: unauthorized to modify Chart of Accounts."))
        }
        val cleanCode = accountCode.trim()
        val cleanName = accountName.trim()
        if (cleanCode.isBlank() || cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Account code and account name are required."))
        }
        return try {
            val saved = database.withTransaction {
                DatabaseSeeder.ensureChartOfAccountsForBranch(database, branchId)
                val duplicate = dao.getAccountByCode(branchId, cleanCode)
                if (duplicate != null && duplicate.accountId != accountId) {
                    throw IllegalArgumentException("Duplicate account code '$cleanCode' already exists in this branch.")
                }
                val existing = accountId?.let { dao.getAccountById(it) }
                if (existing != null && existing.isSystemAccount && existing.accountType != accountType.name) {
                    throw IllegalStateException("System account classification (${existing.accountType}) cannot be changed.")
                }
                val isCashBank = accountCategory == AccountCategory.CASH || accountCategory == AccountCategory.BANK
                val entity = ChartOfAccountEntity(
                    accountId = existing?.accountId ?: "coa_${branchId}_${cleanCode}_${UUID.randomUUID().toString().take(6)}",
                    branchId = branchId,
                    accountCode = cleanCode,
                    accountName = cleanName,
                    accountType = accountType.name,
                    accountCategory = accountCategory.name,
                    isCashOrBank = isCashBank,
                    openingBalanceCents = openingBalanceCents,
                    isSystemAccount = existing?.isSystemAccount ?: false,
                    isActive = if (existing?.isSystemAccount == true) true else isActive,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                )
                dao.upsertAccount(entity)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = if (existing == null) "COA_ACCOUNT_CREATED" else "COA_ACCOUNT_UPDATED",
                        details = "${entity.accountCode} ${entity.accountName} (${accountType.label}, Opening: ${openingBalanceCents}c)"
                    )
                )
                entity.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun postManualJournalEntry(
        session: UserSession,
        branchId: String,
        idempotencyKey: String,
        entryDateMillis: Long,
        reference: String,
        description: String,
        draftLines: List<JournalDraftLine>
    ): Result<JournalEntryRecord> {
        val branchErr = checkBranchAccess(session, branchId, requireConsolidated = false)
        if (branchErr != null) return Result.failure(branchErr)
        if (!session.capabilities.canPostJournalEntries) {
            return Result.failure(SecurityException("Access denied: unauthorized to post journal entries."))
        }
        if (description.isBlank()) {
            return Result.failure(IllegalArgumentException("Journal entry description is required."))
        }
        val validation = AccountingCalculator.validateJournalLines(draftLines)
        if (!validation.isValid) {
            return Result.failure(IllegalArgumentException(validation.errorMessage ?: "Journal entry is unbalanced."))
        }

        return try {
            val saved = database.withTransaction {
                DatabaseSeeder.ensureChartOfAccountsForBranch(database, branchId)
                val existing = dao.getJournalEntryBySourceKey(idempotencyKey)
                if (existing != null) {
                    val lines = dao.getLinesForEntry(existing.entryId).map { it.toDomain() }
                    return@withTransaction existing.toDomain(lines)
                }

                val entry = AccountingIntegrationHelper.insertBalancedJournalInTransaction(
                    database = database,
                    branchId = branchId,
                    entryDateMillis = entryDateMillis,
                    sourceModule = JournalSourceModule.MANUAL,
                    sourceEntityId = null,
                    sourceKey = idempotencyKey,
                    reference = reference,
                    description = description,
                    draftLines = draftLines,
                    postedByUid = session.uid,
                    postedByName = session.displayName,
                    approvedByName = session.displayName
                ) ?: throw IllegalStateException("Duplicate journal submission prevented.")

                val lines = dao.getLinesForEntry(entry.entryId).map { it.toDomain() }
                entry.toDomain(lines)
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reverseJournalEntry(
        session: UserSession,
        entryId: String,
        reason: String
    ): Result<JournalEntryRecord> {
        if (!session.capabilities.canApproveFinancialAdjustments) {
            return Result.failure(SecurityException("Access denied: Owner, Branch Manager, or Super Admin approval is required for financial adjustments and reversals."))
        }
        if (reason.isBlank()) {
            return Result.failure(IllegalArgumentException("Authorization reason is required to reverse a historical journal entry."))
        }
        return try {
            val saved = database.withTransaction {
                val orig = dao.getJournalEntryById(entryId) ?: throw IllegalArgumentException("Journal entry not found.")
                val branchErr = checkBranchAccess(session, orig.branchId, requireConsolidated = false)
                if (branchErr != null) throw branchErr
                if (orig.status == JournalEntryStatus.REVERSED.name) {
                    throw IllegalStateException("Journal entry ${orig.entryNumber} is already reversed.")
                }

                val rev = AccountingIntegrationHelper.reverseJournalInternalInTransaction(
                    database = database,
                    original = orig,
                    sourceKey = "REV_MANUAL_${orig.entryId}",
                    sourceModule = JournalSourceModule.REVERSAL,
                    reason = reason,
                    actorUid = session.uid,
                    actorName = session.displayName
                )
                val lines = dao.getLinesForEntry(rev.entryId).map { it.toDomain() }
                rev.toDomain(lines)
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordCashbookTransaction(
        session: UserSession,
        branchId: String,
        idempotencyKey: String,
        txType: CashbookTxType,
        cashBankAccountId: String,
        counterAccountId: String,
        amountCents: Long,
        txDateMillis: Long,
        reference: String,
        counterpartyName: String,
        description: String
    ): Result<CashbookTransactionRecord> {
        val branchErr = checkBranchAccess(session, branchId, requireConsolidated = false)
        if (branchErr != null) return Result.failure(branchErr)
        if (!session.capabilities.canManageCashBankAccounts) {
            return Result.failure(SecurityException("Access denied: unauthorized to record cashbook entries."))
        }
        if (amountCents <= 0L) {
            return Result.failure(IllegalArgumentException("Amount must be greater than zero."))
        }
        if (cashBankAccountId == counterAccountId) {
            return Result.failure(IllegalArgumentException("Source Cash/Bank account and offset account must be different."))
        }
        if (description.isBlank()) {
            return Result.failure(IllegalArgumentException("Transaction description is required."))
        }

        return try {
            val saved = database.withTransaction {
                DatabaseSeeder.ensureChartOfAccountsForBranch(database, branchId)
                val cashAcc = dao.getAccountById(cashBankAccountId)
                    ?: throw IllegalArgumentException("Selected Cash/Bank account not found.")
                val offsetAcc = dao.getAccountById(counterAccountId)
                    ?: throw IllegalArgumentException("Selected offset account not found.")
                if (!cashAcc.isCashOrBank) {
                    throw IllegalArgumentException("Primary account must be a Cash or Bank account.")
                }

                val sourceKey = "CB_JE_$idempotencyKey"
                val existingJe = dao.getJournalEntryBySourceKey(sourceKey)
                if (existingJe != null) {
                    val existingCb = dao.observeCashbookEntries(branchId).first()
                        .first { it.linkedJournalEntryId == existingJe.entryId }
                    return@withTransaction existingCb.toDomain()
                }

                val isInflow = txType == CashbookTxType.RECEIPT || txType == CashbookTxType.TRANSFER_IN
                val lines = if (isInflow) {
                    listOf(
                        JournalDraftLine(cashAcc.accountId, amountCents, 0L, memo = description),
                        JournalDraftLine(offsetAcc.accountId, 0L, amountCents, memo = description)
                    )
                } else {
                    listOf(
                        JournalDraftLine(offsetAcc.accountId, amountCents, 0L, memo = description),
                        JournalDraftLine(cashAcc.accountId, 0L, amountCents, memo = description)
                    )
                }

                val srcModule = when (txType) {
                    CashbookTxType.RECEIPT -> JournalSourceModule.CASHBOOK_RECEIPT
                    CashbookTxType.PAYMENT -> JournalSourceModule.CASHBOOK_PAYMENT
                    CashbookTxType.TRANSFER_IN, CashbookTxType.TRANSFER_OUT -> JournalSourceModule.CASHBOOK_TRANSFER
                }

                val entry = AccountingIntegrationHelper.insertBalancedJournalInTransaction(
                    database = database,
                    branchId = branchId,
                    entryDateMillis = txDateMillis,
                    sourceModule = srcModule,
                    sourceEntityId = null,
                    sourceKey = sourceKey,
                    reference = reference.trim().ifBlank { "CB-${txType.name.take(3)}" },
                    description = description.trim(),
                    draftLines = lines,
                    postedByUid = session.uid,
                    postedByName = session.displayName,
                    approvedByName = session.displayName
                ) ?: throw IllegalStateException("Duplicate cashbook entry prevented.")

                val cbEntity = CashbookEntryEntity(
                    cashbookId = "cb_${UUID.randomUUID()}",
                    branchId = branchId,
                    accountId = cashAcc.accountId,
                    accountName = cashAcc.accountName,
                    txType = txType.name,
                    amountCents = amountCents,
                    reference = entry.reference,
                    counterpartyName = counterpartyName.trim().ifBlank { offsetAcc.accountName },
                    description = description.trim(),
                    txDateMillis = txDateMillis,
                    linkedJournalEntryId = entry.entryId,
                    sourceKey = "CB_TX_$idempotencyKey",
                    recordedByName = session.displayName,
                    createdAt = System.currentTimeMillis()
                )
                dao.insertCashbookEntryIfNew(cbEntity)
                if (offsetAcc.isCashOrBank && (txType == CashbookTxType.TRANSFER_IN || txType == CashbookTxType.TRANSFER_OUT)) {
                    val pairedType = if (txType == CashbookTxType.TRANSFER_OUT) CashbookTxType.TRANSFER_IN else CashbookTxType.TRANSFER_OUT
                    dao.insertCashbookEntryIfNew(
                        CashbookEntryEntity(
                            cashbookId = "cb_${UUID.randomUUID()}",
                            branchId = branchId,
                            accountId = offsetAcc.accountId,
                            accountName = offsetAcc.accountName,
                            txType = pairedType.name,
                            amountCents = amountCents,
                            reference = entry.reference,
                            counterpartyName = cashAcc.accountName,
                            description = description.trim(),
                            txDateMillis = txDateMillis,
                            linkedJournalEntryId = entry.entryId,
                            sourceKey = "CB_TX_PAIR_$idempotencyKey",
                            recordedByName = session.displayName,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
                cbEntity.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordCustomerPayment(
        session: UserSession,
        branchId: String,
        idempotencyKey: String,
        customerId: String,
        cashBankAccountId: String,
        amountCents: Long,
        reference: String,
        paymentDateMillis: Long,
        notes: String
    ): Result<CashbookTransactionRecord> {
        val branchErr = checkBranchAccess(session, branchId, requireConsolidated = false)
        if (branchErr != null) return Result.failure(branchErr)
        if (!session.capabilities.canManageCashBankAccounts) {
            return Result.failure(SecurityException("Access denied: unauthorized to post customer receipts."))
        }
        if (amountCents <= 0L || reference.isBlank()) {
            return Result.failure(IllegalArgumentException("Positive amount and payment reference are required."))
        }
        return try {
            val saved = database.withTransaction {
                DatabaseSeeder.ensureChartOfAccountsForBranch(database, branchId)
                val customer = database.posDao().getCustomerById(customerId)
                    ?: throw IllegalArgumentException("Customer not found.")
                val cashAcc = dao.getAccountById(cashBankAccountId)
                    ?: throw IllegalArgumentException("Cash/Bank account not found.")
                val arAcc = dao.getAccountByCode(branchId, "1100")
                    ?: throw IllegalStateException("Accounts Receivable (1100) missing.")

                val sourceKey = "CUST_PAY_$idempotencyKey"
                val existing = dao.getJournalEntryBySourceKey(sourceKey)
                if (existing != null) {
                    val cb = dao.observeCashbookEntries(branchId).first()
                        .first { it.linkedJournalEntryId == existing.entryId }
                    return@withTransaction cb.toDomain()
                }

                val desc = notes.trim().ifBlank { "Customer payment from ${customer.name}" }
                val entry = AccountingIntegrationHelper.insertBalancedJournalInTransaction(
                    database = database,
                    branchId = branchId,
                    entryDateMillis = paymentDateMillis,
                    sourceModule = JournalSourceModule.CUSTOMER_PAYMENT,
                    sourceEntityId = customerId,
                    sourceKey = sourceKey,
                    reference = reference.trim(),
                    description = desc,
                    draftLines = listOf(
                        JournalDraftLine(
                            accountId = cashAcc.accountId,
                            debitCents = amountCents,
                            creditCents = 0L,
                            partyType = "CUSTOMER",
                            partyId = customer.customerId,
                            partyName = customer.name,
                            memo = desc
                        ),
                        JournalDraftLine(
                            accountId = arAcc.accountId,
                            debitCents = 0L,
                            creditCents = amountCents,
                            partyType = "CUSTOMER",
                            partyId = customer.customerId,
                            partyName = customer.name,
                            memo = "AR Settlement Ref ${reference.trim()}"
                        )
                    ),
                    postedByUid = session.uid,
                    postedByName = session.displayName,
                    approvedByName = session.displayName
                ) ?: throw IllegalStateException("Duplicate customer receipt prevented.")

                val updatedBalance = (customer.accountBalanceCents - amountCents).coerceAtLeast(0L)
                customerDao.upsertCustomer(customer.copy(accountBalanceCents = updatedBalance))

                val cbEntity = CashbookEntryEntity(
                    cashbookId = "cb_${UUID.randomUUID()}",
                    branchId = branchId,
                    accountId = cashAcc.accountId,
                    accountName = cashAcc.accountName,
                    txType = CashbookTxType.RECEIPT.name,
                    amountCents = amountCents,
                    reference = reference.trim(),
                    counterpartyName = customer.name,
                    description = desc,
                    txDateMillis = paymentDateMillis,
                    linkedJournalEntryId = entry.entryId,
                    sourceKey = "CB_CUST_PAY_$idempotencyKey",
                    recordedByName = session.displayName,
                    createdAt = System.currentTimeMillis()
                )
                dao.insertCashbookEntryIfNew(cbEntity)
                cbEntity.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkBranchAccess(
        session: UserSession,
        targetBranchId: String?,
        requireConsolidated: Boolean
    ): SecurityException? {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let { return it }
        if (requireConsolidated || targetBranchId == null) {
            return if (session.capabilities.canViewConsolidatedReports) {
                null
            } else {
                SecurityException("Access denied: consolidated multi-branch access requires Owner or Super Admin authorization.")
            }
        }
        if (session.capabilities.canSwitchBranches) return null
        if (targetBranchId == session.branchId) return null

        // Check if staff profile explicitly grants access to targetBranchId
        val allStaff = staffDao.observeAllStaff().first()
        val matched = allStaff.firstOrNull { it.linkedUserUid == session.uid }
        val allowedIds = matched?.accessibleBranchIdsCsv
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        return if (targetBranchId in allowedIds) {
            null
        } else {
            SecurityException("Access denied: user '${session.displayName}' is not authorized to access branch $targetBranchId.")
        }
    }

    // ---- Pure Report Builders from Live State ----

    fun buildGeneralLedger(
        accounts: List<ChartOfAccountRecord>,
        entries: List<JournalEntryRecord>,
        selectedAccountId: String?,
        startMillis: Long?,
        endMillis: Long?,
        searchQuery: String
    ): List<GeneralLedgerAccountSection> {
        val entriesById = entries.associateBy { it.entryId }
        val filteredAccounts = if (selectedAccountId != null) {
            accounts.filter { it.accountId == selectedAccountId }
        } else {
            accounts
        }
        val query = searchQuery.trim()
        return filteredAccounts.mapNotNull { acc ->
            val allLines = entries.flatMap { e ->
                e.lines.filter { it.accountId == acc.accountId }.map { line -> e to line }
            }.sortedBy { it.first.entryDateMillis }

            val priorLines = allLines.filter { (e, _) -> startMillis != null && e.entryDateMillis < startMillis }
            val openingAsOfStart = AccountingCalculator.computeAccountNetBalance(
                acc,
                priorLines.map { it.second }
            )

            val inRange = allLines.filter { (e, l) ->
                val afterStart = startMillis == null || e.entryDateMillis >= startMillis
                val beforeEnd = endMillis == null || e.entryDateMillis <= endMillis
                val matchesSearch = query.isBlank() ||
                    e.entryNumber.contains(query, ignoreCase = true) ||
                    e.reference.contains(query, ignoreCase = true) ||
                    e.description.contains(query, ignoreCase = true) ||
                    l.memo.contains(query, ignoreCase = true) ||
                    (l.partyName?.contains(query, ignoreCase = true) == true)
                afterStart && beforeEnd && matchesSearch
            }

            if (inRange.isEmpty() && openingAsOfStart == 0L && selectedAccountId == null) {
                return@mapNotNull null
            }

            var running = openingAsOfStart
            val glLines = inRange.map { (e, line) ->
                running += if (acc.accountType.isDebitNormal) {
                    line.debitCents - line.creditCents
                } else {
                    line.creditCents - line.debitCents
                }
                GeneralLedgerLineItem(
                    entryDateMillis = e.entryDateMillis,
                    entryNumber = e.entryNumber,
                    reference = e.reference,
                    description = line.memo.ifBlank { e.description },
                    debitCents = line.debitCents,
                    creditCents = line.creditCents,
                    runningBalanceCents = running,
                    isReversed = entriesById[e.entryId]?.status == JournalEntryStatus.REVERSED
                )
            }

            GeneralLedgerAccountSection(
                account = acc,
                openingBalanceCents = openingAsOfStart,
                entries = glLines,
                closingBalanceCents = running
            )
        }
    }

    fun buildCashBankSummaries(
        accounts: List<ChartOfAccountRecord>,
        cashbookEntries: List<CashbookTransactionRecord>
    ): List<CashBankAccountSummary> {
        val cashAccounts = accounts.filter { it.isCashOrBank }
        val byAcc = cashbookEntries.groupBy { it.accountId }
        return cashAccounts.map { acc ->
            val txs = byAcc[acc.accountId].orEmpty()
            val inflows = txs.filter { it.txType.isInflow }.sumOf { it.amountCents }
            val outflows = txs.filterNot { it.txType.isInflow }.sumOf { it.amountCents }
            CashBankAccountSummary(
                account = acc,
                openingBalanceCents = acc.openingBalanceCents,
                totalInflowsCents = inflows,
                totalOutflowsCents = outflows,
                closingBalanceCents = acc.currentBalanceCents
            )
        }
    }

    fun buildCustomerLedger(
        customer: CustomerProfileRecord,
        orders: List<PosOrderEntity>,
        cashbookReceipts: List<CashbookTransactionRecord>,
        journalEntries: List<JournalEntryRecord>
    ): PartyLedgerReport {
        val events = mutableListOf<PartyLedgerRow>()
        val receiptEntryIds = journalEntries
            .filter { it.sourceModule == JournalSourceModule.CUSTOMER_PAYMENT && it.sourceEntityId == customer.customerId }
            .map { it.entryId }
            .toSet()

        orders.filter { it.customerId == customer.customerId && it.status != OrderStatus.CANCELLED.name }
            .forEach { o ->
                events += PartyLedgerRow(
                    dateMillis = o.createdAt,
                    reference = o.invoiceNumber,
                    sourceLabel = if (o.status == OrderStatus.REFUNDED.name) "REFUNDED" else "POS SALE",
                    description = "Order ${o.orderNumber} (${o.paymentMethod})",
                    debitCents = o.totalCents,
                    creditCents = if (o.status == OrderStatus.REFUNDED.name) o.totalCents else o.paidCents.coerceAtMost(o.totalCents),
                    runningBalanceCents = 0L,
                    branchId = o.branchId
                )
            }

        cashbookReceipts.filter { it.linkedJournalEntryId in receiptEntryIds }.forEach { cb ->
            events += PartyLedgerRow(
                dateMillis = cb.txDateMillis,
                reference = cb.reference,
                sourceLabel = "PAYMENT",
                description = cb.description,
                debitCents = 0L,
                creditCents = cb.amountCents,
                runningBalanceCents = 0L,
                branchId = cb.branchId
            )
        }

        var running = 0L
        val sorted = events.sortedBy { it.dateMillis }.map { row ->
            running = (running + row.debitCents - row.creditCents).coerceAtLeast(0L)
            row.copy(runningBalanceCents = running)
        }
        return PartyLedgerReport(
            partyId = customer.customerId,
            partyName = customer.name,
            partySubtitle = "${customer.tier} • ${customer.phone}",
            openingBalanceCents = 0L,
            closingBalanceCents = maxOf(running, customer.accountBalanceCents),
            lines = sorted
        )
    }

    fun buildSupplierLedger(
        supplier: SupplierSummary,
        purchases: List<PurchaseOrderEntity>,
        returns: List<PurchaseReturnEntity>,
        payments: List<SupplierPaymentEntity>
    ): PartyLedgerReport {
        val raw = mutableListOf<PartyLedgerRow>()
        var running = supplier.openingBalanceCents
        raw += PartyLedgerRow(
            dateMillis = supplier.createdAt,
            reference = "OPENING",
            sourceLabel = "OPENING",
            description = "Opening Supplier Balance",
            debitCents = supplier.openingBalanceCents,
            creditCents = 0L,
            runningBalanceCents = running,
            branchId = supplier.branchId
        )

        val events = mutableListOf<PartyLedgerRow>()
        purchases.filter {
            it.supplierId == supplier.supplierId &&
                (it.status == PurchaseStatus.RECEIVED.name || it.status == PurchaseStatus.PARTIALLY_RECEIVED.name)
        }.forEach { po ->
            events += PartyLedgerRow(
                dateMillis = po.createdAt,
                reference = po.poNumber,
                sourceLabel = "PURCHASE",
                description = "PO ${po.poNumber} (Inv: ${po.supplierInvoiceNumber})",
                debitCents = po.totalCents,
                creditCents = 0L,
                runningBalanceCents = 0L,
                branchId = po.branchId
            )
        }
        returns.filter { it.supplierId == supplier.supplierId }.forEach { ret ->
            events += PartyLedgerRow(
                dateMillis = ret.returnedAt,
                reference = ret.poNumber,
                sourceLabel = "RETURN",
                description = "Returned ${ret.returnQty} ${ret.unit} ${ret.itemName}",
                debitCents = 0L,
                creditCents = ret.returnAmountCents,
                runningBalanceCents = 0L,
                branchId = ret.branchId
            )
        }
        payments.filter { it.supplierId == supplier.supplierId }.forEach { pay ->
            events += PartyLedgerRow(
                dateMillis = pay.paymentDateMillis,
                reference = pay.referenceNumber,
                sourceLabel = "PAYMENT",
                description = "Paid via ${pay.paymentMethod}",
                debitCents = 0L,
                creditCents = pay.amountCents,
                runningBalanceCents = 0L,
                branchId = pay.branchId
            )
        }

        events.sortedBy { it.dateMillis }.forEach { ev ->
            running = (running + ev.debitCents - ev.creditCents).coerceAtLeast(0L)
            raw += ev.copy(runningBalanceCents = running)
        }

        return PartyLedgerReport(
            partyId = supplier.supplierId,
            partyName = supplier.name,
            partySubtitle = "${supplier.category} • ${supplier.phone}",
            openingBalanceCents = supplier.openingBalanceCents,
            closingBalanceCents = supplier.outstandingBalanceCents,
            lines = raw
        )
    }

    fun buildSalesSummary(orders: List<PosOrderEntity>): SalesSummaryReportData {
        val valid = orders.filter { it.status != OrderStatus.CANCELLED.name && it.status != OrderStatus.REFUNDED.name }
        val gross = valid.sumOf { it.subtotalCents + it.taxCents + it.serviceChargeCents }
        val disc = valid.sumOf { it.discountCents }
        val tax = valid.sumOf { it.taxCents }
        val net = valid.sumOf { it.totalCents }
        val cash = valid.sumOf { (it.cashPaidCents - it.changeCents).coerceAtLeast(0L) }
        val bank = valid.sumOf { it.cardPaidCents + it.bankPaidCents + it.digitalPaidCents }
        val rec = valid.sumOf { it.remainingBalanceCents }
        return SalesSummaryReportData(
            totalOrders = valid.size,
            grossSalesCents = gross,
            discountsCents = disc,
            taxCollectedCents = tax,
            netSalesCents = net,
            cashCollectedCents = cash,
            cardAndBankCollectedCents = bank,
            receivableBalanceCents = rec
        )
    }

    fun buildPayrollSummary(payrolls: List<PayrollRecordEntity>): PayrollSummaryReportData {
        return PayrollSummaryReportData(
            recordCount = payrolls.size,
            draftCount = payrolls.count { it.status == PayrollStatus.DRAFT.name },
            approvedCount = payrolls.count { it.status == PayrollStatus.APPROVED.name },
            paidCount = payrolls.count { it.status == PayrollStatus.PAID.name },
            totalGrossCents = payrolls.sumOf { it.grossSalaryCents },
            totalDeductionsCents = payrolls.sumOf { it.deductionsCents },
            totalNetCents = payrolls.sumOf { it.netSalaryCents },
            totalPaidOutCents = payrolls.filter { it.status == PayrollStatus.PAID.name }.sumOf { it.netSalaryCents }
        )
    }

    fun buildConsolidatedFinancialReport(
        branches: List<BranchSummary>,
        accounts: List<ChartOfAccountRecord>,
        entries: List<JournalEntryRecord>,
        posOrders: List<PosOrderEntity>,
        purchases: List<PurchaseOrderEntity>,
        expenses: List<ExpenseEntity>,
        payrolls: List<PayrollRecordEntity>,
        staffList: List<com.example.data.local.StaffProfileEntity>
    ): ConsolidatedFinancialReport {
        val snapshots = branches.map { br ->
            val bAccs = accounts.filter { it.branchId == br.branchId }
            val bLines = entries.filter { it.branchId == br.branchId }.flatMap { it.lines }
            val bSales = posOrders
                .filter { it.branchId == br.branchId && it.status != OrderStatus.CANCELLED.name && it.status != OrderStatus.REFUNDED.name }
                .sumOf { it.totalCents }
            val bPurchases = purchases
                .filter {
                    it.branchId == br.branchId &&
                        (it.status == PurchaseStatus.RECEIVED.name || it.status == PurchaseStatus.PARTIALLY_RECEIVED.name)
                }.sumOf { (it.totalCents - it.returnedCents).coerceAtLeast(0L) }
            val bExpenses = expenses
                .filter { it.branchId == br.branchId && it.approvalStatus == ExpenseApprovalStatus.APPROVED.name }
                .sumOf { it.amountCents }
            val bPayroll = payrolls
                .filter { it.branchId == br.branchId && (it.status == PayrollStatus.APPROVED.name || it.status == PayrollStatus.PAID.name) }
                .sumOf { it.netSalaryCents }
            val pnl = AccountingCalculator.buildProfitAndLoss(br.name, bAccs, bLines)
            val cashBal = bAccs.filter { it.accountCategory == AccountCategory.CASH }.sumOf { it.currentBalanceCents }
            val bankBal = bAccs.filter { it.accountCategory == AccountCategory.BANK }.sumOf { it.currentBalanceCents }
            val bStaffCount = staffList.count { s ->
                s.isActive && (s.branchId == br.branchId ||
                    s.accessibleBranchIdsCsv.split(",").map { it.trim() }.contains(br.branchId))
            }

            BranchFinancialSnapshot(
                branch = br,
                totalSalesCents = bSales,
                totalPurchasesCents = bPurchases,
                totalExpensesCents = bExpenses,
                totalPayrollCents = bPayroll,
                netProfitCents = if (bLines.isNotEmpty()) pnl.netProfitCents else (bSales - bPurchases - bExpenses - bPayroll),
                cashBalanceCents = cashBal,
                bankBalanceCents = bankBal,
                staffCount = bStaffCount
            )
        }
        return ConsolidatedFinancialReport(
            branchSnapshots = snapshots,
            totalSalesCents = snapshots.sumOf { it.totalSalesCents },
            totalPurchasesCents = snapshots.sumOf { it.totalPurchasesCents },
            totalExpensesCents = snapshots.sumOf { it.totalExpensesCents },
            totalPayrollCents = snapshots.sumOf { it.totalPayrollCents },
            netProfitCents = snapshots.sumOf { it.netProfitCents },
            totalCashBalanceCents = snapshots.sumOf { it.cashBalanceCents },
            totalBankBalanceCents = snapshots.sumOf { it.bankBalanceCents }
        )
    }

    fun buildCustomerOutstandingRows(
        branches: List<BranchSummary>,
        customers: List<CustomerProfileRecord>,
        orders: List<PosOrderEntity>
    ): List<OutstandingBalanceRow> {
        val branchMap = branches.associateBy { it.branchId }
        val ordersByCustomer = orders.filter { it.status != OrderStatus.CANCELLED.name && it.status != OrderStatus.REFUNDED.name }
            .groupBy { it.customerId }
        return customers.map { cust ->
            val cOrders = ordersByCustomer[cust.customerId].orEmpty()
            val billed = cOrders.sumOf { it.totalCents }
            val settled = cOrders.sumOf { it.paidCents.coerceAtMost(it.totalCents) }
            val outstanding = maxOf(cust.accountBalanceCents, (billed - settled).coerceAtLeast(0L))
            OutstandingBalanceRow(
                partyId = cust.customerId,
                branchId = cust.branchId,
                branchName = branchMap[cust.branchId]?.name ?: cust.branchId,
                partyName = cust.name,
                contact = cust.phone,
                totalBilledCents = maxOf(billed, cust.totalPurchasesCents),
                totalSettledCents = settled,
                outstandingCents = outstanding
            )
        }.sortedByDescending { it.outstandingCents }
    }

    fun buildSupplierOutstandingRows(
        branches: List<BranchSummary>,
        suppliers: List<SupplierSummary>
    ): List<OutstandingBalanceRow> {
        val branchMap = branches.associateBy { it.branchId }
        return suppliers.map { sup ->
            OutstandingBalanceRow(
                partyId = sup.supplierId,
                branchId = sup.branchId,
                branchName = branchMap[sup.branchId]?.name ?: sup.branchId,
                partyName = sup.name,
                contact = sup.phone,
                totalBilledCents = sup.openingBalanceCents + sup.totalPurchasesCents,
                totalSettledCents = sup.totalReturnsCents + sup.totalPaidCents,
                outstandingCents = sup.outstandingBalanceCents
            )
        }.sortedByDescending { it.outstandingCents }
    }
}

class MultiBranchRepository(private val database: AaCraveDatabase) {

    private val branchDao = database.branchDao()
    private val staffDao = database.staffDao()
    private val userProfileDao = database.userProfileDao()
    private val dashboardDao = database.dashboardDao()
    private val auditLogDao = database.auditLogDao()
    private val syncQueueDao = database.syncQueueDao()

    fun observeBranches(): Flow<List<BranchSummary>> =
        branchDao.observeBranches().map { list -> list.map { it.toBranchSummary() } }

    fun observeAllStaff(): Flow<List<com.example.data.local.StaffProfileEntity>> =
        staffDao.observeAllStaff()

    suspend fun saveBranch(
        session: UserSession,
        branchId: String?,
        name: String,
        code: String,
        address: String,
        phone: String,
        email: String,
        managerName: String,
        managerUid: String?,
        openingCashCents: Long,
        openingBankCents: Long,
        isActive: Boolean
    ): Result<BranchSummary> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canManageBranches) {
            return Result.failure(SecurityException("Access denied: Owner or Super Admin authorization required to create or edit branches."))
        }
        val cleanName = name.trim()
        val cleanCode = code.trim().uppercase(Locale.US)
        val cleanAddress = address.trim()
        if (cleanName.isBlank() || cleanCode.isBlank() || cleanAddress.isBlank()) {
            return Result.failure(IllegalArgumentException("Branch name, code, and address are required."))
        }
        if (openingCashCents < 0L || openingBankCents < 0L) {
            return Result.failure(IllegalArgumentException("Opening Cash and Bank balances cannot be negative."))
        }

        return try {
            val saved = database.withTransaction {
                val duplicate = branchDao.getBranchByCode(cleanCode)
                if (duplicate != null && duplicate.branchId != branchId) {
                    throw IllegalArgumentException("Branch code '$cleanCode' already exists (${duplicate.name}).")
                }
                val existing = branchId?.let { branchDao.getBranchById(it) }
                if (existing?.isMainBranch == true && !isActive) {
                    throw IllegalStateException("Main flagship branch cannot be deactivated.")
                }

                val now = System.currentTimeMillis()
                val newId = existing?.branchId ?: "branch_${cleanCode.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_")}_${UUID.randomUUID().toString().take(4)}"
                val entity = BranchEntity(
                    branchId = newId,
                    name = cleanName,
                    code = cleanCode,
                    address = cleanAddress,
                    isMainBranch = existing?.isMainBranch ?: false,
                    isActive = if (existing?.isMainBranch == true) true else isActive,
                    phone = phone.trim(),
                    email = email.trim(),
                    managerName = managerName.trim(),
                    managerUid = managerUid,
                    openingCashCents = openingCashCents,
                    openingBankCents = openingBankCents,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
                branchDao.upsertBranch(entity)

                // Seed default Chart of Accounts & clean Dashboard snapshot for new branch
                DatabaseSeeder.ensureChartOfAccountsForBranch(
                    database = database,
                    branchId = newId,
                    openingCashCents = openingCashCents,
                    openingBankCents = openingBankCents
                )
                if (existing == null) {
                    val dateStr = SimpleDateFormat("EEE, dd MMM yyyy", Locale.US).format(Date(now))
                    dashboardDao.upsertSnapshots(
                        listOf(
                            DashboardSnapshotEntity(
                                branchId = newId,
                                businessDate = dateStr,
                                todaySales = 0.0,
                                todayOrders = 0,
                                totalCustomers = 0,
                                pendingKitchenOrders = 0,
                                lowStockAlerts = 0,
                                dailyExpenses = 0.0,
                                estimatedProfit = 0.0,
                                cashPayments = 0.0,
                                digitalPayments = 0.0,
                                isSampleData = false,
                                updatedAt = now
                            )
                        )
                    )
                } else if (existing.openingCashCents != openingCashCents || existing.openingBankCents != openingBankCents) {
                    val accDao = database.accountingDao()
                    accDao.getAccountByCode(newId, "1010")?.let { c ->
                        accDao.upsertAccount(c.copy(openingBalanceCents = openingCashCents))
                    }
                    accDao.getAccountByCode(newId, "1020")?.let { b ->
                        accDao.upsertAccount(b.copy(openingBalanceCents = openingBankCents))
                    }
                    accDao.getAccountByCode(newId, "3000")?.let { eq ->
                        accDao.upsertAccount(eq.copy(openingBalanceCents = openingCashCents + openingBankCents))
                    }
                }

                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_br_${newId}_$now",
                        branchId = newId,
                        entityType = "BRANCH_PROFILE",
                        entityId = newId,
                        payloadJson = """{"code":"$cleanCode","name":"$cleanName","isActive":${entity.isActive}}""",
                        status = "PENDING",
                        lastError = "Realtime Database in Locked Mode — queued safely in Room"
                    )
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = newId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = if (existing == null) "BRANCH_CREATED" else "BRANCH_UPDATED",
                        details = "${entity.code} (${entity.name}) | Mgr: ${entity.managerName.ifBlank { "Unassigned" }} | Active: ${entity.isActive}"
                    )
                )
                entity.toBranchSummary()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setBranchActiveStatus(
        session: UserSession,
        branchId: String,
        active: Boolean,
        reason: String
    ): Result<Unit> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canManageBranches) {
            return Result.failure(SecurityException("Access denied: only Owner or Super Admin can activate or deactivate branches."))
        }
        return try {
            database.withTransaction {
                val br = branchDao.getBranchById(branchId) ?: throw IllegalArgumentException("Branch not found.")
                if (br.isMainBranch && !active) {
                    throw IllegalStateException("Main flagship branch cannot be deactivated.")
                }
                val now = System.currentTimeMillis()
                branchDao.upsertBranch(br.copy(isActive = active, updatedAt = now))
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = if (active) "BRANCH_ACTIVATED" else "BRANCH_DEACTIVATED",
                        details = "${br.code} (${br.name})${if (reason.isNotBlank()) " — ${reason.trim()}" else ""}"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun assignStaffToBranch(
        session: UserSession,
        staffId: String,
        primaryBranchId: String,
        accessibleBranchIds: List<String>,
        setAsBranchManager: Boolean
    ): Result<Unit> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canAssignBranchStaff) {
            return Result.failure(SecurityException("Access denied: unauthorized to assign employees to branches."))
        }
        val cleanBranches = (accessibleBranchIds + primaryBranchId).distinct()
        if (!session.capabilities.canManageBranches) {
            // Non-owners cannot assign employees to branches outside their own branch
            if (cleanBranches.any { it != session.branchId }) {
                return Result.failure(SecurityException("Access denied: only Owner or Super Admin can assign staff across multiple branches."))
            }
        }
        return try {
            database.withTransaction {
                val staff = staffDao.getStaffById(staffId) ?: throw IllegalArgumentException("Staff profile not found.")
                val targetBranch = branchDao.getBranchById(primaryBranchId)
                    ?: throw IllegalArgumentException("Target branch not found.")
                if (!targetBranch.isActive) {
                    throw IllegalStateException("Cannot assign staff to an inactive branch (${targetBranch.name}).")
                }

                val now = System.currentTimeMillis()
                staffDao.upsertStaff(
                    staff.copy(
                        branchId = primaryBranchId,
                        accessibleBranchIdsCsv = cleanBranches.joinToString(","),
                        updatedAt = now
                    )
                )
                staff.linkedUserUid?.let { uid ->
                    userProfileDao.updateUserBranch(uid, primaryBranchId)
                }
                if (setAsBranchManager) {
                    branchDao.upsertBranch(
                        targetBranch.copy(
                            managerName = staff.fullName,
                            managerUid = staff.linkedUserUid ?: staff.staffId,
                            updatedAt = now
                        )
                    )
                }
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = primaryBranchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "BRANCH_STAFF_ASSIGNED",
                        details = "${staff.employeeCode} (${staff.fullName}) assigned to ${targetBranch.code} (branches: ${cleanBranches.joinToString(",")}, manager=$setAsBranchManager)"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun canSwitchToBranch(session: UserSession, targetBranchId: String): Result<BranchSummary> {
        ErpAuthorizationGuard.verifyActiveOperator(database, session)?.let {
            return Result.failure(it)
        }
        if (!session.capabilities.canSwitchBranches && targetBranchId != session.branchId) {
            return Result.failure(SecurityException("Access denied: ${session.role.displayName} is not authorized to switch branches."))
        }
        val branch = branchDao.getBranchById(targetBranchId)
            ?: return Result.failure(IllegalArgumentException("Branch not found."))
        if (!branch.isActive) {
            return Result.failure(IllegalStateException("Cannot switch to inactive branch ${branch.name}."))
        }
        return Result.success(branch.toBranchSummary())
    }
}

internal fun BranchEntity.toBranchSummary(): BranchSummary = BranchSummary(
    branchId = branchId,
    name = name,
    code = code,
    address = address,
    isMainBranch = isMainBranch,
    isActive = isActive,
    phone = phone,
    email = email,
    managerName = managerName,
    managerUid = managerUid,
    openingCashCents = openingCashCents,
    openingBankCents = openingBankCents
)

private fun ChartOfAccountEntity.toDomain(): ChartOfAccountRecord = ChartOfAccountRecord(
    accountId = accountId,
    branchId = branchId,
    accountCode = accountCode,
    accountName = accountName,
    accountType = AccountType.entries.firstOrNull { it.name == accountType } ?: AccountType.EXPENSE,
    accountCategory = AccountCategory.entries.firstOrNull { it.name == accountCategory } ?: AccountCategory.OPERATING_EXPENSE,
    isCashOrBank = isCashOrBank,
    openingBalanceCents = openingBalanceCents,
    isSystemAccount = isSystemAccount,
    isActive = isActive,
    createdAt = createdAt
)

private fun JournalLineEntity.toDomain(): JournalLineRecord = JournalLineRecord(
    lineId = lineId,
    entryId = entryId,
    branchId = branchId,
    accountId = accountId,
    accountCode = accountCode,
    accountName = accountName,
    accountType = AccountType.entries.firstOrNull { it.name == accountType } ?: AccountType.EXPENSE,
    debitCents = debitCents,
    creditCents = creditCents,
    partyType = partyType,
    partyId = partyId,
    partyName = partyName,
    memo = memo,
    entryDateMillis = entryDateMillis
)

private fun JournalEntryEntity.toDomain(lines: List<JournalLineRecord>): JournalEntryRecord = JournalEntryRecord(
    entryId = entryId,
    branchId = branchId,
    entryNumber = entryNumber,
    entryDateMillis = entryDateMillis,
    sourceModule = JournalSourceModule.entries.firstOrNull { it.name == sourceModule } ?: JournalSourceModule.MANUAL,
    sourceEntityId = sourceEntityId,
    sourceKey = sourceKey,
    reference = reference,
    description = description,
    totalDebitCents = totalDebitCents,
    totalCreditCents = totalCreditCents,
    status = JournalEntryStatus.entries.firstOrNull { it.name == status } ?: JournalEntryStatus.POSTED,
    reversedEntryId = reversedEntryId,
    postedByUid = postedByUid,
    postedByName = postedByName,
    approvedByName = approvedByName,
    createdAt = createdAt,
    lines = lines
)

private fun CashbookEntryEntity.toDomain(): CashbookTransactionRecord = CashbookTransactionRecord(
    cashbookId = cashbookId,
    branchId = branchId,
    accountId = accountId,
    accountName = accountName,
    txType = CashbookTxType.entries.firstOrNull { it.name == txType } ?: CashbookTxType.RECEIPT,
    amountCents = amountCents,
    reference = reference,
    counterpartyName = counterpartyName,
    description = description,
    txDateMillis = txDateMillis,
    linkedJournalEntryId = linkedJournalEntryId,
    recordedByName = recordedByName,
    createdAt = createdAt
)

private fun com.example.data.local.SupplierEntity.toDomainSummary(): SupplierSummary = SupplierSummary(
    supplierId = supplierId,
    branchId = branchId,
    name = name,
    contactPerson = contactPerson,
    phone = phone,
    email = email,
    address = address,
    category = category,
    openingBalanceCents = openingBalanceCents,
    totalPurchasesCents = totalPurchasesCents,
    totalReturnsCents = totalReturnsCents,
    totalPaidCents = totalPaidCents,
    isActive = isActive,
    createdAt = createdAt
)

private fun com.example.data.local.CustomerEntity.toCustomerDomain(): CustomerProfileRecord = CustomerProfileRecord(
    customerId = customerId,
    branchId = branchId,
    name = name,
    phone = phone,
    email = email,
    loyaltyPoints = loyaltyPoints,
    tier = tier,
    discountPercent = discountPercent,
    address = address,
    notes = notes,
    totalPurchasesCents = totalPurchasesCents,
    lastOrderAt = lastOrderAt,
    accountBalanceCents = accountBalanceCents,
    feedbackSummary = feedbackSummary,
    createdAt = createdAt
)
