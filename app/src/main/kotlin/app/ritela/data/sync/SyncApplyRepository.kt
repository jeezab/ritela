package app.ritela.data.sync

import androidx.room.withTransaction
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerCrypto
import app.ritela.data.RitelaDatabase
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import app.ritela.domain.sync.SyncConflict
import app.ritela.domain.sync.SyncLimits
import app.ritela.domain.sync.SyncRejected
import org.json.JSONArray
import org.json.JSONObject

/** Called only after authentication, address/nonce and current trust checks by PartnerRepository. */
class SyncApplyRepository(
    private val database: RitelaDatabase,
    private val validateSnapshot: (JSONObject) -> Unit
) {
    private val dao get() = database.syncReceive()

    /** Explicit recipient approval is required even when the device certificate is valid.
     * A successor also requires the previous author's countersignature; lost-device recovery
     * cannot bypass that rule here. Relay fencing/recovery is a later, separate operation.
     */
    suspend fun approveAuthor(peer: app.ritela.domain.PartnerIdentity, grant: String) =
        database.withTransaction {
            require(grant.toByteArray().size <= SyncLimits.GRANT_BYTES)
            val signed = JSONObject(grant)
            val raw = signed.getString("body")
            val body = JSONObject(raw)
            require(body.getString("format") == "ritela.author:2")
            require(body.getString("owner") == peer.id)
            val device = peer.devices.single {
                it.id == body.getString("device") && it.enabled && it.mutualConfirmed
            }
            require(body.getString("key") == device.publicKey)
            require(
                PartnerCrypto.verify(
                    PartnerCrypto.publicKey(device.publicKey, "EC"),
                    raw,
                    signed.getString("signature")
                )
            )
            val generation = SyncEnvelopeCodec.counter(body, "generation")
            val digest = SyncEnvelopeCodec.digest(grant.toByteArray())
            val previous = dao.author(peer.id)
            if (previous != null && previous.digest == digest) return@withTransaction
            if (previous == null) {
                if (generation != 1L || !body.isNull("previous")) reject(SyncConflict.STALE_AUTHOR)
            } else {
                if (generation <= previous.generation) reject(SyncConflict.CONFLICTING_GRANT)
                require(generation == Math.addExact(previous.generation, 1))
                require(
                    body.getString("previous") == previous.digest && device.id != previous.device
                )
                val old = peer.devices.single { it.id == previous.device }
                require(
                    PartnerCrypto.verify(
                        PartnerCrypto.publicKey(old.publicKey, "EC"),
                        raw,
                        signed.getString("handover")
                    )
                )
            }
            if (previous != null) dao.discardPending(peer.id)
            dao.author(SyncReceivedAuthorRow(peer.id, device.id, generation, digest, grant))
        }

    internal suspend fun receive(
        id: String,
        peer: String,
        device: String,
        envelope: ByteArray,
        content: JSONObject,
        received: Long
    ): Boolean = database.withTransaction {
        val wire = SyncEnvelopeCodec.decode(content)
        val digest = SyncEnvelopeCodec.digest(envelope)
        val prior = dao.packet(id)
        if (prior != null) {
            if (prior.identity != peer || prior.device != device || prior.digest != digest) {
                reject(SyncConflict.PACKET_ID_COLLISION)
            }
            return@withTransaction true
        }
        val author = dao.author(peer) ?: reject(SyncConflict.AUTHOR_APPROVAL_REQUIRED)
        if (author.device != device || author.generation != wire.generation ||
            author.digest != wire.grant
        ) {
            reject(SyncConflict.STALE_AUTHOR)
        }
        if (dao.sequence(peer, wire.generation, wire.epoch, wire.sequence) != null) {
            reject(SyncConflict.PACKET_ID_COLLISION)
        }
        if (dao.count() >= 65536 || dao.pending(peer).size >= SyncLimits.ROUTE_PACKETS ||
            dao.pendingBytes() + content.toString().toByteArray().size > SyncLimits.DEVICE_BYTES
        ) {
            reject(SyncConflict.INBOX_FULL)
        }
        dao.insert(
            SyncInboxRow(
                id, peer, device, digest, wire.generation, wire.epoch, wire.sequence,
                canonicalJson(content), received
            )
        )
        drain(peer)
        false
    }

    private suspend fun drain(peer: String) {
        val author = requireNotNull(dao.author(peer))
        var state = dao.state(peer)
        val pending = dao.pending(peer).filter { it.generation == author.generation }
        // Complete baselines may bridge a gap. An incomplete one never replaces visible data.
        val groups = pending.filter { JSONObject(it.payload).getString("kind") == "BASELINE" }
            .groupBy {
                val wire = SyncEnvelopeCodec.decode(JSONObject(it.payload))
                Triple(it.epoch, wire.firstSequence, wire.batch)
            }.values
        val complete = groups.filter { group ->
            val wires = group.map { SyncEnvelopeCodec.decode(JSONObject(it.payload)) }
            val first = wires.first()
            wires.all {
                it.parts == first.parts && it.grant == first.grant &&
                    it.scope == first.scope
            } &&
                wires.map { it.part }.toSet() == (0 until first.parts).toSet() &&
                group.sumOf {
                    it.payload.toByteArray().size.toLong()
                } <= SyncLimits.BASELINE_BYTES &&
                (
                    state == null || state.generation < author.generation ||
                        first.epoch > state.epoch ||
                        (first.epoch == state.epoch && group.maxOf { it.sequence } > state.sequence)
                    )
        }.maxWithOrNull(
            compareBy<List<SyncInboxRow>> { it.first().epoch }
                .thenBy { it.maxOf { row -> row.sequence } }
        )
        if (complete != null) {
            val wire = SyncEnvelopeCodec.decode(JSONObject(complete.first().payload))
            if (state != null && state.generation == author.generation &&
                state.epoch == wire.epoch
            ) {
                require(state.scope == wire.scope)
            }
            dao.reset(peer)
            val seen = mutableSetOf<Pair<String, String>>()
            for (row in complete.sortedBy { it.sequence }) {
                applyRecords(peer, SyncEnvelopeCodec.decode(JSONObject(row.payload)), seen)
            }
            val last = complete.maxBy { it.sequence }
            state = SyncReceivedStateRow(
                peer,
                author.generation,
                last.epoch,
                last.sequence,
                wire.scope,
                "{}",
                complete.maxOf { it.received }
            )
            complete.forEach { dao.applied(it.id) }
            dao.state(state)
        }
        var current = state ?: return
        if (current.generation != author.generation) return
        for (row in dao.pending(peer)) {
            if (row.generation != current.generation) continue
            if (row.epoch < current.epoch ||
                (row.epoch == current.epoch && row.sequence <= current.sequence)
            ) {
                dao.applied(row.id)
                continue
            }
            if (row.epoch != current.epoch || row.sequence != current.sequence + 1) continue
            val wire = SyncEnvelopeCodec.decode(JSONObject(row.payload))
            if (wire.kind != "DELTA") continue
            require(wire.scope == current.scope)
            applyRecords(peer, wire, mutableSetOf())
            current =
                current.copy(
                    sequence = row.sequence,
                    received = maxOf(current.received, row.received)
                )
            dao.applied(row.id)
        }
        val snapshot = snapshot(peer, current.scope)
        validateSnapshot(snapshot)
        require(snapshot.toString().toByteArray().size <= SyncLimits.BASELINE_BYTES)
        dao.state(current.copy(snapshot = canonicalJson(snapshot)))
    }

    private suspend fun applyRecords(
        peer: String,
        wire: SyncWirePacket,
        seen: MutableSet<Pair<String, String>>
    ) {
        for (i in 0 until wire.records.length()) {
            val record = wire.records.getJSONObject(i)
            val type = record.getString("type")
            val key = record.getString("key")
            require(seen.add(type to key))
            dao.record(
                SyncReceivedRecordRow(
                    peer,
                    type,
                    key,
                    record.getLong("revision"),
                    if (record.getBoolean(
                            "deleted"
                        )
                    ) {
                        null
                    } else {
                        canonicalJson(record.getJSONObject("value"))
                    }
                )
            )
        }
    }

    private suspend fun snapshot(peer: String, scope: String): JSONObject {
        val result = JSONObject().put(
            "scope",
            JSONObject(scope)
        )
        val periods = JSONArray()
        val days = JSONArray()
        for (row in dao.records(peer).sortedWith(compareBy({ it.type }, { it.key }))) {
            val value = row.value?.let(::JSONObject) ?: continue
            when (row.type) {
                "PERIOD" -> periods.put(value)

                "DAY" -> days.put(value)

                "LAYOUT" -> result.put("labels", value)

                "FORECAST" -> {
                    require(
                        value.keys().asSequence().all {
                            it in setOf(
                                "periods", "forecasts", "cycleDay", "duration", "phase", "asOf",
                                "calculatedAt", "algorithm", "cycleLength"
                            )
                        }
                    )
                    value.keys().forEach { result.put(it, value.get(it)) }
                }
            }
        }
        if (periods.length() > 0) result.put("periods", periods)
        if (PartnerCodec.scope(JSONObject(scope)).categories.any {
                it in setOf(ShareCategory.TAGS, ShareCategory.SECTIONS)
            }
        ) {
            result.put("days", days)
        }
        return result
    }

    suspend fun clear(peer: String) = database.withTransaction {
        dao.erase(peer)
        dao.discardPending(peer)
        dao.state(peer)?.let { dao.state(it.copy(snapshot = "{}")) }
    }

    private fun reject(reason: SyncConflict): Nothing = throw SyncRejected(reason)
}
