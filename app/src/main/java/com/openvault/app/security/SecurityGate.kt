package com.openvault.app.security

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.biometric.BiometricPrompt
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class VaultLockState {
    data object Uninitialized : VaultLockState()
    data class Locked(
        val vaultId: String,
        val biometricEnabled: Boolean,
        val requirePasswordAfterReboot: Boolean,
        val failedAttempts: Int = 0,
        val lockoutUntilMs: Long = 0L
    ) : VaultLockState()

    data class Unlocked(
        val vaultId: String,
        val isDecoyMode: Boolean = false
    ) : VaultLockState()
}

enum class DuressAction {
    DECOY_VAULT,
    CRYPTOGRAPHIC_WIPE
}

sealed class UnlockResult {
    data class Success(val vaultId: String, val isDecoyMode: Boolean = false) : UnlockResult()
    data object InvalidCredentials : UnlockResult()
    data class RateLimited(val remainingMs: Long) : UnlockResult()
    data object BiometricRequiresPasswordAfterReboot : UnlockResult()
    data object DuressWipeTriggered : UnlockResult()
}

class SecurityGate(
    private val context: Context,
    private val biometricKeyManager: BiometricKeyManager = BiometricKeyManager(context),
    customPreferences: SharedPreferences? = null
) {
    companion object {
        private const val PREFS_FILE_NAME = "openvault_crypto_envelopes"
        private const val KEY_VAULT_ID = "vault_id"
        private const val KEY_KDF_SALT_HEX = "kdf_salt_hex"
        private const val KEY_KDF_ITERATIONS = "kdf_iterations"
        private const val KEY_WRAPPED_VK_MASTER_HEX = "wrapped_vk_master_hex"
        private const val KEY_WRAPPED_VK_MASTER_NONCE_HEX = "wrapped_vk_master_nonce_hex"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_WRAPPED_VK_BIO_HEX = "wrapped_vk_biometric_hex"
        private const val KEY_WRAPPED_VK_BIO_IV_HEX = "wrapped_vk_biometric_iv_hex"
        private const val KEY_LAST_BOOT_COUNT = "last_boot_count"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL_MS = "lockout_until_ms"
        private const val KEY_DURESS_ENABLED = "duress_enabled"
        private const val KEY_DURESS_ACTION = "duress_action"
        private const val KEY_DURESS_SALT_HEX = "duress_salt_hex"
        private const val KEY_WRAPPED_DURESS_VK_HEX = "wrapped_duress_vk_hex"
        private const val KEY_WRAPPED_DURESS_NONCE_HEX = "wrapped_duress_nonce_hex"
    }

    private val prefs: SharedPreferences = customPreferences ?: createEncryptedPrefs(context)

    @Volatile
    private var activeVaultKey: ByteArray? = null

    @Volatile
    private var isBootUnlockedInSession: Boolean = false

    @Volatile
    private var isDecoySession: Boolean = false

    private var onWipeCallback: (() -> Unit)? = null

    private val _lockState = MutableStateFlow<VaultLockState>(computeInitialLockState())
    val lockState: StateFlow<VaultLockState> = _lockState.asStateFlow()

    private val _lastActivityTimestampMs = MutableStateFlow(System.currentTimeMillis())
    val lastActivityTimestampMs: StateFlow<Long> = _lastActivityTimestampMs.asStateFlow()

    private fun createEncryptedPrefs(appContext: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val encrypted = EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            // Verify read does not throw SecurityException from stale keyset
            encrypted.getString(KEY_VAULT_ID, null)
            encrypted
        } catch (_: Throwable) {
            runCatching { appContext.deleteSharedPreferences(PREFS_FILE_NAME) }
            appContext.getSharedPreferences("${PREFS_FILE_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }

    fun setOnCryptographicWipeListener(listener: () -> Unit) {
        onWipeCallback = listener
    }

    fun recordUserActivity() {
        _lastActivityTimestampMs.value = System.currentTimeMillis()
    }

    fun isVaultInitialized(): Boolean = runCatching {
        prefs.getString(KEY_VAULT_ID, null) != null &&
            prefs.getString(KEY_WRAPPED_VK_MASTER_HEX, null) != null
    }.getOrDefault(false)

    fun isUnlocked(): Boolean = activeVaultKey != null && _lockState.value is VaultLockState.Unlocked

    fun isDecoyModeActive(): Boolean = isDecoySession

    fun getVaultIdOrNull(): String? = runCatching { prefs.getString(KEY_VAULT_ID, null) }.getOrNull()

    fun requireVaultId(): String =
        getVaultIdOrNull() ?: throw VaultLockedException("Vault ID není inicializováno")

    fun isBiometricEnabled(): Boolean = runCatching { prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false) }.getOrDefault(false)

    fun isDuressEnabled(): Boolean = runCatching { prefs.getBoolean(KEY_DURESS_ENABLED, false) }.getOrDefault(false)

    fun getDuressAction(): DuressAction {
        val raw = runCatching { prefs.getString(KEY_DURESS_ACTION, DuressAction.DECOY_VAULT.name) }.getOrNull()
        return runCatching { DuressAction.valueOf(raw ?: DuressAction.DECOY_VAULT.name) }
            .getOrDefault(DuressAction.DECOY_VAULT)
    }

    fun getKdfSalt(): ByteArray? =
        runCatching { prefs.getString(KEY_KDF_SALT_HEX, null)?.hexToByteArray() }.getOrNull()

    fun getKdfIterations(): Int =
        runCatching { prefs.getInt(KEY_KDF_ITERATIONS, KdfParameters.DEFAULT_PBKDF2_ITERATIONS) }
            .getOrDefault(KdfParameters.DEFAULT_PBKDF2_ITERATIONS)

    private fun getCurrentDeviceBootCount(): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 1)
        } catch (_: Throwable) {
            1
        }
    }

    private fun isPasswordRequiredAfterReboot(): Boolean {
        val storedBootCount = runCatching { prefs.getInt(KEY_LAST_BOOT_COUNT, -1) }.getOrDefault(-1)
        val currentBootCount = getCurrentDeviceBootCount()
        return !isBootUnlockedInSession || (storedBootCount != -1 && storedBootCount != currentBootCount)
    }

    private fun computeInitialLockState(): VaultLockState {
        return try {
            val vaultId = prefs.getString(KEY_VAULT_ID, null)
            val wrappedVk = prefs.getString(KEY_WRAPPED_VK_MASTER_HEX, null)
            if (vaultId == null || wrappedVk == null) {
                VaultLockState.Uninitialized
            } else {
                VaultLockState.Locked(
                    vaultId = vaultId,
                    biometricEnabled = isBiometricEnabled(),
                    requirePasswordAfterReboot = isPasswordRequiredAfterReboot(),
                    failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
                    lockoutUntilMs = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
                )
            }
        } catch (_: Throwable) {
            VaultLockState.Uninitialized
        }
    }

    /**
     * Initializes a brand-new Vault with a cryptographically random UUID v4, 16-byte KDF salt,
     * and 256-bit Vault Key wrapped by the Master Key derived from Master Password.
     * Master Password is NEVER stored anywhere.
     */
    @Synchronized
    fun initializeNewVault(
        masterPassword: String,
        customVaultId: String = UUID.randomUUID().toString(),
        customVaultKey: ByteArray = CryptoEngine.generateVaultKey(),
        kdfIterations: Int = KdfParameters.DEFAULT_PBKDF2_ITERATIONS
    ): String {
        val salt = KeyDerivationService.generateSalt()
        val masterKey = KeyDerivationService.deriveMasterKey(masterPassword, salt, kdfIterations)
        val wrappedVk = try {
            CryptoEngine.encryptAesGcm(
                plaintext = customVaultKey,
                key = masterKey,
                aad = customVaultId.encodeToByteArray()
            )
        } finally {
            CryptoEngine.zeroize(masterKey)
        }

        val currentBoot = getCurrentDeviceBootCount()
        prefs.edit()
            .putString(KEY_VAULT_ID, customVaultId)
            .putString(KEY_KDF_SALT_HEX, salt.toHexString())
            .putInt(KEY_KDF_ITERATIONS, kdfIterations)
            .putString(KEY_WRAPPED_VK_MASTER_HEX, wrappedVk.ciphertext.toHexString())
            .putString(KEY_WRAPPED_VK_MASTER_NONCE_HEX, wrappedVk.nonce.toHexString())
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .putInt(KEY_LAST_BOOT_COUNT, currentBoot)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .apply()

        CryptoEngine.zeroize(activeVaultKey)
        activeVaultKey = customVaultKey.copyOf()
        isBootUnlockedInSession = true
        isDecoySession = false
        recordUserActivity()
        _lockState.value = VaultLockState.Unlocked(vaultId = customVaultId, isDecoyMode = false)
        return customVaultId
    }

    /**
     * Unlocks the vault using the Master Password (or triggers Duress mode if configured).
     */
    @Synchronized
    fun unlockWithPassword(password: String): UnlockResult {
        val vaultId = getVaultIdOrNull() ?: return UnlockResult.InvalidCredentials
        val now = System.currentTimeMillis()
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
        if (now < lockoutUntil) {
            return UnlockResult.RateLimited(lockoutUntil - now)
        }

        // Check Duress password first if configured
        if (isDuressEnabled()) {
            val duressSaltHex = prefs.getString(KEY_DURESS_SALT_HEX, null)
            val duressCipherHex = prefs.getString(KEY_WRAPPED_DURESS_VK_HEX, null)
            val duressNonceHex = prefs.getString(KEY_WRAPPED_DURESS_NONCE_HEX, null)
            if (duressSaltHex != null && duressCipherHex != null && duressNonceHex != null) {
                val duressKey = KeyDerivationService.deriveMasterKey(
                    password = password,
                    salt = duressSaltHex.hexToByteArray(),
                    pbkdf2Iterations = getKdfIterations()
                )
                try {
                    val decoyVk = CryptoEngine.decryptAesGcm(
                        ciphertext = duressCipherHex.hexToByteArray(),
                        key = duressKey,
                        nonce = duressNonceHex.hexToByteArray(),
                        aad = "duress:$vaultId".encodeToByteArray()
                    )
                    return when (getDuressAction()) {
                        DuressAction.CRYPTOGRAPHIC_WIPE -> {
                            CryptoEngine.zeroize(decoyVk)
                            wipeVaultCryptographically()
                            UnlockResult.DuressWipeTriggered
                        }
                        DuressAction.DECOY_VAULT -> {
                            CryptoEngine.zeroize(activeVaultKey)
                            activeVaultKey = decoyVk
                            isBootUnlockedInSession = true
                            isDecoySession = true
                            resetFailedAttempts()
                            recordUserActivity()
                            _lockState.value = VaultLockState.Unlocked(
                                vaultId = vaultId,
                                isDecoyMode = true
                            )
                            UnlockResult.Success(vaultId = vaultId, isDecoyMode = true)
                        }
                    }
                } catch (_: Exception) {
                    // Not the duress password; proceed to check normal Master Password
                } finally {
                    CryptoEngine.zeroize(duressKey)
                }
            }
        }

        val saltHex = prefs.getString(KEY_KDF_SALT_HEX, null) ?: return UnlockResult.InvalidCredentials
        val wrappedVkHex = prefs.getString(KEY_WRAPPED_VK_MASTER_HEX, null) ?: return UnlockResult.InvalidCredentials
        val nonceHex = prefs.getString(KEY_WRAPPED_VK_MASTER_NONCE_HEX, null) ?: return UnlockResult.InvalidCredentials

        val masterKey = KeyDerivationService.deriveMasterKey(
            password = password,
            salt = saltHex.hexToByteArray(),
            pbkdf2Iterations = getKdfIterations()
        )
        return try {
            val unwrappedVk = CryptoEngine.decryptAesGcm(
                ciphertext = wrappedVkHex.hexToByteArray(),
                key = masterKey,
                nonce = nonceHex.hexToByteArray(),
                aad = vaultId.encodeToByteArray()
            )
            CryptoEngine.zeroize(activeVaultKey)
            activeVaultKey = unwrappedVk
            isBootUnlockedInSession = true
            isDecoySession = false
            prefs.edit()
                .putInt(KEY_LAST_BOOT_COUNT, getCurrentDeviceBootCount())
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
                .apply()
            recordUserActivity()
            _lockState.value = VaultLockState.Unlocked(vaultId = vaultId, isDecoyMode = false)
            UnlockResult.Success(vaultId = vaultId, isDecoyMode = false)
        } catch (e: AEADBadTagException) {
            registerFailedAttempt(vaultId)
            UnlockResult.InvalidCredentials
        } catch (e: Exception) {
            registerFailedAttempt(vaultId)
            UnlockResult.InvalidCredentials
        } finally {
            CryptoEngine.zeroize(masterKey)
        }
    }

    private fun registerFailedAttempt(vaultId: String) {
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        // Progressive rate-limiting delay after 3 failed attempts
        val delayMs = when {
            attempts < 3 -> 0L
            attempts == 3 -> 3_000L
            attempts == 4 -> 10_000L
            attempts == 5 -> 30_000L
            else -> 60_000L
        }
        val lockoutUntil = if (delayMs > 0L) System.currentTimeMillis() + delayMs else 0L
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, attempts)
            .putLong(KEY_LOCKOUT_UNTIL_MS, lockoutUntil)
            .apply()
        _lockState.value = VaultLockState.Locked(
            vaultId = vaultId,
            biometricEnabled = isBiometricEnabled(),
            requirePasswordAfterReboot = isPasswordRequiredAfterReboot(),
            failedAttempts = attempts,
            lockoutUntilMs = lockoutUntil
        )
    }

    private fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .apply()
    }

    /**
     * Prepares the BiometricPrompt.CryptoObject for encrypting the active Vault Key when enabling biometrics.
     */
    fun getBiometricEncryptCryptoObject(): BiometricPrompt.CryptoObject? {
        val cipher = biometricKeyManager.getEncryptCipher() ?: return null
        return BiometricPrompt.CryptoObject(cipher)
    }

    /**
     * Completes biometric enrollment using the authenticated Cipher from BiometricPrompt.
     */
    @Synchronized
    fun enableBiometricWithAuthenticatedCipher(authenticatedCipher: Cipher): Boolean {
        val vk = activeVaultKey ?: throw VaultLockedException()
        return try {
            val encryptedVk = authenticatedCipher.doFinal(vk)
            val iv = authenticatedCipher.iv
            prefs.edit()
                .putBoolean(KEY_BIOMETRIC_ENABLED, true)
                .putString(KEY_WRAPPED_VK_BIO_HEX, encryptedVk.toHexString())
                .putString(KEY_WRAPPED_VK_BIO_IV_HEX, iv.toHexString())
                .apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Enables biometric wrapping directly using a provided Cipher (for unit/Robolectric testing or fallback).
     */
    @Synchronized
    fun disableBiometricUnlock() {
        biometricKeyManager.deleteBiometricKey()
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .remove(KEY_WRAPPED_VK_BIO_HEX)
            .remove(KEY_WRAPPED_VK_BIO_IV_HEX)
            .apply()
    }

    /**
     * Prepares the BiometricPrompt.CryptoObject for decrypting wrapped_vk_biometric.
     * Returns null if device rebooted since last Master Password unlock.
     */
    fun getBiometricDecryptCryptoObject(): BiometricPrompt.CryptoObject? {
        if (!isBiometricEnabled() || isPasswordRequiredAfterReboot()) {
            return null
        }
        val ivHex = prefs.getString(KEY_WRAPPED_VK_BIO_IV_HEX, null) ?: return null
        val cipher = biometricKeyManager.getDecryptCipher(ivHex.hexToByteArray()) ?: return null
        return BiometricPrompt.CryptoObject(cipher)
    }

    /**
     * Unlocks the Vault using the hardware-authenticated Cipher from BiometricPrompt.CryptoObject.
     */
    @Synchronized
    fun unlockWithBiometricCipher(authenticatedCipher: Cipher): UnlockResult {
        val vaultId = getVaultIdOrNull() ?: return UnlockResult.InvalidCredentials
        if (isPasswordRequiredAfterReboot()) {
            return UnlockResult.BiometricRequiresPasswordAfterReboot
        }
        val wrappedBioHex = prefs.getString(KEY_WRAPPED_VK_BIO_HEX, null)
            ?: return UnlockResult.InvalidCredentials

        return try {
            val unwrappedVk = authenticatedCipher.doFinal(wrappedBioHex.hexToByteArray())
            CryptoEngine.zeroize(activeVaultKey)
            activeVaultKey = unwrappedVk
            isDecoySession = false
            resetFailedAttempts()
            recordUserActivity()
            _lockState.value = VaultLockState.Unlocked(vaultId = vaultId, isDecoyMode = false)
            UnlockResult.Success(vaultId = vaultId, isDecoyMode = false)
        } catch (_: Exception) {
            UnlockResult.InvalidCredentials
        }
    }

    /**
     * Simulates a device reboot so that biometric unlock requires Master Password first.
     */
    @Synchronized
    fun simulateDeviceReboot() {
        isBootUnlockedInSession = false
        lock()
    }

    /**
     * Immediately locks the vault and zeroizes the Vault Key bytes in RAM.
     */
    @Synchronized
    fun lock() {
        CryptoEngine.zeroize(activeVaultKey)
        activeVaultKey = null
        isDecoySession = false
        val vaultId = getVaultIdOrNull()
        _lockState.value = if (vaultId == null) {
            VaultLockState.Uninitialized
        } else {
            VaultLockState.Locked(
                vaultId = vaultId,
                biometricEnabled = isBiometricEnabled(),
                requirePasswordAfterReboot = isPasswordRequiredAfterReboot(),
                failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
                lockoutUntilMs = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            )
        }
    }

    private fun requireActiveVaultKey(): ByteArray {
        return activeVaultKey ?: throw VaultLockedException()
    }

    @Synchronized
    fun exportActiveVaultKeyCopy(): ByteArray {
        val vk = requireActiveVaultKey()
        return vk.copyOf()
    }

    /**
     * Encrypts an object or file payload using AES-256-GCM and a fresh 12-byte random nonce.
     */
    fun encryptPayload(
        plaintext: ByteArray,
        aad: ByteArray = ByteArray(0)
    ): EncryptedBlob {
        val vk = requireActiveVaultKey()
        val payloadKey = CryptoEngine.deriveSubKey(vk, "OpenVault-Payload-AES256GCM-v1")
        return try {
            CryptoEngine.encryptAesGcm(plaintext = plaintext, key = payloadKey, aad = aad)
        } finally {
            CryptoEngine.zeroize(payloadKey)
        }
    }

    /**
     * Decrypts an object or file payload using AES-256-GCM and verifies the 128-bit authentication tag.
     */
    fun decryptPayload(
        ciphertext: ByteArray,
        nonce: ByteArray,
        aad: ByteArray = ByteArray(0)
    ): ByteArray {
        val vk = requireActiveVaultKey()
        val payloadKey = CryptoEngine.deriveSubKey(vk, "OpenVault-Payload-AES256GCM-v1")
        return try {
            CryptoEngine.decryptAesGcm(
                ciphertext = ciphertext,
                key = payloadKey,
                nonce = nonce,
                aad = aad
            )
        } finally {
            CryptoEngine.zeroize(payloadKey)
        }
    }

    fun encryptString(plainText: String, aad: String = ""): EncryptedBlob =
        encryptPayload(plainText.encodeToByteArray(), aad.encodeToByteArray())

    fun decryptString(ciphertext: ByteArray, nonce: ByteArray, aad: String = ""): String =
        decryptPayload(ciphertext, nonce, aad.encodeToByteArray()).decodeToString()

    /**
     * Derives the 256-bit key for SQLCipher Room database encryption.
     */
    fun deriveSqlCipherKeyBytes(): ByteArray {
        val vk = requireActiveVaultKey()
        val contextTag = if (isDecoySession) "OpenVault-SQLCipher-Decoy-v1" else "OpenVault-SQLCipher-Primary-v1"
        return CryptoEngine.deriveSubKey(vk, contextTag)
    }

    /**
     * Computes a deterministic HMAC-SHA256 integrity hash over canonical vault payloads.
     */
    fun computeIntegrityHash(data: ByteArray): ByteArray {
        val vk = requireActiveVaultKey()
        val integrityKey = CryptoEngine.deriveSubKey(vk, "OpenVault-Integrity-HMAC-v1")
        return try {
            CryptoEngine.computeHmacSha256(integrityKey, data)
        } finally {
            CryptoEngine.zeroize(integrityKey)
        }
    }

    /**
     * Safely changes the Master Password by verifying the current password, deriving a new Master Key
     * with a fresh 16-byte salt, and re-wrapping the existing Vault Key without re-encrypting all files.
     */
    @Synchronized
    fun changeMasterPassword(currentPassword: String, newPassword: String): Boolean {
        val vaultId = getVaultIdOrNull() ?: return false
        val saltHex = prefs.getString(KEY_KDF_SALT_HEX, null) ?: return false
        val wrappedVkHex = prefs.getString(KEY_WRAPPED_VK_MASTER_HEX, null) ?: return false
        val nonceHex = prefs.getString(KEY_WRAPPED_VK_MASTER_NONCE_HEX, null) ?: return false

        val oldMasterKey = KeyDerivationService.deriveMasterKey(
            password = currentPassword,
            salt = saltHex.hexToByteArray(),
            pbkdf2Iterations = getKdfIterations()
        )
        val unwrappedVk = try {
            CryptoEngine.decryptAesGcm(
                ciphertext = wrappedVkHex.hexToByteArray(),
                key = oldMasterKey,
                nonce = nonceHex.hexToByteArray(),
                aad = vaultId.encodeToByteArray()
            )
        } catch (_: Exception) {
            return false
        } finally {
            CryptoEngine.zeroize(oldMasterKey)
        }

        val newSalt = KeyDerivationService.generateSalt()
        val newMasterKey = KeyDerivationService.deriveMasterKey(
            password = newPassword,
            salt = newSalt,
            pbkdf2Iterations = getKdfIterations()
        )
        return try {
            val rewrapped = CryptoEngine.encryptAesGcm(
                plaintext = unwrappedVk,
                key = newMasterKey,
                aad = vaultId.encodeToByteArray()
            )
            prefs.edit()
                .putString(KEY_KDF_SALT_HEX, newSalt.toHexString())
                .putString(KEY_WRAPPED_VK_MASTER_HEX, rewrapped.ciphertext.toHexString())
                .putString(KEY_WRAPPED_VK_MASTER_NONCE_HEX, rewrapped.nonce.toHexString())
                .apply()
            true
        } finally {
            CryptoEngine.zeroize(newMasterKey)
            CryptoEngine.zeroize(unwrappedVk)
        }
    }

    /**
     * Configures the optional Duress / Panic Password and its behavior (Decoy Vault by default, or Cryptographic Wipe).
     */
    @Synchronized
    fun configureDuressPassword(duressPassword: String, action: DuressAction = DuressAction.DECOY_VAULT) {
        val vaultId = requireVaultId()
        val duressSalt = KeyDerivationService.generateSalt()
        val duressMasterKey = KeyDerivationService.deriveMasterKey(
            password = duressPassword,
            salt = duressSalt,
            pbkdf2Iterations = getKdfIterations()
        )
        val decoyVaultKey = CryptoEngine.generateVaultKey()
        try {
            val wrappedDecoy = CryptoEngine.encryptAesGcm(
                plaintext = decoyVaultKey,
                key = duressMasterKey,
                aad = "duress:$vaultId".encodeToByteArray()
            )
            prefs.edit()
                .putBoolean(KEY_DURESS_ENABLED, true)
                .putString(KEY_DURESS_ACTION, action.name)
                .putString(KEY_DURESS_SALT_HEX, duressSalt.toHexString())
                .putString(KEY_WRAPPED_DURESS_VK_HEX, wrappedDecoy.ciphertext.toHexString())
                .putString(KEY_WRAPPED_DURESS_NONCE_HEX, wrappedDecoy.nonce.toHexString())
                .apply()
        } finally {
            CryptoEngine.zeroize(duressMasterKey)
            CryptoEngine.zeroize(decoyVaultKey)
        }
    }

    @Synchronized
    fun disableDuressPassword() {
        prefs.edit()
            .putBoolean(KEY_DURESS_ENABLED, false)
            .remove(KEY_DURESS_SALT_HEX)
            .remove(KEY_WRAPPED_DURESS_VK_HEX)
            .remove(KEY_WRAPPED_DURESS_NONCE_HEX)
            .apply()
    }

    /**
     * Performs an emergency cryptographic wipe: zeroizes RAM keys, deletes Keystore biometric keys,
     * clears encrypted envelopes, and deletes local encrypted VFS blobs.
     */
    @Synchronized
    fun wipeVaultCryptographically() {
        CryptoEngine.zeroize(activeVaultKey)
        activeVaultKey = null
        isBootUnlockedInSession = false
        isDecoySession = false
        biometricKeyManager.deleteBiometricKey()
        prefs.edit().clear().apply()
        try {
            val blobsDir = File(context.filesDir, "vault_blobs")
            if (blobsDir.exists()) {
                blobsDir.deleteRecursively()
            }
        } catch (_: Exception) {
        }
        onWipeCallback?.invoke()
        _lockState.value = VaultLockState.Uninitialized
    }
}
