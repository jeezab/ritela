package app.ritela

import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import app.ritela.domain.Period
import app.ritela.domain.analyzeCycles
import app.ritela.ui.AppNavigation
import app.ritela.ui.CalendarScreen
import app.ritela.ui.HomeScreen
import app.ritela.ui.PeriodUiState
import app.ritela.ui.RitelaTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
            compose.onAllNodesWithText("Начнём с даты").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Начнём с даты").assertIsDisplayed()
        compose.onNodeWithText(
            "Записи хранятся на этом устройстве"
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
            compose.onAllNodesWithText("Начнём с даты").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Отметить месячные").performClick()
        saveRendering("period-entry", dialog = true)
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Продолжается").fetchSemanticsNodes().isNotEmpty()
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Продолжается").assertIsDisplayed()
        saveRendering("home-active")
        compose.onNodeWithText("Завершить").performClick()
        saveRendering("period-finish", dialog = true)
        compose.onNodeWithText("Готово").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Продолжается").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Отметить месячные").assertIsDisplayed()
        saveRendering("home-recorded")
    }

    @Test
    @Config(sdk = [35], qualifiers = "ru-rRU-w320dp-h740dp")
    fun narrowHomeKeepsPrimaryActionAtLargeFont() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    RitelaTheme(dynamicColor = false) {
                        Scaffold { HomeScreen(it, today = LocalDate.of(2026, 10, 5)) }
                    }
                }
            }
        }
        compose.onNodeWithText("Отметить месячные").assertIsDisplayed()
        saveRendering("home-narrow-large-text")
    }

    @Test
    fun darkHomeShowsRecordedAndOngoingStates() {
        val today = LocalDate.of(2026, 10, 5)
        for (ongoing in listOf(false, true)) {
            val period = Period(
                UUID(0, 1),
                today.minusDays(4),
                if (ongoing) null else today,
                Instant.EPOCH,
                Instant.EPOCH
            )
            compose.activity.runOnUiThread {
                compose.activity.setContent {
                    RitelaTheme(darkTheme = true, dynamicColor = false) {
                        Scaffold {
                            HomeScreen(
                                it,
                                PeriodUiState(periods = listOf(period), loading = false),
                                today = today
                            )
                        }
                    }
                }
            }
            compose.onNodeWithText(
                if (ongoing) "Завершить" else "Отметить месячные"
            ).assertIsDisplayed()
            saveRendering(if (ongoing) "home-active-dark" else "home-recorded-dark")
        }
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

    @Test
    fun editingKeepsTheRecordAndDeletionRequiresConfirmation() {
        val repository = (compose.activity.application as RitelaApplication).periods
        val today = LocalDate.now()
        val original = runBlocking {
            repository.add(today.minusDays(6), today.minusDays(2))
            repository.periods.first().single()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Изменить").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Изменить").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Изменить даты").fetchSemanticsNodes().isNotEmpty()
        }
        saveRendering("period-edit", dialog = true)
        compose.onNodeWithText("Ещё идут").performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Продолжается").fetchSemanticsNodes().isNotEmpty()
        }
        val changed = runBlocking { repository.periods.first().single() }
        assertEquals(original.id, changed.id)
        assertEquals(original.createdAt, changed.createdAt)
        assertEquals(null, changed.end)
        compose.onNodeWithText("Удалить").performScrollTo().performClick()
        saveRendering("period-delete", dialog = true)
        compose.onNodeWithText("Отмена").performClick()
        assertEquals(changed, runBlocking { repository.periods.first().single() })
        compose.onNodeWithText("Удалить").performScrollTo().performClick()
        compose.onNodeWithText("Удалить запись").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Начнём с даты").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(runBlocking { repository.periods.first().isEmpty() })
    }

    @Test
    fun calendarLogsSelectedPastDayAndOpensExistingRecord() {
        val repository = (compose.activity.application as RitelaApplication).periods
        val today = LocalDate.now()
        runBlocking { repository.add(today, today) }
        compose.onNodeWithTag("nav-calendar").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Менструация отмечена").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Изменить").performScrollTo().performClick()
        compose.onNodeWithText("Изменить даты").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithTag("calendar-heading").performScrollTo()
        compose.onNodeWithTag("previous-month").performClick()
        val day = YearMonth.from(today).minusMonths(1).atDay(15)
        compose.onNodeWithTag("calendar-day-$day").performClick()
        compose.onNodeWithText("Отметить месячные").performScrollTo().performClick()
        compose.onNodeWithText("Дата окончания").performClick()
        compose.onNodeWithText("Готово").performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Менструация отмечена").fetchSemanticsNodes().isNotEmpty()
        }
        val saved = runBlocking { repository.periods.first().first { it.start == day } }
        assertEquals(day, saved.end)
        compose.onNodeWithTag("calendar-heading").performScrollTo()
        saveRendering("calendar-recorded")
    }

    @Test
    fun calendarShowsForecastRangeAndSupportsMonthSwipe() {
        val today = LocalDate.of(2026, 10, 5)
        val lastStart = today.minusDays(7)
        val records = (0..6).map {
            val start = lastStart.minusDays(28L * (6 - it))
            Period(UUID(0, it.toLong()), start, start.plusDays(4), Instant.EPOCH, Instant.EPOCH)
        }
        val state =
            PeriodUiState(
                periods = records,
                loading = false,
                today = today,
                analysis = analyzeCycles(records, today)
            )
        val predicted = state.analysis.forecasts.first().predictedStartDate
        for (dark in listOf(false, true)) {
            compose.activity.runOnUiThread {
                compose.activity.setContent {
                    RitelaTheme(darkTheme = dark, dynamicColor = false) {
                        Scaffold(bottomBar = { AppNavigation(1) {} }) {
                            CalendarScreen(it, state, {}, {}, {})
                        }
                    }
                }
            }
            compose.onNodeWithTag("calendar-day-$predicted").performClick()
            saveRendering(if (dark) "calendar-dark" else "calendar-forecast")
        }
        compose.onNodeWithText("Ориентир начала менструации").performScrollTo().assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Отметить месячные").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("calendar-heading").performScrollTo()
        compose.onNodeWithTag("month-grid").performTouchInput { swipeLeft() }
        val nextMonth = YearMonth.from(
            today
        ).plusMonths(
            1
        ).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ru-RU")))
        compose.onNodeWithText(nextMonth).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "ru-rRU-w320dp-h740dp")
    fun narrowCalendarKeepsDaysAndActionsAccessibleAtLargeFonts() {
        val today = LocalDate.of(2026, 10, 5)
        for (scale in listOf(1.3f, 2f)) {
            compose.activity.runOnUiThread {
                compose.activity.setContent {
                    val density = LocalDensity.current.density
                    CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                        RitelaTheme(dynamicColor = false) {
                            key(scale) {
                                Scaffold(bottomBar = { AppNavigation(1) {} }) {
                                    CalendarScreen(
                                        it,
                                        PeriodUiState(loading = false, today = today),
                                        {},
                                        {},
                                        {}
                                    )
                                }
                            }
                        }
                    }
                }
            }
            compose.onNodeWithTag("calendar-day-$today").assertIsDisplayed().performClick()
            saveRendering("calendar-narrow-${if (scale == 2f) "large" else "medium"}-text")
            compose.onNodeWithText("Отметить месячные").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("calendar-heading").performScrollTo()
            compose.onNodeWithTag("month-grid").performTouchInput { swipeLeft() }
            compose.onNodeWithTag("calendar-day-2026-10-04").assertIsDisplayed()
            compose.onNodeWithTag("next-month").performClick()
            compose.onNodeWithText("ноябрь 2026").assertIsDisplayed()
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
