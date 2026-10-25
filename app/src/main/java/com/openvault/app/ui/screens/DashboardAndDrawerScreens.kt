package com.openvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openvault.app.ui.OpenVaultViewModel
import com.openvault.app.ui.components.SwipeableVaultCard
import com.openvault.app.ui.navigation.VaultModalSheet
import com.openvault.app.ui.navigation.VaultScreen
import com.openvault.app.ui.theme.JetBrainsMonoFontFamily
import com.openvault.app.ui.theme.LocalOpenVaultSpacing
import com.openvault.app.ui.theme.StatusErrorRed
import com.openvault.app.ui.theme.StatusWarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen #7: Navigation Drawer
 */
@Composable
fun OpenVaultDrawerContent(
    currentScreen: VaultScreen,
    vaultId: String,
    isDecoyMode: Boolean,
    onNavigate: (VaultScreen) -> Unit,
    onRequestLock: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight()
            .testTag("navigation_drawer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Drawer Header with Vault ID fingerprint & instant lock
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "OpenVault E2E",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "ID: ${vaultId.take(8)}…${vaultId.takeLast(4)}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onRequestLock,
                            modifier = Modifier.testTag("drawer_quick_lock_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Okamžitě zamknout trezor",
                                tint = StatusErrorRed
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isDecoyMode) "Stav: AES-256-GCM (Krycí relace)"
                        else "Stav: AES-256-GCM + SQLCipher aktivní",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val drawerItems: List<Triple<String, ImageVector, VaultScreen>> = listOf(
                Triple("Přehled", Icons.Default.Dashboard, VaultScreen.Dashboard),
                Triple("Poznámky", Icons.Default.Description, VaultScreen.NotesList),
                Triple("Kalendář", Icons.Default.CalendarMonth, VaultScreen.CalendarMonth),
                Triple("Hesla a TOTP", Icons.Default.Key, VaultScreen.PasswordsList),
                Triple("Soubory (VFS)", Icons.Default.AttachFile, VaultScreen.FilesBrowser()),
                Triple("Databáze", Icons.Default.Storage, VaultScreen.DatabasesList),
                Triple("Kolekce", Icons.Default.Folder, VaultScreen.CollectionsManager),
                Triple("Bezpečnostní centrum", Icons.Default.Security, VaultScreen.SecurityCenter),
                Triple("Audit log", Icons.Default.History, VaultScreen.AuditLog),
                Triple("Nastavení", Icons.Default.Settings, VaultScreen.SettingsHub)
            )

            drawerItems.forEach { (label, icon, targetScreen) ->
                val selected = currentScreen.routeKey == targetScreen.routeKey
                NavigationDrawerItem(
                    label = { Text(label) },
                    icon = { Icon(icon, contentDescription = label) },
                    selected = selected,
                    onClick = { onNavigate(targetScreen) },
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .testTag("drawer_item_${targetScreen.routeKey}")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onRequestLock,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusErrorRed),
                border = BorderStroke(1.dp, StatusErrorRed.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Zamknout trezor (Ctrl+L)")
            }
        }
    }
}

/**
 * Screen #6: Dashboard / Přehled
 */
