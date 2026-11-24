package com.example.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.FirebaseEnvironmentStatus
import com.example.domain.model.UserRole
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
import com.example.ui.viewmodel.LoginFormState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    formState: LoginFormState,
    environmentStatus: FirebaseEnvironmentStatus,
    logoState: com.example.data.brand.BrandLogoState = com.example.data.brand.BrandLogoState(hasOwnerLogo = false),
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSignInClick: () -> Unit,
    onOpenResetDialog: () -> Unit,
    onDismissResetDialog: () -> Unit,
    onResetEmailChanged: (String) -> Unit,
    onSubmitPasswordReset: () -> Unit,
    onEnterPreviewSandbox: (UserRole) -> Unit
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var sandboxExpanded by rememberSaveable { mutableStateOf(!environmentStatus.isGoogleServicesConfigured) }
    var selectedSandboxRole by rememberSaveable { mutableStateOf(UserRole.BRANCH_MANAGER) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(CraveGold.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.14f),
                        radius = size.maxDimension * 0.55f
                    )
                )
            }
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AaCraveBrandLogo(size = 84.dp, logoState = logoState)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "AA CRAVE",
                    style = MaterialTheme.typography.displaySmall,
                    color = CraveGoldBright
                )
                Text(
                    text = stringResource(R.string.brand_tagline),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (!logoState.hasOwnerLogo) {
                    Text(
                        text = "Temporary logo placeholder — upload official AA CRAVE logo in Settings",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusAmber,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Main Login Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.login_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.login_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.Security,
                                contentDescription = "RBAC Security",
                                tint = CraveGold
                            )
                        }

                        OutlinedTextField(
                            value = formState.email,
                            onValueChange = onEmailChanged,
                            label = { Text(stringResource(R.string.email_label)) },
                            placeholder = { Text("e.g. username or owner@company.com") },
                            leadingIcon = {
                                Icon(Icons.Filled.Email, contentDescription = "Username or Email icon")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            colors = erpTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_email_input")
                        )

                        OutlinedTextField(
                            value = formState.password,
                            onValueChange = onPasswordChanged,
                            label = { Text(stringResource(R.string.password_label)) },
                            leadingIcon = {
                                Icon(Icons.Filled.Lock, contentDescription = "Password icon")
                            },
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
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { onSignInClick() }),
                            colors = erpTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input")
                        )

                        if (formState.errorMessage != null) {
                            Surface(
                                color = StatusCrimsonBg,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = formState.errorMessage,
                                    color = StatusCrimson,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                )
                            }
                        }

                        Button(
                            onClick = onSignInClick,
                            enabled = !formState.isSubmitting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CraveGold,
                                contentColor = OnCraveGold
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("login_submit_button")
                        ) {
                            if (formState.isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = OnCraveGold
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Text(
                                text = stringResource(R.string.sign_in_button),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onOpenResetDialog,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("forgot_password_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.forgot_password),
                                    color = CraveGoldBright
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Firebase Configuration Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    border = BorderStroke(
                        1.dp,
                        if (environmentStatus.isGoogleServicesConfigured) StatusEmerald.copy(alpha = 0.35f)
                        else StatusAmber.copy(alpha = 0.4f)
                    )
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (environmentStatus.isGoogleServicesConfigured) {
                                        Icons.Outlined.VerifiedUser
                                    } else {
                                        Icons.Outlined.CloudOff
                                    },
                                    contentDescription = "Firebase Status",
                                    tint = if (environmentStatus.isGoogleServicesConfigured) StatusEmerald else StatusAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Firebase: ${environmentStatus.projectName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            StatusPill(
                                text = if (environmentStatus.isGoogleServicesConfigured) "AUTH READY" else "CONFIG NEEDED",
                                containerColor = if (environmentStatus.isGoogleServicesConfigured) StatusEmeraldBg else StatusAmberBg,
                                contentColor = if (environmentStatus.isGoogleServicesConfigured) StatusEmerald else StatusAmber
                            )
                        }
                        Text(
                            text = environmentStatus.statusDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Realtime DB: ${environmentStatus.realtimeDbStatus}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CraveGold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Offline Sandbox Preview Card (No Hardcoded Credentials)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sandboxExpanded = !sandboxExpanded }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "Local Sandbox Info",
                                    tint = CraveGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Local Sandbox Preview (Sample Data)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Inspect Phase 1 Dashboard & RBAC offline without credentials",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (sandboxExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = "Toggle preview options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = sandboxExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Select Role to Preview Access Control:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CraveGold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    UserRole.entries.forEach { role ->
                                        val isSelected = selectedSandboxRole == role
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedSandboxRole = role },
                                            label = {
                                                Text(
                                                    text = role.displayName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = CraveGoldContainer,
                                                selectedLabelColor = CraveGoldBright
                                            )
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onEnterPreviewSandbox(selectedSandboxRole) },
                                    border = BorderStroke(1.dp, CraveGold.copy(alpha = 0.7f)),
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("enter_sandbox_button")
                                ) {
                                    Text(
                                        text = "Open ${selectedSandboxRole.displayName} Preview",
                                        color = CraveGoldBright,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (formState.isResetDialogOpen) {
        AlertDialog(
            onDismissRequest = onDismissResetDialog,
            containerColor = ObsidianSurfaceElevated,
            title = {
                Text(
                    text = stringResource(R.string.reset_password_title),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.reset_password_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = formState.resetEmail,
                        onValueChange = onResetEmailChanged,
                        label = { Text(stringResource(R.string.email_label)) },
                        singleLine = true,
                        colors = erpTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_email_input")
                    )
                    if (formState.resetFeedback != null) {
                        Text(
                            text = formState.resetFeedback,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (formState.isResetSuccess) StatusEmerald else StatusCrimson
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onSubmitPasswordReset,
                    enabled = !formState.isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CraveGold,
                        contentColor = OnCraveGold
                    ),
                    modifier = Modifier.testTag("confirm_reset_button")
                ) {
                    Text(stringResource(R.string.send_reset_link))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissResetDialog) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun erpTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CraveGold,
    unfocusedBorderColor = ObsidianBorder,
    focusedLabelColor = CraveGoldBright,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = CraveGold,
    focusedLeadingIconColor = CraveGold,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
)
