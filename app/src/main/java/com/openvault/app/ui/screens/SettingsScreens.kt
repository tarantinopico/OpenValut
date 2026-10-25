package com.openvault.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.openvault.app.data.remote.SupabaseConfig
import com.openvault.app.domain.model.AccentColorChoice
import com.openvault.app.domain.model.ConflictResolutionStrategy
import com.openvault.app.domain.model.FabActionType
import com.openvault.app.domain.model.FontScaleChoice
import com.openvault.app.domain.model.ThemeMode
import com.openvault.app.domain.model.UiDensityChoice
import com.openvault.app.domain.usecase.SystemCalendarBridge
import com.openvault.app.security.DuressAction
import com.openvault.app.security.OvaultImportMode
import com.openvault.app.ui.OpenVaultViewModel
import com.openvault.app.ui.navigation.VaultScreen
import com.openvault.app.ui.theme.JetBrainsMonoFontFamily
import com.openvault.app.ui.theme.StatusErrorRed

/**
 * Screen #26: Nastavení – Hlavní rozcestník
 */
@Composable
fun SettingsHubScreen(viewModel: OpenVaultViewModel) {
    val categories: List<Triple<String, String, Pair<ImageVector, VaultScreen>>> = listOf(
        Triple(
            "Bezpečnost a klíče",
            "Biometrie, auto-lock timeout, FLAG_SECURE, schránka 30 s, Duress heslo",
            Icons.Default.Security to VaultScreen.SettingsSecurity
        ),
        Triple(
            "Synchronizace (Supabase E2E)",
            "Vault ID, interval synchronizace, Realtime kanál, řešení kolizí",
            Icons.Default.Sync to VaultScreen.SettingsSync
        ),
        Triple(
            "Vzhled a AMOLED motiv",
            "AMOLED Černý #000000, akcentové barvy, velikost písma, hustota UI",
            Icons.Default.Palette to VaultScreen.SettingsAppearance
        ),
        Triple(
            "Data a zálohy (.ovault)",
            "Šifrovaný export a import kontejneru OVAULT01, vymazání cache, Nebezpečná zóna",
            Icons.Default.Backup to VaultScreen.SettingsBackup
        ),
        Triple(
            "Kalendář a systémová synchronizace",
            "Obousměrná integrace s Android CalendarContract a anonymizace názvů",
            Icons.Default.CalendarMonth to VaultScreen.SettingsCalendar
        ),
        Triple(
            "Rychlé akce, notifikace a widgety",
            "Chování multifunkčního FAB, trvalá notifikace a 4 widgety na plochu",
            Icons.Default.FlashOn to VaultScreen.SettingsQuickActions
        ),
        Triple(
            "Audit a bezpečnostní logy",
            "Úroveň detailů append-only auditního protokolu a export",
            Icons.Default.History to VaultScreen.SettingsAudit
        ),
        Triple(
            "O aplikaci a kryptografická specifikace",
            "AES-256-GCM, Argon2id/PBKDF2, SQLCipher a diagnostika Supabase",
            Icons.Default.Info to VaultScreen.SettingsAbout
        )
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_hub_screen")
    ) {
        categories.forEach { (title, subtitle, iconAndTarget) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.navigateTo(iconAndTarget.second) }
                    .testTag("settings_item_${iconAndTarget.second.routeKey}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = iconAndTarget.first,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Screen #27: Nastavení – Bezpečnost
 */
@Composable
fun SettingsSecurityScreen(viewModel: OpenVaultViewModel) {
    val settings by viewModel.settings.collectAsState()
    var duressPasswordInput by remember { mutableStateOf("") }
    var duressActionChoice by remember {
        mutableStateOf(viewModel.container.securityGate.getDuressAction())
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_security_screen")
    ) {
        Text(
            text = "Nastavení zabezpečení a běhového prostředí",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        // Auto-lock timeout selector (1, 2, 5, 10, 15, 30 min)
        Text("Timeout automatického zamknutí při nečinnosti:", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf(1, 2, 5, 10, 15, 30).forEach { mins ->
                FilterChip(
                    selected = settings.autoLockTimeoutMinutes == mins,
                    onClick = {
                        viewModel.updateVaultSettings { it.copy(autoLockTimeoutMinutes = mins) }
                    },
                    label = { Text("$mins min") }
                )
            }
        }

        SettingsToggleRow(
            title = "Zamknout při přechodu aplikace do pozadí",
            subtitle = "Okamžitě vyčistí Vault Key z RAM při minimalizaci nebo vypnutí obrazovky",
            checked = settings.lockOnBackground,
            onCheckedChange = { enabled ->
                viewModel.updateVaultSettings { it.copy(lockOnBackground = enabled) }
            }
        )

        SettingsToggleRow(
            title = "Ochrana obrazovky (FLAG_SECURE)",
            subtitle = "Skryje obsah trezoru v přepínači aplikací a zakáže pořizování screenshotů",
            checked = settings.flagSecureEnabled,
            onCheckedChange = { enabled ->
                viewModel.updateVaultSettings { it.copy(flagSecureEnabled = enabled) }
            }
        )

        SettingsToggleRow(
            title = "Automatické vymazání schránky po 30 sekundách",
            subtitle = "Označí zkopírovaná hesla a TOTP jako EXTRA_IS_SENSITIVE a vymaže je po 30 s",
            checked = settings.clipboardAutoClear30s,
            onCheckedChange = { enabled ->
                viewModel.updateVaultSettings { it.copy(clipboardAutoClear30s = enabled) }
            }
        )

        // Duress / Panic Password Configuration
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Nouzové (Duress / Panic) heslo",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Při zadání nouzového hesla na odemykací obrazovce otevře krycí prázdný trezor (Decoy režim) nebo bezpečně vymaže lokální klíče.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = duressActionChoice == DuressAction.DECOY_VAULT,
                        onClick = { duressActionChoice = DuressAction.DECOY_VAULT },
                        label = { Text("Krycí trezor (Decoy)") }
                    )
                    FilterChip(
                        selected = duressActionChoice == DuressAction.CRYPTOGRAPHIC_WIPE,
                        onClick = { duressActionChoice = DuressAction.CRYPTOGRAPHIC_WIPE },
                        label = { Text("Smazat klíče (Wipe)") }
                    )
                }
                OutlinedTextField(
                    value = duressPasswordInput,
                    onValueChange = { duressPasswordInput = it },
                    label = { Text("Nastavit nové Duress heslo") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (duressPasswordInput.length >= 6) {
                            viewModel.configureDuressPassword(duressPasswordInput, duressActionChoice)
                            duressPasswordInput = ""
                        }
                    },
                    enabled = duressPasswordInput.length >= 6,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Uložit nouzové Duress heslo")
                }
            }
        }
    }
}

