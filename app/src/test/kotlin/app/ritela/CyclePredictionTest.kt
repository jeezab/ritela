package app.ritela

import app.ritela.domain.CalendarDayKind
import app.ritela.domain.ForecastUnavailable
import app.ritela.domain.HistoryConfidence
import app.ritela.domain.Period
import app.ritela.domain.analyzeCycles
import app.ritela.domain.calendarDay
import app.ritela.domain.monthDays
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CyclePredictionTest {
    private fun history(
        lengths: List<Int>,
        first: LocalDate = LocalDate.of(2024, 1, 1)
    ): List<Period> {
        var date = first
        return (listOf(0) + lengths).mapIndexed { index, length ->
            date = date.plusDays(length.toLong())
            Period(UUID(0, index.toLong()), date, date.plusDays(3), Instant.EPOCH, Instant.EPOCH)
        }
    }

    @Test fun stableCyclesHaveBoundedForecastsAndIncreasingUncertainty() {
        val records = history(List(8) { 28 })
        val today = records.last().end!!
        val result = analyzeCycles(records, today)
        assertEquals(4L, result.cycleDay)
        assertEquals(12, result.forecasts.size)
        assertEquals(HistoryConfidence.HIGH, result.forecasts.first().confidence)
        assertEquals(28, result.forecasts.first().cycleMedian)
        assertEquals(records.last().start.plusDays(28), result.forecasts.first().predictedStartDate)
        val widths = result.forecasts.map { forecast ->
            assertTrue(forecast.lowerBound <= forecast.predictedStartDate)
            assertTrue(forecast.upperBound >= forecast.predictedStartDate)
            forecast.upperBound.toEpochDay() - forecast.lowerBound.toEpochDay()
        }
        assertTrue(widths.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun insufficientOrOpenHistoryDoesNotInventForecasts() {
        for (lengths in listOf(emptyList(), listOf(28), listOf(28, 28))) {
            val records = history(lengths)
            assertEquals(
                ForecastUnavailable.NEED_MORE,
                analyzeCycles(records, records.last().end!!).unavailable
            )
        }
        val records = history(List(6) { 28 })
        val open = records.dropLast(1) + records.last().copy(end = null)
        assertEquals(
            ForecastUnavailable.ONGOING,
            analyzeCycles(open, records.last().start.plusDays(2)).unavailable
        )
    }

    @Test fun mixedLengthsAndOutliersKeepMedianExplainable() {
        val mixed = history(listOf(28, 29, 30, 28, 29, 30))
        assertEquals(29, analyzeCycles(mixed, mixed.last().end!!).forecasts.first().cycleMedian)
        val single = history(listOf(28, 28, 28, 84, 28, 28, 28))
        val forecast = analyzeCycles(single, single.last().end!!).forecasts.first()
        assertEquals(28, forecast.cycleMedian)
        assertEquals(HistoryConfidence.LOW, forecast.confidence)
        assertTrue(forecast.upperBound.toEpochDay() - forecast.lowerBound.toEpochDay() > 4)
        for (lengths in listOf(listOf(28, 28, 84, 84, 28, 28), listOf(18, 36, 22, 45, 27, 50))) {
            val records = history(lengths)
            val result = analyzeCycles(records, records.last().end!!)
            assertEquals(HistoryConfidence.LOW, result.forecasts.first().confidence)
        }
    }

    @Test fun invalidOverlappingFutureAndDuplicateRecordsCannotPoisonForecast() {
        val records = history(List(6) { 28 })
        val today = records.last().end!!
        val broken = listOf(
            records + records.last(),
            records.dropLast(1) + records.last().copy(end = records.last().start.minusDays(1)),
            records +
                records.last().copy(id = UUID.randomUUID(), start = today.plusDays(1), end = null),
            records.dropLast(1) + records.last().copy(start = records[records.lastIndex - 1].start)
        )
        for (input in broken) {
            val result = analyzeCycles(input, today)
            assertEquals(ForecastUnavailable.INVALID_HISTORY, result.unavailable)
            assertTrue(result.forecasts.isEmpty())
        }
    }

    @Test fun editingDeletingAndImportingOlderRecordsRecalculatesFromDates() {
        val records = history(List(8) { 28 })
        val today = records.last().end!!
        val old = analyzeCycles(records, today)
        assertEquals(old, analyzeCycles(records.reversed(), today))
        val deleted = analyzeCycles(records.filterIndexed { index, _ -> index != 3 }, today)
        assertEquals(28, deleted.forecasts.first().cycleMedian)
        assertEquals(7, deleted.forecasts.first().cyclesUsed)
        assertEquals(HistoryConfidence.LOW, deleted.forecasts.first().confidence)
        val edited =
            records.dropLast(1) +
                records.last().copy(
                    start = records.last().start.plusDays(2),
                    end = today.plusDays(2)
                )
        assertEquals(
            old.forecasts.first().predictedStartDate.plusDays(2),
            analyzeCycles(edited, today.plusDays(2)).forecasts.first().predictedStartDate
        )
    }

    @Test fun localDatesCrossLeapDayMonthAndYearWithoutTimezoneOrRebootDrift() {
        for (first in listOf(
            LocalDate.of(2023, 12, 1),
            LocalDate.of(2024, 2, 1),
            LocalDate.of(2024, 3, 1)
        )) {
            val records = history(List(6) { 28 }, first)
            val today = records.last().end!!
            val result = analyzeCycles(records, today)
            assertEquals(
                records.last().start.plusDays(28),
                result.forecasts.first().predictedStartDate
            )
            // Reconstructing persisted epoch-day values models a restart/zone change without timestamps.
            assertEquals(
                result,
                analyzeCycles(
                    records.map {
                        it.copy(start = LocalDate.ofEpochDay(it.start.toEpochDay()))
                    },
                    today
                )
            )
        }
    }

    @Test fun longGapsAndOverdueDatesDoNotRollForwardInventedCycles() {
        val records = history(listOf(28, 28, 800, 28, 28))
        val today = records.last().end!!
        val result = analyzeCycles(records, today)
        assertEquals(4, result.forecasts.first().cyclesUsed)
        assertEquals(HistoryConfidence.LOW, result.forecasts.first().confidence)
        assertEquals(
            ForecastUnavailable.PAST_DUE,
            analyzeCycles(records, result.forecasts.first().upperBound.plusDays(1)).unavailable
        )
    }

    @Test fun monthGridAndCalendarSymbolsDistinguishObservedFromEstimatedDays() {
        val grid = monthDays(YearMonth.of(2024, 2))
        assertEquals(35, grid.size)
        assertEquals(3, grid.indexOf(LocalDate.of(2024, 2, 1)))
        assertEquals(29, grid.count { it != null })
        val records = history(List(6) { 28 })
        val today = records.last().end!!
        val result = analyzeCycles(records, today)
        assertEquals(CalendarDayKind.OBSERVED, calendarDay(records, result, today, today).kind)
        val next = result.forecasts.first()
        assertEquals(
            CalendarDayKind.PREDICTED,
            calendarDay(records, result, next.predictedStartDate, today).kind
        )
        assertEquals(
            CalendarDayKind.UNCERTAIN,
            calendarDay(records, result, next.lowerBound, today).kind
        )
        assertEquals(
            CalendarDayKind.APPROXIMATE,
            calendarDay(records, result, result.forecasts.last().predictedStartDate, today).kind
        )
    }
}
