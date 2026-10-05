package app.ritela.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import app.ritela.R
import app.ritela.domain.CalendarDayKind
import app.ritela.domain.Period
import app.ritela.domain.calendarDay
import app.ritela.domain.monthDays
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun CalendarScreen(
    padding: PaddingValues,
    state: PeriodUiState,
    onAdd: (LocalDate) -> Unit,
    onEdit: (Period) -> Unit,
    onDelete: (Period) -> Unit
) {
    var offset by rememberSaveable { mutableStateOf(0) }
    var selectedDay by rememberSaveable { mutableStateOf(state.today.toEpochDay()) }
    val month = YearMonth.from(state.today).plusMonths(offset.toLong())
    val date = LocalDate.ofEpochDay(selectedDay)
    val selection = calendarDay(state.periods, state.analysis, date, state.today)
    val swipeDistance = with(LocalDensity.current) { Spacing.actionHeight.toPx() }
    Column(
        modifier = Modifier.fillMaxSize().padding(
            padding
        ).verticalScroll(rememberScrollState()).padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.large)
    ) {
        Text(
            stringResource(R.string.calendar_title),
            modifier = Modifier.testTag("calendar-heading"),
            style = MaterialTheme.typography.headlineLarge
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val previous = stringResource(R.string.previous_month)
            TextButton(
                onClick = {
                    offset--
                },
                modifier = Modifier.testTag("previous-month").semantics {
                    contentDescription =
                        previous
                }
            ) {
                Text("‹", style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                month.format(
                    DateTimeFormatter.ofPattern("LLLL yyyy", LocalConfiguration.current.locales[0])
                ),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            val next = stringResource(R.string.next_month)
            TextButton(
                onClick = { offset++ },
                modifier = Modifier.testTag("next-month").semantics {
                    contentDescription =
                        next
                }
            ) {
                Text("›", style = MaterialTheme.typography.headlineMedium)
            }
        }
        TextButton(onClick = {
            offset = 0
            selectedDay = state.today.toEpochDay()
        }) {
            Text(stringResource(R.string.back_to_today))
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gridWidth = maxOf(maxWidth, Spacing.calendarMinimumWidth)
            val scroll = rememberScrollState()
            val swipe = if (gridWidth <= maxWidth) {
                Modifier.pointerInput(month) {
                    var distance = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { distance = 0f },
                        onDragEnd = {
                            if (abs(distance) >= swipeDistance) {
                                offset += if (distance < 0) 1 else -1
                            }
                        },
                        onHorizontalDrag = { change, amount ->
                            distance += amount
                            change.consume()
                        }
                    )
                }
            } else {
                Modifier
            }
            Column(
                modifier = Modifier.horizontalScroll(
                    scroll
                ).width(gridWidth).then(swipe).testTag("month-grid")
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (weekday in listOf(
                        R.string.mon,
                        R.string.tue,
                        R.string.wed,
                        R.string.thu,
                        R.string.fri,
                        R.string.sat,
                        R.string.sun
                    )) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                stringResource(weekday),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                for (week in monthDays(month).chunked(7)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (day in week) {
                            if (day == null) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                val info =
                                    calendarDay(state.periods, state.analysis, day, state.today)
                                val label = formattedDate(day) + ", " + dayKindText(info.kind) +
                                    if (day ==
                                        state.today
                                    ) {
                                        ", " + stringResource(R.string.home_title)
                                    } else {
                                        ""
                                    }
                                val chosen = day.toEpochDay() == selectedDay
                                Surface(
                                    onClick = { selectedDay = day.toEpochDay() },
                                    modifier = Modifier.weight(
                                        1f
                                    ).heightIn(min = Spacing.calendarCellHeight)
                                        .testTag("calendar-day-$day").semantics {
                                            contentDescription =
                                                label
                                            selected = chosen
                                        },
                                    shape = MaterialTheme.shapes.small,
                                    color = when (info.kind) {
                                        CalendarDayKind.OBSERVED ->
                                            MaterialTheme.colorScheme.primaryContainer

                                        CalendarDayKind.NONE -> MaterialTheme.colorScheme.surface

                                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    border = if (chosen ||
                                        day == state.today
                                    ) {
                                        BorderStroke(
                                            Spacing.calendarBorder,
                                            MaterialTheme.colorScheme.primary
                                        )
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
                                                state.today
                                            ) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            },
                                            textDecoration = if (day ==
                                                state.today
                                            ) {
                                                TextDecoration.Underline
                                            } else {
                                                null
                                            }
                                        )
                                        Text(
                                            when (info.kind) {
                                                CalendarDayKind.OBSERVED -> "●"
                                                CalendarDayKind.PREDICTED -> "◇"
                                                CalendarDayKind.UNCERTAIN -> "·"
                                                CalendarDayKind.APPROXIMATE -> "≈"
                                                CalendarDayKind.NONE -> " "
                                            },
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.calendar_legend),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            Text(formattedDate(date), style = MaterialTheme.typography.titleLarge)
            Text(dayKindText(selection.kind))
            selection.period?.let { period ->
                Text(periodDates(period))
                Row {
                    TextButton(onClick = {
                        onEdit(period)
                    }, enabled = !state.saving) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = {
                        onDelete(period)
                    }, enabled = !state.saving) { Text(stringResource(R.string.delete)) }
                }
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
                if (date <= state.today) {
                    Button(onClick = { onAdd(date) }, enabled = !state.loading && !state.saving) {
                        Text(stringResource(R.string.add_period))
                    }
                } else {
                    Text(
                        stringResource(R.string.future_calendar_note),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        if (state.loading) Text(stringResource(R.string.loading)) else ForecastCard(state.analysis)
        state.problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun dayKindText(kind: CalendarDayKind): String = stringResource(
    when (kind) {
        CalendarDayKind.OBSERVED -> R.string.calendar_observed
        CalendarDayKind.PREDICTED -> R.string.calendar_predicted
        CalendarDayKind.UNCERTAIN -> R.string.calendar_uncertain
        CalendarDayKind.APPROXIMATE -> R.string.calendar_approximate
        CalendarDayKind.NONE -> R.string.calendar_empty
    }
)
