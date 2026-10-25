package com.openvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openvault.app.domain.model.AuditCategory
import com.openvault.app.domain.model.AuditEntry
import com.openvault.app.ui.OpenVaultViewModel
import com.openvault.app.ui.components.EmptyStateView
import com.openvault.app.ui.theme.JetBrainsMonoFontFamily
import com.openvault.app.ui.theme.LocalOpenVaultSpacing
import com.openvault.app.ui.theme.StatusErrorRed
import com.openvault.app.ui.theme.StatusSuccessEmerald
import com.openvault.app.ui.theme.StatusWarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Screen #24: Šifrovaný Append-Only Audit Log
 */
@Composable
fun AuditLogScreen(viewModel: OpenVaultViewModel) {
    val entries by viewModel.auditEntries.collectAsState()
    val spacing = LocalOpenVaultSpacing.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<AuditCategory?>(null) }
    var selectedEntryDetail by remember { mutableStateOf<AuditEntry?>(null) }

    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()) }

    val filtered = remember(entries, searchQuery, selectedCategory) {
        entries.filter { e ->
            val matchesCat = selectedCategory == null || e.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                e.actionType.contains(searchQuery, ignoreCase = true) ||
                e.summary.contains(searchQuery, ignoreCase = true) ||
                (e.targetObjectId?.contains(searchQuery, ignoreCase = true) == true)
            matchesCat && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.screenHorizontal, vertical = 10.dp)
            .testTag("audit_log_screen")
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Append-Only E2E Audit Log (${filtered.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Záznamy nelze upravit ani jednotlivě smazat • Nulový únik tajemství",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = {
                    val jsonExport = Json { prettyPrint = true }.encodeToString(filtered)
                    viewModel.copySecretToClipboard("Export Audit Logu (JSON)", jsonExport)
                }
            ) {
                Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export JSON")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Filtrovat auditní události podle akce nebo ID…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("Vše") }
            )
            AuditCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                    label = { Text(cat.labelCs) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.History,
                title = "Žádné záznamy v auditním protokolu",
                subtitle = "Každá bezpečnostní a datová událost je automaticky zaznamenána a zašifrována."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { entry ->
                    Card(
                        onClick = { selectedEntryDetail = entry },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "[${entry.category.labelCs}] ${entry.actionType}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = dateFormatter.format(Date(entry.createdAt)),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = entry.summary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    if (selectedEntryDetail != null) {
        val e = selectedEntryDetail!!
        AlertDialog(
            onDismissRequest = { selectedEntryDetail = null },
            title = { Text(e.actionType) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Kategorie: ${e.category.labelCs}")
                    Text("Čas: ${dateFormatter.format(Date(e.createdAt))}")
                    Text("Cílový objekt ID: ${e.targetObjectId ?: "Globální událost trezoru"}")
                    Text("Zařízení: ${e.deviceInfo}")
                    Text("Synchronizováno do Supabase vault_audit: ${if (e.isSynced) "Ano" else "Čeká ve frontě"}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Souhrn: ${e.summary}", fontWeight = FontWeight.Medium)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedEntryDetail = null }) {
                    Text("Zavřít")
                }
            }
        )
    }
}

/**
 * Screen #25: Bezpečnostní centrum
 */
