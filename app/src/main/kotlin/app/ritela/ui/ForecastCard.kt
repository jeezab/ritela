package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ritela.R
import app.ritela.domain.CycleAnalysis
import app.ritela.domain.ForecastUnavailable
import app.ritela.domain.HistoryConfidence
import java.time.format.DateTimeFormatter

@Composable
fun ForecastCard(analysis: CycleAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.large),
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            analysis.cycleDay?.let { CycleDayBadge(it) }
            Text(
                stringResource(R.string.forecast_title),
                style = MaterialTheme.typography.titleMedium
            )
            val next = analysis.forecasts.firstOrNull()
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val locale = LocalConfiguration.current.locales[0]
                    val date = next.predictedStartDate.format(
                        DateTimeFormatter.ofPattern(
                            if (locale.language ==
                                "ru"
                            ) {
                                "d MMM"
                            } else {
                                "MMM d"
                            },
                            locale
                        )
                    )
                    val showOrbit = maxWidth >= 310.dp && LocalDensity.current.fontScale <= 1.3f
                    Row {
                        Text(
                            date,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        if (showOrbit) CycleOrbit(analysis.cycleDay ?: 1, next.cycleMedian)
                    }
                }
                Text(
                    stringResource(
                        R.string.forecast_range,
                        formattedDate(next.lowerBound),
                        formattedDate(next.upperBound)
                    )
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        stringResource(
                            when (next.confidence) {
                                HistoryConfidence.HIGH -> R.string.history_stable
                                HistoryConfidence.MEDIUM -> R.string.history_variable
                                HistoryConfidence.LOW -> R.string.history_limited
                            }
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(
                            horizontal = Spacing.medium,
                            vertical = Spacing.small
                        )
                    )
                }
                Text(
                    if (analysis.usesDefaults) {
                        stringResource(
                            R.string.default_forecast_basis,
                            next.cycleMedian
                        )
                    } else {
                        pluralStringResource(
                            R.plurals.cycles_used,
                            next.cyclesUsed,
                            next.cyclesUsed
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    pluralStringResource(
                        R.plurals.period_duration_days,
                        analysis.periodDuration,
                        analysis.periodDuration
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
