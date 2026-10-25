package com.openvault.app.data.remote

import android.util.Base64
import com.openvault.app.security.hexToByteArray
import com.openvault.app.security.toHexString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes ByteArray to PostgreSQL BYTEA hex format `\x<hex_string>`
 * and deserializes `\x<hex_string>` (with Base64 fallback) back to ByteArray.
 */
object ByteaHexSerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PostgresByteaHex", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString("\\x" + value.toHexString())
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        val raw = decoder.decodeString().trim()
        if (raw.isEmpty()) return ByteArray(0)
        if (raw.startsWith("\\x", ignoreCase = true) || raw.startsWith("0x", ignoreCase = true)) {
            return raw.hexToByteArray()
        }
        val isPureHex = raw.length % 2 == 0 && raw.all {
            it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'
        }
        if (isPureHex) {
            return raw.hexToByteArray()
        }
        return try {
            Base64.decode(raw, Base64.DEFAULT)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(raw)
        }
    }
}
