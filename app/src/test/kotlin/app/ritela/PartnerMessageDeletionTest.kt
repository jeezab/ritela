package app.ritela

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.DayLogEntity
import app.ritela.data.LocalPartnerKeys
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerRepository
import app.ritela.data.PeriodEntity
import app.ritela.data.RitelaDatabase
import app.ritela.data.SavedDoodleLibrary
import app.ritela.data.partnerDataMessage
import app.ritela.domain.DayLog
import app.ritela.domain.Doodle
import app.ritela.domain.DoodlePoint
import app.ritela.domain.DoodleStroke
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import app.ritela.ui.conversationMessages
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
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
class PartnerMessageDeletionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.now()
    private fun period(day: LocalDate) = PeriodEntity(
        UUID.randomUUID().toString(),
        day.toEpochDay(),
        day.plusDays(4).toEpochDay(),
        1,
        1
    )
    private fun db(name: String) = Room.databaseBuilder(context, RitelaDatabase::class.java, name)
        .addMigrations(RitelaDatabase.MIGRATION_5_6, RitelaDatabase.MIGRATION_6_7).build()

    @Test fun hidingAndErasingSurviveRestartAndReplayWithoutChangingOwnRecords() = runBlocking {
        val nameA = "delete-a-${UUID.randomUUID()}.db"
        val nameB = "delete-b-${UUID.randomUUID()}.db"
        var aDb = db(nameA)
        var bDb = db(nameB)
        val a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        var tick = System.currentTimeMillis()
        var ra = PartnerRepository(aDb, { a }, {}, { tick++ })
        var rb = PartnerRepository(bDb, { b }, {}, { tick++ })
        val scope = ShareScope(setOf(ShareCategory.PERIODS))
        try {
            confirmedPair(ra, b, "B")
            confirmedPair(rb, a, "A")
            aDb.periods().addIfSeparate(period(today.minusDays(40)))
            val ownPeriod = period(today.minusDays(60))
            bDb.periods().addIfSeparate(ownPeriod)
            val ownDay = DayLogEntity.from(DayLog(today, note = "Synthetic own note"))
            bDb.dayLogs().save(ownDay)
            rb.confirmData(a.identityId, a.deviceId, scope)
            val first = ra.confirmData(b.identityId, b.deviceId, scope)
            rb.receive(first.envelope.toByteArray())
            aDb.periods().addIfSeparate(period(today.minusDays(10)))
            val second = ra.confirmData(b.identityId, b.deviceId, scope)
            rb.receive(second.envelope.toByteArray())
            rb.deleteMessage(second.id)
            var messages = bDb.partner().messages().first()
            assertFalse(
                conversationMessages(messages, setOf(a.identityId)).any {
                    it.id == second.id
                }
            )
            assertEquals(second.id, partnerDataMessage(messages, setOf(a.identityId))?.id)
            aDb.close()
            bDb.close()
            aDb = db(nameA)
            bDb = db(nameB)
            ra = PartnerRepository(aDb, { a }, {})
            rb = PartnerRepository(bDb, { b }, {})
            assertEquals(scope, ra.snapshot().identities.single().lastSelection)
            assertEquals(scope, rb.snapshot().identities.single().lastSelection)
            assertTrue(rb.receive(second.envelope.toByteArray()).duplicate)
            assertTrue(bDb.partner().message(second.id)!!.hiddenFromChat)
            rb.deleteMessage(second.id, eraseSharedData = true)
            messages = bDb.partner().messages().first()
            assertEquals(first.id, partnerDataMessage(messages, setOf(a.identityId))?.id)
            assertTrue(rb.receive(second.envelope.toByteArray()).duplicate)
            assertEquals("", bDb.partner().message(second.id)!!.body)
            rb.deleteMessage(first.id, eraseSharedData = true)
            assertNull(partnerDataMessage(bDb.partner().messages().first(), setOf(a.identityId)))
            assertEquals(listOf(ownPeriod), bDb.periods().snapshot())
            assertEquals(listOf(ownDay), bDb.dayLogs().snapshot())
            assertFalse(aDb.partner().message(first.id)!!.hiddenFromChat)
            assertTrue(aDb.partner().message(first.id)!!.body.isNotEmpty())
        } finally {
            aDb.close()
            bDb.close()
            context.deleteDatabase(nameA)
            context.deleteDatabase(nameB)
        }
    }

    @Test fun doodlesAndOutgoingMessagesAreErasedAndLateReceiptsCannotRestoreThem() = runBlocking {
        val aDb = Room.inMemoryDatabaseBuilder(context, RitelaDatabase::class.java).build()
        val bDb = Room.inMemoryDatabaseBuilder(context, RitelaDatabase::class.java).build()
        val a = LocalPartnerKeys.create()
        val b = LocalPartnerKeys.create()
        val ra = PartnerRepository(aDb, { a }, {})
        val rb = PartnerRepository(bDb, { b }, {})
        val profile = UUID.randomUUID().toString()
        val library = SavedDoodleLibrary(context, profile)
        try {
            confirmedPair(ra, b, "B")
            confirmedPair(rb, a, "A")
            val doodle =
                Doodle(listOf(DoodleStroke(listOf(DoodlePoint(.1f, .2f)), 0xffee839d, .02f)))
            library.save(doodle)
            val original = ra.prepareDoodle(b.identityId, b.deviceId, doodle)
            val received = rb.receive(original.envelope.toByteArray()).message
            val ack = rb.acknowledgement(received)
            val opened = rb.openDoodle(received.id)!!
            val selected = app.ritela.ui.PartnerViewModel(context, ra)
            kotlinx.coroutines.withTimeout(5000) { selected.busy.first { !it } }
            selected.packet.value = original
            selected.deleteMessage(original.id)
            kotlinx.coroutines.withTimeout(5000) { selected.busy.first { !it } }
            assertNull(selected.packet.value)
            ra.receive(ack.envelope.toByteArray())
            ra.receive(opened.envelope.toByteArray())
            val tombstone = aDb.partner().message(original.id)!!
            assertTrue(tombstone.hiddenFromChat)
            assertEquals("", tombstone.body)
            assertEquals("", tombstone.envelope)
            assertFalse(tombstone.delivered)
            assertFalse(tombstone.opened)
            assertTrue(runCatching { ra.retry(original.id) }.isFailure)
            rb.deleteMessage(received.id)
            assertEquals("", bDb.partner().message(received.id)!!.body)
            assertTrue(rb.receive(original.envelope.toByteArray()).duplicate)
            val data = ra.confirmData(
                b.identityId,
                b.deviceId,
                ShareScope(setOf(ShareCategory.PERIODS))
            )
            val incomingData = rb.receive(data.envelope.toByteArray()).message
            val dataAck = rb.acknowledgement(incomingData)
            ra.deleteMessage(data.id)
            ra.receive(dataAck.envelope.toByteArray())
            assertTrue(aDb.partner().message(data.id)!!.hiddenFromChat)
            assertEquals("", aDb.partner().message(data.id)!!.body)
            assertFalse(aDb.partner().message(data.id)!!.delivered)
            assertEquals(1, SavedDoodleLibrary(context, profile).items.value.size)
        } finally {
            aDb.close()
            bDb.close()
            android.util.AtomicFile(SavedDoodleLibrary.location(context, profile)).delete()
        }
    }

    @Test fun migrationFiveToCurrentPreservesMessagesAndDefaultsToVisible() = runBlocking {
        val name = "migration-six-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val schema = JSONObject(
            File("schemas/app.ritela.data.RitelaDatabase/5.json").readText()
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
            old.execSQL("INSERT INTO periods VALUES ('synthetic-period',1,5,1,1)")
            old.execSQL("INSERT INTO partner_directory VALUES (1,'{}')")
            old.execSQL(
                "INSERT INTO partner_messages VALUES ('synthetic-message','peer','phone',0,'DATA',1,2,'{}','synthetic-envelope',1,0)"
            )
            old.version = 5
        }
        val upgraded = db(name)
        try {
            assertEquals("ru", upgraded.profileSettings().snapshot()?.language)
            assertEquals(1, upgraded.periods().snapshot().size)
            assertEquals("{}", upgraded.partner().snapshot()?.json)
            val message = upgraded.partner().message("synthetic-message")!!
            assertFalse(message.hiddenFromChat)
            assertTrue(message.delivered)
            assertEquals("synthetic-envelope", message.envelope)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }
}
