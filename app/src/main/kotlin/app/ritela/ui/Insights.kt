package app.ritela.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
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
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

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
        TextButton(onClick = onEdit, enabled = enabled, modifier = Modifier.testTag("log-day")) {
            Icon(painterResource(R.drawable.ic_note), contentDescription = null)
            Text(stringResource(R.string.log_day), Modifier.padding(start = Spacing.small))
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            listOf(
                Triple(R.string.headache, R.drawable.ic_head, log?.headache),
                Triple(R.string.cramps, R.drawable.ic_drop, log?.cramps),
                Triple(R.string.energy_title, R.drawable.ic_energy, log?.energy)
            ).forEach { (label, icon, value) ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        Modifier.padding(Spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        Icon(
                            painterResource(icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                        Text(
                            value?.let { eventLabel(it) } ?: stringResource(R.string.not_logged),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        log?.let {
            val rest = listOf(
                R.string.backache to it.backache,
                R.string.flow_title to it.flow,
                R.string.mood_title to it.mood
            )
            rest.forEach { (label, value) ->
                value?.let {
                    Text(
                        stringResource(R.string.day_summary, stringResource(label), eventLabel(it))
                    )
                }
            }
            if (it.sex.isNotEmpty()) {
                Text(
                    stringResource(R.string.sex_title),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    it.sex.sortedBy { value ->
                        value.name
                    }.forEach { value -> Text(eventLabel(value)) }
                }
            }
            if (it.note.isNotBlank()) Text(it.note)
        }
    }
}

@Composable
fun CycleInsights(periods: List<Period>, today: LocalDate, logs: List<DayLog>) {
    val ordered = periods.sortedBy { it.start }
    val samples = ordered.zipWithNext().filter { (a, _) -> a.start >= today.minusYears(1) }
        .takeLast(12).map { (a, b) -> a.start to ChronoUnit.DAYS.between(a.start, b.start).toInt() }
        .filter { it.second in 1..365 }
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        modifier = Modifier.testTag("cycle-insights")
    ) {
        Text(stringResource(R.string.cycle_trends), style = MaterialTheme.typography.titleLarge)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                Modifier.fillMaxWidth().padding(Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                Icon(
                    painterResource(R.drawable.ic_chart),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                if (samples.isEmpty()) {
                    Text(stringResource(R.string.chart_empty))
                } else {
                    val sorted = samples.map { it.second }.sorted()
                    val middle = sorted.size / 2
                    val median = ((sorted[(sorted.size - 1) / 2] + sorted[middle]) / 2.0)
                        .roundToInt()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.large)) {
                        Column {
                            Text(
                                stringResource(R.string.cycle_median),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                pluralStringResource(R.plurals.days_value, median, median),
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                        Column {
                            Text(
                                stringResource(R.string.cycle_spread),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                stringResource(R.string.days_range, sorted.first(), sorted.last()),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.year_basis),
                        style = MaterialTheme.typography.bodySmall
                    )
                    MeasuredChart(samples.takeLast(6))
                }
            }
        }
        val durations = ordered.filter { it.end != null && it.start >= today.minusYears(1) }
            .takeLast(6).map { it.start to (ChronoUnit.DAYS.between(it.start, it.end) + 1).toInt() }
        Card {
            Column(
                Modifier.fillMaxWidth().padding(Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                Text(
                    stringResource(R.string.period_chart),
                    style = MaterialTheme.typography.titleMedium
                )
                if (durations.isEmpty()) {
                    Text(
                        stringResource(R.string.period_chart_empty)
                    )
                } else {
                    MeasuredChart(durations)
                }
            }
        }
        Card {
            Column(
                Modifier.fillMaxWidth().padding(Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                Text(
                    stringResource(R.string.symptom_chart),
                    style = MaterialTheme.typography.titleMedium
                )
                val recent = (6 downTo 0).map { today.minusDays(it.toLong()) }
                val data = recent.map { date -> date to logs.firstOrNull { it.date == date } }
                if (data.all { it.second?.headache == null && it.second?.cramps == null }) {
                    Text(stringResource(R.string.symptom_chart_empty))
                } else {
                    val colors = MaterialTheme.colorScheme
                    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                        data.forEachIndexed { index, (_, log) ->
                            val cell = size.width / 7
                            listOf(log?.headache, log?.cramps).forEachIndexed { series, pain ->
                                if (pain != null) {
                                    val x = cell * (index + 0.4f + series * 0.25f)
                                    drawLine(
                                        if (series == 0) colors.primary else colors.secondary,
                                        Offset(x, size.height),
                                        Offset(
                                            x,
                                            size.height -
                                                (pain.ordinal + 0.15f) / 3.2f * size.height
                                        ),
                                        strokeWidth = cell * 0.16f
                                    )
                                }
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                        data.forEach { (date, log) ->
                            Text(
                                "${date.dayOfMonth}: ${log?.headache?.ordinal ?: "—"}/${log?.cramps?.ordinal ?: "—"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.symptom_chart_key),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun MeasuredChart(values: List<Pair<LocalDate, Int>>) {
    val displayed = if (LocalDensity.current.fontScale > 1.3f) values.takeLast(3) else values
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val description =
        stringResource(R.string.chart_values, displayed.joinToString { it.second.toString() })
    Canvas(Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription = description }) {
        val max = (displayed.maxOf { it.second } + 2).toFloat()
        repeat(3) { index ->
            val y = size.height * index / 2
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        val points = displayed.mapIndexed { index, (_, value) ->
            Offset(size.width * (index + 0.5f) / displayed.size, size.height * (1 - value / max))
        }
        points.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 2.dp.toPx()) }
        points.forEach { point -> drawCircle(color, 4.dp.toPx(), point) }
    }
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth()) {
        displayed.forEach { (date, value) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value.toString(), style = MaterialTheme.typography.titleMedium)
                Text(
                    date.format(DateTimeFormatter.ofPattern("d MMM", locale)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
