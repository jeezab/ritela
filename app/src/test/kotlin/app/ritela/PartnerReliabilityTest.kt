package app.ritela

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.DayLogEntity
import app.ritela.data.LocalPartnerKeys
import app.ritela.data.PartnerBluetooth
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerCrypto
import app.ritela.data.PartnerPreferences
import app.ritela.data.PartnerRepository
import app.ritela.data.RitelaDatabase
import app.ritela.domain.DayLog
import app.ritela.domain.Doodle
import app.ritela.domain.DoodleOpening
import app.ritela.domain.DoodlePoint
import app.ritela.domain.DoodleStroke
import app.ritela.domain.ExchangeEdge
import app.ritela.domain.HeartPhysics
import app.ritela.domain.Mood
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PartnerReliabilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun db() = Room.inMemoryDatabaseBuilder(context, RitelaDatabase::class.java).build()
    private fun repo(db: RitelaDatabase, keys: LocalPartnerKeys) =
        PartnerRepository(db, { keys }, {})

    @Test fun selectedTagsNeverIncludeOtherSectionsNotesOrPrivateSettings() = runBlocking {
        val db = db()
        try {
            val day = LocalDate.now().minusDays(1)
            db.dayLogs().save(
                DayLogEntity.from(
                    DayLog(
                        day,
                        mood = Mood.entries.first(),
                        note = "private synthetic",
                        custom = mapOf("discharge" to setOf("WATERY"))
                    )
                )
            )
            val r = repo(db, LocalPartnerKeys.create())
            val preview = r.preview(
                ShareScope(
                    setOf(ShareCategory.TAGS),
                    sections = setOf("mood"),
                    tags = mapOf("discharge" to setOf("WATERY"))
                )
            )
            val text = preview.toString()
            assertTrue(text.contains("WATERY"))
            assertFalse(text.contains("private synthetic"))
            assertFalse(text.contains("mood"))
            val settings = r.preview(ShareScope(setOf(ShareCategory.SETTINGS)))
            assertEquals(
                setOf("language", "theme"),
                settings.getJSONObject("settings").keys().asSequence().toSet()
            )
        } finally {
            db.close()
        }
    }

    @Test fun signatureTamperingUnknownSenderAndReplayAfterDeletionAreRejected() = runBlocking {
        var a = LocalPartnerKeys.create()
        var b = LocalPartnerKeys.create()
        val dbA = db()
        val dbB = db()
        try {
            val ra = PartnerRepository(dbA, { a }, { a = it })
            val rb = PartnerRepository(dbB, { b }, {
                b =
                    it
            })
            ra.pair(ra.inspectInvitation(b.qr("B")))
            rb.pair(rb.inspectInvitation(a.qr("A")))
            ra.grant(b.identityId, ShareScope(setOf(ShareCategory.PERIODS)))
            val message = ra.prepareData(
                b.identityId,
                b.deviceId,
                ShareScope(setOf(ShareCategory.PERIODS))
            )
            val forged = JSONObject(
                message.envelope
            ).put("signature", PartnerCrypto.b64(ByteArray(64))).toString().toByteArray()
            assertTrue(runCatching { rb.receive(forged) }.isFailure)
            assertTrue(dbB.partner().history(a.identityId).isEmpty())
            rb.remove(rb.snapshot().contacts.single().id)
            assertTrue(runCatching { rb.receive(message.envelope.toByteArray()) }.isFailure)
            rb.pair(rb.inspectInvitation(a.qr("A")))
            assertTrue(runCatching { rb.receive(message.envelope.toByteArray()) }.isFailure)
            assertTrue(dbB.periods().snapshot().isEmpty())
        } finally {
            dbA.close()
            dbB.close()
        }
    }

    @Test fun clearReceivedKeepsReplayTombstoneAndOwnRecords() = runBlocking {
        val a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        val dbA = db()
        val dbB = db()
        try {
            val ra = repo(dbA, a)
            val rb = repo(dbB, b)
            ra.pair(ra.inspectInvitation(b.qr("B")))
            rb.pair(rb.inspectInvitation(a.qr("A")))
            val doodle =
                Doodle(listOf(DoodleStroke(listOf(DoodlePoint(.5f, .5f)), 0xffee5577, .01f)))
            val message = ra.prepareDoodle(b.identityId, b.deviceId, doodle)
            rb.receive(message.envelope.toByteArray())
            rb.clearReceived(a.identityId)
            assertTrue(rb.receive(message.envelope.toByteArray()).duplicate)
            assertEquals("", dbB.partner().message(message.id)?.body)
            assertTrue(dbB.dayLogs().snapshot().isEmpty())
            assertEquals(1, dbB.partner().history(a.identityId).size)
        } finally {
            dbA.close()
            dbB.close()
        }
    }

    @Test fun identityTransferPreservesPersonButDoesNotTrustNewDeviceAutomatically() = runBlocking {
        var a = LocalPartnerKeys.create()
        var next = LocalPartnerKeys.create()
        val dbA = db()
        val dbNext = db()
        val dbRemote = db()
        val remote = LocalPartnerKeys.create()
        try {
            val ra = PartnerRepository(dbA, { a }, { a = it })
            val rn = PartnerRepository(dbNext, { next }, { next = it })
            val rr = repo(dbRemote, remote)
            rr.pair(rr.inspectInvitation(a.qr("A")))
            rr.grant(a.identityId, ShareScope(setOf(ShareCategory.PERIODS)))
            val password = "synthetic password".toCharArray()
            val exported = ra.exportIdentity(password)
            assertTrue(password.all { it == '\u0000' })
            assertFalse(exported.toString(Charsets.UTF_8).contains(a.identityPrivate))
            assertTrue(
                runCatching {
                    rn.restoreIdentity(exported, "wrong password".toCharArray())
                }.isFailure
            )
            rn.restoreIdentity(exported, "synthetic password".toCharArray())
            assertEquals(a.identityId, next.identityId)
            assertNotEquals(a.deviceId, next.deviceId)
            val invitation = rr.inspectInvitation(next.qr("New phone"))
            assertEquals(1, rr.snapshot().identities.single().devices.size)
            rr.pair(invitation)
            assertEquals(2, rr.snapshot().identities.single().devices.size)
            assertTrue(rr.snapshot().identities.single().grants.categories.isEmpty())
        } finally {
            dbA.close()
            dbNext.close()
            dbRemote.close()
        }
    }

    @Test fun partnerModesPersistPerUserWithoutModifyingRecords() = runBlocking {
        val one = UUID.randomUUID().toString()
        val two = UUID.randomUUID().toString()
        var prefs = PartnerPreferences(context, one)
        val other = PartnerPreferences(context, two)
        try {
            withTimeout(5000) { prefs.mode.first { it.ready } }
            prefs.show(true)
            prefs.recipient(true)
            withTimeout(5000) { prefs.mode.first { it.show && it.recipient } }
            assertFalse(withTimeout(5000) { other.mode.first { it.ready } }.recipient)
            prefs.close()
            prefs = PartnerPreferences(context, one)
            val restored = withTimeout(5000) { prefs.mode.first { it.ready } }
            assertTrue(restored.show && restored.recipient)
        } finally {
            prefs.close()
            other.close()
        }
    }

    @Test fun migrationFourToFiveRetainsExistingTables() = runBlocking {
        val name = "partner-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val schema = JSONObject(
            File("schemas/app.ritela.data.RitelaDatabase/4.json").readText()
        ).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            PartnerCodec.objects(schema.getJSONArray("entities")).forEach { entity ->
                old.execSQL(
                    entity.getString(
                        "createSql"
                    ).replace("\${TABLE_NAME}", entity.getString("tableName"))
                )
                PartnerCodec.objects(
                    entity.optJSONArray("indices") ?: org.json.JSONArray()
                ).forEach { index ->
                    old.execSQL(
                        index.getString(
                            "createSql"
                        ).replace("\${TABLE_NAME}", entity.getString("tableName"))
                    )
                }
            }
            old.execSQL("INSERT INTO profile_settings VALUES (1,'ru','DARK')")
            old.version = 4
        }
        val upgraded = Room.databaseBuilder(
            context,
            RitelaDatabase::class.java,
            name
        ).addMigrations(RitelaDatabase.MIGRATION_4_5).build()
        try {
            assertEquals("ru", upgraded.profileSettings().snapshot()?.language)
            assertTrue(upgraded.partner().snapshot() == null)
            assertTrue(upgraded.dayLogs().snapshot().isEmpty())
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun delayedOpeningAndMalformedVectorBoundaries() {
        val stroke = DoodleStroke(listOf(DoodlePoint(.1f, .1f)), 0xffee5577, .01f)
        val doodle = Doodle(listOf(stroke), opening = DoodleOpening.AFTER_DATE, openAt = 5000)
        assertFalse(doodle.canOpen(1000, 4999))
        assertTrue(doodle.canOpen(1000, 5000))
        assertTrue(runCatching { DoodlePoint(Float.NaN, 0f) }.isFailure)
        assertTrue(
            runCatching {
                DoodleStroke(listOf(DoodlePoint(0f, 0f)), 0xffee5577, 0f)
            }.isFailure
        )
        assertTrue(runCatching { Doodle(listOf(stroke), delayDays = 366) }.isFailure)
        assertEquals(doodle, PartnerCodec.doodle(PartnerCodec.doodle(doodle)))
    }

    @Test fun physicsIsBoundedAndNegotiatedEdgesRespectBothOrientations() {
        val physics = HeartPhysics(count = 100)
        assertEquals(24, physics.hearts.size)
        repeat(600) { physics.step(400f, 800f, .016f, 20f, 20f, 100f) }
        physics.hearts.forEach {
            assertTrue(it.x.isFinite() && it.y.isFinite() && it.vx.isFinite() && it.vy.isFinite())
            assertTrue(kotlin.math.abs(it.vx) < 1500 && kotlin.math.abs(it.vy) < 1500)
        }
        assertEquals(ExchangeEdge.LEFT, PartnerBluetooth.receiveEdge(ExchangeEdge.RIGHT, 0, 0))
        assertEquals(ExchangeEdge.TOP, PartnerBluetooth.receiveEdge(ExchangeEdge.RIGHT, 1, 0))
        assertEquals(ExchangeEdge.LEFT, PartnerBluetooth.receiveEdge(ExchangeEdge.RIGHT, 1, 1))
    }

    @Test fun bluetoothFramesRejectOversizeAndTruncationWithoutPartialPayload() {
        val output = java.io.ByteArrayOutputStream()
        val data = "synthetic authenticated packet".toByteArray()
        PartnerBluetooth.writeFrame(output, data)
        assertArrayEquals(
            data,
            PartnerBluetooth.readFrame(java.io.ByteArrayInputStream(output.toByteArray()))
        )
        assertTrue(
            runCatching {
                PartnerBluetooth.readFrame(
                    java.io.ByteArrayInputStream(output.toByteArray().dropLast(1).toByteArray())
                )
            }.isFailure
        )
        val invalid = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(invalid).writeInt(Int.MAX_VALUE)
        assertTrue(
            runCatching {
                PartnerBluetooth.readFrame(java.io.ByteArrayInputStream(invalid.toByteArray()))
            }.isFailure
        )
    }

    @Test fun deletedGroupSourceNeedsFreshConsent() = runBlocking {
        var local = LocalPartnerKeys.create()
        val a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        val database = db()
        try {
            val repository = PartnerRepository(database, { local }, { local = it })
            repository.pair(repository.inspectInvitation(a.qr("A")))
            repository.pair(repository.inspectInvitation(b.qr("B")))
            repository.grant(a.identityId, ShareScope(setOf(ShareCategory.DIARY)))
            val contacts = repository.snapshot().contacts
            repository.merge(contacts[0].id, contacts[1].id, "Group", true)
            repository.remove(contacts[0].id)
            val retained = repository.snapshot().identities.single { it.id == a.identityId }
            assertFalse(retained.exchangeEnabled)
            assertTrue(retained.grants.categories.isEmpty())
            repository.pair(repository.inspectInvitation(a.qr("A")))
            val restored = repository.snapshot().identities.single { it.id == a.identityId }
            assertTrue(restored.exchangeEnabled)
            assertTrue(restored.grants.categories.isEmpty())
            assertTrue(
                repository.snapshot().contacts.any {
                    !it.group &&
                        a.identityId in it.identities
                }
            )
        } finally {
            database.close()
        }
    }

    @Test fun ownCycleForecastSnapshotKeepsCentralDatesAndUncertaintyWithoutChangingHistory() =
        runBlocking {
            val database = db()
            try {
                val today = LocalDate.now()
                val starts = (0..7).map { today.minusDays(7 * 28L).plusDays(it * 28L) }
                starts.forEach { date ->
                    database.periods().addIfSeparate(
                        app.ritela.data.PeriodEntity(
                            UUID.randomUUID().toString(),
                            date.toEpochDay(),
                            date.plusDays(4).toEpochDay().coerceAtMost(today.toEpochDay()),
                            0,
                            0
                        )
                    )
                }
                val original = database.periods().snapshot()
                val repository = repo(database, LocalPartnerKeys.create())
                val shared = repository.preview(ShareScope(setOf(ShareCategory.FORECASTS)))
                val expected = app.ritela.domain.analyzeCycles(
                    original.map {
                        it.toPeriod()
                    },
                    today
                )
                val forecast = shared.getJSONArray("forecasts").getJSONObject(0)
                assertEquals(
                    expected.forecasts.first().predictedStartDate.toEpochDay(),
                    forecast.getLong("start")
                )
                assertEquals(forecast.getLong("start") - 14, forecast.getLong("ovulation"))
                assertEquals(
                    expected.forecasts.first().lowerBound.toEpochDay() - 16,
                    forecast.getLong("ovulationLower")
                )
                assertEquals(original, database.periods().snapshot())
                assertFalse(shared.has("periods"))
                assertFalse(shared.has("days"))
            } finally {
                database.close()
            }
        }
}
