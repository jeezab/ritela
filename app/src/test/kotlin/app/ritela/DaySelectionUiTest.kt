package app.ritela

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.ritela.domain.DayLog
import app.ritela.domain.JournalIcon
import app.ritela.domain.Mood
import app.ritela.domain.Period
import app.ritela.domain.Sex
import app.ritela.domain.analyzeCycles
import app.ritela.domain.calendarMarkers
import app.ritela.ui.CalendarScreen
import app.ritela.ui.DayFieldEntry
import app.ritela.ui.DayLogEntry
import app.ritela.ui.PeriodUiState
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DaySelectionUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val today = LocalDate.of(2026, 10, 9)

    @Test fun quickFieldSelectsWithoutConfigurationAndKeepsOtherRecords() {
        val initial = DayLog(
            today,
            mood = Mood.CALM,
            sex = setOf(Sex.CONDOM),
            note = "fixture",
            calendarIcon = JournalIcon.STAR,
            custom = mapOf("hidden" to setOf("tag"))
        )
        var saved: DayLog? = null
        compose.activity.setContent {
            DayFieldEntry(initial, PeriodUiState(loading = false), "discharge", {}, { saved = it })
        }
        compose.onNodeWithTag("quick-entry-discharge").assertIsDisplayed()
        compose.onNodeWithTag("journal-edit-discharge").assertDoesNotExist()
        compose.onNodeWithTag("day-note").assertDoesNotExist()
        compose.onNodeWithTag("sex-CONDOM").assertDoesNotExist()
        compose.onNodeWithTag("discharge-WATERY").performClick().assertIsSelected()
        compose.onNodeWithTag("save-day").performClick()
        assertEquals(initial.custom + ("discharge" to setOf("WATERY")), saved?.custom)
        assertEquals(initial, saved?.copy(custom = initial.custom))
    }

    @Test fun fullEditorHasNoAutomaticOrHeartChoiceAndNoneCannotBeDeleted() {
        compose.activity.setContent {
            DayLogEntry(DayLog(today), PeriodUiState(loading = false), {}, {})
        }
        compose.onNodeWithTag("journal-edit-sex").performScrollTo().performClick()
        compose.onNodeWithTag("journal-remove-sex-NONE").assertDoesNotExist()
        compose.onNodeWithTag("journal-remove-sex-CONDOM").assertExists()
        compose.onNodeWithTag("journal-icon-NOTE").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("journal-icon-HEART").assertDoesNotExist()
        compose.onNodeWithText("Автоматически").assertDoesNotExist()
    }

    @Test fun savingOnlySexDoesNotCreateAnImplicitNoteMarker() {
        var saved: DayLog? = null
        compose.activity.setContent {
            DayLogEntry(DayLog(today), PeriodUiState(loading = false), {}, { saved = it })
        }
        compose.onNodeWithTag("sex-CONDOM").performScrollTo().performClick()
        compose.onNodeWithTag("save-day").performClick()
        assertNull(saved?.calendarIcon)
        assertNull(saved?.calendarMarkers()?.record)
        assertTrue(saved?.calendarMarkers()?.intimacy == true)
    }

    @Test fun savingLegacyHeartKeepsStoredChoiceButUsesNoteForRecord() {
        val initial = DayLog(today, note = "fixture", calendarIcon = JournalIcon.HEART)
        var saved: DayLog? = null
        compose.activity.setContent {
            DayLogEntry(initial, PeriodUiState(loading = false), {}, { saved = it })
        }
        compose.onNodeWithTag("journal-icon-NOTE").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("save-day").performClick()
        assertEquals(initial, saved)
        assertEquals(JournalIcon.NOTE, saved?.calendarMarkers()?.record)
    }

    @Test fun calendarShowsIndependentSymbolsAndFooterStaysBelowMonths() {
        val period = Period(UUID(0, 1), today.minusDays(6), today, Instant.EPOCH, Instant.EPOCH)
        val logs = listOf(
            DayLog(today, mood = Mood.CALM, sex = setOf(Sex.ORAL)),
            DayLog(today.minusDays(1), sex = setOf(Sex.NONE)),
            DayLog(today.minusDays(2), sex = setOf(Sex.CONDOM))
        )
        val state = PeriodUiState(
            loading = false,
            today = today,
            periods = listOf(period),
            dayLogs = logs,
            analysis = analyzeCycles(listOf(period), today)
        )
        compose.activity.setContent { CalendarScreen(PaddingValues(), state, {}, {}, {}) }
        compose.onNodeWithTag("day-record-icon-$today", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("day-sex-icon-$today", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(
            "day-sex-icon-${today.minusDays(1)}",
            useUnmergedTree = true
        ).assertDoesNotExist()
        compose.onNodeWithTag(
            "day-sex-icon-${today.minusDays(2)}",
            useUnmergedTree = true
        ).assertExists()
        val grid = compose.onNodeWithTag("month-grid").fetchSemanticsNode().boundsInRoot
        val footer = compose.onNodeWithTag("calendar-today").fetchSemanticsNode().boundsInRoot
        assertTrue(footer.top >= grid.bottom)
    }
}