/**
 * Screen #28: Nastavení – Synchronizace
 */
@Composable
fun SettingsSyncScreen(viewModel: OpenVaultViewModel) {
    val settings by viewModel.settings.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val vaultId = viewModel.container.securityGate.getVaultIdOrNull() ?: ""

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_sync_screen")
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Supabase E2E Cloud Backend",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("Stav: ${syncStatus.badgeSymbol} ${syncStatus.labelCs}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "x-vault-id: $vaultId",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.copySecretToClipboard("Vault ID", vaultId) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Zkopírovat Vault ID")
                    }
                }
            }
        }

        Text("Frekvence synchronizace na pozadí (WorkManager):", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf(15 to "15 min", 30 to "30 min", 60 to "1 hod", 0 to "Manuálně").forEach { (mins, label) ->
                FilterChip(
                    selected = settings.syncIntervalMinutes == mins,
                    onClick = { viewModel.updateVaultSettings { it.copy(syncIntervalMinutes = mins) } },
                    label = { Text(label) }
                )
            }
        }

        SettingsToggleRow(
            title = "Synchronizovat pouze přes Wi-Fi",
            subtitle = "Pozastaví automatické přenosy na mobilních datech",
            checked = settings.syncWifiOnly,
            onCheckedChange = { v -> viewModel.updateVaultSettings { it.copy(syncWifiOnly = v) } }
        )

        SettingsToggleRow(
            title = "Realtime odběr změn (Supabase Realtime)",
            subtitle = "Okamžitě promítá změny z ostatních spárovaných zařízení přes WebSocket",
            checked = settings.realtimeSyncEnabled,
            onCheckedChange = { v -> viewModel.updateVaultSettings { it.copy(realtimeSyncEnabled = v) } }
        )

        Text("Strategie řešení konfliktů:", style = MaterialTheme.typography.labelLarge)
        ConflictResolutionStrategy.entries.forEach { strat ->
            FilterChip(
                selected = settings.conflictStrategy == strat,
                onClick = { viewModel.updateVaultSettings { it.copy(conflictStrategy = strat) } },
                label = { Text(strat.labelCs) }
            )
        }

        Button(
            onClick = { viewModel.triggerManualSync(forceFull = true) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("force_full_sync_btn")
        ) {
            Icon(Icons.Default.Sync, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Vynutit plnou synchronizaci se Supabase")
        }
    }
}

