package app.ritela.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Ephemeral UI gesture state; elapsed realtime is unaffected by wall-clock changes. */
class LoveTapSequence {
    private var previous: Long? = null
    private var count = 0

    fun tap(elapsedMillis: Long): Boolean {
        if (previous?.let { elapsedMillis - it > 3_000 || elapsedMillis < it } != false) count = 0
        previous = elapsedMillis
        count++
        if (count < 9) return false
        count = 0
        previous = null
        return true
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
