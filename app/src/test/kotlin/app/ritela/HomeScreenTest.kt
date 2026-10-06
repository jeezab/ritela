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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
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
        ).performScrollTo().assertIsDisplayed()
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
        compose.onNodeWithTag("nav-calendar").performClick()
        compose.onNodeWithTag("calendar-history").performClick()
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
            runBlocking { repository.periods.first().single().end == null }
        }
        val changed = runBlocking { repository.periods.first().single() }
        assertEquals(original.id, changed.id)
        assertEquals(original.createdAt, changed.createdAt)
        assertEquals(null, changed.end)
        compose.onNodeWithTag("calendar-history").performClick()
        compose.onNodeWithText("Удалить").performScrollTo().performClick()
        saveRendering("period-delete", dialog = true)
        compose.onNodeWithText("Отмена").performClick()
        assertEquals(changed, runBlocking { repository.periods.first().single() })
        compose.onNodeWithTag("calendar-history").performClick()
        compose.onNodeWithText("Удалить").performScrollTo().performClick()
        compose.onNodeWithText("Удалить запись").performClick()
        compose.onNodeWithTag("nav-today").performClick()
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
        compose.onNodeWithTag("month-grid").performScrollToNode(hasTestTag("calendar-day-$today"))
        compose.onNodeWithTag("calendar-day-$today").performClick()
        compose.onNodeWithText("Изменить").performScrollTo().performClick()
        compose.onNodeWithText("Изменить даты").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithTag("previous-month").performClick()
        val day = YearMonth.from(today).minusMonths(1).atDay(15)
        compose.onNodeWithTag("month-grid").performScrollToNode(hasTestTag("calendar-day-$day"))
        compose.onNodeWithTag("calendar-day-$day").performClick()
        saveRendering("calendar-day-details", dialog = true)
        compose.onNodeWithText("Отметить месячные").performScrollTo().performClick()
        compose.onNodeWithText("Дата окончания").performClick()
        compose.onNodeWithText("Готово").performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10_000) { runBlocking { repository.periods.first().size == 2 } }
        val saved = runBlocking { repository.periods.first().first { it.start == day } }
        assertEquals(day, saved.end)
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
            compose.onNodeWithTag(
                "month-grid"
            ).performScrollToNode(hasTestTag("calendar-day-$predicted"))
            saveRendering(if (dark) "calendar-dark" else "calendar-forecast")
            compose.onNodeWithTag("calendar-day-$predicted").performClick()
            compose.onNodeWithText(
                "Недостаточно данных для оценки"
            ).performScrollTo().assertIsDisplayed()
            saveRendering(
                if (dark) "calendar-day-details-dark" else "calendar-day-forecast-details",
                dialog = true
            )
            compose.onNodeWithTag("day-details-close").performScrollTo().performClick()
        }
        assertTrue(compose.onAllNodesWithText("Отметить месячные").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("calendar-heading").assertIsDisplayed()
        val before = compose.onNodeWithTag("month-grid").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
            .value()
        compose.onNodeWithTag("month-grid").performTouchInput {
            swipeUp(startY = centerY, endY = centerY - 100, durationMillis = 1000)
        }
        val after = compose.onNodeWithTag("month-grid").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
            .value()
        assertTrue(after > before)
        saveRendering("calendar-free-scroll")
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
            compose.onNodeWithTag(
                "month-grid"
            ).performScrollToNode(hasTestTag("calendar-day-$today"))
            compose.onNodeWithTag("calendar-day-$today").assertIsDisplayed()
            saveRendering("calendar-narrow-${if (scale == 2f) "large" else "medium"}-text")
            compose.onNodeWithTag("calendar-day-$today").performClick()
            compose.onNodeWithText("Отметить месячные").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("day-details-close").performScrollTo().performClick()
            compose.onNodeWithTag("calendar-heading").assertIsDisplayed()
            compose.onNodeWithTag(
                "month-grid"
            ).performScrollToNode(hasTestTag("calendar-day-2026-10-05"))
            compose.onNodeWithTag("calendar-day-2026-10-11").assertIsDisplayed()
            val first = compose.onNodeWithTag(
                "calendar-day-2026-10-05"
            ).fetchSemanticsNode().boundsInRoot
            val last = compose.onNodeWithTag(
                "calendar-day-2026-10-11"
            ).fetchSemanticsNode().boundsInRoot
            val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue(first.left >= viewport.left && last.right <= viewport.right)
            compose.onNodeWithTag("next-month").performClick()
            compose.onNodeWithTag("month-selector").assertIsDisplayed()
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp")
    fun englishPeriodCanBeSavedEditedAndDeleted() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Start with a date").fetchSemanticsNodes().isNotEmpty()
        }
        saveRendering("home-english")
        compose.onNodeWithText("Log period").performClick()
        saveRendering("period-entry-english", dialog = true)
        compose.onNodeWithText("End date").performClick()
        compose.onNodeWithText("Done").assertIsDisplayed().performClick()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithTag("nav-calendar").performClick()
        compose.onNodeWithTag("calendar-history").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Edit").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.onNodeWithText("Edit dates").assertIsDisplayed()
        compose.onNodeWithText("Still ongoing").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) {
            runBlocking {
                (compose.activity.application as RitelaApplication)
                    .periods.periods.first().single().end == null
            }
        }
        compose.onNodeWithTag("calendar-history").performClick()
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Delete this period?").assertIsDisplayed()
        compose.onNodeWithText("Delete period").performClick()
        compose.onNodeWithTag("nav-today").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Start with a date").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-calendar").performClick()
        compose.onNodeWithTag("calendar-heading").assertIsDisplayed()
        saveRendering("calendar-english")
    }

    @Test
    fun renderReferenceStyleWithRealForecastInBothThemes() {
        renderForecastHome()
    }

    @Test
    @Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp")
    fun englishForecastKeepsDatesAndHistoryLocalized() {
        renderForecastHome()
        compose.onNodeWithText("Ritela").performScrollTo()
        compose.onNodeWithText("Oct 19").assertIsDisplayed()
        compose.onNodeWithText("Based on 6 completed cycles").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "en-rUS-w320dp-h740dp")
    fun englishForecastAtLargeFontKeepsLoggingAccessible() {
        val today = LocalDate.of(2026, 10, 14)
        val records = (0..6).map {
            val start = today.minusDays(23 + 28L * it)
            Period(UUID(0, it.toLong()), start, start.plusDays(4), Instant.EPOCH, Instant.EPOCH)
        }
        val state =
            PeriodUiState(
                periods = records,
                loading = false,
                today = today,
                analysis = analyzeCycles(records, today)
            )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                    RitelaTheme {
                        Scaffold(bottomBar = {
                            AppNavigation(0) {}
                        }) { HomeScreen(it, state, today = today) }
                    }
                }
            }
        }
        saveRendering("home-forecast-narrow-english")
        compose.onNodeWithText("Log period").performScrollTo().assertIsDisplayed()
        saveRendering("home-forecast-narrow-action-english")
    }

    @Test
    fun settingsPersistAndCalendarHeaderSelectsYearAndMonth() {
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("settings-heading").assertIsDisplayed()
        saveRendering("settings")
        compose.onNodeWithTag("cycle-plus").assertDoesNotExist()
        compose.onNodeWithTag("duration-plus").assertDoesNotExist()
        compose.onNodeWithTag("theme-selector").performClick()
        compose.onNodeWithTag("theme-dark").performClick()
        compose.waitUntil(10_000) {
            (compose.activity.application as RitelaApplication).settings.theme.value ==
                app.ritela.data.ThemeMode.DARK
        }
        compose.waitForIdle()
        assertEquals(
            0xFF1E191F.toInt(),
            compose.onRoot().captureToImage().asAndroidBitmap().getPixel(0, 0)
        )
        saveRendering("settings-dark")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("theme-selector").performClick()
        compose.onNodeWithTag("theme-dark").assertIsSelected()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithTag("theme-selector").performClick()
        compose.onNodeWithTag("theme-light").performClick()
        compose.waitUntil(10_000) {
            (compose.activity.application as RitelaApplication).settings.theme.value ==
                app.ritela.data.ThemeMode.LIGHT
        }
        compose.waitForIdle()
        assertEquals(
            0xFFF8F4EF.toInt(),
            compose.onRoot().captureToImage().asAndroidBitmap().getPixel(0, 0)
        )
        compose.onNodeWithTag("theme-selector").performClick()
        compose.onNodeWithTag("theme-system").performClick()
        compose.waitUntil(10_000) {
            (compose.activity.application as RitelaApplication).settings.theme.value ==
                app.ritela.data.ThemeMode.SYSTEM
        }
        compose.onNodeWithTag("nav-calendar").performClick()
        compose.onNodeWithTag("month-selector").performClick()
        saveRendering("calendar-month-picker", dialog = true)
        compose.onNodeWithTag("month-year").performClick()
        compose.onNodeWithTag("select-year-2024").performClick()
        compose.onNodeWithTag("select-month-2").performClick()
        compose.onNodeWithTag("month-selector").assertIsDisplayed()
        compose.onNodeWithTag("next-month").performClick()
        compose.onNodeWithTag("month-selector").assertIsDisplayed()
        compose.onNodeWithTag("previous-month").performClick()
        compose.onNodeWithTag("month-selector").assertIsDisplayed()
    }

    @Test
    fun periodRangeAcrossLeapDayCanBeSavedWithTwoCalendarTaps() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Начнём с даты").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Отметить месячные").performClick()
        compose.onNodeWithTag("month-selector").performClick()
        compose.onNodeWithTag("month-year").performClick()
        compose.onNodeWithTag("select-year-2024").performClick()
        compose.onNodeWithTag("select-month-2").performClick()
        compose.onNodeWithTag("entry-day-2024-02-27").performScrollTo().performClick()
        compose.onNodeWithTag("next-month").performClick()
        compose.onNodeWithTag("entry-day-2024-03-01").performClick()
        saveRendering("period-range", dialog = true)
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10_000) {
            runBlocking {
                (compose.activity.application as RitelaApplication)
                    .periods.periods.first().isNotEmpty()
            }
        }
        val record =
            runBlocking {
                (compose.activity.application as RitelaApplication).periods.periods.first().single()
            }
        assertEquals(LocalDate.of(2024, 2, 27), record.start)
        assertEquals(LocalDate.of(2024, 3, 1), record.end)
    }

    @Test
    @Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp")
    fun englishSettingsAreReachableFromBottomNavigation() {
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("settings-heading").assertIsDisplayed()
        compose.onNodeWithText("Language").assertIsDisplayed()
        saveRendering("settings-english")
        compose.onNodeWithText("Theme").assertIsDisplayed()
        compose.onNodeWithTag("theme-selector").performClick()
        compose.onNodeWithTag("theme-dark").performClick()
        compose.waitUntil(10_000) {
            (compose.activity.application as RitelaApplication).settings.theme.value ==
                app.ritela.data.ThemeMode.DARK
        }
        saveRendering("settings-dark-english")
    }

    @Test
    fun dayCanBeLoggedOnHomeAndEditedInCalendarAfterRecreation() {
        compose.onNodeWithTag("log-day").performScrollTo().performClick()
        compose.onNodeWithTag("headache-NONE").performClick()
        compose.onNodeWithTag("cramps-MODERATE").performClick()
        saveRendering("day-entry", dialog = true)
        compose.onNodeWithTag("sex-CONDOM").performScrollTo().performClick()
        compose.onNodeWithTag("sex-ORAL").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("headache-NONE").assertIsSelected()
        compose.onNodeWithTag("save-day").performClick()
        compose.waitUntil(10_000) {
            runBlocking {
                (compose.activity.application as RitelaApplication).days.logs.first().size == 1
            }
        }
        val saved = runBlocking {
            (compose.activity.application as RitelaApplication).days.logs.first().single()
        }
        assertEquals(app.ritela.domain.Pain.NONE, saved.headache)
        assertEquals(app.ritela.domain.Pain.MODERATE, saved.cramps)
        assertEquals(
            setOf(app.ritela.domain.Sex.CONDOM, app.ritela.domain.Sex.ORAL),
            saved.sex
        )
        compose.onNodeWithTag("log-day").performScrollTo()
        saveRendering("home-day-log")
        compose.onNodeWithTag("nav-calendar").performClick()
        compose.onNodeWithTag(
            "month-grid"
        ).performScrollToNode(hasTestTag("calendar-day-${saved.date}"))
        compose.onNodeWithTag("calendar-day-${saved.date}").performClick()
        compose.onNodeWithTag("log-day").performScrollTo().performClick()
        compose.onNodeWithTag("cramps-NONE").performClick()
        compose.onNodeWithTag("save-day").performClick()
        compose.waitUntil(10_000) {
            runBlocking {
                (compose.activity.application as RitelaApplication)
                    .days.logs.first().single().cramps ==
                    app.ritela.domain.Pain.NONE
            }
        }
    }

    @Test
    fun medicationArticleShowsCautionsBeforeAdultDose() {
        compose.onNodeWithTag("help-all").performScrollTo().performClick()
        compose.onNodeWithTag("help-library").performScrollToNode(hasTestTag("help-ibuprofen"))
        compose.onNodeWithTag("help-ibuprofen").performClick()
        compose.onNodeWithTag("medicine-dose").assertDoesNotExist()
        compose.onNodeWithTag("medicine-ack").performScrollTo().performClick()
        compose.onNodeWithTag("medicine-dose").performScrollTo().assertIsDisplayed()
        saveRendering("help-medication", dialog = true)
    }

    @Test
    @Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp")
    fun englishDayFormAndBackupPasswordAreLocalized() {
        compose.onNodeWithTag("log-day").performScrollTo().performClick()
        compose.onNodeWithText("How was your day?").assertIsDisplayed()
        compose.onNodeWithTag("headache-MILD").performClick()
        saveRendering("day-entry-english", dialog = true)
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("backup-export").performScrollTo().performClick()
        compose.onNodeWithText(
            "At least 12 characters. Keep this password: it cannot be recovered."
        )
            .assertIsDisplayed()
        saveRendering("backup-password-english", dialog = true)
    }

    @Test
    @Config(sdk = [35], qualifiers = "en-rUS-w320dp-h740dp", fontScale = 2f)
    fun dayFormKeepsSaveVisibleAtLargeFont() {
        var saved: app.ritela.domain.DayLog? = null
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    RitelaTheme(darkTheme = true) {
                        app.ritela.ui.DayLogEntry(
                            app.ritela.domain.DayLog(LocalDate.of(2026, 10, 5)),
                            PeriodUiState(loading = false),
                            {},
                            { saved = it }
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("save-day").assertIsDisplayed()
        compose.onNodeWithTag("cramps-MODERATE").performScrollTo().performClick()
        saveRendering("day-entry-narrow-dark-english", dialog = true)
        compose.onNodeWithTag("save-day").performClick()
        assertEquals(app.ritela.domain.Pain.MODERATE, saved?.cramps)
    }

    @Test
    fun periodEntryBlocksOccupiedDaysAndRangesAcrossThemButAllowsEditing() {
        val today = LocalDate.of(2026, 10, 20)
        val record = Period(
            UUID.randomUUID(),
            today.minusDays(10),
            today.minusDays(6),
            Instant.EPOCH,
            Instant.EPOCH
        )
        val state = PeriodUiState(periods = listOf(record), today = today, loading = false)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                RitelaTheme {
                    app.ritela.ui.PeriodEntry(state, {}, { _, _ -> }, initialStart = today)
                }
            }
        }
        compose.onNodeWithTag("entry-day-2026-10-10").performScrollTo().assertIsNotEnabled()
        saveRendering("period-occupied", dialog = true)
        compose.onNodeWithTag("entry-day-2026-10-08").performClick()
        compose.onNodeWithTag("entry-day-2026-10-16").assertIsNotEnabled()
        compose.onNodeWithText("Сохранить").assertIsNotEnabled()
        compose.onNodeWithTag("entry-day-2026-10-09").performClick()
        compose.onNodeWithText("Сохранить").assertIsEnabled()
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                RitelaTheme {
                    key("edit") {
                        app.ritela.ui.PeriodEntry(
                            state,
                            {
                            },
                            { _, _ -> },
                            initialStart = record.start,
                            initialEnd = record.end,
                            editing = true,
                            editingId = record.id
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("entry-day-2026-10-10").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Сохранить").assertIsEnabled()
    }

    @Test
    fun variableMeasurementsRenderSelectableSparklines() {
        val today = LocalDate.of(2026, 10, 6)
        val starts = listOf(180L, 152L, 121L, 94L, 65L, 36L, 7L)
        val periods = starts.mapIndexed { index, offset ->
            val start = today.minusDays(offset)
            Period(
                UUID(0, index.toLong()),
                start,
                start.plusDays(3L + index % 3),
                Instant.EPOCH,
                Instant.EPOCH
            )
        }
        val state = PeriodUiState(
            periods = periods,
            today = today,
            loading = false,
            analysis = analyzeCycles(periods, today)
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                RitelaTheme { HomeScreen(state = state, today = today) }
            }
        }
        compose.onNodeWithTag("cycle-insights").performScrollTo()
        saveRendering("home-sparklines")
        compose.onNodeWithText("31", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("7 мая 2026 г. · 31 дн.").performScrollTo().assertIsDisplayed()
    }

    private fun renderForecastHome() {
        val today = LocalDate.of(2026, 10, 14)
        val records = (0..6).map {
            val start = today.minusDays(23 + 28L * (6 - it))
            Period(UUID(0, it.toLong()), start, start.plusDays(4), Instant.EPOCH, Instant.EPOCH)
        }
        val state =
            PeriodUiState(
                periods = records.asReversed(),
                loading = false,
                today = today,
                analysis = analyzeCycles(records, today)
            )
        for (dark in listOf(false, true)) {
            compose.activity.runOnUiThread {
                compose.activity.setContent {
                    RitelaTheme(darkTheme = dark) {
                        Scaffold(bottomBar = { AppNavigation(0) {} }) {
                            HomeScreen(it, state, today = today)
                        }
                    }
                }
            }
            compose.onNodeWithText("Ritela").performScrollTo()
            val english = compose.activity.resources.configuration.locales[0].language == "en"
            compose.onNodeWithText(
                if (english) "Cycle day" else "День цикла"
            ).assertIsDisplayed()
            compose.onNodeWithText(
                if (english) "Log period" else "Отметить месячные"
            ).assertIsDisplayed()
            val name = if (dark) "home-forecast-dark" else "home-forecast"
            saveRendering(if (english) "$name-english" else name)
            compose.onNodeWithTag("cycle-insights").performScrollTo()
            val insights = if (dark) "home-insights-dark" else "home-insights"
            saveRendering(if (english) "$insights-english" else insights)
            compose.onNodeWithTag("help-cards").performScrollTo()
            val help = if (dark) "home-help-dark" else "home-help"
            saveRendering(if (english) "$help-english" else help)
        }
    }

    private fun saveRendering(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val bitmap = if (dialog) {
            // Robolectric PixelCopy can sample the Activity behind a separate dialog window.
            val decor = requireNotNull(
                ShadowDialog.getShownDialogs().last {
                    it.isShowing
                }.window
            ).decorView
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
