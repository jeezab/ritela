package app.ritela.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.ritela.R
import app.ritela.domain.CycleAnalysis
import app.ritela.domain.Period
import app.ritela.domain.PeriodProblem
import app.ritela.domain.PeriodRange
import app.ritela.domain.PeriodRangeSelection
import app.ritela.domain.calendarDay
import app.ritela.domain.periodConflict
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun formattedDate(date: LocalDate): String = CalendarDayFormatter(
    androidx.compose.ui.platform.LocalResources.current,
    LocalConfiguration.current.locales[0]
).date(date)

@Composable
fun problemText(problem: PeriodProblem): String = stringResource(
    when (problem) {
        PeriodProblem.FUTURE_DATE -> R.string.future_date_error
        PeriodProblem.END_BEFORE_START -> R.string.date_order_error
        PeriodProblem.OVERLAP -> R.string.overlap_error
        PeriodProblem.STORAGE -> R.string.storage_error
    }
)

@Composable
fun PeriodEntry(
    state: PeriodUiState,
    onDismiss: () -> Unit,
    onSave: (LocalDate, LocalDate?) -> Unit,
    initialStart: LocalDate = LocalDate.now(),
    initialEnd: LocalDate? = null,
    editing: Boolean = false,
    editingId: java.util.UUID? = null,
    onDelete: (() -> Unit)? = null,
    onChange: () -> Unit = {},
    startEmpty: Boolean = false,
    onSaveBatch: ((List<PeriodRange>) -> Unit)? = null
) {
    HomeTheme {
        PeriodEntryContent(
            state, onDismiss, onSave, initialStart, initialEnd, editing,
            editingId, onDelete, onChange, startEmpty, onSaveBatch
        )
    }
}

