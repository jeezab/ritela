package app.ritela.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure

/** One closed clockwise path starting at the top; drawing and hit testing share it. */
internal class OrbitGeometry(
    val center: Offset,
    val horizontalRadius: Float,
    val verticalRadius: Float
) {
    private fun local(x: Float, y: Float): Offset = center + Offset(x, y)

    val path = Path().apply {
        val k = 0.55228475f
        val rx = horizontalRadius
        val ry = verticalRadius
        val top = local(0f, -ry)
        moveTo(top.x, top.y)
        fun segment(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            val a = local(x1, y1)
            val b = local(x2, y2)
            val c = local(x3, y3)
            cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
        }
        segment(k * rx, -ry, rx, -k * ry, rx, 0f)
        segment(rx, k * ry, k * rx, ry, 0f, ry)
        segment(-k * rx, ry, -rx, k * ry, -rx, 0f)
        segment(-rx, -k * ry, -k * rx, -ry, 0f, -ry)
        close()
    }
    private val measure = PathMeasure().apply { setPath(path, true) }
    fun position(progress: Float): Offset =
        measure.getPosition(measure.length * progress.coerceIn(0f, 1f))
    private val samples = (0..256).map { position(it / 256f) }

    /** Closest point projection works for both compact and expanded aspect ratios. */
    fun progressAt(point: Offset): Float {
        var nearest = Float.MAX_VALUE
        var progress = 0f
        for (index in 0 until samples.lastIndex) {
            val a = samples[index]
            val vector = samples[index + 1] - a
            val squared = vector.getDistanceSquared()
            val relative = point - a
            val t = if (squared == 0f) {
                0f
            } else {
                ((relative.x * vector.x + relative.y * vector.y) / squared).coerceIn(0f, 1f)
            }
            val distance = (point - (a + vector * t)).getDistanceSquared()
            if (distance < nearest) {
                nearest = distance
                progress = (index + t) / 256f
            }
        }
        return progress
    }

    companion object {
        fun inViewport(size: Size, inset: Float): OrbitGeometry = OrbitGeometry(
            Offset(size.width / 2, size.height / 2),
            (size.width / 2 - inset).coerceAtLeast(1f),
            (size.height / 2 - inset).coerceAtLeast(1f)
        )
    }
}

/** Symbolic landmarks, not individual physiological phase boundaries. */
enum class OrbitMarker(val progress: Float) {
    START(0f),
    FOLLICULAR(0.2f),
    OVULATION(0.42f),
    LUTEAL(0.65f),
    END(0.87f)
}
