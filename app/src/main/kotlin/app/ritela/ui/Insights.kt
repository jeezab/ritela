package app.ritela.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ritela.R
import app.ritela.domain.DayLog
import app.ritela.domain.Period
import app.ritela.domain.forSelection
import app.ritela.domain.journalSelections
import java.time.LocalDate

@Composable
fun CycleDayBadge(day: Long) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Text(
                day.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.cycle_day_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
fun DaySummary(
    log: DayLog?,
    onEdit: () -> Unit,
    enabled: Boolean = true,
    layout: app.ritela.domain.JournalLayout = app.ritela.domain.JournalLayout(),
    homeStyle: Boolean = false,
    onQuickLog: ((String) -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.home_title),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(
                onClick = onEdit,
                enabled = enabled,
                modifier = Modifier.testTag("log-day")
            ) {
                Icon(painterResource(R.drawable.ic_note), contentDescription = null)
                Text(
                    stringResource(if (homeStyle) R.string.edit else R.string.log_day),
                    Modifier.padding(start = Spacing.small)
                )
            }
        }
        if (homeStyle) {
            HomeTodayActions(layout, enabled) { id -> onQuickLog?.invoke(id) ?: onEdit() }
        } else {
            JournalQuickActions(layout, onEdit, enabled)
        }
        log?.let {
            val selections = it.journalSelections()
            layout.sections.forEach { section ->
                val chosen = section.tags.filter { tag ->
                    tag.id in selections[section.id].orEmpty()
                }
                if (chosen.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        Icon(
                            painterResource(journalIcon(section.forSelection().icon)),
                            null,
                            Modifier.height(20.dp)
                        )
                        val labels = chosen.map { tag -> journalTagLabel(section, tag) }
                        Text(
                            stringResource(
                                R.string.day_summary,
                                journalSectionLabel(section),
                                labels.joinToString()
                            )
                        )
                    }
                }
            }
            if (it.note.isNotBlank()) Text(it.note)
        }
    }
}

