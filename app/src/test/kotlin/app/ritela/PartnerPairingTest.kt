package app.ritela

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.LocalPartnerKeys
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerCrypto
import app.ritela.data.PartnerRepository
import app.ritela.data.RitelaDatabase
import app.ritela.data.pairingContext
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Existing exchange tests use authenticated peer consent, not an artificial trusted flag. */
internal suspend fun confirmedPair(
    repository: PartnerRepository,
    peer: LocalPartnerKeys,
    name: String
) {
    val invitation = repository.inspectInvitation(peer.invitation(name))
    val context = pairingContext(repository.identity(), invitation)
    repository.pair(repository.inspectInvitation(peer.invitation(name, context)))
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PartnerPairingTest {
    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        RitelaDatabase::class.java
    ).build()

    @Test fun mutualConsentGatesExchangeAndKnownDevicesNeedNoQr() = runBlocking {
        val a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        val da = database()
        val db = database()
        try {
            val ra = PartnerRepository(da, { a }, {})
            val rb = PartnerRepository(db, { b }, {})
            val ia = ra.inspectInvitation(b.invitation("B"))
            val ib = rb.inspectInvitation(a.invitation("A"))
            assertEquals(ra.verificationCode(ia), rb.verificationCode(ib))
            assertTrue(ra.verificationCode(ia).matches(Regex("[0-9]{4} · [0-9]{4}")))
            assertTrue(ra.snapshot().contacts.isEmpty())
            ra.pair(ia)
            val scope = ShareScope(setOf(ShareCategory.PERIODS))
            assertTrue(runCatching { ra.grant(b.identityId, scope) }.isFailure)
            assertTrue(
                runCatching {
                    ra.prepareData(b.identityId, b.deviceId, scope)
                }.isFailure
            )
            val confirmedA = ra.invitation("A", ia)
            val receivedA = rb.inspectInvitation(confirmedA)
            // A signed peer confirmation cannot silently substitute B's explicit consent.
            assertFalse(rb.acceptKnown(receivedA))
            rb.pair(receivedA)
            assertTrue(rb.snapshot().identities.single().devices.single().mutualConfirmed)
            assertTrue(ra.acceptKnown(ra.inspectInvitation(rb.invitation("B", receivedA))))
            ra.grant(b.identityId, scope)
            repeat(2) {
                val packet = ra.prepareData(b.identityId, b.deviceId, scope)
                rb.receive(packet.envelope.toByteArray())
            }
            assertTrue(ra.acceptKnown(ra.inspectInvitation(b.invitation("Renamed"))))
            assertEquals(scope, ra.snapshot().identities.single().grants)
            assertEquals(1, ra.snapshot().contacts.size)
        } finally {
            da.close()
            db.close()
        }
    }

    @Test fun fullContextRejectsWrongReceiptEvenWhenCorrectlySignedAndTamperedQr() = runBlocking {
        val a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        val db = database()
        try {
            val r = PartnerRepository(db, { a }, {})
            val valid = r.inspectInvitation(b.invitation("B"))
            r.pair(valid)
            val wrong = r.inspectInvitation(b.invitation("B", "0".repeat(64)))
            assertFalse(r.acceptKnown(wrong))
            assertTrue(runCatching { r.pair(wrong) }.isFailure)
            val altered = JSONObject(b.invitation("B"))
            altered.put(
                "body",
                JSONObject(
                    altered.getString("body")
                ).put("nonce", java.util.UUID.randomUUID().toString()).toString()
            )
            assertTrue(runCatching { r.inspectInvitation(altered.toString()) }.isFailure)
            assertFalse(r.snapshot().identities.single().devices.single().mutualConfirmed)
        } finally {
            db.close()
        }
    }

    @Test fun deviceOrNonceChangesNeedFreshConsentAndSameNameNeverMakesTrusted() = runBlocking {
        var a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        val db = database()
        try {
            val r = PartnerRepository(db, { a }, { a = it })
            confirmedPair(r, b, "Same")
            val scope = ShareScope(setOf(ShareCategory.PERIODS))
            r.grant(b.identityId, scope)
            val old = r.inspectInvitation(b.invitation("Same"))
            val changedNonce = r.inspectInvitation(
                b.copy(nonce = java.util.UUID.randomUUID().toString()).invitation("Same")
            )
            assertNotEquals(r.verificationCode(old), r.verificationCode(changedNonce))
            assertFalse(r.acceptKnown(changedNonce))
            val next = LocalPartnerKeys.create(
                java.security.KeyPair(
                    PartnerCrypto.publicKey(b.identityPublic),
                    PartnerCrypto.privateKey(b.identityPrivate)
                )
            )
            val invitation = r.inspectInvitation(next.invitation("Same"))
            assertFalse(r.acceptKnown(invitation))
            r.pair(invitation)
            assertEquals(1, r.snapshot().contacts.size)
            assertEquals(2, r.snapshot().identities.single().devices.size)
            assertTrue(r.snapshot().identities.single().grants.categories.isEmpty())
            assertTrue(runCatching { r.prepareData(b.identityId, next.deviceId, scope) }.isFailure)
            val oldReceipt = b.invitation("Same", pairingContext(a, old))
            r.enableDevice(b.identityId, b.deviceId, false)
            assertTrue(runCatching { r.enableDevice(b.identityId, b.deviceId, true) }.isFailure)
            assertFalse(r.acceptKnown(old))
            assertTrue(runCatching { r.pair(r.inspectInvitation(oldReceipt)) }.isFailure)
            val stranger = LocalPartnerKeys.create()
            assertFalse(r.acceptKnown(r.inspectInvitation(stranger.invitation("Same"))))
        } finally {
            db.close()
        }
    }

    @Test fun pendingAndLegacyTrustSurviveCodecRoundTrip() = runBlocking {
        val a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        val db = database()
        try {
            val r = PartnerRepository(db, { a }, {})
            r.pair(r.inspectInvitation(b.invitation("B")))
            val serialized = PartnerCodec.directory(r.snapshot())
            assertFalse(
                PartnerCodec.directory(
                    serialized
                ).identities.single().devices.single().mutualConfirmed
            )
            val old = JSONObject(serialized)
            val device = old.getJSONArray(
                "identities"
            ).getJSONObject(0).getJSONArray("devices").getJSONObject(0)
            device.remove("verification")
            device.remove("mutual")
            val decoded = PartnerCodec.directory(
                old.toString()
            ).identities.single().devices.single()
            assertTrue(decoded.mutualConfirmed)
            assertNull(decoded.verificationContext)
            db.partner().save(app.ritela.data.PartnerDirectoryRow(json = old.toString()))
            assertTrue(r.acceptKnown(r.inspectInvitation(b.invitation("B", version = 1))))
            val scope = ShareScope(setOf(ShareCategory.PERIODS))
            r.grant(b.identityId, scope)
            assertEquals("DATA", r.prepareData(b.identityId, b.deviceId, scope).kind)
            assertTrue(
                runCatching {
                    r.pair(r.inspectInvitation(b.invitation("B", version = 1)))
                }.isFailure
            )
        } finally {
            db.close()
        }
    }
}
