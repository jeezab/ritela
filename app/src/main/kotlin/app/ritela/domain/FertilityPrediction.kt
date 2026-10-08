package app.ritela.domain

import java.time.LocalDate

enum class CervicalMucus { DRY, STICKY, CREAMY, WATERY, CLEAR_STRETCHY, UNUSUAL }

fun cervicalMucus(log: DayLog): CervicalMucus? =
    log.custom["discharge"]?.singleOrNull()?.let { id ->
        CervicalMucus.entries.find { it.name == id }
    }

data class OvulationEstimate(
    val centralDate: LocalDate,
    val earliest: LocalDate,
    val latest: LocalDate
)

/** Calendar inference with a 10–16 day luteal range; no observation confirms ovulation here. */
fun estimateOvulation(forecast: CycleForecast): OvulationEstimate = OvulationEstimate(
    forecast.predictedStartDate.minusDays(14),
    forecast.lowerBound.minusDays(16),
    forecast.upperBound.minusDays(10)
)

data class FertileWindow(
    val possible: ClosedRange<LocalDate>,
    val likely: ClosedRange<LocalDate>,
    val ovulation: OvulationEstimate
)

fun estimateFertileWindow(ovulation: OvulationEstimate): FertileWindow = FertileWindow(
    ovulation.earliest.minusDays(5)..ovulation.latest.plusDays(1),
    ovulation.centralDate.minusDays(5)..ovulation.centralDate.plusDays(1),
    ovulation
)

/** Historical starts give a date anchor, not proof of historical ovulation. */
fun fertilityWindows(periods: List<Period>, analysis: CycleAnalysis): List<FertileWindow> {
    if (analysis.unavailable == ForecastUnavailable.INVALID_HISTORY) return emptyList()
    val historical = periods.map { it.start }.distinct().sorted().drop(1).map {
        estimateFertileWindow(
            OvulationEstimate(it.minusDays(14), it.minusDays(16), it.minusDays(10))
        )
    }
    return historical + analysis.forecasts.map { estimateFertileWindow(estimateOvulation(it)) }
}

fun estimatedFertileWindow(
    periods: List<Period>,
    analysis: CycleAnalysis,
    date: LocalDate
): ClosedRange<LocalDate>? {
    val first = periods.minOfOrNull { it.start } ?: return null
    if (date < first) return null
    return fertilityWindows(periods, analysis).firstOrNull { date in it.possible }?.possible
}

enum class FertilityLevel { UNKNOWN, POSSIBLE, LIKELY, POSSIBLE_OVULATION, MUCUS_SIGNAL }

fun fertilityEstimate(
    periods: List<Period>,
    analysis: CycleAnalysis,
    date: LocalDate
): FertilityLevel {
    if (analysis.unavailable == ForecastUnavailable.INVALID_HISTORY) return FertilityLevel.UNKNOWN
    val signal = analysis.mucusObservations[date] in
        setOf(CervicalMucus.WATERY, CervicalMucus.CLEAR_STRETCHY)
    val first = periods.minOfOrNull { it.start }
    val windows = if (first != null &&
        date >= first
    ) {
        fertilityWindows(periods, analysis)
    } else {
        emptyList()
    }
    // Select the strongest overlapping category rather than whichever distant range comes first.
    return when {
        windows.any { date == it.ovulation.centralDate } -> FertilityLevel.POSSIBLE_OVULATION
        signal -> FertilityLevel.MUCUS_SIGNAL
        windows.any { date in it.likely } -> FertilityLevel.LIKELY
        windows.any { date in it.possible } -> FertilityLevel.POSSIBLE
        else -> FertilityLevel.UNKNOWN
    }
}
