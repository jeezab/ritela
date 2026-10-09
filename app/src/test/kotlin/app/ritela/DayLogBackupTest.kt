package app.ritela

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.BackupCategory
import app.ritela.data.BackupData
import app.ritela.data.BackupRepository
import app.ritela.data.BackupSelection
import app.ritela.data.DayLogEntity
import app.ritela.data.DayLogRepository
import app.ritela.data.PeriodEntity
import app.ritela.data.PeriodRepository
import app.ritela.data.ProfileSettings
import app.ritela.data.RitelaDatabase
import app.ritela.domain.DayLog
import app.ritela.domain.Pain
import app.ritela.domain.PeriodProblem
import app.ritela.domain.Sex
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DayLogBackupTest {
    private lateinit var database: RitelaDatabase
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 10, 5)
    private val password get() = "test-only-password-2026".toCharArray()

    @Before fun open() {
        database = Room.inMemoryDatabaseBuilder(context, RitelaDatabase::class.java).build()
    }

    @After fun close() {
        database.close()
    }

    @Test fun dayLogsDistinguishUnrecordedAndNoPainAndRejectFuture() = runBlocking {
        val repository = DayLogRepository(database.dayLogs(), clock)
        assertNull(repository.save(DayLog(today, headache = Pain.NONE)))
        assertEquals(Pain.NONE, repository.logs.first().single().headache)
        assertNull(repository.logs.first().single().cramps)
        assertNull(
            repository.save(
                DayLog(today, cramps = Pain.MODERATE, sex = setOf(Sex.CONDOM, Sex.ORAL))
            )
        )
        assertEquals(1, repository.logs.first().size)
        assertEquals(
            PeriodProblem.FUTURE_DATE,
            repository.save(DayLog(today.plusDays(1), headache = Pain.MILD))
        )
        assertEquals(
            PeriodProblem.STORAGE,
            repository.save(DayLog(today, sex = setOf(Sex.NONE, Sex.ORAL)))
        )
        assertEquals(PeriodProblem.STORAGE, repository.save(DayLog(today, note = "x".repeat(1001))))
        assertNull(repository.save(DayLog(today)))
        assertTrue(repository.logs.first().isEmpty())
    }

    @Test fun selectableArchivesContainOnlyRequestedCategoriesIncludingEmptyOnes() = runBlocking {
        val backup = BackupRepository(database, clock)
        database.profileSettings().save(ProfileSettings(language = "en"))
        database.dayLogs().save(DayLogEntity.from(DayLog(today, note = "synthetic")))
        for (category in BackupCategory.entries) {
            val data = backup.preview(
                backup.export(charArrayOf(), BackupSelection(setOf(category))),
                charArrayOf()
            )
            assertEquals(setOf(category), data.available)
            assertEquals(category == BackupCategory.SETTINGS, data.settings != null)
            assertEquals(category == BackupCategory.JOURNAL, data.layout != null)
            assertEquals(if (category == BackupCategory.DAYS) 1 else 0, data.logs.size)
        }
    }

    @Test fun selectedImportReplacesSettingsButRecordConflictRollsEverythingBack() = runBlocking {
        val backup = BackupRepository(database, clock)
        val original = DayLog(today, note = "original synthetic note")
        database.dayLogs().save(DayLogEntity.from(original))
        database.profileSettings().save(ProfileSettings(language = "ru"))
        val layout = app.ritela.domain.JournalLayout(title = "imported layout")
        val source = BackupData(
            emptyList(),
            listOf(DayLogEntity.from(original.copy(note = "conflicting note"))),
            layout,
            ProfileSettings(language = "en")
        )
        assertTrue(runCatching { backup.import(source) }.isFailure)
        assertEquals("ru", database.profileSettings().snapshot()?.language)
        assertNull(database.journal().snapshot())
        assertEquals(original, database.dayLogs().snapshot().single().toLog())
        backup.import(
            source,
            BackupSelection(setOf(BackupCategory.SETTINGS, BackupCategory.JOURNAL))
        )
        assertEquals("en", database.profileSettings().snapshot()?.language)
        assertEquals(layout, app.ritela.data.JournalRepository(database.journal()).layout.first())
        assertEquals(original, database.dayLogs().snapshot().single().toLog())
    }

    @Test fun legacyV2ArchiveHasNoInventedSettingsAndFutureVersionsAreRejected() = runBlocking {
        val backup = BackupRepository(database, clock)
        val envelope = org.json.JSONObject(backup.export(charArrayOf()).toString(Charsets.UTF_8))
        val root = envelope.getJSONObject("data")
        root.put("version", 2).remove("settings")
        root.remove("journal")
        val old = backup.preview(envelope.toString().toByteArray(), charArrayOf())
        assertEquals(setOf(BackupCategory.PERIODS, BackupCategory.DAYS), old.available)
        assertNull(old.settings)
        root.put("version", 4)
        assertTrue(
            runCatching {
                backup.preview(envelope.toString().toByteArray(), charArrayOf())
            }.isFailure
        )
    }

    @Test fun encryptedBackupRoundTripsIntoAnotherDatabaseAndMergesIdempotently() = runBlocking {
        val periods = PeriodRepository(database.periods(), clock)
        val days = DayLogRepository(database.dayLogs(), clock)
        periods.add(today.minusDays(10), today.minusDays(6))
        val log = DayLog(
            today,
            headache = Pain.NONE,
            cramps = Pain.MILD,
            sex = setOf(Sex.CONDOM, Sex.ORAL),
            note = "synthetic private note"
        )
        days.save(log)
        val backup = BackupRepository(database, clock)
        val secret = password
        val bytes = backup.export(secret)
        assertTrue(secret.all { it == '\u0000' })
        assertFalse(bytes.toString(Charsets.UTF_8).contains(log.note))
        val data = backup.preview(bytes, password)
        val target = Room.inMemoryDatabaseBuilder(context, RitelaDatabase::class.java).build()
        try {
            val importer = BackupRepository(target, clock)
            importer.import(data)
            importer.import(data)
            assertEquals(database.periods().snapshot(), target.periods().snapshot())
            assertEquals(log, target.dayLogs().snapshot().single().toLog())
        } finally {
            target.close()
        }
    }

    @Test fun wrongPasswordAndModifiedCiphertextCannotImport() = runBlocking {
        val repository = BackupRepository(database, clock)
        val bytes = repository.export(password)
        assertTrue(
            runCatching {
                repository.preview(bytes, "wrong-password".toCharArray())
            }.isFailure
        )
        val envelope = org.json.JSONObject(bytes.toString(Charsets.UTF_8))
        val data = envelope.getString("data")
        envelope.put("data", (if (data.first() == 'A') "B" else "A") + data.drop(1))
        assertTrue(
            runCatching {
                repository.preview(envelope.toString().toByteArray(Charsets.UTF_8), password)
            }.isFailure
        )
        assertTrue(database.periods().snapshot().isEmpty())
    }

    @Test fun unknownVersionExpensiveParametersAndOversizedFilesAreRejected() = runBlocking {
        val repository = BackupRepository(database, clock)
        val bytes = repository.export(password)
        val envelope = org.json.JSONObject(bytes.toString(Charsets.UTF_8))
        envelope.put("version", 2)
        assertTrue(
            runCatching {
                repository.preview(envelope.toString().toByteArray(Charsets.UTF_8), password)
            }.isFailure
        )
        envelope.put("version", 1).put("iterations", 2000000000)
        assertTrue(
            runCatching {
                repository.preview(envelope.toString().toByteArray(Charsets.UTF_8), password)
            }.isFailure
        )
        assertTrue(
            runCatching {
                repository.preview(ByteArray(BackupRepository.MAX_FILE_BYTES + 1), password)
            }.isFailure
        )
        assertTrue(repository.export("x".toCharArray()).isNotEmpty())
        assertTrue(database.periods().snapshot().isEmpty())
    }

    @Test fun conflictingImportRollsBackEarlierInsertsAndPreservesExistingNotes() = runBlocking {
        val periods = PeriodRepository(database.periods(), clock)
        periods.add(today.minusDays(10), today.minusDays(6))
        val before = database.periods().snapshot()
        val newPeriod = PeriodEntity(
            UUID.randomUUID().toString(),
            today.minusDays(30).toEpochDay(),
            today.minusDays(27).toEpochDay(),
            1,
            1
        )
        val conflict = before.single().copy(id = UUID.randomUUID().toString())
        val importer = BackupRepository(database, clock)
        assertTrue(
            runCatching {
                importer.import(BackupData(listOf(newPeriod, conflict), emptyList()))
            }.isFailure
        )
        assertEquals(before, database.periods().snapshot())
        val log = DayLogEntity.from(DayLog(today, note = "original"))
        database.dayLogs().save(log)
        assertTrue(
            runCatching {
                importer.import(BackupData(listOf(newPeriod), listOf(log.copy(note = "changed"))))
            }.isFailure
        )
        assertEquals(before, database.periods().snapshot())
        assertEquals(log, database.dayLogs().snapshot().single())
        assertTrue(
            runCatching {
                importer.import(
                    BackupData(emptyList(), listOf(log.copy(day = today.plusDays(1).toEpochDay())))
                )
            }.isFailure
        )
    }

    @Test fun migrationPreservesV1RecordsAndCreatesDayLogs() = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val id = UUID(0, 1).toString()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL(
                "CREATE TABLE periods (id TEXT NOT NULL PRIMARY KEY, startDay INTEGER NOT NULL, endDay INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
            )
            old.execSQL("CREATE UNIQUE INDEX index_periods_startDay ON periods(startDay)")
            old.execSQL(
                "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            old.execSQL(
                "INSERT INTO room_master_table VALUES (42, '689497641cd548e2aa4dc22831786ffa')"
            )
            old.execSQL(
                "INSERT INTO periods VALUES (?, ?, ?, 1, 1)",
                arrayOf<Any>(id, today.minusDays(5).toEpochDay(), today.toEpochDay())
            )
            old.version = 1
        }
        val upgraded = Room.databaseBuilder(context, RitelaDatabase::class.java, name)
            .addMigrations(
                RitelaDatabase.MIGRATION_1_2,
                RitelaDatabase.MIGRATION_2_3,
                RitelaDatabase.MIGRATION_3_4,
                RitelaDatabase.MIGRATION_4_5
            ).build()
        try {
            assertEquals(id, upgraded.periods().snapshot().single().id)
            assertTrue(upgraded.dayLogs().snapshot().isEmpty())
            upgraded.dayLogs().save(DayLogEntity.from(DayLog(today, headache = Pain.NONE)))
            assertEquals(Pain.NONE, upgraded.dayLogs().snapshot().single().toLog().headache)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun optionalPasswordAndCustomJournalRoundTripWithoutChangingOldRecords() = runBlocking {
        val layout = app.ritela.domain.JournalLayout(
            title = "My journal",
            sections = listOf(
                app.ritela.domain.JournalSection(
                    "custom-section",
                    "Sleep",
                    app.ritela.domain.JournalIcon.STAR,
                    listOf(app.ritela.domain.JournalTag("custom-tag", "Rested"))
                )
            )
        )
        app.ritela.data.JournalRepository(database.journal()).save(layout)
        val log =
            DayLog(
                today,
                note = "synthetic",
                custom = mapOf("custom-section" to setOf("custom-tag")),
                calendarIcon = app.ritela.domain.JournalIcon.STAR
            )
        database.dayLogs().save(DayLogEntity.from(log))
        val backup = BackupRepository(database, clock)
        for (text in listOf("", "x", "x".repeat(300))) {
            val exportedPassword = text.toCharArray()
            val bytes = backup.export(exportedPassword)
            assertTrue(exportedPassword.all { it == '\u0000' })
            val envelope = org.json.JSONObject(bytes.toString(Charsets.UTF_8))
            assertEquals(
                if (text.isEmpty()) "ritela.backup.plain" else "ritela.backup",
                envelope.getString("format")
            )
            val restored = backup.preview(bytes, text.toCharArray())
            assertEquals(layout, restored.layout)
            assertEquals(log, restored.logs.single().toLog())
            val target = Room.inMemoryDatabaseBuilder(context, RitelaDatabase::class.java).build()
            try {
                BackupRepository(target, clock).import(restored)
                assertEquals(
                    layout,
                    app.ritela.data.JournalRepository(target.journal()).layout.first()
                )
                assertEquals(log, target.dayLogs().snapshot().single().toLog())
            } finally {
                target.close()
            }
        }
        val plain = org.json.JSONObject(backup.export(charArrayOf()).toString(Charsets.UTF_8))
        plain.getJSONObject(
            "data"
        ).getJSONArray("days").getJSONObject(0).put("day", today.plusDays(1).toEpochDay())
        assertTrue(
            runCatching {
                backup.preview(plain.toString().toByteArray(), charArrayOf())
            }.isFailure
        )
    }

    @Test fun migrationFromV2KeepsDayLogsAndStartsWithDefaultJournal() = runBlocking {
        val name = "migration-v2-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL(
                "CREATE TABLE periods (id TEXT NOT NULL PRIMARY KEY, startDay INTEGER NOT NULL, endDay INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
            )
            old.execSQL("CREATE UNIQUE INDEX index_periods_startDay ON periods(startDay)")
            old.execSQL(
                "CREATE TABLE day_logs (day INTEGER NOT NULL PRIMARY KEY, headache TEXT, cramps TEXT, backache TEXT, flow TEXT, mood TEXT, energy TEXT, sex TEXT NOT NULL, note TEXT NOT NULL)"
            )
            old.execSQL(
                "INSERT INTO day_logs VALUES (?, 'NONE', NULL, NULL, NULL, NULL, NULL, 'CONDOM', 'synthetic')",
                arrayOf<Any>(today.toEpochDay())
            )
            old.version = 2
        }
        val upgraded = Room.databaseBuilder(context, RitelaDatabase::class.java, name)
            .addMigrations(
                RitelaDatabase.MIGRATION_2_3,
                RitelaDatabase.MIGRATION_3_4,
                RitelaDatabase.MIGRATION_4_5
            ).build()
        try {
            val log = upgraded.dayLogs().snapshot().single().toLog()
            assertEquals(Pain.NONE, log.headache)
            assertEquals(setOf(Sex.CONDOM), log.sex)
            assertEquals("synthetic", log.note)
            assertTrue(log.custom.isEmpty())
            assertNull(log.calendarIcon)
            assertEquals(
                app.ritela.domain.JournalLayout(),
                app.ritela.data.JournalRepository(upgraded.journal()).layout.first()
            )
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun legacyPayloadStillImportsWithoutCustomFields() = runBlocking {
        val original = DayLog(today, headache = Pain.NONE, sex = setOf(Sex.CONDOM), note = "legacy")
        database.dayLogs().save(DayLogEntity.from(original))
        val backup = BackupRepository(database, clock)
        val envelope = org.json.JSONObject(backup.export(charArrayOf()).toString(Charsets.UTF_8))
        val payload = envelope.getJSONObject("data")
        payload.put("version", 1).remove("journal")
        val day = payload.getJSONArray("days").getJSONObject(0)
        day.remove("custom")
        day.remove("calendarIcon")
        val restored = backup.preview(envelope.toString().toByteArray(), charArrayOf())
        assertNull(restored.layout)
        assertEquals(original, restored.logs.single().toLog())
    }
}
