package com.openvault.app.security

import com.openvault.app.domain.model.AuditEntry
import com.openvault.app.domain.model.CalendarEvent
import com.openvault.app.domain.model.CollectionItem
import com.openvault.app.domain.model.DatabaseRecord
import com.openvault.app.domain.model.Note
import com.openvault.app.domain.model.PasswordItem
import com.openvault.app.domain.model.UserDatabase
import com.openvault.app.domain.model.VaultFile
import com.openvault.app.domain.model.VaultSettings
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class OvaultContainerHeader(
    val formatVersion: Int = 1,
    val vaultId: String,
    val createdAt: Long,
    val kdfParameters: KdfParameters,
    val payloadNonceHex: String,
    val objectCount: Int,
    val fileCount: Int
)

@Serializable
data class OvaultEmbeddedFileBlob(
    val fileId: String,
    val encryptedBytesHex: String
)

@Serializable
data class OvaultSnapshot(
    val vaultId: String,
    val exportedAt: Long,
    val settings: VaultSettings,
    val notes: List<Note> = emptyList(),
    val passwords: List<PasswordItem> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val files: List<VaultFile> = emptyList(),
    val fileBlobs: List<OvaultEmbeddedFileBlob> = emptyList(),
    val databases: List<UserDatabase> = emptyList(),
    val records: List<DatabaseRecord> = emptyList(),
    val collections: List<CollectionItem> = emptyList(),
    val auditEntries: List<AuditEntry> = emptyList(),
    val integritySha256Hex: String
)

enum class OvaultImportMode(val labelCs: String) {
    MERGE("Sloučit s existujícím trezorem (Merge)"),
    REPLACE("Nahradit celý trezor (Replace)")
}

object OvaultBackupManager {
    private val MAGIC_BYTES = "OVAULT01".toByteArray(StandardCharsets.US_ASCII) // 8 bytes
    private const val FORMAT_VERSION = 1
    private const val HMAC_SIZE_BYTES = 32

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Exports the complete vault snapshot into a single self-verifying encrypted `.ovault` container.
     * Binary Layout:
     * - [0..7]   8B: Magic bytes "OVAULT01"
     * - [8..11]  4B: Format version (Int, Big-Endian = 1)
     * - [12..15] 4B: Header JSON length N (Int, Big-Endian)
     * - [16..16+N] : Unencrypted JSON header (KDF params, salt, nonce, metadata)
     * - [16+N..48+N] 32B: HMAC-SHA256 over (magic + version + headerLength + headerBytes + ciphertext)
     * - [48+N..EOF] : AES-256-GCM encrypted GZIP-compressed OvaultSnapshot JSON
     */
    fun exportToOvaultBytes(
        snapshot: OvaultSnapshot,
        exportPassword: String,
        kdfIterations: Int = KdfParameters.DEFAULT_PBKDF2_ITERATIONS
    ): ByteArray {
        val salt = KeyDerivationService.generateSalt()
        val kdfParams = KdfParameters(
            saltHex = salt.toHexString(),
            pbkdf2Iterations = kdfIterations
        )
        val masterKey = KeyDerivationService.deriveMasterKey(
            password = exportPassword,
            salt = salt,
            pbkdf2Iterations = kdfIterations
        )

        val encryptionKey = CryptoEngine.deriveSubKey(masterKey, "Ovault-Export-AES256GCM-v1")
        val hmacKey = CryptoEngine.deriveSubKey(masterKey, "Ovault-Export-HMAC-SHA256-v1")

        return try {
            val snapshotJsonBytes = json.encodeToString(snapshot).toByteArray(StandardCharsets.UTF_8)
            val compressedBytes = gzipCompress(snapshotJsonBytes)
            val nonce = CryptoEngine.generateNonce()

            val header = OvaultContainerHeader(
                formatVersion = FORMAT_VERSION,
                vaultId = snapshot.vaultId,
                createdAt = snapshot.exportedAt,
                kdfParameters = kdfParams,
                payloadNonceHex = nonce.toHexString(),
                objectCount = snapshot.notes.size + snapshot.passwords.size + snapshot.events.size +
                    snapshot.databases.size + snapshot.records.size + snapshot.collections.size,
                fileCount = snapshot.files.size
            )
            val headerBytes = json.encodeToString(header).toByteArray(StandardCharsets.UTF_8)

            val encryptedBlob = CryptoEngine.encryptAesGcm(
                plaintext = compressedBytes,
                key = encryptionKey,
                nonce = nonce,
                aad = headerBytes
            )

            val versionAndLenBuffer = ByteBuffer.allocate(8)
                .putInt(FORMAT_VERSION)
                .putInt(headerBytes.size)
                .array()

            val dataToAuthenticate = ByteArrayOutputStream().apply {
                write(MAGIC_BYTES)
                write(versionAndLenBuffer)
                write(headerBytes)
                write(encryptedBlob.ciphertext)
            }.toByteArray()

            val containerHmac = CryptoEngine.computeHmacSha256(hmacKey, dataToAuthenticate)

            ByteArrayOutputStream(
                MAGIC_BYTES.size + 8 + headerBytes.size + HMAC_SIZE_BYTES + encryptedBlob.ciphertext.size
            ).apply {
                write(MAGIC_BYTES)
                write(versionAndLenBuffer)
                write(headerBytes)
                write(containerHmac)
                write(encryptedBlob.ciphertext)
            }.toByteArray()
        } finally {
            CryptoEngine.zeroize(masterKey)
            CryptoEngine.zeroize(encryptionKey)
            CryptoEngine.zeroize(hmacKey)
        }
    }

