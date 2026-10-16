package app.ritela.data.sync

import androidx.room.Dao
import androidx.room.Query
import androidx.room.withTransaction
import app.ritela.data.PartnerCodec
import app.ritela.data.RitelaDatabase
import app.ritela.domain.ShareCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONObject

data class SyncSettingsRow(
    val id: String,
    val identity: String,
    val device: String,
    val kind: String,
    val state: String,
    val records: Boolean,
    val delivered: Boolean,
    val error: String?,
    val created: Long
)

@Dao
interface SyncSettingsDao {
    @Query(
        "SELECT * FROM (SELECT i.messageId AS id,i.identity,i.device,i.kind," +
            "CASE WHEN i.state IN ('CANCELLED','ACKED','BLOCKED') THEN i.state " +
            "ELSE COALESCE(r.state,i.state) END AS state," +
            "0 AS records,COALESCE(m.delivered,0) AS delivered,i.errorCode AS error," +
            "COALESCE(m.created,0) AS created FROM sync_message_intents i " +
            "LEFT JOIN partner_messages m ON m.id=i.messageId " +
            "LEFT JOIN relay_packets r ON r.id=i.messageId UNION ALL " +
            "SELECT o.packetId,o.peerIdentity,o.peerDevice,o.kind,o.state,1," +
            "COALESCE(m.delivered,0),o.errorCode,COALESCE(m.created,0) FROM sync_outbox o " +
            "LEFT JOIN partner_messages m ON m.id=o.packetId) " +
            "ORDER BY CASE WHEN state IN ('CANCELLED','ACKED') THEN 1 ELSE 0 END,created DESC LIMIT 100"
    )
    suspend fun rows(): List<SyncSettingsRow>

    @Query("UPDATE sync_profile_state SET internetEnabled=0 WHERE id=1")
    suspend fun disableInternet()

    @Query(
        "UPDATE sync_message_intents SET state='CANCELLED',errorCode='USER_CANCELLED' " +
            "WHERE state IN ('PENDING_WRAP','BLOCKED') AND (:id IS NULL OR messageId=:id)"
    )
    suspend fun cancelMessages(id: String? = null)

    @Query(
        "UPDATE sync_links SET automaticEnabled=0 " +
            "WHERE peerIdentity=:identity AND peerDevice=:device"
    )
    suspend fun pauseAutomatic(identity: String, device: String)

    @Query(
        "UPDATE sync_link_progress SET requiresBaseline=1 WHERE " +
            "(:identity IS NULL OR (peerIdentity=:identity AND peerDevice=:device)) AND EXISTS " +
            "(SELECT 1 FROM sync_outbox o WHERE o.peerIdentity=sync_link_progress.peerIdentity " +
            "AND o.peerDevice=sync_link_progress.peerDevice " +
            "AND o.scopeEpoch=sync_link_progress.scopeEpoch " +
            "AND o.state IN ('READY','RETRY_WAIT','IN_FLIGHT'))"
    )
    suspend fun invalidateRecords(identity: String? = null, device: String? = null)

    @Query(
        "UPDATE sync_outbox SET state='CANCELLED',envelope=X'',leaseToken=NULL,leaseUntil=0 " +
            "WHERE state IN ('READY','RETRY_WAIT','IN_FLIGHT') AND " +
            "(:identity IS NULL OR (peerIdentity=:identity AND peerDevice=:device))"
    )
    suspend fun cancelRecords(identity: String? = null, device: String? = null)
}

enum class SyncQueueStatus { WAITING, BLOCKED, AWAITING_RECEIPT, DELIVERED, CANCELLED, UNAVAILABLE }
data class SyncQueueEntry(
    val key: String,
    val recipient: String?,
    val kind: String,
    val records: Boolean,
    val status: SyncQueueStatus,
    val tooLarge: Boolean,
    val canCancel: Boolean,
    val problem: String? = null
)
data class SyncSettingsSnapshot(
    val internetRequested: Boolean,
    val entries: List<SyncQueueEntry>,
    val registered: Boolean = false,
    val relayProblem: Boolean = false
)
data class SyncQueueContents(
    val categories: Set<ShareCategory>,
    val note: Boolean,
    val doodle: Boolean
)

