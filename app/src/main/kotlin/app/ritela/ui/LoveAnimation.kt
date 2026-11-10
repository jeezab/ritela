package app.ritela.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal const val LOVE_PAUSE_MILLIS = 2_000L
internal const val LOVE_GAP_MILLIS = 500L
internal const val LOVE_QUIET_MILLIS = 1_000L

/**
 * One tap, two quiet seconds, nine taps at most half a second apart, then one quiet second.
 * Any tap that breaks the step starts the sequence again.
 */
class LoveTapSequence {
    private var previous: Long? = null
    private var burst = 0
    private var holding = false

    /** True when the nine taps are complete and the final quiet second should start. */
    fun tap(elapsedMillis: Long): Boolean {
        val last = previous
        if (holding || last == null || elapsedMillis < last) return open(elapsedMillis)
        if (burst == 0) {
            if (elapsedMillis - last < LOVE_PAUSE_MILLIS) return open(elapsedMillis)
        } else if (elapsedMillis - last > LOVE_GAP_MILLIS) {
            return open(elapsedMillis)
        }
        burst++
        previous = elapsedMillis
        if (burst < 9) return false
        holding = true
        return true
    }

    /** True once, after the quiet second, and only if no tap cancelled it. */
    fun finish(): Boolean {
        if (!holding) return false
        clear()
        return true
    }

    private fun open(elapsedMillis: Long): Boolean {
        burst = 0
        holding = false
        previous = elapsedMillis
        return false
    }

    private fun clear() {
        burst = 0
        holding = false
        previous = null
    }
}

data class LoveParticle(
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float,
    val pink: Boolean
)

data class LoveBurstSpec(val durationMillis: Int, val particles: List<LoveParticle>)

fun loveBurstSpec(random: Random): LoveBurstSpec = LoveBurstSpec(
    random.nextInt(800, 1_501),
    List(random.nextInt(5, 9)) {
        val angle = random.nextDouble(-PI, PI)
        val distance = random.nextDouble(38.0, 110.0)
        LoveParticle(
            (cos(angle) * distance).toFloat(),
            (sin(angle) * distance).toFloat(),
            random.nextDouble(7.0, 13.0).toFloat(),
            random.nextDouble(-55.0, 55.0).toFloat(),
            random.nextBoolean()
        )
    }
)