@Composable
fun CalendarDayRecords(
    log: DayLog?,
    layout: app.ritela.domain.JournalLayout,
    onEdit: () -> Unit,
    editable: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.selected_day_records),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall
            )
            log?.calendarIcon?.let { Icon(painterResource(journalIcon(it)), null) }
            if (editable) {
                TextButton(onClick = onEdit, modifier = Modifier.testTag("log-day")) {
                    Text(
                        stringResource(R.string.selected_day_add_record)
                    )
                }
            }
        }
        if (log == null || log.empty) {
            Text(
                stringResource(R.string.selected_day_no_records),
                style = MaterialTheme.typography.bodySmall,
                color = HomeColors.muted
            )
        } else {
            val registry = (
                layout.sections + app.ritela.domain.defaultJournalSections() +
                    app.ritela.domain.builtInJournalSections()
                ).distinctBy { it.id }
            val selections = log.journalSelections().filterValues { it.isNotEmpty() }
            val sections = registry.filter { it.id in selections } +
                (selections.keys - registry.map { it.id }.toSet()).sorted().map {
                    app.ritela.domain.JournalSection(it, title = it, tags = emptyList())
                }
            sections.forEach { section ->
                val selected = selections.getValue(section.id)
                val tags = section.tags.filter { it.id in selected } +
                    (selected - section.tags.map { it.id }.toSet()).sorted().map {
                        app.ritela.domain.JournalTag(it)
                    }
                val labels = tags.map { journalTagLabel(section, it) }
                Text(
                    stringResource(
                        R.string.day_summary,
                        journalSectionLabel(section),
                        labels.joinToString()
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (log.note.isNotBlank()) Text(log.note, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun JournalQuickActions(
    layout: app.ritela.domain.JournalLayout,
    onEdit: () -> Unit,
    enabled: Boolean
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        // Show actual configured sections; removed sections never reappear as shortcuts.
        val sections = layout.sections.sortedBy {
            when (it.id) {
                "mood" -> 0
                "energy" -> 1
                else -> 2
            }
        }.take(4)
        sections.forEach { section ->
            JournalQuickAction(
                journalSectionLabel(section),
                journalIcon(section.forSelection().icon),
                "quick-${section.id}",
                onEdit,
                enabled
            )
        }
        JournalQuickAction(
            stringResource(R.string.day_note),
            R.drawable.ic_note,
            "quick-note",
            onEdit,
            enabled
        )
    }
}

@Composable
private fun JournalQuickAction(
    label: String,
    icon: Int,
    tag: String,
    onEdit: () -> Unit,
    enabled: Boolean
) {
    Card(
        onClick = onEdit,
        enabled = enabled,
        modifier = Modifier.width(
            Spacing.tileWidth
        ).heightIn(min = Spacing.actionHeight).testTag(tag),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            Modifier.padding(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun CycleInsights(
    periods: List<Period>,
    today: LocalDate,
    logs: List<DayLog>,
    onLogDay: () -> Unit = {}
) {
    val cycles = app.ritela.domain.measuredCycles(periods, today)
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        modifier = Modifier.testTag("cycle-insights")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            Icon(
                painterResource(R.drawable.ic_chart),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(stringResource(R.string.cycle_trends), style = MaterialTheme.typography.titleLarge)
        }
        if (cycles.values.isEmpty()) {
            Text(stringResource(R.string.chart_empty))
        } else {
            SeriesInsight(cycles.values.takeLast(6))
        }
    }
}

@Composable
private fun SeriesInsight(values: List<Pair<LocalDate, Int>>) {
    val min = values.minOf { it.second }
    val max = values.maxOf { it.second }
    Text(
        if (min == max) {
            pluralStringResource(
                R.plurals.stable_values,
                values.size,
                values.size,
                pluralStringResource(R.plurals.days_value, min, min)
            )
        } else {
            pluralStringResource(
                R.plurals.variable_values,
                values.size,
                values.size,
                stringResource(R.string.days_range, min, max)
            )
        },
        style = MaterialTheme.typography.bodyMedium
    )
    if (values.size >= 2 && min != max) MeasuredChart(values)
}

@Composable
private fun MeasuredChart(values: List<Pair<LocalDate, Int>>) {
    val displayed = if (LocalDensity.current.fontScale > 1.3f) values.takeLast(3) else values
    var selected by androidx.compose.runtime.saveable.rememberSaveable(values) {
        androidx.compose.runtime.mutableStateOf(displayed.lastIndex)
    }
    val colors = MaterialTheme.colorScheme
    val description =
        stringResource(R.string.chart_values, displayed.joinToString { it.second.toString() })
    Canvas(Modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = description }) {
        val min = displayed.minOf { it.second }.toFloat() - 1
        val max = displayed.maxOf { it.second }.toFloat() + 1
        val points = displayed.mapIndexed { index, (_, value) ->
            Offset(
                size.width * (index + 0.5f) / displayed.size,
                size.height * (1 - (value - min) / (max - min))
            )
        }
        points.zipWithNext().forEach { (a, b) -> drawLine(colors.primary, a, b, 2.dp.toPx()) }
        points.forEachIndexed { index, point ->
            drawCircle(
                if (index ==
                    selected
                ) {
                    colors.secondary
                } else {
                    colors.primary
                },
                3.dp.toPx(),
                point
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        displayed.forEachIndexed { index, (_, value) ->
            TextButton(onClick = { selected = index }, modifier = Modifier.weight(1f)) {
                Text(value.toString())
            }
        }
    }
    val choice = displayed[selected.coerceIn(displayed.indices)]
    Text(
        stringResource(
            R.string.chart_selected,
            formattedDate(choice.first),
            pluralStringResource(R.plurals.days_value, choice.second, choice.second)
        ),
        style = MaterialTheme.typography.bodySmall
    )
}
