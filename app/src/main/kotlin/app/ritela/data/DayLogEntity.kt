package app.ritela.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.ritela.domain.DayLog
import app.ritela.domain.Energy
import app.ritela.domain.FlowLevel
import app.ritela.domain.JournalIcon
import app.ritela.domain.Mood
import app.ritela.domain.Pain
import app.ritela.domain.Sex
import java.time.LocalDate

@Entity(tableName = "day_logs")
data class DayLogEntity(
    @PrimaryKey val day: Long,
    val headache: String?,
    val cramps: String?,
    val backache: String?,
    val flow: String?,
    val mood: String?,
    val energy: String?,
    val sex: String,
    val note: String,
    @ColumnInfo(defaultValue = "'{}'") val custom: String = "{}",
    val calendarIcon: String? = null
) {
    fun toLog() = DayLog(
        LocalDate.ofEpochDay(day),
        headache?.let(Pain::valueOf),
        cramps?.let(Pain::valueOf),
        backache?.let(Pain::valueOf),
        flow?.let(FlowLevel::valueOf),
        mood?.let(Mood::valueOf),
        energy?.let(Energy::valueOf),
        sex.split(',').filter(String::isNotEmpty).map(Sex::valueOf).toSet(),
        note,
        JournalCodec.decodeSelections(custom),
        calendarIcon?.let(JournalIcon::valueOf)
    )

    companion object {
        fun from(log: DayLog) = DayLogEntity(
            log.date.toEpochDay(), log.headache?.name, log.cramps?.name, log.backache?.name,
            log.flow?.name, log.mood?.name, log.energy?.name,
            log.sex.sortedBy { it.name }.joinToString(",") { it.name }, log.note,
            JournalCodec.encodeSelections(log.custom), log.calendarIcon?.name
        )
    }
}
