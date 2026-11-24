package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.brand.BrandLogoState
import com.example.domain.model.AppModule
import com.example.domain.model.RoleAccessControl
import com.example.domain.model.UserSession
import com.example.ui.theme.CraveGold
import com.example.ui.theme.CraveGoldBright
import com.example.ui.theme.CraveGoldContainer
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusAmberBg
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusEmeraldBg

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.domain.model.ModuleSection

@Composable
fun AaCraveDrawerContent(
    session: UserSession,
    logoState: BrandLogoState,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onSignOutClick: () -> Unit
) {
    val activeModules = RoleAccessControl.activePhase1Modules(session.role)
    val roadmapModules = RoleAccessControl.roadmapModules(session.role)
    var searchQuery by remember { mutableStateOf("") }
    val collapsedSections = remember { mutableStateMapOf<ModuleSection, Boolean>() }

    val filteredModules = remember(activeModules, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) activeModules
        else activeModules.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.section.title.contains(q, ignoreCase = true) ||
                it.route.contains(q, ignoreCase = true)
        }
    }

    ModalDrawerSheet(
        modifier = Modifier.width(316.dp),
        drawerContainerColor = ObsidianSurface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Surface(
                color = ObsidianSurfaceElevated,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AaCraveBrandLogo(
                            size = 48.dp,
                            logoState = logoState,
                            showPlaceholderLabel = false
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AA CRAVE ERP",
                                style = MaterialTheme.typography.titleMedium,
                                color = CraveGoldBright
                            )
                            Text(
                                text = session.branchName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    HorizontalDivider(color = ObsidianBorder)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = session.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = session.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                text = session.role.displayName.uppercase(),
                                containerColor = CraveGoldContainer,
                                contentColor = CraveGoldBright
                            )
                            StatusPill(
                                text = if (session.isFirebaseAuthenticated) "FIREBASE" else "SANDBOX",
                                containerColor = if (session.isFirebaseAuthenticated) StatusEmeraldBg else StatusAmberBg,
                                contentColor = if (session.isFirebaseAuthenticated) StatusEmerald else StatusAmber
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Quick find module (e.g. Inventory)...") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "Filter modules", tint = CraveGold)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CraveGold,
                    unfocusedBorderColor = ObsidianBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredModules.isEmpty()) {
                Text(
                    text = "No authorized modules match \"$searchQuery\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                )
            } else {
                ModuleSection.entries.forEach { section ->
                    val sectionModules = filteredModules.filter { it.section == section }
                    if (sectionModules.isNotEmpty()) {
                        val isSearching = searchQuery.isNotBlank()
                        val isExpanded = isSearching || (collapsedSections[section] != true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    collapsedSections[section] = isExpanded
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${section.title.uppercase()} (${sectionModules.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = CraveGold,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse ${section.title}" else "Expand ${section.title}",
                                tint = CraveGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column {
                                sectionModules.forEach { module ->
                                    val selected = currentRoute == module.route
                                    NavigationDrawerItem(
                                        label = {
                                            Text(
                                                text = module.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = iconForActiveModule(module),
                                                contentDescription = module.title
                                            )
                                        },
                                        selected = selected,
                                        onClick = { onNavigate(module.route) },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = CraveGoldContainer,
                                            selectedIconColor = CraveGoldBright,
                                            selectedTextColor = CraveGoldBright,
                                            unselectedContainerColor = Color.Transparent,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier
                                            .padding(vertical = 2.dp)
                                            .testTag("drawer_item_${module.route}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (roadmapModules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = ObsidianBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "AUTHORIZED ROADMAP (PHASE 7)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                roadmapModules.forEach { module ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LockClock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = module.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = ObsidianBg,
                            shape = MaterialTheme.shapes.extraSmall,
                            border = BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Text(
                                text = "P${module.phaseNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CraveGold.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = ObsidianBorder)
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onSignOutClick,
                border = BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.6f)),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("drawer_logout_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Sign Out",
                    tint = StatusCrimson,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.logout),
                    color = StatusCrimson,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

private fun iconForActiveModule(module: AppModule): ImageVector = when (module) {
    AppModule.DASHBOARD -> Icons.Filled.Dashboard
    AppModule.USER_PROFILE -> Icons.Filled.AccountCircle
    AppModule.BRANCH_MANAGEMENT -> Icons.Filled.Store
    AppModule.AUDIT_LOGS -> Icons.Filled.History
    AppModule.SETTINGS -> Icons.Filled.Settings
    AppModule.POS -> Icons.Filled.PointOfSale
    AppModule.BILLING -> Icons.Filled.Payments
    AppModule.CUSTOMER_INVOICES -> Icons.Filled.Receipt
    AppModule.KITCHEN_KOT -> Icons.Filled.Restaurant
    AppModule.KITCHEN_DISPLAY -> Icons.Filled.Tv
    AppModule.ORDERS -> Icons.Filled.ListAlt
    AppModule.MENU -> Icons.Filled.RestaurantMenu
    AppModule.RECIPES -> Icons.Filled.MenuBook
    AppModule.INVENTORY -> Icons.Filled.Inventory2
    AppModule.CASH_CLOSING -> Icons.Filled.Savings
    AppModule.PURCHASES -> Icons.Filled.ShoppingBag
    AppModule.SUPPLIERS -> Icons.Filled.LocalShipping
    AppModule.CUSTOMERS -> Icons.Filled.Groups
    AppModule.LOYALTY -> Icons.Filled.CardMembership
    AppModule.EXPENSES -> Icons.Filled.AccountBalanceWallet
    AppModule.STAFF -> Icons.Filled.Badge
    AppModule.ATTENDANCE -> Icons.Filled.Fingerprint
    AppModule.SHIFTS -> Icons.Filled.Schedule
    AppModule.LEAVE_MANAGEMENT -> Icons.Filled.EventAvailable
    AppModule.PAYROLL -> Icons.Filled.AccountBalance
    AppModule.ACCOUNTING -> Icons.Filled.ReceiptLong
    AppModule.REPORTS -> Icons.Filled.Assessment
    AppModule.USER_PERMISSIONS -> Icons.Filled.AdminPanelSettings
    AppModule.NOTIFICATIONS -> Icons.Filled.Notifications
    AppModule.BACKUP_RESTORE -> Icons.Filled.CloudSync
    else -> Icons.Filled.Dashboard
}
