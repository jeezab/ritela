package app.ritela.ui

import android.content.res.Resources
import app.ritela.R
import app.ritela.domain.CalendarDayKind
import app.ritela.domain.CalendarSelection
import app.ritela.domain.CycleForecast
import app.ritela.domain.Period
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

internal fun monthYearLabel(month: YearMonth, locale: Locale): String {
    val title = if (locale.language == "ru") {
        RUSSIAN_MONTHS[month.monthValue - 1].replaceFirstChar { it.titlecase(locale) }
    } else {
        val names = android.icu.text.DateFormatSymbols.getInstance(locale).getMonths(
            android.icu.text.DateFormatSymbols.STANDALONE,
            android.icu.text.DateFormatSymbols.WIDE
        )
        val raw = names.getOrNull(month.monthValue - 1).orEmpty()
        raw.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(locale) else char.toString()
        }.ifEmpty { month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) }
    }
    return "$title ${month.year}"
}

internal fun monthShortLabel(month: Int, locale: Locale): String = if (locale.language == "ru") {
    RUSSIAN_MONTHS_SHORT[month - 1]
} else {
    val names = android.icu.text.DateFormatSymbols.getInstance(locale).getMonths(
        android.icu.text.DateFormatSymbols.STANDALONE,
        android.icu.text.DateFormatSymbols.ABBREVIATED
    )
    names.getOrNull(month - 1).orEmpty().replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(locale) else char.toString()
    }.ifEmpty { java.time.Month.of(month).getDisplayName(TextStyle.SHORT_STANDALONE, locale) }
}

private val RUSSIAN_MONTHS = listOf(
    "январь", "февраль", "март", "апрель", "май", "июнь",
    "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"
)
private val RUSSIAN_MONTHS_GENITIVE = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря"
)
private val RUSSIAN_MONTHS_SHORT = listOf(
    "янв.", "февр.", "март", "апр.", "май", "июнь",
    "июль", "авг.", "сент.", "окт.", "нояб.", "дек."
)

/** One locale-aware source for calendar dates, primary states and short descriptions. */
class CalendarDayFormatter(private val resources: Resources, private val locale: Locale) {
    private val zone = android.icu.util.TimeZone.GMT_ZONE
    private val dateFormat = android.icu.text.SimpleDateFormat("MMM d, yyyy", locale).apply {
        timeZone = zone
    }
    private val shortFormat = android.icu.text.SimpleDateFormat(
        if (locale.language == "ru") "d MMM" else "MMM d",
        locale
    ).apply { timeZone = zone }
    private fun format(formatter: android.icu.text.DateFormat, value: LocalDate): String {
        if (locale.language == "ru") return russianDate(value, short = false)
        val calendar = android.icu.util.Calendar.getInstance(zone, locale)
        calendar.set(value.year, value.monthValue - 1, value.dayOfMonth, 12, 0, 0)
        calendar.set(android.icu.util.Calendar.MILLISECOND, 0)
        return formatter.format(calendar)
    }
    private fun russianDate(value: LocalDate, short: Boolean): String {
        val month = if (short) {
            RUSSIAN_MONTHS_SHORT[value.monthValue - 1]
        } else {
            RUSSIAN_MONTHS_GENITIVE[value.monthValue - 1]
        }
        return if (short) {
            "${value.dayOfMonth} $month"
        } else {
            "${value.dayOfMonth} $month ${value.year}"
        }
    }
    fun date(value: LocalDate): String = format(dateFormat, value)
    fun range(start: LocalDate, end: LocalDate): String = if (start == end) {
        date(start)
    } else if (start.year == end.year) {
        resources.getString(
            R.string.period_range,
            if (locale.language == "ru") {
                russianDate(start, short = true)
            } else {
                format(shortFormat, start)
            },
            if (locale.language == "ru") {
                russianDate(end, short = true)
            } else {
                format(shortFormat, end)
            }
        )
    } else {
        resources.getString(R.string.period_range, date(start), date(end))
    }

    fun status(kind: CalendarDayKind): String = resources.getString(
        when (kind) {
            CalendarDayKind.OBSERVED -> R.string.selected_day_period

            CalendarDayKind.PREDICTED, CalendarDayKind.UNCERTAIN,
            CalendarDayKind.APPROXIMATE, CalendarDayKind.ESTIMATED_PERIOD ->
                R.string.selected_day_expected

            CalendarDayKind.FERTILE_LIKELY, CalendarDayKind.FERTILE_ESTIMATE ->
                R.string.selected_day_fertile

            CalendarDayKind.OVULATION_ESTIMATE -> R.string.ovulation_estimate

            CalendarDayKind.NONE -> R.string.selected_day_ordinary
        }
    )
    fun cycle(selection: CalendarSelection): String? = selection.cycleDay?.let {
        resources.getString(R.string.hero_cycle_day, it)
    }
    fun period(value: Period): String = value.end?.let { range(value.start, it) }
        ?: resources.getString(R.string.selected_day_since, date(value.start))
    fun expected(value: CycleForecast): String = resources.getString(
        R.string.selected_day_start_range,
        range(value.lowerBound, value.upperBound)
    )
    fun uncertainty(selection: CalendarSelection): List<String> = selection.possibleWindow?.let {
        listOf(
            resources.getString(
                R.string.selected_day_possible_range,
                range(it.possible.start, it.possible.endInclusive)
            ),
            resources.getString(
                R.string.ovulation_dates,
                date(it.ovulation.earliest),
                date(it.ovulation.latest)
            )
        )
    }.orEmpty()
}
