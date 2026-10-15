package app.ritela

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.LocalPartnerKeys
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerCrypto
import app.ritela.data.PartnerDirectoryRow
import app.ritela.data.PartnerKeyVault
import app.ritela.data.PartnerPreferences
import app.ritela.data.PartnerRepository
import app.ritela.data.PeriodEntity
import app.ritela.data.ProfileRegistry
import app.ritela.data.RitelaDatabase
import app.ritela.data.SavedDoodleLibrary
import app.ritela.data.sync.SyncStateResult
import app.ritela.data.sync.SyncStateStore
import app.ritela.domain.Doodle
import app.ritela.domain.DoodleOpening
import app.ritela.domain.DoodlePoint
import app.ritela.domain.DoodleStroke
import app.ritela.domain.SavedDoodle
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import java.io.File
import java.time.LocalDate
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncCompatibilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun memory() = Room.inMemoryDatabaseBuilder(context, RitelaDatabase::class.java).build()
    private fun period(daysAgo: Long): PeriodEntity {
        val start = LocalDate.now().minusDays(daysAgo).toEpochDay()
        return PeriodEntity(UUID.randomUUID().toString(), start, start + 4, 1, 2)
    }
    private fun doodle(points: Int = 1) = Doodle(
        listOf(DoodleStroke(List(points) { DoodlePoint(.2f, .3f) }, 0xff334455, .01f)),
        "Synthetic caption",
        DoodleOpening.AFTER_DAYS,
        delayHours = 1,
        delayMinutes = 2
    )

    @Test fun v6UpgradeRetainsVaultContactsCiphertextReceiptsAndTimedDoodles() = runBlocking {
        val profile = UUID.randomUUID().toString()
        val name = "sync-compatible-$profile.db"
        val wrapping = SecretKeySpec(PartnerCrypto.random(32), "AES")
        val vault = PartnerKeyVault(context, profile, { wrapping })
        val a = LocalPartnerKeys.create()
        val b = vault.loadOrCreate()
        val sourceA = memory()
        val sourceB = memory()
        var tick = System.currentTimeMillis()
        val ra = PartnerRepository(sourceA, { a }, { error("Unexpected key write") }, { tick })
        val rb = PartnerRepository(sourceB, { b }, { error("Unexpected key write") }, { tick })
        val saved = doodle(4001)
        val scope = ShareScope(setOf(ShareCategory.PERIODS))
        val own = period(90)
        var upgraded: RitelaDatabase? = null
        try {
            confirmedPair(ra, b, "Same")
            confirmedPair(rb, a, "Same")
            val pending = LocalPartnerKeys.create()
            rb.pair(rb.inspectInvitation(pending.invitation("Same")))
            val contacts = rb.snapshot().contacts
            rb.merge(contacts[0].id, contacts[1].id, "Together", group = true)
            sourceA.periods().addIfSeparate(period(40))
            sourceB.periods().addIfSeparate(own)
            val data = ra.confirmData(b.identityId, b.deviceId, scope, "Synthetic note", saved)
            rb.receive(data.envelope.toByteArray())
            val drawing = ra.prepareDoodle(b.identityId, b.deviceId, doodle())
            val received = rb.receive(drawing.envelope.toByteArray()).message
            ra.receive(rb.acknowledgement(received).envelope.toByteArray())
            assertTrue(sourceA.partner().message(drawing.id)!!.delivered)
            assertFalse(
                PartnerCodec.doodle(JSONObject(received.body)).canOpen(received.received, tick)
            )
            val erased = ra.prepareDoodle(b.identityId, b.deviceId, doodle())
            rb.receive(erased.envelope.toByteArray())
            rb.deleteMessage(erased.id)
            val hidden = ra.confirmData(b.identityId, b.deviceId, scope)
            rb.receive(hidden.envelope.toByteArray())
            rb.deleteMessage(hidden.id)
            // A pending device, group and future JSON property all survive byte-for-byte.
            val directory = JSONObject(sourceB.partner().snapshot()!!.json)
                .put("futureSyntheticField", true).toString()
            sourceB.partner().save(PartnerDirectoryRow(json = directory))
            // Seed the existing v1 file, then compare the persisted pre-upgrade representation.
            // Repeated AtomicFile replace behaviour on the Windows host is not this test's subject.
            val libraryFile = SavedDoodleLibrary.location(context, profile)
            libraryFile.writeText(
                SavedDoodleLibrary.encode(
                    List(15) {
                        SavedDoodle(
                            UUID.randomUUID().toString(),
                            if (it == 0) saved else doodle(),
                            if (it == 0) 2 else 0,
                            it.toLong()
                        )
                    }
                )
            )
            val expectedLibrary = SavedDoodleLibrary(context, profile).items.value
            assertEquals(15, expectedLibrary.size)
            val prefs = PartnerPreferences(context, profile)
            prefs.recipient(true)
            prefs.select("synthetic-chat")
            prefs.selectHome("synthetic-home")
            prefs.close()
            val preferencesFile =
                File(context.filesDir, "datastore/partner-$profile.preferences_pb")
            val keyFile = File(context.filesDir, "partner-$profile.keys")
            val beforeFiles = listOf(keyFile, libraryFile, preferencesFile).associateWith {
                it.readBytes()
            }
            val beforeRows = sourceB.partner().messages().first()
            copyLegacySyncRows(context, name, sourceB.openHelper.writableDatabase)
            upgraded = syncTestDatabase(context, name)
            val target = upgraded
            assertEquals(9, target.openHelper.writableDatabase.version)
            beforeFiles.forEach { (file, bytes) -> assertArrayEquals(bytes, file.readBytes()) }
            assertEquals(b, vault.loadOrCreate())
            assertEquals(directory, target.partner().snapshot()!!.json)
            assertEquals(beforeRows, target.partner().messages().first())
            assertEquals(expectedLibrary, SavedDoodleLibrary(context, profile).items.value)
            val decodedPrefs = PartnerPreferences(context, profile)
            try {
                val mode = decodedPrefs.mode.first { it.ready }
                assertTrue(mode.recipient && mode.show)
                assertEquals("synthetic-chat", mode.selected)
                assertEquals("synthetic-home", mode.homeSelected)
            } finally {
                decodedPrefs.close()
            }
            val migrated = PartnerRepository(
                target,
                vault::loadOrCreate,
                { error("Unexpected identity replacement") },
                { tick }
            )
            assertTrue(migrated.receive(data.envelope.toByteArray()).duplicate)
            assertTrue(migrated.receive(erased.envelope.toByteArray()).duplicate)
            assertEquals("", target.partner().message(erased.id)!!.body)
            assertTrue(target.partner().message(hidden.id)!!.hiddenFromChat)
            val newData = ra.confirmData(b.identityId, b.deviceId, scope, "After upgrade")
            assertFalse(migrated.receive(newData.envelope.toByteArray()).duplicate)
            assertEquals(newData.envelope, ra.retry(newData.id).envelope)
            tick = received.received + 62 * 60000L
            val opened = migrated.openDoodle(drawing.id)!!
            ra.receive(opened.envelope.toByteArray())
            assertTrue(sourceA.partner().message(drawing.id)!!.opened)
            assertEquals(received.received, target.partner().message(drawing.id)!!.received)
            assertEquals(listOf(own), target.periods().snapshot())
            assertFalse(
                (
                    SyncStateStore(
                        target
                    ).read() as SyncStateResult.Ready
                    ).metadata.consent.internetEnabled
            )
            assertTrue(target.syncState().links().isEmpty())
            assertEquals(b.identityId, migrated.identity().identityId)
            assertEquals(b.deviceId, migrated.identity().deviceId)
        } finally {
            upgraded?.close()
            sourceA.close()
            sourceB.close()
            context.deleteDatabase(name)
            File(context.filesDir, "partner-$profile.keys").delete()
            SavedDoodleLibrary.location(context, profile).delete()
            File(context.filesDir, "datastore/partner-$profile.preferences_pb").delete()
        }
    }

    @Test fun profileDatabaseNamesAndRegistryStayIsolatedAcrossUpgradeAndReopen() = runBlocking {
        val registryPrefs = context.getSharedPreferences("sync-registry", Context.MODE_PRIVATE)
        val registry = ProfileRegistry(registryPrefs)
        val first = registry.profiles.value.first()
        val second = registry.add("Second")
        val third = registry.add("Third")
        val profiles = listOf(first, second, third)
        val names = profiles.map {
            if (it.id == ProfileRegistry.FIRST_ID) "ritela.db" else "ritela-${it.id}.db"
        }
        val originalRegistry = registryPrefs.all.toMap()
        try {
            names.forEachIndexed { index, name ->
                legacySyncDatabase(context, name, 6) { old, _ ->
                    old.execSQL(
                        "INSERT INTO profile_settings VALUES (1,?,'DARK')",
                        arrayOf(
                            if (index ==
                                0
                            ) {
                                "ru"
                            } else {
                                "en"
                            }
                        )
                    )
                    old.execSQL(
                        "INSERT INTO day_logs (day,sex,note) VALUES (1,'',?)",
                        arrayOf("Synthetic profile $index")
                    )
                }
            }
            val firstDb = syncTestDatabase(context, names[0])
            try {
                SyncStateStore(firstDb).read()
                firstDb.openHelper.writableDatabase.execSQL(
                    "UPDATE sync_profile_state SET internetEnabled=1"
                )
            } finally {
                firstDb.close()
            }
            SQLiteDatabase.openDatabase(
                context.getDatabasePath(names[1]).path,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use {
                assertEquals(6, it.version)
            }
            names.forEachIndexed { index, name ->
                repeat(2) {
                    val db = syncTestDatabase(context, name)
                    try {
                        assertEquals(
                            "Synthetic profile $index",
                            db.dayLogs().snapshot().single().note
                        )
                        val metadata = (SyncStateStore(db).read() as SyncStateResult.Ready).metadata
                        assertEquals(index == 0, metadata.consent.internetEnabled)
                        assertEquals(0L, metadata.authority.generation)
                    } finally {
                        db.close()
                    }
                }
            }
            assertEquals(originalRegistry, registryPrefs.all)
            assertEquals(profiles, ProfileRegistry(registryPrefs).profiles.value)
        } finally {
            names.forEach { context.deleteDatabase(it) }
            registryPrefs.edit().clear().commit()
        }
    }
}
