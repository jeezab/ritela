package app.ritela

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import app.ritela.ui.HomeTheme
import app.ritela.ui.PartnerConnectionCard
import app.ritela.ui.PartnerInvitationActions
import app.ritela.ui.PartnerReceiveCard
import app.ritela.ui.PartnerReceivedCalendar
import app.ritela.ui.PartnerVerificationContent
import app.ritela.ui.PartnerWelcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PartnerControlsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun shortCodeIsPrimaryAndRejectNeverConfirmsOnSmallScreen() {
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("nav-today").fetchSemanticsNodes().isNotEmpty()
        }
        val verifying = mutableStateOf(false)
        var confirmed = false
        compose.activity.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f)
            ) {
                HomeTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        if (verifying.value) {
                            PartnerVerificationContent(
                                "Alex",
                                "4821 · 7053",
                                "a".repeat(64),
                                true,
                                { confirmed = true },
                                { verifying.value = false }
                            )
                        } else {
                            PartnerWelcome { verifying.value = true }
                        }
                    }
                }
            }
        }
        val add = compose.activity.getString(R.string.partner_add_close)
        compose.onNodeWithText(add).performScrollTo().performClick()
        compose.onNodeWithTag("partner-verification-code").assertIsDisplayed()
        val details = "a".repeat(64).chunked(8).joinToString(" ")
        compose.onNodeWithText(details).assertDoesNotExist()
        compose.onNodeWithTag("partner-reject-code").performScrollTo().performClick()
        assertFalse(confirmed)
        compose.onNodeWithText(add).performScrollTo().performClick()
        compose.onNodeWithText(
            compose.activity.getString(R.string.partner_technical)
        ).assertDoesNotExist()
        compose.onNodeWithText(details).assertDoesNotExist()
        compose.onNodeWithTag("partner-confirm-code").performScrollTo().performClick()
        assertTrue(confirmed)
    }

    @Test fun cardActionsAreDirectAndRemainAccessibleWithLargeText() {
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("nav-today").fetchSemanticsNodes().isNotEmpty()
        }
        var opened = 0
        var received = 0
        var invited = 0
        var scanned = 0
        compose.activity.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f)
            ) {
                HomeTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        PartnerConnectionCard(true, { invited++ }, { scanned++ })
                        PartnerReceiveCard(true, { opened++ }, { received++ })
                    }
                }
            }
        }
        listOf(
            R.string.partner_invite,
            R.string.partner_open_invitation,
            R.string.partner_import,
            R.string.partner_receive
        ).forEach { label ->
            compose.onNodeWithText(
                compose.activity.getString(label)
            ).performScrollTo().performClick()
        }
        assertEquals(listOf(1, 1, 1, 1), listOf(invited, scanned, opened, received))
        compose.onNodeWithText(compose.activity.getString(R.string.partner_manual_exchange))
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun connectionInformationShowsStepsWithoutStartingPairing() {
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("nav-today").fetchSemanticsNodes().isNotEmpty()
        }
        var actions = 0
        compose.activity.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f)
            ) {
                HomeTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        PartnerConnectionCard(true, { actions++ }, { actions++ })
                    }
                }
            }
        }
        compose.onNodeWithTag("partner-connection-info").performScrollTo().performClick()
        compose.onNodeWithTag("partner-connection-steps").assertIsDisplayed()
        assertEquals(
            compose.activity.getString(R.string.partner_onboarding_steps),
            compose.onNodeWithTag("partner-connection-steps").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        )
        assertEquals(0, actions)
    }

    @Test fun invitationActionsAreAccessibleWithoutQrOrCamera() {
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("nav-today").fetchSemanticsNodes().isNotEmpty()
        }
        val actions = IntArray(5)
        compose.activity.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f)
            ) {
                HomeTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        PartnerInvitationActions(
                            true,
                            true,
                            { actions[0]++ },
                            { actions[1]++ },
                            { actions[2]++ },
                            { actions[3]++ },
                            { actions[4]++ }
                        )
                    }
                }
            }
        }
        listOf(
            R.string.partner_save_invitation,
            R.string.partner_share_invitation,
            R.string.partner_open_invitation,
            R.string.partner_bluetooth,
            R.string.partner_receive
        ).forEach { label ->
            compose.onNodeWithText(
                compose.activity.getString(label)
            ).performScrollTo().performClick()
        }
        assertEquals(listOf(1, 1, 1, 1, 1), actions.toList())
        assertFalse(
            compose.activity.packageManager.getPackageInfo(
                compose.activity.packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS
            )
                .requestedPermissions.orEmpty().contains(android.Manifest.permission.CAMERA)
        )
    }

    @Test fun twelveMonthForecastRemainsAccessibleInReadOnlyCalendar() {
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("nav-today").fetchSemanticsNodes().isNotEmpty()
        }
        val today = java.time.LocalDate.now()
        val dates = (1..12).map { today.plusMonths(it.toLong()).withDayOfMonth(10) }
        val forecasts = org.json.JSONArray(
            dates.map {
                org.json.JSONObject().put(
                    "start",
                    it.toEpochDay()
                ).put("lower", it.minusDays(2).toEpochDay())
                    .put(
                        "upper",
                        it.plusDays(2).toEpochDay()
                    ).put("ovulation", it.minusDays(14).toEpochDay())
            }
        )
        val snapshot = app.ritela.data.PartnerSnapshot(
            org.json.JSONObject().put("forecasts", forecasts)
        )
        compose.activity.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f)
            ) {
                HomeTheme { PartnerReceivedCalendar(snapshot) }
            }
        }
        val day = "partner-calendar-day-${dates.last()}"
        compose.onNodeWithTag("partner-received-calendar").performScrollToNode(hasTestTag(day))
        compose.onNodeWithTag(day).assertIsDisplayed().performClick()
        compose.onNodeWithText(
            compose.activity.getString(
                R.string.partner_forecast_row,
                app.ritela.ui.PartnerFormatter(
                    compose.activity.resources
                ).date(dates.last().toEpochDay()),
                app.ritela.ui.PartnerFormatter(
                    compose.activity.resources
                ).date(dates.last().minusDays(2).toEpochDay()),
                app.ritela.ui.PartnerFormatter(
                    compose.activity.resources
                ).date(dates.last().plusDays(2).toEpochDay())
            )
        )
            .assertIsDisplayed()
    }
}