/**
 * Screen #29: Nastavení – Vzhled
 */
@Composable
fun SettingsAppearanceScreen(viewModel: OpenVaultViewModel) {
    val settings by viewModel.settings.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_appearance_screen")
    ) {
        Text(
            text = "Motiv a vizuální identita OpenVault",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Text("Režim motivu:", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.updateVaultSettings { it.copy(themeMode = mode) } },
                    label = { Text(mode.labelCs) }
                )
            }
        }

        Text("Akcentová barva:", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            AccentColorChoice.entries.forEach { accent ->
                FilterChip(
                    selected = settings.accentColor == accent,
                    onClick = { viewModel.updateVaultSettings { it.copy(accentColor = accent) } },
                    label = { Text(accent.labelCs) }
                )
            }
        }

        Text("Velikost písma:", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontScaleChoice.entries.forEach { fs ->
                FilterChip(
                    selected = settings.fontScale == fs,
                    onClick = { viewModel.updateVaultSettings { it.copy(fontScale = fs) } },
                    label = { Text(fs.labelCs) }
                )
            }
        }

        Text("Hustota prvků:", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UiDensityChoice.entries.forEach { density ->
                FilterChip(
                    selected = settings.uiDensity == density,
                    onClick = { viewModel.updateVaultSettings { it.copy(uiDensity = density) } },
                    label = { Text(density.labelCs) }
                )
            }
        }
    }
}

/**
 * Screen #30: Nastavení – Data a zálohy (.ovault)
 */
