package app.ritela

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerDirectoryRow
import app.ritela.data.PartnerMessage
import app.ritela.data.RitelaDatabase
import app.ritela.data.sync.SyncLinkProgressRow
import app.ritela.data.sync.SyncMessageIntentRow
import app.ritela.data.sync.SyncOutboxRow
import app.ritela.data.sync.SyncQueueStatus
import app.ritela.data.sync.SyncSettingsRepository
import app.ritela.domain.PartnerDirectory
import app.ritela.domain.PartnerIdentity
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal suspend fun seedSyncSettings(db: RitelaDatabase) {
    db.syncState().ensureDefaults()
    db.openHelper.writableDatabase.execSQL("UPDATE sync_profile_state SET internetEnabled=1")
    db.partner().save(
        PartnerDirectoryRow(
            json = PartnerCodec.directory(
                PartnerDirectory(
                    identities = listOf(
                        PartnerIdentity(
                            "peer",
                            "synthetic public",
                            "Synthetic recipient",
                            emptyList(),
                            grants = ShareScope(setOf(ShareCategory.PERIODS))
                        )
                    )
                )
            )
        )
    )
    val body = JSONObject().put(
        "scope",
        PartnerCodec.scope(ShareScope(setOf(ShareCategory.PERIODS)))
    )
        .put("note", "PRIVATE_SENTINEL").toString()
    db.partner().insert(
        PartnerMessage("message", "peer", "device", true, "DATA", 1, 1, body, "cipher")
    )
    db.syncMessageIntents().insert(
        SyncMessageIntentRow(
            "message", "peer", "device", "DATA", 1,
            "context", "grants", "digest", "PENDING_WRAP"
        )
    )
    for ((index, state) in listOf("READY", "STORED", "RETRY_WAIT", "IN_FLIGHT").withIndex()) {
        db.syncQueue().insert(
            SyncOutboxRow(
                "record-$index", "peer", "device", 1, "hash", "DELTA",
                0, 1, index + 1L, byteArrayOf(1, 2, 3), state
            )
        )
    }
    db.syncQueue().progress(SyncLinkProgressRow("peer", "device", 1, 1, 4, "hash"))
    db.openHelper.writableDatabase.execSQL(
        "INSERT INTO sync_links(peerIdentity,peerDevice,automaticEnabled) " +
            "VALUES ('peer','device',1)"
    )
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private suspend fun database(action: suspend (RitelaDatabase) -> Unit) {
        val name = "settings-${UUID.randomUUID()}.db"
        val db = syncTestDatabase(context, name)
        try {
            action(db)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun openingDoesNotSeedConsentAndSnapshotExposesOnlyMetadata() = runBlocking {
        database { db ->
            val settings = SyncSettingsRepository(db)
            assertFalse(settings.snapshots.first().internetRequested)
            assertTrue(db.syncState().profileStates().isEmpty())
            seedSyncSettings(db)
            val value = settings.read()
            assertTrue(value.internetRequested)
            assertEquals(
                SyncQueueStatus.WAITING,
                value.entries.single {
                    it.key == "m:message"
                }.status
            )
            assertEquals("Synthetic recipient", value.entries.first().recipient)
            assertEquals(
                SyncQueueStatus.AWAITING_RECEIPT,
                value.entries.single {
                    it.key ==
                        "r:record-1"
                }.status
            )
            assertFalse(value.toString().contains("PRIVATE_SENTINEL"))
            val contents = requireNotNull(settings.contents("m:message"))
            assertEquals(setOf(ShareCategory.PERIODS), contents.categories)
            assertTrue(contents.note)
            assertFalse(contents.toString().contains("PRIVATE_SENTINEL"))
        }
    }

    @Test fun disableIsDurableAndRetainsHandedOffBytesOwnDataAndManualMessage() = runBlocking {
        database { db ->
            seedSyncSettings(db)
            val original = db.partner().message("message")
            val directory = db.partner().snapshot()
            SyncSettingsRepository(db).disable()
            assertFalse(SyncSettingsRepository(db).read().internetRequested)
            assertEquals("CANCELLED", db.syncMessageIntents().get("message")!!.state)
            assertEquals("STORED", db.syncQueue().packet("record-1")!!.state)
            assertTrue(db.syncQueue().packet("record-1")!!.envelope.isNotEmpty())
            assertTrue(
                db.syncQueue().packets().filter { it.packetId != "record-1" }
                    .all {
                        it.state == "CANCELLED" && it.envelope.isEmpty() && it.leaseToken == null
                    }
            )
            assertEquals(1, db.syncQueue().progress("peer", "device", 1)!!.requiresBaseline)
            assertEquals(original, db.partner().message("message"))
            assertEquals(directory, db.partner().snapshot())
        }
    }

    @Test fun failedDisableRollsBackFlagAndBothQueues() = runBlocking {
        database { db ->
            seedSyncSettings(db)
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER synthetic_disable_failure BEFORE UPDATE " +
                    "ON sync_outbox BEGIN SELECT RAISE(ABORT,'Synthetic'); END"
            )
            assertTrue(runCatching { SyncSettingsRepository(db).disable() }.isFailure)
            assertTrue(SyncSettingsRepository(db).read().internetRequested)
            assertEquals("PENDING_WRAP", db.syncMessageIntents().get("message")!!.state)
            assertEquals("READY", db.syncQueue().packet("record-0")!!.state)
            assertEquals(0, db.syncQueue().progress("peer", "device", 1)!!.requiresBaseline)
        }
    }

    @Test fun disableSurvivesReopen() = runBlocking {
        val name = "sync-disabled-${UUID.randomUUID()}.db"
        val db = syncTestDatabase(context, name)
        try {
            seedSyncSettings(db)
            SyncSettingsRepository(db).disable()
        } finally {
            db.close()
        }
        val reopened = syncTestDatabase(context, name)
        try {
            assertFalse(SyncSettingsRepository(reopened).read().internetRequested)
            assertEquals("CANCELLED", reopened.syncMessageIntents().get("message")!!.state)
            assertEquals("STORED", reopened.syncQueue().packet("record-1")!!.state)
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun cancelAndPauseAreSeparateAndNeverAcknowledgeOrDeleteStoredCopies() = runBlocking {
        database { db ->
            seedSyncSettings(db)
            val settings = SyncSettingsRepository(db)
            settings.cancel("m:message")
            settings.cancel("m:message")
            assertEquals("CANCELLED", db.syncMessageIntents().get("message")!!.state)
            assertFalse(db.partner().message("message")!!.delivered)
            assertEquals(1, db.syncState().links().single().automaticEnabled)
            settings.cancel("r:record-0")
            assertEquals(0, db.syncState().links().single().automaticEnabled)
            assertEquals("CANCELLED", db.syncQueue().packet("record-2")!!.state)
            settings.cancel("r:record-1")
            assertEquals("STORED", db.syncQueue().packet("record-1")!!.state)
        }
    }

    @Test fun profilesAreIsolatedAndAcknowledgedOrUnknownRowsCannotBeCancelled() = runBlocking {
        database { first ->
            database { second ->
                seedSyncSettings(first)
                seedSyncSettings(second)
                first.syncMessageIntents().finish("message", "ACKED")
                val settings = SyncSettingsRepository(first)
                settings.cancel("m:message")
                assertEquals(
                    SyncQueueStatus.DELIVERED,
                    settings.read().entries.single {
                        it.key ==
                            "m:message"
                    }.status
                )
                first.syncMessageIntents().finish("message", "UNRECOGNIZED")
                assertEquals(
                    SyncQueueStatus.UNAVAILABLE,
                    settings.read().entries.single {
                        it.key ==
                            "m:message"
                    }.status
                )
                settings.disable()
                assertTrue(SyncSettingsRepository(second).read().internetRequested)
                assertEquals("PENDING_WRAP", second.syncMessageIntents().get("message")!!.state)
            }
        }
    }

    @Test fun pausingOneRecipientDoesNotPauseAnotherOrCancelMessages() = runBlocking {
        database { db ->
            seedSyncSettings(db)
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO sync_links(peerIdentity,peerDevice,automaticEnabled) VALUES ('other','device',1)"
            )
            db.syncQueue().insert(
                SyncOutboxRow(
                    "other-packet", "other", "device", 1,
                    "hash", "DELTA", 0, 1, 1, byteArrayOf(9)
                )
            )
            SyncSettingsRepository(db).cancel("r:record-0")
            assertEquals(
                1,
                db.syncState().links().single {
                    it.peerIdentity == "other"
                }.automaticEnabled
            )
            assertEquals("READY", db.syncQueue().packet("other-packet")!!.state)
            assertEquals("PENDING_WRAP", db.syncMessageIntents().get("message")!!.state)
        }
    }
}
