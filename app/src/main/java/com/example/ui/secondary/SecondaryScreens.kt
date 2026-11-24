package com.example.ui.secondary

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AuditLogEntity
import com.example.domain.model.BranchSummary
import com.example.domain.model.CloudProbeResult
import com.example.domain.model.FirebaseCloudConstants
import com.example.domain.model.FirebaseEnvironmentStatus
import com.example.domain.model.RoleAccessControl
import com.example.domain.model.UserSession
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
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusEmeraldBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecondaryScreenScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    BackHandler(onBack = onBack)

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
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("secondary_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to dashboard",
                            tint = CraveGoldBright
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = CraveGoldBright
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        content = content
    )
}

@Composable
fun ProfileScreen(
    session: UserSession,
    logoState: com.example.data.brand.BrandLogoState = com.example.data.brand.BrandLogoState(hasOwnerLogo = false),
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    val capabilities = session.capabilities
    val activeModules = RoleAccessControl.activePhase1Modules(session.role)

    SecondaryScreenScaffold(
        title = "User Profile & Session",
        subtitle = "Role-Based Access Control Matrix",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AaCraveBrandLogo(size = 56.dp, logoState = logoState, showPlaceholderLabel = false)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = session.email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(color = ObsidianBorder)
                        ProfileMetaRow("Assigned Role", session.role.displayName)
                        ProfileMetaRow("Department", session.role.department)
                        ProfileMetaRow("Active Branch", session.branchName)
                        ProfileMetaRow("Operator UID", session.uid)
                        ProfileMetaRow(
                            "Authentication",
                            if (session.isFirebaseAuthenticated) "Firebase Email/Password" else "Local Sandbox Preview"
                        )
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Security, contentDescription = null, tint = CraveGold)
                            Text(
                                text = "Granted RBAC Capabilities",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        CapabilityRow("View Executive Financial KPIs", capabilities.canViewFinancialKpis)
                        CapabilityRow("Approve Refunds & Order Cancellations", capabilities.canApproveRefundsOrCancellations)
                        CapabilityRow("Switch Multi-Branch Workspace", capabilities.canSwitchBranches)
                        CapabilityRow("View Security & Session Audit Logs", capabilities.canViewAuditLogs)
                        CapabilityRow("Access System & Firebase Settings", capabilities.canAccessSystemSettings)
                        CapabilityRow("Super Admin / Owner Governance", capabilities.canAccessAdminFunctions)

                        HorizontalDivider(color = ObsidianBorder)
                        Text(
                            text = "Active Modules (${activeModules.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = CraveGoldBright
                        )
                        Text(
                            text = activeModules.joinToString(" • ") { it.title },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onSignOut,
                    border = BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("profile_logout_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", tint = StatusCrimson)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("End Session & Sign Out", color = StatusCrimson)
                }
            }
        }
    }
}

