package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.ritela.R
import app.ritela.domain.CycleAnalysis
import app.ritela.domain.ForecastUnavailable
import app.ritela.domain.HistoryConfidence

@Composable
fun ForecastCard(analysis: CycleAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.large),
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
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
                Text(
                    formattedDate(next.predictedStartDate),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    stringResource(
                        R.string.forecast_range,
                        formattedDate(next.lowerBound),
                        formattedDate(next.upperBound)
                    )
                )
                Text(
                    stringResource(
                        when (next.confidence) {
                            HistoryConfidence.HIGH -> R.string.history_stable
                            HistoryConfidence.MEDIUM -> R.string.history_variable
                            HistoryConfidence.LOW -> R.string.history_limited
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    pluralStringResource(R.plurals.cycles_used, next.cyclesUsed, next.cyclesUsed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.forecast_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
