package app.ritela.ui

import androidx.compose.foundation.layout.FlowRow
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
    data.optString("phase").takeIf { it.isNotEmpty() }?.let { phase ->
        Text(
            stringResource(
                when (phase) {
                    "EARLY" -> R.string.phase_early_name
                    "FOLLICULAR" -> R.string.phase_follicular_name
                    "OVULATION" -> R.string.phase_ovulation_name
                    "LUTEAL" -> R.string.phase_luteal_name
                    else -> R.string.partner_unknown_phase
                }
            )
        )
        if (!data.isNull(
                "cycleDay"
            )
        ) {
            Text(stringResource(R.string.hero_cycle_day, data.getLong("cycleDay")))
        }
        if (data.has(
                "asOf"
            )
        ) {
            Text(
                stringResource(
                    R.string.partner_snapshot_date,
                    formatter.date(data.getLong("asOf"))
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    data.optJSONArray("forecasts")?.let { array ->
        Text(
            stringResource(R.string.partner_forecasts),
            style = MaterialTheme.typography.titleMedium
        )
        PartnerCodec.objects(array).take(12).forEach { forecast ->
            if (forecast.has("ovulationLower") && forecast.has("ovulationUpper")) {
                Text(
                    stringResource(
                        R.string.partner_ovulation_range,
                        formatter.date(forecast.getLong("ovulationLower")),
                        formatter.date(forecast.getLong("ovulationUpper"))
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                stringResource(
                    R.string.partner_forecast_row,
                    formatter.date(forecast.getLong("start")),
                    formatter.date(forecast.getLong("lower")),
                    formatter.date(forecast.getLong("upper"))
                )
            )
            Text(
                stringResource(
                    R.string.partner_ovulation_row,
                    formatter.date(forecast.getLong("ovulation"))
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    data.optJSONArray("periods")?.let { array ->
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
    data.optJSONArray("days")?.let { array ->
        Text(stringResource(R.string.partner_diary), style = MaterialTheme.typography.titleMedium)
        val records = PartnerCodec.objects(array).sortedByDescending { it.getLong("day") }
        var page by remember(body) { mutableIntStateOf(0) }
        PartnerPagination(page, records.size, 12) { page = it }
        records.drop(page * 12).take(12).forEach { day ->
            Text(formatter.date(day.getLong("day")), style = MaterialTheme.typography.titleSmall)
            val sections = day.getJSONObject("sections")
            sections.keys().asSequence().forEach { id ->
                val label = data.optJSONObject("labels")?.optJSONObject(id)
                val section =
                    JournalSection(id, label?.optString("title").orEmpty(), tags = emptyList())
                val values = PartnerCodec.strings(sections.getJSONArray(id)).map { tagId ->
                    journalTagLabel(
                        section,
                        JournalTag(tagId, label?.optJSONObject("tags")?.optString(tagId).orEmpty())
                    )
                }
                Text(journalSectionLabel(section) + ": " + values.joinToString(", "))
            }
            day.getString("note").takeIf { it.isNotEmpty() }?.let { Text(it) }
        }
    }
    data.optJSONObject("settings")?.let {
        Text(
            stringResource(
                R.string.partner_settings_row,
                it.getString("language"),
                it.getString("theme")
            )
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
