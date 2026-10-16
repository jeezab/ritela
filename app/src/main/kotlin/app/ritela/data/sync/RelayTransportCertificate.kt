package app.ritela.data.sync

import app.ritela.data.LocalPartnerKeys
import app.ritela.data.PartnerCrypto
import app.ritela.domain.PartnerIdentity
import java.util.UUID
import org.json.JSONObject

/** Exchanged inside an existing authenticated manual link, never sent to relay registration. */
object RelayTransportCertificate {
    fun issue(identity: LocalPartnerKeys, relay: RelayKeys): String {
        val body = JSONObject().put(
            "format",
            "ritela.transport:1"
        ).put("owner", identity.identityId)
            .put(
                "device",
                identity.deviceId
            ).put("nonce", identity.nonce).put("handle", relay.handle)
            .put("apiKey", relay.apiPublic).put("keyVersion", 1)
            .put("suite", PartnerEnvelopeCrypto.SUITE)
            .put("hpke", PartnerEnvelopeCrypto.publicKey(relay.hpke)).toString()
        return JSONObject().put("body", body).put(
            "signature",
            PartnerCrypto.sign(
                PartnerCrypto.privateKey(identity.devicePrivate, "EC"),
                body
            )
        ).toString()
    }

    fun verify(
        peer: PartnerIdentity,
        deviceId: String,
        certificate: String,
        requireMutual: Boolean = true
    ): JSONObject {
        require(peer.exchangeEnabled && certificate.toByteArray().size <= 16384)
        val device = peer.devices.single {
            it.id == deviceId && it.enabled &&
                (!requireMutual || it.mutualConfirmed)
        }
        val envelope = JSONObject(certificate)
        val raw = envelope.getString("body")
        require(
            PartnerCrypto.verify(
                PartnerCrypto.publicKey(device.publicKey, "EC"),
                raw,
                envelope.getString("signature")
            )
        )
        val body = JSONObject(raw)
        require(
            body.getString(
                "format"
            ) == "ritela.transport:1" && body.getString("owner") == peer.id &&
                body.getString("device") == deviceId &&
                body.getString("nonce") == device.remoteNonce
        )
        require(
            body.getInt("keyVersion") == 1 && body.getString("suite") == PartnerEnvelopeCrypto.SUITE
        )
        val handle = body.getString("handle")
        require(UUID.fromString(handle).toString() == handle)
        require(PartnerCrypto.publicKey(body.getString("apiKey"), "EC").algorithm == "EC")
        PartnerEnvelopeCrypto.validatePublicKey(body.getString("hpke"))
        return body
    }
}
