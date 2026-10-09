package app.ritela.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.ritela.R
import app.ritela.domain.Doodle
import app.ritela.domain.DoodleOpening
import app.ritela.domain.DoodlePoint
import app.ritela.domain.DoodleStroke
import java.time.LocalDateTime
import java.time.ZoneId

object DoodleSchedule {
    fun timestamp(value: String): Long? = runCatching {
        LocalDateTime.parse(
            value
        ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli().takeIf {
            it >=
                0
        }
    }.getOrNull()
}

@Composable
internal fun DoodleEditor(dismiss: () -> Unit, send: (Doodle) -> Unit) {
    var strokes by remember { mutableStateOf<List<DoodleStroke>>(emptyList()) }
    var redo by remember { mutableStateOf<List<DoodleStroke>>(emptyList()) }
    var current by remember { mutableStateOf<List<DoodlePoint>>(emptyList()) }
    var color by remember { mutableLongStateOf(0xffee839dL) }
    var width by remember { mutableFloatStateOf(0.012f) }
    var eraser by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf(DoodleOpening.IMMEDIATE) }
    var date by remember {
        mutableStateOf(LocalDateTime.now().plusDays(1).withSecond(0).withNano(0).toString())
    }
    var days by remember { mutableStateOf("1") }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val palette =
        listOf(0xffee839dL, 0xffc9506eL, 0xfffacdabL, 0xfff8eee7L, 0xffbfb0dbL, 0xff9ab8aeL)
    fun point(offset: Offset): DoodlePoint = DoodlePoint(
        (offset.x / bounds.width.coerceAtLeast(1)).coerceIn(0f, 1f),
        (offset.y / bounds.height.coerceAtLeast(1)).coerceIn(0f, 1f)
    )
    val moment = DoodleSchedule.timestamp(date)
    val delay = days.toIntOrNull()?.takeIf { it in 1..365 }
    PartnerModal(stringResource(R.string.partner_doodle), dismiss) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.15f).background(HomeColors.background)) {
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(1.15f).testTag("doodle-canvas")
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .onSizeChanged {
                        bounds = it
                    }.pointerInput(color, width, eraser, strokes.size) {
                        detectDragGestures(
                            onDragStart = { current = listOf(point(it)) },
                            onDragEnd = {
                                if (current.isNotEmpty() && strokes.size < 256 &&
                                    strokes.sumOf { it.points.size } + current.size <= 20000
                                ) {
                                    strokes = strokes + DoodleStroke(current, color, width, eraser)
                                    redo =
                                        emptyList()
                                }
                                current = emptyList()
                            },
                            onDragCancel = { current = emptyList() },
                            onDrag = { change, _ ->
                                change.consume()
                                if (current.size < 4096) current = current + point(change.position)
                            }
                        )
                    }
            ) {
                drawDoodle(strokes)
                if (current.isNotEmpty()) {
                    drawDoodle(
                        listOf(DoodleStroke(current, color, width, eraser))
                    )
                }
            }
        }
        FlowRow {
            palette.forEach { shade ->
                FilterChip(color == shade && !eraser, {
                    color = shade
                    eraser =
                        false
                }, label = {
                    Canvas(Modifier.size(22.dp)) { drawCircle(Color(shade.toInt())) }
                })
            }
            FilterChip(eraser, {
                eraser = !eraser
            }, label = { Text(stringResource(R.string.partner_eraser)) })
            TextButton({
                redo = redo + strokes.last()
                strokes = strokes.dropLast(1)
            }, enabled = strokes.isNotEmpty(), modifier = Modifier.testTag("doodle-undo")) {
                Text(stringResource(R.string.partner_undo))
            }
            TextButton({
                strokes = strokes + redo.last()
                redo = redo.dropLast(1)
            }, enabled = redo.isNotEmpty(), modifier = Modifier.testTag("doodle-redo")) {
                Text(stringResource(R.string.partner_redo))
            }
        }
        Text(stringResource(R.string.partner_brush))
        Slider(width, { width = it }, valueRange = 0.003f..0.05f)
        OutlinedTextField(caption, {
            if (it.length <=
                240
            ) {
                caption = it
            }
        }, label = { Text(stringResource(R.string.partner_caption)) })
        DoodleOpening.entries.forEach { mode ->
            FilterChip(opening == mode, { opening = mode }, label = {
                Text(
                    stringResource(
                        when (mode) {
                            DoodleOpening.IMMEDIATE -> R.string.partner_immediate
                            DoodleOpening.AFTER_DATE -> R.string.partner_after_date
                            DoodleOpening.AFTER_DAYS -> R.string.partner_after_days
                        }
                    )
                )
            })
        }
        if (opening ==
            DoodleOpening.AFTER_DATE
        ) {
            OutlinedTextField(
                date,
                {
                    date = it.take(30)
                },
                label = { Text(stringResource(R.string.partner_datetime)) },
                isError =
                    moment == null
            )
        }
        if (opening ==
            DoodleOpening.AFTER_DAYS
        ) {
            OutlinedTextField(
                days,
                {
                    days = it.take(3)
                },
                label = { Text(stringResource(R.string.partner_days)) },
                isError =
                    delay == null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
            )
        }
        Text(stringResource(R.string.partner_doodle_limits))
        Button(
            { send(Doodle(strokes, caption, opening, moment ?: 0, delay ?: 0)) },
            modifier = Modifier.testTag("doodle-prepare"),
            enabled =
                strokes.isNotEmpty() &&
                    (opening != DoodleOpening.AFTER_DATE || moment != null) &&
                    (opening != DoodleOpening.AFTER_DAYS || delay != null)
        ) {
            Text(stringResource(R.string.partner_prepare))
        }
    }
}

internal fun DrawScope.drawDoodle(strokes: List<DoodleStroke>) {
    strokes.forEach { stroke ->
        val color = if (stroke.eraser) Color.Transparent else Color(stroke.color.toInt())
        val blend = if (stroke.eraser) BlendMode.Clear else BlendMode.SrcOver
        val width = stroke.width * size.minDimension
        val points = stroke.points.map { Offset(it.x * size.width, it.y * size.height) }
        if (points.size == 1) {
            drawCircle(color, width / 2, points.first(), blendMode = blend)
        } else {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path,
                color,
                style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = blend
            )
        }
    }
}

@Composable
internal fun DoodleViewer(doodle: Doodle, dismiss: () -> Unit) {
    val reduced = orbitReducedMotion()
    val appearance = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) { appearance.animateTo(1f, tween(if (reduced) 0 else 300)) }
    PartnerModal(stringResource(R.string.partner_open_doodle), dismiss) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.15f).background(HomeColors.background)) {
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(1.15f).graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = appearance.value
                }
            ) { drawDoodle(doodle.strokes) }
        }
        Text(doodle.caption)
    }
}
