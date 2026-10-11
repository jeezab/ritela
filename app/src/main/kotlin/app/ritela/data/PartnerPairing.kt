package app.ritela.data

import java.math.BigInteger
import java.security.MessageDigest
import org.json.JSONArray

/** Canonical, role-independent binding of both certified device keys and signed invitation nonces. */
internal fun pairingContext(local: LocalPartnerKeys, remote: PairInvitation): String {
    fun endpoint(identityKey: String, device: String, deviceKey: String, nonce: String): String =
        JSONArray(listOf(identityKey, device, deviceKey, nonce)).toString()
    val endpoints = listOf(
        endpoint(local.identityPublic, local.deviceId, local.devicePublic, local.nonce),
        endpoint(remote.key, remote.device.id, remote.device.publicKey, remote.device.remoteNonce)
    ).sorted()
    val canonical = JSONArray(listOf("ritela.pair.confirm:2") + endpoints).toString()
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/** Display only: full 256-bit context matching, not the eight digits, gates mutual trust. */
internal fun pairingCode(context: String): String =
    BigInteger(context, 16).mod(BigInteger.valueOf(100000000)).toString().padStart(8, '0')
        .chunked(4).joinToString(" · ")
