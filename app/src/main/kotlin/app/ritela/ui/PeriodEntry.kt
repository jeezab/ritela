package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    onSave: (LocalDate, LocalDate?) -> Unit
) {
    var startDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var endDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var choosingEnd by rememberSaveable { mutableStateOf<Boolean?>(null) }
    AlertDialog(
        onDismissRequest = { if (!state.saving) onDismiss() },
        title = { Text(stringResource(R.string.new_period)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                OutlinedButton(onClick = { choosingEnd = false }, enabled = !state.saving) {
                    Text(
                        stringResource(
                            R.string.start_date,
                            formattedDate(LocalDate.ofEpochDay(startDay))
                        )
                    )
                }
                OutlinedButton(onClick = { choosingEnd = true }, enabled = !state.saving) {
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
                state.problem?.let { Text(problemText(it)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
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
            onDismiss = { choosingEnd = null },
            onChoose = { date ->
                if (isEnd) {
                    endDay = date.toEpochDay()
                } else {
                    startDay =
                        date.toEpochDay()
                }
                choosingEnd = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickDate(initial: LocalDate, onDismiss: () -> Unit, onChoose: (LocalDate) -> Unit) {
    // Material's picker encodes calendar dates as UTC midnight; storage uses epoch days.
    val picker = rememberDatePickerState(
        initialSelectedDateMillis =
            initial.toEpochDay() * 86_400_000L
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
