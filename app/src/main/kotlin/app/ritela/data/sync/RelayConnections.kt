package app.ritela.data.sync

import app.ritela.data.PairInvitation
import app.ritela.data.PartnerCrypto
import app.ritela.data.PartnerRepository
import app.ritela.data.RitelaDatabase
import java.io.IOException
import java.security.MessageDigest
import org.json.JSONObject

internal fun pairingSlot(deviceId: String, nonce: String): String =
    slot("ritela.pairing:1", deviceId, nonce)

internal fun routeSlot(deviceId: String, nonce: String): String =
    slot("ritela.route:1", deviceId, nonce)

private fun slot(label: String, deviceId: String, nonce: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$label:$deviceId:$nonce".toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

/** Public certificates travel with signed invitations. Capabilities remain protected locally. */
class RelayConnections(
    private val database: RitelaDatabase,
    private val partner: PartnerRepository,
    private val runtime: RelayRuntime,
    private val origin: String,
    private val online: () -> Boolean = { true }
) {
    suspend fun invitation(invitation: String, target: PairInvitation?): String {
        if (!runtime.registered()) return invitation
        val offer = JSONObject().put("origin", origin).put("certificate", runtime.certificate())
            .put("caps", SyncEnvelopeCodec.capabilities())
        if (target != null) {
            val peer = partner.snapshot().identities.single { it.id == target.identity }
            val device = peer.devices.single { it.id == target.device.id }
            val known = database.relay().peer(peer.id, device.id)
            if (device.mutualConfirmed && known != null && online() && runtime.foregroundReady()) {
                val setup = optionalRelay {
                    partner.prepareRelaySetup(
                        peer.id,
                        device.id,
                        JSONObject().put("certificate", runtime.certificate())
                            .put(
                                "route",
                                runtime.receiveOffer(peer.id, device.id, known.certificate)
                            )
                    )
                }
                setup?.let { offer.put("setup", it.envelope) }
            }
        }
        val body = JSONObject(
            JSONObject(invitation).getString("body")
        ).put("relay", offer.toString()).toString()
        val local = partner.identity()
        return JSONObject().put("body", body).put(
            "signature",
            PartnerCrypto.sign(PartnerCrypto.privateKey(local.devicePrivate, "EC"), body)
        ).toString().also {
            require(it.toByteArray().size <= app.ritela.data.PartnerInvitationFile.MAX_BYTES)
        }
    }

    /** Pull a confirmation or a renewed route, and refresh a route that has expired. */
    suspend fun maintain() {
        if (!runtime.registered() || !online()) return
        val keys = partner.identity()
        runCatching {
            runtime.readPairing(pairingSlot(keys.deviceId, keys.nonce))?.let { raw ->
                partner.acceptMailbox(raw)
            }
        }
        runCatching {
            runtime.readPairing(routeSlot(keys.deviceId, keys.nonce))?.let { raw ->
                runtime.installSendRoute(raw)
            }
        }
        partner.snapshot().identities.filter { it.exchangeEnabled }.forEach { peer ->
            peer.devices.filter { it.enabled && it.mutualConfirmed }.forEach { device ->
                if (runtime.sendRoute(peer.id, device.id) != null) return@forEach
                val certificate = database.relay().peer(peer.id, device.id)?.certificate
                    ?: return@forEach
                runCatching {
                    val offer = runtime.receiveOffer(peer.id, device.id, certificate)
                    runtime.publishPairing(routeSlot(device.id, device.remoteNonce), offer)
                }
            }
        }
    }

    /** Called only after the user accepts a signed invitation, including the existing code check. */
    suspend fun accepted(invitation: PairInvitation) {
        val raw = invitation.relayOffer ?: return
        if (!runtime.registered()) return
        val offer = JSONObject(raw)
        require(offer.getString("origin") == origin)
        SyncEnvelopeCodec.validateCapabilities(offer.getJSONObject("caps"))
        val peer = partner.snapshot().identities.single {
            it.id == invitation.identity &&
                it.exchangeEnabled
        }
        val device = peer.devices.single { it.id == invitation.device.id && it.enabled }
        // This validates the key only; a pending peer still cannot send, create a route or receive data.
        val certificate = offer.getString("certificate")
        RelayTransportCertificate.verify(peer, device.id, certificate, requireMutual = false)
        database.relay().peer(RelayPeerRow(peer.id, device.id, certificate))
        if (!device.mutualConfirmed) return
        database.syncReceive().capability(
            SyncCapabilityRow(
                peer.id,
                device.id,
                "${device.localNonce}:${device.remoteNonce}",
                true,
                true
            )
        )
        if (!offer.has("setup")) return
        partner.receive(offer.getString("setup").toByteArray())
        val remoteRoute = requireNotNull(runtime.sendRoute(peer.id, device.id))
        val replied = database.partner().controls(peer.id, device.id, true).any {
            JSONObject(it.body).optString("connectionReply") == remoteRoute
        }
        if (!replied) {
            if (!online() || !runtime.foregroundReady()) return
            optionalRelay {
                runtime.queueControl(
                    peer.id,
                    device.id,
                    JSONObject().put("certificate", runtime.certificate())
                        .put("route", runtime.receiveOffer(peer.id, device.id, certificate)),
                    JSONObject().put("connectionReply", remoteRoute)
                )
            }?.let { partner.relayWake?.invoke() }
        }
    }

    /** Internet setup is optional; an outage must never break the existing manual pairing. */
    private suspend fun <T> optionalRelay(action: suspend () -> T): T? = try {
        action()
    } catch (_: IOException) {
        null
    }
}
