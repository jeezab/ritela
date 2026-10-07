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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.ritela.R
import app.ritela.domain.CalendarDayInfo
import app.ritela.domain.CalendarDayKind
import app.ritela.domain.ConceptionEstimate
import app.ritela.domain.Period
import app.ritela.domain.calendarDay
import app.ritela.domain.conceptionEstimate
import app.ritela.domain.journalSelections
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
    val list = rememberLazyListState(initialFirstVisibleItemIndex = 1200)
    val scope = rememberCoroutineScope()
    var selectedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var choosingMonth by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var legendOpen by rememberSaveable { mutableStateOf(false) }
    val visibleIndex by remember { derivedStateOf { list.firstVisibleItemIndex } }
    val month = base.plusMonths((visibleIndex - 1200).toLong())
    val cellHeight = maxOf(56.dp, (40 * LocalDensity.current.fontScale).dp)
    fun showMonth(index: Int) {
        scope.launch { list.scrollToItem(index.coerceIn(0, 2400)) }
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(
            Modifier.padding(horizontal = Spacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.calendar_title),
                Modifier.weight(1f).testTag("calendar-heading"),
                style = MaterialTheme.typography.headlineMedium
            )
            androidx.compose.material3.IconButton(onClick = { legendOpen = true }) {
                Icon(
                    painterResource(R.drawable.ic_info),
                    contentDescription = stringResource(R.string.calendar_key)
                )
            }
        }
        MonthHeader(
            month,
            { showMonth(visibleIndex - 1) },
            { showMonth(visibleIndex + 1) },
            { choosingMonth = true }
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                showMonth(1200)
            }, modifier = Modifier.weight(1f).testTag("calendar-today")) {
                Text(stringResource(R.string.home_title))
            }
            TextButton(onClick = {
                historyOpen = true
            }, modifier = Modifier.weight(1f).testTag("calendar-history")) {
                Text(stringResource(R.string.history_short))
            }
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("month-grid")
        ) {
            items(2401, key = { it }) { index ->
                val displayed = base.plusMonths((index - 1200).toLong())
                Column(Modifier.testTag("calendar-month-$displayed")) {
                    Text(
                        displayed.format(
                            DateTimeFormatter.ofPattern(
                                "LLLL yyyy",
                                LocalConfiguration.current.locales[0]
                            )
                        ),
                        Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.medium),
                        style = MaterialTheme.typography.titleMedium
                    )
                    MonthGrid(
                        displayed,
                        state.today,
                        selectedDay?.let(LocalDate::ofEpochDay),
                        cellHeight = cellHeight,
                        info = { calendarDay(state.periods, state.analysis, it, state.today) },
                        hasLog = { day -> state.dayLogs.any { it.date == day } },
                        logIcon = { day ->
                            val log = state.dayLogs.firstOrNull { it.date == day }
                            val icon =
                                log?.calendarIcon ?: state.journalLayout.sections.firstOrNull {
                                    it.id in
                                        log?.journalSelections().orEmpty().filterValues { tags ->
                                            tags.isNotEmpty()
                                        }
                                }?.icon ?: app.ritela.domain.JournalIcon.NOTE
                            journalIcon(icon)
                        },
                        onDay = { selectedDay = it.toEpochDay() }
                    )
                }
            }
        }
    }
    if (legendOpen) {
        AlertDialog(
            onDismissRequest = { legendOpen = false },
            title = { Text(stringResource(R.string.calendar_key)) },
            text = { Text(stringResource(R.string.calendar_legend)) },
            confirmButton = {
                TextButton(onClick = { legendOpen = false }) {
                    Text(stringResource(R.string.done))
                }
            }
        )
    }
    if (choosingMonth) {
        MonthYearPicker(month, (base.year - 100)..(base.year + 100), { choosingMonth = false }) {
            choosingMonth = false
            showMonth(1200 + (it.year - base.year) * 12 + it.monthValue - base.monthValue)
        }
    }
    selectedDay?.let { epoch ->
        CalendarDayDetails(
            LocalDate.ofEpochDay(epoch),
            state,
            onDismiss = { selectedDay = null },
            onAdd = {
                selectedDay = null
                onAdd(it)
            },
            onEdit = {
                selectedDay = null
                onEdit(it)
            },
            onDelete = {
                selectedDay = null
                onDelete(it)
            },
            onLogDay = {
                selectedDay = null
                onLogDay(it)
            }
        )
    }
    if (historyOpen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { historyOpen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(Spacing.medium)) {
                    Text(
                        stringResource(R.string.calendar_history),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                    ) {
                        if (state.periods.isEmpty()) Text(stringResource(R.string.history_empty))
                        state.periods.forEach { period ->
                            Text(periodDates(period), style = MaterialTheme.typography.titleMedium)
                            if (period.end == null) Text(stringResource(R.string.ongoing))
                            Row {
                                TextButton(
                                    onClick = {
                                        historyOpen = false
                                        onEdit(period)
                                    },
                                    enabled = !state.saving,
                                    modifier = Modifier.testTag("edit-period-${period.id}")
                                ) {
                                    Text(stringResource(R.string.edit))
                                }
                                TextButton(
                                    onClick = {
                                        historyOpen = false
                                        onDelete(period)
                                    },
                                    enabled = !state.saving,
                                    modifier = Modifier.testTag("delete-period-${period.id}")
                                ) {
                                    Text(stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                    TextButton(onClick = {
                        historyOpen = false
                    }) { Text(stringResource(R.string.done)) }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDayDetails(
    date: LocalDate,
    state: PeriodUiState,
    onDismiss: () -> Unit,
    onAdd: (LocalDate) -> Unit,
    onEdit: (Period) -> Unit,
    onDelete: (Period) -> Unit,
    onLogDay: (LocalDate) -> Unit
) {
    val selection = calendarDay(state.periods, state.analysis, date, state.today)
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(Spacing.large).testTag("day-details"),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Text(formattedDate(date), style = MaterialTheme.typography.headlineSmall)
            if (selection.kind != CalendarDayKind.NONE) Text(dayKindText(selection.kind))
            selection.period?.let { period ->
                Text(periodDates(period))
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
            } ?: run {
                selection.forecast?.let {
                    Text(
                        stringResource(
                            R.string.forecast_range,
                            formattedDate(it.lowerBound),
                            formattedDate(it.upperBound)
                        )
                    )
                }
                if (date <= state.today) {
                    Button(onClick = { onAdd(date) }, enabled = !state.loading && !state.saving) {
                        Text(stringResource(R.string.add_period))
                    }
                }
            }
            Text(
                stringResource(R.string.pregnancy_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(
                    when (conceptionEstimate(state.periods, state.analysis, date, state.today)) {
                        ConceptionEstimate.PEAK -> R.string.conception_peak
                        ConceptionEstimate.HIGHER -> R.string.conception_higher
                        ConceptionEstimate.LOWER -> R.string.conception_outside
                        ConceptionEstimate.UNKNOWN -> R.string.conception_unconfirmed
                    }
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        (
                            "https://www.nhs.uk/contraception/methods-of-contraception/" +
                                "natural-family-planning/"
                            ).toUri()
                    )
                )
            }) { Text(stringResource(R.string.help_source, "NHS")) }
            if (date <= state.today) {
                DaySummary(
                    state.dayLogs.firstOrNull { it.date == date },
                    { onLogDay(date) },
                    !state.loading && !state.saving,
                    layout = state.journalLayout
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("day-details-close")) {
                Text(stringResource(R.string.done))
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
    dayEnabled: (LocalDate) -> Boolean = { true },
    logIcon: (LocalDate) -> Int = { R.drawable.ic_note },
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
        val days = monthDays(month)
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
                            enabled = (futureEnabled || day <= today) && dayEnabled(day),
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
                                    CalendarColors.period

                                detail.kind == CalendarDayKind.OVULATION_ESTIMATE ->
                                    CalendarColors.ovulation

                                detail.kind == CalendarDayKind.FERTILE_ESTIMATE ->
                                    CalendarColors.fertile

                                detail.kind !=
                                    CalendarDayKind.NONE ->
                                    CalendarColors.estimatedPeriod

                                else -> MaterialTheme.colorScheme.surface
                            },
                            contentColor = if (inRange || detail.kind == CalendarDayKind.OBSERVED) {
                                CalendarColors.ink
                            } else if (detail.kind != CalendarDayKind.NONE) {
                                CalendarColors.ink
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            border = if (day == chosen ||
                                day == today
                            ) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                if (detail.kind == CalendarDayKind.PREDICTED) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                                } else {
                                    null
                                }
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
                                if ((detail.kind == CalendarDayKind.OBSERVED || inRange) &&
                                    !hasLog(day)
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_drop),
                                        contentDescription = null,
                                        modifier = Modifier.height(12.dp)
                                    )
                                } else if (hasLog(day)) {
                                    Icon(
                                        painterResource(logIcon(day)),
                                        contentDescription = null,
                                        modifier = Modifier.height(12.dp)
                                    )
                                } else {
                                    Text(
                                        when (detail.kind) {
                                            CalendarDayKind.PREDICTED -> "\u25c7"
                                            CalendarDayKind.APPROXIMATE -> "\u2248"
                                            CalendarDayKind.UNCERTAIN -> "\u00b7"
                                            CalendarDayKind.ESTIMATED_PERIOD -> "\u25cb"
                                            CalendarDayKind.OVULATION_ESTIMATE -> "\u273f"
                                            CalendarDayKind.FERTILE_ESTIMATE -> "\u273f"
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
        CalendarDayKind.FERTILE_ESTIMATE -> R.string.fertile_estimate
        CalendarDayKind.OVULATION_ESTIMATE -> R.string.ovulation_estimate
    }
)
