package app.ritela

import androidx.activity.ComponentDialog
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import app.ritela.domain.Period
import app.ritela.ui.PeriodEntry
import app.ritela.ui.PeriodUiState
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
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
class PeriodEntryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val today = LocalDate.of(2026, 10, 9)
    private fun record(start: LocalDate, end: LocalDate) =
        Period(UUID(0, start.toEpochDay()), start, end, Instant.EPOCH, Instant.EPOCH)

    @Test fun reverseHistoricRangeIsClosedAndOngoingIsDisabledBeforeNextPeriod() {
        val first = LocalDate.of(2026, 8, 1)
        val next = record(first.plusMonths(1), first.plusMonths(1).plusDays(4))
        var saved: Pair<LocalDate, LocalDate?>? = null
        compose.activity.setContent {
            PeriodEntry(
                PeriodUiState(periods = listOf(next), today = today, loading = false),
                {},
                { start, end -> saved = start to end },
                initialStart = first.plusDays(2),
                onSaveBatch = { saved = it.single().start to it.single().end }
            )
        }
        compose.onNodeWithTag("entry-day-${first.plusDays(2)}").performClick()
        compose.onNodeWithTag("entry-day-$first").performClick().assertIsSelected()
        compose.onNodeWithTag("entry-ongoing").assertIsNotEnabled()
        compose.onNodeWithTag("entry-save").assertIsEnabled().performClick()
        assertEquals(first to first.plusDays(2), saved)
        compose.onNodeWithTag("next-month").assertDoesNotExist()
        compose.onNodeWithTag("previous-month").assertDoesNotExist()
    }

    @Test fun editExcludesSelfAndNeverOffersSaveMore() {
        val period = record(today.minusDays(5), today.minusDays(2))
        compose.activity.setContent {
            PeriodEntry(
                PeriodUiState(periods = listOf(period), today = today, loading = false),
                {},
                { _, _ -> },
                initialStart = period.start,
                initialEnd = period.end,
                editing = true,
                editingId = period.id,
                onSaveBatch = { }
            )
        }
        compose.onNodeWithTag("entry-day-${period.start}").assertIsEnabled()
        compose.onNodeWithTag("entry-save").assertIsEnabled()
        compose.onNodeWithTag("entry-save-more").assertDoesNotExist()
    }

    @Test fun verticalStreamKeepsRangeAcrossLeapDay() {
        val first = LocalDate.of(2024, 2, 27)
        var saved: Pair<LocalDate, LocalDate?>? = null
        compose.activity.setContent {
            PeriodEntry(
                PeriodUiState(today = today, loading = false),
                {},
                { start, end -> saved = start to end },
                initialStart = first
            )
        }
        compose.onNodeWithTag("entry-day-$first").performClick()
        compose.onNodeWithTag("entry-month-grid").performScrollToIndex((2024 - 1900) * 12 + 2)
        compose.onNodeWithTag("entry-day-2024-03-01").performClick()
        compose.onNodeWithTag("entry-save").performClick()
        assertEquals(first to LocalDate.of(2024, 3, 1), saved)
    }

    @Test
    @Config(sdk = [35], qualifiers = "en-rUS-w320dp-h640dp")
    fun allActionsHaveEqualHeightAndStayReachableAtLargeText() {
        compose.activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                PeriodEntry(
                    PeriodUiState(today = today, loading = false),
                    {},
                    { _, _ -> },
                    initialStart = today.minusDays(3),
                    initialEnd = today,
                    onSaveBatch = { }
                )
            }
        }
        val bounds = listOf("entry-ongoing", "entry-save", "entry-save-more").map {
            compose.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertEquals(bounds[0].height, bounds[1].height, 1f)
        assertEquals(bounds[0].height, bounds[2].height, 1f)
        assertTrue(
            compose.onNodeWithTag("entry-month-grid").fetchSemanticsNode().boundsInRoot.height > 48
        )
    }

    @Test fun markingKeepsDraftAndScrollUntilSavePersistsAllRanges() {
        val repository = (compose.activity.application as RitelaApplication).periods
        val now = LocalDate.now()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Отметить месячные").fetchSemanticsNodes().singleOrNull()
                ?.config?.contains(SemanticsProperties.Disabled) == false
        }
        compose.onNodeWithText("Отметить месячные").performClick()
        fun choose(first: LocalDate, last: LocalDate) {
            compose.onNodeWithTag("entry-month-grid")
                .performScrollToIndex((last.year - 1900) * 12 + last.monthValue - 1)
            compose.onNodeWithTag("entry-day-$last").performClick()
            compose.onNodeWithTag("entry-month-grid")
                .performScrollToIndex((first.year - 1900) * 12 + first.monthValue - 1)
            compose.onNodeWithTag("entry-day-$first").performClick()
        }
        val firstStart = now.minusMonths(1).withDayOfMonth(5)
        val firstEnd = firstStart.plusDays(2)
        choose(firstStart, firstEnd)
        val viewport = compose.onNodeWithTag("entry-month-grid").fetchSemanticsNode().boundsInRoot
        val dayBounds = compose.onNodeWithTag(
            "entry-day-$firstEnd"
        ).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("entry-save-more").performClick()
        compose.onNodeWithTag("entry-save-more").assertIsNotEnabled()
        compose.onNodeWithTag("entry-save").assertIsEnabled()
        compose.onNodeWithTag("entry-day-$firstEnd").assertIsDisplayed().assertIsNotEnabled()
        assertEquals(
            viewport,
            compose.onNodeWithTag("entry-month-grid").fetchSemanticsNode().boundsInRoot
        )
        assertEquals(
            dayBounds,
            compose.onNodeWithTag("entry-day-$firstEnd").fetchSemanticsNode().boundsInRoot
        )
        assertTrue(runBlocking { repository.periods.first().isEmpty() })
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("entry-day-$firstEnd").assertIsDisplayed().assertIsNotEnabled()
        assertTrue(runBlocking { repository.periods.first().isEmpty() })
        choose(now.minusDays(2), now.minusDays(1))
        compose.onNodeWithTag("entry-save").performClick()
        compose.waitUntil(10_000) {
            runBlocking { repository.periods.first().size == 2 } &&
                compose.onAllNodesWithTag("entry-save").fetchSemanticsNodes().isEmpty()
        }
        val records = runBlocking { repository.periods.first().sortedBy { it.start } }
        assertEquals(firstStart, records[0].start)
        assertEquals(firstEnd, records[0].end)
        assertEquals(now.minusDays(2), records[1].start)
        assertEquals(now.minusDays(1), records[1].end)
    }

    @Test fun cancelAndBackAskBeforeDiscardingMarkedRanges() {
        val repository = (compose.activity.application as RitelaApplication).periods
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Отметить месячные").fetchSemanticsNodes().singleOrNull()
                ?.config?.contains(SemanticsProperties.Disabled) == false
        }
        compose.onNodeWithText("Отметить месячные").performClick()
        compose.onNodeWithTag("entry-save-more").performClick()
        val entryDialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        compose.onNodeWithTag("entry-cancel").performClick()
        compose.onNodeWithTag("entry-discard-dialog").assertIsDisplayed()
        compose.onNodeWithTag("entry-discard-cancel").performClick()
        compose.onNodeWithTag("entry-save").assertIsEnabled()
        assertTrue(runBlocking { repository.periods.first().isEmpty() })
        compose.runOnIdle {
            (entryDialog as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithTag("entry-discard-dialog").assertIsDisplayed()
        compose.onNodeWithTag("entry-discard-continue").performClick()
        compose.onNodeWithTag("entry-save").assertDoesNotExist()
        assertTrue(runBlocking { repository.periods.first().isEmpty() })
    }

    @Test fun ongoingDraftBlocksFollowingDaysAndSavesOnlyOnConfirmation() {
        val repository = (compose.activity.application as RitelaApplication).periods
        val start = LocalDate.now().minusDays(2)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Отметить месячные").fetchSemanticsNodes().singleOrNull()
                ?.config?.contains(SemanticsProperties.Disabled) == false
        }
        compose.onNodeWithText("Отметить месячные").performClick()
        compose.onNodeWithTag("entry-day-$start").performClick()
        compose.onNodeWithTag("entry-ongoing").performClick()
        compose.onNodeWithTag("entry-day-${LocalDate.now()}").assertIsNotEnabled()
        assertTrue(runBlocking { repository.periods.first().isEmpty() })
        compose.onNodeWithTag("entry-save").performClick()
        compose.waitUntil(10_000) {
            runBlocking { repository.periods.first().size == 1 } &&
                compose.onAllNodesWithTag("entry-save").fetchSemanticsNodes().isEmpty()
        }
        val period = runBlocking { repository.periods.first().single() }
        assertEquals(start, period.start)
        assertEquals(null, period.end)
    }

    @Test fun failedSaveKeepsMarkedRangesForRetry() {
        val state = androidx.compose.runtime.mutableStateOf(
            PeriodUiState(today = today, loading = false)
        )
        val attempts = mutableListOf<List<app.ritela.domain.PeriodRange>>()
        compose.activity.setContent {
            PeriodEntry(
                state.value,
                {},
                { _, _ -> },
                initialStart = today.minusDays(3),
                initialEnd = today.minusDays(1),
                onSaveBatch = {
                    attempts += it
                    state.value =
                        state.value.copy(problem = app.ritela.domain.PeriodProblem.STORAGE)
                }
            )
        }
        compose.onNodeWithTag("entry-save-more").performClick()
        compose.onNodeWithTag("entry-save").performClick()
        compose.onNodeWithTag("entry-day-${today.minusDays(2)}").assertIsNotEnabled()
        compose.onNodeWithTag("entry-save").assertIsEnabled().performClick()
        assertEquals(2, attempts.size)
        assertEquals(attempts[0], attempts[1])
        assertEquals(today.minusDays(3), attempts[1].single().start)
        assertEquals(today.minusDays(1), attempts[1].single().end)
    }
}
