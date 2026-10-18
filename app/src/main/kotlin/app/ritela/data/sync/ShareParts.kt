package app.ritela.data.sync

import app.ritela.data.PartnerCodec
import app.ritela.domain.sync.SyncLimits
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Splits one confirmed share into internet packets. A share that already fits is unchanged. */
internal object ShareParts {
    private val PART_BUDGET = SyncLimits.PROJECTED_BYTES - 2048
    private const val PART_MARKER = 180

    fun transport(content: JSONObject): Boolean = content.optInt("parts", 1) > 1 &&
        content.has("batch") && content.has("part")

    /** Null when the share is over one packet and has no array that can be split. */
    fun split(content: JSONObject, batch: String): List<JSONObject>? {
        require(UUID.fromString(batch).toString() == batch)
        if (utf8(content) <= PART_BUDGET) return listOf(JSONObject(content.toString()))
        val dayItems = content.optJSONArray("days")?.let(PartnerCodec::objects).orEmpty()
        val periodItems = content.optJSONArray("periods")?.let(PartnerCodec::objects).orEmpty()
        if (dayItems.isEmpty() && periodItems.isEmpty()) return null
        val shell = JSONObject(content.toString()).apply {
            remove("days")
            remove("periods")
        }
        if (utf8(shell) + PART_MARKER >= PART_BUDGET) return null
        val pieces = mutableListOf<JSONObject>()
        var current = JSONObject(shell.toString())
        val days = JSONArray()
        val periods = JSONArray()
        fun measured(): Int {
            val trial = JSONObject(current.toString())
            if (days.length() > 0) trial.put("days", days)
            if (periods.length() > 0) trial.put("periods", periods)
            return utf8(trial)
        }
        fun rotate() {
            if (days.length() > 0) current.put("days", JSONArray(days.toString()))
            if (periods.length() > 0) current.put("periods", JSONArray(periods.toString()))
            pieces += current
            current = JSONObject().put(
                "scope",
                JSONObject(shell.getJSONObject("scope").toString())
            )
            clear(days)
            clear(periods)
        }
        fun add(array: JSONArray, item: JSONObject): Boolean {
            array.put(JSONObject(item.toString()))
            if (measured() + PART_MARKER <= PART_BUDGET) return true
            array.remove(array.length() - 1)
            val empty = days.length() == 0 && periods.length() == 0
            if (empty && pieces.isEmpty()) {
                rotate()
                return add(array, item)
            }
            if (empty) return false
            rotate()
            return add(array, item)
        }
        for (item in periodItems) if (!add(periods, item)) return null
        for (item in dayItems) if (!add(days, item)) return null
        if (days.length() > 0 || periods.length() > 0 || pieces.isEmpty()) rotate()
        if (pieces.size == 1) return pieces.takeIf { utf8(it[0]) <= PART_BUDGET }
        pieces.forEachIndexed { index, part ->
            part.put("batch", batch).put("part", index).put("parts", pieces.size)
            if (utf8(part) > PART_BUDGET) return null
        }
        return pieces
    }

    fun merge(parts: List<JSONObject>): JSONObject {
        val ordered = parts.sortedBy { it.getInt("part") }
        val expected = ordered.first().getInt("parts")
        require(expected == ordered.size && expected > 1)
        require(ordered.map { it.getInt("part") } == (0 until expected).toList())
        val batch = ordered.first().getString("batch")
        require(ordered.all { it.getString("batch") == batch && it.getInt("parts") == expected })
        val result = JSONObject(ordered.first().toString())
        result.remove("batch")
        result.remove("part")
        result.remove("parts")
        result.remove("days")
        result.remove("periods")
        val days = JSONArray()
        val periods = JSONArray()
        for (part in ordered) {
            append(days, part.optJSONArray("days"))
            append(periods, part.optJSONArray("periods"))
        }
        if (periods.length() > 0) result.put("periods", periods)
        if (days.length() > 0) result.put("days", days)
        return result
    }

    private fun append(target: JSONArray, source: JSONArray?) {
        if (source == null) return
        for (index in 0 until source.length()) target.put(source.getJSONObject(index))
    }

    private fun clear(array: JSONArray) {
        while (array.length() > 0) array.remove(array.length() - 1)
    }

    private fun utf8(value: JSONObject) = value.toString().toByteArray().size
}
