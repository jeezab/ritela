package app.ritela

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import app.ritela.ui.HomeColors
import app.ritela.ui.HomeTheme
import app.ritela.ui.LoveSurprise
import app.ritela.ui.PeriodUiState
import app.ritela.ui.SettingsScreen
import app.ritela.ui.WithLoveButton
import kotlin.random.Random
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
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoveSurpriseTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private var now = 0L
    private val random = Random(42)

    private fun content(reducedMotion: Boolean = false) {
        compose.activity.setContent {
            HomeTheme {
                LoveSurprise(reducedMotion, elapsedMillis = {
                    now
                }, random = random) { click, anchor ->
                    Box(
                        Modifier.fillMaxSize().background(HomeColors.background),
                        contentAlignment = Alignment.Center
                    ) {
                        WithLoveButton(click, anchor)
                    }
                }
            }
        }
    }

    @Test
    fun letterIsEnglishAndCanBeClosedAndReopened() {
        content()
        repeat(8) { compose.onNodeWithTag("with-love").performClick() }
        compose.onNodeWithTag("love-letter").assertDoesNotExist()
        compose.onNodeWithTag("with-love").performClick()
        compose.onNodeWithTag("love-letter").assertIsDisplayed()
        compose.onNodeWithTag("love-letter-text").assertTextEquals(
            "Margoshhhhh!\n\n" +
                "time flies by unnoticeably together.. " +
                "Thank you for everything you do for me, for us.\n\n" +
                "In honor of our date, I decided to create an app for you!! " +
                "Inspired only by you, your cookies and your care.\n\n" +
                "I love you very much! You're my big little miracle.."
        )
        compose.onNodeWithTag("love-letter-close").performClick()
        compose.onNodeWithTag("love-letter").assertDoesNotExist()
        repeat(9) { compose.onNodeWithTag("with-love").performClick() }
        compose.onNodeWithTag("love-letter").assertIsDisplayed()
    }

    @Test
    fun longGapResetsUiSequenceWithReducedMotion() {
        content(reducedMotion = true)
        repeat(8) { compose.onNodeWithTag("with-love").performClick() }
        now = 1001
        compose.onNodeWithTag("with-love").performClick()
        compose.onNodeWithTag("love-letter").assertDoesNotExist()
        repeat(8) { compose.onNodeWithTag("with-love").performClick() }
        compose.onNodeWithTag("love-letter").assertIsDisplayed()
        compose.onNodeWithTag("love-letter-close").performClick()
        compose.onNodeWithTag("love-letter").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], qualifiers = "ru-rRU-w320dp-h640dp")
    fun settingsFooterAndLetterRemainAccessibleAtLargeText() {
        compose.activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                SettingsScreen(PaddingValues(), PeriodUiState(loading = false))
            }
        }
        compose.onNodeWithTag("backup-export").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("backup-import").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("with-love").performScrollTo().assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        repeat(9) {
            compose.onNodeWithTag("with-love").performClick()
            compose.mainClock.advanceTimeBy(32)
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("love-letter-text").assertTextEquals(
            "Маргошенька!\n\n" +
                "время вместе летит незаметно.. спасибо тебе за всё, " +
                "что ты делаешь для меня, для нас.\n\n" +
                "В честь нашей даты решил создать приложение для тебя!! " +
                "Вдохновлён лишь тобою, твоими печеньками и заботой.\n\n" +
                "Люблю тебя очень! Ты моё большое маленькое чудо.."
        )
        compose.onNodeWithTag("love-letter-close").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("love-letter").assertDoesNotExist()
    }

    @Test
    fun particlesAnimateAndDisappearWithoutMovingButton() {
        content()
        compose.onNodeWithTag("with-love").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        val button = compose.onNodeWithTag("with-love")
        val bounds = button.fetchSemanticsNode().boundsInRoot
        val before = compose.onRoot().captureToImage().asAndroidBitmap()
        button.performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)
        val during = compose.onRoot().captureToImage().asAndroidBitmap()
        fun outsideButtonChanges(image: android.graphics.Bitmap): Int {
            var count = 0
            for (y in 0 until before.height) {
                for (x in 0 until before.width) {
                    if (x >= bounds.left - 1 && x <= bounds.right + 1 &&
                        y >= bounds.top - 1 && y <= bounds.bottom + 1
                    ) {
                        continue
                    }
                    if (before.getPixel(x, y) != image.getPixel(x, y)) count++
                }
            }
            return count
        }
        assertTrue("Hearts must fly outside the button", outsideButtonChanges(during) > 0)
        assertEquals(bounds, button.fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(1800)
        compose.mainClock.advanceTimeByFrame()
        val after = compose.onRoot().captureToImage().asAndroidBitmap()
        // Exclude TextButton's native glyph/indication rasterization from the overlay check.
        assertEquals("Particles must completely disappear", 0, outsideButtonChanges(after))
    }
}
