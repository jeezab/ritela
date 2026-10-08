package app.ritela

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.ritela.domain.CalendarDayKind
import app.ritela.ui.CalendarDayFormatter
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarDayFormatterTest {
    private fun formatter(language: String): CalendarDayFormatter {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locale = Locale.forLanguageTag(language)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return CalendarDayFormatter(
            context.createConfigurationContext(configuration).resources,
            locale
        )
    }

    @Test fun singleMainStatusIsLocalizedAndForecastVariantsDoNotRepeatTheirMeaning() {
        for ((language, expected) in listOf(
            "ru-RU" to "Ожидаемые месячные",
            "en-US" to "Expected period"
        )) {
            val formatter = formatter(language)
            for (kind in listOf(
                CalendarDayKind.PREDICTED,
                CalendarDayKind.APPROXIMATE,
                CalendarDayKind.UNCERTAIN,
                CalendarDayKind.ESTIMATED_PERIOD
            )) {
                assertEquals(expected, formatter.status(kind))
            }
            assertEquals(5, CalendarDayKind.entries.map { formatter.status(it) }.distinct().size)
        }
    }

    @Test fun datesAndRangesHandleMonthsYearsAndSingleDayWithoutDuplicateDates() {
        for (language in listOf("ru-RU", "en-US")) {
            val formatter = formatter(language)
            val start = LocalDate.of(2026, 12, 30)
            val end = LocalDate.of(2027, 1, 3)
            assertEquals(formatter.date(start), formatter.range(start, start))
            val range = formatter.range(start, end)
            assertTrue(range.contains("2026"))
            assertTrue(range.contains("2027"))
            val sameYear = formatter.range(LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 2))
            assertTrue(sameYear.contains("29"))
            assertTrue(sameYear.contains("2"))
        }
    }
}
