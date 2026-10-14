package app.ritela

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import app.ritela.ui.OrbitGeometry
import app.ritela.ui.OrbitMarker
import app.ritela.ui.orbitDayAtProgress
import app.ritela.ui.orbitDayProgress
import app.ritela.ui.orbitOvulationProgress
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OrbitGeometryTest {
    @Test fun lutealDustFollowsItsArcAndKeepsDynamicOvulationAndMoonTargetsClear() {
        for (size in listOf(Size(320f, 112f), Size(390f, 228f), Size(360f, 300f))) {
            val geometry = OrbitGeometry.inViewport(size, 32f)
            for (length in listOf(21, 28, 35)) {
                val ovulation = orbitOvulationProgress(length)
                val middle = (ovulation + OrbitMarker.END.progress) / 2f
                assertTrue(geometry.lutealDustContains(geometry.position(middle), 24f, ovulation))
                for (progress in listOf(0f, .1f, ovulation, OrbitMarker.END.progress, .98f)) {
                    assertFalse(
                        geometry.lutealDustContains(geometry.position(progress), 24f, ovulation)
                    )
                }
                assertFalse(geometry.lutealDustContains(geometry.center, 8f, ovulation))
                assertFalse(
                    geometry.follicularDustContains(geometry.position(ovulation), 24f, ovulation)
                )
            }
        }
    }

    @Test fun follicularDustFollowsTheArcAndLeavesEndpointTargetsClear() {
        for (size in listOf(Size(320f, 112f), Size(390f, 228f), Size(360f, 300f))) {
            val geometry = OrbitGeometry.inViewport(size, 32f)
            for (progress in listOf(.12f, .2f, .3f)) {
                assertTrue(geometry.follicularDustContains(geometry.position(progress), 24f))
            }
            for (marker in listOf(
                OrbitMarker.START,
                OrbitMarker.OVULATION,
                OrbitMarker.LUTEAL,
                OrbitMarker.END
            )) {
                assertFalse(
                    geometry.follicularDustContains(geometry.position(marker.progress), 24f)
                )
            }
            assertFalse(geometry.follicularDustContains(geometry.center, 8f))
            assertFalse(geometry.follicularDustContains(geometry.position(.01f), 24f))
        }
    }

    @Test fun pathIsClosedClockwiseAndEveryMarkerCanBeProjectedBack() {
        for (height in listOf(80f, 130f)) {
            val geometry = OrbitGeometry(Offset(180f, 160f), 140f, height)
            assertEquals(geometry.position(0f).x, geometry.position(1f).x, 0.001f)
            assertEquals(geometry.position(0f).y, geometry.position(1f).y, 0.001f)
            assertTrue(geometry.position(0.1f).x > geometry.center.x)
            assertTrue(geometry.position(0.6f).x < geometry.center.x)
            for (progress in OrbitMarker.entries.map { it.progress } +
                listOf(0.03f, 7f / 29f, 0.97f)) {
                val projected = geometry.progressAt(geometry.position(progress))
                val error = abs(progress - projected)
                assertTrue(error < 0.002f || 1f - error < 0.002f)
            }
        }
    }

    @Test fun dayMappingRoundTripsAndOverdueDaysNeverWrapToTheStart() {
        for (length in listOf(1, 21, 28, 29, 34, 60)) {
            for (day in 1..length) {
                assertEquals(
                    day.toLong(),
                    orbitDayAtProgress(orbitDayProgress(day.toLong(), length), length)
                )
            }
            assertEquals(
                orbitDayProgress(length.toLong(), length),
                orbitDayProgress(90, length),
                0f
            )
            assertEquals(0f, orbitDayProgress(-1, length), 0f)
        }
        assertEquals(7f / 29f, orbitDayProgress(8, 29), 0f)
        assertTrue(orbitDayProgress(40, 29) < 1f)
    }

    @Test fun ovulationPearlMatchesTheCalendarDayAndTheMoonSitsCloserToTheSun() {
        for (length in listOf(21, 28, 29, 35)) {
            val day = (length - 14).coerceIn(1, length).toLong()
            assertEquals(day, orbitDayAtProgress(orbitOvulationProgress(length), length))
        }
        assertTrue(OrbitMarker.END.progress > .9f)
    }

    @Test fun phaseLabelsStayOutsideTheEllipse() {
        val geometry = OrbitGeometry.inViewport(Size(350f, 228f), 32f)
        for (width in listOf(90f, 160f)) {
            val follicular = geometry.phaseCallout(350f, 228f, width, 18f, 8f, true)
            val luteal = geometry.phaseCallout(350f, 228f, width, 18f, 8f, false)
            assertTrue(follicular.x + width > geometry.center.x + geometry.horizontalRadius * .5f)
            assertTrue(follicular.y > geometry.center.y)
            assertTrue(luteal.x < geometry.center.x)
            assertTrue(luteal.y + 18f < geometry.center.y)
            assertTrue(geometry.labelClearsEllipse(follicular, width, 18f))
            assertTrue(geometry.labelClearsEllipse(luteal, width, 18f))
        }
    }
}
