package app.ritela

import app.ritela.domain.CalendarDayKind
import app.ritela.domain.CervicalMucus
import app.ritela.domain.CycleAnalysis
import app.ritela.domain.CycleForecast
import app.ritela.domain.ForecastUnavailable
import app.ritela.domain.HistoryConfidence
import app.ritela.domain.Period
import app.ritela.domain.calendarDisplayDay
import app.ritela.domain.calendarSelection
import app.ritela.domain.compactFertilityWindows
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarPresentationTest {
    private val today = LocalDate.of(2026, 10, 8)
    private fun period(start: LocalDate, duration: Long = 5) = Period(
        UUID.nameUUIDFromBytes(start.toString().toByteArray()),
        start,
        start.plusDays(duration - 1),
        Instant.EPOCH,
        Instant.EPOCH
    )
    private val history = (6 downTo 0).map { period(today.minusDays(8 + it * 28L)) }
    private fun forecast(start: LocalDate) = CycleForecast(
        start,
        start.minusDays(3),
        start.plusDays(3),
        1,
        HistoryConfidence.HIGH,
        6,
        28,
        0.0
    )
    private fun analysis(value: CycleForecast) =
        CycleAnalysis(forecasts = listOf(value), unavailable = null)

    @Test fun compactGuideIsExactlySevenDaysAcrossMonthAndLeapDay() {
        for (year in listOf(2024, 2025)) {
            val next = LocalDate.of(year, 3, 15)
            val estimate = analysis(forecast(next))
            val periods = listOf(period(next.minusDays(28)))
            val center = next.minusDays(14)
            for (offset in -7L..3L) {
                val kind = calendarDisplayDay(
                    periods,
                    estimate,
                    center.plusDays(offset),
                    center
                ).kind
                assertEquals(
                    when (offset) {
                        0L -> CalendarDayKind.OVULATION_ESTIMATE
                        in -5L..1L -> CalendarDayKind.FERTILE_LIKELY
                        else -> CalendarDayKind.NONE
                    },
                    kind
                )
            }
            assertTrue(
                compactFertilityWindows(periods, estimate).single().possible.start <
                    center.minusDays(5)
            )
        }
    }

    @Test fun guideCrossesYearBoundaryWithoutPaintingWideUncertainty() {
        val next = LocalDate.of(2027, 1, 15)
        val estimate = analysis(forecast(next))
        val records = history.takeLast(1)
        assertEquals(
            CalendarDayKind.FERTILE_LIKELY,
            calendarDisplayDay(records, estimate, LocalDate.of(2026, 12, 27), today).kind
        )
        assertEquals(
            CalendarDayKind.OVULATION_ESTIMATE,
            calendarDisplayDay(records, estimate, LocalDate.of(2027, 1, 1), today).kind
        )
        assertEquals(
            CalendarDayKind.NONE,
            calendarDisplayDay(records, estimate, LocalDate.of(2027, 1, 3), today).kind
        )
    }

    @Test fun actualBleedingWinsAndSuppressesConflictingFertilityMessages() {
        val estimate = analysis(forecast(today.plusDays(14)))
        val records = history + period(today, 1)
        val selected = calendarSelection(records, estimate, today, today)
        assertEquals(CalendarDayKind.OBSERVED, selected.day.kind)
        assertNull(selected.possibleWindow)
        assertEquals(1L, selected.cycleDay)
    }

    @Test fun predictedBleedingWinsOverAnotherForecastsOvulationAndWindow() {
        val first = forecast(today.plusDays(20))
        val second = forecast(first.predictedStartDate.plusDays(14)).copy(horizon = 2)
        val estimate = analysis(first).copy(forecasts = listOf(first, second))
        for (offset in 0L..4L) {
            val selected =
                calendarSelection(
                    history,
                    estimate,
                    first.predictedStartDate.plusDays(offset),
                    today
                )
            assertEquals(
                if (offset ==
                    0L
                ) {
                    CalendarDayKind.PREDICTED
                } else {
                    CalendarDayKind.ESTIMATED_PERIOD
                },
                selected.day.kind
            )
            assertNull(selected.possibleWindow)
        }
    }

    @Test fun sparseHistoryKeepsSevenDayGuideAndSeparatePossibleRange() {
        val value = forecast(
            today.plusDays(14)
        ).copy(confidence = HistoryConfidence.LOW, cyclesUsed = 2)
        val selected = calendarSelection(history.takeLast(2), analysis(value), today, today)
        assertEquals(CalendarDayKind.OVULATION_ESTIMATE, selected.day.kind)
        assertNotNull(selected.possibleWindow)
        assertEquals(2, compactFertilityWindows(history.takeLast(2), analysis(value)).size)
    }

    @Test fun irregularDistantAndWideForecastsKeepExactlySevenHighlightedDays() {
        val base = forecast(today.plusDays(14))
        for (value in listOf(
            base.copy(cycleVariation = 5.0),
            base.copy(confidence = HistoryConfidence.LOW),
            base.copy(horizon = 4),
            base.copy(lowerBound = base.predictedStartDate.minusDays(9))
        )) {
            for (offset in -7L..3L) {
                val kind = calendarDisplayDay(
                    listOf(period(today.minusDays(28))),
                    analysis(value),
                    today.plusDays(offset),
                    today
                ).kind
                assertEquals(
                    when (offset) {
                        0L -> CalendarDayKind.OVULATION_ESTIMATE
                        in -5L..1L -> CalendarDayKind.FERTILE_LIKELY
                        else -> CalendarDayKind.NONE
                    },
                    kind
                )
            }
        }
    }

    @Test fun mucusIsAnIndependentSignalAndNeverMovesDatesOrPaintsExtraDays() {
        val value = forecast(today.plusDays(25)).copy(confidence = HistoryConfidence.LOW)
        for (mucus in CervicalMucus.entries) {
            val estimate = analysis(value).copy(mucusObservations = mapOf(today to mucus))
            val selected = calendarSelection(history.takeLast(1), estimate, today, today)
            assertEquals(CalendarDayKind.NONE, selected.day.kind)
            assertEquals(
                mucus in setOf(CervicalMucus.WATERY, CervicalMucus.CLEAR_STRETCHY),
                selected.mucusSignal
            )
            assertEquals(value, estimate.forecasts.single())
        }
    }

    @Test fun historicalGuideUsesOnlyHistoryAtItsAnchor() {
        val firstAnchor = history.last().start
        val historical = compactFertilityWindows(history, CycleAnalysis())
        assertTrue(historical.any { it.ovulation.centralDate == firstAnchor.minusDays(14) })
        val changed = history + period(today, 1)
        assertEquals(
            historical.filter { it.ovulation.centralDate <= firstAnchor.minusDays(14) },
            compactFertilityWindows(changed, CycleAnalysis()).filter {
                it.ovulation.centralDate <=
                    firstAnchor.minusDays(14)
            }
        )
        assertEquals(2, compactFertilityWindows(history.take(3), CycleAnalysis()).size)
    }

    @Test fun invalidHistoryDoesNotExposeAnyFertilityGuideOrRange() {
        val estimate = analysis(
            forecast(today.plusDays(14))
        ).copy(unavailable = ForecastUnavailable.INVALID_HISTORY)
        assertTrue(compactFertilityWindows(history, estimate).isEmpty())
        assertNull(calendarSelection(history, estimate, today, today).possibleWindow)
    }
}
