package app.ritela.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    val periodDuration: Int = 5
)

/** Engineering estimate of period starts. Confidence is a history-quality label, not a probability. */
fun analyzeCycles(
    periods: List<Period>,
    today: LocalDate,
    defaults: PredictionDefaults = PredictionDefaults()
): CycleAnalysis {
    if (periods.isEmpty()) return CycleAnalysis(periodDuration = defaults.periodDuration)
    val ordered = periods.sortedBy { it.start }
    val invalid = ordered.any { validatePeriod(it.start, it.end, today) != null } ||
        ordered.map { it.id }.distinct().size != ordered.size ||
        ordered.zipWithNext().any { (a, b) -> a.end == null || a.end >= b.start }
    if (invalid) return CycleAnalysis(unavailable = ForecastUnavailable.INVALID_HISTORY)
    val latest = ordered.last()
    val day = ChronoUnit.DAYS.between(latest.start, today) + 1
    // A missing period can produce a gap, not evidence of a very long measured cycle.
    val recent = ordered.zipWithNext().takeLast(12).map { (a, b) ->
        ChronoUnit.DAYS.between(a.start, b.start)
    }
    val usable = recent.filter { it in 1..365 }.map { it.toDouble() }
    val duration = ordered.lastOrNull { it.end != null }?.let {
        (ChronoUnit.DAYS.between(it.start, it.end) + 1).coerceIn(1, 365).toInt()
    } ?: defaults.periodDuration
    val fallback = usable.size < 3
    val center = if (fallback) defaults.cycleLength.toDouble() else median(usable)
    val deviations = usable.map { abs(it - center) }
    val mad = if (fallback) 0.0 else median(deviations)
    val atypical = deviations.count { it > max(7.0, 3 * mad) } + recent.size - usable.size
    val confidence = when {
        usable.size < 6 || atypical > 0 || mad > 3 -> HistoryConfidence.LOW
        mad <= 1 -> HistoryConfidence.HIGH
        else -> HistoryConfidence.MEDIUM
    }
    val length = center.roundToInt()
    // Heuristic safety margin, deliberately wider when dates contain gaps/outliers.
    val baseRadius = if (fallback) 4 else max(2, ceil(3 * mad).toInt()) + 7 * atypical
    val forecasts = (1..12).map { horizon ->
        val expected = latest.start.plusDays(length.toLong() * horizon)
        val radius = ceil(baseRadius * sqrt(horizon.toDouble())).toLong() + horizon - 1
        CycleForecast(
            expected,
            expected.minusDays(radius),
            expected.plusDays(radius),
            horizon,
            confidence,
            usable.size,
            length,
            mad
        )
    }
    if (today > forecasts.first().upperBound) {
        return CycleAnalysis(
            day,
            unavailable = ForecastUnavailable.PAST_DUE,
            usesDefaults = fallback,
            periodDuration = duration
        )
    }
    return CycleAnalysis(day, forecasts, null, fallback, duration)
}

private fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
}

enum class CalendarDayKind { NONE, OBSERVED, PREDICTED, UNCERTAIN, APPROXIMATE, ESTIMATED_PERIOD }

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
    val range = analysis.forecasts.firstOrNull { date >= it.lowerBound && date <= it.upperBound }
    return if (range ==
        null
    ) {
        CalendarDayInfo(CalendarDayKind.NONE)
    } else {
        CalendarDayInfo(CalendarDayKind.UNCERTAIN, forecast = range)
    }
}

/** Monday-first grid with blank cells outside the displayed month. */
fun monthDays(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val days = List<LocalDate?>(first.dayOfWeek.value - 1) { null } +
        (1..month.lengthOfMonth()).map { month.atDay(it) }
    return days + List((7 - days.size % 7) % 7) { null }
}
