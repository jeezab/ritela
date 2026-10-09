package app.ritela.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.ritela.R
import app.ritela.data.ExchangeState
import app.ritela.domain.ExchangePhase
import app.ritela.domain.HeartPhysics
import kotlin.math.sqrt

private class HeartTilt : SensorEventListener {
    var x = 0f
    var y = 1f
    var shake = 0f
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onSensorChanged(event: SensorEvent) {
        x = -event.values[0] / 9.81f
        y = event.values[1] / 9.81f
        val magnitude = sqrt(event.values.take(3).sumOf { (it * it).toDouble() }).toFloat()
        shake = (magnitude - 11f).coerceIn(0f, 12f)
    }
}

@Composable
internal fun PartnerExchangeScene(state: ExchangeState, dismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = orbitReducedMotion()
    val tilt = remember { HeartTilt() }
    val physics = remember(state.incoming, state.edge) { HeartPhysics() }
    var tick by remember { mutableLongStateOf(0L) }
    var last = remember { 0L }
    val rotation = context.displayRotation()
    DisposableEffect(lifecycle, reduced, state.phase) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        fun register() {
            if (!reduced && state.phase !in listOf(
                    ExchangePhase.ACKNOWLEDGED,
                    ExchangePhase.FAILED
                ) &&
                sensor != null
            ) {
                manager.registerListener(tilt, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) register()
            if (event == Lifecycle.Event.ON_STOP) manager.unregisterListener(tilt)
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) register()
        onDispose {
            lifecycle.removeObserver(observer)
            manager.unregisterListener(tilt)
        }
    }
    LaunchedEffect(reduced, state.phase) {
        if (!reduced && state.phase != ExchangePhase.FAILED) {
            var started = 0L
            while (true) {
                var finish = false
                withFrameNanos { frame ->
                    if (started == 0L) started = frame
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) tick = frame
                    finish =
                        state.phase == ExchangePhase.ACKNOWLEDGED && frame - started >= 1200000000L
                }
                if (finish) break
                kotlinx.coroutines.delay(16)
            }
        }
    }
    JournalDialog(dismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(HomeColors.background).safeDrawingPadding()) {
            Canvas(Modifier.fillMaxSize()) {
                val time = tick
                val dt = if (last == 0L) .016f else ((time - last) / 1e9f).coerceIn(0f, .033f)
                last = time
                val gravity = when (rotation) {
                    1 -> -tilt.y to tilt.x
                    2 -> -tilt.x to -tilt.y
                    3 -> tilt.y to -tilt.x
                    else -> tilt.x to tilt.y
                }
                val edge = if (state.phase in
                    listOf(ExchangePhase.TRANSFERRING, ExchangePhase.ACKNOWLEDGED)
                ) {
                    state.edge
                } else {
                    null
                }
                physics.step(
                    size.width,
                    size.height,
                    if (reduced) 0f else dt,
                    gravity.first,
                    gravity.second,
                    tilt.shake,
                    edge,
                    state.incoming
                )
                val colors =
                    listOf(
                        Color(0xffe57493),
                        Color(0xfff0a4b2),
                        Color(0xffbe526d),
                        Color(0xffffc3ca)
                    )
                physics.hearts.forEach { h ->
                    val path = Path().apply {
                        moveTo(h.x, h.y + h.radius * .8f)
                        cubicTo(
                            h.x - h.radius * 1.35f,
                            h.y - h.radius * .1f,
                            h.x - h.radius * .6f,
                            h.y - h.radius * 1.2f,
                            h.x,
                            h.y - h.radius * .4f
                        )
                        cubicTo(
                            h.x + h.radius * .6f,
                            h.y - h.radius * 1.2f,
                            h.x + h.radius * 1.35f,
                            h.y - h.radius * .1f,
                            h.x,
                            h.y + h.radius * .8f
                        )
                        close()
                    }
                    rotate(h.angle * 180f / 3.14159f, Offset(h.x, h.y)) {
                        drawPath(path, colors[h.shade])
                    }
                }
            }
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(
                        when (state.phase) {
                            ExchangePhase.WAITING -> R.string.partner_waiting
                            ExchangePhase.CONNECTING -> R.string.partner_connecting
                            ExchangePhase.TRANSFERRING -> R.string.partner_transferring
                            ExchangePhase.ACKNOWLEDGED -> R.string.partner_exchange_done
                            ExchangePhase.FAILED -> R.string.partner_exchange_failed
                            else -> R.string.partner_bluetooth
                        }
                    ),
                    style = MaterialTheme.typography.headlineSmall
                )
                TextButton(dismiss) { Text(stringResource(R.string.partner_close)) }
            }
        }
    }
}

@Composable
internal fun PartnerFileGlow() {
    val reduced = orbitReducedMotion()
    val opacity = remember { androidx.compose.animation.core.Animatable(if (reduced) 1f else 0.5f) }
    LaunchedEffect(reduced) {
        opacity.animateTo(1f, androidx.compose.animation.core.tween(if (reduced) 0 else 400))
    }
    androidx.compose.material3.Icon(
        androidx.compose.ui.res.painterResource(R.drawable.ic_partner),
        null,
        Modifier.padding(8.dp).then(Modifier.size(32.dp)).graphicsLayer { alpha = opacity.value },
        tint = HomeColors.peach
    )
}
