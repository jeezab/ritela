package app.ritela.data

import app.ritela.domain.PeriodProblem
import app.ritela.domain.validatePeriod
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.map

class PeriodRepository(
    private val dao: PeriodDao,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    val periods = dao.observeRecent().map { rows -> rows.map(PeriodEntity::toPeriod) }

    suspend fun add(start: LocalDate, end: LocalDate?): PeriodProblem? {
        validatePeriod(start, end, LocalDate.now(clock))?.let { return it }
        val now = clock.millis()
        val record =
            PeriodEntity(
                UUID.randomUUID().toString(),
                start.toEpochDay(),
                end?.toEpochDay(),
                now,
                now
            )
        return if (dao.addIfSeparate(record)) null else PeriodProblem.OVERLAP
    }

    suspend fun finish(id: UUID, end: LocalDate): PeriodProblem? {
        val existing = dao.find(id.toString()) ?: return PeriodProblem.STORAGE
        validatePeriod(LocalDate.ofEpochDay(existing.startDay), end, LocalDate.now(clock))?.let {
            return it
        }
        return if (dao.finish(id.toString(), end.toEpochDay(), clock.millis()) ==
            1
        ) {
            null
        } else {
            PeriodProblem.STORAGE
        }
    }

    suspend fun edit(id: UUID, start: LocalDate, end: LocalDate?): PeriodProblem? {
        validatePeriod(start, end, LocalDate.now(clock))?.let { return it }
        return dao.editIfSeparate(
            id.toString(),
            start.toEpochDay(),
            end?.toEpochDay(),
            clock.millis()
        )
    }

    suspend fun delete(id: UUID): PeriodProblem? =
        if (dao.delete(id.toString()) == 1) null else PeriodProblem.STORAGE
}
