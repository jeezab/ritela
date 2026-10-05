package app.ritela.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.ritela.R
import app.ritela.domain.CalendarDayInfo
import app.ritela.domain.CalendarDayKind
import app.ritela.domain.Period
import app.ritela.domain.calendarDay
import app.ritela.domain.monthDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlinx.coroutines.launch

@Composable
fun CalendarScreen(
    padding: PaddingValues,
    state: PeriodUiState,
    onAdd: (LocalDate) -> Unit,
    onEdit: (Period) -> Unit,
    onDelete: (Period) -> Unit,
    onLogDay: (LocalDate) -> Unit = {}
) {
    val base = remember { YearMonth.from(state.today) }
    val pager = rememberPagerState(initialPage = 1200) { 2401 }
    val scope = rememberCoroutineScope()
    var selectedDay by rememberSaveable { mutableStateOf(state.today.toEpochDay()) }
    var choosingMonth by rememberSaveable { mutableStateOf(false) }
    val month = base.plusMonths((pager.currentPage - 1200).toLong())
    val date = LocalDate.ofEpochDay(selectedDay)
    val selection = calendarDay(state.periods, state.analysis, date, state.today)
    val fontScale = LocalDensity.current.fontScale
    val cellHeight = maxOf(56.dp, (40 * fontScale).dp)
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        Text(
            stringResource(R.string.calendar_title),
            Modifier.padding(
                horizontal = Spacing.medium,
                vertical = Spacing.medium
            ).testTag("calendar-heading"),
            style = MaterialTheme.typography.headlineLarge
        )
        MonthHeader(month, {
            scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }
        }, {
            scope.launch {
                pager.animateScrollToPage(
                    (
                        pager.currentPage +
                            1
                        ).coerceAtMost(2400)
                )
            }
        }, { choosingMonth = true })
        TextButton(onClick = {
            selectedDay = state.today.toEpochDay()
            scope.launch {
                pager.scrollToPage(
                    1200 +
                        (
                            (YearMonth.from(state.today).year - base.year) * 12 +
                                state.today.monthValue -
                                base.monthValue
                            )
                )
            }
        }) { Text(stringResource(R.string.back_to_today)) }
        Text(
            stringResource(R.string.vertical_calendar_hint),
            Modifier.padding(horizontal = Spacing.medium),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        VerticalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().height(cellHeight * 6 + 32.dp).testTag("month-grid"),
            beyondViewportPageCount = 0
        ) { page ->
            val displayed = base.plusMonths((page - 1200).toLong())
            MonthGrid(
                displayed,
                state.today,
                date,
                cellHeight = cellHeight,
                tagPrefix = if (page ==
                    pager.currentPage
                ) {
                    "calendar-day"
                } else {
                    "adjacent-day"
                },
                info = { calendarDay(state.periods, state.analysis, it, state.today) },
                hasLog = { day -> state.dayLogs.any { it.date == day } },
                onDay = {
                    selectedDay =
                        it.toEpochDay()
                }
            )
        }
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Text(
                stringResource(R.string.calendar_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(formattedDate(date), style = MaterialTheme.typography.titleLarge)
            Text(dayKindText(selection.kind))
            selection.period?.let { period ->
                Text(periodDates(period))
            } ?: run {
                selection.forecast?.let { forecast ->
                    Text(
                        stringResource(
                            R.string.forecast_range,
                            formattedDate(forecast.lowerBound),
                            formattedDate(forecast.upperBound)
                        )
                    )
                    Text(
                        stringResource(
                            if (forecast.horizon >
                                3
                            ) {
                                R.string.long_forecast_note
                            } else {
                                R.string.forecast_note
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (date <=
                    state.today
                ) {
                    Button(
                        onClick = { onAdd(date) },
                        enabled =
                            !state.loading && !state.saving
                    ) {
                        Text(stringResource(R.string.add_period))
                    }
                } else {
                    Text(
                        stringResource(R.string.future_calendar_note),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (date <= state.today) {
                DaySummary(
                    state.dayLogs.firstOrNull { it.date == date },
                    { onLogDay(date) },
                    !state.loading && !state.saving
                )
                HelpCards(state.dayLogs.firstOrNull { it.date == date }, state.analysis, date)
            }
            Text(
                stringResource(R.string.calendar_history),
                style = MaterialTheme.typography.titleLarge
            )
            state.periods.forEach { period ->
                Text(periodDates(period), style = MaterialTheme.typography.titleMedium)
                if (period.end == null) Text(stringResource(R.string.ongoing))
                Row {
                    TextButton(
                        onClick = { onEdit(period) },
                        enabled = !state.saving,
                        modifier = Modifier.testTag("edit-period-${period.id}")
                    ) {
                        Text(stringResource(R.string.edit))
                    }
                    TextButton(
                        onClick = { onDelete(period) },
                        enabled = !state.saving,
                        modifier = Modifier.testTag("delete-period-${period.id}")
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
            if (state.loading) {
                Text(
                    stringResource(R.string.loading)
                )
            } else {
                ForecastCard(state.analysis)
            }
            state.problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (choosingMonth) {
        MonthYearPicker(month, (base.year - 100)..(base.year + 100), {
            choosingMonth =
                false
        }) {
            choosingMonth = false
            scope.launch {
                pager.scrollToPage(
                    (
                        1200 + (it.year - base.year) * 12 + it.monthValue -
                            base.monthValue
                        ).coerceIn(0, 2400)
                )
            }
        }
    }
}

@Composable
fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChoose: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val previous = stringResource(R.string.previous_month)
        val next = stringResource(R.string.next_month)
        TextButton(
            onClick = onPrevious,
            modifier = Modifier.testTag("previous-month").semantics {
                contentDescription =
                    previous
            }
        ) { Text("⌃", style = MaterialTheme.typography.headlineMedium) }
        TextButton(onClick = onChoose, modifier = Modifier.weight(1f).testTag("month-selector")) {
            Text(
                month.format(
                    DateTimeFormatter.ofPattern("LLLL yyyy", LocalConfiguration.current.locales[0])
                ),
                style = MaterialTheme.typography.titleLarge
            )
            Text(" ▾")
        }
        TextButton(
            onClick = onNext,
            modifier = Modifier.testTag("next-month").semantics {
                contentDescription =
                    next
            }
        ) { Text("⌄", style = MaterialTheme.typography.headlineMedium) }
    }
}

@Composable
fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    chosen: LocalDate?,
    modifier: Modifier = Modifier,
    cellHeight: androidx.compose.ui.unit.Dp = 56.dp,
    tagPrefix: String = "calendar-day",
    rangeStart: LocalDate? = null,
    rangeEnd: LocalDate? = null,
    futureEnabled: Boolean = true,
    info: (LocalDate) -> CalendarDayInfo = { CalendarDayInfo(CalendarDayKind.NONE) },
    hasLog: (LocalDate) -> Boolean = { false },
    onDay: (LocalDate) -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val compact = LocalDensity.current.fontScale > 1.3f
    Column(modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp)) {
            for (weekday in DayOfWeek.entries) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        weekday.getDisplayName(
                            if (compact) TextStyle.NARROW else TextStyle.SHORT,
                            locale
                        ),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        val days = monthDays(month).let { it + List<LocalDate?>(42 - it.size) { null } }
        for (week in days.chunked(7)) {
            Row(Modifier.fillMaxWidth()) {
                for (day in week) {
                    if (day == null) {
                        Spacer(Modifier.weight(1f).height(cellHeight))
                    } else {
                        val detail = info(day)
                        val inRange =
                            rangeStart != null && day >= rangeStart &&
                                day <= (rangeEnd ?: rangeStart)
                        val selected = day == chosen || inRange
                        val description = formattedDate(day) + ", " + dayKindText(detail.kind) +
                            if (hasLog(day)) ", " + stringResource(R.string.day_has_log) else ""
                        Surface(
                            onClick = {
                                onDay(day)
                            },
                            enabled = futureEnabled || day <= today,
                            modifier = Modifier.weight(
                                1f
                            ).height(cellHeight).testTag("$tagPrefix-$day").semantics {
                                contentDescription =
                                    description
                                this.selected = selected
                            },
                            shape = MaterialTheme.shapes.small,
                            color = when {
                                inRange || detail.kind == CalendarDayKind.OBSERVED ->
                                    MaterialTheme.colorScheme.primaryContainer

                                detail.kind !=
                                    CalendarDayKind.NONE ->
                                    MaterialTheme.colorScheme.secondaryContainer.copy(
                                        alpha = 0.5f
                                    )

                                else -> MaterialTheme.colorScheme.surface
                            },
                            border = if (day == chosen ||
                                day == today
                            ) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    day.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (day ==
                                        today
                                    ) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    textDecoration = if (day ==
                                        today
                                    ) {
                                        TextDecoration.Underline
                                    } else {
                                        null
                                    }
                                )
                                Text(
                                    when {
                                        inRange -> "●"

                                        detail.kind == CalendarDayKind.OBSERVED -> "●"

                                        detail.kind ==
                                            CalendarDayKind.PREDICTED -> "◇"

                                        detail.kind ==
                                            CalendarDayKind.APPROXIMATE -> "≈"

                                        detail.kind ==
                                            CalendarDayKind.UNCERTAIN -> "·"

                                        detail.kind ==
                                            CalendarDayKind.ESTIMATED_PERIOD -> "○"

                                        hasLog(day) -> "?"

                                        else -> " "
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthYearPicker(
    initial: YearMonth,
    years: IntRange,
    onDismiss: () -> Unit,
    onChoose: (YearMonth) -> Unit
) {
    var year by rememberSaveable { mutableStateOf(initial.year) }
    var choosingYear by rememberSaveable { mutableStateOf(false) }
    val list = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = (
            year -
                years.first -
                2
            ).coerceAtLeast(0)
    )
    val locale = LocalConfiguration.current.locales[0]
    AlertDialog(onDismissRequest = onDismiss, title = {
        Text(stringResource(R.string.choose_month))
    }, text = {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            TextButton(onClick = {
                choosingYear = !choosingYear
            }, modifier = Modifier.fillMaxWidth().testTag("month-year")) {
                Text(
                    stringResource(R.string.year) + " · " + year + " ▾",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            if (choosingYear) {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                ) {
                    items(years.count()) { index ->
                        val value = years.first + index
                        TextButton(onClick = {
                            year = value
                            choosingYear = false
                        }, modifier = Modifier.fillMaxWidth().testTag("select-year-$value")) {
                            Text(
                                value.toString(),
                                color = if (year ==
                                    value
                                ) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
            for (row in (1..12).chunked(3)) {
                Row(Modifier.fillMaxWidth()) {
                    for (number in row) {
                        TextButton(
                            onClick = {
                                onChoose(YearMonth.of(year, number))
                            },
                            modifier = Modifier.weight(
                                1f
                            ).heightIn(min = 48.dp).testTag("select-month-$number")
                        ) {
                            Text(
                                java.time.Month.of(
                                    number
                                ).getDisplayName(TextStyle.SHORT_STANDALONE, locale)
                            )
                        }
                    }
                }
            }
        }
    }, confirmButton = {
    }, dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    })
}

@Composable
private fun dayKindText(kind: CalendarDayKind): String = stringResource(
    when (kind) {
        CalendarDayKind.OBSERVED -> R.string.calendar_observed
        CalendarDayKind.PREDICTED -> R.string.calendar_predicted
        CalendarDayKind.UNCERTAIN -> R.string.calendar_uncertain
        CalendarDayKind.APPROXIMATE -> R.string.calendar_approximate
        CalendarDayKind.ESTIMATED_PERIOD -> R.string.calendar_estimated_period
        CalendarDayKind.NONE -> R.string.calendar_empty
    }
)
