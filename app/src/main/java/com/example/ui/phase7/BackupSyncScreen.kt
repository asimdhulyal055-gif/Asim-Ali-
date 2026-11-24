package com.example.ui.phase7

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.BackupSnapshotEntity
import com.example.data.local.RecoveryLogEntity
import com.example.data.local.SyncConflictEntity
import com.example.data.local.SyncQueueEntity
import com.example.domain.model.BackupIntegrityResult
import com.example.domain.model.CloudSyncPhase
import com.example.domain.model.ConflictResolutionStrategy
import com.example.domain.model.FirebaseCloudConstants
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
import com.example.ui.theme.StatusSky
import com.example.ui.theme.StatusSkyBg
import com.example.ui.viewmodel.BackupSyncTab
import com.example.ui.viewmodel.BackupSyncUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSyncScreen(
    session: UserSession,
    uiState: BackupSyncUiState,
    onOpenDrawer: () -> Unit,
    onSelectTab: (BackupSyncTab) -> Unit,
    onProbeConnection: () -> Unit,
    onStageAndSyncAll: () -> Unit,
    onRetryFailed: () -> Unit,
    onResolveConflict: (String, ConflictResolutionStrategy, String) -> Unit,
    onCreateBackup: (String, String, Boolean) -> Unit,
    onInspectBackup: (BackupSnapshotEntity) -> Unit,
    onExportBackup: (BackupSnapshotEntity) -> Unit,
    onImportBackupUri: (Uri) -> Unit,
    onDismissRestoreInspector: () -> Unit,
    onConfirmRestore: (String) -> Unit,
    onClearFeedback: () -> Unit
) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportBackupUri(uri)
    }

    var resolvingConflict by remember { mutableStateOf<SyncConflictEntity?>(null) }
    var showCreateBackupDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Open navigation menu",
                            tint = CraveGoldBright
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Cloud Sync & Backups",
                            style = MaterialTheme.typography.titleLarge,
                            color = CraveGoldBright
                        )
                        Text(
                            text = "${FirebaseCloudConstants.PROJECT_ID} • RTDB + Room Offline-First",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    StatusPill(
                        text = uiState.syncStatus.phase.label,
                        containerColor = phaseBgColor(uiState.syncStatus.phase),
                        contentColor = phaseTextColor(uiState.syncStatus.phase),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isWorking || uiState.syncStatus.isSyncing) {
                LinearProgressIndicator(
                    color = CraveGoldBright,
                    trackColor = ObsidianSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (uiState.feedbackMessage != null) {
                Surface(
                    color = if (uiState.isErrorFeedback) StatusCrimsonBg else StatusEmeraldBg,
                    border = BorderStroke(
                        1.dp,
                        if (uiState.isErrorFeedback) StatusCrimson.copy(alpha = 0.6f) else StatusEmerald.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.feedbackMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isErrorFeedback) StatusCrimson else StatusEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearFeedback) {
                            Text("DISMISS", color = CraveGoldBright)
                        }
                    }
                }
            }

            ScrollableTabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = ObsidianSurface,
                contentColor = CraveGoldBright,
                edgePadding = 12.dp
            ) {
                BackupSyncTab.entries.forEach { tab ->
                    val badgeCount = when (tab) {
                        BackupSyncTab.CLOUD_SYNC -> uiState.syncStatus.pendingCount + uiState.syncStatus.failedCount
                        BackupSyncTab.CONFLICTS -> uiState.syncStatus.openConflictCount
                        BackupSyncTab.BACKUPS -> uiState.backups.size
                        BackupSyncTab.RECOVERY_LOGS -> uiState.recoveryLogs.size
                    }
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}"),
                        text = {
                            Text(
                                text = if (badgeCount > 0) "${tab.label} ($badgeCount)" else tab.label,
                                fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            when (uiState.selectedTab) {
                BackupSyncTab.CLOUD_SYNC -> CloudSyncTabContent(
                    session = session,
                    uiState = uiState,
                    onProbeConnection = onProbeConnection,
                    onStageAndSyncAll = onStageAndSyncAll,
                    onRetryFailed = onRetryFailed
                )
                BackupSyncTab.CONFLICTS -> ConflictsTabContent(
                    session = session,
                    conflicts = uiState.conflicts,
                    onOpenResolve = { resolvingConflict = it }
                )
                BackupSyncTab.BACKUPS -> BackupsTabContent(
                    session = session,
                    backups = uiState.backups,
                    onOpenCreateBackup = { showCreateBackupDialog = true },
                    onImportBackupClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    onInspectBackup = onInspectBackup,
                    onExportBackup = onExportBackup
                )
                BackupSyncTab.RECOVERY_LOGS -> RecoveryLogsTabContent(logs = uiState.recoveryLogs)
            }
        }
    }

    resolvingConflict?.let { conflict ->
        ConflictResolutionDialog(
            session = session,
            conflict = conflict,
            onDismiss = { resolvingConflict = null },
            onConfirm = { strategy, notes ->
                onResolveConflict(conflict.conflictId, strategy, notes)
                resolvingConflict = null
            }
        )
    }

    if (showCreateBackupDialog) {
        CreateBackupDialog(
            session = session,
            isFirebaseReady = uiState.syncStatus.isFirebaseConfigured && uiState.syncStatus.isFirebaseAuthenticated,
            onDismiss = { showCreateBackupDialog = false },
            onCreate = { scope, notes, upload ->
                onCreateBackup(scope, notes, upload)
                showCreateBackupDialog = false
            }
        )
    }

    val integrity = uiState.integrityResult
    if (integrity != null) {
        RestoreInspectorDialog(
            session = session,
            label = uiState.importedBackupLabel ?: uiState.selectedBackupForRestore?.backupId ?: "Backup Snapshot",
            integrity = integrity,
            onDismiss = onDismissRestoreInspector,
            onConfirmRestore = onConfirmRestore
        )
    }
}

