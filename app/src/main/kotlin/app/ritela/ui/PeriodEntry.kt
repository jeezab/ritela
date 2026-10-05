package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import app.ritela.R
import app.ritela.domain.PeriodProblem
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
    onChange: () -> Unit = {}
) {
    var startDay by rememberSaveable { mutableStateOf(initialStart.toEpochDay()) }
    var endDay by rememberSaveable { mutableStateOf(initialEnd?.toEpochDay()) }
    var choosingEnd by rememberSaveable { mutableStateOf<Boolean?>(null) }
    AlertDialog(
        onDismissRequest = { if (!state.saving) onDismiss() },
        title = {
            Text(stringResource(if (editing) R.string.edit_period else R.string.new_period))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                OutlinedButton(
                    onClick = { choosingEnd = false },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.actionHeight)
                ) {
                    Text(
                        stringResource(
                            R.string.start_date,
                            formattedDate(LocalDate.ofEpochDay(startDay))
                        )
                    )
                }
                OutlinedButton(
                    onClick = { choosingEnd = true },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.actionHeight)
                ) {
                    Text(
                        endDay?.let {
                            stringResource(
                                R.string.end_date,
                                formattedDate(LocalDate.ofEpochDay(it))
                            )
                        }
                            ?: stringResource(R.string.choose_end)
                    )
                }
                if (endDay != null) {
                    TextButton(onClick = {
                        endDay = null
                        onChange()
                    }, enabled = !state.saving) {
                        Text(stringResource(R.string.clear_end))
                    }
                }
                Text(
                    stringResource(R.string.end_optional),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.problem?.let {
                    Text(problemText(it), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(LocalDate.ofEpochDay(startDay), endDay?.let(LocalDate::ofEpochDay))
            }, enabled = !state.saving) {
                Text(stringResource(if (state.saving) R.string.saving else R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.saving) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
    choosingEnd?.let { isEnd ->
        PickDate(
            initial = LocalDate.ofEpochDay(if (isEnd) endDay ?: startDay else startDay),
            minimum = if (isEnd) LocalDate.ofEpochDay(startDay) else null,
            onDismiss = { choosingEnd = null },
            onChoose = { date ->
                if (isEnd) {
                    endDay = date.toEpochDay()
                } else {
                    startDay =
                        date.toEpochDay()
                }
                choosingEnd = null
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
    maximum: LocalDate = LocalDate.now()
) {
    // Material's picker encodes calendar dates as UTC midnight; storage uses epoch days.
    val picker = rememberDatePickerState(
        initialSelectedDateMillis =
            initial.toEpochDay() * 86_400_000L,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = LocalDate.ofEpochDay(utcTimeMillis / 86_400_000L)
                return date <= maximum && (minimum == null || date >= minimum)
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
