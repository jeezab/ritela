package app.ritela

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.LocalPartnerKeys
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerCrypto
import app.ritela.data.PartnerMessage
import app.ritela.data.PartnerRepository
import app.ritela.data.RitelaDatabase
import app.ritela.data.partnerDataMessage
import app.ritela.data.sync.SyncDraft
import app.ritela.data.sync.SyncEnvelopeCodec
import app.ritela.domain.Doodle
import app.ritela.domain.DoodleOpening
import app.ritela.domain.DoodlePoint
import app.ritela.domain.DoodleStroke
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
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
class SyncReceiveTest {
    @Test fun identityImportRequiresRecoveryAndKeepsManualV1Available() = runBlocking {
        Pair().use { p ->
            p.setup()
            val name = "sync-identity-restore-${UUID.randomUUID()}.db"
            val fresh = syncTestDatabase(context, name)
            try {
                var keys = LocalPartnerKeys.create()
                val restored = PartnerRepository(fresh, { keys }, { keys = it })
                val export = p.ra.exportIdentity("synthetic password".toCharArray())
                restored.restoreIdentity(export, "synthetic password".toCharArray())
                assertEquals(p.a.identityId, keys.identityId)
                assertTrue(keys.deviceId != p.a.deviceId)
                assertEquals("RECOVERY_REQUIRED", fresh.syncState().authorities().single().state)
                assertTrue(runCatching { restored.initializeSyncAuthor() }.isFailure)
                confirmedPair(restored, p.b, "B")
                restored.grant(p.b.identityId, scope)
                assertEquals("DATA", restored.prepareData(p.b.identityId, p.b.deviceId, scope).kind)
                assertEquals(p.a.deviceId, p.da.syncState().authorities().single().authorDevice)
            } finally {
                fresh.close()
                context.deleteDatabase(name)
            }
        }
    }

    @Test fun realQueueEncoderAndManualReceiptReleaseOutboxWithoutCancellationRace() = runBlocking {
        Pair().use { p ->
            p.setup()
            p.da.syncState().ensureDefaults()
            p.da.openHelper.writableDatabase.execSQL(
                "UPDATE sync_profile_state SET internetEnabled=1"
            )
            p.da.openHelper.writableDatabase.execSQL(
                "INSERT INTO sync_links (peerIdentity,peerDevice,automaticEnabled,scopeEpoch," +
                    "scopeJson,protocolVersion,acceptedOwnerGeneration) VALUES (?,?,1,1,?,2,0)",
                arrayOf(p.b.identityId, p.b.deviceId, PartnerCodec.scope(scope).toString())
            )
            p.da.dayLogs().save(
                app.ritela.data.DayLogEntity.from(
                    app.ritela.domain.DayLog(
                        java.time.LocalDate.ofEpochDay(10),
                        note = "queue to recipient"
                    )
                )
            )
            val queue = app.ritela.data.sync.SyncQueueRepository(
                p.da,
                app.ritela.data.sync.SyncPacketEncoder(p.ra::encodeSyncDraft)
            )
            assertEquals(
                app.ritela.data.sync.SyncPreparation.ENQUEUED,
                queue.prepare(p.b.identityId, p.b.deviceId)
            )
            val transport = app.ritela.data.sync.SyncManualTransport(p.ra) { bytes ->
                val received = p.rb.receive(bytes)
                p.rb.acknowledgement(received.message).envelope.toByteArray()
            }
            assertEquals(
                app.ritela.domain.sync.SyncAttempt.STORED,
                app.ritela.domain.sync.SyncEngine(queue, transport).step()
            )
            val row = p.da.syncQueue().packets().single()
            assertTrue(p.da.partner().message(row.packetId)!!.delivered)
            assertTrue(row.envelope.isEmpty())
            assertEquals(0L, p.da.syncQueue().queuedBytes())
            assertEquals(
                "queue to recipient",
                p.snapshot().getJSONArray("days").getJSONObject(0).getString("note")
            )
        }
    }
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = ShareScope(
        setOf(ShareCategory.PERIODS, ShareCategory.SECTIONS),
        sections = setOf("note")
    )