@Composable
private fun CloudSyncTabContent(
    session: UserSession,
    uiState: BackupSyncUiState,
    onProbeConnection: () -> Unit,
    onStageAndSyncAll: () -> Unit,
    onRetryFailed: () -> Unit
) {
    val status = uiState.syncStatus
    val formatter = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.45f)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (status.isNetworkOnline && status.isFirebaseConfigured) {
                                    Icons.Filled.CloudDone
                                } else {
                                    Icons.Filled.CloudOff
                                },
                                contentDescription = "Cloud Sync Status",
                                tint = phaseTextColor(status.phase)
                            )
                            Text(
                                text = "Firebase Realtime Sync Status",
                                style = MaterialTheme.typography.titleMedium,
                                color = CraveGoldBright
                            )
                        }
                        StatusPill(
                            text = status.phase.label,
                            containerColor = phaseBgColor(status.phase),
                            contentColor = phaseTextColor(status.phase)
                        )
                    }

                    Text(
                        text = status.lastResultMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = ObsidianBorder)

                    KeyValueLine("Project ID", status.projectId)
                    KeyValueLine("RTDB URL", status.databaseUrl)
                    KeyValueLine("google-services.json", if (status.isFirebaseConfigured) "Detected" else "Missing in app/")
                    KeyValueLine("Email/Password Auth", if (status.isFirebaseAuthenticated) "Signed In" else "Not Signed In")
                    KeyValueLine("Device Authorization", if (status.isDeviceAuthorized) "AUTHORIZED (${status.deviceId.take(10)})" else "REVOKED")
                    KeyValueLine(
                        "Last Cloud Sync",
                        status.lastSyncAt?.let { formatter.format(Date(it)) } ?: "Never synced to cloud"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SyncMetricBox("Pending", status.pendingCount.toString(), StatusAmber, Modifier.weight(1f))
                        SyncMetricBox("Failed", status.failedCount.toString(), StatusCrimson, Modifier.weight(1f))
                        SyncMetricBox("Conflicts", status.openConflictCount.toString(), StatusCrimson, Modifier.weight(1f))
                        SyncMetricBox("Synced", status.syncedRecordCount.toString(), StatusEmerald, Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onStageAndSyncAll,
                            enabled = !uiState.isWorking && session.capabilities.canTriggerCloudSync,
                            colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("sync_now_button")
                        ) {
                            Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stage & Sync")
                        }
                        OutlinedButton(
                            onClick = onProbeConnection,
                            enabled = !uiState.isWorking,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("probe_firebase_button")
                        ) {
                            Text("Probe RTDB")
                        }
                    }

                    if (status.failedCount > 0) {
                        OutlinedButton(
                            onClick = onRetryFailed,
                            enabled = !uiState.isWorking,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("retry_failed_sync_button")
                        ) {
                            Text("Retry ${status.failedCount} Failed Sync Transactions")
                        }
                    }
                }
            }
        }

        uiState.probeResult?.let { probe ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Latest Firebase Diagnostic Probe",
                            style = MaterialTheme.typography.labelLarge,
                            color = CraveGoldBright
                        )
                        probe.details.forEach { detail ->
                            Text(
                                text = "• $detail",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "SYNC TRANSACTION QUEUE (${uiState.syncQueue.size})",
                style = MaterialTheme.typography.labelSmall,
                color = CraveGold
            )
        }

        if (uiState.syncQueue.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Sync Queue Empty",
                    subtitle = "Tap Stage & Sync to snapshot modified branch records into the Room outbox or create POS/Accounting transactions."
                )
            }
        } else {
            items(uiState.syncQueue, key = { it.transactionId }) { item ->
                SyncQueueCard(item = item, formatter = formatter)
            }
        }
    }
}

