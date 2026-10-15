package app.ritela.data.sync

import androidx.room.withTransaction
import app.ritela.data.LocalPartnerKeys
import app.ritela.data.RitelaDatabase
import org.json.JSONObject

/** No key generation on profile open/migration. Called only by explicit sync setup. */
class SyncAuthorGrants(private val database: RitelaDatabase) {
    suspend fun initialize(keys: LocalPartnerKeys): String = database.withTransaction {
        val dao = database.syncState()
        dao.ensureDefaults()
        val state = dao.authorities().single()
        if (state.generation != 0L) {
            check(
                state.state == "ACTIVE" && state.ownerIdentity == keys.identityId &&
                    state.authorDevice == keys.deviceId
            )
            return@withTransaction requireNotNull(state.grantJson)
        }
        check(state.state == "UNINITIALIZED")
        val body = JSONObject().put("format", "ritela.author:2").put("owner", keys.identityId)
            .put("device", keys.deviceId).put("key", keys.devicePublic)
            .put("generation", 1).put("previous", JSONObject.NULL).toString()
        val grant = JSONObject().put("body", body).put(
            "signature",
            SyncEnvelopeCodec.signed(body, keys.devicePrivate)
        ).toString()
        dao.saveAuthority(
            SyncAuthorityRow(
                ownerIdentity = keys.identityId,
                authorDevice = keys.deviceId,
                generation = 1,
                state = "ACTIVE",
                grantJson = grant,
                grantDigest = SyncEnvelopeCodec.digest(grant.toByteArray())
            )
        )
        grant
    }

    /** The old phone retires atomically before releasing its handover signature.
     * New phone activation is deliberately unavailable until relay fencing is implemented.
     */
    suspend fun retire(keys: LocalPartnerKeys, proposal: String): String =
        database.withTransaction {
            require(proposal.toByteArray().size <= app.ritela.domain.sync.SyncLimits.GRANT_BYTES)
            database.syncReceive().handover()?.let {
                require(it.proposal == proposal)
                return@withTransaction it.grant
            }
            val state = database.syncState().authorities().single()
            check(
                state.state == "ACTIVE" && state.authorDevice == keys.deviceId &&
                    state.ownerIdentity == keys.identityId
            )
            val grant = JSONObject(proposal)
            val raw = grant.getString("body")
            val body = JSONObject(raw)
            require(
                body.getString("format") == "ritela.author:2" &&
                    body.getString("owner") == keys.identityId &&
                    body.getString("device") != keys.deviceId
            )
            require(
                SyncEnvelopeCodec.counter(
                    body,
                    "generation"
                ) == Math.addExact(state.generation, 1) &&
                    body.getString("previous") == state.grantDigest
            )
            val peer = database.partner().snapshot()?.let {
                app.ritela.data.PartnerCodec.directory(it.json)
            }?.identities?.single { it.id == keys.identityId }
            val device = requireNotNull(peer).devices.single {
                it.id == body.getString("device") && it.publicKey == body.getString("key") &&
                    it.enabled && it.mutualConfirmed
            }
            require(
                app.ritela.data.PartnerCrypto.verify(
                    app.ritela.data.PartnerCrypto.publicKey(device.publicKey, "EC"),
                    raw,
                    grant.getString("signature")
                )
            )
            database.syncState().saveAuthority(state.copy(state = "RETIRED"))
            database.syncQueue().activePackets().forEach {
                database.syncQueue().cancel(it.packetId)
            }
            val result = grant.put("handover", SyncEnvelopeCodec.signed(raw, keys.devicePrivate))
                .toString()
            database.syncReceive().handover(SyncHandoverRow(proposal = proposal, grant = result))
            result
        }
}
