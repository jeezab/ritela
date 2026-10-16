package app.ritela

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.LocalPartnerKeys
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerRepository
import app.ritela.data.RitelaDatabase
import app.ritela.domain.JournalLayout
import app.ritela.domain.JournalSection
import app.ritela.domain.JournalTag
import app.ritela.domain.PartnerDirectory
import app.ritela.domain.PartnerIdentity
import app.ritela.domain.PartnerSharing
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
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
class PartnerShareSelectionTest {
    @Test fun newSelectionUsesOnlyCurrentFieldsWithoutErasingHistoricalLayout() {
        val active = JournalSection("mood", tags = listOf(JournalTag("HAPPY")))
        val archived = JournalSection("backache", tags = listOf(JournalTag("SEVERE")))
        val layout = JournalLayout(sections = listOf(active), archivedSections = listOf(archived))
        val selected = PartnerSharing.current(
            ShareScope(setOf(ShareCategory.DIARY, ShareCategory.PERIODS)),
            layout
        )
        assertEquals(mapOf("mood" to setOf("HAPPY")), selected.tags)
        assertEquals(setOf("note"), selected.sections)
        assertTrue(ShareCategory.PERIODS in selected.categories)
        assertEquals(listOf(archived), layout.archivedSections)
        val old = ShareScope(setOf(ShareCategory.TAGS), tags = mapOf("mood" to setOf("REMOVED")))
        assertTrue(PartnerSharing.current(old, layout).categories.isEmpty())
        assertEquals(setOf("REMOVED"), old.tags["mood"])
        assertFalse(
            "NEW" in PartnerSharing.current(
                selected,
                layout.copy(sections = listOf(active.copy(tags = active.tags + JournalTag("NEW"))))
            )
                .tags.getValue("mood")
        )
    }

    @Test fun rememberedSectionsExcludeFutureTagsAndSections() {
        val mood = JournalSection("wellbeing", tags = listOf(JournalTag("happy")))
        val layout = JournalLayout(sections = listOf(mood))
        val selection = PartnerSharing.remembered(
            ShareScope(setOf(ShareCategory.SECTIONS), setOf("wellbeing")),
            layout
        )
        assertEquals(mapOf("wellbeing" to setOf("happy")), selection.tags)
        val changed = layout.copy(
            sections = listOf(
                mood.copy(tags = mood.tags + JournalTag("new")),
                JournalSection("custom", tags = listOf(JournalTag("private")))
            )
        )
        assertEquals(selection, PartnerSharing.remembered(selection, changed))
        assertFalse(
            selection.permits(
                ShareScope(setOf(ShareCategory.TAGS), tags = mapOf("wellbeing" to setOf("new")))
            )
        )
        assertFalse(
            selection.permits(
                ShareScope(setOf(ShareCategory.SECTIONS), setOf("sex", "note", "custom"))
            )
        )
        assertTrue(
            PartnerSharing.defaultDiary(
                ShareScope(),
                ShareScope(setOf(ShareCategory.SECTIONS), setOf("mood", "sex", "note"))
            ).sections ==
                setOf("mood")
        )
    }

    @Test fun legacyDirectoryDoesNotTreatGrantsAsLastSelection() {
        val identity =
            PartnerIdentity(
                "peer",
                "key",
                "Synthetic",
                emptyList(),
                ShareScope(setOf(ShareCategory.DIARY))
            )
        val encoded =
            JSONObject(PartnerCodec.directory(PartnerDirectory(identities = listOf(identity))))
        encoded.getJSONArray("identities").getJSONObject(0).remove("lastSelection")
        assertNull(PartnerCodec.directory(encoded.toString()).identities.single().lastSelection)
    }

    @Test fun confirmationPersistsPerPartnerAndFailureLeavesPreviousSelection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, RitelaDatabase::class.java).build()
        try {
            val keys = LocalPartnerKeys.create()
            val first = LocalPartnerKeys.create()
            val second = LocalPartnerKeys.create()
            val repository = PartnerRepository(db, { keys }, {})
            confirmedPair(repository, first, "First")
            confirmedPair(repository, second, "Second")
            val cycle = ShareScope(setOf(ShareCategory.PERIODS))
            val forecast = ShareScope(setOf(ShareCategory.FORECASTS))
            repository.preview(forecast)
            assertNull(repository.snapshot().identities.first().lastSelection)
            repository.confirmData(first.identityId, first.deviceId, cycle)
            repository.confirmData(second.identityId, second.deviceId, forecast)
            val reopened = PartnerRepository(db, { keys }, {})
            assertEquals(
                cycle,
                reopened.snapshot().identities.single {
                    it.id == first.identityId
                }.lastSelection
            )
            assertEquals(
                forecast,
                reopened.snapshot().identities.single {
                    it.id ==
                        second.identityId
                }.lastSelection
            )
            assertTrue(
                runCatching {
                    reopened.confirmData(first.identityId, "missing", forecast)
                }.isFailure
            )
            assertEquals(
                cycle,
                reopened.snapshot().identities.single {
                    it.id == first.identityId
                }.lastSelection
            )
            reopened.confirmData(first.identityId, first.deviceId, forecast)
            val partner = reopened.snapshot().identities.single { it.id == first.identityId }
            assertEquals(forecast, partner.lastSelection)
            assertEquals(forecast, partner.grants)
            assertTrue(
                runCatching {
                    reopened.prepareData(first.identityId, first.deviceId, cycle)
                }.isFailure
            )
        } finally {
            db.close()
        }
    }
}
