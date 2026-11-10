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
    fun letterArmsOnlyAfterPauseAndNineCloseTaps() {
        val sequence = LoveTapSequence()
        repeat(2) { round ->
            val start = round * 20_000L
            assertFalse(sequence.tap(start))
            assertFalse(sequence.tap(start + 1_999))
            val opened = start + 1_999
            assertFalse(sequence.tap(opened + 2_000))
            repeat(7) { index ->
                assertFalse(sequence.tap(opened + 2_000 + (index + 1) * 500L))
            }
            assertTrue(sequence.tap(opened + 2_000 + 8 * 500L))
            assertTrue(sequence.finish())
            assertFalse(sequence.finish())
        }
    }

    @Test
    fun changedTimingRestartsFromThatTap() {
        val sequence = LoveTapSequence()
        assertFalse(sequence.tap(0))
        assertFalse(sequence.tap(2_000))
        assertFalse(sequence.tap(2_501))
        assertFalse(sequence.tap(4_501))
        repeat(7) { assertFalse(sequence.tap(4_601 + it * 100L)) }
        assertTrue(sequence.tap(4_601 + 7 * 100L))
        assertFalse(sequence.tap(4_601 + 7 * 100L + 400))
        assertFalse(sequence.finish())
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
