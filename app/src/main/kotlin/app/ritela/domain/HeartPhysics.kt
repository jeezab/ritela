package app.ritela.domain

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class HeartBody(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val mass: Float,
    var angle: Float,
    var spin: Float,
    val shade: Int
)

/** A heart is approximated by two lobes and a tapering lower cluster of circles. */
class HeartPhysics(seed: Int = 37, count: Int = 18) {
    val hearts: List<HeartBody> = Random(seed).let { random ->
        (0 until count.coerceIn(1, 24)).map {
            val radius = random.nextFloat() * 12f + 14f
            HeartBody(
                random.nextFloat(), random.nextFloat(), (random.nextFloat() - 0.5f) * 120f,
                (random.nextFloat() - 0.5f) * 120f, radius, radius * radius / 400f,
                random.nextFloat() * 6.28f, random.nextFloat() - 0.5f, random.nextInt(4)
            )
        }
    }
    private var initialized = false
    private val shape = listOf(
        Triple(-0.34f, -0.22f, 0.45f),
        Triple(0.34f, -0.22f, 0.45f),
        Triple(0f, 0.2f, 0.42f),
        Triple(0f, 0.56f, 0.2f)
    )
    fun step(
        width: Float,
        height: Float,
        dt: Float,
        gx: Float,
        gy: Float,
        shake: Float,
        edge: ExchangeEdge? = null,
        incoming: Boolean = false
    ) {
        if (width <= 0 || height <= 0) return
        if (!initialized) {
            hearts.forEach {
                it.x *= width
                it.y *= height
            }
            initialized = true
            if (incoming && edge != null) hearts.forEach { placeAtEdge(it, width, height, edge) }
        }
        val seconds = dt.coerceIn(0f, 0.033f)
        val acceleration = 85f
        hearts.forEach { heart ->
            val mx = when (edge) {
                ExchangeEdge.LEFT -> -500f
                ExchangeEdge.RIGHT -> 500f
                else -> 0f
            }
            val my = when (edge) {
                ExchangeEdge.TOP -> -500f
                ExchangeEdge.BOTTOM -> 500f
                else -> 0f
            }
            val direction = if (incoming) -1f else 1f
            heart.vx =
                (
                    (
                        heart.vx +
                            (gx * acceleration + mx * direction + cos(heart.angle) * shake * 10f) *
                            seconds
                        ) *
                        0.994f
                    ).coerceIn(-650f, 650f)
            heart.vy =
                (
                    (
                        heart.vy +
                            (gy * acceleration + my * direction + sin(heart.angle) * shake * 10f) *
                            seconds
                        ) *
                        0.994f
                    ).coerceIn(-650f, 650f)
            heart.x += heart.vx * seconds
            heart.y += heart.vy * seconds
            heart.angle += heart.spin.coerceIn(-3f, 3f) * seconds
            if (edge == null || incoming) bounds(heart, width, height)
        }
        for (i in hearts.indices) for (j in i + 1 until hearts.size) collide(hearts[i], hearts[j])
    }
    private fun placeAtEdge(h: HeartBody, w: Float, height: Float, edge: ExchangeEdge) {
        when (edge) {
            ExchangeEdge.LEFT -> h.x = h.radius
            ExchangeEdge.RIGHT -> h.x = w - h.radius
            ExchangeEdge.TOP -> h.y = h.radius
            ExchangeEdge.BOTTOM -> h.y = height - h.radius
        }
    }
    private fun bounds(h: HeartBody, w: Float, height: Float) {
        val circles = circles(h)
        val left = circles.minOf { it.first - it.third }
        val right = circles.maxOf {
            it.first +
                it.third
        }
        val top = circles.minOf { it.second - it.third }
        val bottom = circles.maxOf {
            it.second +
                it.third
        }
        if (left < 0) {
            h.x -= left
            h.vx = kotlin.math.abs(h.vx) * 0.72f
        }
        if (right > w) {
            h.x -= right - w
            h.vx = -kotlin.math.abs(h.vx) * 0.72f
        }
        if (top < 0) {
            h.y -= top
            h.vy = kotlin.math.abs(h.vy) * 0.72f
        }
        if (bottom > height) {
            h.y -= bottom - height
            h.vy = -kotlin.math.abs(h.vy) * 0.72f
        }
    }
    private fun circles(h: HeartBody): List<Triple<Float, Float, Float>> = shape.map { (x, y, r) ->
        Triple(
            h.x + (x * cos(h.angle) - y * sin(h.angle)) * h.radius,
            h.y + (x * sin(h.angle) + y * cos(h.angle)) * h.radius,
            r * h.radius
        )
    }
    private fun collide(a: HeartBody, b: HeartBody) {
        if (kotlin.math.abs(a.x - b.x) > (a.radius + b.radius) * 1.5f ||
            kotlin.math.abs(a.y - b.y) > (a.radius + b.radius) * 1.5f
        ) {
            return
        }
        var overlap = 0f
        var nx = 0f
        var ny = 0f
        for (ca in circles(a)) {
            for (cb in circles(b)) {
                val dx = cb.first - ca.first
                val dy = cb.second - ca.second
                val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                val depth = ca.third + cb.third - distance
                if (depth > overlap) {
                    overlap = depth
                    nx = dx / distance
                    ny = dy / distance
                }
            }
        }
        if (overlap <= 0f) return
        val invA = 1f / a.mass
        val invB = 1f / b.mass
        val inv = invA + invB
        a.x -= nx * overlap * invA / inv
        a.y -= ny * overlap * invA / inv
        b.x += nx * overlap * invB / inv
        b.y += ny * overlap * invB / inv
        val speed = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
        if (speed >= 0f) return
        val impulse = -1.65f * speed / inv
        a.vx -= impulse * nx * invA
        a.vy -= impulse * ny * invA
        b.vx += impulse * nx * invB
        b.vy += impulse * ny * invB
        val torque = ((b.vx - a.vx) * ny - (b.vy - a.vy) * nx) * 0.001f
        a.spin = (a.spin + torque).coerceIn(-3f, 3f)
        b.spin = (b.spin - torque).coerceIn(-3f, 3f)
    }
}
