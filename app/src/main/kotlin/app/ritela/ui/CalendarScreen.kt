package app.ritela.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.sp
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
    HomeTheme { CalendarContent(padding, state, onAdd, onEdit, onDelete, onLogDay) }
}

@Composable
private fun CalendarContent(
    padding: PaddingValues,
    state: PeriodUiState,
    onAdd: (LocalDate) -> Unit,
    onEdit: (Period) -> Unit,
    onDelete: (Period) -> Unit,
    onLogDay: (LocalDate) -> Unit
) {
    val base = remember { YearMonth.from(state.today) }
    var monthStream by rememberSaveable { mutableStateOf(false) }
    var monthIndex by rememberSaveable { mutableIntStateOf(1200) }
    val historyList = rememberLazyListState(initialFirstVisibleItemIndex = 1200)
    val singleMonthList = rememberLazyListState()
    val list = if (monthStream) historyList else singleMonthList
    val scope = rememberCoroutineScope()
    var selectedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var choosingMonth by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var legendOpen by rememberSaveable { mutableStateOf(false) }
    val streamIndex by remember { derivedStateOf { historyList.firstVisibleItemIndex } }
    val visibleIndex = if (monthStream) streamIndex else monthIndex
    val month = base.plusMonths((visibleIndex - 1200).toLong())
    val cellHeight = maxOf(56.dp, (40 * LocalDensity.current.fontScale).dp)
    fun showMonth(index: Int) {
        monthIndex = index.coerceIn(0, 2400)
        scope.launch {
            if (monthStream) {
                historyList.scrollToItem(
                    monthIndex
                )
            } else {
                singleMonthList.scrollToItem(0)
            }
        }
    }
    Column(
        Modifier.fillMaxSize().background(HomeColors.background).padding(padding)
            .padding(horizontal = HomeSpacing.gutter)
    ) {
        CalendarAtmosphericHeader { legendOpen = true }
        MonthHeader(
            month,
            { showMonth(visibleIndex - 1) },
            { showMonth(visibleIndex + 1) },
            { choosingMonth = true },
            atmospheric = true
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                showMonth(1200)
            }, modifier = Modifier.weight(1f).testTag("calendar-today")) {
                if (LocalDensity.current.fontScale > 1.3f) {
                    Icon(painterResource(R.drawable.ic_today), stringResource(R.string.home_title))
                } else {
                    Text(stringResource(R.string.home_title))
                }
            }
            TextButton(onClick = {
                historyOpen = true
            }, modifier = Modifier.weight(1f).testTag("calendar-history")) {
                if (LocalDensity.current.fontScale > 1.3f) {
                    Icon(
                        painterResource(R.drawable.ic_chart),
                        stringResource(R.string.history_short)
                    )
                } else {
                    Text(stringResource(R.string.history_short))
                }
            }
            androidx.compose.material3.IconButton(
                onClick = {
                    monthIndex = visibleIndex
                    monthStream = !monthStream
                    if (monthStream) scope.launch { historyList.scrollToItem(monthIndex) }
                },
                modifier = Modifier.testTag("calendar-mode")
            ) {
                Icon(
                    painterResource(R.drawable.ic_calendar),
                    stringResource(
                        if (monthStream) {
                            R.string.calendar_single_month
                        } else {
                            R.string.calendar_month_stream
                        }
                    ),
                    tint = if (monthStream) HomeColors.peach else HomeColors.muted
                )
            }
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("month-grid")
        ) {
            items(if (monthStream) 2401 else 1, key = {
                if (monthStream) it else monthIndex
            }) { index ->
                val displayed = base.plusMonths(
                    ((if (monthStream) index else monthIndex) - 1200).toLong()
                )
                Column(Modifier.testTag("calendar-month-$displayed")) {
                    if (monthStream) {
                        Text(
                            displayed.format(
                                DateTimeFormatter.ofPattern(
                                    "LLLL yyyy",
                                    LocalConfiguration.current.locales[0]
                                )
                            ),
                            Modifier.padding(
                                horizontal = Spacing.medium,
                                vertical = Spacing.medium
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    MonthGrid(
                        displayed,
                        state.today,
                        selectedDay?.let(LocalDate::ofEpochDay),
                        modifier = Modifier.border(
                            1.dp,
                            HomeColors.border.copy(alpha = 0.6f),
                            MaterialTheme.shapes.large
                        )
                            .background(
                                HomeColors.card.copy(alpha = 0.2f),
                                MaterialTheme.shapes.large
                            )
                            .padding(vertical = 8.dp),
                        cellHeight = cellHeight,
                        atmospheric = true,
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
                    if (!monthStream) CalendarSummaries(state)
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
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()
    var headerDrag by remember { mutableFloatStateOf(0f) }
    val dismissDistance = with(LocalDensity.current) { 32.dp.toPx() }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetGesturesEnabled = false,
        dragHandle = null,
        sheetState = sheetState
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.9f)
                .graphicsLayer { translationY = headerDrag }.testTag("day-details-frame")
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .testTag("day-details-drag-header")
                    .pointerInput(sheetState, dismissDistance) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                headerDrag = (headerDrag + amount).coerceAtLeast(0f)
                            },
                            onDragCancel = { headerDrag = 0f },
                            onDragEnd = {
                                if (headerDrag >= dismissDistance) {
                                    scope.launch {
                                        sheetState.hide()
                                        onDismiss()
                                    }
                                } else {
                                    headerDrag = 0f
                                }
                            }
                        )
                    }.padding(horizontal = Spacing.large),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formattedDate(date),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall
                )
                androidx.compose.material3.IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("day-details-header-close")
                ) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.done))
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(Spacing.large).testTag("day-details"),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
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
                        Button(onClick = {
                            onAdd(date)
                        }, enabled = !state.loading && !state.saving) {
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
                        when (
                            conceptionEstimate(
                                state.periods,
                                state.analysis,
                                date,
                                state.today
                            )
                        ) {
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
}

@Composable
fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChoose: () -> Unit,
    atmospheric: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (atmospheric) 0.dp else Spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val previous = stringResource(R.string.previous_month)
        val next = stringResource(R.string.next_month)
        TextButton(
            onClick = onPrevious,
            modifier = Modifier.then(
                if (atmospheric) Modifier.size(48.dp) else Modifier
            ).testTag("previous-month").semantics {
                contentDescription =
                    previous
            }
        ) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
        TextButton(onClick = onChoose, modifier = Modifier.weight(1f).testTag("month-selector")) {
            Text(
                month.format(
                    DateTimeFormatter.ofPattern(
                        if (atmospheric && LocalDensity.current.fontScale > 1.3f) {
                            "LLLL\nyyyy"
                        } else {
                            "LLLL yyyy"
                        },
                        LocalConfiguration.current.locales[0]
                    )
                ),
                style = if (atmospheric) {
                    MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp)
                } else {
                    MaterialTheme.typography.titleLarge
                },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (!atmospheric) Text(" ▾")
        }
        TextButton(
            onClick = onNext,
            modifier = Modifier.then(
                if (atmospheric) Modifier.size(48.dp) else Modifier
            ).testTag("next-month").semantics {
                contentDescription =
                    next
            }
        ) { Text("›", style = MaterialTheme.typography.headlineMedium) }
    }
}