@Composable
fun DashboardScreen(viewModel: OpenVaultViewModel) {
    val notes by viewModel.notes.collectAsState()
    val passwords by viewModel.passwords.collectAsState()
    val events by viewModel.events.collectAsState()
    val files by viewModel.files.collectAsState()
    val databases by viewModel.databases.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val integrity by viewModel.deviceIntegrity.collectAsState()
    val spacing = LocalOpenVaultSpacing.current

    var pullDownOffset by remember { mutableFloatStateOf(0f) }
    val dateFormatter = remember { SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()) }

    val now = System.currentTimeMillis()
    val upcomingEvents = remember(events, now) {
        events.filter { it.endAt >= now - 86_400_000L }.take(4)
    }
    val pinnedOrRecentNotes = remember(notes) {
        notes.take(4)
    }
    val recentPasswords = remember(passwords) {
        passwords.take(4)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.itemSpacing),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { pullDownOffset = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        pullDownOffset += dragAmount
                    },
                    onDragEnd = {
                        if (pullDownOffset > 130f) {
                            viewModel.openModalSheet(VaultModalSheet.QuickCapture(0))
                        }
                        pullDownOffset = 0f
                    }
                )
            }
            .padding(horizontal = spacing.screenHorizontal, vertical = 12.dp)
            .testTag("dashboard_screen")
    ) {
        // Device integrity warning banner if root / hooks detected
        if (integrity.isCompromised) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StatusWarningAmber.copy(alpha = 0.14f)),
                    border = BorderStroke(1.dp, StatusWarningAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = StatusWarningAmber)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Upozornění na integritu zařízení",
                                style = MaterialTheme.typography.titleMedium,
                                color = StatusWarningAmber
                            )
                            Text(
                                text = integrity.findings.joinToString("; "),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // Vault & Sync Status Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(spacing.cardPadding)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Trezor OpenVault odemčen",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${syncStatus.badgeSymbol} ${syncStatus.labelCs}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { viewModel.triggerManualSync(forceFull = false) },
                            modifier = Modifier.testTag("dashboard_sync_now_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Synchronizovat se Supabase",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Metrics Strip
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MetricMiniCard(
                            label = "Poznámky",
                            count = notes.size,
                            onClick = { viewModel.navigateTo(VaultScreen.NotesList) },
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniCard(
                            label = "Hesla",
                            count = passwords.size,
                            onClick = { viewModel.navigateTo(VaultScreen.PasswordsList) },
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniCard(
                            label = "Soubory",
                            count = files.size,
                            onClick = { viewModel.navigateTo(VaultScreen.FilesBrowser()) },
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniCard(
                            label = "Databáze",
                            count = databases.size,
                            onClick = { viewModel.navigateTo(VaultScreen.DatabasesList) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Quick Actions Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Rychlé akce (nebo stáhněte obrazovku dolů pro Quick Capture)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AssistChip(
                        onClick = { viewModel.navigateTo(VaultScreen.NoteEditor()) },
                        label = { Text("+ Poznámka") },
                        leadingIcon = { Icon(Icons.Default.Description, null, Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("quick_action_new_note")
                    )
                    AssistChip(
                        onClick = { viewModel.navigateTo(VaultScreen.PasswordDetailEditor()) },
                        label = { Text("+ Heslo") },
                        leadingIcon = { Icon(Icons.Default.Key, null, Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("quick_action_new_password")
                    )
                    AssistChip(
                        onClick = { viewModel.navigateTo(VaultScreen.EventEditor()) },
                        label = { Text("+ Událost") },
                        leadingIcon = { Icon(Icons.Default.Event, null, Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("quick_action_new_event")
                    )
                }
            }
        }

        // Today / Upcoming in Calendar
        item {
            SectionHeaderRow(
                title = "Dnes a nadcházející v kalendáři",
                actionText = "Otevřít kalendář",
                onAction = { viewModel.navigateTo(VaultScreen.CalendarMonth) }
            )
        }
        if (upcomingEvents.isEmpty()) {
            item {
                SubtleEmptySectionCard(
                    text = "Žádné naplánované události. Klepněte pro přidání šifrované události.",
                    onClick = { viewModel.navigateTo(VaultScreen.EventEditor()) }
                )
            }
        } else {
            items(upcomingEvents, key = { "dash_ev_${it.id}" }) { ev ->
                SwipeableVaultCard(
                    onClick = { viewModel.navigateTo(VaultScreen.EventDetail(ev.id)) },
                    onLongClick = {
                        viewModel.openModalSheet(VaultModalSheet.ContextMenu(ev.id, ev.title, "event"))
                    },
                    onSwipeLeftAction = { viewModel.deleteEvent(ev.id) },
                    onSwipeRightFavorite = {}
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ev.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = dateFormatter.format(Date(ev.startAt)),
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Pinned & Recent Notes
        item {
            SectionHeaderRow(
                title = "Připnuté a nedávné poznámky",
                actionText = "Všechny poznámky",
                onAction = { viewModel.navigateTo(VaultScreen.NotesList) }
            )
        }
        if (pinnedOrRecentNotes.isEmpty()) {
            item {
                SubtleEmptySectionCard(
                    text = "Zatím žádné šifrované poznámky. Vytvořte první poznámku s Markdownem.",
                    onClick = { viewModel.navigateTo(VaultScreen.NoteEditor()) }
                )
            }
        } else {
            items(pinnedOrRecentNotes, key = { "dash_note_${it.id}" }) { note ->
                SwipeableVaultCard(
                    onClick = { viewModel.navigateTo(VaultScreen.NoteDetail(note.id)) },
                    onLongClick = {
                        viewModel.openModalSheet(VaultModalSheet.ContextMenu(note.id, note.title, "note"))
                    },
                    onSwipeLeftAction = { viewModel.deleteNote(note.id) },
                    onSwipeRightFavorite = { viewModel.toggleNotePinned(note.id) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (note.isPinned) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Připnuto",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = note.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "rev ${note.rev}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (note.body.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Recent Passwords
        item {
            SectionHeaderRow(
                title = "Naposledy použitá hesla a TOTP",
                actionText = "Všechna hesla",
                onAction = { viewModel.navigateTo(VaultScreen.PasswordsList) }
            )
        }
        if (recentPasswords.isEmpty()) {
            item {
                SubtleEmptySectionCard(
                    text = "Zatím žádná uložená hesla. Přidejte heslo nebo TOTP autentikátor.",
                    onClick = { viewModel.navigateTo(VaultScreen.PasswordDetailEditor()) }
                )
            }
        } else {
            items(recentPasswords, key = { "dash_pwd_${it.id}" }) { pwd ->
                SwipeableVaultCard(
                    onClick = { viewModel.navigateTo(VaultScreen.PasswordDetailEditor(pwd.id)) },
                    onLongClick = {
                        viewModel.openModalSheet(VaultModalSheet.ContextMenu(pwd.id, pwd.title, "password"))
                    },
                    onSwipeLeftAction = { viewModel.copySecretToClipboard("Heslo (${pwd.title})", pwd.password) },
                    onSwipeRightFavorite = { viewModel.togglePasswordFavorite(pwd.id) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pwd.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = pwd.username.ifEmpty { pwd.url.ifEmpty { pwd.category } },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.copySecretToClipboard("Heslo (${pwd.title})", pwd.password)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Zkopírovat heslo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun MetricMiniCard(
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
        ) {
            Text(
                text = count.toString(),
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    actionText: String,
    onAction: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = actionText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onAction)
        )
    }
}

@Composable
private fun SubtleEmptySectionCard(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
