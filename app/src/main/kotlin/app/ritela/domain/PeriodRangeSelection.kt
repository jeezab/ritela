package app.ritela.domain

import java.time.LocalDate

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

    fun canPick(periods: List<Period>, day: LocalDate, today: LocalDate): Boolean = day <= today &&
        periodConflict(periods, minOf(anchor ?: day, day), maxOf(anchor ?: day, day)) == null

    fun canSave(periods: List<Period>, today: LocalDate): Boolean =
        validatePeriod(start, end, today) == null && periodConflict(periods, start, end) == null

    fun canKeepOngoing(periods: List<Period>, today: LocalDate): Boolean =
        validatePeriod(start, null, today) == null && periodConflict(periods, start, null) == null
}
