package app.ritela.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Presentation only: these calendar phases are estimates, not measured hormone states. */
enum class EstimatedCyclePhase { EARLY, FOLLICULAR, OVULATION, LUTEAL, UNKNOWN }

data class CycleSceneState(val phase: EstimatedCyclePhase, val progress: Float, val length: Int)

fun cycleSceneState(analysis: CycleAnalysis, today: LocalDate): CycleSceneState {
    val next = analysis.forecasts.firstOrNull()
    val day = analysis.cycleDay
    val length = next?.cycleMedian?.coerceAtLeast(1) ?: 28
    if (day == null || next == null || analysis.unavailable != null) {
        return CycleSceneState(EstimatedCyclePhase.UNKNOWN, 0f, length)
    }
    val remaining = ChronoUnit.DAYS.between(today, next.predictedStartDate)
    val phase = when {
        day <= analysis.periodDuration -> EstimatedCyclePhase.EARLY
        remaining in 13..19 -> EstimatedCyclePhase.OVULATION
        remaining > 19 -> EstimatedCyclePhase.FOLLICULAR
        else -> EstimatedCyclePhase.LUTEAL
    }
    return CycleSceneState(phase, ((day - 1).toFloat() / length).coerceIn(0f, 1f), length)
}
