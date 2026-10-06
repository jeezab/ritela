package app.ritela.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class MeasuredSeries(val values: List<Pair<LocalDate, Int>>) {
    val median: Int? get() {
        if (values.isEmpty()) return null
        val sorted = values.map { it.second }.sorted()
        return ((sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2.0).roundToInt()
    }
    val min: Int? get() = values.minOfOrNull { it.second }
    val max: Int? get() = values.maxOfOrNull { it.second }
}

fun measuredCycles(periods: List<Period>, today: LocalDate): MeasuredSeries {
    val ordered = periods.filter { it.start <= today }.sortedBy { it.start }
    return MeasuredSeries(
        ordered.zipWithNext().filter { (a, _) -> a.start >= today.minusYears(1) }
            .takeLast(12).map { (a, b) ->
                a.start to
                    ChronoUnit.DAYS.between(a.start, b.start).toInt()
            }
            .filter { it.second in 1..365 }
    )
}

fun measuredDurations(periods: List<Period>, today: LocalDate): MeasuredSeries = MeasuredSeries(
    periods.filter {
        it.end != null && it.end <= today &&
            it.start >= today.minusYears(1)
    }
        .sortedBy { it.start }.takeLast(12)
        .map { it.start to (ChronoUnit.DAYS.between(it.start, it.end) + 1).toInt() }
)
