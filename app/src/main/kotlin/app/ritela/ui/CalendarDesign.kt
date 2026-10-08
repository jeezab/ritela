package app.ritela.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ritela.R
import app.ritela.domain.EstimatedCyclePhase
import app.ritela.domain.cycleSceneState
import java.time.format.DateTimeFormatter

/** Calendar state accents extend the existing Home design system. */
object CalendarDesign {
    val period = HomeColors.rose
    val estimatedPeriod = HomeColors.blush
    val fertile = Color(0xFFE3EED6)
    val ovulation = Color(0xFFAFCB8B)
}

@Composable
fun CalendarAtmosphericHeader(onLegend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = HomeSpacing.gap, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 32.sp)
            )
            Text(
                stringResource(R.string.calendar_title),
                Modifier.testTag("calendar-heading"),
                style = MaterialTheme.typography.bodyLarge,
                color = HomeColors.muted
            )
        }
        if (LocalDensity.current.fontScale <= 1.3f) {
            CycleOrbitScene(
                1,
                0.3f,
                true,
                OrbitColors(HomeColors.text, HomeColors.orbit, HomeColors.peach, HomeColors.top),
                Modifier.size(96.dp, 80.dp)
            )
        }
        IconButton(onClick = onLegend) {
            Icon(painterResource(R.drawable.ic_info), stringResource(R.string.calendar_key))
        }
    }
}

@Composable
fun CalendarSummaries(state: PeriodUiState) {
    val next = state.analysis.forecasts.firstOrNull()
    val phase = cycleSceneState(state.analysis, state.today).phase
    Column(
        Modifier.fillMaxWidth().padding(vertical = HomeSpacing.gap),
        verticalArrangement = Arrangement.spacedBy(HomeSpacing.gap)
    ) {
        if (next != null && state.periods.none { it.end == null }) {
            CalendarSummaryCard {
                Text(
                    stringResource(R.string.forecast_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeColors.muted
                )
                Text(
                    next.predictedStartDate.format(
                        DateTimeFormatter.ofPattern(
                            "d MMMM yyyy",
                            LocalConfiguration.current.locales[0]
                        )
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    stringResource(
                        R.string.forecast_range,
                        formattedDate(next.lowerBound),
                        formattedDate(next.upperBound)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeColors.muted
                )
            }
        }
        if (phase != EstimatedCyclePhase.UNKNOWN) {
            CalendarSummaryCard {
                Text(
                    stringResource(R.string.home_phase_tile),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeColors.muted
                )
                Text(
                    stringResource(
                        when (phase) {
                            EstimatedCyclePhase.EARLY -> R.string.phase_early_estimate
                            EstimatedCyclePhase.FOLLICULAR -> R.string.phase_follicular_estimate
                            EstimatedCyclePhase.OVULATION -> R.string.phase_ovulation_estimate
                            EstimatedCyclePhase.LUTEAL -> R.string.phase_luteal_estimate
                            EstimatedCyclePhase.UNKNOWN -> R.string.home_phase_estimated
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
        CalendarSummaryCard {
            Text(stringResource(R.string.calendar_key), style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((icon, color, label) in listOf(
                    Triple(R.drawable.ic_drop, CalendarDesign.period, R.string.calendar_observed),
                    Triple(
                        R.drawable.ic_calendar,
                        CalendarDesign.estimatedPeriod,
                        R.string.calendar_predicted
                    ),
                    Triple(R.drawable.ic_flower, CalendarDesign.fertile, R.string.fertile_estimate),
                    Triple(
                        R.drawable.ic_flower,
                        CalendarDesign.ovulation,
                        R.string.ovulation_estimate
                    )
                )) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(painterResource(icon), null, Modifier.size(14.dp), tint = color)
                        Text(
                            stringResource(label),
                            style = MaterialTheme.typography.bodySmall,
                            color = HomeColors.muted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarSummaryCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = HomeColors.card.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, HomeColors.border.copy(alpha = 0.6f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}
