package com.example.ui.phase7

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.data.local.AuditLogEntity
import com.example.data.local.DeviceSessionEntity
import com.example.data.local.RolePermissionPolicyEntity
import com.example.data.local.StaffProfileEntity
import com.example.data.local.UserProfileEntity
import com.example.data.security.PasswordSecurity
import com.example.domain.model.BranchSummary
import com.example.domain.model.UserRole
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
import com.example.ui.viewmodel.UserPermissionsTab
import com.example.ui.viewmodel.UserPermissionsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPermissionsScreen(
    session: UserSession,
    uiState: UserPermissionsUiState,
    onOpenDrawer: () -> Unit,
    onSelectTab: (UserPermissionsTab) -> Unit,
    onSaveOperator: (uid: String, email: String, name: String, role: UserRole, branchId: String, active: Boolean) -> Unit,
    onSaveStaffAccount: (
        existingUid: String?,
        staffId: String?,
        displayName: String,
        username: String,
        explicitEmail: String?,
        password: String?,
        role: UserRole,
        branchId: String,
        isActive: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onResetStaffPassword: (targetUid: String, newTemporaryPassword: String?) -> Unit = { _, _ -> },
    onSetOperatorActive: (uid: String, active: Boolean, reason: String) -> Unit = { _, _, _ -> },
    onSendPasswordReset: (email: String) -> Unit = {},
    onUpdateRolePolicy: (RolePermissionPolicyEntity) -> Unit,
    onSetDeviceAuthorized: (deviceId: String, authorized: Boolean) -> Unit,
    onClearFeedback: () -> Unit
) {
    var editingOperator by remember { mutableStateOf<UserProfileEntity?>(null) }
    var showCreateOperatorDialog by remember { mutableStateOf(false) }
    var suspendTarget by remember { mutableStateOf<UserProfileEntity?>(null) }
    var resetTarget by remember { mutableStateOf<UserProfileEntity?>(null) }
    var historyTarget by remember { mutableStateOf<UserProfileEntity?>(null) }

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
                            text = "Users, Permissions & Devices",
                            style = MaterialTheme.typography.titleLarge,
                            color = CraveGoldBright
                        )
                        Text(
                            text = "Role Access Control & Firebase UID Assignment",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.selectedTab == UserPermissionsTab.OPERATORS && session.capabilities.canManageUserPermissions) {
                FloatingActionButton(
                    onClick = { showCreateOperatorDialog = true },
                    containerColor = CraveGold,
                    contentColor = OnCraveGold,
                    modifier = Modifier.testTag("add_operator_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Firebase Operator")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                        TextButton(onClick = onClearFeedback) { Text("DISMISS", color = CraveGoldBright) }
                    }
                }
            }

            TabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = ObsidianSurface,
                contentColor = CraveGoldBright
            ) {
                UserPermissionsTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        modifier = Modifier.testTag("perm_tab_${tab.name.lowercase()}"),
                        text = { Text(tab.label) }
                    )
                }
            }

            when (uiState.selectedTab) {
                UserPermissionsTab.OPERATORS -> OperatorsListContent(
                    session = session,
                    operators = uiState.operators,
                    branches = uiState.branches,
                    canEdit = session.capabilities.canManageUserPermissions,
                    onEditOperator = { editingOperator = it },
                    onToggleSuspend = { suspendTarget = it },
                    onOpenResetDialog = { resetTarget = it },
                    onViewHistory = { historyTarget = it }
                )
                UserPermissionsTab.ROLE_MATRIX -> RoleMatrixContent(
                    session = session,
                    policies = uiState.rolePolicies,
                    canEdit = session.capabilities.canManageUserPermissions,
                    onUpdatePolicy = onUpdateRolePolicy
                )
                UserPermissionsTab.DEVICES -> DevicesListContent(
                    devices = uiState.devices,
                    canManageDevices = session.capabilities.canManageDevices,
                    onSetAuthorized = onSetDeviceAuthorized
                )
            }
        }
    }

    if (showCreateOperatorDialog || editingOperator != null) {
        OperatorEditDialog(
            session = session,
            existing = editingOperator,
            branches = uiState.branches,
            staffDirectory = uiState.staffDirectory,
            existingOperators = uiState.operators,
            onDismiss = {
                showCreateOperatorDialog = false
                editingOperator = null
            },
            onSave = { existingUid, staffId, displayName, username, explicitEmail, password, role, branchId, active ->
                if (existingUid != null && username.isBlank()) {
                    onSaveOperator(existingUid, explicitEmail ?: "", displayName, role, branchId, active)
                } else {
                    onSaveStaffAccount(
                        existingUid,
                        staffId,
                        displayName,
                        username,
                        explicitEmail,
                        password,
                        role,
                        branchId,
                        active
                    )
                }
                showCreateOperatorDialog = false
                editingOperator = null
            }
        )
    }

    suspendTarget?.let { op ->
        SuspendOperatorDialog(
            operator = op,
            onDismiss = { suspendTarget = null },
            onConfirm = { reason ->
                onSetOperatorActive(op.uid, !op.isActive, reason)
                suspendTarget = null
            }
        )
    }

    resetTarget?.let { op ->
        ResetStaffPasswordDialog(
            operator = op,
            onDismiss = { resetTarget = null },
            onConfirmReset = { newTempPassword ->
                if (op.authProvider == "LOCAL_PENDING_FIREBASE" || newTempPassword != null) {
                    onResetStaffPassword(op.uid, newTempPassword)
                } else if (!PasswordSecurity.isInternalDerivedEmail(op.email)) {
                    onSendPasswordReset(op.email)
                } else {
                    onResetStaffPassword(op.uid, null)
                }
                resetTarget = null
            }
        )
    }

    historyTarget?.let { op ->
        OperatorActivityDialog(
            operator = op,
            logs = uiState.recentAuditLogs,
            onDismiss = { historyTarget = null }
        )
    }
}

