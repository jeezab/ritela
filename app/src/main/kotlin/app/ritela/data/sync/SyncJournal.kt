package app.ritela.data.sync

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import app.ritela.data.DayLogEntity
import app.ritela.data.JournalCodec
import app.ritela.data.PeriodEntity
import app.ritela.domain.journalSelections
import java.util.UUID
import org.json.JSONObject

/** Local sequence is not an authenticated author generation or a wire revision. */
@Entity(
    tableName = "sync_changes",
    indices = [
        Index(
            value = ["changeId"],
            unique = true
        ), Index(value = ["entityType", "entityKey", "sequence"])
    ]
)
data class SyncChangeRow(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val changeId: String = UUID.randomUUID().toString(),
    val entityType: String,
    val entityKey: String,
    val deleted: Boolean,
    val payload: String
)

/** Inherited by each writing DAO so its mutation and journal entry share one Room transaction. */
abstract class ChangeRecorder {
    @Insert
    protected abstract suspend fun insertChange(row: SyncChangeRow): Long

    @Query(
        "SELECT * FROM sync_changes WHERE entityType=:type AND entityKey=:key " +
            "ORDER BY sequence DESC LIMIT 1"
    )
    protected abstract suspend fun lastChange(type: String, key: String): SyncChangeRow?

    @Query(
        "DELETE FROM sync_changes WHERE sequence NOT IN " +
            "(SELECT MAX(sequence) FROM sync_changes GROUP BY entityType,entityKey) " +
            "AND NOT EXISTS(SELECT 1 FROM sync_link_progress) " +
            "AND NOT EXISTS(SELECT 1 FROM sync_outbox WHERE state!='CANCELLED')"
    )
    protected abstract suspend fun trimUnclaimedHistory()

    @Query("INSERT OR IGNORE INTO sync_journal_state (id,floor) VALUES (1,0)")
    protected abstract suspend fun seedJournalFloor()

    @Query(
        "UPDATE sync_journal_state SET floor=MAX(floor,:through) WHERE id=1 " +
            "AND NOT EXISTS(SELECT 1 FROM sync_link_progress) " +
            "AND NOT EXISTS(SELECT 1 FROM sync_outbox WHERE state!='CANCELLED')"
    )
    protected abstract suspend fun advanceUnclaimedFloor(through: Long)

    protected suspend fun recordChange(
        type: String,
        key: String,
        payload: String,
        deleted: Boolean = false
    ) {
        val previous = lastChange(type, key)
        if (previous?.payload == payload && previous.deleted == deleted) return
        val sequence = insertChange(
            SyncChangeRow(entityType = type, entityKey = key, deleted = deleted, payload = payload)
        )
        // Before any recipient checkpoint exists, only latest values/tombstones are needed.
        if (sequence % 256L == 0L) {
            trimUnclaimedHistory()
            seedJournalFloor()
            advanceUnclaimedFloor(sequence)
        }
    }
}

@Dao
interface SyncJournalDao {
    @Insert
    suspend fun insert(row: SyncChangeRow): Long

    @Query("SELECT COALESCE(MAX(sequence),0) FROM sync_changes")
    suspend fun watermark(): Long

    @Query(
        "SELECT * FROM sync_changes WHERE sequence>:after AND sequence<=:through ORDER BY sequence"
    )
    suspend fun changes(after: Long, through: Long): List<SyncChangeRow>

    @Query(
        "SELECT * FROM sync_changes WHERE sequence IN " +
            "(SELECT MAX(sequence) FROM sync_changes GROUP BY entityType,entityKey) ORDER BY sequence"
    )
    suspend fun latest(): List<SyncChangeRow>
}

object ChangePayload {
    fun period(value: PeriodEntity): String = JSONObject().put("id", value.id)
        .put("start", value.startDay).put("end", value.endDay ?: JSONObject.NULL)
        .put("updated", value.updatedAt).toString()

    fun day(value: DayLogEntity): String = JSONObject().put("day", value.day)
        .put(
            "sections",
            JSONObject(JournalCodec.encodeSelections(value.toLog().journalSelections()))
        )
        .put("note", value.note).toString()
}
