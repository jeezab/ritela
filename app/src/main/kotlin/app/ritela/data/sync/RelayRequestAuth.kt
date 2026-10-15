package app.ritela.data.sync

import app.ritela.data.PartnerCrypto
import java.security.PrivateKey
import java.security.Signature
import java.util.UUID

/** Exact ASCII wire format shared with relay/src/auth.ts. Sign the complete query and capability. */
object RelayRequestAuth {
    fun canonical(
        method: String,
        target: String,
        handle: String,
        keyVersion: Int,
        timestamp: Long,
        requestId: String,
        body: ByteArray,
        capability: String = ""
    ): String {
        require(method in setOf("GET", "POST", "PUT", "DELETE"))
        require(target.startsWith("/v1/") && target.matches(Regex("[a-zA-Z0-9/?.=&_-]+")))
        require(!target.contains("//"))
        require(
            UUID.fromString(handle).toString() == handle &&
                UUID.fromString(requestId).toString() == requestId
        )
        require(keyVersion == 1 && timestamp in 1000000000000L..9999999999999L)
        require(capability.isEmpty() || capability.matches(Regex("[0-9a-f]{64}")))
        return listOf(
            "ritela.relay", "1", method, target, handle, keyVersion, timestamp,
            requestId,
            SyncEnvelopeCodec.digest(
                body
            ),
            SyncEnvelopeCodec.digest(capability.toByteArray())
        )
            .joinToString("\n")
    }

    fun sign(privateKey: PrivateKey, canonical: String): String = PartnerCrypto.b64(
        derToRaw(
            Signature.getInstance("SHA256withECDSA").run {
                initSign(privateKey)
                update(canonical.toByteArray(Charsets.UTF_8))
                sign()
            }
        )
    )

    /** JCA DER integers -> WebCrypto IEEE P1363. No curve arithmetic or custom crypto. */
    internal fun derToRaw(der: ByteArray): ByteArray {
        require(der.size in 8..72 && der[0] == 0x30.toByte() && der[1].toInt() == der.size - 2)
        var offset = 2
        val raw = ByteArray(64)
        repeat(2) { index ->
            require(offset + 2 <= der.size && der[offset++] == 2.toByte())
            val length = der[offset++].toInt()
            require(length in 1..33 && offset + length <= der.size && der[offset].toInt() >= 0)
            val padded = length > 1 && der[offset] == 0.toByte()
            if (padded) require(der[offset + 1].toInt() < 0)
            val count = length - if (padded) 1 else 0
            require(count <= 32)
            der.copyInto(
                raw,
                index * 32 + 32 - count,
                offset + if (padded) 1 else 0,
                offset + length
            )
            offset += length
        }
        require(offset == der.size)
        return raw
    }

    internal fun rawToDer(raw: ByteArray): ByteArray {
        require(raw.size == 64)
        val integers = (0..1).map { index ->
            val half = raw.copyOfRange(index * 32, index * 32 + 32)
            val first = half.indexOfFirst { it != 0.toByte() }.let { if (it < 0) 31 else it }
            val value = half.copyOfRange(first, 32)
            val positive = if (value[0].toInt() < 0) byteArrayOf(0) + value else value
            byteArrayOf(2, positive.size.toByte()) + positive
        }
        val body = integers[0] + integers[1]
        return byteArrayOf(0x30, body.size.toByte()) + body
    }
}
