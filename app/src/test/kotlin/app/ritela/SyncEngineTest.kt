package app.ritela

import app.ritela.domain.sync.SyncAttempt
import app.ritela.domain.sync.SyncBackoff
import app.ritela.domain.sync.SyncEngine
import app.ritela.domain.sync.SyncLease
import app.ritela.domain.sync.SyncQueuePort
import app.ritela.domain.sync.SyncTransport
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {
    private class Queue : SyncQueuePort {
        var item: SyncLease? = SyncLease("packet", "lease", "peer", "device", byteArrayOf(1))
        var allowed = true
        var stored = 0
        var failed = 0
        override suspend fun claim(): SyncLease? = item
        override suspend fun authorized(lease: SyncLease): Boolean = allowed
        override suspend fun stored(lease: SyncLease): Boolean {
            stored++
            return true
        }
        override suspend fun failed(lease: SyncLease, retryAfterMillis: Long): Boolean {
            failed++
            return allowed
        }
    }

    @Test fun idleAndRevokedJobsNeverReachTransport() = runBlocking {
        val queue = Queue()
        var calls = 0
        val engine = SyncEngine(queue, SyncTransport { calls++ })
        queue.item = null
        assertEquals(SyncAttempt.IDLE, engine.step())
        queue.item = SyncLease("packet", "lease", "peer", "device", byteArrayOf(1))
        queue.allowed = false
        assertEquals(SyncAttempt.CANCELLED, engine.step())
        assertEquals(0, calls)
    }

    @Test fun storageSuccessAndIoFailureHaveDifferentDurableOutcomes() = runBlocking {
        val queue = Queue()
        assertEquals(SyncAttempt.STORED, SyncEngine(queue, SyncTransport {}).step())
        assertEquals(
            SyncAttempt.RETRY,
            SyncEngine(
                queue,
                SyncTransport {
                    throw IOException()
                }
            ).step()
        )
        assertEquals(1, queue.stored)
        assertEquals(1, queue.failed)
    }

    @Test fun cancellationAndProgrammingErrorsAreNotSwallowedAsNetworkRetries() = runBlocking {
        val queue = Queue()
        val cancelled = runCatching {
            SyncEngine(queue, SyncTransport { throw CancellationException() }).step()
        }.exceptionOrNull()
        assertTrue(cancelled is CancellationException)
        assertTrue(
            runCatching {
                SyncEngine(queue, SyncTransport { error("Synthetic bug") }).step()
            }.isFailure
        )
        assertEquals(0, queue.failed)
    }

    @Test fun revokedLeaseDuringIoFailureIsNotReportedAsScheduledRetry() = runBlocking {
        val queue = Queue()
        val engine = SyncEngine(
            queue,
            SyncTransport {
                queue.allowed = false
                throw IOException()
            }
        )
        assertEquals(SyncAttempt.CANCELLED, engine.step())
    }

    @Test fun boundedBackoffValidatesJitterAndNeverOverflowsAtLargeAttemptCounts() {
        assertTrue(SyncBackoff.delay(1, 0.0) in 1..30000)
        assertTrue(SyncBackoff.delay(2, 0.0) > SyncBackoff.delay(1, 1.0))
        assertEquals(SyncBackoff.MAX_DELAY, SyncBackoff.delay(Int.MAX_VALUE, 1.0))
        for ((attempt, jitter) in listOf(0 to 0.5, 1 to Double.NaN, 1 to -1.0, 1 to 1.1)) {
            assertTrue(runCatching { SyncBackoff.delay(attempt, jitter) }.isFailure)
        }
    }
}
