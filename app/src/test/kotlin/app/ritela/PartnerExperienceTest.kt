package app.ritela

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import app.ritela.data.PartnerPreferences
import app.ritela.data.PartnerSnapshot
import app.ritela.domain.PartnerContact
import app.ritela.ui.HomeTheme
import app.ritela.ui.PartnerHomeContent
import app.ritela.ui.PartnerReceivedCalendar
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PartnerExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val today = LocalDate.now()

    private fun snapshot(day: Long) = PartnerSnapshot(
        JSONObject()
            .put("asOf", today.toEpochDay()).put("cycleDay", day).put("cycleLength", 28)
            .put("phase", "OVULATION").put("duration", 5)
            .put(
                "forecasts",
                JSONArray().put(
                    JSONObject()
                        .put("start", today.plusDays(14).toEpochDay())
                        .put("lower", today.plusDays(12).toEpochDay())
                        .put("upper", today.plusDays(16).toEpochDay())
                        .put("ovulation", today.toEpochDay())
                )
            )
    )

    @Test fun homeChoiceIsIndependentOfChatAndUsesSharedOrbitWithArticles() {
        val prefs = PartnerPreferences(compose.activity, UUID.randomUUID().toString())
        try {
            runBlocking { withTimeout(5000) { prefs.mode.first { it.ready } } }
            val contacts = listOf(
                PartnerContact("first", "Alex", setOf("a")),
                PartnerContact("second", "Robin", setOf("b"))
            )
            compose.activity.setContent {
                val mode by prefs.mode.collectAsState()
                val scope = rememberCoroutineScope()
                val contact =
                    contacts.firstOrNull { it.id == mode.homeSelected } ?: contacts.first()
                PartnerHomeContent(
                    PaddingValues(),
                    contacts,
                    contact,
                    snapshot(if (contact.id == "first") 8 else 17),
                    { scope.launch { prefs.selectHome(it) } },
                    {},
                    {}
                )
            }
            compose.onNodeWithTag("partner-home-selector").performClick()
            compose.onNodeWithTag("partner-home-select-second").performClick()
            compose.waitUntil(5000) { prefs.mode.value.homeSelected == "second" }
            runBlocking {
                prefs.select("first")
                withTimeout(5000) {
                    prefs.mode.first {
                        it.selected ==
                            "first"
                    }
                }
            }
            compose.onNodeWithTag("partner-home-selector").assertTextEquals("Robin")
            compose.onNodeWithTag("orbit-center-day", useUnmergedTree = true).assertTextEquals("17")
            compose.onNodeWithTag("orbit-open").performScrollTo().performClick()
            compose.onNodeWithTag("orbit-detail").assertIsDisplayed()
            compose.onNode(hasTestTag("help-cards") and hasAnyAncestor(hasTestTag("orbit-detail")))
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("orbit-legend-FOLLICULAR").performScrollTo().performClick()
            compose.onNode(
                hasText(compose.activity.getString(R.string.help_follicular_body)) and
                    hasAnyAncestor(hasTestTag("orbit-phase-info"))
            ).assertIsDisplayed()
            compose.onNodeWithTag("orbit-phase-close").performClick()
            compose.onNodeWithTag("orbit-detail-close").performClick()
            compose.onNodeWithTag("help-cards").performScrollTo().assertIsDisplayed()
            assertEquals("second", prefs.mode.value.homeSelected)
        } finally {
            runBlocking { prefs.close() }
        }
    }

    @Test fun calendarFertilityUsesReceivedOvulationAndOrdinaryDayHasNoStatusOrPlaceholder() {
        val data = snapshot(8)
        compose.activity.setContent { HomeTheme { PartnerReceivedCalendar(data) } }
        val ordinary = today.plusDays(7)
        compose.onNodeWithTag("partner-received-calendar")
            .performScrollToNode(hasTestTag("partner-calendar-day-$ordinary"))
        compose.onNodeWithTag("partner-calendar-day-$ordinary").performClick()
        compose.onNodeWithText(
            compose.activity.getString(R.string.selected_day_ordinary)
        ).assertDoesNotExist()
        compose.onNodeWithText("Полученных данных пока нет").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.partner_close)).performClick()
        val fertile = today.plusDays(1)
        compose.onNodeWithTag("partner-received-calendar")
            .performScrollToNode(hasTestTag("partner-calendar-day-$fertile"))
        compose.onNodeWithTag("partner-calendar-day-$fertile").performClick()
        compose.onNodeWithText(
            compose.activity.getString(R.string.selected_day_fertile)
        ).assertIsDisplayed()
    }
}
