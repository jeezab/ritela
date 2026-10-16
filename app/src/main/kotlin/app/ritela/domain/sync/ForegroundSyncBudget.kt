package app.ritela.domain.sync

/** Persisted attempt timestamps also survive profile switches and wall-clock rollback. */
object ForegroundSyncBudget {
    fun permits(attempts: List<Long>, now: Long): Boolean {
        if (attempts.any { it > now }) return false
        val recent = attempts.filter { now - it < 3600000L }.sorted()
        if (recent.size >= 264) return false
        // Capacity 24, refill one token per 15 seconds, plus the independent hourly cap.
        var balance = 24.0
        var previous = recent.firstOrNull() ?: now
        for (time in recent) {
            balance = minOf(24.0, balance + (time - previous) / 15000.0) - 1.0
            previous = time
        }
        return minOf(24.0, balance + (now - previous) / 15000.0) >= 1.0
    }
}
