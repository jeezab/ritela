package app.ritela.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.ritela.R
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private class LoveBurst(val origin: Offset, val spec: LoveBurstSpec) {
    val progress = Animatable(0f)
    var job: Job? = null
}

/** A draw-only overlay: particle progress invalidates Canvas, never the Settings layout. */
@Composable
fun LoveSurprise(
    reducedMotion: Boolean = orbitReducedMotion(),
    elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
    random: Random = Random.Default,
    content: @Composable (onClick: () -> Unit, anchor: Modifier) -> Unit
) {
    val sequence = remember { LoveTapSequence() }
    val bursts = remember { mutableStateListOf<LoveBurst>() }
    val scope = rememberCoroutineScope()
    var anchor by remember { mutableStateOf(Offset.Zero) }
    var root by remember { mutableStateOf(Offset.Zero) }
    var showLetter by rememberSaveable { mutableStateOf(false) }
    val heart = remember {
        Path().apply {
            moveTo(0f, 0.9f)
            cubicTo(-1.6f, -0.1f, -0.9f, -1.4f, 0f, -0.5f)
            cubicTo(0.9f, -1.4f, 1.6f, -0.1f, 0f, 0.9f)
            close()
        }
    }
    Box(Modifier.onGloballyPositioned { root = it.positionInRoot() }) {
        content(
            {
                val burst = LoveBurst(anchor, loveBurstSpec(random))
                // Bound retained work even when an accessibility service sends very rapid taps.
                if (bursts.size >= 12) bursts.removeAt(0).job?.cancel()
                bursts.add(burst)
                burst.job = scope.launch {
                    try {
                        burst.progress.animateTo(
                            1f,
                            tween(burst.spec.durationMillis, easing = LinearEasing)
                        )
                    } finally {
                        bursts.remove(burst)
                    }
                }
                if (sequence.tap(elapsedMillis())) showLetter = true
            },
            Modifier.onGloballyPositioned {
                anchor = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
            }
        )
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}.testTag("love-particles")) {
            bursts.forEach { burst ->
                val progress = burst.progress.value
                val travel = if (reducedMotion) 0.16f else 1f - (1f - progress) * (1f - progress)
                burst.spec.particles.forEach { particle ->
                    val size = particle.size.dp.toPx() *
                        if (reducedMotion) 1f else 1f - 0.8f * progress
                    val offset = burst.origin - root + Offset(
                        particle.x.dp.toPx() * travel,
                        particle.y.dp.toPx() * travel
                    )
                    withTransform({
                        translate(offset.x, offset.y)
                        rotate(if (reducedMotion) 0f else particle.rotation * progress, Offset.Zero)
                        scale(size / 2, size / 2, Offset.Zero)
                    }) {
                        drawPath(
                            heart,
                            (if (particle.pink) Color(0xFFFF8FB5) else Color(0xFFE94C68))
                                .copy(alpha = (1f - progress).coerceIn(0f, 1f))
                        )
                    }
                }
            }
        }
    }
    if (showLetter) LoveLetter(reducedMotion) { showLetter = false }
}

@Composable
fun WithLoveButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(
            onClick = onClick,
            modifier = modifier.testTag("with-love"),
            border = BorderStroke(1.dp, HomeColors.border.copy(alpha = 0.5f)),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text(
                stringResource(R.string.with_love),
                color = HomeColors.blush,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun LoveLetter(reducedMotion: Boolean, onDismiss: () -> Unit) {
    val appearance = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reducedMotion) {
        appearance.animateTo(1f, tween(if (reducedMotion) 0 else 260))
    }
    val height = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.85f
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.fillMaxWidth().heightIn(max = height).graphicsLayer {
                alpha = appearance.value
                translationY = if (reducedMotion) 0f else (1f - appearance.value) * 6.dp.toPx()
            }.testTag("love-letter"),
            shape = MaterialTheme.shapes.extraLarge,
            color = HomeColors.card,
            contentColor = HomeColors.text,
            border = BorderStroke(1.dp, HomeColors.border)
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.love_letter),
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .testTag("love-letter-text"),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 18.sp,
                        lineHeight = 27.sp
                    )
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).testTag("love-letter-close")
                ) {
                    Text(stringResource(R.string.done), color = HomeColors.peach)
                }
            }
        }
    }
}