@Composable
fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    chosen: LocalDate?,
    modifier: Modifier = Modifier,
    cellHeight: androidx.compose.ui.unit.Dp = 56.dp,
    atmospheric: Boolean = false,
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
    Column(
        modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(if (atmospheric) 4.dp else 0.dp)
    ) {
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (atmospheric) 3.dp else 0.dp)
            ) {
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
                                    if (atmospheric) {
                                        CalendarDesign.period
                                    } else {
                                        CalendarColors.period
                                    }

                                detail.kind == CalendarDayKind.OVULATION_ESTIMATE ->
                                    if (atmospheric) {
                                        CalendarDesign.ovulation
                                    } else {
                                        CalendarColors.ovulation
                                    }

                                detail.kind == CalendarDayKind.FERTILE_ESTIMATE ->
                                    if (atmospheric) {
                                        CalendarDesign.fertile
                                    } else {
                                        CalendarColors.fertile
                                    }

                                detail.kind !=
                                    CalendarDayKind.NONE ->
                                    if (atmospheric) {
                                        CalendarDesign.estimatedPeriod
                                    } else {
                                        CalendarColors.estimatedPeriod
                                    }

                                else -> if (atmospheric) {
                                    androidx.compose.ui.graphics.Color.Transparent
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
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
                                BorderStroke(
                                    if (atmospheric && day == chosen) {
                                        2.dp
                                    } else {
                                        1.dp
                                    },
                                    if (atmospheric) {
                                        HomeColors.peach
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
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
