package app.ritela.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import app.ritela.domain.ExchangeEdge
import app.ritela.domain.ExchangePhase
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class NearbyPhone(val address: String, val name: String)
data class ExchangeState(
    val phase: ExchangePhase = ExchangePhase.IDLE,
    val edge: ExchangeEdge? = null,
    val incoming: Boolean = false
)

/** Foreground-only RFCOMM. Closing sockets interrupts blocking accept/read/connect. */
// CONNECT/ADVERTISE are requested by the UI immediately before use.
@SuppressLint("MissingPermission")
class PartnerBluetooth(context: Context, private val repository: PartnerRepository) :
    AutoCloseable {
    private val adapter: BluetoothAdapter? = context.getSystemService(
        BluetoothManager::class.java
    )?.adapter
    private var socket: BluetoothSocket? = null
    private var server: BluetoothServerSocket? = null
    private val mutable = MutableStateFlow(ExchangeState())
    val state = mutable.asStateFlow()
    fun phones(): List<NearbyPhone> = adapter?.bondedDevices?.map {
        NearbyPhone(
            it.address,
            it.name ?: it.address
        )
    }.orEmpty()
    fun available(): Boolean = adapter?.isEnabled == true
    fun reset() {
        close()
        mutable.value = ExchangeState()
    }
    suspend fun send(address: String, message: PartnerMessage, edge: ExchangeEdge, rotation: Int) =
        exchange(ExchangeState(ExchangePhase.CONNECTING, edge)) {
            val bt = requireNotNull(adapter)
            val connected = bt.getRemoteDevice(address).createRfcommSocketToServiceRecord(SERVICE)
            socket = connected
            connected.connect()
            mutable.value = ExchangeState(ExchangePhase.TRANSFERRING, edge)
            val keys = repository.identity()
            val offer = JSONObject().put("format", "ritela.bluetooth").put("v", 1)
                .put(
                    "edge",
                    edge.name
                ).put("rotation", rotation).put("packet", message.envelope).toString()
            val frame = JSONObject().put(
                "offer",
                offer
            ).put(
                "signature",
                PartnerCrypto.sign(PartnerCrypto.privateKey(keys.devicePrivate, "EC"), offer)
            ).toString().toByteArray()
            write(connected, frame)
            val ack = repository.receive(read(connected)).message
            require(ack.kind == "ACK" && JSONObject(ack.body).getString("message") == message.id)
            // Only the authenticated persisted acknowledgement makes the sender successful.
            mutable.value = ExchangeState(ExchangePhase.ACKNOWLEDGED, edge)
        }
    suspend fun receive(rotation: Int) =
        exchange(ExchangeState(ExchangePhase.WAITING, incoming = true)) {
            val listener = requireNotNull(
                adapter
            ).listenUsingRfcommWithServiceRecord("Ritela", SERVICE)
            server = listener
            val connected = listener.accept(120000)
            socket = connected
            listener.close()
            server = null
            mutable.value = ExchangeState(ExchangePhase.TRANSFERRING, incoming = true)
            val frame = JSONObject(read(connected).toString(Charsets.UTF_8))
            val offerRaw = frame.getString("offer")
            val offer = JSONObject(offerRaw)
            require(offer.getString("format") == "ritela.bluetooth" && offer.getInt("v") == 1)
            val packet = offer.getString("packet").toByteArray()
            val header = JSONObject(JSONObject(packet.toString(Charsets.UTF_8)).getString("header"))
            val identity = repository.snapshot().identities.single {
                it.id == header.getString("from") &&
                    it.exchangeEnabled
            }
            val device = identity.devices.single {
                it.id == header.getString("device") && it.enabled
            }
            require(
                PartnerCrypto.verify(
                    PartnerCrypto.publicKey(device.publicKey, "EC"),
                    offerRaw,
                    frame.getString("signature")
                )
            )
            val edge =
                receiveEdge(
                    ExchangeEdge.valueOf(offer.getString("edge")),
                    offer.getInt("rotation"),
                    rotation
                )
            mutable.value = ExchangeState(ExchangePhase.TRANSFERRING, edge, true)
            val received = repository.receive(packet)
            require(
                received.message.kind == "DATA" || received.message.kind == "DOODLE" ||
                    received.message.kind == "OPENED" ||
                    received.message.kind == "ACK"
            )
            val ack = repository.acknowledgement(received.message)
            write(connected, ack.envelope.toByteArray())
            mutable.value = ExchangeState(ExchangePhase.ACKNOWLEDGED, edge, true)
        }
    private suspend fun exchange(initial: ExchangeState, block: suspend () -> Unit) =
        withContext(Dispatchers.IO) {
            check(
                mutable.value.phase !in
                    listOf(
                        ExchangePhase.WAITING,
                        ExchangePhase.CONNECTING,
                        ExchangePhase.TRANSFERRING
                    )
            )
            mutable.value = initial
            try {
                // Timeout closes native blocking I/O before awaiting cancellation.
                coroutineScope {
                    val work = async { block() }
                    try {
                        withTimeout(125000) { work.await() }
                    } finally {
                        close()
                        work.cancel()
                    }
                }
            } catch (error: Exception) {
                mutable.value = initial.copy(phase = ExchangePhase.FAILED)
                throw error
            } finally {
                close()
            }
        }
    override fun close() {
        runCatching { socket?.close() }
        runCatching { server?.close() }
        socket =
            null
        server = null
    }
    companion object {
        private val SERVICE = UUID.fromString("eb5a594e-f607-4479-a97a-704e180f7de5")
        fun receiveEdge(
            edge: ExchangeEdge,
            senderRotation: Int,
            receiverRotation: Int
        ): ExchangeEdge {
            require(senderRotation in 0..3 && receiverRotation in 0..3)
            val clockwise =
                listOf(ExchangeEdge.TOP, ExchangeEdge.RIGHT, ExchangeEdge.BOTTOM, ExchangeEdge.LEFT)
            return clockwise[(clockwise.indexOf(edge) + senderRotation - receiverRotation + 6) % 4]
        }
        fun read(socket: BluetoothSocket): ByteArray =
            DataInputStream(socket.inputStream).let { stream ->
                val size = stream.readInt()
                require(size in 1..MAX_FRAME)
                ByteArray(size).also(stream::readFully)
            }
        fun write(socket: BluetoothSocket, bytes: ByteArray) {
            require(bytes.size in 1..MAX_FRAME)
            DataOutputStream(socket.outputStream).apply {
                writeInt(bytes.size)
                write(bytes)
                flush()
            }
        }
        private const val MAX_FRAME = 4 * 1024 * 1024
    }
}
