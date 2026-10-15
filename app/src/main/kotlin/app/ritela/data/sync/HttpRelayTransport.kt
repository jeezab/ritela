package app.ritela.data.sync

import app.ritela.data.PartnerCrypto
import app.ritela.domain.sync.SyncLease
import app.ritela.domain.sync.SyncLimits
import app.ritela.domain.sync.SyncTransport
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Only a build/operator-supplied exact origin is allowed; no discovery, redirects or fallback. */
class RelayEndpoint(origin: String, allowedHosts: Set<String>) {
    private val origin: String
    init {
        val uri = URI(origin)
        require(
            uri.scheme == "https" && uri.host in allowedHosts && uri.host == uri.host.lowercase()
        )
        require(
            uri.port == -1 && uri.rawUserInfo == null && uri.rawQuery == null &&
                uri.rawFragment == null
        )
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
        this.origin = "https://${uri.host}"
    }
    fun url(target: String): URL {
        require(target.startsWith("/v1/") && !target.contains("//"))
        require(target.matches(Regex("[a-zA-Z0-9/?.=&_-]+")))
        require(URI(target).normalize().rawPath == URI(target).rawPath)
        return URL(origin + target)
    }
    override fun toString() = "RelayEndpoint(redacted)"
}

class RelayHttpFailure(val status: Int, val retryAfterMillis: Long = 0) :
    IOException("RELAY_HTTP") {
    val retryable get() = status == 408 || status == 429 || status in 500..599
}
class RelayProtocolFailure : IOException("RELAY_PROTOCOL")
class RelayAccessDisabled : CancellationException("RELAY_DISABLED")

class RelayHttpRequest(
    val url: URL,
    val method: String,
    val headers: Map<String, String>,
    val body: ByteArray
) {
    override fun toString() = "RelayHttpRequest(redacted)"
}
class RelayHttpResponse(val status: Int, val body: ByteArray, val retryAfter: String? = null) {
    override fun toString() = "RelayHttpResponse(redacted)"
}
fun interface RelayHttpExchange {
    /** authorize must be called immediately before connecting, including test implementations. */
    suspend fun execute(request: RelayHttpRequest, authorize: suspend () -> Unit): RelayHttpResponse
}

/** Platform TLS verification remains enabled. Cancellation actively disconnects blocking IO. */
class HttpsRelayExchange(
    private val open: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }
) : RelayHttpExchange {
    override suspend fun execute(
        request: RelayHttpRequest,
        authorize: suspend () -> Unit
    ): RelayHttpResponse {
        val result = withTimeoutOrNull(30_000) {
            coroutineScope {
                suspendCancellableCoroutine { continuation ->
                    val connection = AtomicReference<HttpsURLConnection?>()
                    val task = launch(Dispatchers.IO) {
                        try {
                            authorize()
                            currentCoroutineContext().ensureActive()
                            val http = open(request.url)
                            connection.set(http)
                            try {
                                currentCoroutineContext().ensureActive()
                                http.connectTimeout = 10_000
                                http.readTimeout = 15_000
                                http.instanceFollowRedirects = false
                                http.useCaches = false
                                http.requestMethod = request.method
                                request.headers.forEach { (key, value) ->
                                    http.setRequestProperty(key, value)
                                }
                                http.setRequestProperty("Accept-Encoding", "identity")
                                http.setRequestProperty("Cache-Control", "no-store")
                                if (request.body.isNotEmpty()) {
                                    http.doOutput = true
                                    http.setFixedLengthStreamingMode(request.body.size)
                                }
                                authorize()
                                currentCoroutineContext().ensureActive()
                                http.connect()
                                if (request.body.isNotEmpty()) {
                                    http.outputStream.use {
                                        it.write(request.body)
                                    }
                                }
                                val status = http.responseCode
                                val limit = if (status in 200..299) RESPONSE_BYTES else 16384
                                if (http.contentLengthLong > limit ||
                                    http.getHeaderField("Content-Encoding")?.let {
                                        it != "identity"
                                    } ==
                                    true
                                ) {
                                    throw RelayProtocolFailure()
                                }
                                val source = if (status in
                                    200..299
                                ) {
                                    http.inputStream
                                } else {
                                    http.errorStream
                                }
                                val bytes = source?.use { stream ->
                                    val out = ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val count = stream.read(buffer)
                                        if (count == -1) break
                                        if (out.size() + count > limit) throw RelayProtocolFailure()
                                        out.write(buffer, 0, count)
                                    }
                                    out.toByteArray()
                                } ?: byteArrayOf()
                                if (http.contentLengthLong >= 0 &&
                                    http.contentLengthLong != bytes.size.toLong()
                                ) {
                                    throw RelayProtocolFailure()
                                }
                                continuation.resume(
                                    RelayHttpResponse(
                                        status,
                                        bytes,
                                        http.getHeaderField("Retry-After")
                                    )
                                )
                            } finally {
                                connection.compareAndSet(http, null)
                                http.disconnect()
                            }
                        } catch (cancelled: CancellationException) {
                            continuation.cancel(cancelled)
                        } catch (error: Exception) {
                            // Never retain an exception cause: platform messages can contain signed URLs.
                            continuation.resumeWithException(
                                if (error is RelayProtocolFailure) {
                                    error
                                } else {
                                    IOException(
                                        "RELAY_IO"
                                    )
                                }
                            )
                        }
                    }
                    continuation.invokeOnCancellation {
                        connection.getAndSet(null)?.disconnect()
                        task.cancel()
                    }
                }
            }
        }
        return result ?: throw IOException("RELAY_TIMEOUT")
    }
    companion object {
        const val RESPONSE_BYTES = 512 * 1024
    }
}

