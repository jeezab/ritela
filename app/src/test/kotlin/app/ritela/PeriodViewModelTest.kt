package app.ritela

import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.PeriodRepository
import app.ritela.data.RitelaDatabase
import app.ritela.domain.PeriodProblem
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

    @Test fun invalidDateShowsAnErrorWithoutWriting() = runBlocking {
        model.save(date, date.minusDays(1))
        val failed = model.uiState.first { it.problem != null }
        assertEquals(PeriodProblem.END_BEFORE_START, failed.problem)
        assertTrue(repository.periods.first().isEmpty())
    }
}