@Composable
fun SettingsBackupScreen(viewModel: OpenVaultViewModel) {
    val context = LocalContext.current
    val lastExportedBytes by viewModel.lastExportedOvaultBytes.collectAsState()

    var backupPassword by remember { mutableStateOf("") }
    var importMode by remember { mutableStateOf(OvaultImportMode.MERGE) }

    val saveDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = lastExportedBytes
        if (uri != null && bytes != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                viewModel.showStatusMessage("Soubor .ovault byl uložen do úložiště.")
            }
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && backupPassword.isNotBlank()) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null) {
                viewModel.importOvaultContainer(bytes, backupPassword, importMode)
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_backup_screen")
    ) {
        Text(
            text = "Šifrovaný kontejner .ovault (OVAULT01)",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Kompletní export obsahuje hlavičku OVAULT01, parametry Argon2id/PBKDF2, 32B HMAC-SHA256 integritní podpis a AES-256-GCM šifrovaný obraz všech objektů, nastavení, audit logu a VFS souborů.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = backupPassword,
            onValueChange = { backupPassword = it },
            label = { Text("Heslo pro šifrování / dešifrování kontejneru .ovault") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ovault_backup_password_input")
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OvaultImportMode.entries.forEach { m ->
                FilterChip(
                    selected = importMode == m,
                    onClick = { importMode = m },
                    label = { Text(m.labelCs) }
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    viewModel.exportOvaultContainer(backupPassword) {
                        saveDocLauncher.launch("openvault_backup_${System.currentTimeMillis()}.ovault")
                    }
                },
                enabled = backupPassword.length >= 6,
                modifier = Modifier
                    .weight(1f)
                    .testTag("export_ovault_btn")
            ) {
                Text("Exportovat .ovault")
            }

            OutlinedButton(
                onClick = { openDocLauncher.launch(arrayOf("*/*")) },
                enabled = backupPassword.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_ovault_btn")
            ) {
                Text("Importovat .ovault")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = StatusErrorRed.copy(alpha = 0.12f)),
            border = BorderStroke(1.dp, StatusErrorRed),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Nebezpečná zóna (Danger Zone)",
                    style = MaterialTheme.typography.titleMedium,
                    color = StatusErrorRed
                )
                Text(
                    text = "Trvalé kryptografické vymazání všech klíčů, obálek a lokální SQLCipher databáze z tohoto zařízení.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { viewModel.wipeEntireVaultCryptographically() },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusErrorRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Trvale smazat lokální trezor (Cryptographic Wipe)", color = Color.White)
                }
            }
        }
    }
}

/**
 * Screen #31: Nastavení – Kalendář
 */
