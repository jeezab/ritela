package app.ritela

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.PeriodRepository
import app.ritela.data.RitelaDatabase
import app.ritela.domain.PeriodProblem
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PeriodRepositoryTest {
    private lateinit var database: RitelaDatabase
    private lateinit var repository: PeriodRepository
    private val clock = Clock.fixed(
        Instant.parse("2024-03-10T12:00:00Z"),
        ZoneId.of("America/New_York")
    )

    @Before fun openDatabase() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                RitelaDatabase::class.java
            ).build()
        repository = PeriodRepository(database.periods(), clock)
    }

    @After fun closeDatabase() {
        database.close()
    }

    @Test fun preservesCalendarDateAcrossDstAndRejectsOverlap() = runBlocking {
        val start = LocalDate.of(2024, 3, 9)
        assertNull(repository.add(start, null))
        val record = repository.periods.first().single()
        assertEquals(start, record.start)
        assertEquals(clock.instant(), record.createdAt)
        assertNotNull(record.id)
        assertEquals(PeriodProblem.OVERLAP, repository.add(start.plusDays(1), null))
        assertEquals(
            PeriodProblem.END_BEFORE_START,
            repository.finish(record.id, start.minusDays(1))
        )
        assertNull(repository.finish(record.id, start.plusDays(1)))
        val finished = repository.periods.first().single()
        assertEquals(record.id, finished.id)
        assertEquals(start.plusDays(1), finished.end)
    }

    @Test fun concurrentDuplicatesProduceOneRecord() = runBlocking {
        val date = LocalDate.of(2024, 2, 29)
        val results = List(2) { async { repository.add(date, date) } }.awaitAll()
        assertEquals(1, results.count { it == null })
        assertEquals(1, results.count { it == PeriodProblem.OVERLAP })
        assertEquals(1, repository.periods.first().size)
    }

    @Test fun recordsSurviveDatabaseReopening() = runBlocking {
        database.close()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "test-persistence.db"
        context.deleteDatabase(name)
        database = Room.databaseBuilder(context, RitelaDatabase::class.java, name).build()
        repository = PeriodRepository(database.periods(), clock)
        assertNull(repository.add(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 5)))
        val original = repository.periods.first().single()
        database.close()
        database = Room.databaseBuilder(context, RitelaDatabase::class.java, name).build()
        val restored = PeriodRepository(database.periods(), clock).periods.first().single()
        assertEquals(original, restored)
    }

    @Test fun editingPreservesIdentityAndRejectsOverlapWithoutChanges() = runBlocking {
        val start = LocalDate.of(2024, 2, 1)
        assertNull(repository.add(start, start.plusDays(3)))
        val original = repository.periods.first().single()
        val laterClock = Clock.fixed(clock.instant().plusSeconds(60), clock.zone)
        val editing = PeriodRepository(database.periods(), laterClock)
        assertNull(editing.edit(original.id, start.plusDays(1), start.plusDays(4)))
        val changed = repository.periods.first().single()
        assertEquals(original.id, changed.id)
        assertEquals(original.createdAt, changed.createdAt)
        assertEquals(laterClock.instant(), changed.updatedAt)
        assertNull(repository.add(start.plusDays(10), start.plusDays(12)))
        assertEquals(PeriodProblem.OVERLAP, editing.edit(original.id, start.plusDays(10), null))
        assertEquals(changed, repository.periods.first().first { it.id == original.id })
        assertEquals(
            PeriodProblem.FUTURE_DATE,
            editing.edit(original.id, LocalDate.of(2024, 3, 11), null)
        )
        assertEquals(
            PeriodProblem.END_BEFORE_START,
            editing.edit(original.id, start, start.minusDays(1))
        )
    }

    @Test fun reopeningAndDeletingDoNotRecreateRecords() = runBlocking {
        val day = LocalDate.of(2024, 2, 29)
        assertNull(repository.add(day, day))
        val original = repository.periods.first().single()
        assertNull(repository.edit(original.id, day, null))
        assertNull(repository.finish(original.id, day))
        assertNull(repository.delete(original.id))
        assertEquals(PeriodProblem.STORAGE, repository.edit(original.id, day, day))
        assertEquals(PeriodProblem.STORAGE, repository.delete(original.id))
        assertEquals(PeriodProblem.STORAGE, repository.finish(original.id, day))
        assertTrue(repository.periods.first().isEmpty())
    }

    @Test fun concurrentEditsCannotCreateOverlappingRecords() = runBlocking {
        val day = LocalDate.of(2024, 2, 1)
        assertNull(repository.add(day, day))
        assertNull(repository.add(day.plusDays(10), day.plusDays(10)))
        val records = repository.periods.first()
        val results = records.map {
            async { repository.edit(it.id, day.plusDays(5), day.plusDays(5)) }
        }.awaitAll()
        assertEquals(1, results.count { it == null })
        assertEquals(1, results.count { it == PeriodProblem.OVERLAP })
        assertEquals(2, repository.periods.first().size)
    }
}
