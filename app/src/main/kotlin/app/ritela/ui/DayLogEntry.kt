package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ritela.R
import app.ritela.domain.DayLog
import app.ritela.domain.Energy
import app.ritela.domain.FlowLevel
import app.ritela.domain.Mood
import app.ritela.domain.Pain
import app.ritela.domain.Sex

@Composable
fun eventLabel(value: Enum<*>): String = stringResource(
    when (value) {
        Pain.NONE -> R.string.pain_none
        Pain.MILD -> R.string.pain_mild
        Pain.MODERATE -> R.string.pain_moderate
        Pain.SEVERE -> R.string.pain_severe
        FlowLevel.NONE -> R.string.flow_none
        FlowLevel.LIGHT -> R.string.flow_light
        FlowLevel.MEDIUM -> R.string.flow_medium
        FlowLevel.HEAVY -> R.string.flow_heavy
        Mood.CALM -> R.string.mood_calm
        Mood.HAPPY -> R.string.mood_happy
        Mood.LOW -> R.string.mood_low
        Mood.ANXIOUS -> R.string.mood_anxious
        Mood.IRRITABLE -> R.string.mood_irritable
        Energy.LOW -> R.string.energy_low
        Energy.NORMAL -> R.string.energy_normal
        Energy.HIGH -> R.string.energy_high
        Sex.NONE -> R.string.sex_none
        Sex.CONDOM -> R.string.sex_condom
        Sex.NO_BARRIER -> R.string.sex_no_barrier
        Sex.ORAL -> R.string.sex_oral
        Sex.VAGINAL -> R.string.sex_vaginal
        Sex.ANAL -> R.string.sex_anal
        Sex.MASTURBATION -> R.string.sex_masturbation
        else -> R.string.sex_other
    }
)

@Composable
fun DayLogEntry(
    initial: DayLog,
    state: PeriodUiState,
    onDismiss: () -> Unit,
    onSave: (DayLog) -> Unit
) {
    var headache by rememberSaveable { mutableStateOf(initial.headache) }
    var cramps by rememberSaveable { mutableStateOf(initial.cramps) }
    var backache by rememberSaveable { mutableStateOf(initial.backache) }
    var flow by rememberSaveable { mutableStateOf(initial.flow) }
    var mood by rememberSaveable { mutableStateOf(initial.mood) }
    var energy by rememberSaveable { mutableStateOf(initial.energy) }
    var sexNames by rememberSaveable { mutableStateOf(initial.sex.map { it.name }.toTypedArray()) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    Dialog(
        onDismissRequest = { if (!state.saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                Text(
                    stringResource(R.string.day_log_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(formattedDate(initial.date))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    DayChoices(
                        R.string.headache,
                        "headache",
                        Pain.entries,
                        headache,
                        !state.saving
                    ) {
                        headache =
                            it
                    }
                    DayChoices(R.string.cramps, "cramps", Pain.entries, cramps, !state.saving) {
                        cramps =
                            it
                    }
                    DayChoices(
                        R.string.backache,
                        "backache",
                        Pain.entries,
                        backache,
                        !state.saving
                    ) {
                        backache =
                            it
                    }
                    DayChoices(
                        R.string.flow_title,
                        "flow",
                        FlowLevel.entries,
                        flow,
                        !state.saving
                    ) {
                        flow =
                            it
                    }
                    DayChoices(R.string.mood_title, "mood", Mood.entries, mood, !state.saving) {
                        mood =
                            it
                    }
                    DayChoices(
                        R.string.energy_title,
                        "energy",
                        Energy.entries,
                        energy,
                        !state.saving
                    ) {
                        energy =
                            it
                    }
                    Text(
                        stringResource(R.string.sex_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                        Sex.entries.forEach { value ->
                            FilterChip(
                                selected = value.name in sexNames,
                                enabled = !state.saving,
                                modifier = Modifier.testTag("sex-${value.name}"),
                                onClick = {
                                    val selected = sexNames.toSet()
                                    sexNames = when {
                                        value.name in selected -> {
                                            (selected - value.name).toTypedArray()
                                        }

                                        value == Sex.NONE -> arrayOf(value.name)

                                        else -> {
                                            ((selected - Sex.NONE.name) + value.name).toTypedArray()
                                        }
                                    }
                                },
                                label = { Text(eventLabel(value)) }
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.sex_barrier_note),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = {
                            if (it.length <=
                                1000
                            ) {
                                note = it
                            }
                        },
                        enabled = !state.saving,
                        label = {
                            Text(stringResource(R.string.day_note))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("day-note")
                    )
                    state.problem?.let {
                        Text(problemText(it), color = MaterialTheme.colorScheme.error)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    TextButton(onClick = onDismiss, enabled = !state.saving) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            onSave(
                                DayLog(
                                    initial.date, headache, cramps, backache, flow, mood, energy,
                                    sexNames.map(Sex::valueOf).toSet(), note
                                )
                            )
                        },
                        enabled = !state.saving,
                        modifier = Modifier.weight(
                            1f
                        ).testTag("save-day")
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
private fun <T : Enum<T>> DayChoices(
    title: Int,
    tag: String,
    choices: List<T>,
    value: T?,
    enabled: Boolean,
    onChange: (T?) -> Unit
) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        choices.forEach { choice ->
            FilterChip(
                selected = value == choice,
                enabled = enabled,
                onClick = { onChange(if (value == choice) null else choice) },
                modifier = Modifier.testTag("$tag-${choice.name}"),
                label = {
                    Text(eventLabel(choice))
                }
            )
        }
    }
}
