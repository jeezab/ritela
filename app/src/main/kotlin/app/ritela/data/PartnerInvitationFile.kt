package app.ritela.data

import org.json.JSONObject

/** Public signed invitation, separate from an encrypted calendar/message packet. */
object PartnerInvitationFile {
    const val MAX_BYTES = 12000
    fun encode(invitation: String): ByteArray = invitation.toByteArray(Charsets.UTF_8).also {
        require(it.size in 1..MAX_BYTES)
        require(decode(it) != null)
    }
    fun decode(bytes: ByteArray): String? {
        require(bytes.size in 1..PartnerCrypto.MAX_PACKET)
        val text = bytes.toString(Charsets.UTF_8)
        val envelope = JSONObject(text)
        if (!envelope.has("body")) return null
        val body = JSONObject(envelope.getString("body"))
        if (body.optString("format") != "ritela.pair") return null
        require(bytes.size <= MAX_BYTES)
        return text
    }
}
