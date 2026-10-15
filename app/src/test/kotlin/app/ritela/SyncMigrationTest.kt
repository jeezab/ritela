package app.ritela

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.RitelaDatabase
import app.ritela.data.sync.SyncStateResult
import app.ritela.data.sync.SyncStateStore
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
class SyncMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun allSixMigrationPathsPreserveEveryExistingValueAndDefaultToOff() = runBlocking {
        for (version in 1..6) {
            val name = "sync-from-$version-${UUID.randomUUID()}.db"
            val expected = mutableMapOf<String, Pair<List<String>, List<List<String?>>>>()
            legacySyncDatabase(context, name, version) { old, tables ->
                old.execSQL("INSERT INTO periods VALUES ('${UUID.randomUUID()}',1,5,10,20)")
                old.execSQL("INSERT INTO periods VALUES ('${UUID.randomUUID()}',30,NULL,30,30)")
                if (version >= 2) {
                    old.execSQL(
                        "INSERT INTO day_logs (day,sex,note) VALUES (1,'ORAL','synthetic sentinel')"
                    )
                }
                if (version >= 3) {
                    old.execSQL(
                        "UPDATE day_logs SET custom='{\"custom\":[\"tag\"]}',calendarIcon='NOTE'"
                    )
                    old.execSQL("INSERT INTO journal_layout VALUES (1,' {\"future\":true} ')")
                }
                if (version >= 4) old.execSQL("INSERT INTO profile_settings VALUES (1,'ru','DARK')")
                if (version >= 5) {
                    old.execSQL(
                        "INSERT INTO partner_directory VALUES " +
                            "(1,' {\"identities\":[],\"contacts\":[],\"future\":true} ')"
                    )
                    for (kind in listOf("DATA", "DOODLE", "ACK", "OPENED")) {
                        val fields = if (version == 6) ",hiddenFromChat" else ""
                        val hidden = if (version == 6) ",1" else ""
                        old.execSQL(
                            "INSERT INTO partner_messages " +
                                "(id,identity,device,outgoing,kind,created,received," +
                                "body,envelope,delivered,opened$fields) " +
                                "VALUES ('$kind','identity','device',0,'$kind'," +
                                "1,2,'{}','envelope',1,1$hidden)"
                        )
                    }
                    old.execSQL("UPDATE partner_messages SET body='',envelope='' WHERE kind='DATA'")
                }
                tables.forEach { table ->
                    old.rawQuery("SELECT * FROM `$table` ORDER BY rowid", null).use {
                        expected[table] = it.columnNames.toList() to syncCursorRows(it)
                    }
                }
            }
            val db = syncTestDatabase(context, name)
            try {
                val sqlite = db.openHelper.writableDatabase
                assertEquals(7, sqlite.version)
                expected.forEach { (table, columnsAndRows) ->
                    val columns = columnsAndRows.first.joinToString(",") { "`$it`" }
                    assertEquals(
                        "v$version $table",
                        columnsAndRows.second,
                        syncCursorRows(sqlite.query("SELECT $columns FROM `$table` ORDER BY rowid"))
                    )
                }
                val state = SyncStateStore(db).read() as SyncStateResult.Ready
                assertFalse(state.metadata.consent.internetEnabled)
                assertEquals(0L, state.metadata.authority.generation)
                assertTrue(state.metadata.links.isEmpty() && state.metadata.versions.isEmpty())
                assertEquals(
                    listOf(listOf("ok")),
                    syncCursorRows(sqlite.query("PRAGMA integrity_check"))
                )
                assertTrue(syncCursorRows(sqlite.query("PRAGMA foreign_key_check")).isEmpty())
                assertEquals(
                    listOf(listOf("1")),
                    syncCursorRows(
                        sqlite.query(
                            "SELECT COUNT(*) FROM sqlite_master WHERE type='index' " +
                                "AND name='index_periods_startDay'"
                        )
                    )
                )
            } finally {
                db.close()
            }
            val reopened = syncTestDatabase(context, name)
            try {
                assertEquals(7, reopened.openHelper.writableDatabase.version)
                assertTrue(SyncStateStore(reopened).read() is SyncStateResult.Ready)
            } finally {
                reopened.close()
                context.deleteDatabase(name)
            }
        }
    }

    @Test fun freshAndMigratedSchemasMatchAndEmptyProfileNeedsNoIdentity() = runBlocking {
        val migratedName = "sync-empty-${UUID.randomUUID()}.db"
        val freshName = "sync-fresh-${UUID.randomUUID()}.db"
        legacySyncDatabase(context, migratedName, 6)
        val migrated = syncTestDatabase(context, migratedName)
        val fresh = syncTestDatabase(context, freshName)
        try {
            for (table in listOf(
                "sync_profile_state",
                "sync_authority",
                "sync_links",
                "sync_record_versions"
            )) {
                assertEquals(
                    syncCursorRows(
                        migrated.openHelper.writableDatabase.query("PRAGMA table_info('$table')")
                    ),
                    syncCursorRows(
                        fresh.openHelper.writableDatabase.query("PRAGMA table_info('$table')")
                    )
                )
                assertEquals(
                    syncCursorRows(
                        migrated.openHelper.writableDatabase.query("PRAGMA index_list('$table')")
                    ),
                    syncCursorRows(
                        fresh.openHelper.writableDatabase.query("PRAGMA index_list('$table')")
                    )
                )
            }
            assertEquals(SyncStateStore(migrated).read(), SyncStateStore(fresh).read())
        } finally {
            migrated.close()
            fresh.close()
            context.deleteDatabase(migratedName)
            context.deleteDatabase(freshName)
        }
    }

    @Test fun failedMigrationRollsBackDdlDefaultsAndVersionAndCanBeRetried() {
        val name = "sync-rollback-${UUID.randomUUID()}.db"
        legacySyncDatabase(context, name, 6) { old, _ ->
            old.execSQL("INSERT INTO profile_settings VALUES (1,'en','LIGHT')")
        }
        val failure = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                RitelaDatabase.MIGRATION_6_7.migrate(db)
                error("Synthetic migration failure")
            }
        }
        val broken = androidx.room.Room.databaseBuilder(context, RitelaDatabase::class.java, name)
            .addMigrations(failure).build()
        try {
            assertTrue(runCatching { broken.openHelper.writableDatabase }.isFailure)
        } finally {
            broken.close()
        }
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        ).use {
            assertEquals(6, it.version)
            assertEquals(
                listOf(listOf("en")),
                syncCursorRows(it.rawQuery("SELECT language FROM profile_settings", null))
            )
            assertEquals(
                listOf(listOf("0")),
                syncCursorRows(
                    it.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE name LIKE 'sync_%'", null)
                )
            )
        }
        val retried = syncTestDatabase(context, name)
        try {
            assertEquals(7, retried.openHelper.writableDatabase.version)
        } finally {
            retried.close()
            context.deleteDatabase(name)
        }
    }
}
