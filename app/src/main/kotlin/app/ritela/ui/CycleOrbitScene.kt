package app.ritela.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.min

data class OrbitColors(val ink: Color, val rose: Color, val peach: Color, val surface: Color)

/** One analytic ellipse. All geometry and brushes are cached; animation only invalidates drawing. */
@Composable
fun CycleOrbitScene(
    cycleDay: Long,
    progress: Float,
    reducedMotion: Boolean,
    colors: OrbitColors,
    modifier: Modifier = Modifier
) {
    val breath = orbitPulse(reducedMotion, 0.93f, 1f, 2600, "orbit breathing")
    val currentPulse = orbitPulse(reducedMotion, 0.98f, 1.04f, 2100, "current point")
    Canvas(
        modifier.testTag("cycle-orbit-$cycleDay").clearAndSetSemantics {}.drawWithCache {
            // Reserve space for the tilted ellipse and the largest halo at every width.
            val unit = min(size.width / 300f, size.height / 150f)
            val center = Offset(size.width / 2, size.height / 2)
            val geometry = OrbitGeometry(center, 115f * unit, 43f * unit)
            val orbitSize = Size(geometry.horizontalRadius * 2, geometry.verticalRadius * 2)
            val orbitTopLeft = center - Offset(geometry.horizontalRadius, geometry.verticalRadius)
            val stroke = Stroke(1.dp.toPx())
            val sun = geometry.pointOnOrbit(OrbitPhaseMarkers.START.angleDegrees)
            val follicular = geometry.pointOnOrbit(OrbitPhaseMarkers.FOLLICULAR.angleDegrees)
            val ovulation = geometry.pointOnOrbit(OrbitPhaseMarkers.OVULATION.angleDegrees)
            val luteal = geometry.pointOnOrbit(OrbitPhaseMarkers.LUTEAL.angleDegrees)
            val moon = geometry.pointOnOrbit(OrbitPhaseMarkers.END.angleDegrees)
            val active = geometry.pointOnOrbit(currentOrbitAngle(progress))
            fun halo(color: Color, point: Offset, radius: Float) = Brush.radialGradient(
                listOf(color.copy(alpha = 0.24f), color.copy(alpha = 0.07f), Color.Transparent),
                point,
                radius * unit
            )
            val sunHalo = halo(colors.peach, sun, 19f)
            val pearl = androidx.compose.ui.graphics.lerp(colors.peach, Color(0xFFFFF4E5), 0.65f)
            val moonColor = androidx.compose.ui.graphics.lerp(
                colors.ink,
                colors.surface,
                if (colors.surface.luminance() < 0.4f) 0.25f else 0.65f
            )
            val ovulationHalo = halo(colors.rose, ovulation, 24f)
            val activeHalo = halo(colors.rose, active, 16f)
            val mist = Brush.radialGradient(
                listOf(colors.rose.copy(alpha = 0.07f), Color.Transparent),
                center,
                85f * unit
            )
            fun planet(color: Color, point: Offset, radius: Float) = Brush.radialGradient(
                listOf(androidx.compose.ui.graphics.lerp(color, Color.White, 0.4f), color),
                point - Offset(radius * unit * 0.3f, radius * unit * 0.4f),
                radius * unit * 1.5f
            )
            val sunBody = planet(colors.peach, sun, 6.5f)
            val pearlBody = planet(pearl, ovulation, 8.5f)
            // A true vector crescent: its transparent cutout preserves the underlying ellipse.
            val moonRadius = 7.5f * unit
            val moonDisc = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        moon - Offset(moonRadius, moonRadius),
                        Size(moonRadius * 2, moonRadius * 2)
                    )
                )
            }
            val cutoutCenter = moon + Offset(3.8f * unit, -2.4f * unit)
            val moonCutout = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        cutoutCenter - Offset(moonRadius, moonRadius),
                        Size(
                            moonRadius * 2,
                            moonRadius * 2
                        )
                    )
                )
            }
            val crescent = Path.combine(PathOperation.Difference, moonDisc, moonCutout)
            onDrawBehind {
                val light = breath.value
                drawCircle(mist, 85f * unit, center)
                rotate(geometry.tiltDegrees, center) {
                    drawOval(
                        colors.rose.copy(alpha = 0.36f * light),
                        orbitTopLeft,
                        orbitSize,
                        style = stroke
                    )
                }
                drawCircle(sunHalo, 19f * unit, sun, alpha = light)
                drawCircle(sunBody, 6.5f * unit, sun)
                drawCircle(colors.surface, 4.2f * unit, follicular)
                drawCircle(
                    colors.rose.copy(alpha = 0.55f),
                    4.2f * unit,
                    follicular,
                    style = Stroke(0.8.dp.toPx())
                )
                drawCircle(ovulationHalo, 24f * unit, ovulation, alpha = light)
                drawCircle(pearlBody, 8.5f * unit, ovulation)
                drawCircle(
                    colors.rose.copy(alpha = 0.30f),
                    8.5f * unit,
                    ovulation,
                    style = Stroke(0.7.dp.toPx())
                )
                drawCircle(colors.rose.copy(alpha = 0.65f), 4.5f * unit, luteal)
                drawPath(crescent, moonColor)
                val scale = currentPulse.value
                drawCircle(activeHalo, 16f * unit * scale, active)
                drawCircle(colors.surface, 6f * unit * scale, active)
                drawCircle(
                    colors.rose.copy(alpha = 0.8f),
                    6f * unit * scale,
                    active,
                    style = stroke
                )
                drawCircle(colors.ink.copy(alpha = 0.8f), 2.4f * unit, active)
            }
        }
    ) {}
}

@Composable
private fun orbitPulse(
    reducedMotion: Boolean,
    from: Float,
    to: Float,
    halfPeriod: Int,
    label: String
): State<Float> {
    if (reducedMotion) return rememberUpdatedState(1f)
    return rememberInfiniteTransition(label = label).animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            tween(halfPeriod, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = label
    )
}
