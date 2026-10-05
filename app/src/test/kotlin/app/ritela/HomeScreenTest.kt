package app.ritela

import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun emptyHomeExplainsLocalPrivacy() {
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
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = File(requireNotNull(System.getProperty("ritela.screenshotDir"))).apply {
                mkdirs()
            }
            File(directory, "home-${if (dark) "dark" else "light"}.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
