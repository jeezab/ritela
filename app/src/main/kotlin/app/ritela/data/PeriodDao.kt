package app.ritela.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.ritela.domain.PeriodProblem
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PeriodDao {
    @Query("SELECT * FROM periods ORDER BY startDay DESC")
    abstract suspend fun snapshot(): List<PeriodEntity>

    @Query("SELECT * FROM periods ORDER BY startDay DESC")
    abstract fun observeAll(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM periods WHERE id = :id")
    abstract suspend fun find(id: String): PeriodEntity?

    @Query(
        "UPDATE periods SET endDay = :endDay, updatedAt = :updatedAt WHERE id = :id AND endDay IS NULL AND startDay <= :endDay"
    )
    abstract suspend fun finish(id: String, endDay: Long, updatedAt: Long): Int

    @Query("DELETE FROM periods WHERE id = :id")
    abstract suspend fun delete(id: String): Int

    @Query(
        "UPDATE periods SET startDay = :startDay, endDay = :endDay, updatedAt = :updatedAt WHERE id = :id"
    )
    protected abstract suspend fun updateDates(
        id: String,
        startDay: Long,
        endDay: Long?,
        updatedAt: Long
    )

    @Query(
        "SELECT COUNT(*) FROM periods WHERE id != :id AND startDay <= :endDay AND (endDay IS NULL OR endDay >= :startDay)"
    )
    protected abstract suspend fun overlapsExcept(id: String, startDay: Long, endDay: Long): Int

    @Transaction
    open suspend fun editIfSeparate(
        id: String,
        startDay: Long,
        endDay: Long?,
        updatedAt: Long
    ): PeriodProblem? {
        if (find(id) == null) return PeriodProblem.STORAGE
        if (overlapsExcept(id, startDay, endDay ?: Long.MAX_VALUE) !=
            0
        ) {
            return PeriodProblem.OVERLAP
        }
        updateDates(id, startDay, endDay, updatedAt)
        return null
    }

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

    @Transaction
    open suspend fun addAllIfSeparate(entities: List<PeriodEntity>): Boolean {
        val ordered = entities.sortedBy { it.startDay }
        if (ordered.zipWithNext().any { (first, second) ->
                (first.endDay ?: Long.MAX_VALUE) >= second.startDay
            }
        ) {
            return false
        }
        if (ordered.any { overlaps(it.startDay, it.endDay ?: Long.MAX_VALUE) != 0 }) return false
        ordered.forEach { insert(it) }
        return true
    }
}
