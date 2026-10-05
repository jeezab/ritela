package app.ritela

import app.ritela.domain.PeriodProblem
import app.ritela.domain.validatePeriod
import java.time.LocalDate
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

    @Test fun rejectsFutureStartOrEnd() {
        assertEquals(PeriodProblem.FUTURE_DATE, validatePeriod(today.plusDays(1), null, today))
        assertEquals(PeriodProblem.FUTURE_DATE, validatePeriod(today, today.plusDays(1), today))
    }

    @Test fun rejectsReversedDates() {
        assertEquals(
            PeriodProblem.END_BEFORE_START,
            validatePeriod(today, today.minusDays(1), today)
        )
    }
}
