package app.ritela.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The stroke and every marker share this ellipse and its rotation. */
internal data class OrbitGeometry(
    val center: Offset,
    val horizontalRadius: Float,
    val verticalRadius: Float,
    val tiltDegrees: Float = -10f
) {
    fun pointOnOrbit(angleDeg: Float): Offset {
        val angle = angleDeg * (PI / 180).toFloat()
        val tilt = tiltDegrees * (PI / 180).toFloat()
        val x = horizontalRadius * cos(angle)
        val y = verticalRadius * sin(angle)
        return center + Offset(x * cos(tilt) - y * sin(tilt), x * sin(tilt) + y * cos(tilt))
    }
}

/** Ordered visual landmarks, not measured phase boundaries or an ovulation date. */
internal enum class OrbitPhaseMarkers(val angleDegrees: Float) {
    START(210f),
    FOLLICULAR(255f),
    OVULATION(320f),
    LUTEAL(390f),
    END(430f)
}

internal fun currentOrbitAngle(progress: Float): Float =
    OrbitPhaseMarkers.START.angleDegrees + 360f * progress.coerceIn(0f, 1f)