    private inner class Pair : AutoCloseable {
        val names = List(2) { "sync-receive-${UUID.randomUUID()}.db" }
        val a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        val da = syncTestDatabase(context, names[0])
        var db = syncTestDatabase(context, names[1])
        var clock = 100000L
        val ra = PartnerRepository(da, { a }, {}, { clock })
        var rb = PartnerRepository(db, { b }, {}, { clock })
        suspend fun setup(negotiate: Boolean = true, approve: Boolean = true) {
            confirmedPair(ra, b, "B")
            confirmedPair(rb, a, "A")
            ra.grant(b.identityId, scope)
            val grant = ra.initializeSyncAuthor()
            if (approve) rb.approveSyncAuthor(a.identityId, grant)
            if (negotiate) {
                rb.receive(
                    ra.prepareSyncCapabilities(b.identityId, b.deviceId).envelope.toByteArray()
                )
                ra.receive(
                    rb.prepareSyncCapabilities(a.identityId, a.deviceId).envelope.toByteArray()
                )
            }
        }
        suspend fun packet(
            sequence: Long,
            records: List<JSONObject>,
            kind: String = "DELTA",
            epoch: Long = 1,
            part: Int = 0,
            parts: Int = 1,
            batch: String = "",
            selection: ShareScope = scope
        ): ByteArray {
            val payload = JSONObject().put("scope", PartnerCodec.scope(selection))
                .put("records", JSONArray(records))
            if (parts > 1) payload.put("batch", batch).put("part", part).put("parts", parts)
            return ra.encodeSyncDraft(
                SyncDraft(
                    UUID.randomUUID().toString(),
                    b.identityId,
                    b.deviceId,
                    epoch,
                    sequence,
                    kind,
                    payload.toString()
                )
            )
        }
        suspend fun snapshot(): JSONObject =
            JSONObject(db.syncReceive().state(a.identityId)!!.snapshot)
        fun reopen() {
            db.close()
            db = syncTestDatabase(context, names[1])
            rb = PartnerRepository(db, { b }, {}, { clock })
        }
        override fun close() {
            da.close()
            db.close()
            names.forEach { context.deleteDatabase(it) }
        }
    }

    private fun day(revision: Long, note: String? = "shared", key: Long = 10): JSONObject =
        JSONObject().put("type", "DAY").put("key", key.toString()).put("revision", revision)
            .put("deleted", note == null).put(
                "value",
                note?.let {
                    JSONObject().put("day", key).put("sections", JSONObject()).put("note", it)
                } ?: JSONObject.NULL
            )

    @Test fun narrowerScopeRequiresNewEpochAndGroupKeepsOtherOwnersEligible() = runBlocking {
        Pair().use { p ->
            p.setup()
            p.rb.receive(p.packet(1, listOf(day(1)), "BASELINE"))
            val narrow = ShareScope(setOf(ShareCategory.PERIODS))
            val invalid = p.packet(2, emptyList(), "BASELINE", selection = narrow)
            assertTrue(runCatching { p.rb.receive(invalid) }.isFailure)
            assertEquals(1, p.snapshot().getJSONArray("days").length())
            p.rb.receive(p.packet(2, emptyList(), "BASELINE", epoch = 2, selection = narrow))
            assertFalse(p.snapshot().has("days"))
            assertEquals(narrow, PartnerCodec.scope(p.snapshot().getJSONObject("scope")))
            assertTrue(
                runCatching {
                    p.rb.receive(p.packet(3, listOf(day(3)), epoch = 2))
                }.isFailure
            )
            val current = p.rb.messages.first()
            val other = app.ritela.data.PartnerMessage(
                "other", "other-owner", "device", false,
                "DATA", 0, p.clock + 1, "{}", ""
            )
            assertEquals(
                other,
                partnerDataMessage(
                    current + other,
                    setOf(p.a.identityId, "other-owner")
                )
            )
            val same = other.copy(id = "old-v1", identity = p.a.identityId)
            assertEquals(
                "SYNC_PROJECTION",
                partnerDataMessage(
                    current + same,
                    setOf(p.a.identityId)
                )!!.kind
            )
        }
    }

