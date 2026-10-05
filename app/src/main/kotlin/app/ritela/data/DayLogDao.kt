package app.ritela.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DayLogDao {
    @Query("SELECT * FROM day_logs ORDER BY day DESC")
    fun observeAll(): Flow<List<DayLogEntity>>

    @Query("SELECT * FROM day_logs ORDER BY day DESC")
    suspend fun snapshot(): List<DayLogEntity>

    @Upsert
    suspend fun save(log: DayLogEntity)

    @Query("DELETE FROM day_logs WHERE day = :day")
    suspend fun delete(day: Long)
}
