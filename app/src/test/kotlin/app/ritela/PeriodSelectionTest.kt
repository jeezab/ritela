package app.ritela

import app.ritela.domain.Period
import app.ritela.domain.measuredCycles
import app.ritela.domain.measuredDurations
import app.ritela.domain.periodConflict
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeriodSelectionTest {
    private val start = LocalDate.of(2026, 10, 1)
    private fun record(day: LocalDate, end: LocalDate?) =
        Period(UUID.randomUUID(), day, end, Instant.EPOCH, Instant.EPOCH)

    @Test fun preventsSelectingThroughAnExistingRecordButAllowsAdjacentDays() {
        val record = record(start, start.plusDays(4))
        assertEquals(record, periodConflict(listOf(record), start.minusDays(2), start.plusDays(7)))
        assertEquals(record, periodConflict(listOf(record), start.plusDays(4), start.plusDays(6)))
        assertNull(periodConflict(listOf(record), start.plusDays(5), start.plusDays(6)))
        assertNull(periodConflict(listOf(record), start.minusDays(2), start.minusDays(1)))
    }

    @Test fun openRangeCannotSwallowLaterHistoryAndEditExcludesOnlyItself() {
        val first = record(start, start.plusDays(4))
        val later = record(start.plusDays(28), null)
        assertEquals(later, periodConflict(listOf(first, later), start, null, first.id))
        assertEquals(later, periodConflict(listOf(later), start.plusDays(60), start.plusDays(61)))
        assertNull(periodConflict(listOf(first, later), start, start.plusDays(5), first.id))
    }

    @Test fun measuredInsightsUseAnnualMedianAndIgnoreUnfinishedDurations() {
        val today = start.plusDays(60)
        val records = listOf(
            record(start.minusYears(2), start.minusYears(2).plusDays(20)),
            record(start, start.plusDays(4)),
            record(start.plusDays(28), start.plusDays(30)),
            record(start.plusDays(58), null)
        )
        assertEquals(29, measuredCycles(records, today).median)
        assertEquals(4, measuredDurations(records, today).median)
        assertEquals(2, measuredDurations(records, today).values.size)
        assertNull(measuredCycles(emptyList(), today).median)
    }
}
