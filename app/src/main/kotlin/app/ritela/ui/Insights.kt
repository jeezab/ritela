package app.ritela.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
fun DaySummary(log: DayLog?, onEdit: () -> Unit, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.titleLarge)
            TextButton(
                onClick = onEdit,
                enabled = enabled,
                modifier = Modifier.testTag("log-day")
            ) {
                Icon(painterResource(R.drawable.ic_note), contentDescription = null)
                Text(stringResource(R.string.log_day), Modifier.padding(start = Spacing.small))
            }
        }
        if (log == null || log.empty) {
            Text(
                stringResource(R.string.today_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        log?.let {
            listOf(
                Triple(R.string.headache, R.drawable.ic_head, it.headache),
                Triple(R.string.cramps, R.drawable.ic_drop, it.cramps),
                Triple(R.string.backache, R.drawable.ic_drop, it.backache),
                Triple(R.string.flow_title, R.drawable.ic_drop, it.flow),
                Triple(R.string.mood_title, R.drawable.ic_heart, it.mood),
                Triple(R.string.energy_title, R.drawable.ic_energy, it.energy)
            ).forEach { (label, icon, value) ->
                if (value != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        Icon(
                            painterResource(icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(20.dp)
                        )
                        Text(
                            stringResource(
                                R.string.day_summary,
                                stringResource(label),
                                eventLabel(value)
                            )
                        )
                    }
                }
            }
            if (it.sex.isNotEmpty()) {
                Text(
                    stringResource(R.string.sex_title),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    it.sex.forEach { sex -> Text(eventLabel(sex)) }
                }
            }
            if (it.note.isNotBlank()) Text(it.note)
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
    val durations = app.ritela.domain.measuredDurations(periods, today)
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.large)) {
                Column {
                    Text(
                        pluralStringResource(
                            R.plurals.days_value,
                            cycles.median!!,
                            cycles.median!!
                        ),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        stringResource(R.string.cycle_median),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column {
                    Text(
                        stringResource(R.string.days_range, cycles.min!!, cycles.max!!),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        stringResource(R.string.cycle_spread),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                stringResource(R.string.year_basis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SeriesInsight(cycles.values.takeLast(6))
        }
        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(stringResource(R.string.period_chart), style = MaterialTheme.typography.titleLarge)
        if (durations.values.isEmpty()) {
            Text(stringResource(R.string.period_chart_empty))
        } else {
            Text(
                pluralStringResource(R.plurals.days_value, durations.median!!, durations.median!!),
                style = MaterialTheme.typography.headlineMedium
            )
            SeriesInsight(durations.values.takeLast(6))
        }
        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(stringResource(R.string.symptom_chart), style = MaterialTheme.typography.titleLarge)
        val recent = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val data = recent.map { date -> date to logs.firstOrNull { it.date == date } }
        val measured = data.filter { it.second?.headache != null || it.second?.cramps != null }
        if (measured.isEmpty()) {
            Text(
                stringResource(R.string.pain_empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.pain_empty_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onLogDay) { Text(stringResource(R.string.log_day)) }
        } else {
            val withPain = measured.count { (_, log) ->
                (log?.headache?.ordinal ?: 0) > 0 ||
                    (log?.cramps?.ordinal ?: 0) > 0
            }
            Text(
                stringResource(
                    if (withPain ==
                        0
                    ) {
                        R.string.no_pain_logged
                    } else {
                        R.string.pain_logged_days
                    },
                    withPain
                )
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                measured.forEach { (date, log) ->
                    Text(
                        "${date.dayOfMonth}: ${log?.headache?.let {
                            eventLabel(it)
                        } ?: "\u2014"} / " +
                            (log?.cramps?.let { eventLabel(it) } ?: "\u2014"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                stringResource(R.string.pain_series_key),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
