package app.ritela

import app.ritela.domain.CycleAnalysis
import app.ritela.domain.EstimatedCyclePhase
import app.ritela.domain.ForecastUnavailable
import app.ritela.domain.Period
import app.ritela.domain.analyzeCycles
import app.ritela.domain.cycleSceneState
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleSceneTest {
    @Test fun estimatedPhasesFollowTheExistingFertileWindowWithoutWrappingOverdueDays() {
        val start = LocalDate.of(2026, 9, 30)
        val periods =
            listOf(Period(UUID(0, 1), start, start.plusDays(4), Instant.EPOCH, Instant.EPOCH))
        for ((day, phase) in listOf(
            5L to EstimatedCyclePhase.EARLY,
            6L to EstimatedCyclePhase.FOLLICULAR,
            10L to EstimatedCyclePhase.OVULATION,
            16L to EstimatedCyclePhase.OVULATION,
            17L to EstimatedCyclePhase.LUTEAL,
            30L to EstimatedCyclePhase.LUTEAL
        )) {
            val today = start.plusDays(day - 1)
            val scene = cycleSceneState(analyzeCycles(periods, today), today)
            assertEquals(phase, scene.phase)
            assertEquals(28, scene.length)
            assertTrue(scene.progress in 0f..1f)
            if (day == 30L) assertEquals(1f, scene.progress, 0f)
        }
    }

    @Test fun missingOrUnavailableForecastDoesNotInventAPhase() {
        val today = LocalDate.of(2026, 10, 7)
        for (reason in ForecastUnavailable.entries) {
            assertEquals(
                EstimatedCyclePhase.UNKNOWN,
                cycleSceneState(CycleAnalysis(cycleDay = 40, unavailable = reason), today).phase
            )
        }
        assertEquals(EstimatedCyclePhase.UNKNOWN, cycleSceneState(CycleAnalysis(), today).phase)
    }
}
