package app.ritela

import app.ritela.domain.Period
import app.ritela.domain.PeriodRangeSelection
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodRangeSelectionTest {
    private val today = LocalDate.of(2026, 10, 9)
    private fun record(start: LocalDate, end: LocalDate?) =
        Period(UUID(0, start.toEpochDay()), start, end, Instant.EPOCH, Instant.EPOCH)

    @Test fun rangeIsIdenticalInBothDirectionsIncludingLeapDayAndYearBoundary() {
        listOf(
            LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 8, 3),
            LocalDate.of(2024, 2, 27) to LocalDate.of(2024, 3, 1),
            LocalDate.of(2025, 12, 30) to LocalDate.of(2026, 1, 3)
        ).forEach { (first, last) ->
            val selection = PeriodRangeSelection(today)
            assertEquals(selection.pick(first).pick(last), selection.pick(last).pick(first))
            assertEquals(first, selection.pick(last).pick(first).start)
            assertEquals(last, selection.pick(last).pick(first).end)
        }
    }

    @Test fun firstClickIsClosedAndPastRecordDoesNotReserveAllFutureDays() {
        val next = record(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5))
        val first = PeriodRangeSelection(today).pick(LocalDate.of(2026, 8, 3))
        assertTrue(first.canSave(listOf(next), today))
        assertFalse(first.canKeepOngoing(listOf(next), today))
        val result = first.pick(LocalDate.of(2026, 8, 1))
        assertTrue(result.canSave(listOf(next), today))
    }

    @Test fun occupiedAndCrossingDaysAreBlockedInBothDirectionsAndEditingExcludesSelf() {
        val existing = record(today.minusDays(10), today.minusDays(6))
        val before = PeriodRangeSelection(today).pick(today.minusDays(12))
        val after = PeriodRangeSelection(today).pick(today.minusDays(4))
        assertFalse(before.canPick(listOf(existing), after.start, today))
        assertFalse(after.canPick(listOf(existing), before.start, today))
        assertFalse(before.canPick(listOf(existing), existing.start, today))
        assertTrue(before.canPick(listOf(existing), before.start.plusDays(1), today))
        assertTrue(PeriodRangeSelection(existing.start, existing.end).canSave(emptyList(), today))
        assertFalse(before.canPick(emptyList(), today.plusDays(1), today))
        assertFalse(PeriodRangeSelection(today.plusDays(1)).canKeepOngoing(emptyList(), today))
    }

    @Test fun ongoingIsOnlyAllowedAfterAllClosedOrOpenRecords() {
        val closed = record(today.minusDays(10), today.minusDays(6))
        assertTrue(PeriodRangeSelection(today).canKeepOngoing(listOf(closed), today))
        assertFalse(PeriodRangeSelection(today.minusDays(12)).canKeepOngoing(listOf(closed), today))
        assertFalse(
            PeriodRangeSelection(today).canKeepOngoing(listOf(closed.copy(end = null)), today)
        )
    }
}
