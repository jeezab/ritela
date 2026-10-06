package app.ritela.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Decorative history marker. The surrounding text carries all cycle information. */
@Composable
fun CycleOrbit(day: Long, length: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier.size(104.dp)) {
        val radius = size.minDimension * 0.43f
        drawCircle(colors.secondary.copy(alpha = 0.15f), radius, style = Stroke(1.dp.toPx()))
        val progress = ((day - 1).toFloat() / length.coerceAtLeast(1)).coerceIn(0f, 1f)
        repeat(12) { index ->
            val angle = (index / 12f * 2 * Math.PI - Math.PI / 2).toFloat()
            val point = center + Offset(cos(angle) * radius, sin(angle) * radius)
            drawCircle(
                colors.secondary.copy(alpha = 0.25f),
                1.5.dp.toPx(),
                point
            )
        }
        val angle = (progress * 2 * Math.PI - Math.PI / 2).toFloat()
        drawCircle(
            colors.secondary,
            6.dp.toPx(),
            center + Offset(cos(angle) * radius, sin(angle) * radius)
        )
        drawCircle(colors.surface.copy(alpha = 0.4f), radius * 0.62f)
        val stem = Path().apply {
            moveTo(size.width * 0.43f, size.height * 0.72f)
            cubicTo(
                size.width * 0.6f,
                size.height * 0.55f,
                size.width * 0.43f,
                size.height * 0.45f,
                size.width * 0.57f,
                size.height * 0.29f
            )
        }
        drawPath(stem, colors.primary.copy(alpha = 0.7f), style = Stroke(1.5.dp.toPx()))
        for (index in 0..2) {
            val y = size.height * (0.4f + index * 0.09f)
            val x = size.width * 0.51f
            val direction = if (index % 2 == 0) 1 else -1
            val leaf = Path().apply {
                moveTo(x, y + size.height * 0.09f)
                quadraticTo(
                    x + direction * size.width * 0.2f,
                    y,
                    x + direction * size.width * 0.12f,
                    y - size.height * 0.05f
                )
                quadraticTo(x, y, x, y + size.height * 0.09f)
            }
            drawPath(leaf, colors.primary.copy(alpha = 0.45f))
        }
    }
}
