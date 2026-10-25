package com.example

import com.openvault.app.data.remote.ByteaHexSerializer
import com.openvault.app.data.remote.RemoteVaultObjectDto
import com.openvault.app.domain.model.ConflictResolutionStrategy
import com.openvault.app.domain.model.Note
import com.openvault.app.domain.model.VaultSettings
import com.openvault.app.security.CryptoEngine
import com.openvault.app.security.KeyDerivationService
import com.openvault.app.security.OvaultBackupManager
import com.openvault.app.security.OvaultSnapshot
import com.openvault.app.security.PasswordAnalyzer
import com.openvault.app.security.PasswordGeneratorConfig
import com.openvault.app.security.PasswordStrengthLevel
import com.openvault.app.security.TotpGenerator
import com.openvault.app.sync.ConflictResolver
import com.openvault.app.sync.ConflictWinner
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException

/**
 * Comprehensive Unit & Cryptographic Test Suite covering the mandatory E2E Zero-Knowledge
 * scenarios from Section 10 of the OpenVault specification.
 */
class ExampleUnitTest {

    // Scenario 1: AES-256-GCM encryption/decryption roundtrip + tamper detection (AAD & tag)
    @Test
    fun scenario01_aes256GcmRoundtripAndTamperDetection() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "Top Secret Zero-Knowledge Payload: Příliš žluťoučký kůň úpěl ďábelské ódy".encodeToByteArray()
        val aad = "item-uuid-123|NOTE|1".encodeToByteArray()

        val encrypted = CryptoEngine.encryptAesGcm(plaintext = plaintext, key = key, aad = aad)
        assertEquals(12, encrypted.nonce.size)
        assertTrue(encrypted.ciphertext.size > plaintext.size)

        val decrypted = CryptoEngine.decryptAesGcm(
            ciphertext = encrypted.ciphertext,
            key = key,
            nonce = encrypted.nonce,
            aad = aad
        )
        assertArrayEquals(plaintext, decrypted)