/** Queue views/actions are local. Explicit activation alone delegates to the consent-gated runtime. */
class SyncSettingsRepository(
    private val database: RitelaDatabase,
    private val runtime: (() -> RelayRuntime)? = null,
    private val stopped: (suspend () -> Unit)? = null
) {
    val available get() = runtime?.invoke()?.available == true
    suspend fun activate(token: String) {
        requireNotNull(runtime).invoke().activate(token, true)
        runtime.invoke().runOnce()
    }
    private val dao get() = database.syncSettings()
    val snapshots = database.invalidationTracker.createFlow(
        "sync_profile_state",
        "sync_message_intents",
        "sync_outbox",
        "partner_directory",
        "partner_messages",
        "sync_links",
        "relay_config",
        "relay_packets"
    ).map { read() }.flowOn(Dispatchers.IO)

    suspend fun read(): SyncSettingsSnapshot = database.withTransaction {
        val profile = database.syncState().profileStates().singleOrNull()
        val config = database.relay().config()
        val routes = database.relay().routes()
        val names = database.partner().snapshot()?.let { PartnerCodec.directory(it.json) }
            ?.identities?.associate { it.id to it.name }.orEmpty()
        SyncSettingsSnapshot(
            profile?.id == 1 && profile.internetEnabled == 1,
            dao.rows().map { row ->
                val status = when {
                    row.delivered || row.state == "ACKED" -> SyncQueueStatus.DELIVERED

                    row.state == "CANCELLED" -> SyncQueueStatus.CANCELLED

                    row.state in setOf("BLOCKED", "EXPIRED") -> SyncQueueStatus.BLOCKED

                    row.state == "STORED" -> SyncQueueStatus.AWAITING_RECEIPT

                    row.state in setOf(
                        "PENDING_WRAP",
                        "PENDING",
                        "READY",
                        "RETRY_WAIT",
                        "IN_FLIGHT"
                    ) -> SyncQueueStatus.WAITING

                    else -> SyncQueueStatus.UNAVAILABLE
                }
                SyncQueueEntry(
                    key(row),
                    names[row.identity],
                    row.kind,
                    row.records,
                    status,
                    row.error == "MESSAGE_TOO_LARGE",
                    row.kind != "SYNC_CAPS" &&
                        status in setOf(SyncQueueStatus.WAITING, SyncQueueStatus.BLOCKED),
                    if (status in setOf(SyncQueueStatus.WAITING, SyncQueueStatus.BLOCKED)) {
                        row.error ?: config?.error
                            ?: if (config?.registered == true && routes.none {
                                    !it.incoming && it.identity == row.identity &&
                                        it.device == row.device &&
                                        it.expires > System.currentTimeMillis()
                                }
                            ) {
                                "NO_ROUTE"
                            } else {
                                null
                            }
                    } else {
                        null
                    }
                )
            },
            database.relay().config()?.registered == true,
            database.relay().config()?.error != null
        )
    }

    suspend fun disable() {
        database.withTransaction {
            database.syncState().ensureDefaults()
            dao.disableInternet()
            dao.cancelMessages()
            dao.invalidateRecords()
            dao.cancelRecords()
            // STORED is retained: a local operation cannot prove deletion of handed-off bytes.
        }
        stopped?.invoke()
    }

    suspend fun cancel(key: String) = database.withTransaction {
        val row = dao.rows().singleOrNull { key(it) == key } ?: return@withTransaction
        if (row.delivered || row.state in setOf("ACKED", "CANCELLED")) return@withTransaction
        if (!row.records) {
            dao.cancelMessages(row.id)
        } else if (row.state in setOf("READY", "RETRY_WAIT", "IN_FLIGHT") && !row.delivered) {
            dao.pauseAutomatic(row.identity, row.device)
            dao.invalidateRecords(row.identity, row.device)
            dao.cancelRecords(row.identity, row.device)
        }
    }

    /** Only the frozen selection and attachment presence, never medical values or message text. */
    suspend fun contents(key: String): SyncQueueContents? = database.withTransaction {
        val row = dao.rows().singleOrNull { key(it) == key } ?: return@withTransaction null
        val message = database.partner().message(row.id) ?: return@withTransaction null
        if (message.body.isEmpty() ||
            message.body.toByteArray().size > 1024 * 1024
        ) {
            return@withTransaction null
        }
        try {
            val body = JSONObject(message.body)
            val categories = body.optJSONObject("scope")?.let {
                PartnerCodec.scope(it).categories
            }.orEmpty()
            SyncQueueContents(
                categories,
                body.optString("note").isNotEmpty(),
                row.kind == "DOODLE" || body.has("doodle")
            )
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: org.json.JSONException) {
            null
        }
    }

    private fun key(row: SyncSettingsRow) = (if (row.records) "r:" else "m:") + row.id
}
