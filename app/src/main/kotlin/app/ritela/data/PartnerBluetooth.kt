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
import java.io.InputStream
import java.io.OutputStream
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

    @Volatile private var socket: BluetoothSocket? = null

    @Volatile private var server: BluetoothServerSocket? = null
    private val mutable = MutableStateFlow(ExchangeState())
    val state = mutable.asStateFlow()
    private val mutablePeer = MutableStateFlow<String?>(null)
    val activePeer = mutablePeer.asStateFlow()
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

    /** An invitation is public and signed. Receiving it never establishes trust automatically. */
    suspend fun exchangeInvitations(address: String?, invitation: String): String {
        var remote: String? = null
        exchange(
            ExchangeState(
                if (address ==
                    null
                ) {
                    ExchangePhase.WAITING
                } else {
                    ExchangePhase.CONNECTING
                }
            )
        ) {
            val bt = requireNotNull(adapter)
            val connected = if (address == null) {
                val listener = bt.listenUsingRfcommWithServiceRecord(
                    "Ritela invitations",
                    INVITATION_SERVICE
                )
                server = listener
                listener.accept(120000).also {
                    socket = it
                    listener.close()
                    server = null
                }
            } else {
                bt.getRemoteDevice(
                    address
                ).createRfcommSocketToServiceRecord(INVITATION_SERVICE).also {
                    socket = it
                    it.connect()
                }
            }
            val local = PartnerInvitationFile.encode(invitation)
            val received = if (address == null) {
                val bytes = readFrame(connected.inputStream, PartnerInvitationFile.MAX_BYTES)
                val signed = requireNotNull(PartnerInvitationFile.decode(bytes))
                repository.inspectInvitation(signed)
                write(connected, local)
                signed
            } else {
                write(connected, local)
                val bytes = readFrame(connected.inputStream, PartnerInvitationFile.MAX_BYTES)
                requireNotNull(PartnerInvitationFile.decode(bytes)).also {
                    repository.inspectInvitation(it)
                }
            }
            remote = received
            // Connection confirmation requires explicit local consent and the signed return invitation.
            mutable.value = ExchangeState()
        }
        return requireNotNull(remote)
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
            mutablePeer.value = ack.identity
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
            mutablePeer.value = received.message.identity
            require(
                received.message.kind == "DATA" || received.message.kind == "DOODLE" ||
                    received.message.kind == "OPENED" ||
                    received.message.kind == "ACK" || received.message.kind == "SYNC" ||
                    received.message.kind == "SYNC_CAPS"
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
        mutablePeer.value = null
        runCatching { socket?.close() }
        runCatching { server?.close() }
        socket =
            null
        server = null
    }
    companion object {
        private val SERVICE = UUID.fromString("eb5a594e-f607-4479-a97a-704e180f7de5")
        private val INVITATION_SERVICE = UUID.fromString("82498566-359a-4f4b-856a-219918c3a1ec")
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
        fun read(socket: BluetoothSocket): ByteArray = readFrame(socket.inputStream)
        fun readFrame(input: InputStream, maximum: Int = MAX_FRAME): ByteArray =
            DataInputStream(input).let { stream ->
                val size = stream.readInt()
                require(size in 1..maximum)
                ByteArray(size).also(stream::readFully)
            }
        fun write(socket: BluetoothSocket, bytes: ByteArray) =
            writeFrame(socket.outputStream, bytes)
        fun writeFrame(output: OutputStream, bytes: ByteArray) {
            require(bytes.size in 1..MAX_FRAME)
            DataOutputStream(output).apply {
                writeInt(bytes.size)
                write(bytes)
                flush()
            }
        }
        private const val MAX_FRAME = 4 * 1024 * 1024
    }
}
