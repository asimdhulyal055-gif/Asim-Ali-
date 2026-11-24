package com.example.ui.documents

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.brand.BrandAssetManager
import com.example.domain.model.BranchSummary
import com.example.domain.model.CustomerInvoiceDocument
import com.example.domain.model.InvoicePaperSize
import com.example.domain.model.PosOrderRecord
import com.example.domain.model.UserSession
import com.example.domain.model.centsToCurrency
import com.example.printing.InvoicePdfAndPrintHelper
import com.example.printing.ThermalPrinterService
import com.example.ui.components.AaCraveBrandLogo
import com.example.ui.theme.CraveGold
import com.example.ui.theme.CraveGoldBright
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnCraveGold
import com.example.ui.viewmodel.PosUiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerInvoiceScreen(
    session: UserSession,
    uiState: PosUiState,
    branches: List<BranchSummary>,
    brandAssetManager: BrandAssetManager,
    thermalPrinterService: ThermalPrinterService,
    buildInvoice: (PosOrderRecord, String, Boolean) -> CustomerInvoiceDocument,
    onSelectOrder: (String) -> Unit,
    onRecordReprint: (String) -> Unit,
    onPrinterFeedback: (Result<String>) -> Unit,
    onClearFeedback: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var paperSize by remember { mutableStateOf(InvoicePaperSize.THERMAL_80MM) }
    var forceReprintFlag by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearFeedback()
        }
    }

    val selectedOrder = uiState.orders.firstOrNull { it.orderId == uiState.selectedOrderForInvoiceId }
        ?: uiState.orders.firstOrNull()
    val branchAddress = branches.firstOrNull { it.branchId == session.branchId }?.address
        ?: "AA CRAVE Culinary District"

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianSurface),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CraveGoldBright)
                    }
                },
                title = {
                    Column {
                        Text("Customer Invoice", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                        Text("Separate financial customer receipt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.orders.isEmpty() || selectedOrder == null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No customer invoices generated yet. Confirm an order in POS Terminal to create a Customer Invoice and separate Kitchen KOT.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
                return@Column
            }

            // Order Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.orders.forEach { order ->
                    FilterChip(
                        selected = order.orderId == selectedOrder.orderId,
                        onClick = {
                            forceReprintFlag = false
                            onSelectOrder(order.orderId)
                        },
                        label = { Text(order.invoiceNumber) }
                    )
                }
            }

            // Paper Layout Selector (80mm Thermal vs A4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InvoicePaperSize.entries.forEach { size ->
                    FilterChip(
                        selected = paperSize == size,
                        onClick = { paperSize = size },
                        label = { Text(size.label) }
                    )
                }
            }

            val invoiceDoc = buildInvoice(selectedOrder, branchAddress, forceReprintFlag)
            val previewWidth = if (paperSize == InvoicePaperSize.THERMAL_80MM) 330.dp else 540.dp

            // Printable Paper Invoice Card (Top Center Logo)
            Surface(
                color = Color.White,
                contentColor = Color.Black,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .widthIn(max = previewWidth)
                    .fillMaxWidth()
                    .testTag("customer_invoice_preview")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (paperSize == InvoicePaperSize.THERMAL_80MM) 16.dp else 26.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // TOP CENTER AA CRAVE LOGO (Small, elegant size)
                    AaCraveBrandLogo(
                        size = 48.dp,
                        logoState = uiState.logoState,
                        showPlaceholderLabel = true
                    )

                    Text("AA CRAVE", style = MaterialTheme.typography.titleLarge, color = Color.Black, fontWeight = FontWeight.Bold)
                    Text(invoiceDoc.branchName, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray, textAlign = TextAlign.Center)
                    Text(invoiceDoc.branchAddress, style = MaterialTheme.typography.labelSmall, color = Color.Gray, textAlign = TextAlign.Center)

                    if (invoiceDoc.isReprint) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFEBEE))
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "REPRINT (#${invoiceDoc.reprintNumber.coerceAtLeast(1)})",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    HorizontalDivider(color = Color.LightGray)

                    val dateText = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(invoiceDoc.timestampMillis))
                    InvoiceInfoRow("Invoice #:", invoiceDoc.invoiceNumber, bold = true)
                    InvoiceInfoRow("Order #:", "${invoiceDoc.orderNumber} (${invoiceDoc.orderType.label})")
                    if (!invoiceDoc.tableNumber.isNullOrBlank()) {
                        InvoiceInfoRow("Table #:", invoiceDoc.tableNumber)
                    }
                    InvoiceInfoRow("Date/Time:", dateText)
                    if (!invoiceDoc.customerName.isNullOrBlank()) {
                        InvoiceInfoRow("Customer:", invoiceDoc.customerName)
                    }

                    HorizontalDivider(color = Color.LightGray)

                    invoiceDoc.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val title = buildString {
                                    append("${item.quantity} x ${item.productName}")
                                    if (!item.variationName.isNullOrBlank()) append(" (${item.variationName})")
                                }
                                Text(title, style = MaterialTheme.typography.bodySmall, color = Color.Black, fontWeight = FontWeight.Medium)
                                if (item.addonsSummary.isNotBlank()) {
                                    Text("   + ${item.addonsSummary}", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                                }
                            }
                            Text(
                                text = centsToCurrency(item.lineTotalCents),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Black,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    HorizontalDivider(color = Color.LightGray)

                    InvoiceInfoRow("Subtotal", centsToCurrency(invoiceDoc.subtotalCents))
                    if (invoiceDoc.discountCents > 0L) {
                        InvoiceInfoRow("Discount", "-${centsToCurrency(invoiceDoc.discountCents)}")
                    }
                    InvoiceInfoRow("Tax", centsToCurrency(invoiceDoc.taxCents))
                    if (invoiceDoc.serviceChargeCents > 0L) {
                        InvoiceInfoRow("Service Charge", centsToCurrency(invoiceDoc.serviceChargeCents))
                    }

                    HorizontalDivider(color = Color.Black)
                    InvoiceInfoRow("TOTAL PAYABLE", centsToCurrency(invoiceDoc.totalPayableCents), bold = true)
                    InvoiceInfoRow("Payment (${invoiceDoc.paymentMethodLabel})", centsToCurrency(invoiceDoc.paidAmountCents))
                    if (invoiceDoc.remainingBalanceCents > 0L) {
                        InvoiceInfoRow("Remaining Balance", centsToCurrency(invoiceDoc.remainingBalanceCents), bold = true)
                    } else if (invoiceDoc.changeCents > 0L) {
                        InvoiceInfoRow("Change Due", centsToCurrency(invoiceDoc.changeCents))
                    }

                    if (!invoiceDoc.loyaltyCardNumber.isNullOrBlank() ||
                        !invoiceDoc.loyaltyVoucherCode.isNullOrBlank() ||
                        !invoiceDoc.loyaltyStatusMessage.isNullOrBlank()
                    ) {
                        HorizontalDivider(color = Color.LightGray)
                        Text(
                            text = "AA CRAVE SMART LOYALTY",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!invoiceDoc.loyaltyCardNumber.isNullOrBlank()) {
                            InvoiceInfoRow(
                                label = "Card ${invoiceDoc.loyaltyCardNumber}",
                                value = "Stamps: ${invoiceDoc.loyaltyStampsAfter}/${invoiceDoc.loyaltyRequiredStamps}",
                                bold = true
                            )
                        }
                        if (!invoiceDoc.loyaltyVoucherCode.isNullOrBlank() && invoiceDoc.loyaltyVoucherDiscountCents > 0L) {
                            InvoiceInfoRow(
                                label = "Promo Voucher (${invoiceDoc.loyaltyVoucherCode})",
                                value = "-${centsToCurrency(invoiceDoc.loyaltyVoucherDiscountCents)}"
                            )
                        }
                        if (!invoiceDoc.loyaltyStatusMessage.isNullOrBlank()) {
                            Text(
                                text = invoiceDoc.loyaltyStatusMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.DarkGray,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = invoiceDoc.thankYouMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val bmp = brandAssetManager.decodeRasterBitmap(uiState.logoState)
                        InvoicePdfAndPrintHelper.generateCustomerInvoicePdf(context, invoiceDoc, paperSize, bmp)
                            .onSuccess { pdfFile ->
                                InvoicePdfAndPrintHelper.launchSystemPrint(context, "AA_CRAVE_${invoiceDoc.invoiceNumber}", pdfFile)
                            }
                            .onFailure { onPrinterFeedback(Result.failure(it)) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Print Preview")
                }

                OutlinedButton(
                    onClick = {
                        val bmp = brandAssetManager.decodeRasterBitmap(uiState.logoState)
                        InvoicePdfAndPrintHelper.generateCustomerInvoicePdf(context, invoiceDoc, paperSize, bmp)
                            .onSuccess { pdfFile ->
                                onPrinterFeedback(Result.success("Exported PDF: ${pdfFile.name}"))
                            }
                            .onFailure { onPrinterFeedback(Result.failure(it)) }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export PDF")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val bmp = brandAssetManager.decodeRasterBitmap(uiState.logoState)
                        InvoicePdfAndPrintHelper.generateCustomerInvoicePdf(context, invoiceDoc, paperSize, bmp)
                            .onSuccess { pdfFile ->
                                InvoicePdfAndPrintHelper.sharePdfFile(context, pdfFile, "AA CRAVE Invoice ${invoiceDoc.invoiceNumber}")
                            }
                            .onFailure { onPrinterFeedback(Result.failure(it)) }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Invoice")
                }

                OutlinedButton(
                    onClick = {
                        forceReprintFlag = true
                        onRecordReprint(selectedOrder.orderId)
                        scope.launch {
                            val res = thermalPrinterService.printCustomerInvoice(invoiceDoc.copy(isReprint = true), paperSize)
                            onPrinterFeedback(res)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Thermal Reprint", color = CraveGoldBright)
                }
            }

            Text(
                text = "Printer Adapter: ${uiState.printerStatus.driverName} — ${uiState.printerStatus.setupGuidance}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun InvoiceInfoRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Black,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Black,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
        )
    }
}
