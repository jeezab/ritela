package app.ritela.domain

import java.util.UUID

data class SavedDoodle(val id: String, val doodle: Doodle, val uses: Int, val savedAt: Long) {
    init {
        require(UUID.fromString(id).toString() == id)
        require(uses >= 0 && savedAt >= 0)
    }
}

/** A data exchange can carry one saved drawing and a short message. */
data class DataShare(
    val scope: ShareScope,
    val note: String = "",
    val doodle: Doodle? = null,
    val savedDoodleId: String? = null,
    val automatic: Boolean = false
)

object SavedDoodles {
    const val LIMIT = 15
    const val NOTE_LIMIT = 500

    fun note(value: String): String {
        val trimmed = value.trim()
        require(trimmed.length <= NOTE_LIMIT)
        return trimmed
    }

    fun sorted(items: List<SavedDoodle>): List<SavedDoodle> = items.sortedWith(
        compareByDescending<SavedDoodle> { it.uses }
            .thenByDescending { it.savedAt }
            .thenBy { it.id }
    )

    fun save(
        items: List<SavedDoodle>,
        doodle: Doodle,
        savedAt: Long,
        id: String
    ): List<SavedDoodle> {
        require(items.size < LIMIT)
        require(items.none { it.id == id })
        return sorted(items + SavedDoodle(id, doodle, 0, savedAt))
    }

    fun delete(items: List<SavedDoodle>, id: String): List<SavedDoodle> =
        items.filterNot { it.id == id }

    fun use(items: List<SavedDoodle>, id: String): List<SavedDoodle> = sorted(
        items.map { if (it.id == id) it.copy(uses = it.uses + 1) else it }
    )
}
