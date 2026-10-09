package app.ritela

import app.ritela.domain.DayLog
import app.ritela.domain.JournalIcon
import app.ritela.domain.JournalLayout
import app.ritela.domain.Mood
import app.ritela.domain.Sex
import app.ritela.domain.calendarMarkers
import app.ritela.domain.forSelection
import app.ritela.domain.journalSelections
import app.ritela.domain.toggleSelection
import app.ritela.domain.withJournalSelections
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalPresentationTest {
    private val day = LocalDate.of(2026, 10, 9)

    @Test fun notesAndEveryNonSexRecordUseNoteUnlessUserChoseAnotherSymbol() {
        listOf(
            DayLog(day, mood = Mood.HAPPY),
            DayLog(day, note = "fixture"),
            DayLog(day, custom = mapOf("hidden" to setOf("tag")))
        ).forEach {
            assertEquals(JournalIcon.NOTE, it.calendarMarkers().record)
            assertFalse(it.calendarMarkers().intimacy)
            assertEquals(
                JournalIcon.STAR,
                it.copy(calendarIcon = JournalIcon.STAR).calendarMarkers().record
            )
        }
        assertEquals(
            JournalIcon.NOTE,
            DayLog(day, calendarIcon = JournalIcon.HEART).calendarMarkers().record
        )
        assertNull(DayLog(day).calendarMarkers().record)
    }

    @Test fun sexHeartIsIndependentOfNotesAndNoneIsNotAnEvent() {
        assertFalse(DayLog(day, sex = setOf(Sex.NONE)).calendarMarkers().intimacy)
        assertNull(DayLog(day, sex = setOf(Sex.NONE)).calendarMarkers().record)
        assertTrue(DayLog(day, sex = setOf(Sex.OTHER)).calendarMarkers().intimacy)
        assertNull(DayLog(day, sex = setOf(Sex.OTHER)).calendarMarkers().record)
        val both = DayLog(day, mood = Mood.CALM, sex = setOf(Sex.CONDOM)).calendarMarkers()
        assertEquals(JournalIcon.NOTE, both.record)
        assertTrue(both.intimacy)
        assertTrue(DayLog(day, custom = mapOf("sex" to setOf("custom"))).calendarMarkers().intimacy)
    }

    @Test fun noneIsProtectedRestoredForLegacyLayoutsAndMutuallyExclusive() {
        val layout = JournalLayout()
        assertTrue(runCatching { layout.removeTag("sex", "NONE") }.isFailure)
        val section = layout.sections.first { it.id == "sex" }
        val legacy = section.copy(tags = section.tags.filterNot { it.id == "NONE" })
        assertTrue(legacy.forSelection().tags.any { it.id == "NONE" })
        assertEquals(setOf("NONE"), section.toggleSelection(setOf("CONDOM"), "NONE"))
        assertEquals(setOf("ORAL"), section.toggleSelection(setOf("NONE"), "ORAL"))
    }

    @Test fun quickSelectionKeepsOtherAndHiddenFields() {
        val original =
            DayLog(
                day,
                mood = Mood.CALM,
                note = "fixture",
                calendarIcon = JournalIcon.STAR,
                custom = mapOf("hidden" to setOf("tag"))
            )
        val result = original.withJournalSelections(
            original.journalSelections() + ("discharge" to setOf("WATERY"))
        )
        assertEquals(original, result.copy(custom = original.custom))
        assertEquals(setOf("WATERY"), result.custom["discharge"])
        assertEquals(JournalIcon.MOOD, JournalLayout().sections.first().icon)
    }
}
