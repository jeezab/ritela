package app.ritela.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** A seven-day visual guide around each central estimate, never the wide possible range.
 * Sparse/variable history changes uncertainty in details, not the width of this guide. */
fun compactFertilityWindows(periods: List<Period>, analysis: CycleAnalysis): List<FertileWindow> =
    fertilityWindows(periods, analysis)

/** Recorded bleeding > expected bleeding > central ovulation > compact window > ordinary day.
 * Journal events never participate in, or disappear because of, this priority. */
fun calendarDisplayDay(
    periods: List<Period>,
    analysis: CycleAnalysis,
    date: LocalDate,
    today: LocalDate,
    windows: List<FertileWindow> = compactFertilityWindows(periods, analysis)
): CalendarDayInfo {
    val calculated = calendarDay(periods, analysis, date, today)
    if (calculated.period != null || calculated.forecast != null) return calculated
    val first = periods.minOfOrNull { it.start }
    if (first == null || date < first) return CalendarDayInfo(CalendarDayKind.NONE)
    return CalendarDayInfo(
        when {
            windows.any { date == it.ovulation.centralDate } -> CalendarDayKind.OVULATION_ESTIMATE
            windows.any { date in it.likely } -> CalendarDayKind.FERTILE_LIKELY
            else -> CalendarDayKind.NONE
        }
    )
}

data class CalendarSelection(
    val date: LocalDate,
    val day: CalendarDayInfo,
    val cycleDay: Long?,
    val possibleWindow: FertileWindow?,
    val mucusSignal: Boolean
)

fun calendarSelection(
    periods: List<Period>,
    analysis: CycleAnalysis,
    date: LocalDate,
    today: LocalDate
): CalendarSelection {
    val day = calendarDisplayDay(periods, analysis, date, today)
    val start = (periods.map { it.start } + analysis.forecasts.map { it.predictedStartDate })
        .filter { it <= date }.maxOrNull()
    val bleeding = day.period != null || day.forecast != null
    val window = if (bleeding || start == null) {
        null
    } else {
        fertilityWindows(periods, analysis)
            .filter { date in it.possible }
            .minByOrNull { abs(ChronoUnit.DAYS.between(date, it.ovulation.centralDate)) }
    }
    return CalendarSelection(
        date,
        day,
        start?.let { ChronoUnit.DAYS.between(it, date) + 1 },
        window,
        analysis.mucusObservations[date] in
            setOf(CervicalMucus.WATERY, CervicalMucus.CLEAR_STRETCHY)
    )
}
