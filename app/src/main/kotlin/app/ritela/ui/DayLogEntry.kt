package app.ritela.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ritela.R
import app.ritela.data.JournalCodec
import app.ritela.domain.DayLog
import app.ritela.domain.Energy
import app.ritela.domain.FlowLevel
import app.ritela.domain.JournalIcon
import app.ritela.domain.JournalLayout
import app.ritela.domain.JournalSection
import app.ritela.domain.JournalTag
import app.ritela.domain.Mood
import app.ritela.domain.Pain
import app.ritela.domain.Sex
import app.ritela.domain.journalSelections
import app.ritela.domain.withJournalSelections
import java.util.UUID

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

fun journalIcon(icon: JournalIcon): Int = when (icon) {
    JournalIcon.NOTE -> R.drawable.ic_note
    JournalIcon.HEAD -> R.drawable.ic_head
    JournalIcon.DROP -> R.drawable.ic_drop
    JournalIcon.HEART -> R.drawable.ic_heart
    JournalIcon.ENERGY -> R.drawable.ic_energy
    JournalIcon.FLOWER -> R.drawable.ic_flower
    JournalIcon.STAR -> R.drawable.ic_star
}

@Composable
fun journalSectionLabel(section: JournalSection): String = section.title.ifBlank {
    stringResource(
        when (section.id) {
            "headache" -> R.string.headache
            "cramps" -> R.string.cramps
            "backache" -> R.string.backache
            "flow" -> R.string.flow_title
            "mood" -> R.string.mood_title
            "energy" -> R.string.energy_title
            "sex" -> R.string.sex_title
            else -> R.string.journal_section
        }
    )
}

@Composable
fun journalTagLabel(section: JournalSection, tag: JournalTag): String {
    if (tag.title.isNotBlank()) return tag.title
    val values: List<Enum<*>> = when (section.id) {
        "headache", "cramps", "backache" -> Pain.entries
        "flow" -> FlowLevel.entries
        "mood" -> Mood.entries
        "energy" -> Energy.entries
        "sex" -> Sex.entries
        else -> emptyList()
    }
    return values.firstOrNull { it.name == tag.id }?.let { eventLabel(it) } ?: tag.id
}

