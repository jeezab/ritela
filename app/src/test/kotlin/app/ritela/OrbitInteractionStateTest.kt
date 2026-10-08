package app.ritela

import app.ritela.domain.CycleAnalysis
import app.ritela.domain.EstimatedCyclePhase
import app.ritela.domain.Period
import app.ritela.domain.analyzeCycles
import app.ritela.ui.orbitInteractionState
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitInteractionStateTest {
    @Test fun focusedDayUsesTheRecordedStartAndExistingPhaseRules() {
        val start = LocalDate.of(2026, 9, 30)
        val today = start.plusDays(7)
        val periods =
            listOf(Period(UUID(0, 1), start, start.plusDays(4), Instant.EPOCH, Instant.EPOCH))
        val analysis = analyzeCycles(periods, today)
        val focus = orbitInteractionState(analysis, today, 12)
        assertEquals(start.plusDays(11), focus.focusDate)
        assertEquals(EstimatedCyclePhase.OVULATION, focus.phase)
        assertEquals(8L, focus.currentDay)
        assertEquals(8L, analysis.cycleDay)
        assertEquals(today, orbitInteractionState(analysis, today).focusDate)
    }

    @Test fun overdueAndUnknownHistoryDoNotBecomeANewCycleOrAPhaseClaim() {
        val today = LocalDate.of(2026, 10, 8)
        val state = orbitInteractionState(CycleAnalysis(cycleDay = 40), today)
        assertTrue(state.overdue)
        assertEquals(40L, state.focusDay)
        assertTrue(state.currentProgress > 0.9f && state.currentProgress < 1f)
        assertEquals(EstimatedCyclePhase.UNKNOWN, state.phase)
        assertEquals(today, state.focusDate)
    }
}
