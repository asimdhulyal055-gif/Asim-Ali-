package com.example.ui.loyalty

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.brand.BrandAssetManager
import com.example.data.brand.BrandLogoState
import com.example.domain.model.CustomerLoyaltyBundle
import com.example.domain.model.CustomerProfileRecord
import com.example.domain.model.LoyaltyCardRecord
import com.example.domain.model.LoyaltyCardStatus
import com.example.domain.model.LoyaltyRewardVoucherRecord
import com.example.domain.model.LoyaltyVoucherStatus
import com.example.domain.model.UserSession
import com.example.domain.model.centsToPkr
import com.example.printing.LoyaltyStatementPdfHelper
import com.example.printing.Phase4ReportExporter
import com.example.printing.QrCodeHelper
import com.example.ui.components.AaCraveBrandLogo
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
import com.example.ui.theme.StatusAmberBg
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusCrimsonBg
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusEmeraldBg
import com.example.ui.theme.StatusSky
import com.example.ui.theme.StatusSkyBg
import com.example.ui.viewmodel.SmartLoyaltyUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LoyaltyDashboardAndMembersTab(
    session: UserSession,
    uiState: SmartLoyaltyUiState,
    onSearchChanged: (String) -> Unit,
    onSelectStatusFilter: (LoyaltyCardStatus?) -> Unit,
    onOpenScanner: () -> Unit,
    onEnrollCustomerCard: (String, String?) -> Unit,
    onOpenMemberProfile: (String) -> Unit
) {
    var showEnrollDialog by remember { mutableStateOf(false) }

    val cardsByCustomer = remember(uiState.cards) {
        uiState.cards.associateBy { it.customerId }
    }
    val unenrolledCustomers = remember(uiState.customers, cardsByCustomer) {
        uiState.customers.filter { !cardsByCustomer.containsKey(it.customerId) }
    }

    val activeCount = uiState.cards.count {
        it.effectiveStatus == LoyaltyCardStatus.ACTIVE || it.effectiveStatus == LoyaltyCardStatus.REWARD_ELIGIBLE
    }
    val eligibleCount = uiState.cards.count { it.effectiveStatus == LoyaltyCardStatus.REWARD_ELIGIBLE }
    val pendingActivationCount = uiState.cards.count { it.effectiveStatus == LoyaltyCardStatus.NOT_ACTIVATED }
    val totalPromoCost = uiState.analyticsReport?.totalActualPromoCostCents ?: 0L
    val totalRedeemedValue = uiState.analyticsReport?.totalPromoDiscountGivenCents ?: 0L

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AA CRAVE Smart Loyalty", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                            Text(
                                "${uiState.settings.requiredStampsForReward} Qualifying Orders (${centsToPkr(uiState.settings.minQualifyingAmountCents)}+) -> Up to ${centsToPkr(uiState.settings.rewardAmountCents)} Voucher • ${uiState.settings.validityMonths}m Valid",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = if (uiState.settings.programEnabled) "ACTIVE" else "PAUSED",
                            containerColor = if (uiState.settings.programEnabled) StatusEmeraldBg else StatusCrimsonBg,
                            contentColor = if (uiState.settings.programEnabled) StatusEmerald else StatusCrimson
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpiMiniBox("Enrolled", "${uiState.cards.size}", CraveGoldBright, Modifier.weight(1f))
                        KpiMiniBox("Active", "$activeCount", StatusEmerald, Modifier.weight(1f))
                        KpiMiniBox("Ready Reward", "$eligibleCount", StatusSky, Modifier.weight(1f))
                        KpiMiniBox("Unactivated", "$pendingActivationCount", StatusAmber, Modifier.weight(1f))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpiMiniBox("Promo Discount", centsToPkr(totalRedeemedValue), CraveGoldBright, Modifier.weight(1f))
                        KpiMiniBox("Actual COGS", centsToPkr(totalPromoCost), StatusEmerald, Modifier.weight(1f))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onOpenScanner,
                            colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("loyalty_hub_scan_qr_button")
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan QR")
                        }
                        if (session.capabilities.canManageLoyaltyCards || session.capabilities.canScanAndRedeemLoyalty) {
                            OutlinedButton(
                                onClick = { showEnrollDialog = true },
                                border = BorderStroke(1.dp, CraveGold),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("loyalty_enroll_member_button")
                            ) {
                                Icon(Icons.Filled.AddCard, contentDescription = null, tint = CraveGoldBright, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Enroll Member", color = CraveGoldBright)
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Search member name, phone, card #, or QR token...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchChanged("") }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedStatusFilter == null,
                        onClick = { onSelectStatusFilter(null) },
                        label = { Text("All (${uiState.cards.size})") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                    )
                    LoyaltyCardStatus.entries.forEach { st ->
                        val cnt = uiState.cards.count { it.effectiveStatus == st }
                        if (cnt > 0 || uiState.selectedStatusFilter == st) {
                            FilterChip(
                                selected = uiState.selectedStatusFilter == st,
                                onClick = { onSelectStatusFilter(st) },
                                label = { Text("${st.label} ($cnt)") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                            )
                        }
                    }
                }
            }
        }

        if (uiState.filteredCards.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.AddCard, contentDescription = null, tint = CraveGold, modifier = Modifier.size(36.dp))
                        Text("No Loyalty Cards Match", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap 'Enroll Member' to issue a digital Smart Loyalty Card with QR code to any customer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(uiState.filteredCards, key = { it.cardId }) { card ->
                LoyaltyMemberSummaryCard(
                    card = card,
                    onClick = { onOpenMemberProfile(card.customerId) }
                )
            }
        }
    }

    if (showEnrollDialog) {
        EnrollCustomerCardDialog(
            unenrolledCustomers = unenrolledCustomers,
            onDismiss = { showEnrollDialog = false },
            onEnroll = { custId, customNo ->
                onEnrollCustomerCard(custId, customNo)
                showEnrollDialog = false
            }
        )
    }
}

