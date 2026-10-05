package app.ritela.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.ritela.domain.Period
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "periods", indices = [Index(value = ["startDay"], unique = true)])
data class PeriodEntity(
    @PrimaryKey val id: String,
    val startDay: Long,
    val endDay: Long?,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toPeriod() = Period(
        id = UUID.fromString(id),
        start = LocalDate.ofEpochDay(startDay),
        end = endDay?.let(LocalDate::ofEpochDay),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )
}
