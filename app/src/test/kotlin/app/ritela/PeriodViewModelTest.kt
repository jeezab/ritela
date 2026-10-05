package app.ritela

import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.PeriodRepository
import app.ritela.data.RitelaDatabase
import app.ritela.data.SettingsRepository
import app.ritela.domain.PeriodProblem
import app.ritela.domain.PredictionDefaults
import app.ritela.ui.PeriodViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PeriodViewModelTest {
    private lateinit var database: RitelaDatabase
    private lateinit var repository: PeriodRepository
    private lateinit var model: PeriodViewModel
    private val date = LocalDate.of(2024, 2, 29)

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                RitelaDatabase::class.java
            ).build()
        repository =
            PeriodRepository(
                database.periods(),
                Clock.fixed(Instant.parse("2024-03-01T12:00:00Z"), ZoneOffset.UTC)
            )
        model = PeriodViewModel(repository)
    }

    @After fun teardown() {
        model.viewModelScope.cancel()
        database.close()
        Dispatchers.resetMain()
    }

    @Test fun duplicateTapSavesOnceAndOverlapKeepsExistingData() = runBlocking {
        model.save(date, null)
        model.save(date, null)
        val saved = model.uiState.first { it.saved }
        assertFalse(saved.saving)
        assertEquals(1, repository.periods.first().size)
        model.clearResult()
        assertFalse(model.uiState.value.saved)
        model.save(date, null)
        val failed = model.uiState.first { it.problem != null }
        assertEquals(PeriodProblem.OVERLAP, failed.problem)
        assertFalse(failed.saving)
        assertFalse(failed.saved)
        assertEquals(1, repository.periods.first().size)
    }

    @Test fun editingAndDeletingRecalculateForecastFromRoom() = runBlocking {
        val latest = LocalDate.of(2024, 2, 20)
        for (index in 0..3) {
            val start = latest.minusDays(28L * index)
            repository.add(start, start.plusDays(3))
        }
        val initial = withTimeout(5_000) { model.uiState.first { it.periods.size == 4 } }
        assertEquals(latest.plusDays(28), initial.analysis.forecasts.first().predictedStartDate)
        val record = initial.periods.first()
        model.edit(record.id, latest.plusDays(2), latest.plusDays(5))
        val edited = withTimeout(5_000) {
            model.uiState.first { it.periods.first().start == latest.plusDays(2) }
        }
        assertEquals(record.id, edited.periods.first().id)
        assertEquals(latest.plusDays(30), edited.analysis.forecasts.first().predictedStartDate)
        model.clearResult()
        model.delete(edited.periods.last().id)
        val deleted = withTimeout(5_000) { model.uiState.first { it.periods.size == 3 } }
        assertTrue(deleted.analysis.usesDefaults)
        assertEquals(28, deleted.analysis.forecasts.first().cycleMedian)
    }

    @Test fun settingsSurviveReopeningAndRecalculateWithoutChangingPeriods() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences(
            "test-settings",
            android.content.Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val settings = SettingsRepository(preferences)
        settings.save(PredictionDefaults(30, 7))
        assertEquals(PredictionDefaults(30, 7), SettingsRepository(preferences).values.value)
        model.viewModelScope.cancel()
        model = PeriodViewModel(repository, settings)
        repository.add(date, null)
        val initial = withTimeout(5_000) { model.uiState.first { it.periods.size == 1 } }
        val record = initial.periods.single()
        assertEquals(date.plusDays(30), initial.analysis.forecasts.first().predictedStartDate)
        model.updateDefaults(PredictionDefaults(28, 5))
        val updated = withTimeout(5_000) { model.uiState.first { it.defaults.cycleLength == 28 } }
        assertEquals(date.plusDays(28), updated.analysis.forecasts.first().predictedStartDate)
        assertEquals(5, updated.analysis.periodDuration)
        assertEquals(record, updated.periods.single())
        assertEquals(PredictionDefaults(), SettingsRepository(preferences).values.value)
    }

    @Test fun invalidDateShowsAnErrorWithoutWriting() = runBlocking {
        model.save(date, date.minusDays(1))
        val failed = model.uiState.first { it.problem != null }
        assertEquals(PeriodProblem.END_BEFORE_START, failed.problem)
        assertTrue(repository.periods.first().isEmpty())
    }
}
