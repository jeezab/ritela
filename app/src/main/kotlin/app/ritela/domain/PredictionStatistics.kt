package app.ritela.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Random
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class BacktestPrediction(
    val origin: LocalDate,
    val predictedStart: LocalDate,
    val actualStart: LocalDate,
    val cyclesUsed: Int
) {
    val errorDays: Long get() = ChronoUnit.DAYS.between(predictedStart, actualStart)
}

data class BacktestResult(val predictions: List<BacktestPrediction> = emptyList()) {
    val mae: Double? get() = predictions.takeIf { it.isNotEmpty() }
        ?.map { abs(it.errorDays).toDouble() }?.average()
    val medianAbsoluteError: Double? get() = predictions.takeIf { it.isNotEmpty() }
        ?.map { abs(it.errorDays).toDouble() }?.let(::sampleMedian)
    val medianError: Double? get() = predictions.takeIf { it.isNotEmpty() }
        ?.map { it.errorDays.toDouble() }?.let(::sampleMedian)
}

/** Recent observations have exponentially larger weights; input is chronological. */
internal fun weightedMedian(values: List<Double>): Double {
    require(values.isNotEmpty())
    val weighted = values.mapIndexed { index, value ->
        value to 0.86.pow(values.lastIndex - index)
    }.sortedBy { it.first }
    val half = weighted.sumOf { it.second } / 2
    var weight = 0.0
    return weighted.first {
        weight += it.second
        weight >= half
    }.first
}

internal fun sampleMedian(values: List<Double>): Double {
    val sorted = values.sorted()
    return (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2
}

internal fun recentIntervals(starts: List<LocalDate>, origin: LocalDate): List<Double> =
    starts.zipWithNext().filter { (a, b) -> a >= origin.minusYears(1) && b <= origin }
        .takeLast(12).map { (a, b) -> ChronoUnit.DAYS.between(a, b).toDouble() }
        .filter { it in 1.0..365.0 }

internal fun personalizedLength(values: List<Double>, baseline: Int, warmup: Int): Int {
    if (values.isEmpty()) return baseline
    val influence = (values.size.toDouble() / warmup).coerceAtMost(1.0)
    return (baseline * (1 - influence) + weightedMedian(values) * influence)
        .roundToInt().coerceIn(1, 365)
}

/** Rolling origins: every training interval ends at or before the origin, never at the target. */
fun backtestCycles(periods: List<Period>, baseline: Int = 28): BacktestResult {
    val starts = periods.map { it.start }.distinct().sorted()
    return BacktestResult(
        starts.zipWithNext().takeLast(24).map { (origin, actual) ->
            val training = recentIntervals(starts, origin)
            BacktestPrediction(
                origin,
                origin.plusDays(personalizedLength(training, baseline, 4).toLong()),
                actual,
                training.size
            )
        }
    )
}

/** Fixed work budget: 512 trajectories, at most 366 future starts. No persisted random state. */
internal fun simulateStarts(
    latest: LocalDate,
    today: LocalDate,
    values: List<Double>,
    length: Int,
    errors: BacktestResult,
    confidence: HistoryConfidence,
    mad: Double
): List<CycleForecast> {
    val random = Random(0x524954454C41L)
    val median = if (values.isEmpty()) length.toDouble() else weightedMedian(values)
    // A missed record is not silently split into invented cycles. Limit its sampling influence.
    val limit = max(7.0, 4.5 * mad)
    val weights = values.indices.map { 0.86.pow(values.lastIndex - it) }
    val weightSum = weights.sum()
    val clipped = values.map { (it - median).coerceIn(-limit, limit) }
    val bias = if (clipped.isEmpty()) {
        0.0
    } else {
        clipped.indices.sumOf { clipped[it] * weights[it] } / weightSum
    }
    val residuals = clipped.map { it - bias }
    val absoluteErrors = errors.predictions.filter {
        it.origin >= today.minusYears(1) &&
            ChronoUnit.DAYS.between(it.origin, it.actualStart) in 1..365
    }.takeLast(12).map {
        abs(it.errorDays).toDouble()
    }.sorted()
    val observedRadius = absoluteErrors.takeIf { it.isNotEmpty() }
        ?.let { it[((it.size - 1) * 0.8).roundToInt()] } ?: 0.0
    // Shrink toward a conservative 8-day planning margin while validation data are scarce.
    val validationWeight = absoluteErrors.size / (absoluteErrors.size + 4.0)
    val radius = max(1.0, 8 * (1 - validationWeight) + observedRadius * validationWeight)
    val noise = max(0.8, radius / 1.2816)
    val offsets = DoubleArray(512)
    var previousLeft = 0L
    var previousRight = 0L
    var previousPoint = latest
    val result = mutableListOf<CycleForecast>()
    for (horizon in 1..366) {
        for (index in offsets.indices) {
            var residual = 0.0
            if (residuals.isNotEmpty()) {
                val target = random.nextDouble() * weightSum
                var cumulative = 0.0
                val selected = weights.indexOfFirst {
                    cumulative += it
                    cumulative >= target
                }
                residual = residuals[selected.coerceAtLeast(0)]
            }
            offsets[index] +=
                (length + residual + random.nextGaussian() * noise).coerceIn(1.0, 365.0)
        }
        val sorted = offsets.sorted()
        val pointOffset = if (horizon == 1) length.toLong() else sorted[256].roundToInt().toLong()
        val point = maxOf(latest.plusDays(pointOffset), previousPoint.plusDays(1))
        if (point > today.plusYears(1)) break
        val actualOffset = ChronoUnit.DAYS.between(latest, point)
        val calibrated = ceil(radius * sqrt(horizon.toDouble())).toLong()
        var left = maxOf(calibrated, actualOffset - sorted[51].roundToInt(), previousLeft)
        var right = maxOf(calibrated, sorted[460].roundToInt() - actualOffset, previousRight)
        // Integer dates sometimes quantize equal widths. Keep growth explicit at every horizon.
        if (horizon > 1 && left + right <= previousLeft + previousRight) {
            if (horizon % 2 == 0) left++ else right++
        }
        result += CycleForecast(
            point,
            point.minusDays(left),
            point.plusDays(right),
            horizon,
            confidence,
            values.size,
            length,
            mad
        )
        previousPoint = point
        previousLeft = left
        previousRight = right
    }
    return result
}
