package app.ritela.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Period(
    val id: UUID,
    val start: LocalDate,
    val end: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant
)

enum class PeriodProblem { FUTURE_DATE, END_BEFORE_START, OVERLAP, STORAGE }

fun validatePeriod(start: LocalDate, end: LocalDate?, today: LocalDate): PeriodProblem? = when {
    start > today || (end != null && end > today) -> PeriodProblem.FUTURE_DATE
    end != null && end < start -> PeriodProblem.END_BEFORE_START
    else -> null
}

/** An open record reserves all following days until it is closed. */
fun periodConflict(
    periods: List<Period>,
    start: LocalDate,
    end: LocalDate?,
    excluding: UUID? = null
): Period? = periods.firstOrNull {
    it.id != excluding && start <= (it.end ?: LocalDate.MAX) &&
        (end ?: LocalDate.MAX) >= it.start
}
