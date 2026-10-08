package app.ritela.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

/** A compact visual guide, distinct from the full possible fertility range. */
fun compactFertilityWindows(periods: List<Period>, analysis: CycleAnalysis): List<FertileWindow> {
    if (analysis.unavailable == ForecastUnavailable.INVALID_HISTORY) return emptyList()
    val starts = periods.map { it.start }.distinct().sorted()
    val historical = starts.drop(1).mapNotNull { nextStart ->
        val intervals = recentIntervals(starts.filter { it <= nextStart }, nextStart)
        if (intervals.size < 6) return@mapNotNull null
        val center = weightedMedian(intervals)
        val deviations = intervals.map { abs(it - center) }
        val mad = weightedMedian(deviations)
        if (mad > 3 || deviations.any { it > max(7.0, 3 * mad) }) return@mapNotNull null
        estimateFertileWindow(
            OvulationEstimate(
                nextStart.minusDays(14),
                nextStart.minusDays(16),
                nextStart.minusDays(10)
            )
        )
    }
    return historical + analysis.forecasts.filter {
        it.cyclesUsed >= 6 && it.confidence != HistoryConfidence.LOW &&
            it.horizon <= 3 && it.cycleVariation <= 3 &&
            ChronoUnit.DAYS.between(it.lowerBound, it.upperBound) <= 10
    }.map { estimateFertileWindow(estimateOvulation(it)) }
}

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
