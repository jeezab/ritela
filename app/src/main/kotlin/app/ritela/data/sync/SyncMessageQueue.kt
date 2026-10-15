package app.ritela.data.sync

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerMessage
import app.ritela.data.RitelaDatabase
import app.ritela.domain.sync.SyncLimits
import org.json.JSONObject

/** Durable confirmed-send intent, not an HTTP packet. No medical content or capabilities here. */
@Entity(tableName = "sync_message_intents", indices = [Index(value = ["state"])])
data class SyncMessageIntentRow(
    @PrimaryKey val messageId: String,
    val identity: String,
    val device: String,
    val kind: String,
    val epoch: Long,
    val context: String,
    val grantsDigest: String,
    val messageDigest: String,
    val state: String,
    val errorCode: String? = null
)

@Dao
interface SyncMessageIntentDao {
    @Insert
    suspend fun insert(row: SyncMessageIntentRow)

    @Query("SELECT * FROM sync_message_intents WHERE messageId=:id")
    suspend fun get(id: String): SyncMessageIntentRow?

    @Query("SELECT * FROM sync_message_intents ORDER BY rowid")
    suspend fun all(): List<SyncMessageIntentRow>

    @Query(
        "SELECT * FROM sync_message_intents WHERE state IN ('PENDING_WRAP','BLOCKED') ORDER BY rowid"
    )
    suspend fun active(): List<SyncMessageIntentRow>

    @Query("SELECT COUNT(*) FROM sync_message_intents")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM sync_message_intents WHERE state='PENDING_WRAP'")
    suspend fun pendingCount(): Int

    @Query("UPDATE sync_message_intents SET state=:state,errorCode=:error WHERE messageId=:id")
    suspend fun finish(id: String, state: String, error: String? = null)
}

enum class SyncMessageOffer { OFF, QUEUED, EXISTS, BLOCKED, TERMINAL }

/** Only explicit send/receipt creation may offer a message. No history backfill on opt-in.
 * Existing PartnerMessage owns the payload; an intent never contains a manual envelope for HTTP.
 * Wrapping/dispatch are deferred until stage 5 provides durable HPKE transport bindings.
 */
class SyncMessageQueue(private val database: RitelaDatabase) {
    private val dao get() = database.syncMessageIntents()

    suspend fun offer(message: PartnerMessage): SyncMessageOffer = database.withTransaction {
        require(message.outgoing && message.kind in setOf("DATA", "DOODLE", "ACK", "OPENED"))
        val old = dao.get(message.id)
        if (old != null) {
            require(
                old.identity == message.identity && old.device == message.device &&
                    old.kind == message.kind && old.messageDigest == digest(message)
            )
            return@withTransaction if (old.state in
                setOf("CANCELLED", "ACKED")
            ) {
                SyncMessageOffer.TERMINAL
            } else {
                SyncMessageOffer.EXISTS
            }
        }
        if (message.hiddenFromChat || message.delivered || message.envelope.isEmpty() ||
            message.body.isEmpty()
        ) {
            return@withTransaction SyncMessageOffer.TERMINAL
        }
        val saved = database.partner().message(message.id)
        require(saved == message) // Must be in the same transaction as the confirmed message.
        val link = binding(message) ?: return@withTransaction SyncMessageOffer.OFF
        if (dao.count() >= 65536 ||
            dao.pendingCount() >= 512
        ) {
            return@withTransaction SyncMessageOffer.BLOCKED
        }
        val error = if (withinBudget(message)) null else "MESSAGE_TOO_LARGE"
        dao.insert(
            SyncMessageIntentRow(
                message.id, message.identity, message.device, message.kind, link.epoch,
                link.context, link.grants, digest(message),
                if (error == null) "PENDING_WRAP" else "BLOCKED", error
            )
        )
        if (error == null) SyncMessageOffer.QUEUED else SyncMessageOffer.BLOCKED
    }

