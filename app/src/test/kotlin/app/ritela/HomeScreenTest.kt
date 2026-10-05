package app.ritela

import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.ritela.ui.HomeScreen
import app.ritela.ui.RitelaTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun emptyHomeExplainsLocalPrivacy() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Пока нет записей").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Пока нет записей").assertIsDisplayed()
        compose.onNodeWithText(
            "Без аккаунта, рекламы и передачи данных на сервер."
        ).assertIsDisplayed()
        assertTrue(
            compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        )
        val applicationInfo = compose.activity.applicationInfo
        assertFalse(
            applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP != 0
        )
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            compose.activity.checkSelfPermission("android.permission.INTERNET")
        )
    }

    @Test
    fun periodCanBeSavedAndFinishedAfterActivityRecreation() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Пока нет записей").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Добавить запись").performClick()
        saveRendering("period-entry", dialog = true)
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Продолжается").fetchSemanticsNodes().isNotEmpty()
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Продолжается").assertIsDisplayed()
        compose.onNodeWithText("Завершить").performClick()
        saveRendering("period-finish", dialog = true)
        compose.onNodeWithText("Готово").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Продолжается").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Добавить запись").assertIsDisplayed()
        saveRendering("home-recorded")
    }

    @Test
    fun renderLightAndDarkHome() {
        for (dark in listOf(false, true)) {
            compose.activity.runOnUiThread {
                compose.activity.setContent {
                    RitelaTheme(darkTheme = dark, dynamicColor = false) {
                        Scaffold { HomeScreen(it) }
                    }
                }
            }
            compose.waitForIdle()
            saveRendering("home-${if (dark) "dark" else "light"}")
        }
    }

    private fun saveRendering(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val bitmap = if (dialog) {
            // Robolectric PixelCopy can sample the Activity behind a separate dialog window.
            val decor = requireNotNull(ShadowDialog.getLatestDialog().window).decorView
            android.graphics.Bitmap.createBitmap(
                decor.width,
                decor.height,
                android.graphics.Bitmap.Config.ARGB_8888
            ).also {
                decor.draw(android.graphics.Canvas(it))
            }
        } else {
            compose.onRoot().captureToImage().asAndroidBitmap()
        }
        val directory = File(requireNotNull(System.getProperty("ritela.screenshotDir"))).apply {
            mkdirs()
        }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
