package com.example.ui.kds

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.KdsTicketSummary
import com.example.domain.model.KitchenStation
import com.example.domain.model.KotStatus
import com.example.domain.model.OrderPriority
import com.example.domain.model.OrderType
import com.example.domain.model.UserSession
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
import com.example.ui.viewmodel.KdsStageFilter
import com.example.ui.viewmodel.KdsUiState
import kotlinx.coroutines.delay
import java.util.Locale

private val PreparingBlue = Color(0xFF38BDF8)
private val PreparingBlueBg = Color(0xFF0E2B3D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KdsScreen(
    session: UserSession,
    uiState: KdsUiState,
    onOpenDrawer: () -> Unit,
    onSelectStation: (KitchenStation) -> Unit,
    onSelectStageFilter: (KdsStageFilter) -> Unit,
    onToggleSound: () -> Unit,
    onUpdateStatus: (String, KotStatus) -> Unit,
    onOpenKotSlip: (String) -> Unit,
    onClearFeedback: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var initializedTicketFeed by remember { mutableStateOf(false) }
    var seenNewKotIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var incomingAlertBanner by remember { mutableStateOf<String?>(null) }

    // 1-second live order timer ticker
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    // Detect newly arriving NEW kitchen orders for sound/visual notification
    LaunchedEffect(uiState.allTickets) {
        val currentNewTickets = uiState.allTickets.filter { it.status == KotStatus.NEW }
        val currentIds = currentNewTickets.map { it.kotId }.toSet()
        if (!initializedTicketFeed) {
            seenNewKotIds = currentIds
            initializedTicketFeed = true
        } else {
            val brandNew = currentNewTickets.filter { it.kotId !in seenNewKotIds }
            if (brandNew.isNotEmpty()) {
                val latest = brandNew.first()
                incomingAlertBanner = "NEW ORDER: ${latest.kotNumber} (${latest.orderNumber}) • ${latest.station.label}"
                if (uiState.soundEnabled) {
                    playKdsAlertChime()
                }
            }
            seenNewKotIds = seenNewKotIds + currentIds
        }
    }

    LaunchedEffect(incomingAlertBanner) {
        if (incomingAlertBanner != null) {
            delay(5_000L)
            incomingAlertBanner = null
        }
    }

    LaunchedEffect(uiState.feedbackMessage) {
        val msg = uiState.feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = CraveGoldBright)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "KITCHEN DISPLAY (KDS)",
                            style = MaterialTheme.typography.titleLarge,
                            color = CraveGoldBright
                        )
                        Text(
                            text = "${session.branchName} • Live Station Sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onToggleSound,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("kds_sound_toggle")
                    ) {
                        Icon(
                            imageVector = if (uiState.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (uiState.soundEnabled) "Mute KDS alerts" else "Enable KDS sound alerts",
                            tint = if (uiState.soundEnabled) CraveGoldBright else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(visible = incomingAlertBanner != null) {
                Surface(
                    color = CraveGoldContainer,
                    border = BorderStroke(1.dp, CraveGoldBright),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = "New order alert",
                            tint = CraveGoldBright
                        )
                        Text(
                            text = incomingAlertBanner.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = CraveGoldBright,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Performance KPI strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KdsMetricChip("New", uiState.performance.newCount.toString(), CraveGoldBright, CraveGoldContainer)
                KdsMetricChip("Preparing", uiState.performance.preparingCount.toString(), PreparingBlue, PreparingBlueBg)
                KdsMetricChip("Ready", uiState.performance.readyCount.toString(), StatusEmerald, StatusEmeraldBg)
                KdsMetricChip("Completed", uiState.performance.completedCount.toString(), MaterialTheme.colorScheme.onSurface, ObsidianSurfaceElevated)
                KdsMetricChip("Avg Prep", "${uiState.performance.avgPrepMinutes}m", StatusAmber, StatusAmberBg)
            }

            // Station selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KitchenStation.entries.forEach { station ->
                    val count = if (station == KitchenStation.ALL) {
                        uiState.allTickets.count { it.status != KotStatus.COMPLETED && it.status != KotStatus.CANCELLED }
                    } else {
                        uiState.allTickets.count {
                            it.station == station && it.status != KotStatus.COMPLETED && it.status != KotStatus.CANCELLED
                        }
                    }
                    FilterChip(
                        selected = uiState.selectedStation == station,
                        onClick = { onSelectStation(station) },
                        label = { Text("${station.label} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CraveGoldContainer,
                            selectedLabelColor = CraveGoldBright
                        ),
                        modifier = Modifier.testTag("kds_station_${station.code}")
                    )
                }
            }

            // Stage selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KdsStageFilter.entries.forEach { stage ->
                    FilterChip(
                        selected = uiState.stageFilter == stage,
                        onClick = { onSelectStageFilter(stage) },
                        label = { Text(stage.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CraveGold,
                            selectedLabelColor = OnCraveGold
                        ),
                        modifier = Modifier.testTag("kds_stage_${stage.name}")
                    )
                }
            }

            HorizontalDivider(color = ObsidianBorder)

            if (uiState.filteredTickets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Restaurant,
                            contentDescription = null,
                            tint = CraveGold.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No kitchen tickets in ${uiState.stageFilter.label}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Confirmed POS orders automatically route KOTs to assigned kitchen stations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.filteredTickets, key = { it.kotId }) { ticket ->
                        KdsTicketCard(
                            ticket = ticket,
                            nowMillis = nowMillis,
                            canOperate = session.capabilities.canOperateKds && !uiState.isUpdating,
                            onAdvance = { next -> onUpdateStatus(ticket.kotId, next) },
                            onOpenKotSlip = { onOpenKotSlip(ticket.orderId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KdsMetricChip(
    label: String,
    value: String,
    accentColor: Color,
    containerColor: Color
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

@Composable
private fun KdsTicketCard(
    ticket: KdsTicketSummary,
    nowMillis: Long,
    canOperate: Boolean,
    onAdvance: (KotStatus) -> Unit,
    onOpenKotSlip: () -> Unit
) {
    val endTimestamp = ticket.completedAt ?: nowMillis
    val elapsedSeconds = ((endTimestamp - ticket.createdAt).coerceAtLeast(0L) / 1000L)
    val mins = elapsedSeconds / 60L
    val secs = elapsedSeconds % 60L
    val timerText = String.format(Locale.US, "%02d:%02d", mins, secs)

    val isOverdue = ticket.status != KotStatus.COMPLETED && mins >= 15L
    val isWarning = ticket.status != KotStatus.COMPLETED && mins in 10L..14L

    val borderColor = when {
        ticket.priority == OrderPriority.RUSH || ticket.priority == OrderPriority.VIP -> StatusCrimson
        ticket.status == KotStatus.NEW -> CraveGold
        ticket.status == KotStatus.PREPARING -> PreparingBlue
        ticket.status == KotStatus.READY -> StatusEmerald
        else -> ObsidianBorder
    }

    val (statusBg, statusFg) = when (ticket.status) {
        KotStatus.NEW -> CraveGoldContainer to CraveGoldBright
        KotStatus.PREPARING -> PreparingBlueBg to PreparingBlue
        KotStatus.READY -> StatusEmeraldBg to StatusEmerald
        KotStatus.COMPLETED -> ObsidianSurfaceElevated to MaterialTheme.colorScheme.onSurfaceVariant
        KotStatus.CANCELLED -> StatusCrimsonBg to StatusCrimson
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.5.dp, borderColor),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kds_ticket_${ticket.kotId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: KOT#, Order#, Status, Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = ticket.kotNumber,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = CraveGoldBright
                        )
                        StatusPill(
                            text = ticket.status.label.uppercase(),
                            containerColor = statusBg,
                            contentColor = statusFg
                        )
                    }
                    Text(
                        text = "${ticket.orderNumber} • ${ticket.station.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = when {
                        isOverdue -> StatusCrimsonBg
                        isWarning -> StatusAmberBg
                        else -> ObsidianSurfaceElevated
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        when {
                            isOverdue -> StatusCrimson
                            isWarning -> StatusAmber
                            else -> ObsidianBorder
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = "Elapsed prep timer",
                            tint = when {
                                isOverdue -> StatusCrimson
                                isWarning -> StatusAmber
                                else -> CraveGoldBright
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = timerText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isOverdue -> StatusCrimson
                                isWarning -> StatusAmber
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            // Order Type, Table, Priority
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val serviceBadge = if (ticket.orderType == OrderType.DINE_IN && !ticket.tableNumber.isNullOrBlank()) {
                    "DINE-IN • ${ticket.tableNumber}"
                } else {
                    ticket.orderType.label.uppercase()
                }
                StatusPill(
                    text = serviceBadge,
                    containerColor = ObsidianSurfaceElevated,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
                if (ticket.priority != OrderPriority.NORMAL) {
                    StatusPill(
                        text = "${ticket.priority.label.uppercase()} PRIORITY",
                        containerColor = StatusCrimsonBg,
                        contentColor = StatusCrimson
                    )
                }
            }

            HorizontalDivider(color = ObsidianBorder)

            // Kitchen Line Items (Zero financial/payment details)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ticket.items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = CraveGoldContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${item.quantity}x",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CraveGoldBright,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = buildString {
                                    append(item.productName)
                                    if (!item.variationName.isNullOrBlank()) {
                                        append(" (${item.variationName})")
                                    }
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (item.addonsSummary.isNotBlank()) {
                            Text(
                                text = "   + ${item.addonsSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CraveGold
                            )
                        }
                        if (item.specialInstructions.isNotBlank()) {
                            Surface(
                                color = StatusAmberBg,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 2.dp)
                            ) {
                                Text(
                                    text = "NOTE: ${item.specialInstructions}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = ObsidianBorder)

            // Stage Transition Actions + KOT Slip link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenKotSlip,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Filled.Print, contentDescription = "Open KOT slip", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("KOT Slip")
                }

                val nextStage = ticket.status.nextStage()
                if (nextStage != null && canOperate) {
                    val (btnLabel, btnIcon, btnBg, btnFg) = when (nextStage) {
                        KotStatus.PREPARING -> Quad("Start Prep", Icons.Filled.PlayArrow, CraveGold, OnCraveGold)
                        KotStatus.READY -> Quad("Mark Ready", Icons.Filled.LocalFireDepartment, PreparingBlue, ObsidianBg)
                        KotStatus.COMPLETED -> Quad("Complete", Icons.Filled.CheckCircle, StatusEmerald, ObsidianBg)
                        else -> Quad("Update", Icons.Filled.CheckCircle, CraveGold, OnCraveGold)
                    }
                    Button(
                        onClick = { onAdvance(nextStage) },
                        colors = ButtonDefaults.buttonColors(containerColor = btnBg, contentColor = btnFg),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("kds_advance_${ticket.kotId}")
                    ) {
                        Icon(btnIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(btnLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun playKdsAlertChime() {
    try {
        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 260)
        toneGen.release()
    } catch (_: Throwable) {
        // Audio hardware may be unavailable in headless test environments
    }
}