class RelayUpload(
    val route: String,
    val capability: String,
    val expiresAt: Long,
    val generation: Long = 0,
    val grantDigest: String? = null,
    val reservation: String? = null
) {
    fun target(id: String): String {
        require(route.matches(Regex("[0-9a-f]{64}")) && capability.matches(Regex("[0-9a-f]{64}")))
        require(UUID.fromString(id).toString() == id && expiresAt > 0)
        require(generation in 0..MAX_SAFE_INTEGER)
        require(
            if (generation == 0L) {
                grantDigest == null && reservation == null
            } else {
                grantDigest?.matches(Regex("[0-9a-f]{64}")) == true
            }
        )
        reservation?.let { require(UUID.fromString(it).toString() == it) }
        val kind = if (generation == 0L) "MESSAGE" else "AUTHOR"
        return "/v1/routes/$route/packets/$id?class=$kind" +
            "&expires=$expiresAt&generation=$generation&grant=${grantDigest ?: "none"}" +
            (reservation?.let { "&reservation=$it" } ?: "")
    }
    override fun toString() = "RelayUpload(redacted)"
}

class RelayFetchedPacket(
    val sequence: Long,
    val route: String,
    val id: String,
    val ciphertext: ByteArray
) {
    override fun toString() = "RelayFetchedPacket(redacted)"
}
class RelayFetchPage(val packets: List<RelayFetchedPacket>, val cursor: Long) {
    override fun toString() = "RelayFetchPage(redacted)"
}
private const val MAX_SAFE_INTEGER = 9007199254740991L

/** No endpoint/key creation or requests in constructors. Bytes must already be HPKE ciphertext.
 * A relay storage ACK never calls queue's E2E acknowledgement. The caller owns durable apply/cursor.
 */