    /**
     * Parses and verifies only the header of an `.ovault` container without decrypting the payload.
     */
    fun inspectHeader(containerBytes: ByteArray): OvaultContainerHeader {
        require(containerBytes.size > 16 + HMAC_SIZE_BYTES) {
            "Soubor .ovault je příliš malý nebo poškozený"
        }
        val magic = containerBytes.copyOfRange(0, 8)
        require(magic.contentEquals(MAGIC_BYTES)) {
            "Neplatná hlavička souboru: chybí magické bajty OVAULT01"
        }
        val buffer = ByteBuffer.wrap(containerBytes, 8, 8)
        val version = buffer.int
        require(version == FORMAT_VERSION) {
            "Nepodporovaná verze kontejneru .ovault: $version"
        }
        val headerLen = buffer.int
        require(headerLen in 20..(1024 * 64) && containerBytes.size > 16 + headerLen + HMAC_SIZE_BYTES) {
            "Poškozená délka hlavičky .ovault"
        }
        val headerJson = String(containerBytes, 16, headerLen, StandardCharsets.UTF_8)
        return json.decodeFromString<OvaultContainerHeader>(headerJson)
    }

    /**
     * Verifies the HMAC-SHA256 signature and AES-256-GCM tag of an `.ovault` container,
     * then decompresses and verifies the internal SHA-256 snapshot integrity hash.
     */
    fun importFromOvaultBytes(
        containerBytes: ByteArray,
        importPassword: String
    ): OvaultSnapshot {
        val header = inspectHeader(containerBytes)
        val buffer = ByteBuffer.wrap(containerBytes, 8, 8)
        val version = buffer.int
        val headerLen = buffer.int

        val headerBytes = containerBytes.copyOfRange(16, 16 + headerLen)
        val hmacStart = 16 + headerLen
        val cipherStart = hmacStart + HMAC_SIZE_BYTES

        val expectedHmac = containerBytes.copyOfRange(hmacStart, cipherStart)
        val ciphertext = containerBytes.copyOfRange(cipherStart, containerBytes.size)

        val salt = header.kdfParameters.saltHex.hexToByteArray()
        val masterKey = KeyDerivationService.deriveMasterKey(
            password = importPassword,
            salt = salt,
            pbkdf2Iterations = header.kdfParameters.pbkdf2Iterations
        )
        val encryptionKey = CryptoEngine.deriveSubKey(masterKey, "Ovault-Export-AES256GCM-v1")
        val hmacKey = CryptoEngine.deriveSubKey(masterKey, "Ovault-Export-HMAC-SHA256-v1")

        try {
            val versionAndLenBuffer = ByteBuffer.allocate(8)
                .putInt(version)
                .putInt(headerLen)
                .array()
            val dataToAuthenticate = ByteArrayOutputStream().apply {
                write(MAGIC_BYTES)
                write(versionAndLenBuffer)
                write(headerBytes)
                write(ciphertext)
            }.toByteArray()

            val computedHmac = CryptoEngine.computeHmacSha256(hmacKey, dataToAuthenticate)
            if (!CryptoEngine.constantTimeEquals(expectedHmac, computedHmac)) {
                throw AEADBadTagException("Selhalo ověření HMAC-SHA256 integrity souboru .ovault (nesprávné heslo nebo poškozený soubor)")
            }

            val compressedBytes = CryptoEngine.decryptAesGcm(
                ciphertext = ciphertext,
                key = encryptionKey,
                nonce = header.payloadNonceHex.hexToByteArray(),
                aad = headerBytes
            )
            val snapshotJsonBytes = gzipDecompress(compressedBytes)
            val snapshot = json.decodeFromString<OvaultSnapshot>(
                String(snapshotJsonBytes, StandardCharsets.UTF_8)
            )

            val expectedSnapshotHash = computeSnapshotCanonicalHashHex(snapshot)
            if (!expectedSnapshotHash.equals(snapshot.integritySha256Hex, ignoreCase = true)) {
                throw SecurityException("Selhalo vnitřní SHA-256 ověření integrity objektů v kontejneru .ovault")
            }
            return snapshot
        } finally {
            CryptoEngine.zeroize(masterKey)
            CryptoEngine.zeroize(encryptionKey)
            CryptoEngine.zeroize(hmacKey)
        }
    }

    fun computeSnapshotCanonicalHashHex(snapshot: OvaultSnapshot): String {
        val canonical = buildString {
            append(snapshot.vaultId).append('|')
            append(snapshot.notes.sortedBy { it.id }.joinToString(",") { "${it.id}:${it.rev}" }).append('|')
            append(snapshot.passwords.sortedBy { it.id }.joinToString(",") { "${it.id}:${it.rev}" }).append('|')
            append(snapshot.events.sortedBy { it.id }.joinToString(",") { "${it.id}:${it.rev}" }).append('|')
            append(snapshot.files.sortedBy { it.id }.joinToString(",") { "${it.id}:${it.sha256Hex}" }).append('|')
            append(snapshot.databases.sortedBy { it.id }.joinToString(",") { "${it.id}:${it.rev}" }).append('|')
            append(snapshot.records.sortedBy { it.id }.joinToString(",") { "${it.id}:${it.rev}" }).append('|')
            append(snapshot.collections.sortedBy { it.id }.joinToString(",") { "${it.id}:${it.rev}" })
        }
        return CryptoEngine.computeSha256Hex(canonical.toByteArray(StandardCharsets.UTF_8))
    }

    private fun gzipCompress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gzipDecompress(compressed: ByteArray): ByteArray {
        return GZIPInputStream(compressed.inputStream()).use { it.readBytes() }
    }
}
