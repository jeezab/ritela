package app.ritela.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import app.ritela.R
import app.ritela.domain.PredictionDefaults

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    state: PeriodUiState,
    onSave: (PredictionDefaults) -> Unit
) {
    var cycle by rememberSaveable(state.defaults.cycleLength) {
        mutableIntStateOf(state.defaults.cycleLength)
    }
    var duration by rememberSaveable(state.defaults.periodDuration) {
        mutableIntStateOf(state.defaults.periodDuration)
    }
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(
            padding
        ).verticalScroll(rememberScrollState()).padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.large)
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.testTag("settings-heading")
        )
        Text(
            stringResource(R.string.defaults_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card {
            Column(
                Modifier.padding(Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                DayStepper(R.string.default_cycle_length, cycle, 1..365, "cycle", { cycle = it })
                DayStepper(R.string.default_period_duration, duration, 1..60, "duration", {
                    duration =
                        it
                })
                TextButton(onClick = {
                    cycle = 28
                    duration = 5
                }) { Text(stringResource(R.string.reset_defaults)) }
                Button(
                    onClick = { onSave(PredictionDefaults(cycle, duration)) },
                    enabled =
                        !state.saving &&
                            (
                                cycle != state.defaults.cycleLength ||
                                    duration != state.defaults.periodDuration
                                ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
        state.problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
        Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.language_description))
        TextButton(onClick = {
            val intent = if (Build.VERSION.SDK_INT >=
                33
            ) {
                Intent(
                    Settings.ACTION_APP_LOCALE_SETTINGS,
                    "package:${context.packageName}".toUri()
                )
            } else {
                Intent(Settings.ACTION_LOCALE_SETTINGS)
            }
            context.startActivity(intent)
        }) { Text(stringResource(R.string.choose_language)) }
        Text(
            stringResource(R.string.privacy_description),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun DayStepper(
    label: Int,
    value: Int,
    range: IntRange,
    tag: String,
    onChange: (Int) -> Unit
) {
    Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = {
            onChange(value - 1)
        }, enabled = value > range.first, modifier = Modifier.testTag("$tag-minus")) { Text("−") }
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("$tag-value")
        )
        TextButton(onClick = {
            onChange(value + 1)
        }, enabled = value < range.last, modifier = Modifier.testTag("$tag-plus")) { Text("+") }
    }
}
