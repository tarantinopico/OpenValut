package com.openvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openvault.app.security.PasswordAnalyzer
import com.openvault.app.security.PasswordGeneratorConfig
import com.openvault.app.security.TotpGenerator
import com.openvault.app.ui.OpenVaultViewModel
import com.openvault.app.ui.components.EmptyStateView
import com.openvault.app.ui.components.SwipeableVaultCard
import com.openvault.app.ui.navigation.VaultModalSheet
import com.openvault.app.ui.navigation.VaultScreen
import com.openvault.app.ui.theme.JetBrainsMonoFontFamily
import com.openvault.app.ui.theme.LocalOpenVaultSpacing
import com.openvault.app.ui.theme.StatusErrorRed
import com.openvault.app.ui.theme.StatusSuccessEmerald
import com.openvault.app.ui.theme.StatusWarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Screen #15: Hesla – Seznam
 */
@Composable
fun PasswordsListScreen(viewModel: OpenVaultViewModel) {
    val passwords by viewModel.passwords.collectAsState()
    val clipboardCountdown by viewModel.clipboardRemainingSeconds.collectAsState()
    val spacing = LocalOpenVaultSpacing.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var tickNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            tickNowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val categories = listOf("Bankovnictví", "Sociální sítě", "Práce", "E-shopy", "Krypto", "Ostatní")
    val filtered = remember(passwords, searchQuery, selectedCategory) {
        passwords.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.username.contains(searchQuery, ignoreCase = true) ||
                item.url.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCategory == null || item.category == selectedCategory
            matchesSearch && matchesCat
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.screenHorizontal, vertical = 10.dp)
            .testTag("passwords_list_screen")
    ) {
        if (clipboardCountdown > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(10.dp)
                ) {
                    Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Schránka bude automaticky vymazána za ${clipboardCountdown} s",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Hledat heslo podle názvu, účtu nebo domény…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("passwords_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("Vše (${passwords.size})") }
            )
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                    label = { Text(cat) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Key,
                title = "Žádná uložená hesla",
                subtitle = "Uložte přihlašovací údaje s RFC 6238 TOTP autentikátorem a kontrolou entropie.",
                actionLabel = "Nové heslo + TOTP",
                onAction = { viewModel.navigateTo(VaultScreen.PasswordDetailEditor()) }
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.itemSpacing),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { item ->
                    val strengthColor = when {
                        item.strengthScore < 45 -> StatusErrorRed
                        item.strengthScore < 72 -> StatusWarningAmber
                        else -> StatusSuccessEmerald
                    }
                    val totpState = remember(item.totpSecret, tickNowMs) {
                        item.totpSecret?.let { TotpGenerator.generateTotpState(it, tickNowMs) }
                    }

                    SwipeableVaultCard(
                        onClick = { viewModel.navigateTo(VaultScreen.PasswordDetailEditor(item.id)) },
                        onLongClick = {
                            viewModel.openModalSheet(
                                VaultModalSheet.ContextMenu(item.id, item.title, "password")
                            )
                        },
                        onSwipeLeftAction = {
                            viewModel.copySecretToClipboard("Heslo (${item.title})", item.password)
                        },
                        onSwipeRightFavorite = { viewModel.togglePasswordFavorite(item.id) }
                    ) {
                        Column(modifier = Modifier.padding(spacing.cardPadding)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (item.isFavorite) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Oblíbené",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${item.username} • ${item.category}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = strengthColor.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, strengthColor.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "${item.strengthScore}%",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = strengthColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.copySecretToClipboard("Heslo (${item.title})", item.password)
                                    },
                                    modifier = Modifier.testTag("copy_password_btn_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Zkopírovat heslo",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (totpState != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    onClick = {
                                        viewModel.copySecretToClipboard("TOTP (${item.title})", totpState.code)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "TOTP: ${totpState.formattedCode}",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${totpState.remainingSeconds} s 📋",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }
}

/**
 * Screen #16: Heslo – Detail & Editor (with live RFC 6238 TOTP authenticator, Password/Passphrase generator, and history)
 */
@Composable
fun PasswordDetailEditorScreen(
    viewModel: OpenVaultViewModel,
    passwordId: String?
) {
    val passwords by viewModel.passwords.collectAsState()
    val existing = remember(passwords, passwordId) { passwords.find { it.id == passwordId } }

    var title by remember(existing) { mutableStateOf(existing?.title ?: "") }
    var username by remember(existing) { mutableStateOf(existing?.username ?: "") }
    var password by remember(existing) { mutableStateOf(existing?.password ?: "") }
    var url by remember(existing) { mutableStateOf(existing?.url ?: "") }
    var totpSecret by remember(existing) { mutableStateOf(existing?.totpSecret ?: "") }
    var category by remember(existing) { mutableStateOf(existing?.category ?: "Ostatní") }
    var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }
    var isFavorite by remember(existing) { mutableStateOf(existing?.isFavorite ?: false) }
    var showPassword by remember { mutableStateOf(false) }

    // Generator controls
    var showGeneratorPanel by remember { mutableStateOf(false) }
    var genLength by remember { mutableFloatStateOf(20f) }
    var genUpper by remember { mutableStateOf(true) }
    var genLower by remember { mutableStateOf(true) }
    var genDigits by remember { mutableStateOf(true) }
    var genSymbols by remember { mutableStateOf(true) }
    var genExcludeAmbiguous by remember { mutableStateOf(false) }
    var genPassphraseMode by remember { mutableStateOf(false) }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val liveTotp = remember(totpSecret, nowMs) {
        if (totpSecret.isBlank()) null else TotpGenerator.generateTotpState(totpSecret, nowMs)
    }
    val strength = remember(password) { PasswordAnalyzer.analyzePassword(password) }
    val categories = listOf("Bankovnictví", "Sociální sítě", "Práce", "E-shopy", "Krypto", "Ostatní")
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("password_detail_editor_screen")
    ) {
        // Live RFC 6238 TOTP Authenticator Card if secret is provided
        if (liveTotp != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Živý TOTP Autentikátor (RFC 6238)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = liveTotp.formattedCode,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("live_totp_code_text")
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                            CircularProgressIndicator(
                                progress = { liveTotp.progress },
                                color = if (liveTotp.remainingSeconds <= 5) StatusErrorRed
                                else MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp,
                                modifier = Modifier.fillMaxSize()
                            )
                            Text(
                                text = "${liveTotp.remainingSeconds}s",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                viewModel.copySecretToClipboard("TOTP kód ($title)", liveTotp.code)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Zkopírovat TOTP kód",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Název služby / Titulek") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password_title_input")
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Uživatelské jméno / E-mail") },
            trailingIcon = {
                IconButton(onClick = { viewModel.copySecretToClipboard("Uživatelské jméno", username) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Kopírovat jméno")
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password_username_input")
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Heslo (AES-256-GCM)") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Zobrazit nebo skrýt heslo"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.copySecretToClipboard("Heslo ($title)", password) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Kopírovat heslo (30s vymazání)",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password_secret_input")
        )

        // Entropy & Generator toggle bar
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Entropie: ${strength.entropyBits} bitů (${strength.level.labelCs}, ${strength.score}%)",
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedButton(
                onClick = { showGeneratorPanel = !showGeneratorPanel },
                modifier = Modifier.testTag("toggle_password_generator_btn")
            ) {
                Icon(Icons.Default.AutoFixHigh, null, Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generátor hesel")
            }
        }

        LinearProgressIndicator(
            progress = { (strength.score / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )

        if (showGeneratorPanel) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Režim Passphrase (víceslovné heslo)", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = genPassphraseMode, onCheckedChange = { genPassphraseMode = it })
                    }
                    if (!genPassphraseMode) {
                        Text(
                            text = "Délka hesla: ${genLength.toInt()} znaků",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = genLength,
                            onValueChange = { genLength = it },
                            valueRange = 8f..64f
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            FilterChip(selected = genUpper, onClick = { genUpper = !genUpper }, label = { Text("A–Z") })
                            FilterChip(selected = genLower, onClick = { genLower = !genLower }, label = { Text("a–z") })
                            FilterChip(selected = genDigits, onClick = { genDigits = !genDigits }, label = { Text("0–9") })
                            FilterChip(selected = genSymbols, onClick = { genSymbols = !genSymbols }, label = { Text("!@#") })
                            FilterChip(
                                selected = genExcludeAmbiguous,
                                onClick = { genExcludeAmbiguous = !genExcludeAmbiguous },
                                label = { Text("Bez O0Il1") }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            password = PasswordAnalyzer.generatePassword(
                                PasswordGeneratorConfig(
                                    length = genLength.toInt(),
                                    includeUppercase = genUpper,
                                    includeLowercase = genLower,
                                    includeDigits = genDigits,
                                    includeSymbols = genSymbols,
                                    excludeAmbiguous = genExcludeAmbiguous,
                                    passphraseMode = genPassphraseMode
                                )
                            )
                            showPassword = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_password_now_btn")
                    ) {
                        Text("Vygenerovat kryptograficky silné heslo")
                    }
                }
            }
        }

        OutlinedTextField(
            value = totpSecret,
            onValueChange = { totpSecret = it },
            label = { Text("TOTP tajemství (Base32 nebo otpauth:// URI – volitelné)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password_totp_secret_input")
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL / Doména (např. https://github.com)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Kategorie:", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { category = cat },
                    label = { Text(cat) }
                )
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Šifrovaná poznámka k účtu / záložní kódy") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        if (existing != null && existing.passwordHistory.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Historie dřívějších hesel (${existing.passwordHistory.size}):",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    existing.passwordHistory.take(5).forEach { h ->
                        Text(
                            text = "• ${dateFormatter.format(Date(h.changedAt))}: ${h.password.take(3)}••••••",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (existing != null) {
                OutlinedButton(
                    onClick = {
                        viewModel.deletePassword(existing.id)
                        viewModel.navigateBack()
                    }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = StatusErrorRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Smazat")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Button(
                onClick = {
                    viewModel.savePassword(
                        id = existing?.id,
                        title = title,
                        username = username,
                        password = password,
                        url = url,
                        totpSecret = totpSecret,
                        notes = notes,
                        category = category,
                        isFavorite = isFavorite
                    ) {
                        viewModel.navigateBack()
                    }
                },
                enabled = title.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.testTag("password_save_btn")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Zašifrovat a uložit")
            }
        }
    }
}
