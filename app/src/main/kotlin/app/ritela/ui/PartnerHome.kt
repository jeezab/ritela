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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.R
import app.ritela.data.PartnerSnapshot
import app.ritela.data.partnerDataMessage
import java.time.LocalDate
import org.json.JSONObject

@Composable
internal fun PartnerHome(padding: PaddingValues, calendar: () -> Unit, messagesPage: () -> Unit) {
    val session = LocalProfileSession.current ?: return
    val directory by session.partnerModel.directory.collectAsStateWithLifecycle()
    val messages by session.partnerModel.messages.collectAsStateWithLifecycle()
    val preferences by session.partnerPreferences.mode.collectAsStateWithLifecycle()
    val contact =
        directory.contacts.firstOrNull { it.id == preferences.selected }
            ?: directory.contacts.firstOrNull()
    val message = contact?.let { partnerDataMessage(messages, it.identities) }
    val snapshot = remember(message?.body) { message?.let { PartnerSnapshot(JSONObject(it.body)) } }
    val formatter = PartnerFormatter(LocalResources.current)
    HomeTheme {
        Column(
            Modifier.fillMaxSize().background(HomeColors.background).padding(padding)
                .verticalScroll(rememberScrollState()).padding(HomeSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(HomeSpacing.gap)
        ) {
            Text(
                contact?.name ?: stringResource(R.string.partner_title),
                style = MaterialTheme.typography.headlineLarge
            )
            if (snapshot == null) {
                SettingsGroup { Text(stringResource(R.string.partner_no_received)) }
            } else {
                SettingsGroup {
                    PartnerCycleSummary(snapshot)
                    message?.let {
                        Text(
                            formatter.exchange(it.received),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    calendar,
                    enabled =
                        snapshot != null &&
                            (snapshot.periods.isNotEmpty() || snapshot.forecasts.isNotEmpty())
                ) {
                    Text(stringResource(R.string.partner_calendar_view))
                }
                TextButton(messagesPage) { Text(stringResource(R.string.partner_gallery)) }
            }
        }
    }
}

@Composable
internal fun PartnerCycleSummary(snapshot: PartnerSnapshot) {
    val formatter = PartnerFormatter(LocalResources.current)
    snapshot.cycleDay?.let {
        Text(
            stringResource(R.string.hero_cycle_day, it),
            style = MaterialTheme.typography.titleLarge
        )
    }
    snapshot.data.optString("phase").takeIf { it.isNotBlank() }?.let {
        Text(
            stringResource(
                when (it) {
                    "EARLY" -> R.string.phase_early_name
                    "FOLLICULAR" -> R.string.phase_follicular_name
                    "OVULATION" -> R.string.phase_ovulation_name
                    "LUTEAL" -> R.string.phase_luteal_name
                    else -> R.string.partner_unknown_phase
                }
            )
        )
    }
    snapshot.asOf?.let {
        Text(
            stringResource(R.string.partner_snapshot_hint, formatter.date(it.toEpochDay())),
            style = MaterialTheme.typography.bodySmall,
            color = HomeColors.muted
        )
    }
    if (snapshot.cycleDay != null && snapshot.cycleLength != null) {
        CycleOrbitScene(
            snapshot.cycleDay,
            orbitDayProgress(snapshot.cycleDay, snapshot.cycleLength),
            orbitReducedMotion(),
            OrbitColors(HomeColors.orbit, HomeColors.rose, HomeColors.peach, HomeColors.card),
            Modifier.fillMaxWidth().height(180.dp)
        )
    }
    snapshot.next(LocalDate.now())?.let {
        Text(
            stringResource(
                R.string.partner_forecast_row,
                formatter.date(it.getLong("start")),
                formatter.date(it.getLong("lower")),
                formatter.date(it.getLong("upper"))
            ),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
internal fun PartnerCalendarPage(padding: PaddingValues) {
    val session = LocalProfileSession.current ?: return
    val directory by session.partnerModel.directory.collectAsStateWithLifecycle()
    val messages by session.partnerModel.messages.collectAsStateWithLifecycle()
    val preferences by session.partnerPreferences.mode.collectAsStateWithLifecycle()
    val contact =
        directory.contacts.firstOrNull { it.id == preferences.selected }
            ?: directory.contacts.firstOrNull()
    val message = contact?.let { partnerDataMessage(messages, it.identities) }
    HomeTheme {
        if (message == null) {
            Column(
                Modifier.fillMaxSize().background(
                    HomeColors.background
                ).padding(padding).padding(24.dp)
            ) {
                Text(stringResource(R.string.partner_no_received))
            }
        } else {
            PartnerReceivedCalendar(
                PartnerSnapshot(JSONObject(message.body)),
                Modifier.padding(padding)
            )
        }
    }
}
