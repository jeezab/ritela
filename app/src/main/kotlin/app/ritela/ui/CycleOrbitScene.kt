package app.ritela.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ritela.R
import kotlin.math.roundToInt

data class OrbitColors(val ink: Color, val rose: Color, val peach: Color, val surface: Color)

private data class OrbitDustParticle(
    val point: Offset,
    val color: Color,
    val radius: Float,
    val alpha: Float
)

private fun dustFade(along: Float): Float {
    val edge = 0.34f
    fun rise(value: Float): Float {
        val x = (value / edge).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
    return rise(along) * rise(1f - along)
}

/** A flat path, five landmarks, a separate current marker. No rotation or perspective. */
@Composable
fun CycleOrbitScene(
    cycleDay: Long,
    progress: Float,
    reducedMotion: Boolean,
    colors: OrbitColors,
    modifier: Modifier = Modifier,
    onMarker: ((OrbitMarker) -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onScrub: ((Float) -> Unit)? = null,
    focusProgress: Float? = null,
    centerDay: Long = cycleDay,
    cycleLength: Int = 28
) {
    val breath = orbitPulse(reducedMotion, 0.9f, 1f, RitelaMotion.ORBIT_HALF_BREATH_MILLIS)
    val pulse = orbitPulse(reducedMotion, 0.98f, 1.04f, RitelaMotion.MARKER_HALF_BREATH_MILLIS)
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val inset = with(density) { 32.dp.toPx() }
    val hitRadius = with(density) { 24.dp.toPx() }
    val geometry = remember(viewport, inset) {
        OrbitGeometry.inViewport(Size(viewport.width.toFloat(), viewport.height.toFloat()), inset)
    }
    var follicularLabel by remember { mutableStateOf(IntSize.Zero) }
    var lutealLabel by remember { mutableStateOf(IntSize.Zero) }
    val showPhaseLabels = viewport != IntSize.Zero
    val labelGap = with(density) { 8.dp.toPx() }
    val fallbackLabel = with(density) { Size(162.dp.toPx(), 26.dp.toPx()) }
    fun labelSize(measured: IntSize) = if (measured == IntSize.Zero) {
        fallbackLabel
    } else {
        Size(measured.width.toFloat(), measured.height.toFloat())
    }
    val follicularCallout = if (showPhaseLabels) {
        val label = labelSize(follicularLabel)
        geometry.phaseCallout(
            viewport.width.toFloat(),
            viewport.height.toFloat(),
            label.width,
            label.height,
            labelGap,
            bottomRight = true
        )
    } else {
        null
    }
    val lutealCallout = if (showPhaseLabels) {
        val label = labelSize(lutealLabel)
        geometry.phaseCallout(
            viewport.width.toFloat(),
            viewport.height.toFloat(),
            label.width,
            label.height,
            labelGap,
            bottomRight = false
        )
    } else {
        null
    }
    val scrub by rememberUpdatedState(onScrub)
    val openLabel = stringResource(R.string.orbit_open)
    val orbitHint = stringResource(R.string.orbit_hint)
    val scrubbing = if (onScrub == null) {
        Modifier
    } else {
        Modifier.pointerInput(geometry) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val nearest = geometry.position(geometry.progressAt(down.position))
                if ((down.position - nearest).getDistance() >
                    hitRadius * 1.5f
                ) {
                    return@awaitEachGesture
                }
                val start = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                    change.consume()
                    scrub?.invoke(geometry.progressAt(change.position))
                }
                if (start != null) {
                    drag(start.id) { change ->
                        change.consume()
                        scrub?.invoke(geometry.progressAt(change.position))
                    }
                }
            }
        }
    }
    val dayLabel = stringResource(R.string.hero_cycle_day, centerDay)
    Box(
        modifier.onSizeChanged { viewport = it }.testTag("cycle-orbit-$cycleDay")
            .then(
                if (onOpen ==
                    null
                ) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = "$openLabel. $orbitHint" }
                        .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
                }
            )
            .then(scrubbing)
    ) {
        Canvas(
            Modifier.fillMaxSize().testTag("orbit-canvas").clearAndSetSemantics {}
                .pointerInput(geometry, onMarker, onOpen, cycleLength) {
                    detectTapGestures { point ->
                        if (onMarker != null &&
                            geometry.follicularDustContains(
                                point,
                                hitRadius,
                                orbitOvulationProgress(cycleLength)
                            )
                        ) {
                            onMarker(OrbitMarker.FOLLICULAR)
                        } else if (onMarker != null &&
                            geometry.lutealDustContains(
                                point,
                                hitRadius,
                                orbitOvulationProgress(cycleLength)
                            )
                        ) {
                            onMarker(OrbitMarker.LUTEAL)
                        } else {
                            onOpen?.invoke()
                        }
                    }
                }.drawWithCache {
                    val path = geometry.path
                    val active = geometry.position(progress)
                    val sun = geometry.position(OrbitMarker.START.progress)
                    val moon = geometry.position(OrbitMarker.END.progress)
                    val focus = focusProgress?.let(geometry::position)
                    val unit = 1.dp.toPx()
                    fun halo(color: Color, point: Offset, radius: Float) = Brush.radialGradient(
                        listOf(color.copy(alpha = 0.35f), Color.Transparent),
                        point,
                        radius * unit
                    )
                    val activeHalo = halo(colors.ink, active, 28f)
                    val sunHalo = halo(colors.peach, sun, 22f)
                    val pathMeasure = PathMeasure().apply { setPath(path, true) }
                    val travelled = Path().also {
                        pathMeasure.getSegment(
                            0f,
                            pathMeasure.length * progress.coerceIn(0f, 1f),
                            it
                        )
                    }
                    val ovulation = orbitOvulationProgress(cycleLength)
                    val markers = OrbitMarker.entries.associateWith {
                        geometry.position(orbitLandmarkProgress(it, cycleLength))
                    }
                    val activeOverLandmark = markers.filterKeys {
                        it != OrbitMarker.FOLLICULAR && it != OrbitMarker.LUTEAL
                    }.values.any {
                        (it - active).getDistance() < 14f * unit
                    }
                    fun band(from: Float, to: Float, start: Color, end: Color, settle: Boolean) =
                        (1 until 42).map { index ->
                            val along = index / 42f
                            val place = from + (to - from) * along
                            val fade = if (settle) 1f - along * .5f else 1f
                            OrbitDustParticle(
                                geometry.position(place),
                                lerp(start, end, along),
                                1.55f * unit,
                                dustFade(along) * fade
                            )
                        }.filter {
                            (it.point - geometry.position(from)).getDistance() >= 13f * unit &&
                                (it.point - geometry.position(to)).getDistance() >= 13f * unit
                        }
                    val dust = band(
                        OrbitMarker.START.progress,
                        ovulation,
                        Color(0xFFE24B5C),
                        Color(0xFF5FCF72),
                        false
                    ) + band(
                        ovulation,
                        OrbitMarker.END.progress,
                        Color(0xFF5FCF72),
                        Color(0xFFF6D7DE),
                        true
                    )
                    val moonRadius = 8f * unit
                    val moonDisc = Path().apply {
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                moon - Offset(moonRadius, moonRadius),
                                Size(moonRadius * 2, moonRadius * 2)
                            )
                        )
                    }
                    val cutout = Path().apply {
                        val center = moon + Offset(4f * unit, -3f * unit)
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                center - Offset(moonRadius, moonRadius),
                                Size(moonRadius * 2, moonRadius * 2)
                            )
                        )
                    }
                    val crescent = Path.combine(PathOperation.Difference, moonDisc, cutout)
                    val arrow = geometry.position(0.08f)
                    val tangent = geometry.position(0.085f) - geometry.position(0.075f)
                    val direction = tangent / tangent.getDistance().coerceAtLeast(1f)
                    val normal = Offset(-direction.y, direction.x)
                    val leaves = (0..1).map { index ->
                        val origin = Offset(
                            12f * unit + index * 12f * unit,
                            size.height - 28f * unit
                        )
                        Path().apply {
                            moveTo(origin.x, origin.y)
                            quadraticTo(
                                origin.x - 3f * unit,
                                origin.y - 18f * unit,
                                origin.x + 16f * unit,
                                origin.y - 28f * unit
                            )
                            quadraticTo(
                                origin.x + 14f * unit,
                                origin.y - 8f * unit,
                                origin.x,
                                origin.y
                            )
                            close()
                        }
                    }
                    onDrawBehind {
                        val light = breath.value
                        drawPath(
                            path,
                            colors.rose.copy(alpha = 0.07f * light),
                            style = Stroke(5f * unit)
                        )
                        drawPath(
                            path,
                            colors.rose.copy(alpha = 0.6f * light),
                            style = Stroke(1.1f * unit)
                        )
                        drawPath(
                            travelled,
                            colors.peach.copy(alpha = 0.8f),
                            style = Stroke(1.5f * unit)
                        )
                        for (particle in dust) {
                            drawCircle(
                                particle.color.copy(alpha = particle.alpha * .34f),
                                particle.radius * 2.5f,
                                particle.point
                            )
                            drawCircle(
                                particle.color,
                                particle.radius,
                                particle.point,
                                alpha = particle.alpha
                            )
                        }
                        drawLine(
                            colors.peach.copy(alpha = 0.8f),
                            arrow - direction * 5f * unit + normal * 3f * unit,
                            arrow,
                            1.2f * unit
                        )
                        drawLine(
                            colors.peach.copy(alpha = 0.8f),
                            arrow - direction * 5f * unit - normal * 3f * unit,
                            arrow,
                            1.2f * unit
                        )
                        drawCircle(sunHalo, 22f * unit, sun, alpha = light)
                        for (marker in OrbitMarker.entries) {
                            val point = markers.getValue(marker)
                            when (marker) {
                                OrbitMarker.START -> drawCircle(colors.peach, 7f * unit, point)

                                OrbitMarker.FOLLICULAR -> Unit

                                OrbitMarker.OVULATION -> drawCircle(
                                    colors.ink.copy(alpha = 0.85f),
                                    9.5f * unit,
                                    point
                                )

                                OrbitMarker.LUTEAL -> Unit

                                OrbitMarker.END -> drawPath(
                                    crescent,
                                    colors.peach.copy(alpha = 0.85f)
                                )
                            }
                        }
                        for (leaf in leaves) drawPath(leaf, colors.rose.copy(alpha = 0.22f))
                        drawCircle(
                            colors.peach.copy(alpha = 0.4f),
                            unit,
                            Offset(
                                size.width - 12f * unit,
                                size.height * 0.3f
                            )
                        )
                        drawCircle(
                            colors.rose.copy(alpha = 0.4f),
                            unit,
                            Offset(
                                14f * unit,
                                size.height * 0.2f
                            )
                        )
                        if (focus != null && (focus - active).getDistance() > unit) {
                            drawCircle(colors.surface, 8f * unit, focus)
                            drawCircle(colors.peach, 8f * unit, focus, style = Stroke(2f * unit))
                        }
                        drawCircle(activeHalo, 28f * unit * pulse.value, active)
                        if (!activeOverLandmark) {
                            drawCircle(colors.surface, 11f * unit * pulse.value, active)
                        }
                        drawCircle(
                            colors.ink,
                            11f * unit * pulse.value,
                            active,
                            style = Stroke(2.3f * unit)
                        )
                        if (!activeOverLandmark) drawCircle(colors.ink, 3.5f * unit, active)
                    }
                }
        ) {}
        if (follicularCallout != null && lutealCallout != null) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                val follicularSize = labelSize(follicularLabel)
                val lutealSize = labelSize(lutealLabel)
                drawPhaseArrow(
                    follicularCallout + Offset(follicularSize.width * .5f, 0f),
                    geometry.position(orbitOvulationProgress(cycleLength) * .7f)
                )
                drawPhaseArrow(
                    lutealCallout + Offset(lutealSize.width * .5f, lutealSize.height),
                    geometry.position(
                        (orbitOvulationProgress(cycleLength) + OrbitMarker.END.progress) / 2f
                    )
                )
            }
            OrbitPhaseCaption(
                stringResource(R.string.orbit_follicular),
                "orbit-label-follicular",
                follicularCallout
            ) { follicularLabel = it }
            OrbitPhaseCaption(
                stringResource(R.string.orbit_luteal),
                "orbit-label-luteal",
                lutealCallout
            ) { lutealLabel = it }
        }
        androidx.compose.material3.Text(
            centerDay.toString(),
            Modifier.align(androidx.compose.ui.Alignment.Center).testTag("orbit-center-day")
                .semantics { contentDescription = dayLabel },
            color = colors.ink,
            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge
        )
        if (viewport != IntSize.Zero && onMarker != null) {
            for (marker in OrbitMarker.entries) {
                val label = stringResource(orbitMarkerTitle(marker))
                OrbitTouchTarget(
                    geometry.position(orbitLandmarkProgress(marker, cycleLength)),
                    hitRadius,
                    "orbit-marker-${marker.name}",
                    label
                ) {
                    onMarker(marker)
                }
            }
        }
        if (viewport != IntSize.Zero && onOpen != null && OrbitMarker.entries.none {
                (
                    geometry.position(orbitLandmarkProgress(it, cycleLength)) -
                        geometry.position(progress)
                    ).getDistance() < hitRadius
            }
        ) {
            OrbitTouchTarget(
                geometry.position(progress),
                hitRadius,
                "orbit-current",
                stringResource(R.string.orbit_current_day, cycleDay),
                onOpen
            )
        }
    }
}

