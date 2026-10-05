package app.ritela.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PeriodDao {
    @Query("SELECT * FROM periods ORDER BY startDay DESC LIMIT 30")
    abstract fun observeRecent(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM periods WHERE id = :id")
    abstract suspend fun find(id: String): PeriodEntity?

    @Query(
        "UPDATE periods SET endDay = :endDay, updatedAt = :updatedAt WHERE id = :id AND endDay IS NULL"
    )
    abstract suspend fun finish(id: String, endDay: Long, updatedAt: Long): Int

    @Query(
        "SELECT COUNT(*) FROM periods WHERE startDay <= :endDay AND (endDay IS NULL OR endDay >= :startDay)"
    )
    protected abstract suspend fun overlaps(startDay: Long, endDay: Long): Int

    @Insert
    protected abstract suspend fun insert(entity: PeriodEntity)

    @Transaction
    open suspend fun addIfSeparate(entity: PeriodEntity): Boolean {
        if (overlaps(entity.startDay, entity.endDay ?: Long.MAX_VALUE) != 0) return false
        insert(entity)
        return true
    }
}
