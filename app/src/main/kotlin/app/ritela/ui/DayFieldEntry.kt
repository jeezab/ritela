package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.ritela.R
import app.ritela.data.JournalCodec
import app.ritela.domain.DayLog
import app.ritela.domain.forSelection
import app.ritela.domain.journalSelections
import app.ritela.domain.toggleSelection
import app.ritela.domain.withJournalSelections

/** A field-only editor: saving keeps every other field and calendar icon unchanged. */
@Composable
fun DayFieldEntry(
    initial: DayLog,
    state: PeriodUiState,
    field: String,
    onDismiss: () -> Unit,
    onSave: (DayLog) -> Unit
) {
    HomeTheme {
        val section = state.journalLayout.sections.firstOrNull { it.id == field }?.forSelection()
        var selectionsText by rememberSaveable(initial.date, field) {
            mutableStateOf(JournalCodec.encodeSelections(initial.journalSelections()))
        }
        val selections = remember(selectionsText) { JournalCodec.decodeSelections(selectionsText) }
        var note by rememberSaveable(initial.date, field) { mutableStateOf(initial.note) }
        val height = with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height.toDp() * 0.85f
        }
        JournalDialog({ if (!state.saving) onDismiss() }, DialogProperties()) {
            Surface(color = HomeColors.bottom, shape = MaterialTheme.shapes.extraLarge) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = height).padding(20.dp)
                        .testTag("quick-entry-$field"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (section == null) {
                            stringResource(R.string.day_note)
                        } else {
                            journalSectionLabel(section)
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(formattedDate(initial.date), color = HomeColors.muted)
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    ) {
                        if (section == null) {
                            OutlinedTextField(
                                note,
                                { if (it.length <= 1000) note = it },
                                modifier = Modifier.fillMaxWidth().testTag("day-note"),
                                enabled = !state.saving,
                                label = { Text(stringResource(R.string.day_note)) }
                            )
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                section.tags.forEach { tag ->
                                    FilterChip(
                                        tag.id in selections[section.id].orEmpty(),
                                        {
                                            selectionsText = JournalCodec.encodeSelections(
                                                selections + (
                                                    section.id to section.toggleSelection(
                                                        selections[section.id].orEmpty(),
                                                        tag.id
                                                    )
                                                    )
                                            )
                                        },
                                        enabled = !state.saving,
                                        modifier = Modifier.testTag("${section.id}-${tag.id}"),
                                        label = { Text(journalTagLabel(section, tag)) }
                                    )
                                }
                            }
                        }
                        state.problem?.let {
                            Text(problemText(it), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onDismiss, enabled = !state.saving) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            { onSave(initial.withJournalSelections(selections).copy(note = note)) },
                            enabled = !state.saving,
                            modifier = Modifier.testTag("save-day")
                        ) { Text(stringResource(R.string.save)) }
                    }
                }
            }
        }
    }
}