@Composable
private fun PeriodEntryContent(
    state: PeriodUiState,
    onDismiss: () -> Unit,
    onSave: (LocalDate, LocalDate?) -> Unit,
    initialStart: LocalDate,
    initialEnd: LocalDate?,
    editing: Boolean,
    editingId: java.util.UUID?,
    onDelete: (() -> Unit)?,
    onChange: () -> Unit,
    startEmpty: Boolean,
    onSaveBatch: ((List<PeriodRange>) -> Unit)?
) {
    var startDay by rememberSaveable { mutableStateOf(initialStart.toEpochDay()) }
    var endDay by rememberSaveable {
        mutableStateOf(
            if (editing) initialEnd?.toEpochDay() else (initialEnd ?: initialStart).toEpochDay()
        )
    }
    var anchorDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var hasSelection by rememberSaveable { mutableStateOf(!startEmpty) }
    var choosingMonth by rememberSaveable { mutableStateOf(false) }
    val selection = PeriodRangeSelection(
        LocalDate.ofEpochDay(startDay),
        endDay?.let(LocalDate::ofEpochDay),
        anchorDay?.let(LocalDate::ofEpochDay)
    )
    var pendingDays by rememberSaveable { mutableStateOf(longArrayOf()) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val batchMode = !editing && onSaveBatch != null
    val pending = remember(pendingDays) {
        pendingDays.toList().chunked(2).map { (start, end) ->
            PeriodRange(
                LocalDate.ofEpochDay(start),
                if (end ==
                    Long.MAX_VALUE
                ) {
                    null
                } else {
                    LocalDate.ofEpochDay(end)
                }
            )
        }
    }
    val pendingRecords = remember(pending) {
        pending.map {
            Period(UUID(0, it.start.toEpochDay()), it.start, it.end, Instant.EPOCH, Instant.EPOCH)
        }
    }
    val others = state.periods.filter { it.id != editingId } + pendingRecords
    val closedValid =
        hasSelection && selection.end != null && selection.canSave(others, state.today)
    val ongoingValid = hasSelection && selection.canKeepOngoing(others, state.today)
    val conflict = if (hasSelection) {
        periodConflict(
            others,
            selection.start,
            selection.end
        )
    } else {
        null
    }
    val firstMonth = YearMonth.of(1900, 1)
    val monthCount = (state.today.year - 1900) * 12 + state.today.monthValue
    fun indexOf(month: YearMonth): Int = ((month.year - 1900) * 12 + month.monthValue - 1)
        .coerceIn(0, monthCount - 1)
    val list =
        rememberLazyListState(initialFirstVisibleItemIndex = indexOf(YearMonth.from(initialStart)))
    val visibleIndex by remember { derivedStateOf { list.firstVisibleItemIndex } }
    val month = firstMonth.plusMonths(visibleIndex.toLong())
    val scope = rememberCoroutineScope()
    fun showMonth(value: YearMonth) {
        scope.launch { list.scrollToItem(indexOf(value)) }
    }
    fun setSelection(value: PeriodRangeSelection) {
        startDay = value.start.toEpochDay()
        endDay = value.end?.toEpochDay()
        anchorDay = value.anchor?.toEpochDay()
        hasSelection = true
        dirty = true
        onChange()
    }
    fun requestDismiss() {
        if (state.saving) return
        if (batchMode && (dirty || pending.isNotEmpty())) confirmDiscard = true else onDismiss()
    }
    fun markRange(end: LocalDate?) {
        pendingDays =
            pendingDays +
            longArrayOf(selection.start.toEpochDay(), end?.toEpochDay() ?: Long.MAX_VALUE)
        hasSelection = false
        anchorDay = null
        dirty = true
        onChange()
    }
    fun saveRanges() {
        if (batchMode) {
            val ranges =
                pending +
                    if (hasSelection) {
                        listOf(
                            PeriodRange(selection.start, selection.end)
                        )
                    } else {
                        emptyList()
                    }
            onSaveBatch?.invoke(ranges)
        } else {
            onSave(selection.start, selection.end)
        }
    }
    val cellHeight = maxOf(56.dp, (40 * LocalDensity.current.fontScale).dp)
    val count = ((selection.end ?: selection.start).toEpochDay() - selection.start.toEpochDay() + 1)
        .coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
    JournalDialog(
        onDismissRequest = ::requestDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = HomeColors.bottom,
            contentColor = HomeColors.text
        ) {
            Column(
                Modifier.fillMaxSize().background(HomeColors.background).windowInsetsPadding(
                    WindowInsets.safeDrawing.union(WindowInsets.ime)
                )
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (editing) R.string.edit_period else R.string.new_period),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(
                        ::requestDismiss,
                        enabled = !state.saving,
                        modifier = Modifier.testTag("entry-cancel")
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                Text(
                    stringResource(R.string.range_entry_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeColors.muted
                )
                if (batchMode) {
                    Text(
                        if (hasSelection) {
                            formattedDate(selection.start) + " – " +
                                formattedDate(selection.end ?: selection.start)
                        } else {
                            stringResource(R.string.entry_choose_days)
                        },
                        minLines = 2,
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeColors.peach
                    )
                } else if (hasSelection) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            {
                                anchorDay = (selection.end ?: selection.start).toEpochDay()
                                showMonth(YearMonth.from(selection.start))
                            },
                            enabled = !state.saving,
                            modifier = Modifier.weight(
                                1f
                            ).testTag("entry-start")
                        ) {
                            Text(
                                stringResource(R.string.start_date, formattedDate(selection.start))
                            )
                        }
                        TextButton(
                            {
                                anchorDay = selection.start.toEpochDay()
                                showMonth(YearMonth.from(selection.end ?: selection.start))
                            },
                            enabled = !state.saving,
                            modifier = Modifier.weight(
                                1f
                            ).testTag("entry-end")
                        ) {
                            Text(
                                selection.end?.let {
                                    stringResource(R.string.end_date, formattedDate(it))
                                }
                                    ?: stringResource(R.string.ongoing)
                            )
                        }
                    }
                } else {
                    Text(stringResource(R.string.entry_choose_days), color = HomeColors.peach)
                }
                TextButton({
                    choosingMonth = true
                }, modifier = Modifier.fillMaxWidth().testTag("month-selector")) {
                    Text(
                        month.format(
                            DateTimeFormatter.ofPattern(
                                "LLLL yyyy",
                                LocalConfiguration.current.locales[0]
                            )
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                LazyColumn(
                    state = list,
                    modifier = Modifier.weight(1f).testTag("entry-month-grid")
                ) {
                    items(monthCount, key = { it }) { index ->
                        val displayed = firstMonth.plusMonths(index.toLong())
                        Column {
                            Text(
                                displayed.format(
                                    DateTimeFormatter.ofPattern(
                                        "LLLL yyyy",
                                        LocalConfiguration.current.locales[0]
                                    )
                                ),
                                Modifier.padding(vertical = 12.dp),
                                style = MaterialTheme.typography.titleMedium
                            )
                            MonthGrid(
                                displayed, state.today,
                                if (hasSelection) selection.start else null,
                                atmospheric = true,
                                cellHeight = cellHeight,
                                tagPrefix = "entry-day",
                                rangeStart = if (hasSelection) selection.start else null,
                                rangeEnd = if (hasSelection) selection.end else null,
                                futureEnabled = false,
                                info = { calendarDay(others, CycleAnalysis(), it, state.today) },
                                dayEnabled = {
                                    !state.saving &&
                                        selection.canPick(others, it, state.today)
                                },
                                onDay = { setSelection(selection.pick(it)) }
                            )
                        }
                    }
                }
                if (conflict !=
                    null
                ) {
                    Text(
                        stringResource(R.string.overlap_error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                state.problem?.let {
                    Text(problemText(it), color = MaterialTheme.colorScheme.error)
                }
                onDelete?.let { action ->
                    TextButton(action, enabled = !state.saving) {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        { if (batchMode) markRange(null) else onSave(selection.start, null) },
                        enabled = !state.saving && ongoingValid,
                        modifier = Modifier.weight(
                            1f
                        ).fillMaxHeight().heightIn(min = 56.dp).testTag("entry-ongoing"),
                        contentPadding = PaddingValues(6.dp)
                    ) {
                        Text(stringResource(R.string.clear_end), textAlign = TextAlign.Center)
                    }
                    if (batchMode) {
                        OutlinedButton(
                            { markRange(requireNotNull(selection.end)) },
                            enabled = !state.saving && closedValid,
                            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 56.dp)
                                .testTag("entry-save-more"),
                            contentPadding = PaddingValues(6.dp)
                        ) {
                            Text(
                                pluralStringResource(R.plurals.quick_duration, count, count),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Button(
                        ::saveRanges,
                        enabled = !state.saving &&
                            (closedValid || (batchMode && !hasSelection && pending.isNotEmpty())),
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 56.dp)
                            .testTag("entry-save"),
                        contentPadding = PaddingValues(6.dp)
                    ) {
                        Text(
                            stringResource(if (state.saving) R.string.saving else R.string.save),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            modifier = Modifier.testTag("entry-discard-dialog"),
            title = { Text(stringResource(R.string.entry_discard_title)) },
            text = { Text(stringResource(R.string.entry_discard_message)) },
            confirmButton = {
                TextButton({
                    confirmDiscard = false
                    onDismiss()
                }, modifier = Modifier.testTag("entry-discard-continue")) {
                    Text(stringResource(R.string.entry_discard_continue))
                }
            },
            dismissButton = {
                TextButton({
                    confirmDiscard = false
                }, modifier = Modifier.testTag("entry-discard-cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (choosingMonth) {
        MonthYearPicker(month, 1900..state.today.year, { choosingMonth = false }) {
            choosingMonth = false
            showMonth(it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickDate(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onChoose: (LocalDate) -> Unit,
    minimum: LocalDate? = null,
    maximum: LocalDate = LocalDate.now(),
    selectable: (LocalDate) -> Boolean = { true }
) {
    // Material's picker encodes calendar dates as UTC midnight; storage uses epoch days.
    val picker = rememberDatePickerState(
        initialSelectedDateMillis =
            initial.toEpochDay() * 86_400_000L,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = LocalDate.ofEpochDay(utcTimeMillis / 86_400_000L)
                return date <= maximum && (minimum == null || date >= minimum) && selectable(date)
            }

            override fun isSelectableYear(year: Int): Boolean =
                year <= maximum.year && (minimum == null || year >= minimum.year)
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { onChoose(LocalDate.ofEpochDay(it / 86_400_000L)) }
            }, enabled = picker.selectedDateMillis != null) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    ) { DatePicker(state = picker) }
}
