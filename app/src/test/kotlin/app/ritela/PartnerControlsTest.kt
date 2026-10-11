package app.ritela

import android.content.pm.ActivityInfo
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
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import app.ritela.ui.HomeTheme
import app.ritela.ui.PartnerCaptureActivity
import app.ritela.ui.PartnerVerificationContent
import app.ritela.ui.PartnerWelcome
import app.ritela.ui.partnerScanOptions
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
        ).performScrollTo().performClick()
        compose.onNodeWithText(details).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("partner-confirm-code").performScrollTo().performClick()
        assertTrue(confirmed)
    }

    @Test fun scannerUsesOwnActivityAndDoesNotForceLandscape() {
        val intent = partnerScanOptions().createScanIntent(compose.activity)
        assertEquals(PartnerCaptureActivity::class.java.name, intent.component!!.className)
        assertFalse(intent.getBooleanExtra("SCAN_ORIENTATION_LOCKED", true))
        val info = compose.activity.packageManager.getActivityInfo(intent.component!!, 0)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, info.screenOrientation)
    }
}
