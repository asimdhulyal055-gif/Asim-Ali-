package com.example.ui.loyalty

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.model.LoyaltyRewardVoucherRecord
import com.example.domain.model.LoyaltyVoucherStatus
import com.example.domain.model.UserSession
import com.example.domain.model.centsToPkr
import com.example.printing.QrCodeHelper
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
import com.example.ui.viewmodel.SmartLoyaltyUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun QrCodeCameraScannerDialog(
    title: String = "Scan AA CRAVE Loyalty QR",
    onDismiss: () -> Unit,
    onCodeCaptured: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var manualCode by remember { mutableStateOf("") }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var scanLocked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = CraveGoldBright)
                Text(title, color = CraveGoldBright)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(Color.Black, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        runCatching {
                                            val provider = cameraProviderFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.surfaceProvider = surfaceProvider
                                            }
                                            val analysis = ImageAnalysis.Builder()
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()
                                                .also { imageAnalysis ->
                                                    imageAnalysis.setAnalyzer(analyzerExecutor) { proxy ->
                                                        try {
                                                            val decoded = QrCodeHelper.decodeQrFromImageProxy(proxy)
                                                            if (decoded != null && !scanLocked) {
                                                                scanLocked = true
                                                                post {
                                                                    onCodeCaptured(decoded)
                                                                }
                                                            }
                                                        } finally {
                                                            proxy.close()
                                                        }
                                                    }
                                                }
                                            provider.unbindAll()
                                            provider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                preview,
                                                analysis
                                            )
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = Color.Transparent,
                            border = BorderStroke(2.dp, CraveGoldBright),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.size(140.dp)
                        ) {}
                    }
                    Text(
                        "Align the AA CRAVE Card QR or Reward Voucher QR inside the gold frame.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Surface(
                        color = ObsidianBg,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = CraveGoldBright, modifier = Modifier.size(32.dp))
                            Text(
                                "Camera access is needed to scan QR codes directly, or enter a card/voucher code below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { launcher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                                modifier = Modifier.testTag("loyalty_request_camera_button")
                            ) {
                                Text("Enable Camera Scanner")
                            }
                        }
                    }
                }

                HorizontalDivider(color = ObsidianBorder)

                OutlinedTextField(
                    value = manualCode,
                    onValueChange = { manualCode = it },
                    label = { Text("Or Type Card # / QR / Voucher / Phone") },
                    placeholder = { Text("e.g. AAC-0001 or AACLC-... or RWD-...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("loyalty_scanner_manual_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val code = manualCode.trim()
                    if (code.isNotBlank()) onCodeCaptured(code)
                },
                enabled = manualCode.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                modifier = Modifier.testTag("loyalty_scanner_lookup_button")
            ) {
                Text("Lookup Code")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun LoyaltyQrLookupTab(
    uiState: SmartLoyaltyUiState,
    onOpenScannerDialog: () -> Unit,
    onLookupCode: (String) -> Unit,
    onOpenCardProfile: (String) -> Unit
) {
    var queryText by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Instant Card & Voucher Verification",
                    style = MaterialTheme.typography.titleMedium,
                    color = CraveGoldBright
                )
                Text(
                    "Scan a member QR code via camera or look up by Card Number, Voucher Code, Customer Phone, or Name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onOpenScannerDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("loyalty_open_camera_scanner_button")
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scan Loyalty / Voucher QR with Camera")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        label = { Text("Card #, QR Token, Voucher Code, or Phone") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("loyalty_quick_lookup_input")
                    )
                    Button(
                        onClick = { onLookupCode(queryText) },
                        enabled = queryText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CraveGoldContainer, contentColor = CraveGoldBright),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("loyalty_quick_lookup_button")
                    ) {
                        Text("Find")
                    }
                }
            }
        }

        uiState.scannedVoucher?.let { v ->
            VoucherVerificationCard(
                voucher = v,
                onOpenCardProfile = { onOpenCardProfile(v.customerId) }
            )
        }

        if (uiState.cards.isNotEmpty()) {
            Text("Quick Card Directory", style = MaterialTheme.typography.titleSmall, color = CraveGold)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.cards.take(15), key = { it.cardId }) { card ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCardProfile(card.customerId) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${card.cardNumber} • ${card.customerName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CraveGoldBright
                                )
                                Text(
                                    "${card.customerPhone} • Stamps ${card.currentCycleStamps}/${card.requiredStamps} • Cycle #${card.cycleCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                text = card.effectiveStatus.label,
                                containerColor = cardStatusBgColor(card.effectiveStatus),
                                contentColor = cardStatusColor(card.effectiveStatus)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoyaltyVouchersTab(
    session: UserSession,
    uiState: SmartLoyaltyUiState,
    onOpenMemberCard: (String) -> Unit,
    onApplyVoucherInPos: (LoyaltyRewardVoucherRecord) -> Unit
) {
    var statusFilter by remember { mutableStateOf<LoyaltyVoucherStatus?>(null) }
    var search by remember { mutableStateOf("") }
    var selectedVoucher by remember { mutableStateOf<LoyaltyRewardVoucherRecord?>(null) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    val filtered = remember(uiState.vouchers, statusFilter, search) {
        val q = search.trim()
        uiState.vouchers.filter { v ->
            val matchStatus = statusFilter == null || v.effectiveStatus == statusFilter
            val matchQuery = q.isBlank() ||
                v.voucherCode.contains(q, ignoreCase = true) ||
                v.customerName.contains(q, ignoreCase = true) ||
                v.cardNumber.contains(q, ignoreCase = true)
            matchStatus && matchQuery
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search voucher code, member, or card #...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotBlank()) {
                        IconButton(onClick = { search = "" }, modifier = Modifier.minimumInteractiveComponentSize()) {
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
                    selected = statusFilter == null,
                    onClick = { statusFilter = null },
                    label = { Text("All (${uiState.vouchers.size})") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                )
                LoyaltyVoucherStatus.entries.forEach { st ->
                    val count = uiState.vouchers.count { it.effectiveStatus == st }
                    FilterChip(
                        selected = statusFilter == st,
                        onClick = { statusFilter = st },
                        label = { Text("${st.label} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CraveGoldContainer, selectedLabelColor = CraveGoldBright)
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = CraveGold, modifier = Modifier.size(40.dp))
                    Text(
                        "No reward vouchers yet.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Vouchers are automatically unlocked when a member completes 6 qualifying paid orders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.voucherId }) { v ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedVoucher = v }
                            .testTag("loyalty_voucher_${v.voucherCode}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(v.voucherCode, style = MaterialTheme.typography.titleMedium, color = CraveGoldBright, fontWeight = FontWeight.Bold)
                                    Text("${v.customerName} • ${v.cardNumber} • Cycle #${v.cycleNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill(
                                    text = v.effectiveStatus.label,
                                    containerColor = voucherStatusBg(v.effectiveStatus),
                                    contentColor = voucherStatusColor(v.effectiveStatus)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Cap Value: ${centsToPkr(v.rewardValueCents)}", style = MaterialTheme.typography.bodyMedium, color = CraveGold)
                                Text("Valid till: ${dateFmt.format(Date(v.graceExpiresAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (v.effectiveStatus == LoyaltyVoucherStatus.REDEEMED) {
                                Text(
                                    "Redeemed ${centsToPkr(v.redeemedAmountCents)} on ${v.redeemedInvoiceNumber ?: "Invoice"} (Actual COGS: ${centsToPkr(v.actualPromoCostCents)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusEmerald
                                )
                            }
                            if (!v.cancelledReason.isNullOrBlank()) {
                                Text(v.cancelledReason, style = MaterialTheme.typography.labelSmall, color = StatusCrimson)
                            }
                        }
                    }
                }
            }
        }
    }

    selectedVoucher?.let { voucher ->
        VoucherRedemptionPreviewDialog(
            session = session,
            voucher = voucher,
            onDismiss = { selectedVoucher = null },
            onOpenMemberCard = {
                selectedVoucher = null
                onOpenMemberCard(voucher.customerId)
            },
            onApplyInPos = {
                selectedVoucher = null
                onApplyVoucherInPos(voucher)
            }
        )
    }
}

@Composable
private fun VoucherVerificationCard(
    voucher: LoyaltyRewardVoucherRecord,
    onOpenCardProfile: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val qrBmp = remember(voucher.qrCodeToken) { QrCodeHelper.generateQrBitmap(voucher.qrCodeToken, 240) }
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
        border = BorderStroke(1.dp, CraveGoldBright),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (qrBmp != null) {
                Surface(color = Color.White, shape = MaterialTheme.shapes.small) {
                    Image(
                        bitmap = qrBmp.asImageBitmap(),
                        contentDescription = "Voucher QR ${voucher.voucherCode}",
                        modifier = Modifier.size(72.dp).padding(4.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(voucher.voucherCode, style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                Text("${voucher.customerName} • Max ${centsToPkr(voucher.rewardValueCents)}", style = MaterialTheme.typography.bodySmall)
                Text("Redeem before ${dateFmt.format(Date(voucher.graceExpiresAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusPill(
                    text = voucher.effectiveStatus.label,
                    containerColor = voucherStatusBg(voucher.effectiveStatus),
                    contentColor = voucherStatusColor(voucher.effectiveStatus)
                )
            }
            TextButton(onClick = onOpenCardProfile) { Text("Profile") }
        }
    }
}

@Composable
private fun VoucherRedemptionPreviewDialog(
    session: UserSession,
    voucher: LoyaltyRewardVoucherRecord,
    onDismiss: () -> Unit,
    onOpenMemberCard: () -> Unit,
    onApplyInPos: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US) }
    val qrBmp = remember(voucher.qrCodeToken) { QrCodeHelper.generateQrBitmap(voucher.qrCodeToken, 320) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(voucher.voucherCode, color = CraveGoldBright)
                StatusPill(
                    text = voucher.effectiveStatus.label,
                    containerColor = voucherStatusBg(voucher.effectiveStatus),
                    contentColor = voucherStatusColor(voucher.effectiveStatus)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (qrBmp != null) {
                    Surface(color = Color.White, shape = MaterialTheme.shapes.medium) {
                        Image(
                            bitmap = qrBmp.asImageBitmap(),
                            contentDescription = "QR for voucher ${voucher.voucherCode}",
                            modifier = Modifier.size(160.dp).padding(8.dp)
                        )
                    }
                }
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Member: ${voucher.customerName} (${voucher.cardNumber})", style = MaterialTheme.typography.bodyMedium)
                    Text("Reward Value Cap: ${centsToPkr(voucher.rewardValueCents)}", style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
                    Text("Issued: ${dateFmt.format(Date(voucher.issuedAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Card Cycle Expiry: ${dateFmt.format(Date(voucher.cardExpiresAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Grace Deadline: ${dateFmt.format(Date(voucher.graceExpiresAt))}", style = MaterialTheme.typography.bodySmall, color = StatusAmber)
                    if (voucher.redeemedInvoiceNumber != null) {
                        Text(
                            "Redeemed on ${voucher.redeemedInvoiceNumber}: Discount ${centsToPkr(voucher.redeemedAmountCents)} • Actual COGS ${centsToPkr(voucher.actualPromoCostCents)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusEmerald
                        )
                    }
                    Text(
                        "One-time redemption only. No cash-out if bill total is below voucher cap.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                if (voucher.isRedeemableNow && session.capabilities.canScanAndRedeemLoyalty) {
                    Button(
                        onClick = onApplyInPos,
                        colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                        modifier = Modifier.fillMaxWidth().testTag("voucher_apply_in_pos_button")
                    ) {
                        Icon(Icons.Filled.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Attach Voucher & Checkout in POS")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onOpenMemberCard) { Text("View Member Card") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    )
}

internal fun voucherStatusColor(status: LoyaltyVoucherStatus): Color = when (status) {
    LoyaltyVoucherStatus.ACTIVE -> StatusEmerald
    LoyaltyVoucherStatus.REDEEMED -> CraveGoldBright
    LoyaltyVoucherStatus.EXPIRED -> StatusAmber
    LoyaltyVoucherStatus.CANCELLED -> StatusCrimson
}

internal fun voucherStatusBg(status: LoyaltyVoucherStatus): Color = when (status) {
    LoyaltyVoucherStatus.ACTIVE -> StatusEmeraldBg
    LoyaltyVoucherStatus.REDEEMED -> CraveGoldContainer
    LoyaltyVoucherStatus.EXPIRED -> StatusAmberBg
    LoyaltyVoucherStatus.CANCELLED -> StatusCrimsonBg
}
