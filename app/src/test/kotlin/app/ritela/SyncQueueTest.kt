package app.ritela

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.DayLogEntity
import app.ritela.data.JournalRepository
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerDirectoryRow
import app.ritela.data.PeriodEntity
import app.ritela.data.RitelaDatabase
import app.ritela.data.sync.ShareProjector
import app.ritela.data.sync.SyncDraft
import app.ritela.data.sync.SyncPacketEncoder
import app.ritela.data.sync.SyncPreparation
import app.ritela.data.sync.SyncQueueRepository
import app.ritela.domain.DayLog
import app.ritela.domain.JournalLayout
import app.ritela.domain.JournalSection
import app.ritela.domain.JournalTag
import app.ritela.domain.Mood
import app.ritela.domain.PartnerDevice
import app.ritela.domain.PartnerDirectory
import app.ritela.domain.PartnerIdentity
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import app.ritela.domain.sync.SyncAttempt
import app.ritela.domain.sync.SyncEngine
import app.ritela.domain.sync.SyncTransport
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
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
class SyncQueueTest {
    @Test fun largeBaselineAndDeltaSplitAtomicallyWithPerLinkRevisions() = runBlocking {
        val name = "sync-chunks-${UUID.randomUUID()}.db"
        val db = syncTestDatabase(context, name)
        try {
            val notes = ShareScope(setOf(ShareCategory.SECTIONS), sections = setOf("note"))
            allow(db, notes)
            repeat(260) { index ->
                db.dayLogs().save(
                    DayLogEntity.from(
                        DayLog(
                            LocalDate.ofEpochDay(index.toLong()),
                            note = "shared $index"
                        )
                    )
                )
            }
            var encoded = 0
            val broken = queue(
                db,
                SyncPacketEncoder {
                    if (++encoded == 2) error("Synthetic second-part failure")
                    it.payload.toByteArray()
                }
            )
            assertTrue(runCatching { broken.prepare(peer, device) }.isFailure)
            assertTrue(db.syncQueue().packets().isEmpty())
            assertNull(db.syncQueue().progress(peer, device, 1))
            assertEquals(SyncPreparation.ENQUEUED, queue(db).prepare(peer, device))
            val baseline = db.syncQueue().packets()
            assertEquals(listOf(1L, 2L, 3L), baseline.map { it.linkSequence })
            val records = baseline.flatMap { packet ->
                val content = JSONObject(packet.envelope.toString(Charsets.UTF_8))
                assertEquals(3, content.getInt("parts"))
                app.ritela.data.PartnerCodec.objects(content.getJSONArray("records"))
            }
            assertEquals(260, records.size)
            assertTrue(records.all { it.getLong("revision") == 1L })
            repeat(260) { index ->
                db.dayLogs().save(
                    DayLogEntity.from(
                        DayLog(
                            LocalDate.ofEpochDay(index.toLong()),
                            note = "edited $index"
                        )
                    )
                )
            }
            assertEquals(SyncPreparation.ENQUEUED, queue(db).prepare(peer, device))
            val deltas = db.syncQueue().packets().drop(3)
            assertEquals(listOf(4L, 5L, 6L), deltas.map { it.linkSequence })
            for (packet in deltas) {
                val content = JSONObject(packet.envelope.toString(Charsets.UTF_8))
                assertFalse(content.has("parts"))
                assertTrue(
                    app.ritela.data.PartnerCodec.objects(content.getJSONArray("records"))
                        .all { it.getLong("revision") == packet.linkSequence }
                )
            }
        } finally {
            close(db, name)
        }
    }
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val peer = "a".repeat(64)
    private val device = UUID.randomUUID().toString()
    private val scope = ShareScope(setOf(ShareCategory.PERIODS))
    private var time = 1000L

    // Deliberately plaintext fake. No production encoder/transport is supplied in stage two.
    private val fakeEncoder = SyncPacketEncoder { it.payload.toByteArray() }
    private fun queue(
        db: RitelaDatabase,
        encoder: SyncPacketEncoder = fakeEncoder,
        projector: ShareProjector = ShareProjector(),
        limit: Long = 16 * 1024 * 1024
    ) = SyncQueueRepository(db, encoder, projector, { time }, { 0.5 }, limit)
    private fun period() = PeriodEntity(
        UUID.randomUUID().toString(),
        LocalDate.now().minusDays(60).toEpochDay(),
        LocalDate.now().minusDays(56).toEpochDay(),
        1,
        2
    )

