package app.ritela.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.R
import app.ritela.data.PartnerSnapshot
import app.ritela.data.partnerDataMessage
import app.ritela.domain.CycleAnalysis
import java.time.LocalDate
import org.json.JSONObject

@Composable
internal fun PartnerHome(padding: PaddingValues, calendar: () -> Unit, messagesPage: () -> Unit) {
    val session = LocalProfileSession.current ?: return
    val directory by session.partnerModel.directory.collectAsStateWithLifecycle()
    val messages by session.partnerModel.messages.collectAsStateWithLifecycle()
    val preferences by session.partnerPreferences.mode.collectAsStateWithLifecycle()
    val contact = directory.contacts.firstOrNull { it.id == preferences.homeSelected }
        ?: directory.contacts.firstOrNull()
    val message = contact?.let { partnerDataMessage(messages, it.identities) }
    val snapshot = remember(message?.body) { message?.let { PartnerSnapshot(JSONObject(it.body)) } }
    PartnerHomeContent(padding, directory.contacts, contact, snapshot, {
        session.partnerModel.updatePreferences { session.partnerPreferences.selectHome(it) }
    }, calendar, messagesPage)
}

@Composable
internal fun PartnerHomeContent(
    padding: PaddingValues,
    contacts: List<app.ritela.domain.PartnerContact>,
    contact: app.ritela.domain.PartnerContact?,
    snapshot: PartnerSnapshot?,
    onSelect: (String) -> Unit,
    calendar: () -> Unit,
    messagesPage: () -> Unit
) {
    var choosing by remember { mutableStateOf(false) }
    HomeTheme {
        Column(
            Modifier.fillMaxSize().background(HomeColors.background).padding(padding)
                .verticalScroll(rememberScrollState()).padding(HomeSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(HomeSpacing.gap)
        ) {
            androidx.compose.foundation.layout.Box {
                val name = contact?.name ?: stringResource(R.string.partner_title)
                if (contacts.size > 1) {
                    TextButton(
                        { choosing = true },
                        modifier = Modifier.testTag("partner-home-selector")
                    ) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                name,
                                color = HomeColors.text,
                                style = MaterialTheme.typography.headlineLarge
                            )
                            Icon(
                                painterResource(R.drawable.ic_chevron_down),
                                stringResource(R.string.partner_choose_home),
                                Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        name,
                        Modifier.testTag("partner-home-selector"),
                        color = HomeColors.text,
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
                DropdownMenu(choosing, { choosing = false }) {
                    contacts.forEach { candidate ->
                        DropdownMenuItem({ Text(candidate.name) }, {
                            choosing = false
                            onSelect(candidate.id)
                        }, modifier = Modifier.testTag("partner-home-select-${candidate.id}"))
                    }
                }
            }
            key(contact?.id) {
                snapshot?.let { PartnerCycleSummary(it) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        calendar,
                        enabled = snapshot != null &&
                            (snapshot.periods.isNotEmpty() || snapshot.forecasts.isNotEmpty())
                    ) {
                        Text(stringResource(R.string.partner_calendar_view))
                    }
                    TextButton(messagesPage) { Text(stringResource(R.string.partner_gallery)) }
                }
                HelpCards(null, CycleAnalysis(), LocalDate.now())
            }
        }
    }
}

@Composable
internal fun PartnerCycleSummary(snapshot: PartnerSnapshot) {
    val reference = snapshot.asOf ?: LocalDate.now()
    val next = snapshot.next(reference)?.let { LocalDate.ofEpochDay(it.getLong("start")) }
    if (next == null && snapshot.cycleDay == null) return
    val formatter = PartnerFormatter(LocalResources.current)
    val reducedMotion = orbitReducedMotion()
    var showInfo by remember { mutableStateOf(false) }
    var showOrbit by remember { mutableStateOf(false) }
    var selectedMarker by remember { mutableStateOf<OrbitMarker?>(null) }
    val day = snapshot.cycleDay
    val length = snapshot.cycleLength
    val available = next != null && day != null && length != null && snapshot.asOf != null
    CycleForecastHero(
        day, next, snapshot.phase,
        if (day != null && length != null) orbitDayProgress(day, length) else 0f,
        reference, reducedMotion, null, available, length ?: 28,
        { showInfo = true }, { showOrbit = true }, { selectedMarker = it }
    )
    snapshot.asOf?.let {
        Text(
            stringResource(R.string.partner_snapshot_hint, formatter.date(it.toEpochDay())),
            style = MaterialTheme.typography.bodySmall,
            color = HomeColors.muted
        )
    }
    if (showInfo) {
        PartnerModal(stringResource(R.string.forecast_info), { showInfo = false }) {
            snapshot.asOf?.let {
                Text(
                    stringResource(R.string.partner_snapshot_hint, formatter.date(it.toEpochDay()))
                )
            }
            Text(stringResource(R.string.prediction_method_info))
            Text(stringResource(R.string.selected_day_fertility_guide))
        }
    }
    if (showOrbit && available) {
        val current = requireNotNull(day)
        CycleOrbitDetails(current, reference, reducedMotion, { focus ->
            requireNotNull(partnerOrbitInteractionState(snapshot, focus))
        }, { showOrbit = false })
    }
    selectedMarker?.let { OrbitPhaseInfo(it) { selectedMarker = null } }
}

@Composable
internal fun PartnerCalendarPage(padding: PaddingValues) {
    val session = LocalProfileSession.current ?: return
    val directory by session.partnerModel.directory.collectAsStateWithLifecycle()
    val messages by session.partnerModel.messages.collectAsStateWithLifecycle()
    val preferences by session.partnerPreferences.mode.collectAsStateWithLifecycle()
    val contact = directory.contacts.firstOrNull { it.id == preferences.homeSelected }
        ?: directory.contacts.firstOrNull()
    val message = contact?.let { partnerDataMessage(messages, it.identities) }
    val snapshot =
        remember(message?.body) {
            PartnerSnapshot(message?.let { JSONObject(it.body) } ?: JSONObject())
        }
    HomeTheme {
        key(contact?.id, message?.id) {
            Column(
                Modifier.fillMaxSize().background(HomeColors.background).padding(padding)
            ) {
                PartnerReceivedCalendar(snapshot, Modifier.weight(1f))
            }
        }
    }
}
