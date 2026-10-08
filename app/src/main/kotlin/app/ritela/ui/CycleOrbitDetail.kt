package app.ritela.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ritela.R
import app.ritela.domain.CycleAnalysis
import app.ritela.domain.EstimatedCyclePhase
import java.time.LocalDate
import kotlin.math.roundToLong

fun orbitMarkerTitle(marker: OrbitMarker): Int = when (marker) {
    OrbitMarker.START -> R.string.orbit_start
    OrbitMarker.FOLLICULAR -> R.string.orbit_follicular
    OrbitMarker.OVULATION -> R.string.orbit_ovulation
    OrbitMarker.LUTEAL -> R.string.orbit_luteal
    OrbitMarker.END -> R.string.orbit_end
}

@Composable
fun OrbitPhaseInfo(marker: OrbitMarker, onDismiss: () -> Unit) {
    HomeTheme {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag("orbit-phase-info"),
            title = { Text(stringResource(orbitMarkerTitle(marker))) },
            text = {
                Column(
                    Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(
                            when (marker) {
                                OrbitMarker.START -> R.string.orbit_start_info
                                OrbitMarker.FOLLICULAR -> R.string.help_follicular_body
                                OrbitMarker.OVULATION -> R.string.help_ovulation_body
                                OrbitMarker.LUTEAL -> R.string.help_luteal_body
                                OrbitMarker.END -> R.string.orbit_end_info
                            }
                        )
                    )
                    Text(
                        stringResource(R.string.orbit_symbolic),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("orbit-phase-close")) {
                    Text(stringResource(R.string.done))
                }
            }
        )
    }
}

@Composable
fun CycleOrbitDetail(
    analysis: CycleAnalysis,
    today: LocalDate,
    reducedMotion: Boolean,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    var focusDay by rememberSaveable(analysis.cycleDay, today) {
        mutableLongStateOf(analysis.cycleDay ?: 1)
    }
    var selectedMarker by rememberSaveable { mutableStateOf<OrbitMarker?>(null) }
    val state = orbitInteractionState(analysis, today, focusDay)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalDensity provides density) {
            HomeTheme {
                Surface(Modifier.fillMaxSize().testTag("orbit-detail")) {
                    Column(Modifier.background(HomeColors.background).padding(HomeSpacing.gutter)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.orbit_title),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.headlineMedium
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("orbit-detail-close")
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_close),
                                    stringResource(R.string.done)
                                )
                            }
                        }
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(HomeSpacing.gap)
                        ) {
                            Text(
                                stringResource(R.string.orbit_current_day, state.currentDay),
                                color = HomeColors.muted
                            )
                            Box(Modifier.testTag("orbit-scrub")) {
                                CycleOrbitScene(
                                    state.currentDay,
                                    state.currentProgress,
                                    reducedMotion,
                                    OrbitColors(
                                        HomeColors.text,
                                        HomeColors.orbit,
                                        HomeColors.peach,
                                        HomeColors.top
                                    ),
                                    Modifier.fillMaxWidth().height(280.dp),
                                    onMarker = { selectedMarker = it },
                                    onScrub = { focusDay = orbitDayAtProgress(it, state.length) },
                                    focusProgress = state.focusProgress
                                )
                            }
                            Text(
                                stringResource(R.string.orbit_focus_legend),
                                style = MaterialTheme.typography.bodySmall,
                                color = HomeColors.muted
                            )
                            Text(
                                stringResource(R.string.orbit_focus_day, state.focusDay),
                                Modifier.testTag("orbit-focus-day"),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                formattedDate(state.focusDate),
                                Modifier.testTag("orbit-focus-date")
                            )
                            Text(
                                stringResource(
                                    when (state.phase) {
                                        EstimatedCyclePhase.EARLY -> R.string.phase_early_estimate

                                        EstimatedCyclePhase.FOLLICULAR -> {
                                            R.string.phase_follicular_estimate
                                        }

                                        EstimatedCyclePhase.OVULATION -> {
                                            R.string.phase_ovulation_estimate
                                        }

                                        EstimatedCyclePhase.LUTEAL -> R.string.phase_luteal_estimate

                                        EstimatedCyclePhase.UNKNOWN -> R.string.hero_phase_info
                                    }
                                ),
                                Modifier.testTag("orbit-focus-phase"),
                                color = HomeColors.muted
                            )
                            Text(
                                stringResource(R.string.orbit_scrub_hint),
                                style = MaterialTheme.typography.bodySmall
                            )
                            val sliderLabel = stringResource(R.string.orbit_focus_control)
                            Slider(
                                value = state.focusDay.toFloat().coerceIn(
                                    1f,
                                    state.length.toFloat()
                                ),
                                onValueChange = {
                                    focusDay =
                                        it.roundToLong().coerceIn(1, state.length.toLong())
                                },
                                valueRange = 1f..state.length.coerceAtLeast(2).toFloat(),
                                steps = (state.length - 2).coerceAtLeast(0),
                                modifier = Modifier.testTag("orbit-day-slider").semantics {
                                    contentDescription =
                                        sliderLabel
                                }
                            )
                            FlowRow {
                                TextButton(onClick = {
                                    focusDay =
                                        (focusDay - 1).coerceIn(1, state.length.toLong())
                                }, modifier = Modifier.testTag("orbit-previous")) {
                                    Text(stringResource(R.string.orbit_previous_day))
                                }
                                TextButton(onClick = {
                                    focusDay = state.currentDay
                                }, modifier = Modifier.testTag("orbit-reset")) {
                                    Text(stringResource(R.string.home_title))
                                }
                                TextButton(onClick = {
                                    focusDay =
                                        (focusDay + 1).coerceIn(1, state.length.toLong())
                                }, modifier = Modifier.testTag("orbit-next")) {
                                    Text(stringResource(R.string.orbit_next_day))
                                }
                            }
                            if (state.overdue) Text(stringResource(R.string.orbit_overdue))
                            Text(
                                stringResource(R.string.orbit_symbolic),
                                style = MaterialTheme.typography.bodySmall,
                                color = HomeColors.muted
                            )
                            analysis.forecasts.firstOrNull()?.let {
                                Text(
                                    stringResource(
                                        R.string.forecast_range,
                                        formattedDate(it.lowerBound),
                                        formattedDate(it.upperBound)
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            FlowRow {
                                OrbitMarker.entries.forEach { marker ->
                                    TextButton(onClick = {
                                        selectedMarker = marker
                                    }, modifier = Modifier.testTag("orbit-legend-${marker.name}")) {
                                        Text(stringResource(orbitMarkerTitle(marker)))
                                    }
                                }
                            }
                        }
                    }
                }
                selectedMarker?.let { OrbitPhaseInfo(it) { selectedMarker = null } }
            }
        }
    }
}
