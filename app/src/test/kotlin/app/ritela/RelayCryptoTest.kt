package app.ritela

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.LocalPartnerKeys
import app.ritela.data.PartnerCrypto
import app.ritela.data.sync.PartnerEnvelopeCrypto
import app.ritela.data.sync.RelayKeyVault
import app.ritela.data.sync.RelayRequestAuth
import app.ritela.data.sync.RelayTransportCertificate
import app.ritela.domain.PartnerDevice
import app.ritela.domain.PartnerIdentity
import java.io.File
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RelayCryptoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun peer(local: LocalPartnerKeys, remote: LocalPartnerKeys) = PartnerIdentity(
        remote.identityId,
        remote.identityPublic,
        "Synthetic",
        listOf(
            PartnerDevice(
                remote.deviceId,
                remote.devicePublic,
                remote.certificate(),
                local.nonce,
                remote.nonce
            )
        )
    )

    @Test fun hpkeRoundTripAcrossSerializedKeysAuthenticatesSenderAddressAndContext() {
        val a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        val keys = PartnerEnvelopeCrypto.newKeyset()
        val publicKey = PartnerEnvelopeCrypto.publicKey(keys)
        val route = "a".repeat(64)
        val id = UUID.randomUUID().toString()
        val content = JSONObject().put("synthetic", "private")
        val ciphertext = PartnerEnvelopeCrypto.seal(
            a,
            b.identityId,
            b.deviceId,
            b.nonce,
            route,
            id,
            publicKey,
            content
        )
        assertEquals(
            "private",
            PartnerEnvelopeCrypto.open(keys, b, peer(b, a), route, id, ciphertext)
                .getJSONObject("content").getString("synthetic")
        )
        assertFalse(ciphertext.toString(Charsets.UTF_8).contains(a.identityId))
        val escaped = "\"".repeat(25000)
        val escapedCiphertext = PartnerEnvelopeCrypto.seal(
            a,
            b.identityId,
            b.deviceId,
            b.nonce,
            route,
            id,
            publicKey,
            JSONObject().put("text", escaped)
        )
        assertEquals(
            escaped,
            PartnerEnvelopeCrypto.open(keys, b, peer(b, a), route, id, escapedCiphertext)
                .getJSONObject("content").getString("text")
        )
        assertTrue(
            runCatching {
                PartnerEnvelopeCrypto.open(keys, b, peer(b, a), "b".repeat(64), id, ciphertext)
            }.isFailure
        )
        assertTrue(
            runCatching {
                PartnerEnvelopeCrypto.open(
                    keys,
                    b,
                    peer(b, a),
                    route,
                    UUID.randomUUID().toString(),
                    ciphertext
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                PartnerEnvelopeCrypto.open(
                    PartnerEnvelopeCrypto.newKeyset(),
                    b,
                    peer(b, a),
                    route,
                    id,
                    ciphertext
                )
            }.isFailure
        )
        val corrupted = ciphertext.copyOf().apply {
            this[lastIndex] =
                (this[lastIndex].toInt() xor 1).toByte()
        }
        assertTrue(
            runCatching {
                PartnerEnvelopeCrypto.open(keys, b, peer(b, a), route, id, corrupted)
            }.isFailure
        )
        val attacker = LocalPartnerKeys.create()
        assertTrue(
            runCatching {
                PartnerEnvelopeCrypto.open(keys, b, peer(b, attacker), route, id, ciphertext)
            }.isFailure
        )
        val wrongLink = PartnerEnvelopeCrypto.seal(
            a,
            b.identityId,
            b.deviceId,
            a.nonce,
            route,
            id,
            publicKey,
            content
        )
        assertTrue(
            runCatching {
                PartnerEnvelopeCrypto.open(keys, b, peer(b, a), route, id, wrongLink)
            }.isFailure
        )
        assertTrue(
            runCatching {
                PartnerEnvelopeCrypto.open(keys, b, peer(b, a), route, id, ByteArray(262145))
            }.isFailure
        )
        assertTrue(
            runCatching {
                PartnerEnvelopeCrypto.seal(
                    a,
                    b.identityId,
                    b.deviceId,
                    b.nonce,
                    route,
                    id,
                    publicKey,
                    JSONObject().put("oversize", "x".repeat(98304))
                )
            }.isFailure
        )
    }

    @Test fun vaultRequiresExplicitConsentAndKeepsSeparateStableWrappedKeys() {
        val profile = UUID.randomUUID().toString()
        val key = SecretKeySpec(PartnerCrypto.random(32), "AES")
        var calls = 0
        val vault = RelayKeyVault(context, profile) {
            calls++
            key
        }
        assertNull(vault.load())
        assertTrue(runCatching { vault.create(false) }.isFailure)
        assertEquals(0, calls)
        val created = vault.create(true)
        val bytes = File(context.filesDir, "relay-$profile.keys").readBytes()
        assertFalse(bytes.toString(Charsets.UTF_8).contains(created.apiPrivate))
        val loaded = RelayKeyVault(context, profile) { key }.load()!!
        assertEquals(created.handle, loaded.handle)
        assertEquals(created.apiPublic, loaded.apiPublic)
        assertEquals(
            PartnerEnvelopeCrypto.publicKey(created.hpke),
            PartnerEnvelopeCrypto.publicKey(loaded.hpke)
        )
        assertArrayEquals(bytes, File(context.filesDir, "relay-$profile.keys").readBytes())
        assertEquals(created.handle, vault.create(true).handle)
        val wrong =
            RelayKeyVault(context, profile) { SecretKeySpec(PartnerCrypto.random(32), "AES") }
        assertTrue(runCatching { wrong.load() }.isFailure)
        assertArrayEquals(bytes, File(context.filesDir, "relay-$profile.keys").readBytes())
        assertNotEquals(
            created.handle,
            RelayKeyVault(context, UUID.randomUUID().toString()) {
                key
            }.create(true).handle
        )
    }

    @Test fun certificateBindsExistingDeviceNonceAndBothTransportKeys() {
        val a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        val relay = RelayKeyVault(context, UUID.randomUUID().toString()) {
            SecretKeySpec(PartnerCrypto.random(32), "AES")
        }.create(true)
        val cert = RelayTransportCertificate.issue(a, relay)
        val verified = RelayTransportCertificate.verify(peer(b, a), a.deviceId, cert)
        assertEquals(relay.handle, verified.getString("handle"))
        assertEquals(relay.apiPublic, verified.getString("apiKey"))
        val forged = JSONObject(cert).put(
            "body",
            JSONObject(JSONObject(cert).getString("body"))
                .put("handle", UUID.randomUUID().toString()).toString()
        ).toString()
        assertTrue(
            runCatching {
                RelayTransportCertificate.verify(peer(b, a), a.deviceId, forged)
            }.isFailure
        )
        val oldPair = peer(b, a).copy(
            devices = peer(b, a).devices.map {
                it.copy(remoteNonce = UUID.randomUUID().toString())
            }
        )
        assertTrue(
            runCatching {
                RelayTransportCertificate.verify(oldPair, a.deviceId, cert)
            }.isFailure
        )
    }

    @Test fun jcaP1363ConversionAndCanonicalRequestMatchWorkerVectors() {
        val pair = PartnerCrypto.ec()
        val message = RelayRequestAuth.canonical(
            "GET",
            "/v1/devices/00000000-0000-4000-8000-000000000001/packets?cursor=0&limit=32",
            "00000000-0000-4000-8000-000000000001",
            1,
            1700000000000,
            "00000000-0000-4000-8000-000000000002",
            byteArrayOf()
        )
        val signature = RelayRequestAuth.sign(pair.private, message)
        val raw = PartnerCrypto.bytes(signature)
        assertEquals(64, raw.size)
        assertArrayEquals(raw, RelayRequestAuth.derToRaw(RelayRequestAuth.rawToDer(raw)))
        assertTrue(
            PartnerCrypto.verify(
                pair.public,
                message,
                PartnerCrypto.b64(RelayRequestAuth.rawToDer(raw))
            )
        )
        assertTrue(runCatching { RelayRequestAuth.derToRaw(byteArrayOf(0x30, 0)) }.isFailure)
        val report = File("build/reports/sync-vectors").apply { mkdirs() }
        File(report, "kotlin-api.json").writeText(
            JSONObject().put("source", "JCA")
                .put("publicKey", PartnerCrypto.b64(pair.public.encoded)).put("message", message)
                .put("signature", signature).toString()
        )
        val fixtures = File("../relay/test-vectors/api-signatures.json")
        require(fixtures.exists())
        run {
            val vectors = org.json.JSONArray(fixtures.readText())
            repeat(vectors.length()) {
                val v = vectors.getJSONObject(it)
                assertTrue(
                    PartnerCrypto.verify(
                        PartnerCrypto.publicKey(v.getString("publicKey"), "EC"),
                        v.getString("message"),
                        PartnerCrypto.b64(
                            RelayRequestAuth.rawToDer(PartnerCrypto.bytes(v.getString("signature")))
                        )
                    )
                )
            }
        }
    }
}