@Composable
private fun SyncQueueCard(item: SyncQueueEntity, formatter: SimpleDateFormat) {
    val pillColor = when {
        item.status == "SYNCED" -> StatusEmeraldBg to StatusEmerald
        item.status == "PENDING" -> StatusAmberBg to StatusAmber
        else -> StatusCrimsonBg to StatusCrimson
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.entityType} • ${item.entityId}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusPill(
                    text = item.status,
                    containerColor = pillColor.first,
                    contentColor = pillColor.second
                )
            }
            Text(
                text = "Branch: ${item.branchId} • Tx: ${item.transactionId.take(24)} • Retries: ${item.retryCount} • ${formatter.format(Date(item.createdAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!item.lastError.isNullOrBlank()) {
                Text(
                    text = item.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusCrimson
                )
            }
        }
    }
}

@Composable
private fun ConflictsTabContent(
    session: UserSession,
    conflicts: List<SyncConflictEntity>,
    onOpenResolve: (SyncConflictEntity) -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.4f)),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "Financial & operational divergence protection: conflicting POS Orders, Invoices, Payroll, and Accounting Journals are quarantined automatically instead of silently overwritten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (conflicts.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Multi-Device Conflicts",
                    subtitle = "All local and cloud ERP records are consistent."
                )
            }
        } else {
            items(conflicts, key = { it.conflictId }) { conflict ->
                val isOpen = conflict.status == "OPEN"
                val canResolveThis = session.capabilities.canResolveSyncConflicts &&
                    (!conflict.isFinancialProtected || session.capabilities.canResolveFinancialConflicts)
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, if (isOpen) StatusCrimson.copy(alpha = 0.7f) else ObsidianBorder),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conflict.recordLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CraveGoldBright
                                )
                                Text(
                                    text = "Branch: ${conflict.branchId} • Detected ${formatter.format(Date(conflict.createdAt))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                text = if (conflict.isFinancialProtected) "FINANCIAL • ${conflict.status}" else conflict.status,
                                containerColor = if (isOpen) StatusCrimsonBg else StatusEmeraldBg,
                                contentColor = if (isOpen) StatusCrimson else StatusEmerald
                            )
                        }

                        HorizontalDivider(color = ObsidianBorder)

                        Text(
                            text = "LOCAL (v${conflict.localVersion} • ${conflict.localActorName}): ${conflict.localSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "CLOUD (v${conflict.remoteVersion} • ${conflict.remoteActorName}): ${conflict.remoteSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber
                        )

                        if (isOpen) {
                            Button(
                                onClick = { onOpenResolve(conflict) },
                                enabled = canResolveThis,
                                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("resolve_conflict_${conflict.conflictId}")
                            ) {
                                Text(
                                    if (canResolveThis) "Review & Resolve Conflict"
                                    else "Owner / Super Admin Required for Financial Conflict"
                                )
                            }
                        } else {
                            Text(
                                text = "Resolved by ${conflict.resolvedByName ?: "Admin"}: ${conflict.resolutionNotes}",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusEmerald
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictResolutionDialog(
    session: UserSession,
    conflict: SyncConflictEntity,
    onDismiss: () -> Unit,
    onConfirm: (ConflictResolutionStrategy, String) -> Unit
) {
    var selectedStrategy by remember { mutableStateOf(ConflictResolutionStrategy.KEEP_BOTH_ADJUSTED) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text(
                text = "Resolve ${conflict.entityType} Conflict",
                color = CraveGoldBright
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Local v${conflict.localVersion} (${conflict.localDeviceId.take(8)}): ${conflict.localSummary}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Cloud v${conflict.remoteVersion} (${conflict.remoteDeviceId.take(8)}): ${conflict.remoteSummary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber
                )
                HorizontalDivider(color = ObsidianBorder)
                Text("Select Resolution Strategy", style = MaterialTheme.typography.labelLarge, color = CraveGold)
                ConflictResolutionStrategy.entries.forEach { strat ->
                    OutlinedButton(
                        onClick = { selectedStrategy = strat },
                        border = BorderStroke(
                            1.dp,
                            if (selectedStrategy == strat) CraveGold else ObsidianBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (selectedStrategy == strat) "✓ ${strat.label}" else strat.label,
                            color = if (selectedStrategy == strat) CraveGoldBright else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Audit Reason (required)") },
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (conflict.isFinancialProtected && !session.capabilities.canResolveFinancialConflicts) {
                    Text(
                        text = "Only Owner or Super Admin can approve financial conflict resolution.",
                        color = StatusCrimson,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedStrategy, notes) },
                enabled = notes.trim().length >= 4 &&
                    (!conflict.isFinancialProtected || session.capabilities.canResolveFinancialConflicts),
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) {
                Text("Apply Resolution")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BackupsTabContent(
    session: UserSession,
    backups: List<BackupSnapshotEntity>,
    onOpenCreateBackup: () -> Unit,
    onImportBackupClick: () -> Unit,
    onInspectBackup: (BackupSnapshotEntity) -> Unit,
    onExportBackup: (BackupSnapshotEntity) -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenCreateBackup,
                    enabled = session.capabilities.canCreateBackup,
                    colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("create_backup_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Backup")
                }
                OutlinedButton(
                    onClick = onImportBackupClick,
                    enabled = session.capabilities.canRestoreBackup,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("import_backup_button")
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import & Verify")
                }
            }
        }

        if (backups.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Backup Snapshots Yet",
                    subtitle = "Create a full SHA-256 verified JSON snapshot of branches, POS, menu, inventory, accounting, and HR tables."
                )
            }
        } else {
            items(backups, key = { it.backupId }) { bkp ->
                val exists = remember(bkp.localFilePath) { File(bkp.localFilePath).exists() }
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bkp.backupId,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CraveGoldBright,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatter.format(Date(bkp.createdAt))} • By ${bkp.createdByName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                text = bkp.cloudStatus,
                                containerColor = if (bkp.cloudStatus == "UPLOADED") StatusEmeraldBg else StatusSkyBg,
                                contentColor = if (bkp.cloudStatus == "UPLOADED") StatusEmerald else StatusSky
                            )
                        }
                        Text(
                            text = "Records: ${bkp.recordCount} • Scope: ${bkp.branchScope} • SHA-256: ${bkp.checksumSha256.take(16)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (bkp.notes.isNotBlank()) {
                            Text(
                                text = "Notes: ${bkp.notes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onExportBackup(bkp) },
                                enabled = exists,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("export_backup_${bkp.backupId}")
                            ) {
                                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export")
                            }
                            Button(
                                onClick = { onInspectBackup(bkp) },
                                enabled = exists && session.capabilities.canRestoreBackup,
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceElevated, contentColor = CraveGoldBright),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("verify_restore_${bkp.backupId}")
                            ) {
                                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verify / Restore")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateBackupDialog(
    session: UserSession,
    isFirebaseReady: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, Boolean) -> Unit
) {
    var notes by remember { mutableStateOf("") }
    var uploadToCloud by remember { mutableStateOf(isFirebaseReady) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("Create ERP Backup Snapshot", color = CraveGoldBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Exports all Room ERP tables into a SHA-256 verified JSON envelope stored locally" +
                        if (isFirebaseReady) " and optionally synced to aa-crave-erp RTDB." else ".",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Snapshot Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isFirebaseReady) "Upload metadata to Firebase RTDB" else "Firebase not signed in (Local snapshot)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uploadToCloud,
                        onCheckedChange = { uploadToCloud = it },
                        enabled = isFirebaseReady
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(session.branchId, notes, uploadToCloud) },
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold)
            ) {
                Text("Create Snapshot")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RestoreInspectorDialog(
    session: UserSession,
    label: String,
    integrity: BackupIntegrityResult,
    onDismiss: () -> Unit,
    onConfirmRestore: (String) -> Unit
) {
    var confirmationInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (integrity.isValid) Icons.Filled.VerifiedUser else Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = if (integrity.isValid) StatusEmerald else StatusCrimson
                )
                Text(
                    text = if (integrity.isValid) "Integrity Verified" else "Integrity Rejected",
                    color = if (integrity.isValid) StatusEmerald else StatusCrimson
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Snapshot: $label", style = MaterialTheme.typography.titleSmall, color = CraveGoldBright)
                Text(
                    text = "Records: ${integrity.totalRecords} • Schema v${integrity.schemaVersion} • Scope: ${integrity.branchScope}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "SHA-256 Match: ${if (integrity.checksumMatch) "VERIFIED" else "MISMATCH"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (integrity.checksumMatch) StatusEmerald else StatusCrimson
                )
                if (integrity.errorMessage != null) {
                    Text(
                        text = integrity.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusCrimson
                    )
                } else {
                    Text(
                        text = "Warning: Restoring replaces local Room tables with this snapshot. Type RESTORE below to confirm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber
                    )
                    OutlinedTextField(
                        value = confirmationInput,
                        onValueChange = { confirmationInput = it },
                        label = { Text("Type RESTORE to confirm") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_confirmation_input")
                    )
                }
            }
        },
        confirmButton = {
            if (integrity.isValid && session.capabilities.canRestoreBackup) {
                Button(
                    onClick = { onConfirmRestore(confirmationInput) },
                    enabled = confirmationInput.trim() == "RESTORE",
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson, contentColor = ObsidianBg),
                    modifier = Modifier.testTag("confirm_restore_button")
                ) {
                    Text("Restore ERP Data")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun RecoveryLogsTabContent(logs: List<RecoveryLogEntity>) {
    val formatter = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (logs.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Recovery Events",
                    subtitle = "Backup creation, SHA-256 checks, and restore operations will appear here."
                )
            }
        } else {
            items(logs, key = { it.recoveryId }) { log ->
                val ok = log.status == "SUCCESS"
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${log.actionType} • ${log.backupId}",
                                style = MaterialTheme.typography.titleSmall,
                                color = CraveGoldBright,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            StatusPill(
                                text = log.status,
                                containerColor = if (ok) StatusEmeraldBg else StatusCrimsonBg,
                                contentColor = if (ok) StatusEmerald else StatusCrimson
                            )
                        }
                        Text(
                            text = log.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${log.actorName} • ${formatter.format(Date(log.createdAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncMetricBox(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = ObsidianSurfaceElevated,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun KeyValueLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyStateCard(title: String, subtitle: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianBorder),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun phaseBgColor(phase: CloudSyncPhase) = when (phase) {
    CloudSyncPhase.ONLINE_READY -> StatusEmeraldBg
    CloudSyncPhase.SYNCING -> StatusSkyBg
    CloudSyncPhase.OFFLINE, CloudSyncPhase.CONFIG_MISSING, CloudSyncPhase.AUTH_REQUIRED -> StatusAmberBg
    CloudSyncPhase.DEVICE_REVOKED, CloudSyncPhase.LOCKED_RULES, CloudSyncPhase.ERROR -> StatusCrimsonBg
}

private fun phaseTextColor(phase: CloudSyncPhase) = when (phase) {
    CloudSyncPhase.ONLINE_READY -> StatusEmerald
    CloudSyncPhase.SYNCING -> StatusSky
    CloudSyncPhase.OFFLINE, CloudSyncPhase.CONFIG_MISSING, CloudSyncPhase.AUTH_REQUIRED -> StatusAmber
    CloudSyncPhase.DEVICE_REVOKED, CloudSyncPhase.LOCKED_RULES, CloudSyncPhase.ERROR -> StatusCrimson
}
