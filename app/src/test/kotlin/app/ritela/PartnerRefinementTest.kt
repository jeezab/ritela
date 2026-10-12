package app.ritela

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.LocalPartnerKeys
import app.ritela.data.PartnerMessage
import app.ritela.data.PartnerRepository
import app.ritela.data.PartnerSnapshot
import app.ritela.data.RitelaDatabase
import app.ritela.data.partnerDataMessage
import app.ritela.data.partnerDoodles
import app.ritela.domain.CalendarDayKind
import app.ritela.domain.HeartPhysics
import app.ritela.domain.JournalLayout
import app.ritela.domain.JournalSection
import app.ritela.domain.JournalTag
import app.ritela.domain.PartnerSharing
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import app.ritela.ui.PartnerFileRequest
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PartnerRefinementTest {
    @Test fun granularSelectionRemainsWithinIndependentMaximums() {
        val mood = JournalSection("mood", tags = listOf(JournalTag("HAPPY"), JournalTag("CALM")))
        val section = ShareScope(setOf(ShareCategory.SECTIONS), setOf("mood"))
        val partial = PartnerSharing.tag(section, mood, "CALM", false)
        assertEquals(setOf("HAPPY"), partial.tags["mood"])
        assertTrue(section.permits(partial))
        assertFalse(
            ShareScope(
                setOf(ShareCategory.TAGS),
                tags = mapOf("mood" to setOf("CALM"))
            ).permits(partial)
        )
        assertFalse(section.permits(ShareScope(setOf(ShareCategory.SECTIONS), setOf("sex"))))
        assertFalse(section.permits(ShareScope(setOf(ShareCategory.DIARY))))
        assertEquals(ShareScope(), PartnerSharing.tag(partial, mood, "HAPPY", false))
        val explicit = PartnerSharing.explicit(
            ShareScope(setOf(ShareCategory.DIARY)),
            JournalLayout()
        )
        assertFalse(ShareCategory.DIARY in explicit.categories)
        assertTrue("note" in explicit.sections)
        assertTrue(ShareScope(setOf(ShareCategory.DIARY)).permits(explicit))
        val basic = PartnerSharing.defaultDiary(ShareScope(), explicit)
        assertFalse("sex" in basic.sections)
        assertFalse("note" in basic.sections)
        assertTrue("mood" in basic.sections)
    }

    @Test fun annualSnapshotKeepsReceivedValuesAndFactPrecedence() {
        val asOf = LocalDate.of(2028, 2, 28)
        val forecasts = JSONArray(
            (1..12).map {
                val start = asOf.plusMonths(it.toLong()).toEpochDay()
                JSONObject().put("start", start).put("lower", start - it).put("upper", start + it)
                    .put("ovulation", start - 14)
            }
        )
        val data = JSONObject().put(
            "forecasts",
            forecasts
        ).put("cycleDay", 9).put("asOf", asOf.toEpochDay())
            .put("duration", 5).put(
                "periods",
                JSONArray().put(
                    JSONObject().put("start", asOf.plusMonths(1).toEpochDay())
                        .put("end", asOf.plusMonths(1).plusDays(2).toEpochDay())
                )
            )
        val snapshot = PartnerSnapshot(data)
        assertEquals(12, snapshot.forecasts.size)
        assertEquals(9L, snapshot.cycleDay)
        assertNull(snapshot.cycleLength)
        assertEquals(asOf, snapshot.asOf)
        assertEquals(CalendarDayKind.OBSERVED, snapshot.dayInfo(asOf.plusMonths(1)).kind)
        assertEquals(
            CalendarDayKind.ESTIMATED_PERIOD,
            snapshot.dayInfo(asOf.plusMonths(2).plusDays(4)).kind
        )
        assertEquals(CalendarDayKind.NONE, snapshot.dayInfo(asOf.plusMonths(2).plusDays(5)).kind)
        assertEquals(
            CalendarDayKind.OVULATION_ESTIMATE,
            snapshot.dayInfo(asOf.plusMonths(3).minusDays(14)).kind
        )
        assertNotEquals(snapshot.next(asOf), snapshot.next(asOf.plusMonths(2)))
    }

    @Test fun acknowledgementsNeverBecomeDoodlesAndOldDataDoesNotLeakIntoNewSnapshot() {
        fun message(id: String, kind: String, time: Long, outgoing: Boolean = false) =
            PartnerMessage(id, "a", "device", outgoing, kind, time, time, "{}", "encrypted")
        val messages = listOf(
            message("doodle", "DOODLE", 1),
            message("ack", "ACK", 2),
            message("opened", "OPENED", 3),
            message("old", "DATA", 4),
            message("latest", "DATA", 5),
            message("own", "DATA", 6, true)
        )
        assertEquals(listOf("doodle"), partnerDoodles(messages, setOf("a")).map { it.id })
        assertEquals("latest", partnerDataMessage(messages, setOf("a"))?.id)
        assertNull(partnerDataMessage(messages, setOf("another")))
    }

    @Test fun fileIntentOnlyAcceptsGrantedContentUris() {
        val request = PartnerFileRequest()
        request.inspect(Intent(Intent.ACTION_VIEW, Uri.parse("file:///private/secret")))
        assertNull(request.uri.value)
        val content = Uri.parse("content://synthetic/shared/test.ritela")
        request.inspect(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, content))
        assertEquals(content, request.uri.value)
        request.inspect(Intent(Intent.ACTION_VIEW, content))
        assertEquals(content, request.uri.value)
    }

    @Test fun strongerGravityReactsToTiltAndKeepsHeartsBounded() {
        val physics = HeartPhysics(count = 1)
        physics.step(400f, 800f, 0f, 0f, 0f, 0f, null, false)
        val heart = physics.hearts.single()
        heart.x = 200f
        heart.y = 400f
        heart.vx = 0f
        heart.vy = 0f
        repeat(15) { physics.step(400f, 800f, .02f, 1f, 0f, 0f, null, false) }
        assertTrue(heart.vx > 75f)
        repeat(15) { physics.step(400f, 800f, .02f, -1f, 0f, 0f, null, false) }
        assertTrue(heart.vx < 0f)
        repeat(600) { physics.step(400f, 800f, .02f, 1f, 1f, 0f, null, false) }
        assertTrue(heart.x in 0f..400f && heart.y in 0f..800f)
    }

    @Test fun shortCyclesKeepTheWholeYearDuringAuthenticatedExchange() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, RitelaDatabase::class.java).build()
        val receiving = Room.inMemoryDatabaseBuilder(context, RitelaDatabase::class.java).build()
        try {
            val today = LocalDate.now()
            (0..8).forEach { index ->
                val start = today.minusDays(7L + index * 21L)
                database.periods().addIfSeparate(
                    app.ritela.data.PeriodEntity(
                        java.util.UUID.randomUUID().toString(),
                        start.toEpochDay(),
                        start.plusDays(4).toEpochDay(),
                        0,
                        0
                    )
                )
            }
            val a = LocalPartnerKeys.create()
            val b = LocalPartnerKeys.create()
            val sender = PartnerRepository(database, { a }, {})
            val receiver = PartnerRepository(receiving, { b }, {})
            confirmedPair(sender, b, "B")
            confirmedPair(receiver, a, "A")
            val scope = ShareScope(setOf(ShareCategory.FORECASTS))
            sender.grant(b.identityId, scope)
            val packet = sender.prepareData(b.identityId, b.deviceId, scope)
            val received = JSONObject(receiver.receive(packet.envelope.toByteArray()).message.body)
            assertTrue(received.getJSONArray("forecasts").length() > 16)
            assertFalse(received.has("periods"))
            assertFalse(received.has("days"))
            assertTrue(received.has("calculatedAt"))
            assertEquals("ritela-local-1", received.getString("algorithm"))
        } finally {
            database.close()
            receiving.close()
        }
    }

    @Test fun renameAndSettingsExportDoNotChangeTrustOrExposeSecrets() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, RitelaDatabase::class.java).build()
        try {
            val keys = LocalPartnerKeys.create()
            val other = LocalPartnerKeys.create()
            val repository = PartnerRepository(database, { keys }, {})
            confirmedPair(repository, other, "Original")
            repository.grant(other.identityId, ShareScope(setOf(ShareCategory.SETTINGS)))
            val before = repository.snapshot()
            repository.rename(before.contacts.single().id, "Local nickname")
            val after = repository.snapshot()
            assertEquals(before.identities, after.identities)
            assertEquals("Local nickname", after.contacts.single().name)
            val preview = repository.preview(ShareScope(setOf(ShareCategory.SETTINGS)))
            assertEquals(
                setOf("language", "theme"),
                preview.getJSONObject("settings").keys().asSequence().toSet()
            )
            assertFalse(preview.toString().contains(keys.identityPrivate))
            assertFalse(preview.has("days"))
            assertFalse(preview.has("forecasts"))
            assertTrue(
                runCatching {
                    repository.prepareData(
                        other.identityId,
                        other.deviceId,
                        ShareScope(setOf(ShareCategory.DIARY))
                    )
                }.isFailure
            )
        } finally {
            database.close()
        }
    }
}
