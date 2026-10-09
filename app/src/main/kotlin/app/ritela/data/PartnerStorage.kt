package app.ritela.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Entity(tableName = "partner_directory")
data class PartnerDirectoryRow(@PrimaryKey val id: Int = 1, val json: String)

@Entity(tableName = "partner_messages")
data class PartnerMessage(
    @PrimaryKey val id: String,
    val identity: String,
    val device: String,
    val outgoing: Boolean,
    val kind: String,
    val created: Long,
    val received: Long,
    val body: String,
    val envelope: String,
    val delivered: Boolean = false,
    val opened: Boolean = false
)

@Dao
interface PartnerDao {
    @Query("SELECT * FROM partner_directory WHERE id=1")
    fun directory(): Flow<PartnerDirectoryRow?>

    @Query("SELECT * FROM partner_directory WHERE id=1")
    suspend fun snapshot(): PartnerDirectoryRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(row: PartnerDirectoryRow)

    @Query("SELECT * FROM partner_messages ORDER BY received DESC")
    fun messages(): Flow<List<PartnerMessage>>

    @Query("SELECT * FROM partner_messages WHERE id=:id")
    suspend fun message(id: String): PartnerMessage?

    @Query("SELECT * FROM partner_messages WHERE identity=:identity")
    suspend fun history(identity: String): List<PartnerMessage>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(message: PartnerMessage)

    @Query("UPDATE partner_messages SET delivered=1 WHERE id=:id AND outgoing=1")
    suspend fun delivered(id: String)

    @Query("UPDATE partner_messages SET opened=1 WHERE id=:id")
    suspend fun opened(id: String)

    // Retain IDs as replay tombstones when the user clears locally received contents.
    @Query(
        "UPDATE partner_messages SET body='', envelope='' WHERE identity=:identity AND outgoing=0"
    )
    suspend fun clearReceived(identity: String)
}

data class PartnerMode(
    val show: Boolean = false,
    val recipient: Boolean = false,
    val ready: Boolean = false,
    val failed: Boolean = false
)

class PartnerPreferences(context: Context, profile: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile("partner-$profile")
    }
    val mode = store.data.map { PartnerMode(it[SHOW] ?: false, it[RECIPIENT] ?: false, true) }
        .catch { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            emit(PartnerMode(failed = true))
        }.stateIn(scope, SharingStarted.Eagerly, PartnerMode())
    suspend fun show(value: Boolean) {
        store.edit { it[SHOW] = value }
    }
    suspend fun recipient(value: Boolean) {
        store.edit { it[RECIPIENT] = value }
    }
    suspend fun close() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.let {
            it.cancel()
            it.join()
        }
    }
    companion object {
        private val SHOW = booleanPreferencesKey("show")
        private val RECIPIENT = booleanPreferencesKey("recipient")
    }
}
