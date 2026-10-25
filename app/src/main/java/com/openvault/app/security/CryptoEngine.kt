package com.openvault.app.security

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class VaultLockedException(message: String = "Trezor OpenVault je uzamčen. Přístup k šifrovaným datům odepřen.") :
    SecurityException(message)

data class EncryptedBlob(
    val ciphertext: ByteArray,
    val nonce: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedBlob) return false
        return ciphertext.contentEquals(other.ciphertext) && nonce.contentEquals(other.nonce)
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        return result
    }
}

object CryptoEngine {
    const val AES_KEY_SIZE_BYTES = 32 // 256 bits
    const val GCM_NONCE_SIZE_BYTES = 12 // 96 bits
    const val GCM_TAG_SIZE_BITS = 128 // 128-bit authentication tag

    private val secureRandom = SecureRandom()

    fun generateRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    fun generateVaultKey(): ByteArray = generateRandomBytes(AES_KEY_SIZE_BYTES)

    fun generateNonce(): ByteArray = generateRandomBytes(GCM_NONCE_SIZE_BYTES)

    fun encryptAesGcm(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray = generateNonce(),
        aad: ByteArray = ByteArray(0)
    ): EncryptedBlob {
        require(key.size == AES_KEY_SIZE_BYTES) { "AES-256 vyžaduje přesně 32bajtový klíč" }
        require(nonce.size == GCM_NONCE_SIZE_BYTES) { "AES-GCM vyžaduje přesně 12bajtový nonce" }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE_BITS, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        if (aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedBlob(ciphertext = ciphertext, nonce = nonce)
    }

    fun decryptAesGcm(
        ciphertext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray = ByteArray(0)
    ): ByteArray {
        require(key.size == AES_KEY_SIZE_BYTES) { "AES-256 vyžaduje přesně 32bajtový klíč" }
        require(nonce.size == GCM_NONCE_SIZE_BYTES) { "AES-GCM vyžaduje přesně 12bajtový nonce" }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE_BITS, nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        if (aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(ciphertext)
    }

    /**
     * HKDF-SHA256 (RFC 5869) subkey derivation from the 256-bit Vault Key.
     */
    fun deriveSubKey(
        inputKeyMaterial: ByteArray,
        contextInfo: String,
        salt: ByteArray = "OpenVault-HKDF-Salt-v1".encodeToByteArray(),
        outputLength: Int = AES_KEY_SIZE_BYTES
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(inputKeyMaterial)

        val infoBytes = contextInfo.encodeToByteArray()
        val result = ByteArray(outputLength)
        var t = ByteArray(0)
        var offset = 0
        var counter: Byte = 1

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        while (offset < outputLength) {
            mac.reset()
            mac.update(t)
            mac.update(infoBytes)
            mac.update(counter)
            t = mac.doFinal()
            val toCopy = minOf(t.size, outputLength - offset)
            System.arraycopy(t, 0, result, offset, toCopy)
            offset += toCopy
            counter++
        }
        zeroize(prk)
        zeroize(t)
        return result
    }

    fun computeSha256(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }

    fun computeSha256Hex(data: ByteArray): String = computeSha256(data).toHexString()

    fun computeHmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
        MessageDigest.isEqual(a, b)

    fun zeroize(bytes: ByteArray?) {
        if (bytes != null) {
            Arrays.fill(bytes, 0.toByte())
        }
    }

    fun zeroizeChars(chars: CharArray?) {
        if (chars != null) {
            Arrays.fill(chars, '\u0000')
        }
    }

    fun packNonceAndCiphertext(blob: EncryptedBlob): ByteArray {
        val buffer = ByteBuffer.allocate(4 + blob.nonce.size + blob.ciphertext.size)
        buffer.putInt(blob.nonce.size)
        buffer.put(blob.nonce)
        buffer.put(blob.ciphertext)
        return buffer.array()
    }

    fun unpackNonceAndCiphertext(packed: ByteArray): EncryptedBlob {
        require(packed.size > 4 + GCM_NONCE_SIZE_BYTES) { "Neplatný formát šifrovaného bloku" }
        val buffer = ByteBuffer.wrap(packed)
        val nonceSize = buffer.int
        require(nonceSize in 12..32 && packed.size >= 4 + nonceSize) { "Poškozená hlavička nonce" }
        val nonce = ByteArray(nonceSize)
        buffer.get(nonce)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)
        return EncryptedBlob(ciphertext = ciphertext, nonce = nonce)
    }
}

fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

fun String.hexToByteArray(): ByteArray {
    val clean = removePrefix("\\x").removePrefix("0x").trim()
    require(clean.length % 2 == 0) { "Hex řetězec musí mít sudou délku" }
    return ByteArray(clean.length / 2) { i ->
        val index = i * 2
        clean.substring(index, index + 2).toInt(16).toByte()
    }
}
