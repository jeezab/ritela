package app.ritela.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ritela.R
import app.ritela.domain.CycleAnalysis
import app.ritela.domain.EstimatedCyclePhase
import app.ritela.domain.ForecastUnavailable
import app.ritela.domain.Period
import app.ritela.domain.analyzeCycles
import app.ritela.domain.cycleSceneState
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

@Composable
fun ForecastCard(
    analysis: CycleAnalysis,
    today: LocalDate = LocalDate.now(),
    reducedMotion: Boolean = orbitReducedMotion()
) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var showOrbit by rememberSaveable { mutableStateOf(false) }
    var selectedMarker by rememberSaveable { mutableStateOf<OrbitMarker?>(null) }
    val orbit = orbitInteractionState(analysis, today)
    val next = analysis.forecasts.firstOrNull()
    val scene = cycleSceneState(analysis, today)
    val colors = HomeColors
    val heroSurface = HomeColors.card.copy(alpha = 0.68f)
    val locale = LocalConfiguration.current.locales[0]
    val fontScale = LocalDensity.current.fontScale
    val openLabel = stringResource(R.string.orbit_open)
    HomeTheme {
        Column(Modifier.fillMaxWidth().testTag("forecast-hero")) {
            if (next != null && fontScale <= 1.5f) {
                CycleOrbitScene(
                    analysis.cycleDay ?: 1,
                    orbit.currentProgress,
                    reducedMotion,
                    OrbitColors(
                        HomeColors.text,
                        HomeColors.orbit,
                        HomeColors.peach,
                        HomeColors.top
                    ),
                    Modifier.fillMaxWidth().aspectRatio(350f / 228f),
                    onMarker = { selectedMarker = it },
                    onOpen = { showOrbit = true }
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth().testTag("orbit-open").semantics {
                    if (next != null) {
                        role = Role.Button
                        onClick(openLabel) {
                            showOrbit = true
                            true
                        }
                    }
                }.pointerInput(next) {
                    detectTapGestures(onTap = { if (next != null) showOrbit = true })
                },
                border = androidx.compose.foundation.BorderStroke(1.dp, HomeColors.border),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = heroSurface)
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            analysis.cycleDay?.let {
                                Text(
                                    stringResource(R.string.hero_cycle_day, it),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                                    )
                                )
                            }
                            if (scene.phase != EstimatedCyclePhase.UNKNOWN) {
                                Text(
                                    stringResource(phaseName(scene.phase)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted
                                )
                            }
                        }
                        IconButton(onClick = {
                            showInfo = true
                        }, Modifier.testTag("forecast-info")) {
                            Icon(
                                painterResource(R.drawable.ic_info),
                                stringResource(R.string.forecast_info)
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.forecast_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.muted
                    )
                    if (next == null) {
                        Text(
                            stringResource(
                                when (analysis.unavailable) {
                                    ForecastUnavailable.INVALID_HISTORY -> R.string.forecast_invalid
                                    ForecastUnavailable.ONGOING -> R.string.forecast_ongoing
                                    ForecastUnavailable.PAST_DUE -> R.string.forecast_past_due
                                    else -> R.string.forecast_need_more
                                }
                            ),
                            color = colors.muted
                        )
                    } else {
                        val pattern = if (locale.language == "ru") "d MMMM" else "MMM d"
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                next.predictedStartDate.format(
                                    DateTimeFormatter.ofPattern(pattern, locale)
                                ),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 30.sp
                                ),
                                color = colors.text,
                                modifier = Modifier.weight(1f).testTag("forecast-date")
                            )
                            val days = ChronoUnit.DAYS.between(
                                today,
                                next.predictedStartDate
                            ).toInt()
                            Surface(
                                color = colors.border.copy(alpha = 0.38f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.sizeIn(minWidth = 76.dp, minHeight = 76.dp)
                                    .testTag("forecast-countdown")
                            ) {
                                Column(
                                    Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    when {
                                        days == 0 -> Text(
                                            stringResource(R.string.period_expected_today),
                                            style = MaterialTheme.typography.labelSmall
                                        )

                                        else -> {
                                            if (days > 0) {
                                                Text(
                                                    stringResource(R.string.countdown_in),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                            Text(
                                                pluralStringResource(
                                                    R.plurals.countdown_days,
                                                    kotlin.math.abs(days),
                                                    kotlin.math.abs(days)
                                                ),
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            if (days < 0) {
                                                Text(
                                                    stringResource(R.string.countdown_ago),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showInfo) PredictionMethodInfo(analysis) { showInfo = false }
    if (showOrbit && next != null) {
        CycleOrbitDetail(analysis, today, reducedMotion) { showOrbit = false }
    }
    selectedMarker?.let { OrbitPhaseInfo(it) { selectedMarker = null } }
}

fun phaseName(phase: EstimatedCyclePhase): Int = when (phase) {
    EstimatedCyclePhase.EARLY -> R.string.phase_early_name
    EstimatedCyclePhase.FOLLICULAR -> R.string.phase_follicular_name
    EstimatedCyclePhase.OVULATION -> R.string.phase_ovulation_name
    EstimatedCyclePhase.LUTEAL -> R.string.phase_luteal_name
    EstimatedCyclePhase.UNKNOWN -> R.string.hero_phase_info
}

@Composable
fun PredictionMethodInfo(analysis: CycleAnalysis, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forecast_info)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                analysis.forecasts.firstOrNull()?.let {
                    Text(
                        if (analysis.usesDefaults) {
                            stringResource(R.string.default_forecast_basis, it.cycleMedian)
                        } else {
                            pluralStringResource(
                                R.plurals.cycles_used,
                                it.cyclesUsed,
                                it.cyclesUsed
                            )
                        }
                    )
                }
                Text(stringResource(R.string.prediction_method_info))
                Text(stringResource(R.string.hero_orbit_info))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
    )
}

@Composable
private fun compactForecastRange(start: LocalDate, end: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = if (locale.language == "ru") "d MMM" else "MMM d"
    val formatter = DateTimeFormatter.ofPattern(pattern, locale)
    val sameMonth = start.month == end.month && start.year == end.year
    return if (sameMonth && locale.language == "ru") {
        "${start.dayOfMonth}–${end.format(formatter)}"
    } else if (sameMonth) {
        "${start.format(formatter)}–${end.dayOfMonth}"
    } else {
        "${start.format(formatter)} — ${end.format(formatter)}"
    }
}

@Composable
fun ForecastDurationInsight(duration: Int) {
    Row(
        Modifier.fillMaxWidth().testTag("forecast-duration"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painterResource(R.drawable.ic_calendar),
            null,
            tint = MaterialTheme.colorScheme.secondary
        )
        Text(
            pluralStringResource(R.plurals.period_duration_days, duration, duration),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "Orbit light", widthDp = 360, showBackground = true)
@Preview(name = "Orbit dark", widthDp = 393, uiMode = 32, showBackground = true)
@Preview(name = "Orbit large text", widthDp = 360, fontScale = 2f, showBackground = true)
@Composable
private fun ForecastHeroPreview() {
    val today = LocalDate.of(2026, 10, 7)
    val start = today.minusDays(7)
    val records = listOf(Period(UUID(0, 1), start, start.plusDays(4), Instant.EPOCH, Instant.EPOCH))
    // Synthetic design fixture from the task; production always uses the supplied analysis.
    val basis = analyzeCycles(records, today)
    val sample = basis.copy(
        forecasts = listOf(
            basis.forecasts.first().copy(
                predictedStartDate = LocalDate.of(2026, 11, 2),
                lowerBound = LocalDate.of(2026, 10, 30),
                upperBound = LocalDate.of(2026, 11, 5),
                cycleMedian = 29
            )
        )
    )
    RitelaTheme {
        Box(Modifier.padding(16.dp)) { ForecastCard(sample, today, true) }
    }
}
