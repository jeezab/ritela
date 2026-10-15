package app.ritela.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.ritela.data.sync.ChangePayload
import app.ritela.data.sync.ChangeRecorder
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DayLogDao : ChangeRecorder() {
    @Query("SELECT * FROM day_logs ORDER BY day DESC")
    abstract fun observeAll(): Flow<List<DayLogEntity>>

    @Query("SELECT * FROM day_logs ORDER BY day DESC")
    abstract suspend fun snapshot(): List<DayLogEntity>

    @Query("SELECT * FROM day_logs WHERE day=:day")
    abstract suspend fun find(day: Long): DayLogEntity?

    @Upsert
    protected abstract suspend fun saveRow(log: DayLogEntity)

    @Transaction
    open suspend fun save(log: DayLogEntity) {
        if (find(log.day) == log) return
        saveRow(log)
        recordChange("DAY", log.day.toString(), ChangePayload.day(log))
    }

    @Query("DELETE FROM day_logs WHERE day = :day")
    protected abstract suspend fun deleteRow(day: Long): Int

    @Transaction
    open suspend fun delete(day: Long) {
        if (deleteRow(day) == 1) recordChange("DAY", day.toString(), "", deleted = true)
    }
}
