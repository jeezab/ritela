package app.ritela

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.BackupCategory
import app.ritela.data.BackupData
import app.ritela.data.BackupRepository
import app.ritela.data.BackupSelection
import app.ritela.data.DayLogEntity
import app.ritela.data.JournalCodec
import app.ritela.data.JournalRepository
import app.ritela.data.RitelaDatabase
import app.ritela.domain.DayLog
import app.ritela.domain.JournalLayout
import app.ritela.domain.JournalTag
import app.ritela.domain.Sex
import app.ritela.domain.calendarMarkers
import app.ritela.domain.forSelection
import app.ritela.domain.historySections
import app.ritela.domain.journalSelections
import app.ritela.domain.restoreDefaultTags
import app.ritela.domain.sexStatusTags
import app.ritela.domain.toggleSelection
import app.ritela.domain.validateDayLog
import app.ritela.domain.withHistoryFrom
import app.ritela.domain.withJournalSelections
import java.time.LocalDate
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JournalChangesTest {
    private val day = LocalDate.of(2026, 10, 8)
    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        RitelaDatabase::class.java
    ).allowMainThreadQueries().build()

    @Test fun multipleMoodsPersistThroughDatabaseAndEditingAnotherField() = runBlocking {
        val db = database()
        try {
            val choices = mapOf("mood" to setOf("HAPPY", "LOW", "custom-mood"))
            val original = DayLog(day, note = "fixture").withJournalSelections(choices)
            db.dayLogs().save(DayLogEntity.from(original))
            val loaded = db.dayLogs().snapshot().single().toLog()
            assertEquals(choices["mood"], loaded.journalSelections()["mood"])
            val updated = loaded.withJournalSelections(
                loaded.journalSelections() +
                    ("discharge" to setOf("DRY"))
            )
            assertEquals(choices["mood"], updated.journalSelections()["mood"])
            val backup = BackupRepository(db)
            val restored = backup.preview(backup.export(charArrayOf()), charArrayOf())
            assertEquals(
                choices["mood"],
                restored.logs.single().toLog().journalSelections()["mood"]
            )
        } finally {
            db.close()
        }
    }

    @Test fun primarySexTagsAreExclusiveButAdditionalTagsRemainAndDefaultsAreProtected() {
        val layout = JournalLayout()
        val sex = layout.sections.first { it.id == "sex" }.forSelection()
        var chosen = sex.toggleSelection(setOf("NONE"), "ORAL")
        assertEquals(setOf("NONE", "ORAL"), chosen)
        chosen = sex.toggleSelection(chosen, "CONDOM")
        assertEquals(setOf("CONDOM", "ORAL"), chosen)
        chosen = sex.toggleSelection(chosen, "NO_BARRIER")
        assertEquals(setOf("NO_BARRIER", "ORAL"), chosen)
        assertTrue(validateDayLog(DayLog(day, sex = setOf(Sex.NONE, Sex.ORAL)), day))
        sexStatusTags.forEach { assertTrue(runCatching { layout.removeTag("sex", it) }.isFailure) }
        assertFalse(sex.tags.any { it.id == "VAGINAL" })
        assertTrue(sex.tags.single { it.id == "MASTURBATION" }.withoutPenetration)
        assertFalse(sex.tags.single { it.id == "ORAL" }.withoutPenetration)
    }

    @Test fun heartOutlineAndFillRespectCustomMetadataAndNoneIsNotAHeart() {
        val base = JournalLayout()
        val sex = base.sections.first { it.id == "sex" }
        val custom = base.replace(sex.copy(tags = sex.tags + JournalTag("custom", "fixture", true)))
        assertFalse(DayLog(day, sex = setOf(Sex.NONE)).calendarMarkers(custom).intimacy)
        val only = DayLog(day, sex = setOf(Sex.NONE, Sex.MASTURBATION)).calendarMarkers(custom)
        assertTrue(only.intimacy)
        assertFalse(only.filledHeart)
        assertFalse(
            DayLog(
                day,
                custom = mapOf("sex" to setOf("custom"))
            ).calendarMarkers(custom).filledHeart
        )
        assertTrue(
            DayLog(day, sex = setOf(Sex.MASTURBATION, Sex.ORAL)).calendarMarkers(custom).filledHeart
        )
        assertTrue(
            DayLog(
                day,
                custom = mapOf("sex" to setOf("missing"))
            ).calendarMarkers(custom).filledHeart
        )
    }

    @Test fun deletedAndResetTagsKeepLabelsAndRecordsAcrossBackupAndLayoutImport() = runBlocking {
        val db = database()
        try {
            val journal = JournalRepository(db.journal())
            val tagId = "4bb5c7c7-5f34-4dd0-a48e-6ab3e5d95725"
            val layout = JournalLayout()
            val discharge = layout.sections.first { it.id == "discharge" }
            val edited = layout.replace(
                discharge.copy(
                    tags =
                        discharge.tags + JournalTag(tagId, "fixture tag")
                )
            )
            journal.save(edited)
            val log = DayLog(day, custom = mapOf("discharge" to setOf(tagId)))
            db.dayLogs().save(DayLogEntity.from(log))
            journal.save(edited.removeTag("discharge", tagId))
            assertEquals(
                "fixture tag",
                journal.layout.first().historySections()
                    .first { it.id == "discharge" }.tags.single { it.id == tagId }.title
            )
            val backup = BackupRepository(db)
            val data = backup.preview(backup.export(charArrayOf()), charArrayOf())
            assertEquals(log, data.logs.single().toLog())
            assertEquals(
                "fixture tag",
                data.layout!!.historySections()
                    .first { it.id == "discharge" }.tags.single { it.id == tagId }.title
            )
            backup.import(
                BackupData(emptyList(), emptyList(), JournalLayout()),
                BackupSelection(setOf(BackupCategory.JOURNAL))
            )
            assertEquals(log, db.dayLogs().snapshot().single().toLog())
            assertEquals(
                "fixture tag",
                journal.layout.first().historySections()
                    .first { it.id == "discharge" }.tags.single { it.id == tagId }.title
            )
            assertTrue(
                JournalLayout(sections = emptyList()).restoreDefaultTags().sections.isEmpty()
            )
            val restored = edited.restoreDefaultTags()
            assertFalse(
                restored.sections.first {
                    it.id == "discharge"
                }.tags.any { it.id == tagId }
            )
            assertTrue(
                restored.historySections().first { it.id == "discharge" }.tags.any {
                    it.id ==
                        tagId
                }
            )
        } finally {
            db.close()
        }
    }

    @Test fun legacyRemovedTagsAndLayoutsDecodeWithoutRewritingHistoricalSelections() {
        val defaults = JournalLayout()
        val discharge = defaults.sections.first { it.id == "discharge" }
        val legacy = defaults.replace(discharge.copy(tags = discharge.tags + JournalTag("UNUSUAL")))
        val encoded = JSONObject(JournalCodec.encode(legacy))
        encoded.remove("archivedSections")
        val sections = encoded.getJSONArray("sections")
        for (i in 0 until sections.length()) {
            val tags = sections.getJSONObject(i).getJSONArray("tags")
            for (j in 0 until tags.length()) tags.getJSONObject(j).remove("withoutPenetration")
        }
        val decoded = JournalCodec.decode(encoded.toString())
        assertFalse(
            decoded.sections.first { it.id == "discharge" }.forSelection().tags.any {
                it.id ==
                    "UNUSUAL"
            }
        )
        assertTrue(
            decoded.sections.first {
                it.id == "sex"
            }.tags.single { it.id == "MASTURBATION" }.withoutPenetration
        )
        val log =
            DayLog(day, sex = setOf(Sex.VAGINAL), custom = mapOf("discharge" to setOf("UNUSUAL")))
        assertEquals(log, DayLogEntity.from(log).toLog())
        assertEquals(log, log.withJournalSelections(log.journalSelections()))
    }

    @Test fun legacyLayoutReorderPersistsThroughArchiveMerging() = runBlocking {
        val db = database()
        try {
            val repository = JournalRepository(db.journal())
            val initial = JournalLayout(sections = app.ritela.domain.builtInJournalSections())
            repository.save(initial)
            val editable = initial.copy(sections = initial.sections.map { it.forSelection() })
                .withHistoryFrom(initial)
            val reordered = editable.moveTag("headache", "SEVERE", "MODERATE")
            repository.save(reordered)
            assertEquals("SEVERE", repository.layout.first().sections.first().tags[2].id)
        } finally {
            db.close()
        }
    }
}
