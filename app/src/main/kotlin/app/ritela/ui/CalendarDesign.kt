package app.ritela.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.ritela.R
import app.ritela.domain.EstimatedCyclePhase
import app.ritela.domain.cycleSceneState
import java.time.format.DateTimeFormatter

/** Calendar state accents extend the existing Home design system. */
object CalendarDesign {
    val period = HomeColors.rose
    val estimatedPeriod = HomeColors.blush
    val fertile = Color(0xFFE3EED6)
    val fertileAccent = Color(0xFFC9DEB2)
    val ovulation = Color(0xFFAFCB8B)
}

@Composable
fun CalendarSummaries(state: PeriodUiState, expanded: Boolean, onToggle: () -> Unit) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    if (showInfo) PredictionMethodInfo(state.analysis, includeOrbit = false) { showInfo = false }
    val expandedLabel = stringResource(R.string.calendar_expand)
    val collapsedLabel = stringResource(R.string.calendar_collapse)
    val next = state.analysis.forecasts.firstOrNull()
    val phase = cycleSceneState(state.analysis, state.today).phase
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "cycle details")
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().testTag("calendar-summaries-toggle").semantics {
                stateDescription = if (expanded) expandedLabel else collapsedLabel
            }
        ) {
            Icon(
                painterResource(R.drawable.ic_chevron_down),
                if (expanded) collapsedLabel else expandedLabel,
                Modifier.size(18.dp).graphicsLayer { rotationZ = angle }
            )
        }
        AnimatedVisibility(
            expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                Modifier.testTag("calendar-summaries"),
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
                            stringResource(R.string.current_cycle_phase),
                            style = MaterialTheme.typography.bodySmall,
                            color = HomeColors.muted
                        )
                        Text(
                            stringResource(phaseName(phase)),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
                CalendarSummaryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.calendar_key),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge
                        )
                        IconButton(onClick = {
                            showInfo = true
                        }, modifier = Modifier.testTag("calendar-forecast-info")) {
                            Icon(
                                painterResource(R.drawable.ic_info),
                                stringResource(R.string.forecast_info)
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for ((icon, color, label) in listOf(
                            Triple(
                                R.drawable.ic_drop,
                                CalendarDesign.period,
                                R.string.calendar_observed
                            ),
                            Triple(
                                R.drawable.ic_calendar,
                                CalendarDesign.estimatedPeriod,
                                R.string.calendar_predicted
                            ),
                            Triple(
                                R.drawable.ic_flower,
                                CalendarDesign.fertile,
                                R.string.selected_day_fertile
                            ),
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
                                Icon(
                                    painterResource(icon),
                                    null,
                                    Modifier.size(14.dp),
                                    tint = color
                                )
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
