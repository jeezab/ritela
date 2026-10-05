package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ritela.R
import app.ritela.domain.Period
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    onFinish: (Period) -> Unit = {},
    today: LocalDate = LocalDate.now()
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
                today.format(
                    DateTimeFormatter.ofPattern(
                        "d MMMM, EEEE",
                        LocalConfiguration.current.locales[0]
                    )
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val active = state.periods.firstOrNull { it.end == null }
        val latest = active ?: state.periods.firstOrNull()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(Spacing.large),
                verticalArrangement = Arrangement.spacedBy(Spacing.large)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    Text(
                        stringResource(
                            when {
                                state.loading -> R.string.loading
                                active != null -> R.string.active_title
                                latest != null -> R.string.latest_title
                                else -> R.string.empty_title
                            }
                        ),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    if (!state.loading) {
                        Text(
                            latest?.let { formattedDate(it.start) }
                                ?: stringResource(R.string.empty_description),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = { if (active != null) onFinish(active) else onAdd() },
                    enabled = !state.loading && !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.actionHeight)
                ) {
                    Text(
                        stringResource(
                            if (active !=
                                null
                            ) {
                                R.string.finish_period
                            } else {
                                R.string.add_period
                            }
                        )
                    )
                }
            }
        }
        state.problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                Text(
                    stringResource(R.string.cycle_title),
                    style = MaterialTheme.typography.titleLarge
                )
                if (active != null) {
                    TextButton(onClick = onAdd, enabled = !state.saving) {
                        Text(stringResource(R.string.add_period))
                    }
                }
            }
            if (!state.loading && state.periods.isEmpty()) {
                Text(
                    stringResource(R.string.empty_history),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            for (period in state.periods) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    Text(formattedDate(period.start), style = MaterialTheme.typography.titleMedium)
                    Text(
                        period.end?.let { stringResource(R.string.end_date, formattedDate(it)) }
                            ?: stringResource(R.string.ongoing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Text(
            stringResource(R.string.privacy_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(
    name = "Empty · light",
    showBackground = true,
    locale = "ru",
    widthDp = 411,
    heightDp = 891
)
@Preview(
    name = "Empty · narrow / large text",
    showBackground = true,
    locale = "ru",
    widthDp = 320,
    heightDp = 740,
    fontScale = 2f
)
@Composable
private fun HomePreview() {
    RitelaTheme(dynamicColor = false) { Scaffold { HomeScreen(it) } }
}

@Preview(
    name = "Recorded · dark",
    showBackground = true,
    locale = "ru",
    widthDp = 411,
    heightDp = 891
)
@Composable
private fun RecordedHomePreview() {
    val today = LocalDate.of(2026, 10, 5)
    val period =
        Period(UUID(0, 1), today.minusDays(30), today.minusDays(26), Instant.EPOCH, Instant.EPOCH)
    RitelaTheme(darkTheme = true, dynamicColor = false) {
        Scaffold {
            HomeScreen(it, PeriodUiState(loading = false, periods = listOf(period)), today = today)
        }
    }
}
