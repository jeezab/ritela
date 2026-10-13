package app.ritela

import app.ritela.domain.Doodle
import app.ritela.domain.DoodlePoint
import app.ritela.domain.DoodleStroke
import app.ritela.domain.SavedDoodle
import app.ritela.domain.SavedDoodles
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedDoodlesTest {
    private fun doodle(caption: String) = Doodle(
        listOf(DoodleStroke(listOf(DoodlePoint(.2f, .4f)), 0xffee839d, .02f)),
        caption
    )

    private fun saved(uses: Int, savedAt: Long) = SavedDoodle(
        UUID.randomUUID().toString(),
        doodle(uses.toString()),
        uses,
        savedAt
    )

    @Test fun frequentDoodlesStayFirstAndTheLibraryStopsAtFifteen() {
        val older = saved(2, 10)
        val newer = saved(2, 20)
        val favorite = saved(5, 1)
        assertEquals(
            listOf(favorite.id, newer.id, older.id),
            SavedDoodles.sorted(listOf(older, newer, favorite)).map { it.id }
        )
        assertEquals("hello", SavedDoodles.note("  hello  "))
        assertTrue(runCatching { SavedDoodles.note("x".repeat(501)) }.isFailure)
        val room = List(SavedDoodles.LIMIT - 1) { saved(0, it.toLong()) }
        val added = SavedDoodles.save(room, doodle("new"), 100, UUID.randomUUID().toString())
        assertEquals(SavedDoodles.LIMIT, added.size)
        assertTrue(
            runCatching {
                SavedDoodles.save(added, doodle("extra"), 101, UUID.randomUUID().toString())
            }.isFailure
        )
        val removed = SavedDoodles.delete(added, added.first().id)
        assertEquals(SavedDoodles.LIMIT - 1, removed.size)
        val used = SavedDoodles.use(removed, removed.last().id)
        assertEquals(removed.last().id, used.first().id)
        assertEquals(1, used.first().uses)
    }
}