    /** Recheck just before any future wrapping/dispatch; never resurrect terminal IDs. */
    suspend fun pending(): List<PartnerMessage> = database.withTransaction {
        val result = mutableListOf<PartnerMessage>()
        for (row in dao.active()) {
            val message = database.partner().message(row.messageId)
            val link = message?.let { binding(it) }
            if (message == null || message.hiddenFromChat || message.body.isEmpty() ||
                message.envelope.isEmpty()
            ) {
                dao.finish(row.messageId, "CANCELLED", "CONTENT_REMOVED")
            } else if (message.delivered) {
                dao.finish(row.messageId, "ACKED")
            } else if (link == null || row.epoch != link.epoch || row.context != link.context ||
                row.grantsDigest != link.grants || row.messageDigest != digest(message)
            ) {
                dao.finish(row.messageId, "CANCELLED", "ACCESS_CHANGED")
            } else if (row.state == "PENDING_WRAP") {
                result += message
            }
        }
        result
    }

    suspend fun cancel(id: String) = database.withTransaction {
        if (dao.get(id)?.state != "ACKED") dao.finish(id, "CANCELLED", "CONTENT_REMOVED")
    }

    /** Called only in the existing authenticated receipt transaction for this exact peer/device. */
    internal suspend fun acknowledged(id: String, identity: String, device: String) =
        database.withTransaction {
            val row = dao.get(id) ?: return@withTransaction
            require(row.identity == identity && row.device == device)
            if (row.state != "CANCELLED") dao.finish(id, "ACKED")
        }

    private data class Binding(val epoch: Long, val context: String, val grants: String)

    private suspend fun binding(message: PartnerMessage): Binding? {
        val state = database.syncState().profileStates().singleOrNull()
        if (state?.id != 1 || state.internetEnabled != 1) return null
        val link = database.syncState().links().singleOrNull {
            it.peerIdentity == message.identity && it.peerDevice == message.device
        } ?: return null
        if (link.protocolVersion != 2 || link.scopeEpoch <= 0) return null
        return try {
            val peer = database.partner().snapshot()?.let { PartnerCodec.directory(it.json) }
                ?.identities?.singleOrNull { it.id == message.identity && it.exchangeEnabled }
                ?: return null
            val device = peer.devices.singleOrNull {
                it.id == message.device && it.enabled && it.mutualConfirmed
            } ?: return null
            // Message intent is separate from auto-send scope/author approval. Both pairing nonces matter.
            val context = SyncEnvelopeCodec.digest(
                "${device.localNonce}\n${device.remoteNonce}\n${requireNotNull(
                    device.verificationContext
                )}\n${device.publicKey}".toByteArray()
            )
            Binding(
                link.scopeEpoch,
                context,
                SyncEnvelopeCodec.digest(
                    canonicalJson(PartnerCodec.scope(peer.grants)).toByteArray()
                )
            )
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: org.json.JSONException) {
            null
        }
    }

    private fun withinBudget(message: PartnerMessage): Boolean = try {
        val body = JSONObject(message.body)
        val bytes = message.body.toByteArray().size
        if (bytes > SyncLimits.PROJECTED_BYTES - 2048) {
            false
        } else {
            val doodle = when {
                message.kind == "DOODLE" -> body
                message.kind == "DATA" && body.has("doodle") -> body.getJSONObject("doodle")
                else -> null
            }
            doodle == null || PartnerCodec.doodle(doodle).let {
                SyncLimits.acceptsNewDoodle(
                    it.strokes.size,
                    it.strokes.map { stroke ->
                        stroke.points.size
                    },
                    it.caption.length,
                    doodle.toString().toByteArray().size
                )
            }
        }
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: org.json.JSONException) {
        false
    }

    private fun digest(message: PartnerMessage): String = SyncEnvelopeCodec.digest(
        JSONObject().put(
            "id",
            message.id
        ).put("identity", message.identity).put("device", message.device)
            .put("kind", message.kind).put("body", message.body).put("envelope", message.envelope)
            .toString().toByteArray()
    )
}
