package app.ritela.domain.sync

/** Internet budgets do not change the manual packet codec or saved Doodle validation. */
object SyncLimits {
    const val ENVELOPE_BYTES = 256 * 1024
    const val PROJECTED_BYTES = 96 * 1024
    const val OPERATION_BYTES = 64 * 1024
    const val DELTA_OPERATIONS = 128
    const val DOODLE_STROKES = 128
    const val DOODLE_POINTS = 4000
    const val STROKE_POINTS = 4096
    const val DOODLE_CAPTION = 240
    const val NOTE_CHARACTERS = 500
    const val LEGACY_OBJECT_BYTES = 1024 * 1024
    const val OBJECT_PARTS = 32
    const val BASELINE_BYTES = 3 * 1024 * 1024
    const val BASELINE_PARTS = 64
    const val RESPONSE_BYTES = 512 * 1024
    const val RESPONSE_PACKETS = 32
    const val ACK_IDS = 128
    const val RETENTION_DAYS = 7
    const val ROUTE_PACKETS = 256
    const val ROUTE_BYTES = 8 * 1024 * 1024
    const val DEVICE_BYTES = 16 * 1024 * 1024
    const val GRANT_BYTES = 16 * 1024

    fun acceptsNewDoodle(
        strokeCount: Int,
        pointsPerStroke: List<Int>,
        captionCharacters: Int,
        encodedBytes: Int
    ): Boolean = strokeCount in 1..DOODLE_STROKES &&
        pointsPerStroke.size == strokeCount &&
        pointsPerStroke.all { it in 1..STROKE_POINTS } &&
        pointsPerStroke.sumOf { it.toLong() } <= DOODLE_POINTS &&
        captionCharacters in 0..DOODLE_CAPTION && encodedBytes in 1..PROJECTED_BYTES
}
