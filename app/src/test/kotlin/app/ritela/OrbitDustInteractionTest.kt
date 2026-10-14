package app.ritela

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.ritela.ui.CycleOrbitScene
import app.ritela.ui.HomeColors
import app.ritela.ui.HomeTheme
import app.ritela.ui.OrbitColors
import app.ritela.ui.OrbitGeometry
import app.ritela.ui.OrbitMarker
import app.ritela.ui.OrbitPhaseInfo
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
class OrbitDustInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun dustOpensExistingPhaseInfoWithoutTakingSunOrOvulationClicks() {
        val selected = mutableStateOf<OrbitMarker?>(null)
        compose.activity.setContent {
            HomeTheme {
                CycleOrbitScene(
                    8,
                    .7f,
                    true,
                    OrbitColors(
                        HomeColors.text,
                        HomeColors.rose,
                        HomeColors.peach,
                        HomeColors.bottom
                    ),
                    Modifier.size(320.dp, 180.dp),
                    onMarker = { selected.value = it }
                )
                selected.value?.let { OrbitPhaseInfo(it) { selected.value = null } }
            }
        }
        val bounds = compose.onNodeWithTag("orbit-canvas").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        val geometry = OrbitGeometry.inViewport(Size(bounds.width, bounds.height), 32f * density)
        compose.onNodeWithTag("orbit-label-follicular").assertIsDisplayed()
        compose.onNodeWithTag("orbit-label-luteal").assertIsDisplayed()
        for (progress in listOf(.12f, .3f)) {
            compose.onNodeWithTag("orbit-canvas").performTouchInput {
                click(geometry.position(progress))
            }
            assertEquals(OrbitMarker.FOLLICULAR, selected.value)
            compose.onNodeWithText(
                compose.activity.getString(R.string.help_follicular_body)
            ).assertIsDisplayed()
            compose.onNodeWithTag("orbit-phase-close").performClick()
        }
        for (progress in listOf(.54f, .78f)) {
            compose.onNodeWithTag("orbit-canvas").performTouchInput {
                click(geometry.position(progress))
            }
            assertEquals(OrbitMarker.LUTEAL, selected.value)
            compose.onNodeWithText(
                compose.activity.getString(R.string.help_luteal_body)
            ).assertIsDisplayed()
            compose.onNodeWithTag("orbit-phase-close").performClick()
        }
        for (marker in listOf(
            OrbitMarker.START,
            OrbitMarker.OVULATION,
            OrbitMarker.END,
            OrbitMarker.FOLLICULAR,
            OrbitMarker.LUTEAL
        )) {
            compose.onNodeWithTag("orbit-marker-${marker.name}").performClick()
            assertEquals(marker, selected.value)
            compose.onNodeWithTag("orbit-phase-close").performClick()
        }
    }
}