        // Tamper with ciphertext byte -> must fail AEAD authentication
        val tamperedCiphertext = encrypted.ciphertext.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0xFF).toByte()
        assertThrows(AEADBadTagException::class.java) {
            CryptoEngine.decryptAesGcm(
                ciphertext = tamperedCiphertext,
                key = key,
                nonce = encrypted.nonce,
                aad = aad
            )
        }

        // Tamper with AAD -> must fail AEAD authentication
        val wrongAad = "item-uuid-123|NOTE|2".encodeToByteArray()
        assertThrows(AEADBadTagException::class.java) {
            CryptoEngine.decryptAesGcm(
                ciphertext = encrypted.ciphertext,
                key = key,
                nonce = encrypted.nonce,
                aad = wrongAad
            )
        }
    }

    // Scenario 2: Key derivation determinism (Argon2id + PBKDF2-HMAC-SHA256 210,000 iterations) + salt isolation
    @Test
    fun scenario02_kdfDeterminismAndSaltIsolation() {
        val password = "CorrectHorseBatteryStaple!99"
        val salt1 = KeyDerivationService.generateSalt()
        val salt2 = KeyDerivationService.generateSalt()

        val key1a = KeyDerivationService.deriveMasterKey(password, salt1)
        val key1b = KeyDerivationService.deriveMasterKey(password, salt1)
        val key2 = KeyDerivationService.deriveMasterKey(password, salt2)

        assertEquals(32, key1a.size)
        assertArrayEquals(key1a, key1b)
        assertFalse(key1a.contentEquals(key2))
    }

    // Scenario 3: Key Hierarchy wrapping & unwrapping (Master Key -> Vault Key -> Master Password Rotation)
    @Test
    fun scenario03_vaultKeyWrappingAndPasswordRotationWithoutReencryptingData() {
        val vaultKey = CryptoEngine.generateVaultKey()
        val itemData = "Encrypted Vault Note Content".encodeToByteArray()
        val encryptedItem = CryptoEngine.encryptAesGcm(itemData, vaultKey, aad = "note-1".encodeToByteArray())

        // Initial Master Password wraps Vault Key
        val oldSalt = KeyDerivationService.generateSalt()
        val oldMasterKey = KeyDerivationService.deriveMasterKey("OldMasterPass#1", oldSalt)
        val wrappedVkOld = CryptoEngine.encryptAesGcm(
            plaintext = vaultKey,
            key = oldMasterKey,
            aad = "WRAPPED_VK_MASTER".encodeToByteArray()
        )

        // Unwrap Vault Key with old Master Key and rotate to New Master Password
        val unwrappedVk = CryptoEngine.decryptAesGcm(
            ciphertext = wrappedVkOld.ciphertext,
            key = oldMasterKey,
            nonce = wrappedVkOld.nonce,
            aad = "WRAPPED_VK_MASTER".encodeToByteArray()
        )
        assertArrayEquals(vaultKey, unwrappedVk)

        val newSalt = KeyDerivationService.generateSalt()
        val newMasterKey = KeyDerivationService.deriveMasterKey("NewMasterPass#2", newSalt)
        val wrappedVkNew = CryptoEngine.encryptAesGcm(
            plaintext = unwrappedVk,
            key = newMasterKey,
            aad = "WRAPPED_VK_MASTER".encodeToByteArray()
        )

        // Verify item is still decryptable using Vault Key unwrapped by the new Master Key
        val recoveredVk = CryptoEngine.decryptAesGcm(
            ciphertext = wrappedVkNew.ciphertext,
            key = newMasterKey,
            nonce = wrappedVkNew.nonce,
            aad = "WRAPPED_VK_MASTER".encodeToByteArray()
        )
        val decryptedItem = CryptoEngine.decryptAesGcm(
            ciphertext = encryptedItem.ciphertext,
            key = recoveredVk,
            nonce = encryptedItem.nonce,
            aad = "note-1".encodeToByteArray()
        )
        assertArrayEquals(itemData, decryptedItem)
    }

    // Scenario 4: RAM Zeroization of sensitive byte & char arrays
    @Test
    fun scenario04_ramZeroizationClearsKeyMaterial() {
        val sensitiveKey = ByteArray(32) { (it + 1).toByte() }
        val sensitivePassword = "UltraSecretPassword123".toCharArray()

        assertTrue(sensitiveKey.any { it != 0.toByte() })
        assertTrue(sensitivePassword.any { it != '\u0000' })

        CryptoEngine.zeroize(sensitiveKey)
        CryptoEngine.zeroizeChars(sensitivePassword)

        assertTrue(sensitiveKey.all { it == 0.toByte() })
        assertTrue(sensitivePassword.all { it == '\u0000' })
    }

    // Scenario 5: PostgreSQL BYTEA hex (\x...) serialization & deserialization for Supabase PostgREST
    @Test
    fun scenario05_byteaHexSerializerRoundtrip() {
        val rawBytes = byteArrayOf(0x00, 0x01, 0x0F, 0x10, 0x7F, 0x80.toByte(), 0xFE.toByte(), 0xFF.toByte())
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        val dto = RemoteVaultObjectDto(
            id = "11111111-2222-3333-4444-555555555555",
            vaultId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            objectType = "note",
            encryptedPayload = rawBytes,
            nonce = ByteArray(12) { 0x2A },
            rev = 3L,
            updatedAt = "2026-10-25T12:00:00Z"
        )
        val serialized = json.encodeToString(RemoteVaultObjectDto.serializer(), dto)
        assertTrue(serialized.contains("\"encrypted_payload\":\"\\\\x00010f107f80feff\""))

        val deserialized = json.decodeFromString(RemoteVaultObjectDto.serializer(), serialized)
        assertArrayEquals(rawBytes, deserialized.encryptedPayload)
        assertArrayEquals(ByteArray(12) { 0x2A }, deserialized.nonce)
    }

    // Scenario 6: Last-Write-Wins (LWW) conflict resolution & revision history preservation
    @Test
    fun scenario06_conflictResolverLwwAndRevisionHistoryPreservation() {
        val localNote = Note(
            id = "note-conflict-1",
            vaultId = "vault-1",
            title = "Local Note Title",
            body = "Local body content edited offline",
            rev = 3L,
            createdAt = 1000L,
            updatedAt = 1500L
        )
        val remoteNote = Note(
            id = "note-conflict-1",
            vaultId = "vault-1",
            title = "Remote Note Title",
            body = "Remote body content edited on another device",
            rev = 3L,
            createdAt = 1000L,
            updatedAt = 2500L
        )

        val outcome = ConflictResolver.resolveNoteConflict(
            localNote = localNote,
            remoteNote = remoteNote,
            hasLocalUnpushedChanges = true,
            strategy = ConflictResolutionStrategy.LAST_WRITE_WINS_WITH_HISTORY
        )

        assertEquals(ConflictWinner.REMOTE, outcome.winner)
        assertTrue(outcome.hadCollision)
        assertEquals("Remote Note Title", outcome.winningItem.title)
        assertEquals(4L, outcome.winningItem.rev)
        // Verify that the losing local version was preserved in revision history
        assertTrue(outcome.winningItem.history.any { it.body == "Local body content edited offline" })
    }

    // Scenario 7: RFC 6238 TOTP generator accuracy with known test vectors
    @Test
    fun scenario07_totpRfc6238GenerationAndWindowProgress() {
        // Base32 for "12345678901234567890" is "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val secretBase32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val codeAt59Sec = TotpGenerator.generateCode(
            base32Secret = secretBase32,
            timestampSeconds = 59L,
            periodSeconds = 30,
            digits = 8,
            algorithm = com.openvault.app.security.TotpAlgorithm.SHA1
        )
        assertEquals("94287082", codeAt59Sec)

        val state = TotpGenerator.generateTotpState(
            base32Secret = secretBase32,
            timestampMillis = 59_000L,
            periodSeconds = 30,
            digits = 6
        )
        assertNotNull(state)
        assertEquals(1, state!!.remainingSeconds)
        assertEquals(6, state.code.length)
    }

    // Scenario 8: Password Entropy, Strength Scoring & Cryptographic Password Generator
    @Test
    fun scenario08_passwordEntropyAndHealthAudit() {
        val weakAnalysis = PasswordAnalyzer.analyzePassword("123456")
        assertEquals(PasswordStrengthLevel.WEAK, weakAnalysis.level)

        val strongGenerated = PasswordAnalyzer.generatePassword(
            PasswordGeneratorConfig(
                length = 24,
                includeLowercase = true,
                includeUppercase = true,
                includeDigits = true,
                includeSymbols = true
            )
        )
        assertEquals(24, strongGenerated.length)
        val strongAnalysis = PasswordAnalyzer.analyzePassword(strongGenerated)
        assertTrue(strongAnalysis.entropyBits >= 90.0)
        assertTrue(strongAnalysis.score >= 75)
    }

    // Scenario 9: Encrypted .ovault archive export & import roundtrip + wrong passphrase rejection
    @Test
    fun scenario09_ovaultEncryptedBackupExportAndImportRoundtrip() {
        val sampleNote = Note(
            id = "note-ovault-1",
            vaultId = "vault-uuid-999",
            title = "Backup Test Note",
            body = "Secret Markdown Body",
            rev = 2L,
            createdAt = 1700000000000L,
            updatedAt = 1700000005000L
        )
        val rawSnapshot = OvaultSnapshot(
            vaultId = "vault-uuid-999",
            exportedAt = 1700000010000L,
            settings = VaultSettings(),
            notes = listOf(sampleNote),
            integritySha256Hex = ""
        )
        val canonicalHash = OvaultBackupManager.computeSnapshotCanonicalHashHex(rawSnapshot)
        val signedSnapshot = rawSnapshot.copy(integritySha256Hex = canonicalHash)

        val archiveBytes = OvaultBackupManager.exportToOvaultBytes(
            snapshot = signedSnapshot,
            exportPassword = "BackupExportPassphrase!2026"
        )

        val header = OvaultBackupManager.inspectHeader(archiveBytes)
        assertEquals("vault-uuid-999", header.vaultId)
        assertEquals(1, header.objectCount)

        val restored = OvaultBackupManager.importFromOvaultBytes(
            containerBytes = archiveBytes,
            importPassword = "BackupExportPassphrase!2026"
        )
        assertEquals("vault-uuid-999", restored.vaultId)
        assertEquals(1, restored.notes.size)
        assertEquals("Backup Test Note", restored.notes.first().title)

        // Wrong passphrase must fail HMAC/GCM verification
        assertThrows(Exception::class.java) {
            OvaultBackupManager.importFromOvaultBytes(
                containerBytes = archiveBytes,
                importPassword = "WrongPassphrase"
            )
        }
    }

    // Scenario 10: Encrypted VFS File Blob Roundtrip with HKDF Subkey Derivation
    @Test
    fun scenario10_encryptedVfsFileBlobRoundtrip() {
        val vaultKey = CryptoEngine.generateVaultKey()
        val vfsKey = CryptoEngine.deriveSubKey(vaultKey, "OpenVault-VFS-Blob-Key")
        val fileBytes = ByteArray(64 * 1024) { (it % 251).toByte() } // 64 KB binary attachment
        val fileId = "vfs-blob-uuid-42"

        val encryptedBlob = CryptoEngine.encryptAesGcm(
            plaintext = fileBytes,
            key = vfsKey,
            aad = "VFS|$fileId".encodeToByteArray()
        )

        val decryptedBlob = CryptoEngine.decryptAesGcm(
            ciphertext = encryptedBlob.ciphertext,
            key = vfsKey,
            nonce = encryptedBlob.nonce,
            aad = "VFS|$fileId".encodeToByteArray()
        )
        assertArrayEquals(fileBytes, decryptedBlob)
    }
}
