package app.ritela

import app.ritela.domain.DayLog
import app.ritela.domain.JournalLayout
import app.ritela.domain.JournalSection
import app.ritela.domain.JournalTag
import app.ritela.domain.Pain
import app.ritela.domain.journalSelections
import app.ritela.domain.validateJournalLayout
import app.ritela.domain.withJournalSelections
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalLayoutTest {
    @Test fun removingAndReorderingTagsKeepsTheirIdentityAndRequiresOneTag() {
        val layout = JournalLayout()
        val reordered = layout.moveTag("headache", "SEVERE", "NONE")
        assertEquals("SEVERE", reordered.sections.first().tags.first().id)
        assertEquals(layout.sections.first().tags.toSet(), reordered.sections.first().tags.toSet())
        val one = layout.removeTag("headache", "NONE").removeTag("headache", "MILD")
            .removeTag("headache", "MODERATE")
        assertTrue(runCatching { one.removeTag("headache", "SEVERE") }.isFailure)
        assertTrue(validateJournalLayout(JournalLayout(sections = emptyList())))
        assertFalse(
            validateJournalLayout(
                JournalLayout(sections = listOf(JournalSection("x", tags = emptyList())))
            )
        )
    }

    @Test fun customTagsDoNotBecomeMedicalSymptomsAndHiddenSelectionsSurviveEditing() {
        val original = DayLog(
            LocalDate.of(2026, 10, 5),
            headache = Pain.MILD,
            custom = mapOf("custom-section" to setOf("custom-tag"))
        )
        val updated = original.withJournalSelections(
            original.journalSelections() +
                ("headache" to setOf("custom-headache"))
        )
        assertNull(updated.headache)
        assertEquals(setOf("custom-headache"), updated.custom["headache"])
        assertEquals(setOf("custom-tag"), updated.custom["custom-section"])
        assertFalse(updated.empty)
        assertFalse(
            validateJournalLayout(
                JournalLayout(
                    sections = listOf(
                        JournalSection("same", tags = listOf(JournalTag("tag"), JournalTag("tag")))
                    )
                )
            )
        )
    }
}
