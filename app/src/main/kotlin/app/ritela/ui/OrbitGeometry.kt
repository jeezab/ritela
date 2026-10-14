package app.ritela.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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

    fun follicularDustContains(
        point: Offset,
        radius: Float,
        until: Float = OrbitMarker.OVULATION.progress
    ): Boolean = dustContains(point, radius, OrbitMarker.START.progress, until)

    fun lutealDustContains(
        point: Offset,
        radius: Float,
        from: Float = OrbitMarker.OVULATION.progress
    ): Boolean = dustContains(point, radius, from, OrbitMarker.END.progress)

    private fun dustContains(point: Offset, radius: Float, from: Float, to: Float): Boolean {
        val progress = progressAt(point)
        return progress > from && progress < to &&
            (point - position(progress)).getDistance() <= radius &&
            (point - position(from)).getDistance() >= radius * 1.25f &&
            (point - position(to)).getDistance() >= radius * 1.25f
    }

    /**
     * Luteal sits at the upper left and follicular at the lower right.
     * Each line is as close as its own width allows without crossing the ellipse.
     */
    fun phaseCallout(
        canvasWidth: Float,
        canvasHeight: Float,
        textWidth: Float,
        textHeight: Float,
        gap: Float,
        bottomRight: Boolean
    ): Offset {
        val margin = 2f
        val width = textWidth.coerceAtMost((canvasWidth - margin * 2f).coerceAtLeast(1f))
        val left = if (bottomRight) canvasWidth - width - margin else margin
        val edge = curveEdge(left, left + width, bottomRight)
        val preferred = if (bottomRight) edge + gap else edge - gap - textHeight
        val fitted = preferred.coerceIn(0f, (canvasHeight - textHeight).coerceAtLeast(0f))
        val top = if (labelClearsEllipse(Offset(left, fitted), width, textHeight)) {
            fitted
        } else {
            preferred
        }
        return Offset(left, top)
    }

    /** True when every sample of the label box is outside the ellipse. */
    fun labelClearsEllipse(topLeft: Offset, width: Float, height: Float): Boolean {
        val steps = 8
        for (ix in 0..steps) {
            for (iy in 0..steps) {
                val x = topLeft.x + width * ix / steps
                val y = topLeft.y + height * iy / steps
                val nx = (x - center.x) / horizontalRadius
                val ny = (y - center.y) / verticalRadius
                if (nx * nx + ny * ny <= 1f) return false
            }
        }
        return true
    }

    private fun curveEdge(fromX: Float, toX: Float, below: Boolean): Float {
        val orbitLeft = center.x - horizontalRadius
        val orbitRight = center.x + horizontalRadius
        val start = max(fromX, orbitLeft)
        val end = min(toX, orbitRight)
        if (start > end) return center.y + if (below) verticalRadius else -verticalRadius
        var edge = if (below) Float.NEGATIVE_INFINITY else Float.POSITIVE_INFINITY
        val steps = 24
        for (index in 0..steps) {
            val x = start + (end - start) * index / steps.toFloat()
            val nx = (x - center.x) / horizontalRadius
            val half = verticalRadius * sqrt((1f - nx * nx).coerceAtLeast(0f))
            val y = center.y + if (below) half else -half
            edge = if (below) max(edge, y) else min(edge, y)
        }
        return edge
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
    END(0.92f)
}
