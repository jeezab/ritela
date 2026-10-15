package app.ritela

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.PartnerCrypto
import app.ritela.data.sync.HttpRelayTransport
import app.ritela.data.sync.HttpsRelayExchange
import app.ritela.data.sync.RelayAccessDisabled
import app.ritela.data.sync.RelayEndpoint
import app.ritela.data.sync.RelayHttpExchange
import app.ritela.data.sync.RelayHttpFailure
import app.ritela.data.sync.RelayHttpRequest
import app.ritela.data.sync.RelayHttpResponse
import app.ritela.data.sync.RelayKeyVault
import app.ritela.data.sync.RelayKeys
import app.ritela.data.sync.RelayProtocolFailure
import app.ritela.data.sync.RelayRequestAuth
import app.ritela.data.sync.RelayUpload
import app.ritela.domain.sync.SyncLease
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URL
import java.security.Principal
import java.security.cert.Certificate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HttpRelayTransportTest {
    private val endpoint = RelayEndpoint("https://relay.example", setOf("relay.example"))
    private val route = "a".repeat(64)
    private val capability = "b".repeat(64)
    private val time = 1800000000000L
    private fun keys(): RelayKeys = RelayKeyVault(
        ApplicationProvider.getApplicationContext<Context>(),
        UUID.randomUUID().toString()
    ) { SecretKeySpec(PartnerCrypto.random(32), "AES") }.create(true)
    private fun lease() =
        SyncLease(UUID.randomUUID().toString(), "lease", "peer", "device", byteArrayOf(1, 2, 3))
    private fun client(
        exchange: RelayHttpExchange,
        keys: RelayKeys = keys(),
        allowed: suspend (SyncLease?) -> Boolean = { true },
        resolve: suspend (
            SyncLease
        ) -> RelayUpload = { RelayUpload(route, capability, time + 60000) }
    ) = HttpRelayTransport(endpoint, keys, allowed, resolve, exchange) { time }
    private fun response(json: JSONObject) = RelayHttpResponse(200, json.toString().toByteArray())
    private suspend fun failure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (e: Exception) {
            return e
        }
        error("Expected failure")
    }

    @Test fun exactOriginAndTargetCannotEscapeAllowlist() {
        listOf(
            "http://relay.example",
            "https://other.example",
            "https://relay.example:443",
            "https://user@relay.example",
            "https://relay.example/path",
            "https://relay.example?x=1",
            "https://relay.example#x"
        ).forEach {
            assertTrue(runCatching { RelayEndpoint(it, setOf("relay.example")) }.isFailure)
        }
        listOf("//other.example/v1/", "/v1/%2e%2e/path", "/v1/x\nsecret", "/v1/x#y").forEach {
            assertTrue(runCatching { endpoint.url(it) }.isFailure)
        }
        assertTrue(runCatching { RelayEndpoint("https://relay.example", emptySet()) }.isFailure)
    }

    @Test fun zeroExchangeBeforeConsentAndFreshPeerGateBeforeConnect() = runBlocking {
        var exchanges = 0
        var allowed = false
        val exchange = RelayHttpExchange { _, authorize ->
            exchanges++
            allowed = false // revoke/switch between signing and connect
            authorize()
            error("Must not connect")
        }
        val http = client(exchange, allowed = { allowed })
        assertTrue(failure { http.fetch(0) } is RelayAccessDisabled)
        assertEquals(0, exchanges)
        allowed = true
        assertTrue(failure { http.store(lease()) } is RelayAccessDisabled)
        assertEquals(1, exchanges)
    }

    @Test fun uploadBindsExactCiphertextCapabilityAndAuthorFence() = runBlocking {
        val keys = keys()
        val lease = lease()
        var request: RelayHttpRequest? = null
        val http = client(
            RelayHttpExchange { r, authorize ->
                authorize()
                request = r
                response(JSONObject().put("accepted", true))
            },
            keys,
            resolve = { RelayUpload(route, capability, time + 60000, 7, "c".repeat(64)) }
        )
        http.store(lease)
        val sent = requireNotNull(request)
        assertArrayEquals(lease.bytes, sent.body)
        assertEquals("PUT", sent.method)
        assertEquals(
            "/v1/routes/$route/packets/${lease.packetId}?class=AUTHOR" +
                "&expires=${time + 60000}&generation=7&grant=${"c".repeat(64)}",
            sent.url.file
        )
        val h = sent.headers
        val canonical = RelayRequestAuth.canonical(
            "PUT",
            sent.url.file,
            keys.handle,
            1,
            time,
            h.getValue("X-Ritela-Request"),
            lease.bytes,
            capability
        )
        assertTrue(
            PartnerCrypto.verify(
                PartnerCrypto.publicKey(keys.apiPublic, "EC"),
                canonical,
                PartnerCrypto.b64(
                    RelayRequestAuth.rawToDer(PartnerCrypto.bytes(h.getValue("X-Ritela-Signature")))
                )
            )
        )
        assertEquals(capability, h["X-Ritela-Capability"])
        assertFalse(sent.toString().contains(capability))
    }

    @Test fun retryStatusDoesNotLeakBodyAndHonorsBoundedRetryAfter() = runBlocking {
        listOf(408, 429, 500, 503, 401, 403, 409, 413, 302).forEach { status ->
            val http = client(
                RelayHttpExchange { _, authorize ->
                    authorize()
                    RelayHttpResponse(status, "SECRET_SENTINEL".toByteArray(), "120")
                }
            )
            val error = failure { http.store(lease()) } as RelayHttpFailure
            assertEquals(status, error.status)
            assertEquals(120000, error.retryAfterMillis)
            assertEquals(status in listOf(408, 429, 500, 503), error.retryable)
            assertFalse(error.toString().contains("SECRET_SENTINEL"))
            assertNull(error.cause)
        }
    }

    private fun packet(sequence: Any = 1, id: String = UUID.randomUUID().toString()) = JSONObject()
        .put("sequence", sequence).put("route", route).put("id", id)
        .put("ciphertext", PartnerCrypto.b64(byteArrayOf(7)))

    @Test fun fetchValidatesWholePageBeforeReturningCursorOrAcknowledging() = runBlocking {
        val valid = packet()
        val cases = listOf(
            JSONObject().put(
                "packets",
                JSONArray().put(valid).put(packet(2).put("ciphertext", "%%%"))
            ).put("cursor", "2"),
            JSONObject().put("packets", JSONArray().put(packet(1.5))).put("cursor", "1"),
            JSONObject().put("packets", JSONArray().put(valid).put(valid)).put("cursor", "1"),
            JSONObject().put("packets", JSONArray().put(valid)).put("cursor", "2"),
            JSONObject().put("packets", JSONArray().put(valid)).put("cursor", "01"),
            JSONObject().put("packets", JSONArray()).put("cursor", "1")
        )
        for (json in cases) {
            var calls = 0
            val http =
                client(
                    RelayHttpExchange { _, authorize ->
                        calls++
                        authorize()
                        response(json)
                    }
                )
            assertTrue(failure { http.fetch(0) } is RelayProtocolFailure)
            assertEquals(1, calls) // no ACK on malformed/partial page
        }
        val page = client(
            RelayHttpExchange { _, authorize ->
                authorize()
                response(JSONObject().put("packets", JSONArray().put(valid)).put("cursor", "1"))
            }
        ).fetch(0)
        assertEquals(1L, page.cursor)
        assertArrayEquals(byteArrayOf(7), page.packets.single().ciphertext)
    }

    @Test fun storageAckIsExplicitAndCancelledUploadIsNotStored() = runBlocking {
        var calls = 0
        val http = client(
            RelayHttpExchange { _, authorize ->
                authorize()
                calls++
                response(JSONObject().put("accepted", true).put("result", "CANCELLED"))
            }
        )
        assertTrue(failure { http.store(lease()) } is RelayProtocolFailure)
        assertEquals(1, calls)
        assertTrue(failure { http.acknowledge(emptyList()) } is IllegalArgumentException)
        assertEquals(1, calls)
    }

    @Test fun malformedJsonUtf8NestingAndTrailingBytesFailClosed() = runBlocking {
        val cases = listOf(
            "{} trailing".toByteArray(),
            byteArrayOf(0xc3.toByte(), 0x28),
            ("{\"x\":".repeat(33) + "0" + "}".repeat(33)).toByteArray(),
            ByteArray(512 * 1024 + 1),
            "[]".toByteArray(),
            "{".toByteArray()
        )
        for (bytes in cases) {
            val http = client(
                RelayHttpExchange { _, authorize ->
                    authorize()
                    RelayHttpResponse(200, bytes)
                }
            )
            assertTrue(failure { http.request("GET", "/v1/devices") } is RelayProtocolFailure)
        }
    }

    private open class Connection(url: URL) : HttpsURLConnection(url) {
        var disconnected = false
        var connectedCalls = 0
        var status = 200
        var bytes = "{}".toByteArray()
        var encoding: String? = null
        override fun connect() {
            connectedCalls++
        }
        override fun disconnect() {
            disconnected = true
        }
        override fun usingProxy() = false
        override fun getCipherSuite() = "TLS_TEST"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getPeerPrincipal(): Principal? = null
        override fun getLocalPrincipal(): Principal? = null
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getErrorStream() = ByteArrayInputStream(bytes)
        override fun getHeaderField(name: String?) = if (name ==
            "Content-Encoding"
        ) {
            encoding
        } else {
            null
        }
    }

    @Test fun platformRejectsOversizedAndEncodedResponsesAndSanitizesIO() = runBlocking {
        for (mode in 0..2) {
            val connection = Connection(endpoint.url("/v1/devices")).apply {
                if (mode == 0) bytes = ByteArray(512 * 1024 + 1)
                if (mode == 1) encoding = "gzip"
                if (mode == 2) status = 503
            }
            val exchange = HttpsRelayExchange { connection }
            val request = RelayHttpRequest(connection.url, "GET", emptyMap(), byteArrayOf())
            if (mode <
                2
            ) {
                assertTrue(failure { exchange.execute(request) {} } is RelayProtocolFailure)
            } else {
                assertEquals(503, exchange.execute(request) {}.status)
            }
            assertTrue(connection.disconnected)
            assertFalse(connection.instanceFollowRedirects)
            assertEquals(10000, connection.connectTimeout)
            assertEquals(15000, connection.readTimeout)
        }
        val request =
            RelayHttpRequest(endpoint.url("/v1/devices"), "GET", emptyMap(), byteArrayOf())
        val error =
            failure {
                HttpsRelayExchange { throw IOException("SECRET_SENTINEL") }.execute(request) {}
            }
        assertEquals("RELAY_IO", error.message)
        // Coroutine stack recovery may attach another sanitized exception as its cause.
        generateSequence(error) { it.cause }.forEach {
            assertFalse(it.toString().contains("SECRET_SENTINEL"))
        }
    }

    @Test fun cancellationDisconnectsBlockedConnectionAndNoOpenBeforeGate() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val connection = object : Connection(endpoint.url("/v1/devices")) {
            override fun connect() {
                entered.countDown()
                check(released.await(10, TimeUnit.SECONDS))
            }
            override fun disconnect() {
                super.disconnect()
                released.countDown()
            }
        }
        var opens = 0
        val exchange = HttpsRelayExchange {
            opens++
            connection
        }
        val request = RelayHttpRequest(connection.url, "GET", emptyMap(), byteArrayOf())
        assertTrue(
            failure {
                exchange.execute(request) { throw RelayAccessDisabled() }
            } is RelayAccessDisabled
        )
        assertEquals(0, opens)
        val pending = async(kotlinx.coroutines.Dispatchers.Default) { exchange.execute(request) {} }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        pending.cancel()
        withTimeout(5000) { pending.join() }
        assertTrue(connection.disconnected)
        assertEquals(0L, released.count)
    }
}
