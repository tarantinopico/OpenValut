package com.openvault.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.openvault.app.security.OvaultImportMode
import com.openvault.app.security.PasswordAnalyzer
import com.openvault.app.security.PasswordStrengthLevel
import com.openvault.app.security.UnlockResult
import com.openvault.app.security.VaultLockState
import com.openvault.app.ui.OpenVaultViewModel
import com.openvault.app.ui.navigation.VaultScreen
import com.openvault.app.ui.theme.JetBrainsMonoFontFamily
import com.openvault.app.ui.theme.StatusErrorRed
import com.openvault.app.ui.theme.StatusSuccessEmerald
import com.openvault.app.ui.theme.StatusWarningAmber
import kotlinx.coroutines.delay

/**
 * Screen #1: Onboarding – Welcome
 */
@Composable
fun OnboardingWelcomeScreen(viewModel: OpenVaultViewModel) {
    val context = LocalContext.current
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restorePassword by remember { mutableStateOf("") }
    var restoreVaultId by remember { mutableStateOf("") }
    var importedFileBytes by remember { mutableStateOf<ByteArray?>(null) }

    val openOvaultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null) {
                importedFileBytes = bytes
                showRestoreDialog = true
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("onboarding_welcome_screen")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "OpenVault E2E Shield",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "OpenVault",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "100% End-to-End šifrovaný osobní digitální trezor",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(18.dp)
            ) {
                Text(
                    text = "Zero-Knowledge Architektura",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "• AES-256-GCM šifrování přímo na zařízení s unikátním 96-bit nonce pro každý objekt a soubor.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Argon2id + PBKDF2-HMAC-SHA256 (210 000 iterací) pro odvození Master Key.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Lokální SQLCipher šifrovaná databáze + realtime synchronizace přes Supabase (x-vault-id RLS izolace).",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = { viewModel.navigateTo(VaultScreen.OnboardingMasterPassword) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("welcome_create_vault_btn")
        ) {
            Icon(Icons.Default.Key, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Vytvořit nový šifrovaný trezor")
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showRestoreDialog = !showRestoreDialog },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("welcome_restore_vault_btn")
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Obnovit existující trezor (.ovault / Vault ID)")
        }

        if (showRestoreDialog) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Obnova trezoru nebo spárování přes Vault ID",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = restoreVaultId,
                        onValueChange = { restoreVaultId = it },
                        label = { Text("Existující Vault ID (UUID v4 – volitelné při .ovault)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = restorePassword,
                        onValueChange = { restorePassword = it },
                        label = { Text("Master Password trezoru / zálohy") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { openOvaultLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (importedFileBytes != null) "Soubor .ovault načten" else "Vybrat .ovault")
                        }
                        Button(
                            onClick = {
                                val bytes = importedFileBytes
                                if (bytes != null && restorePassword.isNotBlank()) {
                                    viewModel.importOvaultContainer(
                                        bytes = bytes,
                                        password = restorePassword,
                                        mode = OvaultImportMode.REPLACE,
                                        initializeIfLocked = true
                                    )
                                } else if (restoreVaultId.isNotBlank() && restorePassword.isNotBlank()) {
                                    viewModel.createNewVault(
                                        masterPassword = restorePassword,
                                        customVaultId = restoreVaultId.trim(),
                                        proceedToBiometrics = false
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Obnovit trezor")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen #2: Onboarding – Vytvoření Master hesla
 */
@Composable
fun OnboardingMasterPasswordScreen(viewModel: OpenVaultViewModel) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val strength = remember(password) { PasswordAnalyzer.analyzePassword(password) }
    val passwordsMatch = password.isNotEmpty() && password == confirmPassword
    val canProceed = password.length >= 8 && passwordsMatch

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("onboarding_master_password_screen")
    ) {
        Text(
            text = "Vytvořte Master Password",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Master Password slouží k odvození 256-bitového Master Key (Argon2id + PBKDF2). Nikdy se nikam neukládá ani neodesílá na server.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Master Password (min. 8 znaků, doporučeno 12+)") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Zobrazit nebo skrýt heslo"
                    )
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("master_password_input")
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Potvrzení Master Password") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("master_password_confirm_input")
        )

        // Live entropy & strength card
        val meterColor = when (strength.level) {
            PasswordStrengthLevel.WEAK -> StatusErrorRed
            PasswordStrengthLevel.MEDIUM -> StatusWarningAmber
            PasswordStrengthLevel.STRONG, PasswordStrengthLevel.CRYPTOGRAPHIC -> StatusSuccessEmerald
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, meterColor.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(14.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Síla hesla: ${strength.level.labelCs}",
                        style = MaterialTheme.typography.titleMedium,
                        color = meterColor
                    )
                    Text(
                        text = "${strength.entropyBits} bitů (${strength.score}/100)",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                LinearProgressIndicator(
                    progress = { (strength.score / 100f).coerceIn(0f, 1f) },
                    color = meterColor,
                    modifier = Modifier.fillMaxWidth()
                )
                strength.warnings.forEach { warn ->
                    Text(
                        text = "• $warn",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = StatusWarningAmber.copy(alpha = 0.12f)),
            border = BorderStroke(1.dp, StatusWarningAmber.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "DŮLEŽITÉ: Vzhledem k Zero-Knowledge E2E architektuře nelze zapomenuté Master Password nijak obnovit ze serveru.",
                style = MaterialTheme.typography.bodyMedium,
                color = StatusWarningAmber,
                modifier = Modifier.padding(14.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                viewModel.createNewVault(
                    masterPassword = password,
                    proceedToBiometrics = true
                )
            },
            enabled = canProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("create_master_password_submit_btn")
        ) {
            Text("Vygenerovat Vault Key a pokračovat")
        }
    }
}

/**
 * Screen #3: Onboarding – Zapnutí biometrie
 */
@Composable
fun OnboardingBiometricsScreen(viewModel: OpenVaultViewModel) {
    val context = LocalContext.current
    val vaultId = viewModel.container.securityGate.getVaultIdOrNull() ?: ""
    val bioAvailable = remember { viewModel.container.biometricKeyManager.isBiometricHardwareAvailable() }
    val strongBox = remember { viewModel.container.biometricKeyManager.isStrongBoxSupported() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("onboarding_biometrics_screen")
    ) {
        Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = "Biometrie",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Hardwarové biometrické odemykání",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Vault Key bude zabalen klíčem z Android Keystore (${if (strongBox) "StrongBox HSM" else "Hardware TEE"}) pomocí BiometricPrompt.CryptoObject.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))
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
                    text = "Váš unikátní identifikátor trezoru (x-vault-id):",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = vaultId,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Poznámka: Po každém restartu zařízení bude z bezpečnostních důvodů vyžadováno zadání Master Password.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (bioAvailable && context is FragmentActivity) {
            Button(
                onClick = {
                    val cryptoObj = viewModel.getBiometricEncryptCryptoObject()
                    if (cryptoObj != null) {
                        val executor = ContextCompat.getMainExecutor(context)
                        val prompt = BiometricPrompt(
                            context,
                            executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    val cipher = result.cryptoObject?.cipher
                                    if (cipher != null) {
                                        viewModel.completeBiometricEnrollment(cipher)
                                    }
                                    viewModel.finishOnboardingToDashboard()
                                }
                            }
                        )
                        val info = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Aktivovat biometrii pro OpenVault")
                            .setSubtitle("Potvrďte otisk prstu pro zabalení Vault Key v Android Keystore")
                            .setNegativeButtonText("Přeskočit")
                            .build()
                        prompt.authenticate(info, cryptoObj)
                    } else {
                        viewModel.finishOnboardingToDashboard()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("enable_biometrics_btn")
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Zapnout biometrické odemykání")
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        OutlinedButton(
            onClick = { viewModel.finishOnboardingToDashboard() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("skip_biometrics_btn")
        ) {
            Text("Pokračovat do trezoru pouze s Master heslem")
        }
    }
}

/**
 * Screen #4: Odemknutí – Biometrie
 */
@Composable
fun UnlockBiometricScreen(viewModel: OpenVaultViewModel) {
    val context = LocalContext.current
    val vaultId = viewModel.container.securityGate.getVaultIdOrNull() ?: ""

    fun triggerBiometricPrompt() {
        val activity = context as? FragmentActivity ?: return
        val cryptoObject = viewModel.getBiometricDecryptCryptoObject()
        if (cryptoObject == null) {
            viewModel.navigateTo(VaultScreen.UnlockPassword, clearStack = true)
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val cipher = result.cryptoObject?.cipher
                    if (cipher != null) {
                        viewModel.unlockWithBiometricAuthenticatedCipher(cipher) {
                            viewModel.navigateTo(VaultScreen.UnlockPassword, clearStack = true)
                        }
                    } else {
                        viewModel.navigateTo(VaultScreen.UnlockPassword, clearStack = true)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    viewModel.navigateTo(VaultScreen.UnlockPassword, clearStack = true)
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Odemknout trezor OpenVault")
            .setSubtitle("Ověřte biometrii pro dešifrování Vault Key")
            .setNegativeButtonText("Zadat Master heslo")
            .build()
        prompt.authenticate(promptInfo, cryptoObject)
    }

    LaunchedEffect(Unit) {
        triggerBiometricPrompt()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("unlock_biometric_screen")
    ) {
        Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = "Odemknout biometrií",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "OpenVault je uzamčen",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Vault ID: ${vaultId.take(13)}…",
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { triggerBiometricPrompt() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("retry_biometric_btn")
        ) {
            Icon(Icons.Default.Fingerprint, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ověřit otisk prstu (CryptoObject)")
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = { viewModel.navigateTo(VaultScreen.UnlockPassword, clearStack = true) },
            modifier = Modifier.testTag("switch_to_password_unlock_btn")
        ) {
            Text("Přepnout na zadání Master Password")
        }
    }
}

/**
 * Screen #5: Odemknutí – Zadání hesla
 */
@Composable
fun UnlockPasswordScreen(
    viewModel: OpenVaultViewModel,
    lockState: VaultLockState.Locked?
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var remainingLockoutSec by remember { mutableLongStateOf(0L) }

    val lockoutUntil = lockState?.lockoutUntilMs ?: 0L
    LaunchedEffect(lockoutUntil) {
        while (true) {
            val rem = (lockoutUntil - System.currentTimeMillis()) / 1000L
            remainingLockoutSec = rem.coerceAtLeast(0L)
            if (rem <= 0L) break
            delay(500L)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("unlock_password_screen")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Uzamčeno",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Odemknout OpenVault",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Vault ID: ${(lockState?.vaultId ?: "").take(13)}…",
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (lockState?.requirePasswordAfterReboot == true && lockState.biometricEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = StatusWarningAmber.copy(alpha = 0.14f)),
                border = BorderStroke(1.dp, StatusWarningAmber.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Po restartu zařízení nebo nové inicializaci relace je z bezpečnostních důvodů vyžadováno zadání Master Password.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StatusWarningAmber,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Master Password") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Zobrazit heslo"
                    )
                }
            },
            singleLine = true,
            enabled = remainingLockoutSec == 0L,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("unlock_password_input")
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage!!,
                style = MaterialTheme.typography.bodyMedium,
                color = StatusErrorRed,
                fontWeight = FontWeight.Medium
            )
        }

        if (remainingLockoutSec > 0L) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ochrana proti brute-force: vyčkejte ${remainingLockoutSec} s (neúspěšných pokusů: ${lockState?.failedAttempts ?: 0})",
                style = MaterialTheme.typography.bodyMedium,
                color = StatusWarningAmber
            )
        } else if ((lockState?.failedAttempts ?: 0) > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Neúspěšné pokusy o odemknutí: ${lockState?.failedAttempts}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = {
                viewModel.unlockWithMasterPassword(password) { result ->
                    when (result) {
                        is UnlockResult.InvalidCredentials -> {
                            errorMessage = "Nesprávné Master Password (selhalo ověření AES-256-GCM tagu)."
                        }
                        is UnlockResult.RateLimited -> {
                            remainingLockoutSec = (result.remainingMs / 1000L).coerceAtLeast(1L)
                        }
                        else -> Unit
                    }
                }
            },
            enabled = password.isNotEmpty() && remainingLockoutSec == 0L,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("unlock_password_submit_btn")
        ) {
            Icon(Icons.Default.Key, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Dešifrovat a odemknout trezor")
        }

        if (lockState?.biometricEnabled == true && !lockState.requirePasswordAfterReboot) {
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = { viewModel.navigateTo(VaultScreen.UnlockBiometric, clearStack = true) }
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Použít biometrické odemykání")
            }
        }
    }
}
