package app.ritela.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class SyncStateDao {
    @Query("SELECT * FROM sync_profile_state ORDER BY id")
    abstract suspend fun profileStates(): List<SyncProfileStateRow>

    @Query("SELECT * FROM sync_authority ORDER BY id")
    abstract suspend fun authorities(): List<SyncAuthorityRow>

    @Query("SELECT * FROM sync_links ORDER BY peerIdentity, peerDevice")
    abstract suspend fun links(): List<SyncLinkRow>

    @Query("SELECT * FROM sync_record_versions ORDER BY ownerIdentity, entityType, entityKey")
    abstract suspend fun versions(): List<SyncRecordVersionRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun seedProfile(row: SyncProfileStateRow)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun seedAuthority(row: SyncAuthorityRow)

    /** No replace/upsert: opening a profile cannot reset saved consent or author metadata. */
    @Transaction
    open suspend fun ensureDefaults() {
        seedProfile(SyncProfileStateRow())
        seedAuthority(SyncAuthorityRow())
    }
}
