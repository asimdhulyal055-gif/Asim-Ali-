package com.example.ui.loyalty

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.LoyaltyProgramSettingsRecord
import com.example.domain.model.LoyaltyRuleConstants
import com.example.domain.model.QualifyingCalculationMethod
import com.example.domain.model.UserSession
import com.example.domain.model.centsToPkr
import com.example.printing.Phase4ReportExporter
import com.example.ui.components.StatusPill
import com.example.ui.theme.CraveGold
import com.example.ui.theme.CraveGoldBright
import com.example.ui.theme.CraveGoldContainer
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.OnCraveGold
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusSky
import com.example.ui.viewmodel.SmartLoyaltyUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LoyaltyReportsAndReconciliationTab(
    session: UserSession,
    uiState: SmartLoyaltyUiState,
    onSelectBranchScope: (String?) -> Unit,
    onSelectPresetDays: (Int?) -> Unit,
    onRefreshReport: () -> Unit,
    onOpenCustomerProfile: (String) -> Unit,
    onShowMessage: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val report = uiState.analyticsReport

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Loyalty Analytics & Accounting Reconciliation", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                        OutlinedButton(onClick = onRefreshReport) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh analytics", modifier = Modifier.size(16.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(null to "All Time", 30 to "Last 30d", 7 to "Last 7d").forEach { (days, label) ->
                            FilterChip(
                                selected = uiState.reportPresetDays == days,
                                onClick = { onSelectPresetDays(days) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                            )
                        }
                    }

                    if (session.capabilities.canViewConsolidatedReports && uiState.branches.size > 1) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = uiState.selectedBranchScope == null,
                                onClick = { onSelectBranchScope(null) },
                                label = { Text("All Branches") }
                            )
                            uiState.branches.forEach { br ->
                                FilterChip(
                                    selected = uiState.selectedBranchScope == br.branchId,
                                    onClick = { onSelectBranchScope(br.branchId) },
                                    label = { Text(br.name) }
                                )
                            }
                        }
                    }

                    if (report != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        val dir = File(context.cacheDir, "loyalty_exports").apply { mkdirs() }
                                        val out = File(dir, "aa_crave_loyalty_report_${System.currentTimeMillis()}.csv")
                                        val sb = StringBuilder()
                                        sb.appendLine("Scope,Enrolled,Active,RewardEligible,Expired,StampsIssued,StampsReversed,VouchersEarned,VouchersRedeemed,PaidSalesPKR,PromoDiscountPKR,ActualPromoCogsPKR")
                                        report.branchRows.forEach { row ->
                                            sb.appendLine(
                                                "\"${row.branchName}\",${row.enrolledCustomers},${row.activeCards},${row.rewardEligibleCards},${row.expiredCards},${row.stampsIssued},${row.stampsReversed},${row.vouchersEarned},${row.vouchersRedeemed},${row.netPaidSalesWithLoyaltyCents / 100.0},${row.promoDiscountTotalCents / 100.0},${row.actualPromoCogsCents / 100.0}"
                                            )
                                        }
                                        out.writeText(sb.toString())
                                        out
                                    }
                                    Phase4ReportExporter.shareReportFile(context, file, "text/csv", "Export Loyalty Analytics CSV")
                                    onShowMessage("Exported CSV: ${file.name}", false)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                            modifier = Modifier.fillMaxWidth().testTag("loyalty_export_csv_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Export CSV Analytics Report")
                        }
                    }
                }
            }
        }

        if (report != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Financial & Ledger Reconciliation (${report.branchScopeLabel})", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                        MetricLine("Total Enrolled Members", "${report.totalEnrolledCustomers}")
                        MetricLine("Active / Reward-Ready / Expired Cards", "${report.activeCardsCount} / ${report.rewardEligibleCardsCount} / ${report.expiredCardsCount}")
                        MetricLine("New Enrollments (Period)", "${report.newEnrollmentsInPeriod}")
                        MetricLine("Stamps Issued / Reversed", "${report.stampsIssuedInPeriod} / ${report.stampsReversedInPeriod}")
                        MetricLine("Vouchers Unlocked / Redeemed", "${report.rewardsEarnedInPeriod} / ${report.rewardsRedeemedInPeriod}")
                        MetricLine("Unused Active / Expired / Cancelled Vouchers", "${report.unusedActiveVouchersCount} / ${report.expiredVouchersCount} / ${report.cancelledVouchersCount}")
                        HorizontalDivider(color = ObsidianBorder)
                        MetricLine("Net Paid Sales (Loyalty Orders)", centsToPkr(report.totalPaidSalesRevenueCents), StatusEmerald)
                        MetricLine("Regular Discounts on Loyalty Orders", centsToPkr(report.totalRegularDiscountsCents))
                        MetricLine("Promo Voucher Discounts Given (Customer Savings)", centsToPkr(report.totalPromoDiscountGivenCents), CraveGoldBright)
                        MetricLine("Actual Promotional COGS (Real Recipe/Stock Cost)", centsToPkr(report.totalActualPromoCostCents), StatusSky)
                        Text(
                            "Sales revenue records net paid cash/bank inflows; voucher discounts reduce net sales without double-counting fake promotional cash expenses. Real stock consumption is tracked separately as Actual Promotional COGS.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (report.branchRows.isNotEmpty()) {
                item { Text("Branch-Wise Loyalty Breakdown", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright) }
                items(report.branchRows, key = { it.branchId }) { row ->
                    Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(row.branchName, style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
                            Text(
                                "Enrolled ${row.enrolledCustomers} • Active ${row.activeCards} • Ready ${row.rewardEligibleCards} • Expired ${row.expiredCards}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Stamps +${row.stampsIssued} / -${row.stampsReversed} • Vouchers ${row.vouchersRedeemed}/${row.vouchersEarned} redeemed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Paid Sales ${centsToPkr(row.netPaidSalesWithLoyaltyCents)} • Promo Disc ${centsToPkr(row.promoDiscountTotalCents)} • Promo COGS ${centsToPkr(row.actualPromoCogsCents)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusEmerald
                            )
                        }
                    }
                }
            }

            if (report.topCustomers.isNotEmpty()) {
                item { Text("Top Loyalty Members Leaderboard", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright) }
                items(report.topCustomers, key = { it.customerId }) { row ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenCustomerProfile(row.customerId) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${row.customerName} • ${row.cardNumber}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "Stamps: ${row.currentStamps} cur / ${row.totalStampsEarned} total • Rewards: ${row.totalRewardsRedeemed}/${row.totalRewardsEarned}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(centsToPkr(row.totalSpendCents), style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoyaltySettingsAndAuditTab(
    session: UserSession,
    uiState: SmartLoyaltyUiState,
    onSaveSettings: (LoyaltyProgramSettingsRecord) -> Unit
) {
    val cfg = uiState.settings
    val canEdit = session.capabilities.canConfigureLoyaltyProgram

    var enabled by remember(cfg) { mutableStateOf(cfg.programEnabled) }
    var minSpendPkr by remember(cfg) { mutableStateOf((cfg.minQualifyingAmountCents / 100L).toString()) }
    var reqStamps by remember(cfg) { mutableStateOf(cfg.requiredStampsForReward.toString()) }
    var rewardCapPkr by remember(cfg) { mutableStateOf((cfg.rewardAmountCents / 100L).toString()) }
    var validityMonths by remember(cfg) { mutableStateOf(cfg.validityMonths.toString()) }
    var graceDays by remember(cfg) { mutableStateOf(cfg.redemptionGraceDays.toString()) }
    var calcMethod by remember(cfg) { mutableStateOf(cfg.qualifyingCalculationMethod) }
    var excludeTax by remember(cfg) { mutableStateOf(cfg.excludeTaxFromQualifying) }
    var excludeService by remember(cfg) { mutableStateOf(cfg.excludeServiceAndDeliveryFromQualifying) }
    var cardPrefix by remember(cfg) { mutableStateOf(cfg.cardPrefix) }
    var voucherPrefix by remember(cfg) { mutableStateOf(cfg.voucherPrefix) }
    val eligibleCats = remember(cfg) { mutableStateListOf<String>().apply { addAll(cfg.eligibleCategoryIds) } }
    val redeemableCats = remember(cfg) { mutableStateListOf<String>().apply { addAll(cfg.redeemableCategoryIds) } }

    val dateFmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.US) }

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Smart Loyalty Program Configuration", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                    if (!canEdit) {
                        StatusPill(
                            text = "READ-ONLY • ADMIN / OWNER / MANAGER REQUIRED TO EDIT",
                            containerColor = ObsidianSurfaceElevated,
                            contentColor = StatusAmber
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Loyalty Program Enabled", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = enabled, onCheckedChange = { if (canEdit) enabled = it }, enabled = canEdit)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minSpendPkr,
                            onValueChange = { minSpendPkr = it },
                            enabled = canEdit,
                            label = { Text("Min Spend (PKR)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("loyalty_min_spend_input")
                        )
                        OutlinedTextField(
                            value = reqStamps,
                            onValueChange = { reqStamps = it },
                            enabled = canEdit,
                            label = { Text("Stamps Needed") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("loyalty_req_stamps_input")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rewardCapPkr,
                            onValueChange = { rewardCapPkr = it },
                            enabled = canEdit,
                            label = { Text("Reward Cap <= 1500 PKR") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("loyalty_reward_cap_input")
                        )
                        OutlinedTextField(
                            value = validityMonths,
                            onValueChange = { validityMonths = it },
                            enabled = canEdit,
                            label = { Text("Validity (Months)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = graceDays,
                            onValueChange = { graceDays = it },
                            enabled = canEdit,
                            label = { Text("Grace (Days)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("Qualifying Amount Calculation Basis", style = MaterialTheme.typography.labelLarge, color = CraveGold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        QualifyingCalculationMethod.entries.forEach { m ->
                            FilterChip(
                                selected = calcMethod == m,
                                onClick = { if (canEdit) calcMethod = m },
                                enabled = canEdit,
                                label = { Text(m.label) }
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Exclude Tax from Qualifying Spend", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = excludeTax, onCheckedChange = { if (canEdit) excludeTax = it }, enabled = canEdit)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Exclude Service/Delivery from Qualifying Spend", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = excludeService, onCheckedChange = { if (canEdit) excludeService = it }, enabled = canEdit)
                    }

                    if (uiState.categories.isNotEmpty()) {
                        Text("Restrict Earning Categories (empty = All)", style = MaterialTheme.typography.labelSmall, color = CraveGold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            uiState.categories.forEach { cat ->
                                val selected = cat.categoryId in eligibleCats
                                FilterChip(
                                    selected = selected,
                                    enabled = canEdit,
                                    onClick = {
                                        if (selected) eligibleCats.remove(cat.categoryId) else eligibleCats.add(cat.categoryId)
                                    },
                                    label = { Text(cat.name) }
                                )
                            }
                        }
                        Text("Restrict Voucher Redemption Categories (empty = All)", style = MaterialTheme.typography.labelSmall, color = CraveGold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            uiState.categories.forEach { cat ->
                                val selected = cat.categoryId in redeemableCats
                                FilterChip(
                                    selected = selected,
                                    enabled = canEdit,
                                    onClick = {
                                        if (selected) redeemableCats.remove(cat.categoryId) else redeemableCats.add(cat.categoryId)
                                    },
                                    label = { Text(cat.name) }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = cardPrefix, onValueChange = { cardPrefix = it }, enabled = canEdit, label = { Text("Card Prefix") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = voucherPrefix, onValueChange = { voucherPrefix = it }, enabled = canEdit, label = { Text("Voucher Prefix") }, singleLine = true, modifier = Modifier.weight(1f))
                    }

                    if (canEdit) {
                        Button(
                            onClick = {
                                val minCents = ((minSpendPkr.toLongOrNull() ?: 1500L) * 100L)
                                val stamps = reqStamps.toIntOrNull() ?: 6
                                val rewardCents = ((rewardCapPkr.toLongOrNull() ?: 1500L) * 100L)
                                val months = validityMonths.toIntOrNull() ?: 3
                                val grace = graceDays.toIntOrNull() ?: 30
                                onSaveSettings(
                                    cfg.copy(
                                        programEnabled = enabled,
                                        minQualifyingAmountCents = minCents,
                                        requiredStampsForReward = stamps,
                                        rewardAmountCents = rewardCents,
                                        validityMonths = months,
                                        redemptionGraceDays = grace,
                                        qualifyingCalculationMethod = calcMethod,
                                        eligibleCategoryIds = eligibleCats.toSet(),
                                        redeemableCategoryIds = redeemableCats.toSet(),
                                        excludeTaxFromQualifying = excludeTax,
                                        excludeServiceAndDeliveryFromQualifying = excludeService,
                                        cardPrefix = cardPrefix,
                                        voucherPrefix = voucherPrefix
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("loyalty_save_settings_button")
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save Program Settings")
                        }
                    }
                    Text(
                        "Max reward cap is capped at ${centsToPkr(LoyaltyRuleConstants.MAX_REWARD_CAP_CENTS)} per reward cycle.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.History, contentDescription = null, tint = CraveGoldBright)
                Text("Immutable Loyalty Audit Log (${uiState.auditLogs.size})", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
            }
        }

        items(uiState.auditLogs.take(40), key = { it.logId }) { log ->
            Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(log.action, style = MaterialTheme.typography.titleSmall, color = CraveGold)
                        Text(dateFmt.format(Date(log.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(log.details, style = MaterialTheme.typography.bodySmall)
                    Text("${log.actorRole} • ${log.actorEmail}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}
