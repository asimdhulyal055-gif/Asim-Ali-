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
import androidx.compose.ui.unit.sp
import com.example.domain.model.KitchenKotDocument
import com.example.domain.model.KotPaperWidth
import com.example.domain.model.PosOrderRecord
import com.example.printing.InvoicePdfAndPrintHelper
import com.example.printing.ThermalPrinterService
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
fun KitchenKotScreen(
    uiState: PosUiState,
    thermalPrinterService: ThermalPrinterService,
    buildKot: (PosOrderRecord, Boolean) -> KitchenKotDocument,
    onSelectOrder: (String) -> Unit,
    onRecordReprint: (String) -> Unit,
    onPrinterFeedback: (Result<String>) -> Unit,
    onClearFeedback: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var paperWidth by remember { mutableStateOf(KotPaperWidth.THERMAL_58MM) }
    var forceReprintFlag by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearFeedback()
        }
    }

    val selectedOrder = uiState.orders.firstOrNull { it.orderId == uiState.selectedOrderForKotId }
        ?: uiState.orders.firstOrNull()

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
                        Text("Kitchen Order Ticket (KOT)", style = MaterialTheme.typography.titleLarge, color = CraveGoldBright)
                        Text("Compact slip • No prices or payment info", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        text = "No Kitchen KOT tickets queued yet. Confirm an order in POS Terminal to send a KOT to the kitchen queue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
                return@Column
            }

            // Order / KOT Selector
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
                        label = { Text("${order.kotNumber} (${order.orderNumber})") }
                    )
                }
            }

            // 58mm Compact (Default) vs 80mm
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KotPaperWidth.entries.forEach { width ->
                    FilterChip(
                        selected = paperWidth == width,
                        onClick = { paperWidth = width },
                        label = { Text(width.label) }
                    )
                }
            }

            val kotDoc = buildKot(selectedOrder, forceReprintFlag)
            val slipWidth = if (paperWidth == KotPaperWidth.THERMAL_58MM) 260.dp else 330.dp

            // Compact Thermal Kitchen Slip (ZERO Financial Info)
            Surface(
                color = Color.White,
                contentColor = Color.Black,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .widthIn(max = slipWidth)
                    .fillMaxWidth()
                    .testTag("kitchen_kot_preview")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "AA CRAVE KITCHEN",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = kotDoc.kotNumber,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (kotDoc.isReprint) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .padding(vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "REPRINT (#${kotDoc.reprintNumber.coerceAtLeast(1)})",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = Color.Black)

                    val dateText = SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(kotDoc.timestampMillis))
                    Text("Order: ${kotDoc.orderNumber}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("Type: ${kotDoc.orderType.label} | $dateText", style = MaterialTheme.typography.bodySmall, color = Color.Black)
                    if (!kotDoc.tableNumber.isNullOrBlank()) {
                        Text(
                            text = "TABLE: ${kotDoc.tableNumber}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    }
                    Text(
                        text = "PRIORITY: ${kotDoc.priority.label.uppercase()} | ${kotDoc.station}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    HorizontalDivider(color = Color.Black)

                    kotDoc.items.forEach { item ->
                        val title = buildString {
                            append(item.productName)
                            if (!item.variationName.isNullOrBlank()) append(" (${item.variationName})")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${item.quantity}x",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                if (item.addonsSummary.isNotBlank()) {
                                    Text(
                                        text = "+ ${item.addonsSummary}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.DarkGray
                                    )
                                }
                                if (item.specialInstructions.isNotBlank()) {
                                    Text(
                                        text = "NOTE: ${item.specialInstructions}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Kitchen Print Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        InvoicePdfAndPrintHelper.generateKitchenKotPdf(context, kotDoc, paperWidth)
                            .onSuccess { pdfFile ->
                                InvoicePdfAndPrintHelper.launchSystemPrint(context, "AA_CRAVE_${kotDoc.kotNumber}", pdfFile)
                            }
                            .onFailure { onPrinterFeedback(Result.failure(it)) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kitchen Print")
                }

                OutlinedButton(
                    onClick = {
                        InvoicePdfAndPrintHelper.generateKitchenKotPdf(context, kotDoc, paperWidth)
                            .onSuccess { pdfFile ->
                                onPrinterFeedback(Result.success("Exported KOT PDF: ${pdfFile.name}"))
                            }
                            .onFailure { onPrinterFeedback(Result.failure(it)) }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export KOT")
                }
            }

            OutlinedButton(
                onClick = {
                    forceReprintFlag = true
                    onRecordReprint(selectedOrder.orderId)
                    scope.launch {
                        val res = thermalPrinterService.printKitchenKot(kotDoc.copy(isReprint = true), paperWidth)
                        onPrinterFeedback(res)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kitchen Thermal Reprint (${paperWidth.label})", color = CraveGoldBright)
            }

            Text(
                text = "Kitchen Printer: ${uiState.printerStatus.driverName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
