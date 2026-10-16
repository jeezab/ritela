package app.ritela.data.sync

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "relay_config")
data class RelayConfigRow(
    @PrimaryKey val id: Int = 1,
    val origin: String,
    val activation: ByteArray?,
    val registered: Boolean = false,
    val cursor: Long = 0,
    val nextAttempt: Long = 0,
    val attempts: Int = 0,
    val error: String? = null,
    val lastCheck: Long = 0
)

@Entity(tableName = "relay_routes")
data class RelayRouteRow(
    @PrimaryKey val route: String,
    val identity: String,
    val device: String,
    val incoming: Boolean,
    val expires: Long,
    val context: String,
    val credentials: ByteArray
)

@Entity(tableName = "relay_packets")
data class RelayPacketRow(
    @PrimaryKey val id: String,
    val identity: String,
    val device: String,
    val route: String,
    val expires: Long,
    val generation: Long,
    val grant: String?,
    val envelope: ByteArray,
    val sourceDigest: String,
    val state: String = "PENDING",
    val nextAttempt: Long = 0
)

@Entity(tableName = "relay_received")
data class RelayReceivedRow(
    @PrimaryKey val id: String,
    val identity: String,
    val device: String,
    val digest: String,
    val manualDigest: String? = null,
    val hpkeDigest: String? = null
)

@Entity(tableName = "relay_peers", primaryKeys = ["identity", "device"])
data class RelayPeerRow(val identity: String, val device: String, val certificate: String)

@Dao
interface RelayDao {
    @Query("SELECT * FROM relay_peers WHERE identity=:identity AND device=:device")
    suspend fun peer(identity: String, device: String): RelayPeerRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun peer(row: RelayPeerRow)

    @Query("SELECT * FROM relay_config WHERE id=1")
    suspend fun config(): RelayConfigRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun config(row: RelayConfigRow)

    @Query("SELECT * FROM relay_routes ORDER BY rowid")
    suspend fun routes(): List<RelayRouteRow>

    @Query("SELECT * FROM relay_routes WHERE route=:route")
    suspend fun route(route: String): RelayRouteRow?

    @Insert suspend fun route(row: RelayRouteRow)

    @Query("UPDATE relay_routes SET credentials=:credentials WHERE route=:route")
    suspend fun routeCredentials(route: String, credentials: ByteArray)

    @Query("SELECT * FROM relay_packets WHERE id=:id")
    suspend fun packet(id: String): RelayPacketRow?

    @Query(
        "SELECT r.* FROM relay_packets r LEFT JOIN partner_messages m ON m.id=r.id " +
            "WHERE r.state IN ('PENDING','STORED') ORDER BY CASE WHEN m.delivered=1 THEN 0 " +
            "WHEN r.state='PENDING' THEN 1 ELSE 2 END,r.expires,r.rowid LIMIT 512"
    )
    suspend fun active(): List<RelayPacketRow>

    @Query("SELECT COALESCE(SUM(length(envelope)),0) FROM relay_packets")
    suspend fun bytes(): Long

    @Query("SELECT COUNT(*) FROM relay_packets")
    suspend fun count(): Int

    @Insert suspend fun packet(row: RelayPacketRow)

    @Query("UPDATE relay_packets SET state=:state,nextAttempt=:next WHERE id=:id")
    suspend fun state(id: String, state: String, next: Long = 0)

    @Query("UPDATE relay_packets SET state=:state,envelope=X'' WHERE id=:id")
    suspend fun retire(id: String, state: String)

    @Query("SELECT * FROM relay_received WHERE id=:id")
    suspend fun received(id: String): RelayReceivedRow?

    @Query("SELECT COUNT(*) FROM relay_received")
    suspend fun receivedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun received(row: RelayReceivedRow)

    @Query("UPDATE sync_profile_state SET internetEnabled=:enabled WHERE id=1")
    suspend fun consent(enabled: Int)

    @Query(
        "UPDATE sync_outbox SET state='STORED',leaseToken=NULL," +
            "leaseUntil=0 WHERE packetId=:id AND state!='CANCELLED'"
    )
    suspend fun stored(id: String)

    @Query(
        "SELECT packetId AS id FROM sync_outbox WHERE state IN ('READY','RETRY_WAIT') " +
            "AND packetId NOT IN (SELECT id FROM relay_packets WHERE state!='PENDING') UNION " +
            "SELECT messageId FROM sync_message_intents WHERE state='PENDING_WRAP' " +
            "AND messageId NOT IN (SELECT id FROM relay_packets WHERE state!='PENDING') " +
            "UNION SELECT id FROM relay_packets WHERE state='PENDING' ORDER BY 1 LIMIT 512"
    )
    suspend fun pendingIds(): List<String>
}
