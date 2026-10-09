package app.ritela

import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import app.ritela.domain.DayLog
import app.ritela.domain.Doodle
import app.ritela.ui.DoodleEditor
import app.ritela.ui.DoodleViewer
import app.ritela.ui.HomeTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
class PartnerUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun home() {
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("nav-today").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun recipientModeHidesOwnCalendarWithoutDeletingRecordsAndCanReturn() {
        val app = compose.activity.application as RitelaApplication
        val date = LocalDate.now().minusDays(1)
        runBlocking { app.days.save(DayLog(date, note = "synthetic own")) }
        home()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("partner-show").performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("nav-partner").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("partner-recipient").performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("nav-calendar").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("nav-today").performClick()
        compose.onNodeWithTag("log-day").assertDoesNotExist()
        compose.onNodeWithText("Моё QR-приглашение").assertIsDisplayed()
        assertEquals("synthetic own", runBlocking { app.days.logs.first().single().note })
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("partner-recipient").performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("nav-calendar").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-today").performClick()
        compose.onNodeWithTag("log-day").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "en-rUS-w320dp-h640dp")
    fun doodleUndoRedoAndReadOnlyReveal() {
        home()
        var result: Doodle? = null
        compose.activity.setContent { HomeTheme { DoodleEditor({}, { result = it }) } }
        compose.onNodeWithTag("doodle-prepare").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("doodle-canvas").performScrollTo().performTouchInput {
            swipe(Offset(width * .2f, height * .2f), Offset(width * .7f, height * .6f), 300)
        }
        compose.onNodeWithTag("doodle-undo").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("doodle-prepare").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("doodle-redo").performScrollTo().performClick()
        compose.onNodeWithTag("doodle-prepare").performScrollTo().assertIsEnabled().performClick()
        assertTrue(requireNotNull(result).strokes.isNotEmpty())
        assertTrue(
            requireNotNull(result).strokes.all { stroke ->
                stroke.points.all {
                    it.x in 0f..1f &&
                        it.y in 0f..1f
                }
            }
        )
        var closed = false
        compose.activity.setContent {
            HomeTheme {
                DoodleViewer(requireNotNull(result), {
                    closed =
                        true
                })
            }
        }
        compose.onNodeWithText("Open postcard").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        assertTrue(closed)
    }
}
