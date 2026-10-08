package app.ritela

import androidx.compose.ui.geometry.Offset
import app.ritela.ui.OrbitGeometry
import app.ritela.ui.OrbitMarker
import app.ritela.ui.orbitDayAtProgress
import app.ritela.ui.orbitDayProgress
import kotlin.math.abs
import org.junit.Assert.assertEquals
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
}
