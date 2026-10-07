package app.ritela.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ritela.R
import app.ritela.data.ThemeMode
import app.ritela.domain.DayLog
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
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var loggingDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var page by rememberSaveable { mutableStateOf(0) }
    var addingDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refreshToday() }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            loggingDay = null
            adding = false
            finishingId = null
            editingId = null
            deletingId = null
            model.clearResult()
        }
    }
    val darkTheme = when (state.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    RitelaTheme(darkTheme = darkTheme) {
        Scaffold(bottomBar = { AppNavigation(page) { page = it } }) { contentPadding ->
            if (page == 2) {
                SettingsScreen(contentPadding, state, model::setTheme) { duration ->
                    model.updateDefaults(state.defaults.copy(periodDuration = duration))
                }
            } else if (page == 1) {
                CalendarScreen(
                    contentPadding,
                    state,
                    onAdd = {
                        addingDay = it.toEpochDay()
                        model.clearResult()
                        adding = true
                    },
                    onEdit = {
                        editingId = it.id.toString()
                        model.clearResult()
                    },
                    onDelete = {
                        deletingId = it.id.toString()
                        model.clearResult()
                    },
                    onLogDay = {
                        model.clearResult()
                        loggingDay = it.toEpochDay()
                    }
                )
            } else {
                HomeScreen(
                    contentPadding,
                    state,
                    onAdd = {
                        model.clearResult()
                        addingDay = state.today.toEpochDay()
                        adding = true
                    },
                    onFinish = {
                        model.clearResult()
                        finishingId =
                            it.id.toString()
                    },
                    onEdit = {
                        editingId = it.id.toString()
                        model.clearResult()
                    },
                    onLogDay = {
                        model.clearResult()
                        loggingDay = state.today.toEpochDay()
                    },
                    today = state.today
                )
            }
        }
        loggingDay?.let { epoch ->
            val date = LocalDate.ofEpochDay(epoch)
            DayLogEntry(
                state.dayLogs.firstOrNull { it.date == date } ?: DayLog(date),
                state,
                onDismiss = {
                    loggingDay = null
                    model.clearResult()
                },
                onSave = model::saveDay,
                onLayoutChange = model::saveJournalLayout
            )
        }
        state.periods.firstOrNull { it.id.toString() == editingId }?.let { period ->
            PeriodEntry(
                state,
                onDismiss = {
                    editingId = null
                    model.clearResult()
                },
                onSave = { start, end -> model.edit(period.id, start, end) },
                initialStart = period.start,
                initialEnd = period.end,
                editing = true,
                editingId = period.id,
                onDelete = {
                    editingId = null
                    deletingId = period.id.toString()
                    model.clearResult()
                },
                onChange = model::clearResult
            )
        }
        state.periods.firstOrNull { it.id.toString() == deletingId }?.let { period ->
            AlertDialog(
                onDismissRequest = {
                    if (!state.saving) {
                        deletingId = null
                        model.clearResult()
                    }
                },
                title = { Text(stringResource(R.string.delete_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                        Text(periodDates(period))
                        Text(stringResource(R.string.delete_description))
                        state.problem?.let {
                            Text(problemText(it), color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { model.delete(period.id) }, enabled = !state.saving) {
                        Text(
                            stringResource(R.string.confirm_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        deletingId = null
                        model.clearResult()
                    }, enabled = !state.saving) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        if (adding) {
            PeriodEntry(
                state,
                onDismiss = {
                    adding = false
                    model.clearResult()
                },
                onSave = model::save,
                initialStart = LocalDate.ofEpochDay(
                    addingDay
                ),
                onChange = model::clearResult
            )
        }
        finishingId?.let { id ->
            PickDate(
                LocalDate.now(),
                minimum = state.periods.firstOrNull {
                    it.id.toString() == id
                }?.start,
                onDismiss = {
                    finishingId =
                        null
                },
                onChoose = {
                    model.finish(UUID.fromString(id), it)
                    finishingId =
                        null
                }
            )
        }
    }
}

@Composable
fun HomeScreen(
    contentPadding: PaddingValues = PaddingValues(),
    state: PeriodUiState = PeriodUiState(loading = false),
    onAdd: () -> Unit = {},
    onFinish: (Period) -> Unit = {},
    onEdit: (Period) -> Unit = {},
    onLogDay: () -> Unit = {},
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
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                today.format(
                    DateTimeFormatter.ofPattern("EEEE, d MMMM")
                        .withLocale(LocalConfiguration.current.locales[0])
                ),
                style = MaterialTheme.typography.headlineSmall
            )
        }
        val active = state.periods.firstOrNull { it.end == null }
        val latest = active ?: state.periods.firstOrNull()
        val hasForecast = !state.loading && active == null && state.analysis.forecasts.isNotEmpty()
        if (hasForecast) ForecastCard(state.analysis, state.today)
        if (!hasForecast) {
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
                        state.analysis.cycleDay?.let {
                            CycleDayBadge(it)
                        }
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
                            if (active != null) Text(stringResource(R.string.ongoing))
                            Text(
                                latest?.let { periodDates(it) }
                                    ?: stringResource(R.string.empty_description),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        if (active == null) {
            Button(
                onClick = onAdd,
                enabled = !state.loading && !state.saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.actionHeight)
            ) {
                Text(stringResource(R.string.add_period))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                TextButton(onClick = { onEdit(active) }, enabled = !state.saving) {
                    Text(stringResource(R.string.edit))
                }
                Button(onClick = { onFinish(active) }, enabled = !state.saving) {
                    Text(stringResource(R.string.finish_period))
                }
            }
        }
        state.problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
        if (!state.loading && state.periods.isNotEmpty() &&
            active == null && !hasForecast
        ) {
            ForecastCard(state.analysis, state.today)
        }
        DaySummary(
            state.dayLogs.firstOrNull { it.date == today },
            onLogDay,
            !state.loading && !state.saving,
            layout = state.journalLayout
        )
        CycleInsights(state.periods, today, state.dayLogs, onLogDay)
        HelpCards(state.dayLogs.firstOrNull { it.date == today }, state.analysis, today)
        Text(
            stringResource(R.string.privacy_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AppNavigation(page: Int, onPage: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
        for (index in 0..2) {
            NavigationBarItem(
                selected = page == index,
                onClick = { onPage(index) },
                modifier = Modifier.testTag(
                    when (index) {
                        0 -> "nav-today"
                        1 -> "nav-calendar"
                        else -> "nav-settings"
                    }
                ),
                icon = {
                    Icon(
                        painterResource(
                            when (index) {
                                0 -> R.drawable.ic_today
                                1 -> R.drawable.ic_calendar
                                else -> R.drawable.ic_settings
                            }
                        ),
                        contentDescription = null
                    )
                },
                label = {
                    Text(
                        stringResource(
                            when (index) {
                                0 -> R.string.home_title
                                1 -> R.string.calendar_title
                                else -> R.string.settings_title
                            }
                        ),
                        fontSize = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >
                            1.3f
                        ) {
                            10.sp
                        } else {
                            12.sp
                        },
                        softWrap = false
                    )
                }
            )
        }
    }
}

@Composable
fun periodDates(period: Period): String = period.end?.let {
    stringResource(R.string.period_range, formattedDate(period.start), formattedDate(it))
} ?: formattedDate(period.start)

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