@Composable
private fun KpiMiniBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianBg,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LoyaltyMemberSummaryCard(
    card: LoyaltyCardRecord,
    onClick: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("loyalty_card_row_${card.cardNumber}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(card.customerName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("${card.cardNumber} • ${card.customerPhone}", style = MaterialTheme.typography.bodySmall, color = CraveGold)
                }
                StatusPill(
                    text = card.effectiveStatus.label,
                    containerColor = cardStatusBgColor(card.effectiveStatus),
                    contentColor = cardStatusColor(card.effectiveStatus)
                )
            }
            StampMiniProgressStrip(
                current = card.currentCycleStamps,
                required = card.requiredStamps
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Cycle #${card.cycleCount} • ${card.currentCycleStamps}/${card.requiredStamps} Stamps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = card.expiresAt?.let { "Expires: ${dateFmt.format(Date(it))}" } ?: "Starts 3m on 1st paid PKR 1,500+ order",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (card.effectiveStatus == LoyaltyCardStatus.EXPIRED) StatusCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StampMiniProgressStrip(current: Int, required: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val totalSlots = required.coerceIn(1, 12)
        for (i in 1..totalSlots) {
            val earned = i <= current
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (earned) CraveGold else ObsidianBg)
                    .border(1.dp, if (earned) CraveGoldBright else ObsidianBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (earned) {
                    Icon(Icons.Filled.Check, contentDescription = "Stamp $i earned", tint = OnCraveGold, modifier = Modifier.size(13.dp))
                } else {
                    Text("$i", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerLoyaltyProfileScreen(
    session: UserSession,
    bundle: CustomerLoyaltyBundle,
    logoState: BrandLogoState,
    branchName: String = session.branchName,
    branchAddress: String = "",
    onBack: () -> Unit,
    onEnrollCard: (String) -> Unit,
    onSuspendCard: (String, String) -> Unit,
    onReactivateCard: (String, String) -> Unit,
    onReplaceCard: (String, String, String?) -> Unit,
    onRenewCard: (String, String) -> Unit,
    onAdjustStamps: (String, Int, String) -> Unit,
    onShowMessage: (String, Boolean) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US) }

    var actionDialogType by remember { mutableStateOf<String?>(null) } // "SUSPEND", "REACTIVATE", "REPLACE", "RENEW", "ADJUST"
    var generatedStatementFile by remember { mutableStateOf<File?>(null) }

    val card = bundle.activeCard

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianSurface),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to loyalty list", tint = CraveGoldBright)
                    }
                },
                title = {
                    Column {
                        Text(bundle.customer.name, style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                        Text(bundle.customer.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (card != null) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        val logoBitmap = BrandAssetManager(context).decodeRasterBitmap(logoState, 512)
                                        LoyaltyStatementPdfHelper.generateCustomerStatementPdf(
                                            context = context,
                                            bundle = bundle,
                                            branchName = branchName,
                                            branchAddress = branchAddress,
                                            logoBitmap = logoBitmap
                                        )
                                    }
                                    result.fold(
                                        onSuccess = { pdf ->
                                            generatedStatementFile = pdf
                                            onShowMessage("Generated PDF statement: ${pdf.name}", false)
                                        },
                                        onFailure = { err ->
                                            onShowMessage(err.message ?: "PDF generation failed.", true)
                                        }
                                    )
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("loyalty_profile_pdf_button")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = "Generate PDF Statement", tint = CraveGoldBright)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (card == null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Customer is not enrolled yet", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                        Text(
                            "Issue a digital 6-stamp Smart Loyalty Card with unique Card # and QR code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { onEnrollCard(bundle.customer.customerId) },
                            colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Filled.AddCard, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Issue Digital Loyalty Card")
                        }
                    }
                }
            } else {
                DigitalStampCardVisual(card = card, logoState = logoState)

                generatedStatementFile?.let { pdf ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                        border = BorderStroke(1.dp, StatusEmerald),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Statement Ready (${pdf.name})", style = MaterialTheme.typography.titleSmall, color = StatusEmerald)
                                Text("A4 Loyalty Statement PDF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(
                                onClick = { Phase4ReportExporter.shareReportFile(context, pdf, "application/pdf", "Share Loyalty Statement") },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Share PDF Statement", tint = CraveGoldBright)
                            }
                            IconButton(
                                onClick = { printLoyaltyPdf(context, pdf, "AA CRAVE Loyalty - ${card.cardNumber}") },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Print, contentDescription = "Print PDF Statement", tint = CraveGoldBright)
                            }
                        }
                    }
                }

                // Authorized Card Lifecycle Controls
                if (session.capabilities.canManageLoyaltyCards || session.capabilities.canAdjustLoyaltyStamps) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Authorized Card Controls", style = MaterialTheme.typography.titleSmall, color = CraveGold)
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (session.capabilities.canAdjustLoyaltyStamps && card.status != LoyaltyCardStatus.REPLACED && card.status != LoyaltyCardStatus.SUSPENDED) {
                                    OutlinedButton(onClick = { actionDialogType = "ADJUST" }, modifier = Modifier.testTag("card_adjust_stamps_button")) {
                                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Adjust Stamps")
                                    }
                                }
                                if (session.capabilities.canManageLoyaltyCards && card.status != LoyaltyCardStatus.REPLACED) {
                                    if (card.effectiveStatus == LoyaltyCardStatus.SUSPENDED) {
                                        OutlinedButton(onClick = { actionDialogType = "REACTIVATE" }, modifier = Modifier.testTag("card_reactivate_button")) {
                                            Icon(Icons.Filled.Autorenew, contentDescription = null, tint = StatusEmerald, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Reactivate", color = StatusEmerald)
                                        }
                                    } else {
                                        OutlinedButton(onClick = { actionDialogType = "SUSPEND" }, modifier = Modifier.testTag("card_suspend_button")) {
                                            Icon(Icons.Filled.Block, contentDescription = null, tint = StatusCrimson, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Suspend Card", color = StatusCrimson)
                                        }
                                    }
                                    OutlinedButton(onClick = { actionDialogType = "REPLACE" }, modifier = Modifier.testTag("card_replace_button")) {
                                        Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = CraveGoldBright, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Replace Card", color = CraveGoldBright)
                                    }
                                    if (card.effectiveStatus == LoyaltyCardStatus.EXPIRED) {
                                        Button(
                                            onClick = { actionDialogType = "RENEW" },
                                            colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                                            modifier = Modifier.testTag("card_renew_button")
                                        ) {
                                            Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Renew Expired Card")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Vouchers Section
            Text("Reward Vouchers (${bundle.vouchers.size})", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
            if (bundle.vouchers.isEmpty()) {
                Text("No vouchers unlocked yet. Complete 6 qualifying paid orders to unlock a ${centsToPkr(bundle.settings.rewardAmountCents)} voucher.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                bundle.vouchers.forEach { v ->
                    VoucherSummaryBlock(v = v, dateFmt = dateFmt)
                }
            }

            // Stamp History Ledger
            Text("Stamp Ledger (${bundle.stampTransactions.size})", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
            if (bundle.stampTransactions.isEmpty()) {
                Text("No stamp events recorded yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                bundle.stampTransactions.forEach { tx ->
                    Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${tx.txType.label} • Cycle #${tx.cycleNumber}", style = MaterialTheme.typography.titleSmall)
                                Text(tx.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${dateFmt.format(Date(tx.createdAt))} • By ${tx.actorName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = when {
                                    tx.stampsDelta > 0 -> "+${tx.stampsDelta}"
                                    else -> "${tx.stampsDelta}"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (tx.stampsDelta > 0) StatusEmerald else if (tx.stampsDelta < 0) StatusCrimson else CraveGoldBright,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Linked Qualifying Orders
            if (bundle.qualifyingOrders.isNotEmpty()) {
                Text("Linked Orders (${bundle.qualifyingOrders.size})", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                bundle.qualifyingOrders.take(10).forEach { ord ->
                    Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${ord.invoiceNumber} (${ord.status.label})", style = MaterialTheme.typography.titleSmall)
                                ord.loyaltyStatusMessage?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = StatusEmerald)
                                }
                            }
                            Text(centsToPkr(ord.totalCents), style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
                        }
                    }
                }
            }

            if (bundle.cardHistory.size > 1) {
                Text("Card Replacement History (${bundle.cardHistory.size})", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                bundle.cardHistory.forEach { hist ->
                    Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(hist.cardNumber, style = MaterialTheme.typography.titleSmall, color = CraveGold)
                                Text("Issued ${dateFmt.format(Date(hist.issuedAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(
                                text = hist.effectiveStatus.label,
                                containerColor = cardStatusBgColor(hist.effectiveStatus),
                                contentColor = cardStatusColor(hist.effectiveStatus)
                            )
                        }
                    }
                }
            }
        }
    }

    if (card != null && actionDialogType != null) {
        CardLifecycleActionDialog(
            actionType = actionDialogType!!,
            card = card,
            onDismiss = { actionDialogType = null },
            onConfirm = { reason, deltaOrNull, customCardNoOrNull ->
                when (actionDialogType) {
                    "SUSPEND" -> onSuspendCard(card.cardId, reason)
                    "REACTIVATE" -> onReactivateCard(card.cardId, reason)
                    "REPLACE" -> onReplaceCard(card.cardId, reason, customCardNoOrNull)
                    "RENEW" -> onRenewCard(card.cardId, reason)
                    "ADJUST" -> onAdjustStamps(card.cardId, deltaOrNull ?: 0, reason)
                }
                actionDialogType = null
            }
        )
    }
}

@Composable
fun DigitalStampCardVisual(
    card: LoyaltyCardRecord,
    logoState: BrandLogoState
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val qrBitmap = remember(card.qrCodeToken) { QrCodeHelper.generateQrBitmap(card.qrCodeToken, 360) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1A1610), Color(0xFF0D0B08), Color(0xFF231C10))
                )
            )
            .border(1.5.dp, CraveGoldBright.copy(alpha = 0.75f), MaterialTheme.shapes.large)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AaCraveBrandLogo(size = 44.dp, logoState = logoState, showPlaceholderLabel = false)
                    Column {
                        Text("AA CRAVE REWARDS", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright, fontWeight = FontWeight.Bold)
                        Text(card.cardNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                StatusPill(
                    text = card.effectiveStatus.label,
                    containerColor = cardStatusBgColor(card.effectiveStatus),
                    contentColor = cardStatusColor(card.effectiveStatus)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(card.customerName, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Cycle #${card.cycleCount} • ${card.currentCycleStamps} of ${card.requiredStamps} Stamps", style = MaterialTheme.typography.bodyMedium, color = CraveGoldBright)

                    // 6-slot gold stamp grid
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val req = card.requiredStamps.coerceIn(1, 12)
                        for (slot in 1..req) {
                            val filled = slot <= card.currentCycleStamps
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) CraveGoldBright else ObsidianBg.copy(alpha = 0.8f))
                                    .border(1.5.dp, CraveGoldBright, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (filled) {
                                    Icon(Icons.Filled.Star, contentDescription = "Stamp $slot complete", tint = OnCraveGold, modifier = Modifier.size(18.dp))
                                } else {
                                    Text("$slot", style = MaterialTheme.typography.labelMedium, color = CraveGold)
                                }
                            }
                        }
                    }
                }

                if (qrBitmap != null) {
                    Surface(color = Color.White, shape = MaterialTheme.shapes.small) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code for Loyalty Card ${card.cardNumber}",
                            modifier = Modifier.size(92.dp).padding(5.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = CraveGold.copy(alpha = 0.3f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = card.firstQualifyingInvoiceAt?.let { "Activated: ${dateFmt.format(Date(it))}" } ?: "Not yet activated",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = card.expiresAt?.let { "Expires: ${dateFmt.format(Date(it))}" } ?: "3 Months on 1st Paid PKR 1,500+",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (card.effectiveStatus == LoyaltyCardStatus.EXPIRED) StatusCrimson else CraveGold
                )
            }
            Text(
                text = "Lifetime Stamps: ${card.totalStampsEarned} • Rewards Unlocked: ${card.totalRewardsEarned} • Redeemed: ${card.totalRewardsRedeemed}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VoucherSummaryBlock(v: LoyaltyRewardVoucherRecord, dateFmt: SimpleDateFormat) {
    val qrBmp = remember(v.qrCodeToken) { QrCodeHelper.generateQrBitmap(v.qrCodeToken, 200) }
    Card(colors = CardDefaults.cardColors(containerColor = ObsidianSurface), border = BorderStroke(1.dp, ObsidianBorder)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (qrBmp != null) {
                Surface(color = Color.White, shape = MaterialTheme.shapes.extraSmall) {
                    Image(bitmap = qrBmp.asImageBitmap(), contentDescription = "Voucher ${v.voucherCode}", modifier = Modifier.size(56.dp).padding(3.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(v.voucherCode, style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
                    StatusPill(text = v.effectiveStatus.label, containerColor = voucherStatusBg(v.effectiveStatus), contentColor = voucherStatusColor(v.effectiveStatus))
                }
                Text("Max Reward: ${centsToPkr(v.rewardValueCents)} • Grace ends ${dateFmt.format(Date(v.graceExpiresAt))}", style = MaterialTheme.typography.bodySmall)
                if (v.effectiveStatus == LoyaltyVoucherStatus.REDEEMED) {
                    Text("Redeemed ${centsToPkr(v.redeemedAmountCents)} on ${v.redeemedInvoiceNumber ?: ""}", style = MaterialTheme.typography.labelSmall, color = StatusEmerald)
                }
            }
        }
    }
}

@Composable
private fun EnrollCustomerCardDialog(
    unenrolledCustomers: List<CustomerProfileRecord>,
    onDismiss: () -> Unit,
    onEnroll: (String, String?) -> Unit
) {
    var selectedCustomerId by remember { mutableStateOf(unenrolledCustomers.firstOrNull()?.customerId) }
    var customCardNo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Enroll Customer in Smart Loyalty", color = CraveGoldBright) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (unenrolledCustomers.isEmpty()) {
                    Text(
                        "All registered branch customers already hold an active Smart Loyalty Card. Add a new customer in CRM first or manage existing cards.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber
                    )
                } else {
                    Text("Select Registered Customer:", style = MaterialTheme.typography.labelLarge, color = CraveGold)
                    unenrolledCustomers.take(15).forEach { cust ->
                        FilterChip(
                            selected = selectedCustomerId == cust.customerId,
                            onClick = { selectedCustomerId = cust.customerId },
                            label = { Text("${cust.name} (${cust.phone})") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = customCardNo,
                        onValueChange = { customCardNo = it },
                        label = { Text("Custom Card Number (Optional)") },
                        placeholder = { Text("Leave blank to auto-generate AAC-XXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val custId = selectedCustomerId ?: return@Button
                    onEnroll(custId, customCardNo.trim().ifBlank { null })
                },
                enabled = selectedCustomerId != null,
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                modifier = Modifier.testTag("confirm_enroll_card_button")
            ) {
                Text("Issue Card")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CardLifecycleActionDialog(
    actionType: String,
    card: LoyaltyCardRecord,
    onDismiss: () -> Unit,
    onConfirm: (String, Int?, String?) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var deltaText by remember { mutableStateOf("1") }
    var customCardNo by remember { mutableStateOf("") }

    val title = when (actionType) {
        "SUSPEND" -> "Suspend Card ${card.cardNumber}"
        "REACTIVATE" -> "Reactivate Card ${card.cardNumber}"
        "REPLACE" -> "Replace Card ${card.cardNumber}"
        "RENEW" -> "Renew Expired Card ${card.cardNumber}"
        else -> "Adjust Stamps (${card.currentCycleStamps}/${card.requiredStamps})"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text(title, color = CraveGoldBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (actionType == "ADJUST") {
                    OutlinedTextField(
                        value = deltaText,
                        onValueChange = { deltaText = it },
                        label = { Text("Stamp Delta (e.g. 1 or -1)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (actionType == "REPLACE") {
                    Text(
                        "Preserves ${card.currentCycleStamps} stamps, exact original expiry, and transfers active vouchers to the new card.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusEmerald
                    )
                    OutlinedTextField(
                        value = customCardNo,
                        onValueChange = { customCardNo = it },
                        label = { Text("New Card # (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (actionType == "REACTIVATE") {
                    Text(
                        "Preserves exact original expiry date — does not silently extend validity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber
                    )
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Audit Reason *") },
                    placeholder = { Text("Required for immutable audit trail") },
                    modifier = Modifier.fillMaxWidth().testTag("card_action_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        reason,
                        deltaText.toIntOrNull(),
                        customCardNo.trim().ifBlank { null }
                    )
                },
                enabled = reason.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                modifier = Modifier.testTag("card_action_confirm_button")
            ) {
                Text("Confirm")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

internal fun cardStatusColor(status: LoyaltyCardStatus): Color = when (status) {
    LoyaltyCardStatus.NOT_ACTIVATED -> StatusSky
    LoyaltyCardStatus.ACTIVE -> StatusEmerald
    LoyaltyCardStatus.REWARD_ELIGIBLE -> CraveGoldBright
    LoyaltyCardStatus.COMPLETED -> StatusEmerald
    LoyaltyCardStatus.EXPIRED -> StatusCrimson
    LoyaltyCardStatus.SUSPENDED -> StatusAmber
    LoyaltyCardStatus.REPLACED -> Color(0xFF9E9E9E)
}

internal fun cardStatusBgColor(status: LoyaltyCardStatus): Color = when (status) {
    LoyaltyCardStatus.NOT_ACTIVATED -> StatusSkyBg
    LoyaltyCardStatus.ACTIVE -> StatusEmeraldBg
    LoyaltyCardStatus.REWARD_ELIGIBLE -> CraveGoldContainer
    LoyaltyCardStatus.COMPLETED -> StatusEmeraldBg
    LoyaltyCardStatus.EXPIRED -> StatusCrimsonBg
    LoyaltyCardStatus.SUSPENDED -> StatusAmberBg
    LoyaltyCardStatus.REPLACED -> ObsidianSurfaceElevated
}

private fun printLoyaltyPdf(context: Context, pdfFile: File, jobName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: android.os.Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback?.onLayoutCancelled()
                return
            }
            val info = android.print.PrintDocumentInfo.Builder(pdfFile.name)
                .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback?.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?
        ) {
            runCatching {
                FileInputStream(pdfFile).use { input ->
                    FileOutputStream(destination?.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
            }.onSuccess {
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }.onFailure {
                callback?.onWriteFailed(it.message)
            }
        }
    }
    printManager.print(jobName, adapter, null)
}
