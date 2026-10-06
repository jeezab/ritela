package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ritela.R
import app.ritela.domain.PeriodProblem
import app.ritela.domain.periodConflict
import app.ritela.domain.validatePeriod
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun formattedDate(date: LocalDate): String = date.format(
    DateTimeFormatter.ofLocalizedDate(
        FormatStyle.MEDIUM
    ).withLocale(LocalConfiguration.current.locales[0])
)

@Composable
fun problemText(problem: PeriodProblem): String = stringResource(
    when (problem) {
        PeriodProblem.FUTURE_DATE -> R.string.future_date_error
        PeriodProblem.END_BEFORE_START -> R.string.date_order_error
        PeriodProblem.OVERLAP -> R.string.overlap_error
        PeriodProblem.STORAGE -> R.string.storage_error
    }
)

@Composable
fun PeriodEntry(
    state: PeriodUiState,
    onDismiss: () -> Unit,
    onSave: (LocalDate, LocalDate?) -> Unit,
    initialStart: LocalDate = LocalDate.now(),
    initialEnd: LocalDate? = null,
    editing: Boolean = false,
    editingId: java.util.UUID? = null,
    onDelete: (() -> Unit)? = null,
    onChange: () -> Unit = {}
) {
    var startDay by rememberSaveable { mutableStateOf(initialStart.toEpochDay()) }
    var endDay by rememberSaveable { mutableStateOf(initialEnd?.toEpochDay()) }
    var choosingEnd by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var monthDay by rememberSaveable {
        mutableStateOf(java.time.YearMonth.from(initialStart).atDay(1).toEpochDay())
    }
    var choosingMonth by rememberSaveable { mutableStateOf(false) }
    var awaitingEnd by rememberSaveable { mutableStateOf(false) }
    val month = java.time.YearMonth.from(LocalDate.ofEpochDay(monthDay))
    val start = LocalDate.ofEpochDay(startDay)
    val end = endDay?.let(LocalDate::ofEpochDay)
    val others = state.periods.filter { it.id != editingId }
    val conflict = periodConflict(others, start, end)
    val valid = validatePeriod(start, end, state.today) == null && conflict == null
    fun free(day: LocalDate): Boolean = periodConflict(others, day, day) == null
    val cellHeight =
        maxOf(48.dp, (36 * androidx.compose.ui.platform.LocalDensity.current.fontScale).dp)
    androidx.compose.ui.window.Dialog(onDismissRequest = {
        if (!state.saving) onDismiss()
    }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface {
            Column(Modifier.fillMaxSize().padding(Spacing.medium)) {
                Text(
                    stringResource(if (editing) R.string.edit_period else R.string.new_period),
                    style = MaterialTheme.typography.headlineMedium
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    Text(
                        stringResource(R.string.range_entry_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row {
                        OutlinedButton(
                            onClick = {
                                choosingEnd = false
                            },
                            enabled = !state.saving,
                            modifier = Modifier.weight(
                                1f
                            ).heightIn(min = Spacing.actionHeight)
                        ) {
                            Text(stringResource(R.string.start_date, formattedDate(start)))
                        }
                        OutlinedButton(
                            onClick = {
                                choosingEnd = true
                            },
                            enabled = !state.saving,
                            modifier = Modifier.weight(
                                1f
                            ).heightIn(min = Spacing.actionHeight)
                        ) {
                            Text(
                                end?.let { stringResource(R.string.end_date, formattedDate(it)) }
                                    ?: stringResource(R.string.choose_end)
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = {
                            endDay = null
                            awaitingEnd = false
                            onChange()
                        }, enabled = !state.saving) { Text(stringResource(R.string.clear_end)) }
                        TextButton(
                            onClick = {
                                endDay =
                                    start.plusDays(state.analysis.periodDuration - 1L).toEpochDay()
                                awaitingEnd =
                                    false
                                onChange()
                            },
                            enabled =
                                !state.saving &&
                                    start.plusDays(state.analysis.periodDuration - 1L) <=
                                    state.today && periodConflict(
                                        others,
                                        start,
                                        start.plusDays(state.analysis.periodDuration - 1L)
                                    ) == null
                        ) {
                            Text(
                                androidx.compose.ui.res.pluralStringResource(
                                    R.plurals.quick_duration,
                                    state.analysis.periodDuration,
                                    state.analysis.periodDuration
                                )
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.end_optional),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MonthHeader(month, { monthDay = month.minusMonths(1).atDay(1).toEpochDay() }, {
                        monthDay =
                            month.plusMonths(1).atDay(1).toEpochDay()
                    }, { choosingMonth = true })
                    MonthGrid(
                        month, state.today, start,
                        cellHeight = cellHeight,
                        tagPrefix = "entry-day",
                        rangeStart = start, rangeEnd = end,
                        futureEnabled = false,
                        info = {
                            app.ritela.domain.calendarDay(
                                others,
                                app.ritela.domain.CycleAnalysis(),
                                it,
                                state.today
                            )
                        },
                        dayEnabled = { day ->
                            free(day) && (
                                !awaitingEnd || day < start ||
                                    periodConflict(others, start, day) == null
                                )
                        },
                        onDay = { day ->
                            if (!state.saving) {
                                if (awaitingEnd &&
                                    day >= start
                                ) {
                                    endDay = day.toEpochDay()
                                    awaitingEnd = false
                                } else {
                                    startDay = day.toEpochDay()
                                    endDay = null
                                    awaitingEnd = true
                                }
                                onChange()
                            }
                        }
                    )
                    Text(
                        stringResource(R.string.entry_existing_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (conflict != null) {
                        Text(
                            stringResource(R.string.overlap_error),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    state.problem?.let {
                        Text(problemText(it), color = MaterialTheme.colorScheme.error)
                    }
                }
                onDelete?.let { action ->
                    TextButton(onClick = action, enabled = !state.saving) {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    TextButton(onClick = onDismiss, enabled = !state.saving) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            onSave(start, end)
                        },
                        enabled = !state.saving && valid,
                        modifier = Modifier.weight(
                            1f
                        ).heightIn(min = Spacing.actionHeight)
                    ) {
                        Text(stringResource(if (state.saving) R.string.saving else R.string.save))
                    }
                }
            }
        }
    }
    if (choosingMonth) {
        MonthYearPicker(month, 1900..(state.today.year + 1), { choosingMonth = false }) {
            monthDay =
                it.atDay(1).toEpochDay()
            choosingMonth = false
        }
    }
    choosingEnd?.let { isEnd ->
        PickDate(
            initial = if (isEnd) end ?: start else start,
            minimum = if (isEnd) start else null,
            maximum = state.today,
            selectable = { candidate ->
                free(candidate) && (
                    !isEnd ||
                        periodConflict(others, start, candidate) == null
                    )
            },
            onDismiss = {
                choosingEnd =
                    null
            },
            onChoose = { date ->
                if (isEnd) {
                    endDay = date.toEpochDay()
                } else {
                    startDay = date.toEpochDay()
                    if (end !=
                        null &&
                        end < date
                    ) {
                        endDay = null
                    }
                }
                monthDay = java.time.YearMonth.from(date).atDay(1).toEpochDay()
                choosingEnd = null
                awaitingEnd = false
                onChange()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickDate(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onChoose: (LocalDate) -> Unit,
    minimum: LocalDate? = null,
    maximum: LocalDate = LocalDate.now(),
    selectable: (LocalDate) -> Boolean = { true }
) {
    // Material's picker encodes calendar dates as UTC midnight; storage uses epoch days.
    val picker = rememberDatePickerState(
        initialSelectedDateMillis =
            initial.toEpochDay() * 86_400_000L,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = LocalDate.ofEpochDay(utcTimeMillis / 86_400_000L)
                return date <= maximum && (minimum == null || date >= minimum) && selectable(date)
            }

            override fun isSelectableYear(year: Int): Boolean =
                year <= maximum.year && (minimum == null || year >= minimum.year)
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { onChoose(LocalDate.ofEpochDay(it / 86_400_000L)) }
            }, enabled = picker.selectedDateMillis != null) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    ) { DatePicker(state = picker) }
}
