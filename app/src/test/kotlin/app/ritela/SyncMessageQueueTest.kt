package app.ritela

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.LocalPartnerKeys
import app.ritela.data.PartnerMessage
import app.ritela.data.PartnerRepository
import app.ritela.data.RitelaDatabase
import app.ritela.data.sync.SyncMessageOffer
import app.ritela.data.sync.SyncMessageQueue
import app.ritela.domain.Doodle
import app.ritela.domain.DoodleOpening
import app.ritela.domain.DoodlePoint
import app.ritela.domain.DoodleStroke
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncMessageQueueTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = ShareScope(setOf(ShareCategory.PERIODS))
    private inner class Pair : AutoCloseable {
        val nameA = "send-a-${UUID.randomUUID()}.db"
        val nameB = "send-b-${UUID.randomUUID()}.db"
        var a: RitelaDatabase = syncTestDatabase(context, nameA)
        var b = syncTestDatabase(context, nameB)
        val ka = LocalPartnerKeys.create()
        val kb = LocalPartnerKeys.create()
        var time = 1000L
        var ra = PartnerRepository(a, { ka }, {}, { time })
        var rb = PartnerRepository(b, { kb }, {}, { time })
        suspend fun setup() {
            confirmedPair(ra, kb, "B")
            confirmedPair(rb, ka, "A")
        }
        suspend fun allow(db: RitelaDatabase, identity: String, device: String) {
            db.syncState().ensureDefaults()
            db.openHelper.writableDatabase.execSQL(
                "UPDATE sync_profile_state SET internetEnabled=1"
            )
            db.openHelper.writableDatabase.execSQL(
                "INSERT OR REPLACE INTO sync_links " +
                    "(peerIdentity,peerDevice,scopeEpoch,protocolVersion) VALUES (?,?,1,2)",
                arrayOf(identity, device)
            )
        }
        suspend fun send(): PartnerMessage =
            ra.confirmData(kb.identityId, kb.deviceId, scope, "Synthetic")
        override fun close() {
            a.close()
            b.close()
            context.deleteDatabase(nameA)
            context.deleteDatabase(nameB)
        }
    }
    private fun doodle(points: Int = 2, days: Int = 0) = Doodle(
        listOf(DoodleStroke(List(points) { DoodlePoint(.2f, .3f) }, 0xffee5577, .01f)),
        "Synthetic",
        if (days == 0) DoodleOpening.IMMEDIATE else DoodleOpening.AFTER_DAYS,
        delayDays = days
    )

    @Test fun offAndPreviewNeverQueueAndOptInDoesNotBackfillHistory() = runBlocking {
        Pair().use { p ->
            p.setup()
            p.ra.grant(p.kb.identityId, scope)
            p.ra.prepareData(p.kb.identityId, p.kb.deviceId, scope)
            p.send()
            p.ra.prepareDoodle(p.kb.identityId, p.kb.deviceId, doodle())
            assertTrue(p.a.syncMessageIntents().all().isEmpty())
            p.allow(p.a, p.kb.identityId, p.kb.deviceId)
            assertTrue(SyncMessageQueue(p.a).pending().isEmpty())
            p.ra.prepareData(p.kb.identityId, p.kb.deviceId, scope)
            assertTrue(p.a.syncMessageIntents().all().isEmpty())
        }
    }

    @Test fun confirmedIntentSurvivesRestartWithoutAutoAuthorSequence() = runBlocking {
        Pair().use { p ->
            p.setup()
            p.allow(p.a, p.kb.identityId, p.kb.deviceId)
            val message = p.send()
            val queue = SyncMessageQueue(p.a)
            assertEquals(listOf(message), queue.pending())
            assertEquals(SyncMessageOffer.EXISTS, queue.offer(message))
            assertTrue(runCatching { queue.offer(message.copy(body = "{}")) }.isFailure)
            assertEquals(1, p.a.syncMessageIntents().count())
            assertEquals(0, p.a.syncState().links().single().automaticEnabled)
            assertEquals("UNINITIALIZED", p.a.syncState().authorities().single().state)
            assertTrue(p.a.syncQueue().packets().isEmpty())
            p.a.close()
            p.a = syncTestDatabase(context, p.nameA)
            p.ra = PartnerRepository(p.a, { p.ka }, {}, { p.time })
            assertEquals(listOf(message), SyncMessageQueue(p.a).pending())
            assertEquals("PENDING_WRAP", p.a.syncMessageIntents().get(message.id)!!.state)
            assertFalse(p.ra.retry(message.id).delivered)
        }
    }

    @Test fun failedIntentInsertRollsBackMessageAndRememberedGrants() = runBlocking {
        Pair().use { p ->
            p.setup()
            p.allow(p.a, p.kb.identityId, p.kb.deviceId)
            val before = p.a.partner().history(p.kb.identityId)
            val grants = p.ra.snapshot().identities.single().grants
            p.a.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER synthetic_intent_failure BEFORE INSERT ON sync_message_intents " +
                    "BEGIN SELECT RAISE(ABORT,'Synthetic'); END"
            )
            assertTrue(runCatching { p.send() }.isFailure)
            assertEquals(before, p.a.partner().history(p.kb.identityId))
            assertEquals(grants, p.ra.snapshot().identities.single().grants)
            assertTrue(p.a.syncMessageIntents().all().isEmpty())
            p.a.openHelper.writableDatabase.execSQL("DROP TRIGGER synthetic_intent_failure")
            p.send()
            assertEquals(1, p.a.syncMessageIntents().count())
        }
    }

    @Test fun revokeAndGlobalOffCancelWithoutResurrectionOrDeletingManualMessage() = runBlocking {
        Pair().use { p ->
            p.setup()
            p.allow(p.a, p.kb.identityId, p.kb.deviceId)
            val first = p.send()
            p.ra.enable(p.kb.identityId, false)
            assertEquals("CANCELLED", p.a.syncMessageIntents().get(first.id)!!.state)
            p.ra.enable(p.kb.identityId, true)
            assertEquals(SyncMessageOffer.TERMINAL, SyncMessageQueue(p.a).offer(first))
            val second = p.send()
            p.a.openHelper.writableDatabase.execSQL(
                "UPDATE sync_profile_state SET internetEnabled=0"
            )
            assertTrue(SyncMessageQueue(p.a).pending().isEmpty())
            assertEquals("CANCELLED", p.a.syncMessageIntents().get(second.id)!!.state)
            assertEquals(second, p.ra.retry(second.id))
        }
    }

    @Test fun epochScopeOrPairingChangeRejectsPendingIntent() = runBlocking {
        Pair().use { p ->
            p.setup()
            p.allow(p.a, p.kb.identityId, p.kb.deviceId)
            val first = p.send()
            p.a.openHelper.writableDatabase.execSQL("UPDATE sync_links SET scopeEpoch=2")
            assertTrue(SyncMessageQueue(p.a).pending().isEmpty())
            assertEquals("CANCELLED", p.a.syncMessageIntents().get(first.id)!!.state)
            val second = p.send()
            p.ra.grant(p.kb.identityId, ShareScope())
            assertEquals("CANCELLED", p.a.syncMessageIntents().get(second.id)!!.state)
            val third = p.send()
            p.ra.enableDevice(p.kb.identityId, p.kb.deviceId, false)
            assertEquals("CANCELLED", p.a.syncMessageIntents().get(third.id)!!.state)
        }
    }

    @Test fun oversizedDoodleKeepsManualExchangeAndBlocksInternetIntent() = runBlocking {
        Pair().use { p ->
            p.setup()
            p.allow(p.a, p.kb.identityId, p.kb.deviceId)
            val message = p.ra.prepareDoodle(p.kb.identityId, p.kb.deviceId, doodle(4001))
            assertEquals("BLOCKED", p.a.syncMessageIntents().get(message.id)!!.state)
            assertEquals("MESSAGE_TOO_LARGE", p.a.syncMessageIntents().get(message.id)!!.errorCode)
            assertTrue(SyncMessageQueue(p.a).pending().isEmpty())
            assertEquals(message, p.ra.retry(message.id))
            assertEquals(message.id, p.rb.receive(message.envelope.toByteArray()).message.id)
        }
    }

    @Test fun e2eReceiptAcknowledgesExactMessageWithoutRevivingDeletedIntent() = runBlocking {
        Pair().use { p ->
            p.setup()
            p.allow(p.a, p.kb.identityId, p.kb.deviceId)
            val first = p.ra.prepareDoodle(p.kb.identityId, p.kb.deviceId, doodle())
            val received = p.rb.receive(first.envelope.toByteArray()).message
            val ack = p.rb.acknowledgement(received)
            p.ra.receive(ack.envelope.toByteArray())
            p.ra.receive(ack.envelope.toByteArray())
            assertEquals("ACKED", p.a.syncMessageIntents().get(first.id)!!.state)
            assertTrue(p.ra.retry(first.id).delivered)
            val second = p.ra.prepareDoodle(p.kb.identityId, p.kb.deviceId, doodle())
            val next = p.rb.receive(second.envelope.toByteArray()).message
            p.ra.deleteMessage(second.id)
            p.ra.receive(p.rb.acknowledgement(next).envelope.toByteArray())
            assertEquals("CANCELLED", p.a.syncMessageIntents().get(second.id)!!.state)
        }
    }

    @Test fun queuedReceiptsReuseIdAndDelayedOpenedUsesFirstReceiptTime() = runBlocking {
        Pair().use { p ->
            p.setup()
            p.allow(p.b, p.ka.identityId, p.ka.deviceId)
            val message = p.ra.prepareDoodle(p.kb.identityId, p.kb.deviceId, doodle(days = 2))
            val received = p.rb.receive(message.envelope.toByteArray()).message
            val first = p.rb.acknowledgement(received)
            p.b.close()
            p.b = syncTestDatabase(context, p.nameB)
            p.rb = PartnerRepository(p.b, { p.kb }, {}, { p.time })
            assertEquals(first.id, p.rb.acknowledgement(received).id)
            assertTrue(runCatching { p.rb.openDoodle(message.id) }.isFailure)
            p.time += 2 * 86400000L
            assertEquals(
                received.received,
                p.rb.receive(message.envelope.toByteArray()).message.received
            )
            val opened = requireNotNull(p.rb.openDoodle(message.id))
            assertEquals("PENDING_WRAP", p.b.syncMessageIntents().get(opened.id)!!.state)
            assertEquals(null, p.rb.openDoodle(message.id))
            assertEquals(2, p.b.syncMessageIntents().count())
            p.ra.receive(opened.envelope.toByteArray())
            assertTrue(p.ra.retry(message.id).opened)
        }
    }
}
