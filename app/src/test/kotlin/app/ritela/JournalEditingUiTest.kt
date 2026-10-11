package app.ritela

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.ritela.domain.DayLog
import app.ritela.domain.JournalLayout
import app.ritela.domain.Sex
import app.ritela.ui.CalendarDayRecords
import app.ritela.ui.CalendarScreen
import app.ritela.ui.DayLogEntry
import app.ritela.ui.HomeTheme
import app.ritela.ui.PeriodUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w320dp-h740dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JournalEditingUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun multiMoodSexChoicesProtectedTagsAndPenetrationSettingAreEditable() {
        var changed: JournalLayout? = null
        compose.activity.setContent {
            DayLogEntry(DayLog(LocalDate.now()), PeriodUiState(loading = false), {}, {}, {
                changed =
                    it
            })
        }
        compose.onNodeWithTag("mood-HAPPY").performScrollTo().performClick()
        compose.onNodeWithTag("mood-LOW").performScrollTo().performClick()
        compose.onNodeWithTag("mood-HAPPY").assertIsSelected()
        compose.onNodeWithTag("mood-LOW").assertIsSelected()
        compose.onNodeWithTag("sex-NONE").performScrollTo().performClick()
        compose.onNodeWithTag("sex-ORAL").performScrollTo().performClick()
        compose.onNodeWithTag("sex-NONE").assertIsSelected()
        compose.onNodeWithTag("sex-CONDOM").performScrollTo().performClick()
        compose.onNodeWithTag("sex-NONE").assertIsNotSelected()
        compose.onNodeWithTag("sex-ORAL").assertIsSelected()
        compose.onNodeWithTag("sex-NO_BARRIER").performScrollTo().performClick()
        compose.onNodeWithTag("sex-CONDOM").assertIsNotSelected()
        compose.onNodeWithTag("sex-VAGINAL").assertDoesNotExist()
        compose.onNodeWithTag("discharge-UNUSUAL").assertDoesNotExist()
        compose.onNodeWithTag("journal-edit-sex").performScrollTo().performClick()
        for (id in listOf("NONE", "CONDOM", "NO_BARRIER")) {
            compose.onNodeWithTag("journal-remove-sex-$id").assertDoesNotExist()
        }
        compose.onNodeWithTag("sex-MASTURBATION").performScrollTo().performClick()
        compose.onNodeWithTag("journal-without-penetration").assertIsOn().performClick()
        compose.onNodeWithTag("journal-penetration-info").performClick()
        compose.onNodeWithText(
            compose.activity.getString(R.string.journal_penetration_hint)
        ).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.partner_close)).performClick()
        compose.onNodeWithTag("journal-confirm").performClick()
        assertNotNull(changed)
        assertFalse(
            changed!!.sections.first { it.id == "sex" }.tags.single {
                it.id ==
                    "MASTURBATION"
            }.withoutPenetration
        )
    }

    @Test fun dischargeAllowsCustomTagsAndRestoreKeepsHistoricNames() {
        var changed = JournalLayout()
        compose.activity.setContent {
            DayLogEntry(DayLog(LocalDate.now()), PeriodUiState(loading = false), {}, {}, {
                changed =
                    it
            })
        }
        compose.onNodeWithTag("journal-edit-discharge").performScrollTo().performClick()
        compose.onNodeWithTag("journal-remove-discharge-DRY").assertDoesNotExist()
        compose.onNodeWithText(
            compose.activity.getString(R.string.journal_add_tag)
        ).performScrollTo().performClick()
        compose.onNodeWithTag("journal-name").performTextReplacement("Custom discharge")
        compose.onNodeWithTag("journal-confirm").performClick()
        val custom = changed.sections.first { it.id == "discharge" }.tags.last()
        compose.onNodeWithTag("journal-edit-discharge").performScrollTo().performClick()
        compose.onNodeWithTag("discharge-${custom.id}").performScrollTo().performClick()
        compose.onNodeWithTag("journal-restore-tags").performScrollTo().performClick()
        assertFalse(changed.sections.first { it.id == "discharge" }.tags.any { it.id == custom.id })
        val layout = changed
        compose.activity.setContent {
            HomeTheme {
                Column {
                    CalendarDayRecords(
                        DayLog(
                            LocalDate.now(),
                            custom = mapOf("discharge" to setOf(custom.id, "unknown-id"))
                        ),
                        layout,
                        {},
                        false
                    )
                }
            }
        }
        compose.onNodeWithText("Discharge: Custom discharge, Removed tag").assertExists()
        compose.onNodeWithText(custom.id, substring = true).assertDoesNotExist()
        assertEquals(5, changed.sections.first { it.id == "discharge" }.tags.size)
    }

    @Test fun calendarHeartStyleUpdatesWhenTagPropertiesChangeWithoutEditingRecords() {
        val today = LocalDate.of(2026, 10, 11)
        val layout = mutableStateOf(JournalLayout())
        val logs = listOf(
            DayLog(today.minusDays(2), sex = setOf(Sex.NONE)),
            DayLog(today.minusDays(1), sex = setOf(Sex.NONE, Sex.MASTURBATION)),
            DayLog(today, sex = setOf(Sex.MASTURBATION, Sex.CONDOM))
        )
        compose.activity.setContent {
            HomeTheme {
                CalendarScreen(
                    PaddingValues(),
                    PeriodUiState(
                        loading = false,
                        today = today,
                        dayLogs = logs,
                        journalLayout = layout.value
                    ),
                    {},
                    {},
                    {}
                )
            }
        }
        compose.onNodeWithTag("day-sex-icon-${today.minusDays(2)}", useUnmergedTree = true)
            .assertDoesNotExist()
        compose.onNodeWithTag("day-sex-icon-${today.minusDays(1)}", useUnmergedTree = true)
            .assertContentDescriptionEquals(
                compose.activity.getString(R.string.journal_sex_outline)
            )
        compose.onNodeWithTag("day-sex-icon-$today", useUnmergedTree = true)
            .assertContentDescriptionEquals(compose.activity.getString(R.string.journal_sex_filled))
        compose.runOnIdle {
            val sex = layout.value.sections.first { it.id == "sex" }
            layout.value = layout.value.replace(
                sex.copy(
                    tags = sex.tags.map {
                        if (it.id == "CONDOM") it.copy(withoutPenetration = true) else it
                    }
                )
            )
        }
        compose.onNodeWithTag("day-sex-icon-$today", useUnmergedTree = true)
            .assertContentDescriptionEquals(
                compose.activity.getString(R.string.journal_sex_outline)
            )
        assertEquals(setOf(Sex.MASTURBATION, Sex.CONDOM), logs.last().sex)
    }
}
