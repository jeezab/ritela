package app.ritela.data

import app.ritela.domain.DayLog
import app.ritela.domain.PeriodProblem
import app.ritela.domain.validateDayLog
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.map

class DayLogRepository(
    private val dao: DayLogDao,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    val logs = dao.observeAll().map { rows -> rows.map(DayLogEntity::toLog) }

    suspend fun save(log: DayLog): PeriodProblem? {
        if (log.date > LocalDate.now(clock)) return PeriodProblem.FUTURE_DATE
        if (!validateDayLog(log, LocalDate.now(clock))) return PeriodProblem.STORAGE
        if (log.empty) dao.delete(log.date.toEpochDay()) else dao.save(DayLogEntity.from(log))
        return null
    }
}
