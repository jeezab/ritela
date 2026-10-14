package app.ritela.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.ritela.R
import app.ritela.data.PartnerSnapshot
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

@Composable
internal fun PartnerReceivedCalendar(snapshot: PartnerSnapshot, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val formatter = PartnerFormatter(resources)
    val today = LocalDate.now()
    val base = remember { YearMonth.from(today) }
    val bounds = remember(snapshot, base) {
        val points =
            snapshot.periods.flatMap {
                listOf(it.getLong("start"), it.optLong("end", it.getLong("start")))
            } +
                snapshot.forecasts.flatMap {
                    listOf(
                        it.getLong("start") + snapshot.data.optInt("duration", 5) - 1,
                        it.getLong("lower"),
                        it.getLong("upper"),
                        it.getLong("ovulation") + 1
                    )
                } +
                snapshot.days.map { it.getLong("day") }
        val months = points.map { YearMonth.from(LocalDate.ofEpochDay(it)) }
        minOf(base.minusYears(100), months.minOrNull() ?: base) to
            maxOf(base.plusYears(100), months.maxOrNull() ?: base)
    }
    val center = java.time.temporal.ChronoUnit.MONTHS.between(bounds.first, base).toInt()
    val count =
        java.time.temporal.ChronoUnit.MONTHS.between(bounds.first, bounds.second).toInt() + 1
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var history by remember { mutableStateOf(false) }
    val list = rememberLazyListState(center)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    CalendarMonthFeed(
        base, today, selected, list, { selected = it }, { history = true }, snapshot::dayInfo,
        modifier = modifier,
        hasLog = { day -> snapshot.days.any { it.getLong("day") == day.toEpochDay() } },
        dayTag = "partner-calendar-day", listTag = "partner-received-calendar",
        firstMonth = bounds.first, lastMonth = bounds.second
    ) { PartnerCalendarSummaries(snapshot.cycleDay, snapshot.phase) }
    if (history) {
        PartnerModal(stringResource(R.string.calendar_history), { history = false }) {
            if (snapshot.periods.isEmpty()) Text(stringResource(R.string.history_empty))
            snapshot.periods.sortedByDescending { it.getLong("start") }.forEach { period ->
                TextButton({
                    history = false
                    val month = YearMonth.from(LocalDate.ofEpochDay(period.getLong("start")))
                    scope.launch {
                        list.scrollToItem(
                            java.time.temporal.ChronoUnit.MONTHS.between(
                                bounds.first,
                                month
                            ).toInt().coerceIn(
                                0,
                                count - 1
                            )
                        )
                    }
                }, Modifier.fillMaxWidth().testTag("partner-history-${period.getLong("start")}")) {
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
