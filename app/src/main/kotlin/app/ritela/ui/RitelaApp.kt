package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ritela.R
import app.ritela.domain.Period
import java.time.LocalDate
import java.util.UUID

@Composable
fun RitelaApp(model: PeriodViewModel = viewModel(factory = PeriodViewModel.Factory)) {
    val state by model.uiState.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var finishingId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            adding = false
            finishingId = null
            model.clearResult()
        }
    }
    RitelaTheme {
        Scaffold { contentPadding ->
            HomeScreen(contentPadding, state, onAdd = {
                model.clearResult()
                adding = true
            }, onFinish = {
                model.clearResult()
                finishingId =
                    it.id.toString()
            })
        }
        if (adding) {
            PeriodEntry(state, onDismiss = {
                adding = false
                model.clearResult()
            }, onSave = model::save)
        }
        finishingId?.let { id ->
            PickDate(LocalDate.now(), onDismiss = { finishingId = null }, onChoose = {
                model.finish(UUID.fromString(id), it)
                finishingId =
                    null
            })
        }
    }
}

@Composable
fun HomeScreen(
    contentPadding: PaddingValues = PaddingValues(),
    state: PeriodUiState = PeriodUiState(loading = false),
    onAdd: () -> Unit = {},
    onFinish: (Period) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.section)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                stringResource(R.string.home_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            Text(stringResource(R.string.cycle_title), style = MaterialTheme.typography.titleLarge)
            Button(onClick = onAdd, enabled = !state.loading && !state.saving) {
                Text(stringResource(R.string.add_period))
            }
            state.problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
            if (state.loading) {
                Text(stringResource(R.string.loading))
            } else if (state.periods.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(Spacing.large),
                        verticalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        Text(
                            stringResource(R.string.empty_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.empty_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            for (period in state.periods) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(Spacing.large),
                        verticalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        Text(
                            stringResource(R.string.start_date, formattedDate(period.start)),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            period.end?.let {
                                stringResource(R.string.end_date, formattedDate(it))
                            }
                                ?: stringResource(R.string.ongoing)
                        )
                        if (period.end ==
                            null
                        ) {
                            TextButton(onClick = {
                                onFinish(period)
                            }, enabled = !state.saving) {
                                Text(stringResource(R.string.finish_period))
                            }
                        }
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(Spacing.large),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                Text(
                    stringResource(R.string.privacy_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(stringResource(R.string.privacy_description))
                Text(
                    stringResource(R.string.privacy_note),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    RitelaTheme(dynamicColor = false) { HomeScreen() }
}
