package app.ritela.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.ritela.domain.Period
import app.ritela.domain.analyzeCycles
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private fun previewState(): PeriodUiState {
    val today = LocalDate.of(2026, 10, 8)
    val start = today.minusDays(8)
    val records = (0L..3L).map { index ->
        val date = start.minusDays(index * 30)
        Period(UUID(0, index + 1), date, date.plusDays(4), Instant.EPOCH, Instant.EPOCH)
    }
    return PeriodUiState(
        loading = false,
        periods = records,
        today = today,
        analysis = analyzeCycles(records, today)
    )
}

@Preview(name = "Home 360", widthDp = 360, heightDp = 800, locale = "ru")
@Preview(name = "Home narrow", widthDp = 320, heightDp = 740, locale = "en")
@Preview(name = "Home dark", widthDp = 412, heightDp = 891, uiMode = 32, locale = "ru")
@Preview(name = "Home large text", widthDp = 360, heightDp = 800, fontScale = 2f, locale = "ru")
@Composable
private fun PolishedHomePreview() {
    val state = previewState()
    RitelaTheme(darkTheme = isSystemInDarkTheme()) {
        Scaffold { HomeScreen(it, state, today = state.today) }
    }
}

@Preview(name = "Calendar 360", widthDp = 360, heightDp = 800, locale = "ru")
@Preview(name = "Calendar narrow", widthDp = 320, heightDp = 740, locale = "en")
@Preview(name = "Calendar dark", widthDp = 412, heightDp = 891, uiMode = 32, locale = "ru")
@Preview(name = "Calendar large text", widthDp = 360, heightDp = 800, fontScale = 2f, locale = "ru")
@Composable
private fun PolishedCalendarPreview() {
    RitelaTheme(darkTheme = isSystemInDarkTheme()) {
        Scaffold { CalendarScreen(it, previewState(), {}, {}, {}) }
    }
}
