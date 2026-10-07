package app.ritela

import androidx.compose.ui.geometry.Offset
import app.ritela.ui.OrbitGeometry
import app.ritela.ui.OrbitPhaseMarkers
import app.ritela.ui.currentOrbitAngle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitGeometryTest {
    @Test fun markersAndCurrentPointStayOnTheSameTiltedEllipse() {
        val geometry = OrbitGeometry(Offset(150f, 78f), 115f, 43f)
        val tilt = -geometry.tiltDegrees * PI / 180
        val angles = OrbitPhaseMarkers.entries.map { it.angleDegrees } +
            (0..29).map { currentOrbitAngle(it / 29f) }
        for (angle in angles) {
            val point = geometry.pointOnOrbit(angle) - geometry.center
            val x = point.x * cos(tilt) - point.y * sin(tilt)
            val y = point.x * sin(tilt) + point.y * cos(tilt)
            assertEquals(1.0, x * x / (115 * 115) + y * y / (43 * 43), 0.00001)
        }
        val first = geometry.pointOnOrbit(currentOrbitAngle(0f))
        val last = geometry.pointOnOrbit(currentOrbitAngle(1f))
        assertEquals(first.x, last.x, 0.0001f)
        assertEquals(first.y, last.y, 0.0001f)
    }

    @Test fun dayEightIsBetweenFollicularAndOvulationAndOverdueDaysClamp() {
        val angle = currentOrbitAngle(7f / 29f)
        assertTrue(angle > OrbitPhaseMarkers.FOLLICULAR.angleDegrees)
        assertTrue(angle < OrbitPhaseMarkers.OVULATION.angleDegrees)
        assertEquals(currentOrbitAngle(1f), currentOrbitAngle(1.5f), 0f)
        assertEquals(currentOrbitAngle(0f), currentOrbitAngle(-0.1f), 0f)
    }
}
