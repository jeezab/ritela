package app.ritela.domain.sync

data class SyncLease(
    val packetId: String,
    val token: String,
    val peerIdentity: String,
    val peerDevice: String,
    val bytes: ByteArray
)

enum class SyncAttempt { IDLE, STORED, RETRY, CANCELLED }

interface SyncQueuePort {
    suspend fun claim(): SyncLease?
    suspend fun authorized(lease: SyncLease): Boolean
    suspend fun stored(lease: SyncLease): Boolean
    suspend fun failed(lease: SyncLease, retryAfterMillis: Long = 0): Boolean
}

/** Explicit dispatch port; internet transport and scheduling are separate opt-in integrations. */
fun interface SyncTransport {
    suspend fun store(lease: SyncLease)
}

class SyncEngine(private val queue: SyncQueuePort, private val transport: SyncTransport) {
    suspend fun step(): SyncAttempt {
        val lease = queue.claim() ?: return SyncAttempt.IDLE
        if (!queue.authorized(lease)) return SyncAttempt.CANCELLED
        return try {
            transport.store(lease)
            if (queue.stored(lease)) SyncAttempt.STORED else SyncAttempt.CANCELLED
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: java.io.IOException) {
            if (queue.failed(lease)) SyncAttempt.RETRY else SyncAttempt.CANCELLED
        }
    }
}

object SyncBackoff {
    const val MAX_DELAY = 6 * 60 * 60 * 1000L
    fun delay(attempt: Int, jitter: Double): Long {
        require(attempt > 0 && jitter.isFinite() && jitter in 0.0..1.0)
        val base = (30000L * (1L shl (attempt - 1).coerceAtMost(10))).coerceAtMost(MAX_DELAY)
        return (base * (0.75 + jitter * 0.25)).toLong()
    }
}
