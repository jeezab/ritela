package app.ritela.data.sync

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "sync_journal_state")
data class SyncJournalStateRow(@PrimaryKey val id: Int = 1, val floor: Long = 0)

@Entity(
    tableName = "sync_link_progress",
    primaryKeys = ["peerIdentity", "peerDevice", "scopeEpoch"]
)
data class SyncLinkProgressRow(
    val peerIdentity: String,
    val peerDevice: String,
    val scopeEpoch: Long,
    val cursor: Long,
    val linkSequence: Long,
    val policyDigest: String,
    val requiresBaseline: Int = 0
)

@Entity(
    tableName = "sync_projection_state",
    primaryKeys = ["peerIdentity", "peerDevice", "scopeEpoch", "entityType", "entityKey"]
)
data class SyncProjectionRow(
    val peerIdentity: String,
    val peerDevice: String,
    val scopeEpoch: Long,
    val entityType: String,
    val entityKey: String,
    val digest: String
)

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["state", "nextAttempt"]),
        Index(value = ["peerIdentity", "peerDevice", "scopeEpoch", "linkSequence"], unique = true)
    ]
)
data class SyncOutboxRow(
    @PrimaryKey val packetId: String,
    val peerIdentity: String,
    val peerDevice: String,
    val scopeEpoch: Long,
    val policyDigest: String,
    val kind: String,
    val fromCursor: Long,
    val throughCursor: Long,
    val linkSequence: Long,
    val envelope: ByteArray,
    val state: String = "READY",
    val attempts: Int = 0,
    val nextAttempt: Long = 0,
    val leaseUntil: Long = 0,
    val leaseToken: String? = null,
    val errorCode: String? = null
)

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seed(row: SyncJournalStateRow)

    @Query("SELECT floor FROM sync_journal_state WHERE id=1")
    suspend fun floor(): Long?

    @Query("UPDATE sync_journal_state SET floor=:value WHERE id=1")
    suspend fun floor(value: Long)

    @Query(
        "SELECT * FROM sync_link_progress WHERE peerIdentity=:peer AND peerDevice=:device AND scopeEpoch=:epoch"
    )
    suspend fun progress(peer: String, device: String, epoch: Long): SyncLinkProgressRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun progress(row: SyncLinkProgressRow)

    @Insert
    suspend fun insert(row: SyncOutboxRow)

    @Query(
        "SELECT * FROM sync_projection_state WHERE peerIdentity=:peer AND peerDevice=:device " +
            "AND scopeEpoch=:epoch AND entityType=:type AND entityKey=:key"
    )
    suspend fun projection(
        peer: String,
        device: String,
        epoch: Long,
        type: String,
        key: String
    ): SyncProjectionRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun projection(row: SyncProjectionRow)

    @Query(
        "DELETE FROM sync_projection_state WHERE peerIdentity=:peer AND peerDevice=:device " +
            "AND scopeEpoch=:epoch AND entityType=:type AND entityKey=:key"
    )
    suspend fun removeProjection(
        peer: String,
        device: String,
        epoch: Long,
        type: String,
        key: String
    )

    @Query(
        "DELETE FROM sync_projection_state WHERE peerIdentity=:peer AND peerDevice=:device AND scopeEpoch=:epoch"
    )
    suspend fun clearProjection(peer: String, device: String, epoch: Long)

    @Query("SELECT * FROM sync_outbox WHERE packetId=:id")
    suspend fun packet(id: String): SyncOutboxRow?

    @Query("SELECT * FROM sync_outbox ORDER BY rowid")
    suspend fun packets(): List<SyncOutboxRow>

    @Query("SELECT * FROM sync_outbox WHERE state IN ('READY','RETRY_WAIT','IN_FLIGHT')")
    suspend fun activePackets(): List<SyncOutboxRow>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE state!='CANCELLED'")
    suspend fun packetCount(): Int

    @Query(
        "SELECT COUNT(*) FROM sync_outbox WHERE peerIdentity=:peer AND peerDevice=:device AND state!='CANCELLED'"
    )
    suspend fun packetCount(peer: String, device: String): Int

    @Query(
        "UPDATE sync_link_progress SET requiresBaseline=1 " +
            "WHERE peerIdentity=:peer AND peerDevice=:device AND scopeEpoch=:epoch"
    )
    suspend fun invalidate(peer: String, device: String, epoch: Long)

    @Query("SELECT COALESCE(SUM(length(envelope)),0) FROM sync_outbox WHERE state!='CANCELLED'")
    suspend fun queuedBytes(): Long

    @Query(
        "SELECT * FROM sync_outbox o WHERE " +
            "((state IN ('READY','RETRY_WAIT') AND nextAttempt<=:now) " +
            "OR (state='IN_FLIGHT' AND leaseUntil<=:now)) AND NOT EXISTS " +
            "(SELECT 1 FROM sync_outbox p WHERE p.peerIdentity=o.peerIdentity " +
            "AND p.peerDevice=o.peerDevice AND p.scopeEpoch=o.scopeEpoch " +
            "AND p.linkSequence<o.linkSequence AND p.state NOT IN ('STORED','CANCELLED')) " +
            "ORDER BY o.rowid LIMIT 64"
    )
    suspend fun candidates(now: Long): List<SyncOutboxRow>

    @Query(
        "UPDATE sync_outbox SET state='IN_FLIGHT',attempts=attempts+1," +
            "leaseToken=:token,leaseUntil=:until WHERE packetId=:id"
    )
    suspend fun lease(id: String, token: String, until: Long)

    @Query(
        "UPDATE sync_outbox SET state=:state,leaseToken=NULL,leaseUntil=0," +
            "nextAttempt=:next,errorCode=:error WHERE packetId=:id AND leaseToken=:token " +
            "AND state='IN_FLIGHT'"
    )
    suspend fun finish(id: String, token: String, state: String, next: Long, error: String?): Int

    @Query(
        "UPDATE sync_outbox SET state='CANCELLED',envelope=X'',leaseToken=NULL,leaseUntil=0 WHERE packetId=:id"
    )
    suspend fun cancel(id: String)

    @Query("SELECT MIN(cursor) FROM sync_link_progress")
    suspend fun minimumCursor(): Long?

    @Query("SELECT MIN(fromCursor) FROM sync_outbox WHERE state NOT IN ('STORED','CANCELLED')")
    suspend fun pendingFloor(): Long?

    @Query(
        "DELETE FROM sync_changes WHERE sequence<=:through AND sequence NOT IN " +
            "(SELECT MAX(sequence) FROM sync_changes GROUP BY entityType,entityKey)"
    )
    suspend fun compact(through: Long): Int
}
