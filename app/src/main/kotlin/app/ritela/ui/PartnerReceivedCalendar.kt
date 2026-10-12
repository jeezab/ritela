package app.ritela.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ritela.R
import app.ritela.data.PartnerSnapshot
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
internal fun PartnerReceivedCalendar(snapshot: PartnerSnapshot, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val formatter = PartnerFormatter(resources)
    val today = LocalDate.now()
    val months = remember(snapshot) {
        val points =
            snapshot.periods.map { it.getLong("start") } +
                snapshot.forecasts.map { it.getLong("start") } +
                snapshot.days.map { it.getLong("day") } + today.toEpochDay()
        val first = YearMonth.from(LocalDate.ofEpochDay(points.min()))
        val last = YearMonth.from(LocalDate.ofEpochDay(points.max())).plusMonths(1)
        generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= last }.toList()
    }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val list = rememberLazyListState(months.indexOf(YearMonth.from(today)).coerceAtLeast(0))
    LazyColumn(
        modifier.fillMaxSize().background(
            HomeColors.background
        ).testTag("partner-received-calendar"),
        state = list,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        snapshot.asOf?.let { asOf ->
            item {
                Text(
                    stringResource(
                        R.string.partner_snapshot_hint,
                        formatter.date(asOf.toEpochDay())
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        items(months, key = { it.toString() }) { month ->
            Text(
                month.format(
                    DateTimeFormatter.ofPattern(
                        "LLLL yyyy"
                    ).withLocale(resources.configuration.locales[0])
                ),
                style = MaterialTheme.typography.titleLarge
            )
            MonthGrid(
                month,
                today,
                selected,
                atmospheric = true,
                tagPrefix = "partner-calendar-day",
                info = snapshot::dayInfo,
                hasLog = { day -> snapshot.days.any { it.getLong("day") == day.toEpochDay() } },
                onDay = { selected = it }
            )
        }
    }
    selected?.let { date ->
        PartnerModal(formatter.date(date.toEpochDay()), { selected = null }) {
            Text(dayKindText(snapshot.dayInfo(date).kind))
            val forecast = snapshot.forecasts.firstOrNull {
                date.toEpochDay() in it.getLong("lower")..it.getLong("upper") ||
                    date.toEpochDay() == it.getLong("ovulation")
            }
            forecast?.let {
                Text(
                    stringResource(
                        R.string.partner_forecast_row,
                        formatter.date(it.getLong("start")),
                        formatter.date(it.getLong("lower")),
                        formatter.date(it.getLong("upper"))
                    )
                )
                Text(
                    stringResource(
                        R.string.partner_ovulation_range,
                        formatter.date(it.optLong("ovulationLower", it.getLong("ovulation"))),
                        formatter.date(it.optLong("ovulationUpper", it.getLong("ovulation")))
                    )
                )
            }
            val log = snapshot.days.firstOrNull { it.getLong("day") == date.toEpochDay() }
            if (log == null && forecast == null) Text(stringResource(R.string.partner_no_received))
            log?.let { PartnerReceivedDay(snapshot, it) }
        }
    }
}

@Composable
internal fun PartnerReceivedDay(snapshot: PartnerSnapshot, day: org.json.JSONObject) {
    val sections = day.getJSONObject("sections")
    sections.keys().asSequence().forEach { id ->
        val label = snapshot.data.optJSONObject("labels")?.optJSONObject(id)
        val section = app.ritela.domain.JournalSection(
            id,
            label?.optString("title").orEmpty(),
            tags = emptyList()
        )
        val tags = app.ritela.data.PartnerCodec.strings(sections.getJSONArray(id)).map { tag ->
            journalTagLabel(
                section,
                app.ritela.domain.JournalTag(
                    tag,
                    label?.optJSONObject("tags")?.optString(tag).orEmpty()
                )
            )
        }
        Text(journalSectionLabel(section) + ": " + tags.joinToString(", "))
    }
    day.getString("note").takeIf { it.isNotEmpty() }?.let { Text(it) }
}
