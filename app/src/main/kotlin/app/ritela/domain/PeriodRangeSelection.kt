package app.ritela.domain

import java.time.LocalDate

/** Days after today that can still be chosen as the end of a period that has already started. */
const val PERIOD_END_HORIZON_DAYS = 14L

fun latestSelectablePeriodEnd(today: LocalDate): LocalDate = today.plusDays(PERIOD_END_HORIZON_DAYS)

/** A first tap is a one-day closed range; the second tap completes it in either direction. */
data class PeriodRangeSelection(
    val start: LocalDate,
    val end: LocalDate? = start,
    val anchor: LocalDate? = null
) {
    fun pick(day: LocalDate): PeriodRangeSelection = if (anchor == null) {
        PeriodRangeSelection(day, day, day)
    } else {
        PeriodRangeSelection(minOf(anchor, day), maxOf(anchor, day))
    }

    fun canPick(periods: List<Period>, day: LocalDate, today: LocalDate): Boolean {
        val start = minOf(anchor ?: day, day)
        val end = maxOf(anchor ?: day, day)
        val futureEnd = anchor != null && anchor <= today && day > today &&
            day <= latestSelectablePeriodEnd(today)
        return (day <= today || futureEnd) && start <= today &&
            periodConflict(periods, start, end) == null
    }

    fun canSave(periods: List<Period>, today: LocalDate): Boolean =
        validatePeriod(start, end, today) == null && periodConflict(periods, start, end) == null

    fun canKeepOngoing(periods: List<Period>, today: LocalDate): Boolean =
        validatePeriod(start, null, today) == null && periodConflict(periods, start, null) == null
}
