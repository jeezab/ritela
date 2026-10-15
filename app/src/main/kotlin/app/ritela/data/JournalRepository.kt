package app.ritela.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.ritela.data.sync.ChangeRecorder
import app.ritela.domain.JournalIcon
import app.ritela.domain.JournalLayout
import app.ritela.domain.JournalSection
import app.ritela.domain.JournalTag
import app.ritela.domain.validateJournalLayout
import app.ritela.domain.withHistoryFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "journal_layout")
data class JournalLayoutEntity(@PrimaryKey val id: Int = 1, val config: String)

@Dao
abstract class JournalDao : ChangeRecorder() {
    @Query("SELECT * FROM journal_layout WHERE id = 1")
    abstract fun observe(): Flow<JournalLayoutEntity?>

    @Query("SELECT * FROM journal_layout WHERE id = 1")
    abstract suspend fun snapshot(): JournalLayoutEntity?

    @Upsert
    protected abstract suspend fun saveRow(entity: JournalLayoutEntity)

    @Transaction
    open suspend fun save(entity: JournalLayoutEntity) {
        if (snapshot() == entity) return
        saveRow(entity)
        recordChange("LAYOUT", "current", entity.config)
    }
}

class JournalRepository(private val dao: JournalDao) {
    private val mutations = Mutex()
    val layout = dao.observe().map {
        it?.let { entity -> JournalCodec.decode(entity.config) }
            ?: JournalLayout()
    }
    suspend fun save(layout: JournalLayout) = withContext(Dispatchers.IO) {
        mutations.withLock {
            require(validateJournalLayout(layout))
            val previous = dao.snapshot()?.let { JournalCodec.decode(it.config) }
            dao.save(
                JournalLayoutEntity(
                    config = JournalCodec.encode(
                        previous?.let {
                            layout.withHistoryFrom(it)
                        } ?: layout
                    )
                )
            )
        }
    }
}

object JournalCodec {
    fun encode(layout: JournalLayout): String {
        require(validateJournalLayout(layout))
        return JSONObject().put("title", layout.title)
            .put("sections", encodeSections(layout.sections))
            .put("archivedSections", encodeSections(layout.archivedSections)).toString()
    }

    private fun encodeSections(sections: List<JournalSection>): JSONArray = JSONArray().apply {
        sections.forEach { section ->
            put(
                JSONObject().put("id", section.id).put("title", section.title)
                    .put("icon", section.icon.name).put("multiple", section.multiple)
                    .put(
                        "tags",
                        JSONArray().apply {
                            section.tags.forEach { tag ->
                                put(
                                    JSONObject().put("id", tag.id).put("title", tag.title)
                                        .put("withoutPenetration", tag.withoutPenetration)
                                )
                            }
                        }
                    )
            )
        }
    }

    fun decode(text: String): JournalLayout {
        val root = JSONObject(text)
        return JournalLayout(
            root.getString("title"),
            decodeSections(root.getJSONArray("sections"), 64, 64),
            root.optJSONArray("archivedSections")?.let { decodeSections(it, 256, 256) }.orEmpty()
        ).also { require(validateJournalLayout(it)) }
    }

    private fun decodeSections(
        sections: JSONArray,
        maxSections: Int,
        maxTags: Int
    ): List<JournalSection> {
        require(sections.length() <= maxSections)
        return (0 until sections.length()).map { index ->
            val section = sections.getJSONObject(index)
            val tags = section.getJSONArray("tags")
            require(tags.length() <= maxTags)
            JournalSection(
                section.getString("id"),
                section.getString("title"),
                JournalIcon.valueOf(section.getString("icon")),
                (0 until tags.length()).map { tagIndex ->
                    val tag = tags.getJSONObject(tagIndex)
                    JournalTag(
                        tag.getString("id"),
                        tag.getString("title"),
                        tag.optBoolean(
                            "withoutPenetration",
                            tag.getString("id") in setOf("MASTURBATION", "ORAL")
                        )
                    )
                },
                section.getBoolean("multiple")
            )
        }
    }

    fun encodeSelections(selections: Map<String, Set<String>>): String = JSONObject().apply {
        selections.toSortedMap().forEach { (id, tags) -> put(id, JSONArray(tags.sorted())) }
    }.toString()

    fun decodeSelections(text: String): Map<String, Set<String>> {
        val root = JSONObject(text)
        require(root.length() <= 64)
        return root.keys().asSequence().associateWith { id ->
            val tags = root.getJSONArray(id)
            require(tags.length() <= 64)
            (0 until tags.length()).map { tags.getString(it) }.toSet()
        }
    }
}
