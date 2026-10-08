package app.ritela.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

enum class HistoryConfidence { LOW, MEDIUM, HIGH }
enum class ForecastUnavailable { NEED_MORE, INVALID_HISTORY, ONGOING, PAST_DUE }

data class CycleForecast(
    val predictedStartDate: LocalDate,
    val lowerBound: LocalDate,
    val upperBound: LocalDate,
    val horizon: Int,
    val confidence: HistoryConfidence,
    val cyclesUsed: Int,
    val cycleMedian: Int,
    val cycleVariation: Double
)

data class PredictionDefaults(val cycleLength: Int = 28, val periodDuration: Int = 5)

data class CycleAnalysis(
    val cycleDay: Long? = null,
    val forecasts: List<CycleForecast> = emptyList(),
    val unavailable: ForecastUnavailable? = ForecastUnavailable.NEED_MORE,
    val usesDefaults: Boolean = false,
    val periodDuration: Int = 5,
    val measuredPeriodDuration: Int? = null,
    val backtest: BacktestResult = BacktestResult(),
    val mucusObservations: Map<LocalDate, CervicalMucus> = emptyMap()
)

/** Engineering estimate of period starts. Confidence is a history-quality label, not a probability. */
fun analyzeCycles(
    periods: List<Period>,
    today: LocalDate,
    defaults: PredictionDefaults = PredictionDefaults(),
    dayLogs: List<DayLog> = emptyList()
): CycleAnalysis {
    val observations = dayLogs.filter { it.date <= today }.mapNotNull { log ->
        cervicalMucus(log)?.let { log.date to it }
    }.toMap()
    if (periods.isEmpty()) {
        return CycleAnalysis(
            periodDuration = defaults.periodDuration,
            mucusObservations = observations
        )
    }
    val ordered = periods.sortedBy { it.start }
    val invalid = ordered.any { validatePeriod(it.start, it.end, today) != null } ||
        ordered.map { it.id }.distinct().size != ordered.size ||
        ordered.zipWithNext().any { (a, b) -> a.end == null || a.end >= b.start }
    if (invalid) return CycleAnalysis(unavailable = ForecastUnavailable.INVALID_HISTORY)
    val latest = ordered.last()
    val day = ChronoUnit.DAYS.between(latest.start, today) + 1
    val starts = ordered.map { it.start }
    val usable = recentIntervals(starts, today)
    val durations = ordered.filter { it.end != null && it.start >= today.minusYears(1) }
        .takeLast(12).map { ChronoUnit.DAYS.between(it.start, it.end) + 1.0 }
    val duration = personalizedLength(durations, defaults.periodDuration, 3)
    val length = personalizedLength(usable, defaults.cycleLength, 4)
    val median = usable.takeIf { it.isNotEmpty() }?.let(::weightedMedian) ?: length.toDouble()
    val deviations = usable.map { abs(it - median) }
    val mad = deviations.takeIf { it.isNotEmpty() }?.let(::weightedMedian) ?: 0.0
    val atypical = deviations.count { it > max(7.0, 3 * mad) }
    val confidence = when {
        usable.size < 6 || atypical > 0 || mad > 3 -> HistoryConfidence.LOW
        mad <= 1 -> HistoryConfidence.HIGH
        else -> HistoryConfidence.MEDIUM
    }
    val backtest = backtestCycles(ordered, defaults.cycleLength)
    val forecasts = simulateStarts(latest.start, today, usable, length, backtest, confidence, mad)
    val overdue = forecasts.isEmpty() || today > forecasts.first().upperBound
    return CycleAnalysis(
        cycleDay = day,
        forecasts = if (overdue) emptyList() else forecasts,
        unavailable = if (overdue) ForecastUnavailable.PAST_DUE else null,
        usesDefaults = usable.size < 4,
        periodDuration = duration,
        measuredPeriodDuration = durations.takeIf {
            it.isNotEmpty()
        }?.let(::weightedMedian)?.toInt(),
        backtest = backtest,
        mucusObservations = observations
    )
}

enum class CalendarDayKind {
    NONE,
    OBSERVED,
    PREDICTED,
    UNCERTAIN,
    APPROXIMATE,
    ESTIMATED_PERIOD,
    FERTILE_ESTIMATE,
    FERTILE_LIKELY,
    OVULATION_ESTIMATE
}

data class CalendarDayInfo(
    val kind: CalendarDayKind,
    val period: Period? = null,
    val forecast: CycleForecast? = null
)

fun calendarDay(
    periods: List<Period>,
    analysis: CycleAnalysis,
    date: LocalDate,
    today: LocalDate
): CalendarDayInfo {
    val period = periods.firstOrNull { date >= it.start && date <= (it.end ?: today) }
    if (period != null) return CalendarDayInfo(CalendarDayKind.OBSERVED, period)
    val point = analysis.forecasts.firstOrNull { it.predictedStartDate == date }
    if (point != null) {
        return CalendarDayInfo(
            if (point.horizon <=
                3
            ) {
                CalendarDayKind.PREDICTED
            } else {
                CalendarDayKind.APPROXIMATE
            },
            forecast = point
        )
    }
    val estimatedPeriod = analysis.forecasts.firstOrNull {
        date > it.predictedStartDate &&
            date < it.predictedStartDate.plusDays(analysis.periodDuration.toLong())
    }
    if (estimatedPeriod !=
        null
    ) {
        return CalendarDayInfo(CalendarDayKind.ESTIMATED_PERIOD, forecast = estimatedPeriod)
    }
    val fertility = fertilityEstimate(periods, analysis, date)
    return CalendarDayInfo(
        when (fertility) {
            FertilityLevel.POSSIBLE_OVULATION -> CalendarDayKind.OVULATION_ESTIMATE
            FertilityLevel.LIKELY, FertilityLevel.MUCUS_SIGNAL -> CalendarDayKind.FERTILE_LIKELY
            FertilityLevel.POSSIBLE -> CalendarDayKind.FERTILE_ESTIMATE
            FertilityLevel.UNKNOWN -> CalendarDayKind.NONE
        }
    )
}

/** Monday-first grid with blank cells outside the displayed month. */
fun monthDays(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val days = List<LocalDate?>(first.dayOfWeek.value - 1) { null } +
        (1..month.lengthOfMonth()).map { month.atDay(it) }
    return days + List((7 - days.size % 7) % 7) { null }
}
