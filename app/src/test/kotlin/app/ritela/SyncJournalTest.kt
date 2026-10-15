package app.ritela

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.BackupData
import app.ritela.data.BackupRepository
import app.ritela.data.DayLogEntity
import app.ritela.data.DayLogRepository
import app.ritela.data.JournalRepository
import app.ritela.data.PeriodEntity
import app.ritela.data.PeriodRepository
import app.ritela.data.RitelaDatabase
import app.ritela.domain.DayLog
import app.ritela.domain.JournalLayout
import app.ritela.domain.Mood
import app.ritela.domain.PeriodRange
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncJournalTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun memory() = Room.inMemoryDatabaseBuilder(context, RitelaDatabase::class.java).build()
    private val today = LocalDate.now()
    private fun period() = PeriodEntity(
        UUID.randomUUID().toString(),
        today.minusDays(60).toEpochDay(),
        today.minusDays(56).toEpochDay(),
        1,
        2
    )

    @Test fun periodMutationsTrackOnlySuccessfulWrites() = runBlocking {
        val db = memory()
        try {
            val repo = PeriodRepository(db.periods())
            assertNull(
                repo.addAll(
                    listOf(
                        PeriodRange(today.minusDays(40), today.minusDays(36)),
                        PeriodRange(today.minusDays(10), null)
                    )
                )
            )
            val open = repo.periods.first().first()
            val before = db.syncJournal().watermark()
            assertTrue(repo.add(today.minusDays(39), today.minusDays(38)) != null)
            assertEquals(before, db.syncJournal().watermark())
            assertNull(repo.finish(open.id, today.minusDays(6)))
            assertNull(repo.edit(open.id, today.minusDays(11), today.minusDays(7)))
            assertNull(repo.delete(open.id))
            val changes = db.syncJournal().changes(0, db.syncJournal().watermark())
            assertEquals(5, changes.size)
            assertTrue(changes.zipWithNext().all { (a, b) -> a.sequence < b.sequence })
            assertEquals(5, changes.map { it.changeId }.toSet().size)
            assertTrue(changes.last().deleted)
            assertEquals(open.id.toString(), changes.last().entityKey)
        } finally {
            db.close()
        }
    }

    @Test fun dayClearAndLayoutChangesShareTheJournalAndNoOpDoesNotDuplicate() = runBlocking {
        val db = memory()
        try {
            val repo = DayLogRepository(db.dayLogs())
            val log = DayLog(today.minusDays(1), mood = Mood.HAPPY, note = "Synthetic note")
            assertNull(repo.save(log))
            val watermark = db.syncJournal().watermark()
            assertNull(repo.save(log))
            assertEquals(watermark, db.syncJournal().watermark())
            assertNull(repo.save(DayLog(log.date)))
            assertNull(repo.save(DayLog(log.date)))
            JournalRepository(db.journal()).save(JournalLayout(title = "Synthetic title"))
            val rows = db.syncJournal().changes(0, db.syncJournal().watermark())
            assertEquals(listOf("DAY", "DAY", "LAYOUT"), rows.map { it.entityType })
            assertTrue(rows[1].deleted && rows[1].payload.isEmpty())
            assertTrue(db.dayLogs().snapshot().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test fun journalFailureRollsBackMedicalWritesAndBulkImportAsOneTransaction() = runBlocking {
        val db = memory()
        try {
            val sql = db.openHelper.writableDatabase
            sql.execSQL(
                "CREATE TRIGGER synthetic_journal_failure BEFORE INSERT ON sync_changes " +
                    "BEGIN SELECT RAISE(ABORT,'Synthetic failure'); END"
            )
            assertTrue(runCatching { db.periods().addIfSeparate(period()) }.isFailure)
            assertTrue(
                runCatching {
                    db.dayLogs().save(DayLogEntity.from(DayLog(today, note = "Synthetic")))
                }.isFailure
            )
            assertTrue(
                runCatching {
                    JournalRepository(db.journal()).save(JournalLayout())
                }.isFailure
            )
            assertTrue(db.periods().snapshot().isEmpty() && db.dayLogs().snapshot().isEmpty())
            assertNull(db.journal().snapshot())
            assertEquals(0L, db.syncJournal().watermark())
            sql.execSQL("DROP TRIGGER synthetic_journal_failure")
            val original = period()
            db.periods().addIfSeparate(original)
            val watermark = db.syncJournal().watermark()
            val invalid =
                BackupData(
                    listOf(original.copy(endDay = original.endDay!! + 1)),
                    emptyList(),
                    JournalLayout(title = "Must roll back")
                )
            assertTrue(runCatching { BackupRepository(db).import(invalid) }.isFailure)
            assertNull(db.journal().snapshot())
            assertEquals(listOf(original), db.periods().snapshot())
            assertEquals(watermark, db.syncJournal().watermark())
        } finally {
            db.close()
        }
    }

    @Test fun importTracksChangesWithoutEnablingSync() = runBlocking {
        val db = memory()
        try {
            val data = BackupData(
                listOf(period()),
                listOf(DayLogEntity.from(DayLog(today.minusDays(1), note = "Synthetic import"))),
                JournalLayout(title = "Imported")
            )
            BackupRepository(db).import(data)
            val changes = db.syncJournal().latest()
            assertEquals(setOf("PERIOD", "DAY", "LAYOUT"), changes.map { it.entityType }.toSet())
            val count = db.syncJournal().watermark()
            BackupRepository(db).import(data)
            // Existing import archives prior layout definitions on the second merge.
            assertTrue(
                db.syncJournal().changes(count, db.syncJournal().watermark())
                    .all { it.entityType == "LAYOUT" }
            )
            val merged = db.syncJournal().watermark()
            BackupRepository(db).import(data)
            assertEquals(merged, db.syncJournal().watermark())
            assertTrue(db.syncQueue().packets().isEmpty())
            assertTrue(db.syncState().profileStates().none { it.internetEnabled == 1 })
        } finally {
            db.close()
        }
    }

    @Test fun optOutHistoryIsBoundedBeforeAnyRecipientCheckpointExists() = runBlocking {
        val db = memory()
        try {
            repeat(256) {
                db.dayLogs().save(DayLogEntity.from(DayLog(today, note = "Synthetic revision $it")))
            }
            assertEquals(256L, db.syncJournal().watermark())
            assertEquals(1, db.syncJournal().changes(0, 256).size)
            assertEquals(256L, db.syncQueue().floor())
            assertEquals("Synthetic revision 255", db.dayLogs().snapshot().single().note)
            assertTrue(db.syncQueue().packets().isEmpty())
        } finally {
            db.close()
        }
    }
}