@Composable
fun SettingsCalendarScreen(viewModel: OpenVaultViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    var hasPerms by remember { mutableStateOf(SystemCalendarBridge.hasCalendarPermissions(context)) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPerms = result.values.all { it }
    }

    val availableCalendars = remember(hasPerms) {
        if (hasPerms) SystemCalendarBridge.queryAvailableCalendars(context) else emptyList()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_calendar_screen")
    ) {
        Text(
            text = "Synchronizace se systémovým Android kalendářem",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        if (!hasPerms) {
            Button(
                onClick = {
                    permLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Udělit oprávnění READ_CALENDAR a WRITE_CALENDAR")
            }
        }

        SettingsToggleRow(
            title = "Povolit obousměrnou synchronizaci se systémovým kalendářem",
            subtitle = "Automaticky propisuje události přes Android CalendarContract",
            checked = settings.systemCalendarSyncEnabled,
            onCheckedChange = { v -> viewModel.updateVaultSettings { it.copy(systemCalendarSyncEnabled = v) } }
        )

        SettingsToggleRow(
            title = "Anonymizovat názvy při exportu do systémového kalendáře",
            subtitle = "Nahradí citlivý název události textem 'OpenVault událost'",
            checked = settings.anonymizeExportedEvents,
            onCheckedChange = { v -> viewModel.updateVaultSettings { it.copy(anonymizeExportedEvents = v) } }
        )

        if (availableCalendars.isNotEmpty()) {
            Text("Cílový systémový kalendář:", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                availableCalendars.forEach { cal ->
                    FilterChip(
                        selected = settings.targetSystemCalendarId == cal.id,
                        onClick = {
                            viewModel.updateVaultSettings { it.copy(targetSystemCalendarId = cal.id) }
                        },
                        label = { Text(cal.displayName) }
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { viewModel.importEventsFromSystemCalendar() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Importovat nadcházející události ze systémového kalendáře")
        }
    }
}

/**
 * Screen #32: Nastavení – Rychlé akce
 */
@Composable
fun SettingsQuickActionsScreen(viewModel: OpenVaultViewModel) {
    val settings by viewModel.settings.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_quick_actions_screen")
    ) {
        Text(
            text = "Rychlé akce, gesta a widgety",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Text("Výchozí akce při dlouhém stisku FAB:", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            FabActionType.entries.forEach { act ->
                FilterChip(
                    selected = settings.fabLongPressAction == act,
                    onClick = { viewModel.updateVaultSettings { it.copy(fabLongPressAction = act) } },
                    label = { Text(act.labelCs) }
                )
            }
        }

        SettingsToggleRow(
            title = "Trvalá tichá notifikace pro rychlé akce",
            subtitle = "Zobrazuje tlačítka + Poznámka, + Událost a Zamknout hned, dokud je trezor odemčený",
            checked = settings.persistentNotificationEnabled,
            onCheckedChange = { v ->
                viewModel.updateVaultSettings { it.copy(persistentNotificationEnabled = v) }
            }
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Přehled registrovaných widgetů a klávesových zkratek",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("• Widget 1×1 Quick Note: Okamžité otevření editoru nové poznámky.")
                Text("• Widget 2×2 Quick Actions: Poznámka, Heslo, Událost a okamžité uzamčení.")
                Text("• Widget 4×2 Today Agenda: Přehled dnešních šifrovaných událostí.")
                Text("• Widget 4×2 Recent Notes: Rychlý přístup k posledním poznámkám.")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Klávesové zkratky: Ctrl+N (Poznámka), Ctrl+E (Událost), Ctrl+K/F (Quick Capture), Ctrl+S (Sync), Ctrl+L (Zamknout), Esc (Zpět).",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * Screen #33: Nastavení – Audit a logy
 */
@Composable
fun SettingsAuditScreen(viewModel: OpenVaultViewModel) {
    val settings by viewModel.settings.collectAsState()
    val entries by viewModel.auditEntries.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_audit_screen")
    ) {
        Text(
            text = "Konfigurace Append-Only Audit Logu",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text("Celkový počet šifrovaných auditních záznamů: ${entries.size}")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.auditVerboseLevel == 1,
                onClick = { viewModel.updateVaultSettings { it.copy(auditVerboseLevel = 1) } },
                label = { Text("Základní bezpečnostní události") }
            )
            FilterChip(
                selected = settings.auditVerboseLevel == 2,
                onClick = { viewModel.updateVaultSettings { it.copy(auditVerboseLevel = 2) } },
                label = { Text("Kompletní E2E audit (Autentizace + CRUD + Sync)") }
            )
        }

        Button(
            onClick = { viewModel.navigateTo(VaultScreen.AuditLog) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Otevřít kompletní šifrovaný Audit Log")
        }
    }
}

/**
 * Screen #34: Nastavení – O aplikaci
 */
@Composable
fun SettingsAboutScreen(viewModel: OpenVaultViewModel) {
    val diagText by viewModel.supabaseDiagnosticText.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_about_screen")
    ) {
        Text(
            text = "OpenVault v1.0.0 (E2E Zero-Knowledge)",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Kryptografická specifikace:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("• Šifrování objektů a VFS: AES-256-GCM (96-bit unikátní nonce, 128-bit tag)")
                Text("• Odvození klíče (KDF): Argon2id (64 MB, 3 iterace, 4 vlákna) + PBKDF2-HMAC-SHA256 (210 000 iterací)")
                Text("• Lokální databáze: Room + SQLCipher 256-bit")
                Text("• Hardwarové úložiště: Android Keystore (StrongBox / TEE) + BiometricPrompt.CryptoObject")
                Text("• Cloud Backend: Supabase PostgREST + Storage (vault-files) + Realtime s RLS hlavičkou x-vault-id")
                Text(
                    text = "Endpoint: ${SupabaseConfig.SUPABASE_URL}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (diagText != null) {
            Text(
                text = diagText!!,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Button(
            onClick = { viewModel.pingSupabaseDiagnostics() },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ping_supabase_diagnostics_btn")
        ) {
            Text("Otestovat spojení a hlavičku x-vault-id se Supabase")
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
