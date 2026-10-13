package app.ritela.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import app.ritela.R
import app.ritela.domain.CalendarDayInfo
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Common calendar display with dates supplied by the caller. */
@Composable
internal fun CalendarMonthFeed(
    base: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    list: androidx.compose.foundation.lazy.LazyListState,
    onDay: (LocalDate) -> Unit,
    onHistory: () -> Unit,
    info: (LocalDate) -> CalendarDayInfo,
    modifier: Modifier = Modifier,
    hasLog: (LocalDate) -> Boolean = { false },
    hasSex: (LocalDate) -> Boolean = { false },
    filledSex: (LocalDate) -> Boolean = { false },
    logIcon: (LocalDate) -> Int = { R.drawable.ic_note },
    dayTag: String = "calendar-day",
    listTag: String = "month-grid",
    firstMonth: YearMonth = base.minusYears(100),
    lastMonth: YearMonth = base.plusYears(100),
    summary: @Composable () -> Unit
) {
    val center = java.time.temporal.ChronoUnit.MONTHS.between(firstMonth, base).toInt()
    val count = java.time.temporal.ChronoUnit.MONTHS.between(firstMonth, lastMonth).toInt() + 1
    val scope = rememberCoroutineScope()
    var choosingMonth by rememberSaveable { mutableStateOf(false) }
    val visibleIndex by remember { derivedStateOf { list.firstVisibleItemIndex } }
    val month = firstMonth.plusMonths(visibleIndex.toLong())
    val cellHeight = maxOf(56.dp, (40 * LocalDensity.current.fontScale).dp)
    fun showMonth(index: Int) {
        scope.launch { list.scrollToItem(index.coerceIn(0, count - 1)) }
    }
    Column(
        modifier.fillMaxSize().background(HomeColors.background)
            .padding(horizontal = HomeSpacing.gutter)
    ) {
        TextButton(
            onClick = { choosingMonth = true },
            modifier = Modifier.fillMaxWidth().testTag("month-selector")
        ) {
            Text(
                month.format(
                    DateTimeFormatter.ofPattern("LLLL yyyy", LocalConfiguration.current.locales[0])
                ),
                style = MaterialTheme.typography.titleLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag(listTag)
        ) {
            items(count, key = { it }) { index ->
                val displayed = firstMonth.plusMonths(index.toLong())
                Column(Modifier.testTag("calendar-month-$displayed")) {
                    Text(
                        displayed.format(
                            DateTimeFormatter.ofPattern(
                                "LLLL yyyy",
                                LocalConfiguration.current.locales[0]
                            )
                        ),
                        Modifier.padding(
                            horizontal = Spacing.medium,
                            vertical = Spacing.medium
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                    MonthGrid(
                        displayed,
                        today,
                        selected,
                        modifier = Modifier.border(
                            1.dp,
                            HomeColors.border.copy(alpha = 0.6f),
                            MaterialTheme.shapes.large
                        )
                            .background(
                                HomeColors.card.copy(alpha = 0.2f),
                                MaterialTheme.shapes.large
                            )
                            .padding(vertical = 8.dp),
                        cellHeight = cellHeight,
                        atmospheric = true,
                        info = info,
                        hasLog = hasLog,
                        hasSex = hasSex,
                        filledSex = filledSex,
                        logIcon = logIcon,
                        tagPrefix = dayTag,
                        onDay = onDay
                    )
                    if (index == center) {
                        summary()
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                showMonth(center)
            }, modifier = Modifier.weight(1f).testTag("calendar-today")) {
                if (LocalDensity.current.fontScale > 1.3f) {
                    Icon(painterResource(R.drawable.ic_today), stringResource(R.string.home_title))
                } else {
                    Text(stringResource(R.string.home_title))
                }
            }
            TextButton(onClick = {
                onHistory()
            }, modifier = Modifier.weight(1f).testTag("calendar-history")) {
                if (LocalDensity.current.fontScale > 1.3f) {
                    Icon(
                        painterResource(R.drawable.ic_chart),
                        stringResource(R.string.history_short)
                    )
                } else {
                    Text(stringResource(R.string.history_short))
                }
            }
        }
    }
    if (choosingMonth) {
        MonthYearPicker(month, firstMonth.year..lastMonth.year, { choosingMonth = false }) {
            choosingMonth = false
            showMonth(java.time.temporal.ChronoUnit.MONTHS.between(firstMonth, it).toInt())
        }
    }
}
