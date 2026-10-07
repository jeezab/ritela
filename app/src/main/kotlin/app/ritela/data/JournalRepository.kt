package app.ritela.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import app.ritela.domain.JournalIcon
import app.ritela.domain.JournalLayout
import app.ritela.domain.JournalSection
import app.ritela.domain.JournalTag
import app.ritela.domain.validateJournalLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "journal_layout")
data class JournalLayoutEntity(@PrimaryKey val id: Int = 1, val config: String)

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_layout WHERE id = 1")
    fun observe(): Flow<JournalLayoutEntity?>

    @Query("SELECT * FROM journal_layout WHERE id = 1")
    suspend fun snapshot(): JournalLayoutEntity?

    @Upsert
    suspend fun save(entity: JournalLayoutEntity)
}

class JournalRepository(private val dao: JournalDao) {
    val layout = dao.observe().map {
        it?.let { entity -> JournalCodec.decode(entity.config) }
            ?: JournalLayout()
    }
    suspend fun save(layout: JournalLayout) {
        require(validateJournalLayout(layout))
        dao.save(JournalLayoutEntity(config = JournalCodec.encode(layout)))
    }
}

object JournalCodec {
    fun encode(layout: JournalLayout): String {
        require(validateJournalLayout(layout))
        return JSONObject().put("title", layout.title).put(
            "sections",
            JSONArray().apply {
                layout.sections.forEach { section ->
                    put(
                        JSONObject().put("id", section.id).put("title", section.title)
                            .put("icon", section.icon.name).put("multiple", section.multiple)
                            .put(
                                "tags",
                                JSONArray().apply {
                                    section.tags.forEach {
                                        put(JSONObject().put("id", it.id).put("title", it.title))
                                    }
                                }
                            )
                    )
                }
            }
        ).toString()
    }

    fun decode(text: String): JournalLayout {
        val root = JSONObject(text)
        val sections = root.getJSONArray("sections")
        require(sections.length() <= 64)
        return JournalLayout(
            root.getString("title"),
            (0 until sections.length()).map { index ->
                val section = sections.getJSONObject(index)
                val tags = section.getJSONArray("tags")
                require(tags.length() in 1..64)
                JournalSection(
                    section.getString("id"),
                    section.getString("title"),
                    JournalIcon.valueOf(section.getString("icon")),
                    (0 until tags.length()).map { tagIndex ->
                        val tag = tags.getJSONObject(tagIndex)
                        JournalTag(tag.getString("id"), tag.getString("title"))
                    },
                    section.getBoolean("multiple")
                )
            }
        ).also { require(validateJournalLayout(it)) }
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