    private suspend fun allow(db: RitelaDatabase, chosen: ShareScope = scope, epoch: Long = 1) {
        db.syncState().ensureDefaults()
        db.openHelper.writableDatabase.execSQL("UPDATE sync_profile_state SET internetEnabled=1")
        db.partner().save(
            PartnerDirectoryRow(
                json = PartnerCodec.directory(
                    PartnerDirectory(
                        identities = listOf(
                            PartnerIdentity(
                                peer,
                                "Synthetic public key",
                                "Synthetic",
                                listOf(
                                    PartnerDevice(
                                        device,
                                        "Synthetic device key",
                                        "Synthetic certificate",
                                        "n",
                                        "n"
                                    )
                                ),
                                grants = chosen
                            )
                        )
                    )
                )
            )
        )
        db.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO sync_links " +
                "(peerIdentity,peerDevice,automaticEnabled,scopeEpoch,scopeJson,protocolVersion) " +
                "VALUES (?,?,1,?,?,2)",
            arrayOf<Any>(peer, device, epoch, PartnerCodec.scope(chosen).toString())
        )
    }

    private suspend fun close(db: RitelaDatabase, name: String) {
        db.close()
        context.deleteDatabase(name)
    }

    @Test fun baselineAndDeltaHaveStableIdsAndCursors() = runBlocking {
        val name = "sync-queue-${UUID.randomUUID()}.db"
        val db = syncTestDatabase(context, name)
        try {
            val repo = queue(db)
            val record = period()
            db.periods().addIfSeparate(record)
            assertEquals(SyncPreparation.OFF, repo.prepare(peer, device))
            assertNull(repo.claim())
            assertTrue(db.syncQueue().packets().isEmpty())
            allow(db)
            assertEquals(SyncPreparation.ENQUEUED, repo.prepare(peer, device))
            val baseline = db.syncQueue().packets().single()
            assertEquals("BASELINE", baseline.kind)
            assertEquals(SyncPreparation.UNCHANGED, repo.prepare(peer, device))
            db.periods().editIfSeparate(record.id, record.startDay, record.endDay!! + 1, 3)
            db.periods().editIfSeparate(record.id, record.startDay, record.endDay!! + 2, 4)
            assertEquals(SyncPreparation.ENQUEUED, repo.prepare(peer, device))
            val delta = db.syncQueue().packets().last()
            assertEquals(
                1,
                JSONObject(delta.envelope.toString(Charsets.UTF_8)).getJSONArray("records").length()
            )
            assertEquals(baseline.throughCursor, delta.fromCursor)
            assertEquals(2L, delta.linkSequence)
            val lease = repo.claim()!!
            assertEquals(baseline.packetId, lease.packetId)
            assertFalse(repo.authorized(lease.copy(bytes = byteArrayOf(0))))
            assertNull(repo.claim()) // Same link cannot overtake the in-flight baseline.
            assertTrue(repo.stored(lease))
            assertEquals(delta.packetId, repo.claim()!!.packetId)
        } finally {
            close(db, name)
        }
    }

    @Test fun removedTagClearsProjectionWithoutLeakingForbiddenDays() = runBlocking {
        val name = "sync-filter-${UUID.randomUUID()}.db"
        val db = syncTestDatabase(context, name)
        try {
            val selected =
                ShareScope(setOf(ShareCategory.TAGS), tags = mapOf("mood" to setOf("HAPPY")))
            allow(db, selected)
            val date = LocalDate.now().minusDays(1)
            db.dayLogs().save(
                DayLogEntity.from(DayLog(date, mood = Mood.HAPPY, note = "Forbidden note"))
            )
            val repo = queue(db)
            assertEquals(SyncPreparation.ENQUEUED, repo.prepare(peer, device))
            val initial = db.syncQueue().packets().last().envelope.toString(Charsets.UTF_8)
            assertTrue(initial.contains("HAPPY"))
            assertFalse(initial.contains("Forbidden note"))
            db.dayLogs().save(DayLogEntity.from(DayLog(date, mood = Mood.LOW, note = "Hidden")))
            assertEquals(SyncPreparation.ENQUEUED, repo.prepare(peer, device))
            val delta =
                JSONObject(db.syncQueue().packets().last().envelope.toString(Charsets.UTF_8))
            val row = delta.getJSONArray("records").getJSONObject(0)
            assertTrue(row.getBoolean("deleted"))
            assertFalse(delta.toString().contains("LOW") || delta.toString().contains("Hidden"))
            val hiddenDay = date.minusDays(1)
            db.dayLogs().save(
                DayLogEntity.from(DayLog(hiddenDay, mood = Mood.LOW, note = "Secret"))
            )
            assertEquals(SyncPreparation.UNCHANGED, repo.prepare(peer, device))
            assertFalse(
                db.syncQueue().packets().any {
                    it.envelope.toString(Charsets.UTF_8).contains(hiddenDay.toEpochDay().toString())
                }
            )
        } finally {
            close(db, name)
        }
    }

    @Test fun leaseRestartBackoffAndStaleWorkerKeepTheSameImmutablePacket() = runBlocking {
        val name = "sync-retry-${UUID.randomUUID()}.db"
        var db = syncTestDatabase(context, name)
        try {
            allow(db)
            db.periods().addIfSeparate(period())
            var repo = queue(db)
            repo.prepare(peer, device)
            val first = repo.claim()!!
            val expected = first.bytes.copyOf()
            db.close()
            db = syncTestDatabase(context, name)
            repo = queue(db)
            assertNull(repo.claim())
            time += 120001
            val second = repo.claim()!!
            assertEquals(first.packetId, second.packetId)
            assertArrayEquals(expected, second.bytes)
            assertFalse(repo.stored(first))
            assertTrue(repo.failed(second, 60000))
            assertNull(repo.claim())
            time += 60001
            val sent = mutableListOf<String>()
            val engine = SyncEngine(repo, SyncTransport { sent += it.packetId })
            assertEquals(SyncAttempt.STORED, engine.step())
            assertEquals(listOf(first.packetId), sent)
            assertEquals("STORED", db.syncQueue().packet(first.packetId)!!.state)
        } finally {
            close(db, name)
        }
    }

    @Test fun revokeBeforeDispatchCancelsBytesAndReenableRequiresFreshBaseline() = runBlocking {
        val name = "sync-revoke-${UUID.randomUUID()}.db"
        val db = syncTestDatabase(context, name)
        try {
            allow(db)
            val repo = queue(db)
            db.periods().addIfSeparate(period())
            repo.prepare(peer, device)
            val lease = repo.claim()!!
            db.openHelper.writableDatabase.execSQL(
                "UPDATE sync_profile_state SET internetEnabled=0"
            )
            assertFalse(repo.authorized(lease))
            assertEquals(0, db.syncQueue().packet(lease.packetId)!!.envelope.size)
            assertEquals("CANCELLED", db.syncQueue().packet(lease.packetId)!!.state)
            allow(db)
            assertEquals(SyncPreparation.ENQUEUED, repo.prepare(peer, device))
            val new = db.syncQueue().packets().last()
            assertEquals("BASELINE", new.kind)
            assertEquals(2L, new.linkSequence)
            val sender =
                SyncEngine(repo, SyncTransport { throw IOException("Synthetic network failure") })
            assertEquals(SyncAttempt.RETRY, sender.step())
            assertEquals("RETRY_WAIT", db.syncQueue().packet(new.packetId)!!.state)
            allow(db, ShareScope(setOf(ShareCategory.TAGS), tags = mapOf("mood" to setOf("HAPPY"))))
            assertNull(repo.claim()) // Revoke even a not-yet-due retry.
            assertEquals("CANCELLED", db.syncQueue().packet(new.packetId)!!.state)
            assertEquals(SyncPreparation.BLOCKED, repo.prepare(peer, device))
            allow(db, epoch = 2)
            assertEquals(SyncPreparation.ENQUEUED, repo.prepare(peer, device))
        } finally {
            close(db, name)
        }
    }

    @Test fun encodingAndQueueLimitFailuresDoNotAdvanceProgressOrLoseSourceChanges() = runBlocking {
        val name = "sync-encode-${UUID.randomUUID()}.db"
        val db = syncTestDatabase(context, name)
        try {
            allow(db)
            db.periods().addIfSeparate(period())
            val before = db.syncJournal().watermark()
            assertTrue(
                runCatching {
                    queue(
                        db,
                        SyncPacketEncoder {
                            error("Synthetic encoding failure")
                        }
                    ).prepare(peer, device)
                }.isFailure
            )
            assertEquals(before, db.syncJournal().watermark())
            assertNull(db.syncQueue().progress(peer, device, 1))
            assertEquals(SyncPreparation.BLOCKED, queue(db, limit = 1).prepare(peer, device))
            assertTrue(db.syncQueue().packets().isEmpty())
            assertEquals(1, db.periods().snapshot().size)
            assertEquals(SyncPreparation.ENQUEUED, queue(db).prepare(peer, device))
            db.openHelper.writableDatabase.execSQL("UPDATE sync_link_progress SET cursor=-1")
            assertEquals(SyncPreparation.BLOCKED, queue(db).prepare(peer, device))
        } finally {
            close(db, name)
        }
    }

    @Test fun snapshotAndCompactionPreserveConcurrentChangesAndTombstones() = runBlocking {
        val name = "sync-snapshot-${UUID.randomUUID()}.db"
        val db = syncTestDatabase(context, name)
        try {
            allow(db)
            val record = period()
            db.periods().addIfSeparate(record)
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val repo = queue(
                db,
                SyncPacketEncoder {
                    started.complete(Unit)
                    release.await()
                    it.payload.toByteArray()
                }
            )
            val prepare = async(Dispatchers.IO) { repo.prepare(peer, device) }
            started.await()
            val edit = async(Dispatchers.IO) { db.periods().delete(record.id) }
            assertFalse(edit.isCompleted)
            release.complete(Unit)
            assertEquals(SyncPreparation.ENQUEUED, prepare.await())
            edit.await()
            val baseline = db.syncQueue().packets().single()
            assertTrue(db.syncJournal().watermark() > baseline.throughCursor)
            val next = queue(db)
            assertEquals(SyncPreparation.ENQUEUED, next.prepare(peer, device))
            val delta =
                JSONObject(db.syncQueue().packets().last().envelope.toString(Charsets.UTF_8))
            assertTrue(delta.getJSONArray("records").getJSONObject(0).getBoolean("deleted"))
            assertEquals(0, next.compact()) // Outstanding baseline protects its source range.
            repeat(2) { assertTrue(next.stored(next.claim()!!)) }
            assertTrue(next.compact() > 0)
            assertTrue(db.syncJournal().latest().any { it.entityKey == record.id && it.deleted })
        } finally {
            close(db, name)
        }
    }

    @Test fun newTagsAndPrivateLabelsDoNotExpandFrozenPermissions() = runBlocking {
        val name = "sync-tags-${UUID.randomUUID()}.db"
        val db = syncTestDatabase(context, name)
        try {
            val chosen =
                ShareScope(setOf(ShareCategory.TAGS), tags = mapOf("custom" to setOf("KNOWN")))
            allow(db, chosen)
            val layout = JournalLayout(
                sections = listOf(
                    JournalSection(
                        "custom",
                        "Shared section",
                        tags = listOf(JournalTag("KNOWN", "Shared label"))
                    )
                )
            )
            JournalRepository(db.journal()).save(layout)
            val repo = queue(db)
            assertEquals(SyncPreparation.ENQUEUED, repo.prepare(peer, device))
            val original = db.syncQueue().packets().single()
            val baseline = JSONObject(original.envelope.toString(Charsets.UTF_8))
            assertFalse(baseline.has("watermark"))
            assertEquals(
                original.linkSequence,
                baseline.getJSONArray("records")
                    .getJSONObject(0).getLong("revision")
            )
            JournalRepository(db.journal()).save(
                layout.copy(
                    sections = listOf(
                        layout.sections.single().copy(
                            tags = listOf(
                                JournalTag("KNOWN", "Shared label"),
                                JournalTag("NEW", "Forbidden label")
                            )
                        )
                    )
                )
            )
            db.dayLogs().save(
                DayLogEntity.from(
                    DayLog(
                        LocalDate.now(),
                        custom = mapOf("custom" to setOf("NEW")),
                        note = "Hidden"
                    )
                )
            )
            assertEquals(SyncPreparation.UNCHANGED, repo.prepare(peer, device))
            assertEquals(1, db.syncQueue().packets().size)
            assertFalse(original.envelope.toString(Charsets.UTF_8).contains("Forbidden label"))
        } finally {
            close(db, name)
        }
    }

    @Test fun profilesHaveIndependentQueuesAndForecastsRequireAnOwnerProvider() = runBlocking {
        val names = List(2) { "sync-profile-${UUID.randomUUID()}.db" }
        val a = syncTestDatabase(context, names[0])
        val b = syncTestDatabase(context, names[1])
        try {
            val first = period()
            val second = period()
            a.periods().addIfSeparate(first)
            b.periods().addIfSeparate(second)
            allow(a)
            assertEquals(SyncPreparation.ENQUEUED, queue(a).prepare(peer, device))
            assertEquals(SyncPreparation.OFF, queue(b).prepare(peer, device))
            assertTrue(b.syncQueue().packets().isEmpty())
            allow(b)
            assertEquals(SyncPreparation.ENQUEUED, queue(b).prepare(peer, device))
            val aBody = a.syncQueue().packets().single().envelope.toString(Charsets.UTF_8)
            val bBody = b.syncQueue().packets().single().envelope.toString(Charsets.UTF_8)
            assertTrue(aBody.contains(first.id) && !aBody.contains(second.id))
            assertTrue(bBody.contains(second.id) && !bBody.contains(first.id))
            allow(b, ShareScope(setOf(ShareCategory.FORECASTS)), epoch = 2)
            assertEquals(SyncPreparation.BLOCKED, queue(b).prepare(peer, device))
            assertNull(b.syncQueue().progress(peer, device, 2))
            val provided = ShareProjector {
                JSONObject().put("forecasts", org.json.JSONArray())
                    .put("note", "Forbidden forecast field").put("algorithm", "Synthetic")
            }
            assertEquals(
                SyncPreparation.ENQUEUED,
                queue(b, projector = provided).prepare(peer, device)
            )
            assertFalse(
                b.syncQueue().packets().last().envelope.toString(Charsets.UTF_8)
                    .contains("Forbidden forecast field")
            )
        } finally {
            close(a, names[0])
            close(b, names[1])
        }
    }
}
