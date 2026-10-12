package app.ritela.ui

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ritela.R
import app.ritela.data.PartnerCodec
import app.ritela.domain.JournalSection
import app.ritela.domain.JournalTag
import org.json.JSONObject

@Composable
internal fun ReadOnlyPartnerData(body: String) {
    val data = remember(body) { JSONObject(body) }
    val formatter = PartnerFormatter(LocalResources.current)
    Text(stringResource(R.string.partner_readonly), style = MaterialTheme.typography.bodySmall)
    val snapshot = remember(body) { app.ritela.data.PartnerSnapshot(data) }
    PartnerCycleSummary(snapshot)
    var calendar by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (snapshot.periods.isNotEmpty() || snapshot.forecasts.isNotEmpty()) {
        TextButton({ calendar = true }) { Text(stringResource(R.string.partner_calendar_view)) }
    }
    if (calendar) {
        PartnerModal(stringResource(R.string.calendar_title), { calendar = false }) {
            PartnerReceivedCalendar(snapshot, androidx.compose.ui.Modifier.height(540.dp))
        }
    }
    data.optJSONArray("periods")?.takeIf { it.length() > 0 }?.let { array ->
        Text(stringResource(R.string.partner_periods), style = MaterialTheme.typography.titleMedium)
        val records = PartnerCodec.objects(array).sortedByDescending { it.getLong("start") }
        var page by remember(body) { mutableIntStateOf(0) }
        PartnerPagination(page, records.size, 12) { page = it }
        records.drop(page * 12).take(12).forEach { period ->
            Text(
                formatter.date(period.getLong("start")) + " — " +
                    if (period.isNull(
                            "end"
                        )
                    ) {
                        stringResource(
                            R.string.partner_ongoing
                        )
                    } else {
                        formatter.date(period.getLong("end"))
                    }
            )
        }
    }
    data.optJSONArray("days")?.takeIf { it.length() > 0 }?.let { array ->
        Text(stringResource(R.string.partner_diary), style = MaterialTheme.typography.titleMedium)
        val records = PartnerCodec.objects(array).sortedByDescending { it.getLong("day") }
        var page by remember(body) { mutableIntStateOf(0) }
        PartnerPagination(page, records.size, 12) { page = it }
        records.drop(page * 12).take(12).forEach { day ->
            SettingsGroup {
                Text(
                    formatter.date(day.getLong("day")),
                    style = MaterialTheme.typography.titleSmall
                )
                PartnerReceivedDay(snapshot, day)
            }
        }
    }
    data.optJSONObject("settings")?.let {
        Text(
            stringResource(R.string.partner_app_settings),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            when (it.getString("language")) {
                "ru" -> "Русский"
                "en" -> "English"
                else -> stringResource(R.string.language_system)
            }
        )
    }
}

@Composable
private fun PartnerPagination(page: Int, count: Int, size: Int, select: (Int) -> Unit) {
    if (count > size) {
        FlowRow {
            TextButton({
                select(page - 1)
            }, enabled = page > 0) { Text(stringResource(R.string.partner_previous)) }
            TextButton({
                select(page + 1)
            }, enabled = (page + 1) * size < count) { Text(stringResource(R.string.partner_next)) }
        }
    }
}
