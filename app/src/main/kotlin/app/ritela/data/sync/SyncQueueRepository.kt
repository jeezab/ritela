package app.ritela.data.sync

import androidx.room.withTransaction
import app.ritela.data.JournalCodec
import app.ritela.data.PartnerCodec
import app.ritela.data.RitelaDatabase
import app.ritela.domain.JournalLayout
import app.ritela.domain.sync.SyncBackoff
import app.ritela.domain.sync.SyncLease
import app.ritela.domain.sync.SyncLimits
import app.ritela.domain.sync.SyncLinkPolicy
import app.ritela.domain.sync.SyncQueuePort
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Internal draft, not a v2 wire format. A production authenticated encoder comes in later stages. */
class SyncDraft(
    val packetId: String,
    val peerIdentity: String,
    val peerDevice: String,
    val scopeEpoch: Long,
    val linkSequence: Long,
    val kind: String,
    val payload: String
) {
    override fun toString(): String = "SyncDraft(redacted)"
}

fun interface SyncPacketEncoder {
    suspend fun encode(draft: SyncDraft): ByteArray
}

enum class SyncPreparation { OFF, ENQUEUED, UNCHANGED, BLOCKED }

/** No default encoder/transport, no scheduler, no UI opt-in or author activation. */
class SyncQueueRepository(
    private val database: RitelaDatabase,
    private val encoder: SyncPacketEncoder,
    private val projector: ShareProjector = ShareProjector(),
    private val now: () -> Long = System::currentTimeMillis,
    private val jitter: () -> Double = { kotlin.random.Random.nextDouble() },
    private val queueLimit: Long = SyncLimits.DEVICE_BYTES.toLong()
) : SyncQueuePort {
    private val dao get() = database.syncQueue()
    private val journal get() = database.syncJournal()

    suspend fun prepare(peer: String, device: String): SyncPreparation = try {
        database.withTransaction {
            val policy = policy(peer, device) ?: return@withTransaction SyncPreparation.OFF
            dao.seed(SyncJournalStateRow())
            val hash = digest(canonicalJson(PartnerCodec.scope(requireNotNull(policy.scope))))
            val previous = dao.progress(peer, device, policy.scopeEpoch)
            if (previous != null && (
                    previous.cursor < 0 || previous.linkSequence < 0 ||
                        previous.requiresBaseline !in 0..1
                    )
            ) {
                throw SyncPreparationException("INVALID_PROGRESS")
            }
            if (previous != null && previous.policyDigest != hash) {
                throw SyncPreparationException("SCOPE_EPOCH_REQUIRED")
            }
            val baseline = previous == null || previous.requiresBaseline != 0 ||
                previous.cursor < (dao.floor() ?: 0)
            if (baseline) {
                backfill()
                dao.clearProjection(peer, device, policy.scopeEpoch)
            }
            val through = journal.watermark()
            val after = if (baseline) 0 else requireNotNull(previous).cursor
            val rows = if (baseline) {
                journal.latest()
            } else {
                journal.changes(after, through)
                    .groupBy { it.entityType to it.entityKey }.values.map { it.last() }
            }
            val projected = projector.project(rows, requireNotNull(policy.scope), baseline)
            val sequence = previous?.linkSequence ?: 0
            val next = Math.addExact(sequence, 1)
            val records = JSONArray()
            val updates = mutableListOf<ProjectedChange>()
            for (item in projected) {
                val old = dao.projection(peer, device, policy.scopeEpoch, item.type, item.key)
                val changed = if (item.body ==
                    null
                ) {
                    old != null
                } else {
                    old?.digest != digest(item.body)
                }
                if ((baseline && item.body != null) || (!baseline && changed)) {
                    records.put(
                        JSONObject().put("type", item.type).put("key", item.key)
                            .put("revision", next).put("deleted", item.body == null)
                            .put("value", item.body?.let(::JSONObject) ?: JSONObject.NULL)
                    )
                    updates += item
                }
            }
            if (!baseline && records.length() == 0) {
                dao.progress(
                    SyncLinkProgressRow(peer, device, policy.scopeEpoch, through, sequence, hash)
                )
                return@withTransaction SyncPreparation.UNCHANGED
            }
            if (records.length() >
                SyncLimits.DELTA_OPERATIONS
            ) {
                throw SyncPreparationException("NEEDS_CHUNKS")
            }
            val payload =
                canonicalJson(JSONObject().put("records", records))
            if (payload.toByteArray().size > SyncLimits.PROJECTED_BYTES) {
                throw SyncPreparationException("NEEDS_CHUNKS")
            }
            val id = UUID.randomUUID().toString()
            val kind = if (baseline) "BASELINE" else "DELTA"
            val bytes = encoder.encode(
                SyncDraft(id, peer, device, policy.scopeEpoch, next, kind, payload)
            )
                .copyOf()
            require(bytes.isNotEmpty() && bytes.size <= SyncLimits.ENVELOPE_BYTES)
            if (dao.queuedBytes() + bytes.size >
                queueLimit || dao.packetCount() >= 512 ||
                dao.packetCount(peer, device) >= SyncLimits.ROUTE_PACKETS
            ) {
                throw SyncPreparationException("QUEUE_FULL")
            }
            dao.insert(
                SyncOutboxRow(
                    id, peer, device, policy.scopeEpoch, hash, kind,
                    after, through, next, bytes
                )
            )
            for (item in updates) {
                if (item.body ==
                    null
                ) {
                    dao.removeProjection(peer, device, policy.scopeEpoch, item.type, item.key)
                } else {
                    dao.projection(
                        SyncProjectionRow(
                            peer,
                            device,
                            policy.scopeEpoch,
                            item.type,
                            item.key,
                            digest(item.body)
                        )
                    )
                }
            }
            dao.progress(SyncLinkProgressRow(peer, device, policy.scopeEpoch, through, next, hash))
            SyncPreparation.ENQUEUED
        }
    } catch (_: SyncPreparationException) {
        SyncPreparation.BLOCKED
    }

    /** A consistent first snapshot; it never opens the key vault or infers consent. */
    private suspend fun backfill() {
        val known = journal.latest().map { it.entityType to it.entityKey }.toSet()
        for (period in database.periods().snapshot()) {
            if ("PERIOD" to period.id !in known) {
                journal.insert(
                    SyncChangeRow(
                        entityType = "PERIOD",
                        entityKey = period.id,
                        deleted = false,
                        payload = ChangePayload.period(period)
                    )
                )
            }
        }
        for (day in database.dayLogs().snapshot()) {
            if ("DAY" to day.day.toString() !in known) {
                journal.insert(
                    SyncChangeRow(
                        entityType = "DAY",
                        entityKey = day.day.toString(),
                        deleted = false,
                        payload = ChangePayload.day(day)
                    )
                )
            }
        }
        if ("LAYOUT" to "current" !in known) {
            journal.insert(
                SyncChangeRow(
                    entityType = "LAYOUT",
                    entityKey = "current",
                    deleted = false,
                    payload =
                        database.journal().snapshot()?.config
                            ?: JournalCodec.encode(JournalLayout())
                )
            )
        }
    }

    override suspend fun claim(): SyncLease? = database.withTransaction {
        val time = now().also { require(it >= 0) }
        for (pending in dao.activePackets()) if (!eligible(pending)) cancel(pending)
        for (row in dao.candidates(time)) {
            if (!eligible(row)) {
                cancel(row)
                continue
            }
            if (row.attempts == Int.MAX_VALUE) continue
            val token = UUID.randomUUID().toString()
            dao.lease(row.packetId, token, safeTime(time, 120000))
            return@withTransaction SyncLease(
                row.packetId,
                token,
                row.peerIdentity,
                row.peerDevice,
                row.envelope.copyOf()
            )
        }
        null
    }

    override suspend fun authorized(lease: SyncLease): Boolean = database.withTransaction {
        val row = dao.packet(lease.packetId) ?: return@withTransaction false
        if (row.peerIdentity != lease.peerIdentity || row.peerDevice != lease.peerDevice ||
            !row.envelope.contentEquals(lease.bytes)
        ) {
            return@withTransaction false
        }
        if (row.leaseToken != lease.token || row.state != "IN_FLIGHT" || row.leaseUntil <= now()) {
            return@withTransaction false
        }
        if (eligible(row)) {
            true
        } else {
            cancel(row)
            false
        }
    }

    override suspend fun stored(lease: SyncLease): Boolean = database.withTransaction {
        if (!authorized(lease)) return@withTransaction false
        dao.finish(lease.packetId, lease.token, "STORED", 0, null) == 1
    }

    override suspend fun failed(lease: SyncLease, retryAfterMillis: Long): Boolean =
        database.withTransaction {
            if (!authorized(lease)) return@withTransaction false
            val row = requireNotNull(dao.packet(lease.packetId))
            val delay =
                maxOf(SyncBackoff.delay(row.attempts, jitter()), retryAfterMillis.coerceAtLeast(0))
            dao.finish(row.packetId, lease.token, "RETRY_WAIT", safeTime(now(), delay), "IO") == 1
        }

    /** Preserve latest source versions/tombstones, and never compact beyond outstanding work. */
    suspend fun compact(): Int = database.withTransaction {
        dao.seed(SyncJournalStateRow())
        val through = minOf(
            journal.watermark(),
            dao.minimumCursor() ?: Long.MAX_VALUE,
            dao.pendingFloor() ?: Long.MAX_VALUE
        )
        val removed = dao.compact(through)
        dao.floor(maxOf(dao.floor() ?: 0, through))
        removed
    }

    private suspend fun cancel(row: SyncOutboxRow) {
        dao.cancel(row.packetId)
        dao.invalidate(row.peerIdentity, row.peerDevice, row.scopeEpoch)
    }

    private suspend fun eligible(row: SyncOutboxRow): Boolean {
        val policy = policy(row.peerIdentity, row.peerDevice) ?: return false
        return policy.scopeEpoch == row.scopeEpoch &&
            digest(canonicalJson(PartnerCodec.scope(requireNotNull(policy.scope)))) ==
            row.policyDigest
    }

    private suspend fun policy(peer: String, device: String): SyncLinkPolicy? {
        val states = database.syncState().profileStates()
        if (states.size != 1 || states.single().id != 1 ||
            states.single().internetEnabled != 1
        ) {
            return null
        }
        val row = database.syncState().links().singleOrNull {
            it.peerIdentity == peer && it.peerDevice == device
        } ?: return null
        if (row.automaticEnabled != 1 || row.protocolVersion != 2 ||
            row.scopeJson == null
        ) {
            return null
        }
        return try {
            val scope = PartnerCodec.scope(JSONObject(row.scopeJson))
            // Whole-section grants must already be frozen to tag IDs; only the note is section-wide.
            require(scope.sections.all { it == "note" })
            val policy =
                SyncLinkPolicy(
                    peer, device, true, row.scopeEpoch, scope, row.protocolVersion,
                    row.acceptedOwnerGeneration, row.acceptedAuthorDevice, row.acceptedGrantDigest
                )
            val directory =
                database.partner().snapshot()?.let { PartnerCodec.directory(it.json) }
                    ?: return null
            val partner =
                directory.identities.singleOrNull { it.id == peer && it.exchangeEnabled }
                    ?: return null
            if (!partner.grants.permits(scope) || partner.devices.none {
                    it.id == device && it.enabled && it.mutualConfirmed
                }
            ) {
                null
            } else {
                policy
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: org.json.JSONException) {
            null
        }
    }
}

private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

private fun safeTime(now: Long, delay: Long): Long = if (now >
    Long.MAX_VALUE - delay
) {
    Long.MAX_VALUE
} else {
    now + delay
}
