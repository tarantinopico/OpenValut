package com.openvault.app.security

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

enum class TotpAlgorithm(val hmacName: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512")
}

data class TotpCodeState(
    val code: String,
    val formattedCode: String,
    val remainingSeconds: Int,
    val progress: Float,
    val periodSeconds: Int = 30
)

object TotpGenerator {
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun generateTotpState(
        base32Secret: String,
        timestampMillis: Long = System.currentTimeMillis(),
        periodSeconds: Int = 30,
        digits: Int = 6,
        algorithm: TotpAlgorithm = TotpAlgorithm.SHA1
    ): TotpCodeState? {
        val cleanSecret = extractSecretFromUriOrRaw(base32Secret)
        if (cleanSecret.isBlank()) return null
        return try {
            val code = generateCode(
                base32Secret = cleanSecret,
                timestampSeconds = timestampMillis / 1000L,
                periodSeconds = periodSeconds,
                digits = digits,
                algorithm = algorithm
            )
            val elapsedInWindow = ((timestampMillis / 1000L) % periodSeconds).toInt()
            val remaining = periodSeconds - elapsedInWindow
            val progress = remaining.toFloat() / periodSeconds.toFloat()
            val mid = code.length / 2
            val formatted = "${code.substring(0, mid)} ${code.substring(mid)}"
            TotpCodeState(
                code = code,
                formattedCode = formatted,
                remainingSeconds = remaining,
                progress = progress.coerceIn(0f, 1f),
                periodSeconds = periodSeconds
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates an RFC 6238 TOTP code for the given secret and timestamp in seconds.
     */
    fun generateCode(
        base32Secret: String,
        timestampSeconds: Long = System.currentTimeMillis() / 1000L,
        periodSeconds: Int = 30,
        digits: Int = 6,
        algorithm: TotpAlgorithm = TotpAlgorithm.SHA1
    ): String {
        val secretBytes = decodeBase32(extractSecretFromUriOrRaw(base32Secret))
        require(secretBytes.isNotEmpty()) { "Neplatný Base32 TOTP klíč" }
        return generateCodeFromRawKey(
            secretBytes = secretBytes,
            timestampSeconds = timestampSeconds,
            periodSeconds = periodSeconds,
            digits = digits,
            algorithm = algorithm
        )
    }

    fun generateCodeFromRawKey(
        secretBytes: ByteArray,
        timestampSeconds: Long,
        periodSeconds: Int = 30,
        digits: Int = 6,
        algorithm: TotpAlgorithm = TotpAlgorithm.SHA1
    ): String {
        val counter = timestampSeconds / periodSeconds
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()

        val mac = Mac.getInstance(algorithm.hmacName)
        mac.init(SecretKeySpec(secretBytes, algorithm.hmacName))
        val hash = mac.doFinal(counterBytes)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)

        val modulus = 10.0.pow(digits.toDouble()).toLong()
        val otp = (binary.toLong() % modulus)
        return otp.toString().padStart(digits, '0')
    }

    fun extractSecretFromUriOrRaw(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
            val query = trimmed.substringAfter("?", "")
            val params = query.split("&")
            for (param in params) {
                val parts = param.split("=", limit = 2)
                if (parts.size == 2 && parts[0].equals("secret", ignoreCase = true)) {
                    return parts[1].uppercase().filter { it in BASE32_ALPHABET }
                }
            }
        }
        return trimmed.uppercase().filter { it in BASE32_ALPHABET }
    }

    fun decodeBase32(base32: String): ByteArray {
        val clean = base32.uppercase().replace("=", "").replace(" ", "").trim()
        if (clean.isEmpty()) return ByteArray(0)
        val output = ByteArray(clean.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        var index = 0
        for (ch in clean) {
            val value = BASE32_ALPHABET.indexOf(ch)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                output[index++] = ((buffer shr (bitsLeft - 8)) and 0xFF).toByte()
                bitsLeft -= 8
            }
        }
        return if (index == output.size) output else output.copyOf(index)
    }

    fun encodeBase32(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                sb.append(BASE32_ALPHABET[(buffer shr (bitsLeft - 5)) and 0x1F])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return sb.toString()
    }
}
