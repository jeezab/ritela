package app.ritela

import app.ritela.ui.LoveTapSequence
import app.ritela.ui.loveBurstSpec
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoveAnimationTest {
    @Test
    fun ninthTapOpensAndStartsFreshSequence() {
        val sequence = LoveTapSequence()
        repeat(2) {
            repeat(8) { assertFalse(sequence.tap(it * 100L)) }
            assertTrue(sequence.tap(800))
        }
    }

    @Test
    fun timeoutDependsOnAdjacentTapsAndExactBoundaryIsIncluded() {
        val sequence = LoveTapSequence()
        repeat(8) { assertFalse(sequence.tap(it * 3000L)) }
        assertTrue(sequence.tap(24_000))
        repeat(8) { assertFalse(sequence.tap(30_000 + it * 100L)) }
        assertFalse(sequence.tap(33_701))
        repeat(7) { assertFalse(sequence.tap(33_800 + it * 100L)) }
        assertTrue(sequence.tap(34_500))
    }

    @Test
    fun burstsAreBoundedVariedAndReproducible() {
        val random = Random(42)
        val specs = List(100) { loveBurstSpec(random) }
        val repeatedRandom = Random(42)
        assertEquals(specs, List(100) { loveBurstSpec(repeatedRandom) })
        specs.forEach { spec ->
            assertTrue(spec.durationMillis in 800..1500)
            assertTrue(spec.particles.size in 5..8)
            spec.particles.forEach {
                assertTrue(it.x.isFinite() && it.y.isFinite())
                assertTrue(it.size in 7f..13f)
            }
        }
        val particles = specs.flatMap { it.particles }
        assertTrue(particles.any { it.pink } && particles.any { !it.pink })
        assertTrue(particles.any { it.x < 0 } && particles.any { it.x > 0 })
        assertTrue(particles.any { it.y < 0 } && particles.any { it.y > 0 })
    }
}