@Composable
private fun OperatorsListContent(
    session: UserSession,
    operators: List<UserProfileEntity>,
    branches: List<BranchSummary>,
    canEdit: Boolean,
    onEditOperator: (UserProfileEntity) -> Unit,
    onToggleSuspend: (UserProfileEntity) -> Unit,
    onOpenResetDialog: (UserProfileEntity) -> Unit,
    onViewHistory: (UserProfileEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.4f)),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Staff Login Accounts & Firebase Security",
                        style = MaterialTheme.typography.labelLarge,
                        color = CraveGoldBright
                    )
                    Text(
                        text = "Create individual staff accounts with unique usernames and personal passwords. Online accounts provision into Firebase Authentication without signing out the active Owner/Admin; offline accounts use PBKDF2 salted hashes and auto-link on first online login. Existing passwords are never stored in plain text or exposed to admins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items(operators, key = { it.uid }) { op ->
            val role = UserRole.fromCode(op.roleCode)
            val branchName = branches.firstOrNull { it.branchId == op.branchId }?.name ?: op.branchId
            val isPendingLink = op.uid.startsWith("pending_") || op.authProvider == "LOCAL_PENDING_FIREBASE"
            val isSelf = op.uid == session.uid
            val isProtectedForAdmin = (role == UserRole.OWNER || role == UserRole.SUPER_ADMIN) &&
                (session.role != UserRole.OWNER && session.role != UserRole.SUPER_ADMIN)

            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, if (op.isActive) ObsidianBorder else StatusCrimson.copy(alpha = 0.55f)),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.testTag("operator_card_${op.uid}")
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = CraveGold)
                            Column {
                                Text(op.displayName, style = MaterialTheme.typography.titleMedium)
                                val identityLine = buildString {
                                    if (op.username.isNotBlank()) {
                                        append("@${op.username} • ")
                                    }
                                    append(op.email)
                                }
                                Text(
                                    text = identityLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill(
                                text = if (isPendingLink) "LOCAL / QUEUED" else "FIREBASE AUTH",
                                containerColor = if (isPendingLink) StatusAmberBg else CraveGoldContainer,
                                contentColor = if (isPendingLink) StatusAmber else CraveGoldBright
                            )
                            StatusPill(
                                text = if (op.isActive) "ACTIVE" else "SUSPENDED",
                                containerColor = if (op.isActive) StatusEmeraldBg else StatusCrimsonBg,
                                contentColor = if (op.isActive) StatusEmerald else StatusCrimson
                            )
                        }
                    }
                    Text(
                        text = "Role: ${role.displayName} • Branch: $branchName • UID: ${op.uid}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CraveGoldBright
                    )
                    if (canEdit) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onEditOperator(op) },
                                enabled = !isProtectedForAdmin,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("edit_operator_${op.uid}")
                            ) {
                                Text("Edit Access")
                            }
                            OutlinedButton(
                                onClick = { onToggleSuspend(op) },
                                enabled = !isSelf && !isProtectedForAdmin,
                                border = BorderStroke(
                                    1.dp,
                                    if (op.isActive) StatusCrimson.copy(alpha = 0.7f) else StatusEmerald.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("toggle_operator_${op.uid}")
                            ) {
                                Icon(
                                    imageVector = if (op.isActive) Icons.Filled.Block else Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = if (op.isActive) StatusCrimson else StatusEmerald,
                                    modifier = Modifier.width(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (op.isActive) "Deactivate" else "Activate",
                                    color = if (op.isActive) StatusCrimson else StatusEmerald
                                )
                            }
                            OutlinedButton(
                                onClick = { onOpenResetDialog(op) },
                                enabled = !isProtectedForAdmin,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("reset_password_${op.uid}")
                            ) {
                                Icon(Icons.Filled.LockReset, contentDescription = null, tint = CraveGold, modifier = Modifier.width(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Password")
                            }
                            OutlinedButton(
                                onClick = { onViewHistory(op) },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("history_operator_${op.uid}")
                            ) {
                                Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Activity")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleMatrixContent(
    session: UserSession,
    policies: List<RolePermissionPolicyEntity>,
    canEdit: Boolean,
    onUpdatePolicy: (RolePermissionPolicyEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(policies, key = { it.roleCode }) { policy ->
            val role = UserRole.fromCode(policy.roleCode)
            val isSuperAdmin = role == UserRole.SUPER_ADMIN
            val isOwnerProtected = role == UserRole.OWNER &&
                session.role != UserRole.OWNER && session.role != UserRole.SUPER_ADMIN
            val isLocked = isSuperAdmin || isOwnerProtected
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, ObsidianBorder),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = CraveGold)
                            Text(role.displayName, style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                        }
                        StatusPill(
                            text = if (isLocked) "PROTECTED" else "EDITABLE",
                            containerColor = CraveGoldContainer,
                            contentColor = CraveGoldBright
                        )
                    }
                    HorizontalDivider(color = ObsidianBorder)
                    PolicyToggleRow(
                        label = "Trigger Cloud Sync",
                        checked = policy.canTriggerCloudSync,
                        enabled = canEdit && !isLocked,
                        onCheckedChange = { onUpdatePolicy(policy.copy(canTriggerCloudSync = it)) }
                    )
                    PolicyToggleRow(
                        label = "Resolve Standard Conflicts",
                        checked = policy.canResolveSyncConflicts,
                        enabled = canEdit && !isLocked,
                        onCheckedChange = { onUpdatePolicy(policy.copy(canResolveSyncConflicts = it)) }
                    )
                    PolicyToggleRow(
                        label = "Resolve Financial Conflicts",
                        checked = policy.canResolveFinancialConflicts,
                        enabled = canEdit && !isLocked,
                        onCheckedChange = { onUpdatePolicy(policy.copy(canResolveFinancialConflicts = it)) }
                    )
                    PolicyToggleRow(
                        label = "Create Backups",
                        checked = policy.canCreateBackup,
                        enabled = canEdit && !isLocked,
                        onCheckedChange = { onUpdatePolicy(policy.copy(canCreateBackup = it)) }
                    )
                    PolicyToggleRow(
                        label = "Restore Backups",
                        checked = policy.canRestoreBackup,
                        enabled = canEdit && !isLocked,
                        onCheckedChange = { onUpdatePolicy(policy.copy(canRestoreBackup = it)) }
                    )
                    PolicyToggleRow(
                        label = "Manage Devices",
                        checked = policy.canManageDevices,
                        enabled = canEdit && !isLocked,
                        onCheckedChange = { onUpdatePolicy(policy.copy(canManageDevices = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicyToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun DevicesListContent(
    devices: List<DeviceSessionEntity>,
    canManageDevices: Boolean,
    onSetAuthorized: (String, Boolean) -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(devices, key = { it.deviceId }) { dev ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, if (dev.isAuthorized) ObsidianBorder else StatusCrimson.copy(alpha = 0.6f)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            Icon(Icons.Filled.Devices, contentDescription = null, tint = CraveGold)
                            Column {
                                Text(
                                    text = if (dev.isCurrentDevice) "${dev.deviceName} (This Terminal)" else dev.deviceName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${dev.osVersion} • ${dev.operatorEmail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        StatusPill(
                            text = if (dev.isAuthorized) dev.syncStatus else "REVOKED",
                            containerColor = if (dev.isAuthorized) StatusEmeraldBg else StatusCrimsonBg,
                            contentColor = if (dev.isAuthorized) StatusEmerald else StatusCrimson
                        )
                    }
                    Text(
                        text = "Device ID: ${dev.deviceId} • Last Seen: ${formatter.format(Date(dev.lastSeenAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (canManageDevices) {
                        OutlinedButton(
                            onClick = { onSetAuthorized(dev.deviceId, !dev.isAuthorized) },
                            border = BorderStroke(1.dp, if (dev.isAuthorized) StatusCrimson else StatusEmerald),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("toggle_device_${dev.deviceId}")
                        ) {
                            Text(
                                text = if (dev.isAuthorized) "Revoke Terminal Sync Access" else "Re-Authorize Terminal",
                                color = if (dev.isAuthorized) StatusCrimson else StatusEmerald
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperatorEditDialog(
    session: UserSession,
    existing: UserProfileEntity?,
    branches: List<BranchSummary>,
    staffDirectory: List<StaffProfileEntity>,
    existingOperators: List<UserProfileEntity>,
    onDismiss: () -> Unit,
    onSave: (
        existingUid: String?,
        staffId: String?,
        displayName: String,
        username: String,
        explicitEmail: String?,
        password: String?,
        role: UserRole,
        branchId: String,
        active: Boolean
    ) -> Unit
) {
    val isCreating = existing == null
    var selectedStaffId by remember { mutableStateOf(existing?.staffId) }
    var name by remember { mutableStateOf(existing?.displayName ?: "") }
    var username by remember {
        mutableStateOf(
            existing?.username?.takeIf { it.isNotBlank() }
                ?: existing?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: ""
        )
    }
    var explicitEmail by remember {
        mutableStateOf(
            existing?.email?.takeIf { !PasswordSecurity.isInternalDerivedEmail(it) } ?: ""
        )
    }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var selectedRole by remember { mutableStateOf(existing?.let { UserRole.fromCode(it.roleCode) } ?: UserRole.CASHIER) }
    var selectedBranchId by remember { mutableStateOf(existing?.branchId ?: branches.firstOrNull()?.branchId ?: "branch_main_01") }
    var isActive by remember { mutableStateOf(existing?.isActive ?: true) }
    var staffExpanded by remember { mutableStateOf(false) }
    var roleExpanded by remember { mutableStateOf(false) }
    var branchExpanded by remember { mutableStateOf(false) }

    val canAssignExecutiveRoles = session.role == UserRole.OWNER || session.role == UserRole.SUPER_ADMIN
    val assignableRoles = UserRole.entries.filter {
        canAssignExecutiveRoles || (it != UserRole.OWNER && it != UserRole.SUPER_ADMIN)
    }

    val linkedStaffIds = remember(existingOperators, existing) {
        existingOperators.mapNotNull { it.staffId }
            .filter { it != existing?.staffId }
            .toSet()
    }
    val linkableStaff = remember(staffDirectory, linkedStaffIds) {
        staffDirectory.filter { it.staffId !in linkedStaffIds }
    }

    val usernameError = remember(username) {
        if (username.isBlank()) null else PasswordSecurity.validateUsername(username)
    }
    val passwordError = remember(isCreating, password) {
        if (!isCreating || password.isEmpty()) null else PasswordSecurity.validatePassword(password)
    }
    val confirmError = remember(isCreating, password, confirmPassword) {
        if (!isCreating || confirmPassword.isEmpty()) null
        else if (password != confirmPassword) "Passwords do not match." else null
    }

    val canSubmit = name.isNotBlank() &&
        username.isNotBlank() &&
        usernameError == null &&
        (!isCreating || (PasswordSecurity.validatePassword(password) == null && password == confirmPassword))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text(
                text = if (isCreating) "Create Staff Login Account" else "Edit Staff Account Access",
                color = CraveGoldBright
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isCreating && linkableStaff.isNotEmpty()) {
                    val linkedName = linkableStaff.firstOrNull { it.staffId == selectedStaffId }
                        ?.let { "${it.fullName} (${it.employeeCode})" }
                        ?: "Standalone Account (Optional Link)"
                    ExposedDropdownMenuBox(
                        expanded = staffExpanded,
                        onExpandedChange = { staffExpanded = !staffExpanded }
                    ) {
                        OutlinedTextField(
                            value = linkedName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Link HR Staff Member (Optional)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = staffExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = staffExpanded, onDismissRequest = { staffExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Standalone Account (No HR link)") },
                                onClick = {
                                    selectedStaffId = null
                                    staffExpanded = false
                                }
                            )
                            linkableStaff.forEach { staff ->
                                DropdownMenuItem(
                                    text = { Text("${staff.fullName} • ${staff.jobTitle} (${staff.employeeCode})") },
                                    onClick = {
                                        selectedStaffId = staff.staffId
                                        name = staff.fullName
                                        if (username.isBlank()) {
                                            username = staff.fullName
                                                .trim()
                                                .lowercase(Locale.US)
                                                .replace(Regex("[^a-z0-9._-]"), "")
                                                .take(20)
                                        }
                                        val staffRole = UserRole.fromCode(staff.assignedRoleCode)
                                        if (canAssignExecutiveRoles || (staffRole != UserRole.OWNER && staffRole != UserRole.SUPER_ADMIN)) {
                                            selectedRole = staffRole
                                        }
                                        if (branches.any { it.branchId == staff.branchId }) {
                                            selectedBranchId = staff.branchId
                                        }
                                        staffExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Staff Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("operator_name_input")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Unique Username *") },
                    isError = usernameError != null,
                    supportingText = {
                        Text(usernameError ?: "Used on Sign In screen (3–32 letters, numbers, '.', '_', '-')")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("operator_username_input")
                )

                OutlinedTextField(
                    value = explicitEmail,
                    onValueChange = { explicitEmail = it },
                    label = { Text("Work Email (Optional)") },
                    supportingText = {
                        val preview = if (username.isNotBlank()) {
                            PasswordSecurity.deriveAuthEmail(username, explicitEmail.takeIf { it.isNotBlank() })
                        } else {
                            "Leave blank to derive username@staff.aacrave.erp"
                        }
                        Text("Firebase Auth ID: $preview")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("operator_email_input")
                )

                if (isCreating) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Individual Login Password *") },
                        isError = passwordError != null,
                        supportingText = {
                            Text(passwordError ?: "Minimum 6 characters. Hashed with PBKDF2 & provisioned to Firebase Auth.")
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("operator_password_input")
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password *") },
                        isError = confirmError != null,
                        supportingText = confirmError?.let { msg -> { Text(msg) } },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("operator_confirm_password_input")
                    )
                } else {
                    Text(
                        text = "Passwords are never displayed to administrators. Use Reset Password on the account card if needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = roleExpanded,
                    onExpandedChange = { roleExpanded = !roleExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedRole.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assigned Role") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                        assignableRoles.forEach { role ->
                            DropdownMenuItem(
                                text = { Text("${role.displayName} (${role.department})") },
                                onClick = {
                                    selectedRole = role
                                    roleExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = branchExpanded,
                    onExpandedChange = { branchExpanded = !branchExpanded }
                ) {
                    val branchLabel = branches.firstOrNull { it.branchId == selectedBranchId }?.name ?: selectedBranchId
                    OutlinedTextField(
                        value = branchLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assigned Branch") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = branchExpanded, onDismissRequest = { branchExpanded = false }) {
                        branches.forEach { br ->
                            DropdownMenuItem(
                                text = { Text("${br.name} (${br.code})") },
                                onClick = {
                                    selectedBranchId = br.branchId
                                    branchExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Account Enabled", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        existing?.uid,
                        selectedStaffId,
                        name.trim(),
                        username.trim(),
                        explicitEmail.trim().takeIf { it.isNotBlank() },
                        if (isCreating) password else null,
                        selectedRole,
                        selectedBranchId,
                        isActive
                    )
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                modifier = Modifier.testTag("save_operator_button")
            ) {
                Text(if (isCreating) "Create Account" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ResetStaffPasswordDialog(
    operator: UserProfileEntity,
    onDismiss: () -> Unit,
    onConfirmReset: (String?) -> Unit
) {
    val isLocalPending = operator.authProvider == "LOCAL_PENDING_FIREBASE"
    val isInternalEmail = PasswordSecurity.isInternalDerivedEmail(operator.email)
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    val passError = remember(newPassword) {
        if (newPassword.isEmpty()) null else PasswordSecurity.validatePassword(newPassword)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Reset Staff Password", color = CraveGoldBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Account: ${operator.displayName} (${if (operator.username.isNotBlank()) "@${operator.username}" else operator.email})",
                    style = MaterialTheme.typography.labelLarge
                )
                if (isLocalPending) {
                    Text(
                        text = "Enter a new personal password for this offline/local staff account. Previous passwords cannot be viewed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password *") },
                        isError = passError != null,
                        supportingText = passError?.let { msg -> { Text(msg) } },
                        singleLine = true,
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { visible = !visible }) {
                                Icon(
                                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (visible) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("reset_staff_new_password_input")
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password *") },
                        isError = confirmPassword.isNotEmpty() && confirmPassword != newPassword,
                        singleLine = true,
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (!isInternalEmail) {
                    Text(
                        text = "Send a secure Firebase Authentication password reset email to ${operator.email}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "This Firebase Auth user was created with an internal username-only address (${operator.email}). Add a deliverable work email via Edit Access or reset the password in Firebase Console -> Authentication.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber
                    )
                }
            }
        },
        confirmButton = {
            if (isLocalPending) {
                Button(
                    onClick = { onConfirmReset(newPassword) },
                    enabled = PasswordSecurity.validatePassword(newPassword) == null && newPassword == confirmPassword,
                    colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                    modifier = Modifier.testTag("confirm_reset_password_button")
                ) {
                    Text("Update Password")
                }
            } else if (!isInternalEmail) {
                Button(
                    onClick = { onConfirmReset(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = CraveGold, contentColor = OnCraveGold),
                    modifier = Modifier.testTag("confirm_reset_password_button")
                ) {
                    Text("Send Reset Email")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun SuspendOperatorDialog(
    operator: UserProfileEntity,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val suspending = operator.isActive
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = {
            Text(
                text = if (suspending) "Suspend Operator Account" else "Reactivate Operator Account",
                color = if (suspending) StatusCrimson else StatusEmerald
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (suspending) {
                        "Suspending ${operator.displayName} (${operator.email}) immediately blocks login and protected ERP operations."
                    } else {
                        "Reactivating ${operator.displayName} (${operator.email}) restores their assigned role access."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Audit Reason (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (suspending) StatusCrimson else StatusEmerald,
                    contentColor = OnCraveGold
                )
            ) {
                Text(if (suspending) "Suspend" else "Reactivate")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun OperatorActivityDialog(
    operator: UserProfileEntity,
    logs: List<AuditLogEntity>,
    onDismiss: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM HH:mm", Locale.US) }
    val matching = remember(operator, logs) {
        logs.filter {
            it.actorUid == operator.uid ||
                it.actorEmail.equals(operator.email, ignoreCase = true) ||
                it.details.contains(operator.email, ignoreCase = true)
        }.take(25)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurfaceElevated,
        title = { Text("Activity • ${operator.displayName}", color = CraveGoldBright) },
        text = {
            if (matching.isEmpty()) {
                Text(
                    text = "No recent audit events recorded for ${operator.email}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    matching.forEach { log ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                            border = BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(log.action, style = MaterialTheme.typography.labelMedium, color = CraveGoldBright)
                                    Text(formatter.format(Date(log.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(log.details, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
