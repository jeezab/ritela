package app.ritela

import app.ritela.domain.Period
import app.ritela.domain.PeriodProblem
import app.ritela.domain.filledPeriodEnd
import app.ritela.domain.validatePeriod
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeriodRulesTest {
    private val today = LocalDate.of(2024, 3, 10)

    @Test fun acceptsLeapDayAndOpenPeriod() {
        assertNull(validatePeriod(LocalDate.of(2024, 2, 29), today, today))
        assertNull(validatePeriod(today, null, today))
        assertNull(validatePeriod(today, today, today))
    }

    @Test fun rejectsFutureStartAndKeepsACalculatedEnd() {
        assertEquals(PeriodProblem.FUTURE_DATE, validatePeriod(today.plusDays(1), null, today))
        assertNull(validatePeriod(today, today.plusDays(4), today))
        val start = today.minusDays(2)
        val next = Period(
            UUID(0, 1),
            start.plusDays(3),
            start.plusDays(6),
            Instant.EPOCH,
            Instant.EPOCH
        )
        assertEquals(start.plusDays(4), filledPeriodEnd(start, 5))
        assertEquals(start.plusDays(2), filledPeriodEnd(start, 5, listOf(next)))
    }

    @Test fun rejectsReversedDates() {
        assertEquals(
            PeriodProblem.END_BEFORE_START,
            validatePeriod(today, today.minusDays(1), today)
        )
    }
}
