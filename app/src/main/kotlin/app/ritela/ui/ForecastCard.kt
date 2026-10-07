package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
    val next = analysis.forecasts.firstOrNull()
    val scene = cycleSceneState(analysis, today)
    val colors = MaterialTheme.colorScheme
    val heroSurface = androidx.compose.ui.graphics.lerp(
        colors.surfaceContainerLow,
        colors.secondaryContainer,
        0.18f
    )
    val locale = LocalConfiguration.current.locales[0]
    val fontScale = LocalDensity.current.fontScale
    Card(
        modifier = Modifier.fillMaxWidth().testTag("forecast-hero"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = heroSurface)
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    analysis.cycleDay?.let {
                        Text(
                            stringResource(R.string.hero_cycle_day, it),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (scene.phase != EstimatedCyclePhase.UNKNOWN) {
                        Text(
                            stringResource(phaseLabel(scene.phase)),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { showInfo = true }, Modifier.testTag("forecast-info")) {
                    Icon(
                        painterResource(R.drawable.ic_info),
                        stringResource(R.string.forecast_info)
                    )
                }
            }
            if (next != null && fontScale <= 1.5f) {
                CycleOrbitScene(
                    analysis.cycleDay ?: 1,
                    scene.progress,
                    reducedMotion,
                    OrbitColors(
                        colors.primary,
                        colors.secondary,
                        if (colors.surface.luminance() <
                            0.4f
                        ) {
                            Color(0xFFBA9077)
                        } else {
                            Color(0xFFF3D4BA)
                        },
                        heroSurface
                    ),
                    Modifier.fillMaxWidth().height(if (fontScale > 1.2f) 124.dp else 156.dp)
                )
            }
            Text(
                stringResource(R.string.forecast_title),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant
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
                    color = colors.onSurfaceVariant
                )
            } else {
                val pattern = if (locale.language == "ru") "d MMMM" else "MMM d"
                Text(
                    next.predictedStartDate.format(DateTimeFormatter.ofPattern(pattern, locale)),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp),
                    color = colors.secondary,
                    modifier = Modifier.testTag("forecast-date")
                )
                Text(
                    compactForecastRange(next.lowerBound, next.upperBound),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.testTag("forecast-window")
                )
                Surface(
                    color = colors.secondaryContainer.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        when (
                            val days = ChronoUnit.DAYS.between(
                                today,
                                next.predictedStartDate
                            ).toInt()
                        ) {
                            0 -> stringResource(R.string.period_expected_today)

                            in 1..Int.MAX_VALUE -> pluralStringResource(
                                R.plurals.period_countdown,
                                days,
                                days
                            )

                            else -> pluralStringResource(
                                R.plurals.period_expected_ago,
                                -days,
                                -days
                            )
                        },
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(stringResource(R.string.forecast_info)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    next?.let {
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
                        Text(
                            stringResource(
                                R.string.forecast_range,
                                formattedDate(it.lowerBound),
                                formattedDate(it.upperBound)
                            )
                        )
                    }
                    Text(stringResource(R.string.hero_phase_info))
                    Text(stringResource(R.string.hero_orbit_info))
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.done)) }
            }
        )
    }
}

private fun phaseLabel(phase: EstimatedCyclePhase): Int = when (phase) {
    EstimatedCyclePhase.EARLY -> R.string.phase_early_estimate
    EstimatedCyclePhase.FOLLICULAR -> R.string.phase_follicular_estimate
    EstimatedCyclePhase.OVULATION -> R.string.phase_ovulation_estimate
    EstimatedCyclePhase.LUTEAL -> R.string.phase_luteal_estimate
    EstimatedCyclePhase.UNKNOWN -> R.string.hero_phase_info
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
