package app.ritela.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.ritela.domain.EstimatedCyclePhase
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class OrbitColors(val ink: Color, val rose: Color, val peach: Color, val surface: Color)

/** Cached vector scene. State reads happen in drawing, not in composition on every frame. */
@Composable
fun CycleOrbitScene(
    cycleDay: Long,
    cycleLengthEstimate: Int,
    phase: EstimatedCyclePhase,
    progress: Float,
    predictedWindow: ClosedRange<LocalDate>?,
    reducedMotion: Boolean,
    colors: OrbitColors,
    modifier: Modifier = Modifier
) {
    val pulse = orbitPulse(reducedMotion)
    val position = if (reducedMotion) {
        rememberUpdatedState(progress.coerceIn(0f, 1f))
    } else {
        animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "cycle position")
    }
    val length = cycleLengthEstimate.coerceAtLeast(1)
    val peakProgress = ((length - 14).toFloat() / length).coerceIn(0f, 1f)
    val glow = when (phase) {
        EstimatedCyclePhase.EARLY -> colors.rose
        EstimatedCyclePhase.FOLLICULAR -> colors.peach
        EstimatedCyclePhase.OVULATION -> colors.peach
        else -> colors.ink
    }
    val phaseLight = if (reducedMotion) {
        rememberUpdatedState(glow)
    } else {
        animateColorAsState(glow, tween(900), label = "phase light")
    }
    Box(
        modifier.fillMaxSize().testTag("cycle-orbit-$cycleDay")
            .clearAndSetSemantics {}.drawWithCache {
                val center = Offset(size.width / 2, size.height / 2)
                val rx = size.width * 0.32f
                val ry = size.height * 0.37f
                fun point(fraction: Float): Offset {
                    val angle = (fraction * 2 * PI - PI * 0.8).toFloat()
                    return center + Offset(cos(angle) * rx, sin(angle) * ry)
                }
                val orbit = Path().apply {
                    repeat(65) { index ->
                        val fraction = index / 64f
                        val angle = (fraction * 2 * PI - PI * 0.8).toFloat()
                        val organic = 1f + 0.018f * sin(angle * 3)
                        val p = center + Offset(cos(angle) * rx * organic, sin(angle) * ry)
                        if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    close()
                }
                val nodes = List(6) { point(it / 6f) }
                val sun = point(0f)
                val moon = point(0.9f)
                val peak = point(peakProgress)
                val halo = Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.16f), Color.Transparent),
                    center,
                    rx
                )
                val sunBrush = Brush.radialGradient(
                    listOf(colors.peach, colors.rose.copy(alpha = 0.3f)),
                    sun,
                    9.dp.toPx()
                )
                val stroke = Stroke(1.dp.toPx())
                val windowStroke = Stroke(3.dp.toPx())
                val driftDistance = 2.dp.toPx()
                val uncertainty = predictedWindow?.let {
                    (ChronoUnit.DAYS.between(it.start, it.endInclusive).toFloat() / length * 360)
                        .coerceIn(0f, 120f)
                } ?: 0f
                onDrawBehind {
                    val breath = pulse.value
                    drawCircle(halo, rx, center)
                    drawPath(orbit, colors.ink.copy(alpha = 0.18f + breath * 0.06f), style = stroke)
                    if (uncertainty > 0f) {
                        drawArc(
                            colors.rose.copy(alpha = 0.18f),
                            -144f - uncertainty / 2,
                            uncertainty,
                            false,
                            center - Offset(rx, ry),
                            Size(rx * 2, ry * 2),
                            style = windowStroke
                        )
                    }
                    nodes.forEachIndexed { index, node ->
                        val drift = Offset(
                            0f,
                            (breath - 0.5f) * if (index % 2 == 0) driftDistance else -driftDistance
                        )
                        drawCircle(colors.rose.copy(alpha = 0.45f), 2.5.dp.toPx(), node + drift)
                    }
                    drawCircle(colors.peach.copy(alpha = 0.12f), 17.dp.toPx(), sun)
                    drawCircle(sunBrush, 8.dp.toPx(), sun)
                    drawCircle(colors.ink.copy(alpha = 0.28f), 8.dp.toPx(), moon)
                    drawCircle(
                        colors.surface,
                        7.dp.toPx(),
                        moon + Offset(4.dp.toPx(), -2.dp.toPx())
                    )
                    drawCircle(colors.peach.copy(alpha = 0.16f), 12.dp.toPx(), peak)
                    drawCircle(colors.peach, 4.dp.toPx(), peak)
                    val active = point(position.value)
                    val scale = 0.98f + breath * 0.04f
                    drawCircle(phaseLight.value.copy(alpha = 0.12f), 16.dp.toPx() * scale, active)
                    drawCircle(colors.surface, 7.dp.toPx() * scale, active)
                    drawCircle(
                        colors.rose.copy(alpha = 0.5f),
                        7.dp.toPx() * scale,
                        active,
                        style = stroke
                    )
                    drawCircle(
                        colors.ink.copy(alpha = 0.18f),
                        1.dp.toPx(),
                        center + Offset(rx, -ry)
                    )
                    drawCircle(
                        colors.rose.copy(alpha = 0.25f),
                        1.dp.toPx(),
                        center - Offset(rx, -ry)
                    )
                }
            }
    )
}

@Composable
private fun orbitPulse(reducedMotion: Boolean): State<Float> {
    if (reducedMotion) return rememberUpdatedState(0.5f)
    return rememberInfiniteTransition(label = "orbit breathing").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "orbit light"
    )
}
