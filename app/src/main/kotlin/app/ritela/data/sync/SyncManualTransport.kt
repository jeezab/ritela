package app.ritela.data.sync

import app.ritela.data.PartnerRepository
import app.ritela.domain.sync.SyncLease
import app.ritela.domain.sync.SyncTransport
import org.json.JSONObject

/** Explicit request/reply adapter. The caller supplies foreground manual I/O; no default
 * network, discovery or scheduler. File export alone cannot acknowledge remote storage.
 */
class SyncManualTransport(
    private val repository: PartnerRepository,
    private val exchange: suspend (ByteArray) -> ByteArray
) : SyncTransport {
    override suspend fun store(lease: SyncLease) {
        val message = repository.retry(lease.packetId)
        require(
            message.kind == "SYNC" && message.identity == lease.peerIdentity &&
                message.device == lease.peerDevice &&
                message.envelope.toByteArray().contentEquals(lease.bytes)
        )
        val receipt = repository.receive(exchange(lease.bytes.copyOf())).message
        require(
            receipt.kind == "ACK" && JSONObject(receipt.body).getString("message") == message.id
        )
    }
}
