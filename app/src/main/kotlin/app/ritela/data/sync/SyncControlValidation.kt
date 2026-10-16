package app.ritela.data.sync

import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerCrypto
import app.ritela.domain.PartnerDevice
import app.ritela.domain.PartnerIdentity
import app.ritela.domain.ShareCategory
import app.ritela.domain.sync.SyncLimits
import java.util.UUID
import org.json.JSONObject

/** Validate optional authenticated control fields before persistence or UI projection. */
internal object SyncControlValidation {
    fun validate(content: JSONObject, peer: PartnerIdentity, device: PartnerDevice) {
        if (content.has("connectionReply")) require(hex(content.getString("connectionReply")))
        for (key in listOf("authorRequest", "authorApproval")) {
            if (!content.has(key)) continue
            val value = content.getJSONObject(key)
            require(value.getString("context") == device.verificationContext)
            SyncEnvelopeCodec.counter(value, "epoch")
            val scope = PartnerCodec.scope(value.getJSONObject("scope"))
            require(scope == scope.normalized() && scope.categories.isNotEmpty())
            require(scope.sections.all { it == "note" })
            require(
                ShareCategory.DIARY !in scope.categories &&
                    ShareCategory.SETTINGS !in scope.categories
            )
            if (key == "authorApproval") {
                val id = value.getString("request")
                require(UUID.fromString(id).toString() == id && hex(value.getString("grant")))
                require(value.get("published") == true)
            } else {
                require(value.getLong("bytes") in 1..6291456L && value.getLong("parts") in 1..64L)
                if (value.has("renewal")) require(value.get("renewal") is Boolean)
                val grant = value.getString("grant")
                require(grant.toByteArray().size <= SyncLimits.GRANT_BYTES)
                val envelope = JSONObject(grant)
                val raw = envelope.getString("body")
                val body = JSONObject(raw)
                require(
                    body.getString("format") == "ritela.author:2" &&
                        body.getString("owner") == peer.id
                )
                require(
                    body.getString("device") == device.id &&
                        body.getString("key") == device.publicKey
                )
                require(
                    SyncEnvelopeCodec.counter(body, "generation") == 1L && body.isNull("previous")
                )
                require(
                    PartnerCrypto.verify(
                        PartnerCrypto.publicKey(device.publicKey, "EC"),
                        raw,
                        envelope.getString("signature")
                    )
                )
            }
        }
    }

    private fun hex(value: String) = value.matches(Regex("[0-9a-f]{64}"))
}