@Composable
private fun OrbitPhaseCaption(
    text: String,
    tag: String,
    topLeft: Offset,
    onSize: (IntSize) -> Unit
) {
    val script = remember { FontFamily(Font(R.font.caveat, FontWeight.Medium)) }
    androidx.compose.material3.Text(
        text,
        Modifier.offset {
            IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
        }.onSizeChanged(onSize).testTag(tag),
        color = Color(0xFFF6E4D6),
        style = TextStyle(
            fontFamily = script,
            fontSize = 18.sp,
            shadow = Shadow(Color(0x55C9A090), Offset(0f, 1f), 5f)
        )
    )
}

private fun DrawScope.drawPhaseArrow(start: Offset, target: Offset) {
    val delta = target - start
    val distance = delta.getDistance()
    if (distance < 8.dp.toPx()) return
    val direction = delta / distance
    val normal = Offset(-direction.y, direction.x)
    val end = target - direction * 5.dp.toPx()
    val control = (start + end) * .5f + normal * 6.dp.toPx()
    val tangent = (end - control).let { it / it.getDistance().coerceAtLeast(1f) }
    val side = Offset(-tangent.y, tangent.x)
    val head = 5.dp.toPx()
    val path = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(control.x, control.y, end.x, end.y)
        val left = end - tangent * head + side * head * .55f
        val right = end - tangent * head - side * head * .55f
        moveTo(left.x, left.y)
        lineTo(end.x, end.y)
        lineTo(right.x, right.y)
    }
    drawPath(path, Color(0xFFF6E4D6), style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round))
}

@Composable
private fun OrbitTouchTarget(
    point: Offset,
    radius: Float,
    tag: String,
    label: String,
    onClick: () -> Unit
) {
    Box(
        Modifier.offset {
            IntOffset((point.x - radius).roundToInt(), (point.y - radius).roundToInt())
        }
            .size(48.dp).testTag(tag).semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick)
    )
}

@Composable
private fun orbitPulse(
    reducedMotion: Boolean,
    from: Float,
    to: Float,
    halfPeriod: Int
): State<Float> {
    if (reducedMotion) return rememberUpdatedState(1f)
    return rememberInfiniteTransition(label = "orbit light").animateFloat(
        from,
        to,
        infiniteRepeatable(tween(halfPeriod), RepeatMode.Reverse),
        label = "orbit pulse"
    )
}
