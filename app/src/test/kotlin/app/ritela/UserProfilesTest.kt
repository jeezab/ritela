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
        assertEquals("qwerty", first.name)
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

    @Test fun legacyDefaultNameChangesOnceWithoutOverwritingCustomNames() {
        fun oldCatalog(name: String, collision: Boolean = false): ProfileRegistry {
            val prefs = context.getSharedPreferences("old-$name-$collision", Context.MODE_PRIVATE)
            val users = org.json.JSONArray().put(
                org.json.JSONObject().put("id", ProfileRegistry.FIRST_ID).put("name", name)
            )
            if (collision) {
                users.put(
                    org.json.JSONObject().put("id", "00000000-0000-0000-0000-000000000002")
                        .put("name", "qwerty")
                )
            }
            prefs.edit().putString(
                "catalog",
                org.json.JSONObject()
                    .put("active", ProfileRegistry.FIRST_ID).put("users", users).toString()
            ).commit()
            return ProfileRegistry(prefs)
        }
        val migrated = oldCatalog("Я")
        assertEquals("qwerty", migrated.profiles.value.single().name)
        migrated.rename(ProfileRegistry.FIRST_ID, "Я")
        assertEquals(
            "Я",
            ProfileRegistry(
                context.getSharedPreferences(
                    "old-Я-false",
                    Context.MODE_PRIVATE
                )
            ).profiles.value.single().name
        )
        assertEquals("Custom", oldCatalog("Custom").profiles.value.single().name)
        assertEquals(
            listOf("qwerty (2)", "qwerty"),
            oldCatalog("Я", true).profiles.value.map {
                it.name
            }
        )
    }

    @Test fun deletingInactiveUserRemovesOnlyTheirDatabaseAndKeepsCurrentData() = runBlocking {
        val profiles = ProfileManager(context).also { manager = it }
        val first = profiles.session.value
        first.settings.awaitReady()
        val day = LocalDate.now().minusDays(2)
        first.days.save(DayLog(day, note = "keep first"))
        first.settings.saveLanguage("en")
        val second = profiles.registry.add("Second")
        profiles.switchTo(second.id)
        profiles.session.value.days.save(DayLog(day, note = "remove second"))
        profiles.switchTo(ProfileRegistry.FIRST_ID)
        val current = profiles.session.value
        val removedPath = context.getDatabasePath("ritela-${second.id}.db")
        assertTrue(removedPath.exists())
        profiles.delete(second.id)
        assertTrue(profiles.session.value === current)
        assertEquals("keep first", current.days.logs.first().single().note)
        assertEquals("en", current.settings.language.value)
        assertTrue(context.getDatabasePath("ritela.db").exists())
        assertFalse(removedPath.exists())
        assertTrue(profiles.registry.pendingDeletions().isEmpty())
        assertEquals(1, profiles.registry.profiles.value.size)
        assertTrue(runCatching { profiles.switchTo(second.id) }.isFailure)
        val backup = current.backups.preview(current.backups.export(charArrayOf()), charArrayOf())
        assertEquals("keep first", backup.logs.single().note)
    }

    @Test fun deletingActiveFirstUserSwitchesToRemainingAndClearsLegacySettings() = runBlocking {
        val profiles = ProfileManager(context).also { manager = it }
        profiles.session.value.settings.awaitReady()
        val second = profiles.registry.add("Second")
        profiles.switchTo(second.id)
        val day = LocalDate.now().minusDays(1)
        profiles.session.value.periods.add(day, day)
        profiles.session.value.settings.saveLanguage("en")
        profiles.switchTo(ProfileRegistry.FIRST_ID)
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("themeMode", "LIGHT").commit()
        profiles.session.value.days.save(DayLog(day, note = "remove first"))
        profiles.delete(ProfileRegistry.FIRST_ID)
        assertEquals(second.id, profiles.registry.activeId)
        assertEquals(second.id, profiles.session.value.profileId)
        assertEquals(day, profiles.session.value.periods.periods.first().single().start)
        assertEquals("en", profiles.session.value.settings.language.value)
        assertFalse(context.getDatabasePath("ritela.db").exists())
        assertTrue(context.getSharedPreferences("settings", Context.MODE_PRIVATE).all.isEmpty())
        assertEquals(
            second.id,
            ProfileRegistry(
                context.getSharedPreferences("users", Context.MODE_PRIVATE)
            ).activeId
        )
    }

    @Test fun deletingLastUserCreatesFreshQwertyAndNeverReusesDeletedIdentity() = runBlocking {
        val profiles = ProfileManager(context).also { manager = it }
        profiles.session.value.settings.awaitReady()
        val day = LocalDate.now().minusDays(1)
        profiles.session.value.days.save(DayLog(day, note = "remove last"))
        profiles.session.value.journal.save(JournalLayout(title = "remove layout"))
        profiles.session.value.settings.saveLanguage("en")
        profiles.delete(ProfileRegistry.FIRST_ID)
        val fresh = profiles.registry.profiles.value.single()
        assertEquals("qwerty", fresh.name)
        assertFalse(fresh.id == ProfileRegistry.FIRST_ID)
        assertEquals(fresh.id, profiles.registry.activeId)
        assertTrue(profiles.session.value.days.logs.first().isEmpty())
        assertEquals("system", profiles.session.value.settings.language.value)
        assertEquals(JournalLayout(), profiles.session.value.journal.layout.first())
        profiles.session.value.days.save(DayLog(day, note = "remove again"))
        profiles.delete(fresh.id)
        assertFalse(context.getDatabasePath("ritela-${fresh.id}.db").exists())
        val replacement = profiles.registry.profiles.value.single()
        assertFalse(replacement.id == fresh.id || replacement.id == ProfileRegistry.FIRST_ID)
        profiles.session.value.close()
        val restored = ProfileManager(context).also { manager = it }
        restored.session.value.settings.awaitReady()
        assertEquals(replacement.id, restored.session.value.profileId)
        assertTrue(restored.session.value.days.logs.first().isEmpty())
        assertFalse(context.getDatabasePath("ritela.db").exists())
    }

    @Test fun deletingDuringBackupOrUsingUnknownIdentityDoesNotChangeUsers() = runBlocking {
        val profiles = ProfileManager(context).also { manager = it }
        val first = profiles.session.value
        first.settings.awaitReady()
        val second = profiles.registry.add("Second")
        val before = profiles.registry.profiles.value
        first.backupActive.value = true
        assertTrue(runCatching { profiles.delete(second.id) }.isFailure)
        first.backupActive.value = false
        assertTrue(runCatching { profiles.delete("../ritela.db") }.isFailure)
        assertEquals(before, profiles.registry.profiles.value)
        assertEquals(ProfileRegistry.FIRST_ID, profiles.registry.activeId)
    }

    @Test fun interruptedFileDeletionIsRetriedOnRestartWithoutRestoringUser() = runBlocking {
        val blocked = object : android.content.ContextWrapper(context) {
            override fun deleteDatabase(name: String): Boolean = false
        }
        val profiles = ProfileManager(blocked).also { manager = it }
        profiles.session.value.settings.awaitReady()
        val second = profiles.registry.add("Second")
        profiles.switchTo(second.id)
        profiles.session.value.days.save(DayLog(LocalDate.now(), note = "pending removal"))
        profiles.switchTo(ProfileRegistry.FIRST_ID)
        assertTrue(runCatching { profiles.delete(second.id) }.isFailure)
        assertEquals(listOf(second.id), profiles.registry.pendingDeletions())
        assertTrue(profiles.registry.profiles.value.none { it.id == second.id })
        assertTrue(context.getDatabasePath("ritela-${second.id}.db").exists())
        profiles.session.value.close()
        val restored = ProfileManager(context).also { manager = it }
        withTimeout(5000) {
            while (restored.registry.pendingDeletions().isNotEmpty()) kotlinx.coroutines.delay(10)
        }
        restored.session.value.settings.awaitReady()
        assertFalse(context.getDatabasePath("ritela-${second.id}.db").exists())
        assertEquals(ProfileRegistry.FIRST_ID, restored.registry.activeId)
        assertEquals(1, restored.registry.profiles.value.size)
    }
}
