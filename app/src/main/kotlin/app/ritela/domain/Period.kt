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

/** Dates awaiting a single explicit save; these are not stored records. */
data class PeriodRange(val start: LocalDate, val end: LocalDate?)

fun validatePeriod(start: LocalDate, end: LocalDate?, today: LocalDate): PeriodProblem? = when {
    start > today -> PeriodProblem.FUTURE_DATE
    end != null && end < start -> PeriodProblem.END_BEFORE_START
    else -> null
}

/** Inclusive end of a saved period. A later record keeps this one from running into it. */
fun filledPeriodEnd(
    start: LocalDate,
    durationDays: Int,
    periods: List<Period> = emptyList(),
    excluding: UUID? = null
): LocalDate {
    val proposed = start.plusDays((durationDays.coerceAtLeast(1) - 1).toLong())
    val limit = periods.asSequence()
        .filter { it.id != excluding && it.start > start }
        .minOfOrNull { it.start.minusDays(1) }
    val end = if (limit != null && proposed > limit) limit else proposed
    return if (end < start) start else end
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
