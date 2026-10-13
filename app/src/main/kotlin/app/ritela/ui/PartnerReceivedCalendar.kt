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
import kotlinx.coroutines.launch

@Composable
internal fun PartnerReceivedCalendar(snapshot: PartnerSnapshot, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val formatter = PartnerFormatter(resources)
    val today = LocalDate.now()
    val base = remember { YearMonth.from(today) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var history by remember { mutableStateOf(false) }
    val list = rememberLazyListState(1200)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    CalendarMonthFeed(
        base, today, selected, list, { selected = it }, { history = true }, snapshot::dayInfo,
        modifier = modifier,
        hasLog = { day -> snapshot.days.any { it.getLong("day") == day.toEpochDay() } },
        dayTag = "partner-calendar-day", listTag = "partner-received-calendar"
    ) { CalendarLegend() }
    if (history) {
        PartnerModal(stringResource(R.string.calendar_history), { history = false }) {
            if (snapshot.periods.isEmpty()) Text(stringResource(R.string.history_empty))
            snapshot.periods.sortedByDescending { it.getLong("start") }.forEach { period ->
                TextButton({
                    history = false
                    val month = YearMonth.from(LocalDate.ofEpochDay(period.getLong("start")))
                    scope.launch {
                        list.scrollToItem(
                            (
                                1200 + (month.year - base.year) * 12 +
                                    month.monthValue -
                                    base.monthValue
                                ).coerceIn(0, 2400)
                        )
                    }
                }, Modifier.fillMaxWidth()) {
                    Text(
                        formatter.date(period.getLong("start")) + " \u2013 " +
                            if (period.isNull(
                                    "end"
                                )
                            ) {
                                "\u2026"
                            } else {
                                formatter.date(period.getLong("end"))
                            }
                    )
                }
            }
        }
    }

    selected?.let { date ->
        PartnerModal(formatter.date(date.toEpochDay()), { selected = null }) {
            if (snapshot.dayInfo(date).kind != app.ritela.domain.CalendarDayKind.NONE) {
                Text(
                    dayKindText(snapshot.dayInfo(date).kind),
                    Modifier.testTag("partner-day-status")
                )
            }
            val forecast = snapshot.forecasts.firstOrNull {
                date.toEpochDay() in it.getLong("lower")..it.getLong("upper") ||
                    date.toEpochDay() in
                    (it.getLong("ovulation") - 5)..(it.getLong("ovulation") + 1)
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
