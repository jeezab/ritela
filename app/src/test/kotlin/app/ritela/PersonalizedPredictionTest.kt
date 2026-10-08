package app.ritela

import app.ritela.domain.CalendarDayKind
import app.ritela.domain.CervicalMucus
import app.ritela.domain.CycleForecast
import app.ritela.domain.DayLog
import app.ritela.domain.FertilityLevel
import app.ritela.domain.Period
import app.ritela.domain.analyzeCycles
import app.ritela.domain.backtestCycles
import app.ritela.domain.calendarDay
import app.ritela.domain.cervicalMucus
import app.ritela.domain.estimateFertileWindow
import app.ritela.domain.estimateOvulation
import app.ritela.domain.fertilityEstimate
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizedPredictionTest {
    private fun history(lengths: List<Int>, duration: Int = 5): List<Period> {
        var date = LocalDate.of(2024, 1, 1)
        return (listOf(0) + lengths).mapIndexed { index, length ->
            date = date.plusDays(length.toLong())
            Period(
                UUID(0, index.toLong()),
                date,
                date.plusDays(duration - 1L),
                Instant.EPOCH,
                Instant.EPOCH
            )
        }
    }

    private fun analyze(records: List<Period>) = analyzeCycles(records, records.last().end!!)
    private fun width(forecast: CycleForecast) =
        forecast.upperBound.toEpochDay() - forecast.lowerBound.toEpochDay()

    @Test fun firstTwoIntervalsAlreadyInfluenceTheEstimateSmoothly() {
        val estimates = (0..4).map {
            analyze(history(List(it) { 36 })).forecasts.first().cycleMedian
        }
        assertEquals(listOf(28, 30, 32, 34, 36), estimates)
    }

    @Test fun recentSixCyclesOutweighOlderSixAndSingleOutlierDoesNotMoveCenter() {
        assertEquals(
            32,
            analyze(
                history(
                    List(6) {
                        26
                    } + List(6) { 32 }
                )
            ).forecasts.first().cycleMedian
        )
        val regular = analyze(history(List(10) { 28 }))
        val outlier = analyze(history(List(9) { 28 } + 84))
        assertEquals(28, outlier.forecasts.first().cycleMedian)
        assertTrue(width(outlier.forecasts.first()) >= width(regular.forecasts.first()))
    }

    @Test fun errorsAreMeasuredAtRollingOriginsWithoutTargetLeakage() {
        val records = history(listOf(30, 34, 28))
        val validation = backtestCycles(records)
        assertEquals(listOf(2L, 5L, -3L), validation.predictions.map { it.errorDays })
        assertEquals(10.0 / 3, validation.mae!!, 0.0001)
        assertEquals(3.0, validation.medianAbsoluteError!!, 0.0)
        assertEquals(2.0, validation.medianError!!, 0.0)
        assertEquals(listOf(0, 1, 2), validation.predictions.map { it.cyclesUsed })
        validation.predictions.forEachIndexed { index, point ->
            val prefix = records.take(index + 1).map {
                if (it.start == point.origin) it.copy(end = null) else it
            }
            assertEquals(
                analyzeCycles(prefix, point.origin).forecasts.first().predictedStartDate,
                point.predictedStart
            )
        }
    }

    @Test fun addingOrChangingFutureTargetsDoesNotRewriteEarlierBacktests() {
        val first = history(listOf(28, 30, 32, 26, 29, 31))
        val longer = history(listOf(28, 30, 32, 26, 29, 31, 45, 20))
        assertEquals(backtestCycles(first).predictions, backtestCycles(longer).predictions.take(6))
        val changedTarget = history(listOf(28, 30, 32, 26, 29, 50))
        assertEquals(
            backtestCycles(first).predictions.last().predictedStart,
            backtestCycles(changedTarget).predictions.last().predictedStart
        )
    }

    @Test fun empiricalErrorsAndVariationWidenBoundsWhileSparseHistoryStaysConservative() {
        val regular = analyze(history(List(10) { 28 }))
        val irregular = analyze(history(listOf(22, 36, 24, 34, 21, 38, 25, 32, 23, 35)))
        val sparse = analyze(history(emptyList()))
        assertTrue(irregular.backtest.mae!! > regular.backtest.mae!!)
        assertTrue(width(irregular.forecasts.first()) > width(regular.forecasts.first()))
        assertTrue(width(sparse.forecasts.first()) > width(regular.forecasts.first()))
        assertNull(sparse.backtest.mae)
        assertTrue(width(sparse.forecasts.first()) >= 16)
    }

    @Test fun simulationIsDeterministicCoversCalendarYearAndWidensAtEveryHorizon() {
        for (length in listOf(20, 28, 40)) {
            val records = history(List(8) { length })
            val result = analyze(records)
            assertEquals(result, analyzeCycles(records.reversed(), records.last().end!!))
            val end = records.last().end!!.plusYears(1)
            assertTrue(result.forecasts.last().predictedStartDate > end.minusDays(length + 3L))
            assertTrue(result.forecasts.last().predictedStartDate <= end)
            assertTrue(
                result.forecasts.zipWithNext().all { (a, b) ->
                    b.predictedStartDate > a.predictedStartDate && width(b) > width(a)
                }
            )
            result.forecasts.forEach {
                assertTrue(it.lowerBound <= it.predictedStartDate)
                assertTrue(it.predictedStartDate <= it.upperBound)
            }
        }
    }

    @Test fun repeatedMissedRecordsDoNotBecomeInventedHistoricalPeriods() {
        val records = history(listOf(28, 28, 56, 28, 28, 84, 28, 28))
        val snapshot = records.toList()
        val result = analyze(records)
        assertEquals(28, result.forecasts.first().cycleMedian)
        assertEquals(8, result.forecasts.first().cyclesUsed)
        assertEquals(snapshot, records)
        assertTrue(result.backtest.mae!! > 0)
    }

    @Test fun shortestValidIntervalsStillHaveBoundedAnnualWork() {
        val records = history(List(12) { 1 }, duration = 1)
        val started = System.nanoTime()
        val result = analyze(records)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
        assertEquals(1, result.forecasts.first().cycleMedian)
        assertTrue(result.forecasts.size in 1..366)
        assertTrue(result.forecasts.last().predictedStartDate <= records.last().end!!.plusYears(1))
        println(
            "Synthetic shortest-cycle annual simulation: ${result.forecasts.size} horizons, $elapsedMs ms"
        )
    }

    @Test fun durationStartsAtFiveAndLearnsWithoutChangingRecordedEnds() {
        assertEquals(5, analyzeCycles(emptyList(), LocalDate.of(2024, 1, 1)).periodDuration)
        val estimates = (0..2).map { analyze(history(List(it) { 28 }, 8)).periodDuration }
        assertEquals(listOf(6, 7, 8), estimates)
        val records = history(List(6) { 28 }, 5)
        val changed =
            records.dropLast(1) + records.last().copy(end = records.last().start.plusDays(19))
        assertEquals(5, analyze(changed).periodDuration)
        assertEquals(20L, changed.last().end!!.toEpochDay() - changed.last().start.toEpochDay() + 1)
        val open = records.map { if (it == records.last()) it.copy(end = null) else it }
        assertEquals(5, analyzeCycles(open, records.last().start).periodDuration)
    }

    @Test fun ovulationAndExpandedWindowUseBothStartBoundsAndLutealVariability() {
        val next = analyze(history(List(8) { 28 })).forecasts.first()
        val ovulation = estimateOvulation(next)
        assertEquals(next.predictedStartDate.minusDays(14), ovulation.centralDate)
        assertEquals(next.lowerBound.minusDays(16), ovulation.earliest)
        assertEquals(next.upperBound.minusDays(10), ovulation.latest)
        val window = estimateFertileWindow(ovulation)
        assertEquals(ovulation.earliest.minusDays(5), window.possible.start)
        assertEquals(ovulation.latest.plusDays(1), window.possible.endInclusive)
        assertTrue(window.possible.start < window.likely.start)
        assertTrue(window.possible.endInclusive > window.likely.endInclusive)
    }

    @Test fun calendarDistinguishesBroadWindowLikelyDaysAndPossibleOvulation() {
        val records = history(List(10) { 28 })
        val result = analyze(records)
        val today = records.last().end!!
        val window = estimateFertileWindow(estimateOvulation(result.forecasts.first()))
        assertEquals(
            CalendarDayKind.FERTILE_ESTIMATE,
            calendarDay(
                records,
                result,
                maxOf(window.possible.start, today.plusDays(1)),
                today
            ).kind
        )
        assertEquals(
            CalendarDayKind.FERTILE_LIKELY,
            calendarDay(records, result, window.likely.start, today).kind
        )
        assertEquals(
            CalendarDayKind.OVULATION_ESTIMATE,
            calendarDay(records, result, window.ovulation.centralDate, today).kind
        )
        assertEquals(
            CalendarDayKind.OBSERVED,
            calendarDay(records, result, records.last().start, today).kind
        )
    }

    @Test fun mucusIsAnAdditionalSameDaySignalAndNeverMovesPeriodForecast() {
        val records = history(List(8) { 28 })
        val today = records.last().start.plusDays(8)
        val baseline = analyzeCycles(records, today)
        for (mucus in CervicalMucus.entries) {
            val log = DayLog(today, custom = mapOf("discharge" to setOf(mucus.name)))
            val result = analyzeCycles(records, today, dayLogs = listOf(log))
            assertEquals(baseline.forecasts, result.forecasts)
            assertEquals(baseline.backtest, result.backtest)
            val signal = mucus in setOf(CervicalMucus.WATERY, CervicalMucus.CLEAR_STRETCHY)
            assertEquals(
                signal,
                fertilityEstimate(records, result, today) == FertilityLevel.MUCUS_SIGNAL
            )
            assertTrue(
                fertilityEstimate(records, result, today) != FertilityLevel.POSSIBLE_OVULATION
            )
            assertEquals(
                fertilityEstimate(records, baseline, today.plusDays(1)),
                fertilityEstimate(records, result, today.plusDays(1))
            )
        }
    }

    @Test fun futureOrAmbiguousMucusIsIgnoredAndNoHistoryStillAllowsTodaysSignal() {
        val today = LocalDate.of(2024, 2, 29)
        val watery = DayLog(today, custom = mapOf("discharge" to setOf("WATERY")))
        val result = analyzeCycles(emptyList(), today, dayLogs = listOf(watery))
        assertEquals(FertilityLevel.MUCUS_SIGNAL, fertilityEstimate(emptyList(), result, today))
        assertTrue(result.forecasts.isEmpty())
        val future =
            analyzeCycles(
                emptyList(),
                today,
                dayLogs = listOf(watery.copy(date = today.plusDays(1)))
            )
        assertTrue(future.mucusObservations.isEmpty())
        assertNull(
            cervicalMucus(watery.copy(custom = mapOf("discharge" to setOf("WATERY", "UNUSUAL"))))
        )
    }
}
