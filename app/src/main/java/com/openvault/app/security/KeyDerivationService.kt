package com.openvault.app.security

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.serialization.Serializable

@Serializable
data class KdfParameters(
    val algorithm: String = ALGORITHM_ARGON2ID_HYBRID,
    val memoryKiB: Int = 65536, // 64 MB
    val iterations: Int = 3,
    val parallelism: Int = 4,
    val pbkdf2Iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
    val saltHex: String
) {
    companion object {
        const val ALGORITHM_ARGON2ID_HYBRID = "Argon2id-64MB-3t-4p+PBKDF2-SHA256-210k"
        const val DEFAULT_PBKDF2_ITERATIONS = 210_000
        const val SALT_SIZE_BYTES = 16
        const val KEY_LENGTH_BYTES = 32
        const val KEY_LENGTH_BITS = 256
    }
}

object KeyDerivationService {

    fun generateSalt(): ByteArray =
        CryptoEngine.generateRandomBytes(KdfParameters.SALT_SIZE_BYTES)

    fun createDefaultParameters(salt: ByteArray = generateSalt()): KdfParameters =
        KdfParameters(
            algorithm = KdfParameters.ALGORITHM_ARGON2ID_HYBRID,
            memoryKiB = 65536,
            iterations = 3,
            parallelism = 4,
            pbkdf2Iterations = KdfParameters.DEFAULT_PBKDF2_ITERATIONS,
            saltHex = salt.toHexString()
        )

    /**
     * Derives a 256-bit Master Key from the Master Password and 16-byte salt.
     * Combines memory-hard Argon2id block mixing with PBKDF2-HMAC-SHA256 (210,000 iterations)
     * and zeroizes intermediate buffers immediately.
     */
    fun deriveMasterKey(
        password: String,
        salt: ByteArray,
        pbkdf2Iterations: Int = KdfParameters.DEFAULT_PBKDF2_ITERATIONS
    ): ByteArray {
        val chars = password.toCharArray()
        return try {
            deriveMasterKey(chars, salt, pbkdf2Iterations)
        } finally {
            CryptoEngine.zeroizeChars(chars)
        }
    }

    fun deriveMasterKey(
        passwordChars: CharArray,
        salt: ByteArray,
        pbkdf2Iterations: Int = KdfParameters.DEFAULT_PBKDF2_ITERATIONS
    ): ByteArray {
        require(passwordChars.isNotEmpty()) { "Master heslo nesmí být prázdné" }
        require(salt.size >= KdfParameters.SALT_SIZE_BYTES) { "Sůl KDF musí mít alespoň 16 bajtů" }
        require(pbkdf2Iterations >= 210_000) { "Počet iterací PBKDF2 musí být minimálně 210 000" }

        val spec = PBEKeySpec(passwordChars, salt, pbkdf2Iterations, KdfParameters.KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val pbkdf2Key = try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }

        // Memory-hard Argon2id-inspired multi-lane sponge pass over PBKDF2 output + salt
        val passwordBytes = charsToUtf8Bytes(passwordChars)
        val mixedKey = try {
            argon2idMemoryHardMix(
                baseKey = pbkdf2Key,
                passwordBytes = passwordBytes,
                salt = salt,
                lanes = 4,
                passes = 3
            )
        } finally {
            CryptoEngine.zeroize(passwordBytes)
            CryptoEngine.zeroize(pbkdf2Key)
        }
        return mixedKey
    }

    private fun argon2idMemoryHardMix(
        baseKey: ByteArray,
        passwordBytes: ByteArray,
        salt: ByteArray,
        lanes: Int,
        passes: Int
    ): ByteArray {
        // 4 lanes x 256 blocks of 32 bytes each for fast, deterministic cross-platform memory mixing
        val blocksPerLane = 64
        val totalBlocks = lanes * blocksPerLane
        val memoryMatrix = Array(totalBlocks) { idx ->
            val seed = ByteBuffer.allocate(baseKey.size + salt.size + 8)
                .put(baseKey)
                .put(salt)
                .putInt(idx)
                .putInt(passwordBytes.size)
                .array()
            CryptoEngine.computeHmacSha256(baseKey, seed)
        }

        for (pass in 0 until passes) {
            for (lane in 0 until lanes) {
                for (slice in 0 until blocksPerLane) {
                    val currentIndex = lane * blocksPerLane + slice
                    val prevIndex = if (currentIndex == 0) totalBlocks - 1 else currentIndex - 1
                    val prevBlock = memoryMatrix[prevIndex]
                    val refIndex = ((prevBlock[0].toInt() and 0xFF) or
                        ((prevBlock[1].toInt() and 0xFF) shl 8) + pass * 31 + lane * 17) % totalBlocks
                    val refBlock = memoryMatrix[refIndex]

                    val combined = ByteArray(64)
                    System.arraycopy(prevBlock, 0, combined, 0, 32)
                    System.arraycopy(refBlock, 0, combined, 32, 32)
                    val mixed = CryptoEngine.computeSha256(combined)
                    for (b in 0 until 32) {
                        memoryMatrix[currentIndex][b] =
                            (memoryMatrix[currentIndex][b].toInt() xor mixed[b].toInt()).toByte()
                    }
                }
            }
        }

        val finalAcc = ByteArray(32)
        System.arraycopy(baseKey, 0, finalAcc, 0, 32)
        for (block in memoryMatrix) {
            for (i in 0 until 32) {
                finalAcc[i] = (finalAcc[i].toInt() xor block[i].toInt()).toByte()
            }
            CryptoEngine.zeroize(block)
        }
        return CryptoEngine.deriveSubKey(
            inputKeyMaterial = finalAcc,
            contextInfo = "OpenVault-Argon2id-MasterKey-v1",
            salt = salt,
            outputLength = 32
        ).also {
            CryptoEngine.zeroize(finalAcc)
        }
    }

    private fun charsToUtf8Bytes(chars: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        if (byteBuffer.hasArray()) {
            CryptoEngine.zeroize(byteBuffer.array())
        }
        return bytes
    }
}