    @Test fun explicitCapabilitiesAndAuthorApprovalAreBothRequired() = runBlocking {
        Pair().use { p ->
            p.setup(negotiate = false, approve = false)
            assertTrue(runCatching { p.packet(1, listOf(day(1)), "BASELINE") }.isFailure)
            p.rb.receive(
                p.ra.prepareSyncCapabilities(p.b.identityId, p.b.deviceId).envelope.toByteArray()
            )
            p.ra.receive(
                p.rb.prepareSyncCapabilities(p.a.identityId, p.a.deviceId).envelope.toByteArray()
            )
            val bytes = p.packet(1, listOf(day(1)), "BASELINE")
            assertTrue(runCatching { p.rb.receive(bytes) }.isFailure)
            assertNull(p.db.syncReceive().state(p.a.identityId))
            p.rb.approveSyncAuthor(p.a.identityId, p.ra.initializeSyncAuthor())
            p.rb.receive(bytes)
            assertEquals(
                "shared",
                p.snapshot().getJSONArray("days").getJSONObject(0).getString("note")
            )
            val bad = SyncEnvelopeCodec.capabilities().put("suite", "unknown")
            assertTrue(runCatching { SyncEnvelopeCodec.validateCapabilities(bad) }.isFailure)
        }
    }

    @Test fun reversedChunksSurviveRestartAndApplyOnceOnlyWhenComplete() = runBlocking {
        Pair().use { p ->
            p.setup()
            val batch = UUID.randomUUID().toString()
            val first = p.packet(
                1,
                listOf(day(1, "one")),
                "BASELINE",
                part = 0,
                parts = 2,
                batch = batch
            )
            val second = p.packet(
                2,
                listOf(day(1, "two", 11)),
                "BASELINE",
                part = 1,
                parts = 2,
                batch = batch
            )
            val received = p.rb.receive(second).message
            assertNull(p.db.syncReceive().state(p.a.identityId))
            val earlyAck = p.rb.acknowledgement(received)
            p.ra.receive(earlyAck.envelope.toByteArray())
            assertFalse(p.da.partner().message(received.id)!!.delivered)
            p.reopen()
            assertTrue(p.rb.receive(second).duplicate)
            p.clock += 99999
            val completed = p.rb.receive(first)
            assertEquals(2, p.snapshot().getJSONArray("days").length())
            assertEquals(100000L, p.db.partner().message(received.id)!!.received)
            p.ra.receive(p.rb.acknowledgement(completed.message).envelope.toByteArray())
            assertTrue(p.da.partner().message(received.id)!!.delivered)
            assertTrue(p.rb.receive(first).duplicate)
            assertTrue(p.db.dayLogs().snapshot().isEmpty())
            assertTrue(p.db.periods().snapshot().isEmpty())
        }
    }

    @Test fun gapsTombstonesScopeResetAndOldV1NeverRollBackProjection() = runBlocking {
        Pair().use { p ->
            p.setup()
            val baseline = p.packet(1, listOf(day(1)), "BASELINE")
            p.rb.receive(baseline)
            val third = p.packet(3, listOf(day(3, null)))
            p.rb.receive(third)
            assertEquals(1, p.snapshot().getJSONArray("days").length())
            p.rb.receive(p.packet(2, listOf(day(2, "edited"))))
            assertEquals(0, p.snapshot().getJSONArray("days").length())
            p.rb.receive(baseline)
            assertEquals(0, p.snapshot().getJSONArray("days").length())
            p.rb.receive(p.packet(4, listOf(day(4, "future", 12)), epoch = 2))
            assertEquals(0, p.snapshot().getJSONArray("days").length())
            p.rb.receive(p.packet(5, emptyList(), "BASELINE", epoch = 2))
            p.rb.receive(
                p.ra.prepareData(p.b.identityId, p.b.deviceId, scope).envelope.toByteArray()
            )
            val selected = partnerDataMessage(p.rb.messages.first(), setOf(p.a.identityId))!!
            assertEquals("SYNC_PROJECTION", selected.kind)
            assertEquals(0, JSONObject(selected.body).getJSONArray("days").length())
            p.rb.clearReceived(p.a.identityId)
            p.rb.receive(baseline)
            assertEquals(
                "{}",
                partnerDataMessage(p.rb.messages.first(), setOf(p.a.identityId))!!.body
            )
        }
    }

