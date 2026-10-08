package app.ritela.ui

import app.ritela.domain.CycleAnalysis
import app.ritela.domain.EstimatedCyclePhase
import app.ritela.domain.cycleSceneState
import java.time.LocalDate
import kotlin.math.roundToInt

/** Read-only focus: it never changes records or recalculates the forecast. */
data class OrbitInteractionState(
    val currentDay: Long,
    val length: Int,
    val focusDay: Long,
    val focusDate: LocalDate,
    val phase: EstimatedCyclePhase
) {
    val currentProgress: Float get() = orbitDayProgress(currentDay, length)
    val focusProgress: Float get() = orbitDayProgress(focusDay, length)
    val overdue: Boolean get() = currentDay > length
}

fun orbitDayProgress(day: Long, length: Int): Float {
    val days = length.coerceAtLeast(1)
    // Pin overdue days to the last represented day, never wrap them back to the sun.
    return (day.coerceAtLeast(1) - 1).coerceIn(0, (days - 1).toLong()).toFloat() / days
}

fun orbitDayAtProgress(progress: Float, length: Int): Long {
    val days = length.coerceAtLeast(1)
    return (1 + (progress.coerceIn(0f, 1f) * days).roundToInt()).coerceIn(1, days).toLong()
}

fun orbitInteractionState(
    analysis: CycleAnalysis,
    today: LocalDate,
    focusDay: Long = analysis.cycleDay ?: 1
): OrbitInteractionState {
    val current = analysis.cycleDay ?: 1
    val length = analysis.forecasts.firstOrNull()?.cycleMedian?.coerceAtLeast(1) ?: 28
    val date = today.minusDays(current - 1).plusDays(focusDay - 1)
    return OrbitInteractionState(
        current,
        length,
        focusDay,
        date,
        cycleSceneState(analysis.copy(cycleDay = focusDay), date).phase
    )
}
