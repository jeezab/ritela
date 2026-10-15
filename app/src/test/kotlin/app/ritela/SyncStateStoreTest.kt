package app.ritela

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.PartnerDirectoryRow
import app.ritela.data.sync.SyncStateError
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
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncStateStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val peer = "a".repeat(64)
    private val device = UUID.randomUUID().toString()

    @Test fun defaultsAreIdempotentIndependentOfLegacyGrantsAndPersistAcrossReopen() = runBlocking {
        val name = "sync-default-${UUID.randomUUID()}.db"
        var db = syncTestDatabase(context, name)
        try {
            val rawDirectory = " {\"synthetic legacy grants\":true} "
            db.partner().save(PartnerDirectoryRow(json = rawDirectory))
            val first = SyncStateStore(db).read() as SyncStateResult.Ready
            repeat(3) { assertEquals(first, SyncStateStore(db).read()) }
            assertFalse(first.metadata.consent.internetEnabled)
            assertEquals(rawDirectory, db.partner().snapshot()!!.json)
            assertEquals(1, db.syncState().profileStates().size)
            assertEquals(1, db.syncState().authorities().size)
            db.openHelper.writableDatabase.execSQL(
                "UPDATE sync_profile_state SET internetEnabled=1"
            )
            db.close()
            db = syncTestDatabase(context, name)
            val reopened = SyncStateStore(db).read() as SyncStateResult.Ready
            assertTrue(reopened.metadata.consent.internetEnabled)
            assertEquals(0L, reopened.metadata.authority.generation)
            assertEquals(1, db.syncState().profileStates().single().internetEnabled)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun corruptionFailsClosedWithoutLeakingRawMetadataOrChangingOwnRecords() = runBlocking {
        val name = "sync-invalid-${UUID.randomUUID()}.db"
        val db = syncTestDatabase(context, name)
        val sentinel = "synthetic-secret-must-not-appear"
        try {
            val store = SyncStateStore(db)
            store.read()
            val sql = db.openHelper.writableDatabase
            sql.execSQL("INSERT INTO profile_settings VALUES (1,'ru','DARK')")
            for (update in listOf(
                "UPDATE sync_profile_state SET internetEnabled=2",
                "UPDATE sync_profile_state SET internetEnabled=-1",
                "UPDATE sync_authority SET state='$sentinel'",
                "UPDATE sync_authority SET generation=-1",
                "UPDATE sync_authority SET state='ACTIVE'",
                "INSERT INTO sync_profile_state VALUES (2,0)"
            )) {
                sql.execSQL(update)
                assertEquals(
                    SyncStateResult.Unavailable(SyncStateError.INVALID_METADATA),
                    store.read()
                )
                sql.execSQL("DELETE FROM sync_profile_state WHERE id!=1")
                sql.execSQL("UPDATE sync_profile_state SET internetEnabled=0")
                sql.execSQL("UPDATE sync_authority SET state='UNINITIALIZED',generation=0")
            }
            assertEquals("ru", db.profileSettings().snapshot()!!.language)
            assertTrue(ShadowLog.getLogs().none { it.msg.contains(sentinel) })
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun unknownProtocolsAndScopesAreNeverInterpretedAsConsent() = runBlocking {
        val name = "sync-link-${UUID.randomUUID()}.db"
        val db = syncTestDatabase(context, name)
        try {
            val store = SyncStateStore(db)
            store.read()
            val sql = db.openHelper.writableDatabase
            sql.execSQL(
                "INSERT INTO sync_links (peerIdentity,peerDevice) VALUES (?,?)",
                arrayOf(peer, device)
            )
            assertEquals(1, (store.read() as SyncStateResult.Ready).metadata.links.size)
            sql.execSQL("UPDATE sync_links SET protocolVersion=99")
            assertEquals(
                SyncStateResult.Unavailable(SyncStateError.UNSUPPORTED_PROTOCOL),
                store.read()
            )
            sql.execSQL("UPDATE sync_links SET protocolVersion=0,scopeJson='{}'")
            assertEquals(
                SyncStateResult.Unavailable(SyncStateError.UNSUPPORTED_PROTOCOL),
                store.read()
            )
            sql.execSQL("UPDATE sync_links SET scopeJson=NULL,automaticEnabled=1")
            assertEquals(SyncStateResult.Unavailable(SyncStateError.INVALID_METADATA), store.read())
            sql.execSQL("UPDATE sync_links SET automaticEnabled=2")
            assertEquals(SyncStateResult.Unavailable(SyncStateError.INVALID_METADATA), store.read())
            assertEquals(0, db.syncState().profileStates().single().internetEnabled)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun structuralAuthorityStillRequiresCryptographicVerification() = runBlocking {
        val name = "sync-author-${UUID.randomUUID()}.db"
        var db = syncTestDatabase(context, name)
        try {
            SyncStateStore(db).read()
            db.openHelper.writableDatabase.execSQL(
                "UPDATE sync_authority SET ownerIdentity=?,authorDevice=?,generation=1," +
                    "state='ACTIVE',grantJson='{}',grantDigest=?",
                arrayOf(peer, device, "b".repeat(64))
            )
            assertEquals(
                SyncStateResult.Unavailable(SyncStateError.UNVERIFIED_AUTHORITY),
                SyncStateStore(db).read()
            )
            db.close()
            db = syncTestDatabase(context, name)
            assertEquals(
                SyncStateResult.Unavailable(SyncStateError.UNVERIFIED_AUTHORITY),
                SyncStateStore(db).read()
            )
            assertEquals(1L, db.syncState().authorities().single().generation)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun recordTombstonesKeepKeysAndRevisionsAndRejectCorruptVersions() = runBlocking {
        val name = "sync-version-${UUID.randomUUID()}.db"
        val db = syncTestDatabase(context, name)
        try {
            val store = SyncStateStore(db)
            store.read()
            val sql = db.openHelper.writableDatabase
            val id = UUID.randomUUID().toString()
            sql.execSQL(
                "INSERT INTO sync_record_versions " +
                    "(ownerIdentity,entityType,entityKey,generation,revision,isDeleted) " +
                    "VALUES (?,?,?,1,2,1)",
                arrayOf(peer, "PERIOD", id)
            )
            val version = (store.read() as SyncStateResult.Ready).metadata.versions.single()
            assertTrue(version.deleted)
            assertEquals(id, version.key.key)
            assertEquals(2L, version.revision.value)
            for (update in listOf(
                "revision=0",
                "generation=-1",
                "isDeleted=2",
                "entityType='UNKNOWN'"
            )) {
                sql.execSQL("UPDATE sync_record_versions SET $update")
                assertEquals(
                    SyncStateResult.Unavailable(SyncStateError.INVALID_METADATA),
                    store.read()
                )
                sql.execSQL(
                    "UPDATE sync_record_versions SET revision=2,generation=1," +
                        "isDeleted=1,entityType='PERIOD'"
                )
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun defaultSeedingIsOneTransactionEvenWhenTheSecondInsertFails() = runBlocking {
        val name = "sync-default-rollback-${UUID.randomUUID()}.db"
        val db = syncTestDatabase(context, name)
        try {
            val sql = db.openHelper.writableDatabase
            sql.execSQL(
                "CREATE TRIGGER synthetic_seed_failure BEFORE INSERT ON sync_authority " +
                    "BEGIN SELECT RAISE(ABORT, 'Synthetic failure'); END"
            )
            assertTrue(runCatching { SyncStateStore(db).read() }.isFailure)
            assertTrue(db.syncState().profileStates().isEmpty())
            sql.execSQL("DROP TRIGGER synthetic_seed_failure")
            assertTrue(SyncStateStore(db).read() is SyncStateResult.Ready)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
