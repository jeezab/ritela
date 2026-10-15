package app.ritela.data.sync

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sync_doodle_receipts")
data class SyncDoodleReceiptRow(@PrimaryKey val id: String)

@Entity(tableName = "sync_handover")
data class SyncHandoverRow(@PrimaryKey val id: Int = 1, val proposal: String, val grant: String)

@Entity(tableName = "sync_capabilities", primaryKeys = ["identity", "device"])
data class SyncCapabilityRow(
    val identity: String,
    val device: String,
    val context: String,
    val offered: Boolean,
    val accepted: Boolean
)

@Entity(tableName = "sync_received_author")
data class SyncReceivedAuthorRow(
    @PrimaryKey val identity: String,
    val device: String,
    val generation: Long,
    val digest: String,
    val grant: String
)

@Entity(tableName = "sync_received_state")
data class SyncReceivedStateRow(
    @PrimaryKey val identity: String,
    val generation: Long,
    val epoch: Long,
    val sequence: Long,
    val scope: String,
    val snapshot: String,
    val received: Long
)

@Entity(tableName = "sync_received_records", primaryKeys = ["identity", "type", "key"])
data class SyncReceivedRecordRow(
    val identity: String,
    val type: String,
    val key: String,
    val revision: Long,
    val value: String?
)

@Entity(
    tableName = "sync_inbox",
    indices = [
        Index(value = ["identity", "generation", "epoch", "sequence"], unique = true),
        Index(value = ["identity", "applied", "sequence"])
    ]
)
data class SyncInboxRow(
    @PrimaryKey val id: String,
    val identity: String,
    val device: String,
    val digest: String,
    val generation: Long,
    val epoch: Long,
    val sequence: Long,
    val payload: String,
    val received: Long,
    // 0 pending, 1 applied, 2 deliberately discarded. Discard is never an application ACK.
    val applied: Int = 0
)

@Dao
interface SyncReceiveDao {
    @Query("SELECT * FROM sync_handover WHERE id=1")
    suspend fun handover(): SyncHandoverRow?

    @Insert
    suspend fun handover(row: SyncHandoverRow)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun doodle(row: SyncDoodleReceiptRow)

    @Query("SELECT COUNT(*) FROM sync_doodle_receipts WHERE id=:id")
    suspend fun hasDoodle(id: String): Int

    @Query(
        "SELECT id FROM sync_inbox WHERE identity=:identity AND device=:device AND applied=1 " +
            "ORDER BY generation DESC,epoch DESC,sequence DESC LIMIT 128"
    )
    suspend fun appliedIds(identity: String, device: String): List<String>

    @Query("SELECT * FROM sync_capabilities WHERE identity=:identity AND device=:device")
    suspend fun capability(identity: String, device: String): SyncCapabilityRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun capability(row: SyncCapabilityRow)

    @Query("SELECT * FROM sync_received_author WHERE identity=:identity")
    suspend fun author(identity: String): SyncReceivedAuthorRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun author(row: SyncReceivedAuthorRow)

    @Query("SELECT * FROM sync_received_state WHERE identity=:identity")
    suspend fun state(identity: String): SyncReceivedStateRow?

    @Query("SELECT * FROM sync_received_state")
    fun states(): Flow<List<SyncReceivedStateRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun state(row: SyncReceivedStateRow)

    @Query("SELECT * FROM sync_received_records WHERE identity=:identity")
    suspend fun records(identity: String): List<SyncReceivedRecordRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(row: SyncReceivedRecordRow)

    @Query("DELETE FROM sync_received_records WHERE identity=:identity")
    suspend fun reset(identity: String)

    @Query("UPDATE sync_received_records SET value=NULL WHERE identity=:identity")
    suspend fun erase(identity: String)

    @Query("SELECT * FROM sync_inbox WHERE id=:id")
    suspend fun packet(id: String): SyncInboxRow?

    @Query("SELECT COUNT(*) FROM sync_inbox")
    suspend fun count(): Int

    @Query(
        "SELECT * FROM sync_inbox WHERE identity=:identity AND generation=:generation " +
            "AND epoch=:epoch AND sequence=:sequence LIMIT 1"
    )
    suspend fun sequence(
        identity: String,
        generation: Long,
        epoch: Long,
        sequence: Long
    ): SyncInboxRow?

    @Insert
    suspend fun insert(row: SyncInboxRow)

    @Query("SELECT * FROM sync_inbox WHERE identity=:identity AND applied=0 ORDER BY sequence")
    suspend fun pending(identity: String): List<SyncInboxRow>

    @Query("SELECT COALESCE(SUM(length(CAST(payload AS BLOB))),0) FROM sync_inbox WHERE applied=0")
    suspend fun pendingBytes(): Long

    @Query("UPDATE sync_inbox SET applied=1,payload='' WHERE id=:id")
    suspend fun applied(id: String)

    @Query("UPDATE sync_inbox SET applied=2,payload='' WHERE identity=:identity AND applied=0")
    suspend fun discardPending(identity: String)
}
