package app.ritela

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.BackupCategory
import app.ritela.data.BackupSelection
import app.ritela.data.ProfileManager
import app.ritela.data.ProfileRegistry
import app.ritela.data.ThemeMode
import app.ritela.domain.DayLog
import app.ritela.domain.JournalLayout
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserProfilesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var manager: ProfileManager? = null

    @Before fun prepare() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun finish() {
        runBlocking { manager?.session?.value?.close() }
        Dispatchers.resetMain()
    }

    @Test fun identityNamesAndLastSelectionPersistIndependently() {
        val preferences = context.getSharedPreferences("catalog-test", Context.MODE_PRIVATE)
        val registry = ProfileRegistry(preferences)
        val first = registry.profiles.value.single()
        assertEquals("Я", first.name)
        registry.rename(first.id, " First ")
        val second = registry.add("Second")
        assertEquals(first.id, registry.activeId)
        registry.select(second.id)
        val restored = ProfileRegistry(preferences)
        assertEquals(second.id, restored.activeId)
        assertEquals("First", restored.profiles.value.first().name)
        assertTrue(runCatching { restored.add(" second ") }.isFailure)
        assertTrue(runCatching { restored.rename(first.id, " ") }.isFailure)
        assertEquals(2, restored.profiles.value.size)
    }

    @Test fun profilesIsolateRecordsLanguageJournalAndExports() = runBlocking {
        val profiles = ProfileManager(context).also { manager = it }
        var session = profiles.session.value
        session.settings.awaitReady()
        val day = LocalDate.now().minusDays(3)
        assertNull(session.periods.add(day, day.plusDays(1)))
        assertNull(session.days.save(DayLog(day, note = "first profile synthetic note")))
        session.journal.save(JournalLayout(title = "first layout"))
        session.settings.saveLanguage("en")
        withTimeout(5000) { session.settings.language.first { it == "en" } }
        val second = profiles.registry.add("Second")
        val oldToken = session.token
        profiles.switchTo(second.id)
        session = profiles.session.value
        assertFalse(oldToken == session.token)
        assertTrue(session.periods.periods.first().isEmpty())
        assertTrue(session.days.logs.first().isEmpty())
        assertEquals("system", session.settings.language.value)
        assertEquals(JournalLayout(), session.journal.layout.first())
        val emptyBackup = session.backups.preview(
            session.backups.export(charArrayOf()),
            charArrayOf()
        )
        assertTrue(emptyBackup.periods.isEmpty() && emptyBackup.logs.isEmpty())
        assertNull(session.days.save(DayLog(day, note = "second profile synthetic note")))
        session.settings.saveLanguage("ru")
        profiles.switchTo(ProfileRegistry.FIRST_ID)
        session = profiles.session.value
        assertEquals("en", session.settings.language.value)
        assertEquals("first layout", session.journal.layout.first().title)
        assertEquals("first profile synthetic note", session.days.logs.first().single().note)
        assertEquals(day, session.periods.periods.first().single().start)
        profiles.switchTo(second.id)
        assertEquals("ru", profiles.session.value.settings.language.value)
        assertEquals(
            "second profile synthetic note",
            profiles.session.value.days.logs.first().single().note
        )
    }

    @Test fun importTargetsOnlyTheSelectedProfileAndDoesNotRenameIt() = runBlocking {
        val profiles = ProfileManager(context).also { manager = it }
        val source = profiles.session.value
        source.settings.awaitReady()
        val day = LocalDate.now().minusDays(2)
        source.days.save(DayLog(day, note = "synthetic import"))
        source.settings.saveLanguage("en")
        val bytes = source.backups.export(charArrayOf())
        val target = profiles.registry.add("Target")
        profiles.switchTo(target.id)
        val session = profiles.session.value
        val data = session.backups.preview(bytes, charArrayOf())
        session.backups.import(data, BackupSelection(setOf(BackupCategory.DAYS)))
        assertEquals("synthetic import", session.days.logs.first().single().note)
        assertEquals("system", session.settings.language.value)
        assertEquals("Target", profiles.registry.profiles.value.last().name)
        assertTrue(session.periods.periods.first().isEmpty())
        profiles.switchTo(ProfileRegistry.FIRST_ID)
        assertEquals("synthetic import", profiles.session.value.days.logs.first().single().note)
    }

    @Test fun backupInProgressPreventsSwitchAndClosedSessionDoesNotReceiveNewData() = runBlocking {
        val profiles = ProfileManager(context).also { manager = it }
        val first = profiles.session.value
        first.settings.awaitReady()
        val second = profiles.registry.add("Second")
        first.backupActive.value = true
        assertTrue(runCatching { profiles.switchTo(second.id) }.isFailure)
        assertEquals(ProfileRegistry.FIRST_ID, profiles.registry.activeId)
        first.backupActive.value = false
        profiles.switchTo(second.id)
        assertEquals(second.id, profiles.registry.activeId)
        assertTrue(
            runCatching {
                first.days.save(DayLog(LocalDate.now(), note = "stale"))
            }.isFailure
        )
        assertTrue(profiles.session.value.days.logs.first().isEmpty())
    }

    @Test fun failedOpeningKeepsTheOriginalProfile() = runBlocking {
        val profiles = ProfileManager(context).also { manager = it }
        profiles.session.value.settings.awaitReady()
        val second = profiles.registry.add("Unreadable")
        val path = context.getDatabasePath("ritela-${second.id}.db")
        path.parentFile?.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null).use {
            it.version = 99
        }
        assertTrue(withTimeout(5000) { runCatching { profiles.switchTo(second.id) }.isFailure })
        assertEquals(ProfileRegistry.FIRST_ID, profiles.registry.activeId)
        assertEquals(ProfileRegistry.FIRST_ID, profiles.session.value.profileId)
    }

    @Test fun legacyV3DatabaseAndSettingsBelongToFirstProfileWithoutMovingFiles() = runBlocking {
        val path = context.getDatabasePath("ritela.db")
        path.parentFile?.mkdirs()
        val day = LocalDate.now().minusDays(5).toEpochDay()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL(
                "CREATE TABLE periods (id TEXT NOT NULL PRIMARY KEY, startDay INTEGER " +
                    "NOT NULL, endDay INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER " +
                    "NOT NULL)"
            )
            old.execSQL("CREATE UNIQUE INDEX index_periods_startDay ON periods(startDay)")
            old.execSQL(
                "CREATE TABLE day_logs (day INTEGER NOT NULL PRIMARY KEY, headache TEXT, " +
                    "cramps TEXT, backache TEXT, flow TEXT, mood TEXT, energy TEXT, sex TEXT " +
                    "NOT NULL, note TEXT NOT NULL, custom TEXT NOT NULL DEFAULT '{}', " +
                    "calendarIcon TEXT)"
            )
            old.execSQL(
                "CREATE TABLE journal_layout (id INTEGER NOT NULL PRIMARY KEY, config " +
                    "TEXT NOT NULL)"
            )
            old.execSQL(
                "INSERT INTO periods VALUES ('00000000-0000-0000-0000-000000000005', ?, " +
                    "?, 1, 1)",
                arrayOf(day, day + 2)
            )
            old.execSQL(
                "INSERT INTO day_logs (day, sex, note) VALUES (?, '', 'synthetic legacy')",
                arrayOf(day)
            )
            old.version = 3
        }
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("themeMode", ThemeMode.LIGHT.name).commit()
        val profiles = ProfileManager(context).also { manager = it }
        profiles.session.value.settings.awaitReady()
        assertEquals(ThemeMode.LIGHT, profiles.session.value.settings.theme.value)
        assertEquals(
            day,
            profiles.session.value.periods.periods.first().single().start.toEpochDay()
        )
        assertEquals("synthetic legacy", profiles.session.value.days.logs.first().single().note)
        assertTrue(path.exists())
        val second = profiles.registry.add("Second")
        profiles.switchTo(second.id)
        assertEquals(ThemeMode.SYSTEM, profiles.session.value.settings.theme.value)
        assertTrue(profiles.session.value.periods.periods.first().isEmpty())
    }
}
