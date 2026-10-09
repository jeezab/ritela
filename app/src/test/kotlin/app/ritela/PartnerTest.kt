package app.ritela

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.LocalPartnerKeys
import app.ritela.data.PartnerCrypto
import app.ritela.data.PartnerRepository
import app.ritela.data.RitelaDatabase
import app.ritela.domain.Doodle
import app.ritela.domain.DoodleOpening
import app.ritela.domain.DoodlePoint
import app.ritela.domain.DoodleStroke
import app.ritela.domain.PartnerContact
import app.ritela.domain.PartnerDirectory
import app.ritela.domain.PartnerIdentity
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import kotlinx.coroutines.runBlocking
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
class PartnerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun database() =
        Room.inMemoryDatabaseBuilder(context, RitelaDatabase::class.java).build()
    private fun repo(database: RitelaDatabase, keys: LocalPartnerKeys): PartnerRepository =
        PartnerRepository(database, { keys }, {})

    @Test fun repeatedQrAndNewDeviceNeedExplicitConfirmation() = runBlocking {
        val a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        val db = database()
        try {
            val repository = repo(db, a)
            val invite = repository.inspectInvitation(b.qr("Close"))
            assertTrue(repository.snapshot().contacts.isEmpty())
            repository.pair(invite)
            repository.pair(invite)
            assertEquals(1, repository.snapshot().contacts.size)
            assertTrue(repository.snapshot().identities.single().grants.categories.isEmpty())
            val changed = LocalPartnerKeys.create(
                java.security.KeyPair(
                    PartnerCrypto.publicKey(b.identityPublic),
                    PartnerCrypto.privateKey(b.identityPrivate)
                )
            )
            val next = repository.inspectInvitation(changed.qr("Close"))
            assertEquals(1, repository.snapshot().identities.single().devices.size)
            repository.pair(next)
            assertEquals(2, repository.snapshot().identities.single().devices.size)
        } finally {
            db.close()
        }
    }

    @Test fun distinctKeysSameNameMergeAndSplitWithoutCombiningAccess() {
        val one =
            PartnerIdentity(
                "one",
                "key1",
                "Same",
                emptyList(),
                ShareScope(setOf(ShareCategory.PERIODS))
            )
        val two = PartnerIdentity("two", "key2", "Same", emptyList())
        val a = PartnerContact(name = "Same", identities = setOf("one"))
        val b = PartnerContact(name = "Same", identities = setOf("two"))
        val initial = PartnerDirectory(listOf(one, two), listOf(a, b))
        val merged = initial.merge(a.id, b.id, "Close", false)
        assertEquals(1, merged.contacts.size)
        assertEquals(initial.identities, merged.identities)
        assertEquals(2, merged.split(merged.contacts.single().id).contacts.size)
        val group = initial.merge(a.id, b.id, "Together", true)
        assertEquals(3, group.contacts.size)
        assertEquals(initial.identities, group.identities)
        assertEquals(2, group.remove(group.contacts.last().id).contacts.size)
    }

    @Test fun encryptedExchangeDeduplicatesAndOnlyAuthenticatedAckConfirmsDelivery() = runBlocking {
        val a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        val dbA = database()
        val dbB = database()
        try {
            val ra = repo(dbA, a)
            val rb = repo(dbB, b)
            ra.pair(ra.inspectInvitation(b.qr("B")))
            rb.pair(rb.inspectInvitation(a.qr("A")))
            assertTrue(
                runCatching {
                    ra.prepareData(
                        b.identityId,
                        b.deviceId,
                        ShareScope(setOf(ShareCategory.PERIODS))
                    )
                }.isFailure
            )
            ra.grant(b.identityId, ShareScope(setOf(ShareCategory.PERIODS)))
            val packet = ra.prepareData(
                b.identityId,
                b.deviceId,
                ShareScope(setOf(ShareCategory.PERIODS))
            )
            assertFalse(packet.delivered)
            assertFalse(packet.envelope.contains("periods"))
            val received = rb.receive(packet.envelope.toByteArray())
            assertFalse(received.duplicate)
            assertTrue(rb.receive(packet.envelope.toByteArray()).duplicate)
            assertEquals(packet.envelope, ra.retry(packet.id).envelope)
            val ack = rb.acknowledgement(received.message)
            ra.receive(ack.envelope.toByteArray())
            assertTrue(ra.retry(packet.id).delivered)
            assertTrue(runCatching { ra.receive(packet.envelope.toByteArray()) }.isFailure)
            rb.enable(a.identityId, false)
            assertTrue(runCatching { rb.receive(packet.envelope.toByteArray()) }.isFailure)
        } finally {
            dbA.close()
            dbB.close()
        }
    }

    @Test fun removedIdentityDoesNotRecoverGrantsAndGroupRetainsOriginals() = runBlocking {
        val db = database()
        val a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        try {
            val r = repo(db, a)
            val invitation = r.inspectInvitation(b.qr("B"))
            r.pair(invitation)
            r.grant(b.identityId, ShareScope(setOf(ShareCategory.DIARY)))
            r.remove(r.snapshot().contacts.single().id)
            r.pair(invitation)
            assertTrue(r.snapshot().identities.single().grants.categories.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test fun delayedDoodleCannotBeOpenedEarlyAndImportsAreIdempotent() = runBlocking {
        var time = 1000L
        val dbA = database()
        val dbB = database()
        val a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        try {
            val ra = repo(dbA, a)
            val rb = PartnerRepository(dbB, { b }, {}, { time })
            ra.pair(ra.inspectInvitation(b.qr("B")))
            rb.pair(rb.inspectInvitation(a.qr("A")))
            val doodle =
                Doodle(
                    listOf(
                        DoodleStroke(
                            listOf(DoodlePoint(.1f, .2f), DoodlePoint(.5f, .8f)),
                            0xffee5577,
                            .01f
                        )
                    ),
                    "synthetic",
                    DoodleOpening.AFTER_DAYS,
                    delayDays = 2
                )
            val message = ra.prepareDoodle(b.identityId, b.deviceId, doodle)
            rb.receive(message.envelope.toByteArray())
            assertTrue(runCatching { rb.openDoodle(message.id) }.isFailure)
            time += 2 * 86400000L
            val receipt = requireNotNull(rb.openDoodle(message.id))
            ra.receive(receipt.envelope.toByteArray())
            assertTrue(ra.retry(message.id).opened)
            assertNull(rb.openDoodle(message.id))
            assertTrue(rb.receive(message.envelope.toByteArray()).duplicate)
        } finally {
            dbA.close()
            dbB.close()
        }
    }
}
