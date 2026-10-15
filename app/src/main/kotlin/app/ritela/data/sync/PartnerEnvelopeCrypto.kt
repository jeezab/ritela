package app.ritela.data.sync

import app.ritela.data.LocalPartnerKeys
import app.ritela.data.PartnerCrypto
import app.ritela.domain.PartnerIdentity
import app.ritela.domain.sync.SyncLimits
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.hybrid.HybridConfig
import java.util.UUID
import org.json.JSONObject

/** HPKE base mode authenticates the recipient/context, not the sender. The device signature
 * inside is mandatory. Static receiver key compromise does not have forward secrecy.
 */
object PartnerEnvelopeCrypto {
    const val SUITE = "HPKE-X25519-HKDF-SHA256-AES256GCM-TINK-RAW-1"
    private val parameters = HpkeParameters.builder()
        .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
        .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
        .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
        .setVariant(HpkeParameters.Variant.NO_PREFIX).build()

    init {
        HybridConfig.register()
    }
    internal fun newKeyset(): KeysetHandle = KeysetHandle.generateNew(parameters)
    fun publicKey(keyset: KeysetHandle): String =
        TinkJsonProtoKeysetFormat.serializeKeysetWithoutSecret(keyset.publicKeysetHandle)

    fun validatePublicKey(publicKey: String) {
        require(publicKey.toByteArray().size <= 16384)
        val keyset = TinkJsonProtoKeysetFormat.parseKeysetWithoutSecret(publicKey)
        require(keyset.size() == 1 && keyset.getAt(0).key.parameters == parameters)
    }

    private fun context(route: String, id: String, publicKey: String): ByteArray {
        require(route.matches(Regex("[0-9a-f]{64}")) && UUID.fromString(id).toString() == id)
        require(publicKey.toByteArray().size <= SyncLimits.GRANT_BYTES)
        return "$SUITE\n$route\n$id\n${SyncEnvelopeCodec.digest(publicKey.toByteArray())}"
            .toByteArray(Charsets.UTF_8)
    }

    fun seal(
        keys: LocalPartnerKeys,
        recipient: String,
        recipientDevice: String,
        linkNonce: String,
        route: String,
        id: String,
        recipientPublicKey: String,
        content: JSONObject
    ): ByteArray {
        require(recipient.matches(Regex("[0-9a-f]{64}")))
        require(UUID.fromString(recipientDevice).toString() == recipientDevice)
        require(UUID.fromString(linkNonce).toString() == linkNonce)
        val body = JSONObject().put("format", "ritela.hpke.inner").put("v", 1)
            .put("from", keys.identityId).put("device", keys.deviceId).put("to", recipient)
            .put("targetDevice", recipientDevice).put("link", linkNonce).put("id", id)
            .put("content", content).toString()
        require(body.toByteArray().size <= SyncLimits.PROJECTED_BYTES)
        val signed = JSONObject().put("body", body).put(
            "signature",
            PartnerCrypto.sign(
                PartnerCrypto.privateKey(keys.devicePrivate, "EC"),
                body
            )
        ).toString().toByteArray()
        val keyset = TinkJsonProtoKeysetFormat.parseKeysetWithoutSecret(recipientPublicKey)
        require(keyset.size() == 1 && keyset.getAt(0).key.parameters == parameters)
        return keyset.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
            .encrypt(signed, context(route, id, recipientPublicKey)).also {
                require(it.size <= SyncLimits.ENVELOPE_BYTES)
            }
    }

    fun open(
        keyset: KeysetHandle,
        local: LocalPartnerKeys,
        peer: PartnerIdentity,
        route: String,
        id: String,
        ciphertext: ByteArray
    ): JSONObject {
        require(peer.exchangeEnabled && ciphertext.size in 1..SyncLimits.ENVELOPE_BYTES)
        val bytes = keyset.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
            .decrypt(ciphertext, context(route, id, publicKey(keyset)))
        try {
            require(bytes.size <= SyncLimits.ENVELOPE_BYTES)
            val envelope = JSONObject(bytes.toString(Charsets.UTF_8))
            val raw = envelope.getString("body")
            require(raw.toByteArray(Charsets.UTF_8).size <= SyncLimits.PROJECTED_BYTES)
            val body = JSONObject(raw)
            require(body.getString("format") == "ritela.hpke.inner" && body.getInt("v") == 1)
            require(
                body.getString("from") == peer.id && body.getString("to") == local.identityId &&
                    body.getString("targetDevice") == local.deviceId && body.getString("id") == id
            )
            val device = peer.devices.single {
                it.id == body.getString("device") && it.enabled && it.mutualConfirmed
            }
            require(body.getString("link") == device.localNonce)
            require(
                PartnerCrypto.verify(
                    PartnerCrypto.publicKey(device.publicKey, "EC"),
                    raw,
                    envelope.getString("signature")
                )
            )
            return body
        } finally {
            bytes.fill(0)
        }
    }
}