@Composable
fun BranchesScreen(
    session: UserSession,
    branches: List<BranchSummary>,
    onSwitchBranch: (String) -> Unit,
    onBack: () -> Unit
) {
    SecondaryScreenScaffold(
        title = "Branch Management",
        subtitle = "Multi-branch isolation foundation",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!session.capabilities.canSwitchBranches) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                        border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Only Owner and Super Admin roles can switch between branches. You are locked to ${session.branchName}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            items(branches, key = { it.branchId }) { branch ->
                val isCurrent = branch.branchId == session.branchId
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) ObsidianSurfaceElevated else ObsidianSurface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isCurrent) CraveGold else ObsidianBorder
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Store, contentDescription = null, tint = CraveGoldBright)
                                Text(
                                    text = branch.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            StatusPill(
                                text = if (isCurrent) "ACTIVE" else branch.code,
                                containerColor = if (isCurrent) CraveGoldContainer else ObsidianBg,
                                contentColor = if (isCurrent) CraveGoldBright else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = branch.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (session.capabilities.canSwitchBranches && !isCurrent) {
                            Button(
                                onClick = { onSwitchBranch(branch.branchId) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CraveGold,
                                    contentColor = OnCraveGold
                                ),
                                modifier = Modifier.testTag("switch_branch_${branch.branchId}")
                            ) {
                                Text("Switch Active Branch")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditLogsScreen(
    logs: List<AuditLogEntity>,
    onBack: () -> Unit
) {
    val formatter = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US)

    SecondaryScreenScaffold(
        title = "Audit Logs",
        subtitle = "Local Room security & operator trail",
        onBack = onBack
    ) { padding ->
        if (logs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No audit events recorded yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusPill(
                                    text = log.action,
                                    containerColor = CraveGoldContainer,
                                    contentColor = CraveGoldBright
                                )
                                Text(
                                    text = formatter.format(Date(log.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = log.details,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${log.actorEmail} • ${log.actorRole} • ${log.branchId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    environmentStatus: FirebaseEnvironmentStatus,
    logoState: com.example.data.brand.BrandLogoState,
    pendingSyncCount: Int,
    auditLogCount: Int,
    probeResult: CloudProbeResult? = null,
    onProbeFirebaseConnection: () -> Unit = {},
    onImportLogoUri: (android.net.Uri) -> Unit,
    onResetLogo: () -> Unit,
    onRefreshSampleData: () -> Unit,
    onBack: () -> Unit
) {
    val logoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportLogoUri(uri)
        }
    }

    SecondaryScreenScaffold(
        title = "Settings & Brand Assets",
        subtitle = "Official logo, Firebase & Room diagnostics",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Original AA CRAVE Logo Upload / Replacement (Section 23)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.5f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "AA CRAVE Brand Logo Asset",
                            style = MaterialTheme.typography.titleMedium,
                            color = CraveGoldBright
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AaCraveBrandLogo(size = 72.dp, logoState = logoState)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (logoState.hasOwnerLogo) {
                                        "Original Logo: ${logoState.originalFileName ?: "Uploaded"}"
                                    } else {
                                        "Temporary Placeholder Active"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (logoState.hasOwnerLogo) {
                                        "Format: ${logoState.format?.name ?: "IMAGE"} ${logoState.widthPx?.let { w -> logoState.heightPx?.let { h -> "(${w}x${h}px)" } }.orEmpty()}"
                                    } else {
                                        "Upload your original AA CRAVE logo (PNG, JPG, WEBP, or SVG) to display across Login, Dashboard & Invoices."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    logoPickerLauncher.launch(
                                        arrayOf("image/png", "image/jpeg", "image/webp", "image/svg+xml")
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("upload_brand_logo_button")
                            ) {
                                Text(if (logoState.hasOwnerLogo) "Replace Logo" else "Upload Official Logo")
                            }
                            if (logoState.hasOwnerLogo) {
                                OutlinedButton(
                                    onClick = onResetLogo,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Use Placeholder")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = CraveGold)
                            Text(
                                text = "Firebase Configuration Status",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        ProfileMetaRow("Target Project", environmentStatus.projectName)
                        ProfileMetaRow("Android Package", FirebaseCloudConstants.EXPECTED_PACKAGE)
                        ProfileMetaRow(
                            "google-services.json",
                            if (environmentStatus.isGoogleServicesConfigured) "Present in app/" else "Missing in app/"
                        )
                        ProfileMetaRow(
                            "Email/Password Auth",
                            if (environmentStatus.isFirebaseAuthAvailable) "Ready" else "Awaiting google-services.json"
                        )
                        ProfileMetaRow("RTDB Endpoint", FirebaseCloudConstants.RTDB_URL)
                        ProfileMetaRow("Realtime Database", environmentStatus.realtimeDbStatus)
                        HorizontalDivider(color = ObsidianBorder)
                        Text(
                            text = "Manual Firebase Console Setup Checklist (aa-crave-erp)",
                            style = MaterialTheme.typography.labelLarge,
                            color = CraveGoldBright
                        )
                        Text(
                            text = "1. Register Android app package ${FirebaseCloudConstants.EXPECTED_PACKAGE} in Firebase project '${FirebaseCloudConstants.PROJECT_ID}' and place downloaded google-services.json into app/.\n" +
                                "2. Enable Email/Password under Authentication -> Sign-in method and create operator accounts.\n" +
                                "3. Publish database.rules.json to Firebase Realtime Database (${FirebaseCloudConstants.RTDB_URL}).\n" +
                                "4. Seed /erp/users/{uid} (roleCode='OWNER', branchId='branch_main_01', isActive=true) for the primary owner UID.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onProbeFirebaseConnection,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_probe_firebase_button")
                        ) {
                            Text("Test Firebase & Rules Connection", color = CraveGoldBright)
                        }
                        if (probeResult != null) {
                            Text(
                                text = "Probe: ${probeResult.summary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (probeResult.rtdbWritePermitted) StatusEmerald else StatusAmber
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Storage, contentDescription = null, tint = StatusEmerald)
                            Text(
                                text = "Local Room Database Foundation",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        ProfileMetaRow("Database Engine", "SQLite via Room (aa_crave_erp.db v9)")
                        ProfileMetaRow("Recorded Audit Events", auditLogCount.toString())
                        ProfileMetaRow("Pending Sync Queue", pendingSyncCount.toString())

                        Button(
                            onClick = onRefreshSampleData,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CraveGold,
                                contentColor = OnCraveGold
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_refresh_sample_button")
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset / Refresh Sample Dashboard KPIs")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CapabilityRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                contentDescription = if (granted) "Granted" else "Restricted",
                tint = if (granted) StatusEmerald else StatusAmber,
                modifier = Modifier.size(16.dp)
            )
            StatusPill(
                text = if (granted) "ALLOWED" else "RESTRICTED",
                containerColor = if (granted) StatusEmeraldBg else StatusAmberBg,
                contentColor = if (granted) StatusEmerald else StatusAmber
            )
        }
    }
}