@Composable
fun SecurityCenterScreen(viewModel: OpenVaultViewModel) {
    val report by viewModel.securityReport.collectAsState()
    val integrity by viewModel.deviceIntegrity.collectAsState()
    val isBusy by viewModel.isBusyOperation.collectAsState()
    val busyStep by viewModel.busyOperationStep.collectAsState()
    val vaultId = viewModel.container.securityGate.getVaultIdOrNull() ?: "N/A"

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (report == null) {
            viewModel.runFullSecurityIntegrityCheck()
        }
    }

    val score = report?.overallScore ?: 92
    val scoreColor = when {
        score < 55 -> StatusErrorRed
        score < 80 -> StatusWarningAmber
        else -> StatusSuccessEmerald
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("security_center_screen")
    ) {
        // Security Score Hero Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.5.dp, scoreColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(18.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Celkové Security Score",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$score %",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp,
                            color = scoreColor,
                            modifier = Modifier.testTag("security_score_value")
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = scoreColor,
                        modifier = Modifier.size(48.dp)
                    )
                }

                LinearProgressIndicator(
                    progress = { (score / 100f).coerceIn(0f, 1f) },
                    color = scoreColor,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isBusy) {
                    Text(
                        text = busyStep,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(
                    onClick = { viewModel.runFullSecurityIntegrityCheck() },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("run_integrity_check_btn")
                ) {
                    Icon(Icons.Default.Security, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Spustit kontrolu integrity trezoru (GCM + SHA-256)")
                }
            }
        }

        // Cryptographic Integrity Report Card
        if (report != null) {
            val r = report!!
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
                        text = "Výsledek kontroly kryptografické integrity",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Ověřených šifrovaných objektů: ${r.verifiedObjectsCount} / ${r.totalObjectsChecked}")
                    Text(
                        text = "Poškozené GCM tagy / objekty: ${r.corruptedObjectIds.size}",
                        color = if (r.corruptedObjectIds.isEmpty()) StatusSuccessEmerald else StatusErrorRed
                    )
                    Text("Slabá hesla (< 50 % entropie): ${r.weakPasswordsCount}")
                    Text("Duplicitní hesla napříč trezorem: ${r.duplicatePasswordsCount}")
                    Text("Expirovaná nebo stará hesla (> 180 dní): ${r.expiredOrOldPasswordsCount}")
                    Text(
                        text = "HMAC-SHA256 otisk trezoru: ${r.computedIntegrityHashHex.take(24)}…",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Device & Hardware Posture Card
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
                    text = "Integrita zařízení a hardwarového úložiště klíčů",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("Aktivní Vault ID: $vaultId", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
                Text("Android Keystore StrongBox HSM: ${if (integrity.isStrongBoxAvailable) "Dostupný (Hardware)" else "Standardní TEE fallback"}")
                Text("Detekce Rootu (su / Magisk): ${if (integrity.isRootDetected) "RIZIKO ZJIŠTĚNO" else "Čisté (Nenalezeno)"}")
                Text("Detekce Hook frameworků (Frida / Xposed): ${if (integrity.isHookFrameworkDetected) "RIZIKO ZJIŠTĚNO" else "Čisté"}")
            }
        }

        // Key Management Actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { showChangePasswordDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("open_change_master_password_btn")
            ) {
                Icon(Icons.Default.Key, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Změnit Master heslo")
            }

            Button(
                onClick = { showWipeConfirmDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = StatusErrorRed),
                modifier = Modifier
                    .weight(1f)
                    .testTag("cryptographic_wipe_btn")
            ) {
                Icon(Icons.Default.DeleteForever, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Nouzový Wipe", color = Color.White)
            }
        }
    }

    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Bezpečná změna Master Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Vault Key (256-bit) bude rozbalen současným heslem a okamžitě zabalen novým Master Key (Argon2id + PBKDF2 210k).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = oldPwd,
                        onValueChange = { oldPwd = it },
                        label = { Text("Současné Master Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPwd,
                        onValueChange = { newPwd = it },
                        label = { Text("Nové Master Password (min. 8 znaků)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (oldPwd.isNotBlank() && newPwd.length >= 8) {
                            viewModel.changeMasterPassword(oldPwd, newPwd) {
                                showChangePasswordDialog = false
                                oldPwd = ""
                                newPwd = ""
                            }
                        }
                    }
                ) {
                    Text("Přešifrovat Vault Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) {
                    Text("Zrušit")
                }
            }
        )
    }

    if (showWipeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmDialog = false },
            title = { Text("Cryptographic Wipe – Trvalé smazání") },
            text = {
                Text("Tato akce okamžitě přepíše Vault Key v RAM nulami, odstraní klíče z Android Keystore, vymaže kryptografické obálky a odstraní lokální SQLCipher databázi i VFS bloby.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirmDialog = false
                        viewModel.wipeEntireVaultCryptographically()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusErrorRed)
                ) {
                    Text("Potvrdit Cryptographic Wipe", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirmDialog = false }) {
                    Text("Zrušit")
                }
            }
        )
    }
}