    @Test fun changedDigestSequenceCollisionAndMalformedFinalPartRollBack() = runBlocking {
        Pair().use { p ->
            p.setup()
            val bytes = p.packet(1, listOf(day(1)), "BASELINE")
            val original = p.rb.receive(bytes).message
            val outer = JSONObject(bytes.toString(Charsets.UTF_8))
            // Same header, different envelope bytes: immutable packet ID collision.
            outer.put("extra", true)
            assertTrue(runCatching { p.rb.receive(outer.toString().toByteArray()) }.isFailure)
            assertTrue(
                runCatching {
                    p.rb.receive(p.packet(1, listOf(day(1, "collision")), "BASELINE"))
                }.isFailure
            )
            val batch = UUID.randomUUID().toString()
            p.rb.receive(
                p.packet(2, listOf(day(2, "a")), "BASELINE", part = 0, parts = 2, batch = batch)
            )
            assertTrue(
                runCatching {
                    p.rb.receive(
                        p.packet(
                            3,
                            listOf(day(2, "b")),
                            "BASELINE",
                            part = 1,
                            parts = 2,
                            batch = batch
                        )
                    )
                }.isFailure
            ) // Repeated record across parts is not last-writer-wins.
            assertEquals(
                "shared",
                p.snapshot().getJSONArray("days").getJSONObject(0).getString("note")
            )
            p.rb.clearReceived(p.a.identityId)
            assertTrue(p.rb.receive(bytes).duplicate)
            assertTrue(runCatching { p.rb.receive(outer.toString().toByteArray()) }.isFailure)
            assertEquals(original.id, p.db.syncReceive().packet(original.id)!!.id)
        }
    }

    @Test fun successorGrantFencesOldPhoneAndRestoredIdentityCannotBecomeAuthor() = runBlocking {
        Pair().use { p ->
            p.setup()
            val old = p.packet(1, listOf(day(1)), "BASELINE")
            val next = LocalPartnerKeys.create(
                java.security.KeyPair(
                    PartnerCrypto.publicKey(p.a.identityPublic),
                    PartnerCrypto.privateKey(p.a.identityPrivate)
                )
            )
            confirmedPair(p.rb, next, "new A")
            confirmedPair(p.ra, next, "new local phone")
            val previous = p.db.syncReceive().author(p.a.identityId)!!
            val pending = p.rb.receive(p.packet(2, listOf(day(2)))).message
            assertEquals(1, p.db.syncReceive().pending(p.a.identityId).size)
            val raw = JSONObject().put("format", "ritela.author:2").put("owner", p.a.identityId)
                .put("device", next.deviceId).put("key", next.devicePublic).put("generation", 2)
                .put("previous", previous.digest).toString()
            val grant = JSONObject().put("body", raw).put(
                "signature",
                SyncEnvelopeCodec.signed(raw, next.devicePrivate)
            )
            assertTrue(
                runCatching {
                    p.rb.approveSyncAuthor(p.a.identityId, grant.toString())
                }.isFailure
            )
            val retirement = app.ritela.data.sync.SyncAuthorGrants(p.da)
            val fractional = JSONObject(raw).put("generation", 2.5).toString()
            val invalidProposal = JSONObject().put("body", fractional).put(
                "signature",
                SyncEnvelopeCodec.signed(fractional, next.devicePrivate)
            ).toString()
            assertTrue(runCatching { retirement.retire(p.a, invalidProposal) }.isFailure)
            assertEquals("ACTIVE", p.da.syncState().authorities().single().state)
            val handover = retirement.retire(p.a, grant.toString())
            assertEquals(handover, retirement.retire(p.a, grant.toString()))
            assertEquals("RETIRED", p.da.syncState().authorities().single().state)
            assertTrue(runCatching { p.ra.initializeSyncAuthor() }.isFailure)
            p.rb.approveSyncAuthor(p.a.identityId, handover)
            assertTrue(p.db.syncReceive().pending(p.a.identityId).isEmpty())
            assertEquals(2, p.db.syncReceive().packet(pending.id)!!.applied)
            assertEquals("", p.db.syncReceive().packet(pending.id)!!.payload)
            assertTrue(p.db.syncReceive().appliedIds(p.a.identityId, p.a.deviceId).isEmpty())
            assertTrue(runCatching { p.rb.receive(old) }.isFailure)
            assertTrue(
                runCatching {
                    p.rb.approveSyncAuthor(p.a.identityId, previous.grant)
                }.isFailure
            )
            val restoredRepo = PartnerRepository(p.da, { next }, {})
            assertTrue(runCatching { restoredRepo.initializeSyncAuthor() }.isFailure)
            assertEquals(2L, p.db.syncReceive().author(p.a.identityId)!!.generation)
        }
    }

