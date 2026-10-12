package app.ritela

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.LocalPartnerKeys
import app.ritela.data.PartnerBluetooth
import app.ritela.data.PartnerInvitationFile
import app.ritela.data.PartnerRepository
import app.ritela.data.RitelaDatabase
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
class PartnerInvitationsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun database() =
        Room.inMemoryDatabaseBuilder(context, RitelaDatabase::class.java).build()

    @Test fun fileInvitationsRequireConsentAndPreserveEncryptedExchange() = runBlocking {
        val a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        val da = database()
        val db = database()
        try {
            val ra = PartnerRepository(da, { a }, {})
            val rb = PartnerRepository(db, { b }, {})
            val fileA = PartnerInvitationFile.encode(ra.invitation("A"))
            val fileB = PartnerInvitationFile.encode(rb.invitation("B"))
            assertFalse(fileA.toString(Charsets.UTF_8).contains(a.identityPrivate))
            assertFalse(fileA.toString(Charsets.UTF_8).contains(a.devicePrivate))
            val ia = ra.inspectInvitation(requireNotNull(PartnerInvitationFile.decode(fileB)))
            val ib = rb.inspectInvitation(requireNotNull(PartnerInvitationFile.decode(fileA)))
            assertEquals(ra.verificationCode(ia), rb.verificationCode(ib))
            assertTrue(ra.snapshot().contacts.isEmpty())
            assertTrue(rb.snapshot().contacts.isEmpty())
            assertFalse(ra.acceptKnown(ia))
            ra.pair(ia)
            val scope = ShareScope(setOf(ShareCategory.PERIODS))
            assertTrue(runCatching { ra.grant(b.identityId, scope) }.isFailure)
            rb.pair(ib)
            val updatedA = PartnerInvitationFile.encode(ra.invitation("A", ia))
            val updatedB = PartnerInvitationFile.encode(rb.invitation("B", ib))
            assertTrue(
                ra.acceptKnown(
                    ra.inspectInvitation(requireNotNull(PartnerInvitationFile.decode(updatedB)))
                )
            )
            assertTrue(
                rb.acceptKnown(
                    rb.inspectInvitation(requireNotNull(PartnerInvitationFile.decode(updatedA)))
                )
            )
            // Reopening the same invitation does not create duplicate contacts or widen grants.
            assertTrue(ra.acceptKnown(ia))
            assertEquals(1, ra.snapshot().contacts.size)
            assertTrue(ra.snapshot().identities.single().grants.categories.isEmpty())
            ra.grant(b.identityId, scope)
            val packet = ra.prepareData(b.identityId, b.deviceId, scope)
            assertNull(PartnerInvitationFile.decode(packet.envelope.toByteArray()))
            assertEquals("DATA", rb.receive(packet.envelope.toByteArray()).message.kind)
        } finally {
            da.close()
            db.close()
        }
    }

    @Test fun alteredSelfAndOversizedInvitationsAreRejected() = runBlocking {
        val own = LocalPartnerKeys.create()
        val other = LocalPartnerKeys.create()
        val db = database()
        try {
            val repository = PartnerRepository(db, { own }, {})
            val altered = JSONObject(other.invitation("B"))
            val body = JSONObject(altered.getString("body")).put("name", "Changed")
            altered.put("body", body.toString())
            assertTrue(
                runCatching {
                    repository.inspectInvitation(
                        requireNotNull(
                            PartnerInvitationFile.decode(altered.toString().toByteArray())
                        )
                    )
                }.isFailure
            )
            assertTrue(runCatching { repository.inspectInvitation(own.invitation("A")) }.isFailure)
            assertTrue(runCatching { PartnerInvitationFile.encode(" ".repeat(12001)) }.isFailure)
            assertTrue(repository.snapshot().contacts.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test fun bluetoothInvitationFramesUseTheirOwnSmallBound() {
        val bytes = PartnerInvitationFile.encode(LocalPartnerKeys.create().invitation("A"))
        val output = ByteArrayOutputStream()
        PartnerBluetooth.writeFrame(output, bytes)
        val received = PartnerBluetooth.readFrame(
            ByteArrayInputStream(output.toByteArray()),
            PartnerInvitationFile.MAX_BYTES
        )
        assertEquals(PartnerInvitationFile.decode(bytes), PartnerInvitationFile.decode(received))
        val tooLarge = ByteArrayOutputStream().also {
            DataOutputStream(it).writeInt(PartnerInvitationFile.MAX_BYTES + 1)
        }
        assertTrue(
            runCatching {
                PartnerBluetooth.readFrame(
                    ByteArrayInputStream(tooLarge.toByteArray()),
                    PartnerInvitationFile.MAX_BYTES
                )
            }.isFailure
        )
    }
}
