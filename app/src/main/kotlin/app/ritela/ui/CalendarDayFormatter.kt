package app.ritela.ui

import android.content.res.Resources
import app.ritela.R
import app.ritela.domain.CalendarDayKind
import app.ritela.domain.CalendarSelection
import app.ritela.domain.CycleForecast
import app.ritela.domain.Period
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One locale-aware source for calendar dates, primary states and short descriptions. */
class CalendarDayFormatter(private val resources: Resources, locale: Locale) {
    private val dateFormat = DateTimeFormatter.ofLocalizedDate(
        FormatStyle.MEDIUM
    ).withLocale(locale)
    private val shortFormat = DateTimeFormatter.ofPattern(
        if (locale.language == "ru") "d MMM" else "MMM d",
        locale
    )
    fun date(value: LocalDate): String = value.format(dateFormat)
    fun range(start: LocalDate, end: LocalDate): String = if (start == end) {
        date(start)
    } else if (start.year == end.year) {
        resources.getString(
            R.string.period_range,
            start.format(shortFormat),
            end.format(shortFormat)
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