    @Test fun receiptFromAnotherTrustedDeviceCannotMarkFirstDeviceDelivered() = runBlocking {
        Pair().use { p ->
            p.setup()
            val b2 = LocalPartnerKeys.create(
                java.security.KeyPair(
                    PartnerCrypto.publicKey(p.b.identityPublic),
                    PartnerCrypto.privateKey(p.b.identityPrivate)
                )
            )
            val name = "sync-second-device-${UUID.randomUUID()}.db"
            val other = syncTestDatabase(context, name)
            try {
                val r2 = PartnerRepository(other, { b2 }, {})
                confirmedPair(p.ra, b2, "second B")
                confirmedPair(r2, p.a, "A")
                p.ra.grant(p.b.identityId, scope)
                val bytes = p.packet(1, listOf(day(1)), "BASELINE")
                val incoming = p.rb.receive(bytes).message
                val wrong = r2.acknowledgement(
                    incoming.copy(
                        identity = p.a.identityId,
                        device = p.a.deviceId,
                        kind = "DATA"
                    )
                )
                assertTrue(runCatching { p.ra.receive(wrong.envelope.toByteArray()) }.isFailure)
                assertFalse(p.da.partner().message(incoming.id)!!.delivered)
                p.ra.receive(p.rb.acknowledgement(incoming).envelope.toByteArray())
                assertTrue(p.da.partner().message(incoming.id)!!.delivered)
            } finally {
                other.close()
                context.deleteDatabase(name)
            }
        }
    }

    @Test fun embeddedDoodleUsesFirstReceiptTimeAndOpenedSurvivesSenderDeletion() = runBlocking {
        Pair().use { p ->
            p.setup()
            val doodle = Doodle(
                listOf(DoodleStroke(listOf(DoodlePoint(0.2f, 0.3f)), 0xFF000000, 0.01f)),
                opening = DoodleOpening.AFTER_DAYS,
                delayMinutes = 1
            )
            val sent = p.ra.confirmData(p.b.identityId, p.b.deviceId, scope, doodle = doodle)
            p.clock += 5000
            val received = p.rb.receive(sent.envelope.toByteArray()).message
            assertTrue(runCatching { p.rb.openDoodle(received.id) }.isFailure)
            p.clock += 60000
            assertTrue(p.rb.receive(sent.envelope.toByteArray()).duplicate)
            assertEquals(105000L, p.db.partner().message(received.id)!!.received)
            val opened = p.rb.openDoodle(received.id)!!
            p.ra.deleteMessage(sent.id, true)
            assertFalse(p.ra.receive(opened.envelope.toByteArray()).duplicate)
            assertFalse(p.da.partner().message(sent.id)!!.opened)
            assertTrue(p.ra.receive(opened.envelope.toByteArray()).duplicate)
            assertEquals("", p.da.partner().message(sent.id)!!.body)
            val plain = p.ra.confirmData(p.b.identityId, p.b.deviceId, scope)
            p.ra.deleteMessage(plain.id, true)
            // An erased DATA packet without a doodle does not gain OPENED authority.
            assertEquals(0, p.da.syncReceive().hasDoodle(plain.id))
        }
    }
}
