package com.example.ui.phase7

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.NotificationCategory
import com.example.ui.components.StatusPill
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
import com.example.ui.theme.StatusCrimsonBg
import com.example.ui.theme.StatusSky
import com.example.ui.theme.StatusSkyBg
import com.example.ui.viewmodel.NotificationsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    uiState: NotificationsUiState,
    onOpenDrawer: () -> Unit,
    onSelectCategory: (NotificationCategory) -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }

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
                            text = "Notifications Center",
                            style = MaterialTheme.typography.titleLarge,
                            color = CraveGoldBright
                        )
                        Text(
                            text = "${uiState.unreadCount} unread alerts across sync, security & operations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (uiState.unreadCount > 0) {
                        TextButton(
                            onClick = onMarkAllRead,
                            modifier = Modifier.testTag("mark_all_read_button")
                        ) {
                            Icon(Icons.Filled.DoneAll, contentDescription = null, tint = CraveGoldBright, modifier = Modifier.size(18.dp))
                            Text(" Read All", color = CraveGoldBright)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NotificationCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = uiState.selectedCategory == cat,
                        onClick = { onSelectCategory(cat) },
                        label = { Text(cat.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CraveGoldContainer,
                            selectedLabelColor = CraveGoldBright
                        ),
                        modifier = Modifier.testTag("notif_filter_${cat.code.lowercase()}")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.filteredNotifications.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Notifications, contentDescription = null, tint = CraveGold)
                                Text("No Notifications", style = MaterialTheme.typography.titleMedium, color = CraveGoldBright)
                                Text(
                                    "Alerts for sync failures, security rules, conflicts, and backups will appear here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(uiState.filteredNotifications, key = { it.notificationId }) { notif ->
                        val pillColors = when (notif.severity) {
                            "CRITICAL" -> StatusCrimsonBg to StatusCrimson
                            "WARNING" -> StatusAmberBg to StatusAmber
                            else -> StatusSkyBg to StatusSky
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (notif.isRead) ObsidianSurface else ObsidianSurfaceElevated
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (!notif.isRead) CraveGold.copy(alpha = 0.55f) else ObsidianBorder
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (!notif.isRead) onMarkRead(notif.notificationId) }
                                .testTag("notification_card_${notif.notificationId}")
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
                                    Text(
                                        text = notif.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (notif.isRead) FontWeight.Medium else FontWeight.Bold,
                                        color = if (notif.isRead) MaterialTheme.colorScheme.onSurface else CraveGoldBright,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatusPill(
                                        text = "${notif.category} • ${notif.severity}",
                                        containerColor = pillColors.first,
                                        contentColor = pillColors.second
                                    )
                                }
                                Text(
                                    text = notif.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatter.format(Date(notif.createdAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (notif.isRead) "READ" else "TAP TO MARK READ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (notif.isRead) MaterialTheme.colorScheme.onSurfaceVariant else CraveGold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
