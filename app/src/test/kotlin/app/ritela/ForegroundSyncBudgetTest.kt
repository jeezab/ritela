package app.ritela

import app.ritela.domain.sync.ForegroundSyncBudget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundSyncBudgetTest {
    @Test fun burstRefillHourlyLimitAndClockRollbackCannotBypassBudget() {
        val now = 1800000000000L
        assertTrue(ForegroundSyncBudget.permits(emptyList(), now))
        val burst = List(24) { now }
        assertFalse(ForegroundSyncBudget.permits(burst, now))
        assertFalse(ForegroundSyncBudget.permits(burst, now + 14999))
        assertTrue(ForegroundSyncBudget.permits(burst, now + 15000))
        assertFalse(ForegroundSyncBudget.permits(List(264) { now - 30000 }, now))
        assertTrue(ForegroundSyncBudget.permits(List(264) { now - 3600000 }, now))
        assertFalse(ForegroundSyncBudget.permits(listOf(now + 1), now))
    }
}