class HttpRelayTransport(
    private val endpoint: RelayEndpoint,
    private val keys: RelayKeys,
    private val permitted: suspend (SyncLease?) -> Boolean,
    private val upload: suspend (SyncLease) -> RelayUpload,
    private val exchange: RelayHttpExchange = HttpsRelayExchange(),
    private val now: () -> Long = System::currentTimeMillis
) : SyncTransport {
    private suspend fun authorize(lease: SyncLease?) {
        currentCoroutineContext().ensureActive()
        if (!permitted(lease)) throw RelayAccessDisabled()
    }

    override suspend fun store(lease: SyncLease) {
        authorize(lease)
        require(lease.bytes.size in 1..SyncLimits.ENVELOPE_BYTES)
        val address = upload(lease)
        require(address.expiresAt > now() && address.expiresAt <= now() + 7 * 86400000L)
        val result =
            request("PUT", address.target(lease.packetId), lease.bytes, address.capability, lease)
        if (result.opt("accepted") != true ||
            result.optString("result") !in listOf("", "IMPORTED")
        ) {
            // CANCELLED is not successful storage or E2E delivery.
            throw RelayProtocolFailure()
        }
    }

    suspend fun fetch(cursor: Long): RelayFetchPage {
        require(cursor in 0..MAX_SAFE_INTEGER)
        val result = request("GET", "/v1/devices/${keys.handle}/packets?cursor=$cursor&limit=32")
        try {
            val rows = result.getJSONArray("packets")
            require(rows.length() <= 32)
            var previous = cursor
            val seen = mutableSetOf<String>()
            val packets = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val sequence = strictInteger(row.get("sequence"))
                require(sequence > previous && sequence <= MAX_SAFE_INTEGER)
                previous = sequence
                val route = row.getString("route")
                val id = row.getString("id")
                require(
                    route.matches(Regex("[0-9a-f]{64}")) && UUID.fromString(id).toString() == id
                )
                require(seen.add("$route/$id"))
                val encoded = row.getString("ciphertext")
                require(encoded.length <= ((SyncLimits.ENVELOPE_BYTES + 2) / 3) * 4)
                val bytes = PartnerCrypto.bytes(encoded)
                require(
                    bytes.size in 1..SyncLimits.ENVELOPE_BYTES &&
                        PartnerCrypto.b64(bytes) == encoded
                )
                RelayFetchedPacket(sequence, route, id, bytes)
            }
            val next = result.getString("cursor")
            require(next.matches(Regex("0|[1-9][0-9]{0,15}")) && next.toLong() == previous)
            return RelayFetchPage(packets, previous)
        } catch (_: Exception) {
            throw RelayProtocolFailure()
        }
    }

    /** Call only after durable authenticated apply; retry after death is safe by immutable IDs. */
    suspend fun acknowledge(packets: List<RelayFetchedPacket>) {
        require(packets.size in 1..128)
        val rows = JSONArray()
        val seen = mutableSetOf<String>()
        packets.forEach {
            require(
                it.route.matches(Regex("[0-9a-f]{64}")) &&
                    UUID.fromString(it.id).toString() == it.id
            )
            require(seen.add("${it.route}/${it.id}"))
            rows.put(JSONObject().put("route", it.route).put("id", it.id))
        }
        val result =
            request(
                "POST",
                "/v1/devices/${keys.handle}/ack",
                JSONObject().put("packets", rows).toString().toByteArray()
            )
        if (result.opt("deleted") != true) throw RelayProtocolFailure()
    }

    /** Explicit pilot control operations, including registration, use the same consent gate. */
    suspend fun request(
        method: String,
        target: String,
        body: ByteArray = byteArrayOf(),
        capability: String = "",
        lease: SyncLease? = null
    ): JSONObject {
        authorize(lease)
        require(body.size <= if (method == "PUT") SyncLimits.ENVELOPE_BYTES else 16384)
        val bytes = body.copyOf()
        val timestamp = now()
        val id = UUID.randomUUID().toString()
        val canonical = RelayRequestAuth.canonical(
            method,
            target,
            keys.handle,
            1,
            timestamp,
            id,
            bytes,
            capability
        )
        val headers = mapOf(
            "X-Ritela-Device" to keys.handle,
            "X-Ritela-Time" to timestamp.toString(),
            "X-Ritela-Request" to id,
            "X-Ritela-Key-Version" to "1",
            "X-Ritela-Capability" to capability,
            "X-Ritela-Signature" to
                RelayRequestAuth.sign(PartnerCrypto.privateKey(keys.apiPrivate, "EC"), canonical),
            "Content-Type" to
                if (method == "PUT") "application/octet-stream" else "application/json",
            "Accept" to "application/json"
        )
        val response = exchange.execute(
            RelayHttpRequest(endpoint.url(target), method, headers, bytes)
        ) {
            authorize(lease)
        }
        authorize(lease)
        if (response.status !in 200..299) {
            val seconds = response.retryAfter?.toLongOrNull()?.takeIf { it >= 0 }
            // This Worker emits delta-seconds. An oversized value pauses rather than overflowing.
            val delay = seconds?.coerceAtMost(7 * 86400L)?.times(1000) ?: 0
            throw RelayHttpFailure(response.status, delay)
        }
        if (response.body.size > HttpsRelayExchange.RESPONSE_BYTES) throw RelayProtocolFailure()
        return try {
            val text = Charsets.UTF_8.newDecoder().decode(
                java.nio.ByteBuffer.wrap(response.body)
            ).toString()
            // Bound nesting before invoking the platform's recursive JSON reader.
            var depth = 0
            var quoted = false
            var escaped = false
            for (character in text) {
                if (quoted) {
                    if (escaped) {
                        escaped = false
                    } else {
                        when (character) {
                            '\\' -> escaped = true
                            '"' -> quoted = false
                        }
                    }
                } else {
                    when (character) {
                        '"' -> quoted = true

                        '{', '[' -> {
                            depth++
                            require(depth <= 32)
                        }

                        '}', ']' -> {
                            depth--
                            require(depth >= 0)
                        }
                    }
                }
            }
            require(depth == 0 && !quoted)
            val tokens = JSONTokener(text)
            val value = tokens.nextValue()
            require(value is JSONObject && tokens.nextClean() == '\u0000')
            value
        } catch (_: Exception) {
            throw RelayProtocolFailure()
        }
    }

    private fun strictInteger(value: Any): Long {
        require(value is Int || value is Long)
        return (value as Number).toLong()
    }
}