@Composable
fun DayLogEntry(
    initial: DayLog,
    state: PeriodUiState,
    onDismiss: () -> Unit,
    onSave: (DayLog) -> Unit,
    onLayoutChange: (JournalLayout) -> Unit = {}
) {
    var layoutText by rememberSaveable { mutableStateOf(JournalCodec.encode(state.journalLayout)) }
    val layout = remember(layoutText) { JournalCodec.decode(layoutText) }
    var selectionText by rememberSaveable {
        mutableStateOf(JournalCodec.encodeSelections(initial.journalSelections()))
    }
    val selections = remember(selectionText) { JournalCodec.decodeSelections(selectionText) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var calendarIcon by rememberSaveable { mutableStateOf(initial.calendarIcon?.name) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    // Dialog targets: title, add-section, section:<id>, add-tag:<id>, tag:<section>:<tag>.
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var label by rememberSaveable { mutableStateOf("") }
    var newTag by rememberSaveable { mutableStateOf("") }
    var sectionIcon by rememberSaveable { mutableStateOf(JournalIcon.NOTE.name) }
    fun update(value: JournalLayout) {
        layoutText = JournalCodec.encode(value)
        onLayoutChange(value)
    }
    fun editLabel(key: String, value: String) {
        target = key
        label = value
        newTag = ""
        sectionIcon = layout.sections.firstOrNull { it.id == key.substringAfter(':') }?.icon?.name
            ?: JournalIcon.NOTE.name
    }
    Dialog(
        onDismissRequest = { if (!state.saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(Spacing.medium).imePadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        layout.title.ifBlank { stringResource(R.string.day_log_title) },
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    IconButton(
                        onClick = {
                            editLabel("title", layout.title)
                        },
                        enabled = !state.saving,
                        modifier = Modifier.testTag("journal-edit-title")
                    ) {
                        Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.edit))
                    }
                }
                Text(formattedDate(initial.date))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    TextButton(
                        onClick = { editLabel("add-section", "") },
                        enabled = !state.saving && layout.sections.size < 64,
                        modifier = Modifier.testTag("journal-add-section")
                    ) {
                        Text(stringResource(R.string.journal_add_section))
                    }
                    layout.sections.forEach { section ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painterResource(journalIcon(section.icon)),
                                    null,
                                    Modifier.size(20.dp)
                                )
                                Text(
                                    journalSectionLabel(section),
                                    Modifier.weight(1f).padding(start = Spacing.small),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                IconButton(
                                    onClick = {
                                        editing =
                                            if (editing == section.id) null else section.id
                                    },
                                    enabled = !state.saving,
                                    modifier = Modifier.testTag("journal-edit-${section.id}")
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_edit),
                                        stringResource(R.string.edit)
                                    )
                                }
                            }
                            JournalChips(
                                section,
                                selections[section.id].orEmpty(),
                                editing == section.id,
                                !state.saving,
                                onSelect = { tag ->
                                    val old = selections[section.id].orEmpty()
                                    val next = when {
                                        tag in old -> old - tag
                                        section.id == "sex" && tag == "NONE" -> setOf(tag)
                                        section.id == "sex" -> (old - "NONE") + tag
                                        section.multiple -> old + tag
                                        else -> setOf(tag)
                                    }
                                    selectionText =
                                        JournalCodec.encodeSelections(
                                            selections + (section.id to next)
                                        )
                                },
                                onRemove = { update(layout.removeTag(section.id, it)) },
                                onMove = { from, to ->
                                    update(layout.moveTag(section.id, from, to))
                                },
                                onRename = { tag ->
                                    editLabel("tag:${section.id}:${tag.id}", tag.title)
                                }
                            )
                            if (editing == section.id) {
                                TextButton(onClick = {
                                    editLabel("section:${section.id}", section.title)
                                }, enabled = !state.saving) {
                                    Text(stringResource(R.string.journal_section_settings))
                                }
                                TextButton(
                                    onClick = { editLabel("add-tag:${section.id}", "") },
                                    enabled = !state.saving && section.tags.size < 64
                                ) {
                                    Text(stringResource(R.string.journal_add_tag))
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            update(
                                                layout.copy(
                                                    sections = layout.sections.filterNot {
                                                        it.id ==
                                                            section.id
                                                    }
                                                )
                                            )
                                            editing = null
                                        },
                                        enabled = !state.saving,
                                        modifier = Modifier.testTag(
                                            "journal-delete-${section.id}"
                                        )
                                    ) {
                                        Text(
                                            stringResource(R.string.journal_delete_section),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        note,
                        {
                            if (it.length <=
                                1000
                            ) {
                                note = it
                            }
                        },
                        enabled = !state.saving,
                        label = { Text(stringResource(R.string.day_note)) },
                        modifier = Modifier.fillMaxWidth().testTag("day-note")
                    )
                    Text(
                        stringResource(R.string.journal_calendar_icon),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconChoices(calendarIcon, !state.saving) { calendarIcon = it }
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
                                initial.withJournalSelections(selections).copy(
                                    note = note,
                                    calendarIcon = calendarIcon?.let(JournalIcon::valueOf)
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
    target?.let { key ->
        val sectionId = key.split(':').getOrNull(1)
        val section = layout.sections.firstOrNull { it.id == sectionId }
        Dialog(
            onDismissRequest = { target = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    Modifier.padding(Spacing.medium).imePadding(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    Text(
                        stringResource(R.string.edit),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                    ) {
                        OutlinedTextField(
                            label,
                            { if (it.length <= 200) label = it },
                            label = {
                                Text(stringResource(R.string.journal_name))
                            },
                            modifier = Modifier.testTag("journal-name")
                        )
                        if (key == "add-section") {
                            OutlinedTextField(
                                newTag,
                                { if (it.length <= 200) newTag = it },
                                label = {
                                    Text(stringResource(R.string.journal_first_tag))
                                },
                                modifier = Modifier.testTag("journal-first-tag")
                            )
                        }
                        if (key.startsWith("section:") && section != null) {
                            IconChoices(
                                sectionIcon,
                                !state.saving,
                                allowAutomatic = false,
                                tagPrefix = "journal-section-icon"
                            ) { icon ->
                                sectionIcon = requireNotNull(icon)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = {
                            target = null
                        }, modifier = Modifier.testTag("journal-config-cancel")) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(
                            modifier = Modifier.testTag("journal-confirm"),
                            enabled = !state.saving && (key == "title" || label.isNotBlank()) &&
                                (key != "add-section" || newTag.isNotBlank()),
                            onClick = {
                                val value = label.trim()
                                when {
                                    key == "title" -> update(layout.copy(title = value))

                                    key == "add-section" -> update(
                                        layout.copy(
                                            sections = layout.sections +
                                                JournalSection(
                                                    UUID.randomUUID().toString(),
                                                    value,
                                                    tags = listOf(
                                                        JournalTag(
                                                            UUID.randomUUID().toString(),
                                                            newTag.trim()
                                                        )
                                                    )
                                                )
                                        )
                                    )

                                    key.startsWith(
                                        "section:"
                                    ) && section != null -> update(
                                        layout.replace(
                                            section.copy(
                                                title = value,
                                                icon = JournalIcon.valueOf(sectionIcon)
                                            )
                                        )
                                    )

                                    key.startsWith("add-tag:") && section != null -> update(
                                        layout.replace(
                                            section.copy(
                                                tags =
                                                    section.tags +
                                                        JournalTag(
                                                            UUID.randomUUID().toString(),
                                                            value
                                                        )
                                            )
                                        )
                                    )

                                    key.startsWith("tag:") && section != null -> {
                                        val tagId = key.split(':')[2]
                                        update(
                                            layout.replace(
                                                section.copy(
                                                    tags = section.tags.map {
                                                        if (it.id ==
                                                            tagId
                                                        ) {
                                                            it.copy(title = value)
                                                        } else {
                                                            it
                                                        }
                                                    }
                                                )
                                            )
                                        )
                                    }
                                }
                                target = null
                            }
                        ) { Text(stringResource(R.string.save)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconChoices(
    value: String?,
    enabled: Boolean,
    allowAutomatic: Boolean = true,
    tagPrefix: String = "journal-icon",
    onChange: (String?) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        if (allowAutomatic) {
            FilterChip(
                value == null,
                { onChange(null) },
                enabled = enabled,
                label = { Text(stringResource(R.string.journal_automatic)) }
            )
        }
        JournalIcon.entries.forEach { icon ->
            FilterChip(
                value == icon.name,
                { onChange(icon.name) },
                enabled = enabled,
                modifier = Modifier.testTag("$tagPrefix-${icon.name}"),
                label = {
                    Icon(
                        painterResource(journalIcon(icon)),
                        stringResource(
                            when (icon) {
                                JournalIcon.NOTE -> R.string.day_note
                                JournalIcon.HEAD -> R.string.headache
                                JournalIcon.DROP -> R.string.flow_title
                                JournalIcon.HEART -> R.string.mood_title
                                JournalIcon.ENERGY -> R.string.energy_title
                                JournalIcon.FLOWER -> R.string.journal_flower
                                JournalIcon.STAR -> R.string.journal_star
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun JournalChips(
    section: JournalSection,
    selected: Set<String>,
    editing: Boolean,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, String) -> Unit,
    onRename: (JournalTag) -> Unit
) {
    val bounds = remember(section.id) { mutableStateMapOf<String, Rect>() }
    val tilt = if (editing) {
        val transition = rememberInfiniteTransition(label = "journal-edit")
        transition.animateFloat(
            -1.5f,
            1.5f,
            infiniteRepeatable(tween(170), RepeatMode.Reverse),
            label = "wiggle"
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        section.tags.forEach { tag ->
            var drag by remember(tag.id) { mutableStateOf(Offset.Zero) }
            Box(
                Modifier.onGloballyPositioned { bounds[tag.id] = it.boundsInRoot() }
                    .graphicsLayer {
                        rotationZ = if (editing) tilt.value else 0f
                        translationX = drag.x
                        translationY =
                            drag.y
                    }
                    .pointerInput(editing, enabled, section.tags) {
                        if (editing && enabled) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { drag = Offset.Zero },
                                onDragCancel = { drag = Offset.Zero },
                                onDragEnd = {
                                    val center = bounds[tag.id]?.center?.plus(drag)
                                    val target = center?.let { point ->
                                        bounds.filterKeys { id ->
                                            id != tag.id && section.tags.any { it.id == id }
                                        }
                                            .filterValues { it.contains(point) }.keys.firstOrNull()
                                    }
                                    drag = Offset.Zero
                                    if (target != null) onMove(tag.id, target)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag += amount
                                }
                            )
                        }
                    }
            ) {
                Column {
                    if (editing) {
                        Row {
                            IconButton(
                                onClick = { onRemove(tag.id) },
                                enabled = enabled && section.tags.size > 1,
                                modifier = Modifier.testTag(
                                    "journal-remove-${section.id}-${tag.id}"
                                )
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_minus),
                                    stringResource(R.string.journal_delete_tag),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            val index = section.tags.indexOf(tag)
                            TextButton(
                                onClick = { onMove(tag.id, section.tags[index - 1].id) },
                                enabled = enabled && index > 0,
                                modifier = Modifier.testTag("journal-move-${section.id}-${tag.id}")
                            ) {
                                Text(stringResource(R.string.journal_move_before))
                            }
                        }
                    }
                    FilterChip(
                        tag.id in selected,
                        { if (editing) onRename(tag) else onSelect(tag.id) },
                        enabled = enabled,
                        modifier = Modifier.testTag("${section.id}-${tag.id}"),
                        label = { Text(journalTagLabel(section, tag)) }
                    )
                }
            }
        }
    }
}
